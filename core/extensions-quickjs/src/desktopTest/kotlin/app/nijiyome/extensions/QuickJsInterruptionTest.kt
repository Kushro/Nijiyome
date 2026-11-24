// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * How the real quickjs-kt runtime reports an interrupted evaluation, and that [QuickJsSession.isInterruption] recognises every
 * shape of it (the runtime's own budget, `interruptEvaluation`, after an `await`, inside a bundle's `try`/`catch`) while a real stack
 * overflow is not taken for a timeout. Same limits as [QuickJsEngine.open], with a short budget.
 */
class QuickJsInterruptionTest {
    private fun failureOf(timeoutMs: Long = 300, interruptAfterMs: Long? = null, script: String): Throwable = runBlocking {
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "quickjs-test").apply { isDaemon = true } }
        val qjs = QuickJs.create(executor.asCoroutineDispatcher())
        try {
            qjs.memoryLimit = JsEngine.MEMORY_LIMIT_BYTES
            qjs.maxStackSize = QuickJsEngine.MAX_STACK_BYTES
            qjs.evaluationTimeoutMillis = timeoutMs
            qjs.asyncFunction("__test_tick") { delay(10); "tick" }
            // A plain thread, like the watchdog of QuickJsSession.invoke running beside the evaluation.
            val watchdog = interruptAfterMs?.let { ms -> Thread { runCatching { Thread.sleep(ms); qjs.interruptEvaluation() } }.apply { start() } }
            val failure = runCatching { qjs.evaluate<Any?>(script, "test.js") }.exceptionOrNull()
            watchdog?.interrupt()
            println("[quickjs-interruption] ${failure?.javaClass?.name}: ${failure?.message?.lineSequence()?.firstOrNull()}")
            assertNotNull(failure, "the evaluation was expected to fail")
        } finally {
            qjs.close()
            executor.shutdown()
        }
    }

    @Test
    fun runtimeBudgetIsAnInterruption() {
        assertTrue(QuickJsSession.isInterruption(failureOf(script = "for (;;) {}")))
    }

    @Test
    fun watchdogInterruptIsAnInterruption() {
        assertTrue(QuickJsSession.isInterruption(failureOf(timeoutMs = 60_000, interruptAfterMs = 300, script = "for (;;) {}")))
    }

    @Test
    fun budgetAfterAnAwaitIsAnInterruption() {
        // The call shape of the host: `await __tk_call(...)`, the bundle spinning after its first host round trip.
        val script = "await (async () => { await __test_tick(); for (;;) {} })()"
        assertTrue(QuickJsSession.isInterruption(failureOf(script = script)))
    }

    @Test(timeout = 20_000)
    fun bundleCatchDoesNotSwallowTheInterruption() {
        // A bundle that wraps its work in try/catch and keeps going must still end as an interruption.
        val script = "await (async () => { for (;;) { try { for (;;) {} } catch (e) { globalThis.caught = String(e) } } })()"
        assertTrue(QuickJsSession.isInterruption(failureOf(script = script)))
    }

    @Test
    fun stackOverflowErrorIsNotAnInterruption() {
        // The message QuickJS throws for a runaway recursion (run for real, in a child JVM, by QuickJsRecursionTest); a bundle may
        // catch it, so it is a bundle error.
        assertFalse(QuickJsSession.isInterruption(RuntimeException("InternalError: stack overflow")))
    }

    @Test
    fun wrappedInterruptCountsOnlyOnceTheBudgetIsSpent() {
        // BUG-128: the interrupt wrapped in a JS error, worded as quickjs-kt 1.0.18 does.
        val wrapped = RuntimeException("Error: call failed", RuntimeException("InternalError: stack overflow\n    at loop (bundle.js)"))
        assertTrue(QuickJsSession.isSpentBudgetInterruption(wrapped, elapsedMs = 45_000, budgetMs = 45_000))
        assertFalse(QuickJsSession.isSpentBudgetInterruption(wrapped, elapsedMs = 120, budgetMs = 45_000))
        assertFalse(QuickJsSession.isSpentBudgetInterruption(RuntimeException("TypeError: x is undefined"), 50_000, 45_000))
        // The 1.0.15 wording keeps working through isInterruption.
        assertTrue(QuickJsSession.isInterruption(RuntimeException("Error", RuntimeException("InternalError: interrupted"))))
    }
}
