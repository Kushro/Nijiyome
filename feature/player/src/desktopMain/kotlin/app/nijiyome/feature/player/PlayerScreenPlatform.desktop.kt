// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import app.nijiyome.model.SettingsSection
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.mpv.MpvVideoSurface
import app.nijiyome.player.mpv.external.ExternalMpvPlayerSession
import app.nijiyome.ui.platform.KeepScreenOn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import app.nijiyome.player.screenshotFileName
import app.nijiyome.ui.platform.AppWindow
import app.nijiyome.ui.platform.LocalAppWindow
import app.nijiyome.ui.platform.rememberFilePicker
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File
import app.nijiyome.ui.platform.DesktopPictures

// Milestone 4: the desktop side of PlayerScreenPlatform.kt. libmpv is the only engine; what the window and the system do not offer yet
// carries a pending note.

@Composable
internal actual fun PlayerVideoSurface(session: PlayerSession, modifier: Modifier) {
    // The libmpv frame, drawn by Compose (MediaMP); it draws nothing for a session of another engine, so the box stays black.
    Box(modifier.background(Color.Black)) { MpvVideoSurface(session, Modifier.fillMaxSize()) }
}

/**
 * Moving the mouse shows the controls and the cursor (both hide together), click plays / pauses, double click toggles fullscreen, the
 * mouse wheel seeks ±5 s. No brightness or volume gestures.
 */
@Composable
internal actual fun PlayerGestureLayer(
    session: PlayerSession,
    onToggleControls: () -> Unit,
    onHud: (Hud) -> Unit,
    controlsShown: Boolean,
    onPointerMoved: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val moved by rememberUpdatedState(onPointerMoved)
    val toggleFullscreen by rememberUpdatedState(onToggleFullscreen)
    val hud by rememberUpdatedState(onHud)
    Box(
        Modifier
            .fillMaxSize()
            .pointerHoverIcon(if (controlsShown) PointerIcon.Default else hiddenPointer, overrideDescendants = true)
            .pointerInput(session) {
                detectTapGestures(
                    onTap = { session.togglePlay() },
                    onDoubleTap = { toggleFullscreen() },
                )
            }
            .pointerInput(session) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Move) {
                            moved()
                            continue
                        }
                        if (event.type != PointerEventType.Scroll) continue
                        val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                        if (dy == 0.0) continue
                        // wheel up (negative delta) goes forward
                        val forward = dy < 0
                        session.seekBy(if (forward) WHEEL_SEEK_MS else -WHEEL_SEEK_MS)
                        hud(Hud(Hud.Kind.Seek, seconds = ((if (forward) WHEEL_SEEK_MS else -WHEEL_SEEK_MS) / 1000).toInt()))
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    )
}

/** A blank cursor for the video while the controls are hidden (the normal one where AWT cannot make a custom cursor). */
private val hiddenPointer: PointerIcon by lazy {
    runCatching {
        val blank = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(blank, Point(0, 0), "nijiyome-hidden"))
    }.getOrDefault(PointerIcon.Default)
}

/** A new player screen keeps the window as it is (the next episode stays fullscreen). */
@Composable
internal actual fun initialFullscreen(): Boolean = LocalAppWindow.current?.fullscreen ?: false

@Composable
internal actual fun PlayerWindowScope() {
    // Leaving the last player screen leaves fullscreen too; going to the next episode keeps it (see AppWindow.releasePlayer).
    val window = LocalAppWindow.current
    DisposableEffect(window) {
        window?.acquirePlayer()
        onDispose {
            window?.releasePlayer()
            // Leaving the player: nothing is playing any more (Discord goes back to browsing).
            app.nijiyome.model.NowPlaying.clear()
        }
    }
}

@Composable
internal actual fun PlayerContentEffects(
    session: PlayerSession,
    isPlaying: Boolean,
    fullscreen: Boolean,
    backgroundPlayback: Boolean,
    isInPip: () -> Boolean,
    onPipChange: (Boolean) -> Unit,
) {
    // Keep-awake while the video plays (:core:ui's helper; what it still lacks on desktop is marked there). Picture in picture is the
    // window itself (DesktopPlayerActions); no background service: the window keeps playing while minimised. [fullscreen] is the
    // window's.
    if (isPlaying) KeepScreenOn()
    val window = LocalAppWindow.current
    LaunchedEffect(window, fullscreen) { window?.fullscreen = fullscreen }
    // Position and pause for Discord Rich Presence: on every play / pause and every 15 s (a seek shows up at the next tick).
    LaunchedEffect(session, isPlaying) {
        while (true) {
            val ps = session.state.value
            app.nijiyome.model.NowPlaying.update { now ->
                now?.copy(positionMs = ps.positionMs, durationMs = ps.durationMs, paused = !isPlaying)
            }
            kotlinx.coroutines.delay(15_000)
        }
    }
}

