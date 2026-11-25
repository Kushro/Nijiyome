// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import app.nijiyome.ui.patterns.MenuEntriesDropdown
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.feature.library.resources.Res
import app.nijiyome.feature.library.resources.*
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.ListEntry
import app.nijiyome.model.ListProvider
import app.nijiyome.model.ListStatus
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.progressFraction as watchedFraction
import app.nijiyome.ui.patterns.ContextMenuArea
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuDivider
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.patterns.MenuSheetHeader
import app.nijiyome.ui.patterns.MenuSubmenu
import app.nijiyome.ui.patterns.icon
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

// Sparkles redesign, lane S7: pieces shared by the three structures of Library › Mi lista and by Favoritos.

/** Everything a structure can do with the list. One instance per screen; the structures only compose. */
@Immutable
internal class MyListActions(
    /** A card or row: Details when the title has an AniList id, else the editor. */
    val open: (ListEntry) -> Unit,
    val edit: (ListEntry) -> Unit,
    /** Status change with undo (drag, "Mover a", batch). */
    val move: (List<ListEntry>, ListStatus) -> Unit,
    val addEpisode: (List<ListEntry>) -> Unit,
    /** "Quitar de la lista" with undo. */
    val remove: (List<ListEntry>) -> Unit,
    val favorite: (ListEntry, Boolean) -> Unit,
    val play: (HistoryEntry) -> Unit,
    val setQuery: (String) -> Unit,
    val setFilter: (ListStatus?) -> Unit,
    val toggleSort: (ListSort) -> Unit,
    val clearFilters: () -> Unit,
    val openAccounts: () -> Unit,
    val explore: () -> Unit,
    val retry: () -> Unit,
)

/** Labels the non-composable lambdas need (menus, drop targets), resolved once per composition. */
@Immutable
internal class LibraryLabels(
    val status: Map<ListStatus, String>,
    val moveTo: String,
    val edit: String,
    val addFavorite: String,
    val removeFavorite: String,
    val remove: String,
    val pick: String,
) {
    fun of(status: ListStatus): String = this.status.getValue(status)
}

@Composable
internal fun rememberLibraryLabels(): LibraryLabels = LibraryLabels(
    status = ListStatus.entries.associateWith { stringResource(it.labelRes()) },
    moveTo = stringResource(Res.string.rd_s7_move_to),
    edit = stringResource(Res.string.rd_s7_edit_entry),
    addFavorite = stringResource(Res.string.rd_s7_add_favorite),
    removeFavorite = stringResource(Res.string.rd_s7_remove_favorite),
    remove = stringResource(Res.string.rd_s7_remove),
    pick = stringResource(Res.string.rd_s7_pick),
)

