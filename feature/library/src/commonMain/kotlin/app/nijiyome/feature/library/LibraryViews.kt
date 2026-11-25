// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.feature.library.resources.Res
import app.nijiyome.feature.library.resources.*
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.ListEntry
import app.nijiyome.model.LoadState
import app.nijiyome.model.LibraryStructure
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.toResumeArgs
import app.nijiyome.ui.patterns.rememberShowUndo
import app.nijiyome.ui.state.LoadStateContent
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_retry
import app.nijiyome.ui.resources.list_queued_offline

/** The list's write outcomes on the app snackbar ("Se sincronizará", "No se pudo guardar"). */
@Composable
internal fun ListsEvents(vm: ListsViewModel) {
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                ListsEvent.Queued -> snackbar.show(getString(UiR.string.list_queued_offline))
                is ListsEvent.Failed -> snackbar.show(getString(Res.string.library_lists_write_failed))
            }
        }
    }
}

/** Builds the actions of Mi lista / Favoritos over [vm]: every move and removal shows an undo on the app snackbar. */
@Composable
internal fun rememberMyListActions(vm: ListsViewModel, labels: LibraryLabels, screen: LibraryActions, onEdit: (ListEntry) -> Unit): MyListActions {
    val showUndo = rememberShowUndo()
    val scope = rememberCoroutineScope()
    return remember(vm, labels, screen, showUndo, onEdit) {
        MyListActions(
            open = { e -> e.ref.anilistId?.let(screen.onOpenDetails) ?: onEdit(e) },
            edit = onEdit,
            move = { entries, status ->
                val changed = entries.filter { it.status != status }
                if (changed.isNotEmpty()) {
                    vm.moveTo(changed, status)
                    scope.launch {
                        val message = if (changed.size == 1) {
                            getString(Res.string.rd_s7_moved, changed.first().title, labels.of(status))
                        } else {
                            getString(Res.string.rd_s7_moved_many, changed.size, labels.of(status))
                        }
                        showUndo(message) { vm.restore(changed) }
                    }
                }
            },
            addEpisode = { entries ->
                if (entries.isNotEmpty()) {
                    vm.addEpisode(entries)
                    scope.launch { showUndo(getString(Res.string.rd_s7_plus_one_done, entries.size)) { vm.restore(entries) } }
                }
            },
            remove = { entries ->
                if (entries.isNotEmpty()) {
                    vm.remove(entries)
                    scope.launch {
                        val message = if (entries.size == 1) {
                            getString(Res.string.rd_s7_removed, entries.first().title)
                        } else {
                            getString(Res.string.rd_s7_removed_many, entries.size)
                        }
                        showUndo(message) { vm.restore(entries) }
                    }
                }
            },
            favorite = { e, on -> vm.setFavorite(e, on) },
            play = { h -> screen.onPlay(h.toResumeArgs()) },
            setQuery = vm::setQuery,
            setFilter = vm::setFilter,
            toggleSort = vm::toggleSort,
            clearFilters = vm::clearFilters,
            openAccounts = screen.onOpenAccounts,
            explore = screen.onExplore,
            retry = vm::retry,
        )
    }
}

/** The editor sheet of [entry] wired to [vm]; removal goes through [actions] so it can be undone. */
@Composable
internal fun EntryEditorFor(entry: ListEntry, vm: ListsViewModel, actions: MyListActions, onDismiss: () -> Unit) {
    ListEntryEditor(
        entry = entry,
        onDismiss = onDismiss,
        onStatus = { s -> if (s == null) actions.remove(listOf(entry)) else vm.setStatus(entry, s) },
        onProgress = { vm.setProgress(entry.ref, it) },
        onScore = { vm.setScore(entry.ref, it) },
        onFavorite = { vm.setFavorite(entry, it) },
        onRemove = {
            actions.remove(listOf(entry))
            onDismiss()
        },
    )
}

/**
 * Library › Mi lista with the lists service: the structure the user picked in Ajustes › Apariencia, switched once here. The three
 * share this view model, these actions, the editor and the undo.
 */
