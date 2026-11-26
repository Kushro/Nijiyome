// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.obi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.direction.LocalDirection
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel

/** Ease of the band lift and the shared band (artifact `--obi-out`). */
val ObiEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private val SweepEase = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/**
 * Background of an obi band that, when [color] changes (status change, episode marked watched), lets the new color in with a sweep:
 * the old color withdraws to the right in 520 ms (artifact `obi-sweep`). CALM fades in 140 ms; PERF and OFF jump to the new color.
 * Only the draw phase reads the animation, so the band never recomposes while it plays.
 */
@Composable
fun Modifier.obiSweepBackground(color: Color): Modifier {
    val level = LocalMotion.current.level
    var shown by remember { mutableStateOf(color) }
    var previous by remember { mutableStateOf(color) }
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(color) {
        if (color == shown) return@LaunchedEffect
        previous = shown
        shown = color
        when (level) {
            MotionLevel.FULL -> {
                sweep.snapTo(0f)
                sweep.animateTo(1f, tween(durationMillis = 520, easing = SweepEase))
            }
            MotionLevel.CALM -> {
                sweep.snapTo(0f)
                sweep.animateTo(1f, tween(durationMillis = 140, easing = LinearEasing))
            }
            MotionLevel.PERF, MotionLevel.OFF -> sweep.snapTo(1f)
        }
    }
    val fade = level == MotionLevel.CALM
    return drawBehind {
        drawRect(shown)
        val p = sweep.value
        if (p < 1f) {
            if (fade) {
                drawRect(previous, alpha = 1f - p)
            } else {
                drawRect(previous, topLeft = Offset(size.width * p, 0f), size = Size(size.width * (1f - p), size.height))
            }
        }
    }
}

/**
 * The novelty band colors (`--obi-new` / `--obi-on-new`): the active palette's under the Obi direction (carried in
 * `tertiaryContainer`), the artifact's yellow fallback when an Obi card is drawn under another direction.
 */
@Composable
fun obiNoveltyBand(): ObiBand =
    if (LocalDirection.current.id == ObiDirection.id) {
        ObiBand(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    } else {
        ObiBand(ObiInk.NEW, ObiInk.ON_NEW)
    }

/** The shelf edge color (`--obi-shelf`). */
@Composable
fun obiShelfColor(): Color = MaterialTheme.colorScheme.surfaceContainerHighest
