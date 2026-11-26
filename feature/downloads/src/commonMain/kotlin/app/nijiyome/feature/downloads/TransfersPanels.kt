// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.feature.downloads.resources.Res
import app.nijiyome.feature.downloads.resources.*
import app.nijiyome.model.AutoDownloadRule
import app.nijiyome.model.ChapterDownload
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadProfile
import app.nijiyome.model.DownloadState
import app.nijiyome.model.ListStatus
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.LocalLibraryItem
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.torrent.TorrentServiceStatus
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.patterns.ActionButton
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.NumberStepperField
import app.nijiyome.ui.platform.rememberFilePicker
import app.nijiyome.ui.platform.rememberFolderPicker
import app.nijiyome.ui.platform.rememberOpenFolder
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

// Sparkles redesign, lane S8 (Transferencias): the background notice, the panels (storage, background, auto-download, local library)
// and the shelves of what is on disk. Shared by both structures.

// ------------------------------------------------------------------ background downloads (TOR-16, TOR-17)

/**
 * The background downloads notice, short and inside the page (it no longer covers the list): what it costs, then "Got it, turn on"
 * (stores the acknowledgement, turns them on, asks for notifications on Android 13+) or "Not now" (hides it for this visit).
 */
@Composable
internal fun BackgroundNotice(onAccept: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    TransfersPanel(
        title = stringResource(if (desktopDownloadsUi) Res.string.rd_s8_bg_title_desktop else Res.string.rd_s8_bg_title),
        modifier = modifier,
        trailing = { Icon(Icons.Outlined.BatteryAlert, contentDescription = null, tint = LocalSignalColors.current.warn) },
    ) {
        Text(stringResource(Res.string.rd_s8_bg_body), style = MaterialTheme.typography.bodyMedium)
        val points = buildList {
            add(Res.string.rd_s8_bg_cpu)
            add(Res.string.rd_s8_bg_disk)
            add(Res.string.rd_s8_bg_network)
            add(Res.string.rd_s8_bg_upload)
            if (!desktopDownloadsUi) add(Res.string.rd_s8_bg_notification)
        }
        points.forEach { res ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodySmall)
                Text(stringResource(res), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            stringResource(if (desktopDownloadsUi) Res.string.rd_s8_bg_note_desktop else Res.string.rd_s8_bg_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionButton(stringResource(Res.string.rd_s8_bg_accept), onClick = onAccept, emphasis = ActionEmphasis.FILLED)
            TextButton(onClick = onNotNow) { Text(stringResource(Res.string.rd_s8_bg_not_now)) }
        }
    }
}

/** What the background service says now: on / off, waiting for Wi-Fi, low battery, paused by the system (with resume). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackgroundStatusRow(
    enabled: Boolean,
    status: TorrentServiceStatus,
    onTurnOn: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signals = LocalSignalColors.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (enabled) {
            SignalChip(stringResource(Res.string.rd_s8_bg_on), Icons.Outlined.CloudDone, signals.ok)
        } else {
            SignalChip(stringResource(Res.string.rd_s8_bg_off), Icons.Outlined.CloudOff, signals.warn)
            TextButton(onClick = onTurnOn) { Text(stringResource(Res.string.rd_s8_bg_turn_on)) }
        }
        if (status.waitingForWifi) SignalChip(stringResource(Res.string.rd_s8_bg_wifi), Icons.Outlined.SignalWifiOff, signals.info)
        if (status.pausedLowBattery) SignalChip(stringResource(Res.string.rd_s8_bg_battery), Icons.Outlined.BatteryAlert, signals.warn)
        if (status.pausedBySystem) {
            SignalChip(stringResource(Res.string.rd_s8_bg_system), Icons.Outlined.ReportProblem, signals.warn)
            TextButton(onClick = onResumeAll) { Text(stringResource(Res.string.rd_s8_bg_resume_all)) }
        }
    }
}

/** Desktop aside: the background switch (after the notice was accepted). */
@Composable
internal fun BackgroundPanel(enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    TransfersPanel(stringResource(Res.string.rd_s8_bg_panel), modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.rd_s8_bg_switch), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.rd_s8_bg_switch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

// ------------------------------------------------------------------ storage (DL-01, DL-16)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoragePanel(
    usedBytes: Long?,
    busy: Boolean,
    verifyResult: String?,
    onVerify: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val openFolder = rememberOpenFolder()
    val koin = getKoin()
    val settingsRepo = remember(koin) { koin.getOrNull<app.nijiyome.model.SettingsRepository>() }
    val chosen = settingsRepo?.settings?.collectAsStateWithLifecycle()?.value?.downloads?.directoryTreeUri
    val root = rememberDownloadFolderLabel(chosen)
    TransfersPanel(
        stringResource(Res.string.rd_s8_storage),
        modifier,
        trailing = { usedBytes?.let { Text(stringResource(Res.string.downloads_usage, formatBytes(it)), style = MaterialTheme.typography.labelMedium) } },
    ) {
        if (busy) app.nijiyome.designsystem.motion.LoadingPlaceholder(Modifier.fillMaxWidth().height(6.dp))
        verifyResult?.let { ResultLine(it) }
        // TRA-01: a flow row, so "Abrir carpeta" moves under "Comprobar archivos" when the aside is narrow instead of wrapping
        // letter by letter.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                stringResource(Res.string.downloads_verify),
                onClick = onVerify,
                icon = Icons.AutoMirrored.Outlined.FactCheck,
                emphasis = ActionEmphasis.OUTLINED,
                enabled = !busy,
            )
            if (openFolder != null && root.isNotBlank()) {
                TextButton(onClick = { openFolder(root) }) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.downloads_desktop_open_folder), Modifier.padding(start = 6.dp), maxLines = 1, softWrap = false)
                }
            }
        }
        if (onOpenSettings != null) {
            TextButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rd_s8_more_settings), Modifier.padding(start = 6.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ auto-download (DL-05, DL-16)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AutoDownloadPanel(
    rules: List<AutoDownloadRule>,
    vm: DownloadsViewModel,
    busy: Boolean,
    result: String?,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val lists = remember { koin.getOrNull<ListsRepository>() }
    val watchingFlow = remember(lists) { lists?.entries(ListStatus.WATCHING) ?: flowOf(emptyList()) }
    val watching by watchingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val suggestions = watching.filter { w -> rules.none { r -> sameRef(r.ref, w.ref) } }
    // TRA-02: the check button sits under the explanation (beside the title it squeezed "Auto-descarga" onto two lines), and the
    // suggestions from Watching start folded so the panels below stay in view.
    var showAllSuggestions by remember { mutableStateOf(false) }
    TransfersPanel(stringResource(Res.string.downloads_auto_title), modifier) {
        Text(
            stringResource(if (desktopDownloadsUi) Res.string.downloads_desktop_auto_body else Res.string.downloads_auto_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionButton(
            stringResource(if (busy) Res.string.rd_s8_auto_checking else Res.string.downloads_auto_check),
            onClick = vm::checkNow,
            icon = Icons.Outlined.Refresh,
            emphasis = ActionEmphasis.OUTLINED,
            enabled = !busy,
        )
        result?.let { ResultLine(it, icon = Icons.Outlined.Science) }
        rules.forEach { rule ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                MediaCover(url = rule.poster, modifier = Modifier.width(32.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(Res.string.downloads_auto_last, rule.lastEpisode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProfilePicker(rule.profile) { p -> vm.setRule(rule.copy(profile = p)) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { on -> vm.setRule(rule.copy(enabled = on)) },
                    )
                    TextButton(onClick = { vm.preview(rule.ref) }) { Text(stringResource(Res.string.downloads_auto_simulate)) }
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            Text(stringResource(Res.string.downloads_auto_suggest), style = MaterialTheme.typography.labelLarge)
            val offered = suggestions.take(MAX_SUGGESTIONS)
            val shown = if (showAllSuggestions) offered else offered.take(FOLDED_SUGGESTIONS)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEach { entry ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.addRule(entry.ref, entry.title, entry.poster, DownloadProfile.BALANCED_1080) },
                        label = { Text(stringResource(Res.string.rd_s8_auto_add, entry.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            if (offered.size > FOLDED_SUGGESTIONS) {
                TextButton(onClick = { showAllSuggestions = !showAllSuggestions }) {
                    Text(
                        if (showAllSuggestions) {
                            stringResource(Res.string.rd_s8_auto_fewer)
                        } else {
                            stringResource(Res.string.rd_s8_auto_more, offered.size - FOLDED_SUGGESTIONS)
                        },
                    )
                }
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 8
private const val FOLDED_SUGGESTIONS = 3

// ------------------------------------------------------------------ local library (DL-09, DL-10, DL-11, DL-15)

/** Import / scan actions, the "not matched" filter and the watched folders (desktop). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocalLibraryHeader(
    items: List<LocalLibraryItem>,
    folders: List<String>,
    onlyUnmatched: Boolean,
    onOnlyUnmatched: (Boolean) -> Unit,
    vm: DownloadsViewModel,
    modifier: Modifier = Modifier,
) {
    val pickFiles = rememberFilePicker(mimeTypes = listOf("video/*")) { uris -> if (uris.isNotEmpty()) vm.importFiles(uris) }
    val pickFolder = rememberFolderPicker { uri -> if (uri != null) vm.scanFolder(uri) }
    val openFolder = rememberOpenFolder()
    val unmatched = items.count { !it.matched }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(stringResource(Res.string.downloads_local_import), onClick = pickFiles, icon = Icons.Outlined.VideoFile)
            ActionButton(stringResource(Res.string.downloads_local_scan), onClick = pickFolder, icon = Icons.Outlined.FolderOpen, emphasis = ActionEmphasis.OUTLINED)
            if (unmatched > 0) {
                FilterChip(
                    selected = onlyUnmatched,
                    onClick = { onOnlyUnmatched(!onlyUnmatched) },
                    label = { Text(pluralStringResource(Res.plurals.rd_s8_local_unmatched, unmatched, unmatched)) },
                    leadingIcon = { Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (desktopDownloadsUi) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.rd_s8_local_folders), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = pickFolder) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.downloads_desktop_library_add), Modifier.padding(start = 6.dp))
                }
            }
            Text(
                stringResource(if (folders.isEmpty()) Res.string.downloads_desktop_library_empty else Res.string.downloads_desktop_library_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            folders.forEach { folder ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(folder, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (openFolder != null) {
                        IconButton(onClick = { openFolder(folder) }) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = stringResource(Res.string.downloads_desktop_open_folder))
                        }
                    }
                    IconButton(onClick = { vm.forgetFolder(folder) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.downloads_desktop_library_remove, folder))
                    }
                }
            }
        }
    }
}

/** The local library grouped by series, unmatched files last. */
internal fun localGroups(items: List<LocalLibraryItem>, onlyUnmatched: Boolean): List<Pair<String?, List<LocalLibraryItem>>> {
    val shown = if (onlyUnmatched) items.filterNot { it.matched } else items
    val (matched, rest) = shown.partition { it.matched }
    return matched.groupBy { it.title }.entries.sortedBy { it.key?.lowercase() }.map { it.key to it.value.sortedBy { v -> v.episode ?: 0 } } +
        if (rest.isEmpty()) emptyList() else listOf(null to rest)
}

@Composable
internal fun LocalRow(item: LocalLibraryItem, onPlay: () -> Unit, onMatch: () -> Unit, onRemove: () -> Unit) {
    val title = item.title ?: item.fileName
    Surface(onClick = onPlay, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaCover(url = item.poster, modifier = Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.episode?.let { stringResource(UiR.string.ui_episode_number, it) }, item.fileName, formatBytes(item.sizeBytes)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!item.matched) SignalChip(stringResource(Res.string.downloads_local_unmatched), Icons.Outlined.LinkOff, LocalSignalColors.current.warn)
            IconButton(onClick = onPlay) { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.downloads_play)) }
            val openFolder = rememberOpenFolder()
            if (openFolder != null) {
                IconButton(onClick = { openFolder(item.uri) }) { Icon(Icons.Outlined.FolderOpen, contentDescription = stringResource(Res.string.downloads_desktop_open_folder)) }
            }
            IconButton(onClick = onMatch) { Icon(Icons.Outlined.Link, contentDescription = stringResource(Res.string.rd_s8_local_match_for, title)) }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.downloads_local_remove)) }
        }
    }
}

/** DL-11: remove from the library, keeping or deleting the file, or cancel. */
@Composable
internal fun LocalRemoveDialog(item: LocalLibraryItem, onRemove: (deleteFile: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.downloads_local_remove)) },
        text = { Text(stringResource(Res.string.downloads_local_remove_body, item.fileName)) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onRemove(false) }) { Text(stringResource(Res.string.downloads_local_remove_entry)) }
                TextButton(
                    onClick = { onRemove(true) },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.downloads_local_remove_file)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

/**
 * DL-10: match a local video. Search AniList, pick a result, choose the episode with a stepper (no typing), Save; Unmatch and Cancel
 * are separate.
 */
@Composable
internal fun MatchDialog(item: LocalLibraryItem, onDismiss: () -> Unit, onMatch: (Media?, Int?) -> Unit) {
    val koin = getKoin()
    val metadata = remember { koin.getOrNull<MetadataRepository>() }
    var query by remember { mutableStateOf(item.title ?: item.fileName.substringBeforeLast('.')) }
    var episode by remember { mutableStateOf((item.episode ?: 1).toLong()) }
    var results by remember { mutableStateOf<List<Media>>(emptyList()) }
    var picked by remember { mutableStateOf<Media?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val search: () -> Unit = {
        val repo = metadata
        if (repo != null && query.isNotBlank()) {
            searching = true
            failed = false
            scope.launch {
                val r = runCatching { repo.search(MediaKind.ANIME, query).items.take(6) }
                results = r.getOrDefault(emptyList())
                failed = r.isFailure
                searching = false
            }
        }
    }
    LaunchedEffect(Unit) { search() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rd_s8_local_match_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.downloads_local_match_title)) },
                    trailingIcon = {
                        IconButton(onClick = search) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(Res.string.rd_s8_search_anilist))
                        }
                    },
                )
                if (searching) app.nijiyome.designsystem.motion.LoadingPlaceholder(Modifier.fillMaxWidth().height(40.dp))
                if (failed) Text(stringResource(Res.string.downloads_local_match_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (!searching && !failed && results.isEmpty()) {
                    Text(stringResource(Res.string.rd_s8_search_none), style = MaterialTheme.typography.bodySmall)
                }
                results.forEach { m ->
                    Surface(
                        onClick = { picked = m },
                        color = if (picked == m) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MediaCover(url = m.cover, modifier = Modifier.width(32.dp))
                            Text(
                                listOfNotNull(m.title, m.year?.toString()).joinToString(" · "),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (picked == m) Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        }
                    }
                }
                NumberStepperField(
                    value = episode,
                    onValueChange = { episode = it },
                    min = 1,
                    max = 9_999,
                    label = stringResource(Res.string.downloads_local_match_episode),
                )
            }
        },
        confirmButton = {
            Row {
                if (item.ref != null) {
                    TextButton(onClick = {
                        onMatch(null, null)
                        onDismiss()
                    }) { Text(stringResource(Res.string.downloads_local_match_clear)) }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_cancel)) }
                TextButton(
                    onClick = {
                        onMatch(picked ?: currentMedia(item), episode.toInt())
                        onDismiss()
                    },
                    enabled = picked != null || item.ref != null,
                ) { Text(stringResource(Res.string.downloads_local_match_save)) }
            }
        },
    )
}

