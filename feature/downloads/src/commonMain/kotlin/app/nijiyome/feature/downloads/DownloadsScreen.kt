// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.feature.downloads.resources.Res
import app.nijiyome.feature.downloads.resources.*
import app.nijiyome.feature.downloads.transfers.TransferFilter
import app.nijiyome.feature.downloads.transfers.TransferItem
import app.nijiyome.feature.downloads.transfers.TransferSort
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.ChapterDownload
import app.nijiyome.model.ChapterKind
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadProfile
import app.nijiyome.model.DownloadsTab
import app.nijiyome.model.LocalLibraryItem
import app.nijiyome.model.MangaReaderArgs
import app.nijiyome.model.MediaRef
import app.nijiyome.model.NovelReaderArgs
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.TransfersStructure
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.patterns.ActionButton
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.patterns.SelectionAction
import app.nijiyome.ui.patterns.SelectionActionBar
import app.nijiyome.ui.patterns.SelectionState
import app.nijiyome.ui.patterns.SelectionTopBar
import app.nijiyome.ui.patterns.UndoController
import app.nijiyome.ui.patterns.UndoSnackbar
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.rememberSelectionState
import app.nijiyome.ui.patterns.rememberUndoController
import app.nijiyome.ui.platform.rememberNotificationPermission
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.state.toAppError
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The torrent pieces Transferencias shows. :feature:torrents and :feature:downloads do not see each other, so the shell builds this
 * from `TransfersTorrentDetail` and `TransfersAddTorrentHost` of :feature:torrents and hands it to [DownloadsScreen]. Without it, a
 * torrent opens through `onOpenTorrent` (`Route.Torrents`) and "Add torrent" is hidden.
 */
@Immutable
class TransfersTorrentSlots(
    /** The detail of one torrent (Resumen, Archivos, Pares, Rastreadores). `showBack` = stacked phone navigation. */
    val detail: @Composable (torrentId: String, onBack: () -> Unit, showBack: Boolean, modifier: Modifier) -> Unit,
    /** "Añadir torrent" with the file-selection step; it also opens itself for a `magnet:` / `.torrent` handed over by the system. */
    val addTorrent: @Composable (open: Boolean, onOpenChange: (Boolean) -> Unit, onAdded: (torrentId: String) -> Unit) -> Unit,
)

