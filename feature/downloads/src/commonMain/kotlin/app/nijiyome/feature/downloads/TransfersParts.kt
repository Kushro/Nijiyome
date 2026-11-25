// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.feature.downloads.resources.Res
import app.nijiyome.feature.downloads.resources.*
import app.nijiyome.feature.downloads.transfers.TransferItem
import app.nijiyome.feature.downloads.transfers.TransferKind
import app.nijiyome.feature.downloads.transfers.TransferPhase
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.torrent.TorrentStats
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.format.formatRate
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.patterns.ContextMenuArea
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuDivider
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.patterns.MenuSheetHeader
import app.nijiyome.ui.patterns.SelectionCheckbox
import app.nijiyome.ui.patterns.SelectionState
import app.nijiyome.ui.patterns.multiSelectable
import app.nijiyome.ui.platform.rememberOpenFolder
import app.nijiyome.ui.platform.rememberShareText
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane S8 (Transferencias): pieces both structures share. A row says its kind and its state in words, with an icon
// and the direction's signal color; progress goes through the direction's `ProgressTrack`.

// ------------------------------------------------------------------ labels

@Composable
internal fun TransferKind.label(): String = stringResource(
    when (this) {
        TransferKind.WEB -> Res.string.rd_s8_kind_web
        TransferKind.DEBRID -> Res.string.rd_s8_kind_debrid
        TransferKind.TORRENT -> Res.string.rd_s8_kind_torrent
        TransferKind.MANGA -> Res.string.rd_s8_kind_manga
        TransferKind.NOVEL -> Res.string.rd_s8_kind_novel
    },
)

internal val TransferKind.icon: ImageVector
    get() = when (this) {
        TransferKind.WEB -> Icons.Outlined.Language
        TransferKind.DEBRID -> Icons.Outlined.CloudDone
        TransferKind.TORRENT -> Icons.Outlined.Hub
        TransferKind.MANGA -> Icons.Outlined.AutoStories
        TransferKind.NOVEL -> Icons.AutoMirrored.Outlined.MenuBook
    }

@Composable
internal fun TransferPhase.label(): String = stringResource(
    when (this) {
        TransferPhase.STARTING -> Res.string.rd_s8_phase_starting
        TransferPhase.DOWNLOADING -> Res.string.rd_s8_phase_downloading
        TransferPhase.QUEUED -> Res.string.rd_s8_phase_queued
        TransferPhase.PAUSED -> Res.string.rd_s8_phase_paused
        TransferPhase.FAILED -> Res.string.rd_s8_phase_failed
        TransferPhase.SEEDING -> Res.string.rd_s8_phase_seeding
        TransferPhase.COMPLETE -> Res.string.rd_s8_phase_complete
    },
)

private val TransferPhase.icon: ImageVector
    get() = when (this) {
        TransferPhase.STARTING -> Icons.Outlined.Sync
        TransferPhase.DOWNLOADING -> Icons.Outlined.Download
        TransferPhase.QUEUED -> Icons.Outlined.HourglassEmpty
        TransferPhase.PAUSED -> Icons.Outlined.Pause
        TransferPhase.FAILED -> Icons.Outlined.ErrorOutline
        TransferPhase.SEEDING -> Icons.Outlined.CloudUpload
        TransferPhase.COMPLETE -> Icons.Outlined.CheckCircle
    }

@Composable
private fun TransferPhase.signal(): Color {
    val s = LocalSignalColors.current
    return when (this) {
        TransferPhase.DOWNLOADING, TransferPhase.SEEDING, TransferPhase.COMPLETE -> s.ok
        TransferPhase.STARTING -> s.info
        TransferPhase.QUEUED, TransferPhase.PAUSED -> MaterialTheme.colorScheme.outline
        TransferPhase.FAILED -> s.err
    }
}

/** State in words with its icon; the signal color only outlines it (the text keeps the surface's contrast). */
@Composable
internal fun PhaseChip(phase: TransferPhase, modifier: Modifier = Modifier) {
    SignalChip(phase.label(), phase.icon, phase.signal(), modifier)
}

