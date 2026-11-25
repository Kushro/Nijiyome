// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer

// Moment "estado" (lab.js): the status changes the title's colour across the app; the motion says what changed and where to.
// The old and the new value are drawn on top of each other for the length of the change; only layers move.

/**
 * The spec for anything a status change animates by itself (a picker's indicator offset, a chip colour): "ind" 250 ms and
 * "ribbon" 300 ms on the emphasized curve, "odo" 180 ms on the exit curve; calm 120 ms; performance and off jump.
 */
@Composable
@ReadOnlyComposable
fun <T> statusChangeSpec(): FiniteAnimationSpec<T> {
    val motion = LocalMotion.current
    return when (motion.level) {
        MotionLevel.FULL -> when (motion.variant(Moments.STATUS_CHANGE, "ind")) {
            "ribbon" -> tween(300, easing = MotionEasing.Emphasized)
            "odo" -> tween(180, easing = MotionEasing.ExitExponential)
            else -> tween(250, easing = MotionEasing.Emphasized)
        }
        MotionLevel.CALM -> tween(MotionDurations.CALM_QUICK, easing = LinearEasing)
        MotionLevel.PERF, MotionLevel.OFF -> snap()
    }
}

@Composable
internal fun <T> StatusChangeTransitionImpl(state: T, modifier: Modifier, content: @Composable (T) -> Unit) {
    val motion = LocalMotion.current
    val level = motion.level
    val variant = if (level == MotionLevel.CALM) "fade" else motion.variant(Moments.STATUS_CHANGE, "ind")
    val animates = level == MotionLevel.FULL || level == MotionLevel.CALM

    // The value whose change has finished. While [state] differs from it, both are drawn.
    var settled by remember { mutableStateOf(state) }
    // A fresh 0 for every new value, so the first frame of a change is already its start.
    val progress = remember(state) { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == settled) return@LaunchedEffect
        if (animates) {
            val spec: FiniteAnimationSpec<Float> = when (variant) {
                "fade" -> tween(MotionDurations.CALM_QUICK, easing = LinearEasing)
                "ribbon" -> tween(300, easing = MotionEasing.Emphasized)
                "odo" -> tween(180, easing = MotionEasing.ExitExponential)
                else -> tween(250, easing = MotionEasing.Emphasized)
            }
            progress.animateTo(1f, spec)
        }
        settled = state
    }
    val outgoing = settled
    val changing = animates && state != outgoing

    Box(if (changing && variant == "odo") modifier.clipToBounds() else modifier) {
        if (changing) {
            key(outgoing) {
                Box(
                    Modifier.graphicsLayer {
                        val p = progress.value
                        when (variant) {
                            // The ribbon of the new status sweeps over the old one, which stays put.
                            "ribbon" -> Unit
                            "odo" -> {
                                translationY = -p * size.height
                                alpha = 1f - p
                            }
                            else -> {
                                alpha = 1f - p
                            }
                        }
                    },
                ) { content(outgoing) }
            }
        }
        key(state) {
            val layer = if (!changing) {
                Modifier
            } else if (variant == "ribbon") {
                Modifier.drawWithContent {
                    val p = progress.value
                    if (p >= 1f) drawContent() else clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
                }
            } else {
                Modifier.graphicsLayer {
                    val p = progress.value
                    alpha = p
                    // The name rolls up like a counter.
                    if (variant == "odo") translationY = (1f - p) * size.height
                }
            }
            Box(layer) { content(state) }
        }
    }
}