/**
 * Transferencias (Sparkles lane S8): `Route.Downloads(tab)`. One queue for web, debrid, torrent and chapter downloads, the shelves of
 * what is on disk, the history, the local library and the auto-download rules, in the structure the user chose
 * (`structures.transfers`: [TransfersStructure.QUEUE] or [TransfersStructure.MASTER_DETAIL]). [initialTab] picks the section
 * (ACTIVE → queue, ANIME / MANGA / NOVELS → that shelf, HISTORY → the history sheet, LOCAL → the local library).
 *
 * New, optional: [torrentSlots] (the torrent client inside the page), [initialTorrentId] (open on that torrent's detail),
 * [onOpenSettings] (Settings › Torrents y descargas) and [onOpenDebrid] (Sources › Debrid). [onBack] null on wide windows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    initialTab: DownloadsTab,
    onBack: (() -> Unit)?,
    onPlay: (PlayerArgs) -> Unit,
    onOpenDetails: (anilistId: Int) -> Unit,
    onOpenTorrent: (torrentId: String) -> Unit,
    onOpenMangaReader: (MangaReaderArgs) -> Unit,
    onOpenNovelReader: (NovelReaderArgs) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = koinViewModel(),
    torrentSlots: TransfersTorrentSlots? = null,
    initialTorrentId: String? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenDebrid: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val host = remember { TransfersHost() }
    val selection = rememberSelectionState<String>()
    val undo = rememberUndoController()
    val structure = structures.transfers
    val currentOpenTorrent by rememberUpdatedState(onOpenTorrent)

    LaunchedEffect(initialTab) {
        viewModel.selectTab(initialTab)
        host.applyTab(initialTab)
    }
    LaunchedEffect(initialTorrentId, torrentSlots != null) {
        if (initialTorrentId != null && torrentSlots != null) {
            host.section = TransfersSection.QUEUE
            host.detailKey = "t:$initialTorrentId"
        }
    }
    // Ask POST_NOTIFICATIONS only once something actually downloads (never cold-prompt on an empty screen).
    val permission = rememberNotificationPermission()
    val hasActive = state.queue.isNotEmpty()
    LaunchedEffect(hasActive) {
        if (hasActive && !permission.isGranted()) permission.request()
    }
    // A selection keeps only rows that still exist.
    LaunchedEffect(state.queue, state.stored) {
        if (selection.active) selection.retainOnly((state.queue + state.stored).map { it.key })
    }

    val msgChecked = stringResource(Res.string.downloads_checked)
    val msgVerified = stringResource(Res.string.downloads_verified)
    val msgVerifiedOk = stringResource(Res.string.downloads_verified_ok)
    val msgImported = stringResource(Res.string.downloads_imported)
    val msgPreviewNone = stringResource(Res.string.downloads_preview_none)
    val msgPreview = stringResource(Res.string.downloads_preview)
    val msgChapterUnavailable = stringResource(Res.string.downloads_chapter_unavailable)
    val errorText = rememberDownloadErrorText()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                // DL-16: the result of Revisar / Simular / Revisar archivos stays visible in its panel, and is announced once.
                is DownloadsEvent.Checked -> msgChecked.format(e.queued).also { host.autoResult = it; snackbar.show(it) }
                is DownloadsEvent.Verified -> (if (e.broken == 0) msgVerifiedOk else msgVerified.format(e.broken)).also { host.verifyResult = it; snackbar.show(it) }
                is DownloadsEvent.Imported -> snackbar.show(msgImported.format(e.added))
                is DownloadsEvent.Preview -> {
                    val text = e.preview?.episodes?.takeIf { it.isNotEmpty() }?.let { msgPreview.format(it.joinToString(", ")) } ?: msgPreviewNone
                    host.autoResult = text
                    snackbar.show(text)
                }
                is DownloadsEvent.Failed -> snackbar.show(errorText(e.cause))
                is DownloadsEvent.OpenTorrent -> {
                    if (torrentSlots != null) {
                        host.detailKey = "t:" + e.id
                    } else {
                        currentOpenTorrent(e.id)
                    }
                }
            }
        }
    }

    val play: (DownloadItem) -> Unit = { item ->
        item.localUri?.let { onPlay(PlayerArgs(media = item.ref, title = item.title, episode = item.episode, localUri = it)) }
    }
    val openChapter: (ChapterDownload) -> Unit = { c ->
        when (c.kind) {
            ChapterKind.MANGA -> decodeArgs(c, MangaReaderArgs.serializer())?.let(onOpenMangaReader) ?: snackbar.show(msgChapterUnavailable)
            ChapterKind.NOVEL -> decodeArgs(c, NovelReaderArgs.serializer())?.let(onOpenNovelReader) ?: snackbar.show(msgChapterUnavailable)
        }
    }
    val removedEpisode = stringResource(Res.string.rd_s8_undo_episode)
    val removedChapter = stringResource(Res.string.rd_s8_undo_chapter)
    val removedLocal = stringResource(Res.string.rd_s8_undo_local)
    val transferError = rememberTransferErrorText()
    val openItem: (TransferItem) -> Unit = { item ->
        if (item.isTorrent && torrentSlots == null) {
            onOpenTorrent(item.id)
        } else {
            host.detailKey = item.key
        }
    }
    val playTorrent: (TransferItem) -> Unit = { item ->
        onPlay(PlayerArgs(media = mediaRefOf(item.torrent?.mediaId), title = item.title, episode = item.episode ?: 1, torrentId = item.id))
    }
    val rowMenu = rememberRowMenu(viewModel, onOpen = openItem, onPlayTorrent = playTorrent, onDelete = { host.deleting = it })
    val env = TransfersEnv(
        vm = viewModel,
        host = host,
        selection = selection,
        slots = torrentSlots,
        openItem = openItem,
        rowMenu = rowMenu,
        errorText = transferError,
        onOpenDetails = onOpenDetails,
        playWeb = play,
        playLocal = { item ->
            onPlay(PlayerArgs(media = item.ref ?: MediaRef(), title = item.title ?: item.fileName, episode = item.episode ?: 1, localUri = item.uri))
        },
        openChapter = openChapter,
        // DL-04 / DL-07: one episode or chapter goes with an undo; the files are deleted when the undo runs out.
        deleteEpisode = { item -> undoable(undo, viewModel, listOf("w:" + item.id), removedEpisode.format(item.title, item.episode)) },
        deleteChapter = { c -> undoable(undo, viewModel, listOf("c:" + c.id), removedChapter.format(c.title, c.chapterLabel), chapterKind = c.kind) },
        acceptBackground = {
            viewModel.acknowledgeBackground()
            if (!permission.isGranted()) permission.request()
        },
        addTorrent = if (torrentSlots != null) ({ host.addOpen = true }) else null,
        onOpenSettings = onOpenSettings,
        onOpenDebrid = onOpenDebrid,
        confirmDelete = { host.deleting = it },
    )

    val compact = isCompactWindow()
    // Phone, stacked navigation: a detail opened from the queue covers the page; back returns to the list.
    val coveringDetail = host.detailKey != null && (structure == TransfersStructure.QUEUE || compact)
    Box(modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = screenInsets,
            topBar = {
                when {
                    coveringDetail -> Unit
                    compact && selection.active -> SelectionTopBar(
                        count = selection.count,
                        onExit = selection::exit,
                        onSelectAll = { selection.selectAll(state.queue.map { it.key }) },
                    )
                    else -> TransfersTopBar(state, env, onBack, compact)
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    !state.loaded -> LoadingRows()
                    coveringDetail -> {
                        val key = host.detailKey.orEmpty()
                        DetailPane(key, state.queue + state.stored, env, showBack = true, modifier = Modifier.fillMaxSize())
                    }
                    // The structure is chosen once, here; both share the state, the actions and the overlays below.
                    else -> when (structure) {
                        TransfersStructure.QUEUE -> TransfersQueue(state, env)
                        TransfersStructure.MASTER_DETAIL -> TransfersMasterDetail(state, env)
                    }
                }
                BatchBar(state, env, Modifier.align(Alignment.BottomCenter))
                UndoSnackbar(undo, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
            }
        }
    }

    // ------------------------------------------------------------------ overlays shared by both structures
    torrentSlots?.addTorrent?.invoke(host.addOpen, { host.addOpen = it }) { id ->
        host.section = TransfersSection.QUEUE
        host.detailKey = "t:$id"
    }
    if (host.limitsOpen) {
        LimitsSheet(state.limits, state.torrentsAvailable, viewModel, onOpenSettings, onDismiss = { host.limitsOpen = false })
    }
    if (host.historyOpen) {
        HistorySheet(state.history, state.torrentSessions, state.torrentIds, state.torrentsAvailable, viewModel, play, onDismiss = { host.historyOpen = false })
    }
    host.deleting?.let { items ->
        TransferDeleteDialog(
            items = items,
            onConfirm = { deleteFiles ->
                viewModel.removeItems(items, deleteFiles)
                if (items.any { it.key == host.detailKey }) host.detailKey = null
                selection.exit()
                host.deleting = null
            },
            onDismiss = { host.deleting = null },
        )
    }
    host.deletingTitle?.let { group ->
        DangerConfirm(
            title = stringResource(Res.string.downloads_remove_title),
            body = stringResource(Res.string.downloads_remove_title_confirm, group.title),
            confirm = stringResource(Res.string.rd_s8_delete_all),
            onConfirm = { viewModel.removeTitle(group) },
            onDismiss = { host.deletingTitle = null },
        )
    }
    host.deletingChapters?.let { chapters ->
        DangerConfirm(
            title = stringResource(Res.string.rd_s8_delete_chapters_title),
            body = pluralStringResource(Res.plurals.rd_s8_delete_chapters_body, chapters.size, chapters.size, chapters.first().title),
            confirm = stringResource(Res.string.rd_s8_delete_all),
            onConfirm = { chapters.forEach(viewModel::removeChapter) },
            onDismiss = { host.deletingChapters = null },
        )
    }
    host.matching?.let { item ->
        MatchDialog(item, onDismiss = { host.matching = null }) { media, ep -> viewModel.matchLocal(item.id, media, ep) }
    }
    host.removingLocal?.let { item ->
        LocalRemoveDialog(
            item = item,
            onRemove = { deleteFile ->
                host.removingLocal = null
                if (deleteFile) {
                    viewModel.removeLocal(item.id, deleteFile = true)
                } else {
                    // Keeping the file is reversible: hide it now, forget it when the undo runs out.
                    undoable(undo, viewModel, listOf("l:" + item.id), removedLocal.format(item.fileName))
                }
            },
            onDismiss = { host.removingLocal = null },
        )
    }
}

/** Removes with an undo: hide now, commit when the undo expires or the screen leaves, restore on Undo. */
private fun undoable(undo: UndoController, vm: DownloadsViewModel, keys: List<String>, message: String, chapterKind: ChapterKind? = null) {
    vm.hide(keys)
    undo.show(message, undo = { vm.restore(keys) }, onCommit = { vm.commitRemoval(keys, deleteLocalFile = false, chapterKind = chapterKind) })
}

