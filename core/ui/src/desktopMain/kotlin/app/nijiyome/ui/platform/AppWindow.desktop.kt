// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import javax.swing.SwingUtilities

/** The app's window, for the screens that change it (the player's fullscreen). The desktop app provides it in [LocalAppWindow]. */
class AppWindow(
    private val state: WindowState,
    /** Runs a task on the main thread later (Swing's event queue in the app). */
    private val post: (() -> Unit) -> Unit = { task -> SwingUtilities.invokeLater(task) },
) {
    // What the window was (floating or maximised) before it went fullscreen, to go back to it.
    private var beforeFullscreen: WindowPlacement = WindowPlacement.Floating

    // Player screens open right now: 2 for a moment while going to the next episode, or 0 for a moment when the old one leaves
    // before the new one comes (the last release waits a turn of the event queue for that).
    private var playerScreens = 0

    /** A player screen opened (main thread). */
    fun acquirePlayer() {
        playerScreens++
    }

    /** A player screen closed (main thread); the last one leaves picture in picture and fullscreen (the app is not laid out for them). */
    fun releasePlayer() {
        check(playerScreens > 0) { "releasePlayer without acquirePlayer" }
        if (--playerScreens == 0) {
            post {
                if (playerScreens == 0) {
                    pictureInPicture = false
                    fullscreen = false
                }
            }
        }
    }

    /** While in picture in picture, the fullscreen the player wants once it leaves it. */
    private var pipFullscreen = false

    // What the window was before picture in picture, to go back to it.
    private var beforePip: Pair<DpSize, WindowPlacement>? = null

    /** Fullscreen as the player sees it; while in picture in picture it is only remembered for when the window grows back. */
    var fullscreen: Boolean
        get() = if (pictureInPicture) pipFullscreen else state.placement == WindowPlacement.Fullscreen
        set(value) {
            if (pictureInPicture) {
                pipFullscreen = value
                return
            }
            if (value == fullscreen) return
            if (value) {
                beforeFullscreen = state.placement
                state.placement = WindowPlacement.Fullscreen
            } else {
                state.placement = beforeFullscreen
            }
        }

    /**
     * Hito 6 (Tatakai Next `set_pip`): picture in picture is the window itself, small ([PIP_SIZE], down to [PIP_MIN_SIZE]) and always
     * on top (the app's window reads [pictureInPicture] for both); leaving it gives the window its size, placement and fullscreen back.
     */
    var pictureInPicture: Boolean
        get() = pip
        set(on) {
            if (on == pip) return
            if (on) {
                pipFullscreen = fullscreen
                fullscreen = false
                beforePip = state.size to state.placement
                pip = true
                state.placement = WindowPlacement.Floating
                state.size = PIP_SIZE
            } else {
                pip = false
                beforePip?.let { (size, placement) ->
                    state.size = size
                    state.placement = placement
                }
                beforePip = null
                fullscreen = pipFullscreen
            }
        }

    // Observable, so the window's always-on-top and minimum size follow it.
    private var pip: Boolean by mutableStateOf(false)
}

/** Tatakai Next's picture-in-picture window size and its minimum (`PIP_SIZE`, `PIP_MIN`). */
val PIP_SIZE: DpSize = DpSize(480.dp, 270.dp)
val PIP_MIN_SIZE: DpSize = DpSize(320.dp, 180.dp)

/** Null outside the desktop app's window (tests, previews). */
val LocalAppWindow: ProvidableCompositionLocal<AppWindow?> = staticCompositionLocalOf { null }
