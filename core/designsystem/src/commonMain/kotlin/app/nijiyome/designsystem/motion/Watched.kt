// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import kotlinx.coroutines.delay

// Moment "visto" (lab.js): confirms the action. It reads at a glance and never slows down someone marking ten episodes in a row:
// only a flip plays (never the first composition), and unmarking is immediate or shorter.

@Composable
internal fun WatchedTransitionImpl(
    watched: Boolean,
    modifier: Modifier,
    shape: Shape,
    tint: Color,
    content: @Composable (watched: Boolean) -> Unit,
) {
    val motion = LocalMotion.current
    val level = motion.level
    val variant = motion.variant(Moments.MARK_WATCHED, "stamp")
    val band = variant == "band"
    val bandColor = if (tint.isSpecified) tint else MaterialTheme.colorScheme.primaryContainer

    var shown by remember { mutableStateOf(watched) }
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    // Band: how far the tint has swept (0..1) and how strong it is (calm fades it instead of sweeping).
    val sweep = remember { Animatable(if (watched) 1f else 0f) }
    val tintAlpha = remember { Animatable(1f) }

    LaunchedEffect(watched) {
        if (shown == watched) return@LaunchedEffect
        scale.snapTo(1f)
        alpha.snapTo(1f)
        when {
            level == MotionLevel.OFF || level == MotionLevel.PERF -> {
                shown = watched
                sweep.snapTo(if (watched) 1f else 0f)
            }
            level == MotionLevel.CALM -> {
                shown = watched
                if (band) {
                    sweep.snapTo(1f)
                    tintAlpha.snapTo(if (watched) 0f else 1f)
                    tintAlpha.animateTo(if (watched) 1f else 0f, tween(MotionDurations.CALM_QUICK, easing = LinearEasing))
                    if (!watched) sweep.snapTo(0f)
                    tintAlpha.snapTo(1f)
                } else if (watched) {
                    // Stamp: the seal fades in; stations: the station lights up from 30 %.
                    alpha.snapTo(if (variant == "stations") 0.3f else 0f)
                    alpha.animateTo(1f, tween(MotionDurations.CALM_QUICK, easing = LinearEasing))
                }
            }
            // FULL
            variant == "stations" -> if (watched) {
                // The progress line travels for 350 ms, then the station pops in 120 ms.
                delay(MotionDurations.LAYOUT.toLong())
                shown = true
                scale.snapTo(0.6f)
                scale.animateTo(1f, tween(MotionDurations.CALM_QUICK, easing = MotionEasing.ExitExponential))
            } else {
                shown = false
            }
            band -> {
                shown = watched
                tintAlpha.snapTo(1f)
                if (watched) {
                    sweep.animateTo(1f, tween(MotionDurations.LAYOUT, easing = MotionEasing.Emphasized))
                } else {
                    sweep.animateTo(0f, tween(MotionDurations.STATE, easing = MotionEasing.ExitExponential))
                }
            }
            else -> {
                shown = watched
                if (watched) {
                    // The seal drops: 1.6 to 1 on a short spring, with a slight turn that settles with it.
                    scale.snapTo(1.6f)
                    scale.animateTo(1f, MotionSprings.stamp())
                }
            }
        }
    }

    val stamp = variant == "stamp"
    var layered = modifier
    if (band) {
        layered = layered.drawWithCache {
            val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            onDrawBehind {
                val w = sweep.value
                if (w > 0f) {
                    clipPath(outline) {
                        drawRect(bandColor, size = Size(size.width * w, size.height), alpha = tintAlpha.value)
                    }
                }
            }
        }
    }
    Box(layered) {
        Box(
            Modifier.graphicsLayer {
                val s = scale.value
                scaleX = s
                scaleY = s
                if (stamp) rotationZ = -(s - 1f) * 14f
                this.alpha = alpha.value
            },
        ) { content(shown) }
    }
}

/**
 * A progress fraction for a watched line (progress track, station line) that follows [target]: in full motion it travels for
 * 350 ms on the exit curve (the "stations" fill); calm, performance and off jump to the value. The first value never animates.
 * Read the returned lambda in the draw phase (`drawBehind`, `graphicsLayer`) so the travel does not recompose.
 */
@Composable
fun animateWatchedProgress(target: Float): () -> Float {
    val level = LocalMotion.current.level
    val progress = remember { Animatable(target) }
    LaunchedEffect(target) {
        if (progress.value == target) return@LaunchedEffect
        if (level == MotionLevel.FULL) {
            progress.animateTo(target, tween(MotionDurations.LAYOUT, easing = MotionEasing.ExitExponential))
        } else {
            progress.snapTo(target)
        }
    }
    return remember(progress) { { progress.value } }
}
