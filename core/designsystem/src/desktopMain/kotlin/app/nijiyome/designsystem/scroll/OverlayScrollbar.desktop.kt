// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionEasing
import app.nijiyome.designsystem.motion.MotionLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.max

// Drawn by hand over the container (CMP's VerticalScrollbar would need a sibling in a Box, which changes the caller's layout);
// the position comes from the same v2 ScrollbarAdapter the stock scrollbar uses, so lazy lists get its size estimate.

private const val THIN_DP = 4f
private const val WIDE_DP = 8f
private val EDGE_INSET = 2.dp
private val MIN_THUMB = 32.dp

/** Pointer zone at the end edge that shows, widens and grabs the bar (wider than the bar so it is easy to hit). */
private val HIT_ZONE = 12.dp
private const val HIDE_AFTER_MS = 1_000L

@Composable
internal actual fun Modifier.overlayScrollbarPlatform(state: ScrollState): Modifier =
    scrollbarChrome(rememberScrollbarAdapter(state), state)

@Composable
internal actual fun Modifier.overlayScrollbarPlatform(state: LazyListState): Modifier =
    scrollbarChrome(rememberScrollbarAdapter(state), state)

@Composable
internal actual fun Modifier.overlayScrollbarPlatform(state: LazyGridState): Modifier =
    scrollbarChrome(rememberScrollbarAdapter(state), state)

private class BarUi {
    var hovered by mutableStateOf(false)
    var dragging by mutableStateOf(false)
}

private class Thumb(val top: Float, val length: Float)

/** The thumb on a track [trackLength] long starting at [trackStart], or null when everything fits. */
private fun thumbOf(adapter: ScrollbarAdapter, trackStart: Float, trackLength: Float, minLength: Float): Thumb? {
    val content = adapter.contentSize
    val viewport = adapter.viewportSize
    if (content <= viewport + 0.5 || trackLength <= 0f) return null
    val length = max(minLength, (trackLength * viewport / content).toFloat()).coerceAtMost(trackLength)
    val fraction = (adapter.scrollOffset / (content - viewport)).coerceIn(0.0, 1.0).toFloat()
    return Thumb(trackStart + fraction * (trackLength - length), length)
}

@Composable
private fun Modifier.scrollbarChrome(adapter: ScrollbarAdapter, scrollable: ScrollableState): Modifier {
    // OFF: the bar shows and hides at once (it still shows: it is information, not decoration).
    val instant = LocalMotion.current.level == MotionLevel.OFF
    val color = MaterialTheme.colorScheme.onSurface
    val scope = rememberCoroutineScope()
    val ui = remember { BarUi() }
    val alpha = remember { Animatable(0f) }
    val thickness = remember { Animatable(THIN_DP) }

    // Snapshot flows instead of reading these in composition: scrolling and hovering only redraw, never recompose the list.
    LaunchedEffect(scrollable, instant) {
        snapshotFlow { scrollable.isScrollInProgress || ui.hovered || ui.dragging }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (active) {
                    if (instant) alpha.snapTo(1f) else alpha.animateTo(1f, tween(120))
                } else {
                    delay(HIDE_AFTER_MS)
                    if (instant) alpha.snapTo(0f) else alpha.animateTo(0f, tween(300))
                }
            }
    }
    LaunchedEffect(instant) {
        snapshotFlow { ui.hovered || ui.dragging }
            .distinctUntilChanged()
            .collectLatest { wide ->
                val target = if (wide) WIDE_DP else THIN_DP
                if (instant) thickness.snapTo(target) else thickness.animateTo(target, tween(150, easing = MotionEasing.ExitExponential))
            }
    }

    return this
        .pointerInput(adapter) {
            awaitPointerEventScope {
                while (true) {
                    // Initial pass and no consumption except a press on the bar: the list keeps every other event.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    val zone = HIT_ZONE.toPx()
                    val p = change.position
                    val inZone = p.x >= size.width - zone && p.x <= size.width && p.y >= 0f && p.y <= size.height
                    when (event.type) {
                        PointerEventType.Exit -> ui.hovered = false
                        PointerEventType.Enter, PointerEventType.Move -> ui.hovered = inZone
                        PointerEventType.Press -> {
                            if (!inZone || !event.buttons.isPrimaryPressed) continue
                            val inset = EDGE_INSET.toPx()
                            val track = size.height - 2 * inset
                            val minThumb = MIN_THUMB.toPx()
                            val thumb = thumbOf(adapter, inset, track, minThumb) ?: continue
                            change.consume()
                            ui.dragging = true
                            // On the thumb: keep the grab point. On the track: centre the thumb under the pointer.
                            val onThumb = p.y >= thumb.top && p.y <= thumb.top + thumb.length
                            val grab = if (onThumb) p.y - thumb.top else thumb.length / 2f
                            var job: Job? = null
                            fun dragTo(y: Float) {
                                val now = thumbOf(adapter, inset, track, minThumb) ?: return
                                val room = track - now.length
                                if (room <= 0f) return
                                val fraction = ((y - grab - inset) / room).coerceIn(0f, 1f)
                                val offset = fraction * (adapter.contentSize - adapter.viewportSize)
                                job?.cancel()
                                job = scope.launch { adapter.scrollTo(offset) }
                            }
                            if (!onThumb) dragTo(p.y)
                            var last = p
                            while (true) {
                                val next = awaitPointerEvent(PointerEventPass.Initial)
                                val c = next.changes.firstOrNull() ?: break
                                next.changes.forEach { it.consume() }
                                if (!c.pressed) break
                                last = c.position
                                dragTo(c.position.y)
                            }
                            ui.dragging = false
                            ui.hovered = last.x >= size.width - zone && last.x <= size.width
                        }
                        else -> Unit
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val a = alpha.value
            if (a <= 0.01f) return@drawWithContent
            val inset = EDGE_INSET.toPx()
            val thumb = thumbOf(adapter, inset, size.height - 2 * inset, MIN_THUMB.toPx()) ?: return@drawWithContent
            val w = thickness.value.dp.toPx()
            val strong = ui.hovered || ui.dragging
            drawRoundRect(
                color = color.copy(alpha = (if (strong) 0.55f else 0.38f) * a),
                topLeft = Offset(size.width - w - inset, thumb.top),
                size = Size(w, thumb.length),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
}
