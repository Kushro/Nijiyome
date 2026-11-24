// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import app.nijiyome.model.Container
import app.nijiyome.network.Outbound
import app.nijiyome.network.ProxyEndpoint
import app.nijiyome.network.ProxyKind
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.mpv.DesktopMpvSessionFactory
import app.nijiyome.server.desktop.LoopbackMediaRoute
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.Resolver
import app.nijiyome.server.net.SafeHttp
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * link-servidor-7 end to end: the app's libmpv session factory (as the player builds it) plays an HTTP stream whose CDN refuses any
 * request without its Cookie and Referer, through the loopback media proxy; and, with an outbound SOCKS5 proxy, a plain stream whose
 * bytes all come through that proxy. Audio goes to `ao=null` and there is no video surface (`vo=null`), as in
 * DesktopMpvEngineIntegrationTest; `.claude/` runs it inside gamescope headless. Skipped without ffmpeg or the libmpv runtime.
 */
class LocalMediaProxyPlaybackTest {
    private val tmp: File = Files.createTempDirectory("nj-lmp").toFile()
    private val cleanup = mutableListOf<() -> Unit>()
    private val listener = object : PlaybackListener {
        override fun onProgress(positionMs: Long, durationMs: Long, force: Boolean) = Unit
    }

    @AfterTest
    fun tearDown() {
        Outbound.proxy = null
        cleanup.reversed().forEach { runCatching { it() } }
        tmp.deleteRecursively()
    }

    private val lo = InetAddress.getByName("127.0.0.1")

    /** `cdn.test` -> loopback (no real DNS in tests). */
    private val resolver = Resolver { host, _ -> if (host == "cdn.test") listOf(lo) else throw java.net.UnknownHostException(host) }

    @Test
    fun aStreamThatNeedsCookieAndRefererPlaysThroughTheLoopbackProxy() {
        val cdn = Cdn(makeClip(), needCookie = true)
        val route = LoopbackMediaRoute(File(tmp, "local"), enabled = { true }, outboundProxy = { false },
            upstream = SafeHttp(NetPolicy.extensionFetch().copy(allowLoopback = true), resolver))
        cleanup += { route.close() }
        play(route, "http://cdn.test:${cdn.port}/clip.mkv", mapOf("Cookie" to "session=s3cret", "Referer" to "https://site.example/"))
        assertTrue(cdn.requests.isNotEmpty())
        assertTrue(cdn.requests.all { it.second }, "every CDN request carried Cookie + Referer: ${cdn.requests}")
        println("CDN requests through the loopback media proxy: ${cdn.requests.size} (ranges: ${cdn.requests.map { it.first }})")
    }

    @Test
    fun withAnOutboundSocks5ProxyThePlainStreamGoesThroughIt() {
        val cdn = Cdn(makeClip(), needCookie = false)
        val socks = TestSocks5 { _, _ -> InetSocketAddress(lo, cdn.port) }
        cleanup += { socks.close() }
        Outbound.install()
        val endpoint = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", socks.port)
        Outbound.proxy = endpoint
        val route = LoopbackMediaRoute(File(tmp, "local2"), enabled = { false }, outboundProxy = { Outbound.proxy != null },
            upstream = SafeHttp(NetPolicy.extensionFetch().copy(allowLoopback = true), resolver, outboundProxy = { endpoint.javaProxy() }))
        cleanup += { route.close() }
        play(route, "http://cdn.test:${cdn.port}/clip.mkv", emptyMap())
        assertTrue(socks.targets.isNotEmpty() && socks.targets.all { it == "cdn.test:${cdn.port}" }, "${socks.targets}")
        assertTrue(cdn.requests.size <= socks.targets.size, "no CDN request outside the SOCKS5 proxy")
        println("SOCKS5 connections for the stream: ${socks.targets.size}")
    }

    private fun play(route: LoopbackMediaRoute, url: String, headers: Map<String, String>) {
        val factory = DesktopMpvSessionFactory(cacheDir = File(tmp, "cache"), mainDispatcher = Dispatchers.Main, extraOptions = mapOf("ao" to "null"), mediaRoute = route)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        cleanup += { scope.cancel() }
        runBlocking(Dispatchers.Main) {
            val session = try {
                factory.create(scope, listener, remote = false)
            } catch (t: Throwable) {
                assumeNoException("libmpv runtime not loadable here", t)
                error("unreachable")
            }
            try {
                session.load(PlayableSource(url, headers, Container.PROGRESSIVE, "clip"), startMs = 0)
                val s = session.await("playing past 1 s") { it.started && it.positionMs > 1_000 }
                assertTrue(s.error == null)
            } finally {
                session.release()
            }
        }
    }

    private suspend fun PlayerSession.await(what: String, timeoutMs: Long = 20_000, p: (PlaybackState) -> Boolean): PlaybackState =
        withTimeoutOrNull(timeoutMs) { state.first(p) } ?: fail("timed out waiting for $what: ${state.value}")

    /** 4 s of testsrc2 (320x240, no audio) in Matroska; skipped without ffmpeg. */
    private fun makeClip(): File {
        val bin = System.getenv("NIJIYOME_FFMPEG") ?: "ffmpeg"
        val ok = runCatching { ProcessBuilder(bin, "-version").redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() == 0 } }.getOrDefault(false)
        assumeTrue("ffmpeg not available", ok)
        val out = File(tmp, "clip.mkv")
        val p = ProcessBuilder(bin, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=24:duration=4",
            "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p", out.absolutePath).redirectErrorStream(true).start()
        p.inputStream.readBytes()
        assumeTrue("ffmpeg could not make the clip", p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0 && out.length() > 0)
        return out
    }

    /** The CDN: serves the clip with byte ranges; with [needCookie], 403 unless Cookie + Referer are the source's. */
    private inner class Cdn(clip: File, needCookie: Boolean) {
        /** (range, had the headers) of each request. */
        val requests = CopyOnWriteArrayList<Pair<String, Boolean>>()
        private val bytes = clip.readBytes()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                val good = ex.requestHeaders.getFirst("Cookie") == "session=s3cret" && ex.requestHeaders.getFirst("Referer") == "https://site.example/"
                val range = ex.requestHeaders.getFirst("Range").orEmpty()
                requests += range to good
                if (needCookie && !good) {
                    ex.sendResponseHeaders(403, -1)
                    ex.close()
                    return@createContext
                }
                val from = Regex("bytes=(\\d+)-").find(range)?.groupValues?.get(1)?.toInt()
                ex.responseHeaders.add("Content-Type", "video/x-matroska")
                ex.responseHeaders.add("Accept-Ranges", "bytes")
                val start = (from ?: 0).coerceIn(0, bytes.size)
                if (from != null) {
                    ex.responseHeaders.add("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
                    ex.sendResponseHeaders(206, (bytes.size - start).toLong())
                } else {
                    ex.sendResponseHeaders(200, bytes.size.toLong())
                }
                ex.responseBody.use { runCatching { it.write(bytes, start, bytes.size - start) } }
            }
            start()
        }
        val port: Int get() = server.address.port

        init {
            cleanup += { server.stop(0) }
        }
    }
}
