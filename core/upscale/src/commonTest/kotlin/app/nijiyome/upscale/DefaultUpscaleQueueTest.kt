// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUpscaleQueueTest {
    private class Rig(scope: TestScope) {
        val files = InMemoryFileStore()
        val cache = UpscaleResultCache(files, Long.MAX_VALUE, clock = { scope.testScheduler.currentTime })
        val settings = MutableStateFlow(ImageUpscaleSettings())
        val pauses = MutableStateFlow<Set<UpscalePauseReason>>(emptySet())
        val engine = FakeEngine()
        val source = FakeSource()
        val queue = DefaultUpscaleQueue(
            engine = engine,
            source = source,
            codec = FakeCodec,
            cache = cache,
            settings = settings,
            pauseReasons = pauses,
            scope = scope.testWorkScope(),
            limits = TEST_LIMITS,
            worker = StandardTestDispatcher(scope.testScheduler),
            clock = { scope.testScheduler.currentTime },
        )

        /** A 100x140 source shown at 192x256 (already 64 px buckets): x2. */
        fun request(url: String): UpscaleTicket {
            if (url !in source.bytes) source.put(url, 100, 140)
            return queue.request(url, UpscaleImageKind.POSTER, 100, 140, 192, 256)
        }
    }

    @Test
    fun theNewestRequestRunsFirst() = runTest {
        val rig = Rig(this)
        rig.pauses.value = setOf(UpscalePauseReason.PLAYER_OPEN)
        advanceUntilIdle()
        rig.request("a")
        rig.request("b")
        rig.request("c")
        rig.pauses.value = emptySet()
        advanceUntilIdle()
        assertEquals(listOf("c", "b", "a"), rig.source.reads)
    }

    @Test
    fun aRequestedAgainKeyMovesToTheTopAndDuplicatesMerge() = runTest {
        val rig = Rig(this)
        rig.pauses.value = setOf(UpscalePauseReason.LINK_BUSY)
        advanceUntilIdle()
        rig.request("a")
        rig.request("b")
        rig.request("a")
        assertEquals(2, rig.queue.status.value.queued)
        rig.pauses.value = emptySet()
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), rig.source.reads)
        assertEquals(2, rig.engine.calls.size)
    }

    @Test
    fun aReleasedQueuedJobIsDropped() = runTest {
        val rig = Rig(this)
        rig.pauses.value = setOf(UpscalePauseReason.APP_HIDDEN)
        advanceUntilIdle()
        val a = rig.request("a")
        val b = rig.request("b")
        a.release()
        a.release() // idempotent
        rig.pauses.value = emptySet()
        advanceUntilIdle()
        assertEquals(listOf("b"), rig.source.reads)
        assertEquals(UpscaleState.Idle, rig.queue.state(a.key).value)
        assertIs<UpscaleState.Done>(rig.queue.state(b.key).value)
    }

    @Test
    fun aJobStaysWhileAnotherTicketHoldsIt() = runTest {
        val rig = Rig(this)
        rig.pauses.value = setOf(UpscalePauseReason.APP_HIDDEN)
        advanceUntilIdle()
        val first = rig.request("a")
        rig.request("a")
        first.release()
        rig.pauses.value = emptySet()
        advanceUntilIdle()
        assertEquals(listOf("a"), rig.source.reads)
    }

    @Test
    fun releasingARunningJobCancelsItBetweenTiles() = runTest {
        val rig = Rig(this)
        advanceUntilIdle()
        lateinit var ticket: UpscaleTicket
        rig.engine.during = { ticket.release() }
        ticket = rig.request("a")
        advanceUntilIdle()
        assertEquals(1, rig.engine.calls.size)
        assertEquals(UpscaleState.Idle, rig.queue.state(ticket.key).value)
        assertNull(rig.cache.peek(ticket.key))
    }

    @Test
    fun aFinishedResultIsCachedAndServedToTheFirstFrame() = runTest {
        val rig = Rig(this)
        advanceUntilIdle()
        val t = rig.request("a")
        advanceUntilIdle()
        val done = assertIs<UpscaleState.Done>(rig.queue.state(t.key).value)
        assertEquals(done.path, rig.queue.cached("a", UpscaleImageKind.POSTER, 192, 256))
        assertEquals(Triple(100, 140, 2), rig.engine.calls.single())
        assertEquals("Test GPU", rig.queue.status.value.gpuName)
        assertNotNull(rig.queue.status.value.lastMillis)

        // A second request of the same key is answered from the cache, without the GPU.
        rig.request("a")
        advanceUntilIdle()
        assertEquals(1, rig.engine.calls.size)
    }

    @Test
    fun aChangedSourceInvalidatesTheResult() = runTest {
        val rig = Rig(this)
        advanceUntilIdle()
        val t = rig.request("a")
        advanceUntilIdle()
        assertEquals(1, rig.engine.calls.size)

        rig.source.put("a", 100, 140, content = "v2")
        rig.queue.sourceRefreshed("a")
        advanceUntilIdle()
        assertEquals(2, rig.engine.calls.size)
        assertEquals(UpscaleKey.sourceHash(rig.source.bytes.getValue("a")), rig.cache.entry(t.key)?.sourceHash)
        assertIs<UpscaleState.Done>(rig.queue.state(t.key).value)

        // The same bytes again: nothing to redo.
        rig.queue.sourceRefreshed("a")
        advanceUntilIdle()
        assertEquals(2, rig.engine.calls.size)
    }

    @Test
    fun pausingHoldsTheQueueAndResumingRunsIt() = runTest {
        val rig = Rig(this)
        rig.pauses.value = setOf(UpscalePauseReason.PLAYER_OPEN, UpscalePauseReason.HOT)
        advanceUntilIdle()
        val t = rig.request("a")
        advanceUntilIdle()
        assertEquals(UpscalePauseReason.PLAYER_OPEN, rig.queue.status.value.pausedBy)
        assertEquals(1, rig.queue.status.value.queued)
        assertEquals(UpscaleState.Queued, rig.queue.state(t.key).value)
        assertEquals(emptyList(), rig.source.reads)

        rig.pauses.value = emptySet()
        advanceUntilIdle()
        assertNull(rig.queue.status.value.pausedBy)
        assertIs<UpscaleState.Done>(rig.queue.state(t.key).value)
    }

    @Test
    fun theEngineIsReleasedAfterSixtySecondsIdle() = runTest {
        val rig = Rig(this)
        advanceUntilIdle()
        rig.request("a")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, rig.engine.calls.size)
        assertEquals(0, rig.engine.releases)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(0, rig.engine.releases)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, rig.engine.releases)
        // Already released: no more releases while idle.
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(1, rig.engine.releases)
    }

    @Test
    fun pausingReleasesTheEngineAtOnce() = runTest {
        val rig = Rig(this)
        advanceUntilIdle()
        rig.request("a")
        advanceTimeBy(1_000)
        runCurrent()
        rig.pauses.value = setOf(UpscalePauseReason.FRAMEGEN_ACTIVE)
        runCurrent()
        assertEquals(1, rig.engine.releases)
    }

    @Test
    fun noGpuTurnsTheQueueOffWithoutACpuFallback() = runTest {
        val rig = Rig(this)
        rig.engine.gpu = null
        advanceUntilIdle()
        val t = rig.request("a")
        advanceUntilIdle()
        assertEquals(0, rig.engine.calls.size)
        assertIs<UpscaleState.Skipped>(rig.queue.state(t.key).value)
        assertEquals(UpscalePauseReason.NO_GPU, rig.queue.status.value.pausedBy)
        assertIs<UpscaleState.Skipped>(rig.queue.state(rig.request("b").key).value)
    }

    @Test
    fun anEngineThatFailsItsFirstImageTurnsTheQueueOff() = runTest {
        val rig = Rig(this)
        rig.engine.fail = true
        advanceUntilIdle()
        val t = rig.request("a")
        advanceUntilIdle()
        assertIs<UpscaleState.Failed>(rig.queue.state(t.key).value)
        assertEquals(UpscalePauseReason.ENGINE_FAILED, rig.queue.status.value.pausedBy)
    }

    @Test
    fun turningTheFeatureOffSkipsRequestsAndPending() = runTest {
        val rig = Rig(this)
        rig.pauses.value = setOf(UpscalePauseReason.APP_HIDDEN)
        advanceUntilIdle()
        val queued = rig.request("a")
        rig.settings.value = ImageUpscaleSettings(enabled = false)
        advanceUntilIdle()
        assertIs<UpscaleState.Skipped>(rig.queue.state(queued.key).value)
        assertEquals(UpscalePauseReason.DISABLED, rig.queue.status.value.pausedBy)
        val later = rig.request("b")
        assertIs<UpscaleState.Skipped>(rig.queue.state(later.key).value)
        assertNull(rig.queue.cached("a", UpscaleImageKind.POSTER, 192, 256))
    }

    @Test
    fun aSourceLargeEnoughOnceDecodedIsSkipped() = runTest {
        val rig = Rig(this)
        advanceUntilIdle()
        // The UI saw a downsampled 100x140 decode; the real source is 460x650.
        rig.source.put("big", 460, 650)
        val t = rig.request("big")
        advanceUntilIdle()
        assertIs<UpscaleState.Skipped>(rig.queue.state(t.key).value)
        assertEquals(0, rig.engine.calls.size)
    }

    @Test
    fun aMissingSourceIsSkipped() = runTest {
        val rig = Rig(this)
        advanceUntilIdle()
        val t = rig.queue.request("gone", UpscaleImageKind.POSTER, 100, 140, 192, 256)
        advanceUntilIdle()
        assertIs<UpscaleState.Skipped>(rig.queue.state(t.key).value)
    }

    @Test
    fun clearingTheCacheForgetsFinishedPaths() = runTest {
        val rig = Rig(this)
        advanceUntilIdle()
        val t = rig.request("a")
        advanceUntilIdle()
        rig.cache.clear()
        advanceUntilIdle()
        assertEquals(UpscaleState.Idle, rig.queue.state(t.key).value)
        assertEquals(0L, rig.queue.status.value.cacheBytes)
    }
}
