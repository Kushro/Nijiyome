// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.nijiyome.model.SettingsSection
import app.nijiyome.player.PlayerSession

// Milestone 4: what the shared player screen (PlayerBody / PlayerContent and their controls, sheets and overlays) needs from the
// platform. Android: the code the screen ran before the move, unchanged; desktop: see the PENDIENTE-ESCRITORIO notes.

/** The video of the running [session]. Android: Media3's `ContentFrame` or the libmpv surface (with the phone frame generation). */
@Composable
internal expect fun PlayerVideoSurface(session: PlayerSession, modifier: Modifier)

/**
 * The pointer gestures over the video. Android: tap toggles the controls, double tap seeks, drags scrub / change the brightness and
 * the system volume, a long press plays at 2x. Desktop: moving the mouse shows the controls, click plays / pauses, double click
 * toggles fullscreen, the wheel seeks, and the cursor hides with the controls ([controlsShown]).
 */
@Composable
internal expect fun PlayerGestureLayer(
    session: PlayerSession,
    onToggleControls: () -> Unit,
    onHud: (Hud) -> Unit,
    controlsShown: Boolean,
    onPointerMoved: () -> Unit,
    onToggleFullscreen: () -> Unit,
)

/** Whether a new player screen starts in fullscreen. Android: yes (immersive + landscape). Desktop: what the window is now. */
@Composable
internal expect fun initialFullscreen(): Boolean

/**
 * Captures the window when the first player screen opens and restores it when the last one closes (called from PlayerBody, which
 * outlives the content while the next episode resolves). Android: orientation, system bars, brightness, keep-awake.
 */
@Composable
internal expect fun PlayerWindowScope()

/**
 * What the running content does with the window and the process: Android = keep-awake while playing, immersive + landscape for
 * [fullscreen], the background playback service, picture in picture (reported through [onPipChange]) and pause on stop.
 */
@Composable
internal expect fun PlayerContentEffects(
    session: PlayerSession,
    isPlaying: Boolean,
    fullscreen: Boolean,
    backgroundPlayback: Boolean,
    /** Read when the app stops (the PiP state of that moment, not of the last composition). */
    isInPip: () -> Boolean,
    onPipChange: (Boolean) -> Unit,
)

/** Which platform controls the screen offers (a control whose flag is false is not shown). */
internal class PlayerCapabilities(
    /** The picture-in-picture button. */
    val pictureInPicture: Boolean,
    /** The fullscreen button (Android: immersive + landscape). */
    val fullscreenToggle: Boolean,
    /** Esc leaves fullscreen before anything else (desktop; on Android Esc is the system back). */
    val escapeLeavesFullscreen: Boolean,
    /** The screen lock (a touch screen locked while watching). */
    val screenLock: Boolean,
    /** "Captura" (top bar and its menu). */
    val screenshot: Boolean,
    /** "Cargar archivo…" in the subtitles sheet. */
    val subtitleFile: Boolean,
    /** Playback that goes on with media controls after leaving the app (Settings › Reproductor). */
    val backgroundPlayback: Boolean,
    /** Touch first (Android: "tap an action"); otherwise a pointer ("click an action"). */
    val touchFirst: Boolean,
    // Hito 6: the desktop parity with Tatakai Next. The defaults are Android's, which does not pass them.
    /** The step of the seek keys and of the ± buttons. */
    val seekStepMs: Long = SEEK_STEP_MS,
    /** The playback speeds of the Speed menu and of the speed keys. */
    val speeds: List<Float> = PHONE_SPEEDS,
    /** A mute button and a volume slider in the controls (the engine's own volume; Android uses the system volume). */
    val volumeControls: Boolean = false,
    /** The Quality menu lists the qualities of the episode's sources and switches to one of them (Android: the stream's heights). */
    val sourceQualities: Boolean = false,
    /** The desktop keys: the extra actions of [app.nijiyome.player.PlayerKeymap.DESKTOP_EXTRAS], Ctrl+1..6 and Esc leaving the player. */
    val desktopShortcuts: Boolean = false,
)

/** Android's playback speeds. */
internal val PHONE_SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Tatakai Next's desktop speeds (with 1.75x). */
internal val DESKTOP_SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Tatakai Next's desktop seek step (`SEEK_STEP_SECS`). */
internal const val DESKTOP_SEEK_STEP_MS = 5_000L

internal expect val playerCapabilities: PlayerCapabilities

/** BUG-061: keyboard shortcuts only mean something with a hardware keyboard. Android: `Configuration.keyboard`. */
@Composable
internal expect fun hasHardwareKeyboard(): Boolean

/** The volume steps of the keyboard shortcuts (M / ↑ / ↓). */
internal enum class VolumeStep { TOGGLE_MUTE, RAISE, LOWER }

/** What the running content asks of the platform. Android: the activity and the system services. */
internal interface PlayerPlatformActions {
    fun enterPictureInPicture()

    /** Moves the volume; the HUD to show for it, or null when the platform shows its own (Android's system volume panel). */
    fun adjustVolume(session: PlayerSession, step: VolumeStep): Hud?

    /** Saves the current frame; the shown path of the saved file, or null when it failed. */
    suspend fun screenshot(session: PlayerSession, title: String, episode: Int?): String?
}

@Composable
internal expect fun rememberPlayerPlatformActions(): PlayerPlatformActions

/** "Cargar archivo…": returns the function that opens the platform's picker for a subtitle file. */
@Composable
internal expect fun rememberSubtitleFilePicker(onPicked: (SubtitleDocument) -> Unit): () -> Unit

/** Whether [this] is the Media3 engine, whose subtitles the screen draws itself. */
internal expect val PlayerSession.isMedia3: Boolean

/** The non-blank texts of the Media3 engine's current cues ([session] is Media3, see [isMedia3]). */
@Composable
internal expect fun media3CueTexts(session: PlayerSession): List<String>

/**
 * The phone's frame generation of the running session as the shared screen shows it (Android: `:core:framegen` on libmpv, its texts
 * are that module's Android resources). null where there is none.
 */
internal interface PhoneFrameGen {
    /** The multiplier while it runs (the status chip), else null. */
    val activeMultiplier: Int?

    /** On but unavailable for a reason other than a suspension (the chip warns). */
    val problem: Boolean

    /** The "Generación de cuadros" card of the "Mejoras" panel. */
    @Composable
    fun Section(onOpenSettings: () -> Unit)

    /** Its line of the statistics overlay, drawn with [line]. */
    @Composable
    fun StatsLine(line: @Composable (text: String, color: Color?) -> Unit)
}

/** Follows the session's frame generation (and posts a notice when it fails mid-video). */
@Composable
internal expect fun rememberPhoneFrameGen(vm: PlayerViewModel, session: PlayerSession): PhoneFrameGen?

/** The " <why>" text of a phone frame generation unavailability named by [detail] (its reason's name), null when it names none. */
@Composable
internal expect fun phoneFrameGenReason(detail: String): PhoneFrameGenReason?

internal class PhoneFrameGenReason(val text: String, val missingShaders: Boolean)

/** The reason line of a [PlayerNotice.FrameGen] notice. */
@Composable
internal expect fun frameGenNoticeReason(notice: PlayerNotice.FrameGen): String

/**
 * The EXTERNAL engine branch of the screen. Android: launch the other app, then the informative screen. Desktop: the card shown while
 * mpv plays in its own window ([onOpenPicker] opens the source list there; Android does not use it).
 */
@Composable
internal expect fun ExternalBranch(
    vm: PlayerViewModel,
    state: PlayerUiState,
    title: String,
    onBack: () -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenPicker: () -> Unit,
)
