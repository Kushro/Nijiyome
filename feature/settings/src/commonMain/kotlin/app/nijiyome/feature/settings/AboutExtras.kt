// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.settings

import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.nijiyome.ui.state.userMessage
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

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
        FilledTonalButton(onClick = system::checkUpdates, enabled = update != UpdateCheckState.Checking) {
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
            is UpdateCheckState.Available -> TextButton(onClick = { openUrl(update.info.url) }) {
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

/** "Abrir registros": last 500 lines of the app log (newest at the bottom) + Compartir (FileProvider) + Vaciar. */
@Composable
internal fun LogsSheet(system: SystemViewModel, onDismiss: () -> Unit) {
    val state by system.state.collectAsStateWithLifecycle()
    val shareLog = rememberShareLog(system)
    val shareFailed = stringResource(Res.string.settings_logs_share_failed)
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(system) { system.loadLogs() }
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(Res.string.settings_logs),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            val lines = (state.logs as? LoadState.Ok)?.value.orEmpty()
            TextButton(onClick = system::clearLogs, enabled = lines.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(Res.string.settings_logs_clear), modifier = Modifier.padding(start = 4.dp))
            }
            FilledTonalButton(
                onClick = {
                    if (!shareLog()) snackbar.show(shareFailed)
                },
                enabled = lines.isNotEmpty(),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(Res.string.settings_logs_share), modifier = Modifier.padding(start = 4.dp))
            }
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
            is LoadState.Ok -> if (logs.value.isEmpty()) {
                EmptyState(icon = Icons.Outlined.Terminal, title = stringResource(Res.string.settings_logs_empty))
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(logs.value.size) { listState.scrollToItem(logs.value.lastIndex) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(logs.value) { line ->
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
