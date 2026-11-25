// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.BrowserError
import app.nijiyome.extensions.BrowserId
import app.nijiyome.extensions.BrowserPolicy
import app.nijiyome.extensions.BrowserRuntime
import app.nijiyome.extensions.BrowserScripts
import app.nijiyome.extensions.ExtensionLogStore
import app.nijiyome.extensions.browserOperationTimeoutMs
import app.nijiyome.extensions.browserWantsWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * [BrowserRuntime] of the desktop (1.1, F4) over [ChromiumHost]: the same contract as Android's `WebViewBrowserRuntime`.
 *
 * * One hidden page per browser, each in its own browser context: its cookies and storage exist only for it and are gone on close
 *   (Android shares the app-wide `CookieManager` and wipes per host instead).
 * * Every request of the page and its iframes goes through [subresourceAllowed] (the provider domains + `challenges.cloudflare.com`),
 *   the rest is refused; `evaluate` re-checks the current page strictly with [urlAllowed].
 * * Popups and downloads are refused; `headless: false` is only logged (the browser always stays hidden).
 * * Each operation times out after `clamp(options.timeout, 1, 30)` s ([BrowserError.Timeout]); a browser closes itself
 *   [BrowserPolicy.AUTO_CLOSE_S] after creation; at most [BrowserPolicy.MAX_BROWSERS_PER_EXTENSION] per extension.
 *
 * [urlAllowed] / [subresourceAllowed] are [BrowserPolicy]'s; tests pass looser ones to reach a local `http://127.0.0.1` page.
 */
