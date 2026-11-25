// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_dnd_cancel
import app.nijiyome.ui.resources.rd_i1_dnd_copy
import app.nijiyome.ui.resources.rd_i1_dnd_copy_hint
import app.nijiyome.ui.resources.rd_i1_dnd_drop_here
import app.nijiyome.ui.resources.rd_i1_dnd_ghost_idle
import app.nijiyome.ui.resources.rd_i1_dnd_keys_hint
import app.nijiyome.ui.resources.rd_i1_dnd_move
import app.nijiyome.ui.resources.rd_i1_dnd_move_hint
import app.nijiyome.ui.resources.rd_i1_dnd_picked
import app.nijiyome.ui.resources.rd_i1_dnd_remember
import app.nijiyome.ui.resources.rd_i1_dnd_where
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.isCtrlPressed as pointerCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as pointerMeta
import androidx.compose.ui.input.pointer.isShiftPressed as pointerShift

// Sparkles redesign, lane I1. Pattern `dnd` of docs/redesign/artefacto/lab.js: drag a title onto a list status (a kanban column, a rail
// entry) or one of the user's lists. While dragging every target reacts: the valid ones are outlined, the others fade and say why. A drag
// is never the only way: Space picks the focused title and a target's "Drop here" button (or the item's context menu, or TalkBack's
// custom actions) drops it. The drag stays inside the window (no system drag and drop), so it works the same on desktop and Android.

/** Keys held at the moment of the drop: on a list, Ctrl copies and Shift moves (without either the caller may ask, see [DropModeMenu]). */
@Immutable
data class DropModifiers(val copy: Boolean = false, val move: Boolean = false) {
    companion object {
        val None: DropModifiers = DropModifiers()
    }
}

internal class DropTargetEntry<T>(
    var label: String,
    var refuse: (T) -> String?,
    var onDrop: (T, DropModifiers) -> Unit,
) {
    var bounds: Rect = Rect.Zero
}

/**
 * The drag in progress of items of type [T] (a title key, a media id) and the drop targets of one screen. One state per screen,
 * given to [DragDropContainer], to every [dragSource] and every [dropTarget].
 *
 * ```kotlin
 * val dnd = rememberDragDropState<String>()
 * DragDropContainer(dnd, itemLabel = { titleOf(it) }) { Board(dnd) }
 * ```
 */
@Stable
class DragDropState<T : Any> {
    /** The item under the pointer, while a pointer drag is on. */
    var dragging: T? by mutableStateOf(null)
        private set

    /** The item picked with the keyboard (Space) or a menu, waiting for a target. */
    var picked: T? by mutableStateOf(null)
        private set

    /** Pointer position in the container, while dragging. */
    var pointer: Offset? by mutableStateOf(null)
        private set

    /** Key of the target under the pointer. */
    var hovered: Any? by mutableStateOf(null)
        private set

    /** The item being moved by either way. */
    val active: T? get() = dragging ?: picked

    internal var container: LayoutCoordinates? = null
    internal val targets = LinkedHashMap<Any, DropTargetEntry<T>>()

    /** Bumped when targets come and go, so the sources' accessibility actions follow. */
    internal var targetsVersion by mutableIntStateOf(0)
    private var token = 0

    /** Picks [item] for a keyboard or menu move; the targets then show "Drop here". */
    fun pick(item: T) {
        dragging = null
        picked = item
    }

    /** Ends any drag or pick without dropping. */
    fun cancel() {
        token++
        dragging = null
        picked = null
        pointer = null
        hovered = null
    }

    /** Why the active item cannot go to [targetKey], or null when it can (or nothing is active). */
    fun refusal(targetKey: Any): String? {
        val item = active ?: return null
        return targets[targetKey]?.refuse?.invoke(item)
    }

    /** The active item can be dropped on [targetKey]. */
    fun accepts(targetKey: Any): Boolean {
        val item = active ?: return false
        val target = targets[targetKey] ?: return false
        return target.refuse(item) == null
    }