@Composable
internal fun SignalChip(text: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .border(1.dp, color, CircleShape)
            .heightIn(min = 24.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** "Ao no Hako · Ep. 9" / "Berserk · Ch. 12" / the torrent's name. */
@Composable
internal fun TransferItem.displayTitle(): String = when {
    chapterLabel != null -> "$title · $chapterLabel"
    episode != null -> "$title · " + stringResource(UiR.string.ui_episode_number, episode)
    else -> title
}

// ------------------------------------------------------------------ error texts

/** Text of a torrent error code (`torrent:<code>[:detail]` of the engine) or of a download error; never the raw code. */
@Composable
internal fun rememberTransferErrorText(): (String) -> String {
    val missing = stringResource(Res.string.rd_s8_err_torrent_missing)
    val moved = stringResource(Res.string.rd_s8_err_torrent_moved)
    val noSpace = stringResource(Res.string.rd_s8_err_no_space)
    val noFile = stringResource(Res.string.rd_s8_err_torrent_no_file)
    val bgOff = stringResource(Res.string.rd_s8_err_background_off)
    val incomplete = stringResource(Res.string.rd_s8_err_torrent_incomplete)
    val invalid = stringResource(Res.string.rd_s8_err_torrent_invalid)
    val broken = stringResource(Res.string.downloads_err_broken)
    val failed = stringResource(Res.string.downloads_err_failed)
    val dash = stringResource(Res.string.downloads_err_dash)
    val limit = stringResource(Res.string.downloads_err_limit)
    val noSource = stringResource(Res.string.downloads_err_no_source)
    val folder = stringResource(Res.string.downloads_err_folder)
    val debridTexts = rememberDebridProblemTexts()
    return remember(missing, moved, noSpace, noFile, bgOff, incomplete, invalid, broken, failed, debridTexts) {
        { raw: String ->
            val parts = raw.substringAfter("torrent:", "").split(':')
            when {
                raw.startsWith("torrent:") -> when (parts.firstOrNull()) {
                    "missing" -> missing
                    "moved" -> moved
                    "no_space" -> noSpace.format(formatBytes(parts.getOrNull(1)?.toLongOrNull() ?: 0))
                    "no_file" -> noFile
                    "background_off" -> bgOff
                    "incomplete" -> incomplete
                    "invalid_source", "invalid_file" -> invalid
                    else -> raw.substringAfter("torrent:")
                }
                raw == "broken" -> broken
                raw == "failed" -> failed
                else -> codeText(raw, dash, limit, noSource, folder)
                    ?: debridProblemIn(raw)?.let { debridTexts(it) }
                    ?: raw
            }
        }
    }
}

@Composable
private fun rememberDebridProblemTexts(): (app.nijiyome.model.torrent.DebridProblem) -> String {
    val all = app.nijiyome.model.torrent.DebridProblem.entries.associateWith { p ->
        stringResource(Res.string.downloads_err_debrid, stringResource(p.labelRes()))
    }
    return remember(all) { { p -> all[p].orEmpty() } }
}

/** Whether an [AppException] carries a code the screen explains. */
internal fun Throwable.errorCode(): String? = ((this as? AppException)?.error as? AppError.Extension)?.msg

// ------------------------------------------------------------------ the row

/**
 * One transfer of any kind: selection check, poster, title, kind · source · size, error or progress, state and the live line,
 * pause / resume / retry and the ⋮ menu. Right click, Shift+F10 and long press (sheet) open the same menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransferRow(
    item: TransferItem,
    selection: SelectionState<String>,
    order: () -> List<String>,
    vm: DownloadsViewModel,
    etaSecs: Long?,
    onOpen: () -> Unit,
    menu: () -> List<MenuEntry>,
    errorText: (String) -> String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    compactLine: Boolean = false,
) {
    val statsFlow = remember(item.key) { if (item.isTorrent) vm.torrentStats(item.id) ?: emptyFlow() else emptyFlow<TorrentStats>() }
    val stats by statsFlow.collectAsStateWithLifecycle(initialValue = null as TorrentStats?)
    val live = stats
    val progress = (live?.progress ?: item.progress).toFloat().coerceIn(0f, 1f)
    val error = live?.error?.takeIf { item.phase == TransferPhase.FAILED } ?: item.error?.takeIf { item.phase == TransferPhase.FAILED }
    val title = item.displayTitle()
    ContextMenuArea(entries = menu, sheetHeader = { MenuSheetHeader(title) }, modifier = modifier) { _ ->
        Surface(
            color = if (selected || selection.isSelected(item.key)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .multiSelectable(selection, item.key, order, onOpen = onOpen)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionCheckbox(selected = selection.isSelected(item.key), visible = selection.active)
                MediaCover(url = item.poster, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(item.kind.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            listOfNotNull(
                                item.kind.label(),
                                item.source.takeIf { it.isNotBlank() },
                                item.files.takeIf { it > 1 }?.let { pluralStringResource(Res.plurals.rd_s8_files, it, it) },
                                item.sizeBytes?.let(::formatBytes),
                                stringResource(Res.string.rd_s8_row_not_kept).takeIf { item.keep == false },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (error != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = LocalSignalColors.current.err, modifier = Modifier.size(16.dp))
                            Text(errorText(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    } else if (item.phase != TransferPhase.SEEDING && item.phase != TransferPhase.COMPLETE) {
                        ProgressTrack(progress = progress, modifier = Modifier.fillMaxWidth())
                    }
                    if (compactLine) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PhaseChip(item.phase)
                            Text(liveLine(item, live, progress, etaSecs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!compactLine) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PhaseChip(item.phase)
                        Text(liveLine(item, live, progress, etaSecs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!selection.active) {
                    PrimaryRowAction(item, vm)
                    ContextMenuButton(entries = menu, contentDescription = stringResource(Res.string.rd_s8_more_for, title), sheetHeader = { MenuSheetHeader(title) })
                }
            }
        }
    }
}

/** Speed and time left while it moves; "waits its turn"; "37 % of 1.2 GB"; "complete". */
@Composable
private fun liveLine(item: TransferItem, live: TorrentStats?, progress: Float, etaSecs: Long?): String {
    val pct = (progress * 100).toInt()
    return when (item.phase) {
        TransferPhase.DOWNLOADING, TransferPhase.STARTING -> listOfNotNull(
            live?.downBps?.takeIf { it > 0 }?.let { "↓ " + formatRate(it) },
            (live?.etaSecs ?: etaSecs)?.let { stringResource(Res.string.rd_s8_row_left, formatDuration(it)) },
        ).ifEmpty { listOf(stringResource(Res.string.rd_s8_row_pct, pct)) }.joinToString(" · ")
        TransferPhase.QUEUED -> stringResource(Res.string.rd_s8_row_waits)
        TransferPhase.SEEDING -> live?.upBps?.takeIf { it > 0 }?.let { "↑ " + formatRate(it) } ?: stringResource(Res.string.rd_s8_phase_complete)
        TransferPhase.COMPLETE -> stringResource(Res.string.rd_s8_phase_complete)
        else -> item.sizeBytes?.let { stringResource(Res.string.rd_s8_row_pct_of, pct, formatBytes(it)) } ?: stringResource(Res.string.rd_s8_row_pct, pct)
    }
}

/** Pause while it moves, resume when paused, retry when it failed. */
@Composable
internal fun PrimaryRowAction(item: TransferItem, vm: DownloadsViewModel) {
    when {
        item.canRetry -> IconButton(onClick = { vm.resumeItems(listOf(item)) }) {
            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(Res.string.rd_s8_retry))
        }
        item.canResume -> IconButton(onClick = { vm.resumeItems(listOf(item)) }) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.downloads_resume))
        }
        item.canPause && item.chapter == null -> IconButton(onClick = { vm.pauseItems(listOf(item)) }) {
            Icon(Icons.Outlined.Pause, contentDescription = stringResource(Res.string.downloads_pause))
        }
    }
}