@Composable
internal fun MyListPane(continueWatching: LoadState<List<HistoryEntry>>, screen: LibraryActions, modifier: Modifier = Modifier) {
    val vm: ListsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    ListsEvents(vm)
    val labels = rememberLibraryLabels()
    var editingKey by remember { mutableStateOf<String?>(null) }
    val actions = rememberMyListActions(vm, labels, screen, onEdit = { editingKey = it.key })
    val resume = (continueWatching as? LoadState.Ok)?.value.orEmpty()

    when (val all = state.all) {
        LoadState.Loading -> Box(modifier.fillMaxSize()) { DelayedLoading() }
        is LoadState.Err -> ErrorState(
            message = all.cause.userMessage(),
            retryLabel = stringResource(UiR.string.ui_retry),
            onRetry = vm::retry,
            title = stringResource(Res.string.library_lists_error),
            modifier = modifier.fillMaxSize(),
        )
        is LoadState.Ok -> when (structures.library) {
            LibraryStructure.SHELVES -> LibraryShelves(state, resume, labels, actions, modifier)
            LibraryStructure.BOARD -> LibraryBoard(state, resume, labels, actions, modifier)
            LibraryStructure.LIST -> LibraryList(state, resume, labels, actions, modifier)
        }
    }
    // The editor follows the live entry, so a change made elsewhere (sync, undo) shows at once.
    val editing = editingKey?.let { k -> (state.all as? LoadState.Ok)?.value?.firstOrNull { it.key == k } }
    if (editing != null) EntryEditorFor(editing, vm, actions, onDismiss = { editingKey = null })
}

/** Library › Mi lista without the lists service (no AniList / MAL layer bound): Continuar as a grid, as before. */
@Composable
internal fun ContinueOnlyPane(continueWatching: LoadState<List<HistoryEntry>>, screen: LibraryActions, modifier: Modifier = Modifier) {
    LoadStateContent(state = continueWatching, onRetry = screen.onRetry, loading = { DelayedLoading() }) { entries ->
        if (entries.isEmpty()) {
            LibraryEmpty(
                title = stringResource(Res.string.library_empty_title),
                message = stringResource(Res.string.library_empty_message),
                modifier = modifier,
            ) { Button(onClick = screen.onExplore) { Text(stringResource(Res.string.library_explore)) } }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = modifier.fillMaxSize().decor(DecorRegion.GRID),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "head", span = { GridItemSpan(maxLineSpan) }, contentType = "head") {
                    SectionHeader(stringResource(Res.string.rd_s7_continue))
                }
                itemsIndexed(entries, key = { _, e -> "c-" + e.episodeId }, contentType = { _, _ -> "entry" }) { i, e ->
                    TitleCard(
                        data = e.continueCardData(),
                        onClick = { screen.onPlay(e.toResumeArgs()) },
                        context = CardContext.GRID,
                        modifier = Modifier.cardSway(i),
                        index = i,
                    )
                }
            }
        }
    }
}

