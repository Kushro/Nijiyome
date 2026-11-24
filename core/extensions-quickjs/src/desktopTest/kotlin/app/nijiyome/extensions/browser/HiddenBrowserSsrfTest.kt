// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.model.EmbedSniffException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 1.1 review #1: a hidden page (an embed an extension pointed at, or a page it loaded) cannot reach this PC or the LAN, whatever it
 * uses: navigation, iframe, `fetch`, `<img>`, WebSocket. The browser's only way out is the [BrowserGateway].
 */
class HiddenBrowserSsrfTest {
    /** A "service on this PC / the LAN": a bare TCP listener that counts who connects. */
    private class LanService : AutoCloseable {
        val accepted = AtomicInteger()
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                while (true) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    accepted.incrementAndGet()
                    runCatching { s.close() }
                }
            }
        }

        override fun close() = server.close()
    }

    @Test
    fun aPageCannotReachLocalServicesInAnyWay() {
        val exe = LocalPages.browser() ?: return LocalPages.skip("aPageCannotReachLocalServicesInAnyWay")
        LanService().use { lan ->
            val page = MockWebServer().apply {
                dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = MockResponse.Builder().code(200)
                        .addHeader("Content-Type", "text/html")
                        .body(
                            """<!doctype html><title>t</title><body><p id=p>public page</p><script>
var lan='127.0.0.1:${lan.port}';
fetch('http://'+lan+'/secret').catch(function(){});
var i=new Image();i.src='http://'+lan+'/pixel.png';
try{new WebSocket('ws://'+lan+'/ws');}catch(e){}
var f=document.createElement('iframe');f.src='http://localhost:${lan.port}/admin';document.body.appendChild(f);
var x=new XMLHttpRequest();x.open('GET','http://[::ffff:127.0.0.1]:${lan.port}/x');x.send();
</script></body>""",
                        ).build()
                }
                start(InetAddress.getByName("127.0.0.1"), 0)
            }
            page.use {
                // the real gateway rules, except that the test page itself (this port) stands for a public site
                val host = ChromiumHost(executable = { exe }, proxy = { null }, destinationAllowed = { ip, port -> port == page.port && ip.isLoopbackAddress })
                // the CDP-level policy lets everything through: only the gateway stands in the way here
                val runtime = CdpBrowserRuntime(host, urlAllowed = { _, _ -> true }, subresourceAllowed = { _, _ -> true })
                runBlocking {
                    try {
                        val id = runtime.newBrowser("ext", listOf("127.0.0.1"), JsonObject(emptyMap()))
                        runtime.navigate(id, "http://127.0.0.1:${page.port}/")
                        assertEquals(JsonPrimitive("public page"), runtime.evaluate(id, "document.getElementById('p').textContent"))
                        delay(2_000)
                        // and a direct navigation to the local service
                        assertFailsWith<app.nijiyome.extensions.BrowserError.Failed> { runtime.navigate(id, "http://127.0.0.1:${lan.port}/") }
                        val refused = host.currentGateway!!.refused
                        assertTrue("127.0.0.1:${lan.port}" in refused || "localhost:${lan.port}" in refused, "the gateway refused them: $refused")
                        runtime.close(id)
                    } finally {
                        host.shutdown()
                    }
                }
            }
            assertEquals(0, lan.accepted.get(), "nothing reached the local service (fetch, img, WebSocket, iframe, XHR, navigation)")
        }
    }

    @Test
    fun anEmbedUrlOnThisPcOrTheLanIsRefusedBeforeOpeningAnything() = runBlocking<Unit> {
        val host = ChromiumHost(executable = { LocalPages.browser() ?: ChromiumExecutable("/nonexistent", manual = true) }, proxy = { null })
        val sniffer = CdpEmbedSniffer(host, blockedHosts = emptySet(), log = {})
        for (url in listOf("http://127.0.0.1:47410/callback", "http://localhost:8080/", "http://192.168.1.1/", "http://[::1]/", "http://169.254.169.254/latest/meta-data/", "file:///etc/passwd")) {
            assertEquals(EmbedSniffException.BLOCKED, assertFailsWith<EmbedSniffException> { sniffer.sniff(url, emptyMap(), "ext", 5_000) }.reason, url)
        }
        assertNull(host.currentProcess, "no browser was started for them")
    }

    @Test
    fun embedUrlRules() {
        val ok = listOf("https://embed.example.com/e/1", "http://cdn.example.net:8080/v?x=1", "https://8.8.8.8/x")
        val bad = listOf(
            "http://127.0.0.1/", "http://127.1/", "http://localhost/", "http://LOCALHOST./", "http://box.local/", "http://intranet/",
            "http://10.0.0.1/", "http://172.20.1.1/", "http://192.168.0.1/", "http://169.254.169.254/", "http://100.64.1.1/",
            "http://[::1]/", "http://[fe80::1]/", "http://[fd12::1]/", "http://[::ffff:127.0.0.1]/", "http://[::ffff:c0a8:101]/",
            "https://user:pw@embed.example.com/", "file:///etc/passwd", "ftp://embed.example.com/", "javascript:alert(1)", "not a url",
        )
        ok.forEach { assertTrue(CdpEmbedSniffer.embedUrlAllowed(it), it) }
        bad.forEach { assertFalse(CdpEmbedSniffer.embedUrlAllowed(it), it) }
        assertFalse(CdpEmbedSniffer.schemeAllowed("file:///etc/passwd"))
        assertFalse(CdpEmbedSniffer.schemeAllowed("chrome://settings"))
        assertTrue(CdpEmbedSniffer.schemeAllowed("blob:https://x/1"))
    }
}