    /** Drops the active item on [targetKey] (keyboard, menu, "Drop here" button). Returns false when it was refused or nothing is active. */
    fun dropOn(targetKey: Any, modifiers: DropModifiers = DropModifiers.None): Boolean {
        val item = active ?: return false
        val target = targets[targetKey] ?: return false
        if (target.refuse(item) != null) return false
        cancel()
        target.onDrop(item, modifiers)
        return true
    }

    /** Label of the target with [key] ("Move to Watching"). */
    fun labelOf(key: Any): String? = targets[key]?.label

    internal fun beginDrag(item: T, at: Offset): Int {
        token++
        picked = null
        dragging = item
        pointer = at
        hovered = targetAt(at)
        return token
    }

    internal fun moveDrag(dragToken: Int, at: Offset) {
        if (dragToken != token) return
        pointer = at
        hovered = targetAt(at)
    }

    internal fun endDrag(dragToken: Int, modifiers: DropModifiers) {
        if (dragToken != token) return
        val item = dragging
        val key = hovered
        cancel()
        if (item == null || key == null) return
        val target = targets[key] ?: return
        if (target.refuse(item) == null) target.onDrop(item, modifiers)
    }

    internal fun abandonDrag(dragToken: Int) {
        if (dragToken == token && dragging != null) cancel()
    }

    internal fun toContainer(source: LayoutCoordinates, local: Offset): Offset {
        val c = container
        return if (c != null && c.isAttached && source.isAttached) c.localPositionOf(source, local) else local
    }

    private fun targetAt(at: Offset): Any? = targets.entries.lastOrNull { it.value.bounds.contains(at) }?.key
}

@Composable
fun <T : Any> rememberDragDropState(): DragDropState<T> = remember { DragDropState() }

/**
 * The area where drags happen (the board, the list screen). It knows where the targets are, draws the item under the pointer and
 * cancels on Esc. [itemLabel] names the item in the ghost; [ghost] replaces the default chip (poster + title + what will happen).
 *
 * ```kotlin
 * DragDropContainer(dnd, itemLabel = { titles.getValue(it).title }, modifier = Modifier.fillMaxSize()) {
 *     Row { statuses.forEach { s -> Column(Modifier.weight(1f).dropTarget(dnd, s, moveLabel(s), onDrop = { id, _ -> setStatus(id, s) })) { … } } }
 * }
 * ```
 */
@Composable
fun <T : Any> DragDropContainer(
    state: DragDropState<T>,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    ghost: (@Composable (item: T, hint: String, refused: Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .onGloballyPositioned { state.container = it }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && state.active != null) {
                    state.cancel()
                    true
                } else {
                    false
                }
            },
    ) {
        content()
        val item = state.dragging
        val at = state.pointer
        if (item != null && at != null) {
            val key = state.hovered
            val refusal = key?.let { state.refusal(it) }
            val hint = refusal ?: key?.let { state.labelOf(it) } ?: stringResource(Res.string.rd_i1_dnd_ghost_idle)
            Box(Modifier.offset { IntOffset(at.x.roundToInt() + 12, at.y.roundToInt() + 8) }) {
                if (ghost != null) ghost(item, hint, refusal != null) else DragGhostChip(itemLabel(item), hint, refusal != null)
            }
        }
    }
}

/** The default ghost: the item's name and what dropping here does (in the error color when the target refuses). */
@Composable
fun DragGhostChip(title: String, hint: String, refused: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 280.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (refused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inversePrimary,
                maxLines = 2,
            )
        }
    }
}

private class CoordsHolder {
    var coords: LayoutCoordinates? = null
}

/**
 * Makes [item] draggable onto the [dropTarget]s of [state]. Mouse: press and move. Touch: long-press then move ([touchLongPress] = false
 * turns touch dragging off, for phones that use the long-press for a sheet instead). Keyboard: Space picks the item (Space again or
 * Esc puts it back), then a target's [DropHereButton] drops it. TalkBack gets one custom action per valid target.
 * The dragged item fades in place; a picked one is outlined.
 *
 * ```kotlin
 * TitleCard(data, onClick = { open(id) }, modifier = Modifier.dragSource(dnd, id), context = CardContext.COMPACT)
 * ```
 */
