// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-server/src/proxy.rs and upstream.rs (AGPL-3.0, same author).
package app.nijiyome.server.ext

import app.nijiyome.server.net.NetError
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.SafeHttp
import app.nijiyome.server.net.SafeRequest
import app.nijiyome.server.net.originOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Headers a ticket may carry: lower-case names, no hop-by-hop or forwarding headers, no CR / LF. */
fun cleanHeaders(input: List<Pair<String, String>>): List<Pair<String, String>> = input.filter { (k0, v) ->
    val k = k0.lowercase()
    k.isNotEmpty() && k.length <= 200 && v.length <= 8192 && k.none { it == '\r' || it == '\n' || it == ':' } && v.none { it == '\r' || it == '\n' } &&
        k !in setOf("host", "connection", "content-length", "transfer-encoding", "proxy-authorization") && !k.startsWith("x-forwarded-")
}.map { it.first.lowercase() to it.second }

/** [cleanHeaders] without `cookie` / `authorization` (a child resource on another origin). */
fun stripCredentials(headers: List<Pair<String, String>>): List<Pair<String, String>> =
    cleanHeaders(headers).filter { it.first != "cookie" && it.first != "authorization" }

class Ticket(val url: String, val headers: List<Pair<String, String>>, val session: String, val expiresUnix: Long)

/** One proxied answer, already shaped for the HTTP layer. */
class ProxyAnswer(val status: Int, val headers: List<Pair<String, String>>, val body: ByteArray?, val stream: Response? = null)

/**
 * `GET | HEAD /media/{ticket}/{name}`: a resolve result whose stream needs headers (Referer, cookies, User-Agent) is rewritten to
 * `/media/<ticket>/<file name>`; the ticket (random id -> url, headers, session, expiry) lives in memory, the bytes stream through
 * the SSRF-safe client with Range passthrough, HLS playlists are rewritten so segments and keys go through the proxy too, and
 * small objects (images, subtitles, short segments) are cached on disk. Only the device that owns a ticket may use it.
 */
class MediaProxy(serverDir: File, private val http: SafeHttp = SafeHttp(NetPolicy.extensionFetch())) {
    private val tickets = ConcurrentHashMap<String, Ticket>()
    val cache = MediaCache(serverDir)

    /** Registers [url] + [headers] for [session]; returns `<base>/api/server/v1/media/<ticket>/<name>`. */
    fun mint(base: String, url: String, headers: List<Pair<String, String>>, session: String, expiresUnix: Long? = null): String {
        val id = ExtAuth.newToken()
        val now = ExtAuth.nowUnix()
        if (tickets.size >= MAX_TICKETS) tickets.entries.removeIf { it.value.expiresUnix <= now }
        if (tickets.size >= MAX_TICKETS) tickets.entries.minByOrNull { it.value.expiresUnix }?.let { tickets.remove(it.key) }
        tickets[id] = Ticket(url, cleanHeaders(headers), session, expiresUnix ?: (now + TICKET_TTL_SECS))
        return "${base.trimEnd('/')}$API_PREFIX/media/$id/${displayName(url)}"
    }

    fun ticket(id: String): Ticket? = tickets[id]

    fun ticketCount(): Int = tickets.size

