// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.model.AspectMode
import app.nijiyome.model.DebandConfig
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.ImageAdjust
import app.nijiyome.model.QualityTier
import app.nijiyome.model.SharpenLevel
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.player.LanguageCodes
import app.nijiyome.player.MPV_START_TIMEOUT_MS
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerVolume
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.QualityUiState
import app.nijiyome.player.SessionTicker
import app.nijiyome.player.mpv.HlsPrefetch
import app.nijiyome.player.mpv.MpvEngineDefaults
import app.nijiyome.player.mpv.MpvInputGuard
import app.nijiyome.player.mpv.MpvState
import app.nijiyome.player.mpv.MpvSubtitle
import app.nijiyome.player.mpv.MpvSubtitleStyleApplier
import app.nijiyome.player.mpv.START_TIMEOUT
import app.nijiyome.player.mpv.desktopHwdec
import app.nijiyome.player.mpv.desktopSubtitleFamily
import app.nijiyome.player.mpv.mpvFontFamily
import app.nijiyome.player.mpv.mpvPlaybackState
import app.nijiyome.player.mpv.mpvSubtitleProperties
import app.nijiyome.player.mpv.mpvSubtitles
import app.nijiyome.player.mpv.quality.FrameDropMonitor
import app.nijiyome.player.mpv.quality.MpvQualityController
import app.nijiyome.player.mpv.quality.QualityMpvPort
import app.nijiyome.player.mpv.quality.ShaderResolver
import app.nijiyome.player.mpv.quality.mpvLogInfo
import app.nijiyome.player.mpv.subAddCommand
import app.nijiyome.player.mpv.windowsShaderList
import app.nijiyome.player.mpvStartWatched
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** How the running external mpv is set up, for the player screen's card. */
data class ExternalMpvMode(
    val config: Kind,
    /** lsfg-vk's multiplier when frame generation runs, else null. */
    val frameGenMultiplier: Int? = null,
    /** The multiplier asked for (0 = automatic) when frame generation runs. */
    val frameGenRequested: Int? = null,
    /** The refresh rate the multiplier was fitted to, when known. */
    val refreshHz: Double? = null,
    /** Frame generation pulled a libmpv user into this window (the card offers watching without it). */
    val forcedByFrameGen: Boolean = false,
    /** The enhanced mode was asked for but could not start (English diagnostic); the user's config runs instead. */
    val enhancedFallback: String? = null,
) {
    enum class Kind { USER_CONFIG, ANIME_BUILD, APP_CONFIG }
}

/** Frame generation for one session: lsfg-vk's config goes to [configDir]; [multiplier] is already fitted to the display. */
data class ExternalFrameGen(
    val multiplier: Int,
    val requested: Int,
    val refreshHz: Double?,
    val flowScale: Float,
    val performance: Boolean,
    val dll: File?,
    val configDir: File,
)

/** The enhanced mode for one session: where the build's zip and its extraction live, and whether uosc draws the interface. */
data class ExternalEnhanced(val dataDir: File, val cacheDir: File, val uosc: Boolean)

/** What a session starts its mpv with (see [ExternalMpvSessionFactory]). */
data class ExternalMpvSetup(
    val mpv: File,
    /** The user chose the external engine (their own config, or the enhanced mode). False = frame generation pulled them here. */
    val userChoseExternal: Boolean,
    val enhanced: ExternalEnhanced? = null,
    val frameGen: ExternalFrameGen? = null,
    val title: String = "",
    /** The bundled subtitle fonts (`sub-fonts-dir`) for the app's subtitle style. */
    val fontsDir: File? = null,
    /** Where the filtered HLS master is written (HlsPrefetch). */
    val cacheDir: File,
    /** Extra options of every process (tests: `--ao=null`). */
    val extra: List<String> = emptyList(),
    /** Environment changes of every process (null = remove; tests: an empty `MPV_HOME`, gamescope's display). */
    val extraEnv: Map<String, String?> = emptyMap(),
    /** Where the IPC sockets go (a private folder, [PrivateRuntimeDir]). */
    val ipcDir: File? = null,
)

