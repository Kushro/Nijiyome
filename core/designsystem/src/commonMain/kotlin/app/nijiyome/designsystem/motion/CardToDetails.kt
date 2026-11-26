// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.time.TimeSource

// Moment "ficha" (lab.js): the one author moment of navigation. It explains that Details is that card opened, without making
// anyone wait, and it leaves faster than it arrives.
//
// Two ways to run, chosen per call site:
// - Wired: the shell gives every navigation entry its AnimatedVisibilityScope with [CardToDetailsEntry]. Then `ct` is a real
//   shared-bounds container transform (Compose SharedTransitionLayout), forward and back, and `axis` is left to the screen
//   transition the shell takes from [cardToDetailsEnterTransform] / [cardToDetailsPopTransform].
// - Not wired (or outside a navigation entry): the Details poster / hero plays a coordinated entrance that starts from the
//   bounds of the card that was tapped with the same key: a cropped container transform (`ct`), a slide on the shared axis
//   (`axis`) or a paper unfold (`paper`). Going back plays the shell's pop transition.

/** Pointer presses seen by [CardToDetailsHost], in root coordinates. Also read by the snackbar "origin" variant. */
internal object TapTracker {
    private val clock = TimeSource.Monotonic.markNow()

    class Tap(val position: Offset, val atMs: Long)

    var last: Tap? by mutableStateOf(null)
        private set

    fun record(position: Offset) {
        last = Tap(position, nowMs())
    }

    fun nowMs(): Long = clock.elapsedNow().inWholeMilliseconds

    /** [last] without registering a snapshot read: callers that decide once, at composition or in an effect. */
    fun peek(): Tap? = Snapshot.withoutReadObservation { last }

    /** The last press, if it happened in the last [maxAgeMs]. Not a snapshot read. */
    fun recent(maxAgeMs: Long): Offset? {
        val tap = peek() ?: return null
        return tap.position.takeIf { nowMs() - tap.atMs <= maxAgeMs }
    }
}

/** A press older than this did not open the Details that is composing now. */
private const val PENDING_MS = 1_500L

/**
 * For a Details target that appears late (its screen showed a loading skeleton first, so the shared transition had already ended):
 * a press this recent still explains it.
 */
private const val LATE_PENDING_MS = 4_000L

/** One card poster that can open Details. [bounds] in root coordinates, refreshed on every placement (no snapshot state). */
internal class CardSource(val key: String, val id: Long) {
    var bounds: Rect = Rect.Zero
}

@Stable
internal class CardToDetailsState(val shared: SharedTransitionScope) {
    private val sources = LinkedHashSet<CardSource>()
    private var nextId = 0L

    fun newSource(key: String): CardSource = CardSource(key, ++nextId)

    fun add(source: CardSource) {
        sources.add(source)
    }

    /**
     * The pressed card whose screen has since left the composition (navigation disposes the list): kept so a Details target that
     * appears late can still start from its bounds.
     */
    private var departed: CardSource? = null

    fun remove(source: CardSource) {
        val tap = TapTracker.peek()
        if (tap != null && source.bounds.contains(tap.position)) departed = source
        sources.remove(source)
    }

    /** The source with [key] under the last recent press. */
    private fun pressed(key: String, tap: TapTracker.Tap?): CardSource? {
        if (tap == null || TapTracker.nowMs() - tap.atMs > PENDING_MS) return null
        return sources.firstOrNull { it.key == key && it.bounds.contains(tap.position) }
    }

    /**
     * Whether [source] takes part in the shared transition. When the same title shows twice (Continue watching and a feed),
     * only the copy that was pressed matches the Details poster; with no press on any copy (keyboard), all of them stay on.
     * Reads [TapTracker.last], so it belongs in a `derivedStateOf`.
     */
    fun sharedEnabled(source: CardSource): Boolean {
        val hit = pressed(source.key, TapTracker.last) ?: return true
        return hit === source
    }

    /** Bounds of the card with [key] that was just pressed, if Details is opening from one. Read once, at composition. */
    fun pendingBounds(key: String, maxAgeMs: Long = PENDING_MS): Rect? {
        val tap = TapTracker.peek()
        if (tap == null || TapTracker.nowMs() - tap.atMs > maxAgeMs) return null
        val hit = sources.firstOrNull { it.key == key && it.bounds.contains(tap.position) }
            ?: departed?.takeIf { it.key == key && it.bounds.contains(tap.position) }
        return hit?.bounds?.takeIf { it.width > 0f && it.height > 0f }
    }
}