/** Keeps the current match when only the episode changes. */
private fun currentMedia(item: LocalLibraryItem): Media? {
    val ref = item.ref ?: return null
    return Media(
        ref = ref, kind = MediaKind.ANIME, title = item.title ?: item.fileName, titles = emptyMap(), cover = item.poster, banner = null, color = null,
        synopsis = null, genres = emptyList(), episodes = null, status = null, season = null, year = null, score = null, sourceProvider = "local",
    )
}

// ------------------------------------------------------------------ shelves: what is on disk (DL-04, DL-06, DL-07)

/** Which media the "Descargado" shelf shows. */
internal enum class DoneMedia { ANIME, MANGA, NOVELS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DoneMediaChips(selected: DoneMedia, counts: Map<DoneMedia, Int>, onSelect: (DoneMedia) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DoneMedia.entries.forEach { m ->
            FilterChip(
                selected = selected == m,
                onClick = { onSelect(m) },
                label = {
                    Text(
                        stringResource(
                            when (m) {
                                DoneMedia.ANIME -> Res.string.downloads_tab_anime
                                DoneMedia.MANGA -> Res.string.downloads_tab_manga
                                DoneMedia.NOVELS -> Res.string.downloads_tab_novels
                            },
                        ) + " · " + (counts[m] ?: 0),
                    )
                },
            )
        }
    }
}

