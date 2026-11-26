// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.AdaptiveSheetScrollColumn
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.feature.downloads.resources.Res
import app.nijiyome.feature.downloads.resources.*
import app.nijiyome.feature.downloads.transfers.HistoryFilter
import app.nijiyome.feature.downloads.transfers.TransferHistoryRow
import app.nijiyome.feature.downloads.transfers.TransferLimits
import app.nijiyome.feature.downloads.transfers.TransfersLogic
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadState
import app.nijiyome.model.torrent.BandwidthSchedule
import app.nijiyome.model.torrent.TorrentSessionEntry
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.patterns.QuantityPicker
import app.nijiyome.ui.patterns.TimeRangePicker
import app.nijiyome.ui.patterns.speedUnits
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane S8 (Transferencias): the quick limits and the history, as sheets (side sheet on wide windows).

/**
 * Quick limits (TOR-25, TOR-26, DL-14): torrent download and upload, the web downloads' speed (desktop), and other limits by
 * schedule, all with units and presets instead of raw KiB/s fields; "More torrent settings" leads to Settings › Torrents and downloads.
 */
@Composable
internal fun LimitsSheet(
    limits: TransferLimits,
    torrents: Boolean,
    vm: DownloadsViewModel,
    onOpenSettings: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val units = speedUnits()
    val presets = listOf(1024L, 5 * 1024L, 10 * 1024L, 25 * 1024L)
    val max = TorrentSettings.MAX_LIMIT_KIB.toLong()
    val none = stringResource(Res.string.rd_s8_limits_none)
    AdaptiveSheet(onDismissRequest = onDismiss) {
        AdaptiveSheetScrollColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.rd_s8_limits_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.rd_s8_limits_zero), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (torrents) {
                    QuantityPicker(
                        baseValue = limits.downKib.toLong(),
                        onBaseValueChange = { v -> vm.setTorrentLimits { it.copy(downloadLimitKib = v.toInt()) } },
                        units = units,
                        label = stringResource(Res.string.rd_s8_limits_down),
                        max = max,
                        presets = listOf(0L) + presets,
                        presetLabel = { if (it == 0L) none else app.nijiyome.ui.patterns.formatQuantity(it, units) },
                    )
                    QuantityPicker(
                        baseValue = limits.upKib.toLong(),
                        onBaseValueChange = { v -> vm.setTorrentLimits { it.copy(uploadLimitKib = v.toInt()) } },
                        units = units,
                        label = stringResource(Res.string.rd_s8_limits_up),
                        max = max,
                        presets = listOf(0L, 128L, 512L, 1024L),
                        presetLabel = { if (it == 0L) none else app.nijiyome.ui.patterns.formatQuantity(it, units) },
                    )
                }
                if (desktopDownloadsUi) {
                    QuantityPicker(
                        baseValue = limits.webKib.toLong(),
                        onBaseValueChange = { v -> vm.setWebSpeedLimit(v.toInt()) },
                        units = units,
                        label = stringResource(Res.string.rd_s8_limits_web),
                        max = app.nijiyome.model.DownloadSettings.MAX_SPEED_KIB.toLong(),
                        presets = listOf(0L) + presets,
                        presetLabel = { if (it == 0L) none else app.nijiyome.ui.patterns.formatQuantity(it, units) },
                    )
                }
                if (torrents) {
                    HorizontalDivider()
                    val schedule = limits.schedule
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.rd_s8_limits_schedule), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(Res.string.rd_s8_limits_schedule_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = schedule != null,
                            onCheckedChange = { on ->
                                vm.setTorrentLimits {
                                    it.copy(schedule = if (on) BandwidthSchedule(startMinute = 8 * 60, endMinute = 23 * 60, downloadLimitKib = 1024) else null)
                                }
                            },
                        )
                    }
                    if (schedule != null) {
                        TimeRangePicker(
                            start = schedule.startMinute,
                            end = schedule.endMinute,
                            onChange = { s, e -> vm.setTorrentLimits { it.copy(schedule = schedule.copy(startMinute = s, endMinute = e)) } },
                            label = stringResource(Res.string.rd_s8_limits_schedule_hours),
                            stepMinutes = 30,
                        )
                        QuantityPicker(
                            baseValue = schedule.downloadLimitKib.toLong(),
                            onBaseValueChange = { v -> vm.setTorrentLimits { it.copy(schedule = schedule.copy(downloadLimitKib = v.toInt())) } },
                            units = units,
                            label = stringResource(Res.string.rd_s8_limits_schedule_down),
                            max = max,
                            presets = listOf(0L) + presets,
                            presetLabel = { if (it == 0L) none else app.nijiyome.ui.patterns.formatQuantity(it, units) },
                        )
                        QuantityPicker(
                            baseValue = schedule.uploadLimitKib.toLong(),
                            onBaseValueChange = { v -> vm.setTorrentLimits { it.copy(schedule = schedule.copy(uploadLimitKib = v.toInt())) } },
                            units = units,
                            label = stringResource(Res.string.rd_s8_limits_schedule_up),
                            max = max,
                            presets = listOf(0L, 128L, 512L, 1024L),
                            presetLabel = { if (it == 0L) none else app.nijiyome.ui.patterns.formatQuantity(it, units) },
                        )
                    }
                }
                if (onOpenSettings != null) {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenSettings()
                    }, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.rd_s8_more_settings), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/**
 * History (TOR-19, TOR-20, DL-08): web downloads and torrent sessions in one list with a type filter. A finished episode plays, a
 * failed one retries, a torrent opens (or is added again when it left the library). "Clear history…" is confirmed and keeps the files.
 */
