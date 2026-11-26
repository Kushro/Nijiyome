// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalFoundationApi::class)

package app.nijiyome.ui.patterns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_sel_exit
import app.nijiyome.ui.resources.rd_i1_sel_open
import app.nijiyome.ui.resources.rd_i1_sel_select
import app.nijiyome.ui.resources.rd_i1_sel_select_all
import app.nijiyome.ui.resources.rd_i1_sel_select_span
import app.nijiyome.ui.resources.rd_i1_sel_select_unwatched
import app.nijiyome.ui.resources.rd_i1_sel_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.input.pointer.isCtrlPressed as pointerCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as pointerMeta
import androidx.compose.ui.input.pointer.isShiftPressed as pointerShift
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane I1. Pattern `eps` of docs/redesign/artefacto/lab.js: a visible selection mode with an anchor.
// Desktop: click / Ctrl+click toggles, Shift+click adds the range from the anchor, Ctrl+A takes everything selectable, Esc leaves.
// Touch: long-press starts the selection, a tap adds or removes, a long-press on another item while selecting adds the range.

/**
 * Selection of items of key [K] (episode numbers, download ids...). Holds the selected keys, the anchor of range selection and whether
 * the selection mode is on (it can be on with nothing selected: "choose episodes").
 *
 * ```kotlin
 * val selection = rememberSelectionState<Int>()
 * selection.toggle(3); selection.extendTo(7, order = aired)   // 3..7
 * if (selection.active) SelectionActionBar(visible = true, count = selection.count, actions = …, onExit = selection::exit)
 * ```
 */
@Stable
class SelectionState<K>(initial: Set<K> = emptySet()) {
    /** The selected keys. */
    var selected: Set<K> by mutableStateOf(initial)
        private set

    /** Where a range starts: the last key toggled on. */
    var anchor: K? by mutableStateOf(null)
        private set

    /** The selection mode is on (checkboxes and the action bar show). */
    var active: Boolean by mutableStateOf(initial.isNotEmpty())
        private set

    val count: Int get() = selected.size

    fun isSelected(key: K): Boolean = key in selected

    /** Turns the selection mode on, optionally selecting [key] as the anchor. */
    fun start(key: K? = null) {
        active = true
        if (key != null) {
            selected = selected + key
            anchor = key
        }
    }

    /** Adds or removes [key] and makes it the anchor. Turns the mode on. */
    fun toggle(key: K) {
        active = true
        selected = if (key in selected) selected - key else selected + key
        anchor = key
    }

    /** Adds every key of [order] between the anchor and [key] (both included). Without an anchor in [order] it toggles [key]. */
    fun extendTo(key: K, order: List<K>) {
        val from = anchor?.let { order.indexOf(it) } ?: -1
        val to = order.indexOf(key)
        if (from < 0 || to < 0) {
            toggle(key)
            return
        }
        active = true
        selected = selected + order.subList(minOf(from, to), maxOf(from, to) + 1)
    }

    /** Fills the gaps between the first and the last selected key of [order] (the "range" button of the touch bar). */
    fun fillSpan(order: List<K>) {
        val indices = selected.map { order.indexOf(it) }.filter { it >= 0 }
        if (indices.size < 2) return
        selected = selected + order.subList(indices.min(), indices.max() + 1)
    }

    /** Selects all of [keys] (what is selectable: aired episodes, not unaired ones). */
    fun selectAll(keys: Collection<K>) {
        active = true
        selected = keys.toSet()
    }

    /** Selects exactly the keys of [keys] that match [predicate] ("select unwatched"). */
    fun selectWhere(keys: Collection<K>, predicate: (K) -> Boolean) {
        active = true
        selected = keys.filter(predicate).toSet()
    }

    /** Deselects everything; the mode stays on. */
    fun clear() {
        selected = emptySet()
        anchor = null
    }

    /** Deselects everything and leaves the selection mode. */
    fun exit() {
        clear()
        active = false
    }

    /** Drops keys that no longer exist (after a refresh). */
    fun retainOnly(keys: Collection<K>) {
        val keep = keys.toSet()
        if (selected.any { it !in keep }) selected = selected.filter { it in keep }.toSet()
        if (anchor != null && anchor !in keep) anchor = null
    }
}

/** A [SelectionState] kept across recompositions. It does not survive process death: a selection is short-lived. */
@Composable
fun <K> rememberSelectionState(): SelectionState<K> = remember { SelectionState() }

private class PressModifiers {
    var shift = false
    var ctrl = false

    fun reset() {
        shift = false
        ctrl = false
    }
}

