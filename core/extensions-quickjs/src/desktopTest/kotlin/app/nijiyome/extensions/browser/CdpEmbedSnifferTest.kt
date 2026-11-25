// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.ExtensionLogStore
import app.nijiyome.model.Container
import app.nijiyome.model.EmbedSniffException
import kotlinx.coroutines.runBlocking
import mockwebserver3.RecordedRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** The desktop [CdpEmbedSniffer] against the local test site, with the installed Chrome / Edge / Chromium (skipped without one). */
class CdpEmbedSnifferTest {
    private fun withSniffer(test: String, blocked: Set<String> = emptySet(), block: suspend (CdpEmbedSniffer, LocalPages, ExtensionLogStore) -> Unit) {
        val exe = LocalPages.browser() ?: return LocalPages.skip(test)
        val host = LocalPages.host(exe)
        val logs = ExtensionLogStore()
        val sniffer = CdpEmbedSniffer(host, logs, blockedHosts = blocked, log = {}, nudgeDelayMs = 300, embedUrlCheck = { true })
        LocalPages().use { pages ->
            runBlocking {
                try {
                    block(sniffer, pages, logs)
                } finally {
                    host.shutdown()
                }
            }
        }
    }

    /**
     * The request for [path] as the local server recorded it. The sniffer answers on `Network.requestWillBeSent`, before the request
     * leaves the browser, so it can reach the server a moment after [CdpEmbedSniffer.sniff] returned (seen with Chrome on the CI
     * runner): waited for, up to [timeoutMs].
     */
    private fun LocalPages.awaitRequest(path: String, timeoutMs: Long = 5_000, also: (RecordedRequest) -> Boolean = { true }): RecordedRequest {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            requests.firstOrNull { it.url.encodedPath == path && also(it) }?.let { return it }
            if (System.currentTimeMillis() > deadline) fail("$path never reached the local server in $timeoutMs ms: ${paths()}")
            Thread.sleep(20)
        }
    }

    @Test
    fun capturesTheStreamOfACrossSiteIframeAfterTheNudge() = withSniffer("capturesTheStreamOfACrossSiteIframeAfterTheNudge") { sniffer, pages, logs ->
        val cap = sniffer.sniff(pages.base + "/player", emptyMap(), "ext", 15_000)
        assertEquals(Container.HLS, cap.container)
        assertEquals("${pages.other}/hls/master.m3u8?token=222", cap.url)
        assertEquals("${pages.other}/embed?id=42", cap.headers["Referer"])
        assertFalse(cap.headers["User-Agent"].orEmpty().contains("Headless"), cap.headers.toString())
        assertTrue(cap.headers["User-Agent"].orEmpty().isNotEmpty())
        // the XHR really went out from the out-of-process iframe, after the click
        pages.awaitRequest("/hls/master.m3u8") { it.headers["Host"].orEmpty().startsWith("localhost") }
        assertTrue(logs.flow("ext").value.any { it.op == "embed" && it.message.contains("-> HLS") })
    }

    @Test
    fun theExtensionHeadersGoOnTheEmbedPageOnly() = withSniffer("theExtensionHeadersGoOnTheEmbedPageOnly") { sniffer, pages, _ ->
        val cap = sniffer.sniff(
            pages.other + "/embed?id=42",
            mapOf("Referer" to pages.base + "/watch", "X-Ext" to "1", "User-Agent" to "NijiyomeTest/1.0"),
            "ext",
            15_000,
        )
        assertEquals("${pages.other}/hls/master.m3u8?token=222", cap.url)
        val embed = pages.awaitRequest("/embed")
        assertEquals("1", embed.headers["X-Ext"])
        assertEquals("NijiyomeTest/1.0", embed.headers["User-Agent"])
        assertNull(cap.headers["X-Ext"], "extra headers are for the embed page itself, as on Android")
        // sniff() closes the tab right after Network.requestWillBeSent, so Chromium may abort the media request before it hits LocalPages.
        val media = runCatching { pages.awaitRequest("/hls/master.m3u8", timeoutMs = 1_500) }.getOrNull()
        if (media != null) {
            assertNull(media.headers["X-Ext"], "extra headers are for the embed page itself, as on Android")
        }
        assertEquals("NijiyomeTest/1.0", cap.headers["User-Agent"])
    }

    @Test
    fun adHostsAreAnsweredEmptyWithoutReachingTheNetwork() = withSniffer("adHostsAreAnsweredEmptyWithoutReachingTheNetwork", blocked = setOf("localhost")) { sniffer, pages, _ ->
        // the embed is on 127.0.0.1 and the "ad" on localhost, which is on the block list here
        assertFailsWith<EmbedSniffException> { sniffer.sniff(pages.base + "/player-ad", emptyMap(), "ext", 5_000) }
        assertTrue(pages.paths("localhost").isEmpty(), "nothing reached the blocked host: ${pages.paths("localhost")}")
        // an embed on a blocked host is refused before opening anything
        val e = assertFailsWith<EmbedSniffException> { sniffer.sniff(pages.other + "/embed?id=42", emptyMap(), "ext", 5_000) }
        assertEquals(EmbedSniffException.BLOCKED, e.reason)
    }

    @Test
    fun missingPagesAndPagesWithoutMedia() = withSniffer("missingPagesAndPagesWithoutMedia") { sniffer, pages, _ ->
        assertEquals(EmbedSniffException.NO_MEDIA, assertFailsWith<EmbedSniffException> { sniffer.sniff(pages.base + "/gone", emptyMap(), "ext", 5_000) }.reason)
        val started = System.currentTimeMillis()
        assertEquals(EmbedSniffException.TIMEOUT, assertFailsWith<EmbedSniffException> { sniffer.sniff(pages.base + "/still", emptyMap(), "ext", 5_000) }.reason)
        assertTrue(System.currentTimeMillis() - started < 9_000)
    }

    @Test
    fun noBrowserMeansUnavailable() = runBlocking<Unit> {
        val sniffer = CdpEmbedSniffer(ChromiumHost(executable = { null }, proxy = { null }), blockedHosts = emptySet(), log = {})
        assertFalse(sniffer.available)
        val e = assertFailsWith<EmbedSniffException> { sniffer.sniff("https://embed.example.com/e/1", emptyMap(), "ext", 5_000) }
        assertEquals(EmbedSniffException.UNAVAILABLE, e.reason)
    }

    @Test
    fun theAdListOfAndroidIsPackagedForDesktop() {
        val hosts = CdpEmbedSniffer.loadBlockedHosts()
        assertTrue(hosts.size > 20, "adblock-hosts.txt is a desktop resource too (${hosts.size} hosts)")
    }
}
