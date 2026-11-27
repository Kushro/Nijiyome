// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.TimeSource

// Moment "tanzaku" (lab.js): the strips hang from a cord and move like paper, never in an endless loop. All three variants
// rotate the card around its top centre in the layer, so nothing is measured or recomposed per frame.

private val HangOrigin = TransformOrigin(0.5f, 0f)

/** Cards from this index on do not get the arrival push (they are off screen at first and would swing while scrolling). */
private const val ARRIVAL_SEEDS = 6

/** True where the cards do not hang (the Hanafuda skin deals them on a mat): no sway there, whatever the motion level. */
val LocalCardSwayOff = staticCompositionLocalOf { false }

@Composable
internal fun Modifier.cardSwayMotion(seed: Int): Modifier {
    if (LocalCardSwayOff.current) return this
    val motion = LocalMotion.current
    val variant = motion.variant(Moments.CARD_SWAY, "spring")
    return when (motion.level) {
        MotionLevel.OFF -> this
        // "Sin balanceo: la tira se resalta con un fundido de 150 ms." Wind and scroll are off.
        MotionLevel.CALM -> if (variant == "spring") calmHighlight(seed) else this
        // Only the touched strip moves, never the whole row; wind and scroll are continuous, so they are off.
        MotionLevel.PERF -> if (variant == "spring") springSway(seed, arrival = false) else this
        MotionLevel.FULL -> when (variant) {
            "wind" -> windSway(seed)
            "scroll" -> scrollTilt()
            else -> springSway(seed, arrival = true)
        }
    }
}

/** Once per screen instance (saved, so going back finds the row settled, like `data-tz-settled`). */
@Composable
private fun rememberArrival(seed: Int, limit: Int = ARRIVAL_SEEDS): Boolean {
    var settled by rememberSaveable { mutableStateOf(false) }
    val arrives = remember { !settled && seed in 0 until limit }
    LaunchedEffect(Unit) { settled = true }
    return arrives
}

@Composable
private fun Modifier.calmHighlight(seed: Int): Modifier {
    val arrives = rememberArrival(seed)
    if (!arrives) return this
    val alpha = remember { Animatable(0.55f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(MotionDurations.CALM_FADE, easing = LinearEasing)) }
    return this.graphicsLayer { this.alpha = alpha.value }
}

/** "Resorte suave": a push on arrival (staggered 90 ms) and on every press; settles by itself in about two seconds. */
@Composable
private fun Modifier.springSway(seed: Int, arrival: Boolean): Modifier {
    val angle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val arrives = rememberArrival(seed)
    if (arrival && arrives) {
        LaunchedEffect(Unit) {
            delay(seed * 90L)
            angle.animateTo(0f, MotionSprings.tanzaku(), initialVelocity = (70f - seed * 8f))
        }
    }
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // Observed in the initial pass and never consumed: the card's own click and the row's scroll still get it.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        scope.launch { angle.animateTo(0f, MotionSprings.tanzaku(), initialVelocity = 60f) }
                    }
                }
            }
        }
        .graphicsLayer {
            transformOrigin = HangOrigin
            rotationZ = angle.value
        }
}

/** "Viento": one four-second gust on arrival, each strip out of phase, then it calms down. A spring follows the gust. */
@Composable
private fun Modifier.windSway(seed: Int): Modifier {
    val arrives = rememberArrival(seed, limit = 8)
    if (!arrives) return this
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val stiffness = 50f
        val damping = 2f * 0.6f * sqrt(stiffness)
        val phase = seed.toFloat()
        var x = 0f
        var v = 0f
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val t = (now - start) / 1e9f
            val envelope = when {
                t < 0.6f -> t / 0.6f
                t < 3f -> 1f
                t < 4f -> 4f - t
                else -> 0f
            }
            val target = envelope * (4.5f * sin(1.3f * t + phase * 0.9f) + 2f * sin(3.1f * t + phase * 1.7f))
            val steps = max(1, ceil(dt / 0.004f).toInt())
            val h = dt / steps
            repeat(steps) {
                val a = -stiffness * (x - target) - damping * v
                v += a * h
                x += v * h
            }
            angle.floatValue = x
            if ((t > 4f && abs(x) < 0.02f && abs(v) < 0.05f) || t > 8f) {
                angle.floatValue = 0f
                break
            }
        }
    }
    return this.graphicsLayer {
        transformOrigin = HangOrigin
        rotationZ = angle.floatValue
    }
}

/**
 * "Reacción al scroll": the strip leans with the speed its row moves (8° at most) and springs back when it stops. The speed
 * comes from the card's own position, so no scroll state has to be passed in.
 */
@Composable
private fun Modifier.scrollTilt(): Modifier {
    val density = LocalDensity.current.density
    val tilt = remember { Animatable(0f) }
    val target = remember { mutableFloatStateOf(0f) }
    val moves = remember { mutableIntStateOf(0) }
    val tracker = remember { PositionTracker() }
    LaunchedEffect(Unit) {
        launch { snapshotFlow { target.floatValue }.collectLatest { tilt.animateTo(it, MotionSprings.scrollTilt()) } }
        // Back to rest 90 ms after the last movement.
        snapshotFlow { moves.intValue }.collectLatest {
            delay(90)
            target.floatValue = 0f
        }
    }
    return this
        .onGloballyPositioned { coordinates ->
            val lean = tracker.lean(coordinates.positionInRoot().x, density) ?: return@onGloballyPositioned
            target.floatValue = lean
            moves.intValue++
        }
        .graphicsLayer {
            transformOrigin = HangOrigin
            rotationZ = tilt.value
        }
}

/** Horizontal speed of a node between two placements, as a lean angle: 6° per dp/ms, capped at 8°. */
private class PositionTracker {
    private val clock = TimeSource.Monotonic.markNow()
    private var lastX = Float.NaN
    private var lastUs = 0L

    fun lean(x: Float, density: Float): Float? {
        val nowUs = clock.elapsedNow().inWholeMicroseconds
        val previous = lastX
        val dtMs = (nowUs - lastUs) / 1000f
        lastX = x
        lastUs = nowUs
        if (previous.isNaN() || x == previous) return null
        // A jump after a pause (relayout, first frame back on screen) is not a scroll.
        if (dtMs <= 0f || dtMs > 100f) return null
        val dpPerMs = (x - previous) / density / dtMs
        return (dpPerMs * 6f).coerceIn(-8f, 8f)
    }
}
