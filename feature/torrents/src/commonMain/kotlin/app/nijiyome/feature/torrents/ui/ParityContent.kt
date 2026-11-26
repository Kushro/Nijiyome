// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.torrents.ui

import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.feature.torrents.resources.Res
import app.nijiyome.feature.torrents.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.platform.rememberFilePicker
import app.nijiyome.ui.platform.windowWidthDp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import app.nijiyome.model.torrent.TorrentPreview
import app.nijiyome.model.torrent.TorrentPreviewProgress
import app.nijiyome.model.torrent.TORRENT_METADATA_TIMEOUT_SECS
import app.nijiyome.model.torrent.TORRENT_PREVIEW_TIMEOUT_DETAIL
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.SIDE_SHEET_MIN_WIDTH_DP
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.SectionCard
import app.nijiyome.feature.torrents.vm.AddSourceKind
import app.nijiyome.feature.torrents.vm.AddTorrentEvent
import app.nijiyome.feature.torrents.vm.AddTorrentViewModel
import app.nijiyome.feature.torrents.vm.DebridViewModel
import app.nijiyome.feature.torrents.vm.TorrentDetailViewModel
import app.nijiyome.feature.torrents.vm.TorrentHistoryEvent
import app.nijiyome.feature.torrents.vm.TorrentHistoryViewModel
import app.nijiyome.model.AppError
import app.nijiyome.model.LoadState
import app.nijiyome.model.torrent.BandwidthSchedule
import app.nijiyome.model.torrent.DebridAccount
import app.nijiyome.model.torrent.DebridService
import app.nijiyome.model.torrent.DebridSettings
import app.nijiyome.model.torrent.debridProblemOf
import app.nijiyome.feature.torrents.vm.DebridCheck
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.model.torrent.EpisodeAlignment
import app.nijiyome.model.torrent.TorrentFile
import app.nijiyome.model.torrent.TorrentSessionEntry
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.release.isVideoPath
import app.nijiyome.torrent.policy.AlignFile
import app.nijiyome.torrent.policy.alignedEpisodes
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.media.MediaCover
import org.koin.compose.viewmodel.koinViewModel
import java.text.DateFormat
import java.util.Date
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls

// parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.9, worker q): Añadir torrent, Alinear episodios, Historial,
// Debrid, re-announce and the new torrent settings.

// ==================================================================== Añadir torrent

/**
 * "Añadir torrent" (torrent.add-magnet / add-file / magnet-link-handler / episode-alignment): magnet or info-hash field, `.torrent` from
 * SAF, optional series (AniList search) and alignment (first episode / offset), "Guardar para después" (persisted download) switch.
 * [onAdded] receives the new torrent id (the client opens it).
 */
