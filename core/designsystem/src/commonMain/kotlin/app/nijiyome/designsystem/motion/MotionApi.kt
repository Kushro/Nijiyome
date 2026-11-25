// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

// Sparkles redesign contract (docs/redesign/CONTRATO-COMPOSE.md). Signatures written by the integrator and FROZEN for callers;
// bodies are pass-through STUBS that lane M1 replaces (M1 owns this package and may add parameters with defaults, never remove or
// rename). Screens call these functions and never read which variant is selected.

/** Effective motion level, after Reducir movimiento, the system setting and performance mode are folded in. */
enum class MotionLevel {
    /** Everything. */
    FULL,

    /** Fades of 150 ms or less; no travel, sway or parallax. */
    CALM,

    /** No blur, large shadows, animated textures or idle loops; one animation at a time. */
    PERF,

    /** Nothing moves (Animaciones desactivadas). */
    OFF,
}

/** The user's motion choices: the level and the variant picked for each moment (ids of `MotionMoments` in `:core:model`). */
@Immutable
data class MotionChoices(val level: MotionLevel = MotionLevel.FULL, val variants: Map<String, String> = emptyMap()) {
    /** Variant id of [moment], or [default] when none was picked. */
    fun variant(moment: String, default: String): String = variants[moment] ?: default

    /** Travel, sway and springs are allowed. */
    val spatial: Boolean get() = level == MotionLevel.FULL

    /** Decorative cost (textures, blur, idle loops) is allowed. */
    val rich: Boolean get() = level == MotionLevel.FULL || level == MotionLevel.CALM
}

/** Provided once by the app shell from `LookPrefs.design` (`:core:ui` `rememberMotionChoices`). */
val LocalMotion: ProvidableCompositionLocal<MotionChoices> = staticCompositionLocalOf { MotionChoices() }

/** Which edge a block of player controls belongs to, for the slide variant. */
enum class ControlsEdge { TOP, BOTTOM, CENTER }

/** Moment "inicio": entrance of the Home sections. [index] = position of the section, for the cascade. */
@Composable
fun Modifier.homeEntrance(index: Int): Modifier = this

/** Moment "tanzaku": idle sway of a hanging card. [seed] makes neighbours move out of phase (use the item index). */
@Composable
fun Modifier.cardSway(seed: Int): Modifier = this

/** Moment "ficha", on the card (its poster). [key] identifies the title on both sides, e.g. `"anime:1234"`. */
@Composable
fun Modifier.cardToDetailsSource(key: String): Modifier = this

/** Moment "ficha", on the Details header poster / hero. Same [key] as the card. */
@Composable
fun Modifier.cardToDetailsTarget(key: String): Modifier = this

/** Moment "ficha": wraps the navigation host so source and target can meet. Called once by the shell (lane S1). */
@Composable
fun CardToDetailsHost(content: @Composable () -> Unit) = content()

/** Moment "visto": plays when [watched] flips. [content] draws the mark for the given state. */
@Composable
fun WatchedTransition(watched: Boolean, modifier: Modifier = Modifier, content: @Composable (watched: Boolean) -> Unit) {
    Box(modifier) { content(watched) }
}

/** Moment "controles": shows and hides one block of player controls. */
@Composable
fun PlayerControlsTransition(visible: Boolean, edge: ControlsEdge, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) { content() }
}

/** Moment "carga": the placeholder of something still loading. */
@Composable
fun LoadingPlaceholder(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

/** Moment "estado": transition between two list statuses (or any small keyed value such as a counter). */
@Composable
fun <T> StatusChangeTransition(state: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(targetState = state, modifier = modifier, label = "statusChange") { content(it) }
}

/** Moment "snack": entrance and exit of a snackbar. */
@Composable
fun SnackbarTransition(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) { content() }
}