@Composable
fun <T : Any> Modifier.dragSource(
    state: DragDropState<T>,
    item: T,
    enabled: Boolean = true,
    touchLongPress: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
): Modifier {
    val holder = remember { CoordsHolder() }
    val current by rememberUpdatedState(item)
    val isPicked = state.picked == item
    val isDragged = state.dragging == item
    val pickedColor = MaterialTheme.colorScheme.primary
    @Suppress("UNUSED_VARIABLE")
    val version = state.targetsVersion // re-read the targets for the accessibility actions below
    val actions = if (enabled) {
        state.targets.entries.filter { it.value.refuse(item) == null }.map { (key, target) ->
            CustomAccessibilityAction(target.label) {
                state.pick(item)
                state.dropOn(key)
            }
        }
    } else {
        emptyList()
    }
    return this
        .onGloballyPositioned { holder.coords = it }
        .then(if (isDragged) Modifier.alpha(0.4f) else Modifier)
        .then(if (isPicked) Modifier.border(2.dp, pickedColor, shape) else Modifier)
        .semantics { if (actions.isNotEmpty()) customActions = actions }
        .onPreviewKeyEvent { e ->
            if (!enabled || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                e.key == Key.Spacebar -> {
                    if (isPicked) state.cancel() else state.pick(item)
                    true
                }
                e.key == Key.Escape && isPicked -> {
                    state.cancel()
                    true
                }
                else -> false
            }
        }
        .pointerInput(state, enabled, touchLongPress) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val mouse = down.type == PointerType.Mouse
                if (mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val start: PointerInputChange? = when {
                    mouse -> awaitSlop(down.id, viewConfiguration.touchSlop)
                    touchLongPress -> awaitLongPressOrCancellation(down.id)
                    else -> null
                }
                val source = holder.coords
                if (start == null || source == null) return@awaitEachGesture
                val dragToken = state.beginDrag(current, state.toContainer(source, start.position))
                try {
                    while (true) {
                        // Initial pass: the card under the pointer never sees these events, so a drag never ends in a click.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == start.id } ?: break
                        change.consume()
                        if (!change.pressed) {
                            val m = event.keyboardModifiers
                            state.endDrag(dragToken, DropModifiers(copy = m.pointerCtrl || m.pointerMeta, move = m.pointerShift))
                            break
                        }
                        state.moveDrag(dragToken, state.toContainer(source, change.position))
                    }
                } finally {
                    state.abandonDrag(dragToken)
                }
            }
        }
}

/** Waits until the pointer travels past [slop] (consuming that move) or is released (null). Runs on the Initial pass. */
private suspend fun AwaitPointerEventScope.awaitSlop(id: PointerId, slop: Float): PointerInputChange? {
    var total = Offset.Zero
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == id } ?: return null
        if (!change.pressed) return null
        total += change.positionChange()
        if (total.getDistance() > slop) {
            change.consume()
            return change
        }
    }
}

/**
 * Makes this element a drop target of [state] under [key] (a status, a list id). [label] says what dropping does ("Move to Watching")
 * and is the ghost's hint and the TalkBack action. [refuse] returns why an item cannot come here ("Already in Watching"), or null.
 * While something is active the valid targets are outlined (filled when hovered) and the refused ones fade.
 *
 * ```kotlin
 * Column(Modifier.dropTarget(dnd, key = status, label = moveTo(status), refuse = { if (statusOf(it) == status) already else null },
 *     onDrop = { id, _ -> setStatus(id, status) })) { … }
 * ```
 */