@Composable
internal fun AddTorrentSheet(vm: AddTorrentViewModel, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val storage = rememberStorageAccess()
    val currentAdded by rememberUpdatedState(onAdded)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is AddTorrentEvent.Added -> currentAdded(event.id)
            }
        }
    }
    val picker = rememberFilePicker(listOf("application/x-bittorrent", "application/octet-stream", "*/*"), multiple = false) { uris ->
        uris.firstOrNull()?.let { uri -> vm.setSource(storage.documentUri(uri), fileName = storage.documentName(uri)) }
    }
    // AdaptiveSheet already scrolls its content on the wide (side sheet) branch; a second unbounded scroll inside it
    // throws ("infinity maximum height"), so this sheet scrolls by itself only on the bottom sheet branch.
    val sheetScrolls = windowWidthDp() >= SIDE_SHEET_MIN_WIDTH_DP
    // Desktop: a .torrent dragged from the file manager onto the sheet, and the file list read as soon as the source is valid
    // (a pasted magnet waits a moment, it may still be typed).
    var dropHover by remember { mutableStateOf(false) }
    if (desktopTorrentUi) {
        LaunchedEffect(state.source, state.kind) {
            if (state.kind == AddSourceKind.EMPTY || state.kind == AddSourceKind.INVALID) return@LaunchedEffect
            if (state.kind != AddSourceKind.FILE) delay(PREVIEW_DEBOUNCE_MS)
            vm.loadPreview()
        }
    }
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .torrentFileDrop(onHover = { dropHover = it }) { path -> vm.setSource(storage.documentUri(path), fileName = storage.documentName(path)) }
                .then(
                    if (dropHover) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                    } else {
                        Modifier
                    },
                )
                .then(if (sheetScrolls) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.torrent_add_title), style = MaterialTheme.typography.titleLarge)
            if (state.kind == AddSourceKind.FILE) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null)
                    Text(state.fileName ?: state.source, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { vm.setSource("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.torrent_add_clear))
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.source,
                    onValueChange = { vm.setSource(it) },
                    label = { Text(stringResource(Res.string.torrent_add_source)) },
                    placeholder = { Text("magnet:?xt=urn:btih:…") },
                    isError = state.kind == AddSourceKind.INVALID,
                    supportingText = {
                        Text(
                            stringResource(
                                when (state.kind) {
                                    AddSourceKind.INVALID -> Res.string.torrent_android_invalid_source
                                    AddSourceKind.HASH -> Res.string.torrent_add_kind_hash
                                    AddSourceKind.MAGNET -> Res.string.torrent_add_kind_magnet
                                    else -> Res.string.torrent_add_source_hint
                                },
                            ),
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                )
                // Sparkles (TOR-23): pick a .torrent or paste a magnet / info-hash from the clipboard.
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker() }, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(Res.string.torrent_add_pick_file), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = {
                        runCatching { clipboard.getText()?.text }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.setSource(it) }
                    }, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(Res.string.rd_s8_tor_add_from_clipboard), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (desktopTorrentUi) {
                    Text(
                        stringResource(if (dropHover) Res.string.torrent_desktop_add_drop else Res.string.torrent_desktop_add_drop_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dropHover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // v1.1: how reading the files goes, right under the source (the file table further down may be out of view).
            state.preview?.takeUnless { it is LoadState.Ok }?.let { preview ->
                AddTorrentPreviewStatus(preview = preview, progress = state.previewProgress, onRetry = vm::loadPreview)
            }

            HorizontalDivider()
            Text(stringResource(Res.string.torrent_add_series), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.torrent_add_series_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val media = state.media
            if (media != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MediaCover(url = media.cover, modifier = Modifier.width(40.dp))
                    Text(media.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { vm.pickMedia(null) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.torrent_add_series_clear))
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.seriesQuery,
                    onValueChange = vm::setSeriesQuery,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(Res.string.torrent_add_series_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                when (val results = state.results) {
                    null -> Unit
                    LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
                    is LoadState.Err -> Text(torrentErrorMessage(results.cause), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is LoadState.Ok -> if (results.value.isEmpty()) {
                        Text(stringResource(Res.string.torrent_add_series_none), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column {
                            results.value.forEach { m ->
                                ListItem(
                                    headlineContent = { Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = m.year?.let { y -> { Text(y.toString()) } },
                                    leadingContent = { MediaCover(url = m.cover, modifier = Modifier.width(32.dp)) },
                                    modifier = Modifier.clickable { vm.pickMedia(m) },
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(stringResource(Res.string.torrent_align_first), state.firstEpisode, vm::setFirstEpisode, Modifier.weight(1f), allowNegative = false)
                IntField(stringResource(Res.string.torrent_align_offset), state.offset, vm::setOffset, Modifier.weight(1f), allowNegative = true)
            }
            (state.preview as? LoadState.Ok)?.let { preview ->
                AddTorrentFiles(
                    preview = preview,
                    chosen = state.chosenFiles,
                    alignment = EpisodeAlignment(state.firstEpisode, state.offset),
                    onToggle = vm::toggleFile,
                    onChoose = vm::setChosenFiles,
                )
            }
            SwitchRow(
                title = stringResource(Res.string.torrent_add_persist),
                hint = stringResource(Res.string.torrent_add_persist_hint),
                checked = state.persist,
                onChange = vm::setPersist,
            )
            state.error?.let { Text(torrentErrorMessage(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) }
                Button(
                    onClick = vm::add,
                    enabled = state.canAdd,
                    modifier = Modifier.padding(start = 8.dp),
                    shape = NijiyomeControls.shape,
                ) {
                    if (state.adding) LoadingIndicator(Modifier.size(20.dp))
                    Text(stringResource(if (state.adding) Res.string.torrent_add_adding else Res.string.torrent_add_confirm), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

private const val PREVIEW_DEBOUNCE_MS = 600L

/**
 * v1.1, desktop "Añadir torrent": the wait for the files of the source (a magnet asks the swarm: seconds out of the cap, and whether
 * any peer is connected yet), or why it failed, with a retry.
 */
@Composable
private fun AddTorrentPreviewStatus(preview: LoadState<TorrentPreview>, progress: TorrentPreviewProgress?, onRetry: () -> Unit) {
    when (preview) {
        LoadState.Loading -> Row(
            Modifier.fillMaxWidth().testTag(ADD_PREVIEW_STATUS_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingIndicator(Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (progress == null) {
                        stringResource(Res.string.torrent_desktop_add_files_loading)
                    } else {
                        stringResource(Res.string.torrent_desktop_add_files_searching, progress.elapsedSecs, progress.timeoutSecs)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (progress != null) {
                    Text(
                        if (progress.peers == 0) {
                            stringResource(Res.string.torrent_desktop_add_files_no_peers)
                        } else {
                            stringResource(Res.string.torrent_desktop_add_files_peers, progress.peers)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(Res.string.torrent_desktop_add_files_whole),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is LoadState.Err -> Row(Modifier.fillMaxWidth().testTag(ADD_PREVIEW_STATUS_TAG), verticalAlignment = Alignment.CenterVertically) {
            val cause = preview.cause
            Text(
                if ((cause as? AppError.Unknown)?.msg == TORRENT_PREVIEW_TIMEOUT_DETAIL) {
                    stringResource(Res.string.torrent_desktop_add_files_timeout, progress?.timeoutSecs ?: TORRENT_METADATA_TIMEOUT_SECS.toInt())
                } else {
                    stringResource(Res.string.torrent_desktop_add_files_error) + ": " + torrentErrorMessage(cause)
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_retry)) }
        }
        is LoadState.Ok -> Unit
    }
}

/** Test tag of [AddTorrentPreviewStatus]. */
internal const val ADD_PREVIEW_STATUS_TAG = "add-torrent-preview-status"

/**
 * Desktop "Añadir torrent" › Archivos (Tatakai Next's add-sheet table): every file of the torrent with a check box (only the checked
 * ones download), its size and the episode it would be under the alignment above; all / none / videos only.
 */
@Composable
private fun AddTorrentFiles(
    preview: LoadState.Ok<TorrentPreview>,
    chosen: Set<Int>,
    alignment: EpisodeAlignment,
    onToggle: (Int) -> Unit,
    onChoose: (Set<Int>) -> Unit,
) {
    HorizontalDivider()
    Text(stringResource(Res.string.torrent_desktop_add_files), style = MaterialTheme.typography.titleSmall)
    val files = preview.value.files
    val sorted = remember(files) { files.sortedBy { it.path.lowercase() } }
    val videos = remember(files) { files.filter { isVideoPath(it.path) } }
    val parsed = remember(videos) {
        videos.map { AlignFile(it.index, it.path, app.nijiyome.release.parseRelease(it.path.substringAfterLast('/')).episodes?.first) }
    }
    val episodes = remember(parsed, alignment) { alignedEpisodes(parsed, alignment) }
    val chosenBytes = files.filter { it.index in chosen }.sumOf { it.sizeBytes }
    Text(
        stringResource(Res.string.torrent_desktop_add_files_count, chosen.size, files.size, formatBytes(chosenBytes)),
        style = monoStyle(MaterialTheme.typography.bodySmall),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onChoose(files.map { it.index }.toSet()) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.torrent_desktop_add_files_all)) }
        TextButton(onClick = { onChoose(emptySet()) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.torrent_desktop_add_files_none)) }
        if (videos.isNotEmpty() && videos.size != files.size) {
            TextButton(onClick = { onChoose(videos.map { it.index }.toSet()) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.torrent_desktop_add_files_videos)) }
        }
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
        items(sorted, key = { it.index }) { f ->
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(f.index) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = f.index in chosen, onCheckedChange = { onToggle(f.index) })
                Text(f.path, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    episodes[f.index]?.let { stringResource(Res.string.torrent_desktop_add_file_episode, it) } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(56.dp),
                )
                Text(formatBytes(f.sizeBytes), style = monoStyle(MaterialTheme.typography.labelMedium), modifier = Modifier.width(80.dp))
            }
        }
    }
    Text(
        stringResource(if (chosen.isEmpty()) Res.string.torrent_desktop_add_no_files else Res.string.torrent_desktop_add_files_hint),
        style = MaterialTheme.typography.bodySmall,
        color = if (chosen.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Integer field that saves every valid value (blank / "-" keep the last one). */
@Composable
private fun IntField(label: String, value: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier, allowNegative: Boolean) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val cleaned = input.filterIndexed { i, c -> c.isDigit() || (allowNegative && c == '-' && i == 0) }.take(5)
            text = cleaned
            cleaned.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

// ==================================================================== Alinear episodios (Files tab)

/**
 * Files › "Alinear episodios" (torrent.episode-alignment): proposes the episode of every video (high confidence when the name carries
 * the number, "by order" otherwise) and lets the user shift / renumber before saving the labels.
 */
@Composable
internal fun AlignmentSheet(files: List<TorrentFile>, vm: TorrentDetailViewModel, onDismiss: () -> Unit) {
    var first by rememberSaveable { mutableStateOf(1) }
    var offset by rememberSaveable { mutableStateOf(0) }
    val videos = remember(files) { files.filter { isVideoPath(it.path) } }
    // the parsed numbers come from the names, not from a previous alignment
    val parsed = remember(videos) { videos.map { AlignFile(it.index, it.path, app.nijiyome.release.parseRelease(it.name).episodes?.first) } }
    val preview = remember(parsed, first, offset) { alignedEpisodes(parsed, EpisodeAlignment(first, offset)) }
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.torrent_align_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.torrent_align_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(stringResource(Res.string.torrent_align_first), first, { first = it.coerceIn(1, 9_999) }, Modifier.weight(1f), allowNegative = false)
                IntField(stringResource(Res.string.torrent_align_offset), offset, { offset = it.coerceIn(-9_999, 9_999) }, Modifier.weight(1f), allowNegative = true)
            }
            if (videos.isEmpty()) {
                EmptyState(icon = Icons.Outlined.Search, title = stringResource(Res.string.torrent_align_no_videos))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(parsed.sortedBy { it.path.lowercase() }, key = { it.index }) { f ->
                        val ep = preview[f.index]
                        ListItem(
                            headlineContent = { Text(f.path.substringAfterLast('/'), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(stringResource(if (f.episode != null) Res.string.torrent_align_confidence_high else Res.string.torrent_align_confidence_order))
                            },
                            trailingContent = {
                                Pill(
                                    if (ep != null) stringResource(Res.string.torrent_files_episode_value, ep) else stringResource(Res.string.torrent_fmt_none),
                                    tone = if (f.episode != null) PillTone.OK else PillTone.WARN,
                                )
                            },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    vm.setAlignment(null)
                    onDismiss()
                }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.torrent_align_reset)) }
                Button(
                    onClick = {
                        vm.setAlignment(EpisodeAlignment(first, offset))
                        onDismiss()
                    },
                    enabled = videos.isNotEmpty(),
                    modifier = Modifier.padding(start = 8.dp),
                    shape = NijiyomeControls.shape,
                ) { Text(stringResource(Res.string.torrent_align_save)) }
            }
        }
    }
}

// ==================================================================== Trackers: re-announce

@Composable
internal fun ReannounceButton(vm: TorrentDetailViewModel, modifier: Modifier = Modifier) {
    val busy by vm.reannouncing.collectAsStateWithLifecycle()
    FilledTonalButton(onClick = vm::reannounce, enabled = !busy, modifier = modifier, shape = NijiyomeControls.shape) {
        if (busy) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(Res.string.torrent_trackers_reannounce), modifier = Modifier.padding(start = 8.dp))
    }
}

// ==================================================================== Historial

/** TorrentSection.HISTORY (torrent.session-history): torrents the session has seen, with date; tap reopens; "Vaciar". */
@Composable
internal fun HistorySection(onOpenTorrent: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm: TorrentHistoryViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val failed = stringResource(Res.string.torrent_library_action_failed)
    val currentOpen by rememberUpdatedState(onOpenTorrent)
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is TorrentHistoryEvent.Open -> currentOpen(e.id)
                is TorrentHistoryEvent.Failed -> snackbar.show(failed)
            }
        }
    }
    TorrentLoadState(state, vm::retry, modifier, errorTitle = stringResource(Res.string.torrent_history_error)) { entries ->
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(Res.string.torrent_history_empty_title),
                message = stringResource(Res.string.torrent_history_empty_body),
                modifier = modifier,
            )
        } else {
            Column(modifier) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.torrent_history_count, entries.size),
                        style = monoStyle(MaterialTheme.typography.labelMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { confirm = true }, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(Res.string.torrent_history_clear), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(entries, key = { it.id }) { entry -> HistoryRow(entry) { vm.reopen(entry) } }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(Res.string.torrent_history_clear_title)) },
            text = { Text(stringResource(Res.string.torrent_history_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.clear()
                }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.torrent_history_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
}

@Composable
private fun HistoryRow(entry: TorrentSessionEntry, onClick: () -> Unit) {
    val added = relativeTimeSpan(entry.addedUnix * 1000)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(entry.series ?: entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entry.series != null && entry.name != entry.series) {
                    Text(entry.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(
                        entry.episode?.let { stringResource(Res.string.torrent_files_episode_value, it) },
                        added,
                        entry.downloadedBytes.takeIf { it > 0 }?.let { formatBytes(it) },
                    ).joinToString(" · "),
                    style = monoStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(stringResource(originRes(entry.origin)), tone = PillTone.NEUTRAL)
                if (entry.removedUnix != null) Pill(stringResource(Res.string.torrent_history_removed), tone = PillTone.WARN)
            }
        },
    )
}

private fun originRes(origin: String): StringResource = when (origin) {
    "download" -> Res.string.torrent_history_origin_download
    "manual" -> Res.string.torrent_history_origin_manual
    "magnet" -> Res.string.torrent_history_origin_magnet
    else -> Res.string.torrent_history_origin_play
}

// ==================================================================== Debrid

/**
 * TorrentSection.DEBRID (sources.debrid.settings): one card per service with the API key field, "Probar y vincular" (validates against
 * the account endpoint, key stored in the Keystore, never shown again), user / premium, "Preferir" (order) and "Desvincular".
 */
@Composable
internal fun DebridSection(modifier: Modifier = Modifier) {
    val vm: DebridViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(vm) {
        vm.linked.collect { a -> snackbar.show(getString(Res.string.torrent_debrid_linked, a.service.label, a.username ?: a.service.label)) }
    }
    LaunchedEffect(vm) {
        vm.checks.collect { c ->
            snackbar.show(
                when (c) {
                    is DebridCheck.Ok -> getString(Res.string.torrent_debrid_check_ok, c.account.service.label, c.account.username ?: c.account.service.label)
                    is DebridCheck.Failed -> getString(Res.string.torrent_debrid_check_failed, c.service.label, getString(debridProblemOf(c.error).labelRes()))
                },
            )
        }
    }
    if (!vm.available) {
        ErrorState(
            message = stringResource(Res.string.torrent_debrid_unavailable),
            retryLabel = stringResource(UiR.string.ui_retry),
            onRetry = {},
            modifier = modifier,
        )
        return
    }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(if (desktopTorrentUi) Res.string.torrent_desktop_debrid_intro else Res.string.torrent_debrid_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 1.1 (F7): the switches, then TorBox (recommended) first among the services not linked yet
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                DebridPrefsRows(state.settings, onChange = { next -> vm.setSettings { next } })
            }
        }
        val ordered = state.accounts.map { it.service } +
            DebridService.entries.filter { s -> state.accounts.none { it.service == s } }.sortedByDescending { it.recommended }
        ordered.forEach { service ->
            val account = state.accounts.firstOrNull { it.service == service }
            DebridCard(
                service = service,
                account = account,
                position = state.accounts.indexOfFirst { it.service == service }.takeIf { it >= 0 },
                linking = state.linking == service,
                error = state.errors[service],
                onLink = { key -> vm.link(service, key) },
                onUnlink = { vm.unlink(service) },
                onPrefer = { vm.prefer(service) },
                checking = state.checking == service,
                onCheck = { vm.recheck(service) },
            )
        }
        Text(stringResource(Res.string.torrent_debrid_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DebridCard(
    service: DebridService,
    account: DebridAccount?,
    position: Int?,
    linking: Boolean,
    error: AppError?,
    onLink: (String) -> Unit,
    onUnlink: () -> Unit,
    onPrefer: () -> Unit,
    checking: Boolean = false,
    onCheck: () -> Unit = {},
) {
    var key by remember(service, account) { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(service.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // 1.1 (F7): TorBox is the tested service; the others keep working as before, untested
                Pill(
                    stringResource(if (service.recommended) Res.string.torrent_debrid_recommended else Res.string.torrent_debrid_experimental),
                    tone = if (service.recommended) PillTone.PRIMARY else PillTone.WARN,
                )
                if (position != null) Pill(stringResource(Res.string.torrent_debrid_order, position + 1), tone = if (position == 0) PillTone.PRIMARY else PillTone.NEUTRAL)
                Pill(
                    stringResource(if (account != null) Res.string.torrent_debrid_linked_pill else Res.string.torrent_debrid_not_linked),
                    tone = if (account != null) PillTone.OK else PillTone.NEUTRAL,
                )
            }
            Text(
                stringResource(
                    when {
                        service.instantCheck() -> Res.string.torrent_debrid_instant
                        desktopTorrentUi -> Res.string.torrent_desktop_debrid_probe
                        else -> Res.string.torrent_debrid_probe
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (account != null) {
                Text(stringResource(Res.string.torrent_debrid_user, account.username ?: "—"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    account.premiumUntilMs?.let {
                        stringResource(Res.string.torrent_debrid_premium_until, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
                    } ?: stringResource(Res.string.torrent_debrid_premium_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((account.premiumUntilMs ?: Long.MAX_VALUE) < System.currentTimeMillis()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(Res.string.torrent_debrid_key_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCheck, enabled = !checking, shape = NijiyomeControls.shape) {
                        if (checking) LoadingIndicator(Modifier.size(20.dp))
                        Text(stringResource(Res.string.torrent_debrid_check), modifier = Modifier.padding(start = if (checking) 4.dp else 0.dp))
                    }
                    if (position != null && position > 0) OutlinedButton(onClick = onPrefer, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.torrent_debrid_prefer)) }
                    TextButton(onClick = onUnlink, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), shape = NijiyomeControls.shape) {
                        Text(stringResource(Res.string.torrent_debrid_unlink))
                    }
                }
            } else {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text(stringResource(Res.string.torrent_debrid_key)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null,
                    supportingText = error?.let { e -> { Text(debridErrorText(e)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(onClick = { onLink(key) }, enabled = key.isNotBlank() && !linking, shape = NijiyomeControls.shape) {
                    if (linking) LoadingIndicator(Modifier.size(20.dp))
                    Text(stringResource(Res.string.torrent_debrid_test_link), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

private fun DebridService.instantCheck(): Boolean = this == DebridService.TORBOX || this == DebridService.PREMIUMIZE

@Composable
private fun debridErrorText(error: AppError): String {
    val msg = (error as? AppError.Unknown)?.msg.orEmpty()
    return when {
        msg == "debrid:invalid_key" -> stringResource(Res.string.torrent_debrid_invalid_key)
        error is AppError.Offline -> stringResource(Res.string.torrent_debrid_offline)
        // 1.1 (F7): the service's own reason (plan, limits, rate limit) instead of a generic error
        msg.startsWith("debrid:") -> stringResource(debridProblemOf(error).labelRes())
        else -> torrentErrorMessage(error)
    }
}

/**
 * 1.1 (F7): Settings › Torrents › Debrid: use debrid at all, prefer cached torrents in the ranking, download cached torrents over
 * HTTPS. Shown in the Debrid tab and in the torrent settings page.
 */
@Composable
internal fun DebridPrefsRows(settings: DebridSettings, onChange: (DebridSettings) -> Unit) {
    SwitchRow(
        title = stringResource(Res.string.torrent_debrid_enabled),
        hint = stringResource(Res.string.torrent_debrid_enabled_hint),
        checked = settings.enabled,
        onChange = { on -> onChange(settings.copy(enabled = on)) },
    )
    SwitchRow(
        title = stringResource(Res.string.torrent_debrid_prefer_cached),
        hint = stringResource(Res.string.torrent_debrid_prefer_cached_hint),
        checked = settings.preferCached,
        onChange = { on -> onChange(settings.copy(preferCached = on)) },
        enabled = settings.enabled,
    )
    SwitchRow(
        title = stringResource(Res.string.torrent_debrid_download_cached),
        hint = stringResource(Res.string.rd_s8_tor_debrid_cached_hint),
        checked = settings.downloadCached,
        onChange = { on -> onChange(settings.copy(downloadCached = on)) },
        enabled = settings.enabled,
    )
}

// ==================================================================== settings: schedule, trackers, cache, preload

/**
 * New rows of Settings › Torrents (torrent.bandwidth-schedule, torrent.custom-trackers, torrent.cache-limit, torrent.preload-next).
 * Saved through the same [onChange] as the rest of the page (inside `torrent_settings_json`).
 */
@Composable
internal fun ParityTorrentSettings(settings: TorrentSettings, onChange: ((TorrentSettings) -> TorrentSettings) -> Unit) {
    // 1.1 (F7): debrid (TorBox recommended, the other services experimental); accounts are linked in Torrents › Debrid
    SectionCard(title = stringResource(Res.string.torrent_debrid_settings_title)) {
        DebridPrefsRows(settings.debrid, onChange = { next -> onChange { it.copy(debrid = next) } })
        Text(
            stringResource(Res.string.rd_s8_tor_debrid_services),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    SectionCard(title = stringResource(Res.string.torrent_sched_title)) {
        val schedule = settings.schedule
        SwitchRow(
            title = stringResource(Res.string.torrent_sched_switch),
            hint = stringResource(Res.string.torrent_sched_hint),
            checked = schedule != null,
            onChange = { on -> onChange { it.copy(schedule = if (on) BandwidthSchedule(startMinute = 8 * 60, endMinute = 23 * 60, downloadLimitKib = 1_024) else null) } },
        )
        if (schedule != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField(stringResource(Res.string.torrent_sched_start), schedule.startMinute, Modifier.weight(1f)) { m ->
                    onChange { it.copy(schedule = it.schedule?.copy(startMinute = m)) }
                }
                TimeField(stringResource(Res.string.torrent_sched_end), schedule.endMinute, Modifier.weight(1f)) { m ->
                    onChange { it.copy(schedule = it.schedule?.copy(endMinute = m)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(stringResource(Res.string.torrent_sched_down), schedule.downloadLimitKib, { v ->
                    onChange { it.copy(schedule = it.schedule?.copy(downloadLimitKib = v.coerceIn(0, TorrentSettings.MAX_LIMIT_KIB))) }
                }, Modifier.weight(1f), allowNegative = false)
                IntField(stringResource(Res.string.torrent_sched_up), schedule.uploadLimitKib, { v ->
                    onChange { it.copy(schedule = it.schedule?.copy(uploadLimitKib = v.coerceIn(0, TorrentSettings.MAX_LIMIT_KIB))) }
                }, Modifier.weight(1f), allowNegative = false)
            }
            Text(stringResource(Res.string.torrent_sched_limits_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SectionCard(title = stringResource(Res.string.torrent_trackers_custom_title)) {
        var text by rememberSaveable(settings.trackers) { mutableStateOf(settings.trackers.joinToString("\n")) }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(Res.string.torrent_trackers_custom_label)) },
            supportingText = { Text(stringResource(Res.string.torrent_trackers_custom_hint)) },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        val parsed = text.lines().map(String::trim).filter(String::isNotEmpty)
        OutlinedButton(onClick = { onChange { it.copy(trackers = parsed) } }, enabled = parsed != settings.trackers, shape = NijiyomeControls.shape) {
            Text(stringResource(Res.string.torrent_trackers_custom_save))
        }
    }
    SectionCard(title = stringResource(Res.string.torrent_cache_title)) {
        SwitchRow(
            title = stringResource(Res.string.torrent_cache_switch),
            hint = stringResource(Res.string.torrent_cache_hint),
            checked = settings.cacheLimitMib > 0,
            onChange = { on -> onChange { it.copy(cacheLimitMib = if (on) DEFAULT_CACHE_MIB else 0) } },
        )
        if (settings.cacheLimitMib > 0) {
            IntField(
                stringResource(Res.string.torrent_cache_gib),
                (settings.cacheLimitMib / 1024).coerceAtLeast(1),
                { gib -> onChange { it.copy(cacheLimitMib = (gib.coerceAtLeast(1) * 1024).coerceAtMost(TorrentSettings.MAX_CACHE_MIB)) } },
                Modifier.fillMaxWidth(),
                allowNegative = false,
            )
        }
        SwitchRow(
            title = stringResource(Res.string.torrent_preload_switch),
            hint = stringResource(Res.string.torrent_preload_hint),
            checked = settings.preloadNext,
            onChange = { on -> onChange { it.copy(preloadNext = on) } },
        )
    }
}

/** `HH:MM` field (24 h) for the schedule window. */
@Composable
private fun TimeField(label: String, minute: Int, modifier: Modifier, onMinute: (Int) -> Unit) {
    var text by rememberSaveable(minute) { mutableStateOf(formatMinute(minute)) }
    val parsed = parseMinute(text)
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() || it == ':' }.take(5)
            parseMinute(text)?.let { if (it != minute) onMinute(it) }
        },
        label = { Text(label) },
        singleLine = true,
        isError = parsed == null,
        placeholder = { Text("23:00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** 0..1439 -> `HH:MM`. */
internal fun formatMinute(minute: Int): String = "%02d:%02d".format(minute.coerceIn(0, 1439) / 60, minute.coerceIn(0, 1439) % 60)

/** `HH:MM` / `H:MM` / `HHMM` -> 0..1439; null when invalid. Pure, unit tested. */
internal fun parseMinute(text: String): Int? {
    val t = text.trim()
    val (h, m) = when {
        ':' in t -> t.substringBefore(':') to t.substringAfter(':')
        t.length in 3..4 -> t.dropLast(2) to t.takeLast(2)
        else -> return null
    }
    val hour = h.toIntOrNull() ?: return null
    val min = m.toIntOrNull() ?: return null
    if (hour !in 0..23 || min !in 0..59 || m.length != 2) return null
    return hour * 60 + min
}

private const val DEFAULT_CACHE_MIB = 10 * 1024
