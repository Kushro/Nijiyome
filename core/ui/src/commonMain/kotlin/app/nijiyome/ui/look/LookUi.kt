// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.look

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.motion.tweenFor
import app.nijiyome.designsystem.theme.LocalReduceMotion
import app.nijiyome.model.Density
import app.nijiyome.model.LookPrefs
import app.nijiyome.model.Motion

// 2026-11 UI refresh: the user's Diseño y estilo choices (AppSettings.look), provided once by the app shell (NijiyomeApp) like
// LocalTitleLanguage, so shared components (MediaCard, episode rows, the hero) follow them without extra parameters.

val LocalLook: ProvidableCompositionLocal<LookPrefs> = staticCompositionLocalOf { LookPrefs() }

/**
 * Effective motion level of the older Diseño y estilo switch, folded into the Sparkles one ([LocalMotion]) so the two never disagree:
 * [Motion.OFF] when the `motion` pref is off, reduce motion is on (setting or system, folded by LocalReduceMotion) or the level is
 * OFF; [Motion.SUBTLE] when the pref asks for it or the level is CALM / PERF (no bounce, scale or slide there); else the pref.
 */
@Composable
fun lookMotion(): Motion {
    val level = LocalMotion.current.level
    val pref = LocalLook.current.motion
    return when {
        LocalReduceMotion.current || level == MotionLevel.OFF || pref == Motion.OFF -> Motion.OFF
        pref == Motion.SUBTLE || level != MotionLevel.FULL -> Motion.SUBTLE
        else -> pref
    }
}

/** True when spatial motion (scale, slides, bounces) is allowed: the expressive pref at the FULL level only. */
@Composable
fun spatialMotion(): Boolean = lookMotion() == Motion.EXPRESSIVE

/**
 * Opacity / color animation spec of the current level ([tweenFor]: CALM caps it at 150 ms, PERF keeps it, OFF snaps). The old
 * SUBTLE pref behaves as CALM.
 */
@Composable
fun <T> lookFadeSpec(durationMs: Int = 180): FiniteAnimationSpec<T> {
    val level = when (lookMotion()) {
        Motion.OFF -> MotionLevel.OFF
        Motion.SUBTLE -> LocalMotion.current.level.takeIf { it == MotionLevel.PERF } ?: MotionLevel.CALM
        Motion.EXPRESSIVE -> LocalMotion.current.level
    }
    return level.tweenFor(durationMs)
}

/** Horizontal gap between cards in rows (compact density packs them tighter). */
@Composable
fun rowSpacing(): Dp = if (LocalLook.current.density == Density.COMPACT) 8.dp else 12.dp

/** Poster card width in rows (0.3.2: Tamaño de tarjetas small / medium; the wide sizes use [CardSizing.wideCardDp]). */
@Composable
fun cardWidth(): Dp = LocalLook.current.let { CardSizing.posterDp(it.cardSize, it.density == Density.COMPACT).dp }

/**
 * Press feedback: scales the node to 0.96 with a spring while [interactionSource] is pressed (expressive pref at the FULL level only,
 * see [spatialMotion]; graphicsLayer scale, no relayout). Subtle, Calmo, Rendimiento and off leave it still.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val enabled = spatialMotion()
    val scale = animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = if (enabled) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "pressScale",
    )
    // Read in the layer block: the animation only re-draws, it never recomposes the card.
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