/** The anime shelf: completed episodes by title, then complete torrents. */
internal fun LazyListScope.animeShelf(
    groups: List<TitleGroup>,
    torrents: List<app.nijiyome.feature.downloads.transfers.TransferItem>,
    onOpenDetails: (Int) -> Unit,
    onPlay: (DownloadItem) -> Unit,
    onRedownload: (DownloadItem) -> Unit,
    onDeleteEpisode: (DownloadItem) -> Unit,
    onDeleteTitle: (TitleGroup) -> Unit,
    onOpenTorrent: (app.nijiyome.feature.downloads.transfers.TransferItem) -> Unit,
) {
    if (groups.isEmpty() && torrents.isEmpty()) {
        item(key = "done-empty") {
            ShelfEmpty(stringResource(Res.string.downloads_empty_anime), stringResource(Res.string.downloads_empty_anime_body))
        }
        return
    }
    groups.forEach { group ->
        item(key = "g:" + group.ref.anilistId + ":" + group.ref.malId + ":" + group.title) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaCover(url = group.poster, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(Res.string.downloads_group_summary, group.episodes.size, formatBytes(group.bytes)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                group.ref.anilistId?.let { id -> TextButton(onClick = { onOpenDetails(id) }) { Text(stringResource(Res.string.rd_s8_open_details)) } }
                TextButton(onClick = { onDeleteTitle(group) }) { Text(stringResource(Res.string.rd_s8_delete_all)) }
            }
        }
        items(group.episodes, key = { "e:" + it.id }) { ep -> EpisodeShelfRow(ep, onPlay, onRedownload, onDeleteEpisode) }
    }
    if (torrents.isNotEmpty()) {
        item(key = "ct-header") {
            Text(
                stringResource(Res.string.downloads_torrents),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(torrents, key = { "ct:" + it.key }) { t ->
            Surface(onClick = { onOpenTorrent(t) }, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaCover(url = t.poster, modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.displayTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(t.kind.label(), t.sizeBytes?.let(::formatBytes)).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PhaseChip(t.phase)
                }
            }
        }
    }
}

/** TRA-03: an empty shelf draws the direction's empty art, like the empty queue above it. */
@Composable
private fun ShelfEmpty(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyArt(EmptyKind.DOWNLOADS, Modifier.size(96.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EpisodeShelfRow(item: DownloadItem, onPlay: (DownloadItem) -> Unit, onRedownload: (DownloadItem) -> Unit, onDelete: (DownloadItem) -> Unit) {
    val broken = item.state == DownloadState.BROKEN
    Row(
        Modifier.fillMaxWidth().padding(start = 72.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(UiR.string.ui_episode_number, item.episode), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(
                    item.sourceLabel.takeIf { it.isNotBlank() },
                    stringResource(if (item.extId == app.nijiyome.feature.downloads.transfers.DEBRID_EXTENSION_ID) Res.string.rd_s8_kind_debrid else Res.string.rd_s8_kind_web),
                    (item.totalBytes ?: item.downloadedBytes).takeIf { it > 0 }?.let(::formatBytes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (broken) {
            SignalChip(stringResource(Res.string.rd_s8_broken), Icons.Outlined.BrokenImage, LocalSignalColors.current.err)
            TextButton(onClick = { onRedownload(item) }) { Text(stringResource(Res.string.downloads_redownload)) }
        } else {
            IconButton(onClick = { onPlay(item) }) { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.downloads_play)) }
        }
        val openFolder = rememberOpenFolder()
        val file = item.localUri
        if (openFolder != null && file != null) {
            IconButton(onClick = { openFolder(file) }) { Icon(Icons.Outlined.FolderOpen, contentDescription = stringResource(Res.string.downloads_desktop_open_folder)) }
        }
        IconButton(onClick = { onDelete(item) }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.rd_s8_delete_episode)) }
    }
}

/** Manga or novel chapters by title: read offline, retry, delete one (with undo) or all (confirmed). */
internal fun LazyListScope.chapterShelf(
    list: List<ChapterDownload>,
    novels: Boolean,
    onOpen: (ChapterDownload) -> Unit,
    onRetry: (ChapterDownload) -> Unit,
    onDelete: (ChapterDownload) -> Unit,
    onDeleteAll: (List<ChapterDownload>) -> Unit,
) {
    if (list.isEmpty()) {
        item(key = "ch-empty") {
            ShelfEmpty(
                stringResource(if (novels) Res.string.downloads_empty_novels else Res.string.downloads_empty_manga),
                stringResource(Res.string.downloads_empty_chapters_body),
            )
        }
        return
    }
    list.groupBy { it.mediaId }.forEach { (mediaId, chapters) ->
        val first = chapters.first()
        item(key = "ch:$mediaId") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaCover(url = first.poster, modifier = Modifier.width(44.dp))
                Text(first.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onDeleteAll(chapters) }) { Text(stringResource(Res.string.rd_s8_delete_all)) }
            }
        }
        items(chapters.sortedBy { it.chapterNumber ?: Double.MAX_VALUE }, key = { "c:" + it.id }) { c ->
            val done = c.state == DownloadState.COMPLETED
            val failed = c.state == DownloadState.FAILED || c.state == DownloadState.BROKEN
            Row(
                Modifier.fillMaxWidth().padding(start = 72.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.chapterLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(stringResource(c.state.labelRes()), formatBytes(c.bytes)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (c.state == DownloadState.DOWNLOADING || c.state == DownloadState.QUEUED) {
                        ProgressTrack(progress = c.progress.toFloat().coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                    }
                }
                when {
                    failed -> TextButton(onClick = { onRetry(c) }) { Text(stringResource(Res.string.rd_s8_retry)) }
                    done -> IconButton(onClick = { onOpen(c) }) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = stringResource(Res.string.rd_s8_read_offline))
                    }
                }
                IconButton(onClick = { onDelete(c) }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.rd_s8_delete_chapter)) }
            }
        }
    }
}
