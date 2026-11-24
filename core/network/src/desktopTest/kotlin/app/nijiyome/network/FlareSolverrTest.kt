// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.network

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FlareSolverr (Settings › Red): a fake instance on loopback and a source behind a Cloudflare-style challenge. */
class FlareSolverrTest {
    private val fsCalls = CopyOnWriteArrayList<String>()
    private val siteCalls = CopyOnWriteArrayList<RecordedRequest>()

    /** The source: a challenge page unless the request carries the clearance cookie and the solver's User-Agent. */
    private val site = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                siteCalls += request
                val cleared = request.headers["Cookie"]?.contains("cf_clearance=abc") == true && request.headers["User-Agent"] == "SolverUA/1"
                return if (cleared) {
                    MockResponse.Builder().code(200).body("episode list").build()
                } else {
                    MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").addHeader("Server", "cloudflare")
                        .addHeader("Content-Type", "text/html").body("<html>Just a moment...</html>").build()
                }
            }
        }
        start()
    }

    /** A fake FlareSolverr: `GET /` says it is ready, `POST /v1` returns a clearance for the asked URL. */
    private val fs = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "GET") {
                    return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                        .body("""{"msg":"FlareSolverr is ready!","version":"3.3.21","userAgent":"x"}""").build()
                }
                fsCalls += request.body?.utf8().orEmpty()
                return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(
                    """{"status":"ok","message":"Challenge solved!","solution":{"url":"x","status":200,
                       "cookies":[{"name":"cf_clearance","value":"abc"},{"name":"bad;","value":"x"}],"userAgent":"SolverUA/1"}}""",
                ).build()
            }
        }
        start()
    }

    @AfterTest
    fun stop() {
        site.close()
        fs.close()
    }

    private fun client(cache: ClearanceCache, endpoint: String? = fs.url("/").toString().trimEnd('/')) =
        OkHttpClient.Builder().addInterceptor(FlareSolverrInterceptor({ endpoint }, cache)).callTimeout(20, TimeUnit.SECONDS).build()

    @Test
    fun aChallengeIsSolvedOnceAndTheClearanceReused() {
        val cache = ClearanceCache()
        val c = client(cache)
        val url = site.url("/anime/1").toString()
        assertEquals("episode list", c.newCall(Request.Builder().url(url).header("User-Agent", "Ext/1").build()).execute().use { it.body.string() })
        assertEquals(1, fsCalls.size)
        assertTrue("\"cmd\":\"request.get\"" in fsCalls.single() && url in fsCalls.single())
        // the next request to that host goes straight with the clearance (no challenge, no second solve)
        assertEquals("episode list", c.newCall(Request.Builder().url(site.url("/anime/2")).build()).execute().use { it.body.string() })
        assertEquals(1, fsCalls.size)
        assertEquals(3, siteCalls.size) // challenge, retry, cleared request
    }

    @Test
    fun withoutAnEndpointTheChallengeIsReturnedAsIs() {
        val c = client(ClearanceCache(), endpoint = null)
        assertEquals(403, c.newCall(Request.Builder().url(site.url("/x")).build()).execute().use { it.code })
        assertEquals(0, fsCalls.size)
    }

    @Test
    fun aFailedSolveReturnsTheChallengeResponse() {
        val c = OkHttpClient.Builder().addInterceptor(
            FlareSolverrInterceptor({ "http://127.0.0.1:9" }, ClearanceCache(), solver = { _, _ -> Result.failure(java.io.IOException("down")) }),
        ).build()
        assertEquals(403, c.newCall(Request.Builder().url(site.url("/x")).build()).execute().use { it.code })
    }

    @Test
    fun theSolveGoesThroughTheOutboundProxy() {
        val proxy = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", 9050, "alice", "pw")
        val body = FlareSolverr.solveBody("https://site/x", 60_000, proxy)
        assertTrue("\"proxy\":{\"url\":\"socks5://127.0.0.1:9050\",\"username\":\"alice\",\"password\":\"pw\"}" in body, body)
        assertTrue("proxy" !in FlareSolverr.solveBody("https://site/x", 60_000, null))
        // the fake FlareSolverr receives it
        val r = FlareSolverr.solve(fs.url("/").toString().trimEnd('/'), "https://site/x", proxy = ProxyEndpoint(ProxyKind.HTTP, "proxy.example", 3128))
        assertTrue(r.isSuccess)
        assertTrue("\"url\":\"http://proxy.example:3128\"" in fsCalls.last(), fsCalls.last())
    }

    @Test
    fun clearancesArePerHostAndExpire() {
        var now = 0L
        val cache = ClearanceCache { now }
        cache.put("Example.com", Clearance("cf_clearance=a", "UA"))
        assertEquals("cf_clearance=a", cache.get("example.com")?.cookieHeader)
        assertNull(cache.get("other.com"))
        now = FlareSolverr.CLEARANCE_TTL_MS + 1
        assertNull(cache.get("example.com"))
    }

    @Test
    fun challengesAreRecognised() {
        val h = { m: Map<String, String> -> { n: String -> m[n] } }
        assertTrue(FlareSolverr.isChallenge(403, h(mapOf("cf-mitigated" to "challenge"))))
        assertTrue(FlareSolverr.isChallenge(503, h(mapOf("server" to "cloudflare", "content-type" to "text/html; charset=UTF-8"))))
        assertTrue(!FlareSolverr.isChallenge(200, h(mapOf("cf-mitigated" to "challenge"))))
        assertTrue(!FlareSolverr.isChallenge(503, h(emptyMap())))
        assertNull(FlareSolverr.parseSolution("""{"status":"error","message":"timeout"}"""))
        assertEquals("a=1", FlareSolverr.parseSolution("""{"status":"ok","solution":{"cookies":[{"name":"a","value":"1"}],"userAgent":"U"}}""")?.cookieHeader)
    }

    @Test
    fun probeChecksTheEndpoint() {
        val ok = FlareSolverr.probe(fs.url("/").toString())
        assertIs<ProbeResult.Ok>(ok)
        assertEquals("3.3.21", ok.detail)
        assertIs<ProbeResult.Failed>(FlareSolverr.probe(site.url("/").toString()))
        assertIs<ProbeResult.Failed>(FlareSolverr.probe("ftp://x"))
        assertNull(FlareSolverr.normalize("http://u:p@h:8191"))
        assertEquals("http://127.0.0.1:8191", FlareSolverr.normalize(" http://127.0.0.1:8191/ "))
    }
}