internal val LocalCardToDetails: ProvidableCompositionLocal<CardToDetailsState?> = staticCompositionLocalOf { null }

internal val LocalCardToDetailsEntry: ProvidableCompositionLocal<AnimatedVisibilityScope?> = staticCompositionLocalOf { null }

private fun sharedKeyOf(key: String) = "nj-ficha:$key"

@Composable
internal fun CardToDetailsHostImpl(content: @Composable () -> Unit) {
    val coordinates = remember { CoordinatesHolder() }
    // Always the same tree, whatever the level: switching it would rebuild the navigation host and lose its state.
    SharedTransitionLayout(
        Modifier
            .onGloballyPositioned { coordinates.value = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Initial pass, never consumed: everything below still receives the press.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Press) continue
                        val local = event.changes.firstOrNull()?.position ?: continue
                        val node = coordinates.value
                        TapTracker.record(if (node != null && node.isAttached) node.localToRoot(local) else local)
                    }
                }
            },
    ) {
        val state = remember(this) { CardToDetailsState(this) }
        CompositionLocalProvider(LocalCardToDetails provides state) { content() }
    }
}

private class CoordinatesHolder {
    var value: LayoutCoordinates? = null
}

/** Durations of the transform at this level: (enter, exit). */
private fun transformMs(level: MotionLevel): Pair<Int, Int> =
    if (level == MotionLevel.PERF) 350 to MotionDurations.exitOf(350) else MotionDurations.AUTHOR to MotionDurations.exitOf(MotionDurations.AUTHOR)

private val PosterCrop = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop, Alignment.Center)

