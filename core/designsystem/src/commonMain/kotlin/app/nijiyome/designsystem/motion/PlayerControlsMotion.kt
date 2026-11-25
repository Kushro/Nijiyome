// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

// Moment "controles" (lab.js): it happens hundreds of times per session, over the video. The less it shows, the better; hiding
// is faster than showing.

@Composable
internal fun PlayerControlsTransitionImpl(
    visible: Boolean,
    edge: ControlsEdge,
    modifier: Modifier,
    hiddenContent: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotion.current
    val level = motion.level
    val variant = motion.variant(Moments.PLAYER_CONTROLS, "fade")
    val density = LocalDensity.current
    val minimal = variant == "min"

    val (enter, exit) = when {
        level == MotionLevel.OFF -> EnterTransition.None to ExitTransition.None
        level == MotionLevel.CALM || (level == MotionLevel.PERF && minimal) ->
            fadeIn(tween(MotionDurations.CALM_QUICK, easing = LinearEasing)) to fadeOut(tween(MotionDurations.CALM_QUICK, easing = LinearEasing))
        level == MotionLevel.PERF && variant == "slide" ->
            fadeIn(tween(MotionDurations.CALM_FADE, easing = MotionEasing.Emphasized)) to
                fadeOut(tween(MotionDurations.CALM_FADE, easing = MotionEasing.ExitExponential))
        // FULL slide: the top bar rises, the bottom one drops and the centre button shrinks, so they say where they come back from.
        variant == "slide" -> {
            val show = tween<Float>(250, easing = MotionEasing.Emphasized)
            val hide = tween<Float>(MotionDurations.STATE, easing = MotionEasing.ExitExponential)
            if (edge == ControlsEdge.CENTER) {
                (scaleIn(show, initialScale = 0.88f) + fadeIn(show)) to (scaleOut(hide, targetScale = 0.88f) + fadeOut(hide))
            } else {
                val shift = with(density) { (if (edge == ControlsEdge.TOP) -12 else 16).dp.roundToPx() }
                (slideInVertically(tween(250, easing = MotionEasing.Emphasized)) { shift } + fadeIn(show)) to
                    (slideOutVertically(tween(MotionDurations.STATE, easing = MotionEasing.ExitExponential)) { shift } + fadeOut(hide))
            }
        }
        // FULL min: 150 ms; the bottom block grows out of the 2 dp line (below).
        minimal ->
            fadeIn(tween(MotionDurations.CALM_FADE, easing = MotionEasing.ExitExponential)) to
                fadeOut(tween(MotionDurations.CALM_FADE, easing = MotionEasing.ExitExponential))
        // Fade, in full and performance motion alike: 200 ms in, 150 ms out.
        else ->
            fadeIn(tween(MotionDurations.STATE, easing = MotionEasing.ExitExponential)) to
                fadeOut(tween(MotionDurations.CALM_FADE, easing = MotionEasing.ExitExponential))
    }
    val growFromLine = minimal && level == MotionLevel.FULL && edge == ControlsEdge.BOTTOM

    val controls: @Composable (Modifier) -> Unit = { m ->
        AnimatedVisibility(visible = visible, modifier = m, enter = enter, exit = exit) {
            if (growFromLine) {
                val grow = transition.animateFloat(
                    transitionSpec = { tween(MotionDurations.CALM_FADE, easing = MotionEasing.ExitExponential) },
                    label = "controlsGrow",
                ) { state -> if (state == EnterExitState.Visible) 1f else 0.1f }
                Box(
                    Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = grow.value
                    },
                ) { content() }
            } else {
                content()
            }
        }
    }
    if (!minimal || hiddenContent == null) {
        controls(modifier)
        return
    }
    // "Mínimo": while the controls are hidden, [hiddenContent] (usually MiniProgressLine) stays; the line is fixed in calm.
    Box(modifier) {
        controls(Modifier)
        AnimatedVisibility(
            visible = !visible,
            enter = if (level == MotionLevel.OFF) EnterTransition.None else fadeIn(tween(MotionDurations.CALM_QUICK, easing = LinearEasing)),
            exit = if (level == MotionLevel.OFF) ExitTransition.None else fadeOut(tween(MotionDurations.CALM_QUICK, easing = LinearEasing)),
        ) { hiddenContent() }
    }
}

/**
 * The 2 dp progress line of the "min" player-controls variant, for `PlayerControlsTransition(hiddenContent = …)`. [progress]
 * is read in the draw phase (0..1). Decorative: the seek bar carries the semantics.
 */
@Composable
fun MiniProgressLine(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.White.copy(alpha = 0.3f),
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .clearAndSetSemantics { }
            .drawBehind {
                drawRect(trackColor)
                drawRect(color, size = Size(size.width * progress().coerceIn(0f, 1f), size.height))
            },
    )
}