/** The ⋮ / right-click entries of one row, grouped, with the destructive one apart (TOR-04). */
@Composable
internal fun rememberRowMenu(
    vm: DownloadsViewModel,
    onOpen: (TransferItem) -> Unit,
    onPlayTorrent: (TransferItem) -> Unit,
    onDelete: (List<TransferItem>) -> Unit,
): (TransferItem) -> List<MenuEntry> {
    val openLabel = stringResource(Res.string.rd_s8_menu_open)
    val playLabel = stringResource(Res.string.rd_s8_menu_play)
    val pauseLabel = stringResource(Res.string.downloads_pause)
    val resumeLabel = stringResource(Res.string.downloads_resume)
    val retryLabel = stringResource(Res.string.rd_s8_retry)
    val keepLabel = stringResource(Res.string.rd_s8_menu_keep)
    val unkeepLabel = stringResource(Res.string.rd_s8_menu_unkeep)
    val folderLabel = stringResource(Res.string.downloads_desktop_open_folder)
    val magnetLabel = stringResource(if (desktopDownloadsUi) Res.string.rd_s8_menu_copy_magnet else Res.string.rd_s8_menu_share_magnet)
    val cancelLabel = stringResource(Res.string.rd_s8_menu_cancel_download)
    val deleteLabel = stringResource(Res.string.rd_s8_menu_delete)
    val copied = stringResource(Res.string.rd_s8_magnet_copied)
    val openFolder = rememberOpenFolder()
    val share = rememberShareText()
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalAppSnackbar.current
    return remember(vm, openFolder, share, clipboard, snackbar, onOpen, onPlayTorrent, onDelete) {
        { item: TransferItem ->
            buildList<MenuEntry> {
                add(MenuAction(openLabel, { onOpen(item) }, Icons.Outlined.OpenInNew))
                if (item.isTorrent) add(MenuAction(playLabel, { onPlayTorrent(item) }, Icons.Outlined.PlayCircle))
                add(MenuDivider)
                when {
                    item.canRetry -> add(MenuAction(retryLabel, { vm.resumeItems(listOf(item)) }, Icons.Outlined.Refresh))
                    item.canResume -> add(MenuAction(resumeLabel, { vm.resumeItems(listOf(item)) }, Icons.Outlined.PlayArrow))
                    item.canPause && item.chapter == null -> add(MenuAction(pauseLabel, { vm.pauseItems(listOf(item)) }, Icons.Outlined.Pause))
                }
                item.keep?.let { keep ->
                    add(
                        MenuAction(
                            if (keep) unkeepLabel else keepLabel,
                            { vm.setKeep(listOf(item), !keep) },
                            if (keep) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        ),
                    )
                }
                val path = item.torrent?.path?.takeIf { it.isNotBlank() } ?: item.web?.localUri
                if (openFolder != null && path != null) add(MenuAction(folderLabel, { openFolder(path) }, Icons.Outlined.FolderOpen))
                if (item.isTorrent) {
                    add(
                        MenuAction(magnetLabel, {
                            val magnet = "magnet:?xt=urn:btih:" + item.id
                            if (desktopDownloadsUi) {
                                runCatching { clipboard.setText(AnnotatedString(magnet)) }
                                snackbar.show(copied)
                            } else {
                                share(magnet, magnetLabel)
                            }
                        }, if (desktopDownloadsUi) Icons.Outlined.Link else Icons.Outlined.Share),
                    )
                }
                add(MenuDivider)
                add(
                    MenuAction(
                        if (item.web != null && item.phase != TransferPhase.COMPLETE) cancelLabel else deleteLabel,
                        { onDelete(listOf(item)) },
                        Icons.Outlined.Delete,
                        destructive = true,
                    ),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ confirmations

/**
 * Destructive confirmation (TOR-06, TOR-18, DL-03): web downloads say "cancel the download" with distinct verbs; torrents (alone or
 * mixed) say "delete" and offer to delete the data too.
 */
@Composable
internal fun TransferDeleteDialog(items: List<TransferItem>, onConfirm: (deleteFiles: Boolean) -> Unit, onDismiss: () -> Unit) {
    val torrents = items.any { it.isTorrent }
    var deleteFiles by rememberSaveable { mutableStateOf(true) }
    val names = items.map { it.displayTitle() }.joinToString(", ")
    val n = items.size
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = LocalSignalColors.current.err) },
        title = {
            Text(
                if (torrents) pluralStringResource(Res.plurals.rd_s8_delete_title, n, n)
                else pluralStringResource(Res.plurals.rd_s8_cancel_title, n, n),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (torrents) Res.string.rd_s8_delete_body else Res.string.rd_s8_cancel_body, names))
                if (torrents) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                        Text(stringResource(Res.string.rd_s8_delete_files))
                    }
                    if (!deleteFiles) {
                        Text(
                            stringResource(Res.string.rd_s8_delete_files_kept),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(deleteFiles) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    if (torrents) pluralStringResource(Res.plurals.rd_s8_delete_confirm, n, n)
                    else stringResource(Res.string.rd_s8_cancel_confirm),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (torrents) stringResource(UiR.string.ui_cancel) else stringResource(Res.string.rd_s8_keep_downloading))
            }
        },
    )
}

/** A destructive yes / no with distinct verbs (DL-06, TOR-20, DL-08). */
@Composable
internal fun DangerConfirm(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

// ------------------------------------------------------------------ panels

/** A titled panel of the aside / shelves (one level of surface: no cards inside it). */
@Composable
internal fun TransfersPanel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

/** A persistent result line (DL-16: the result of Revisar / Simular stays visible). */
@Composable
internal fun ResultLine(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.CheckCircle) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = LocalSignalColors.current.info, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
