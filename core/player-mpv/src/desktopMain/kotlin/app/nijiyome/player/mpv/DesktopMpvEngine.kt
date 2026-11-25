// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.LanguageCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.openani.mediamp.PlaybackException
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.Screenshots
import org.openani.mediamp.mpv.EventListener
import org.openani.mediamp.mpv.MPVEvent
import org.openani.mediamp.mpv.MPVFormat
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.source.UriMediaData
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

// Hito 4: the desktop counterpart of androidMain MpvEngine (same public surface and the same MpvState semantics), over MediaMP 0.5.3
// (docs/spikes/reproductor.md). MediaMP owns the native handle, the render API (vo=libmpv into Skia textures, see MpvVideoSurface) and
// a single-writer state machine for open / play / pause / seek / close, so those go through [MpvMediampPlayer]; everything else
// (tracks, subtitles, filters, quality properties, telemetry) is plain mpv through the same [MPVHandle].
//
// mpv events: an MPVHandle has ONE event listener and MediaMP's machine needs it, so [install] wraps it (read by reflection from the
// handle's private `eventListener` field) in a fan-out that also feeds this engine.
// PENDIENTE-ESCRITORIO: la lectura por reflexión de MPVHandle.eventListener, MPVHandle.ptr y del ciclo de vida del contexto de render de MediaMP hay que revalidarla con cada versión de MediaMP (DesktopMpvEngineIntegrationTest y MpvSurfaceSmoke lo cubren)

/** Desktop libmpv options of an engine (production: none beyond the defaults; tests pass `ao=null`). */
internal class DesktopMpvConfig(
    /** `sub-fonts-dir`: the bundled static subtitle fonts are installed here (see [DesktopMpvAssets.installSubtitleFonts]). */
    val fontsDir: File,
    /** Extra mpv options set before `mpv_initialize`, after every default (they win). */
    val extraOptions: Map<String, String> = emptyMap(),
    /** 1.1 (novel narrator): audio clips only. No video track is decoded and a load does not wait for a video surface. */
    val audioOnly: Boolean = false,
)

/**
 * One libmpv instance (create and drive it on [mainDispatcher]'s thread, the Swing EDT in the app; [release] exactly once). Options
 * follow the Android engine where they mean the same on desktop (`idle`, `keep-open`, the demuxer cache and the network timeout for
 * torrent loopback streams, `sub-ass-override=no`); MediaMP sets the desktop video / audio outputs (`vo=libmpv`, `ao=pulse,alsa` /
 * `wasapi`) and `hwdec`. State updates come from mpv's event thread; [state] is safe to collect anywhere.
 */
