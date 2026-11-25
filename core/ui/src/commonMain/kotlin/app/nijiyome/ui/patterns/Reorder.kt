// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.patterns

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_reo_down
import app.nijiyome.ui.resources.rd_i1_reo_handle
import app.nijiyome.ui.resources.rd_i1_reo_position
import app.nijiyome.ui.resources.rd_i1_reo_top
import app.nijiyome.ui.resources.rd_i1_reo_up
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane I1. Pattern `reo` of docs/redesign/artefacto/lab.js: the handle drags a row, the other rows stay still while
// dragging and a 2 dp bar marks where it will land; near the edges the list scrolls by itself. Keyboard: Alt+Up / Alt+Down moves the
// focused row; TalkBack: "Move up" / "Move down" / "Move to the top" actions. The move happens on drop (animate it with
// [reorderPlacement]).

/**
 * Reordering state of one `LazyColumn` whose items are exactly the reorderable rows (index i of the list = row i). [onMove] receives
 * `from` and `to` as in `list.add(to, list.removeAt(from))` (use [moved]).
 *
 * ```kotlin
 * val reorder = rememberReorderState(onMove = { from, to -> queue = queue.moved(from, to); save(queue) })
 * LazyColumn(state = reorder.listState, modifier = Modifier.reorderContainer(reorder)) {
 *     itemsIndexed(queue, key = { _, it -> it.id }) { i, it -> ReorderableRow(reorder, i, queue.size, it.name, Modifier.reorderPlacement()) }
 * }
 * ```
 */
@Stable
class ReorderState internal constructor(val listState: LazyListState) {
    internal var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** Index of the row being dragged. */
    var draggingIndex: Int? by mutableStateOf(null)
        private set

    /** The gap where the row would land: 0 = before the first row, n = after the last. */
    var insertionIndex: Int? by mutableStateOf(null)
        private set

    internal var pointerY: Float? by mutableStateOf(null)
    internal var pointerId: PointerId? = null
    internal var listCoords: LayoutCoordinates? = null

    /** Moves the row at [from] to [to] (keyboard and accessibility). False when [to] is outside the list. */
    fun move(from: Int, to: Int, count: Int): Boolean {
        if (from == to || to < 0 || to >= count || from < 0 || from >= count) return false
        onMove(from, to)
        return true
    }

    internal fun start(index: Int, y: Float, id: PointerId) {
        draggingIndex = index
        pointerId = id
        update(y)
    }

    internal fun update(y: Float) {
        pointerY = y
        insertionIndex = gapAt(y)
    }

    internal fun finish() {
        val from = draggingIndex
        val gap = insertionIndex
        cancel()
        if (from == null || gap == null) return
        val to = if (gap > from) gap - 1 else gap
        if (to != from) onMove(from, to)
    }

    internal fun cancel() {
        draggingIndex = null
        insertionIndex = null
        pointerY = null
        pointerId = null
    }

    private fun gapAt(y: Float): Int {
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty()) return 0
        val inContent = y + info.viewportStartOffset
        for (item in items) {
            if (inContent < item.offset + item.size / 2f) return item.index
        }
        return items.last().index + 1
    }

    /** Y of the insertion bar in the list's own coordinates, or null when the gap is off screen. */
    internal fun barY(): Float? {
        val gap = insertionIndex ?: return null
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        val at = items.firstOrNull { it.index == gap }?.offset
            ?: items.firstOrNull { it.index == gap - 1 }?.let { it.offset + it.size }
            ?: return null
        return (at - info.viewportStartOffset).toFloat()
    }
}

@Composable
fun rememberReorderState(listState: LazyListState = rememberLazyListState(), onMove: (from: Int, to: Int) -> Unit): ReorderState {
    val state = remember(listState) { ReorderState(listState) }
    val current by rememberUpdatedState(onMove)
    state.onMove = { from, to -> current(from, to) }
    return state
}

/**
 * Goes on the `LazyColumn` of a [ReorderState]: follows the drag started by a [reorderHandle] (even when the dragged row scrolls away),
 * draws the insertion bar and scrolls the list near its top and bottom edges.
 */
