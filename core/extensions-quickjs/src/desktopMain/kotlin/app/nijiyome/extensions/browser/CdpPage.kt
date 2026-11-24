// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.browser.ChromiumHost.Companion.params
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** One request the page is about to send, paused by the `Fetch` domain until [CdpPage.policy] answers. */
internal data class PausedRequest(
    val url: String,
    val resourceType: String,
    /** The top-level document of the page (a navigation of the main frame). */
    val mainFrameDocument: Boolean,
    val headers: Map<String, String>,
)

internal sealed interface FetchDecision {
    /** Let it go; [extraHeaders] are set on top of its own (same name replaced). */
    data class Continue(val extraHeaders: Map<String, String> = emptyMap()) : FetchDecision

    /** Refused (`net::ERR_BLOCKED_BY_CLIENT`). */
    data object Fail : FetchDecision

    /** Answered with an empty 204 without reaching the network (ad hosts of the sniffer, as Android). */
    data object Empty : FetchDecision
}

/**
 * A hidden page in its own browser context. Every request of the page, of its out-of-process iframes and of its workers is paused
 * and decided by [policy]; downloads are denied; popups are closed as soon as they appear; every authentication prompt is cancelled.
 */
internal class CdpPage private constructor(
    private val conn: CdpConnection,
    val contextId: String,
    private val userAgent: String,
    private val onClosed: () -> Unit,
) {
    lateinit var targetId: String
        private set
    lateinit var session: String
        private set

    @Volatile var policy: (PausedRequest) -> FetchDecision = { FetchDecision.Continue() }

    /** Every `Network.*` event of the page and its iframes (runs on the connection's thread: do not block). */
    @Volatile var onNetwork: (CdpEvent) -> Unit = {}

    /** URL of the main frame's current document. */
    @Volatile var mainUrl: String = "about:blank"
        private set

    /** HTTP status of the last main-frame document; null before one arrives. */
    @Volatile var mainStatus: Int? = null
        private set

    /** Flattened sessions of the out-of-process iframes (and workers) of this page. */
    val childSessions: Set<String> get() = children.keys

    private val children = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    @Volatile private var loadWaiter: CompletableDeferred<Unit>? = null
    @Volatile private var loadCount = 0
    private var unlisten: () -> Unit = {}
    private var unwatch: () -> Unit = {}

    val isClosed: Boolean get() = closed.get() || conn.closed

    /** Number of `load` events of the main frame so far. */
    val loads: Int get() = loadCount

    private fun ours(session: String?): Boolean = session != null && (session == this.session || children.containsKey(session))

    private fun onEvent(e: CdpEvent) {
        when (e.method) {
            "Fetch.requestPaused" -> if (ours(e.session)) decide(e)
            "Fetch.authRequired" -> if (ours(e.session)) authenticate(e)
            "Target.attachedToTarget" -> if (ours(e.session)) attached(e)
            "Target.detachedFromTarget" -> e.params.str("sessionId")?.let { children.remove(it) }
            "Target.targetCreated" -> {
                // a popup (window.open / target=_blank) of this context: closed, never shown or loaded further
                val info = e.params.obj("targetInfo") ?: return
                if (info.str("browserContextId") == contextId && info.str("type") == "page" && info.str("openerId") != null) {
                    info.str("targetId")?.let { conn.fire("Target.closeTarget", params("targetId" to it)) }
                }
            }
            "Page.loadEventFired" -> if (e.session == session) {
                loadCount++
                loadWaiter?.complete(Unit)
            }
            "Page.frameNavigated" -> if (e.session == session) {
                val frame = e.params.obj("frame") ?: return
                if (frame.str("parentId") == null) frame.str("url")?.let { mainUrl = it }
            }
            else -> if (e.method.startsWith("Network.") && ours(e.session)) {
                if (e.method == "Network.responseReceived" && e.session == session && e.params.str("type") == "Document" &&
                    e.params.str("frameId") == targetId
                ) {
                    mainStatus = (e.params.obj("response")?.get("status") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()
                }
                onNetwork(e)
            }
        }
    }

    private fun decide(e: CdpEvent) {
        val id = e.params.str("requestId") ?: return
        val req = e.params.obj("request") ?: return
        val headers = req.obj("headers")?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()
        val type = e.params.str("resourceType").orEmpty()
        val paused = PausedRequest(
            url = req.str("url").orEmpty(),
            resourceType = type,
            mainFrameDocument = type == "Document" && e.session == session && e.params.str("frameId") == targetId,
            headers = headers,
        )
        val decision = runCatching { policy(paused) }.getOrDefault(FetchDecision.Fail)
        when (decision) {
            is FetchDecision.Continue -> if (decision.extraHeaders.isEmpty()) {
                conn.fire("Fetch.continueRequest", params("requestId" to id), e.session)
            } else {
                val merged = LinkedHashMap<String, String>()
                for ((k, v) in headers) if (decision.extraHeaders.keys.none { it.equals(k, ignoreCase = true) }) merged[k] = v
                merged.putAll(decision.extraHeaders)
                val list = buildJsonArray { for ((k, v) in merged) add(buildJsonObject { put("name", k); put("value", v) }) }
                conn.fire("Fetch.continueRequest", params("requestId" to id, "headers" to list), e.session)
            }
            FetchDecision.Fail -> conn.fire("Fetch.failRequest", params("requestId" to id, "errorReason" to "BlockedByClient"), e.session)
            FetchDecision.Empty -> conn.fire(
                "Fetch.fulfillRequest",
                params("requestId" to id, "responseCode" to 204, "responseHeaders" to JsonArray(emptyList())),
                e.session,
            )
        }
    }

    /**
     * Any HTTP authentication prompt (a site's 401, or a 407: the outbound proxy is the [BrowserGateway]'s business, Chromium never
     * talks to it) is cancelled, once: the page then gets the 401 / 407 response itself, no retry loop (1.1 review #10).
     */
    private fun authenticate(e: CdpEvent) {
        val id = e.params.str("requestId") ?: return
        val answer = buildJsonObject { put("response", "CancelAuth") }
        conn.fire("Fetch.continueWithAuth", params("requestId" to id, "authChallengeResponse" to answer), e.session)
    }

    private fun attached(e: CdpEvent) {
        val child = e.params.str("sessionId") ?: return
        val type = e.params.obj("targetInfo")?.str("type").orEmpty()
        if (type == "page") {
            // a popup attached through auto-attach: closed
            e.params.obj("targetInfo")?.str("targetId")?.let { conn.fire("Target.closeTarget", params("targetId" to it)) }
            return
        }
        children[child] = type
        scope.launch {
            runCatching { setup(child, main = false, type = type) }
            conn.fire("Runtime.runIfWaitingForDebugger", session = child)
        }
    }

    /** Network, Fetch and auto-attach on [s] (the page, or one of its iframes / workers). */
    private suspend fun setup(s: String, main: Boolean, type: String = "page") {
        if (type == "iframe" || main) {
            conn.call("Network.enable", NETWORK_ENABLE, session = s)
            conn.call("Fetch.enable", params("patterns" to buildJsonArray { add(buildJsonObject { put("urlPattern", "*") }) }, "handleAuthRequests" to true), session = s)
            conn.call("Target.setAutoAttach", params("autoAttach" to true, "waitForDebuggerOnStart" to true, "flatten" to true), session = s)
            // the same user agent in out-of-process iframes as in the page (the replayed headers must match what the site saw)
            conn.call("Network.setUserAgentOverride", params("userAgent" to userAgent), s)
        } else {
            // workers: their requests are decided too where the protocol allows it
            runCatching { conn.call("Network.enable", NETWORK_ENABLE, session = s) }
            runCatching {
                conn.call("Fetch.enable", params("patterns" to buildJsonArray { add(buildJsonObject { put("urlPattern", "*") }) }, "handleAuthRequests" to true), session = s)
            }
        }
        if (main) conn.call("Page.enable", session = s)
    }

    /** Loads [url] and returns once its `load` event fires. A refused or failed navigation throws with Chromium's error text. */
    suspend fun navigate(url: String, referrer: String? = null) {
        val waiter = CompletableDeferred<Unit>()
        loadWaiter = waiter
        mainStatus = null
        val res = conn.call("Page.navigate", params("url" to url, "referrer" to referrer), session)
        res.str("errorText")?.takeIf { it.isNotEmpty() }?.let { throw IOException(it) }
        waiter.await()
    }

    /** Starts loading [url] without waiting (the sniffer watches the network instead). */
    suspend fun startNavigation(url: String, referrer: String? = null): String? {
        mainStatus = null
        return conn.call("Page.navigate", params("url" to url, "referrer" to referrer), session).str("errorText")?.takeIf { it.isNotEmpty() }
    }

    /** `Runtime.evaluate` with `returnByValue` in [on] (default: the page); its JSON value. An exception thrown by the page throws. */
    suspend fun evaluateRaw(expression: String, on: String = session): JsonElement {
        val res = conn.call("Runtime.evaluate", params("expression" to expression, "returnByValue" to true, "awaitPromise" to false), on)
        res.obj("exceptionDetails")?.let { d ->
            throw IOException(d.obj("exception")?.str("description") ?: d.str("text") ?: "evaluation failed")
        }
        return res.obj("result")?.get("value") ?: JsonNull
    }

    /** `Network.getCookies` for [urls] in this page's context. */
    suspend fun cookies(urls: List<String>): List<JsonObject> {
        val res = conn.call("Network.getCookies", params("urls" to buildJsonArray { urls.forEach { add(JsonPrimitive(it)) } }), session)
        return (res["cookies"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    /** Closes the page and throws its whole browser context away (cookies, storage, cache). Idempotent. */
    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        unlisten()
        unwatch()
        loadWaiter?.completeExceptionally(IOException("closed"))
        withContext(NonCancellable) {
            runCatching { conn.call("Target.disposeBrowserContext", params("browserContextId" to contextId), timeoutMs = 5_000) }
        }
        scope.cancel()
        onClosed()
    }

    companion object {
        /** No response bodies kept by the browser for us, and request bodies in events capped (they are never needed). */
        private val NETWORK_ENABLE = params("maxTotalBufferSize" to 0, "maxResourceBufferSize" to 0, "maxPostDataSize" to 1024)

        suspend fun open(conn: CdpConnection, userAgent: String, onClosed: () -> Unit): CdpPage {
            val ctx = conn.call("Target.createBrowserContext", params("disposeOnDetach" to true)).str("browserContextId")
                ?: throw IOException("no browser context")
            val page = CdpPage(conn, ctx, userAgent, onClosed)
            try {
                page.unlisten = conn.listen(page::onEvent)
                page.unwatch = conn.onClosed { page.loadWaiter?.completeExceptionally(IOException("the browser is closed")) }
                runCatching { conn.call("Target.setDiscoverTargets", params("discover" to true)) }
                runCatching { conn.call("Browser.setDownloadBehavior", params("behavior" to "deny", "browserContextId" to ctx)) }
                page.targetId = conn.call("Target.createTarget", params("url" to "about:blank", "browserContextId" to ctx)).str("targetId")
                    ?: throw IOException("no page")
                page.session = conn.call("Target.attachToTarget", params("targetId" to page.targetId, "flatten" to true)).str("sessionId")
                    ?: throw IOException("no page session")
                page.setup(page.session, main = true)
            } catch (e: Throwable) {
                page.close()
                throw e
            }
            return page
        }

        /** `{ "a": "b" }` headers of a CDP request as a plain map. */
        fun headerMap(o: JsonObject?): Map<String, String> = o?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()

        internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    }
}
