// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/sessions.rs (AGPL-3.0, same author): the pure decisions of the session engine.
package app.nijiyome.server.link

import app.nijiyome.server.link.media.Encoder
import app.nijiyome.server.link.media.Fg
import app.nijiyome.server.link.media.FrameRate
import app.nijiyome.server.link.media.ProfileSpec
import app.nijiyome.server.link.media.SEGMENT_SECS
import app.nijiyome.server.link.media.Upscale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.SortedMap
import kotlin.math.ceil
import kotlin.math.roundToLong

const val HEARTBEAT_SECS = 4

/** Highest segment index a session can have: sources are at most [app.nijiyome.server.link.media.Probe.MAX_SOURCE_SECS] long. */
const val MAX_SEGMENT_INDEX: Int = 24 * 3600 / app.nijiyome.server.link.media.SEGMENT_SECS
const val IDLE_TIMEOUT_SECS = 120L

/** Adding a magnet waits for its metadata: give up after this long (BUG-009). */
const val TORRENT_OPEN_TIMEOUT_MS = 75_000L

/** BUG-131: preparation limit of every session that is not a fresh torrent, below the phones' 20 s read timeout. */
const val PREPARE_TIMEOUT_MS = 15_000L

/** Sources of the last sessions kept for a `session` source. */
const val ORIGINS_KEPT = 128

/** BUG-095: segments kept behind the playhead (60 s), so a short rewind is served from RAM. */
const val KEEP_BEHIND = 30

/** BUG-095: a request at most this many segments past the frontier may wait for the running engine instead of restarting it. */
const val MAX_WAIT_AHEAD = 30

/** 0.4.1: segments right behind the playhead that are never evicted. */
const val PROTECT_BEHIND = 2

/** 0.4.1: automatic restarts without any segment produced in between before the session fails with `engine_stalled`. */
const val MAX_AUTO_RESTARTS = 3

/** 0.4.1: a parked run starts again this many segments (plus its restart cost) before the phone reaches it. */
const val RESUME_MARGIN = 5

/** Segments before the announced end that still count as the end of the source. */
const val END_SLACK = 3

/** Seconds of a run after which the sustain verdict is taken (BUG-025). */
const val SUSTAIN_WINDOW_SECS = 20L

const val TOMBSTONES = 64

data class SessionTuning(
    val segmentWaitMs: Long = app.nijiyome.server.link.media.SEGMENT_WAIT_SECS * 1000,
    val idleTimeoutMs: Long = IDLE_TIMEOUT_SECS * 1000,
    val maxRamBytes: Long = app.nijiyome.server.link.media.MAX_RAM_BYTES,
    /** How far (segments) the engine may run ahead of the playhead before the sink holds it. */
    val maxLeadSegments: Int = 15,
    /** A request further than this past the frontier restarts the engine there (unless waiting is measured to be cheaper). */
    val seekAheadSegments: Int = 4,
    val prepareTimeoutMs: Long = PREPARE_TIMEOUT_MS,
    val subtitleWaitMs: Long = Subtitles.REQUEST_WAIT_MS,
    /** 0.4.1: a run silent this long while it should produce is stalled and restarted. */
    val stallTimeoutMs: Long = 30_000,
)

enum class FailReason(val wire: String) {
    SOURCE_UNAVAILABLE("source_unavailable"),
    SOURCE_READ_FAILED("source_read_failed"),
    ENGINE_UNAVAILABLE("engine_unavailable"),
    URL_SOURCE_DENIED("url_source_denied"),
    MEMORY_LIMIT("memory_limit"),
    ENGINE_STALLED("engine_stalled"),
}

sealed interface Phase {
    data object Preparing : Phase
    data object Ready : Phase
    data class Failed(val reason: FailReason) : Phase
    data object Closed : Phase
}

/** `POST /v1/sessions` source. */
sealed interface SourceRequest {
    val kind: String

    data class Library(val itemId: String) : SourceRequest { override val kind = "library" }
    /** The source of an earlier session of the SAME device, open or recently closed (BUG-008 / BUG-022 / BUG-038). */
    data class Session(val sessionId: String) : SourceRequest { override val kind = "session" }
    /** A torrent the PC adds to its own engine (BUG-009). */
    data class Torrent(val uri: String, val mediaId: String, val episode: Long) : SourceRequest { override val kind = "torrent" }
    data class Url(val url: String, val headers: SortedMap<String, String>, val extensionId: String?, val container: String?) : SourceRequest {
        override val kind = "url"
    }
}

data class CreateRequest(val source: SourceRequest, val profile: String?, val audioTrack: Long = 0, val startSecs: Double = 0.0, val title: String? = null)

/** Back-pressure: segment [k] waits while more than [maxLead] past the playhead, unless a phone waits for it or a later one (BUG-058). */
fun leadExceeded(k: Int, playhead: Int, wanted: Int, maxLead: Int): Boolean = k > satAdd(playhead, maxLead) && k > wanted

/**
 * BUG-095: whether a request [distance] segments past the running engine's frontier waits for it or restarts the engine there.
 * Always waits within [minAhead]; past it, up to [MAX_WAIT_AHEAD], only when the engine makes them sooner than a restart.
 */