/** The unified context menu of an entry (right click, Shift+F10, ⋮, long-press sheet). [onPick] adds "Tomar para mover" (board). */
internal fun entryMenu(entry: ListEntry, labels: LibraryLabels, actions: MyListActions, onPick: (() -> Unit)? = null): List<MenuEntry> =
    buildList {
        add(
            MenuSubmenu(
                labels.moveTo,
                LibraryStatusOrder.filter { it != entry.status }.map { s ->
                    MenuAction(labels.of(s), onClick = { actions.move(listOf(entry), s) }, icon = s.slot().icon)
                },
                icon = Icons.Outlined.SwapHoriz,
            ),
        )
        if (onPick != null) add(MenuAction(labels.pick, onClick = onPick, icon = Icons.Outlined.PanTool))
        add(MenuAction(labels.edit, onClick = { actions.edit(entry) }, icon = Icons.Outlined.Edit))
        add(
            MenuAction(
                if (entry.favorite) labels.removeFavorite else labels.addFavorite,
                onClick = { actions.favorite(entry, !entry.favorite) },
                icon = if (entry.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            ),
        )
        add(MenuDivider)
        add(MenuAction(labels.remove, onClick = { actions.remove(listOf(entry)) }, icon = Icons.Outlined.Delete, destructive = true))
    }

/** "Ep 6 / 12" or "Ep 6". */
@Composable
internal fun ListEntry.progressText(): String =
    totalEpisodes?.let { stringResource(Res.string.library_lists_progress_of, progress, it) }
        ?: stringResource(Res.string.library_lists_progress, progress)

/** 0..100 as the artifact's "7.4". */
internal fun formatScore(score: Int): String = String.format(Locale.ROOT, "%.1f", score / 10.0)

/** Full card data of a list entry: status mark and label, progress, episodes seen / total, the next episode and the score. */
@Composable
internal fun ListEntry.cardData(labels: LibraryLabels): TitleCardData {
    val total = totalEpisodes
    val ongoing = status == ListStatus.WATCHING || status == ListStatus.REPEATING
    return TitleCardData(
        key = key,
        title = title,
        coverUrl = poster,
        subtitle = progressText(),
        progress = progressFraction(),
        listStatus = status.slot(),
        listStatusLabel = labels.of(status),
        nextLabel = if (ongoing && (total == null || progress < total)) stringResource(Res.string.rd_s7_next_episode, progress + 1) else null,
        score = score?.takeIf { it > 0 }?.let(::formatScore),
        episodesSeen = progress,
        episodesTotal = total,
    )
}

/** "Continuar" card of a history row: what plays next and how far the current episode went. */
@Composable
internal fun HistoryEntry.continueCardData(): TitleCardData = TitleCardData(
    key = "anime:" + (anilistId ?: malId ?: animeId),
    title = title,
    coverUrl = poster,
    subtitle = if (completed || progressSec <= 0) {
        stringResource(Res.string.library_episode, episode)
    } else {
        stringResource(Res.string.library_episode_at, episode, formatDuration(progressSec.toLong()))
    },
    progress = if (completed) null else watchedFraction(),
    nextLabel = stringResource(Res.string.rd_s7_next_episode, if (completed) episode + 1 else episode),
    episodesSeen = if (completed) episode else (episode - 1).coerceAtLeast(0),
)

/** A list entry drawn with the user's card style and the entry's context menu. */
@Composable
internal fun EntryCard(
    entry: ListEntry,
    labels: LibraryLabels,
    actions: MyListActions,
    context: CardContext,
    modifier: Modifier = Modifier,
    show: CardShow = CardShow(),
    index: Int = 0,
    onPick: (() -> Unit)? = null,
    cardModifier: Modifier = Modifier,
) {
    ContextMenuArea(
        entries = { entryMenu(entry, labels, actions, onPick) },
        modifier = modifier,
        sheetHeader = { MenuSheetHeader(entry.title) },
    ) { menu ->
        TitleCard(
            data = entry.cardData(labels),
            onClick = { actions.open(entry) },
            context = context,
            show = show,
            onLongClick = menu::showSheet,
            modifier = cardModifier.cardSway(index).cardToDetailsSource(entry.key),
            index = index,
        )
    }
}

/** Continuar: what was playing, at the top of the shelves (and above the board and the list). Plays on click. */
@Composable
internal fun ContinueShelf(entries: List<HistoryEntry>, onPlay: (HistoryEntry) -> Unit, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Column(modifier.decor(DecorRegion.TONIGHT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            stringResource(Res.string.rd_s7_continue),
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                Text(
                    stringResource(Res.string.rd_s7_continue_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        LazyRow(
            Modifier.fillMaxWidth().decor(DecorRegion.ROW),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> "c-" + e.episodeId }, contentType = { _, _ -> "continue" }) { i, e ->
                val data = e.continueCardData()
                TitleCard(
                    data = data,
                    onClick = { onPlay(e) },
                    context = CardContext.CONTINUE,
                    modifier = Modifier.cardSway(i),
                    onPlay = { onPlay(e) },
                    index = i,
                )
            }
        }
    }
}

/** Title filter, the sort menu when [showSort], and when [showStatus] the status chips with their counts ("Todo" first). */
@Composable
internal fun ListFilterBar(
    state: ListsUiState,
    labels: LibraryLabels,
    actions: MyListActions,
    showStatus: Boolean,
    modifier: Modifier = Modifier,
    showSort: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TitleFilterField(state, actions, Modifier.weight(1f, fill = false))
            if (showSort) SortMenuButton(state, actions)
        }
        if (showStatus) {
            val total = state.counts.values.sum()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { actions.setFilter(null) },
                    label = { Text(stringResource(Res.string.rd_s7_filter_all, total)) },
                )
                val colors = LocalStatusColors.current
                LibraryStatusOrder.filter { (state.counts[it] ?: 0) > 0 || state.filter == it }.forEach { s ->
                    val selected = state.filter == s
                    FilterChip(
                        selected = selected,
                        onClick = { actions.setFilter(if (selected) null else s) },
                        label = { Text(labels.of(s) + " · " + (state.counts[s] ?: 0)) },
                        leadingIcon = { Icon(s.slot().icon, contentDescription = null, tint = colors.of(s.slot()), modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

/** The sort of the shelves and the board (the list structure sorts from its column heads). A second pick of the same flips it. */
@Composable
private fun SortMenuButton(state: ListsUiState, actions: MyListActions) {
    var open by remember { mutableStateOf(false) }
    val names = ListSort.entries.associateWith { stringResource(it.sortLabelRes()) }
    val label = stringResource(Res.string.rd_s7_sort_by, names.getValue(state.sort))
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
        }
        MenuEntriesDropdown(
            expanded = open,
            entries = ListSort.entries.map { s ->
                MenuAction(names.getValue(s), onClick = { actions.toggleSort(s) }, checked = state.sort == s)
            },
            onDismiss = { open = false },
        )
    }
}

internal fun ListSort.sortLabelRes(): StringResource = when (this) {
    ListSort.TITLE -> Res.string.rd_s7_col_title
    ListSort.STATUS -> Res.string.rd_s7_col_status
    ListSort.PROGRESS -> Res.string.rd_s7_col_progress
    ListSort.SCORE -> Res.string.rd_s7_col_score
    ListSort.UPDATED -> Res.string.rd_s7_col_updated
}

@Composable
private fun TitleFilterField(state: ListsUiState, actions: MyListActions, modifier: Modifier) {
    Box(modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.setQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                {
                    IconButton(onClick = { actions.setQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.library_lists_clear_filter))
                    }
                }
            } else {
                null
            },
            placeholder = { Text(stringResource(Res.string.library_lists_filter)) },
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
    }
}

/** "Lista local · Vincular AniList o MAL" or the linked providers and the pending changes. */
@Composable
internal fun ListAccountsLine(state: ListsUiState, onOpenAccounts: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.accounts.isEmpty()) {
            Pill(stringResource(Res.string.library_lists_local), tone = PillTone.NEUTRAL)
            TextButton(onClick = onOpenAccounts) { Text(stringResource(Res.string.library_lists_link)) }
        } else {
            val anilist = stringResource(ListProvider.ANILIST.labelRes())
            val mal = stringResource(ListProvider.MAL.labelRes())
            Pill(state.accounts.joinToString(" · ") { if (it.provider == ListProvider.ANILIST) anilist else mal }, tone = PillTone.PRIMARY)
            if (state.pendingChanges > 0) {
                Pill(stringResource(Res.string.library_lists_pending, state.pendingChanges), tone = PillTone.WARN)
            }
        }
    }
}

