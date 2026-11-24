// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.BrowserError
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop [CdpBrowserRuntime] against the local test site, with the installed Chrome / Edge / Chromium (skipped without one). */
class CdpBrowserRuntimeTest {
    private val none = JsonObject(emptyMap())
    private val domains = listOf("127.0.0.1", "localhost")

    private fun withRuntime(
        test: String,
        transport: ChromiumHost.Transport = ChromiumHost.Transport.PIPE,
        idleMs: Long = ChromiumHost.DEFAULT_IDLE_MS,
        block: suspend (CdpBrowserRuntime, ChromiumHost, LocalPages) -> Unit,
    ) {
        val exe = LocalPages.browser() ?: return LocalPages.skip(test)
        val host = LocalPages.host(exe, idleMs, transport)
        val runtime = CdpBrowserRuntime(host, urlAllowed = LocalPages.localPolicy, subresourceAllowed = LocalPages.localPolicy)
        LocalPages().use { pages ->
            runBlocking {
                try {
                    block(runtime, host, pages)
                } finally {
                    host.shutdown()
                }
            }
        }
    }

    private suspend fun waitTrue(runtime: CdpBrowserRuntime, id: String, expr: String, ms: Long = 10_000): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            val v = runCatching { runtime.evaluate(id, expr) }.getOrNull()
            if ((v as? JsonPrimitive)?.content == "true") return true
            delay(100)
        }
        return false
    }

    @Test
    fun solvesTheJsChallengeEvaluatesAndReadsCookies() = withRuntime("solvesTheJsChallengeEvaluatesAndReadsCookies") { runtime, _, pages ->
        val id = runtime.newBrowser("ext", domains, none)
        runtime.navigate(id, pages.base + "/")
        assertTrue(waitTrue(runtime, id, "window.__ready===true"), "the challenge page went on to /watch")
        val v = runtime.evaluate(id, "({title: document.getElementById('title').textContent, ua: navigator.userAgent, wd: navigator.webdriver})").jsonObject
        assertEquals("Episode 1", v["title"]!!.jsonPrimitive.content)
        assertFalse(v["ua"]!!.jsonPrimitive.content.contains("Headless"), "no HeadlessChrome in the user agent")
        assertEquals(false, v["wd"]!!.jsonPrimitive.boolean)
        val cookies = runtime.cookies(id, listOf(pages.base + "/"))
        val sess = cookies.single { it["name"]!!.jsonPrimitive.content == "sess" }
        assertEquals("abc123", sess["value"]!!.jsonPrimitive.content)
        assertEquals(true, sess["httpOnly"]!!.jsonPrimitive.boolean)
        assertEquals("45", cookies.single { it["name"]!!.jsonPrimitive.content == "js_ok" }["value"]!!.jsonPrimitive.content)
        runtime.close(id)
    }

    @Test
    fun requestsOutsideTheProviderDomainsNeverLeave() = withRuntime("requestsOutsideTheProviderDomainsNeverLeave") { runtime, _, pages ->
        // only 127.0.0.1: the cross-site iframe on localhost is refused before it reaches the network
        val id = runtime.newBrowser("ext", listOf("127.0.0.1"), none)
        runtime.navigate(id, pages.base + "/player")
        delay(1_000)
        assertTrue("/player" in pages.paths("127.0.0.1"))
        assertTrue(pages.paths("localhost").isEmpty(), "nothing was asked of localhost: ${pages.paths("localhost")}")
        assertFailsWith<BrowserError.UrlNotAllowed> { runtime.navigate(id, pages.other + "/player") }
        assertFailsWith<BrowserError.UrlNotAllowed> { runtime.cookies(id, listOf(pages.other + "/")) }
        runtime.close(id)
    }

    @Test
    fun theRealPolicyWantsHttpsWithoutPort() = runBlocking<Unit> {
        val exe = LocalPages.browser() ?: return@runBlocking LocalPages.skip("theRealPolicyWantsHttpsWithoutPort")
        val host = ChromiumHost(executable = { exe }, proxy = { null })
        try {
            val runtime = CdpBrowserRuntime(host)
            val id = runtime.newBrowser("ext", listOf("example.com"), none)
            assertFailsWith<BrowserError.UrlNotAllowed> { runtime.navigate(id, "http://example.com/") }
            assertFailsWith<BrowserError.UrlNotAllowed> { runtime.navigate(id, "https://example.com:8443/") }
            assertFailsWith<BrowserError.UrlNotAllowed> { runtime.navigate(id, "https://evil.test/") }
            // evaluate checks the page it is on: about:blank is not a provider page
            assertFailsWith<BrowserError.UrlNotAllowed> { runtime.evaluate(id, "1") }
            runtime.close(id)
            assertFailsWith<BrowserError.NoDomains> { runtime.newBrowser("ext", emptyList(), none) }
        } finally {
            host.shutdown()
        }
    }

    @Test
    fun limitsErrorsTimeoutsAndClose() = withRuntime("limitsErrorsTimeoutsAndClose") { runtime, _, pages ->
        val quick = buildJsonObject { put("timeout", 2) }
        val a = runtime.newBrowser("ext", domains, quick)
        val b = runtime.newBrowser("ext", domains, none)
        assertFailsWith<BrowserError.Limit> { runtime.newBrowser("ext", domains, none) }
        val other = runtime.newBrowser("other-ext", domains, none) // the limit is per extension
        runtime.close(other)

        runtime.navigate(a, pages.base + "/still")
        val err = assertFailsWith<BrowserError.Failed> { runtime.evaluate(a, "notDefinedAnywhere.x") }
        assertTrue(err.message!!.contains("notDefinedAnywhere"), err.message)
        assertEquals(JsonPrimitive(2), runtime.evaluate(a, "1+1;"))
        assertEquals(kotlinx.serialization.json.JsonNull, runtime.evaluate(a, "undefined"))
        assertFailsWith<BrowserError.Failed> { runtime.evaluate(a, "x".repeat(40_000)) }

        val started = System.currentTimeMillis()
        assertFailsWith<BrowserError.Timeout> { runtime.navigate(a, pages.base + "/slow") }
        assertTrue(System.currentTimeMillis() - started < 5_000, "the 2 s operation timeout applied")

        runtime.close(a)
        runtime.close(a) // idempotent
        assertFailsWith<BrowserError.Closed> { runtime.navigate(a, pages.base + "/") }
        runtime.close(b)
    }

    @Test
    fun cookiesOfOneBrowserAreNotSeenByAnother() = withRuntime("cookiesOfOneBrowserAreNotSeenByAnother") { runtime, _, pages ->
        val a = runtime.newBrowser("ext", domains, none)
        runtime.navigate(a, pages.base + "/")
        assertTrue(waitTrue(runtime, a, "window.__ready===true"))
        val b = runtime.newBrowser("ext", domains, none)
        runtime.navigate(b, pages.base + "/still")
        assertTrue(runtime.cookies(b, listOf(pages.base + "/")).isEmpty(), "each browser has its own cookie jar")
        runtime.close(a)
        runtime.close(b)
    }

    @Test
    fun stopsWhenIdleAndLeavesNothingBehind() = withRuntime("stopsWhenIdleAndLeavesNothingBehind", idleMs = 500) { runtime, host, pages ->
        val id = runtime.newBrowser("ext", domains, none)
        runtime.navigate(id, pages.base + "/still")
        val process = assertNotNull(host.currentProcess)
        val profile: File = assertNotNull(host.currentProfile)
        assertTrue(profile.isDirectory)
        if (host.currentTransport == ChromiumHost.Transport.PIPE) {
            assertFalse(File(profile, "DevToolsActivePort").exists(), "pipe transport: no debugging port at all")
            assertTrue(listeningPorts(process.toHandle()).isEmpty(), "the browser listens on no TCP port")
        }
        runtime.close(id)
        delay(2_500)
        assertNull(host.currentProcess)
        assertFalse(process.isAlive, "the browser stopped after the idle time")
        assertFalse(profile.exists(), "its temporary profile is gone")
    }

    @Test
    fun portTransportListensOnLoopbackOnly() = withRuntime("portTransportListensOnLoopbackOnly", transport = ChromiumHost.Transport.PORT) { runtime, host, pages ->
        if (!File("/proc/net/tcp").exists()) return@withRuntime LocalPages.skip("portTransportListensOnLoopbackOnly (no /proc)")
        val id = runtime.newBrowser("ext", domains, none)
        runtime.navigate(id, pages.base + "/still")
        val profile = assertNotNull(host.currentProfile)
        val port = File(profile, "DevToolsActivePort").readLines().first().trim().toInt()
        val listening = File("/proc/net/tcp").readLines().drop(1).map { it.trim().split(Regex("\\s+")) }
            .filter { it[3] == "0A" && it[1].substringAfter(':').toInt(16) == port }
            .map { it[1].substringBefore(':') }
        assertEquals(listOf("0100007F"), listening, "the debugging port is bound to 127.0.0.1 only")
        assertTrue(File("/proc/net/tcp6").readLines().drop(1).none { l ->
            val f = l.trim().split(Regex("\\s+"))
            f[3] == "0A" && f[1].substringAfter(':').toInt(16) == port
        }, "and not on IPv6")
        runtime.close(id)
    }

    @Test
    fun noBrowserMeansUnavailable() = runBlocking<Unit> {
        val host = ChromiumHost(executable = { null }, proxy = { null })
        val runtime = CdpBrowserRuntime(host)
        assertFalse(runtime.available)
        assertFailsWith<BrowserError.Unavailable> { runtime.newBrowser("ext", domains, none) }
    }

    /** TCP ports in LISTEN state owned by [root] or its children (Linux /proc). */
    private fun listeningPorts(root: ProcessHandle): List<Int> {
        if (!File("/proc/net/tcp").exists()) return emptyList()
        val pids = (listOf(root) + root.descendants().toList()).map { it.pid() }
        val inodes = pids.flatMap { pid ->
            File("/proc/$pid/fd").listFiles().orEmpty().mapNotNull { f ->
                runCatching { java.nio.file.Files.readSymbolicLink(f.toPath()).toString() }.getOrNull()
                    ?.takeIf { it.startsWith("socket:[") }?.removePrefix("socket:[")?.removeSuffix("]")
            }
        }.toSet()
        return listOf("/proc/net/tcp", "/proc/net/tcp6").flatMap { path ->
            File(path).readLines().drop(1).map { it.trim().split(Regex("\\s+")) }
                .filter { it.size > 9 && it[3] == "0A" && it[9] in inodes }
                .map { it[1].substringAfter(':').toInt(16) }
        }
    }
}
