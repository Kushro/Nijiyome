// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.look

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionDurations
import app.nijiyome.designsystem.motion.MotionEasing
import app.nijiyome.designsystem.motion.MotionExtra
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.motion.MotionSprings
import app.nijiyome.designsystem.motion.motionExtraOn
import kotlinx.coroutines.launch

// v1.3.5 motion extras used by shared components and screens (ids in designsystem MotionExtras.kt). Each one checks the user's
// switch and the motion level; under OFF (and reduce motion, which folds into OFF) they all return a plain Modifier.

/**
 * [MotionExtra.LIST_PLACEMENT] on a keyed `LazyColumn` / `LazyRow` item: FULL glides it to its new place (interface spring) and fades
 * it in / out in 150 ms; CALM only fades; PERF and OFF leave the list as it was. Only on items with a stable key.
 */
@Composable
fun LazyItemScope.motionListPlacement(): Modifier {
    val level = listPlacementLevel() ?: return Modifier
    return Modifier.animateItem(
        fadeInSpec = tween(MotionDurations.CALM_FADE),
        placementSpec = if (level == MotionLevel.FULL) MotionSprings.ui(IntOffset.VisibilityThreshold) else null,
        fadeOutSpec = tween(MotionDurations.CALM_FADE),
    )
}

/** [motionListPlacement] for a keyed `LazyVerticalGrid` item. */
@Composable
fun LazyGridItemScope.motionListPlacement(): Modifier {
    val level = listPlacementLevel() ?: return Modifier
    return Modifier.animateItem(
        fadeInSpec = tween(MotionDurations.CALM_FADE),
        placementSpec = if (level == MotionLevel.FULL) MotionSprings.ui(IntOffset.VisibilityThreshold) else null,
        fadeOutSpec = tween(MotionDurations.CALM_FADE),
    )
}

/** FULL or CALM when list placement should run, else null (same rule as `reorderPlacement`: no travel off FULL). */
@Composable
private fun listPlacementLevel(): MotionLevel? {
    val level = LocalMotion.current.level
    return level.takeIf { motionExtraOn(MotionExtra.LIST_PLACEMENT) && (it == MotionLevel.FULL || it == MotionLevel.CALM) }
}

/**
 * [MotionExtra.EXPAND]: animates the height of a block that folds / unfolds (shelf, "más"). FULL and CALM: the interface spring;
 * PERF: a 150 ms tween; OFF: snaps. Keep it off lazy items whose size changes on their own (loading rows), or it chases them.
 */
@Composable
fun Modifier.motionExpand(): Modifier {
    if (!motionExtraOn(MotionExtra.EXPAND)) return this
    return when (LocalMotion.current.level) {
        MotionLevel.FULL, MotionLevel.CALM -> animateContentSize(MotionSprings.ui(IntSize.VisibilityThreshold))
        MotionLevel.PERF -> animateContentSize(tween(MotionDurations.CALM_FADE, easing = MotionEasing.ExitExponential))
        MotionLevel.OFF -> this
    }
}

/** Hover state of a card for [MotionExtra.HOVER_LIFT], from [rememberHoverLift]; 0 = rest, 1 = hovered. */
@Stable
class HoverLift internal constructor(internal val fraction: Animatable<Float, AnimationVector1D>, internal val rise: Boolean)

/**
 * [MotionExtra.HOVER_LIFT] of a Material / Canon card: listens to [interactionSource]'s hover (the card's clickable emits it) and
 * animates a fraction in 200 ms (standard curve). FULL rises and grows a shadow, CALM only grows the shadow; PERF and OFF return
 * null (nothing to draw). Pass the result to [hoverRise] on the card and [hoverShadow] on the surface that casts it.
 */
@Composable
fun rememberHoverLift(interactionSource: InteractionSource): HoverLift? {
    val level = LocalMotion.current.level
    if (!motionExtraOn(MotionExtra.HOVER_LIFT) || (level != MotionLevel.FULL && level != MotionLevel.CALM)) return null
    val lift = remember(level) { HoverLift(Animatable(0f), rise = level == MotionLevel.FULL) }
    val spec = remember(level) {
        tween<Float>(if (level == MotionLevel.FULL) MotionDurations.STATE else MotionDurations.CALM_FADE, easing = MotionEasing.ExitExponential)
    }
    // Collected here instead of collectIsHoveredAsState: the hover never recomposes the card, only its layer redraws.
    LaunchedEffect(interactionSource, lift) {
        var hovers = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> hovers++
                is HoverInteraction.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
                else -> return@collect
            }
            val target = if (hovers > 0) 1f else 0f
            launch { lift.fraction.animateTo(target, spec) }
        }
    }
    return lift
}

/** The 3 dp rise of [lift] (FULL only), read in the layer: hovering redraws, it never relayouts. */
fun Modifier.hoverRise(lift: HoverLift?): Modifier {
    if (lift == null || !lift.rise) return this
    return graphicsLayer { translationY = -HOVER_RISE.toPx() * lift.fraction.value }
}

/**
 * The shadow of [lift], growing from none to 6 dp around [shape]. Put it on an opaque surface (the poster, a filled card): a shadow
 * under a transparent area shows as a grey box.
 */
fun Modifier.hoverShadow(lift: HoverLift?, shape: Shape): Modifier {
    if (lift == null) return this
    return graphicsLayer {
        shadowElevation = HOVER_SHADOW.toPx() * lift.fraction.value
        this.shape = shape
        clip = false
    }
}

private val HOVER_RISE = 3.dp
private val HOVER_SHADOW = 6.dp
