// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerSessionFactory
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.RemoteProcessingState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.openani.mediamp.mpv.MpvMediampPlayer
import java.io.File

/**
 * Koin-bound factory of desktop mpv sessions (see [desktopMpvPlayerModule]). [quality] (the Settings › Reproductor defaults) is read at
 * creation time. Everything the engine needs on disk lives under `<cacheDir>/mpv/`: the native runtime MediaMP extracts from its jar
 * (`runtime/`), the bundled subtitle fonts (`fonts/`) and the installed shader chains (`shaders/`).
 *
 * One mpv instance per session; the next episode loads into the same one (the player screen keeps its session).
 */
class DesktopMpvSessionFactory(
    private val cacheDir: File,
    private val quality: () -> QualityRequest = { QualityRequest() },
    private val remoteProcessing: RemoteProcessingState? = null,
    /** MediaMP's state machine thread (the Swing EDT in the app); sessions are driven on it. */
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    /** Extra mpv options of every engine (tests: `ao=null`). */
    private val extraOptions: Map<String, String> = emptyMap(),
    /** mpv's `sub-fonts-dir`: the bundled fonts for libass and the user's imported subtitle fonts (`DesktopSubtitleFonts`). */
    private val fontsDir: File = File(File(cacheDir, "mpv"), "fonts"),
    /** The last volume the user set, so the next player opens at it (kept across runs when it has a store). */
    private val volumeMemory: PlayerVolumeMemory = PlayerVolumeMemory(),
    /** 1.1: the loopback media proxy of Settings › Red (null = none). */
    private val mediaRoute: app.nijiyome.model.LocalMediaRoute? = null,
    /** 1.1: sessions that only play audio clips (the novel narrator): no video track, no wait for a video surface. */
    private val audioOnly: Boolean = false,
) : PlayerSessionFactory {
    private val root = File(cacheDir, "mpv")
    private val shaders by lazy { DesktopShaderInstaller(File(root, "shaders")) }

    override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean): PlayerSession {
        mediaRoute?.warm()
        return DesktopMpvPlayerSession(
            engine = newEngine(),
            scope = scope,
            listener = listener,
            remote = remote,
            initialQuality = quality(),
            shaders = shaders,
            cacheDir = cacheDir,
            fontsDir = fontsDir,
            remoteProcessing = remoteProcessing,
            onVolume = volumeMemory::remember,
            mediaRoute = mediaRoute,
        )
    }

    private fun newEngine(): DesktopMpvEngine {
        DesktopMpvRuntime.prepare(File(root, "runtime"))
        DesktopMpvAssets.installSubtitleFonts(fontsDir)
        val volume = volumeMemory.last()?.let { mapOf("volume" to it.percent.toString(), "mute" to if (it.muted) "yes" else "no") }.orEmpty()
        return DesktopMpvEngine(DesktopMpvConfig(fontsDir = fontsDir, extraOptions = volume + extraOptions, audioOnly = audioOnly), mainDispatcher)
    }
}

/** Where MediaMP loads libmpv + its JNI wrapper from; set once per process, before the first player. */
object DesktopMpvRuntime {
    @Volatile
    private var prepared: File? = null

    /** Extracts the classpath runtime into [dir] (validated, so a complete copy is reused) the first time; later calls do nothing. */
    @Synchronized
    fun prepare(dir: File) {
        if (prepared != null) return
        dir.mkdirs()
        // v1.1 (F2): the system's libva before MediaMP loads libmpv, so VAAPI works (see DesktopVideoDecoding.kt).
        SystemLibva.preload()
        MpvMediampPlayer.prepareLibraries(dir.absolutePath, extractRuntimeLibrary = true)
        prepared = dir
        DesktopVideoDecoding.warmUp()
    }
}
