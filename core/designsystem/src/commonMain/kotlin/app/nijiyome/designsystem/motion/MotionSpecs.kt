// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Shared motion specs of the Sparkles artifact (lab.js "Presupuestos", core.css --nj-ease / --nj-dur-*). Any lane may use them;
// docs/redesign/carriles/movimiento-api.md lists every value.

/** Curves of the artifact. */
object MotionEasing {
    /** "Salida exponencial", `cubic-bezier(.2,0,0,1)` (`--nj-ease`): almost everything (states, menus, notices, exits). */
    val ExitExponential: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** "Énfasis", `cubic-bezier(.05,.7,.1,1)` (`--nj-ease-emph`): entrances that travel far (card to details, reveals). */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Progress that tracks time (shimmer pass, snackbar life). */
    val Linear: Easing = LinearEasing
}

/** Duration budgets in milliseconds (`--nj-dur-1..4`) and the calm limits. */
object MotionDurations {
    /** Immediate answer to a press; content appearing in performance mode. */
    const val INSTANT = 100

    /** A control changing state: player controls, snackbar, drop after reorder. */
    const val STATE = 200

    /** A layout, sheet or overlay change: shared axis, mark watched, row reveal. */
    const val LAYOUT = 350

    /** The one author moment of a screen: card to details, paper unfold. */
    const val AUTHOR = 500

    /** Longest fade allowed in [MotionLevel.CALM]. */
    const val CALM_FADE = 150

    /** Short calm fade used by confirmations (mark watched, status change, controls). */
    const val CALM_QUICK = 120

    /** "Salir dura el 70 % de entrar." */
    fun exitOf(enterMs: Int): Int = enterMs * 7 / 10
}

/** Springs of the artifact (damping ratio, stiffness). */
object MotionSprings {
    /** Interface spring, ζ 1, stiffness 400: sheets, indicators, drop after drag. */
    fun <T> ui(visibilityThreshold: T? = null): SpringSpec<T> = spring(1f, 400f, visibilityThreshold)

    /** Stamp, ζ 0.6, stiffness 1500: mark watched with a seal. */
    fun <T> stamp(visibilityThreshold: T? = null): SpringSpec<T> = spring(0.6f, 1500f, visibilityThreshold)

    /** Scroll reaction, ζ 0.5, stiffness 200: strip tilt. */
    fun <T> scrollTilt(visibilityThreshold: T? = null): SpringSpec<T> = spring(0.5f, 200f, visibilityThreshold)

    /** Tanzaku, ζ 0.3, stiffness 50: hanging strip sway (full motion only). */
    fun <T> tanzaku(visibilityThreshold: T? = null): SpringSpec<T> = spring(0.3f, 50f, visibilityThreshold)
}

/**
 * A tween for this level: FULL as given; CALM a fade-length tween (at most [MotionDurations.CALM_FADE], no delay); PERF at most
 * [MotionDurations.LAYOUT] and no delay (one thing at a time); OFF a snap. Use it for opacity / color; travel should check
 * [MotionChoices.spatial] first.
 */
fun <T> MotionLevel.tweenFor(
    durationMs: Int = MotionDurations.STATE,
    easing: Easing = MotionEasing.ExitExponential,
    delayMs: Int = 0,
): FiniteAnimationSpec<T> = when (this) {
    MotionLevel.FULL -> tween(durationMs, delayMs, easing)
    MotionLevel.CALM -> tween(minOf(durationMs, MotionDurations.CALM_FADE), 0, LinearEasing)
    MotionLevel.PERF -> tween(minOf(durationMs, MotionDurations.LAYOUT), 0, easing)
    MotionLevel.OFF -> snap()
}

/** [MotionLevel.tweenFor] for the current `LocalMotion` level. */
@Composable
@ReadOnlyComposable
fun <T> motionTween(
    durationMs: Int = MotionDurations.STATE,
    easing: Easing = MotionEasing.ExitExponential,
    delayMs: Int = 0,
): FiniteAnimationSpec<T> = LocalMotion.current.level.tweenFor(durationMs, easing, delayMs)

/** Plain fade-in of the current level (200 ms, calm 150 ms, off none). For things that simply appear. */
@Composable
@ReadOnlyComposable
fun motionFadeIn(durationMs: Int = MotionDurations.STATE): EnterTransition {
    val level = LocalMotion.current.level
    return if (level == MotionLevel.OFF) EnterTransition.None else fadeIn(level.tweenFor(durationMs))
}

/** Plain fade-out of the current level, 70 % of [enterMs]. */
@Composable
@ReadOnlyComposable
fun motionFadeOut(enterMs: Int = MotionDurations.STATE): ExitTransition {
    val level = LocalMotion.current.level
    return if (level == MotionLevel.OFF) ExitTransition.None else fadeOut(level.tweenFor(MotionDurations.exitOf(enterMs)))
}

/**
 * v1.3.5 [MotionExtra.TAB_SLIDE]: the transform of tab content, an `AnimatedContent` keyed by the tab. FULL: shared axis X, the new
 * tab slides 28 dp in from the side it lies on while the old one fades out (out 90 ms, in 250 ms, emphasized); CALM and PERF: a
 * 150 ms fade; OFF or the extra switched off: an instant swap. Read it in composition, then pick the direction in the spec:
 *
 * ```kotlin
 * val axis = motionSharedAxis()
 * AnimatedContent(tab, transitionSpec = { axis(targetState.ordinal > initialState.ordinal) }) { t -> TabBody(t) }
 * ```
 *
 * No size animation: tab bodies fill their pane, and a growing container would relayout the screen on every frame.
 */
@Composable
fun motionSharedAxis(): (forward: Boolean) -> ContentTransform {
    val level = if (motionExtraOn(MotionExtra.TAB_SLIDE)) LocalMotion.current.level else MotionLevel.OFF
    val slidePx = with(LocalDensity.current) { 28.dp.roundToPx() }
    return remember(level, slidePx) { { forward -> sharedAxisTransform(level, forward, slidePx) } }
}

private fun sharedAxisTransform(level: MotionLevel, forward: Boolean, slidePx: Int): ContentTransform {
    val dir = if (forward) 1 else -1
    return when (level) {
        MotionLevel.FULL -> {
            // the old tab is gone (90 ms) before the new one is mostly in, so the two never read as one blurred page
            val enter = fadeIn(tween(160, 90, MotionEasing.ExitExponential)) +
                slideInHorizontally(tween(250, 0, MotionEasing.Emphasized)) { dir * slidePx }
            val exit = fadeOut(tween(90, 0, MotionEasing.ExitExponential)) +
                slideOutHorizontally(tween(250, 0, MotionEasing.Emphasized)) { -dir * slidePx }
            ContentTransform(enter, exit, sizeTransform = null)
        }
        MotionLevel.CALM, MotionLevel.PERF ->
            ContentTransform(fadeIn(tween(MotionDurations.CALM_FADE)), fadeOut(tween(90)), sizeTransform = null)
        MotionLevel.OFF -> ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    }
}

/** Moment and variant ids, mirrored from `MotionMoments` in `:core:model` (this module cannot see the model). */
internal object Moments {
    const val CARD_TO_DETAILS = "ficha"
    const val CARD_SWAY = "tanzaku"
    const val HOME_ENTRANCE = "inicio"
    const val MARK_WATCHED = "visto"
    const val PLAYER_CONTROLS = "controles"
    const val LOADING = "carga"
    const val STATUS_CHANGE = "estado"
    const val SNACKBAR = "snack"
}

/** Linear interpolation used by the draw-phase animations. */
internal fun lerpF(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