/**
 * Makes an item (an episode row, a download) part of a multi-selection. It replaces the item's own `clickable`:
 * - outside the selection mode a click calls [onOpen]; Ctrl+click or Shift+click start the mode;
 * - inside it a click toggles and Shift+click adds the range from the anchor ([order] = the selectable keys in display order);
 * - long-press (touch and mouse) starts the mode on this item, or adds the range when the mode is already on;
 * - Space toggles and Shift+Space adds the range; Enter acts as a click.
 * [enabled] = false (an unaired episode) keeps it out of the selection; a click then calls [onDisabledClick].
 *
 * ```kotlin
 * EpisodeRow(ep, Modifier.multiSelectable(selection, ep.number, order = { airedNumbers }, enabled = ep.aired, onOpen = { play(ep) }))
 * ```
 */
@Composable
fun <K> Modifier.multiSelectable(
    state: SelectionState<K>,
    key: K,
    order: () -> List<K>,
    onOpen: () -> Unit,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
): Modifier {
    val mods = remember { PressModifiers() }
    val openLabel = stringResource(Res.string.rd_i1_sel_open)
    val selectLabel = stringResource(Res.string.rd_i1_sel_select)
    val isSelected = state.isSelected(key)
    return this
        .pointerInput(Unit) {
            // Records the keyboard modifiers of the press without consuming it; the click below reads them.
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        val m = event.keyboardModifiers
                        mods.shift = m.pointerShift
                        mods.ctrl = m.pointerCtrl || m.pointerMeta
                    }
                }
            }
        }
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key != Key.Spacebar || !enabled) return@onPreviewKeyEvent false
            if (e.isShiftPressed) state.extendTo(key, order()) else state.toggle(key)
            true
        }
        .semantics { selected = isSelected }
        .combinedClickable(
            onClickLabel = if (state.active) selectLabel else openLabel,
            onLongClickLabel = selectLabel,
            onLongClick = {
                // combinedClickable already gives the long-press haptic.
                if (enabled) {
                    when {
                        state.active && state.anchor != null && !isSelected -> state.extendTo(key, order())
                        !isSelected -> state.toggle(key)
                        else -> state.start()
                    }
                }
            },
            onClick = {
                val shift = mods.shift
                val ctrl = mods.ctrl
                mods.reset()
                when {
                    !enabled -> onDisabledClick?.invoke()
                    shift -> state.extendTo(key, order())
                    state.active || ctrl -> state.toggle(key)
                    else -> onOpen()
                }
            },
        )
}

/**
 * Keyboard of a selectable list, on its container: Ctrl+A selects [selectable], Esc leaves the selection mode.
 *
 * ```kotlin
 * LazyColumn(Modifier.selectionKeys(selection) { airedNumbers }) { … }
 * ```
 */
fun <K> Modifier.selectionKeys(state: SelectionState<K>, selectable: () -> Collection<K>): Modifier = onKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
    when {
        e.key == Key.Escape && state.active -> {
            state.exit()
            true
        }
        e.key == Key.A && (e.isCtrlPressed || e.isMetaPressed) -> {
            state.selectAll(selectable())
            true
        }
        else -> false
    }
}

