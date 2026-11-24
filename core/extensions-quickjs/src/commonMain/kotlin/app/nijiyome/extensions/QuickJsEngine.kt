// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * `quickjs-kt` 1.0.18 implementation of [JsEngine]: ONE runtime per extension, [JsEngine.MEMORY_LIMIT_BYTES] memory limit, [JsEngine.CALL_TIMEOUT_MS] per host call, the globals of
 * contracts/extension-host/globals.v1.md provided by `nijiyome/host-prelude.js` on top of a handful of `__host_*` bridge functions.
 * Everything crossing the bridge is a JSON string, so no QuickJS object marshalling is relied upon.
 *
 * Each runtime lives on its own thread ([QuickJsThread]): created there, configured there and always evaluated there. QuickJS keeps
 * its stack limit as an address on the stack of the thread that set it; evaluated from other threads (a shared dispatcher hops
 * threads after every `await`) the limit never trips and a runaway recursion overflows the real stack (SIGSEGV, the whole app).
 */
class QuickJsEngine(
    /** android wave (12 §3.7): the hidden-WebView browser handed to bundles with permitted `browserDomains`; null = none. */
    private val browserRuntime: BrowserRuntime? = null,
) : JsEngine {
    private val prelude: String by lazy {
        QuickJsEngine::class.java.getResourceAsStream("/nijiyome/host-prelude.js")?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("host-prelude.js missing from resources")
    }

    override suspend fun open(manifest: ExtensionManifest, bundle: String, host: JsHost): JsSession {
        val thread = QuickJsThread(manifest.id)
        val qjs = try {
            thread.run {
                QuickJs.create(thread.dispatcher).apply {
                    memoryLimit = JsEngine.MEMORY_LIMIT_BYTES
                    // JS_SetMaxStackSize takes the current stack pointer as the top: it has to run on the session thread.
                    maxStackSize = MAX_STACK_BYTES
                }
            }
        } catch (e: Throwable) {
            thread.close()
            throw e
        }
        // Browser runtime only for a bundle whose manifest has browserDomains covered by its network permissions (desktop host.rs).
        val domains = BrowserPolicy.browserDomains(manifest)
        val bridge = browserRuntime?.takeIf { domains.isNotEmpty() && it.available }?.let { runtime ->
            BrowserBridge(runtime, manifest.id, domains) { level, message -> host.event(level, "browser", message) }
        }
        val session = QuickJsSession(qjs, host, bridge, thread)
        try {
            // With a browser the interrupt budget is the 90 s of a browser call; the budget of a plain call is then enforced by the
            // watchdog in QuickJsSession.invoke.
            qjs.evaluationTimeoutMillis = if (bridge != null) BrowserPolicy.CALL_BUDGET_WITH_BROWSER_MS else JsEngine.CALL_TIMEOUT_MS
            thread.run { session.bind() }
            withTimeout(JsEngine.OPEN_TIMEOUT_MS) { thread.run {
                if (bridge != null) {
                    // A JSON string literal is a valid JS string literal (domains are [a-z0-9.-] only).
                    qjs.evaluate<Any?>(
                        "globalThis.__host_browser_domains = " + JsonPrimitive(JsonArray(domains.map { JsonPrimitive(it) }).toString()) + ";",
                        "browser-domains.js",
                    )
                }
                qjs.evaluate<Any?>(prelude, "host-prelude.js")
                // Same parameter order as the legacy `new Function` (globals.v1.md section 1), then the legacy `__tatakai_*__`
                // names so bundles built for the previous host keep working. Header and bundle share line 1.
                qjs.evaluate<Any?>(
                    "globalThis.__tk_bundle_fn = (function(module, exports, require, __nijiyome_fetch__, __nijiyome_anitomy__, " +
                        "__nijiyome_parse_html__, Buffer, process, global, setImmediate, clearImmediate, fetch, " +
                        "__tatakai_fetch__, __tatakai_anitomy__, __tatakai_parse_html__){" + bundle + "\n});",
                    "bundle.js",
                )
                qjs.evaluate<Any?>("__tk_instantiate()", "instantiate.js")
            } }
        } catch (e: Throwable) {
            runCatching { session.close() }
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            throw when (e) {
                is TimeoutCancellationException -> ExtError.Timeout()
                is ExtError -> e
                else -> session.mapFailure(e, "Extension failed to load")
            }
        }
        return session
    }

    companion object {
        /** QuickJS's own limit; [QuickJsThread.STACK_BYTES] leaves room below it for the JNI and Kotlin frames. */
        const val MAX_STACK_BYTES = 1024L * 1024
    }
}

/**
 * The one thread of a QuickJS runtime, with a stack well above [QuickJsEngine.MAX_STACK_BYTES]. [run] executes on it (and every
 * resumption of it, so a whole `evaluate` with its awaits stays there); the runtime's async host functions are dispatched to it too.
 */
