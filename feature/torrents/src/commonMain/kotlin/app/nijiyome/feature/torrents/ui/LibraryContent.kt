// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.ui

import app.nijiyome.feature.torrents.resources.Res
import app.nijiyome.feature.torrents.resources.*
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.resources.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.nijiyome.ui.platform.rememberOpenFolder
import app.nijiyome.feature.torrents.vm.LibrarySort
import app.nijiyome.feature.torrents.vm.sortLibrary
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import app.nijiyome.ui.platform.rememberShareText
import androidx.compose.material.icons.outlined.FolderOpen
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.WavyProgress
import app.nijiyome.model.LoadState
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentState
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.media.MediaCover

/** Library list (11 §3.2): cards, 3-dot menu, long-press multi-select with a contextual bar. Stateless; the VM lives in the caller. */
@Composable
internal fun LibraryContent(
    state: LoadState<List<TorrentLibraryItem>>,
    selection: Set<String>,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (Collection<String>) -> Unit,
    onClearSelection: () -> Unit,
    onPause: (Collection<String>) -> Unit,
    onResume: (Collection<String>) -> Unit,
    onPersist: (Collection<String>, Boolean) -> Unit,
    onRemove: (Collection<String>, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    header: (@Composable () -> Unit)? = null,
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
) {
    var pendingDelete by remember { mutableStateOf<Collection<String>?>(null) }
    // Desktop (Tatakai Next's library table): sortable, cards or a compact list. Android keeps the engine order and the cards.
    var sort by rememberSaveable { mutableStateOf(LibrarySort.ADDED) }
    var descending by rememberSaveable { mutableStateOf(true) }
    var listView by rememberSaveable { mutableStateOf(false) }
    TorrentLoadState(state, onRetry, modifier, errorTitle = stringResource(Res.string.torrent_library_error)) { loaded ->
        val items = if (desktopTorrentUi) sortLibrary(loaded, sort, descending) else loaded
        if (items.isEmpty()) {
            Column(modifier.fillMaxSize()) {
                header?.invoke()
                EmptyState(
                    icon = Icons.Outlined.DownloadForOffline,
                    title = stringResource(Res.string.torrent_library_empty_title),
                    message = stringResource(Res.string.torrent_library_empty_body),
                    actionLabel = emptyActionLabel,
                    onAction = onEmptyAction,
                )
            }
        } else {
            Column(modifier.fillMaxSize()) {
                if (selection.isNotEmpty()) {
                    SelectionBar(
                        count = selection.size,
                        onSelectAll = { onSelectAll(items.map { it.id }) },
                        onPause = { onPause(selection) },
                        onResume = { onResume(selection) },
                        onKeep = { onPersist(selection, true) },
                        onDelete = { pendingDelete = selection },
                        onClear = onClearSelection,
                    )
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wideList = maxWidth >= WIDE_LIST_DP.dp
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (header != null) item(key = "header", contentType = "header") { header() }
                        item(key = "count", contentType = "count") {
                            if (desktopTorrentUi) {
                                LibraryToolbar(
                                    count = items.size,
                                    sort = sort,
                                    descending = descending,
                                    listView = listView,
                                    onSort = { sort = it },
                                    onDirection = { descending = !descending },
                                    onListView = { listView = it },
                                )
                            } else {
                                Text(
                                    pluralStringResource(Res.plurals.torrent_library_count, items.size, items.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (desktopTorrentUi && listView) item(key = "columns", contentType = "columns") { LibraryColumns(wideList) }
                        items(items, key = { it.id }, contentType = { if (desktopTorrentUi && listView) "row" else "torrent" }) { item ->
                            if (desktopTorrentUi && listView) {
                                LibraryRow(
                                    item = item,
                                    wide = wideList,
                                    selecting = selection.isNotEmpty(),
                                    selected = item.id in selection || item.id == selectedId,
                                    checked = item.id in selection,
                                    onClick = { if (selection.isNotEmpty()) onToggleSelect(item.id) else onOpen(item.id) },
                                    onLongClick = { onToggleSelect(item.id) },
                                    onOpen = { onOpen(item.id) },
                                    onPlay = { onPlay(playArgsFor(item, item.id, null, item.episode, item.name)) },
                                    onPause = { onPause(listOf(item.id)) },
                                    onResume = { onResume(listOf(item.id)) },
                                    onPersist = { onPersist(listOf(item.id), it) },
                                    onDelete = { pendingDelete = listOf(item.id) },
                                )
                                return@items
                            }
                            LibraryCard(
                                item = item,
                                selecting = selection.isNotEmpty(),
                                selected = item.id in selection || item.id == selectedId,
                                checked = item.id in selection,
                                onClick = { if (selection.isNotEmpty()) onToggleSelect(item.id) else onOpen(item.id) },
                                onLongClick = { onToggleSelect(item.id) },
                                onOpen = { onOpen(item.id) },
                                onPlay = { onPlay(playArgsFor(item, item.id, null, item.episode, item.name)) },
                                onPause = { onPause(listOf(item.id)) },
                                onResume = { onResume(listOf(item.id)) },
                                onPersist = { onPersist(listOf(item.id), it) },
                                onDelete = { pendingDelete = listOf(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { ids ->
        DeleteDialog(
            count = ids.size,
            onConfirm = { deleteFiles ->
                onRemove(ids, deleteFiles)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    item: TorrentLibraryItem,
    selecting: Boolean,
    selected: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPersist: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val title = item.series ?: item.name
    val selectLabel = stringResource(Res.string.torrent_library_select, title)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = selectLabel),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.poster != null) {
                MediaCover(url = item.poster, modifier = Modifier.width(52.dp), contentDescription = null)
            } else {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = when {
                    item.episode != null && item.files <= 1 -> stringResource(Res.string.torrent_library_episode, item.episode!!)
                    item.files > 1 -> pluralStringResource(Res.plurals.torrent_library_batch, item.files, item.files)
                    else -> item.name
                }
                Text(
                    text = stringResource(Res.string.torrent_library_detail, detail, formatBytes(item.sizeBytes)),
                    style = monoStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatePill(item.state)
                    Text(
                        text = stringResource(Res.string.torrent_library_progress, percentText(item.progress)),
                        style = monoStyle(MaterialTheme.typography.labelMedium),
                    )
                    if (item.addedUnix > 0) {
                        Text(
                            text = relativeTimeSpan(item.addedUnix * 1000),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                app.nijiyome.designsystem.direction.ProgressTrack(progress = item.progress.toFloat().coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(if (item.persist) Res.string.torrent_library_kept else Res.string.torrent_library_not_kept),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selecting) {
                Checkbox(checked = checked, onCheckedChange = { onLongClick() }, modifier = Modifier.semantics { contentDescription = selectLabel })
            } else {
                ItemMenu(item, onOpen, onPlay, onPause, onResume, onPersist, onDelete)
            }
        }
    }
}

/** The 3-dot menu of a library card or row. */
@Composable
private fun ItemMenu(
    item: TorrentLibraryItem,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPersist: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val shareText = rememberShareText()
    val openFolder = rememberOpenFolder()
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(Res.string.torrent_library_actions))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.torrent_library_open)) },
                leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) },
                onClick = { menu = false; onOpen() },
            )
            if (item.episode != null && item.mediaId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.torrent_library_play)) },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                    onClick = { menu = false; onPlay() },
                )
            }
            if (item.state == TorrentState.PAUSED) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.torrent_overview_resume)) },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                    onClick = { menu = false; onResume() },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.torrent_overview_pause)) },
                    leadingIcon = { Icon(Icons.Outlined.Pause, null) },
                    onClick = { menu = false; onPause() },
                )
            }
            if (openFolder != null && item.path.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.torrent_desktop_open_folder)) },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                    onClick = { menu = false; openFolder(item.path) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (item.persist) Res.string.torrent_library_unkeep else Res.string.torrent_library_keep)) },
                leadingIcon = { Icon(if (item.persist) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null) },
                onClick = { menu = false; onPersist(!item.persist) },
            )
            // Desktop: "sharing" copies the link to the clipboard (rememberShareText), and says so.
            val shareTitle = stringResource(if (desktopTorrentUi) Res.string.torrent_desktop_copy_magnet else Res.string.torrent_android_share_magnet)
            DropdownMenuItem(
                text = { Text(shareTitle) },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = { menu = false; shareText(magnetOf(item.id), shareTitle) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.torrent_library_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------ desktop: sort and list view

@Composable
private fun LibrarySort.label(): String = stringResource(
    when (this) {
        LibrarySort.ADDED -> Res.string.torrent_desktop_sort_added
        LibrarySort.NAME -> Res.string.torrent_desktop_sort_name
        LibrarySort.EPISODE -> Res.string.torrent_desktop_sort_episode
        LibrarySort.SIZE -> Res.string.torrent_desktop_sort_size
        LibrarySort.PROGRESS -> Res.string.torrent_desktop_sort_progress
        LibrarySort.STATE -> Res.string.torrent_desktop_sort_state
    },
)

/** Count, sort key and direction, cards or list (desktop); the controls wrap in the narrow list pane. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryToolbar(
    count: Int,
    sort: LibrarySort,
    descending: Boolean,
    listView: Boolean,
    onSort: (LibrarySort) -> Unit,
    onDirection: () -> Unit,
    onListView: (Boolean) -> Unit,
) {
    Text(
        pluralStringResource(Res.plurals.torrent_library_count, count, count),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        var open by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { open = true }) {
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.torrent_desktop_sort) + ": " + sort.label(), modifier = Modifier.padding(start = 6.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                LibrarySort.entries.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.label()) },
                        leadingIcon = { if (key == sort) Icon(Icons.Outlined.Check, null) },
                        onClick = { open = false; onSort(key) },
                    )
                }
            }
        }
        IconButton(onClick = onDirection) {
            Icon(
                if (descending) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = stringResource(if (descending) Res.string.torrent_desktop_sort_desc else Res.string.torrent_desktop_sort_asc),
            )
        }
        Segmented(
            options = listOf(
                SegmentedOption(false, stringResource(Res.string.torrent_desktop_view_cards)),
                SegmentedOption(true, stringResource(Res.string.torrent_desktop_view_list)),
            ),
            selected = listView,
            onSelect = onListView,
            small = true,
        )
    }
}

/** Below this width the list view leaves out the state and date columns. */
private const val WIDE_LIST_DP = 640

private val COL_EPISODE = 72.dp
private val COL_SIZE = 88.dp
private val COL_STATE = 112.dp
private val COL_PROGRESS = 56.dp
private val COL_ADDED = 120.dp

/** Column titles of the list view; [wide] = room for the state and date columns. */
@Composable
private fun LibraryColumns(wide: Boolean) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.torrent_desktop_sort_name), style = style, color = color, modifier = Modifier.weight(1f))
        Text(stringResource(Res.string.torrent_desktop_sort_episode), style = style, color = color, modifier = Modifier.width(COL_EPISODE))
        Text(stringResource(Res.string.torrent_desktop_sort_size), style = style, color = color, modifier = Modifier.width(COL_SIZE))
        if (wide) Text(stringResource(Res.string.torrent_desktop_sort_state), style = style, color = color, modifier = Modifier.width(COL_STATE))
        Text(stringResource(Res.string.torrent_desktop_sort_progress), style = style, color = color, modifier = Modifier.width(COL_PROGRESS))
        if (wide) Text(stringResource(Res.string.torrent_desktop_sort_added), style = style, color = color, modifier = Modifier.width(COL_ADDED))
        Spacer(Modifier.width(48.dp))
    }
}

