// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nijiyome.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.AfterWatch
import app.nijiyome.model.torrent.StorageLocation
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.ui.platform.rememberFolderPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import app.nijiyome.designsystem.theme.NijiyomeControls

/** v1.1 (F6, sistema-ajustes-7): Tatakai Next's desktop wizard has a download folder step and a system step. */
internal actual val platformOnboardingSteps: Set<OnboardingStep> = setOf(OnboardingStep.DOWNLOADS, OnboardingStep.SYSTEM)

@Composable
internal actual fun OnboardingPlatformStep(step: OnboardingStep) {
    when (step) {
        OnboardingStep.DOWNLOADS -> OnboardingDownloads()
        OnboardingStep.SYSTEM -> OnboardingSystem()
        else -> Unit
    }
}

/**
 * The torrent download folder ([TorrentSettings.downloadDir], blank = the engine's default, shown as such) and "after watching",
 * saved at once through [SettingsRepository.setTorrentSettings] like Torrents › Ajustes.
 */
@Composable
private fun OnboardingDownloads() {
    val koin = getKoin()
    val settingsRepo = remember(koin) { koin.get<SettingsRepository>() }
    val torrents = remember(koin) { koin.getOrNull<TorrentRepository>() }
    val settings by settingsRepo.settings.collectAsState()
    val torrent = settings.torrent
    val scope = rememberCoroutineScope()
    var defaultDir by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(torrents, torrent.downloadDir) {
        // The engine reports the folder in use; with a blank setting that is its default.
        if (torrent.downloadDir.isBlank()) {
            defaultDir = torrents?.let { repo -> withContext(Dispatchers.IO) { runCatching { repo.storageUsage().downloadDir }.getOrNull() } }
        }
    }
    fun save(transform: (TorrentSettings) -> TorrentSettings) {
        scope.launch { settingsRepo.setTorrentSettings(transform(settingsRepo.settings.value.torrent)) }
    }
    val pick = rememberFolderPicker { path ->
        if (path != null) save { it.copy(downloadDir = path, storageLocation = StorageLocation.AppSpecific) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.settings_onboarding_downloads_folder), style = MaterialTheme.typography.labelLarge)
        Text(
            when {
                torrent.downloadDir.isNotBlank() -> torrent.downloadDir
                defaultDir != null -> stringResource(Res.string.settings_onboarding_downloads_default, defaultDir.orEmpty())
                else -> "…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = pick, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.settings_onboarding_downloads_change)) }
            if (torrent.downloadDir.isNotBlank()) {
                TextButton(onClick = { save { it.copy(downloadDir = "", storageLocation = StorageLocation.AppSpecific) } }, shape = NijiyomeControls.shape) {
                    Text(stringResource(Res.string.settings_onboarding_downloads_reset))
                }
            }
        }
        Text(stringResource(Res.string.settings_onboarding_downloads_after_watch), style = MaterialTheme.typography.labelLarge)
        Segmented(
            options = listOf(
                SegmentedOption(AfterWatch.KEEP, stringResource(Res.string.settings_onboarding_downloads_keep)),
                SegmentedOption(AfterWatch.DELETE, stringResource(Res.string.settings_onboarding_downloads_delete)),
            ),
            selected = torrent.afterWatch,
            onSelect = { choice -> save { it.copy(afterWatch = choice) } },
        )
    }
}

/** The rows of Ajustes › Datos for this PC (tray, autostart, Discord, magnet links) and the new-version check. */
@Composable
private fun OnboardingSystem() {
    val koin = getKoin()
    val settingsRepo = remember(koin) { koin.get<SettingsRepository>() }
    val settings by settingsRepo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    Column {
        PlatformSystemRows()
        SwitchRow(
            label = stringResource(Res.string.settings_update_check),
            hint = stringResource(Res.string.settings_update_check_hint),
            checked = settings.updateCheck,
            onChange = { on -> scope.launch { settingsRepo.update { it.copy(updateCheck = on) } } },
        )
    }
}