/** Everything both structures need: the state holder, the selection and the actions. */
@Stable
internal class TransfersEnv(
    val vm: DownloadsViewModel,
    val host: TransfersHost,
    val selection: SelectionState<String>,
    val slots: TransfersTorrentSlots?,
    val openItem: (TransferItem) -> Unit,
    val rowMenu: (TransferItem) -> List<MenuEntry>,
    val errorText: (String) -> String,
    val onOpenDetails: (Int) -> Unit,
    val playWeb: (DownloadItem) -> Unit,
    val playLocal: (LocalLibraryItem) -> Unit,
    val openChapter: (ChapterDownload) -> Unit,
    val deleteEpisode: (DownloadItem) -> Unit,
    val deleteChapter: (ChapterDownload) -> Unit,
    val acceptBackground: () -> Unit,
    val addTorrent: (() -> Unit)?,
    val onOpenSettings: (() -> Unit)?,
    val onOpenDebrid: (() -> Unit)?,
    val confirmDelete: (List<TransferItem>) -> Unit,
)

/** Screen-level UI state of Transferencias (which section, which detail, which sheet or dialog is open). */
@Stable
internal class TransfersHost {
    var section by mutableStateOf(TransfersSection.QUEUE)
    var detailKey by mutableStateOf<String?>(null)
    var doneMedia by mutableStateOf(DoneMedia.ANIME)
    var filter by mutableStateOf(TransferFilter.ALL)
    var sort by mutableStateOf(TransferSort.ADDED)
    var descending by mutableStateOf(true)
    var addOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    var limitsOpen by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var noticeHidden by mutableStateOf(false)
    var onlyUnmatched by mutableStateOf(false)
    var deleting by mutableStateOf<List<TransferItem>?>(null)
    var deletingTitle by mutableStateOf<TitleGroup?>(null)
    var deletingChapters by mutableStateOf<List<ChapterDownload>?>(null)
    var matching by mutableStateOf<LocalLibraryItem?>(null)
    var removingLocal by mutableStateOf<LocalLibraryItem?>(null)
    var autoResult by mutableStateOf<String?>(null)
    var verifyResult by mutableStateOf<String?>(null)