@Composable
internal fun Modifier.cardToDetailsSourceMotion(key: String): Modifier {
    val state = LocalCardToDetails.current ?: return this
    val motion = LocalMotion.current
    if (motion.level == MotionLevel.OFF) return this
    val source = remember(state, key) { state.newSource(key) }
    DisposableEffect(state, source) {
        state.add(source)
        onDispose { state.remove(source) }
    }
    val tracked = this.onGloballyPositioned { source.bounds = it.boundsInRoot() }

    val entry = LocalCardToDetailsEntry.current
    val transform = motion.level == MotionLevel.FULL || motion.level == MotionLevel.PERF
    if (entry == null || !transform || motion.variant(Moments.CARD_TO_DETAILS, "ct") != "ct") return tracked

    val enabled by remember(state, source) { derivedStateOf { state.sharedEnabled(source) } }
    val sharedKey = if (enabled) sharedKeyOf(key) else "${sharedKeyOf(key)}#${source.id}"
    val (enterMs, exitMs) = transformMs(motion.level)
    return with(state.shared) {
        tracked.sharedBounds(
            rememberSharedContentState(sharedKey),
            entry,
            // Coming back: the card reappears inside the shrinking container.
            fadeIn(tween(exitMs / 2, easing = LinearEasing)),
            // Leaving: the poster gives way to the Details content within the first frames.
            fadeOut(tween(enterMs / 3, easing = LinearEasing)),
            // The bounds animation of the side that becomes visible is the one that runs: this is the way back.
            BoundsTransform { _, _ -> tween(exitMs, easing = MotionEasing.ExitExponential) },
            PosterCrop,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun Modifier.cardToDetailsTargetMotion(key: String): Modifier {
    val state = LocalCardToDetails.current ?: return this
    val motion = LocalMotion.current
    val level = motion.level
    if (level == MotionLevel.OFF) return this
    val variant = motion.variant(Moments.CARD_TO_DETAILS, "ct")
    val entry = LocalCardToDetailsEntry.current
    val transform = level == MotionLevel.FULL || level == MotionLevel.PERF

    // Details often composes its target only once its data has loaded (a skeleton first): by then the screen transition has ended
    // and a shared element finds nothing to match. Decided once: a target that first appears on a settled entry plays its own
    // entrance from the pressed card's bounds instead, as when the shell is not wired.
    val late = remember(key) {
        entry != null && !entry.transition.isRunning && entry.transition.currentState == EnterExitState.Visible
    }
    if (entry != null && !late && variant == "ct" && transform) {
        val (enterMs, exitMs) = transformMs(level)
        return with(state.shared) {
            this@cardToDetailsTargetMotion.sharedBounds(
                rememberSharedContentState(sharedKeyOf(key)),
                entry,
                // "El contenido aparece cuando ya casi llegó": the hero fades in over the second half of the travel.
                fadeIn(tween(enterMs / 2, delayMillis = enterMs / 3, easing = LinearEasing)),
                fadeOut(tween(exitMs * 2 / 5, easing = LinearEasing)),
                BoundsTransform { _, _ -> tween(enterMs, easing = MotionEasing.Emphasized) },
                PosterCrop,
            )
        }
    }
    // Wired shell: the shared axis is the whole screen moving (cardToDetailsEnterTransform), not the hero alone.
    if (entry != null && !late && variant == "axis") return this

    val from = remember(key) { state.pendingBounds(key, if (late) LATE_PENDING_MS else PENDING_MS) } ?: return this
    val kind = when {
        level == MotionLevel.CALM -> TargetKind.FADE
        variant == "axis" -> TargetKind.AXIS
        variant == "paper" -> if (level == MotionLevel.FULL) TargetKind.PAPER else TargetKind.FADE
        else -> TargetKind.TRANSFORM
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val spec: FiniteAnimationSpec<Float> = when (kind) {
            TargetKind.FADE -> tween(MotionDurations.CALM_FADE, easing = LinearEasing)
            TargetKind.AXIS -> tween(if (level == MotionLevel.PERF) 250 else MotionDurations.LAYOUT, easing = MotionEasing.ExitExponential)
            TargetKind.PAPER -> tween(MotionDurations.AUTHOR, easing = MotionEasing.Emphasized)
            TargetKind.TRANSFORM -> tween(transformMs(level).first, easing = MotionEasing.Emphasized)
        }
        progress.animateTo(1f, spec)
    }
    val own = remember { RectHolder() }
    val density = LocalDensity.current
    val axisShift = with(density) { 30.dp.toPx() }
    val cornerStart = with(density) { 12.dp.toPx() }
    return this
        .onGloballyPositioned { own.value = it.boundsInRoot() }
        .graphicsLayer {
            val p = progress.value
            val target = own.value
            if (target == null || target.width <= 0f || target.height <= 0f) {
                alpha = if (p >= 1f) 1f else 0f
                return@graphicsLayer
            }
            when (kind) {
                TargetKind.FADE -> {
                    alpha = p
                }
                TargetKind.AXIS -> {
                    // Keyframes of the artifact: still transparent at 30 %, the 30 px travel spread over the whole run.
                    alpha = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    translationX = (1f - p) * axisShift
                }
                TargetKind.PAPER -> {
                    if (p < 1f) {
                        clip = true
                        shape = paperWindow(from, target, p, cornerStart)
                    }
                }
                TargetKind.TRANSFORM -> {
                    if (p < 1f) applyContainerTransform(from, target, p, cornerStart)
                }
            }
        }
}

private enum class TargetKind { FADE, AXIS, PAPER, TRANSFORM }

private class RectHolder {
    var value: Rect? = null
}

/**
 * Container transform without a shared scope: the visible window grows from the card's rect to the hero's, and the hero is
 * scaled to cover that window (cropped, never stretched), centred on it.
 */
private fun GraphicsLayerScope.applyContainerTransform(from: Rect, target: Rect, p: Float, cornerStart: Float) {
    val w = target.width
    val h = target.height
    val start = Rect(Offset(from.left - target.left, from.top - target.top), Size(from.width, from.height))
    val window = lerp(start, Rect(0f, 0f, w, h), p)
    val k = max(window.width / w, window.height / h).coerceAtLeast(0.01f)
    val tx = window.center.x - k * w / 2f
    val ty = window.center.y - k * h / 2f
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = k
    scaleY = k
    translationX = tx
    translationY = ty
    clip = true
    // The clip is in the layer's own (unscaled) coordinates.
    shape = WindowShape(
        Rect((window.left - tx) / k, (window.top - ty) / k, (window.right - tx) / k, (window.bottom - ty) / k),
        lerpF(cornerStart, 0f, p) / k,
    )
}

/** Paper unfold: from the card's rect (kept inside the hero), first to full width (40 %), then down (60 %). */
private fun paperWindow(from: Rect, target: Rect, p: Float, cornerStart: Float): Shape {
    val w = target.width
    val h = target.height
    val cw = from.width.coerceAtMost(w)
    val ch = from.height.coerceAtMost(h)
    val left = (from.left - target.left).coerceIn(0f, w - cw)
    val top = (from.top - target.top).coerceIn(0f, h - ch)
    val card = Rect(left, top, left + cw, top + ch)
    val strip = Rect(0f, card.top, w, card.bottom)
    val full = Rect(0f, 0f, w, h)
    return if (p < 0.4f) {
        val f = p / 0.4f
        WindowShape(lerp(card, strip, f), lerpF(cornerStart, 0f, f))
    } else {
        WindowShape(lerp(strip, full, (p - 0.4f) / 0.6f), 0f)
    }
}

private class WindowShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        if (radius <= 0f) {
            Outline.Rectangle(rect)
        } else {
            Outline.Rounded(RoundRect(rect.left, rect.top, rect.right, rect.bottom, radius, radius))
        }
}

@Composable
internal fun CardToDetailsEntryImpl(animatedVisibilityScope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCardToDetailsEntry provides animatedVisibilityScope) { content() }
}

/**
 * Screen transition for opening Details from a card, for the shell's `NavDisplay(transitionSpec = …)` when the new top entry is
 * a Details route. `ct`: the screens cross-fade while the poster travels; `axis`: the list leaves 30 dp to the left and Details
 * arrives from 30 dp on the right; `paper`: a short cross-fade (the unfold is on the hero). CALM 150 ms fades, OFF instant.
 */
fun MotionChoices.cardToDetailsEnterTransform(density: Density): ContentTransform {
    val shift = with(density) { 30.dp.roundToPx() }
    return when (level) {
        MotionLevel.OFF -> EnterTransition.None togetherWith ExitTransition.None
        MotionLevel.CALM -> fadeIn(tween(MotionDurations.CALM_FADE)) togetherWith fadeOut(tween(MotionDurations.CALM_FADE))
        MotionLevel.FULL, MotionLevel.PERF -> when (variant(Moments.CARD_TO_DETAILS, "ct")) {
            "axis" -> {
                val ms = if (level == MotionLevel.PERF) 250 else MotionDurations.LAYOUT
                (
                    slideInHorizontally(tween(ms, easing = MotionEasing.ExitExponential)) { shift } +
                        fadeIn(tween(ms * 7 / 10, delayMillis = ms * 3 / 10, easing = LinearEasing))
                    ) togetherWith (
                    slideOutHorizontally(tween(ms, easing = MotionEasing.ExitExponential)) { -shift } +
                        fadeOut(tween(ms * 45 / 100, easing = MotionEasing.ExitExponential))
                    )
            }
            "paper" -> if (level == MotionLevel.FULL) {
                fadeIn(tween(MotionDurations.STATE, easing = LinearEasing)) togetherWith
                    fadeOut(tween(MotionDurations.STATE, delayMillis = MotionDurations.INSTANT, easing = LinearEasing))
            } else {
                fadeIn(tween(MotionDurations.CALM_FADE)) togetherWith fadeOut(tween(MotionDurations.CALM_FADE))
            }
            else -> {
                val (enterMs, _) = transformMs(level)
                // MOV-01: fade-through (list exits cleanly before Details text fades in) so both screens never read on top of each other.
                val outMs = enterMs / 4
                fadeIn(tween(enterMs / 2, delayMillis = outMs, easing = LinearEasing)) togetherWith
                    fadeOut(tween(outMs, easing = LinearEasing))
            }
        }
    }
}

/** Screen transition for going back from Details to the list: the reverse of [cardToDetailsEnterTransform], at 70 % of its time. */
fun MotionChoices.cardToDetailsPopTransform(density: Density): ContentTransform {
    val shift = with(density) { 30.dp.roundToPx() }
    return when (level) {
        MotionLevel.OFF -> EnterTransition.None togetherWith ExitTransition.None
        MotionLevel.CALM -> fadeIn(tween(MotionDurations.CALM_QUICK)) togetherWith fadeOut(tween(MotionDurations.CALM_QUICK))
        MotionLevel.FULL, MotionLevel.PERF -> when (variant(Moments.CARD_TO_DETAILS, "ct")) {
            "axis" -> {
                val ms = if (level == MotionLevel.PERF) 250 else MotionDurations.LAYOUT
                (
                    slideInHorizontally(tween(ms * 7 / 10, easing = MotionEasing.ExitExponential)) { -shift } +
                        fadeIn(tween(ms * 7 / 10, easing = MotionEasing.ExitExponential))
                    ) togetherWith (
                    slideOutHorizontally(tween(ms * 6 / 10, easing = MotionEasing.ExitExponential)) { shift } +
                        fadeOut(tween(ms * 6 / 10, easing = LinearEasing))
                    )
            }
            else -> {
                val (_, exitMs) = transformMs(level)
                fadeIn(tween(exitMs / 2, delayMillis = exitMs / 4, easing = LinearEasing)) togetherWith
                    fadeOut(tween(exitMs / 2, easing = LinearEasing))
            }
        }
    }
}
