// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlusOne
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.motion.StatusChangeTransition
import app.nijiyome.feature.library.resources.Res
import app.nijiyome.feature.library.resources.*
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.ListEntry
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.ListStatusPicker
import app.nijiyome.ui.patterns.SelectionAction
import app.nijiyome.ui.patterns.SelectionActionBar
import app.nijiyome.ui.patterns.SelectionCheckbox
import app.nijiyome.ui.patterns.SelectionState
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.multiSelectable
import app.nijiyome.ui.patterns.rememberSelectionState
import app.nijiyome.ui.patterns.selectionKeys
import app.nijiyome.ui.patterns.toListStatus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Structure "Lista" (`LibraryStructure.LIST`): a dense list sorted by its column heads, with multi-selection (Ctrl / Shift click,
 * long-press, Space, Ctrl+A, Esc) and a batch bar: change status, +1 episode, remove. Every batch action can be undone.
 * Wide windows draw a table with a checkbox column; phones a list with a sort chip and "Seleccionar".
 */
@Composable
internal fun LibraryList(
    state: ListsUiState,
    continueWatching: List<HistoryEntry>,
    labels: LibraryLabels,
    actions: MyListActions,
    modifier: Modifier = Modifier,
) {
    val compact = isCompactWindow()
    val sel = rememberSelectionState<String>()
    val rows = state.visible
    val keys = rows.map { it.key }
    // Rows that left the view (filter, move) leave the selection too.
    LaunchedEffect(keys) { sel.retainOnly(keys) }
    val selectedRows = { rows.filter { sel.isSelected(it.key) } }
    var statusSheet by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().selectionKeys(sel) { keys },
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        ) {
            if (state.query.isBlank() && continueWatching.isNotEmpty()) {
                item(key = "continue", contentType = "continue") { ContinueShelf(continueWatching, actions.play, Modifier.padding(bottom = 16.dp)) }
            }
            item(key = "filters", contentType = "filters") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListFilterBar(state, labels, actions, showStatus = true, showSort = false)
                    ListAccountsLine(state, actions.openAccounts)
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty", contentType = "empty") { if (state.hasAny) NoMatch(state, actions) else MyListEmpty(state, actions) }
            } else {
                item(key = "head", contentType = "head") {
                    if (compact) {
                        PhoneSortLine(state, rows.size, sel, actions)
                    } else {
                        TableHead(state, sel, keys, actions)
                    }
                }
                items(rows, key = { it.key }, contentType = { "row" }) { e ->
                    if (compact) PhoneRow(e, sel, keys, labels, actions) else TableRow(e, sel, keys, labels, actions)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        val count = sel.count
        SelectionActionBar(
            visible = sel.active,
            count = count,
            actions = listOf(
                SelectionAction(
                    stringResource(Res.string.rd_s7_change_status),
                    Icons.Outlined.SwapHoriz,
                    onClick = { statusSheet = true },
                    enabled = count > 0,
                    emphasis = ActionEmphasis.FILLED,
                ),
                SelectionAction(
                    stringResource(Res.string.rd_s7_plus_one),
                    Icons.Outlined.PlusOne,
                    onClick = { actions.addEpisode(selectedRows()) },
                    enabled = count > 0,
                ),
                SelectionAction(
                    stringResource(Res.string.rd_s7_remove),
                    Icons.Outlined.Delete,
                    onClick = {
                        actions.remove(selectedRows())
                        sel.exit()
                    },
                    enabled = count > 0,
                    emphasis = ActionEmphasis.DANGER,
                    confirmLabel = stringResource(Res.string.rd_s7_remove_n, count),
                ),
            ),
            onExit = sel::exit,
            onSelectAll = { sel.selectAll(keys) },
            selectAllLabel = stringResource(Res.string.rd_s7_select_all),
            emptyHint = stringResource(Res.string.rd_s7_select_hint),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    if (statusSheet) {
        AdaptiveSheet(onDismissRequest = { statusSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.rd_s7_batch_status_title, sel.count), style = MaterialTheme.typography.titleLarge)
                ListStatusPicker(
                    selected = null,
                    onSelect = { slot ->
                        actions.move(selectedRows(), slot.toListStatus())
                        statusSheet = false
                        sel.exit()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    labelOf = { labels.of(it.toListStatus()) },
                )
            }
        }
    }
}

/** Phone: "12 títulos", the sort chip (cycles the columns; a second tap on the same flips) and Seleccionar / Listo. */
@Composable
private fun PhoneSortLine(state: ListsUiState, count: Int, sel: SelectionState<String>, actions: MyListActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            pluralStringResource(Res.plurals.rd_s7_titles, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        val next = ListSort.entries[(state.sort.ordinal + 1) % ListSort.entries.size]
        val sortName = stringResource(state.sort.columnRes())
        val sortLabel = stringResource(Res.string.rd_s7_sort_by, sortName)
        AssistChip(
            onClick = { actions.toggleSort(next) },
            label = { Text(sortName) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                Icon(
                    if (state.sortReversed) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            modifier = Modifier.semantics { contentDescription = sortLabel },
        )
        if (sel.active) {
            TextButton(onClick = sel::exit) { Text(stringResource(Res.string.rd_s7_done)) }
        } else {
            TextButton(onClick = { sel.start() }) { Text(stringResource(Res.string.rd_s7_select)) }
        }
    }
}

/** Wide: the table head with the "select all" box and the sortable columns. */
@Composable
private fun TableHead(state: ListsUiState, sel: SelectionState<String>, keys: List<String>, actions: MyListActions) {
    val all = keys.isNotEmpty() && keys.all { sel.isSelected(it) }
    val selectAll = stringResource(Res.string.rd_s7_select_all)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .toggleable(value = all, role = Role.Checkbox, onValueChange = { if (all) sel.clear() else sel.selectAll(keys) })
                .semantics { contentDescription = selectAll },
            contentAlignment = Alignment.Center,
        ) { SelectionCheckbox(selected = all, visible = true) }
        SortHead(ListSort.TITLE, state, actions, Modifier.weight(3f))
        SortHead(ListSort.STATUS, state, actions, Modifier.weight(1.4f))
        SortHead(ListSort.PROGRESS, state, actions, Modifier.weight(1.4f))
        SortHead(ListSort.SCORE, state, actions, Modifier.weight(0.9f))
        SortHead(ListSort.UPDATED, state, actions, Modifier.weight(1.1f))
        Spacer(Modifier.size(48.dp))
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SortHead(sort: ListSort, state: ListsUiState, actions: MyListActions, modifier: Modifier) {
    val current = state.sort == sort
    val name = stringResource(sort.columnRes())
    val direction = when {
        !current -> null
        state.sortReversed -> stringResource(Res.string.rd_s7_sort_reversed)
        else -> stringResource(Res.string.rd_s7_sort_natural)
    }
    Box(modifier) {
        TextButton(
            onClick = { actions.toggleSort(sort) },
            modifier = Modifier.semantics { if (direction != null) stateDescription = direction },
        ) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(
                when {
                    !current -> Icons.Outlined.UnfoldMore
                    state.sortReversed -> Icons.Outlined.ArrowUpward
                    else -> Icons.Outlined.ArrowDownward
                },
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(16.dp),
            )
        }
    }
}

private fun ListSort.columnRes() = sortLabelRes()

/** A click opens the title (or toggles while selecting); long-press and Ctrl / Shift click select. */
private fun rowClick(entry: ListEntry, sel: SelectionState<String>, actions: MyListActions): () -> Unit =
    { if (sel.active) sel.toggle(entry.key) else actions.open(entry) }

@Composable
private fun TableRow(entry: ListEntry, sel: SelectionState<String>, keys: List<String>, labels: LibraryLabels, actions: MyListActions) {
    val selected = sel.isSelected(entry.key)
    val selectLabel = stringResource(Res.string.rd_s7_select_title, entry.title)
    RowFrame(entry, sel, keys, actions) {
        Box(
            Modifier
                .size(48.dp)
                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { sel.toggle(entry.key) })
                .semantics { contentDescription = selectLabel },
            contentAlignment = Alignment.Center,
        ) { SelectionCheckbox(selected = selected, visible = true) }
        Box(Modifier.weight(3f)) {
            TitleCard(
                data = entry.cardData(labels),
                onClick = rowClick(entry, sel, actions),
                context = CardContext.COMPACT,
                show = CardShow(progress = false, status = false, next = false, score = false),
                onLongClick = { sel.toggle(entry.key) },
            )
        }
        Box(Modifier.weight(1.4f)) {
            StatusChangeTransition(entry.status) { s -> StatusMark(s.slot(), labels.of(s), selected = true) }
        }
        Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ProgressTrack(entry.progressFraction() ?: 0f, Modifier.fillMaxWidth(), segments = entry.totalEpisodes)
            Text(
                entry.totalEpisodes?.let { "${entry.progress} / $it" } ?: entry.progress.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            entry.score?.takeIf { it > 0 }?.let(::formatScore) ?: stringResource(Res.string.rd_s7_no_score),
            style = MaterialTheme.typography.bodyMedium,
            color = if ((entry.score ?: 0) > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
        )
        Text(
            relativeTime(entry.updatedAtMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ContextMenuButton(
            entries = { entryMenu(entry, labels, actions) },
            contentDescription = stringResource(Res.string.rd_s7_more_for, entry.title),
        )
    }
}

@Composable
private fun PhoneRow(entry: ListEntry, sel: SelectionState<String>, keys: List<String>, labels: LibraryLabels, actions: MyListActions) {
    RowFrame(entry, sel, keys, actions) {
        SelectionCheckbox(selected = sel.isSelected(entry.key), visible = sel.active, modifier = Modifier.padding(end = 4.dp))
        Box(Modifier.weight(1f)) {
            TitleCard(
                data = entry.cardData(labels),
                onClick = rowClick(entry, sel, actions),
                context = CardContext.COMPACT,
                show = CardShow(progress = false, status = false, next = false, score = false),
                onLongClick = { sel.toggle(entry.key) },
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusChangeTransition(entry.status) { s -> StatusMark(s.slot(), labels.of(s), selected = true) }
            Text(entry.progressText(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The selectable row around a table or phone row. */
@Composable
private fun RowFrame(
    entry: ListEntry,
    sel: SelectionState<String>,
    keys: List<String>,
    actions: MyListActions,
    content: @Composable RowScope.() -> Unit,
) {
    val selected = sel.isSelected(entry.key)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .multiSelectable(sel, entry.key, order = { keys }, onOpen = { actions.open(entry) })
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