/** Empty state of Library: the direction's art, a title, a message and the actions. */
@Composable
internal fun LibraryEmpty(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    kind: EmptyKind = EmptyKind.LIBRARY,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().decor(DecorRegion.EMPTY).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyArt(kind, Modifier.size(160.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Nothing matches the title filter or the status chip: the way back. */
@Composable
internal fun NoMatch(state: ListsUiState, actions: MyListActions, modifier: Modifier = Modifier) {
    LibraryEmpty(
        title = if (state.query.isNotBlank()) {
            stringResource(Res.string.rd_s7_no_match, state.query.trim())
        } else {
            stringResource(Res.string.rd_s7_no_status)
        },
        kind = EmptyKind.SEARCH,
        modifier = modifier,
    ) {
        androidx.compose.material3.FilledTonalButton(onClick = actions.clearFilters) {
            Text(stringResource(if (state.query.isNotBlank()) Res.string.rd_s7_clear_filter else Res.string.rd_s7_show_all))
        }
    }
}

/** The whole list is empty (before any filter): art, explanation and the ways to fill it. */
@Composable
internal fun MyListEmpty(state: ListsUiState, actions: MyListActions, modifier: Modifier = Modifier) {
    LibraryEmpty(
        title = stringResource(Res.string.rd_s7_empty_title),
        message = stringResource(if (state.accounts.isEmpty()) Res.string.library_lists_empty_local else Res.string.library_lists_empty_linked),
        modifier = modifier,
    ) {
        androidx.compose.material3.Button(onClick = actions.explore) { Text(stringResource(Res.string.library_explore)) }
        if (state.accounts.isEmpty()) {
            androidx.compose.material3.OutlinedButton(onClick = actions.openAccounts) { Text(stringResource(Res.string.library_lists_link)) }
        }
    }
}
