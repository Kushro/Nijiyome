// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.BrowserScripts
import app.nijiyome.extensions.EmbedDetector
import app.nijiyome.extensions.ExtensionLogStore
import app.nijiyome.model.EMBED_SNIFF_TIMEOUT_MS
import app.nijiyome.model.EmbedCapture
import app.nijiyome.model.EmbedSniffException
import app.nijiyome.model.EmbedSniffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [EmbedSniffer] of the desktop (1.1, F4) over [ChromiumHost]: the same behaviour as Android's `WebViewEmbedSniffer`.
 *
 * The embed is loaded in a fresh browser context, its first document request carrying the extension's headers (the user agent through
 * the page's user agent). Every request of the page and of its iframes is paused: ad / popunder hosts ([EmbedDetector.isBlockedHost],
 * the same `adblock-hosts.txt` Android ships) get an empty 204, top-level navigations outside the embed's site are refused
 * ([EmbedDetector.navigationAllowed]), and the first request [EmbedDetector.mediaContainer] recognises as HLS / MP4 completes the capture
 * with its request headers + the context's cookies for that URL + the user agent. [nudgeDelayMs] after the page's `load` the page
 * and each out-of-process iframe get the same nudge as Android ([BrowserScripts.NUDGE_JS]). After `timeoutMs` ->
 * [EmbedSniffException.TIMEOUT]; main-frame error -> BLOCKED (404 / 410 -> NO_MEDIA); no browser -> UNAVAILABLE. At most
 * [MAX_CONCURRENT] at once. One log line per sniff, host only.
 */
