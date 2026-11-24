// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The hidden browser's [BrowserGateway] (1.1 review #1) driven by a minimal SOCKS5 client, no browser: local, private, link-local,
 * IPv6 and IPv4-mapped destinations, local names and names that resolve to them are refused (SafeHttp's rules); a public answer is
 * relayed to the address that was validated.
 */
class BrowserGatewayTest {
    /** An echo server standing for "the destination"; counts its connections. */
    private class Echo : AutoCloseable {
        val accepted = AtomicInteger()
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                while (true) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    accepted.incrementAndGet()
                    thread(isDaemon = true) { runCatching { s.use { it.getInputStream().transferTo(it.getOutputStream()) } } }
                }
            }
        }

        override fun close() = server.close()
    }

    /** SOCKS5 CONNECT through [gateway] to a name ([atyp] 3) or an address (1 / 4); the reply code, and the socket when it is 0. */
    private fun connect(gateway: BrowserGateway, atyp: Int, addr: ByteArray, port: Int): Pair<Int, Socket> {
        val s = Socket("127.0.0.1", gateway.port)
        s.soTimeout = 10_000
        val out = s.getOutputStream()
        val input = DataInputStream(s.getInputStream())
        out.write(byteArrayOf(5, 1, 0))
        assertEquals(5, input.readUnsignedByte())
        assertEquals(0, input.readUnsignedByte())
        val a = if (atyp == 3) byteArrayOf(addr.size.toByte()) + addr else addr
        out.write(byteArrayOf(5, 1, 0, atyp.toByte()) + a + byteArrayOf((port shr 8).toByte(), port.toByte()))
        assertEquals(5, input.readUnsignedByte())
        val rep = input.readUnsignedByte()
        input.readFully(ByteArray(8))
        return rep to s
    }

    private fun name(gw: BrowserGateway, host: String, port: Int) = connect(gw, 3, host.toByteArray(), port).also { it.second.close() }.first
    private fun ip(gw: BrowserGateway, literal: String, port: Int): Int {
        val a = InetAddress.getByName(literal).address
        return connect(gw, if (a.size == 4) 1 else 4, a, port).also { it.second.close() }.first
    }

    @Test
    fun localAndPrivateDestinationsAreRefusedAtTheSocket() = Echo().use { echo ->
        // the real rules; the resolver is fake so no DNS query leaves the test
        val answers = mapOf(
            "rebind.example.com" to listOf("127.0.0.1"),
            "router.example.com" to listOf("192.168.1.1"),
            "metadata.example.com" to listOf("169.254.169.254"),
            "mixed.example.com" to listOf("93.184.216.34", "10.0.0.5"),
            "v6.example.com" to listOf("::1"),
        )
        BrowserGateway(upstream = null, resolve = { h -> answers[h].orEmpty().map(InetAddress::getByName) }).use { gw ->
            for (literal in listOf("127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.0.10", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fe80::1", "fd00::1", "::ffff:127.0.0.1", "::ffff:192.168.1.1")) {
                assertEquals(2, ip(gw, literal, echo.port), "$literal is refused")
            }
            for (host in listOf("localhost", "LOCALHOST.", "printer.local", "nas.lan", "db.internal", "intranet", "rebind.example.com", "router.example.com", "metadata.example.com", "mixed.example.com", "v6.example.com")) {
                assertEquals(2, name(gw, host, echo.port), "$host is refused")
            }
            assertEquals(0, echo.accepted.get(), "nothing ever reached the local service")
            assertTrue("rebind.example.com:${echo.port}" in gw.refused)
        }
    }

    @Test
    fun aPublicAnswerIsRelayedToTheValidatedAddress() = Echo().use { echo ->
        val lookups = AtomicInteger()
        // here "public.example.com" stands for a public host; its (test) address is the local echo server, allowed by the test rule
        BrowserGateway(
            upstream = null,
            allowed = { ip, port -> ip.isLoopbackAddress && port == echo.port },
            resolve = { h -> lookups.incrementAndGet(); if (h == "public.example.com") listOf(InetAddress.getByName("127.0.0.1")) else emptyList() },
        ).use { gw ->
            val (rep, s) = connect(gw, 3, "public.example.com".toByteArray(), echo.port)
            s.use {
                assertEquals(0, rep)
                it.getOutputStream().write("ping".toByteArray())
                assertEquals("ping", String(DataInputStream(it.getInputStream()).let { d -> ByteArray(4).also(d::readFully) }))
            }
            assertEquals(1, lookups.get(), "resolved once, here, and that answer was used")
            // another port of the same address is still refused by the rule
            assertEquals(2, ip(gw, "127.0.0.1", echo.port + 1))
        }
    }

    @Test
    fun withAnOutboundProxyNamesAreNotResolvedHereButLocalOnesAreStillRefused() = Echo().use { echo ->
        val lookups = AtomicInteger()
        BrowserGateway(
            upstream = app.nijiyome.network.ProxyEndpoint(app.nijiyome.network.ProxyKind.SOCKS5, "127.0.0.1", 9), // nothing listens: connects fail
            resolve = { lookups.incrementAndGet(); emptyList() },
        ).use { gw ->
            assertEquals(2, ip(gw, "127.0.0.1", echo.port))
            assertEquals(2, name(gw, "localhost", echo.port))
            assertEquals(4, name(gw, "public.example.com", 443), "a public name goes to the proxy (unreachable here)")
            assertEquals(0, lookups.get(), "no DNS query outside the proxy")
        }
    }

    @Test
    fun onlyConnectIsServedAndTheGatewayListensOnLoopbackOnly() {
        BrowserGateway(upstream = null).use { gw ->
            Socket("127.0.0.1", gw.port).use { s ->
                val input = DataInputStream(s.getInputStream())
                s.getOutputStream().write(byteArrayOf(5, 1, 0))
                input.readFully(ByteArray(2))
                // UDP ASSOCIATE
                s.getOutputStream().write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
                assertEquals(5, input.readUnsignedByte())
                assertEquals(7, input.readUnsignedByte())
            }
            val wildcard = runCatching { Socket(InetAddress.getLocalHost(), gw.port).close() }.isSuccess &&
                !InetAddress.getLocalHost().isLoopbackAddress
            assertFalse(wildcard, "not reachable on the machine's own network address")
        }
        assertTrue(BrowserGateway.isLocalName("Router.Local."))
        assertFalse(BrowserGateway.isLocalName("cdn.example.com"))
    }
}
