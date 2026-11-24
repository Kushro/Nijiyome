// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/sessions.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import app.nijiyome.link.SessionCreatedDto
import app.nijiyome.model.AppLog
import app.nijiyome.server.link.media.Args
import app.nijiyome.server.link.media.ArgsException
import app.nijiyome.server.link.media.BenchStore
import app.nijiyome.server.link.media.Encoder
import app.nijiyome.server.link.media.Engine
import app.nijiyome.server.link.media.EngineExit
import app.nijiyome.server.link.media.EngineJob
import app.nijiyome.server.link.media.EngineProcess
import app.nijiyome.server.link.media.Environment
import app.nijiyome.server.link.media.FrameRate
import app.nijiyome.server.link.media.LsfgStages
import app.nijiyome.server.link.media.PipelineParams
import app.nijiyome.server.link.media.ProfileSpec
import app.nijiyome.server.link.media.Profiles
import app.nijiyome.server.link.media.SEGMENT_SECS
import app.nijiyome.server.link.media.SourceInfo
import app.nijiyome.server.net.NetError
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.Target
import app.nijiyome.server.net.sanitizeHeaders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.floor

/** A segment PUT that the sink refuses: a bad file name (400) or a run that is no longer current (409). */
enum class SinkResult { OK, BAD_NAME, STALE }

/** One transcoding session: on-demand fMP4 HLS with a RAM window; the engine runs produce segments into it through the sink. */
class Session internal constructor(
    val id: String,
    val deviceId: String,
    val profile: ProfileSpec,
    val encoder: Encoder,
    val inputUrl: String,
    val fetchToken: String,
    val sinkToken: String,
    val duration: Double?,
    val torrentId: String?,
    val audioTrack: Int,
    val shader: String?,
    val lsfgRate: FrameRate?,
    val workdir: File,
    val subs: SubtitleStore,
    startSegment: Int,
) {
    /** Guarded by `this`. */
    internal var phase: Phase = Phase.Preparing
    internal var runGen = 0
    internal var genStart = startSegment
    internal var init: ByteArray? = null
    internal val segs = TreeMap<Int, ByteArray>()
    internal var bytes = 0L
    internal var playhead = startSegment
    internal var paused = false
    internal var finished = false
    internal var kill: CompletableDeferred<Unit>? = null
    internal var throttled = false
    internal var wanted = startSegment
    internal var eventTop = startSegment
    internal var runStarted = System.nanoTime()
    internal var runSegments = 0
    internal var runThrottled = false
    internal var runTop = startSegment
    internal var runSpawned = System.nanoTime()
    internal var restartCostMs: Long? = null
    internal var segIntervalMs: Long? = null
    internal var lastStored: Long? = null
    internal var parkedAt: Int? = null
    internal var held = 0
    internal var noWait = false
    internal var lastLife = System.nanoTime()
    internal var autoRestarts = 0
    internal var sourceEnd: Int? = null
    internal var earlyEnd: Int? = null

    @Volatile internal var lastHeartbeat = System.nanoTime()

    /** Bumped on every change waiters care about (`notify_waiters`). */
    internal val version = MutableStateFlow(0L)

    internal fun notifyWaiters() {
        version.value = version.value + 1
    }

    fun totalSegments(): Int? = duration?.let { ceil(it / SEGMENT_SECS).toInt() }

    internal fun removeSeg(k: Int) {
        segs.remove(k)?.let { bytes -= it.size }
    }
}

/**
 * The sessions of the Link server. A *run* (engine generation) produces `seg_<k>.m4s` from `k`; FFmpeg PUTs them to the
 * loopback sink. Segments live in a bounded RAM window (oldest evicted first, never what the phone is about to play). A request
 * outside the window restarts the engine there. The sink holds the answer to a PUT while the engine is too far ahead of the
 * heartbeat's playhead; an encoder that does not wait for that answer is *parked* (killed, its next segment remembered, started
 * again when the playhead comes close). A silent run is restarted where it stopped; after [MAX_AUTO_RESTARTS] the session fails and
 * its media answer `410 segment_unavailable`. 120 s without a heartbeat closes the session. One session per device.
 */
