// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.BrowserError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 1.1 review #9 (CDP frames are size-capped while they are read) and #6 (pages counted exactly once, idle stop under the lock). */
class CdpFramesAndLeasesTest {
    private class Collect : FrameSink {
        val frames = CopyOnWriteArrayList<String>()
        val oversized = CopyOnWriteArrayList<String>()
        var ended = false
        override fun frame(text: String) { frames += text }
        override fun oversized(head: String) { oversized += head }
        override fun closed(error: Throwable?) { ended = true }
    }

    /** An endless-looking stream of [size] 'a's (never materialised) followed by a NUL. */
    private class Huge(private val prefix: ByteArray, private val size: Long) : InputStream() {
        private var pos = 0L
        override fun read(): Int = when {
            pos < prefix.size -> prefix[pos++.toInt()].toInt()
            pos < prefix.size + size -> { pos++; 'a'.code }
            pos == prefix.size + size -> { pos++; 0 }
            else -> -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            var n = 0
            while (n < len) {
                val c = read()
                if (c < 0) break
                b[off + n++] = c.toByte()
            }
            return if (n == 0 && len > 0) -1 else n
        }
    }

    @Test
    fun anOversizedFrameIsSkippedWhileReadingAndTheNextOneArrives() {
        val sink = Collect()
        val max = 1024 * 1024
        val big = Huge("{\"id\":7,\"result\":{\"v\":\"".toByteArray(), 8L * max)
        val next = ByteArrayInputStream("{\"id\":8,\"result\":{}}\u0000".toByteArray())
        readNulFrames(java.io.SequenceInputStream(big, next), sink, max)
        assertEquals(1, sink.oversized.size)
        assertTrue(sink.oversized[0].startsWith("{\"id\":7,"), "its head is kept to find the call")
        assertTrue(sink.oversized[0].length <= FrameAssembler.HEAD_BYTES)
        assertEquals(listOf("{\"id\":8,\"result\":{}}"), sink.frames)
        assertTrue(sink.ended)
    }

    @Test
    fun theAssemblerNeverHoldsMoreThanItsCap() {
        val a = FrameAssembler(max = 1000)
        val chunk = ByteArray(300) { 'x'.code.toByte() }
        repeat(50) {
            a.add(chunk, 0, chunk.size)
            assertTrue(a.held <= 1000, "held ${a.held}")
        }
        val sink = Collect()
        a.finish(sink)
        assertEquals(1, sink.oversized.size)
        a.add("ok".toByteArray(), 0, 2)
        a.finish(sink)
        assertEquals(listOf("ok"), sink.frames, "the assembler is usable again")
    }

    @Test
    fun theCallAnsweredByAnOversizedFrameFailsAndTheConnectionGoesOn() = runBlocking<Unit> {
        // a fake browser: an evaluation is answered with a frame over the cap, anything else normally
        val transport = object : CdpTransport {
            private var out: FrameSink? = null

            override fun start(sink: FrameSink) {
                out = sink
            }

            override fun send(text: String) {
                val id = Regex("\"id\":(\\d+)").find(text)!!.groupValues[1]
                if ("Runtime.evaluate" in text) {
                    out!!.oversized("{\"id\":$id,\"result\":{\"result\":{\"value\":\"aaaa")
                } else {
                    out!!.frame("{\"id\":$id,\"result\":{\"product\":\"Test/1\"}}")
                }
            }

            override fun close() {}
        }
        val conn = CdpConnection(transport).also { it.start() }
        assertFailsWith<CdpTooLarge> { conn.call("Runtime.evaluate", JsonObject(emptyMap())) }
        assertEquals("Test/1", conn.call("Browser.getVersion").str("product"), "still connected")
    }

    @Test
    fun aHugeEvaluationIsTooLargeAndTheBrowserStaysUsable() {
        val exe = LocalPages.browser() ?: return LocalPages.skip("aHugeEvaluationIsTooLargeAndTheBrowserStaysUsable")
        val host = LocalPages.host(exe)
        val runtime = CdpBrowserRuntime(host, urlAllowed = LocalPages.localPolicy, subresourceAllowed = LocalPages.localPolicy)
        LocalPages().use { pages ->
            runBlocking {
                try {
                    val id = runtime.newBrowser("ext", listOf("127.0.0.1"), JsonObject(emptyMap()))
                    runtime.navigate(id, pages.base + "/still")
                    assertFailsWith<BrowserError.TooLarge> { runtime.evaluate(id, "'a'.repeat(5000000)") }
                    assertFailsWith<BrowserError.TooLarge> { runtime.evaluate(id, "'é'.repeat(2000001)") }
                    assertEquals(JsonPrimitive(1_999_000), runtime.evaluate(id, "'a'.repeat(1999000).length"))
                    assertEquals(1_900_000, (runtime.evaluate(id, "'a'.repeat(1900000)") as JsonPrimitive).content.length, "just under the cap still arrives")
                    runtime.close(id)
                } finally {
                    host.shutdown()
                }
            }
        }
    }

    @Test
    fun aPageIsCountedOnceAndTheIdleStopWaitsForTheLastOne() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val mutex = Mutex()
        val idle = AtomicInteger()
        val leases = ChromiumHost.PageLeases(scope, mutex, idleMs = 200) { idle.incrementAndGet() }
        try {
            val a = mutex.withLock { leases.acquireLocked() }
            // a failed open: the page's close and the caller both give it back
            a.release()?.join()
            assertEquals(null, a.release(), "a second release does nothing")
            assertEquals(0, leases.count, "never below zero")
            val b = mutex.withLock { leases.acquireLocked() } // cancels the pending idle stop
            delay(500)
            assertEquals(0, idle.get(), "no idle stop while a page is open")
            val c = mutex.withLock { leases.acquireLocked() }
            b.release()?.join()
            delay(400)
            assertEquals(0, idle.get(), "one page still open")
            c.release()?.join()
            delay(500)
            assertEquals(1, idle.get(), "stopped once the last page closed")
            assertEquals(0, leases.count)
        } finally {
            scope.cancel()
        }
    }
}