    /** `Route.Downloads(tab)` → the matching place of the page. */
    fun applyTab(tab: DownloadsTab) {
        when (tab) {
            DownloadsTab.ACTIVE -> section = TransfersSection.QUEUE
            DownloadsTab.ANIME -> { section = TransfersSection.DONE; doneMedia = DoneMedia.ANIME }
            DownloadsTab.MANGA -> { section = TransfersSection.DONE; doneMedia = DoneMedia.MANGA }
            DownloadsTab.NOVELS -> { section = TransfersSection.DONE; doneMedia = DoneMedia.NOVELS }
            DownloadsTab.HISTORY -> historyOpen = true
            DownloadsTab.LOCAL -> section = TransfersSection.LOCAL
        }
    }
}

// ------------------------------------------------------------------ header

/**
 * Wide: title, a live line (how many move, the download limit) and the actions (search, limits, history, add torrent). Phone: the
 * title with icon actions (search, limits, history, settings), the search field under it when open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransfersTopBar(state: DownloadsUiState, env: TransfersEnv, onBack: (() -> Unit)?, compact: Boolean) {
    val host = env.host
    val moving = state.queue.count { it.phase == app.nijiyome.feature.downloads.transfers.TransferPhase.DOWNLOADING }
    val meta = listOfNotNull(
        pluralStringResource(Res.plurals.rd_s8_meta_moving, moving, moving),
        if (!state.torrentsAvailable) null
        else if (state.limits.downKib == 0) stringResource(Res.string.rd_s8_meta_no_limit)
        else stringResource(Res.string.rd_s8_meta_limited, app.nijiyome.ui.patterns.formatQuantity(state.limits.downKib.toLong(), app.nijiyome.ui.patterns.speedUnits())),
        state.usedBytes?.let { stringResource(Res.string.downloads_usage, app.nijiyome.ui.format.formatBytes(it)) },
    ).joinToString(" · ")
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(Res.string.rd_s8_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
                }
            },
            actions = {
                if (compact) {
                    IconButton(onClick = { host.searchOpen = !host.searchOpen }) { Icon(Icons.Outlined.Search, contentDescription = stringResource(Res.string.rd_s8_search)) }
                    IconButton(onClick = { host.limitsOpen = true }) { Icon(Icons.Outlined.Speed, contentDescription = stringResource(Res.string.rd_s8_limits_title)) }
                    IconButton(onClick = { host.historyOpen = true }) { Icon(Icons.Outlined.History, contentDescription = stringResource(Res.string.rd_s8_history_title)) }
                    env.onOpenSettings?.let { open ->
                        IconButton(onClick = open) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(Res.string.rd_s8_settings)) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                        if (env.host.section != TransfersSection.QUEUE || structuresIsQueue()) {
                            TransfersSearchField(state.query, env.vm::setQuery, Modifier.width(280.dp))
                        }
                        ActionButton(stringResource(Res.string.rd_s8_limits), onClick = { host.limitsOpen = true }, icon = Icons.Outlined.Speed, emphasis = ActionEmphasis.OUTLINED)
                        ActionButton(stringResource(Res.string.rd_s8_history_title), onClick = { host.historyOpen = true }, icon = Icons.Outlined.History, emphasis = ActionEmphasis.OUTLINED)
                        env.addTorrent?.let { ActionButton(stringResource(Res.string.rd_s8_add_torrent), onClick = it, icon = Icons.Outlined.Add, emphasis = ActionEmphasis.FILLED) }
                    }
                }
            },
        )
        if (compact && (host.searchOpen || state.query.isNotEmpty())) {
            TransfersSearchField(state.query, env.vm::setQuery, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

/** The master list has its own search field; the header shows one for the queue structure. */
@Composable
private fun structuresIsQueue(): Boolean = structures.transfers == TransfersStructure.QUEUE

