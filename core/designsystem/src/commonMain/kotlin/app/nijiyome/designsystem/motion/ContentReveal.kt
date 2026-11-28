// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// [MotionExtra.CONTENT_REVEAL]: loaded content replaces its placeholder softly. A modifier on the arriving content instead of an
// AnimatedContent: the placeholder and the content never compose together (Details carries the same shared-transition key in
// both), and the tree does not change with the setting.

/**
 * Remembers whether a placeholder was shown at this place, so only content that replaces one is revealed; content that was
 * already there (cached) just appears. Set [placeholderShown] from the placeholder branch.
 */
class RevealGate {
    var placeholderShown: Boolean = false
}

@Composable
fun rememberRevealGate(): RevealGate = remember { RevealGate() }

/**
 * Put on the content that replaced a placeholder ([play] = [RevealGate.placeholderShown]). Full motion: fades in over 300 ms
 * rising 6 dp; calm: a 150 ms fade; performance and off: nothing. Plays once, when the content first composes.
 */
@Composable
fun Modifier.contentReveal(play: Boolean = true): Modifier {
    val level = LocalMotion.current.level
    if (!play || !motionExtraOn(MotionExtra.CONTENT_REVEAL) || level == MotionLevel.OFF || level == MotionLevel.PERF) return this
    val rise = with(LocalDensity.current) { 6.dp.toPx() }
    val full = level == MotionLevel.FULL
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            if (full) tween(300, easing = MotionEasing.Emphasized) else tween(MotionDurations.CALM_FADE, easing = MotionEasing.Linear),
        )
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        if (full) translationY = rise * (1f - p)
    }
}
