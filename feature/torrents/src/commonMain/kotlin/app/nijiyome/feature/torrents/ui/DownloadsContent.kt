// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.ui

import app.nijiyome.feature.torrents.resources.Res
import app.nijiyome.feature.torrents.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.WavyProgress
import app.nijiyome.feature.torrents.vm.DownloadsUi
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentState
import app.nijiyome.model.torrent.TorrentStats
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.format.formatRate
import app.nijiyome.ui.platform.rememberNotificationPermission
import kotlinx.coroutines.flow.catch
import org.koin.compose.koinInject

/**
 * The "Entiendo / Ahora no" warning (11 §3.4): full screen, not a dismissable dialog. Explains CPU, battery, storage and data use,
 * uploading to peers (IP visible) and the permanent notification, plus why notifications are requested.
 */
@Composable
internal fun BackgroundWarning(onAccept: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
        // Desktop (Tatakai Next's desktop warning): the computer, while the app is open; no battery nor permanent notification.
        Text(
            stringResource(if (desktopTorrentUi) Res.string.torrent_desktop_warn_title else Res.string.torrent_downloads_warn_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (desktopTorrentUi) Res.string.torrent_desktop_warn_body else Res.string.torrent_downloads_warn_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            val points = if (desktopTorrentUi) {
                listOf(
                    Res.string.torrent_downloads_warn_cpu,
                    Res.string.torrent_downloads_warn_disk,
                    Res.string.torrent_downloads_warn_network,
                    Res.string.torrent_downloads_warn_upload,
                )
            } else {
                listOf(
                    Res.string.torrent_downloads_warn_cpu,
                    Res.string.torrent_android_warn_battery,
                    Res.string.torrent_downloads_warn_disk,
                    Res.string.torrent_downloads_warn_network,
                    Res.string.torrent_downloads_warn_upload,
                    Res.string.torrent_android_warn_notification,
                )
            }
            points.forEach { res ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (asksNotificationPermission) {
                Text(
                    stringResource(Res.string.torrent_android_notif_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(if (desktopTorrentUi) Res.string.rd_s8_tor_desktop_warn_note else Res.string.rd_s8_tor_android_warn_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            Text(stringResource(Res.string.torrent_downloads_warn_accept))
        }
        TextButton(onClick = onNotNow) { Text(stringResource(Res.string.torrent_android_not_now)) }
    }
}

/** Body of Downloads (11 §3.4) after the acknowledgement: switch, network / battery chips, list of unfinished downloads. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadsBody(
    ui: DownloadsUi,
    onAccept: () -> Unit,
    onNotNow: () -> Unit,
    onBackground: (Boolean) -> Unit,
    onOpenTorrent: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (id: String, deleteFiles: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permission = rememberNotificationPermission()
    if (!ui.settings.backgroundAck) {
        BackgroundWarning(
            onAccept = {
                onAccept()
                if (!permission.isGranted()) permission.request()
            },
            onNotNow = onNotNow,
            modifier = modifier,
        )
        return
    }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "switch") {
            SwitchRow(
                title = stringResource(Res.string.torrent_downloads_switch),
                hint = stringResource(if (desktopTorrentUi) Res.string.torrent_desktop_downloads_switch_hint else Res.string.torrent_android_downloads_switch_hint),
                checked = ui.settings.backgroundDownloads,
                onChange = { enabled ->
                    onBackground(enabled)
                    if (enabled && !permission.isGranted()) permission.request()
                },
            )
        }
        item(key = "chips") {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ui.status.waitingForWifi) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(Res.string.torrent_android_waiting_wifi)) },
                        leadingIcon = { Icon(Icons.Outlined.SignalWifiOff, null) },
                    )
                }
                if (ui.status.pausedLowBattery) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(Res.string.torrent_android_low_battery)) },
                        leadingIcon = { Icon(Icons.Outlined.BatteryAlert, null) },
                    )
                }
                if (ui.status.pausedBySystem) {
                    AssistChip(
                        onClick = { ui.items.forEach { onResume(it.id) } },
                        label = { Text(stringResource(Res.string.torrent_android_paused_by_system)) },
                        leadingIcon = { Icon(Icons.Outlined.ReportProblem, null) },
                    )
                }
            }
        }
        if (!ui.settings.backgroundDownloads) {
            item(key = "off") {
                Banner(stringResource(Res.string.torrent_downloads_off_banner), Modifier.padding(16.dp))
            }
        }
        if (ui.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.DownloadForOffline,
                    title = stringResource(Res.string.torrent_downloads_empty_title),
                    message = stringResource(
                        if (ui.settings.backgroundDownloads) Res.string.torrent_downloads_empty_body else Res.string.torrent_downloads_empty_body_off,
                    ),
                )
            }
        } else {
            items(ui.items, key = { it.id }, contentType = { "download" }) { item ->
                DownloadRow(
                    item = item,
                    onOpen = { onOpenTorrent(item.id) },
                    onPause = { onPause(item.id) },
                    onResume = { onResume(item.id) },
                    onRemove = { confirmRemove = item.id },
                )
            }
        }
    }
    // Sparkles (TOR-18): the same confirmation as removing a torrent, with "also delete the files".
    confirmRemove?.let { id ->
        DeleteDialog(
            count = 1,
            onConfirm = { deleteFiles ->
                onRemove(id, deleteFiles)
                confirmRemove = null
            },
            onDismiss = { confirmRemove = null },
        )
    }
}

@Composable
private fun DownloadRow(
    item: TorrentLibraryItem,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    // live figures of this row (the repository ticks every second while collected)
    val repository = koinInject<TorrentRepository>()
    val statsFlow = remember(item.id) { repository.stats(item.id).catch { } }
    val stats by statsFlow.collectAsStateWithLifecycle(initialValue = null as TorrentStats?)
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.series ?: item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.episode?.let {
                        Text(
                            stringResource(Res.string.torrent_downloads_episode, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val state = stats?.state ?: item.state
                if (state == TorrentState.PAUSED) {
                    IconButton(onClick = onResume) { Icon(Icons.Outlined.PlayArrow, stringResource(Res.string.torrent_downloads_resume)) }
                } else {
                    IconButton(onClick = onPause) { Icon(Icons.Outlined.Pause, stringResource(Res.string.torrent_downloads_pause)) }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, stringResource(Res.string.torrent_downloads_remove)) }
            }
            val progress = stats?.progress ?: item.progress
            app.nijiyome.designsystem.direction.ProgressTrack(progress = progress.toFloat().coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            val s = stats
            val line = buildList {
                add(
                    stringResource(
                        Res.string.torrent_downloads_of,
                        formatBytes(s?.downloadedBytes ?: (item.sizeBytes * item.progress).toLong()),
                        formatBytes(s?.totalBytes ?: item.sizeBytes),
                    ),
                )
                if (s != null) {
                    add(stringResource(Res.string.torrent_downloads_down, formatRate(s.downBps)))
                    add(stringResource(Res.string.torrent_downloads_up, formatRate(s.upBps)))
                    add(pluralStringResource(Res.plurals.torrent_downloads_peers, s.peers, s.peers))
                    s.etaSecs?.let { add(stringResource(Res.string.torrent_downloads_eta, durationText(it))) }
                }
            }.joinToString(" · ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatePill(s?.state ?: item.state)
                Text(line, style = monoStyle(MaterialTheme.typography.bodySmall), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