class CdpBrowserRuntime(
    private val host: ChromiumHost,
    private val logs: ExtensionLogStore? = null,
    private val urlAllowed: (String, List<String>) -> Boolean = BrowserPolicy::urlAllowed,
    private val subresourceAllowed: (String, List<String>) -> Boolean = BrowserPolicy::subresourceAllowed,
) : BrowserRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = ConcurrentHashMap<BrowserId, Session>()
    private val createLock = Mutex()
    private val seq = AtomicLong(1)

    private class Session(val id: BrowserId, val extId: String, val domains: List<String>, val timeoutMs: Long) {
        @Volatile var page: CdpPage? = null
        var autoClose: Job? = null
    }

    override val available: Boolean get() = host.available

    override suspend fun newBrowser(extId: String, allowedDomains: List<String>, options: JsonObject): BrowserId {
        if (allowedDomains.isEmpty()) throw BrowserError.NoDomains()
        val timeoutMs = browserOperationTimeoutMs(options)
        if (browserWantsWindow(options)) logs?.ok(extId, "browser", "headless: false is ignored on desktop: the browser stays hidden")
        val session = createLock.withLock {
            if (sessions.values.count { it.extId == extId } >= BrowserPolicy.MAX_BROWSERS_PER_EXTENSION) throw BrowserError.Limit()
            val id: BrowserId = "tk-browser-${seq.getAndIncrement()}"
            Session(id, extId, allowedDomains.toList(), timeoutMs).also { sessions[id] = it }
        }
        session.autoClose = scope.launch {
            delay(BrowserPolicy.AUTO_CLOSE_S * 1000L)
            if (sessions.containsKey(session.id)) {
                logs?.ok(extId, "browser", "browser closed automatically after ${BrowserPolicy.AUTO_CLOSE_S} s")
                close(session.id)
            }
        }
        try {
            // starting the browser itself may take a few seconds the first time: it gets the operation budget plus a margin
            val page = within(timeoutMs + START_MARGIN_MS) { host.openPage() }
            page.policy = { req -> if (navigationAllowed(req.url, session.domains)) FetchDecision.Continue() else FetchDecision.Fail }
            session.page = page
            if (!sessions.containsKey(session.id)) page.close() // closed meanwhile
        } catch (e: Throwable) {
            sessions.remove(session.id)
            session.autoClose?.cancel()
            session.page?.close()
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            logs?.err(extId, "browser", "the hidden browser could not be created: ${e.message ?: e.javaClass.simpleName}")
            throw if (e is BrowserError) e else BrowserError.Unavailable()
        }
        return session.id
    }

    override suspend fun navigate(id: BrowserId, url: String) {
        val s = sessions[id] ?: throw BrowserError.Closed()
        if (!urlAllowed(url, s.domains)) throw BrowserError.UrlNotAllowed(url)
        val page = s.page ?: throw BrowserError.Closed()
        within(s.timeoutMs) {
            try {
                page.navigate(url)
            } catch (e: java.io.IOException) {
                if (page.isClosed || !sessions.containsKey(id)) throw BrowserError.Closed()
                throw BrowserError.Failed(e.message ?: "ChromeDP navigation failed")
            }
        }
    }

    override suspend fun evaluate(id: BrowserId, expression: String): JsonElement {
        val s = sessions[id] ?: throw BrowserError.Closed()
        if (expression.toByteArray(Charsets.UTF_8).size > BrowserPolicy.MAX_EXPRESSION_BYTES) throw BrowserError.Failed("Invalid ChromeDP expression")
        val page = s.page ?: throw BrowserError.Closed()
        val current = page.mainUrl
        if (!urlAllowed(current, s.domains)) throw BrowserError.UrlNotAllowed(current)
        val raw = within(s.timeoutMs) {
            try {
                page.evaluateRaw(capped(BrowserScripts.wrapExpression(expression)))
            } catch (e: CdpTooLarge) {
                throw BrowserError.TooLarge()
            } catch (e: java.io.IOException) {
                if (page.isClosed || !sessions.containsKey(id)) throw BrowserError.Closed()
                throw BrowserError.Failed("ChromeDP evaluation failed")
            }
        }
        return parseEnvelope(raw)
    }

    override suspend fun cookies(id: BrowserId, urls: List<String>): List<JsonObject> {
        val s = sessions[id] ?: throw BrowserError.Closed()
        if (urls.isEmpty()) throw BrowserError.Failed("ChromeDP cookie URLs are required")
        urls.firstOrNull { !urlAllowed(it, s.domains) }?.let { throw BrowserError.UrlNotAllowed(it) }
        val page = s.page ?: throw BrowserError.Closed()
        return within(s.timeoutMs) {
            try {
                page.cookies(urls.take(BrowserPolicy.MAX_COOKIE_URLS)).map(::cookieJson)
            } catch (e: java.io.IOException) {
                throw if (page.isClosed) BrowserError.Closed() else BrowserError.Failed("ChromeDP cookies failed")
            }
        }
    }

    override suspend fun close(id: BrowserId) {
        val s = sessions.remove(id) ?: return
        s.autoClose?.cancel()
        s.page?.close()
    }

    private fun navigationAllowed(url: String, domains: List<String>): Boolean =
        url == "about:blank" || url == "about:srcdoc" || url.startsWith("data:") || url.startsWith("blob:") || subresourceAllowed(url, domains)

    private suspend fun <T> within(timeoutMs: Long, block: suspend () -> T): T =
        try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            throw BrowserError.Timeout()
        }

    companion object {
        private const val START_MARGIN_MS = 35_000L

        /** What [capped] answers instead of a result over [BrowserPolicy.MAX_RESULT_BYTES]. */
        internal const val TOO_LARGE_MARK = "__nijiyome_result_too_large__"

        /**
         * 1.1 review #9: the result is measured in the page and never sent when it is too large (a `'a'.repeat(250e6)` would otherwise
         * cross the pipe whole). Characters are counted, a bound below the bytes; the transport caps the rest ([MAX_FRAME_BYTES]).
         */
        internal fun capped(wrapped: String): String =
            "(function(){var s=$wrapped;return (typeof s==='string'&&s.length>${BrowserPolicy.MAX_RESULT_BYTES})?'$TOO_LARGE_MARK':s;})()"

        /** The JSON string [BrowserScripts.wrapExpression] returns, as `Runtime.evaluate` hands it back (encoded once). */
        internal fun parseEnvelope(raw: JsonElement): JsonElement {
            val bad = { BrowserError.Failed("ChromeDP evaluation failed") }
            val text = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw bad()
            if (text == TOO_LARGE_MARK) throw BrowserError.TooLarge()
            if (text.length > BrowserPolicy.MAX_RESULT_BYTES) throw BrowserError.TooLarge()
            val env = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw bad()
            return if ((env["ok"] as? JsonPrimitive)?.content == "true") {
                env["v"] ?: JsonNull
            } else {
                throw BrowserError.Failed((env["e"] as? JsonPrimitive)?.content ?: "ChromeDP evaluation failed")
            }
        }

        /** CDP `Network.Cookie` -> the documented shape `{ name, value, domain, path, expires, size, httpOnly, secure, session, sameSite }`. */
        internal fun cookieJson(c: JsonObject): JsonObject = buildJsonObject {
            fun s(k: String) = (c[k] as? JsonPrimitive)?.contentOrNull
            put("name", s("name").orEmpty())
            put("value", s("value").orEmpty())
            put("domain", s("domain").orEmpty())
            put("path", s("path") ?: "/")
            put("expires", s("expires")?.toDoubleOrNull() ?: -1.0)
            put("size", s("size")?.toDoubleOrNull()?.toInt() ?: (s("name").orEmpty().length + s("value").orEmpty().length))
            put("httpOnly", s("httpOnly") == "true")
            put("secure", s("secure") == "true")
            put("session", s("session") == "true")
            put("sameSite", s("sameSite") ?: "Lax")
        }
    }
}
