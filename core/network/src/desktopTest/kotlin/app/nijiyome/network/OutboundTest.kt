// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.network

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Settings › Red: the outbound proxy (HTTP and SOCKS5, with credentials), no direct fallback, local hosts bypassed, "Probar". */
class OutboundTest {
    private val closeables = mutableListOf<AutoCloseable>()

    @BeforeTest
    fun setUp() {
        Outbound.install()
        Outbound.proxy = null
    }

    @AfterTest
    fun tearDown() {
        Outbound.proxy = null
        closeables.reversed().forEach { runCatching { it.close() } }
    }

    private fun client() = OkHttpClient.Builder().outbound().callTimeout(10, TimeUnit.SECONDS).build()

    /** A local HTTP proxy (MockWebServer receives absolute-form targets) that wants Basic `u:p` when [auth]. */
    private fun httpProxy(auth: Boolean): Pair<MockWebServer, MutableList<RecordedRequest>> {
        val seen = CopyOnWriteArrayList<RecordedRequest>()
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                if (auth && request.headers["Proxy-Authorization"] != okhttp3.Credentials.basic("u", "p")) {
                    return MockResponse.Builder().code(407).addHeader("Proxy-Authenticate", "Basic realm=\"test\"").build()
                }
                return MockResponse.Builder().code(200).body("via-proxy ${request.target}").build()
            }
        }
        s.start()
        closeables += s
        return s to seen
    }

    @Test
    fun httpProxyCarriesTheRequestAndItsCredentials() {
        val (proxy, seen) = httpProxy(auth = true)
        Outbound.proxy = ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", proxy.port, "u", "p")
        val body = client().newCall(Request.Builder().url("http://media.example/clip.mp4").build()).execute().use { it.body.string() }
        assertEquals("via-proxy http://media.example/clip.mp4", body)
        assertTrue(seen.size >= 2 && seen.last().headers["Proxy-Authorization"] == okhttp3.Credentials.basic("u", "p"))
    }

    @Test
    fun jvmDefaultSelectorRoutesClientsBuiltWithoutTheHook() {
        val (proxy, seen) = httpProxy(auth = false)
        Outbound.proxy = ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", proxy.port)
        // a plain client (Coil's, the player's) asks the JVM default selector, which is ours after install()
        val plain = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        val body = plain.newCall(Request.Builder().url("http://api.example/q").build()).execute().use { it.body.string() }
        assertEquals("via-proxy http://api.example/q", body)
        assertEquals(1, seen.size)
    }

    @Test
    fun socks5ProxyWithCredentialsResolvesNamesRemotely() {
        val target = MockWebServer().also { closeables += it }
        target.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(200).body("hello ${request.headers["Host"]}").build()
        }
        target.start()
        val socks = TestSocks5("alice", "s3cret") { _, _ -> InetSocketAddress("127.0.0.1", target.port) }.also { closeables += it }
        Outbound.proxy = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", socks.port, "alice", "s3cret")
        val body = client().newCall(Request.Builder().url("http://cdn.example:8080/seg.ts").build()).execute().use { it.body.string() }
        assertEquals("hello cdn.example:8080", body)
        // the host name went to the proxy as a name (no local DNS query), with the configured credentials
        assertEquals(listOf("cdn.example:8080"), socks.targets.toList())
        assertEquals(listOf("alice:s3cret"), socks.logins.toList())
    }

    @Test
    fun aProxyThatFailsNeverFallsBackToDirect() {
        val direct = MockWebServer().also { closeables += it }
        direct.start()
        val dead = ServerSocket(0).use { it.localPort } // nothing listens there any more
        Outbound.proxy = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", dead)
        // a remote name that would resolve to the direct server if the client ever left the proxy
        assertFailsWith<IOException> {
            client().newCall(Request.Builder().url("http://remote.example:${direct.port}/").build()).execute().close()
        }
        Outbound.proxy = ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", dead)
        assertFailsWith<IOException> {
            client().newCall(Request.Builder().url("http://remote.example:${direct.port}/").build()).execute().close()
        }
        assertEquals(0, direct.requestCount)
    }

    @Test
    fun thisPcAndTheLanAreReachedDirectly() {
        val proxy = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", 9)
        Outbound.proxy = proxy
        for (h in listOf("127.0.0.1", "localhost", "192.168.1.43", "10.0.0.2", "172.16.5.1", "169.254.1.1", "[::1]", "[fd00::1]", "phone.local")) {
            assertEquals(listOf(Proxy.NO_PROXY), OutboundProxySelector.select(URI("http://$h:8080/x")), h)
        }
        for (h in listOf("graphql.anilist.co", "8.8.8.8", "[2001:4860::8888]", "100.64.0.1.example")) {
            assertEquals(Proxy.Type.SOCKS, OutboundProxySelector.select(URI("https://$h/x")).single().type(), h)
        }
        Outbound.proxy = null
        assertFalse(OutboundProxySelector.select(URI("https://graphql.anilist.co/")).any { it.type() == Proxy.Type.SOCKS })
    }

    @Test
    fun probeReportsAWorkingAndABrokenProxy() {
        val (proxy, _) = httpProxy(auth = true)
        val ok = ProxyProbe.test(ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", proxy.port, "u", "p"), target = "http://probe.example/")
        assertIs<ProbeResult.Ok>(ok)
        val noAuth = ProxyProbe.test(ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", proxy.port), target = "http://probe.example/")
        assertIs<ProbeResult.Failed>(noAuth)
        val dead = ServerSocket(0).use { it.localPort }
        assertIs<ProbeResult.Failed>(ProxyProbe.test(ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", dead), target = "http://probe.example/", timeoutMs = 3_000))
        // the probe never changes the route the app uses
        assertEquals(null, Outbound.proxy)
    }

    @Test
    fun endpointValidationAndLogsHideCredentials() {
        assertTrue(ProxyEndpoint.valid("proxy.lan", 3128))
        assertFalse(ProxyEndpoint.valid("", 3128))
        assertFalse(ProxyEndpoint.valid("a b", 80))
        assertFalse(ProxyEndpoint.valid("user@host", 80))
        assertFalse(ProxyEndpoint.valid("host", 0))
        val e = ProxyEndpoint(ProxyKind.SOCKS5, "h", 1080, "alice", "s3cret")
        assertFalse("s3cret" in e.toString())
        assertEquals("socks5://h:1080", e.uri())
    }
}
