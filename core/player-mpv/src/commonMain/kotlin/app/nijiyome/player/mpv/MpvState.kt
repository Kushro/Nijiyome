// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.AudioOption
import app.nijiyome.player.MPV_OPEN_ERROR_CODE
import app.nijiyome.player.MPV_START_TIMEOUT_CODE
import app.nijiyome.player.PlaybackError
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.SubtitleOption

// Hito 4: the engine-independent part of both libmpv engines (Android MPVLib, desktop MediaMP), moved verbatim from androidMain
// MpvEngine.kt / MpvPlayerSession.kt: mpv's state as the engines collect it, the external-subtitle arguments, the buffered position,
// the error code of a load and the mapping to the shared [PlaybackState].

/** One subtitle / audio track as mpv lists it (`track-list/N/...`). [id] is mpv's `sid` / `aid`. */
data class MpvTrack(
    val id: Int,
    val title: String?,
    val lang: String?,
    val codec: String?,
    val selected: Boolean,
    /** 0.4.0 (sub-translation): `track-list/N/external` - added with `sub-add`, not stored in the media file. */
    val external: Boolean = false,
    /** v1.2.0: `track-list/N/demux-channel-count` for audio tracks, null when unknown. */
    val channels: Int? = null,
)

/** Minimal state the player UI renders for the mpv branch (d may extend it). */
data class MpvState(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val ended: Boolean = false,
    val positionSecs: Double = 0.0,
    val durationSecs: Double = 0.0,
    val error: String? = null,
    // --- worker d extensions ---
    /** Bumped by every [MpvEngine.load]. */
    val loadId: Int = 0,
    /** The current file played at least once (first `playback-restart` after loading). */
    val started: Boolean = false,
    val speed: Double = 1.0,
    /** Timestamp (seconds) where the buffered data ends (`demuxer-cache-time`; 0.4.1: an absolute time, not a duration). */
    val cacheSecs: Double = 0.0,
    val subtitles: List<MpvTrack> = emptyList(),
    val audio: List<MpvTrack> = emptyList(),
    /** Video height of the current file (`height`), 0 = unknown. */
    val videoHeight: Int = 0,
    /** 0.4.0 (worker A): video width of the current file (`width`), 0 = unknown. */
    val videoWidth: Int = 0,
    /** 0.4.0 (worker A): size of the attached surface in pixels (the view the video is fitted into), 0 = none. */
    val surfaceWidth: Int = 0,
    val surfaceHeight: Int = 0,
    // --- parity wave A (worker p) ---
    /** Primary subtitle line on screen right now (`sub-text`), for the screen-drawn translation. */
    val subText: String? = null,
    /** `secondary-sid`, null = off. */
    val secondarySid: Int? = null,
    /** `loop-file=inf`. */
    val loop: Boolean = false,
    /** `sub-delay` in ms. */
    val subDelayMs: Long = 0,
)

/** One external subtitle for `sub-add`: [title] / [lang] as mpv lists it; [select] = the active track. */
data class MpvSubtitle(val url: String, val title: String? = null, val lang: String? = null, val select: Boolean = false)

/** Option values both engines set the same way (Android MpvEngine's companion constants are these). */
internal object MpvEngineDefaults {
    const val DEMUXER_MAX_MB = 200
    const val DEMUXER_BACK_MB = 100
    const val READAHEAD_SECS = 90
    const val DESKTOP_DEMUXER_MAX_MB = 300
    const val DESKTOP_DEMUXER_BACK_MB = 150
    const val DESKTOP_READAHEAD_SECS = 120
    const val NETWORK_TIMEOUT_S = 90
    const val DEFAULT_USER_AGENT = "Nijiyome/0.1"
    const val STABLE_VOLUME_FILTER = "lavfi=[dynaudnorm=f=250:g=15:p=0.9]"
}

/** mpv codec names that libass renders with full styling. */
internal val ASS_CODECS = setOf("ass", "ssa")

/** mpv error text of a load given up by the start watchdog (BUG-059). */
internal const val START_TIMEOUT = "start timeout"

