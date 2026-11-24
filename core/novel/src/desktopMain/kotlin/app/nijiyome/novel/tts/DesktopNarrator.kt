// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.Narrator
import app.nijiyome.model.NovelChapterText
import app.nijiyome.model.TtsEngine
import app.nijiyome.model.TtsSettings
import app.nijiyome.model.TtsState
import app.nijiyome.model.TtsVoice
import app.nijiyome.novel.LocalVoiceInstaller
import app.nijiyome.novel.NarratorEngineStatus
import app.nijiyome.novel.NarratorErrors
import app.nijiyome.novel.NarratorOptions
import app.nijiyome.novel.NarratorProblem
import app.nijiyome.novel.NovelText
import app.nijiyome.novel.ParagraphQueue
import app.nijiyome.novel.TtsException
import app.nijiyome.novel.TtsFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/** Which voice speaks for each engine here (resolved at every run: installing espeak-ng or speech-dispatcher takes effect at once). */
internal interface VoiceCatalog {
    /** The system voice of this computer, or null when there is none. */
    fun system(): DesktopVoice?

    fun forEngine(engine: TtsEngine): DesktopVoice?

    val localVoice: LocalVoiceInstaller?

    /** Narration stopped: drop what is cheap to open again (the speech-dispatcher socket, the KittenTTS session and its thread). */
    fun releaseIdle() {}

    /** The app is closing: release everything. */
    fun close() {}
}

/**
 * 1.1 desktop [Narrator] (Android: `NarratorImpl`): the same queue of paragraphs ([ParagraphQueue]), the same `currentParagraph`
 * the reader highlights and scrolls to, and the same cloud requests ([app.nijiyome.novel.CloudTts]). File voices (espeak-ng, SAPI,
 * KittenTTS, cloud) synthesise each paragraph to `<cacheDir>/<sha256>.<ext>` (prefetching the next one) and [player] plays it, so
 * pause holds the clip mid-sentence and the speed changes at once. speech-dispatcher speaks by itself and is paused / resumed
 * through SSIP. Pausing while a paragraph is still being prepared cancels it and resume restarts that paragraph, as on Android.
 */
