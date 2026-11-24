// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.model.NetProxyKind
import app.nijiyome.model.NetTestResult
import app.nijiyome.model.SecretStore
import app.nijiyome.network.FlareSolverrInterceptor
import app.nijiyome.network.Outbound
import app.nijiyome.server.link.Canned
import app.nijiyome.server.link.MapResolver
import app.nijiyome.server.link.Stub
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.SafeHttp
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * link-servidor-7: the player's loopback media proxy against a CDN that insists on its headers (Referer + Cookie), with HLS whose
 * segments live on another origin; link-servidor-6: a Cloudflare challenge solved through a fake FlareSolverr; link-servidor-5:
 * Settings › Red saved and tested (credentials in the secret store).
 */
class LoopbackMediaRouteTest {
    private val open = mutableListOf<AutoCloseable>()
    private val dirs = mutableListOf<File>()
    private val lo = InetAddress.getByName("127.0.0.1")
    private val plain = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    @BeforeTest
    fun setUp() {
        Outbound.proxy = null
        Outbound.flareSolverr = null
        FlareSolverrInterceptor.shared.clear()
    }

    @AfterTest
    fun tearDown() {
        Outbound.proxy = null
        Outbound.flareSolverr = null
        open.reversed().forEach { runCatching { it.close() } }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun tmp(): File = Files.createTempDirectory("nj-lmr").toFile().also { dirs += it }

    private fun get(url: String): Pair<Int, String> = plain.newCall(Request.Builder().url(url).build()).execute().use { it.code to it.body.string() }

    private class MemorySecrets : SecretStore {
        val map = ConcurrentHashMap<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override suspend fun clear() = map.clear()
    }

    @Test
    fun theRuleFollowsTheOriginApp() {
        assertTrue(LoopbackMediaRoute.needsLocalProxy(mapOf("Cookie" to "a=b"), false))
        assertTrue(LoopbackMediaRoute.needsLocalProxy(mapOf("authorization" to "Bearer x"), false))
        assertTrue(LoopbackMediaRoute.needsLocalProxy(mapOf("Origin" to "https://site"), false))
        assertFalse(LoopbackMediaRoute.needsLocalProxy(mapOf("Referer" to "https://site/", "User-Agent" to "UA"), false))
        assertTrue(LoopbackMediaRoute.needsLocalProxy(emptyMap(), outboundProxy = true))
        assertTrue(LoopbackMediaRoute.isRemoteHttp("https://cdn.example/x.m3u8"))
        assertFalse(LoopbackMediaRoute.isRemoteHttp("http://127.0.0.1:8765/stream/abc")) // a torrent stream
        assertFalse(LoopbackMediaRoute.isRemoteHttp("https://192.168.1.20:47323/hls/x.m3u8")) // Link
        assertFalse(LoopbackMediaRoute.isRemoteHttp("file:///home/x.mkv"))
        val h = listOf("referer" to "https://site/", "cookie" to "s=1")
        assertEquals(h, LoopbackMediaRoute.subtitleHeaders("https://a.example/v.m3u8", "https://a.example/s.vtt", h))
        assertEquals(listOf("referer" to "https://site/"), LoopbackMediaRoute.subtitleHeaders("https://a.example/v.m3u8", "https://subs.example/s.vtt", h))
    }

    @Test
    fun aStreamThatNeedsItsHeadersPlaysThroughTheLoopbackProxy() {
        // the segments' CDN: plays only with the Referer; must never see the playlist's cookie
        val segs = Stub { req ->
            if ("referer: https://site.example/" in req.lowercase()) Canned(200, listOf("content-type" to "video/mp2t"), ByteArray(1000) { 7 })
            else Canned(403)
        }.also { open += it }
        // the playlist host: wants Referer + Cookie
        val origin = Stub { req ->
            val r = req.lowercase()
            when {
                "referer: https://site.example/" !in r || "cookie: session=s3cret" !in r -> Canned(403)
                else -> Canned(
                    200,
                    listOf("content-type" to "application/vnd.apple.mpegurl"),
                    "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2.0,\nhttp://segs.test:${segs.port}/seg0.ts\n#EXT-X-ENDLIST\n".toByteArray(),
                )
            }
        }.also { open += it }
        val resolver = MapResolver(mapOf("origin.test" to listOf(lo), "segs.test" to listOf(lo)))
        val upstream = SafeHttp(NetPolicy.extensionFetch().copy(allowLoopback = true), resolver)
        val route = LoopbackMediaRoute(tmp(), enabled = { true }, outboundProxy = { false }, upstream = upstream).also { open += it }
        val headers = mapOf("Referer" to "https://site.example/", "Cookie" to "session=s3cret", "User-Agent" to "UA/1")
        val playlistUrl = "http://origin.test:${origin.port}/v/index.m3u8"

        // a Referer alone stays direct (mpv sends it fine)
        assertNull(route.route(playlistUrl, mapOf("Referer" to "https://site.example/")))
        val routed = assertNotNull(route.route(playlistUrl, headers, listOf("http://origin.test:${origin.port}/subs/es.vtt")))
        assertTrue(routed.url.startsWith(assertNotNull(route.base())), routed.url)
        assertTrue(routed.headers.isEmpty(), "mpv gets no header fields")
        assertTrue(routed.subtitleUrls.values.single().startsWith(route.base()!!))

        // mpv's view: the playlist, rewritten so the segment also goes through the proxy
        val (code, body) = get(routed.url)
        assertEquals(200, code)
        val seg = body.lines().single { it.startsWith("http") }
        assertTrue(seg.startsWith(route.base()!!), seg)
        // the origin got its headers from the proxy
        assertTrue("cookie: session=s3cret" in origin.seen.last().lowercase())
        val (segCode, segBody) = get(seg)
        assertEquals(200, segCode)
        assertEquals(1000, segBody.length)
        assertTrue("referer: https://site.example/" in segs.seen.last().lowercase())
    }

    @Test
    fun crossOriginSegmentsLoseTheCookie() {
        val segs = Stub { Canned(200, listOf("content-type" to "video/mp2t"), ByteArray(10)) }.also { open += it }
        val origin = Stub {
            Canned(200, listOf("content-type" to "application/vnd.apple.mpegurl"), "#EXTM3U\n#EXTINF:2.0,\nhttp://segs.test:${segs.port}/a.ts\n#EXT-X-ENDLIST\n".toByteArray())
        }.also { open += it }
        val resolver = MapResolver(mapOf("origin.test" to listOf(lo), "segs.test" to listOf(lo)))
        val route = LoopbackMediaRoute(tmp(), enabled = { true }, outboundProxy = { false }, upstream = SafeHttp(NetPolicy.extensionFetch().copy(allowLoopback = true), resolver)).also { open += it }
        val routed = assertNotNull(route.route("http://origin.test:${origin.port}/i.m3u8", mapOf("Cookie" to "k=v", "Referer" to "https://r/")))
        val seg = get(routed.url).second.lines().single { it.startsWith("http") }
        assertEquals(200, get(seg).first)
        val sent = segs.seen.last().lowercase()
        assertTrue("referer: https://r/" in sent)
        assertFalse("cookie" in sent, sent)
    }

    @Test
    fun withAnOutboundProxyEveryStreamIsProxiedAndTheSwitchDoesNotMatter() {
        val route = LoopbackMediaRoute(tmp(), enabled = { false }, outboundProxy = { true }).also { open += it }
        assertNotNull(route.route("https://cdn.example/plain.mp4", emptyMap()))
        // without one and with the switch off, nothing is rewritten
        val off = LoopbackMediaRoute(tmp(), enabled = { false }, outboundProxy = { false }).also { open += it }
        assertNull(off.route("https://cdn.example/x.m3u8", mapOf("Cookie" to "a=b")))
    }

    @Test
    fun aCloudflareChallengeIsSolvedThroughFlareSolverr() {
        val solves = java.util.concurrent.atomic.AtomicInteger()
        val fs = Stub { req ->
            if (req.startsWith("POST /v1")) {
                solves.incrementAndGet()
                Canned(200, listOf("content-type" to "application/json"),
                    """{"status":"ok","solution":{"cookies":[{"name":"cf_clearance","value":"ok1"}],"userAgent":"SolverUA"}}""".toByteArray())
            } else {
                Canned(200, listOf("content-type" to "application/json"), """{"msg":"FlareSolverr is ready!","version":"3.3.21"}""".toByteArray())
            }
        }.also { open += it }
        val cdn = Stub { req ->
            val r = req.lowercase()
            if ("cf_clearance=ok1" in r && "user-agent: solverua" in r) Canned(200, listOf("content-type" to "video/mp4"), ByteArray(600_000) { 1 })
            else Canned(403, listOf("cf-mitigated" to "challenge", "content-type" to "text/html"), "<html>challenge</html>".toByteArray())
        }.also { open += it }
        Outbound.flareSolverr = "http://127.0.0.1:${fs.port}"
        val upstream = SafeHttp(NetPolicy.extensionFetch().copy(allowLoopback = true), MapResolver(mapOf("cdn.test" to listOf(lo))))
        val route = LoopbackMediaRoute(tmp(), enabled = { true }, outboundProxy = { false }, upstream = upstream).also { open += it }
        val routed = assertNotNull(route.route("http://cdn.test:${cdn.port}/ep1.mp4", mapOf("Cookie" to "site=1")))
        val (code, body) = get(routed.url)
        assertEquals(200, code)
        assertEquals(600_000, body.length)
        assertEquals(1, solves.get())
    }

    @Test
    fun networkSettingsKeepCredentialsInTheSecretStoreAndTestTheProxy() = runBlocking {
        val dir = tmp()
        val secrets = MemorySecrets()
        val proxy = Stub { Canned(200, emptyList(), "ok".toByteArray()) }.also { open += it }
        val net = DesktopNetwork(dir, secrets, probeTarget = "http://probe.test/").also { open += it }
        assertTrue(net.saveProxy(true, NetProxyKind.HTTP, " 127.0.0.1 ", proxy.port, "bob", "hunter2"))
        val file = File(dir, "network.json").readText()
        assertFalse("hunter2" in file || "bob" in file, file)
        assertEquals("hunter2", secrets.map[DesktopNetwork.KEY_PASS])
        assertEquals("bob", Outbound.proxy?.username)
        assertEquals(proxy.port, Outbound.proxy?.port)
        assertTrue(net.state.value.hasPassword)
        // "Probar" with the stored password (null = keep)
        assertIs<NetTestResult.Ok>(net.testProxy(NetProxyKind.HTTP, "127.0.0.1", proxy.port, "bob", null))
        assertIs<NetTestResult.Failed>(net.testProxy(NetProxyKind.HTTP, "bad host", 1, "", null))
        // keep the password when the field is left empty
        assertTrue(net.saveProxy(true, NetProxyKind.SOCKS5, "127.0.0.1", 1080, "bob", null))
        assertEquals("hunter2", Outbound.proxy?.password)
        // off: direct again, the settings survive a restart
        assertTrue(net.saveProxy(false, NetProxyKind.SOCKS5, "127.0.0.1", 1080, "bob", null))
        assertNull(Outbound.proxy)
        assertEquals(false, NetworkFile.load(NetworkFile.fileIn(dir)).proxyEnabled)
        // FlareSolverr: only http(s) addresses
        assertFalse(net.saveFlareSolverr("ftp://x"))
        assertTrue(net.saveFlareSolverr("http://127.0.0.1:8191/"))
        assertEquals("http://127.0.0.1:8191", Outbound.flareSolverr)
        assertTrue(net.saveFlareSolverr(""))
        assertNull(Outbound.flareSolverr)
    }
}
