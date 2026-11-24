// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link.camera

import app.nijiyome.feature.link.ScanState
import app.nijiyome.feature.link.scanFirstCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.1 review #2 and #12: one owner closes the camera after its last read; nothing blocks past its timeout. */
class CameraOwnershipTest {
    /** A camera whose reads take a while and that records any close that lands during a read. */
    private class SlowStream : FrameStream {
        val reading = AtomicBoolean(false)
        val closedDuringRead = AtomicBoolean(false)
        val closes = AtomicInteger(0)
        val closeThreads = CopyOnWriteArrayList<String>()
        val readThreads = CopyOnWriteArrayList<String>()

        override fun next(timeoutMs: Int): GrayFrame? {
            reading.set(true)
            readThreads += Thread.currentThread().name
            Thread.sleep(minOf(timeoutMs, 150).toLong())
            reading.set(false)
            return null
        }

        override fun close() {
            if (reading.get()) closedDuringRead.set(true)
            closeThreads += Thread.currentThread().name
            closes.incrementAndGet()
        }
    }

    private fun backend(stream: FrameStream) = object : WebcamBackend {
        override fun devices() = listOf(Webcam("/dev/video0", "cam"))
        override fun open(camera: Webcam) = stream
    }

    @Test
    fun cancellingClosesOnceAfterTheReadReturned() = runBlocking {
        val stream = SlowStream()
        val states = CopyOnWriteArrayList<ScanState>()
        val job = launch(Dispatchers.IO) { scanFirstCamera(backend(stream), onState = { states += it }, onFrame = {}) }
        Thread.sleep(400) // a few reads under way
        job.cancelAndJoin()
        assertEquals(1, stream.closes.get())
        assertFalse(stream.closedDuringRead.get(), "the camera was closed while a frame was being read")
        assertFalse(stream.reading.get())
        assertEquals(listOf<ScanState>(ScanState.Aiming), states.toList())
    }

    @Test
    fun aFailedReadClosesTooAndSaysWhy() = runBlocking {
        var closed = 0
        val broken = object : FrameStream {
            override fun next(timeoutMs: Int): GrayFrame? = throw java.io.IOException("unplugged")
            override fun close() { closed++ }
        }
        val states = mutableListOf<ScanState>()
        assertNull(scanFirstCamera(backend(broken), onState = { states += it }, onFrame = {}))
        assertEquals(1, closed)
        assertEquals(ScanState.Failed("unplugged"), states.last())
        val none = object : WebcamBackend {
            override fun devices() = emptyList<Webcam>()
            override fun open(camera: Webcam) = error("never")
        }
        assertNull(scanFirstCamera(none, onState = { states += it }, onFrame = {}))
        assertEquals(ScanState.NoCamera, states.last())
    }

    @Test
    fun aSilentFfmpegReadReturnsWithinItsTimeout() {
        val process = ProcessBuilder("sleep", "30").start()
        val stream = RawGrayStream(process, 64, 48)
        val t0 = System.nanoTime()
        assertNull(stream.next(200))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms in 150..1_500, "next took $ms ms")
        stream.close()
        assertFalse(process.isAlive)
    }

    @Test
    fun framesArriveInPiecesAcrossReads() {
        // 2 frames of 4 x 2 written slowly: a read may end with half a frame, the next one completes it
        val process = ProcessBuilder("sh", "-c", "printf 'abcd'; sleep 0.3; printf 'efghABCDEFGH'; sleep 5").start()
        val stream = RawGrayStream(process, 4, 2)
        assertNull(stream.next(100))
        val first = stream.next(2_000)!!
        assertEquals("abcdefgh", String(first.luma))
        assertEquals("ABCDEFGH", String(stream.next(2_000)!!.luma))
        stream.close()
    }

    @Test
    fun aHungProgramDoesNotHoldTheCallerPastTheTimeout() {
        val t0 = System.nanoTime()
        assertNull(runProcess(listOf("sleep", "30"), timeoutSeconds = 1))
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000)
        // ends at once but leaves a child holding its output open: readText() before waitFor used to wait for the child
        val t1 = System.nanoTime()
        val (code, out) = runProcess(listOf("sh", "-c", "echo listed; sleep 30 & exit 0"), timeoutSeconds = 5)!!
        assertEquals(0, code)
        assertTrue("listed" in out)
        assertTrue((System.nanoTime() - t1) / 1_000_000 < 3_000)
        assertNull(runProcess(listOf("/nonexistent/ffmpeg"), timeoutSeconds = 1))
    }

    @Test
    fun anExpensiveListingNeverRunsOnTheCaller() {
        val slow = object : WebcamBackend {
            override fun devices(): List<Webcam> {
                Thread.sleep(800)
                return listOf(Webcam("Integrated Camera", "Integrated Camera"))
            }
            override fun open(camera: Webcam) = error("never")
            override val listingIsCheap = false
        }
        val t0 = System.nanoTime()
        assertFalse(DesktopWebcams.availableOf(slow)) // not known yet
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 200)
        val deadline = System.currentTimeMillis() + 5_000
        while (!DesktopWebcams.availableOf(slow) && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(DesktopWebcams.availableOf(slow))
    }
}