internal class DesktopNarrator(
    private val catalog: VoiceCatalog,
    private val player: ClipPlayer,
    private val cacheDir: File,
    private val settings: () -> TtsSettings,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Narrator, NarratorErrors, NarratorOptions {
    private val _state = MutableStateFlow(TtsState.IDLE)
    override val state: StateFlow<TtsState> = _state.asStateFlow()
    private val _current = MutableStateFlow<Int?>(null)
    override val currentParagraph: StateFlow<Int?> = _current.asStateFlow()
    private val _lastError = MutableStateFlow<Throwable?>(null)
    override val lastError: StateFlow<Throwable?> = _lastError.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = ParagraphQueue()
    private val lock = Any()
    private var job: Job? = null
    private var speedOverride: Float? = null

    /** The live voice speaking now (speech-dispatcher), for pause / resume. */
    @Volatile
    private var live: LiveVoice? = null

    // ------------------------------------------------------------------------------------------------------------- NarratorOptions

    override val engines: List<TtsEngine> = listOf(TtsEngine.SYSTEM, TtsEngine.KITTEN, TtsEngine.OPENAI, TtsEngine.ELEVENLABS, TtsEngine.OPENAI_COMPATIBLE)

    override fun hasPitch(engine: TtsEngine): Boolean = engine == TtsEngine.SYSTEM

    override suspend fun status(engine: TtsEngine): NarratorEngineStatus? = when (engine) {
        TtsEngine.SYSTEM -> catalog.system()?.status() ?: NarratorEngineStatus(false, problem = NarratorProblem.NO_SYSTEM_VOICE)
        else -> catalog.forEngine(engine)?.status()
    }

    override val localVoice: LocalVoiceInstaller? get() = catalog.localVoice

    // ------------------------------------------------------------------------------------------------------------- Narrator

    override suspend fun speak(chapter: NovelChapterText, fromParagraph: Int) {
        synchronized(lock) {
            cancelRun()
            queue.load(chapter.paragraphs, fromParagraph)
            _lastError.value = null
            start()
        }
    }

    override fun pause() {
        synchronized(lock) {
            val st = _state.value
            if (st != TtsState.SPEAKING && st != TtsState.PREPARING) return
            // A clip / a message in progress holds where it is; anything else (still synthesising) is cancelled and restarted.
            val held = st == TtsState.SPEAKING && (live?.pause() ?: player.pause())
            if (!held) cancelRun()
            _state.value = TtsState.PAUSED
        }
    }

    override fun resume() {
        synchronized(lock) {
            if (_state.value != TtsState.PAUSED || queue.isEmpty) return
            val running = job?.isActive == true
            val resumed = running && (live?.resume() ?: player.resume())
            if (resumed) _state.value = TtsState.SPEAKING else start()
        }
    }

    /** Stops reading (the reader closed, or Stop): also releases the engines until the next narration ([releaseIdle]). */
    override fun stop() {
        reset()
        releaseIdle()
    }

    private fun reset() {
        synchronized(lock) {
            cancelRun()
            queue.clear()
            _state.value = TtsState.IDLE
            _current.value = null
            _lastError.value = null
        }
    }

    /** The audio-only libmpv session, the speech-dispatcher socket and the KittenTTS session and thread; reopened on demand. */
    private fun releaseIdle() {
        player.release()
        catalog.releaseIdle()
    }

    override fun setSpeed(speed: Float) {
        val s = if (speed.isFinite()) speed.coerceIn(0.5f, 2f) else 1f
        speedOverride = s
        player.setSpeed(s)
    }

    override suspend fun voices(engine: TtsEngine): List<TtsVoice> {
        val s = settings().sanitized()
        val voice = if (engine == TtsEngine.SYSTEM) catalog.system() else catalog.forEngine(engine)
        return runCatching { voice?.voices(s) }.getOrNull().orEmpty()
    }

    override suspend fun preview(text: String): Result<Unit> {
        reset()
        val run = synchronized(lock) {
            _state.value = TtsState.PREPARING
            scope.async {
                val s = settings().sanitized()
                val speed = speedOverride ?: s.speed
                when (val v = voiceFor(s)) {
                    is LiveVoice -> {
                        markSpeaking()
                        live = v
                        try {
                            v.speak(text, s, speed)
                        } finally {
                            live = null
                        }
                    }
                    is FileVoice -> {
                        val file = synth(v, text, s)
                        markSpeaking()
                        player.play(file, speed)
                    }
                    else -> throw TtsException(TtsFailure.ENGINE_UNAVAILABLE)
                }
            }.also { job = it }
        }
        return try {
            run.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            // Stopped (the settings' Stop button, or reading started): not a failure. The caller's own cancellation goes on.
            if (!run.isCancelled || !kotlin.coroutines.coroutineContext[Job]!!.isActive) throw e
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            synchronized(lock) {
                if (job === run) {
                    job = null
                    _state.value = TtsState.IDLE
                }
            }
        }
    }

    /** The app is closing (Koin `onClose`): stops, releases every engine and ends this narrator's coroutines. Not usable afterwards. */
    fun release() {
        if (released) return
        released = true
        reset()
        player.release()
        catalog.close()
        scope.cancel()
    }

    @Volatile
    internal var released: Boolean = false
        private set

    // ------------------------------------------------------------------------------------------------------------- run loop

    private fun cancelRun() {
        job?.cancel()
        job = null
        live = null
    }

    private fun voiceFor(s: TtsSettings): DesktopVoice = when (s.engine) {
        TtsEngine.SYSTEM -> catalog.system() ?: throw TtsException(TtsFailure.ENGINE_UNAVAILABLE)
        else -> catalog.forEngine(s.engine) ?: throw TtsException(TtsFailure.ENGINE_UNAVAILABLE)
    }

    private fun start() {
        job?.cancel()
        _state.value = TtsState.PREPARING
        job = scope.launch {
            val s = settings().sanitized()
            try {
                when (val v = voiceFor(s)) {
                    is LiveVoice -> runLive(v, s)
                    is FileVoice -> runFiles(v, s)
                    else -> throw TtsException(TtsFailure.ENGINE_UNAVAILABLE)
                }
                // Reached the end of the chapter (SPEAKING → IDLE: the reader goes on with the next one).
                synchronized(lock) {
                    queue.clear()
                    _state.value = TtsState.IDLE
                    _current.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(lock) {
                    _lastError.value = e
                    _state.value = TtsState.IDLE
                    _current.value = null
                }
            }
        }
    }

    private suspend fun runLive(v: LiveVoice, s: TtsSettings) {
        while (true) {
            val (index, text) = synchronized(lock) { queue.current() } ?: return
            _current.value = index
            markSpeaking()
            live = v
            try {
                for (chunk in NovelText.speechChunks(text)) v.speak(chunk, s, speedOverride ?: s.speed)
            } finally {
                live = null
            }
            synchronized(lock) { queue.advance() }
        }
    }

    private suspend fun runFiles(v: FileVoice, s: TtsSettings) {
        var prefetch: Deferred<File>? = null
        var prefetchIndex = -1
        try {
            while (true) {
                val (index, text) = synchronized(lock) { queue.current() } ?: return
                _current.value = index
                val file = if (prefetchIndex == index && prefetch != null) prefetch.await() else synth(v, text, s)
                val next = synchronized(lock) { queue.peekNext() }
                prefetch?.takeIf { prefetchIndex != index }?.cancel()
                if (next != null) {
                    prefetchIndex = next.first
                    prefetch = scope.async(Dispatchers.IO) { synth(v, next.second, s) }
                } else {
                    prefetch = null
                    prefetchIndex = -1
                }
                markSpeaking()
                player.play(file, speedOverride ?: s.speed)
                synchronized(lock) { queue.advance() }
            }
        } finally {
            prefetch?.cancel()
        }
    }

    /** PREPARING → SPEAKING. Paused meanwhile (a live voice that ended its message just after PAUSE): waits for the resume. */
    private suspend fun markSpeaking() {
        _state.first { it != TtsState.PAUSED }
        synchronized(lock) { if (_state.value == TtsState.PREPARING) _state.value = TtsState.SPEAKING }
    }

    private suspend fun synth(v: FileVoice, text: String, s: TtsSettings): File {
        val key = cacheKey(s, v.cacheMaterial(s), text)
        return v.synthesize(text, s, File(cacheDir, "$key.${v.extension}"))
    }

    companion object {
        const val CACHE_DIR: String = "tts"

        /** Cache name of one paragraph: sha256 of everything that changes the clip (the speed does not: it is applied when playing). */
        fun cacheKey(s: TtsSettings, material: String, text: String): String {
            val all = listOf(s.engine.id, s.endpoint.trim(), s.model.trim(), s.voice.orEmpty(), material, text).joinToString("\u0001")
            return MessageDigest.getInstance("SHA-256").digest(all.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