@Composable
internal fun HistorySheet(
    web: List<DownloadItem>,
    sessions: List<TorrentSessionEntry>,
    torrentIds: Set<String>,
    torrents: Boolean,
    vm: DownloadsViewModel,
    onPlay: (DownloadItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    val rows = remember(web, sessions, filter) { TransfersLogic.history(web, sessions, filter) }
    val errorText = rememberTransferErrorText()
    AdaptiveSheet(onDismissRequest = onDismiss) {
        AdaptiveSheetScrollColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.rd_s8_history_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.rd_s8_history_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (torrents) {
                    Segmented(
                        options = listOf(
                            SegmentedOption(HistoryFilter.ALL, stringResource(Res.string.rd_s8_history_all)),
                            SegmentedOption(HistoryFilter.TORRENTS, stringResource(Res.string.downloads_torrents)),
                            SegmentedOption(HistoryFilter.WEB, stringResource(Res.string.rd_s8_kind_web)),
                        ),
                        selected = filter,
                        onSelect = { filter = it },
                        small = true,
                    )
                }
                if (rows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = stringResource(Res.string.downloads_empty_history),
                        message = stringResource(Res.string.rd_s8_history_empty_body),
                    )
                } else {
                    rows.forEach { row -> HistoryRow(row, torrentIds, vm, onPlay, errorText) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { confirmClear = true },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = NijiyomeControls.shape,
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.rd_s8_history_clear), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        DangerConfirm(
            title = stringResource(Res.string.rd_s8_history_clear_title),
            body = stringResource(Res.string.rd_s8_history_clear_body),
            confirm = stringResource(Res.string.rd_s8_history_clear_confirm),
            onConfirm = vm::clearAllHistory,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun HistoryRow(
    row: TransferHistoryRow,
    torrentIds: Set<String>,
    vm: DownloadsViewModel,
    onPlay: (DownloadItem) -> Unit,
    errorText: (String) -> String,
) {
    val date = remember(row.atMs) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.atMs)) }
    val signals = LocalSignalColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaCover(url = row.web?.poster, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            val web = row.web
            val session = row.session
            Text(
                listOfNotNull(row.title, (web?.episode ?: session?.episode)?.let { stringResource(UiR.string.ui_episode_number, it) }).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    date,
                    stringResource(if (row.torrent) Res.string.rd_s8_kind_torrent else Res.string.rd_s8_kind_web),
                    session?.downloadedBytes?.takeIf { it > 0 }?.let(::formatBytes) ?: web?.totalBytes?.let(::formatBytes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            web?.error?.takeIf { row.failed }?.let { Text(errorText(it), style = MaterialTheme.typography.bodySmall) }
        }
        when {
            row.session != null -> {
                SignalChip(originLabel(row.session.origin), Icons.Outlined.History, MaterialTheme.colorScheme.outline)
                if (row.removed && row.session.id !in torrentIds) {
                    SignalChip(stringResource(Res.string.rd_s8_history_removed), Icons.Outlined.DeleteSweep, signals.warn)
                    TextButton(onClick = { vm.reopenTorrent(row.session) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s8_history_readd)) }
                } else {
                    IconButton(onClick = { vm.reopenTorrent(row.session) }) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = stringResource(Res.string.rd_s8_menu_open))
                    }
                }
            }
            row.web != null && row.web.state == DownloadState.COMPLETED -> {
                PhaseChip(app.nijiyome.feature.downloads.transfers.TransferPhase.COMPLETE)
                IconButton(onClick = { onPlay(row.web) }) { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.downloads_play)) }
            }
            row.web != null -> {
                PhaseChip(app.nijiyome.feature.downloads.transfers.TransferPhase.FAILED)
                IconButton(onClick = { vm.retry(row.web.id) }) { Icon(Icons.Outlined.Refresh, contentDescription = stringResource(Res.string.rd_s8_retry)) }
            }
        }
    }
}

@Composable
private fun originLabel(origin: String): String = stringResource(
    when (origin) {
        "download" -> Res.string.rd_s8_origin_download
        "manual" -> Res.string.rd_s8_origin_manual
        "magnet" -> Res.string.rd_s8_origin_magnet
        else -> Res.string.rd_s8_origin_play
    },
)