internal class DesktopMpvEngine(
    private val config: DesktopMpvConfig,
    private val mainDispatcher: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(MpvState(buffering = false))
    val state: StateFlow<MpvState> get() = _state.asStateFlow()

    @Volatile
    private var released = false

    @Volatile
    private var fileLoaded = false

    @Volatile
    private var lastError: String? = null

    /**
     * Playlist entry id of the current load's file (from its START_FILE), [AWAITING_ENTRY] between [beginLoad] and that event. An
     * END_FILE of another entry belongs to a replaced file (`loadfile replace` reports the old one's end first) and is ignored.
     */
    @Volatile
    private var currentEntry = AWAITING_ENTRY

    /** External subtitles of the current load, added with `sub-add` once the file is loaded. */
    @Volatile
    private var pendingSubtitles: List<MpvSubtitle> = emptyList()

    @Volatile
    private var pendingSubtitlesOff = false

    private val subtitleLock = Any()

    @Volatile
    private var preferredSub: String? = null

    @Volatile
    private var subtitleDecided = false

    /** mpv log lines (level, prefix, text) for the quality engine's shader-error detection; called on mpv's threads. */
    @Volatile
    var logListener: ((Int, String, String) -> Unit)? = null

    private val machineScope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val immediate: CoroutineDispatcher =
        (mainDispatcher as? MainCoroutineDispatcher)?.let { runCatching { it.immediate }.getOrNull() } ?: mainDispatcher

    private lateinit var handle: MPVHandle

    /** MediaMP's native player id (log routing); null when it could not be read. */
    private var nativeId: Long? = null

    private val observer = object : EventListener {
        override fun onPropertyChange(name: String) {
            when (name) {
                "track-list" -> refreshTracks()
                "sub-text" -> _state.update { it.copy(subText = null) }
            }
        }

        override fun onPropertyChange(name: String, value: Boolean) {
            when (name) {
                "pause" -> _state.update { it.copy(playing = !value && fileLoaded && !it.ended) }
                "paused-for-cache" -> _state.update { it.copy(buffering = value || !it.started) }
                "eof-reached" -> if (value && fileLoaded) _state.update { it.copy(ended = true, playing = false, buffering = false) }
                "core-idle" -> _state.update { s ->
                    val paused = handle.getPropertyBooleanOrNull("pause") == true
                    s.copy(playing = !value && !paused && fileLoaded && !s.ended)
                }
            }
        }

        override fun onPropertyChange(name: String, value: Long) {
            when (name) {
                "height" -> _state.update { it.copy(videoHeight = value.toInt()) }
                "width" -> _state.update { it.copy(videoWidth = value.toInt()) }
            }
        }

        override fun onPropertyChange(name: String, value: Double) {
            when (name) {
                "time-pos" -> _state.update { it.copy(positionSecs = value.coerceAtLeast(0.0)) }
                "duration" -> _state.update { it.copy(durationSecs = value.coerceAtLeast(0.0)) }
                "speed" -> _state.update { it.copy(speed = value) }
                "demuxer-cache-time" -> _state.update { it.copy(cacheSecs = value.coerceAtLeast(0.0)) }
            }
        }

        override fun onPropertyChange(name: String, value: String) {
            when (name) {
                "track-list" -> refreshTracks()
                "sub-text" -> _state.update { it.copy(subText = value.takeIf { v -> v.isNotBlank() }) }
            }
        }

        override fun onEvent(event: Int) {
            when (event) {
                MPVEvent.FILE_LOADED -> {
                    fileLoaded = true
                    // The previous file's demuxer cache and decoder are freed by now: give that memory back (see NativeHeap).
                    NativeHeap.scheduleTrim()
                    val (subs, off) = synchronized(subtitleLock) { pendingSubtitles to pendingSubtitlesOff }
                    addPending(subs, off)
                    refreshTracks()
                }
                MPVEvent.PLAYBACK_RESTART -> _state.update {
                    it.copy(started = true, buffering = false, playing = handle.getPropertyBooleanOrNull("pause") != true)
                }
            }
        }

        override fun onStartFile(playlistEntryId: Long) {
            if (released) return
            currentEntry = playlistEntryId
            fileLoaded = false
            _state.update { it.copy(buffering = true, ended = false, error = null, started = false) }
        }

        override fun onEndFile(reason: Int, mpvError: Int, playlistEntryId: Long) {
            // keep-open: a normal end does not unload the file, so an END_FILE of this load before FILE_LOADED is a load failure.
            if (released || playlistEntryId != currentEntry || fileLoaded) return
            _state.update { it.copy(error = lastError ?: "loading failed", buffering = false, playing = false) }
        }
    }

    val player: MpvMediampPlayer = MpvMediampPlayer(
        context = Any(),
        parentCoroutineContext = SupervisorJob(),
        mainDispatcher = mainDispatcher,
    ) { h ->
        handle = h
        install(h)
        h.option("input-default-bindings", "no")
        h.option("cache", "yes")
        h.option("demuxer-max-bytes", "${DEMUXER_MAX_MB}MiB")
        h.option("demuxer-max-back-bytes", "${DEMUXER_BACK_MB}MiB")
        h.option("demuxer-readahead-secs", "$READAHEAD_SECS")
        h.option("network-timeout", "$NETWORK_TIMEOUT_S")
        h.option("sub-ass-override", "no")
        // Static Outfit / Space Grotesk faces for the "Outfit" and "Space Grotesk" subtitle fonts (libass draws a variable font at its
        // default instance); installed by the factory before the engine starts, libass reads the folder when a track starts.
        h.option("sub-fonts-dir", config.fontsDir.absolutePath)
        if (config.audioOnly) {
            h.option("vid", "no")
            h.option("audio-display", "no")
        }
        for ((k, v) in config.extraOptions) h.option(k, v)
    }

    init {
        nativeId = readNativeId(handle)
        MpvLogRouter.register(this)
        // Properties MediaMP does not observe itself (it observes time-pos, duration, pause, paused-for-cache, eof-reached,
        // demuxer-cache-time and track-list, which reach [observer] through the fan-out too).
        handle.observeProperty("speed", MPVFormat.MPV_FORMAT_DOUBLE, OBSERVE_ID)
        handle.observeProperty("core-idle", MPVFormat.MPV_FORMAT_FLAG, OBSERVE_ID)
        handle.observeProperty("height", MPVFormat.MPV_FORMAT_INT64, OBSERVE_ID)
        handle.observeProperty("width", MPVFormat.MPV_FORMAT_INT64, OBSERVE_ID)
        handle.observeProperty("sub-text", MPVFormat.MPV_FORMAT_STRING, OBSERVE_ID)
        // v1.1 (F2): the FFmpeg decoders of this libmpv, for the source picker's codec checks (DesktopVideoDecoding.codecCaps).
        runCatching { DesktopVideoDecoding.reportSoftwareCodecs(decoderCodecs()) }
    }

    /** `decoder-list/N/codec` of every decoder libmpv's FFmpeg has (h264, hevc, av1...). */
    private fun decoderCodecs(): Set<String> {
        val n = handle.getPropertyLongOrNull("decoder-list/count")?.toInt() ?: return emptySet()
        return (0 until n).mapNotNullTo(HashSet()) { i -> handle.getPropertyString("decoder-list/$i/codec") }
    }

    /** Runs a MediaMP command on its machine thread: right away when already there, else posted in order. */
    private fun onMachine(block: () -> Unit) {
        if (released) return
        if (!immediate.isDispatchNeeded(EmptyCoroutineContext)) block() else machineScope.launch { if (!released) block() }
    }

    /**
     * Starts a new load (new `loadId`, buffering) before its URL is known, so the session can prepare the stream (a filtered HLS
     * master, BUG-059) while the player already shows the new load. Returns its `loadId`; [loadPrepared] then opens the URL.
     */
    fun beginLoad(): Int {
        if (released) return _state.value.loadId
        fileLoaded = false
        lastError = null
        subtitleDecided = false
        currentEntry = AWAITING_ENTRY
        _state.update {
            MpvState(
                loadId = it.loadId + 1, buffering = true, speed = it.speed, loop = it.loop, subDelayMs = it.subDelayMs,
                surfaceWidth = it.surfaceWidth, surfaceHeight = it.surfaceHeight,
            )
        }
        return _state.value.loadId
    }

    /**
     * Opens [url] for the load [beginLoad] started, with HTTP [headers] (`user-agent`, `referrer`, `http-header-fields`) and the start
     * position applied by MediaMP to the `loadfile` itself. External [subtitles] are added once the file is loaded.
     */
    fun loadPrepared(
        url: String,
        headers: Map<String, String>,
        startSecs: Double,
        subtitles: List<MpvSubtitle>,
        lavfOptions: String = "",
        subtitlesOff: Boolean = false,
    ) {
        if (released) return
        synchronized(subtitleLock) {
            pendingSubtitles = subtitles
            pendingSubtitlesOff = subtitlesOff
        }
        val loadId = _state.value.loadId
        // BUG-080: set on every load, so the widened protocol whitelist of a filtered local master does not leak into the next one.
        handle.setPropertyString("demuxer-lavf-o", lavfOptions)
        val media = mediampHeaders(headers)
        // MediaMP only writes `referrer` when the load has one: clear the previous load's.
        if ("Referer" !in media) handle.setPropertyString("referrer", "")
        val startMs = (startSecs.coerceAtLeast(0.0) * 1000).toLong()
        onMachine {
            machineScope.launch {
                if (!config.audioOnly) awaitRenderContext()
                if (released || _state.value.loadId != loadId) return@launch // a newer load replaced this one meanwhile
                try {
                    player.setMediaData(UriMediaData(url, media), playWhenReady = true, startPositionMillis = startMs)
                } catch (e: CancellationException) {
                    throw e // a newer load or a stop superseded this one (MediaLoadCancellationException)
                } catch (e: PlaybackException) {
                    // Normally already reported by END_FILE (observer); this covers a refusal before mpv started the file.
                    if (!released && _state.value.loadId == loadId && _state.value.error == null) {
                        _state.update { it.copy(error = lastError ?: e.message ?: "loading failed", buffering = false, playing = false) }
                    }
                }
            }
        }
    }

    /**
     * Android loads a file only once its surface exists (vo=gpu without one fails for good). MediaMP instead opens a file that comes
     * before the render context with `vo=null` and restores the video output when the surface draws, by deselecting and reselecting
     * the video track; on a file without audio that leaves mpv with nothing to play for a moment, it reaches EOF and stays paused at
     * the start (seen in the smoke run). So a load waits here for the render context: up to [SURFACE_APPEAR_MS] for a
     * [MpvVideoSurface] to appear (the player screen composes it right after creating the session) and then up to
     * [RENDER_CONTEXT_WAIT_MS] for its first draw. Without a surface (no window: tests, audio only) the file opens anyway.
     */
    private suspend fun awaitRenderContext() {
        var waited = 0L
        while (renderContextReady() == false) {
            if (waited >= RENDER_CONTEXT_WAIT_MS || (!surfaceAttached && waited >= SURFACE_APPEAR_MS)) {
                // PENDIENTE-ESCRITORIO: sin superficie MediaMP abre con vo=null; un archivo sin audio puede quedar en EOF al restaurar el video (ver awaitRenderContext)
                return
            }
            delay(POLL_MS)
            waited += POLL_MS
        }
    }

    /** A [MpvVideoSurface] of this engine is in the composition (set on the main thread). */
    @Volatile
    internal var surfaceAttached = false

    /**
     * Whether MediaMP's render context exists for a load (its own check before `loadfile`), or null when that cannot be asked (no
     * surface backend on this platform, or MediaMP's internals changed: then nothing waits).
     */
    private fun renderContextReady(): Boolean? = runCatching {
        val lifecycle = player.javaClass.getMethod("getRenderContextLifecycle\$mediamp_mpv").invoke(player) ?: return@runCatching null
        lifecycle.javaClass.getMethod("ensureReadyForLoad").apply { isAccessible = true }.invoke(lifecycle) as Boolean
    }.getOrNull()

    /**
     * BUG-059: give up on the current load when it produced no frame yet: stop mpv and report [reason] as the load's error. No-op
     * once playback started.
     */
    fun failStart(reason: String) {
        if (released || _state.value.started) return
        lastError = reason
        onMachine { player.stopPlayback() }
        _state.update { it.copy(error = reason, buffering = false, playing = false) }
    }

    fun play() {
        if (released) return
        // As on Android: at the end, play replays from the start (MediaMP would too; this also clears [MpvState.ended]).
        if (_state.value.ended) seekTo(0.0)
        onMachine { player.play() }
    }

    fun pause() = onMachine { player.pause() }

    fun seekTo(secs: Double) {
        if (released) return
        _state.update { it.copy(ended = false) }
        val ms = (secs.coerceAtLeast(0.0) * 1000).toLong()
        onMachine { player.seekTo(ms) }
    }

    fun setSpeed(speed: Double) = onMachine {
        player.features[PlaybackSpeed.Key]?.set(speed.toFloat()) ?: handle.setPropertyDouble("speed", speed)
    }

    /** mpv `sid`; null = subtitles off. */
    fun selectSubtitle(id: Int?) {
        if (released) return
        subtitleDecided = true
        handle.setPropertyString("sid", id?.toString() ?: "no")
        refreshTracks()
    }

    /** mpv `aid` (`AUDIO_TRACK_OFF` disables audio with `aid=no`). */
    fun selectAudio(id: Int) {
        if (released) return
        handle.setPropertyString("aid", if (id == app.nijiyome.player.AUDIO_TRACK_OFF) "no" else id.toString())
        refreshTracks()
    }

    /** `slang` / `alang` with every variant of the language; the first track list of a load picks a matching subtitle ([refreshTracks]). */
    fun setPreferredLanguages(subtitle: String?, audio: String?) {
        if (released) return
        preferredSub = LanguageCodes.normalize(subtitle)
        LanguageCodes.engineVariants(subtitle).takeIf { it.isNotEmpty() }?.let { handle.setPropertyString("slang", it.joinToString(",")) }
        LanguageCodes.engineVariants(audio).takeIf { it.isNotEmpty() }?.let { handle.setPropertyString("alang", it.joinToString(",")) }
    }

    /** `sub-delay` (seconds), ±60 s. */
    fun setSubtitleDelay(ms: Long) {
        if (released) return
        val clamped = app.nijiyome.player.SubtitleDelay.clamp(ms)
        handle.setPropertyDouble("sub-delay", clamped / 1000.0)
        _state.update { it.copy(subDelayMs = clamped) }
    }

    /** `sub-add` of [subs] to the file playing now; [off] = none selected. Blocks while mpv reads them: call it off the main thread. */
    fun addSubtitles(subs: List<MpvSubtitle>, off: Boolean) {
        if (released) return
        val now = synchronized(subtitleLock) {
            if (fileLoaded) return@synchronized true
            pendingSubtitles = pendingSubtitles + subs
            pendingSubtitlesOff = pendingSubtitlesOff || off
            false
        }
        if (!now) return
        addPending(subs, off)
        refreshTracks()
    }

    private fun addPending(subs: List<MpvSubtitle>, off: Boolean) {
        if (subs.isEmpty() || released) return
        if (off || subs.any { it.select }) subtitleDecided = true
        for (s in subs) runCatching { handle.command(*subAddCommand(s)) }
        if (off) handle.setPropertyString("sid", "no")
    }

    /** `sub-add <path> select <title>`: a loaded SRT / ASS / VTT becomes the active track. */
    fun addSubtitleFile(path: String, title: String) {
        if (released) return
        subtitleDecided = true
        handle.command("sub-add", path, "select", title)
        refreshTracks()
    }

    /** `secondary-sid`; null = off. */
    fun selectSecondarySubtitle(id: Int?) {
        if (released) return
        handle.setPropertyString("secondary-sid", id?.toString() ?: "no")
        _state.update { it.copy(secondarySid = id) }
    }

    fun setSubtitlesVisible(visible: Boolean) {
        if (!released) handle.setPropertyBoolean("sub-visibility", visible)
    }

    fun setLoop(loop: Boolean) {
        if (released) return
        handle.setPropertyString("loop-file", if (loop) "inf" else "no")
        _state.update { it.copy(loop = loop, ended = if (loop) false else it.ended) }
    }

    /** Stable volume: ffmpeg `dynaudnorm` through mpv's `af` (same filter as Android). */
    fun setStableVolume(enabled: Boolean) {
        if (!released) handle.setPropertyString("af", if (enabled) MpvEngineDefaults.STABLE_VOLUME_FILTER else "")
    }

    /**
     * The current video frame (no subtitles or OSD) as PNG at [path]; true when the file exists afterwards. MediaMP's FFmpeg has no PNG
     * encoder, so mpv's own `screenshot-to-file` fails: MediaMP renders the frame once more at the video's size through its render
     * backend and writes the PNG itself (falling back to mpv's command without a surface). Blocks the caller up to
     * [SCREENSHOT_TIMEOUT_MS] while that frame is rendered off this thread.
     */
    fun screenshotToFile(path: String): Boolean {
        if (released) return false
        val f = File(path)
        f.delete()
        f.parentFile?.mkdirs()
        val shots = player.features[Screenshots.Key] ?: return false
        runCatching { runBlocking(Dispatchers.IO) { withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) { shots.takeScreenshot(path) } } }
        return f.isFile && f.length() > 0
    }

    /** Raw property access for MpvQualityController and the session. */
    internal fun setProperty(name: String, value: String) {
        if (!released) handle.setPropertyString(name, value)
    }

    internal fun getProperty(name: String): String? = if (released) null else handle.getPropertyString(name)

    /** Frames mpv dropped so far in this file (`frame-drop-count` + `decoder-frame-drop-count`); null when unknown. */
    fun droppedFrames(): Long? {
        if (released) return null
        val vo = handle.getPropertyLongOrNull("frame-drop-count")
        val dec = handle.getPropertyLongOrNull("decoder-frame-drop-count")
        if (vo == null && dec == null) return null
        return (vo ?: 0) + (dec ?: 0)
    }

    /** The video's frame rate (`container-fps`, else `estimated-vf-fps`); null when unknown. */
    fun videoFps(): Double? {
        if (released) return null
        return (handle.getPropertyDoubleOrNull("container-fps") ?: handle.getPropertyDoubleOrNull("estimated-vf-fps"))?.takeIf { it > 0 }
    }

    fun currentPositionSecs(): Double = if (released) 0.0 else handle.getPropertyDoubleOrNull("time-pos") ?: _state.value.positionSecs

    /** Size in pixels of the composable the video is drawn into (0 x 0 = none); MediaMP sizes mpv's render target to it. */
    internal fun onViewport(width: Int, height: Int) {
        if (released) return
        _state.update { it.copy(surfaceWidth = width.coerceAtLeast(0), surfaceHeight = height.coerceAtLeast(0)) }
    }

    /**
     * v1.1 (F2): the ~4 to 9 MB per file opened of spike reserve 1 is glibc retention, not a leak (measured with libmpv alone and with
     * the app, see [NativeHeap]); a trim follows every load and every release.
     */
    fun release() {
        if (released) return
        released = true
        logListener = null
        MpvLogRouter.unregister(this)
        machineScope.cancel()
        // MediaMP stops mpv and destroys it on its own daemon thread (mpv destruction joins native threads).
        player.close()
        NativeHeap.scheduleTrim()
    }

    internal fun ownsLog(instance: Long): Boolean = nativeId == null || nativeId == instance

    internal fun onLog(level: Int, prefix: String, text: String) {
        if (released) return
        if (level <= MPV_LOG_ERROR) lastError = "$prefix: ${text.trim()}"
        logListener?.invoke(level, prefix, text)
    }

    private fun refreshTracks() {
        if (released || !::handle.isInitialized) return
        val count = handle.getPropertyLongOrNull("track-list/count")?.toInt() ?: return
        val subs = mutableListOf<MpvTrack>()
        val audio = mutableListOf<MpvTrack>()
        for (i in 0 until count) {
            val type = handle.getPropertyString("track-list/$i/type") ?: continue
            val id = handle.getPropertyLongOrNull("track-list/$i/id")?.toInt() ?: continue
            val track = MpvTrack(
                id = id,
                title = handle.getPropertyString("track-list/$i/title"),
                lang = handle.getPropertyString("track-list/$i/lang"),
                codec = handle.getPropertyString("track-list/$i/codec"),
                selected = handle.getPropertyBooleanOrNull("track-list/$i/selected") == true,
                external = handle.getPropertyBooleanOrNull("track-list/$i/external") == true,
                channels = if (type == "audio") handle.getPropertyLongOrNull("track-list/$i/demux-channel-count")?.toInt()?.takeIf { it > 0 } else null,
            )
            when (type) {
                "sub" -> subs += track
                "audio" -> audio += track
            }
        }
        _state.update { it.copy(subtitles = subs, audio = audio) }
        // First track list of a load with subtitles and none selected: pick the preferred language (bug player.subs.preferred-lang).
        if (fileLoaded && !subtitleDecided && subs.isNotEmpty()) {
            subtitleDecided = true
            if (subs.none { it.selected }) {
                LanguageCodes.firstMatch(subs, preferredSub, { it.lang }, { it.title })?.let { i -> selectSubtitle(subs[i].id) }
            }
        }
    }

    /** Puts [observer] next to MediaMP's own listener (see the file header). Runs inside MediaMP's constructor, before initialize. */
    private fun install(h: MPVHandle) {
        val field = MPVHandle::class.java.getDeclaredField("eventListener").apply { isAccessible = true }
        val mediamp = field.get(h) as? EventListener ?: error("MediaMP did not set its mpv event listener before the options hook")
        h.setEventListener(FanOutListener(mediamp, observer))
    }

    companion object {
        const val DEMUXER_MAX_MB = MpvEngineDefaults.DESKTOP_DEMUXER_MAX_MB
        const val DEMUXER_BACK_MB = MpvEngineDefaults.DESKTOP_DEMUXER_BACK_MB
        const val READAHEAD_SECS = MpvEngineDefaults.DESKTOP_READAHEAD_SECS
        const val NETWORK_TIMEOUT_S = MpvEngineDefaults.NETWORK_TIMEOUT_S
        private const val AWAITING_ENTRY = Long.MIN_VALUE

        /** reply id of this engine's own property observations (MediaMP uses 0). */
        private const val OBSERVE_ID = 0x4E4A_4D50L

        private const val MPV_LOG_ERROR = 20
        private const val SCREENSHOT_TIMEOUT_MS = 5_000L
        private const val POLL_MS = 50L
        private const val SURFACE_APPEAR_MS = 400L
        private const val RENDER_CONTEXT_WAIT_MS = 3_000L

        private fun readNativeId(h: MPVHandle): Long? =
            runCatching { MPVHandle::class.java.getMethod("getPtr\$mediamp_mpv").invoke(h) as Long }.getOrNull()
    }
}