/**
 * Hito 4, paso D: the EXTERNAL engine on desktop. mpv runs in its own window, the way Tatakai Next's external mode did
 * (tk-player/src/external.rs): one mpv process per load, started with the stream, its headers and the start position on the command
 * line, then driven over its JSON IPC. The session follows the same properties as the embedded engine and maps them with the same
 * [mpvPlaybackState], so the player's flow (failover, progress, end of episode, next episode) does not know the difference.
 *
 * Window and process: closing the session (Stop / Back / the app quits) asks mpv to quit and kills it after a grace period; the user
 * closing the mpv window ends the load ([windowClosed]). [play] / [seekTo] after that start mpv again at that position (Replay).
 *
 * Configuration ([ExternalMpvMode]): the user's own mpv config (never `--no-config`), mpv-anime-build (the enhanced mode), or the app's
 * own (frame generation pulled a libmpv user here: the app's quality presets run through the shared [MpvQualityController]).
 */
class ExternalMpvPlayerSession internal constructor(
    private val scope: CoroutineScope,
    private val listener: PlaybackListener,
    remote: Boolean,
    private val setup: ExternalMpvSetup,
    initialQuality: QualityRequest,
    shaders: ShaderResolver?,
    initialVolume: PlayerVolume?,
    private val onVolume: (PlayerVolume) -> Unit = {},
    private val refreshHz: () -> Float? = { null },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val env: Map<String, String> = System.getenv(),
    /** 1.1: the loopback media proxy (Settings › Red); null = streams open directly. */
    private val mediaRoute: app.nijiyome.model.LocalMediaRoute? = null,
) : PlayerSession {
    private val snap = MutableStateFlow(ExternalMpvSnapshot(mpv = MpvState(buffering = false)))
    private val _state = MutableStateFlow(PlaybackState(isBuffering = false, fullAss = true))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _windowClosed = MutableStateFlow(false)

    /** The user closed the mpv window (or mpv quit by itself) during this load without an error: the card goes back. */
    val windowClosed: StateFlow<Boolean> = _windowClosed.asStateFlow()

    private val _mode = MutableStateFlow(initialMode())

    /** How mpv is configured (known for sure once the first process started). */
    val mode: StateFlow<ExternalMpvMode> = _mode.asStateFlow()

    @Volatile
    private var released = false
    private var loaded = false

    @Volatile
    private var endedHere = false
    private var wasPlaying = false

    /** The load the VM issued last (= [MpvState.loadId]); a restart of the same load keeps it. */
    @Volatile
    private var loadSeq = 0

    /** Bumped by every process start; messages of an older process are ignored. */
    @Volatile
    private var spawnSeq = 0

    /** Guards the hand-over of a started mpv from the IO thread to the main thread ([starting], [spawnSeq], [released]). */
    private val spawnLock = Any()

    /** An mpv that started (IO thread) and is not yet the session's [proc]. */
    private var starting: ExternalMpvProcess? = null

    /** A new spawn number: whatever was starting for the previous one is stopped. */
    private fun nextSpawn(): Int = synchronized(spawnLock) {
        starting?.stop()
        starting = null
        ++spawnSeq
    }

    @Volatile
    private var proc: ExternalMpvProcess? = null

    /** Tests: the running mpv process (to make it quit as if the user closed its window). */
    internal val processForTest: ExternalMpvProcess? get() = proc
    private var currentSource: PlayableSource? = null
    private var playedUrl: String? = null
    private var resolvedConfig: ExternalMpvConfig? = null

    @Volatile
    private var lsfgConfig: File? = null

    /** Options applied to every process at start (`--name=value`) and live over IPC; the session's settings survive a restart. */
    private val desired = LinkedHashMap<String, String>()

    @Volatile
    private var pendingSubs: List<MpvSubtitle> = emptyList()

    @Volatile
    private var pendingSubsOff = false

    @Volatile
    private var watchedLoadId = -1
    private var prepareJob: Job? = null
    private var startWatch: Job? = null
    private var monitorJob: Job? = null
    private val mirror: Job

    private val styleApplier = MpvSubtitleStyleApplier { name, value -> setOption(name, value) }

    private val ticker = SessionTicker(
        scope = scope,
        listener = listener,
        remote = remote,
        now = { System.nanoTime() / 1_000_000 },
        snapshot = { val s = _state.value; Triple(currentPositionMs(), s.durationMs, s.isPlaying) },
    )

    /** The app's quality engine, only when the app owns mpv's config (the user's own config and mpv-anime-build bring their own). */
    private val qualityController: MpvQualityController? =
        if (!setup.userChoseExternal && shaders != null) {
            MpvQualityController(port = ExternalQualityPort(), shaders = shaders, initial = initialQuality, fp16 = true)
        } else {
            null
        }

    override val quality: StateFlow<QualityUiState> get() = qualityController?.state ?: super.quality

    init {
        initialVolume?.let {
            desired["volume"] = it.percent.toString()
            desired["mute"] = if (it.muted) "yes" else "no"
        }
        qualityController?.let { q ->
            q.onDisplayRefreshRate(refreshHz())
            // lsfg-vk paces the frames: mpv's own smoothing stays off while it runs.
            if (setup.frameGen != null) q.setFrameGenActive(true)
            startMonitor(q)
        }
        mirror = scope.launch { snap.collect { s -> publish(s) } }
        ticker.start()
    }

    /** [s] as the player's [PlaybackState] (main thread), with the forced progress tick of a pause. */
    private fun publish(s: ExternalMpvSnapshot) {
        qualityController?.onVideoSize(s.mpv.videoWidth, s.mpv.videoHeight)
        _state.value = mpvPlaybackState(_state.value, s.mpv, loaded = loaded, endedHere = endedHere, watched = s.mpv.loadId == watchedLoadId)
        if (wasPlaying && !s.mpv.playing && loaded && !released) listener.onProgress(currentPositionMs(), _state.value.durationMs, true)
        wasPlaying = s.mpv.playing
    }

    private fun initialMode(): ExternalMpvMode {
        val fg = setup.frameGen
        val kind = when {
            !setup.userChoseExternal -> ExternalMpvMode.Kind.APP_CONFIG
            setup.enhanced != null -> ExternalMpvMode.Kind.ANIME_BUILD
            else -> ExternalMpvMode.Kind.USER_CONFIG
        }
        return ExternalMpvMode(
            config = kind,
            frameGenMultiplier = fg?.multiplier,
            frameGenRequested = fg?.requested,
            refreshHz = fg?.refreshHz,
            forcedByFrameGen = !setup.userChoseExternal,
        )
    }

    private fun startMonitor(q: MpvQualityController) {
        monitorJob = scope.launch {
            while (isActive && !released) {
                delay(FrameDropMonitor.SAMPLE_MS)
                if (released) break
                q.refreshTelemetry()
                val st = _state.value
                if (!st.isPlaying || st.isBuffering) {
                    q.onPlaybackIdle()
                    continue
                }
                val p = snap.value.props
                val drops = (p["frame-drop-count"]?.toLongOrNull() ?: continue) + (p["decoder-frame-drop-count"]?.toLongOrNull() ?: 0)
                val fps = (p["container-fps"] ?: p["estimated-vf-fps"])?.toDoubleOrNull()?.takeIf { it > 0 } ?: continue
                q.onDropSample(drops, fps, System.nanoTime() / 1_000_000)
            }
        }
    }

    // ---- loading ----------------------------------------------------------------------------------------------------------------

    override fun load(source0: PlayableSource, startMs: Long) {
        if (released) return
        // 1.1 (link-servidor-7): a stream with credentials / Origin, or any while an outbound proxy is set, through the loopback proxy
        val routed = runCatching { app.nijiyome.player.mpv.routedSource(source0, mediaRoute) }
        val source = routed.getOrNull() ?: source0
        routed.exceptionOrNull()?.let { mpvLogInfo("${it.message}: not playing the stream outside the outbound proxy") }
        loaded = true
        endedHere = false
        qualityController?.onFileStarted()
        val loadId = ++loadSeq
        currentSource = source
        synchronized(desired) { TRACK_OPTIONS.forEach { desired.remove(it) } }
        pendingSubs = safeSubtitles(mpvSubtitles(source.subtitles, source.selectSubtitle), source.localFile)
        pendingSubsOff = source.selectSubtitle == PlayableSource.SUBTITLES_OFF
        playedUrl = null
        val prev = snap.value.mpv
        val fresh = MpvState(loadId = loadId, buffering = true, speed = prev.speed, loop = prev.loop, subDelayMs = prev.subDelayMs)
        if (routed.isFailure || MpvInputGuard.mediaUrl(source.url, allowLocal = source.localFile) == null) {
            // An extension's URL with another protocol (edl://, ytdl://, file://...) or control characters: this source fails as any
            // broken one, and the player moves on.
            mpvLogInfo("refused source URL (not http / https${if (source.localFile) " or a local file" else ""})")
            prepareJob?.cancel()
            startWatch?.cancel()
            nextSpawn()
            proc?.stop()
            proc = null
            _windowClosed.value = false
            snap.value = ExternalMpvSnapshot(mpv = fresh.copy(buffering = false, error = REFUSED_URL))
            return
        }
        restart(loadId, source, startMs.coerceAtLeast(0) / 1000.0, fresh = fresh)
    }

    /** Closes the running mpv (if any) and starts a new one for [loadId] at [startSecs]. */
    private fun restart(loadId: Int, source: PlayableSource, startSecs: Double, fresh: MpvState) {
        prepareJob?.cancel()
        startWatch?.cancel()
        val spawn = nextSpawn()
        proc?.stop()
        proc = null
        _windowClosed.value = false
        snap.value = ExternalMpvSnapshot(mpv = fresh)
        prepareJob = scope.launch {
            val url = playedUrl ?: if (HlsPrefetch.applies(source)) {
                // BUG-059: FFmpeg waits for every rendition of an HLS master; mpv gets one with only the variant and languages it plays.
                withContext(io) { HlsPrefetch.filteredMaster(setup.cacheDir, source.url, audioLang, subLang) } ?: source.url
            } else {
                source.url
            }
            if (released || spawn != spawnSeq) return@launch
            playedUrl = url
            val lavf = HlsPrefetch.lavfOptions(url, source.url)
            val watched = mpvStartWatched(source.container, source.torrent)
            watchedLoadId = if (watched) loadId else -1
            // The process is handed over under [spawnLock] (spawnProcess): if this load is cancelled meanwhile (Back, Stop, another
            // source, "Ver sin generación de cuadros"), release() / restart() stop it, so no mpv window, socket or thread is left.
            val started = withContext(io + NonCancellable) { runCatching { spawnProcess(spawn, source, url, lavf, startSecs) } }
            val p = started.getOrNull()
            val mine = p != null && synchronized(spawnLock) {
                (starting === p && !released && spawn == spawnSeq).also { if (starting === p) starting = null }
            }
            if (p != null && !mine) {
                p.stop()
                return@launch
            }
            if (released || spawn != spawnSeq || !isActive) {
                p?.stop()
                return@launch
            }
            if (p == null) {
                val why = started.exceptionOrNull()?.message ?: "unknown error"
                mpvLogInfo("external mpv could not start: $why")
                snap.update { it.copy(mpv = it.mpv.copy(error = "mpv could not start: $why", buffering = false, playing = false)) }
                return@launch
            }
            proc = p
            p.listen(
                onMessage = { msg -> if (spawn == spawnSeq && !released) onMessage(p, msg) },
                onClosed = { code -> scope.launch { if (spawn == spawnSeq && !released) onProcessClosed(code) } },
            )
            // mpv opened paused (ExternalMpvLaunch.startPaused) so nothing plays before the session follows it; now it may.
            p.send(listOf("set", "pause", synchronized(desired) { desired["pause"] } ?: "no"))
            // BUG-059: a network stream that shows no frame in time is given up (MPV_START_TIMEOUT_CODE), as in the embedded engine.
            startWatch = if (watched) {
                scope.launch {
                    delay(MPV_START_TIMEOUT_MS)
                    val m = snap.value.mpv
                    if (!released && spawn == spawnSeq && !m.started && m.error == null) {
                        mpvLogInfo("no frame after ${MPV_START_TIMEOUT_MS / 1000} s: giving up on this load")
                        snap.update { it.copy(mpv = it.mpv.copy(error = START_TIMEOUT, buffering = false, playing = false)) }
                        proc?.stop()
                    }
                }
            } else {
                null
            }
        }
    }

    /** Resolves the configuration once (mpv-anime-build may need extracting), then starts mpv. Blocking, off the main thread. */
    private fun spawnProcess(spawn: Int, source: PlayableSource, url: String, lavf: String, startSecs: Double): ExternalMpvProcess {
        val config = resolvedConfig ?: resolveConfig().also { resolvedConfig = it }
        val frameGen = setup.frameGen?.let { fg ->
            LsfgVk.writeConfig(fg.configDir, fg.multiplier, fg.flowScale, fg.performance, fg.dll)
        }
        // lsfg-vk reads its config when mpv starts: the previous process's one is no longer needed.
        lsfgConfig?.delete()
        lsfgConfig = frameGen?.configFile
        val options = synchronized(desired) { LinkedHashMap(desired) }
        val launch = ExternalMpvLaunch(
            endpoint = newIpcEndpoint(dir = setup.ipcDir?.also { it.mkdirs() }),
            url = url,
            headers = MpvInputGuard.headers(source.headers).also {
                if (it.size != source.headers.size) mpvLogInfo("dropped ${source.headers.size - it.size} unsafe header(s)")
            },
            startSecs = startSecs,
            title = setup.title,
            config = config,
            frameGen = frameGen,
            fullscreen = !setup.userChoseExternal,
            lavfOptions = lavf,
            loopback = source.torrent,
            options = options,
            extra = setup.extra,
        )
        val args = externalMpvArgs(launch)
        mpvLogInfo("external mpv (${_mode.value.config}${frameGen?.let { ", lsfg-vk x${it.multiplier}" }.orEmpty()}) spawn $spawn")
        val p = ExternalMpvProcess.start(setup.mpv, args, externalMpvEnv(launch, env) + setup.extraEnv, launch.endpoint)
        // Hand-over: a load that was closed or replaced while mpv started gets it stopped here; otherwise it waits in [starting] for
        // the main thread, where release() / restart() can still stop it.
        synchronized(spawnLock) {
            if (released || spawn != spawnSeq) {
                p.stop()
                throw java.io.IOException("load closed while mpv started")
            }
            starting?.stop()
            starting = p
        }
        return p
    }

    private fun resolveConfig(): ExternalMpvConfig {
        if (!setup.userChoseExternal) return ExternalMpvConfig.App
        val e = setup.enhanced ?: return ExternalMpvConfig.User
        return runCatching {
            val dir = AnimeBuild.configDir(e.dataDir)
            if (!AnimeBuild.isCurrent(dir)) {
                val zip = AnimeBuild.zipFile(e.cacheDir)
                if (!zip.isFile) error("mpv-anime-build is not downloaded")
                AnimeBuild.prepare(zip, dir)
            }
            AnimeBuild.config(dir, e.uosc)
        }.getOrElse { t ->
            mpvLogInfo("mpv-anime-build unavailable (${t.message}); the external mpv runs with the user's config")
            _mode.update { it.copy(config = ExternalMpvMode.Kind.USER_CONFIG, enhancedFallback = t.message ?: "unknown error") }
            ExternalMpvConfig.User
        }
    }

    /** One IPC line of the current process (its reader thread). */
    private fun onMessage(p: ExternalMpvProcess, msg: IpcMessage) {
        var loadedNow = false
        snap.update { before -> reduce(before, msg).also { after -> loadedNow = !before.fileLoaded && after.fileLoaded } }
        // The file is loaded (the event, or its first position / duration when the event came before the session connected).
        if (loadedNow) {
            val subs = pendingSubs
            subs.forEach { p.send(subAddCommand(it).toList()) }
            if (pendingSubsOff) p.send(listOf("set", "sid", "no"))
        }
    }

    /** The current process is gone. */
    private fun onProcessClosed(exitCode: Int?) {
        val s = snap.value
        // mpv exits with 2 when the file could not be played. If it failed before the session connected, its `end-file` error never
        // arrived: that is still an error of this load (the player moves to the next source), not the user closing the window.
        val failed = s.mpv.error == null && !s.mpv.started && !s.mpv.ended && exitCode != null && exitCode != 0
        val error = s.mpv.error ?: if (failed) "mpv could not play the file (exit $exitCode)" else null
        val closed = s.copy(closed = true, mpv = s.mpv.copy(playing = false, buffering = false, error = error))
        snap.value = closed
        proc = null
        startWatch?.cancel()
        mpvLogInfo("external mpv exited (exit $exitCode)")
        // The player state first (an end of file that mpv reported just before exiting included), then the closed window: the screen
        // decides between the end screen and going back from both, so it must never see the window closed with the end still missing.
        publish(closed)
        if (error == null) {
            if (loaded) listener.onProgress(currentPositionMs(), (closed.mpv.durationSecs * 1000).toLong(), true)
            _windowClosed.value = true
        }
    }

    /** mpv is gone but the load is still the player's: Replay / seek start it again where asked. */
    private fun restartIfClosed(startSecs: Double): Boolean {
        val src = currentSource ?: return false
        if (released || !snap.value.closed) return false
        val prev = snap.value.mpv
        restart(loadSeq, src, startSecs, fresh = MpvState(
            loadId = prev.loadId, buffering = true, speed = prev.speed, loop = prev.loop, subDelayMs = prev.subDelayMs,
            started = prev.started, durationSecs = prev.durationSecs, positionSecs = startSecs,
        ))
        return true
    }

    // ---- options ------------------------------------------------------------------------------------------------------------------

    /** Remembers [name] = [value] for the next start and sets it on the running mpv. */
    private fun setOption(name: String, value: String) {
        if (released) return
        synchronized(desired) { desired[name] = value }
        proc?.send(listOf("set", name, value))
    }

    private fun command(vararg args: Any?) {
        if (!released) proc?.send(args.toList())
    }

    override fun play() {
        endedHere = false
        if (restartIfClosed(snap.value.mpv.positionSecs)) return
        setOption("pause", "no")
    }

    override fun pause() = setOption("pause", "yes")

    override fun togglePlay() = if (_state.value.isPlaying) pause() else play()

    override fun seekTo(positionMs: Long) {
        endedHere = false
        qualityController?.onPlaybackIdle()
        val secs = positionMs.coerceAtLeast(0) / 1000.0
        if (restartIfClosed(secs)) return
        command("seek", secs, "absolute")
        snap.update { it.copy(mpv = it.mpv.copy(positionSecs = secs, ended = false)) }
    }

    override fun seekBy(deltaMs: Long) = seekTo(currentPositionMs() + deltaMs)

    override fun setSpeed(speed: Float) = setOption("speed", speed.toDouble().toString())

    /** mpv picks the stream quality itself (as in the embedded engine). */
    override fun selectVideoHeight(height: Int?) = Unit

    override fun selectSubtitle(groupIndex: Int?) = setOption("sid", groupIndex?.toString() ?: "no")

    override fun selectAudio(id: Int) = setOption("aid", if (id == app.nijiyome.player.AUDIO_TRACK_OFF) "no" else id.toString())

    override fun setPreferredLanguages(subtitle: String?, audio: String?) {
        subLang = subtitle
        audioLang = audio
        LanguageCodes.engineVariants(subtitle).takeIf { it.isNotEmpty() }?.let { setOption("slang", it.joinToString(",")) }
        LanguageCodes.engineVariants(audio).takeIf { it.isNotEmpty() }?.let { setOption("alang", it.joinToString(",")) }
    }

    /**
     * The app's subtitle style. With the user's own mpv config the default style is left out (their `sub-*` options win); any other
     * style, or the app's own config, is applied like in the embedded engine.
     */
    override fun setSubtitleStyle(style: SubtitleStyle) {
        if (released) return
        if (setup.userChoseExternal && setup.enhanced == null && style == SubtitleStyle() && !styleApplied) return
        styleApplied = true
        setup.fontsDir?.takeIf { it.isDirectory }?.let { setOption("sub-fonts-dir", it.absolutePath) }
        // libass does not scale sub-line-spacing: pixels per "scaled pixel" of a 1080 p window (the external window's usual size).
        styleApplier.apply(mpvSubtitleProperties(style, mpvFontFamily(style, desktopSubtitleFamily(style.fontFile, setup.fontsDir)), STYLE_PX_PER_UNIT))
    }

    private var styleApplied = false

    @Volatile
    private var subLang: String? = null

    @Volatile
    private var audioLang: String? = null

    override fun currentPositionMs(): Long = (snap.value.mpv.positionSecs * 1000).toLong()

    override fun setSubtitleDelay(ms: Long) = setOption("sub-delay", (app.nijiyome.player.SubtitleDelay.clamp(ms) / 1000.0).toString())

    /** A subtitle file the user picked (the app copied it to its own folder). */
    override fun addSubtitleFile(path: String, title: String): Boolean {
        if (MpvInputGuard.mediaUrl(path, allowLocal = true) == null) return false
        command("sub-add", path, "select", title)
        return true
    }

    override fun addSubtitleTracks(tracks: List<app.nijiyome.model.SubtitleTrack>, select: Int?): Boolean {
        if (mediaRoute == null) {
            addRoutedSubtitles(tracks, select)
            return true
        }
        // 1.1: through the loopback media proxy while an outbound proxy is set; the route may start the proxy, so off the caller's
        // (UI) thread
        scope.launch {
            val routed = withContext(io) { app.nijiyome.player.mpv.routedSubtitleTracks(tracks, mediaRoute) }
            if (!released) addRoutedSubtitles(routed, select)
        }
        return true
    }

    private fun addRoutedSubtitles(tracks: List<app.nijiyome.model.SubtitleTrack>, select: Int?) {
        val subs = safeSubtitles(mpvSubtitles(tracks, select), currentSource?.localFile == true)
        if (!snap.value.fileLoaded) {
            pendingSubs = pendingSubs + subs
            pendingSubsOff = pendingSubsOff || select == PlayableSource.SUBTITLES_OFF
            return
        }
        subs.forEach { command(*subAddCommand(it)) }
        if (select == PlayableSource.SUBTITLES_OFF) setOption("sid", "no")
    }

    override val supportsSecondaryTracks: Boolean get() = true

    override fun selectSecondarySubtitle(id: Int?): Boolean {
        setOption("secondary-sid", id?.toString() ?: "no")
        return true
    }

    override fun setSubtitlesVisible(visible: Boolean) = setOption("sub-visibility", if (visible) "yes" else "no")

    override fun setLoop(loop: Boolean) = setOption("loop-file", if (loop) "inf" else "no")

    override fun setStableVolume(enabled: Boolean) = setOption("af", if (enabled) MpvEngineDefaults.STABLE_VOLUME_FILTER else "")

    /** `screenshot-to-file` on the running mpv; waits for its reply (a few hundred ms). */
    override fun screenshotToFile(path: String): Boolean {
        val p = proc ?: return false
        val f = File(path)
        f.parentFile?.mkdirs()
        val reply = p.request(listOf("screenshot-to-file", f.absolutePath, "video")) ?: return false
        return reply.error == "success" && f.isFile
    }

    override fun changeVolume(deltaPercent: Int): PlayerVolume? {
        val current = currentVolume()
        val next = (current.percent + deltaPercent).coerceIn(0, MAX_VOLUME_PERCENT)
        setOption("volume", next.toString())
        setOption("mute", "no")
        return PlayerVolume(next, muted = false).also(onVolume)
    }

    override fun toggleMute(): PlayerVolume? {
        val current = currentVolume()
        val next = !current.muted
        setOption("mute", if (next) "yes" else "no")
        return PlayerVolume(current.percent, muted = next).also(onVolume)
    }

    override fun volume(): PlayerVolume = currentVolume()

    /** mpv's volume as it last reported it (or as this session set it; 100 before anything is known). */
    private fun currentVolume(): PlayerVolume {
        val p = snap.value.props
        val percent = (p["volume"] ?: synchronized(desired) { desired["volume"] })?.toDoubleOrNull()?.roundToInt() ?: 100
        val muted = (p["mute"] ?: synchronized(desired) { desired["mute"] }) == "yes"
        return PlayerVolume(percent, muted)
    }

    override fun finishAsEnded() {
        if (released || !loaded || endedHere) return
        endedHere = true
        pause()
        _state.value = _state.value.copy(error = null, ended = true, isBuffering = false, isPlaying = false)
        listener.onProgress(currentPositionMs(), _state.value.durationMs, true)
    }

    // ---- quality engine: only when the app owns mpv's config -------------------------------------------------------------------

    override fun setQuality(preset: String, tier: QualityTier) {
        qualityController?.setQuality(preset, tier)
    }

    override fun setQualityRequest(request: QualityRequest) {
        qualityController?.setRequest(request)
    }

    override fun setSharpen(level: SharpenLevel) {
        qualityController?.setSharpen(level)
    }

    override fun setImage(image: ImageAdjust) {
        qualityController?.setImage(image)
    }

    override fun setAspect(mode: AspectMode, zoomPercent: Int) {
        qualityController?.setAspect(mode, zoomPercent)
    }

    override fun setDecoder(mode: DecoderMode) {
        qualityController?.setDecoder(mode)
    }

    override fun setDeband(deband: DebandConfig) {
        qualityController?.setDeband(deband)
    }

    override fun setSmoothing(enabled: Boolean) {
        qualityController?.setSmoothing(enabled)
    }

    override fun setAutoAdjust(enabled: Boolean) {
        qualityController?.setAutoAdjust(enabled)
    }

    override fun undoAutoDowngrade() {
        qualityController?.undoAutoDowngrade()
    }

    override fun setRemoteProcessing(active: Boolean) {
        qualityController?.setRemoteProcessing(active)
    }

    override fun release() {
        if (released) return
        if (loaded) listener.onProgress(currentPositionMs(), _state.value.durationMs, true)
        synchronized(spawnLock) {
            released = true
            starting?.stop()
            starting = null
        }
        ticker.stop()
        monitorJob?.cancel()
        prepareJob?.cancel()
        startWatch?.cancel()
        mirror.cancel()
        proc?.stop()
        proc = null
        lsfgConfig?.delete()
    }

    /** The quality controller's writes as mpv options: Android's hwdec names and `:`-joined shader lists translated for desktop. */
    private inner class ExternalQualityPort : QualityMpvPort {
        override fun setProperty(name: String, value: String) = setOption(name, externalQualityValue(name, value, isWindowsHost()))

        override fun getProperty(name: String): String? = snap.value.props[name] ?: synchronized(desired) { desired[name] }
    }

    /** The subtitles mpv may open: http / https, or local files of the app's own playback ([MpvInputGuard]). */
    private fun safeSubtitles(subs: List<MpvSubtitle>, allowLocal: Boolean): List<MpvSubtitle> =
        subs.filter { MpvInputGuard.mediaUrl(it.url, allowLocal) != null }.also {
            if (it.size != subs.size) mpvLogInfo("refused ${subs.size - it.size} subtitle URL(s)")
        }

    internal companion object {
        /** The error of a load whose URL mpv must not open. */
        const val REFUSED_URL = "refused source URL"

        /** Per-file choices that must not carry over to another file. */
        val TRACK_OPTIONS = listOf("sid", "aid", "secondary-sid", "pause")
        const val MAX_VOLUME_PERCENT = 100

        /** 1080 / 720 (ScreenSubtitleSpec.REFERENCE_HEIGHT): the style's sizes as on a 1080 p window. */
        const val STYLE_PX_PER_UNIT = 1.5f
    }
}

/**
 * The quality controller's values for an mpv with its own window: `hwdec` Auto is mpv's safe list there (the embedded engine's
 * VAAPI-copy list exists for Skiko's GLX context), the rest as the embedded engine translates them.
 */
internal fun externalQualityValue(name: String, value: String, windows: Boolean): String = when (name) {
    "hwdec" -> if (value == DecoderMode.AUTO.hwdec) "auto-safe" else desktopHwdec(value, windows)
    "glsl-shaders" -> if (windows) windowsShaderList(value) else value
    else -> value
}
