// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.patterns

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.ui.platform.windowWidthDp

// Sparkles redesign, lane I1 (interaction patterns). Shared helpers of the patterns package; see docs/redesign/carriles/patrones-api.md.

/** Width (dp) under which the patterns use phone forms: sheets instead of popovers, grids instead of rows. */
const val PATTERNS_COMPACT_WIDTH_DP: Float = 600f

/**
 * True on phone-width windows. Every pattern that has a phone form and a wide form decides with this unless the caller passes the
 * form explicitly.
 *
 * ```kotlin
 * if (isCompactWindow()) AdaptiveSheet(onDismissRequest = close) { panel() } else DropdownMenu(open, close) { panel() }
 * ```
 */
@Composable
fun isCompactWindow(): Boolean = windowWidthDp() < PATTERNS_COMPACT_WIDTH_DP

/** Entrance of a bar or panel that rises from the bottom (selection bar, undo snackbar), by the active motion level. */
@Composable
internal fun riseEnter(): EnterTransition = when (LocalMotion.current.level) {
    MotionLevel.FULL -> fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 4 }
    MotionLevel.CALM -> fadeIn(tween(120))
    MotionLevel.PERF -> fadeIn(tween(90))
    MotionLevel.OFF -> EnterTransition.None
}

@Composable
internal fun riseExit(): ExitTransition = when (LocalMotion.current.level) {
    MotionLevel.FULL -> fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 }
    MotionLevel.CALM -> fadeOut(tween(120))
    MotionLevel.PERF -> fadeOut(tween(90))
    MotionLevel.OFF -> ExitTransition.None
}

/** Entrance of an overlay that covers a region (drop layer): a short scale on FULL, a fade otherwise. */
@Composable
internal fun overlayEnter(): EnterTransition = when (LocalMotion.current.level) {
    MotionLevel.FULL -> fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.98f)
    MotionLevel.CALM, MotionLevel.PERF -> fadeIn(tween(120))
    MotionLevel.OFF -> EnterTransition.None
}

@Composable
internal fun overlayExit(): ExitTransition = when (LocalMotion.current.level) {
    MotionLevel.FULL -> fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.98f)
    MotionLevel.CALM, MotionLevel.PERF -> fadeOut(tween(100))
    MotionLevel.OFF -> ExitTransition.None
}

/** `list` with the element at [from] moved so it ends at index [to] (the index after removal). Out-of-range moves return the list as is. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val out = toMutableList()
    val item = out.removeAt(from)
    out.add(to, item)
    return out
}
