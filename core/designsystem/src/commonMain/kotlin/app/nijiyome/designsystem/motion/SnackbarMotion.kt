// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Moment "snack" (lab.js): every reversible action leaves a notice with Undo. What matters is not how it enters but that the
// previous one is not lost; the host keeps the queue, this file only moves one notice.

/** A press older than this did not trigger the snackbar that is appearing now. */
private const val ORIGIN_MS = 2_000L

@Composable
internal fun SnackbarTransitionImpl(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val motion = LocalMotion.current
    val level = motion.level
    val variant = motion.variant(Moments.SNACKBAR, "rise")
    val rise = with(LocalDensity.current) { 16.dp.roundToPx() }
    val fromAction = level == MotionLevel.FULL && variant == "origin"

    val (enter, exit) = when {
        level == MotionLevel.OFF -> EnterTransition.None to ExitTransition.None
        level == MotionLevel.CALM -> fadeIn(tween(MotionDurations.CALM_FADE, easing = LinearEasing)) to
            fadeOut(tween(MotionDurations.CALM_FADE, easing = LinearEasing))
        // "origin" in performance motion: a 150 ms fade in place.
        level == MotionLevel.PERF && variant == "origin" -> fadeIn(tween(MotionDurations.CALM_FADE, easing = LinearEasing)) to
            fadeOut(tween(MotionDurations.CALM_FADE, easing = LinearEasing))
        // Performance: it is simply there (no shadowed travel), and gone.
        level == MotionLevel.PERF -> EnterTransition.None to ExitTransition.None
        // The scale from the action is drawn below; the fade carries the presence.
        fromAction -> fadeIn(tween(MotionDurations.CALM_FADE, easing = LinearEasing)) to
            fadeOut(tween(MotionDurations.CALM_FADE, easing = LinearEasing))
        // "rise" and "stack": 16 dp up from below in 220 ms, out in 150 ms.
        else -> (
            slideInVertically(tween(220, easing = MotionEasing.ExitExponential)) { rise } +
                fadeIn(tween(220, easing = MotionEasing.ExitExponential))
            ) to fadeOut(tween(MotionDurations.CALM_FADE, easing = MotionEasing.ExitExponential))
    }

    AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit) {
        if (fromAction) GrowFromAction { content() } else content()
    }
}

/** "Crece desde la acción": scales from 0.4 around the point that was pressed last (the trigger), else from its bottom centre. */
@Composable
private fun GrowFromAction(content: @Composable () -> Unit) {
    val origin = remember { TapTracker.recent(ORIGIN_MS) }
    val bounds = remember { BoundsHolder() }
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(250, easing = MotionEasing.Emphasized)) }
    Box(
        Modifier
            .onGloballyPositioned { bounds.value = it.boundsInRoot() }
            .graphicsLayer {
                val b = bounds.value
                transformOrigin = pivot(origin, b)
                scaleX = scale.value
                scaleY = scale.value
            },
    ) { content() }
}

private fun pivot(origin: Offset?, bounds: Rect?): TransformOrigin {
    if (origin == null || bounds == null || bounds.width <= 0f || bounds.height <= 0f) return TransformOrigin(0.5f, 1f)
    // Fractions outside 0..1 are fine: the trigger is usually above the snackbar.
    return TransformOrigin((origin.x - bounds.left) / bounds.width, (origin.y - bounds.top) / bounds.height)
}

private class BoundsHolder {
    var value: Rect? = null
}

/**
 * "Apilado": put on a snackbar that a newer one pushed behind. Full motion lifts it 8 dp, scales it to 0.96 and dims it to 75 %
 * in 220 ms; calm only dims it; performance and off do it at once (the host should keep only the newest in performance motion,
 * with "and 1 more" opening the history).
 */
@Composable
fun Modifier.snackbarBehind(behind: Boolean): Modifier {
    val level = LocalMotion.current.level
    val spec: FiniteAnimationSpec<Float> = when (level) {
        MotionLevel.FULL -> tween(220, easing = MotionEasing.ExitExponential)
        MotionLevel.CALM -> tween(MotionDurations.CALM_FADE, easing = LinearEasing)
        MotionLevel.PERF, MotionLevel.OFF -> snap()
    }
    val back = animateFloatAsState(if (behind) 1f else 0f, spec, label = "snackbarBehind")
    val lift = with(LocalDensity.current) { 8.dp.toPx() }
    val spatial = level == MotionLevel.FULL
    return this.graphicsLayer {
        val b = back.value
        alpha = 1f - 0.25f * b
        if (spatial) {
            translationY = -lift * b
            val s = 1f - 0.04f * b
            scaleX = s
            scaleY = s
        }
    }
}
