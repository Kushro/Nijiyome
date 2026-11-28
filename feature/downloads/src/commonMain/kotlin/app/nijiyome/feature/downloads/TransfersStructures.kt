// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.downloads.resources.Res
import app.nijiyome.feature.downloads.resources.*
import app.nijiyome.feature.downloads.transfers.TransferFilter
import app.nijiyome.feature.downloads.transfers.TransferItem
import app.nijiyome.feature.downloads.transfers.TransferPhase
import app.nijiyome.feature.downloads.transfers.TransferSort
import app.nijiyome.feature.downloads.transfers.TransfersLogic
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.patterns.ActionButton
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.multiSelectable
import app.nijiyome.ui.patterns.selectionKeys
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.designsystem.scroll.NjLazyColumn
import app.nijiyome.designsystem.scroll.njVerticalScroll

// Sparkles redesign, lane S8 (Transferencias): the two structures. Both read the same state and call the same actions ([TransfersEnv]);
// only the composition changes.

/** The three places of the page: what is moving, what is on disk, the local library. */
internal enum class TransfersSection { QUEUE, DONE, LOCAL }

// ================================================================== QUEUE ("Cola y estanterías")

/**
 * One queue of everything in flight, then the shelves of what is stored. Wide: the queue and the shelves in the main column, the
 * panels (storage, background, auto-download, local library) at the side. Phone: three tabs, En curso / Descargado / Local.
 */
@Composable
internal fun TransfersQueue(state: DownloadsUiState, env: TransfersEnv, modifier: Modifier = Modifier) {
    if (isCompactWindow()) QueuePhone(state, env, modifier) else QueueWide(state, env, modifier)
}

