// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.library.resources.Res
import app.nijiyome.feature.library.resources.*
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.ListStatus
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.look.slot
import org.jetbrains.compose.resources.stringResource

/**
 * Structure "Estantes" (`LibraryStructure.SHELVES`): Continuar on top, the filter bar, then one collapsible shelf per status, each a
 * row of cards. Completed and Dropped start folded, as in the artifact.
 */
@Composable
internal fun LibraryShelves(
    state: ListsUiState,
    continueWatching: List<HistoryEntry>,
    labels: LibraryLabels,
    actions: MyListActions,
    modifier: Modifier = Modifier,
) {
    var collapsed by rememberSaveable { mutableStateOf(listOf(ListStatus.COMPLETED.id, ListStatus.DROPPED.id)) }
    val all = state.visible
    val groups = LibraryStatusOrder.map { s -> s to all.filter { it.status == s } }.filter { it.second.isNotEmpty() }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.query.isBlank() && continueWatching.isNotEmpty()) {
            item(key = "continue", contentType = "continue") { ContinueShelf(continueWatching, actions.play, statusOf = { state.statusOf(it) }) }
        }
        item(key = "filters", contentType = "filters") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListFilterBar(state, labels, actions, showStatus = true)
                ListAccountsLine(state, actions.openAccounts)
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                if (state.hasAny) NoMatch(state, actions) else MyListEmpty(state, actions)
            }
        }
        groups.forEach { (status, entries) ->
            item(key = "shelf-" + status.id, contentType = "shelf") {
                val folded = status.id in collapsed
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShelfHead(
                        status = status,
                        label = labels.of(status),
                        count = entries.size,
                        folded = folded,
                        onToggle = { collapsed = if (folded) collapsed - status.id else collapsed + status.id },
                    )
                    if (!folded) {
                        LazyRow(
                            Modifier.fillMaxWidth().decor(DecorRegion.ROW),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(entries, key = { _, e -> e.key }, contentType = { _, _ -> "entry" }) { i, e ->
                                EntryCard(
                                    entry = e,
                                    labels = labels,
                                    actions = actions,
                                    context = CardContext.ROW,
                                    show = CardShow(progress = true, status = false, next = status == ListStatus.WATCHING, score = true),
                                    index = i,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The head of a shelf: chevron, the status mark and the count; a button that folds the shelf. */
@Composable
private fun ShelfHead(status: ListStatus, label: String, count: Int, folded: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(if (folded) Res.string.rd_s7_folded else Res.string.rd_s7_unfolded)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = stateText
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (folded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusMark(status.slot(), label, selected = true)
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
