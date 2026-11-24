// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/fetcher.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import app.nijiyome.server.http.KtorServer
import app.nijiyome.server.net.AddressRules
import app.nijiyome.server.net.NetError
import app.nijiyome.server.net.SafeHttp
import app.nijiyome.server.net.SafeRequest
import app.nijiyome.server.net.TrustedOrigin
import app.nijiyome.server.net.ipLiteral
import app.nijiyome.server.net.originOf
import app.nijiyome.server.net.readCapped
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** The policy refuses the URL (`403 url_source_denied`) or the upstream is unreachable / answers badly (`503 source_unavailable`). */
class FetchException(val kind: Kind) : Exception(kind.name) {
    enum class Kind { DENIED, UNAVAILABLE }
}

class SourceSpec(
    val url: HttpUrl,
    /** Already sanitised with `sanitizeHeaders`. */
    val headers: List<Pair<String, String>>,
    /** `network:domain:*` permissions of the extension, when the PC has it installed. */
    val extensionPermissions: List<String>? = null,
    /** Exact local endpoints this source may reach (`LinkHost.trustedMediaOrigins`). */
    val trusted: List<TrustedOrigin> = emptyList(),
)

/**
 * Loopback SSRF fetcher of the `url` source: FFmpeg NEVER receives the external URL. It reads
 * `http://127.0.0.1:<port>/u/<token>/...` and this server fetches upstream through [SafeHttp] (IP validation incl. DNS rebinding,
 * manual redirects, cross-origin Cookie / Authorization strip), rewriting HLS playlists so segments, keys and maps go through here
 * too. Paths: `/u/<token>/p/<depth>/<b64url target>` (playlist, rewritten), `/u/<token>/s/<b64url target>` (bytes) and
 * `/f/<token>` (one local library file, with Range support; its path never reaches FFmpeg).
 */
class Fetcher private constructor(private val http: SafeHttp) {
    private class Token(
        val http: SafeHttp,
        val initial: HttpUrl,
        val headers: List<Pair<String, String>>,
        /** [MAX_BYTES_PER_SESSION], or null for this PC's own torrent engine stream. */
        val quota: Long?,
    ) {
        val bytes = AtomicLong(0)
        val slots = Semaphore(MAX_CONCURRENT_PER_SESSION)
        val denied = AtomicBoolean(false)

        /** Cookie only travels to the initial origin; a request Range replaces any given one. */
        fun headersFor(target: HttpUrl, range: String?): List<Pair<String, String>> {
            val same = originOf(target) == originOf(initial)
            val h = headers.filter { same || !it.first.equals("cookie", true) }.toMutableList()
            if (range != null) {
                h.removeAll { it.first.equals("range", true) }
                h += "range" to range
            }
            return h
        }
    }

    private val tokens = ConcurrentHashMap<String, Token>()
    private val files = ConcurrentHashMap<String, File>()
    private lateinit var server: KtorServer

    val port: Int get() = server.port
    val address: String get() = "127.0.0.1:$port"

    /** Where it listens (loopback only; checked by the security tests). */
    internal val boundAddresses: List<java.net.InetSocketAddress> get() = server.addresses

    fun register(spec: SourceSpec): String = registerWithQuota(spec, MAX_BYTES_PER_SESSION)

    /** [register] without the byte quota, for this PC's own torrent engine (a whole file is read several times per session). */
    fun registerUnmetered(spec: SourceSpec): String = registerWithQuota(spec, null)

    private fun registerWithQuota(spec: SourceSpec, quota: Long?): String {
        val token = newToken()
        var h = http
        spec.extensionPermissions?.let { perms -> h = h.withHostFilter { host -> AddressRules.hostMatchesPermissions(host, perms) } }
        if (spec.trusted.isNotEmpty()) h = h.withTrustedOrigins(spec.trusted)
        tokens[token] = Token(h, spec.url, spec.headers, quota)
        return token
    }

    fun unregister(token: String) {
        tokens.remove(token)
        files.remove(token)
    }

    fun registerFile(path: File): String = newToken().also { files[it] = path }

    fun fileUrl(token: String): String = "http://$address/f/$token"

    /** True once any request of this source was refused by the policy. */
    fun wasDenied(token: String): Boolean = tokens[token]?.denied?.get() == true