class Manager(
    val tuning: SessionTuning,
    private val engine: Engine,
    private val fetcher: Fetcher,
    private val env: Environment,
    private val shadersDir: File?,
    private val workRoot: File,
    private val allowHttp: Boolean,
    private val host: LinkHost,
    private val events: (LinkEvent) -> Unit = {},
    /** Whether a device is still paired (checked again once a slow preparation is over: it may have been revoked meanwhile). */
    private val deviceActive: (String) -> Boolean = { true },
) {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One lock per device: "close its session, install the new one" and "close everything of a revoked device" never interleave. */
    private val deviceLocks = ConcurrentHashMap<String, Any>()

    private fun lockOf(deviceId: String): Any = deviceLocks.computeIfAbsent(deviceId) { Any() }
    @Volatile var sinkAddress: String = "127.0.0.1:0"
    private val sessions = ConcurrentHashMap<String, Session>()
    private val byDevice = ConcurrentHashMap<String, String>()
    private val deviceGeneration = ConcurrentHashMap<String, Int>()
    private val tombstones = ArrayDeque<String>()
    private val origins = ArrayDeque<Origin>()
    private val nextRun = AtomicInteger(1)
    private val measured = ConcurrentHashMap.newKeySet<String>()
    private val subtitleStores = ConcurrentHashMap<String, Pair<SourceRequest, SubtitleStore>>()
    @Volatile var bench: BenchStore? = null

    private class Origin(val sessionId: String, val deviceId: String, val source: SourceRequest, val torrent: OpenedTorrent?)

    /** A source opened for a session that does not own it yet; [release] gives back its fetch token and torrent. */
    private inner class OpenedSource(val inputUrl: String, val duration: Double?, var token: String?, var torrent: OpenedTorrent?) {
        fun release() {
            token?.let(fetcher::unregister)
            torrent?.let { host.torrentSessionEnded(it.torrentId) }
            token = null
            torrent = null
        }
    }

    private class Prepared(val profile: ProfileSpec, val encoder: Encoder, val source: SourceRequest, val opened: Manager.OpenedSource, val lsfgRate: FrameRate?)

    fun get(id: String): Session? = sessions[id]

    fun bySinkToken(token: String): Session? = sessions.values.firstOrNull { it.sinkToken == token }

    /** The session of [device], or why not: `session_gone` for a tombstone, `session_not_found` otherwise. */
    fun owned(id: String, device: String): Session {
        val s = sessions[id]
        if (s != null) return if (s.deviceId == device) s else api(ErrorCode.SESSION_NOT_FOUND)
        if (synchronized(tombstones) { id in tombstones }) api(ErrorCode.SESSION_GONE)
        api(ErrorCode.SESSION_NOT_FOUND)
    }

    fun activeCount(): Int = sessions.size

    fun deviceIds(): List<String> = byDevice.keys.toList()

    // ---- create / close -----------------------------------------------------------------------------------------------

    suspend fun create(deviceId: String, req: CreateRequest): SessionCreatedDto {
        if (!req.startSecs.isFinite() || req.startSecs < 0 || req.audioTrack > 32) api(ErrorCode.REQUEST_INVALID)
        // BUG-131: a fresh torrent may wait for its metadata; everything else answers within the preparation limit
        val prepared = if (req.source is SourceRequest.Torrent) {
            prepare(deviceId, req)
        } else {
            val job = scope.async { prepare(deviceId, req) }
            val r = withTimeoutOrNull(tuning.prepareTimeoutMs) { job.await() }
            if (r == null) {
                job.cancel()
                // whatever the preparation opened after the deadline is given back
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                job.invokeOnCompletion { e -> if (e == null) runCatching { job.getCompleted().opened.release() } }
                api(ErrorCode.SOURCE_UNAVAILABLE)
            }
            r
        }
        val id = UUID.randomUUID().toString()
        val workdir = File(workRoot, id)
        if (!workdir.mkdirs() && !workdir.isDirectory) {
            AppLog.w(LINK_LOG_TAG, "session workdir creation failed")
            prepared.opened.release()
            api(ErrorCode.INTERNAL)
        }
        val shader = try {
            Args.prepareShader(prepared.profile, shadersDir, workdir)
        } catch (e: Exception) {
            AppLog.w(LINK_LOG_TAG, "prepareShader failed profile=${prepared.profile.id}: ${e::class.simpleName}")
            workdir.deleteRecursively()
            prepared.opened.release()
            api(ErrorCode.PROFILE_UNAVAILABLE)
        }
        val opened = prepared.opened
        val fetchToken = opened.token.orEmpty()
        val torrent = opened.torrent
        // 0.4.0: a renewal of the same source keeps its subtitles (one store per device: its last source)
        val subs = subtitleStores.compute(deviceId) { _, cur ->
            if (cur != null && cur.first == prepared.source) cur else prepared.source to SubtitleStore()
        }!!.second
        val total = opened.duration?.let { ceil(it / SEGMENT_SECS).toInt() }
        // a start past any source (no duration known) is clamped: `start * SEGMENT_SECS` must never overflow FFmpeg's `-ss`
        var start = floor(req.startSecs / SEGMENT_SECS).toInt().coerceAtMost(MAX_SEGMENT_INDEX)
        if (total != null) start = minOf(start, (total - 1).coerceAtLeast(0))
        val session = Session(
            id = id, deviceId = deviceId, profile = prepared.profile, encoder = prepared.encoder, inputUrl = opened.inputUrl,
            fetchToken = fetchToken, sinkToken = UUID.randomUUID().toString().replace("-", ""), duration = opened.duration,
            torrentId = torrent?.torrentId, audioTrack = req.audioTrack.toInt(), shader = shader, lsfgRate = prepared.lsfgRate,
            workdir = workdir, subs = subs, startSegment = start,
        )
        // one active session per device; BUG-131: only now, with the new source already open. Under the device's lock, so two
        // concurrent creates of one phone cannot both survive, and a revocation during the preparation refuses the new session.
        val generation = synchronized(lockOf(deviceId)) {
            if (!deviceActive(deviceId)) null else {
                sessions.values.filter { it.deviceId == deviceId }.map { it.id }.forEach(::close)
                byDevice.remove(deviceId)
                sessions[id] = session
                synchronized(origins) {
                    origins.addLast(Origin(id, deviceId, prepared.source, torrent))
                    while (origins.size > ORIGINS_KEPT) origins.removeFirst()
                }
                byDevice[deviceId] = id
                deviceGeneration.merge(deviceId, 1, Int::plus)!!
            }
        }
        if (generation == null) {
            workdir.deleteRecursively()
            opened.release()
            api(ErrorCode.DEVICE_NOT_AUTHORIZED)
        }
        startRun(session, start)
        listSubtitles(session)
        events(LinkEvent.SessionStarted(deviceId, id))
        return SessionCreatedDto(
            sessionId = id,
            generation = generation,
            playbackPath = "/v1/sessions/$id/master.m3u8",
            durationSecs = opened.duration,
            segmentSecs = SEGMENT_SECS,
            heartbeatIntervalSecs = HEARTBEAT_SECS,
            idleTimeoutSecs = (tuning.idleTimeoutMs / 1000).toInt(),
        )
    }

    private fun originOf(device: String, id: String): Origin? =
        synchronized(origins) { origins.lastOrNull { it.sessionId == id && it.deviceId == device } }

    private suspend fun prepare(deviceId: String, req: CreateRequest): Prepared {
        val wanted = req.profile ?: Profiles.DEFAULT_PROFILE
        val profile = Profiles.find(wanted)?.takeIf(env::profileAvailable) ?: api(ErrorCode.PROFILE_UNAVAILABLE)
        val encoder = env.encoderFor(profile) ?: api(ErrorCode.PROFILE_UNAVAILABLE)
        val (source, reuse) = when (val s = req.source) {
            is SourceRequest.Session -> originOf(deviceId, s.sessionId)?.let { it.source to it.torrent } ?: api(ErrorCode.SESSION_NOT_FOUND)
            else -> s to null
        }
        val opened = openSource(source, reuse)
        try {
            // one probe for what the source does not say: the frame rate (LSFG) and the duration of sources without one
            val needsRate = profile.fg.lsfg() != null
            val info = if (needsRate || opened.duration == null) engine.sourceInfo(opened.inputUrl) else SourceInfo()
            // BUG-024: a link ffprobe cannot read is refused here, not a session that fails a minute later
            if (info.unreadable && (source is SourceRequest.Url || source is SourceRequest.Torrent)) api(ErrorCode.SOURCE_UNAVAILABLE)
            val duration = opened.duration ?: info.durationSecs
            val lsfgRate = if (needsRate) info.rate ?: FrameRate.FALLBACK else null
            return Prepared(profile, encoder, source, OpenedSource(opened.inputUrl, duration, opened.token, opened.torrent), lsfgRate)
        } catch (e: Throwable) {
            opened.release()
            throw e
        }
    }

    private suspend fun torrentInput(t: OpenedTorrent): OpenedSource {
        val held = OpenedSource("", null, null, t)
        try {
            val target = t.url.toHttpUrlOrNull() ?: api(ErrorCode.INTERNAL)
            val trusted = torrentStreamOrigin(target)
            val spec = when {
                trusted != null -> SourceSpec(target, emptyList(), null, listOf(trusted))
                // Debrid (e.g. TorBox) resolves cached torrents to a public HTTPS stream: validate through SafeHttp with no local origins.
                target.scheme == "https" -> SourceSpec(target, emptyList(), null, emptyList())
                else -> api(ErrorCode.INTERNAL)
            }
            val token = fetcher.registerUnmetered(spec)
            held.token = token
            try {
                fetcher.validate(token, target)
            } catch (e: FetchException) {
                api(if (e.kind == FetchException.Kind.DENIED) ErrorCode.URL_SOURCE_DENIED else ErrorCode.SOURCE_UNAVAILABLE)
            }
            return OpenedSource(fetcher.entryUrl(token, target, hls = false), null, token, t)
        } catch (e: Throwable) {
            held.release()
            throw e
        }
    }

    private suspend fun openSource(src: SourceRequest, reuse: OpenedTorrent?): OpenedSource = when (src) {
        is SourceRequest.Session -> api(ErrorCode.REQUEST_INVALID)
        is SourceRequest.Torrent -> {
            if (src.uri.isEmpty() || src.uri.length > 4096 || src.uri.any { it.isISOControl() } || src.mediaId.isEmpty() || src.mediaId.length > 128 || src.episode == 0L) {
                api(ErrorCode.REQUEST_INVALID)
            }
            // BUG-131: a renew holds the torrent the earlier session opened; only when the host lost it is it opened again
            if (reuse != null && host.reuseTorrent(reuse.torrentId)) {
                torrentInput(reuse)
            } else {
                val opened = withTimeoutOrNull(TORRENT_OPEN_TIMEOUT_MS) { host.openTorrent(TorrentRequest(src.uri, src.mediaId, src.episode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())) }
                    ?: api(ErrorCode.SOURCE_UNAVAILABLE)
                torrentInput(opened)
            }
        }
        is SourceRequest.Library -> {
            if (src.itemId.length != 32 || !src.itemId.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) api(ErrorCode.REQUEST_INVALID)
            val path = host.libraryPath(src.itemId) ?: api(ErrorCode.LIBRARY_ITEM_NOT_FOUND)
            if (!path.isFile) api(ErrorCode.LIBRARY_ITEM_NOT_FOUND)
            val duration = host.library().firstOrNull { it.id == src.itemId }?.durationSecs?.takeIf { it > 0 }
            val token = fetcher.registerFile(path)
            OpenedSource(fetcher.fileUrl(token), duration, token, null)
        }
        is SourceRequest.Url -> {
            if (src.url.length > 4096) api(ErrorCode.REQUEST_INVALID)
            val target = when (val t = Target.parse(src.url)) {
                is Target.Http -> t.url
                Target.OtherScheme -> api(ErrorCode.URL_SOURCE_DENIED)
                Target.Malformed -> api(ErrorCode.REQUEST_INVALID)
            }
            // headers: allow-list, size, CR/LF -> 400 (before any policy decision)
            val headers = try {
                sanitizeHeaders(NetPolicy.linkSource(allowHttp), src.headers)
            } catch (_: NetError) {
                api(ErrorCode.REQUEST_INVALID)
            }
            val hls = when (src.container) {
                "hls" -> true
                "mp4" -> false
                null -> target.encodedPath.lowercase().endsWith(".m3u8")
                else -> api(ErrorCode.REQUEST_INVALID) // dash: not supported
            }
            val perms = src.extensionId?.let(host::extensionPermissions)
            val token = fetcher.register(SourceSpec(target, headers, perms, host.trustedMediaOrigins()))
            try {
                fetcher.validate(token, target)
                val duration = if (hls) fetcher.probeHls(token, target) else null
                OpenedSource(fetcher.entryUrl(token, target, hls), duration, token, null)
            } catch (e: FetchException) {
                fetcher.unregister(token)
                api(if (e.kind == FetchException.Kind.DENIED) ErrorCode.URL_SOURCE_DENIED else ErrorCode.SOURCE_UNAVAILABLE)
            } catch (e: Throwable) {
                fetcher.unregister(token)
                throw e
            }
        }
    }

    /** Sessions of [deviceId] that are still open (tests: one per device at most). */
    internal fun liveSessionIds(deviceId: String): List<String> = sessions.values.filter { it.deviceId == deviceId }.map { it.id }

    fun closeDeviceSession(deviceId: String) {
        byDevice.remove(deviceId)?.let(::close)
    }

    fun close(id: String) {
        val s = sessions.remove(id) ?: return
        synchronized(s) {
            s.phase = Phase.Closed
            s.kill?.complete(Unit)
            s.kill = null
            s.init = null
            s.segs.clear()
            s.bytes = 0
        }
        s.notifyWaiters()
        // a listing / extraction this session runs reads its loopback input, which goes away below
        s.subs.abandon(id)
        byDevice.remove(s.deviceId, id)
        fetcher.unregister(s.fetchToken)
        s.torrentId?.let(host::torrentSessionEnded)
        // the working dir is `<work_root>/<session id>`: a uuid we generated, never user input
        if (s.workdir.parentFile == workRoot) s.workdir.deleteRecursively()
        synchronized(tombstones) {
            tombstones.addLast(id)
            while (tombstones.size > TOMBSTONES) tombstones.removeFirst()
        }
        measured.remove(id)
        events(LinkEvent.SessionEnded(id))
    }

    fun closeAll() {
        sessions.keys.toList().forEach(::close)
    }

    /** Closes the sessions of a revoked device. */
    fun closeForDevice(deviceId: String) = synchronized(lockOf(deviceId)) {
        subtitleStores.remove(deviceId)
        byDevice.remove(deviceId)
        sessions.values.filter { it.deviceId == deviceId }.map { it.id }.forEach(::close)
    }

    /** Closes sessions whose last heartbeat is older than the idle timeout; returns how many. */
    fun reapIdle(): Int {
        val now = System.nanoTime()
        val idle = sessions.values.filter { (now - it.lastHeartbeat) / 1_000_000 > tuning.idleTimeoutMs }.map { it.id }
        idle.forEach(::close)
        return idle.size
    }

    fun shutdown() {
        closeAll()
        scope.cancel()
    }

    // ---- engine runs --------------------------------------------------------------------------------------------------

    /** Starts (or restarts) the engine at [startSegment], killing the previous run. */
    internal suspend fun startRun(s: Session, startSegment: Int) {
        val kill = CompletableDeferred<Unit>()
        val gen = nextRun.getAndIncrement()
        synchronized(s) {
            if (s.phase == Phase.Closed) return
            s.kill?.complete(Unit)
            s.kill = kill
            s.runGen = gen
            s.genStart = startSegment
            // a (re)start at k means the consumer is at k; the next heartbeat refines it
            s.playhead = startSegment
            // BUG-095: the segments of earlier runs stay (absolute timestamps via -output_ts_offset)
            s.runTop = startSegment
            evict(s)
            val now = System.nanoTime()
            s.runSpawned = now
            s.lastStored = null
            s.finished = false
            s.wanted = startSegment
            s.runStarted = now
            s.runSegments = 0
            s.runThrottled = false
            s.parkedAt = null
            s.held = 0
            s.throttled = false
            s.lastLife = now
            if (s.phase is Phase.Failed) s.phase = Phase.Preparing
        }
        val sinkBase = "http://$sinkAddress/sink/${s.sinkToken}/$gen"
        val params = PipelineParams(s.profile, s.encoder, env.backend(), s.inputUrl, sinkBase, startSegment, s.audioTrack, s.shader)
        val rate = s.lsfgRate
        val paths = env.lsfg.paths
        val built = try {
            when {
                rate != null && paths != null -> {
                    val a = Args.buildLsfg(params, rate, paths.dll)
                    a.encode to LsfgStages(a.decode, paths.helper, a.helper)
                }
                rate != null -> {
                    AppLog.w(LINK_LOG_TAG, "session start failed profile=${s.profile.id}: lsfg_paths_missing")
                    return fail(s, gen, FailReason.ENGINE_UNAVAILABLE)
                }
                else -> Args.build(params) to null
            }
        } catch (e: ArgsException) {
            AppLog.w(LINK_LOG_TAG, "session args failed profile=${s.profile.id} encoder=${s.encoder.wire}: ${e.reason}")
            return fail(s, gen, FailReason.ENGINE_UNAVAILABLE)
        }
        val proc: EngineProcess = try {
            engine.spawn(EngineJob(built.first, s.workdir, startSegment, sinkBase, built.second))
        } catch (e: Exception) {
            AppLog.w(LINK_LOG_TAG, "session spawn failed profile=${s.profile.id} encoder=${s.encoder.wire}: ${e::class.simpleName}")
            return fail(s, gen, FailReason.ENGINE_UNAVAILABLE)
        }
        scope.launch {
            val exit = async { proc.await() }
            select<Unit> {
                exit.onAwait { code -> onRunExit(s, gen, code) }
                kill.onAwait {
                    proc.kill()
                    exit.cancel()
                }
            }
        }
    }

    private fun onRunExit(s: Session, gen: Int, exit: EngineExit) {
        if (synchronized(s) { s.runGen } != gen) return
        when {
            exit == EngineExit.SUCCESS -> {
                synchronized(s) {
                    s.finished = true
                    runEnd(s, s.totalSegments())
                }
                s.notifyWaiters()
            }
            fetcher.wasDenied(s.fetchToken) -> fail(s, gen, FailReason.URL_SOURCE_DENIED)
            else -> {
                // 0.4.1: a run that already produced may have hit a transient read error: end it like an early end
                val retry = synchronized(s) {
                    val retry = s.runSegments > 0 && s.autoRestarts < MAX_AUTO_RESTARTS && s.phase != Phase.Closed
                    if (retry) {
                        s.autoRestarts++
                        s.finished = true
                        if (s.runTop > s.genStart) {
                            s.removeSeg(s.runTop - 1)
                            s.runTop -= 1
                        }
                    }
                    retry
                }
                if (retry) s.notifyWaiters() else fail(s, gen, FailReason.SOURCE_READ_FAILED)
            }
        }
    }

    /**
     * 0.4.1: a run exited with success. FFmpeg also exits with 0 when its input breaks, so only a run that got within [END_SLACK] of
     * the announced length (or a source of unknown length), or ended at the same place twice, reached the end of the source;
     * otherwise its last segment may be cut short and is dropped, and the next run makes it again. Under `synchronized(s)`.
     */
    private fun runEnd(s: Session, total: Int?) {
        val top = if (s.runSegments > 0) s.runTop else s.genStart
        val again = s.runSegments > 0 && s.earlyEnd == top
        if (total == null || satAdd(top, END_SLACK) >= total || again) {
            s.sourceEnd = top
            return
        }
        s.earlyEnd = if (s.runSegments > 0) top else null
        if (s.runSegments > 0 && s.runTop > s.genStart) {
            s.removeSeg(s.runTop - 1)
            s.runTop -= 1
        }
    }

    private fun fail(s: Session, gen: Int, reason: FailReason) {
        var changed = false
        synchronized(s) {
            if (s.runGen == gen && s.phase != Phase.Closed) {
                s.phase = Phase.Failed(reason)
                changed = true
            }
        }
        if (changed) AppLog.w(LINK_LOG_TAG, "session failed profile=${s.profile.id} encoder=${s.encoder.wire} reason=${reason.wire}")
        s.notifyWaiters()
    }

    // ---- sink (PUTs of FFmpeg) ----------------------------------------------------------------------------------------

    /** Stores a file PUT by FFmpeg, holding the answer (back-pressure) while the run is too far ahead of the playhead. */
    suspend fun sinkPut(s: Session, gen: Int, name: String, body: ByteArray): SinkResult {
        val k: Int? = when {
            name == "init.mp4" -> null
            name == "live.m3u8" || name.endsWith(".tmp") -> {
                // the playlist FFmpeg rewrites after each segment: a sign of life of the run
                synchronized(s) { if (s.runGen == gen) s.lastLife = System.nanoTime() }
                return SinkResult.OK
            }
            else -> name.removePrefix("seg_").removeSuffix(".m4s").takeIf { name.startsWith("seg_") && name.endsWith(".m4s") }?.toIntOrNull()
                ?: return SinkResult.BAD_NAME
        }
        if (k == null) {
            synchronized(s) {
                if (s.runGen != gen || s.phase == Phase.Closed) return SinkResult.STALE
                s.init = body
                s.lastLife = System.nanoTime()
                if (s.phase == Phase.Preparing) s.phase = Phase.Ready
            }
            s.notifyWaiters()
            return SinkResult.OK
        }
        var isHeld = false
        try {
            while (true) {
                val v = s.version.value
                var park: Boolean? = null
                var tooFar = false
                synchronized(s) {
                    if (s.runGen != gen || s.phase == Phase.Closed) return SinkResult.STALE
                    s.lastLife = System.nanoTime()
                    tooFar = leadExceeded(k, s.playhead, s.wanted, lead(s))
                    s.throttled = tooFar
                    s.runThrottled = s.runThrottled || tooFar
                    if (!tooFar) return@synchronized
                    if (!isHeld) {
                        isHeld = true
                        s.held++
                    }
                    // 0.4.1: an encoder that waits for the answer never has two PUTs held at once; FFmpeg n9 does not wait
                    if (s.held >= 2) park = park(s)
                }
                val parkedAhead = park
                if (parkedAhead != null) {
                    if (parkedAhead) noteSustain(s, true)
                    s.notifyWaiters()
                    return SinkResult.STALE
                }
                if (!tooFar) break
                withTimeoutOrNull(250) { s.version.first { it != v } }
            }
        } finally {
            if (isHeld) synchronized(s) { if (s.runGen == gen) s.held = (s.held - 1).coerceAtLeast(0) }
        }
        var verdict: Boolean? = null
        synchronized(s) {
            if (s.runGen != gen || s.phase == Phase.Closed) return SinkResult.STALE
            // BUG-095: restart cost (first segment of a run) and time per segment (back-pressure waits excluded)
            val now = System.nanoTime()
            val prev = s.lastStored
            if (prev == null) {
                s.restartCostMs = (now - s.runSpawned) / 1_000_000
            } else {
                val dt = (now - prev) / 1_000_000
                s.segIntervalMs = s.segIntervalMs?.let { (it * 3 + dt) / 4 } ?: dt
            }
            s.eventTop = maxOf(s.eventTop, k + 1)
            s.runTop = maxOf(s.runTop, k + 1)
            s.bytes += body.size
            s.segs.put(k, body)?.let { s.bytes -= it.size }
            evict(s)
            // timed from the first segment: FFmpeg start-up is not throughput
            if (s.runSegments == 0) s.runStarted = System.nanoTime()
            s.lastStored = System.nanoTime()
            s.lastLife = System.nanoTime()
            s.runSegments++
            s.autoRestarts = 0
            verdict = sustainVerdict(s.runThrottled, s.runSegments, (System.nanoTime() - s.runStarted) / 1_000_000, s.paused)
            if (s.phase == Phase.Preparing && s.init != null) s.phase = Phase.Ready
        }
        verdict?.let { noteSustain(s, it) }
        s.notifyWaiters()
        return SinkResult.OK
    }

    /** Twice the lead once the encoder is known not to wait for the sink (fewer restarts after parking). */
    private fun lead(s: Session): Int = if (s.noWait) tuning.maxLeadSegments * 2 else tuning.maxLeadSegments

    /** 0.4.1: kills the run (its held PUTs become stale) and remembers its next segment. Returns whether it was held. */
    private fun park(s: Session): Boolean {
        s.kill?.complete(Unit)
        s.kill = null
        // a fresh generation without a process: whatever the killed run still sends is stale
        s.runGen = nextRun.getAndIncrement()
        s.parkedAt = s.runTop
        s.noWait = true
        s.held = 0
        s.throttled = true
        return s.runThrottled
    }

    /** Records the first verdict of [s] for its profile (BUG-025). */
    private fun noteSustain(s: Session, sustained: Boolean) {
        if (!measured.add(s.id)) return
        bench?.record(s.profile.id, sustained)
    }

    /** 0.4.1: resume parked runs the phone is about to need, restart stalled ones, fail the hopeless ones. */
    fun supervise() {
        for (s in sessions.values.toList()) {
            val decision = synchronized(s) {
                val view = RunView(
                    active = s.phase == Phase.Preparing || s.phase == Phase.Ready,
                    parkedAt = s.parkedAt,
                    runTop = s.runTop,
                    playhead = s.playhead,
                    wanted = s.wanted,
                    finished = s.finished,
                    held = s.held,
                    quietMs = (System.nanoTime() - s.lastLife) / 1_000_000,
                    restartCostMs = s.restartCostMs,
                    autoRestarts = s.autoRestarts,
                )
                val d = superviseDecision(view, tuning.maxLeadSegments, tuning.stallTimeoutMs)
                when (d) {
                    is Supervise.Restart -> s.autoRestarts++
                    Supervise.Fail -> {
                        s.kill?.complete(Unit)
                        s.kill = null
                        s.phase = Phase.Failed(FailReason.ENGINE_STALLED)
                    }
                    is Supervise.Resume -> s.parkedAt = null
                    null -> Unit
                }
                d
            } ?: continue
            val at = when (decision) {
                is Supervise.Resume -> decision.at
                is Supervise.Restart -> decision.at
                Supervise.Fail -> {
                    AppLog.w(LINK_LOG_TAG, "session stalled profile=${s.profile.id} encoder=${s.encoder.wire}")
                    s.notifyWaiters()
                    continue
                }
            }
            scope.launch { startRun(s, at) }
        }
    }

    /** Drops segments from the RAM window; what the phone is about to play is never evicted, even over the budget. */
    private fun evict(s: Session) {
        val keepFrom = (s.playhead - KEEP_BEHIND).coerceAtLeast(0)
        s.segs.headMap(keepFrom).keys.toList().forEach(s::removeSeg)
        val protectFrom = (s.playhead - PROTECT_BEHIND).coerceAtLeast(0)
        val protectTo = maxOf(satAdd(s.playhead, lead(s)), s.wanted)
        while (s.bytes > tuning.maxRamBytes) {
            // behind the playhead first (oldest first), then past the protected range (furthest first)
            val first = s.segs.firstEntry()?.key?.takeIf { it < protectFrom }
            val last = s.segs.lastEntry()?.key?.takeIf { it > protectTo }
            val k = first ?: last ?: break
            s.removeSeg(k)
        }
    }

    // ---- subtitles -----------------------------------------------------------------------------------------------------

    /** Lists the source's subtitle tracks in the background (unless the renewed session's store already has them). */
    private fun listSubtitles(s: Session) {
        val cancel = s.subs.beginProbe(s.id) ?: return
        val job = scope.launch {
            val tracks = engine.subtitleTracks(s.inputUrl)
            s.subs.finishProbe(s.id, tracks)
        }
        cancel.attach(job)
    }

    /** The single extraction pass of the store of [s], started by the first file request. */
    private fun extractSubtitles(s: Session) {
        val (tracks, cancel) = s.subs.beginExtraction(s.id) ?: return
        val dir = File(workRoot, "subs-${UUID.randomUUID().toString().replace("-", "")}")
        val outputs = Subtitles.planOutputs(tracks, dir)
        val job = scope.launch {
            if (outputs.isEmpty()) {
                s.subs.finishExtraction(s.id, emptyMap())
                return@launch
            }
            if (!dir.mkdirs() && !dir.isDirectory) {
                s.subs.finishExtraction(s.id, null)
                return@launch
            }
            try {
                val ok = withTimeoutOrNull(Subtitles.EXTRACT_TIMEOUT_MS) {
                    runCatching { engine.extractSubtitles(s.inputUrl, outputs) }.isSuccess
                }
                s.subs.finishExtraction(s.id, if (ok == true) Subtitles.collectOutputs(outputs) else null)
            } finally {
                dir.deleteRecursively()
            }
        }
        cancel.attach(job)
    }

    /** `GET .../subtitles/{n}.{ass|vtt}`: the file, waiting up to the subtitle wait for the listing / the pass. */
    suspend fun subtitle(s: Session, n: Int, format: SubFormat): ByteArray {
        val deadline = System.nanoTime() + tuning.subtitleWaitMs * 1_000_000
        while (true) {
            val v = s.subs.changes.value
            if (synchronized(s) { s.phase } == Phase.Closed) api(ErrorCode.SESSION_GONE)
            val waiting = when (val l = s.subs.lookup(n, format)) {
                is Lookup.File -> return l.bytes
                Lookup.NotFound -> api(ErrorCode.SESSION_NOT_FOUND)
                Lookup.Unavailable -> api(ErrorCode.SOURCE_UNAVAILABLE)
                Lookup.Listing -> ErrorCode.SESSION_NOT_READY
                is Lookup.Extracting -> {
                    if (l.start) {
                        extractSubtitles(s)
                        continue
                    }
                    ErrorCode.SEGMENT_WAIT_TIMEOUT
                }
            }
            val left = (deadline - System.nanoTime()) / 1_000_000
            if (left <= 0 || withTimeoutOrNull(left) { s.subs.changes.first { it != v } } == null) api(waiting)
        }
    }

    // ---- reads (HLS surface) ------------------------------------------------------------------------------------------

    fun heartbeat(s: Session, positionSecs: Double, paused: Boolean) {
        s.lastHeartbeat = System.nanoTime()
        synchronized(s) {
            s.playhead = floor(positionSecs.coerceAtLeast(0.0) / SEGMENT_SECS).toInt()
            s.paused = paused
        }
        s.notifyWaiters()
        // 0.4.1: a parked run resumes as soon as the playhead gets close, not at the next tick
        supervise()
    }

    suspend fun initSegment(s: Session): ByteArray {
        val deadline = System.nanoTime() + tuning.segmentWaitMs * 1_000_000
        while (true) {
            val v = s.version.value
            synchronized(s) {
                when (s.phase) {
                    Phase.Closed -> api(ErrorCode.SESSION_GONE)
                    is Phase.Failed -> api(ErrorCode.SEGMENT_UNAVAILABLE)
                    else -> Unit
                }
                s.init?.let { return it }
            }
            val left = (deadline - System.nanoTime()) / 1_000_000
            if (left <= 0 || withTimeoutOrNull(left) { s.version.first { it != v } } == null) api(ErrorCode.SEGMENT_WAIT_TIMEOUT)
        }
    }

    /** Segment [k]: from the window, or restart the engine there and wait. */
    suspend fun segment(s: Session, k: Int): ByteArray {
        s.totalSegments()?.let { if (k >= it) api(ErrorCode.SESSION_NOT_FOUND) }
        val deadline = System.nanoTime() + tuning.segmentWaitMs * 1_000_000
        var restarted = false
        while (true) {
            val v = s.version.value
            var restartAt: Int? = null
            var wake = false
            synchronized(s) {
                when (s.phase) {
                    Phase.Closed -> api(ErrorCode.SESSION_GONE)
                    is Phase.Failed -> api(ErrorCode.SEGMENT_UNAVAILABLE)
                    else -> Unit
                }
                s.segs[k]?.let { return it }
                if (s.sourceEnd?.let { k >= it } == true) api(ErrorCode.SESSION_NOT_FOUND)
                val frontier = s.runTop
                val behind = k < s.runTop
                val tooFar = k >= frontier && !waitForFrontier(k - frontier, tuning.seekAheadSegments, s.segIntervalMs, s.restartCostMs)
                val dead = s.finished
                val parked = s.parkedAt != null
                val restart = (behind || tooFar || dead || parked) && !restarted
                if (!restart && k > s.wanted) {
                    // the phone waits for k: let the engine produce it even past the lead (BUG-058)
                    s.wanted = k
                    wake = true
                    return@synchronized
                }
                if (restart && dead && !behind && !tooFar && s.runSegments == 0) {
                    // 0.4.1: runs that end without making anything are the "preparing" loop: give up after MAX_AUTO_RESTARTS
                    s.autoRestarts++
                    if (s.autoRestarts > MAX_AUTO_RESTARTS) {
                        s.phase = Phase.Failed(FailReason.ENGINE_STALLED)
                        wake = true
                        restartAt = -1
                        return@synchronized
                    }
                }
                if (restart) {
                    // a parked or early-ended run goes on from where it stopped when k is within reach of it
                    restartAt = if ((parked || dead) && !behind && !tooFar) frontier else k
                    s.parkedAt = null
                }
            }
            if (wake) {
                s.notifyWaiters()
                if (restartAt == -1) api(ErrorCode.SEGMENT_UNAVAILABLE)
                continue
            }
            val at = restartAt
            if (at != null) {
                restarted = true
                startRun(s, at)
                continue
            }
            val left = (deadline - System.nanoTime()) / 1_000_000
            if (left <= 0 || withTimeoutOrNull(left) { s.version.first { it != v } } == null) api(ErrorCode.SEGMENT_WAIT_TIMEOUT)
        }
    }

    /** `media.m3u8`: VOD (known duration) or EVENT (growing). */
    fun mediaPlaylist(s: Session): String = synchronized(s) {
        when (s.phase) {
            Phase.Closed -> api(ErrorCode.SESSION_GONE)
            is Phase.Failed -> api(ErrorCode.SOURCE_UNAVAILABLE)
            Phase.Preparing -> api(ErrorCode.SESSION_NOT_READY)
            Phase.Ready -> Unit
        }
        val secs = SEGMENT_SECS.toDouble()
        buildString {
            append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:$SEGMENT_SECS\n#EXT-X-MEDIA-SEQUENCE:0\n")
            val d = s.duration
            if (d != null) {
                append("#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-INDEPENDENT-SEGMENTS\n#EXT-X-MAP:URI=\"init.mp4\"\n")
                val n = s.totalSegments() ?: 0
                for (k in 0 until n) {
                    val len = if (k + 1 == n) (d - secs * k).coerceIn(0.001, secs) else secs
                    append("#EXTINF:${"%.6f".format(java.util.Locale.ROOT, len)},\nseg_$k.m4s\n")
                }
                append("#EXT-X-ENDLIST\n")
            } else {
                append("#EXT-X-PLAYLIST-TYPE:EVENT\n#EXT-X-INDEPENDENT-SEGMENTS\n#EXT-X-MAP:URI=\"init.mp4\"\n")
                val top = maxOf(if (s.segs.isEmpty()) s.genStart else s.segs.lastEntry().key + 1, s.eventTop)
                for (k in 0 until top) append("#EXTINF:${"%.6f".format(java.util.Locale.ROOT, secs)},\nseg_$k.m4s\n")
                if (s.finished) append("#EXT-X-ENDLIST\n")
            }
        }
    }

    fun masterPlaylist(s: Session): String {
        synchronized(s) {
            when (s.phase) {
                Phase.Closed -> api(ErrorCode.SESSION_GONE)
                is Phase.Failed -> api(ErrorCode.SOURCE_UNAVAILABLE)
                Phase.Preparing -> api(ErrorCode.SESSION_NOT_READY)
                Phase.Ready -> Unit
            }
        }
        val bw = s.profile.videoKbps.toLong() * 1000 + 192_000
        return "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-INDEPENDENT-SEGMENTS\n#EXT-X-STREAM-INF:BANDWIDTH=$bw,RESOLUTION=${s.profile.width}x${s.profile.height}\nmedia.m3u8\n"
    }

    /** `GET /v1/sessions/{id}` (contract `SessionState`, with the additive 0.4.0 / 0.4.1 fields). */
    fun stateJson(s: Session, instanceId: String): JsonObject {
        val processing = processingJson(s.profile, s.encoder, s.lsfgRate)
        val outFps = (processing["fg_effective"] as? JsonObject)?.get("out_fps")
        val tracks = s.subs.tracks()
        val subsState = s.subs.stateString()
        return synchronized(s) {
            val (state, error) = when (val p = s.phase) {
                Phase.Preparing -> "preparing" to null
                Phase.Ready -> "ready" to null
                is Phase.Failed -> "failed" to p.reason.wire
                Phase.Closed -> "closed" to null
            }
            val frontier = s.segs.subMap(s.genStart, true, s.runTop, false).lastEntry()?.key
            val secsOf = { k: Int ->
                val t = k.toDouble() * SEGMENT_SECS
                s.duration?.let { minOf(it, t) } ?: t
            }
            buildJsonObject {
                put("state", state)
                put("generation", s.runGen)
                put("instance_id", instanceId)
                put("profile", s.profile.id)
                put("throttled", s.throttled)
                putJsonObject("output") {
                    put("width", s.profile.width)
                    put("height", s.profile.height)
                    outFps?.let { put("fps", it) }
                }
                putJsonObject("window") {
                    put("first_segment", if (s.segs.isEmpty()) s.genStart else s.segs.firstEntry().key)
                    put("bytes", s.bytes)
                }
                if (frontier != null) {
                    put("frontier_segment", frontier)
                    put("lead_secs", (frontier - s.playhead).coerceAtLeast(0).toDouble() * SEGMENT_SECS)
                }
                error?.let { put("error", it) }
                put("parked", s.parkedAt != null)
                s.sourceEnd?.let { e ->
                    put("ended", true)
                    val end = e.toDouble() * SEGMENT_SECS
                    put("end_secs", s.duration?.let { minOf(it, end) } ?: end)
                }
                producedRange(s.segs.keys, s.playhead)?.let { (from, to) ->
                    putJsonObject("produced") {
                        put("from_secs", secsOf(from))
                        put("to_secs", secsOf(to))
                    }
                }
                processing.forEach { (k, v) -> put(k, v) }
                tracks?.let { t -> put("subtitles", buildJsonArray { t.forEach { add(it.toJson()) } }) }
                put("subtitles_state", JsonPrimitive(subsState))
            }
        }
    }

    /** Supervising tick (idle reaper + engine supervisor), at most once a second. */
    fun startTicker(): kotlinx.coroutines.Job {
        val tick = minOf(tuning.idleTimeoutMs / 4, tuning.stallTimeoutMs / 4).coerceIn(50, 1000)
        return scope.launch {
            while (true) {
                delay(tick)
                runCatching { reapIdle() }
                runCatching { supervise() }
            }
        }
    }
    private companion object {
        const val LINK_LOG_TAG = "link"
    }
}

sealed interface LinkEvent {
    data class DevicePaired(val deviceId: String, val name: String) : LinkEvent
    data class DeviceRevoked(val deviceId: String) : LinkEvent
    data class SessionStarted(val deviceId: String, val sessionId: String) : LinkEvent
    data class SessionEnded(val sessionId: String) : LinkEvent
}
