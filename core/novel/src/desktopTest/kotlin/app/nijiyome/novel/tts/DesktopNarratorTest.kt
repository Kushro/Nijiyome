// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.NovelChapterText
import app.nijiyome.model.TtsEngine
import app.nijiyome.model.TtsSettings
import app.nijiyome.model.TtsState
import app.nijiyome.model.TtsVoice
import app.nijiyome.novel.LocalVoiceInstaller
import app.nijiyome.novel.NarratorEngineStatus
import app.nijiyome.novel.NarratorOptions
import app.nijiyome.novel.NarratorProblem
import app.nijiyome.novel.TtsException
import app.nijiyome.novel.TtsFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.1: the desktop narrator's queue, highlight (currentParagraph), pause / resume / stop and errors, with fake voices and player. */
class DesktopNarratorTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-narrator").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    /** Writes `<text>` into the clip; [gate] (when set) holds every synthesis until the test releases one. */
    private class FakeFileVoice(val gate: Channel<Unit>? = null, val failOn: String? = null) : FileVoice {
        val synthesized: MutableList<String> = CopyOnWriteArrayList()
        override val extension = "wav"

        override fun cacheMaterial(settings: TtsSettings) = "fake|${settings.pitch}"

        override suspend fun status() = NarratorEngineStatus(true, backend = "fake")

        override suspend fun voices(settings: TtsSettings) = listOf(TtsVoice("v", "Voice", "en"))

        override suspend fun synthesize(text: String, settings: TtsSettings, target: File): File {
            gate?.receive()
            if (text == failOn) throw TtsException(TtsFailure.HTTP, 500)
            synthesized += text
            target.parentFile.mkdirs()
            target.writeText(text)
            return target
        }
    }

    /** Each clip plays until the test calls [finish]; records what was played, at which speed, and pause / resume. */
    private class FakePlayer : ClipPlayer {
        val played: MutableList<String> = CopyOnWriteArrayList()
        val speeds: MutableList<Float> = CopyOnWriteArrayList()
        val events: MutableList<String> = CopyOnWriteArrayList()
        val started = Channel<String>(Channel.UNLIMITED)

        @Volatile
        var current: CompletableDeferred<Unit>? = null

        @Volatile
        var paused = false

        override suspend fun play(file: File, speed: Float) {
            val done = CompletableDeferred<Unit>()
            current = done
            paused = false
            played += file.readText()
            speeds += speed
            started.send(file.readText())
            try {
                done.await()
            } finally {
                current = null
            }
        }

        fun finish() {
            current!!.complete(Unit)
        }

        override fun pause(): Boolean {
            if (current == null || paused) return false
            paused = true
            events += "pause"
            return true
        }

        override fun resume(): Boolean {
            if (current == null || !paused) return false
            paused = false
            events += "resume"
            return true
        }

        override fun setSpeed(speed: Float) {
            events += "speed $speed"
        }

        override fun release() {
            events += "release"
        }
    }

    private class FakeLive : LiveVoice {
        val spoken = Channel<String>(Channel.UNLIMITED)
        val events: MutableList<String> = CopyOnWriteArrayList()

        @Volatile
        var current: CompletableDeferred<Unit>? = null

        override suspend fun status() = NarratorEngineStatus(true, backend = "speech-dispatcher · fake")

        override suspend fun voices(settings: TtsSettings) = emptyList<TtsVoice>()

        override suspend fun speak(text: String, settings: TtsSettings, speed: Float) {
            val done = CompletableDeferred<Unit>()
            current = done
            spoken.send("$text@$speed/${settings.pitch}")
            try {
                done.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                events += "cancel"
                throw e
            } finally {
                current = null
            }
        }

        override fun pause(): Boolean = (current != null).also { if (it) events += "pause" }

        override fun resume(): Boolean = (current != null).also { if (it) events += "resume" }

        override fun close() {}
    }

    private class Catalog(var system: DesktopVoice?, val others: Map<TtsEngine, DesktopVoice> = emptyMap()) : VoiceCatalog {
        override fun system() = system

        override fun forEngine(engine: TtsEngine) = if (engine == TtsEngine.SYSTEM) system else others[engine]

        override val localVoice: LocalVoiceInstaller? = null
        val idle = java.util.concurrent.atomic.AtomicInteger()
        val closed = java.util.concurrent.atomic.AtomicInteger()

        override fun releaseIdle() {
            idle.incrementAndGet()
        }

        override fun close() {
            closed.incrementAndGet()
        }
    }

    private fun chapter(vararg p: String) = NovelChapterText(chapterId = "c1", title = "Chapter", paragraphs = p.toList())

    private fun narrator(catalog: VoiceCatalog, player: ClipPlayer, settings: TtsSettings = TtsSettings()) =
        DesktopNarrator(catalog, player, File(tmp, "tts"), { settings }, Dispatchers.Default)

    private suspend fun <T> kotlinx.coroutines.flow.StateFlow<T>.until(value: T) = withTimeout(5_000) { first { it == value } }

    @Test
    fun readsEveryParagraphInOrderSkippingBlanksAndFollowsTheHighlight(): Unit = runBlocking {
        val player = FakePlayer()
        val voice = FakeFileVoice()
        val n = narrator(Catalog(voice), player)
        val highlighted = CopyOnWriteArrayList<Int?>()
        val watch = watch(n.currentParagraph, highlighted)
        n.speak(chapter("One.", "", "  ", "Two.", "Three."), fromParagraph = 0)
        for (expected in listOf("One.", "Two.", "Three.")) {
            assertEquals(expected, withTimeout(5_000) { player.started.receive() })
            assertEquals(TtsState.SPEAKING, n.state.value)
            player.finish()
        }
        n.state.until(TtsState.IDLE)
        watch.cancel()
        assertEquals(listOf("One.", "Two.", "Three."), player.played)
        assertEquals(listOf(0, 3, 4), highlighted.filterNotNull().distinct())
        assertNull(n.currentParagraph.value)
        assertNull(n.lastError.value)
        // The next paragraph was synthesised while the current one played (prefetch), each once.
        assertEquals(setOf("One.", "Two.", "Three."), voice.synthesized.toSet())
        assertEquals(3, voice.synthesized.size)
    }

    @Test
    fun startsAtTheTappedParagraph(): Unit = runBlocking {
        val player = FakePlayer()
        val n = narrator(Catalog(FakeFileVoice()), player)
        n.speak(chapter("A.", "B.", "C."), fromParagraph = 1)
        assertEquals("B.", withTimeout(5_000) { player.started.receive() })
        assertEquals(1, n.currentParagraph.value)
        n.stop()
        assertEquals(TtsState.IDLE, n.state.value)
        assertNull(n.currentParagraph.value)
    }

    @Test
    fun pauseHoldsTheClipMidSentenceAndResumeGoesOn(): Unit = runBlocking {
        val player = FakePlayer()
        val n = narrator(Catalog(FakeFileVoice()), player)
        n.speak(chapter("First.", "Second."))
        assertEquals("First.", withTimeout(5_000) { player.started.receive() })
        n.pause()
        assertEquals(TtsState.PAUSED, n.state.value)
        assertEquals(0, n.currentParagraph.value)
        assertTrue(player.paused)
        n.resume()
        assertEquals(TtsState.SPEAKING, n.state.value)
        assertEquals(listOf("pause", "resume"), player.events)
        player.finish()
        assertEquals("Second.", withTimeout(5_000) { player.started.receive() })
        // The paused clip was not restarted.
        assertEquals(listOf("First.", "Second."), player.played)
        n.stop()
    }

    @Test
    fun pauseWhilePreparingRestartsThatParagraphOnResume(): Unit = runBlocking {
        val gate = Channel<Unit>(Channel.UNLIMITED)
        val player = FakePlayer()
        val voice = FakeFileVoice(gate)
        val n = narrator(Catalog(voice), player)
        n.speak(chapter("Slow.", "Next."))
        assertEquals(TtsState.PREPARING, n.state.value)
        n.pause()
        assertEquals(TtsState.PAUSED, n.state.value)
        n.resume()
        assertEquals(TtsState.PREPARING, n.state.value)
        gate.send(Unit)
        gate.send(Unit)
        assertEquals("Slow.", withTimeout(5_000) { player.started.receive() })
        assertEquals(0, n.currentParagraph.value)
        n.stop()
    }

    @Test
    fun speedGoesToThePlayerAtOnceAndToTheNextClip(): Unit = runBlocking {
        val player = FakePlayer()
        val n = narrator(Catalog(FakeFileVoice()), player, TtsSettings(speed = 1.2f))
        n.speak(chapter("A.", "B."))
        withTimeout(5_000) { player.started.receive() }
        n.setSpeed(1.7f)
        assertTrue("speed 1.7" in player.events)
        player.finish()
        withTimeout(5_000) { player.started.receive() }
        assertEquals(listOf(1.2f, 1.7f), player.speeds)
        n.setSpeed(9f)
        assertTrue("speed 2.0" in player.events)
        n.stop()
    }

    @Test
    fun aFailureEndsTheRunWithTheErrorAndStopClearsIt(): Unit = runBlocking {
        val player = FakePlayer()
        val n = narrator(Catalog(FakeFileVoice(failOn = "Bad.")), player)
        n.speak(chapter("Bad.", "Good."))
        withTimeout(5_000) { n.lastError.first { it != null } }
        assertEquals(TtsFailure.HTTP, (n.lastError.value as TtsException).reason)
        assertEquals(TtsState.IDLE, n.state.value)
        assertNull(n.currentParagraph.value)
        n.stop()
        assertNull(n.lastError.value)
    }

    @Test
    fun theReaderSeesSpeakingThenIdleAtTheEndOfTheChapter(): Unit = runBlocking {
        // NovelReaderViewModel goes on with the next chapter on SPEAKING → IDLE without an error.
        val player = FakePlayer()
        val n = narrator(Catalog(FakeFileVoice()), player)
        val states = CopyOnWriteArrayList<TtsState>()
        val watch = watch(n.state, states)
        n.speak(chapter("Only."))
        withTimeout(5_000) { player.started.receive() }
        player.finish()
        n.state.until(TtsState.IDLE)
        delay(50)
        watch.cancel()
        val distinct = states.zipWithNext().filter { (a, b) -> a != b }.map { it.second }
        assertEquals(TtsState.IDLE, distinct.last())
        assertTrue(states.indexOf(TtsState.SPEAKING) < states.lastIndexOf(TtsState.IDLE))
    }

    @Test
    fun liveVoiceIsPausedAndResumedThroughTheOsAndCancelledOnStop(): Unit = runBlocking {
        val live = FakeLive()
        val player = FakePlayer()
        val n = narrator(Catalog(live), player, TtsSettings(speed = 1.5f, pitch = 0.8f))
        n.speak(chapter("Hola.", "Chau."))
        assertEquals("Hola.@1.5/0.8", withTimeout(5_000) { live.spoken.receive() })
        assertEquals(0, n.currentParagraph.value)
        n.pause()
        assertEquals(TtsState.PAUSED, n.state.value)
        n.resume()
        assertEquals(TtsState.SPEAKING, n.state.value)
        assertEquals(listOf("pause", "resume"), live.events.toList())
        live.current!!.complete(Unit)
        assertEquals("Chau.@1.5/0.8", withTimeout(5_000) { live.spoken.receive() })
        assertEquals(1, n.currentParagraph.value)
        n.stop()
        withTimeout(5_000) { while ("cancel" !in live.events) delay(10) }
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun previewPlaysOnceAndStopEndsItWithoutAnError(): Unit = runBlocking {
        val player = FakePlayer()
        val n = narrator(Catalog(FakeFileVoice()), player)
        val result = asyncPreview(n, "This is how it sounds.")
        assertEquals("This is how it sounds.", withTimeout(5_000) { player.started.receive() })
        assertEquals(TtsState.SPEAKING, n.state.value)
        n.stop()
        assertTrue(withTimeout(5_000) { result.await() }.isSuccess)
        assertEquals(TtsState.IDLE, n.state.value)
    }

    @Test
    fun previewReportsAMissingSystemVoice(): Unit = runBlocking {
        val n = narrator(Catalog(null), FakePlayer())
        val r = n.preview("x")
        assertEquals(TtsFailure.ENGINE_UNAVAILABLE, (r.exceptionOrNull() as TtsException).reason)
        assertEquals(TtsState.IDLE, n.state.value)
    }

    @Test
    fun offersTheDesktopEnginesPitchOnlyForTheSystemVoiceAndSaysWhenThereIsNone(): Unit = runBlocking {
        val n = narrator(Catalog(null), FakePlayer())
        assertEquals(TtsEngine.KITTEN, n.engines[1])
        assertEquals(TtsEngine.entries.toSet(), n.engines.toSet())
        assertTrue(n.hasPitch(TtsEngine.SYSTEM))
        assertFalse(n.hasPitch(TtsEngine.KITTEN))
        assertFalse(n.hasPitch(TtsEngine.OPENAI))
        val st = n.status(TtsEngine.SYSTEM)!!
        assertFalse(st.available)
        assertEquals(NarratorProblem.NO_SYSTEM_VOICE, st.problem)
        assertTrue(n.voices(TtsEngine.SYSTEM).isEmpty())
        // A narrator without options (Android's) keeps its four engines.
        assertEquals(listOf(TtsEngine.SYSTEM, TtsEngine.OPENAI, TtsEngine.ELEVENLABS, TtsEngine.OPENAI_COMPATIBLE), NarratorOptions.enginesOf(null))
    }

    @Test
    fun theCacheNameChangesWithTheVoiceAndPitchButNotTheSpeed() {
        val base = TtsSettings(engine = TtsEngine.SYSTEM, voice = "es")
        val a = DesktopNarrator.cacheKey(base, "espeak|es|50", "Hola")
        assertEquals(a, DesktopNarrator.cacheKey(base.copy(speed = 1.8f), "espeak|es|50", "Hola"))
        assertTrue(a != DesktopNarrator.cacheKey(base, "espeak|es|60", "Hola"))
        assertTrue(a != DesktopNarrator.cacheKey(base.copy(voice = "en"), "espeak|es|50", "Hola"))
        assertTrue(a != DesktopNarrator.cacheKey(base, "espeak|es|50", "Hola."))
    }

    @Test
    fun stoppingReleasesTheEnginesAndTheNextNarrationReopensThem(): Unit = runBlocking {
        val player = FakePlayer()
        val catalog = Catalog(FakeFileVoice())
        val n = narrator(catalog, player)
        n.speak(chapter("A.", "B."))
        assertEquals("A.", withTimeout(5_000) { player.started.receive() })
        assertEquals(0, catalog.idle.get(), "speaking does not release")
        n.pause()
        n.resume()
        assertEquals(0, catalog.idle.get(), "pause / resume do not release")
        n.stop()
        assertEquals(1, catalog.idle.get())
        assertTrue("release" in player.events)
        // Narrating again works (the player and the voices open again on demand).
        n.speak(chapter("C."))
        assertEquals("C.", withTimeout(5_000) { player.started.receive() })
        n.stop()
        assertEquals(2, catalog.idle.get())
        assertEquals(0, catalog.closed.get())
    }

    @Test
    fun previewDoesNotReleaseWhatItIsAboutToUse(): Unit = runBlocking {
        val player = FakePlayer()
        val catalog = Catalog(FakeFileVoice())
        val n = narrator(catalog, player)
        val r = asyncPreview(n, "Hi.")
        withTimeout(5_000) { player.started.receive() }
        assertEquals(0, catalog.idle.get())
        assertTrue("release" !in player.events)
        player.finish()
        assertTrue(withTimeout(5_000) { r.await() }.isSuccess)
    }

    @Test
    fun releaseClosesEverythingOnce(): Unit = runBlocking {
        val player = FakePlayer()
        val catalog = Catalog(FakeFileVoice())
        val n = narrator(catalog, player)
        n.speak(chapter("A."))
        withTimeout(5_000) { player.started.receive() }
        n.release()
        assertTrue(n.released)
        assertEquals(1, catalog.closed.get())
        assertTrue("release" in player.events)
        assertEquals(TtsState.IDLE, n.state.value)
        n.release()
        assertEquals(1, catalog.closed.get())
    }
}

private val watchers = CoroutineScope(Dispatchers.Default)

private fun <T> watch(flow: Flow<T>, into: MutableList<T>): Job = watchers.launch { flow.collect { into += it } }

private fun asyncPreview(n: DesktopNarrator, text: String): Deferred<Result<Unit>> = watchers.async { n.preview(text) }