    fun bytesServed(token: String): Long = tokens[token]?.bytes?.get() ?: 0

    /** The loopback URL FFmpeg opens: an HLS playlist (rewritten) or an opaque stream. */
    fun entryUrl(token: String, target: HttpUrl, hls: Boolean): String =
        if (hls) "http://$address/u/$token/p/0/${encExt(target, "m3u8")}" else "http://$address/u/$token/s/${enc(target)}"

    /** Validates the URL against the policy (scheme, port, DNS answers) before a session is created. */
    suspend fun validate(token: String, target: HttpUrl) {
        val t = tokens[token] ?: throw FetchException(FetchException.Kind.UNAVAILABLE)
        try {
            t.http.resolveValidated(target)
        } catch (e: NetError) {
            throw e.toFetch()
        }
    }

    /** Loads the top playlist (and, for a master, its first variant) to learn the duration and fail early. */
    suspend fun probeHls(token: String, target: HttpUrl): Double? {
        val t = tokens[token] ?: throw FetchException(FetchException.Kind.UNAVAILABLE)
        val top = loadPlaylist(t, token, target, 0)
        if (!top.isMaster) return top.durationSecs.takeIf { it > 0 }
        val first = top.text.lineSequence().firstOrNull { "/p/1/" in it }?.substringAfter("/p/1/")?.substringBefore('"')?.let(::dec) ?: return null
        val media = loadPlaylist(t, token, first, 1)
        return media.durationSecs.takeIf { it > 0 }
    }

    fun stop() {
        server.stop(0, 500)
        tokens.clear()
        files.clear()
    }

    private suspend fun loadPlaylist(t: Token, token: String, target: HttpUrl, depth: Int): Playlist.Rewritten {
        val resp = try {
            t.http.send(SafeRequest.get(target, t.headersFor(target, null)))
        } catch (e: NetError) {
            throw e.toFetch().also { if (it.kind == FetchException.Kind.DENIED) t.denied.set(true) }
        }
        if (!resp.response.isSuccessful) {
            resp.response.close()
            throw FetchException(FetchException.Kind.UNAVAILABLE)
        }
        val body = try {
            withContext(Dispatchers.IO) { readCapped(resp.response, Playlist.MAX_PLAYLIST_BYTES) }
        } catch (e: NetError) {
            throw if (e is NetError.TooLarge) FetchException(FetchException.Kind.DENIED).also { t.denied.set(true) } else e.toFetch()
        }
        return try {
            Playlist.rewrite(body, resp.finalUrl, depth) { u, kind -> mapUri(t, "/u/$token", depth, u, kind) }
        } catch (e: Playlist.RewriteException) {
            val f = when (e.reason) {
                Playlist.RewriteException.Reason.NOT_PLAYLIST, Playlist.RewriteException.Reason.NOT_UTF8, Playlist.RewriteException.Reason.BAD_URI ->
                    FetchException(FetchException.Kind.UNAVAILABLE)
                else -> FetchException(FetchException.Kind.DENIED)
            }
            if (f.kind == FetchException.Kind.DENIED) t.denied.set(true)
            throw f
        }
    }

    /** Validates one URI of a playlist (cheap, synchronous checks) and produces its loopback path. */
    private fun mapUri(t: Token, prefix: String, depth: Int, u: HttpUrl, kind: Playlist.UriKind): String {
        try {
            t.http.checkUrl(u)
        } catch (_: NetError) {
            throw Playlist.RewriteException(Playlist.RewriteException.Reason.DENIED)
        }
        // keys and init maps decide whether decryption can work at all: literal private addresses are refused here
        if (kind == Playlist.UriKind.KEY || kind == Playlist.UriKind.MAP) {
            val ip = ipLiteral(u.host)
            if (ip != null && !t.http.ipAllowed(ip) && !t.http.isTrusted(u)) throw Playlist.RewriteException(Playlist.RewriteException.Reason.DENIED)
        }
        return when (kind) {
            Playlist.UriKind.PLAYLIST -> "$prefix/p/${depth + 1}/${encExt(u, "m3u8")}"
            // FFmpeg >= 7 refuses HLS segments whose URL lacks a known extension (`allowed_segment_extensions`)
            Playlist.UriKind.SEGMENT -> "$prefix/s/${encExt(u, hlsExt(u, "ts"))}"
            Playlist.UriKind.MAP -> "$prefix/s/${encExt(u, hlsExt(u, "mp4"))}"
            Playlist.UriKind.KEY -> "$prefix/s/${enc(u)}"
        }
    }

