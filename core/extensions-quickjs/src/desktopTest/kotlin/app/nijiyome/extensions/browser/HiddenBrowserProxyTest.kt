// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.BrowserError
import app.nijiyome.network.ProxyEndpoint
import app.nijiyome.network.ProxyKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Settings › Red's outbound proxy on the hidden browser (1.1 review #10): Chromium only talks to the [BrowserGateway], which goes
 * through the proxy with its password (HTTP CONNECT or SOCKS5, so SOCKS5 with a password works now) and never asks twice.
 */
class HiddenBrowserProxyTest {
    /** A fake upstream proxy that, once the tunnel is open, plays the destination web server itself. */
    private class FakeUpstream(private val kind: ProxyKind, private val user: String, private val pass: String) : AutoCloseable {
        val targets = CopyOnWriteArrayList<String>()
        val authFailures = AtomicInteger()
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                while (true) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { s.use { if (kind == ProxyKind.HTTP) http(it) else socks(it) } } }
                }
            }
        }

        private fun http(s: Socket) {
            val head = readHead(s.getInputStream())
            targets += head.lineSequence().first().split(' ')[1]
            val expected = "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
            if (!head.lineSequence().any { it.equals("Proxy-Authorization: $expected", ignoreCase = true) }) {
                authFailures.incrementAndGet()
                s.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"t\"\r\nContent-Length: 0\r\n\r\n".toByteArray())
                return
            }
            s.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
            page(s)
        }

        private fun socks(s: Socket) {
            val input = DataInputStream(s.getInputStream())
            val out = s.getOutputStream()
            input.readUnsignedByte()
            val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            if (2 !in methods.map { it.toInt() }) {
                out.write(byteArrayOf(5, 0xFF.toByte()))
                return
            }
            out.write(byteArrayOf(5, 2))
            input.readUnsignedByte()
            val u = String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) })
            val p = String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) })
            if (u != user || p != pass) {
                authFailures.incrementAndGet()
                out.write(byteArrayOf(1, 1))
                return
            }
            out.write(byteArrayOf(1, 0))
            input.readUnsignedByte(); input.readUnsignedByte(); input.readUnsignedByte()
            val host = when (input.readUnsignedByte()) {
                3 -> String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) })
                1 -> ByteArray(4).also { input.readFully(it) }.joinToString(".") { (it.toInt() and 0xff).toString() }
                else -> "?"
            }
            targets += "$host:${input.readUnsignedShort()}"
            out.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            page(s)
        }

        private fun page(s: Socket) {
            readHead(s.getInputStream())
            val body = "<!doctype html><title>via proxy</title><p id=p>proxied</p>"
            s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
        }

        private fun readHead(input: InputStream): String {
            val b = StringBuilder()
            while (!b.endsWith("\r\n\r\n")) {
                val c = input.read()
                if (c < 0) break
                b.append(c.toChar())
            }
            return b.toString()
        }

        override fun close() = server.close()
    }

    // the page is an outside host: it can only be reached through the proxy (the real gateway rules apply)
    private val policy: (String, List<String>) -> Boolean = { url, d -> url.startsWith("http://") && d.any { url.startsWith("http://$it/") } }

    private fun loadThrough(test: String, endpoint: ProxyEndpoint, options: JsonObject = JsonObject(emptyMap())): String? {
        val exe = LocalPages.browser() ?: return null.also { LocalPages.skip(test) }
        val host = ChromiumHost(executable = { exe }, proxy = { endpoint })
        val runtime = CdpBrowserRuntime(host, urlAllowed = policy, subresourceAllowed = policy)
        return runBlocking {
            try {
                val id = runtime.newBrowser("ext", listOf("nijiyome-proxy.test"), options)
                try {
                    runtime.navigate(id, "http://nijiyome-proxy.test/page")
                    (runtime.evaluate(id, "document.getElementById('p') ? document.getElementById('p').textContent : 'none'") as JsonPrimitive).content
                } finally {
                    runtime.close(id)
                }
            } finally {
                host.shutdown()
            }
        }
    }

    @Test
    fun pagesGoThroughAnHttpProxyWithItsPassword() = FakeUpstream(ProxyKind.HTTP, "nij", "s3cret").use { p ->
        val text = loadThrough("pagesGoThroughAnHttpProxyWithItsPassword", ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", p.port, "nij", "s3cret")) ?: return
        assertEquals("proxied", text)
        assertTrue("nijiyome-proxy.test:80" in p.targets, "the name went to the proxy unresolved: ${p.targets}")
        assertEquals(0, p.authFailures.get())
    }

    @Test
    fun socks5WithAPasswordWorksNow() = FakeUpstream(ProxyKind.SOCKS5, "u", "p").use { p ->
        val text = loadThrough("socks5WithAPasswordWorksNow", ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", p.port, "u", "p")) ?: return
        assertEquals("proxied", text)
        assertTrue("nijiyome-proxy.test:80" in p.targets, p.targets.toString())
    }

    @Test
    fun aWrongProxyPasswordFailsInsteadOfLooping() = FakeUpstream(ProxyKind.HTTP, "nij", "right").use { p ->
        val exe = LocalPages.browser() ?: return LocalPages.skip("aWrongProxyPasswordFailsInsteadOfLooping")
        val host = ChromiumHost(executable = { exe }, proxy = { ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", p.port, "nij", "wrong") })
        val runtime = CdpBrowserRuntime(host, urlAllowed = policy, subresourceAllowed = policy)
        runBlocking {
            try {
                val id = runtime.newBrowser("ext", listOf("nijiyome-proxy.test"), buildJsonObject { put("timeout", 20) })
                val started = System.currentTimeMillis()
                assertFailsWith<BrowserError.Failed> { runtime.navigate(id, "http://nijiyome-proxy.test/page") }
                val took = System.currentTimeMillis() - started
                assertTrue(took < 5_000, "failed at once, not after the 20 s timeout ($took ms)")
                runtime.close(id)
            } finally {
                host.shutdown()
            }
        }
        // the first refusal is remembered: the proxy is not asked again with the same wrong password (a couple may be in flight at once)
        assertTrue(p.authFailures.get() in 1..2, "asked once, not in a loop: ${p.authFailures.get()}")
    }

    @Test
    fun aSitesPasswordPromptIsCancelledOnce() {
        val exe = LocalPages.browser() ?: return LocalPages.skip("aSitesPasswordPromptIsCancelledOnce")
        val host = LocalPages.host(exe)
        val runtime = CdpBrowserRuntime(host, urlAllowed = LocalPages.localPolicy, subresourceAllowed = LocalPages.localPolicy)
        LocalPages().use { pages ->
            runBlocking {
                try {
                    val id = runtime.newBrowser("ext", listOf("127.0.0.1"), buildJsonObject { put("timeout", 20) })
                    val started = System.currentTimeMillis()
                    runtime.navigate(id, pages.base + "/auth")
                    assertTrue(System.currentTimeMillis() - started < 5_000)
                    assertEquals(JsonPrimitive("auth required"), runtime.evaluate(id, "document.body.textContent.trim()"))
                    runtime.close(id)
                } finally {
                    host.shutdown()
                }
            }
            assertTrue(pages.paths().count { it == "/auth" } <= 2, "no retry loop: ${pages.paths()}")
        }
    }
}
