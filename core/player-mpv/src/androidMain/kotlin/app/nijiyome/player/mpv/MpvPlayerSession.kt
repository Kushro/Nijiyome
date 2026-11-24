// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import androidx.media3.common.util.UnstableApi
import app.nijiyome.model.AspectMode
import app.nijiyome.model.DebandConfig
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.ImageAdjust
import app.nijiyome.model.QualityTier
import app.nijiyome.model.SharpenLevel
import app.nijiyome.framegen.FgController
import app.nijiyome.player.RemoteProcessingState
import app.nijiyome.framegen.FrameGenController
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.QualityUiState
import app.nijiyome.player.mpv.quality.AssetShaderInstaller
import app.nijiyome.player.mpv.quality.FrameDropMonitor
import app.nijiyome.player.mpv.quality.MpvQualityController
import app.nijiyome.player.mpv.quality.QualityMpvPort
import app.nijiyome.player.mpv.quality.ThermalWatcher
import app.nijiyome.player.mpv.quality.isShaderCompilationError
import app.nijiyome.player.ActivePlayback
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerSessionFactory
import app.nijiyome.player.SessionTicker
import app.nijiyome.player.MPV_START_TIMEOUT_MS
import app.nijiyome.player.ScreenSubtitleSpec
import app.nijiyome.player.SubtitleFonts
import app.nijiyome.player.mpvStartWatched
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [PlayerSession] over one [MpvEngine]: maps [MpvState] to the shared [PlaybackState] (so feature/player draws the same controls for
 * every engine) and reports progress like the Media3 engine. Create on the main thread; [release] exactly once.
 */