/** Library › Historial: one row per watched episode, newest first; a row opens the title (or resumes without an AniList id). */
@Composable
internal fun HistoryPane(history: LoadState<List<HistoryEntry>>, screen: LibraryActions, modifier: Modifier = Modifier) {
    LoadStateContent(state = history, onRetry = screen.onRetry, loading = { DelayedLoading() }) { entries ->
        if (entries.isEmpty()) {
            LibraryEmpty(
                title = stringResource(Res.string.library_history_empty_title),
                message = stringResource(Res.string.library_history_empty_message),
                modifier = modifier,
            ) { Button(onClick = screen.onExplore) { Text(stringResource(Res.string.library_explore)) } }
        } else {
            LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                item(key = "head", contentType = "head") {
                    Text(
                        pluralStringResource(Res.plurals.rd_s7_episodes, entries.size, entries.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(entries, key = { "h-" + it.episodeId + "-" + it.watchedAt.toEpochMilli() }, contentType = { "entry" }) { entry ->
                    HistoryRow(entry, onClick = { entry.anilistId?.let(screen.onOpenDetails) ?: screen.onPlay(entry.toResumeArgs()) })
                }
            }
        }
    }
}

/** Poster, title and Visto, "Episodio N · visto hace 2 h", chevron. */
@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaCover(url = entry.poster, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(Res.string.library_watched_on, entry.episode, relativeTime(entry.watchedAt.toEpochMilli())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.completed) Pill(stringResource(Res.string.library_watched), tone = PillTone.OK)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Library › Favoritos: favorite characters (remove with undo) and favorite titles as a grid with the entry menu and the editor. */
@Composable
internal fun FavoritesPane(screen: LibraryActions, modifier: Modifier = Modifier) {
    val vm: ListsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    ListsEvents(vm)
    val labels = rememberLibraryLabels()
    var editingKey by remember { mutableStateOf<String?>(null) }
    val actions = rememberMyListActions(vm, labels, screen, onEdit = { editingKey = it.key })
    val showUndo = rememberShowUndo()
    val scope = rememberCoroutineScope()

    when (val favorites = state.favorites) {
        LoadState.Loading -> Box(modifier.fillMaxSize()) { DelayedLoading() }
        is LoadState.Err -> ErrorState(
            message = favorites.cause.userMessage(),
            retryLabel = stringResource(UiR.string.ui_retry),
            onRetry = vm::retry,
            title = stringResource(Res.string.library_favorites_error),
            modifier = modifier.fillMaxSize(),
        )
        is LoadState.Ok -> if (favorites.value.isEmpty() && state.favoriteCharacters.isEmpty()) {
            LibraryEmpty(
                title = stringResource(Res.string.library_favorites_empty_title),
                message = stringResource(
                    if (state.accounts.isEmpty()) Res.string.library_favorites_empty_local else Res.string.library_favorites_empty_linked,
                ),
                modifier = modifier,
            ) {
                if (state.accounts.isEmpty()) {
                    OutlinedButton(onClick = screen.onOpenAccounts) { Text(stringResource(Res.string.library_lists_link)) }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = modifier.fillMaxSize().decor(DecorRegion.GRID),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "chars", span = { GridItemSpan(maxLineSpan) }, contentType = "chars") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(stringResource(Res.string.library_favorites_characters))
                        if (state.favoriteCharacters.isEmpty()) {
                            Text(
                                stringResource(Res.string.rd_s7_no_characters),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyRow(Modifier.decor(DecorRegion.ROW), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.favoriteCharacters, key = { "ch-" + it.id }) { c ->
                                    Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box {
                                            MediaCover(url = c.image, modifier = Modifier.width(72.dp), shape = CircleShape, aspectRatio = 1f)
                                            val removeLabel = stringResource(Res.string.library_favorites_remove_named, c.name)
                                            IconButton(
                                                onClick = {
                                                    vm.removeCharacter(c)
                                                    scope.launch {
                                                        showUndo(getString(Res.string.rd_s7_char_removed, c.name)) { vm.restoreCharacter(c) }
                                                    }
                                                },
                                                modifier = Modifier.align(Alignment.TopEnd).size(48.dp).semantics { contentDescription = removeLabel },
                                            ) {
                                                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Text(c.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                if (favorites.value.isNotEmpty()) {
                    item(key = "titles-head", span = { GridItemSpan(maxLineSpan) }, contentType = "head") {
                        SectionHeader(stringResource(Res.string.library_favorites_titles))
                    }
                }
                itemsIndexed(favorites.value, key = { _, e -> "f-" + e.key }, contentType = { _, _ -> "entry" }) { i, e ->
                    EntryCard(
                        entry = e,
                        labels = labels,
                        actions = actions,
                        context = CardContext.GRID,
                        show = CardShow(progress = true, status = true, next = false, score = true),
                        index = i,
                    )
                }
            }
        }
    }
    val editing = editingKey?.let { k -> (state.favorites as? LoadState.Ok)?.value?.firstOrNull { it.key == k } }
    if (editing != null) EntryEditorFor(editing, vm, actions, onDismiss = { editingKey = null })
}

@Composable
internal fun DelayedLoading() {
    if (rememberAfterDelay()) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingState() }
}

/** "hace 5 min" / "5 minutes ago" in the app locale (platform formatter), lowercase: it always follows other words (BUG-092). */
@Composable
internal fun relativeTime(epochMs: Long): String = app.nijiyome.ui.format.midSentence(platformRelativeTimeSpan(epochMs))