@Composable
fun <T : Any> Modifier.dropTarget(
    state: DragDropState<T>,
    key: Any,
    label: String,
    onDrop: (item: T, modifiers: DropModifiers) -> Unit,
    refuse: (item: T) -> String? = { null },
    shape: Shape = MaterialTheme.shapes.large,
): Modifier {
    val entry = remember(state, key) { DropTargetEntry(label, refuse, onDrop) }
    SideEffect {
        entry.label = label
        entry.refuse = refuse
        entry.onDrop = onDrop
    }
    DisposableEffect(state, key) {
        state.targets[key] = entry
        state.targetsVersion++
        onDispose {
            if (state.targets[key] === entry) state.targets.remove(key)
            state.targetsVersion++
        }
    }
    val active = state.active
    val refusal = if (active != null) refuse(active) else null
    val hovered = state.hovered == key
    val primary = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val visual = when {
        active == null -> Modifier
        refusal != null -> Modifier.alpha(0.5f)
        hovered -> Modifier.background(over, shape).border(2.dp, primary, shape)
        else -> Modifier.border(1.dp, primary.copy(alpha = 0.6f), shape)
    }
    return this
        .onGloballyPositioned { coords ->
            val c = state.container
            entry.bounds = if (c != null && c.isAttached) c.localBoundingBoxOf(coords, clipBounds = true) else coords.boundsInRoot()
        }
        .then(visual)
        .semantics { if (refusal != null) stateDescription = refusal }
}

/**
 * The keyboard and touch way to drop: shows only while an item is picked or dragged; disabled with the reason when [targetKey] refuses it.
 *
 * ```kotlin
 * Row { Text(statusName); DropHereButton(dnd, status) }
 * ```
 */
@Composable
fun <T : Any> DropHereButton(state: DragDropState<T>, targetKey: Any, modifier: Modifier = Modifier) {
    if (state.active == null) return
    val refusal = state.refusal(targetKey)
    TextButton(
        onClick = { state.dropOn(targetKey) },
        enabled = refusal == null,
        modifier = modifier.semantics { if (refusal != null) stateDescription = refusal },
    ) { Text(stringResource(Res.string.rd_i1_dnd_drop_here), maxLines = 1) }
}

/**
 * The banner while an item is picked with the keyboard: "<item> picked. Choose a target with Tab and Enter", with Cancel. Announced
 * politely. Show it when `state.picked != null`.
 *
 * ```kotlin
 * dnd.picked?.let { PickedBanner(titleOf(it), onCancel = dnd::cancel) }
 * ```
 */
@Composable
fun PickedBanner(itemLabel: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.PanTool, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                stringResource(Res.string.rd_i1_dnd_picked, itemLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.rd_i1_dnd_cancel)) }
        }
    }
}

/**
 * Asks whether a title dropped on a list moves (leaves [sourceLabel]) or is copied (stays in both). Anchor it inside the target (a
 * `Box` around the list entry). [rememberChoice] is the "Do not ask again" box; the caller stores it and then passes the remembered
 * mode straight to its drop handler, as Ctrl (copy) and Shift (move) do.
 *
 * ```kotlin
 * Box { ListEntry(…); DropModeMenu(ask != null, title, listName, "For the weekend", onMove = { … }, onCopy = { … }, onDismiss = { ask = null },
 *     rememberChoice = remember, onRememberChoiceChange = { remember = it }) }
 * ```
 */
@Composable
fun DropModeMenu(
    expanded: Boolean,
    itemTitle: String,
    targetLabel: String,
    sourceLabel: String,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    rememberChoice: Boolean,
    onRememberChoiceChange: (Boolean) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            stringResource(Res.string.rd_i1_dnd_where, itemTitle, targetLabel),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 300.dp),
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(Res.string.rd_i1_dnd_move))
                    Text(
                        stringResource(Res.string.rd_i1_dnd_move_hint, sourceLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
            onClick = onMove,
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(Res.string.rd_i1_dnd_copy))
                    Text(
                        stringResource(Res.string.rd_i1_dnd_copy_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = onCopy,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.rd_i1_dnd_remember)) },
            leadingIcon = { Checkbox(checked = rememberChoice, onCheckedChange = null, modifier = Modifier.size(24.dp)) },
            onClick = { onRememberChoiceChange(!rememberChoice) },
        )
        Text(
            stringResource(Res.string.rd_i1_dnd_keys_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