    suspend fun serve(head: Boolean, ticketId: String, range: String?, base: String, sessionOk: (String) -> Boolean): ProxyAnswer {
        val t = tickets[ticketId] ?: throw Failure(404, "media_not_found")
        if (t.expiresUnix < ExtAuth.nowUnix()) throw Failure(410, "media_expired", "Resolve this source again to refresh playback")
        if (!sessionOk(t.session)) throw Failure(401, "device_revoked")
        val key = MediaCache.key(t.url, t.headers)
        if (!head && range == null) {
            cache.get(key)?.let { (ct, data) -> return ProxyAnswer(200, listOf("content-type" to ct, "x-nijiyome-cache" to "HIT") + SECURE, data) }
        }
        val headers = t.headers.filter { it.first != "range" && it.first != "accept-encoding" }.toMutableList()
        headers += "accept-encoding" to "identity"
        if (range != null) {
            if (!validRange(range)) throw Failure(416, "invalid_range")
            headers += "range" to range
        }
        val url = t.url.toHttpUrlOrNull() ?: throw Failure(400, "invalid_url")
        val resp = try {
            // a direct video streams for as long as the player reads it: the timeout bounds the connection, the head and each read,
            // never the whole body (send's call timeout cut every stream at 30 s)
            http.sendStream(SafeRequest(if (head) "HEAD" else "GET", url, headers)).let { it.response to it.finalUrl }
        } catch (e: NetError) {
            throw when (e) {
                is NetError.BlockedIp, is NetError.Host, is NetError.Scheme, is NetError.Port -> Failure(403, "private_upstream")
                is NetError.Timeout -> Failure(504, "upstream_timeout")
                else -> Failure(502, "upstream_unavailable", e.message ?: "upstream unavailable")
            }
        }
        val (r, finalUrl) = resp
        val out = mutableListOf<Pair<String, String>>()
        for (name in listOf("content-type", "content-range", "accept-ranges", "content-length", "etag", "last-modified")) {
            r.header(name)?.let { out += name to it }
        }
        out += SECURE
        val ct = r.header("content-type").orEmpty()
        if (head) {
            r.close()
            return ProxyAnswer(r.code, out, ByteArray(0))
        }
        if (r.isSuccessful && isPlaylist(ct, finalUrl.toString())) {
            val text = readCapped(r, MAX_PLAYLIST).decodeToString()
            val parentOrigin = originOf(finalUrl)
            val body = rewritePlaylist(text, finalUrl) { u ->
                val h = if (originOf(u) == parentOrigin) t.headers else stripCredentials(t.headers)
                mint(base, u.toString(), h, t.session, t.expiresUnix)
            } ?: throw Failure(502, "invalid_playlist", "The playlist points outside http(s)")
            out.removeAll { it.first == "content-length" || it.first == "content-type" }
            out += "content-type" to "application/vnd.apple.mpegurl"
            return ProxyAnswer(r.code, out, body.toByteArray())
        }
        val size = r.body.contentLength()
        if (r.code == 200 && range == null && size in 1..MAX_CACHED_OBJECT.toLong() && cacheable(ct)) {
            val data = readCapped(r, MAX_CACHED_OBJECT)
            cache.put(key, ct, data)
            out.removeAll { it.first == "content-length" }
            out += "x-nijiyome-cache" to "MISS"
            return ProxyAnswer(r.code, out, data)
        }
        return ProxyAnswer(r.code, out, null, r)
    }

    private suspend fun readCapped(r: Response, cap: Int): ByteArray = withContext(Dispatchers.IO) {
        try {
            app.nijiyome.server.net.readCapped(r, cap)
        } catch (_: NetError.TooLarge) {
            throw Failure(413, "response_too_large")
        } catch (_: NetError) {
            throw Failure(502, "upstream_unavailable")
        }
    }

    companion object {
        const val CACHE_DIR = "media-cache"
        const val TICKET_TTL_SECS = 6L * 3600
        const val MAX_TICKETS = 20_000
        const val MAX_CACHED_OBJECT = 512 * 1024
        const val MAX_CACHE_BYTES = 32L * 1024 * 1024
        const val CACHE_TTL_SECS = 300L
        private const val MAX_PLAYLIST = 2 * 1024 * 1024
        /** Session of tickets minted for this PC's own player (accepted only from a loopback peer). */
        const val LOCAL_SESSION = "local:player"

        /** Headers every proxied answer carries. */
        val SECURE = listOf(
            "cache-control" to "private, no-store",
            "referrer-policy" to "no-referrer",
            "x-content-type-options" to "nosniff",
            "content-security-policy" to "sandbox; default-src 'none'",
        )

        /** The last path segment of [url] reduced to `[A-Za-z0-9._-]`, at most 80 characters, `media` when nothing is left. */
        fun displayName(url: String): String {
            val path = url.toHttpUrlOrNull()?.encodedPath.orEmpty()
            val last = path.split('/').lastOrNull { it.isNotEmpty() }.orEmpty()
            val clean = last.filter { (it.isLetterOrDigit() && it.code < 128) || it == '.' || it == '_' || it == '-' }.take(80)
            return if (clean.isEmpty() || clean.all { it == '.' }) "media" else clean
        }

        /** Rewrites an HLS playlist so every URI (media lines and `URI="..."` attributes) goes through [mint]. */
        /**
         * Every URI of an HLS playlist (lines and `URI="..."` attributes) through [mint]; null when one does not resolve to http(s)
         * (`data:`, `file:`, `skd:`… are never handed to the player, as Link's playlist rewriting refuses them too).
         */
        fun rewritePlaylist(text: String, playlistUrl: HttpUrl, mint: (HttpUrl) -> String): String? {
            val out = text.lines().let { if (text.endsWith('\n')) it.dropLast(1) else it }.map { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> line
                    trimmed.startsWith('#') -> {
                        val sb = StringBuilder()
                        var rest = line
                        while (true) {
                            val i = rest.indexOf("URI=\"")
                            if (i < 0) break
                            sb.append(rest, 0, i + 5)
                            val tail = rest.substring(i + 5)
                            val j = tail.indexOf('"')
                            if (j < 0) {
                                rest = tail
                                break
                            }
                            val value = tail.substring(0, j)
                            sb.append(playlistUrl.resolve(value)?.let(mint) ?: return null)
                            rest = tail.substring(j)
                        }
                        sb.append(rest).toString()
                    }
                    else -> playlistUrl.resolve(trimmed)?.let(mint) ?: return null
                }
            }
            return out.joinToString("\n") + if (text.endsWith('\n')) "\n" else ""
        }

