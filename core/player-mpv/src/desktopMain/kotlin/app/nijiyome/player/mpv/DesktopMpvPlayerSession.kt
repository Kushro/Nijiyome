// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.model.AspectMode
import app.nijiyome.model.DebandConfig
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.ImageAdjust
import app.nijiyome.model.QualityTier
import app.nijiyome.model.SharpenLevel
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.player.MPV_START_TIMEOUT_MS
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerVolume
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.QualityUiState
import app.nijiyome.player.RemoteProcessingState
import app.nijiyome.player.ScreenSubtitleSpec
import app.nijiyome.player.SessionTicker
import app.nijiyome.player.mpv.quality.FrameDropMonitor
import app.nijiyome.player.mpv.quality.MpvQualityController
import app.nijiyome.player.mpv.quality.isShaderCompilationError
import app.nijiyome.player.mpv.quality.mpvLogInfo
import app.nijiyome.player.mpvStartWatched
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.DisplayMode
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.math.roundToInt

/**
 * Hito 4: the desktop [PlayerSession] over one [DesktopMpvEngine], the counterpart of androidMain `MpvPlayerSession`: the same
 * [PlaybackState] mapping ([mpvPlaybackState]), progress ticks, start watchdog, HLS master filter, subtitles and the whole quality
 * engine ([MpvQualityController], live, no reload). Create and drive it on the main thread (the Swing EDT); [release] exactly once.
 *
 * Not on desktop (no-ops, the Android session's phone-only parts): picture-in-picture, the phone frame generation (worker B), the
 * thermal watcher and the battery Eco rule (PowerManager / sticky battery broadcast), and the Media3 adapter for the notification /
 * headset buttons (Android's PlaybackService).
 */
