// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A bundle with a runaway recursion must end as an error of that extension, not take the process down (before 1.1 QuickJS's stack
 * limit never tripped and the JVM died with SIGSEGV). The recursion runs in a child JVM ([QuickJsRecursionProbe]) so that a crash
 * fails this test instead of killing the test worker. Cases: while loading, in a call, and in a call resumed after host round trips
 * (where the evaluation used to continue on another thread of the dispatcher).
 */
class QuickJsRecursionTest {
    private fun probe(case: String): Pair<Int, String> {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), QuickJsRecursionProbe::class.java.name, case)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }.apply { start() }
        val finished = process.waitFor(90, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        reader.join(5_000)
        println("[quickjs-recursion:$case] exit=${if (finished) process.exitValue() else "timeout"}\n$output")
        return (if (finished) process.exitValue() else -1) to output.toString()
    }

    private fun assertBundleError(case: String) {
        val (exit, output) = probe(case)
        assertEquals(0, exit, "the child JVM must survive the recursion:\n$output")
        assertTrue(output.contains("FAILED BundleError"), output)
        assertTrue(output.contains("stack overflow", ignoreCase = true), output)
        assertTrue(output.contains("AFTER ok"), "the session must stay usable after the overflow:\n$output")
    }

    @Test
    fun recursionInACallIsABundleError() = assertBundleError("call")

    @Test
    fun recursionAfterHostRoundTripsIsABundleError() = assertBundleError("after-await")

    @Test
    fun recursionWhileLoadingIsALoadError() {
        val (exit, output) = probe("load")
        assertEquals(0, exit, "the child JVM must survive the recursion:\n$output")
        assertTrue(output.contains("FAILED BundleError"), output)
        assertTrue(output.contains("stack overflow", ignoreCase = true), output)
    }
}

/** Child JVM of [QuickJsRecursionTest]: prints `FAILED <error> <message>` and `AFTER <result>`, exit 0 unless it crashes. */
object QuickJsRecursionProbe {
    private val manifest = ManifestParser.parse(
        """{"id":"recursion","name":"Recursion","version":"1.0.0","type":"onlinestream",
           "description":"","permissions":["network:domain:api.example.invalid"]}""",
    )

    private const val CLASS = """
        function deep(n) { return deep(n + 1) + 1; }
        class Recursion {
          spin() { return deep(0); }
          async spinLater() {
            for (let i = 0; i < 20; i++) await fetch('https://api.example.invalid/' + i);
            await new Promise(function (r) { setTimeout(r, 5); });
            return deep(0);
          }
          ping() { return 'ok'; }
        }
        module.exports = Recursion;
    """

    @JvmStatic
    fun main(args: Array<String>) {
        val case = args.firstOrNull() ?: "call"
        // Function declarations are hoisted, so the top-level call of the load case runs while the bundle is instantiated.
        val loadBundle = if (case == "load") "deep(0);\n$CLASS" else CLASS
        runBlocking {
            val session = try {
                QuickJsEngine().open(manifest, loadBundle, OfflineJsHost(manifest.networkPolicy))
            } catch (e: ExtError) {
                println("FAILED ${e.javaClass.simpleName} ${e.message}")
                exitProcess(0)
            }
            try {
                val method = if (case == "after-await") "spinLater" else "spin"
                runCatching { session.invoke(method, emptyList()) }
                    .onSuccess { println("UNEXPECTED ${it.result}") }
                    .onFailure { println("FAILED ${it.javaClass.simpleName} ${it.message}") }
                val after = session.invoke("ping", listOf(JsonPrimitive(1))).result.jsonPrimitive.content
                println("AFTER $after")
            } finally {
                session.close()
            }
        }
        exitProcess(0)
    }
}