        fun isPlaylist(contentType: String, url: String): Boolean =
            "mpegurl" in contentType.lowercase() || url.substringBefore('?').substringBefore('#').lowercase().endsWith(".m3u8")

        fun cacheable(contentType: String): Boolean {
            val ct = contentType.lowercase()
            return ct.startsWith("image/") || ct.startsWith("text/vtt") || ct.startsWith("application/octet-stream") ||
                ct.startsWith("application/x-subrip") || ct.startsWith("video/mp2t")
        }

        /** `bytes=<a>-<b>` with optional ends (classic `^bytes=\d*-\d*$`). */
        fun validRange(r: String): Boolean {
            val spec = r.removePrefix("bytes=").takeIf { it != r } ?: return false
            val dash = spec.indexOf('-')
            if (dash < 0) return false
            val a = spec.substring(0, dash)
            val b = spec.substring(dash + 1)
            return a.all { it.isDigit() } && b.all { it.isDigit() } && !(a.isEmpty() && b.isEmpty())
        }
    }
}

/** On-disk cache of small proxied objects (per run: leftovers of a previous run are removed). */
class MediaCache(serverDir: File) {
    private val dir = File(serverDir, MediaProxy.CACHE_DIR).also { it.deleteRecursively() }
    private val entries = LinkedHashMap<String, Triple<String, Long, Long>>()
    private var bytes = 0L

    /** `(entries, bytes)` still valid. */
    @Synchronized
    fun stats(): Pair<Int, Long> {
        val now = ExtAuth.nowUnix()
        val live = entries.values.filter { it.third > now }
        return live.size to live.sumOf { it.second }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        bytes = 0
        dir.deleteRecursively()
    }

    suspend fun get(key: String): Pair<String, ByteArray>? {
        val ct = synchronized(this) { entries[key]?.takeIf { it.third > ExtAuth.nowUnix() }?.first } ?: return null
        val data = withContext(Dispatchers.IO) { runCatching { File(dir, key).readBytes() }.getOrNull() } ?: return null
        return ct to data
    }

    suspend fun put(key: String, contentType: String, data: ByteArray) {
        if (data.size > MediaProxy.MAX_CACHED_OBJECT) return
        val ok = withContext(Dispatchers.IO) { runCatching { dir.mkdirs(); File(dir, key).writeBytes(data) }.isSuccess }
        if (!ok) return
        val evict = mutableListOf<String>()
        synchronized(this) {
            entries.remove(key)?.let { bytes -= it.second }
            bytes += data.size
            entries[key] = Triple(contentType, data.size.toLong(), ExtAuth.nowUnix() + MediaProxy.CACHE_TTL_SECS)
            while (bytes > MediaProxy.MAX_CACHE_BYTES && entries.isNotEmpty()) {
                val k = entries.keys.first()
                entries.remove(k)?.let { bytes -= it.second }
                evict += k
            }
        }
        withContext(Dispatchers.IO) { evict.forEach { File(dir, it).delete() } }
    }

    companion object {
        fun key(url: String, headers: List<Pair<String, String>>): String =
            ExtAuth.tokenHash(url + "\u0000" + headers.sortedWith(compareBy({ it.first }, { it.second })).joinToString("\u0000") { "${it.first}:${it.second}" })
    }
}