// ------------------------------------------------------------------ batch actions (TOR-05)

@Composable
private fun BatchBar(state: DownloadsUiState, env: TransfersEnv, modifier: Modifier) {
    val selection = env.selection
    val chosen = (state.queue + state.stored).filter { selection.isSelected(it.key) }
    val n = chosen.size
    val torrents = chosen.any { it.isTorrent }
    val actions = buildList {
        add(SelectionAction(stringResource(Res.string.downloads_pause), Icons.Outlined.Pause, { env.vm.pauseItems(chosen) }, enabled = chosen.any { it.canPause }))
        add(SelectionAction(stringResource(Res.string.downloads_resume), Icons.Outlined.PlayArrow, { env.vm.resumeItems(chosen) }, enabled = chosen.any { it.canResume || it.canRetry }))
        if (torrents) {
            add(SelectionAction(stringResource(Res.string.rd_s8_menu_keep), Icons.Outlined.Bookmark, { env.vm.setKeep(chosen, true) }))
            add(SelectionAction(stringResource(Res.string.rd_s8_menu_unkeep), Icons.Outlined.BookmarkBorder, { env.vm.setKeep(chosen, false) }))
        }
        add(
            SelectionAction(
                pluralStringResource(Res.plurals.rd_s8_batch_delete, n, n),
                Icons.Outlined.Delete,
                { env.host.deleting = chosen },
                enabled = n > 0,
                emphasis = ActionEmphasis.DANGER,
            ),
        )
    }
    SelectionActionBar(
        visible = selection.active,
        count = selection.count,
        actions = actions,
        onExit = selection::exit,
        modifier = modifier,
        onSelectAll = { selection.selectAll(state.queue.map { it.key } + state.stored.map { it.key }) },
        selectAllLabel = stringResource(Res.string.rd_s8_select_all),
        emptyHint = stringResource(Res.string.rd_s8_select_hint),
    )
}

