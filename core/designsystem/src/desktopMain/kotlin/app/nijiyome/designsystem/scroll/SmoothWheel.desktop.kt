// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.MotionEasing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.awt.event.MouseWheelEvent
import kotlin.math.abs

// The built-in wheel of CMP 1.12 moves ~30 px a notch with a short linear tween and no carry-over between notches, which reads as
// jumps. Here a notch adds ~100 dp to a target and one animation chases it, keeping its velocity when the next notch lands.

internal actual val desktopPlatform: Boolean = true

/** Distance of one wheel notch. */
private val NOTCH = 100.dp

/** Interface spring for the wheel: no bounce (ζ 1), soft enough that chained notches blend. */
private val WheelSpring: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 250f, visibilityThreshold = 0.5f)

/** Calm / performance: a short tween per notch, no carried velocity. */
private val WheelTween: AnimationSpec<Float> = tween(120, easing = MotionEasing.ExitExponential)

/**
 * One wheel event reaches every container under the pointer. The Initial pass goes outer to inner, so each container that can
 * scroll overwrites [owner] and the innermost one wins; only the owner animates, in the Final pass. The UI thread is the only
 * caller, so plain fields are enough.
 */
private object WheelArbiter {
    private var event: Any? = null
    var owner: Any? = null

    fun begin(awt: Any) {
        if (event !== awt) {
            event = awt
            owner = null
        }
    }

    fun isCurrent(awt: Any): Boolean = event === awt
}

internal actual fun Modifier.smoothWheelPlatform(
    state: ScrollableState,
    orientation: Orientation,
    reverse: Boolean,
    spring: Boolean,
): Modifier = pointerInput(state, orientation, reverse, spring) {
    val animator = WheelAnimator(state, if (spring) WheelSpring else WheelTween)
    val notchPx = NOTCH.toPx()
    coroutineScope {
        val scope: CoroutineScope = this
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val awt = event.awtEventOrNull as? MouseWheelEvent ?: continue
                WheelArbiter.begin(awt)
                if (awt.scrollType != MouseWheelEvent.WHEEL_UNIT_SCROLL) continue
                val rotation = awt.preciseWheelRotation
                // Touchpads (and hi-res wheels) send fractional rotation: the built-in handler already follows the fingers.
                if (awt.wheelRotation == 0 || abs(rotation - awt.wheelRotation) > 0.001) continue
                // Shift + wheel is horizontal (AWT also reports tilt wheels that way); a plain wheel belongs to vertical lists.
                if (awt.isShiftDown != (orientation == Orientation.Horizontal)) continue
                val delta = (rotation * notchPx).toFloat() * (if (reverse) -1f else 1f)
                val canScroll = if (delta > 0f) state.canScrollForward else state.canScrollBackward
                // At its end this container lets the wheel through, so an outer one (the page) scrolls instead.
                if (!canScroll) continue
                WheelArbiter.owner = animator
                // Consumed now so the built-in handlers (which skip consumed events) stay out of it.
                event.changes.forEach { it.consume() }
                awaitPointerEvent(PointerEventPass.Final)
                if (WheelArbiter.isCurrent(awt) && WheelArbiter.owner === animator) animator.push(delta, scope)
            }
        }
    }
}

/** Chases a target that each notch moves; re-targeting keeps the current velocity, hitting an edge stops it. */
private class WheelAnimator(private val state: ScrollableState, private val spec: AnimationSpec<Float>) {
    private var anim = AnimationState(0f)
    private var target = 0f
    private var job: Job? = null

    fun push(delta: Float, scope: CoroutineScope) {
        if (job?.isActive != true) {
            anim = AnimationState(0f)
            target = 0f
        }
        target += delta
        val previous = job
        job = scope.launch {
            // The previous run must stop before its last frame is read back as the starting point.
            previous?.cancelAndJoin()
            state.scroll(MutatePriority.UserInput) {
                var last = anim.value
                anim.animateTo(target, spec, sequentialAnimation = true) {
                    val step = value - last
                    last = value
                    val used = scrollBy(step)
                    if (abs(used - step) > 0.5f) cancelAnimation()
                }
            }
        }
    }
}
