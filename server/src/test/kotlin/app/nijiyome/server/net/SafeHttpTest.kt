// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of the tests of Tatakai Next tk-core/src/net.rs (AGPL-3.0, same author). The outbound-proxy tests are in SafeHttpOutboundTest
// and FlareSolverr's in desktop/LoopbackMediaRouteTest (1.1).
package app.nijiyome.server.net

import app.nijiyome.server.link.Canned
import app.nijiyome.server.link.MapResolver
import app.nijiyome.server.link.Stub
import app.nijiyome.server.link.redirect
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class SafeHttpTest {
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() = open.forEach { runCatching { it.close() } }

    private fun stub(f: (String) -> Canned) = Stub(f).also { open += it }

    private fun ip(s: String) = InetAddress.getByName(s)

    private fun ok(body: String) = Canned(200, emptyList(), body.toByteArray())

    private fun lo() = NetPolicy.linkSource(true).copy(allowLoopback = true, allowedPorts = null)

    private inline fun <reified E : NetError> expect(block: () -> Unit) {
        try {
            block()
        } catch (e: NetError) {
            assertIs<E>(e)
            return
        }
        fail("expected ${E::class.simpleName}")
    }

    @Test
    fun blockedAddressTable() {
        for (b in listOf(
            "127.0.0.1", "127.255.255.254", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1", "100.64.0.1", "100.100.100.200",
            "169.254.169.254", "169.254.0.1", "0.0.0.0", "0.1.2.3", "224.0.0.1", "255.255.255.255", "192.0.2.1", "198.18.0.1", "240.0.0.1",
            "::1", "::", "fe80::1", "fc00::1", "fd00:ec2::254", "ff02::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254",
            "64:ff9b::7f00:1", "2002:7f00:1::1", "2001:db8::1",
        )) {
            assertTrue(AddressRules.isBlocked(ip(b)), "$b must be blocked")
        }
        for (o in listOf("1.1.1.1", "8.8.8.8", "93.184.216.34", "172.32.0.1", "100.128.0.1", "2606:4700:4700::1111", "::ffff:8.8.8.8")) {
            assertFalse(AddressRules.isBlocked(ip(o)), "$o must be allowed")
        }
    }

    @Test
    fun permissionMatching() {
        val perms = listOf("network:domain:cdn.example.com", "parse:html")
        assertTrue(AddressRules.hostMatchesPermissions("cdn.example.com", perms))
        assertTrue(AddressRules.hostMatchesPermissions("a.cdn.example.com", perms))
        assertTrue(AddressRules.hostMatchesPermissions("CDN.Example.com.", perms))
        assertFalse(AddressRules.hostMatchesPermissions("evilcdn.example.com", perms))
        assertFalse(AddressRules.hostMatchesPermissions("example.com", perms))
        assertTrue(AddressRules.hostMatchesPermissions("anything.test", listOf("network:domain:*")))
        assertFalse(AddressRules.hostMatchesPermissions("anything.test", listOf("parse:html")))
    }

    @Test
    fun headerSanitizing() {
        val p = NetPolicy.linkSource(false)
        val h = mapOf("Referer" to "https://a/", "User-Agent" to "x")
        assertEquals(2, sanitizeHeaders(p, h).size)
        expect<NetError.Header> { sanitizeHeaders(p, h + ("Referer" to "https://a/\r\nX-Evil: 1")) }
        expect<NetError.Header> { sanitizeHeaders(p, mapOf("Host" to "evil")) }
        expect<NetError.Header> { sanitizeHeaders(p, mapOf("X-A\r\nB" to "v")) }
        expect<NetError.Header> { sanitizeHeaders(p, mapOf("Referer" to "a".repeat(9 * 1024))) }
    }

    @Test
    fun rejectsPrivateLiteralsAndHostnamesResolvingToPrivate(): Unit = runBlocking {
        val m = mapOf(
            "intranet.test" to listOf(ip("192.168.1.10")),
            "mixed.test" to listOf(ip("93.184.216.34"), ip("127.0.0.1")),
            "good.test" to listOf(ip("93.184.216.34")),
        )
        val http = SafeHttp(NetPolicy.linkSource(true), MapResolver(m))
        for (t in listOf(
            "http://127.0.0.1/x", "http://10.1.2.3/", "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://[fd00::1]/",
            "http://0.0.0.0/", "http://100.64.1.1/", "https://intranet.test/", "https://mixed.test/", "http://[::ffff:127.0.0.1]/",
        )) {
            try {
                http.resolveValidated(t.toHttpUrl())
                fail("$t must be refused")
            } catch (e: NetError) {
                assertIs<NetError.BlockedIp>(e, t)
            }
        }
        http.resolveValidated("https://good.test/".toHttpUrl())
        try {
            http.resolveValidated("https://missing.test/".toHttpUrl())
            fail("missing.test resolved")
        } catch (e: NetError) {
            assertIs<NetError.Dns>(e)
        }
    }

    @Test
    fun schemePortAndUserinfoChecks() {
        val http = SafeHttp(NetPolicy.linkSource(false), MapResolver(emptyMap()))
        expect<NetError.Scheme> { http.checkUrl("http://a.test/".toHttpUrl()) }
        // ftp: / file: never become an HttpUrl: the session route refuses them as `url_source_denied` (Target.OtherScheme)
        assertEquals(Target.OtherScheme, Target.parse("ftp://a.test/"))
        assertEquals(Target.OtherScheme, Target.parse("file:///etc/passwd"))
        expect<NetError.Port> { http.checkUrl("https://a.test:22/".toHttpUrl()) }
        expect<NetError.Port> { http.checkUrl("https://a.test:6379/".toHttpUrl()) }
        expect<NetError.Host> { http.checkUrl("https://user:pw@a.test/".toHttpUrl()) }
        http.checkUrl("https://a.test:8443/".toHttpUrl())
    }

    @Test
    fun rebindingSecondResolutionIsNeverUsed(): Unit = runBlocking {
        val n = AtomicInteger()
        val http = SafeHttp(NetPolicy.linkSource(true), Resolver { _, _ -> listOf(ip(if (n.getAndIncrement() == 0) "93.184.216.34" else "127.0.0.1")) })
        http.resolveValidated("https://rebind.test/".toHttpUrl())
        try {
            http.resolveValidated("https://rebind.test/".toHttpUrl())
            fail("the second answer must be refused")
        } catch (e: NetError) {
            assertIs<NetError.BlockedIp>(e)
        }
    }

    @Test
    fun redirectToPrivateIpIsRejectedAfterTheFirstHop(): Unit = runBlocking {
        for (target in listOf("http://10.0.0.1/secret", "http://169.254.169.254/latest", "http://192.168.0.1:8080/", "http://[::1]:8080/")) {
            val s = stub { redirect(target) }
            val http = SafeHttp(lo(), MapResolver(emptyMap()))
            try {
                http.send(SafeRequest.get("http://127.0.0.1:${s.port}/start".toHttpUrl(), emptyList())).response.close()
                fail(target)
            } catch (e: NetError) {
                // the loopback IPv6 case is allowed by the test policy but nothing listens there: an HTTP error
                if ("[::1]" !in target) assertIs<NetError.BlockedIp>(e, target)
            }
        }
    }

    @Test
    fun redirectLoopStopsAtFive(): Unit = runBlocking {
        val s = stub { redirect("/again") }
        val http = SafeHttp(lo(), MapResolver(emptyMap()))
        try {
            http.send(SafeRequest.get("http://127.0.0.1:${s.port}/start".toHttpUrl(), emptyList()))
            fail("no error")
        } catch (e: NetError) {
            assertIs<NetError.TooManyRedirects>(e)
        }
        assertEquals(6, s.seen.size)
    }

    @Test
    fun cookieAndAuthorizationAreDroppedOnCrossOriginRedirectButKeptSameOrigin(): Unit = runBlocking {
        val b = stub { ok("done") }
        val target = "http://127.0.0.1:${b.port}/landing"
        val a = stub { req -> if (req.startsWith("GET /start")) redirect(target) else ok("a") }
        val http = SafeHttp(lo(), MapResolver(emptyMap()))
        val headers = listOf("cookie" to "sid=secret", "authorization" to "Bearer tok", "referer" to "https://ref.example/")
        http.send(SafeRequest.get("http://127.0.0.1:${a.port}/start".toHttpUrl(), headers)).response.use { assertEquals(200, it.code) }
        val first = a.seen[0].lowercase()
        assertTrue("cookie: sid=secret" in first && "authorization: bearer tok" in first)
        val second = b.seen[0].lowercase()
        assertFalse("cookie:" in second, "cookie leaked cross-origin: $second")
        assertFalse("authorization:" in second, "authorization leaked cross-origin: $second")
        assertTrue("referer: https://ref.example/" in second, "non-credential headers survive")
        val c = stub { req -> if (req.startsWith("GET /start")) redirect("/next") else ok("c") }
        http.send(SafeRequest.get("http://127.0.0.1:${c.port}/start".toHttpUrl(), listOf("cookie" to "k=v"))).response.close()
        assertTrue(c.seen[1].startsWith("GET /next") && "cookie: k=v" in c.seen[1].lowercase())
    }

    @Test
    fun readCappedEnforcesTheLimit(): Unit = runBlocking {
        val s = stub { ok("x".repeat(1000)) }
        val http = SafeHttp(lo(), MapResolver(emptyMap()))
        val r1 = http.send(SafeRequest.get("http://127.0.0.1:${s.port}/".toHttpUrl(), emptyList())).response
        expect<NetError.TooLarge> { readCapped(r1, 999) }
        val r2 = http.send(SafeRequest.get("http://127.0.0.1:${s.port}/".toHttpUrl(), emptyList())).response
        assertEquals(1000, readCapped(r2, 1000).size)
    }

    @Test
    fun aTrustedOriginOpensOnlyThatEndpoint(): Unit = runBlocking {
        val s = stub { ok("media") }
        val port = s.port
        val http = SafeHttp(NetPolicy.linkSource(false), MapResolver(emptyMap()))
            .withHostFilter { false }
            .withTrustedOrigins(listOf(TrustedOrigin(InetSocketAddress("127.0.0.1", port), "/api/server/v1/media/")))
        val r = http.send(SafeRequest.get("http://127.0.0.1:$port/api/server/v1/media/t1/a.m3u8".toHttpUrl(), emptyList())).response
        assertEquals("media", readCapped(r, 100).decodeToString())
        for (bad in listOf(
            "http://127.0.0.1:$port/api/server/v1/status",
            "http://127.0.0.1:$port/api/server/v1/media/../status",
            "http://127.0.0.1:${(port + 1).coerceAtMost(65535)}/api/server/v1/media/t1/a.m3u8",
            "http://127.0.0.2:$port/api/server/v1/media/t1/a.m3u8",
            "http://user@127.0.0.1:$port/api/server/v1/media/t1/a.m3u8",
        )) {
            try {
                http.resolveValidated(bad.toHttpUrl())
                fail("$bad must stay blocked")
            } catch (_: NetError) {
            }
        }
        val plain = SafeHttp(NetPolicy.linkSource(false), MapResolver(emptyMap()))
        try {
            plain.resolveValidated("http://127.0.0.1:$port/api/server/v1/media/t1/a.m3u8".toHttpUrl())
            fail("no trust without the origin")
        } catch (_: NetError) {
        }
    }
}