// No touch lock; fullscreen and picture in picture are the window's, screenshots go to the pictures folder. Hito 6: the rest is
// Tatakai Next's desktop player (5 s seeks, 1.75x, volume in the controls, the sources' qualities, its extra keys).
internal actual val playerCapabilities: PlayerCapabilities = PlayerCapabilities(
    pictureInPicture = true,
    fullscreenToggle = true,
    escapeLeavesFullscreen = true,
    screenLock = false,
    screenshot = true,
    // The desktop file chooser; mpv adds and selects the file (`sub-add`).
    subtitleFile = true,
    // A desktop window keeps playing when minimized; there is no "leave the app" to go on after.
    backgroundPlayback = false,
    touchFirst = false,
    seekStepMs = DESKTOP_SEEK_STEP_MS,
    speeds = DESKTOP_SPEEDS,
    volumeControls = true,
    sourceQualities = true,
    desktopShortcuts = true,
)

/** A computer always has a keyboard. */
@Composable
internal actual fun hasHardwareKeyboard(): Boolean = true

private class DesktopPlayerActions(private val window: AppWindow?) : PlayerPlatformActions {
    // Picture in picture is the app's window, small and always on top; the button and P toggle it (Tatakai Next).
    override fun enterPictureInPicture() {
        window?.let { it.pictureInPicture = !it.pictureInPicture }
    }

    // The player's own volume (libmpv), shown in the HUD; the system volume is not touched.
    override fun adjustVolume(session: PlayerSession, step: VolumeStep): Hud? {
        val volume = when (step) {
            VolumeStep.TOGGLE_MUTE -> session.toggleMute()
            VolumeStep.RAISE -> session.changeVolume(VOLUME_STEP_PERCENT)
            VolumeStep.LOWER -> session.changeVolume(-VOLUME_STEP_PERCENT)
        } ?: return null
        return Hud(Hud.Kind.Volume, percent = volume.percent, muted = volume.muted)
    }

    // mpv writes the PNG itself (`screenshot-to-file`) into <pictures>/Nijiyome. Both engines wait for it (up to a few seconds), so
    // the wait runs off the Swing EDT.
    override suspend fun screenshot(session: PlayerSession, title: String, episode: Int?): String? = withContext(Dispatchers.IO) {
        val file = File(File(DesktopPictures.dir(), SCREENSHOT_ALBUM), screenshotFileName(title, episode))
        if (!runCatching { session.screenshotToFile(file.absolutePath) }.getOrDefault(false)) return@withContext null
        DesktopPictures.shown(file)
    }
}

@Composable
internal actual fun rememberPlayerPlatformActions(): PlayerPlatformActions {
    val window = LocalAppWindow.current
    return remember(window) { DesktopPlayerActions(window) }
}

/** The desktop file chooser, for one subtitle file (SRT, VTT, ASS / SSA). */
@Composable
internal actual fun rememberSubtitleFilePicker(onPicked: (SubtitleDocument) -> Unit): () -> Unit =
    rememberFilePicker(SUBTITLE_MIME_TYPES, multiple = false) { paths -> paths.firstOrNull()?.let { onPicked(SubtitleFile(File(it))) } }

/** No Media3 on desktop. */
internal actual val PlayerSession.isMedia3: Boolean get() = false

@Composable
internal actual fun media3CueTexts(session: PlayerSession): List<String> = emptyList()

/** The phone frame generation does not exist on desktop. */
@Composable
internal actual fun rememberPhoneFrameGen(vm: PlayerViewModel, session: PlayerSession): PhoneFrameGen? = null

@Composable
internal actual fun phoneFrameGenReason(detail: String): PhoneFrameGenReason? = null

@Composable
internal actual fun frameGenNoticeReason(notice: PlayerNotice.FrameGen): String = ""

/** Hito 4, paso D: mpv plays in its own window ([ExternalMpvPlayerSession]); the screen shows its card ([ExternalMpvBranch]). */
@Composable
internal actual fun ExternalBranch(
    vm: PlayerViewModel,
    state: PlayerUiState,
    title: String,
    onBack: () -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val session = state.session as? ExternalMpvPlayerSession ?: return
    ExternalMpvBranch(vm, state, session, title, onBack, onOpenSettings, onOpenPicker)
}

private val SUBTITLE_MIME_TYPES = listOf("text/plain")
private const val WHEEL_SEEK_MS = 5_000L
private const val VOLUME_STEP_PERCENT = 5
private const val SCREENSHOT_ALBUM = "Nijiyome"