/**
 * [PlaybackError.code] for an mpv error: the start timeout, an error before the first frame of a watched network stream (BUG-080:
 * the view-model moves both to Media3), or -1 (the source fails as usual: torrents, local files, errors after playback started).
 */
internal fun mpvErrorCode(error: String, started: Boolean, watched: Boolean): Int = when {
    error == START_TIMEOUT -> MPV_START_TIMEOUT_CODE
    !started && watched -> MPV_OPEN_ERROR_CODE
    else -> -1
}

/**
 * 0.4.0: a source's external subtitles as `sub-add` arguments: the label as mpv's title (a URL's file name before) and the language
 * as mpv's `lang` (so the track list and `slang` see it), [select] (an index, [PlayableSource.SUBTITLES_OFF] or null) marked.
 */
internal fun mpvSubtitles(tracks: List<app.nijiyome.model.SubtitleTrack>, select: Int?): List<MpvSubtitle> =
    tracks.mapIndexed { i, t ->
        MpvSubtitle(
            url = t.url,
            title = t.label.ifBlank { t.lang }.ifBlank { null },
            lang = t.lang.trim().takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } },
            select = select == i,
        )
    }

/**
 * 0.4.1 (seek bar): `demuxer-cache-time` is the timestamp where the demuxer cache ends (not a duration: that is
 * `demuxer-cache-duration`), so the buffered position is that value itself. Adding the playhead to it drew the buffer at about
 * twice the position. Never behind the playhead (a cache cleared by a seek reports an older end until it refills).
 */
internal fun mpvBufferedMs(positionSecs: Double, cacheEndSecs: Double): Long =
    (maxOf(positionSecs, cacheEndSecs).coerceAtLeast(0.0) * 1000).toLong()

/**
 * Hito 4: the shared [PlaybackState] of an mpv engine's [MpvState] (Android `MpvPlayerSession` and the desktop session map it the same
 * way, so feature/player draws the same controls for both). [loaded] = the session loaded something; [endedHere] = the session ended
 * the load itself (`finishAsEnded`); [watched] = this load is watched by the start timeout ([mpvErrorCode]).
 */
internal fun mpvPlaybackState(prev: PlaybackState, m: MpvState, loaded: Boolean, endedHere: Boolean, watched: Boolean): PlaybackState {
    // 0.4.0 (sub-translation): in-file tracks keep demuxer (container) order in track-list; `sub-add` ones are external.
    var embedded = 0
    val subs = m.subtitles.map { t ->
        SubtitleOption(
            t.id, t.title ?: t.lang ?: "#${t.id}", t.lang, t.selected, ass = t.codec?.lowercase() in ASS_CODECS,
            codec = t.codec, embeddedOrdinal = if (t.external) null else embedded++,
        )
    }
    return prev.copy(
        loadId = m.loadId,
        started = m.started,
        isPlaying = m.playing,
        isBuffering = loaded && !endedHere && (m.buffering && m.error == null),
        ended = endedHere || m.ended,
        positionMs = (m.positionSecs * 1000).toLong(),
        durationMs = (m.durationSecs * 1000).toLong(),
        bufferedMs = mpvBufferedMs(m.positionSecs, m.cacheSecs),
        speed = m.speed.toFloat(),
        videoHeights = emptyList(),
        subtitles = subs,
        subtitlesEnabled = subs.any { it.selected },
        subtitleIsAss = subs.any { it.selected && it.ass },
        audioTracks = m.audio.map { t ->
            AudioOption(
                id = t.id,
                label = t.title ?: t.lang ?: "#${t.id}",
                language = t.lang,
                selected = t.selected,
                codec = t.codec,
                channels = t.channels,
            )
        },
        fullAss = true,
        error = if (endedHere) null else m.error?.let { PlaybackError(mpvErrorCode(it, m.started, watched), it, exhausted = true) },
        subText = m.subText,
        subtitleDelayMs = m.subDelayMs,
        loop = m.loop,
        secondarySubtitleId = m.secondarySid,
    )
}
