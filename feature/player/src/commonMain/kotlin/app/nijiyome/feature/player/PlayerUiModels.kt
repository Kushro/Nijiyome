// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.AppError
import app.nijiyome.model.Episode
import app.nijiyome.player.SleepOption
import app.nijiyome.player.TimedCue

// Milestone 3: the pure UI models of the player (moved verbatim from PlayerViewModel.kt, which stays in androidMain with the
// engines). [PlayerUiState] and [PlayerNotice] stay there: they carry Android-only types (ExternalLaunch, frame generation).

/** What the screen shows besides the engine's own [PlaybackState]. */
enum class PlayerPhase {
    /** Loading the media / the candidate list. */
    LOADING,

    /** Resolving a source (web stream or `TorrentRepository.play`). */
    RESOLVING,

    /** Engine loaded, no frame yet (or stalled). */
    BUFFERING,
    PLAYING,

    /** Manual mode: "Pick a source for this episode", nothing resolved yet. */
    MANUAL,

    /** Every source failed (or a direct source failed): ErrorState with `failed[]`. */
    ERROR,
    NO_SOURCES,
    REMOTE_UNAVAILABLE,

    /** The media / candidates could not be loaded (retryable). */
    FAILED,

    /** EXTERNAL engine: the informative screen. */
    EXTERNAL,
}

/** Torrent branch facts for the overlay, the seek bar and the "Open in the client" chip. */
data class TorrentUi(
    val torrentId: String?,
    val fileIndex: Int? = null,
    val fileName: String? = null,
    val batch: Boolean = false,
    val waitingMetadata: Boolean = true,
    val downBps: Long = 0,
    val peers: Int = 0,
    /** 0..1 of the selected files (`TorrentStats.progress`). */
    val progress: Double = 0.0,
    /** Downloaded byte ranges of the playing file (`seekMarks`). */
    val marks: List<ClosedFloatingPointRange<Double>> = emptyList(),
)

/** Metered network confirmation (11 §4.1 step 2): X = the source's size when known. */
data class MeteredPrompt(val sizeBytes: Long?, val hasWeb: Boolean)

/**
 * The end-of-episode card. [secondsLeft] null = auto-next is off (checklist player.auto-next.toggle): the card only offers the button and
 * never moves on by itself. [totalSecs] = `AppSettings.autoNextCountdownSecs` (3..30, player.auto-next.countdown).
 */
data class NextEpisodeUi(val episode: Int, val secondsLeft: Int?, val sameTorrent: Boolean, val totalSecs: Int = DEFAULT_COUNTDOWN_SECS)

internal const val DEFAULT_COUNTDOWN_SECS = 10

/** Sleep timer of the "More" sheet: [option] armed, [remainingMs] for the wall-clock options. */
data class SleepUi(val option: SleepOption, val remainingMs: Long?)

/** Episode list over the player (player.jump-to-episode). */
data class EpisodesUi(val loading: Boolean = false, val items: List<Episode> = emptyList(), val error: AppError? = null, val loaded: Boolean = false)

/** Subtitle translation state (player.subs.translate.*): [enabled] = the in-player toggle; [progress] = pre-translation (done, total). */
data class TranslationUi(
    val available: Boolean = false,
    val enabled: Boolean = true,
    val progress: Pair<Int, Int>? = null,
    val error: String? = null,
)

/**
 * Subtitles the screen draws itself (Media3 never renders text, mpv only when translating): [primary] parsed cues of the selected external
 * track or of a loaded file (null = the engine's own cues), [secondary] the second track (Media3), [translated] original -> translated.
 */
data class ScreenSubtitles(
    val primary: List<TimedCue>? = null,
    val secondary: List<TimedCue>? = null,
    val secondaryLabel: String? = null,
    val translated: Map<String, String> = emptyMap(),
)

/** A subtitle file loaded with "Load file…" while the Media3 engine plays (the screen draws it). */
data class CustomSubtitleUi(val name: String, val active: Boolean)

/**
 * BUG-038 / BUG-009 / BUG-058: whether recovering a Link session opens a new one. [alive] = whether the PC still has the session
 * (null: the PC could not be asked). A session the PC closed is always renewed; one it still has only on the user's Retry
 * (reloading a stuck session fails again), never on the automatic path. Needs a session the phone can renew ([canRenew]).
 */
internal fun shouldRenewRemote(alive: Boolean?, canRenew: Boolean, auto: Boolean): Boolean =
    canRenew && (alive == false || (alive == true && !auto))

/**
 * BUG-138: whether start() renews a Link session before loading it: only when the PC said it no longer has it (a hand-off
 * card tapped after the 120 s idle timeout). Alive or unknown (PC unreachable, slow answer) loads it as before, so a network
 * drop still goes through the retries and the "connection lost" screen.
 */
internal fun renewRemoteBeforeLoad(alive: Boolean?, canRenew: Boolean): Boolean = canRenew && alive == false

/** BUG-038 / BUG-026: why a Link (PC) playback stopped, for an error screen in words (no Media3 codes, no "Extensions"). */
enum class RemoteFailure {
    /** The PC did not answer (network cut, PC asleep): playback resumes by itself when the network returns. */
    CONNECTION_LOST,

    /** The PC closed the session and it cannot be recreated here (the PC sent it): send it again from the PC. */
    SESSION_ENDED,

    /** The PC answered with an error for a session it still has. */
    PC_ERROR,

    /** BUG-132: this phone's video decoder does not take the profile's stream (not the PC's fault); Retry uses another profile. */
    PHONE_DECODER,
}
