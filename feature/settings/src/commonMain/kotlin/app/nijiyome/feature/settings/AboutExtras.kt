// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.settings

import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.model.LoadState
import app.nijiyome.ui.platform.rememberOpenFolder
import app.nijiyome.ui.state.userMessage
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls

// parity wave A (15 §3.4, worker q; checklist system.update-check, system.open-logs): rows of Settings › Acerca de.

/** "Buscar actualizaciones" against GitHub Releases of Kushro/Nijiyome; "Al día" or the new version with a link to the release. */
@Composable
internal fun UpdateCheckRow(system: SystemViewModel, updateCheckEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val state by system.state.collectAsStateWithLifecycle()
    val openUrl = rememberOpenReleaseUrl()
    SwitchRow(
        label = stringResource(Res.string.settings_update_check),
        checked = updateCheckEnabled,
        onChange = onToggle,
        hint = stringResource(Res.string.settings_update_check_hint),
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val update = state.update
        FilledTonalButton(onClick = system::checkUpdates, enabled = update != UpdateCheckState.Checking, shape = NijiyomeControls.shape) {
            if (update == UpdateCheckState.Checking) {
                LoadingIndicator(Modifier.size(20.dp))
            } else {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Text(stringResource(Res.string.settings_update_check_now), modifier = Modifier.padding(start = 8.dp))
        }
        when (update) {
            UpdateCheckState.Idle, UpdateCheckState.Checking -> Unit
            UpdateCheckState.UpToDate -> Pill(stringResource(Res.string.settings_update_up_to_date), tone = PillTone.OK)
            is UpdateCheckState.Available -> TextButton(onClick = { openUrl(update.info.url) }, shape = NijiyomeControls.shape) {
                Icon(Icons.Outlined.NewReleases, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(Res.string.settings_update_available, update.info.version), modifier = Modifier.padding(start = 8.dp))
            }
            is UpdateCheckState.Failed -> Text(
                stringResource(Res.string.settings_update_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private enum class SeverityFilter { ALL, WARN_UP, ERROR_ONLY }

/** "Abrir registros": last 500 lines of local or linked-device logs + severity/search filters + push-to-PC + share + clear. */
@Composable
internal fun LogsSheet(system: SystemViewModel, onDismiss: () -> Unit) {
    val state by system.state.collectAsStateWithLifecycle()
    val shareLog = rememberShareLog(system)
    val openFolder = rememberOpenFolder()
    val shareFailed = stringResource(Res.string.settings_logs_share_failed)
    val actionFailed = stringResource(Res.string.settings_action_failed)
    val snackbar = LocalAppSnackbar.current
    var severity by remember { mutableStateOf(SeverityFilter.ALL) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(system) { system.loadLogs() }
    LaunchedEffect(system) {
        system.events.collect { ev ->
            when (ev) {
                is SystemEvent.LogsPushed -> snackbar.show(
                    if (ev.count > 0) getString(Res.string.rd_s10_logs_pushed, ev.count) else getString(Res.string.rd_s10_logs_up_to_date),
                )
                is SystemEvent.Failed -> snackbar.show(actionFailed)
                else -> Unit
            }
        }
    }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(Res.string.settings_logs),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            val lines = (state.logs as? LoadState.Ok)?.value.orEmpty()
            IconButton(onClick = system::loadLogs) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(Res.string.rd_s10_logs_refresh))
            }
            if (openFolder != null) {
                val folder = system.logsFolder()
                if (folder != null) {
                    IconButton(onClick = { openFolder(folder.absolutePath) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = stringResource(Res.string.rd_s10_logs_open_folder))
                    }
                }
            }
            TextButton(onClick = system::clearLogs, enabled = lines.isNotEmpty(), shape = NijiyomeControls.shape) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(Res.string.settings_logs_clear), modifier = Modifier.padding(start = 4.dp))
            }
            FilledTonalButton(
                onClick = {
                    if (!shareLog()) snackbar.show(shareFailed)
                },
                enabled = lines.isNotEmpty(),
                shape = NijiyomeControls.shape,
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(Res.string.settings_logs_share), modifier = Modifier.padding(start = 4.dp))
            }
        }

        // Device source strip (Desktop when linked devices have shipped logs) + Push to PC (Android when paired).
        if (state.logDevices.isNotEmpty() || system.canPushLogs) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.logDevices.isNotEmpty()) {
                    FilterChip(
                        selected = state.logSource == LogSourceFilter.Local,
                        onClick = { system.selectLogSource(LogSourceFilter.Local) },
                        label = { Text(stringResource(Res.string.rd_s10_logs_this_pc)) },
                    )
                    state.logDevices.forEach { dev ->
                        FilterChip(
                            selected = (state.logSource as? LogSourceFilter.Device)?.id == dev.id,
                            onClick = { system.selectLogSource(LogSourceFilter.Device(dev.id, dev.name)) },
                            label = { Text(dev.name) },
                        )
                    }
                    FilterChip(
                        selected = state.logSource == LogSourceFilter.All,
                        onClick = { system.selectLogSource(LogSourceFilter.All) },
                        label = { Text(stringResource(Res.string.rd_s10_logs_all)) },
                    )
                }
                if (system.canPushLogs) {
                    FilledTonalButton(
                        onClick = system::pushLogsToPc,
                        enabled = !state.pushingLogs,
                        shape = NijiyomeControls.shape,
                    ) {
                        if (state.pushingLogs) {
                            LoadingIndicator(Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Text(stringResource(Res.string.rd_s10_logs_send_pc), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        // Severity filter + live search filter.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = severity == SeverityFilter.ALL,
                    onClick = { severity = SeverityFilter.ALL },
                    label = { Text(stringResource(Res.string.rd_s10_logs_all)) },
                )
                FilterChip(
                    selected = severity == SeverityFilter.WARN_UP,
                    onClick = { severity = SeverityFilter.WARN_UP },
                    label = { Text("WARN+") },
                )
                FilterChip(
                    selected = severity == SeverityFilter.ERROR_ONLY,
                    onClick = { severity = SeverityFilter.ERROR_ONLY },
                    label = { Text("ERROR") },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.rd_s10_logs_filter)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s10_search_clear))
                        }
                    }
                },
            )
        }

        when (val logs = state.logs) {
            LoadState.Loading -> if (rememberAfterDelay(150)) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            is LoadState.Err -> ErrorState(
                message = logs.cause.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = system::loadLogs,
            )
            is LoadState.Ok -> {
                val q = query.trim().lowercase()
                val filtered = remember(logs.value, severity, q) {
                    logs.value.filter { line ->
                        val sevOk = when (severity) {
                            SeverityFilter.ALL -> true
                            SeverityFilter.WARN_UP -> " W/" in line || " E/" in line
                            SeverityFilter.ERROR_ONLY -> " E/" in line
                        }
                        sevOk && (q.isEmpty() || q in line.lowercase())
                    }
                }
                if (filtered.isEmpty()) {
                    EmptyState(icon = Icons.Outlined.Terminal, title = stringResource(Res.string.settings_logs_empty))
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(filtered.size) { listState.scrollToItem(filtered.lastIndex) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        items(filtered) { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                                color = when {
                                    " E/" in line -> MaterialTheme.colorScheme.error
                                    " W/" in line -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
