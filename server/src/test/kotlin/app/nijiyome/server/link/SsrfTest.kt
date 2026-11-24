// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of Tatakai Next tk-link/tests/ssrf.rs (AGPL-3.0, same author): the SSRF table of review E6 against the loopback fetcher.
package app.nijiyome.server.link

import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.Resolver
import app.nijiyome.server.net.SafeHttp
import app.nijiyome.server.net.TrustedOrigin
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SsrfTest {
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() = open.reversed().forEach { runCatching { it.close() } }

    private fun fetcher(http: SafeHttp): Fetcher = Fetcher.start(http).also { f -> open += AutoCloseable { f.stop() } }

    private fun stub(responder: (String) -> Canned) = Stub(responder).also { open += it }

    private fun strict() = fetcher(SafeHttp(NetPolicy.linkSource(true), MapResolver(emptyMap())))

    private fun spec(url: HttpUrl) = SourceSpec(url, emptyList())

    private fun b64(u: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(u.toByteArray())

    private fun outcome(block: suspend () -> Unit): FetchException.Kind? = runBlocking {
        try {
            block()
            null
        } catch (e: FetchException) {
            e.kind
        }
    }

    /** The validation of a URL string (an OkHttp [HttpUrl] cannot hold `ftp:` / `file:`: those are refused before any token). */
    private fun validate(f: Fetcher, target: String): FetchException.Kind? {
        val url = when (val t = app.nijiyome.server.net.Target.parse(target)) {
            is app.nijiyome.server.net.Target.Http -> t.url
            else -> return FetchException.Kind.DENIED
        }
        val token = f.register(spec(url))
        return outcome { f.validate(token, url) }
    }

    @Test
    fun privateLoopbackLinkLocalAndOddTargetsAreDeniedBeforeAnyConnection() {
        val f = strict()
        for (target in listOf(
            "http://127.0.0.1/a.m3u8", "http://127.1.2.3:8080/a.m3u8", "http://10.0.0.5/a.m3u8", "http://172.16.0.1/a.m3u8", "http://172.31.255.254/a.m3u8",
            "http://192.168.1.1/a.m3u8", "http://169.254.169.254/latest/meta-data/", "http://100.64.0.1/a.m3u8", "http://100.100.100.200/a.m3u8", "http://0.0.0.0/a.m3u8",
            "http://224.0.0.1/a.m3u8", "http://[::1]/a.m3u8", "http://[fd00::1]/a.m3u8", "http://[fe80::1]/a.m3u8", "http://[::ffff:10.0.0.1]/a.m3u8",
            "ftp://example.com/a.m3u8", "file:///etc/passwd", "gopher://example.com/", "https://example.com:22/a.m3u8", "https://example.com:6379/", "https://user:pw@example.com/a.m3u8",
        )) {
            assertEquals(FetchException.Kind.DENIED, validate(f, target), target)
        }
    }

    @Test
    fun namesResolvingToPrivateAddressesAreDeniedIncludingMixedAnswers() {
        val m = mapOf(
            "intranet.test" to listOf(InetAddress.getByName("192.168.0.10")),
            "mixed.test" to listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.1.1.1")),
            "public.test" to listOf(InetAddress.getByName("93.184.216.34")),
        )
        val f = fetcher(SafeHttp(NetPolicy.linkSource(true), MapResolver(m)))
        for ((host, want) in listOf("intranet.test" to FetchException.Kind.DENIED, "mixed.test" to FetchException.Kind.DENIED, "public.test" to null, "nxdomain.test" to FetchException.Kind.UNAVAILABLE)) {
            assertEquals(want, validate(f, "https://$host/a.m3u8"), host)
        }
    }

    /** DNS rebinding: the name resolves to a permitted address once and to a private one afterwards. */
    @Test
    fun dnsRebindingPublicThenPrivateIsRejected() {
        val st = stub { text(200, mediaPlaylist(2)) }
        val n = AtomicInteger(0)
        val rebind = Resolver { _, _ -> listOf(InetAddress.getByName(if (n.getAndIncrement() == 0) "127.0.0.1" else "10.9.9.9")) }
        val f = fetcher(SafeHttp(NetPolicy.linkSource(true).copy(allowLoopback = true, allowedPorts = null), rebind))
        val url = "http://rebind.test:${st.port}/list.m3u8".toHttpUrl()
        val token = f.register(spec(url))
        assertNull(outcome { f.validate(token, url) })
        assertEquals(FetchException.Kind.DENIED, outcome { f.probeHls(token, url) })
        assertTrue(st.seen.isEmpty(), "no request may reach any host after the rebinding answer")
        assertTrue(f.wasDenied(token))
    }

    private fun fetchVia(entry: String): Pair<Int, ByteArray> = plainClient.get(entry).use { it.code to it.body.bytes() }

    @Test
    fun redirectToAPrivateIpIsRefusedAndFlagged() {
        val st = stub { redirect("http://10.0.0.1/secret") }
        val f = fetcher(lenientHttp())
        val url = "http://127.0.0.1:${st.port}/seg.ts".toHttpUrl()
        val token = f.register(spec(url))
        assertEquals(403, fetchVia(f.entryUrl(token, url, false)).first)
        assertTrue(f.wasDenied(token))
        val st2 = stub { redirect("http://169.254.169.254/latest/meta-data/iam") }
        val url2 = "http://127.0.0.1:${st2.port}/seg.ts".toHttpUrl()
        val token2 = f.register(spec(url2))
        assertEquals(403, fetchVia(f.entryUrl(token2, url2, false)).first)
    }

    @Test
    fun cookieGoesOnlyToTheInitialOriginAndIsStrippedOnCrossOriginRedirects() {
        val b = stub { Canned(200, emptyList(), "BBBB".toByteArray()) }
        val targetB = "http://127.0.0.1:${b.port}/seg0.ts"
        val a = stub { req ->
            val p = requestPath(req)
            when {
                p.startsWith("/list.m3u8") -> text(200, "#EXTM3U\n#EXTINF:2,\nseg-a.ts\n#EXTINF:2,\n$targetB\n#EXTINF:2,\nredir.ts\n#EXT-X-ENDLIST\n")
                p.startsWith("/redir.ts") -> redirect(targetB)
                else -> Canned(200, emptyList(), "AAAA".toByteArray())
            }
        }
        val f = fetcher(lenientHttp())
        val top = "http://127.0.0.1:${a.port}/list.m3u8".toHttpUrl()
        val token = f.register(SourceSpec(top, listOf("cookie" to "sid=SECRET", "referer" to "https://ref.example/", "user-agent" to "UA/1")))
        val (status, body) = fetchVia(f.entryUrl(token, top, true))
        assertEquals(200, status)
        val rewritten = body.decodeToString()
        assertFalse(rewritten.contains("127.0.0.1:"), "segment URIs must be loopback paths, got $rewritten")
        val segs = rewritten.lines().filter { "/s/" in it }
        assertEquals(3, segs.size)
        for (s in segs) assertEquals(200, fetchVia("http://${f.address}$s").first, s)
        val seenA = a.seen.map { it.lowercase() }
        for (req in seenA.filter { it.startsWith("get /seg-a.ts") || it.startsWith("get /redir.ts") || it.startsWith("get /list.m3u8") }) {
            assertTrue("cookie: sid=secret" in req, "same-origin requests keep the cookie: $req")
            assertTrue("referer: https://ref.example/" in req && "user-agent: ua/1" in req)
        }
        val seenB = b.seen.map { it.lowercase() }
        assertEquals(2, seenB.size, "segment fetched directly + via the redirect")
        for (req in seenB) {
            assertFalse("cookie" in req, "cookie leaked to another origin: $req")
            assertTrue("referer: https://ref.example/" in req, "non-credential headers still travel")
        }
    }

    @Test
    fun extXKeyWithAbsoluteUriToADisallowedHostIsRejected() {
        for (uri in listOf("http://10.0.0.9/key.bin", "http://169.254.169.254/key", "http://[fd00::5]/key", "file:///C:/secret.key", "data:text/plain;base64,AAAA")) {
            val body = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"$uri\"\n#EXTINF:2,\nseg0.ts\n#EXT-X-ENDLIST\n"
            val st = stub { text(200, body) }
            val f = fetcher(lenientHttp())
            val url = "http://127.0.0.1:${st.port}/list.m3u8".toHttpUrl()
            val token = f.register(spec(url))
            assertEquals(403, fetchVia(f.entryUrl(token, url, true)).first, uri)
            assertEquals(FetchException.Kind.DENIED, outcome { f.probeHls(token, url) }, uri)
        }
        val st = stub { text(200, "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"keys/k.bin\"\n#EXTINF:2,\nseg0.ts\n#EXT-X-ENDLIST\n") }
        val f = fetcher(lenientHttp())
        val url = "http://127.0.0.1:${st.port}/list.m3u8".toHttpUrl()
        val token = f.register(spec(url))
        val (status, body) = fetchVia(f.entryUrl(token, url, true))
        assertEquals(200, status)
        assertTrue(body.decodeToString().contains("URI=\"/u/$token/s/"))
    }

    @Test
    fun extensionPermissionsIntersectWithTheGenericPolicy() {
        val st = stub { text(200, "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"http://other.test/k\"\n#EXTINF:2,\nseg0.ts\n#EXT-X-ENDLIST\n") }
        val f = fetcher(lenientHttp(MapResolver(mapOf("other.test" to listOf(InetAddress.getByName("127.0.0.1"))))))
        val url = "http://127.0.0.1:${st.port}/list.m3u8".toHttpUrl()
        val t1 = f.register(SourceSpec(url, emptyList()))
        assertNull(outcome { f.probeHls(t1, url) })
        val t2 = f.register(SourceSpec(url, emptyList(), listOf("network:domain:127.0.0.1")))
        assertEquals(FetchException.Kind.DENIED, outcome { f.probeHls(t2, url) })
        val t3 = f.register(SourceSpec(url, emptyList(), listOf("network:domain:127.0.0.1", "network:domain:other.test")))
        assertNull(outcome { f.probeHls(t3, url) })
    }

    @Test
    fun playlistsWithForbiddenSchemesNestingOrExcessSizeAreDenied() {
        val cases = listOf(
            "data: segment" to "#EXTM3U\n#EXTINF:2,\ndata:video/mp2t;base64,AAAA\n",
            "file: segment" to "#EXTM3U\n#EXTINF:2,\nfile:///C:/Windows/win.ini\n",
            "file: map" to "#EXTM3U\n#EXT-X-MAP:URI=\"file:///C:/x.mp4\"\n#EXTINF:2,\na.ts\n",
            "master inside master" to "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nsub.m3u8\n",
            "playlist > 2 MiB" to "#EXTM3U\n" + "#EXTINF:1,\nsegment-name-long-enough.ts\n".repeat(80_000),
        )
        for ((name, body) in cases) {
            val st = stub { req ->
                if (requestPath(req).startsWith("/sub.m3u8")) text(200, "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nnested.m3u8\n") else text(200, body)
            }
            val f = fetcher(lenientHttp())
            val url = "http://127.0.0.1:${st.port}/list.m3u8".toHttpUrl()
            val token = f.register(spec(url))
            assertEquals(FetchException.Kind.DENIED, outcome { f.probeHls(token, url) }, name)
        }
    }

    @Test
    fun entryUrlsAreLoopbackOnlyAndUnknownTokensAre404() {
        val f = strict()
        val target = "https://cdn.example.com/a.m3u8".toHttpUrl()
        val token = f.register(spec(target))
        val e = f.entryUrl(token, target, true)
        assertTrue(e.startsWith("http://127.0.0.1:"))
        assertFalse(e.contains("cdn.example.com"), "FFmpeg never sees the external URL")
        assertEquals(404, fetchVia("http://${f.address}/u/not-a-token/s/${b64("https://x.example/")}").first)
        f.unregister(token)
        assertEquals(404, fetchVia(e).first)
        assertEquals(405, plainClient.call("POST", e).code)
    }

    /** BUG-023: this PC's own extension-server media proxy is a trusted origin; the rest of that address stays refused. */
    @Test
    fun theOwnExtensionServerMediaProxyIsATrustedOrigin() {
        val st = stub { req -> if ("/api/server/v1/media/t1/" in req) text(200, mediaPlaylist(2)) else text(200, "nope") }
        val f = strict()
        val trusted = listOf(TrustedOrigin(InetSocketAddress("127.0.0.1", st.port), "/api/server/v1/media/"))
        val url = "http://127.0.0.1:${st.port}/api/server/v1/media/t1/index.m3u8".toHttpUrl()
        val token = f.register(SourceSpec(url, emptyList(), listOf("network:domain:cdn.example"), trusted))
        assertNull(outcome { f.validate(token, url) })
        assertEquals(4.0, runBlocking { f.probeHls(token, url) })
        val seg = "http://127.0.0.1:${st.port}/api/server/v1/media/t1/seg0.ts"
        assertEquals(200, fetchVia("http://${f.address}/u/$token/s/${b64(seg)}.ts").first)
        val other = "http://127.0.0.1:${st.port}/api/server/v1/devices".toHttpUrl()
        val t2 = f.register(SourceSpec(other, emptyList(), null, trusted))
        assertEquals(FetchException.Kind.DENIED, outcome { f.validate(t2, other) })
        assertTrue(st.seen.all { "/api/server/v1/media/" in it })
    }

    /** Loopback upstream that streams [chunks] chunks of [size] bytes, one every [everyMs]. */
    private fun slowUpstream(chunks: Int, size: Int, everyMs: Long): Int {
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        open += server
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) {
                    runCatching {
                        s.use {
                            s.getInputStream().read(ByteArray(16 * 1024))
                            val out = s.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\ncontent-length: ${chunks * size}\r\ncontent-type: video/x-matroska\r\nconnection: close\r\n\r\n".toByteArray())
                            out.flush()
                            repeat(chunks) {
                                Thread.sleep(everyMs)
                                out.write(ByteArray(size) { 7 })
                                out.flush()
                            }
                        }
                    }
                }
            }
        }
        return server.localPort
    }

    /** 0.4.1: the policy timeout bounds each read of a progressive stream, not the whole body. */
    @Test
    fun aLongStreamIsNotCutByThePolicyTimeoutButAStalledOneIs() {
        val f = fetcher(SafeHttp(NetPolicy.linkSource(true).copy(allowLoopback = true, allowedPorts = null, timeoutMs = 700), MapResolver(emptyMap())))
        val port = slowUpstream(10, 4096, 250)
        val url = "http://127.0.0.1:$port/episode.mkv".toHttpUrl()
        val token = f.registerUnmetered(spec(url))
        plainClient.get(f.entryUrl(token, url, false)).use { r ->
            assertEquals(200, r.code)
            assertEquals(10 * 4096, r.body.bytes().size, "the whole stream arrives")
        }
        val stalled = slowUpstream(2, 4096, 2000)
        val url2 = "http://127.0.0.1:$stalled/episode.mkv".toHttpUrl()
        val t2 = f.register(spec(url2))
        val cut = runCatching {
            plainClient.newBuilder().readTimeout(10, TimeUnit.SECONDS).build().get(f.entryUrl(t2, url2, false)).use { it.body.bytes().size < 2 * 4096 }
        }.getOrDefault(true)
        if (!cut) fail("a stalled source must not hang the reader")
    }
}