/** One torrent as a compact table row (desktop list view): the same clicks, selection and menu as the card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    item: TorrentLibraryItem,
    wide: Boolean,
    selecting: Boolean,
    selected: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPersist: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val title = item.series ?: item.name
    val selectLabel = stringResource(Res.string.torrent_library_select, title)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = selectLabel),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.series != null && item.series != item.name) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val episode = when {
                item.episode != null -> stringResource(Res.string.torrent_desktop_add_file_episode, item.episode!!)
                item.files > 1 -> pluralStringResource(Res.plurals.torrent_library_batch, item.files, item.files)
                else -> "—"
            }
            Text(episode, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(COL_EPISODE))
            Text(formatBytes(item.sizeBytes), style = monoStyle(MaterialTheme.typography.bodySmall), maxLines = 1, modifier = Modifier.width(COL_SIZE))
            if (wide) Box(Modifier.width(COL_STATE)) { StatePill(item.state) }
            Text(percentText(item.progress), style = monoStyle(MaterialTheme.typography.bodySmall), modifier = Modifier.width(COL_PROGRESS))
            if (wide) Text(
                if (item.addedUnix > 0) relativeTimeSpan(item.addedUnix * 1000) else "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(COL_ADDED),
            )
            if (selecting) {
                Checkbox(checked = checked, onCheckedChange = { onLongClick() }, modifier = Modifier.semantics { contentDescription = selectLabel })
            } else {
                ItemMenu(item, onOpen, onPlay, onPause, onResume, onPersist, onDelete)
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.torrent_library_clear_selection)) }
            Text(
                pluralStringResource(Res.plurals.torrent_library_selected, count, count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(Res.string.torrent_library_select_all)) }
            IconButton(onClick = onPause) { Icon(Icons.Outlined.Pause, contentDescription = stringResource(Res.string.torrent_overview_pause)) }
            IconButton(onClick = onResume) { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.torrent_overview_resume)) }
            IconButton(onClick = onKeep) { Icon(Icons.Outlined.Bookmark, contentDescription = stringResource(Res.string.torrent_library_keep)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.torrent_library_delete_selected)) }
        }
    }
}

/** Remove confirmation with "also delete the downloaded files" (desktop `torrent.library.delete_*`). */
@Composable
internal fun DeleteDialog(count: Int, onConfirm: (deleteFiles: Boolean) -> Unit, onDismiss: () -> Unit) {
    var deleteFiles by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (count == 1) stringResource(Res.string.torrent_library_delete_one) else pluralStringResource(Res.plurals.torrent_library_delete_many, count, count),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.torrent_library_delete_body))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                    Text(stringResource(Res.string.torrent_library_delete_files))
                }
                if (!deleteFiles) {
                    Text(
                        stringResource(Res.string.torrent_library_delete_files_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        // Sparkles (TOR-06): "Cancel" and a destructive confirm, instead of "Close".
        confirmButton = {
            TextButton(
                onClick = { onConfirm(deleteFiles) },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.torrent_library_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(app.nijiyome.ui.resources.Res.string.ui_cancel)) } },
    )
}