@Composable
private fun QueueWide(state: DownloadsUiState, env: TransfersEnv, modifier: Modifier) {
    val keys = state.queue.map { it.key }
    Row(modifier.fillMaxSize()) {
        NjLazyColumn(
            Modifier.weight(1f).fillMaxHeight().selectionKeys(env.selection) { keys },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            noticeItems(state, env)
            queueHeader(state, env, showHint = true)
            queueItems(state, env)
            item(key = "done-head") {
                SectionHeader(
                    stringResource(Res.string.rd_s8_done),
                    modifier = Modifier.padding(top = 16.dp),
                    trailing = { DoneMediaChips(env.host.doneMedia, doneCounts(state), onSelect = { env.host.doneMedia = it }) },
                )
            }
            doneItems(state, env)
        }
        Column(
            Modifier.width(360.dp).fillMaxHeight().njVerticalScroll(rememberScrollState(), scrollToTopOnReselect = false).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // TRA-02: the artifact's order, the switches first (background), then storage, then the longer panels.
            if (state.torrentsAvailable && state.background.acknowledged && desktopDownloadsUi) {
                BackgroundPanel(state.background.enabled, env.vm::setBackground)
            }
            StoragePanel(state.usedBytes, state.busy, env.host.verifyResult, env.vm::verify, env.onOpenSettings)
            AutoDownloadPanel(state.rules, env.vm, state.busy, env.host.autoResult)
            TransfersPanel(stringResource(Res.string.downloads_tab_local)) {
                LocalLibraryHeader(state.library, state.folders, env.host.onlyUnmatched, { env.host.onlyUnmatched = it }, env.vm)
                localGroups(state.library, env.host.onlyUnmatched).forEach { (title, files) ->
                    Text(title ?: stringResource(Res.string.downloads_local_unmatched), style = MaterialTheme.typography.labelLarge)
                    files.forEach { item -> LocalRow(item, { env.playLocal(item) }, { env.host.matching = item }, { env.host.removingLocal = item }) }
                }
                if (state.library.isEmpty()) {
                    Text(stringResource(Res.string.downloads_empty_local_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QueuePhone(state: DownloadsUiState, env: TransfersEnv, modifier: Modifier) {
    val host = env.host
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = host.section.ordinal) {
                TransfersSection.entries.forEach { s ->
                    Tab(selected = host.section == s, onClick = { host.section = s }, text = { Text(s.label()) })
                }
            }
            val keys = state.queue.map { it.key }
            NjLazyColumn(
                Modifier.fillMaxSize().selectionKeys(env.selection) { keys },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (host.section) {
                    TransfersSection.QUEUE -> {
                        noticeItems(state, env)
                        queueHeader(state, env, showHint = false)
                        queueItems(state, env)
                    }
                    TransfersSection.DONE -> {
                        item(key = "auto") { AutoDownloadPanel(state.rules, env.vm, state.busy, host.autoResult) }
                        item(key = "done-chips") {
                            DoneMediaChips(host.doneMedia, doneCounts(state), onSelect = { host.doneMedia = it }, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        doneItems(state, env)
                        item(key = "storage") { StoragePanel(state.usedBytes, state.busy, host.verifyResult, env.vm::verify, env.onOpenSettings, Modifier.padding(top = 16.dp)) }
                    }
                    TransfersSection.LOCAL -> localItems(state, env)
                }
            }
        }
        val add = env.addTorrent
        if (host.section == TransfersSection.QUEUE && !env.selection.active && add != null) {
            ExtendedFloatingActionButton(
                onClick = add,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.rd_s8_add_torrent)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
internal fun TransfersSection.label(): String = stringResource(
    when (this) {
        TransfersSection.QUEUE -> Res.string.rd_s8_section_queue
        TransfersSection.DONE -> Res.string.rd_s8_done
        TransfersSection.LOCAL -> Res.string.rd_s8_section_local
    },
)

private fun doneCounts(state: DownloadsUiState): Map<DoneMedia, Int> = mapOf(
    DoneMedia.ANIME to state.anime.size + state.completedTorrents.size,
    DoneMedia.MANGA to state.manga.map { it.mediaId }.distinct().size,
    DoneMedia.NOVELS to state.novels.map { it.mediaId }.distinct().size,
)

// ------------------------------------------------------------------ queue blocks

/** The background notice while not acknowledged, else the service state (Android) or the "off" warning. */
internal fun LazyListScope.noticeItems(state: DownloadsUiState, env: TransfersEnv) {
    if (!state.torrentsAvailable) return
    val bg = state.background
    if (!bg.acknowledged) {
        if (!env.host.noticeHidden) item(key = "bg-notice") { BackgroundNotice(onAccept = env.acceptBackground, onNotNow = { env.host.noticeHidden = true }) }
    } else if (!desktopDownloadsUi || !bg.enabled) {
        item(key = "bg-status") {
            BackgroundStatusRow(
                enabled = bg.enabled,
                status = state.torrentStatus,
                onTurnOn = { env.vm.setBackground(true) },
                onResumeAll = { env.vm.resumeItems(state.queue.filter { it.isTorrent && it.canResume }) },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

internal fun LazyListScope.queueHeader(state: DownloadsUiState, env: TransfersEnv, showHint: Boolean) {
    item(key = "queue-head") {
        SectionHeader(
            stringResource(Res.string.rd_s8_in_progress, state.queue.size),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (showHint) Res.string.rd_s8_queue_hint else Res.string.rd_s8_queue_hint_touch),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showHint && state.queue.isNotEmpty()) {
                        TextButton(onClick = { if (env.selection.active) env.selection.exit() else env.selection.start() }, shape = NijiyomeControls.shape) {
                            Icon(if (env.selection.active) Icons.Outlined.Check else Icons.Outlined.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(if (env.selection.active) Res.string.rd_s8_select_done else Res.string.rd_s8_select),
                                Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            },
        )
    }
    if (state.query.isNotBlank()) {
        item(key = "queue-filtered") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = LocalSignalColors.current.info, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rd_s8_queue_filtered, state.query), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { env.vm.setQuery("") }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s8_search_clear)) }
            }
        }
    }
}

/**
 * The rows, or the empty state with an action. The repositories keep no common order, so there is no drag to reorder: each kind
 * keeps its own order and the list goes by state.
 */
internal fun LazyListScope.queueItems(state: DownloadsUiState, env: TransfersEnv) {
    if (state.queue.isEmpty()) {
        item(key = "queue-empty") { QueueEmpty(state, env) }
        return
    }
    val keys = state.queue.map { it.key }
    items(state.queue, key = { it.key }) { item ->
        TransferRow(
            item = item,
            selection = env.selection,
            order = { keys },
            vm = env.vm,
            etaSecs = item.web?.let { state.eta[it.id] },
            onOpen = { env.openItem(item) },
            menu = { env.rowMenu(item) },
            errorText = env.errorText,
            compactLine = isCompactWindow(),
        )
    }
}

@Composable
private fun QueueEmpty(state: DownloadsUiState, env: TransfersEnv) {
    Column(
        Modifier.fillMaxWidth().decor(DecorRegion.EMPTY).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyArt(EmptyKind.DOWNLOADS, Modifier.size(120.dp))
        Text(stringResource(Res.string.downloads_empty_active), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (state.torrentsAvailable) Res.string.rd_s8_queue_empty_body else Res.string.downloads_empty_active_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            env.addTorrent?.let { ActionButton(stringResource(Res.string.rd_s8_add_torrent), onClick = it, icon = Icons.Outlined.Add) }
            env.onOpenDebrid?.let { TextButton(onClick = it, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s8_setup_debrid)) } }
        }
        if (state.stored.isNotEmpty()) {
            TextButton(onClick = { env.host.section = TransfersSection.DONE }, shape = NijiyomeControls.shape) {
                Text(stringResource(Res.string.rd_s8_queue_empty_stored, state.stored.size))
            }
        }
    }
}

/** The shelf of the chosen media. */
internal fun LazyListScope.doneItems(state: DownloadsUiState, env: TransfersEnv) {
    when (env.host.doneMedia) {
        DoneMedia.ANIME -> animeShelf(
            groups = state.anime,
            torrents = state.stored,
            onOpenDetails = env.onOpenDetails,
            onPlay = env.playWeb,
            onRedownload = { env.vm.retry(it.id) },
            onDeleteEpisode = env.deleteEpisode,
            onDeleteTitle = { env.host.deletingTitle = it },
            onOpenTorrent = env.openItem,
        )
        DoneMedia.MANGA -> chapterShelf(state.manga, novels = false, env.openChapter, env.vm::retryChapter, env.deleteChapter) { env.host.deletingChapters = it }
        DoneMedia.NOVELS -> chapterShelf(state.novels, novels = true, env.openChapter, env.vm::retryChapter, env.deleteChapter) { env.host.deletingChapters = it }
    }
}

/** The local library as list items (phone tab, master-detail section). */
internal fun LazyListScope.localItems(state: DownloadsUiState, env: TransfersEnv) {
    item(key = "local-head") {
        LocalLibraryHeader(state.library, state.folders, env.host.onlyUnmatched, { env.host.onlyUnmatched = it }, env.vm, Modifier.padding(vertical = 8.dp))
    }
    if (state.library.isEmpty()) {
        item(key = "local-empty") {
            EmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(Res.string.downloads_empty_local),
                message = stringResource(Res.string.downloads_empty_local_body),
            )
        }
        return
    }
    localGroups(state.library, env.host.onlyUnmatched).forEach { (title, files) ->
        item(key = "lg:" + (title ?: "\u0000")) {
            Text(
                title ?: stringResource(Res.string.downloads_local_unmatched),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        items(files, key = { "l:" + it.id }) { item ->
            LocalRow(item, { env.playLocal(item) }, { env.host.matching = item }, { env.host.removingLocal = item })
        }
    }
}

// ================================================================== MASTER_DETAIL ("Cliente maestro-detalle")

/**
 * A client: the filterable, sortable list on the left and the selected transfer's detail on the right (files, peers, trackers,
 * pieces, speed for a torrent). Phones stack them: the list, then the detail with a back arrow. "Descargado" and "Local" are
 * sections of the same page.
 */
@Composable
internal fun TransfersMasterDetail(state: DownloadsUiState, env: TransfersEnv, modifier: Modifier = Modifier) {
    val host = env.host
    val compact = isCompactWindow()
    Column(modifier.fillMaxSize()) {
        if (!(compact && host.detailKey != null)) {
            PrimaryTabRow(selectedTabIndex = host.section.ordinal) {
                TransfersSection.entries.forEach { s ->
                    Tab(selected = host.section == s, onClick = { host.section = s }, text = { Text(s.label()) })
                }
            }
        }
        when (host.section) {
            TransfersSection.QUEUE -> ClientPane(state, env, compact)
            TransfersSection.DONE -> NjLazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "auto") { AutoDownloadPanel(state.rules, env.vm, state.busy, host.autoResult) }
                item(key = "done-chips") {
                    DoneMediaChips(host.doneMedia, doneCounts(state), onSelect = { host.doneMedia = it }, modifier = Modifier.padding(vertical = 8.dp))
                }
                doneItems(state, env)
                item(key = "storage") { StoragePanel(state.usedBytes, state.busy, host.verifyResult, env.vm::verify, env.onOpenSettings, Modifier.padding(top = 16.dp)) }
            }
            TransfersSection.LOCAL -> NjLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) { localItems(state, env) }
        }
    }
}

@Composable
private fun ClientPane(state: DownloadsUiState, env: TransfersEnv, compact: Boolean) {
    val host = env.host
    val all = remember(state.queue, state.stored) { state.queue + state.stored }
    val shown = remember(all, host.filter, host.sort, host.descending) {
        TransfersLogic.sort(all.filter { TransfersLogic.matches(it, host.filter) }, host.sort, host.descending)
    }
    val selected = host.detailKey
    if (compact) {
        if (selected != null) {
            DetailPane(selected, all, env, showBack = true, modifier = Modifier.fillMaxSize())
        } else {
            MasterList(state, all, shown, env, Modifier.fillMaxSize())
        }
        return
    }
    Row(Modifier.fillMaxSize()) {
        MasterList(state, all, shown, env, Modifier.width(380.dp).fillMaxHeight())
        VerticalDivider()
        if (selected != null) {
            DetailPane(selected, all, env, showBack = false, modifier = Modifier.weight(1f).fillMaxHeight())
        } else {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.TouchApp,
                    title = stringResource(Res.string.rd_s8_md_pick),
                    message = stringResource(Res.string.rd_s8_md_pick_body),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MasterList(state: DownloadsUiState, all: List<TransferItem>, shown: List<TransferItem>, env: TransfersEnv, modifier: Modifier) {
    val host = env.host
    val keys = shown.map { it.key }
    Column(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TransfersSearchField(state.query, env.vm::setQuery, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                SortMenu(host.sort) { host.sort = it }
                IconButton(onClick = { host.descending = !host.descending }) {
                    Icon(
                        if (host.descending) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                        contentDescription = stringResource(if (host.descending) Res.string.rd_s8_sort_desc else Res.string.rd_s8_sort_asc),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TransferFilter.entries.forEach { f ->
                    val n = all.count { TransfersLogic.matches(it, f) }
                    FilterChip(
                        selected = host.filter == f,
                        onClick = { host.filter = f },
                        label = { Text(f.label() + " · " + n) },
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            if (all.isEmpty()) {
                QueueEmpty(state, env)
            } else {
                EmptyState(
                    icon = Icons.Outlined.FilterAltOff,
                    title = stringResource(Res.string.rd_s8_md_none),
                    message = stringResource(Res.string.rd_s8_md_none_body),
                    actionLabel = stringResource(Res.string.rd_s8_md_all),
                    onAction = {
                        host.filter = TransferFilter.ALL
                        env.vm.setQuery("")
                    },
                )
            }
            return@Column
        }
        NjLazyColumn(
            Modifier.fillMaxSize().selectionKeys(env.selection) { keys },
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(shown, key = { it.key }) { item -> MasterRow(item, env, keys, selected = host.detailKey == item.key) }
        }
    }
}

@Composable
private fun MasterRow(item: TransferItem, env: TransfersEnv, keys: List<String>, selected: Boolean) {
    val title = item.displayTitle()
    app.nijiyome.ui.patterns.ContextMenuArea(entries = { env.rowMenu(item) }, sheetHeader = { app.nijiyome.ui.patterns.MenuSheetHeader(title) }) { _ ->
        Surface(
            color = when {
                env.selection.isSelected(item.key) || selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                Modifier.fillMaxWidth().multiSelectable(env.selection, item.key, { keys }, onOpen = { env.openItem(item) }).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                app.nijiyome.ui.patterns.SelectionCheckbox(env.selection.isSelected(item.key), env.selection.active)
                MediaCover(url = item.poster, modifier = Modifier.width(32.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(item.kind.label(), item.sizeBytes?.let(::formatBytes), stringResource(Res.string.rd_s8_row_pct, (item.progress * 100).toInt()))
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProgressTrack(progress = item.progress.toFloat(), modifier = Modifier.fillMaxWidth())
                }
                PhaseChip(item.phase)
            }
        }
    }
}

@Composable
internal fun TransferFilter.label(): String = stringResource(
    when (this) {
        TransferFilter.ALL -> Res.string.rd_s8_filter_all
        TransferFilter.DOWNLOADING -> Res.string.rd_s8_filter_downloading
        TransferFilter.SEEDING -> Res.string.rd_s8_filter_seeding
        TransferFilter.PAUSED -> Res.string.rd_s8_filter_paused
        TransferFilter.ERROR -> Res.string.rd_s8_filter_error
    },
)

@Composable
private fun TransferSort.label(): String = stringResource(
    when (this) {
        TransferSort.ADDED -> Res.string.rd_s8_sort_added
        TransferSort.NAME -> Res.string.rd_s8_sort_name
        TransferSort.SIZE -> Res.string.rd_s8_sort_size
        TransferSort.PROGRESS -> Res.string.rd_s8_sort_progress
        TransferSort.STATE -> Res.string.rd_s8_sort_state
    },
)

@Composable
private fun SortMenu(sort: TransferSort, onSort: (TransferSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, shape = NijiyomeControls.shape) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.rd_s8_sort_by, sort.label()), Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TransferSort.entries.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key.label()) },
                    leadingIcon = { if (key == sort) Icon(Icons.Outlined.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onSort(key)
                    },
                )
            }
        }
    }
}

/**
 * TRA-03: the search field at the height of the header buttons (40 dp) with the theme's small shape, instead of a 56 dp Material
 * outlined field with its own corners.
 */
@Composable
internal fun TransfersSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 40.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(colors.surfaceContainerLow)
                    .border(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.outline, shape)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(Res.string.rd_s8_search),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s8_search_clear), modifier = Modifier.size(18.dp))
                    }
                } else {
                    Spacer(Modifier.width(4.dp))
                }
            }
        },
    )
}

