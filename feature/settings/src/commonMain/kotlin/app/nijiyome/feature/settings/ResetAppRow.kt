// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.settings

import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.LocalAppSnackbar
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

/**
 * Parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.4, 2026-11-09 amendment; checklist system.reset-app,
 * system.export-history, system.clear-watch-history). Owner: worker q. Mounted at the end of `DataSection`.
 *
 * "Exportar historial" (the camelCase `HistoryEntry[]` JSON that travels over Link, so desktop imports it), "Borrar historial" (with the
 * optional "también en la PC" when a PC is paired) and "Restablecer la app": error-coloured outlined button → confirmation dialog →
 * [SettingsViewModel.resetApp]; while resetting the button shows a `LoadingIndicator`. On `SettingsEvent.ResetDone` the settings screen
 * recreates the activity, which lands on the first-run wizard (`onboardingDone` is false again).
 */
@Composable
internal fun ResetAppRow(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val system: SystemViewModel = koinViewModel()
    val sys by system.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val saveExport = rememberSaveJsonDocument { ok ->
        snackbar.show(getString(if (ok) Res.string.settings_data_history_exported else Res.string.settings_action_failed))
    }
    val currentSnackbar by rememberUpdatedState(snackbar)
    LaunchedEffect(system) {
        system.events.collect { event ->
            when (event) {
                is SystemEvent.HistoryExported ->
                    saveExport("nijiyome-history-${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}.json", event.json)
                SystemEvent.HistoryCleared -> currentSnackbar.show(getString(Res.string.settings_data_history_cleared))
                SystemEvent.LogsCleared -> currentSnackbar.show(getString(Res.string.settings_logs_cleared))
                is SystemEvent.Failed -> currentSnackbar.show(getString(Res.string.settings_action_failed))
            }
        }
    }

    SettingRow(stringResource(Res.string.settings_data_history), stringResource(platformHint(Res.string.settings_data_history_hint)), stack = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = system::exportHistory, enabled = !sys.exportingHistory) {
                if (sys.exportingHistory) {
                    LoadingIndicator(Modifier.size(20.dp))
                } else {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Text(stringResource(Res.string.settings_data_history_export), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { confirmClear = true }, enabled = !sys.clearingHistory) {
                if (sys.clearingHistory) {
                    LoadingIndicator(Modifier.size(20.dp))
                } else {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Text(stringResource(Res.string.settings_data_history_clear), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (vm.hasReset) SettingRow(stringResource(Res.string.settings_data_reset), stringResource(platformHint(Res.string.settings_data_reset_hint)), stack = true) {
        OutlinedButton(
            onClick = { confirmReset = true },
            enabled = !state.resetting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            if (state.resetting) {
                LoadingIndicator(Modifier.size(20.dp))
            } else {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Text(
                stringResource(if (state.resetting) Res.string.settings_data_resetting else Res.string.settings_data_reset),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    if (confirmClear) {
        var alsoPc by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(Res.string.settings_data_history_clear_title)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(platformHint(Res.string.settings_data_history_clear_body)))
                    if (state.linkPaired) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = alsoPc, role = Role.Checkbox, onValueChange = { alsoPc = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = alsoPc, onCheckedChange = null)
                            Text(stringResource(Res.string.settings_data_history_clear_pc), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        system.clearHistory(includeRemote = alsoPc && state.linkPaired)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.settings_data_history_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }

    if (confirmReset) {
        if (resetNeedsTypedWord) {
            TypedResetDialog(onDismiss = { confirmReset = false }) { keepTorrents ->
                confirmReset = false
                vm.resetApp(keepTorrents = keepTorrents)
            }
        } else {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(Res.string.settings_data_reset_title)) },
                text = { Text(stringResource(platformHint(Res.string.settings_data_reset_hint))) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmReset = false
                            vm.resetApp()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(Res.string.settings_data_reset_confirm)) }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(UiR.string.ui_cancel)) } },
            )
        }
    }
}

/** The typed confirmation of [resetNeedsTypedWord] (Tatakai Next's `reset_word_matches`): case and surrounding spaces do not matter. */
internal fun resetWordMatches(typed: String, word: String): Boolean {
    val t = typed.trim()
    return t.isNotEmpty() && t.lowercase() == word.trim().lowercase()
}

/**
 * Desktop's reset confirmation (Tatakai Next's danger zone): the word of `settings_data_reset_word` has to be typed, and
 * "Conservar los torrents descargados" (on by default) decides whether the torrents go with their files.
 */
@Composable
private fun TypedResetDialog(onDismiss: () -> Unit, onConfirm: (keepTorrents: Boolean) -> Unit) {
    val word = stringResource(Res.string.settings_data_reset_word)
    var typed by rememberSaveable { mutableStateOf("") }
    var keepTorrents by rememberSaveable { mutableStateOf(true) }
    val matches = resetWordMatches(typed, word)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Res.string.settings_data_reset_title)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(platformHint(Res.string.settings_data_reset_hint)))
                Row(
                    Modifier.fillMaxWidth().toggleable(value = keepTorrents, role = Role.Checkbox, onValueChange = { keepTorrents = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keepTorrents, onCheckedChange = null)
                    androidx.compose.foundation.layout.Column(Modifier.padding(start = 8.dp)) {
                        Text(stringResource(Res.string.settings_data_reset_keep_torrents))
                        if (!keepTorrents) {
                            Text(
                                stringResource(Res.string.settings_data_reset_keep_torrents_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.settings_data_reset_type, word)) },
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(keepTorrents) },
                enabled = matches,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.settings_data_reset_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}
