// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

// Sparkles redesign contract (docs/redesign/CONTRATO-COMPOSE.md). Signatures written by the integrator and FROZEN for callers;
// bodies by lane M1 (M1 owns this package and may add parameters with defaults, never remove or rename). Screens call these
// functions and never read which variant is selected. Each moment lives in its own file of this package; the variants, their
// calm / performance behaviour and usage are in docs/redesign/carriles/movimiento-api.md.

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

/**
 * Moment "inicio": entrance of the Home sections. [index] = position of the section, for the cascade. Plays only the first time
 * Home opens in a session (never on going back). `cascade`: fade and 8 dp rise, 240 ms, 30 ms apart (300 ms cap); `rows`: each
 * section revealed left to right, 350 ms, 80 ms apart; `none`: 150 ms fade. Calm: one 150 ms fade; performance: 100 ms fade
 * (`rows`: nothing); off: nothing.
 */
@Composable
fun Modifier.homeEntrance(index: Int): Modifier = homeEntranceMotion(index)

/**
 * Moment "tanzaku": idle sway of a hanging card, rotating around its top centre. [seed] makes neighbours move out of phase (use
 * the item index). `spring`: a push on arrival and on every press, settles in about 2 s; `wind`: one 4 s gust on arrival;
 * `scroll`: leans with the row's scroll speed (8° max). Calm: `spring` becomes a 150 ms highlight, the rest is still;
 * performance: only the pressed card moves (`spring`); off: still.
 */
@Composable
fun Modifier.cardSway(seed: Int): Modifier = cardSwayMotion(seed)

/**
 * Moment "ficha", on the card (its poster). [key] identifies the title on both sides, e.g. `"anime:1234"`. Inside a
 * [CardToDetailsHost]; with the shell's [CardToDetailsEntry] the `ct` variant is a real shared-bounds transform.
 */
@Composable
fun Modifier.cardToDetailsSource(key: String): Modifier = cardToDetailsSourceMotion(key)

/**
 * Moment "ficha", on the Details header poster / hero. Same [key] as the card. Plays only when Details opens from a card that
 * was just pressed: `ct` grows the card into the hero (500 ms, back 350 ms), `axis` slides it in 30 dp on the shared axis,
 * `paper` unfolds it from the card, first across and then down. Calm: 150 ms fade; performance: `ct` in 350 ms, `axis` in
 * 250 ms, `paper` a 150 ms fade; off: nothing.
 */
@Composable
fun Modifier.cardToDetailsTarget(key: String): Modifier = cardToDetailsTargetMotion(key)

/**
 * Moment "ficha": wraps the navigation host so source and target can meet. Called once by the shell (lane S1). It is a
 * `SharedTransitionLayout` in every motion level (the tree never changes with the setting) and notes where presses land.
 */
@Composable
fun CardToDetailsHost(content: @Composable () -> Unit) = CardToDetailsHostImpl(content)

/**
 * Moment "ficha": gives the screens of one navigation entry the entry's [animatedVisibilityScope], so source and target can run
 * a shared-bounds transform. The shell wraps each entry, e.g. in a `NavEntryDecorator`, with
 * `CardToDetailsEntry(LocalNavAnimatedContentScope.current) { entry.Content() }`. Without it the target plays its own entrance.
 */
@Composable
fun CardToDetailsEntry(animatedVisibilityScope: AnimatedVisibilityScope, content: @Composable () -> Unit) =
    CardToDetailsEntryImpl(animatedVisibilityScope, content)

/**
 * Moment "visto": plays when [watched] flips (never on first composition). [content] draws the mark for the given state.
 * `stamp`: the mark drops from 1.6 on a short spring with a slight turn; `stations`: the mark pops (0.6 to 1, 120 ms) after the
 * 350 ms line travel (pair with [animateWatchedProgress]); `band`: a [tint] (default primaryContainer) sweeps left to right
 * behind the content, clipped to [shape], and stays while watched. Calm: 120 ms fades; performance and off: immediate.
 */
@Composable
fun WatchedTransition(
    watched: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color = Color.Unspecified,
    content: @Composable (watched: Boolean) -> Unit,
) {
    WatchedTransitionImpl(watched, modifier, shape, tint, content)
}

/**
 * Moment "controles": shows and hides one block of player controls; hiding is faster than showing. `fade`: 200 ms in, 150 ms
 * out; `slide`: top rises 12 dp, bottom drops 16 dp, centre shrinks to 0.88 (250 / 200 ms); `min`: 150 ms, the bottom block
 * grows out of a 2 dp line, and [hiddenContent] (e.g. [MiniProgressLine]) shows while the controls are hidden. Calm: 120 ms
 * fades; performance: fades (`slide` 150 ms, `min` 120 ms), and the shell should drop the scrim gradient
 * (`!LocalMotion.current.rich`); off: instant.
 */
@Composable
fun PlayerControlsTransition(
    visible: Boolean,
    edge: ControlsEdge,
    modifier: Modifier = Modifier,
    hiddenContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    PlayerControlsTransitionImpl(visible, edge, modifier, hiddenContent, content)
}

/**
 * Moment "carga": the placeholder of something still loading. `shimmer`: a highlight sweeps across (1.2 s a pass);
 * `ink`: an ink drop beats in the centre (900 ms); `count`: when [label] (e.g. "Finding sources · 2 of 6 extensions") or
 * [progress] (0..1) are given, they show with a bar that moves 200 ms per answer. Loops stop after 5 s. Calm and performance:
 * a still block (`ink` adds "Loading…" where it fits); off: still. Pair the content that replaces it with [loadedReveal].
 */
@Composable
fun LoadingPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    label: String? = null,
    progress: Float? = null,
) {
    LoadingPlaceholderImpl(modifier, shape, label, progress)
}

/**
 * Moment "estado": transition between two list statuses (or any small keyed value such as a counter). `ind`: 250 ms
 * cross-fade (a picker's own indicator takes [statusChangeSpec]); `ribbon`: the new value sweeps over the old one left to
 * right in 300 ms; `odo`: the new value rolls up from below in 180 ms. Calm: 120 ms fade; performance and off: immediate.
 */
@Composable
fun <T> StatusChangeTransition(state: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    StatusChangeTransitionImpl(state, modifier, content)
}

/**
 * Moment "snack": entrance and exit of a snackbar. `rise` and `stack`: 16 dp up and in over 220 ms, out in 150 ms (a stacked
 * one behind takes [snackbarBehind]); `origin`: grows from 0.4 around the point last pressed (the action that caused it) in
 * 250 ms. Calm: 150 ms fades; performance: `origin` fades 150 ms, the others appear and leave at once; off: instant.
 */
@Composable
fun SnackbarTransition(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SnackbarTransitionImpl(visible, modifier, content)
}
