// SPDX-License-Identifier: AGPL-3.0-or-later
// After Tatakai Next tk-app/src/live/server.rs (needs_local_proxy, local_stream; AGPL-3.0, same author).
package app.nijiyome.server.desktop

import app.nijiyome.model.LocalMediaRoute
import app.nijiyome.model.LocalRouteUnavailable
import app.nijiyome.model.RoutedStream
import app.nijiyome.network.Outbound
import app.nijiyome.server.ext.EmptyCatalog
import app.nijiyome.server.ext.ExtServer
import app.nijiyome.server.ext.ExtServerConfig
import app.nijiyome.server.ext.stripCredentials
import app.nijiyome.server.net.SafeHttp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

/**
 * `server.cors-proxy-local` of Tatakai Next for this PC's own player: a stream whose headers mpv would send to EVERY request of the
 * stream (segments on other CDNs, key servers) — `Cookie`, `Authorization`, `Origin` — or any stream while an outbound proxy is set
 * (mpv has no SOCKS support), is rewritten to a ticket of a loopback-only media proxy (an extension server on 127.0.0.1, its own
 * state under `<data>/server/local`, started the first time it is needed, also when the LAN extension server is off). The proxy sends
 * the headers upstream per origin, rewrites HLS playlists, passes Range through and solves Cloudflare challenges with FlareSolverr.
 */
class LoopbackMediaRoute(
    private val dir: File,
    /** Settings › Red › "Proxy local de medios". */
    private val enabled: () -> Boolean,
    /** Whether an outbound proxy is set (every stream must then go through the loopback proxy, never directly). */
    private val outboundProxy: () -> Boolean = { Outbound.proxy != null },
    /** Test hook: the proxy's upstream client. */
    private val upstream: SafeHttp? = null,
) : LocalMediaRoute, AutoCloseable {
    @Volatile
    private var server: ExtServer? = null

    @Volatile
    private var failed: String? = null

    override fun warm() {
        if (server != null || !(enabled() || outboundProxy())) return
        thread(name = "nijiyome-local-media-proxy", isDaemon = true) { runCatching { ensure() } }
    }

    @Synchronized
    private fun ensure(): ExtServer {
        server?.let { return it }
        val s = ExtServer.start(
            ExtServerConfig(dir = dir, port = 0, loopbackOnly = true, requirePairing = true, proxyEnabled = true, upstream = upstream),
            EmptyCatalog,
        )
        server = s
        failed = null
        return s
    }

    /** The loopback base (`http://127.0.0.1:<port>/api/server/v1/media/`), null while it is not running (tests). */
    fun base(): String? = server?.localMediaBase()

    override fun route(url: String, headers: Map<String, String>, subtitles: List<String>): RoutedStream? {
        val proxied = outboundProxy()
        if (!isRemoteHttp(url)) return null
        if (!needsLocalProxy(headers, proxied) || !(enabled() || proxied)) return null
        val s = try {
            ensure()
        } catch (e: Exception) {
            failed = e.message
            // without an outbound proxy the stream plays directly as before; with one, never (that would leave the proxy)
            if (proxied) throw LocalRouteUnavailable("loopback media proxy did not start: ${e.message}")
            return null
        }
        val list = headers.entries.map { it.key to it.value }
        val subs = subtitles.filter(::isRemoteHttp).associateWith { sub -> s.localMediaUrl(sub, subtitleHeaders(url, sub, list)) }
        return RoutedStream(s.localMediaUrl(url, list), emptyMap(), subs)
    }

    override fun close() {
        runCatching { server?.stop() }
        server = null
    }

    companion object {
        /**
         * Whether mpv should get [headers] through the loopback proxy instead of `http-header-fields` (Tatakai Next
         * `needs_local_proxy`): credentials and Origin would leak across origins or break CORS-checked CDNs; with an outbound proxy
         * every stream goes through it. Pure.
         */
        fun needsLocalProxy(headers: Map<String, String>, outboundProxy: Boolean): Boolean =
            outboundProxy || headers.keys.any { it.lowercase(Locale.ROOT) in setOf("cookie", "authorization", "origin") }

        /** An http(s) URL of a host outside this PC and the LAN (torrent streams, Link and local files are left alone). */
        fun isRemoteHttp(url: String): Boolean {
            val u = url.toHttpUrlOrNull() ?: return false
            return !Outbound.isLocal(u.host)
        }

        /** A subtitle on the video's origin gets its headers; one elsewhere gets them without credentials (Tatakai Next BUG-101). */
        fun subtitleHeaders(videoUrl: String, subUrl: String, headers: List<Pair<String, String>>): List<Pair<String, String>> {
            val a = videoUrl.toHttpUrlOrNull()
            val b = subUrl.toHttpUrlOrNull()
            val same = a != null && b != null && a.scheme == b.scheme && a.host.equals(b.host, true) && a.port == b.port
            return if (same) headers else stripCredentials(headers)
        }
    }
}