@UnstableApi
class MpvPlayerSession(
    context: Context,
    private val scope: CoroutineScope,
    private val listener: PlaybackListener,
    remote: Boolean,
    /** 0.4.0: Settings › Reproductor quality defaults (preset, tier, deband, automatic adjustments...), changed live afterwards. */
    initialQuality: QualityRequest = QualityRequest(),
    /** 0.4.0 (worker B): the app-wide phone frame generation controller (null = FG not wired, e.g. tests). */
    frameGenController: FgController? = null,
    /** 0.4.0 integration: whether the PC processes this stream (Link PC / Mixto); local shaders stay off while it does. */
    remoteProcessing: RemoteProcessingState? = null,
    /** v1.1: `AppSettings.mpvTlsVerify` when the session was created (off = tls-verify=no, as before). */
    tlsVerify: Boolean = false,
) : PlayerSession {
    val engine = MpvEngine(context, tlsVerify)

    /** 0.4.0 (worker B): routes this engine's output through the phone FG (see MpvFrameGenBridge); released before the engine. */
    private val frameGenBridge: MpvFrameGenBridge? = frameGenController?.let { MpvFrameGenBridge(engine, it) }

    /** 0.4.0 (worker B): phone frame generation of this playback for the in-player UI; null when FG is not wired. */
    val frameGen: FrameGenController? = frameGenController
    private val _state = MutableStateFlow(PlaybackState(isBuffering = false, fullAss = true))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var released = false
    private var loaded = false

    /** 0.4.1 (Link Mixto): [finishAsEnded] ended this load; mpv is paused and [PlaybackState.ended] stays up until play / seek / load. */
    @Volatile
    private var endedHere = false
    private var wasPlaying = false
    private val mirror: Job
    private val fonts = SubtitleFonts(context)

    /** 0.4.1: writes only the sub-* options a style change touches (live while a slider moves). */
    private val subtitleApplier = MpvSubtitleStyleApplier { name, value -> engine.setProperty(name, value) }

    /** Imported font file -> its family (reading the name table once per file, not on every slider step). */
    private var fontFamilyCache: Pair<String, String?>? = null

    /** parity wave A (15 §3.2): the engine as a Media3 Player for PlaybackService (background, notification, headset buttons). */
    val adapter: MpvPlayerAdapter = MpvPlayerAdapter(engine, Looper.getMainLooper())

    private val ticker = SessionTicker(
        scope = scope,
        listener = listener,
        remote = remote,
        now = { SystemClock.elapsedRealtime() },
        snapshot = { val s = _state.value; Triple(currentPositionMs(), s.durationMs, s.isPlaying) },
    )

    private val appContext = context.applicationContext

    /**
     * 0.4.0 (worker A): owns every quality property of this mpv (shader chain, deband, image, aspect, decoder, smoothing) and the
     * automatic adjustments (thermal, battery, frame drops, 4K / 8K, view and PiP guards). Replaces BUG-043's PresetGovernor.
     */
    private val qualityController = MpvQualityController(
        port = object : QualityMpvPort {
            override fun setProperty(name: String, value: String) = engine.setProperty(name, value)

            override fun getProperty(name: String): String? = engine.getProperty(name)
        },
        shaders = AssetShaderInstaller(appContext),
        initial = initialQuality,
        fp16 = FP16_ANIME4K,
    )
    private val thermal = ThermalWatcher(appContext, scope) { hot -> qualityController.onThermal(hot) }
    private var lastQualityEventSeq = 0
    private var monitorJob: Job? = null
    private var qualityEventsJob: Job? = null
    private var frameGenLinkJob: Job? = null
    private var remoteProcessingJob: Job? = null
    private var startWatch: Job? = null
    private var prepareJob: Job? = null
    private var audioLang: String? = null
    private var subLang: String? = null

    /** loadId of the last load watched by the start timeout ([mpvStartWatched]); its errors before a frame go to Media3 (BUG-080). */
    @Volatile private var watchedLoadId = -1

    private fun battery(): BatteryInfo? = runCatching {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return@runCatching null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return@runCatching null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        BatteryInfo(level * 100 / scale, charging)
    }.getOrNull()

    /** Telemetry and frame drops every [FrameDropMonitor.SAMPLE_MS], battery every [BATTERY_CHECK_MS], while the session lives. */
    private fun startMonitor() {
        monitorJob = scope.launch {
            var sinceBattery = 0L
            while (isActive && !released) {
                delay(FrameDropMonitor.SAMPLE_MS)
                if (released) break
                sinceBattery += FrameDropMonitor.SAMPLE_MS
                if (sinceBattery >= BATTERY_CHECK_MS) {
                    sinceBattery = 0
                    battery().let { qualityController.onBattery(it?.percent, it?.charging == true) }
                }
                qualityController.refreshTelemetry()
                val st = _state.value
                if (!st.isPlaying || st.isBuffering) {
                    qualityController.onPlaybackIdle()
                    continue
                }
                val drops = engine.droppedFrames() ?: continue
                val fps = engine.videoFps() ?: continue
                qualityController.onDropSample(drops, fps, SystemClock.elapsedRealtime())
            }
        }
    }

    private fun displayRefreshRate(): Float? = runCatching {
        appContext.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
    }.getOrNull()

    init {
        battery().let { qualityController.onBattery(it?.percent, it?.charging == true) }
        qualityController.onDisplayRefreshRate(displayRefreshRate())
        // Shader compile / link errors arrive on mpv's event thread; the controller reacts on the session's scope.
        engine.logListener = { level, prefix, text ->
            if (isShaderCompilationError(level, "$prefix: $text")) scope.launch { if (!released) qualityController.onShaderError() }
        }
        thermal.start()
        startMonitor()
        qualityEventsJob = scope.launch {
            qualityController.state.collect { q ->
                val e = q.lastEvent ?: return@collect
                if (e.seq == lastQualityEventSeq) return@collect
                lastQualityEventSeq = e.seq
                // 0.4.0 (worker D): the player shows QualityUiState.lastEvent itself (PlayerNotice.Quality); the 0.3.x shaderNotice
                // toast it used to feed here is gone, so users never see both.
                android.util.Log.i("mpv", "quality ${e.kind} (${e.reason}): ${e.from} -> ${e.to}")
            }
        }
        // 0.4.0 integration (A <-> B): the quality engine drops mpv cadence interpolation and keeps its chain sized for the
        // frame generation input while FG runs; FG pauses with the quality engine's battery Eco, like Enigmata.
        remoteProcessing?.let { rp ->
            remoteProcessingJob = scope.launch { rp.active.collect { on -> if (!released) qualityController.setRemoteProcessing(on) } }
        }
        frameGenController?.let { fg ->
            frameGenLinkJob = scope.launch {
                launch { fg.flag.active.collect { on -> if (!released) qualityController.setFrameGenActive(on) } }
                launch {
                    qualityController.state.collect { q -> if (!released) fg.onLowPower(q.batteryLow) }
                }
            }
        }
        mirror = scope.launch {
            engine.state.collect { m ->
                qualityController.onVideoSize(m.videoWidth, m.videoHeight)
                qualityController.onViewport(m.surfaceWidth, m.surfaceHeight)
                // Hito 4: the mapping is shared with the desktop session (commonMain MpvState.kt, moved verbatim).
                _state.value = mpvPlaybackState(_state.value, m, loaded = loaded, endedHere = endedHere, watched = m.loadId == watchedLoadId)
                if (wasPlaying && !m.playing && loaded && !released) listener.onProgress(currentPositionMs(), _state.value.durationMs, true)
                wasPlaying = m.playing
            }
        }
        ticker.start()
        ActivePlayback.publish(adapter)
    }

    override fun load(source: PlayableSource, startMs: Long) {
        if (released) return
        loaded = true
        endedHere = false
        ActivePlayback.publish(adapter)
        qualityController.onFileStarted()
        startWatch?.cancel()
        prepareJob?.cancel()
        val loadId = engine.beginLoad()
        val startSecs = startMs.coerceAtLeast(0) / 1000.0
        val subs = mpvSubtitles(source.subtitles, source.selectSubtitle)
        if (!HlsPrefetch.applies(source)) {
            open(source, source.url, startSecs, subs, loadId)
            return
        }
        // BUG-059: FFmpeg waits for every rendition of an HLS master; hand mpv a master with only the variant and the languages it
        // will play. The PC's extension-server sources (no headers needed) are the ones with 15 audio renditions.
        prepareJob = scope.launch {
            val url = withContext(Dispatchers.IO) { HlsPrefetch.filteredMaster(appContext.cacheDir, source.url, audioLang, subLang) } ?: source.url
            if (!released && engine.state.value.loadId == loadId) open(source, url, startSecs, subs, loadId)
        }
    }

    private fun open(source: PlayableSource, url: String, startSecs: Double, subs: List<MpvSubtitle>, loadId: Int) {
        val lavf = HlsPrefetch.lavfOptions(url, source.url)
        if (lavf.isNotEmpty()) android.util.Log.i("mpv", "filtered HLS master opened with demuxer-lavf-o=$lavf")
        watchedLoadId = if (mpvStartWatched(source.container, source.torrent)) loadId else -1
        engine.loadPrepared(url, source.headers, startSecs, subs, lavf, subtitlesOff = source.selectSubtitle == PlayableSource.SUBTITLES_OFF)
        // BUG-059: a network stream that shows no frame in time is given up (the view-model moves it to Media3). Safety net only:
        // the filtered master above is what makes such streams start.
        startWatch = if (mpvStartWatched(source.container, source.torrent)) {
            scope.launch {
                delay(MPV_START_TIMEOUT_MS)
                val m = engine.state.value
                if (!released && m.loadId == loadId && !m.started && m.error == null) {
                    android.util.Log.w("mpv", "no frame after ${MPV_START_TIMEOUT_MS / 1000} s: giving up on this load")
                    engine.failStart(START_TIMEOUT)
                }
            }
        } else {
            null
        }
    }

    override fun play() {
        endedHere = false
        engine.play()
    }

    override fun finishAsEnded() {
        if (released || !loaded || endedHere) return
        endedHere = true
        engine.pause()
        _state.value = _state.value.copy(error = null, ended = true, isBuffering = false, isPlaying = false)
        listener.onProgress(currentPositionMs(), _state.value.durationMs, true)
    }

    override fun pause() = engine.pause()

    override fun togglePlay() = if (_state.value.isPlaying) pause() else play()

    override fun seekTo(positionMs: Long) {
        endedHere = false
        qualityController.onPlaybackIdle()
        engine.seekTo(positionMs.coerceAtLeast(0) / 1000.0)
    }

    override fun seekBy(deltaMs: Long) = seekTo(currentPositionMs() + deltaMs)

    override fun setSpeed(speed: Float) = engine.setSpeed(speed.toDouble())

    /** mpv picks the stream quality itself; adaptive height caps are not offered on this engine (no video heights in the state). */
    override fun selectVideoHeight(height: Int?) = Unit

    override fun selectSubtitle(groupIndex: Int?) = engine.selectSubtitle(groupIndex)

    override fun selectAudio(id: Int) = engine.selectAudio(id)

    override fun setPreferredLanguages(subtitle: String?, audio: String?) {
        subLang = subtitle
        audioLang = audio
        engine.setPreferredLanguages(subtitle, audio)
    }

    override fun setSubtitleStyle(style: SubtitleStyle) {
        if (released) return
        val imported = style.fontFile?.let { file ->
            fontFamilyCache?.takeIf { it.first == file }?.second ?: fonts.familyName(file).also { fontFamilyCache = file to it }
        }
        // libass does not scale sub-line-spacing: screen pixels per "scaled pixel" (720 px tall window; the player is landscape).
        val dm = appContext.resources.displayMetrics
        val pxPerUnit = minOf(dm.widthPixels, dm.heightPixels) / ScreenSubtitleSpec.REFERENCE_HEIGHT
        subtitleApplier.apply(mpvSubtitleProperties(style, mpvFontFamily(style, imported), pxPerUnit))
    }

    override fun setSubtitleDelay(ms: Long) = engine.setSubtitleDelay(ms)

    override fun addSubtitleFile(path: String, title: String): Boolean {
        engine.addSubtitleFile(path, title)
        return true
    }

    /** 0.4.0: `sub-add` reads each URL (the PC's, through the loopback proxy): off the main thread. */
    override fun addSubtitleTracks(tracks: List<app.nijiyome.model.SubtitleTrack>, select: Int?): Boolean {
        if (released) return true
        val subs = mpvSubtitles(tracks, select)
        scope.launch(Dispatchers.IO) { if (!released) engine.addSubtitles(subs, off = select == PlayableSource.SUBTITLES_OFF) }
        return true
    }

    override val supportsSecondaryTracks: Boolean get() = true

    override fun selectSecondarySubtitle(id: Int?): Boolean {
        engine.selectSecondarySubtitle(id)
        return true
    }

    override fun setSubtitlesVisible(visible: Boolean) = engine.setSubtitlesVisible(visible)

    override fun setLoop(loop: Boolean) = engine.setLoop(loop)

    override fun setStableVolume(enabled: Boolean) = engine.setStableVolume(enabled)

    override fun screenshotToFile(path: String): Boolean = engine.screenshotToFile(path)

    override fun currentPositionMs(): Long = (engine.currentPositionSecs() * 1000).toLong()

    // ---- 0.4.0 quality engine (worker A): everything applies to the running mpv, no reload --------------------------------------

    override val quality: StateFlow<QualityUiState> get() = qualityController.state

    override fun setQuality(preset: String, tier: QualityTier) = qualityController.setQuality(preset, tier)

    override fun setQualityRequest(request: QualityRequest) = qualityController.setRequest(request)

    override fun setSharpen(level: SharpenLevel) = qualityController.setSharpen(level)

    override fun setImage(image: ImageAdjust) = qualityController.setImage(image)

    override fun setAspect(mode: AspectMode, zoomPercent: Int) = qualityController.setAspect(mode, zoomPercent)

    override fun setDecoder(mode: DecoderMode) = qualityController.setDecoder(mode)

    override fun setDeband(deband: DebandConfig) = qualityController.setDeband(deband)

    override fun setSmoothing(enabled: Boolean) {
        // The panel may have switched modes since the session started (120 Hz <-> 60 Hz): read it when smoothing turns on.
        if (enabled) qualityController.onDisplayRefreshRate(displayRefreshRate())
        qualityController.setSmoothing(enabled)
    }

    override fun setAutoAdjust(enabled: Boolean) = qualityController.setAutoAdjust(enabled)

    override fun undoAutoDowngrade() = qualityController.undoAutoDowngrade()

    override fun setPictureInPicture(active: Boolean) {
        qualityController.setPictureInPicture(active)
        frameGen?.onPip(active) // 0.4.0 integration: FG pauses in picture-in-picture
    }

    override fun setRemoteProcessing(active: Boolean) = qualityController.setRemoteProcessing(active)

    override fun setFrameGenActive(active: Boolean) = qualityController.setFrameGenActive(active)

    override fun release() {
        if (released) return
        if (loaded) listener.onProgress(currentPositionMs(), _state.value.durationMs, true)
        released = true
        ticker.stop()
        monitorJob?.cancel()
        qualityEventsJob?.cancel()
        frameGenLinkJob?.cancel()
        remoteProcessingJob?.cancel()
        thermal.stop()
        engine.logListener = null
        startWatch?.cancel()
        prepareJob?.cancel()
        mirror.cancel()
        if (ActivePlayback.player.value === adapter) ActivePlayback.publish(null)
        adapter.detach()
        frameGenBridge?.release() // 0.4.0 (worker B): FG off and unbound before MPVLib.destroy
        engine.release()
    }
}

