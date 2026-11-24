// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenSaverInhibitorTest {
    private val worker = Executors.newSingleThreadScheduledExecutor()

    @AfterTest
    fun stop() {
        worker.shutdownNow()
    }

    private class FakeLock(private val letGoTakesMs: Long = 0) : InhibitLock {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val threads: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        override fun hold() {
            threads += Thread.currentThread().name
            calls += "hold"
        }

        override fun letGo() {
            threads += Thread.currentThread().name
            Thread.sleep(letGoTakesMs)
            calls += "letGo"
        }
    }

    /** Waits until everything posted to the worker so far has run. */
    private fun drain() {
        val done = CountDownLatch(1)
        worker.execute { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun playPauseReusesTheHeldLock() {
        val lock = FakeLock()
        val inhibitor = ScreenSaverInhibitor(lock, lingerMs = 300, worker = worker)
        repeat(5) {
            inhibitor.acquire()
            inhibitor.release()
        }
        inhibitor.acquire()
        drain()
        assertEquals(listOf("hold"), lock.calls)
        inhibitor.release()
        Thread.sleep(600)
        drain()
        assertEquals(listOf("hold", "letGo"), lock.calls)
    }

    @Test
    fun theCallerNeverWaitsForTheSystem() {
        // A helper that takes 1.5 s to go away: release (on the UI thread in the app) still returns at once.
        val lock = FakeLock(letGoTakesMs = 1_500)
        val inhibitor = ScreenSaverInhibitor(lock, lingerMs = 0, worker = worker)
        inhibitor.acquire()
        drain()
        val start = System.nanoTime()
        inhibitor.release()
        inhibitor.acquire()
        inhibitor.release()
        val tookMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(tookMs < 100, "release/acquire took $tookMs ms")
        assertEquals(1, lock.threads.size, "every system call on one thread")
    }

    @Test
    fun nestedHoldersKeepTheLock() {
        val lock = FakeLock()
        val inhibitor = ScreenSaverInhibitor(lock, lingerMs = 0, worker = worker)
        inhibitor.acquire()
        inhibitor.acquire()
        inhibitor.release()
        Thread.sleep(100)
        drain()
        assertEquals(listOf("hold"), lock.calls)
        inhibitor.release()
        Thread.sleep(100)
        drain()
        assertEquals(listOf("hold", "letGo"), lock.calls)
        // an extra release does nothing
        inhibitor.release()
        drain()
        assertEquals(listOf("hold", "letGo"), lock.calls)
    }
}