fun waitForFrontier(distance: Int, minAhead: Int, perSegmentMs: Long?, restartCostMs: Long?): Boolean {
    if (distance <= minAhead) return true
    if (perSegmentMs == null || restartCostMs == null) return false
    return distance <= MAX_WAIT_AHEAD && perSegmentMs * distance < restartCostMs
}

/**
 * BUG-025: whether the engine keeps up with real time. Held by back-pressure = it was ahead. Otherwise after [SUSTAIN_WINDOW_SECS]
 * of an unpaused run (timed from its first segment) it must have made at least 95 % of that time. null = not decided yet.
 */
fun sustainVerdict(throttled: Boolean, segments: Int, elapsedMs: Long, paused: Boolean): Boolean? {
    if (throttled) return true
    if (paused || elapsedMs < SUSTAIN_WINDOW_SECS * 1000) return null
    val produced = (segments - 1).coerceAtLeast(0).toDouble() * SEGMENT_SECS
    return produced >= 0.95 * (elapsedMs / 1000.0)
}

sealed interface Supervise {
    data class Resume(val at: Int) : Supervise
    data class Restart(val at: Int) : Supervise
    data object Fail : Supervise
}

/** The part of a session's state [superviseDecision] looks at. */
data class RunView(
    val active: Boolean,
    val parkedAt: Int?,
    val runTop: Int,
    val playhead: Int,
    val wanted: Int,
    val finished: Boolean,
    val held: Int,
    val quietMs: Long,
    val restartCostMs: Long?,
    val autoRestarts: Int,
)

/**
 * 0.4.1: a parked run resumes once the playhead is within `restart cost + RESUME_MARGIN` segments of where it stopped (or a phone
 * asks for it); a running run that is not held, not finished and silent for the stall timeout (or twice its restart cost) is
 * restarted where it stopped, up to [MAX_AUTO_RESTARTS] times in a row, then the session fails.
 */
fun superviseDecision(v: RunView, maxLead: Int, stallTimeoutMs: Long): Supervise? {
    if (!v.active) return null
    val at = v.parkedAt
    if (at != null) {
        val cost = v.restartCostMs?.let { ceil(it / 1000.0 / SEGMENT_SECS).toInt() } ?: 8
        val reach = (cost + RESUME_MARGIN).coerceIn(RESUME_MARGIN, maxOf(maxLead, RESUME_MARGIN))
        return if (at <= satAdd(v.playhead, reach) || at <= v.wanted) Supervise.Resume(at) else null
    }
    if (v.finished || v.held > 0) return null
    val limit = v.restartCostMs?.let { maxOf(stallTimeoutMs, it * 2) } ?: stallTimeoutMs
    if (v.quietMs < limit) return null
    return if (v.autoRestarts >= MAX_AUTO_RESTARTS) Supervise.Fail else Supervise.Restart(v.runTop)
}

/** 0.4.1: the run of consecutive segments the window holds around [playhead], as `[from, to)`: the one that contains it, else the next. */
fun producedRange(segs: Set<Int>, playhead: Int): Pair<Int, Int>? {
    val start = if (playhead in segs) {
        var from = playhead
        while (from > 0 && (from - 1) in segs) from--
        from
    } else {
        segs.filter { it >= playhead }.minOrNull() ?: return null
    }
    var to = start
    while (to in segs) to++
    return start to to
}

/**
 * 0.4.0: what the session's processing is, as additive `GET /v1/sessions/{id}` fields: `upscale`, `anime4k {mode, variant}`, `fg`,
 * `encoder`, `video_kbps` and `fg_effective {multiplier, source_fps?, out_fps?}` (for LSFG auto the multiplier chosen for this source).
 */
fun processingJson(profile: ProfileSpec, encoder: Encoder, lsfgRate: FrameRate?): JsonObject = buildJsonObject {
    put("upscale", if (profile.upscale == Upscale.None) "none" else "anime4k")
    put("fg", profile.fg.wire)
    put("encoder", encoder.wire)
    put("video_kbps", profile.videoKbps)
    (profile.upscale as? Upscale.Anime4k)?.let { a ->
        putJsonObject("anime4k") {
            put("mode", a.mode.wire)
            put("variant", a.variant.wire)
        }
    }
    when (val fg = profile.fg) {
        Fg.None -> Unit
        Fg.FrcAmf -> putJsonObject("fg_effective") { put("multiplier", 2) }
        is Fg.Lsfg -> {
            val source = lsfgRate ?: FrameRate.FALLBACK
            val m = fg.mode.multiplier(source.fps())
            putJsonObject("fg_effective") {
                put("multiplier", m)
                put("source_fps", round3(source.fps()))
                put("out_fps", round3(source.fps() * m))
            }
        }
    }
}

internal fun round3(x: Double): Double = (x * 1000.0).roundToLong() / 1000.0

internal fun satAdd(a: Int, b: Int): Int = if (a.toLong() + b > Int.MAX_VALUE) Int.MAX_VALUE else a + b