class DesktopMpvPlayerSession internal constructor(
    internal val engine: DesktopMpvEngine,
    private val scope: CoroutineScope,
    private val listener: PlaybackListener,
    remote: Boolean,
    initialQuality: QualityRequest,
    shaders: DesktopShaderInstaller,
    private val cacheDir: File,
    private val fontsDir: File,
    remoteProcessing: RemoteProcessingState?,
    /** Told every volume change (the factory opens the next player at it). */
    private val onVolume: (PlayerVolume) -> Unit = {},
    /** 1.1: the loopback media proxy (Settings › Red); null = streams open directly. */
    private val mediaRoute: app.nijiyome.model.LocalMediaRoute? = null,
) : PlayerSession {
    private val _state = MutableStateFlow(PlaybackState(isBuffering = false, fullAss = true))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var released = false
    private var loaded = false

    /** 0.4.1 (Link Mixto): [finishAsEnded] ended this load; mpv is paused and [PlaybackState.ended] stays up until play / seek / load. */
    @Volatile
    private var endedHere = false
    private var wasPlaying = false
    private val mirror: Job

    private val subtitleApplier = MpvSubtitleStyleApplier { name, value -> engine.setProperty(name, value) }
    private var fontFamilyCache: Pair<String, String?>? = null

    /** The last style asked for: re-applied when the view's height changes (libass does not scale `sub-line-spacing`). */
    private var lastStyle: SubtitleStyle? = null
    private var lastViewportHeight = 0

    private val ticker = SessionTicker(
        scope = scope,
        listener = listener,
        remote = remote,
        now = { System.nanoTime() / 1_000_000 },
        snapshot = { val s = _state.value; Triple(currentPositionMs(), s.durationMs, s.isPlaying) },
    )

    private val qualityController = MpvQualityController(
        port = DesktopQualityPort(write = engine::setProperty, readProperty = engine::getProperty),
        shaders = shaders,
        initial = initialQuality,
        fp16 = FP16_ANIME4K,
        desktopShaders = DesktopMpvAssets.hasDesktopShaders,
    )
    private var monitorJob: Job? = null
    private var remoteProcessingJob: Job? = null
    private var startWatch: Job? = null
    private var prepareJob: Job? = null
    private var audioLang: String? = null

    /** The current source is the app's own file (local subtitle files are allowed with it, see MpvInputGuard). */
    private var currentLocalFile = false
    private var subLang: String? = null

    /** loadId of the last load watched by the start timeout ([mpvStartWatched]). */
    @Volatile
    private var watchedLoadId = -1

    /** Telemetry and frame drops every [FrameDropMonitor.SAMPLE_MS] while the session lives (Android also samples the battery). */
    private fun startMonitor() {
        monitorJob = scope.launch {
            while (isActive && !released) {
                delay(FrameDropMonitor.SAMPLE_MS)
                if (released) break
                qualityController.refreshTelemetry()
                val st = _state.value
                if (st.isPlaying) {
                    DesktopVideoDecoding.reportDecoder(qualityController.state.value.decoderInUse)
                    logRenderTarget()
                }
                if (!st.isPlaying || st.isBuffering) {
                    qualityController.onPlaybackIdle()
                    continue
                }
                val drops = engine.droppedFrames() ?: continue
                val fps = engine.videoFps() ?: continue
                qualityController.onDropSample(drops, fps, System.nanoTime() / 1_000_000)
            }
        }
    }

    /** loadId whose render target was logged ([logRenderTarget]). */
    private var renderLoggedFor = -1

    /** Once per load: the video, the render target (the surface, real pixels), mpv's own OSD size and the decoder. */
    private fun logRenderTarget() {
        val m = engine.state.value
        if (renderLoggedFor == m.loadId || m.surfaceWidth <= 0) return
        renderLoggedFor = m.loadId
        mpvLogInfo(
            "playing ${m.videoWidth}x${m.videoHeight} into ${m.surfaceWidth}x${m.surfaceHeight} px " +
                "(osd ${engine.getProperty("osd-width")}x${engine.getProperty("osd-height")}), " +
                "decoder ${qualityController.state.value.decoderInUse}, libva ${DesktopVideoDecoding.libva.value.source}",
        )
    }

    init {
        qualityController.onDisplayRefreshRate(displayRefreshRate())
        engine.logListener = { level, prefix, text ->
            if (isShaderCompilationError(level, "$prefix: $text")) scope.launch { if (!released) qualityController.onShaderError() }
        }
        startMonitor()
        remoteProcessing?.let { rp ->
            remoteProcessingJob = scope.launch { rp.active.collect { on -> if (!released) qualityController.setRemoteProcessing(on) } }
        }
        mirror = scope.launch {
            engine.state.collect { m ->
                qualityController.onVideoSize(m.videoWidth, m.videoHeight)
                qualityController.onViewport(m.surfaceWidth, m.surfaceHeight)
                if (m.surfaceHeight != lastViewportHeight) {
                    lastViewportHeight = m.surfaceHeight
                    lastStyle?.let { applyStyle(it) }
                }
                _state.value = mpvPlaybackState(_state.value, m, loaded = loaded, endedHere = endedHere, watched = m.loadId == watchedLoadId)
                if (wasPlaying && !m.playing && loaded && !released) listener.onProgress(currentPositionMs(), _state.value.durationMs, true)
                wasPlaying = m.playing
            }
        }
        ticker.start()
    }

    override fun load(source: PlayableSource, startMs: Long) {
        if (released) return
        currentLocalFile = source.localFile
        loaded = true
        endedHere = false
        qualityController.onFileStarted()
        startWatch?.cancel()
        prepareJob?.cancel()
        val loadId = engine.beginLoad()
        // 1.1 (link-servidor-7): a stream with credentials / Origin, or any while an outbound proxy is set, through the loopback proxy
        val source = try {
            routedSource(source, mediaRoute)
        } catch (e: app.nijiyome.model.LocalRouteUnavailable) {
            mpvLogInfo("${e.message}: not playing the stream outside the outbound proxy")
            engine.failStart("local media proxy unavailable")
            return
        }
        // Hito 4 (review): an extension's URL with another protocol (edl://, ytdl://, file://...) fails like any broken source.
        if (MpvInputGuard.mediaUrl(source.url, allowLocal = source.localFile) == null) {
            mpvLogInfo("refused source URL (not http / https)")
            engine.failStart("refused source URL")
            return
        }
        val startSecs = startMs.coerceAtLeast(0) / 1000.0
        val subs = mpvSubtitles(source.subtitles, source.selectSubtitle).filter { MpvInputGuard.mediaUrl(it.url, source.localFile) != null }
        if (!HlsPrefetch.applies(source)) {
            open(source, source.url, startSecs, subs, loadId)
            return
        }
        // BUG-059: FFmpeg waits for every rendition of an HLS master; hand mpv a master with only the variant and the languages it
        // will play (same as Android).
        prepareJob = scope.launch {
            val url = withContext(Dispatchers.IO) { HlsPrefetch.filteredMaster(cacheDir, source.url, audioLang, subLang) } ?: source.url
            if (!released && engine.state.value.loadId == loadId) open(source, url, startSecs, subs, loadId)
        }
    }

    private fun open(source: PlayableSource, url: String, startSecs: Double, subs: List<MpvSubtitle>, loadId: Int) {
        val lavf = HlsPrefetch.lavfOptions(url, source.url)
        if (lavf.isNotEmpty()) mpvLogInfo("filtered HLS master opened with demuxer-lavf-o=$lavf")
        val watched = mpvStartWatched(source.container, source.torrent)
        watchedLoadId = if (watched) loadId else -1
        engine.loadPrepared(url, source.headers, startSecs, subs, lavf, subtitlesOff = source.selectSubtitle == PlayableSource.SUBTITLES_OFF)
        // BUG-059: a network stream that shows no frame in time is given up (MPV_START_TIMEOUT_CODE), as on Android.
        startWatch = if (watched) {
            scope.launch {
                delay(MPV_START_TIMEOUT_MS)
                val m = engine.state.value
                if (!released && m.loadId == loadId && !m.started && m.error == null) {
                    mpvLogInfo("no frame after ${MPV_START_TIMEOUT_MS / 1000} s: giving up on this load")
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
        lastStyle = style
        applyStyle(style)
    }

    private fun applyStyle(style: SubtitleStyle) {
        val imported = style.fontFile?.let { file ->
            fontFamilyCache?.takeIf { it.first == file }?.second ?: importedFamily(file).also { fontFamilyCache = file to it }
        }
        // libass does not scale sub-line-spacing: pixels per "scaled pixel" of the view the video is drawn in (720 px reference).
        val pxPerUnit = viewportOrScreenHeight()?.let { it / ScreenSubtitleSpec.REFERENCE_HEIGHT } ?: 1f
        subtitleApplier.apply(mpvSubtitleProperties(style, mpvFontFamily(style, imported), pxPerUnit))
    }

    /** The family of the style's font file: an installed family or an imported file of the folder mpv reads ([DesktopMpvConfig.fontsDir]). */
    private fun importedFamily(name: String): String? = desktopSubtitleFamily(name, fontsDir)

    private fun viewportOrScreenHeight(): Float? {
        val m = engine.state.value
        if (m.surfaceWidth > 0 && m.surfaceHeight > 0) return minOf(m.surfaceWidth, m.surfaceHeight).toFloat()
        if (GraphicsEnvironment.isHeadless()) return null
        return runCatching {
            val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
            minOf(mode.width, mode.height).toFloat().takeIf { it > 0f }
        }.getOrNull()
    }

    override fun setSubtitleDelay(ms: Long) = engine.setSubtitleDelay(ms)

    override fun addSubtitleFile(path: String, title: String): Boolean {
        engine.addSubtitleFile(path, title)
        return true
    }

    /** `sub-add` reads each URL: off the main thread. */
    override fun addSubtitleTracks(tracks: List<app.nijiyome.model.SubtitleTrack>, select: Int?): Boolean {
        if (released) return true
        scope.launch(Dispatchers.IO) {
            // 1.1: through the loopback media proxy while an outbound proxy is set (the route may start it: off the main thread)
            val subs = mpvSubtitles(routedSubtitleTracks(tracks, mediaRoute), select).filter { MpvInputGuard.mediaUrl(it.url, currentLocalFile) != null }
            if (!released) engine.addSubtitles(subs, off = select == PlayableSource.SUBTITLES_OFF)
        }
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

    override fun changeVolume(deltaPercent: Int): PlayerVolume? {
        val current = engine.getProperty("volume")?.toDoubleOrNull() ?: return null
        val next = (current.roundToInt() + deltaPercent).coerceIn(0, MAX_VOLUME_PERCENT)
        engine.setProperty("volume", next.toString())
        engine.setProperty("mute", "no")
        return PlayerVolume(next, muted = false).also(onVolume)
    }

    override fun volume(): PlayerVolume? {
        val percent = engine.getProperty("volume")?.toDoubleOrNull()?.roundToInt() ?: return null
        return PlayerVolume(percent.coerceIn(0, MAX_VOLUME_PERCENT), muted = engine.getProperty("mute") == "yes")
    }

    override fun toggleMute(): PlayerVolume? {
        val muted = engine.getProperty("mute") ?: return null
        val percent = engine.getProperty("volume")?.toDoubleOrNull()?.roundToInt() ?: return null
        val next = muted != "yes"
        engine.setProperty("mute", if (next) "yes" else "no")
        return PlayerVolume(percent, muted = next).also(onVolume)
    }

    override fun currentPositionMs(): Long = (engine.currentPositionSecs() * 1000).toLong()

    // ---- quality engine: everything applies to the running mpv, no reload -------------------------------------------------------

    override val quality: StateFlow<QualityUiState> get() = qualityController.state

    override fun setQuality(preset: String, tier: QualityTier) = qualityController.setQuality(preset, tier)

    override fun setQualityRequest(request: QualityRequest) = qualityController.setRequest(request)

    override fun setSharpen(level: SharpenLevel) = qualityController.setSharpen(level)

    override fun setImage(image: ImageAdjust) = qualityController.setImage(image)

    override fun setAspect(mode: AspectMode, zoomPercent: Int) = qualityController.setAspect(mode, zoomPercent)

    override fun setDecoder(mode: DecoderMode) = qualityController.setDecoder(mode)

    override fun setDeband(deband: DebandConfig) = qualityController.setDeband(deband)

    override fun setSmoothing(enabled: Boolean) {
        // The monitor may have changed mode since the session started: read it when smoothing turns on.
        if (enabled) qualityController.onDisplayRefreshRate(displayRefreshRate())
        qualityController.setSmoothing(enabled)
    }

    override fun setAutoAdjust(enabled: Boolean) = qualityController.setAutoAdjust(enabled)

    override fun undoAutoDowngrade() = qualityController.undoAutoDowngrade()

    /** No picture-in-picture window on desktop. */
    override fun setPictureInPicture(active: Boolean) = Unit

    override fun setRemoteProcessing(active: Boolean) = qualityController.setRemoteProcessing(active)

    /** Phone frame generation (worker B) does not exist on desktop, so it never forces mpv's smoothing off. */
    override fun setFrameGenActive(active: Boolean) = Unit

    override fun release() {
        if (released) return
        if (loaded) listener.onProgress(currentPositionMs(), _state.value.durationMs, true)
        released = true
        ticker.stop()
        monitorJob?.cancel()
        remoteProcessingJob?.cancel()
        engine.logListener = null
        startWatch?.cancel()
        prepareJob?.cancel()
        mirror.cancel()
        engine.release()
    }
}

/** Anime4K from FP16 (mediump) copies, as on Android; a shader error switches the session to full precision. */
private const val FP16_ANIME4K = true

/** Refresh rate of the default screen (smoothing's `display-fps-override`; vo=libmpv has no display to ask). */
internal fun displayRefreshRate(): Float? = if (GraphicsEnvironment.isHeadless()) null else runCatching {
    val hz = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate
    hz.takeIf { it != DisplayMode.REFRESH_RATE_UNKNOWN && it > 0 }?.toFloat()
}.getOrNull()

/** mpv goes up to 130 %; the player stops at 100, like the system volume. */
private const val MAX_VOLUME_PERCENT = 100