private const val BATTERY_CHECK_MS = 60_000L

/**
 * 0.4.0: Anime4K runs from FP16 (mediump) copies (Enigmata's default; cheaper on Adreno / Mali). A shader error on such a chain switches
 * to full precision for the rest of the session ([MpvQualityController.onShaderError]); flip this to ship FP32 only.
 */
private const val FP16_ANIME4K = true

/** Battery level from the sticky `ACTION_BATTERY_CHANGED` broadcast. */
internal data class BatteryInfo(val percent: Int, val charging: Boolean)

/** Koin-bound factory of mpv sessions (see `mpvPlayerModule`). [quality] (the Settings › Reproductor defaults) is read at creation time. */
@UnstableApi
class MpvSessionFactory(
    private val context: Context,
    private val quality: () -> QualityRequest,
    // 0.4.0 (worker B): phone frame generation (null = not wired).
    private val frameGen: FgController? = null,
    // 0.4.0 integration: Link "PC" / "Mixto" turn the local shaders off (set by the player through LocalEnhancementGate).
    private val remoteProcessing: RemoteProcessingState? = null,
    /** v1.1: Settings › Reproductor "verify certificates" for libmpv, read when a session is created. */
    private val tlsVerify: () -> Boolean = { false },
) : PlayerSessionFactory {
    override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean): PlayerSession =
        MpvPlayerSession(context, scope, listener, remote, quality(), frameGen, remoteProcessing, tlsVerify())
}