/** The checkbox drawn at the start of a selectable row while the mode is on (the row carries the semantics). */
@Composable
fun SelectionCheckbox(selected: Boolean, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Icon(
        if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
        contentDescription = null,
        modifier = modifier.size(24.dp),
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Weight of a button in an action bar. */
enum class ActionEmphasis { FILLED, TONAL, OUTLINED, DANGER }

/**
 * One batch action of the selection ("Mark 3 as watched", "Download 2"). The label is final (counts included). With [confirmLabel] the
 * first press turns the button into a danger button with that text and the second press runs [onClick] ("Remove" then "Remove 2
 * downloads"); prefer an undo instead when the action can be reverted.
 */
@Immutable
data class SelectionAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val emphasis: ActionEmphasis = ActionEmphasis.TONAL,
    val confirmLabel: String? = null,
)

/**
 * The batch bar of a selection. Wide windows: a floating pill at the bottom (place it with `Modifier.align(Alignment.BottomCenter)`
 * over the list so the list does not jump); phones ([compact]): a bottom toolbar of icon-over-label buttons. It enters with the rise
 * motion of the active level.
 *
 * ```kotlin
 * SelectionActionBar(
 *     visible = selection.active, count = selection.count, onExit = selection::exit,
 *     actions = listOf(SelectionAction(markLabel, Icons.Outlined.Visibility, onClick = { mark(selection.selected) }, emphasis = ActionEmphasis.FILLED)),
 *     onSelectAll = { selection.selectAll(aired) }, selectAllLabel = allLabel, modifier = Modifier.align(Alignment.BottomCenter),
 * )
 * ```
 */
@Composable
fun SelectionActionBar(
    visible: Boolean,
    count: Int,
    actions: List<SelectionAction>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectAll: (() -> Unit)? = null,
    selectAllLabel: String? = null,
    /** Shown instead of the count while nothing is selected ("Choose episodes: click or Shift+click"). */
    emptyHint: String? = null,
    compact: Boolean = isCompactWindow(),
) {
    var confirming by remember { mutableStateOf<String?>(null) }
    // A pending confirmation does not survive a change of the selection.
    LaunchedEffect(count) { confirming = null }
    AnimatedVisibility(visible = visible, modifier = modifier, enter = riseEnter(), exit = riseExit()) {
        val countText = if (count == 0 && emptyHint != null) emptyHint else pluralStringResource(Res.plurals.rd_i1_sel_count, count, count)
        if (compact) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        val armed = action.confirmLabel != null && confirming == action.label
                        CompactActionButton(
                            action = action,
                            armed = armed,
                            enabled = action.enabled && count > 0,
                            onClick = {
                                if (action.confirmLabel != null && !armed) confirming = action.label else {
                                    confirming = null
                                    action.onClick()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        countText,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.padding(end = 8.dp),
                        maxLines = 1,
                    )
                    actions.forEach { action ->
                        val armed = action.confirmLabel != null && confirming == action.label
                        ActionButton(
                            label = if (armed) action.confirmLabel.orEmpty() else action.label,
                            icon = action.icon,
                            emphasis = if (armed) ActionEmphasis.DANGER else action.emphasis,
                            enabled = action.enabled && count > 0,
                            onClick = {
                                if (action.confirmLabel != null && !armed) confirming = action.label else {
                                    confirming = null
                                    action.onClick()
                                }
                            },
                        )
                    }
                    if (onSelectAll != null) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(shape = NijiyomeControls.shape, onClick = onSelectAll) { Text(selectAllLabel ?: stringResource(Res.string.rd_i1_sel_select_all), maxLines = 1) }
                    }
                    IconButton(onClick = onExit) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_i1_sel_exit))
                    }
                }
            }
        }
    }
}

/** A button of the given [emphasis] with a leading icon; shared by the patterns' bars and dialogs. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasis: ActionEmphasis = ActionEmphasis.TONAL,
    enabled: Boolean = true,
) {
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (emphasis) {
        ActionEmphasis.FILLED -> Button(shape = NijiyomeControls.shape, onClick = onClick, modifier = modifier, enabled = enabled) { content() }
        ActionEmphasis.TONAL -> FilledTonalButton(shape = NijiyomeControls.shape, onClick = onClick, modifier = modifier, enabled = enabled) { content() }
        ActionEmphasis.OUTLINED -> OutlinedButton(shape = NijiyomeControls.shape, onClick = onClick, modifier = modifier, enabled = enabled) { content() }
        ActionEmphasis.DANGER -> Button(
            shape = NijiyomeControls.shape,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { content() }
    }
}

@Composable
private fun CompactActionButton(action: SelectionAction, armed: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        armed || action.emphasis == ActionEmphasis.DANGER -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    TextButton(shape = NijiyomeControls.shape, onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(action.icon, contentDescription = null, tint = color)
            Text(
                if (armed) action.confirmLabel.orEmpty() else action.label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The contextual top bar of a phone while selecting: close, the live count, and the range / all / unwatched shortcuts. Put it in
 * place of the screen's app bar while `selection.active`.
 *
 * ```kotlin
 * if (selection.active) SelectionTopBar(selection.count, onExit = selection::exit, onSelectSpan = { selection.fillSpan(aired) }, onSelectAll = { selection.selectAll(aired) })
 * ```
 */
@Composable
fun SelectionTopBar(
    count: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectSpan: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onSelectUnwatched: (() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_i1_sel_exit)) }
            Text(
                pluralStringResource(Res.plurals.rd_i1_sel_count, count, count),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                maxLines = 1,
            )
            if (onSelectSpan != null) {
                IconButton(onClick = onSelectSpan, enabled = count > 1) {
                    Icon(Icons.Outlined.LinearScale, contentDescription = stringResource(Res.string.rd_i1_sel_select_span))
                }
            }
            if (onSelectUnwatched != null) {
                IconButton(onClick = onSelectUnwatched) {
                    Icon(Icons.Outlined.VisibilityOff, contentDescription = stringResource(Res.string.rd_i1_sel_select_unwatched))
                }
            }
            if (onSelectAll != null) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(Res.string.rd_i1_sel_select_all))
                }
            }
        }
    }
}