class CdpEmbedSniffer(
    private val host: ChromiumHost,
    private val logs: ExtensionLogStore? = null,
    private val blockedHosts: Set<String> = loadBlockedHosts(),
    private val log: (String) -> Unit = { System.err.println("I/Nijiyome: $it") },
    /** Wait after the page's `load` before the nudge (Android: 2 s). */
    private val nudgeDelayMs: Long = NUDGE_DELAY_MS,
    /** [embedUrlAllowed]; tests loosen it to sniff a local test page. */
    private val embedUrlCheck: (String) -> Boolean = ::embedUrlAllowed,
) : EmbedSniffer {
    private val permits = Semaphore(MAX_CONCURRENT)

    /** Whether a browser is there to sniff with (the source list hides embed pages otherwise). */
    val available: Boolean get() = host.available

    override suspend fun sniff(embedUrl: String, headers: Map<String, String>, extId: String, timeoutMs: Long): EmbedCapture {
        val embedHost = EmbedDetector.hostOf(embedUrl) ?: throw EmbedSniffException(EmbedSniffException.BLOCKED)
        // 1.1 review #1: an extension cannot point the hidden browser at this PC or the LAN (the gateway refuses it again at the socket)
        if (!embedUrlCheck(embedUrl)) {
            note(extId, false, "embed $embedHost refused: not a public http(s) address")
            throw EmbedSniffException(EmbedSniffException.BLOCKED)
        }
        if (EmbedDetector.isBlockedHost(embedHost, blockedHosts)) {
            note(extId, false, "embed host $embedHost is on the ad block list")
            throw EmbedSniffException(EmbedSniffException.BLOCKED)
        }
        if (!host.available) {
            note(extId, false, "embed $embedHost: no Chrome / Edge / Chromium for the hidden browser")
            throw EmbedSniffException(EmbedSniffException.UNAVAILABLE)
        }
        val budget = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        return permits.withPermit {
            val started = System.currentTimeMillis()
            var page: CdpPage? = null
            val watch = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                withTimeout(budget) {
                    val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }
                    val p = try {
                        host.openPage(userAgent = ua)
                    } catch (e: java.io.IOException) {
                        throw EmbedSniffException(EmbedSniffException.UNAVAILABLE, e)
                    }
                    page = p
                    val capture = capture(p, embedUrl, headers, watch)
                    note(extId, true, "embed $embedHost -> ${capture.container.name} from ${EmbedDetector.hostOf(capture.url) ?: "?"} in ${System.currentTimeMillis() - started} ms")
                    capture
                }
            } catch (e: TimeoutCancellationException) {
                note(extId, false, "embed $embedHost: no media request within ${budget / 1000} s")
                throw EmbedSniffException(EmbedSniffException.TIMEOUT, e)
            } catch (e: EmbedSniffException) {
                note(extId, false, "embed $embedHost failed: ${e.reason}")
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note(extId, false, "embed $embedHost: the hidden browser is unavailable (${e.javaClass.simpleName})")
                throw EmbedSniffException(EmbedSniffException.UNAVAILABLE, e)
            } finally {
                watch.cancel()
                page?.let { withContext(NonCancellable) { it.close() } }
            }
        }
    }

    private suspend fun capture(page: CdpPage, embedUrl: String, headers: Map<String, String>, watch: CoroutineScope): EmbedCapture {
        val result = CompletableDeferred<EmbedCapture>()
        val first = AtomicBoolean(true)
        val loadHeaders = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) && !it.equals("Referer", ignoreCase = true) }
        val referrer = headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
        page.policy = { req ->
            val reqHost = EmbedDetector.hostOf(req.url)
            when {
                !schemeAllowed(req.url) -> FetchDecision.Fail
                reqHost != null && EmbedDetector.isBlockedHost(reqHost, blockedHosts) -> FetchDecision.Empty
                req.mainFrameDocument && !EmbedDetector.navigationAllowed(embedUrl, req.url) -> FetchDecision.Fail
                // the extension's headers on the embed page itself only (Android: loadUrl(url, headers))
                req.mainFrameDocument && first.getAndSet(false) -> FetchDecision.Continue(loadHeaders)
                else -> FetchDecision.Continue()
            }
        }
        page.onNetwork = { e ->
            if (e.method == "Network.requestWillBeSent" && !result.isCompleted) {
                val req = e.params.obj("request")
                val url = req?.str("url")
                if (url != null && !EmbedDetector.isIgnorable(url)) {
                    val reqHeaders = CdpPage.headerMap(req.obj("headers"))
                    val accept = reqHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
                    val container = EmbedDetector.mediaContainer(url, accept?.takeIf { it.contains("mpegurl", ignoreCase = true) })
                    if (container != null) {
                        watch.launch {
                            val cookie = runCatching {
                                page.cookies(listOf(url)).mapNotNull { c -> c.str("name")?.let { n -> "$n=${c.str("value").orEmpty()}" } }
                                    .joinToString("; ").takeIf { it.isNotEmpty() }
                            }.getOrNull()
                            val ua = reqHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                            val replay = EmbedDetector.replayHeaders(reqHeaders, cookie, ua).toMutableMap()
                            if (replay.keys.none { it.equals("Referer", ignoreCase = true) }) replay["Referer"] = embedUrl
                            result.complete(EmbedCapture(url, replay, container))
                        }
                    }
                }
            }
        }
        val error = page.startNavigation(embedUrl, referrer)
        if (error != null) throw EmbedSniffException(EmbedSniffException.BLOCKED)
        // main-frame HTTP errors and the nudge after the page's load event
        watch.launch {
            var nudged = -1
            while (!result.isCompleted) {
                page.mainStatus?.let { code ->
                    if (code >= 400) result.completeExceptionally(EmbedSniffException(if (code == 404 || code == 410) EmbedSniffException.NO_MEDIA else EmbedSniffException.BLOCKED))
                }
                if (page.loads > 0 && nudged != page.loads) {
                    nudged = page.loads
                    watch.launch {
                        delay(nudgeDelayMs)
                        if (!result.isCompleted) nudge(page)
                    }
                }
                delay(POLL_MS)
            }
        }
        return result.await()
    }

    private suspend fun nudge(page: CdpPage) {
        runCatching { page.evaluateRaw(BrowserScripts.NUDGE_JS) }
        for (child in page.childSessions) runCatching { page.evaluateRaw(BrowserScripts.NUDGE_JS, on = child) }
    }

    private fun note(extId: String, ok: Boolean, message: String) {
        if (ok) logs?.ok(extId, "embed", message) else logs?.warn(extId, "embed", message)
        log("[$extId] $message")
    }

    companion object {
        const val MAX_CONCURRENT: Int = 2
        val DEFAULT_TIMEOUT_MS: Long = EMBED_SNIFF_TIMEOUT_MS
        private const val MIN_TIMEOUT_MS = 5_000L
        private const val MAX_TIMEOUT_MS = 60_000L
        const val NUDGE_DELAY_MS = 2_000L
        private const val POLL_MS = 50L

        /**
         * The embed page itself: http(s), no user info, and not a local / private literal address or local name ([BrowserGateway]'s rules;
         * names are resolved, and refused if private, by the gateway at connection time).
         */
        fun embedUrlAllowed(url: String): Boolean {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "http" && scheme != "https") return false
            if (uri.rawUserInfo != null) return false
            val host = uri.host?.takeIf { it.isNotEmpty() } ?: return false
            val literal = BrowserGateway.literalOf(host)
            if (literal != null) return !app.nijiyome.network.AddressRules.isBlocked(literal)
            // short / numeric IPv4 forms (`127.1`, `2130706433`, `0x7f.1`) that browsers turn into addresses: never a name
            if (NUMERIC_HOST.matches(host)) return false
            return !BrowserGateway.isLocalName(host)
        }

        private val NUMERIC_HOST = Regex("^(0x[0-9a-f]+|[0-9]+)(\\.(0x[0-9a-f]+|[0-9]+))*\\.?$", RegexOption.IGNORE_CASE)

        /** Schemes a hidden page may load: the web and the in-page ones (no `file:`, `chrome:`, `ftp:`…). */
        fun schemeAllowed(url: String): Boolean {
            val u = url.lowercase()
            return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("data:") || u.startsWith("blob:") || u.startsWith("about:")
        }

        /** The ad host list Android ships as an asset, packaged here as a resource (empty when missing: nothing is blocked then). */
        fun loadBlockedHosts(): Set<String> =
            runCatching {
                CdpEmbedSniffer::class.java.getResourceAsStream("/adblock-hosts.txt")?.bufferedReader()?.use { EmbedDetector.parseHostList(it.readText()) }
            }.getOrNull().orEmpty()
    }
}
