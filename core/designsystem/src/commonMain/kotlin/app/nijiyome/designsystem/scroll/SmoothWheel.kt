// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionExtra
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.motion.motionExtraOn

/**
 * [MotionExtra.SMOOTH_WHEEL]: a mouse-wheel notch moves [state] ~100 dp on a spring that keeps its speed when notches chain
 * (calm / performance motion: a 120 ms tween). Desktop only; Android and touchpads keep the built-in behaviour. Off, under
 * [MotionLevel.OFF] or with [enabled] false nothing is intercepted. [orientation] Horizontal answers Shift + wheel only, so a plain
 * wheel over a card row still scrolls the page. Put it outside the scrollable (before `verticalScroll`, or in a lazy list's
 * modifier) so it sees the wheel before the built-in handler does. [reverse] = the list's `reverseLayout` / `reverseScrolling`.
 */
@Composable
fun Modifier.smoothWheel(
    state: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
    reverse: Boolean = false,
    enabled: Boolean = true,
): Modifier {
    val level = LocalMotion.current.level
    if (!enabled || level == MotionLevel.OFF || !motionExtraOn(MotionExtra.SMOOTH_WHEEL)) return this
    return smoothWheelPlatform(state, orientation, reverse, spring = level == MotionLevel.FULL)
}

/** Desktop: the wheel animator; Android: nothing (no mouse wheel worth smoothing, and touch already flings). */
internal expect fun Modifier.smoothWheelPlatform(
    state: ScrollableState,
    orientation: Orientation,
    reverse: Boolean,
    spring: Boolean,
): Modifier

/** true on the desktop app (pointer-first: hover affordances, wheel, scrollbars). */
internal expect val desktopPlatform: Boolean
