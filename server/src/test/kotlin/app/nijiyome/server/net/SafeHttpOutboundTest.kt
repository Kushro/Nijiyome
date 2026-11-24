// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.net

import app.nijiyome.network.Outbound
import app.nijiyome.network.ProxyEndpoint
import app.nijiyome.network.ProxyKind
import app.nijiyome.server.link.Canned
import app.nijiyome.server.link.MapResolver
import app.nijiyome.server.link.Stub
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Settings › Red on the SSRF-safe client (Tatakai Next Upstream): the target is validated here, the proxy reaches it, no fallback. */
class SafeHttpOutboundTest {
    private val open = mutableListOf<AutoCloseable>()

    @BeforeTest
    fun setUp() {
        Outbound.install()
        Outbound.proxy = null
    }

    @AfterTest
    fun tearDown() {
        Outbound.proxy = null
        open.forEach { runCatching { it.close() } }
    }

    private val lo = InetAddress.getByName("127.0.0.1")
    private fun policy() = NetPolicy.extensionFetch().copy(allowLoopback = true)

    @Test
    fun httpProxyGetsTheValidatedRequestWithCredentials() = runBlocking {
        val target = Stub { Canned(200, emptyList(), "direct".toByteArray()) }.also { open += it }
        val proxy = Stub { req ->
            if (Credentials.basic("u", "p") in req) Canned(200, emptyList(), "via proxy".toByteArray())
            else Canned(407, listOf("proxy-authenticate" to "Basic realm=\"t\""))
        }.also { open += it }
        val endpoint = ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", proxy.port, "u", "p")
        Outbound.proxy = endpoint
        val http = SafeHttp(policy(), MapResolver(mapOf("media.test" to listOf(lo))), outboundProxy = { endpoint.javaProxy() })
        val r = http.send(SafeRequest.get("http://media.test:${target.port}/clip.mp4".toHttpUrl(), listOf("referer" to "https://site/")))
        assertEquals("via proxy", r.response.use { it.body.string() })
        assertTrue(proxy.seen.last().startsWith("GET http://media.test:${target.port}/clip.mp4 "), proxy.seen.last())
        assertTrue("referer: https://site/" in proxy.seen.last().lowercase())
        assertEquals(0, target.seen.size)
    }

    @Test
    fun socks5ProxyCarriesTheRequestByName() = runBlocking {
        val target = Stub { Canned(200, emptyList(), "segment".toByteArray()) }.also { open += it }
        val socks = TestSocks5("alice", "pw") { _, _ -> InetSocketAddress(lo, target.port) }.also { open += it }
        val endpoint = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", socks.port, "alice", "pw")
        Outbound.proxy = endpoint
        val http = SafeHttp(policy(), MapResolver(mapOf("cdn.test" to listOf(lo))), outboundProxy = { endpoint.javaProxy() })
        val r = http.send(SafeRequest.get("http://cdn.test:${target.port}/seg0.ts".toHttpUrl(), emptyList()))
        assertEquals("segment", r.response.use { it.body.string() })
        assertEquals(listOf("cdn.test:${target.port}"), socks.targets.toList())
        assertEquals(listOf("alice:pw"), socks.logins.toList())
    }

    @Test
    fun aDeadProxyFailsInsteadOfGoingDirect() = runBlocking {
        val target = Stub { Canned(200, emptyList(), "direct".toByteArray()) }.also { open += it }
        val dead = ServerSocket(0).use { it.localPort }
        val endpoint = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", dead)
        val http = SafeHttp(policy(), MapResolver(mapOf("cdn.test" to listOf(lo))), outboundProxy = { endpoint.javaProxy() })
        try {
            http.send(SafeRequest.get("http://cdn.test:${target.port}/x".toHttpUrl(), emptyList())).response.close()
            fail("expected a failure")
        } catch (e: NetError) {
            assertTrue(e is NetError.Http || e is NetError.Timeout, e.toString())
        }
        assertEquals(0, target.seen.size)
    }

    @Test
    fun throughAProxyNothingIsResolvedHereButThePolicyStillApplies() = runBlocking {
        val endpoint = ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", 9)
        val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
        val resolver = Resolver { host, _ -> asked += host; listOf(InetAddress.getByName("10.0.0.5")) }
        // production policy (no loopback): literal private / loopback addresses and local-only names are refused before the proxy
        val http = SafeHttp(NetPolicy.extensionFetch(), resolver, outboundProxy = { endpoint.javaProxy() })
        for (u in listOf("http://10.0.0.5/", "http://127.0.0.1:8080/", "http://[::1]/", "http://169.254.169.254/latest", "http://printer.local/",
            "http://router.lan/", "http://db.internal/", "http://intranet/")) {
            try {
                http.send(SafeRequest.get(u.toHttpUrl(), emptyList())).response.close()
                fail("expected a refusal for $u")
            } catch (e: NetError) {
                assertTrue(e is NetError.BlockedIp || e is NetError.Host, "$u: $e")
            }
        }
        assertTrue(asked.isEmpty(), "no DNS query outside the proxy: $asked")
    }

    @Test
    fun aNameGoesToTheProxyUnresolved() = runBlocking {
        val target = Stub { Canned(200, emptyList(), "seg".toByteArray()) }.also { open += it }
        val socks = TestSocks5 { _, _ -> InetSocketAddress(lo, target.port) }.also { open += it }
        val endpoint = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", socks.port)
        val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
        val http = SafeHttp(policy(), Resolver { host, _ -> asked += host; throw java.net.UnknownHostException(host) }, outboundProxy = { endpoint.javaProxy() })
        val r = http.send(SafeRequest.get("http://blocked-by-isp.example:${target.port}/seg.ts".toHttpUrl(), emptyList()))
        assertEquals("seg", r.response.use { it.body.string() })
        assertEquals(listOf("blocked-by-isp.example:${target.port}"), socks.targets.toList())
        assertTrue(asked.isEmpty(), "resolved locally: $asked")
    }

    @Test
    fun byDefaultTheProxyComesFromSettings() {
        Outbound.proxy = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", 1080)
        assertEquals(java.net.Proxy.Type.SOCKS, Outbound.proxyFor("cdn.example")?.type())
        assertEquals(null, Outbound.proxyFor("192.168.1.43"))
        Outbound.proxy = null
        assertEquals(null, Outbound.proxyFor("cdn.example"))
    }
}