/**
 * The headers of a load as MediaMP passes them to mpv: it takes `User-Agent` and `Referer` by these exact (case-sensitive) keys and
 * joins the rest into `http-header-fields` with `,` and no escaping, so commas inside values are escaped mpv-style here. `User-Agent`
 * is always present (Android's default when the source has none), so a previous load's agent never sticks.
 */
internal fun mediampHeaders(source: Map<String, String>): LinkedHashMap<String, String> {
    // Hito 4 (review): no CR / LF / NUL from an extension reaches mpv's header list.
    val headers = MpvInputGuard.headers(source)
    val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
    val referer = headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
    val out = LinkedHashMap<String, String>()
    out["User-Agent"] = ua ?: MpvEngineDefaults.DEFAULT_USER_AGENT
    if (referer != null) out["Referer"] = referer
    for ((k, v) in headers) {
        if (k.equals("User-Agent", ignoreCase = true) || k.equals("Referer", ignoreCase = true)) continue
        out[k] = v.replace(",", "\\,")
    }
    return out
}

/** `sub-add <url> select|auto [<title> [<lang>]]` (same arguments as Android's MpvEngine.addPending). */
internal fun subAddCommand(s: MpvSubtitle): Array<String> {
    val cmd = mutableListOf("sub-add", s.url, if (s.select) "select" else "auto")
    if (s.title != null || s.lang != null) cmd += s.title ?: ""
    if (s.lang != null) cmd += s.lang
    return cmd.toTypedArray()
}