// ------------------------------------------------------------------ detail

/** The detail of [key]: a torrent through the torrent client's own detail, a web download or a chapter through [TransferInfoPane]. */
@Composable
internal fun DetailPane(key: String, all: List<TransferItem>, env: TransfersEnv, showBack: Boolean, modifier: Modifier = Modifier) {
    val item = all.firstOrNull { it.key == key }
    val slots = env.slots
    val close = { env.host.detailKey = null }
    when {
        key.startsWith("t:") && slots != null -> slots.detail(key.removePrefix("t:"), close, showBack, modifier)
        item != null -> TransferInfoPane(item, env, showBack, close, modifier)
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.TouchApp,
                title = stringResource(Res.string.rd_s8_md_gone),
                actionLabel = stringResource(UiR.string.ui_back),
                onAction = close,
            )
        }
    }
}

/** Detail of a web, debrid or chapter download: state, progress, figures, error with its recovery, and its actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransferInfoPane(item: TransferItem, env: TransfersEnv, showBack: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val title = item.displayTitle()
    Column(modifier.njVerticalScroll(rememberScrollState(), scrollToTopOnReselect = false)) {
        if (showBack) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaCover(url = item.poster, modifier = Modifier.width(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!showBack) Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignalChip(item.kind.label(), item.kind.icon, MaterialTheme.colorScheme.outline)
                        PhaseChip(item.phase)
                    }
                }
            }
            Text(
                stringResource(Res.string.rd_s8_row_pct, (item.progress * 100).toInt()),
                style = MaterialTheme.typography.displaySmall,
            )
            ProgressTrack(progress = item.progress.toFloat(), modifier = Modifier.fillMaxWidth())
            val error = item.error?.takeIf { item.phase == TransferPhase.FAILED }
            if (error != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = LocalSignalColors.current.err)
                    Text(env.errorText(error), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            val added = remember(item.addedAtMs) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.addedAtMs)) }
            InfoLine(stringResource(Res.string.rd_s8_info_done), formatBytes(item.doneBytes) + (item.sizeBytes?.let { " / " + formatBytes(it) } ?: ""))
            if (item.source.isNotBlank()) InfoLine(stringResource(Res.string.rd_s8_info_source), item.source)
            item.web?.let { InfoLine(stringResource(Res.string.rd_s8_info_container), it.container.name) }
            InfoLine(stringResource(Res.string.rd_s8_info_added), added)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    item.canRetry -> ActionButton(stringResource(Res.string.rd_s8_retry), { env.vm.resumeItems(listOf(item)) }, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.FILLED)
                    item.canResume -> ActionButton(stringResource(Res.string.downloads_resume), { env.vm.resumeItems(listOf(item)) }, icon = Icons.Outlined.PlayArrow, emphasis = ActionEmphasis.FILLED)
                    item.canPause && item.chapter == null -> ActionButton(stringResource(Res.string.downloads_pause), { env.vm.pauseItems(listOf(item)) }, icon = Icons.Outlined.Pause)
                }
                item.web?.ref?.anilistId?.let { id ->
                    ActionButton(stringResource(Res.string.rd_s8_open_details), { env.onOpenDetails(id) }, icon = Icons.Outlined.OpenInNew, emphasis = ActionEmphasis.OUTLINED)
                }
                ActionButton(
                    stringResource(if (item.web != null && item.phase != TransferPhase.COMPLETE) Res.string.rd_s8_menu_cancel_download else Res.string.rd_s8_menu_delete),
                    { env.confirmDelete(listOf(item)) },
                    icon = Icons.Outlined.Delete,
                    emphasis = ActionEmphasis.DANGER,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
