// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppWindowTest {
    @Test
    fun fullscreenGoesBackToWhatTheWindowWas() {
        val state = WindowState(placement = WindowPlacement.Maximized)
        val window = AppWindow(state)
        window.fullscreen = true
        assertEquals(WindowPlacement.Fullscreen, state.placement)
        window.fullscreen = false
        assertEquals(WindowPlacement.Maximized, state.placement)
    }

    @Test
    fun theNextEpisodeKeepsFullscreenAndTheLastPlayerLeavesIt() {
        val queue = ArrayDeque<() -> Unit>()
        val window = AppWindow(WindowState(), post = { queue.addLast(it) })
        fun drain() = generateSequence { queue.removeFirstOrNull() }.forEach { it() }
        window.acquirePlayer()
        window.fullscreen = true
        // next episode, the new screen first
        window.acquirePlayer()
        window.releasePlayer()
        drain()
        assertTrue(window.fullscreen)
        // next episode, the old screen first (0 for a moment)
        window.releasePlayer()
        window.acquirePlayer()
        drain()
        assertTrue(window.fullscreen)
        // leaving the player
        window.releasePlayer()
        drain()
        assertFalse(window.fullscreen)
    }

    @Test
    fun pictureInPictureIsASmallWindowAndGivesTheWindowBack() {
        val state = WindowState(placement = WindowPlacement.Maximized, size = androidx.compose.ui.unit.DpSize(1280.dp, 800.dp))
        val window = AppWindow(state)
        window.fullscreen = true
        window.pictureInPicture = true
        assertEquals(WindowPlacement.Floating, state.placement)
        assertEquals(PIP_SIZE, state.size)
        // the player still sees its fullscreen, applied when the window grows back
        assertTrue(window.fullscreen)
        window.pictureInPicture = false
        assertEquals(WindowPlacement.Fullscreen, state.placement)
        window.fullscreen = false
        assertEquals(WindowPlacement.Maximized, state.placement)
        assertEquals(androidx.compose.ui.unit.DpSize(1280.dp, 800.dp), state.size)
    }

    @Test
    fun leavingThePlayerLeavesPictureInPicture() {
        val queue = ArrayDeque<() -> Unit>()
        val state = WindowState(size = androidx.compose.ui.unit.DpSize(1000.dp, 700.dp))
        val window = AppWindow(state, post = { queue.addLast(it) })
        window.acquirePlayer()
        window.pictureInPicture = true
        window.releasePlayer()
        generateSequence { queue.removeFirstOrNull() }.forEach { it() }
        assertFalse(window.pictureInPicture)
        assertFalse(window.fullscreen)
        assertEquals(androidx.compose.ui.unit.DpSize(1000.dp, 700.dp), state.size)
    }
}
