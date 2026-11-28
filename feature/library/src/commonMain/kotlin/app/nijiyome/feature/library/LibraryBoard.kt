// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.library.resources.Res
import app.nijiyome.feature.library.resources.*
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.ListEntry
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.patterns.DragDropContainer
import app.nijiyome.ui.patterns.DropHereButton
import app.nijiyome.ui.patterns.PickedBanner
import app.nijiyome.ui.patterns.dragSource
import app.nijiyome.ui.patterns.dropTarget
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.rememberDragDropState
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.scroll.NjLazyColumn

/**
 * Structure "Tablero" (`LibraryStructure.BOARD`): a kanban with one column per status. Drag a card to another column to change its
 * status (mouse: press and move; touch on wide windows: long-press and move). Never only by dragging: Space or the menu's "Tomar para
 * mover" picks a card and the column's "Soltar aquí" drops it; "Mover a" in the card's menu moves it directly; TalkBack gets one action
 * per column. On phones the board scrolls sideways and a long-press opens the card's sheet with "Mover a" (artifact). Every move can
 * be undone.
 */
@Composable
internal fun LibraryBoard(
    state: ListsUiState,
    continueWatching: List<HistoryEntry>,
    labels: LibraryLabels,
    actions: MyListActions,
    modifier: Modifier = Modifier,
) {
    val compact = isCompactWindow()
    val dnd = rememberDragDropState<String>()
    val entries = state.visible
    val byKey = entries.associateBy { it.key }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.query.isBlank() && continueWatching.isNotEmpty()) ContinueShelf(continueWatching, actions.play, Modifier.padding(top = 8.dp), statusOf = { state.statusOf(it) })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListFilterBar(state, labels, actions, showStatus = false)
            ListAccountsLine(state, actions.openAccounts)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (compact) Icons.Outlined.TouchApp else Icons.Outlined.OpenWith,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(if (compact) Res.string.rd_s7_board_hint_touch else Res.string.rd_s7_board_hint_wide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dnd.picked?.let { key -> PickedBanner(byKey[key]?.title.orEmpty(), onCancel = dnd::cancel) }
        }
        if (entries.isEmpty() && !state.hasAny) {
            MyListEmpty(state, actions)
            return@Column
        }
        if (entries.isEmpty()) {
            NoMatch(state, actions)
            return@Column
        }
        DragDropContainer(dnd, itemLabel = { byKey[it]?.title.orEmpty() }, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Row(
                Modifier
                    .fillMaxSize()
                    .decor(DecorRegion.GRID)
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LibraryStatusOrder.forEach { status ->
                    val column = entries.filter { it.status == status }
                    val label = labels.of(status)
                    val moveLabel = stringResource(Res.string.rd_s7_move_to_status, label)
                    val alreadyIn = stringResource(Res.string.rd_s7_already_in, label)
                    val columnName = stringResource(Res.string.rd_s7_column, label, column.size)
                    Column(
                        Modifier
                            .width(if (compact) 248.dp else 272.dp)
                            .fillMaxHeight()
                            .dropTarget(
                                dnd,
                                key = status,
                                label = moveLabel,
                                onDrop = { key, _ -> byKey[key]?.let { actions.move(listOf(it), status) } },
                                refuse = { key -> if (byKey[key]?.status == status) alreadyIn else null },
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                            .semantics { contentDescription = columnName }
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusMark(status.slot(), label, selected = true)
                            Spacer(Modifier.weight(1f))
                            Text(column.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropHereButton(dnd, status, Modifier.fillMaxWidth())
                        NjLazyColumn(
                            Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            scrollToTopOnReselect = false,
                        ) {
                            itemsIndexed(column, key = { _, e -> e.key }, contentType = { _, _ -> "entry" }) { i, e ->
                                BoardCard(e, i, labels, actions, onPick = { dnd.pick(e.key) }, cardModifier = Modifier.dragSource(dnd, e.key, touchLongPress = !compact))
                            }
                            if (column.isEmpty()) {
                                item(key = "empty-" + status.id, contentType = "empty") { EmptyColumn() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCard(
    entry: ListEntry,
    index: Int,
    labels: LibraryLabels,
    actions: MyListActions,
    onPick: () -> Unit,
    cardModifier: Modifier,
) {
    EntryCard(
        entry = entry,
        labels = labels,
        actions = actions,
        context = CardContext.COMPACT,
        show = CardShow(progress = true, status = false, next = false, score = true),
        index = index,
        onPick = onPick,
        cardModifier = cardModifier,
    )
}

/** "Suelta aquí": the dashed slot of an empty column. */
@Composable
private fun EmptyColumn() {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(Res.string.rd_s7_drop_here_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