    private suspend fun handle(call: ApplicationCall) {
        val method = call.request.httpMethod
        if (method != HttpMethod.Get && method != HttpMethod.Head) return call.status(HttpStatusCode.MethodNotAllowed)
        val seg = call.request.path().trimStart('/').split('/')
        if (seg.firstOrNull() == "f") {
            val path = seg.getOrNull(1)?.let { files[it] } ?: return call.status(HttpStatusCode.NotFound)
            return serveFile(call, path, call.request.header("Range"))
        }
        if (seg.firstOrNull() != "u") return call.status(HttpStatusCode.NotFound)
        val token = seg.getOrNull(1) ?: return call.status(HttpStatusCode.NotFound)
        val t = tokens[token] ?: return call.status(HttpStatusCode.NotFound)
        val (depth, target) = when (seg.getOrNull(2)) {
            "p" -> {
                val d = seg.getOrNull(3)?.toIntOrNull() ?: return call.status(HttpStatusCode.NotFound)
                d to (dec(seg.getOrNull(4) ?: return call.status(HttpStatusCode.NotFound)) ?: return call.status(HttpStatusCode.BadRequest))
            }
            "s" -> null to (dec(seg.getOrNull(3) ?: return call.status(HttpStatusCode.NotFound)) ?: return call.status(HttpStatusCode.BadRequest))
            else -> return call.status(HttpStatusCode.NotFound)
        }
        val acquired = withTimeoutOrNull(20_000) { t.slots.acquire() } != null
        if (!acquired) return call.status(HttpStatusCode.ServiceUnavailable)
        try {
            val quota = t.quota
            if (quota != null && t.bytes.get() >= quota) return call.status(HttpStatusCode.Forbidden)
            fun statusOf(e: FetchException) = if (e.kind == FetchException.Kind.DENIED) HttpStatusCode.Forbidden else HttpStatusCode.BadGateway
            if (depth != null) {
                val r = try {
                    loadPlaylist(t, token, target, depth)
                } catch (e: FetchException) {
                    return call.status(statusOf(e))
                }
                val body = r.text.toByteArray()
                t.bytes.addAndGet(body.size.toLong())
                return call.respondBytes(body, ContentType.parse("application/vnd.apple.mpegurl"))
            }
            val up = try {
                t.http.sendStream(SafeRequest.get(target, t.headersFor(target, call.request.header("Range"))))
            } catch (e: NetError) {
                val f = e.toFetch()
                if (f.kind == FetchException.Kind.DENIED) t.denied.set(true)
                return call.status(statusOf(f))
            }
            val resp = up.response
            resp.use {
                resp.header("Content-Range")?.let { call.response.header("Content-Range", it) }
                resp.header("Accept-Ranges")?.let { call.response.header("Accept-Ranges", it) }
                val ct = resp.header("Content-Type")?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                val length = resp.header("Content-Length")?.toLongOrNull()
                call.respondBytesWriter(ct, HttpStatusCode.fromValue(resp.code), length) {
                    val source = resp.body.source()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = withContext(Dispatchers.IO) { source.read(buf) }
                        if (n < 0) break
                        val total = t.bytes.addAndGet(n.toLong())
                        if (quota != null && total > quota) throw java.io.IOException("session byte quota exceeded")
                        writeFully(buf, 0, n)
                    }
                }
            }
        } finally {
            t.slots.release()
        }
    }

    /** `GET /f/<token>` with `Range: bytes=a-b` (single range). */
    private suspend fun serveFile(call: ApplicationCall, path: File, range: String?) {
        val len = withContext(Dispatchers.IO) { if (path.isFile) path.length() else -1L }
        if (len < 0) return call.status(HttpStatusCode.NotFound)
        var start = 0L
        var end = len - 1
        var partial = false
        val spec = range?.removePrefix("bytes=")?.takeIf { it != range }
        if (spec != null) {
            val dash = spec.indexOf('-')
            if (dash < 0) return call.status(HttpStatusCode.RequestedRangeNotSatisfiable)
            val a = spec.substring(0, dash)
            val b = spec.substring(dash + 1)
            if (a.isEmpty()) {
                val n = b.toLongOrNull() ?: return call.status(HttpStatusCode.RequestedRangeNotSatisfiable)
                start = (len - n).coerceAtLeast(0)
                end = len - 1
            } else {
                start = a.toLongOrNull() ?: return call.status(HttpStatusCode.RequestedRangeNotSatisfiable)
                end = if (b.isEmpty()) len - 1 else b.toLongOrNull() ?: return call.status(HttpStatusCode.RequestedRangeNotSatisfiable)
            }
            if (start >= len || end < start) return call.status(HttpStatusCode.RequestedRangeNotSatisfiable)
            end = minOf(end, len - 1)
            partial = true
        }
        val take = if (len == 0L) 0L else end - start + 1
        call.response.header("Accept-Ranges", "bytes")
        if (partial) call.response.header("Content-Range", "bytes $start-$end/$len")
        call.respondBytesWriter(ContentType.Application.OctetStream, if (partial) HttpStatusCode.PartialContent else HttpStatusCode.OK, take) {
            if (call.request.httpMethod == HttpMethod.Head) return@respondBytesWriter
            withContext(Dispatchers.IO) { RandomAccessFile(path, "r") }.use { raf ->
                raf.seek(start)
                var left = take
                val buf = ByteArray(64 * 1024)
                while (left > 0) {
                    val n = withContext(Dispatchers.IO) { raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt()) }
                    if (n < 0) break
                    writeFully(buf, 0, n)
                    left -= n
                }
            }
        }
    }

    companion object {
        const val MAX_CONCURRENT_PER_SESSION = 64
        const val MAX_BYTES_PER_SESSION: Long = 2L * 1024 * 1024 * 1024

        /** Extensions FFmpeg's HLS demuxer accepts for segments (`allowed_segment_extensions`, FFmpeg >= 7). */
        private val HLS_SEGMENT_EXTS = setOf("ts", "m4s", "mp4", "m4v", "m4a", "aac", "ac3", "eac3", "ec3", "mp3", "mkv", "mpegts", "cmfv", "cmfa", "fmp4", "vtt")

        /** Binds 127.0.0.1 on an ephemeral port. [http] carries the global policy (tests inject a lenient one). */
        fun start(http: SafeHttp): Fetcher {
            val f = Fetcher(http)
            f.server = KtorServer.start(listOf("127.0.0.1"), 0, writeTimeoutSecs = Int.MAX_VALUE, threads = 4) { f.handle(it) }
            return f
        }

        fun enc(u: HttpUrl): String = Base64.getUrlEncoder().withoutPadding().encodeToString(u.toString().toByteArray())

        /** `enc(u)` + `.ext` (base64url has no dot, so [dec] strips it unambiguously). */
        fun encExt(u: HttpUrl, ext: String): String = "${enc(u)}.$ext"

        fun dec(s: String): HttpUrl? {
            val b = s.substringBefore('.')
            val bytes = runCatching { Base64.getUrlDecoder().decode(b) }.getOrNull() ?: return null
            return bytes.decodeToString().toHttpUrlOrNull()
        }

        /** The extension a rewritten URL carries: the original one when FFmpeg accepts it, else [fallback]. */
        fun hlsExt(u: HttpUrl, fallback: String): String {
            val last = u.pathSegments.lastOrNull().orEmpty()
            val ext = last.substringAfterLast('.', "").lowercase()
            return if (last.contains('.') && ext in HLS_SEGMENT_EXTS) ext else fallback
        }

        private fun newToken(): String = UUID.randomUUID().toString().replace("-", "")

        private suspend fun ApplicationCall.status(s: HttpStatusCode) = respondBytes(ByteArray(0), ContentType.Application.OctetStream, s)
    }
}

internal fun NetError.toFetch(): FetchException =
    FetchException(if (isDenial) FetchException.Kind.DENIED else FetchException.Kind.UNAVAILABLE)