/** Loading: placeholders shaped like queue rows. */
@Composable
private fun LoadingRows() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(5) { LoadingPlaceholder(Modifier.fillMaxWidth().height(64.dp)) }
    }
}

// ------------------------------------------------------------------ helpers kept from the Downloads page

private val ArgsJson = Json { ignoreUnknownKeys = true }

/** `ChapterDownload.localRef` = JSON of the reader args written by s (offline = true). */
private fun <T> decodeArgs(c: ChapterDownload, serializer: kotlinx.serialization.KSerializer<T>): T? =
    runCatching { ArgsJson.decodeFromString(serializer, c.localRef) }.getOrNull()

/** `HistoryEntry.animeIdOf` inverse: "123" → AniList 123, "mal:45" → MAL 45. */
internal fun mediaRefOf(mediaId: String?): MediaRef {
    val id = mediaId?.trim().orEmpty()
    return if (id.startsWith("mal:")) MediaRef(malId = id.removePrefix("mal:").toIntOrNull()) else MediaRef(anilistId = id.toIntOrNull())
}

internal fun DownloadsTab.labelRes(): StringResource = when (this) {
    DownloadsTab.ACTIVE -> Res.string.downloads_tab_active
    DownloadsTab.ANIME -> Res.string.downloads_tab_anime
    DownloadsTab.MANGA -> Res.string.downloads_tab_manga
    DownloadsTab.NOVELS -> Res.string.downloads_tab_novels
    DownloadsTab.HISTORY -> Res.string.downloads_tab_history
    DownloadsTab.LOCAL -> Res.string.downloads_tab_local
}

/** Localised text of a download failure (the short codes of `DownloadRepositoryImpl` + the shared [userMessage]). */
@Composable
internal fun rememberDownloadErrorText(): (Throwable) -> String {
    val dash = stringResource(Res.string.downloads_err_dash)
    val limit = stringResource(Res.string.downloads_err_limit)
    val noSource = stringResource(Res.string.downloads_err_no_source)
    val folder = stringResource(Res.string.downloads_err_folder)
    val generic = stringResource(Res.string.downloads_err_generic)
    val transfer = rememberTransferErrorText()
    return remember(dash, limit, noSource, folder, generic, transfer) {
        { t: Throwable ->
            val code = ((t as? AppException)?.error as? AppError.Extension)?.msg ?: (t.toAppError() as? AppError.Unknown)?.msg
            codeText(code, dash, limit, noSource, folder)
                ?: code?.takeIf { it.startsWith("torrent:") }?.let(transfer)
                ?: generic.format(t.message ?: t::class.java.simpleName)
        }
    }
}

internal fun codeText(code: String?, dash: String, limit: String, noSource: String, folder: String): String? = when (code) {
    "dash_not_supported" -> dash
    "storage_limit" -> limit
    "no_source" -> noSource
    "folder_unavailable" -> folder
    else -> null
}

/**
 * 1.1 review #7: the debrid reason inside a stored download error: a bare `debrid:torbox:ACTIVE_LIMIT`, or one wrapped by an older
 * desktop build (`Unknown(msg=debrid:torbox:ACTIVE_LIMIT)`). Null = not a debrid error.
 */
internal fun debridProblemIn(error: String): app.nijiyome.model.torrent.DebridProblem? {
    val code = DEBRID_ERROR.find(error)?.value ?: return null
    return app.nijiyome.model.torrent.debridProblemOf(AppError.Unknown(code))
}

private val DEBRID_ERROR = Regex("""debrid:[A-Za-z0-9_:]+""")

@Composable
internal fun ProfilePicker(selected: DownloadProfile, onSelect: (DownloadProfile) -> Unit) {
    Segmented(
        options = DownloadProfile.entries.map { SegmentedOption(it, stringResource(it.labelRes())) },
        selected = selected,
        onSelect = onSelect,
        small = true,
    )
}

internal fun DownloadProfile.labelRes(): StringResource = when (this) {
    DownloadProfile.BEST -> Res.string.downloads_profile_best
    DownloadProfile.BALANCED_1080 -> Res.string.downloads_profile_balanced
    DownloadProfile.SAVER_720 -> Res.string.downloads_profile_saver
}

internal fun sameRef(a: MediaRef, b: MediaRef): Boolean =
    (a.anilistId != null && a.anilistId == b.anilistId) || (a.malId != null && a.malId == b.malId)