internal class QuickJsThread(name: String) : AutoCloseable {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(null, task, "quickjs-" + name.take(40) + "-" + SEQUENCE.incrementAndGet(), STACK_BYTES).apply { isDaemon = true }
    }
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    suspend fun <T> run(block: suspend () -> T): T = withContext(dispatcher) { block() }

    /** Lets the thread end once its queue is empty (a call still running finishes first). */
    override fun close() = executor.shutdown()

    companion object {
        const val STACK_BYTES = 8L * 1024 * 1024
        private val SEQUENCE = AtomicInteger()
    }
}

internal class QuickJsSession(
    private val qjs: QuickJs,
    private val host: JsHost,
    /** Present only when the bundle may use the browser runtime (12 §3.7). */
    private val browser: BrowserBridge? = null,
    /** The thread the runtime was created on; every evaluation runs there (see [QuickJsThread]). */
    private val thread: QuickJsThread,
) : JsSession {
    private val mutex = Mutex()
    @Volatile private var softTimedOut = false
    private val html = HtmlRegistry()
    private val random = SecureRandom()
    @Volatile private var pendingArgs: String = "[]"
    /** BUG-099/100: host jobs of a call stop waiting once its result is known (see [HostCallGate]); cancelled with the session. */
    private val hostJobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = HostCallGate(hostJobs)
    /** The result JSON the prelude handed to `__host_settled` for the current call (kept if the evaluation fails afterwards). */
    @Volatile private var settledResult: String? = null
    /** BUG-128: the `onChunk` values of the current call as they arrive (what a timed-out call still returns, see [ExtError.Timeout]). */
    private val liveChunks = java.util.Collections.synchronizedList(ArrayList<JsonElement>())
    /** Number of the call in progress, handed to `__tk_call` (BUG-128). */
    @Volatile private var callId = 0

    private fun isCurrentCall(id: Any?): Boolean = (id as? Number)?.toInt() == callId

    fun bind() {
        qjs.function("__host_log") { a -> host.log(a[0].toString(), a[1].toString()); null }
        qjs.function("__host_args") { _ -> pendingArgs }
        // A sleep or a fetch still running when the call settles returns at once (the JS side already dropped its timers).
        qjs.asyncFunction("__host_sleep") { a ->
            val ms = ((a[0] as Number).toLong()).coerceIn(0, 15_000)
            gate.run(Unit) { delay(ms) }
            null
        }
        qjs.asyncFunction("__host_fetch") { a ->
            val request = a[0].toString()
            gate.run(ABANDONED_FETCH) { fetchJson(request) }
        }
        qjs.function("__host_chunk") { a ->
            if (isCurrentCall(a.getOrNull(1))) {
                (a.getOrNull(0) as? String)?.let { text -> runCatching { Json.parseToJsonElement(text) }.getOrNull()?.let(liveChunks::add) }
            }
            null
        }
        // BUG-128: only the call in progress may settle; a call left behind that finishes during a later one is ignored.
        qjs.function("__host_settled") { a ->
            if (isCurrentCall(a.getOrNull(1))) {
                settledResult = a.getOrNull(0) as? String
                gate.settle()
            }
            null
        }
        qjs.function("__host_hash") { a -> b64(digest(a[0].toString()).digest(unb64(a[1].toString()))) }
        qjs.function("__host_hmac") { a ->
            val algo = macName(a[0].toString())
            val mac = Mac.getInstance(algo)
            val key = unb64(a[1].toString())
            mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(1) else key, algo))
            b64(mac.doFinal(unb64(a[2].toString())))
        }
        qjs.function("__host_random") { a ->
            val n = (a[0] as Number).toInt().coerceIn(0, 65536)
            b64(ByteArray(n).also { random.nextBytes(it) })
        }
        qjs.function("__host_html") { a -> html.op(a[0].toString(), (a[1] as Number).toInt(), a[2].toString()) }
        // op: new | navigate | evaluate | cookies | close; resolves to the JSON envelope {"ok":true,"value":..} / {"ok":false,"error":".."}
        browser?.let { bridge ->
            qjs.asyncFunction("__host_browser") { a ->
                val op = a.getOrNull(0)?.toString().orEmpty()
                val id = a.getOrNull(1)?.toString().orEmpty()
                val arg = a.getOrNull(2)?.toString().orEmpty()
                // A late `close` still closes; any other operation of a settled call is dropped (the browser auto-closes).
                if (op == "close") bridge.call(op, id, arg) else gate.run(ABANDONED_BROWSER) { bridge.call(op, id, arg) }
            }
        }
    }

    private suspend fun fetchJson(requestJson: String): String {
        return try {
            val req = Json.parseToJsonElement(requestJson) as JsonObject
            val headers = (req["headers"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
            val body = (req["body"] as? JsonPrimitive)?.contentOrNull?.let(::unb64)
            val res = host.fetch(
                HostFetchRequest(
                    url = (req["url"] as JsonPrimitive).content,
                    method = (req["method"] as? JsonPrimitive)?.contentOrNull ?: "GET",
                    headers = headers,
                    body = body,
                ),
            )
            buildJsonObject {
                put("url", res.url); put("status", res.status); put("statusText", res.statusText)
                put("headers", JsonObject(res.headers.mapValues { JsonPrimitive(it.value) }))
                put("body", b64(res.body))
            }.toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExtError) {
            buildJsonObject { put("error", e.message ?: "Network error") }.toString()
        } catch (e: Exception) {
            buildJsonObject { put("error", "Network request failed") }.toString()
        }
    }

    override suspend fun hasMethod(name: String): Boolean {
        require(METHOD.matches(name)) { "bad method name" }
        return mutex.withLock { thread.run { qjs.evaluate<Boolean>("__tk_has(\"$name\")") } }
    }

    override suspend fun invoke(method: String, args: List<JsonElement>, collectChunks: Boolean): JsInvocation {
        require(METHOD.matches(method)) { "bad method name" }
        return mutex.withLock {
            pendingArgs = JsonArray(args).toString()
            html.clear()
            softTimedOut = false
            settledResult = null
            liveChunks.clear()
            val id = ++callId
            gate.begin()
            browser?.openedBrowser = false
            val startedNs = System.nanoTime()
            val text: String? = try {
                if (browser == null) {
                    withTimeout(JsEngine.CALL_TIMEOUT_MS + 1_000) {
                        thread.run { qjs.evaluate<String?>("await __tk_call(\"$method\", __host_args(), $collectChunks, $id)", "call.js") }
                    }
                } else {
                    // A call that opened a browser may run up to CALL_BUDGET_WITH_BROWSER_MS (Cloudflare solve); any other call keeps the
                    // CALL_TIMEOUT_MS budget, enforced by this watchdog because the interrupt budget of the runtime is the long one.
                    coroutineScope {
                        val eval = async { thread.run { qjs.evaluate<String?>("await __tk_call(\"$method\", __host_args(), $collectChunks, $id)", "call.js") } }
                        val watchdog = launch {
                            delay(JsEngine.CALL_TIMEOUT_MS)
                            if (!browser.openedBrowser) {
                                softTimedOut = true
                                runCatching { qjs.interruptEvaluation() }
                                eval.cancel()
                            }
                        }
                        try {
                            withTimeout(BrowserPolicy.CALL_BUDGET_WITH_BROWSER_MS + 1_000) { eval.await() }
                        } finally {
                            watchdog.cancel()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                runCatching { qjs.interruptEvaluation() }
                settledResult ?: throw timedOut()
            } catch (e: QuickJsInterruptedException) {
                settledResult ?: throw timedOut()
            } catch (e: CancellationException) {
                if (softTimedOut) settledResult ?: throw timedOut() else throw e
            } catch (e: Exception) {
                // The bundle's answer already arrived: a late failure of an abandoned job does not take it away.
                settledResult ?: run {
                    // BUG-128: the runtime's own deadline (evaluationTimeoutMillis) surfaces as the JS error "InternalError:
                    // interrupted", not as QuickJsInterruptedException: it is a timeout too, with the sources found so far.
                    val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
                    if (softTimedOut || isInterruption(e) || isSpentBudgetInterruption(e, elapsedMs, qjs.evaluationTimeoutMillis)) {
                        throw timedOut()
                    }
                    throw mapFailure(e, "Extension call failed")
                }
            } finally {
                gate.settle()
            }
            runCatching { thread.run { qjs.evaluate<Any?>("__tk_reset_timers()", "reset.js") } }
            val parsed = Json.parseToJsonElement(text ?: "{\"result\":null,\"chunks\":[]}") as JsonObject
            JsInvocation(parsed["result"] ?: JsonNull, (parsed["chunks"] as? JsonArray)?.toList().orEmpty())
        }
    }

    private fun timedOut(): ExtError.Timeout = ExtError.Timeout(JsInvocation(JsonNull, synchronized(liveChunks) { liveChunks.toList() }))

    fun mapFailure(e: Throwable, fallback: String): ExtError {
        val msg = e.message.orEmpty()
        return when {
            e is ExtError -> e
            msg.contains("__TK_NOT_IMPLEMENTED__") -> ExtError.NotImplemented("Bundle does not implement ${msg.substringAfter("__TK_NOT_IMPLEMENTED__").takeWhile { it.isLetterOrDigit() || it == '_' }}")
            msg.contains("out of memory", ignoreCase = true) -> ExtError.OutOfMemory()
            msg.contains(ExtError.PermissionDenied.MESSAGE) -> ExtError.PermissionDenied()
            else -> ExtError.BundleError(msg.lineSequence().firstOrNull()?.take(500).takeUnless { it.isNullOrBlank() } ?: fallback, e)
        }
    }

    override fun close() {
        gate.settle()
        hostJobs.cancel()
        // Not on the session thread: close waits for a running evaluation, which may need that thread to finish.
        runCatching { qjs.close() }
        thread.close()
        // Browsers this runtime still owns die with it (desktop runtime.rs close_owned); fire and forget.
        browser?.takeIf { it.openCount > 0 }?.let { bridge -> CLOSER.launch { bridge.closeAll() } }
    }

    private fun digest(algo: String): MessageDigest = MessageDigest.getInstance(
        when (algo) { "md5" -> "MD5"; "sha1" -> "SHA-1"; "sha256" -> "SHA-256"; "sha512" -> "SHA-512"; else -> throw IllegalArgumentException("digest") },
    )

    private fun macName(algo: String): String =
        when (algo) { "md5" -> "HmacMD5"; "sha1" -> "HmacSHA1"; "sha256" -> "HmacSHA256"; "sha512" -> "HmacSHA512"; else -> throw IllegalArgumentException("hmac") }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    companion object {
        private val METHOD = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

        /** QuickJS's interrupt error (budget or `interruptEvaluation`), however quickjs-kt wraps it. */
        internal fun isInterruption(e: Throwable): Boolean =
            causes(e).any { t -> t is QuickJsInterruptedException || firstLine(t).contains("InternalError: interrupted") }

        /**
         * quickjs-kt 1.0.18 words the interrupt "InternalError: stack overflow" (1.0.15: "InternalError: interrupted"), the text of a
         * real stack overflow too. Unwrapped it is still a [QuickJsInterruptedException] ([isInterruption]); wrapped in a JS error
         * (BUG-128) it is told apart from a bundle's own overflow by the runtime's budget being spent.
         */
        internal fun isSpentBudgetInterruption(e: Throwable, elapsedMs: Long, budgetMs: Long): Boolean =
            budgetMs > 0 && elapsedMs >= budgetMs && causes(e).any { t -> firstLine(t).contains("InternalError: stack overflow") }

        private fun causes(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause }.take(5)

        private fun firstLine(t: Throwable): String = t.message.orEmpty().lineSequence().firstOrNull().orEmpty()
        /** What a fetch / browser operation still running when its call settled resolves to (the bundle no longer reads it). */
        const val ABANDONED_FETCH = "{\"error\":\"Extension call already answered\"}"
        const val ABANDONED_BROWSER = "{\"ok\":false,\"error\":\"Extension call already answered\"}"
        private val CLOSER = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/** Handle table behind `__nijiyome_parse_html__`: each handle is a static list of jsoup elements (cheerio-like, routes: globals.v1.md). */
internal class HtmlRegistry {
    private val handles = ArrayList<List<Element>>()

    fun clear() = handles.clear()

    private fun add(list: List<Element>): Int {
        check(handles.size < MAX_HANDLES) { "HTML handle space exhausted" }
        handles.add(list)
        return handles.size - 1
    }

    fun op(op: String, handle: Int, arg: String): String {
        if (op == "parse") {
            val doc = Jsoup.parse(arg)
            return add(listOf(doc.child(0))).toString()
        }
        val nodes = handles.getOrNull(handle) ?: emptyList()
        return when (op) {
            "len" -> nodes.size.toString()
            "eq" -> {
                val i = arg.toIntOrNull() ?: 0
                val idx = if (i < 0) nodes.size + i else i
                add(listOfNotNull(nodes.getOrNull(idx))).toString()
            }
            "parent" -> add(nodes.mapNotNull { it.parent() }.distinct()).toString()
            "children" -> add(nodes.flatMap { it.children() }.filter { arg.isEmpty() || it.`is`(arg) }).toString()
            "next" -> add(nodes.mapNotNull { it.nextElementSibling() }).toString()
            "prev" -> add(nodes.mapNotNull { it.previousElementSibling() }).toString()
            "find" -> add(LinkedHashSet<Element>().also { set -> nodes.forEach { set.addAll(it.select(arg)) } }.toList()).toString()
            "attr" -> nodes.firstOrNull()?.takeIf { it.hasAttr(arg) }?.attr(arg)?.let { JsonPrimitive(it).toString() } ?: "null"
            "text" -> JsonPrimitive(nodes.joinToString("") { it.wholeText() }).toString()
            "html" -> nodes.firstOrNull()?.let { JsonPrimitive(it.html()).toString() } ?: "null"
            else -> throw IllegalArgumentException("unknown html op")
        }
    }

    companion object {
        const val MAX_HANDLES = 200_000
    }
}