/** MediaMP's listener first (its state machine), then this engine's; a failure of ours never reaches MediaMP or the JNI caller. */
private class FanOutListener(private val first: EventListener, private val second: EventListener) : EventListener {
    private inline fun ours(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            System.err.println("W/mpv: engine listener failed: $t")
        }
    }

    override fun onPropertyChange(name: String) {
        first.onPropertyChange(name)
        ours { second.onPropertyChange(name) }
    }

    override fun onPropertyChange(name: String, value: Boolean) {
        first.onPropertyChange(name, value)
        ours { second.onPropertyChange(name, value) }
    }

    override fun onPropertyChange(name: String, value: Long) {
        first.onPropertyChange(name, value)
        ours { second.onPropertyChange(name, value) }
    }

    override fun onPropertyChange(name: String, value: Double) {
        first.onPropertyChange(name, value)
        ours { second.onPropertyChange(name, value) }
    }

    override fun onPropertyChange(name: String, value: String) {
        first.onPropertyChange(name, value)
        ours { second.onPropertyChange(name, value) }
    }

    override fun onEvent(event: Int) {
        first.onEvent(event)
        ours { second.onEvent(event) }
    }

    override fun onStartFile(playlistEntryId: Long) {
        first.onStartFile(playlistEntryId)
        ours { second.onStartFile(playlistEntryId) }
    }

    override fun onEndFile(reason: Int, mpvError: Int, playlistEntryId: Long) {
        first.onEndFile(reason, mpvError, playlistEntryId)
        ours { second.onEndFile(reason, mpvError, playlistEntryId) }
    }
}