@Composable
fun Modifier.reorderContainer(state: ReorderState): Modifier {
    val barColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    val maxSpeed = with(density) { 900.dp.toPx() } // per second
    val bar = with(density) { 2.dp.toPx() }
    val dragging = state.draggingIndex != null
    LaunchedEffect(state, dragging) {
        if (!dragging) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (state.draggingIndex != null) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val y = state.pointerY ?: continue
            val height = state.listCoords?.size?.height?.toFloat() ?: continue
            val speed = when {
                y < edge -> -maxSpeed * (1f - (y.coerceAtLeast(0f) / edge))
                y > height - edge -> maxSpeed * (1f - ((height - y).coerceAtLeast(0f) / edge))
                else -> 0f
            }
            if (speed != 0f) {
                state.listState.scrollBy(speed * dt)
                state.update(y)
            }
        }
    }
    return this
        .onGloballyPositioned { state.listCoords = it }
        .pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val id = state.pointerId ?: continue
                    val change = event.changes.firstOrNull { it.id == id } ?: continue
                    change.consume()
                    if (!change.pressed) state.finish() else state.update(change.position.y)
                }
            }
        }
        .drawWithContent {
            drawContent()
            val y = state.barY()
            if (y != null) drawRect(barColor, topLeft = Offset(0f, y - bar / 2f), size = Size(size.width, bar))
        }
}

private class HandleCoords {
    var coords: LayoutCoordinates? = null
}

/** The drag handle of the row at [index]: pressing it starts the drag right away (mouse and touch), no long-press needed. */
@Composable
fun Modifier.reorderHandle(state: ReorderState, index: Int): Modifier {
    val holder = remember { HandleCoords() }
    val current by rememberUpdatedState(index)
    return this
        .onGloballyPositioned { holder.coords = it }
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val source = holder.coords ?: return@awaitEachGesture
                val list = state.listCoords ?: return@awaitEachGesture
                if (!source.isAttached || !list.isAttached) return@awaitEachGesture
                down.consume()
                state.start(current, list.localPositionOf(source, down.position).y, down.id)
            }
        }
}

/**
 * Keyboard and accessibility of the reorderable row at [index] of [count]: Alt+Up / Alt+Down, the TalkBack actions, its position as
 * state description, and the fade while it is the dragged row. The row must be focusable ([ReorderableRow] is).
 */
@Composable
fun Modifier.reorderableItem(state: ReorderState, index: Int, count: Int): Modifier {
    val up = stringResource(Res.string.rd_i1_reo_up)
    val down = stringResource(Res.string.rd_i1_reo_down)
    val top = stringResource(Res.string.rd_i1_reo_top)
    val position = stringResource(Res.string.rd_i1_reo_position, index + 1, count)
    val actions = buildList {
        if (index > 0) {
            add(CustomAccessibilityAction(up) { state.move(index, index - 1, count) })
            add(CustomAccessibilityAction(top) { state.move(index, 0, count) })
        }
        if (index < count - 1) add(CustomAccessibilityAction(down) { state.move(index, index + 1, count) })
    }
    return this
        .then(if (state.draggingIndex == index) Modifier.alpha(0.5f) else Modifier)
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || !e.isAltPressed) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionUp -> state.move(index, index - 1, count)
                Key.DirectionDown -> state.move(index, index + 1, count)
                else -> false
            }
        }
        .semantics {
            stateDescription = position
            customActions = actions
        }
}

/**
 * Placement animation of a reordered row, on the item of the `LazyColumn` (`Modifier.reorderPlacement()` inside `items { }`): it
 * slides to its new place after the drop when travel is allowed, and simply appears there otherwise.
 */
@Composable
fun LazyItemScope.reorderPlacement(): Modifier =
    if (LocalMotion.current.spatial) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null) else Modifier

/**
 * A ready-made reorderable row (download queue, Home rows): handle at the start on wide windows and at the end on phones, an optional
 * leading icon, title and supporting line, an optional trailing slot. Focusable with a visible focus ring.
 *
 * ```kotlin
 * ReorderableRow(reorder, i, rows.size, title = rowName(id), supporting = hiddenLabel, modifier = Modifier.reorderPlacement(),
 *     trailing = { Switch(checked = id !in hidden, onCheckedChange = { toggle(id) }) })
 * ```
 */
@Composable
fun ReorderableRow(
    state: ReorderState,
    index: Int,
    count: Int,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    handleAtStart: Boolean = !isCompactWindow(),
) {
    var focused by remember { mutableStateOf(false) }
    val handleLabel = stringResource(Res.string.rd_i1_reo_handle)
    val handle: @Composable () -> Unit = {
        Box(
            Modifier.size(48.dp).reorderHandle(state, index).semantics { stateDescription = handleLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (handleAtStart) Icons.Outlined.DragIndicator else Icons.Outlined.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
            .reorderableItem(state, index, count)
            .focusable()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (handleAtStart) handle()
        if (leading != null) leading()
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
        if (!handleAtStart) handle()
    }
}
