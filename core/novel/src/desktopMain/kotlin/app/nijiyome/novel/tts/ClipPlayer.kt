// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.Container
import app.nijiyome.novel.TtsException
import app.nijiyome.novel.TtsFailure
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerSessionFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File

/** Plays the narrator's clips. Tests use a fake; the app plays them with libmpv ([MpvClipPlayer]). */
internal interface ClipPlayer {
    /** Plays [file] from the start at [speed] and returns when it ended; cancelling stops it. Throws [TtsException] (NOT_AUDIO). */
    suspend fun play(file: File, speed: Float)

    /** Pauses the clip in progress; false when nothing is playing. */
    fun pause(): Boolean

    /** Resumes a paused clip; false when there is none. */
    fun resume(): Boolean

    fun setSpeed(speed: Float)

    fun release()
}

/**
 * [ClipPlayer] over a desktop libmpv [PlayerSession] made by [factory] (an audio-only factory: no video track, no surface). One session,
 * created on the first clip and kept; every call runs on [main], MediaMP's thread.
 */
internal class MpvClipPlayer(private val factory: PlayerSessionFactory, private val main: CoroutineDispatcher) : ClipPlayer {
    private val scope = CoroutineScope(SupervisorJob() + main)

    @Volatile
    private var session: PlayerSession? = null

    @Volatile
    private var playing = false

    @Volatile
    private var paused = false

    /** The speed set while a clip plays (it wins over the one the clip was started with). */
    @Volatile
    private var currentSpeed: Float? = null

    override suspend fun play(file: File, speed: Float) {
        val playable = playableFile(file)
        currentSpeed = null
        val s = withContext(main) { session ?: factory.create(scope, object : PlaybackListener {}, remote = false).also { session = it } }
        try {
            val loadId = withContext(main) {
                val before = s.state.value.loadId
                s.load(PlayableSource(playable.absolutePath, emptyMap(), Container.PROGRESSIVE, "narration", localFile = true), startMs = 0)
                paused = false
                playing = true
                before + 1
            }
            // MediaMP applies the load after this call and resets the speed then: set it once the clip started.
            val started = s.state.first { it.loadId >= loadId && (it.started || it.ended || it.error != null) }
            if (started.error == null) withContext(main) { s.setSpeed(currentSpeed ?: speed) }
            val end = s.state.first { it.loadId >= loadId && (it.ended || it.error != null) }
            if (end.error != null) throw TtsException(TtsFailure.NOT_AUDIO)
        } finally {
            playing = false
            paused = false
            // Cancelled mid-clip (pause → stop, a new chapter): silence it now.
            withContext(NonCancellable + main) { if (session === s && !s.state.value.ended) s.pause() }
        }
    }

    /** The FFmpeg of MediaMP's runtime reads FLAC but has no WAV demuxer: a WAV clip is played from a FLAC copy next to it. */
    private suspend fun playableFile(file: File): File {
        if (!file.name.endsWith(".wav", ignoreCase = true)) return file
        val flac = File(file.parentFile, file.name.substringBeforeLast('.') + ".flac")
        if (flac.isFile && flac.lastModified() >= file.lastModified()) return flac
        return withContext(Dispatchers.IO) {
            val bytes = Flac.fromWav(file.readBytes()) ?: throw TtsException(TtsFailure.NOT_AUDIO)
            val part = File(flac.parentFile, flac.name + ".part")
            part.writeBytes(bytes)
            part.commitTo(flac)
        }
    }

    override fun pause(): Boolean {
        val s = session ?: return false
        if (!playing || paused) return false
        paused = true
        scope.launch(main) { s.pause() }
        return true
    }

    override fun resume(): Boolean {
        val s = session ?: return false
        if (!playing || !paused) return false
        paused = false
        scope.launch(main) { s.play() }
        return true
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
        val s = session ?: return
        scope.launch(main) { s.setSpeed(speed) }
    }

    /**
     * Releases the libmpv instance (the next clip opens a new one). Done right away on MediaMP's thread when called there, else
     * waiting up to [RELEASE_WAIT_MS] for it, so the app's exit does not leave the native instance behind.
     */
    override fun release() {
        val s = session ?: return
        session = null
        playing = false
        paused = false
        if (java.awt.EventQueue.isDispatchThread()) {
            runCatching { s.release() }
            return
        }
        runBlocking { withTimeoutOrNull(RELEASE_WAIT_MS) { withContext(main) { runCatching { s.release() } } } }
    }

    private companion object {
        const val RELEASE_WAIT_MS = 3_000L
    }
}
