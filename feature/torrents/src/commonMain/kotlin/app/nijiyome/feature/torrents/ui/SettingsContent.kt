// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.ui

import app.nijiyome.feature.torrents.resources.Res
import app.nijiyome.feature.torrents.resources.*
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.nijiyome.ui.platform.rememberFolderPicker
import app.nijiyome.ui.platform.rememberOpenFolder
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.TextButton
import app.nijiyome.designsystem.components.SectionCard
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.torrents.vm.TorrentFieldRules
import app.nijiyome.model.LoadState
import app.nijiyome.model.torrent.AfterWatch
import app.nijiyome.model.torrent.StorageLocation
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.model.torrent.TorrentStorage
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.labels.labelRes
import kotlin.math.roundToInt

/** Slider stops of the speed limits in KiB/s (0 = unlimited); the text field accepts any value up to MAX_LIMIT_KIB. */
internal val LIMIT_STEPS = listOf(0, 128, 256, 512, 1_024, 2_048, 5_120, 10_240, 25_600, 51_200)

/** Nearest slider stop of [kib]. */
internal fun limitStepIndex(kib: Int): Int = LIMIT_STEPS.indices.minByOrNull { kotlin.math.abs(LIMIT_STEPS[it] - kib) } ?: 0

/**
 * Settings › Torrents (11 §3.5): Velocidad, Red, Almacenamiento, Segundo plano. Immediate save per field ([onChange] ->
 * SettingsRepository.setTorrentSettings), inline validation in supportingText.
 */
@Composable
internal fun TorrentSettingsBody(
    settings: TorrentSettings,
    storage: LoadState<TorrentStorage>,
    onChange: ((TorrentSettings) -> TorrentSettings) -> Unit,
    onRetryStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val storageAccess = rememberStorageAccess()
    val treePicker = rememberFolderPicker { uri: String? ->
        if (uri != null) {
            storageAccess.keepFolderAccess(uri)
            onChange { it.copy(storageLocation = StorageLocation.UserFolder(uri)) }
            onRetryStorage()
        }
    }
    // Desktop: the chosen folder is where new torrents download (TorrentSettings.downloadDir), as in Tatakai Next.
    val downloadFolderPicker = rememberFolderPicker { path: String? ->
        if (path != null) {
            onChange { it.copy(downloadDir = path, storageLocation = StorageLocation.AppSpecific) }
            onRetryStorage()
        }
    }
    val openFolder = rememberOpenFolder()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.torrent_settings_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Velocidad ---
        SectionCard(title = stringResource(Res.string.torrent_settings_speed)) {
            LimitField(
                label = stringResource(Res.string.torrent_settings_download_limit),
                value = settings.downloadLimitKib,
                onValue = { v -> onChange { it.copy(downloadLimitKib = v) } },
            )
            LimitField(
                label = stringResource(Res.string.torrent_settings_upload_limit),
                value = settings.uploadLimitKib,
                onValue = { v -> onChange { it.copy(uploadLimitKib = v) } },
            )
        }

        // --- Red ---
        SectionCard(title = stringResource(Res.string.torrent_settings_connection)) {
            // A desktop has no metered-network signal: these two would do nothing there.
            if (!desktopTorrentUi) {
                SwitchRow(
                    title = stringResource(Res.string.torrent_android_wifi_only),
                    hint = stringResource(Res.string.torrent_android_wifi_only_hint),
                    checked = settings.wifiOnly,
                    onChange = { v -> onChange { it.copy(wifiOnly = v) } },
                )
                SwitchRow(
                    title = stringResource(Res.string.torrent_android_upload_mobile),
                    hint = stringResource(Res.string.torrent_android_upload_mobile_hint),
                    checked = settings.uploadOnMobileData,
                    onChange = { v -> onChange { it.copy(uploadOnMobileData = v) } },
                )
            }
            NumberField(
                label = stringResource(Res.string.torrent_settings_port),
                value = settings.listenPort,
                hint = stringResource(Res.string.torrent_settings_port_hint),
                error = stringResource(Res.string.torrent_settings_port_invalid),
                parse = TorrentFieldRules::port,
                onValue = { v -> onChange { it.copy(listenPort = v) } },
            )
            SwitchRow(
                title = stringResource(Res.string.torrent_settings_upnp),
                hint = stringResource(if (desktopTorrentUi) Res.string.torrent_desktop_upnp_hint else Res.string.torrent_android_upnp_hint),
                checked = settings.upnp,
                onChange = { v -> onChange { it.copy(upnp = v) } },
            )
            NumberField(
                label = stringResource(Res.string.torrent_settings_max_peers),
                value = settings.maxPeers,
                hint = stringResource(Res.string.torrent_settings_max_peers_hint),
                error = stringResource(Res.string.torrent_settings_max_peers_invalid, TorrentSettings.MAX_PEERS),
                parse = TorrentFieldRules::peers,
                onValue = { v -> onChange { it.copy(maxPeers = v) } },
            )
            Text(
                stringResource(Res.string.torrent_settings_discovery),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            SwitchRow(
                title = stringResource(Res.string.torrent_settings_dht),
                hint = stringResource(Res.string.torrent_settings_dht_hint),
                checked = settings.dht,
                onChange = { v -> onChange { it.copy(dht = v) } },
            )
            SwitchRow(
                title = stringResource(Res.string.torrent_settings_pex),
                hint = stringResource(Res.string.torrent_settings_pex_hint),
                checked = settings.pex,
                onChange = { v -> onChange { it.copy(pex = v) } },
            )
            SwitchRow(
                title = stringResource(Res.string.torrent_settings_lsd),
                hint = stringResource(if (desktopTorrentUi) Res.string.torrent_desktop_lsd_hint else Res.string.torrent_android_lsd_hint),
                checked = settings.lsd,
                onChange = { v -> onChange { it.copy(lsd = v) } },
            )
        }

        // --- Almacenamiento ---
        SectionCard(title = stringResource(Res.string.torrent_settings_storage)) {
            val userFolder = settings.storageLocation as? StorageLocation.UserFolder
            if (desktopTorrentUi) {
                DesktopDownloadFolder(
                    chosen = settings.downloadDir.ifBlank { userFolder?.treeUri.orEmpty() },
                    current = (storage as? LoadState.Ok)?.value?.downloadDir.orEmpty(),
                    onChange = { downloadFolderPicker() },
                    onDefault = {
                        onChange { it.copy(downloadDir = "", storageLocation = StorageLocation.AppSpecific) }
                        onRetryStorage()
                    },
                    onOpen = openFolder,
                )
            } else {
                Segmented(
                    options = listOf(
                        SegmentedOption(false, stringResource(Res.string.torrent_android_storage_app)),
                        SegmentedOption(true, stringResource(Res.string.torrent_android_storage_folder)),
                    ),
                    selected = userFolder != null,
                    onSelect = { folder ->
                        if (folder) {
                            treePicker()
                        } else {
                            onChange { it.copy(storageLocation = StorageLocation.AppSpecific) }
                            onRetryStorage()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (userFolder != null) {
                        val name = storageAccess.folderName(userFolder.treeUri)
                        stringResource(Res.string.torrent_android_storage_folder_hint, name ?: userFolder.treeUri)
                    } else {
                        stringResource(Res.string.torrent_android_storage_app_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (userFolder != null) {
                    OutlinedButton(onClick = { treePicker() }) { Text(stringResource(Res.string.torrent_android_storage_change)) }
                }
            }
            when (storage) {
                LoadState.Loading -> DelayedLoading(Modifier.padding(8.dp))
                is LoadState.Err -> app.nijiyome.designsystem.components.ErrorState(
                    message = torrentErrorMessage(storage.cause),
                    retryLabel = stringResource(app.nijiyome.ui.resources.Res.string.ui_retry),
                    onRetry = onRetryStorage,
                    compact = true,
                )
                is LoadState.Ok -> Text(
                    stringResource(Res.string.torrent_android_storage_usage, formatBytes(storage.value.usedBytes), formatBytes(storage.value.freeBytes)),
                    style = monoStyle(MaterialTheme.typography.bodyMedium),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Text(stringResource(Res.string.torrent_settings_after_watch), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Segmented(
                options = AfterWatch.entries.map { SegmentedOption(it, stringResource(it.labelRes())) },
                selected = settings.afterWatch,
                onSelect = { v -> onChange { it.copy(afterWatch = v) } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Text(
                stringResource(Res.string.torrent_settings_after_watch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- parity wave A (worker q): horario, trackers propios, límite de caché, precarga ---
        ParityTorrentSettings(settings, onChange)

        // --- Segundo plano ---
        SectionCard(title = stringResource(Res.string.torrent_settings_background)) {
            val backgroundHint = when {
                !settings.backgroundAck -> Res.string.rd_s8_tor_background_locked
                desktopTorrentUi -> Res.string.torrent_desktop_background_hint
                else -> Res.string.torrent_android_background_hint
            }
            SwitchRow(
                title = stringResource(Res.string.torrent_settings_background_switch),
                hint = stringResource(backgroundHint),
                checked = settings.backgroundDownloads && settings.backgroundAck,
                enabled = settings.backgroundAck,
                onChange = { v -> onChange { it.copy(backgroundDownloads = v) } },
            )
            // No battery signal on a desktop.
            if (!desktopTorrentUi) {
                SwitchRow(
                    title = stringResource(Res.string.torrent_android_pause_low_battery),
                    hint = stringResource(Res.string.torrent_android_pause_low_battery_hint),
                    checked = settings.pauseOnLowBattery,
                    onChange = { v -> onChange { it.copy(pauseOnLowBattery = v) } },
                )
            }
        }
    }
}

/**
 * Desktop Settings › Torrents › Almacenamiento (Tatakai Next's `download_dir`): where new torrents are written, change it, go back to
 * the default one, open it in the file manager.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DesktopDownloadFolder(chosen: String, current: String, onChange: () -> Unit, onDefault: () -> Unit, onOpen: ((String) -> Unit)?) {
    Text(stringResource(Res.string.torrent_desktop_folder), style = MaterialTheme.typography.titleSmall)
    val shown = current.ifBlank { chosen }
    Text(
        if (shown.isNotBlank()) stringResource(Res.string.torrent_desktop_folder_hint, shown) else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onChange) { Text(stringResource(Res.string.torrent_android_storage_change)) }
        if (chosen.isNotBlank()) {
            TextButton(onClick = onDefault) { Text(stringResource(Res.string.torrent_desktop_folder_default)) }
        }
        if (onOpen != null && shown.isNotBlank()) {
            TextButton(onClick = { onOpen(shown) }) { Text(stringResource(Res.string.torrent_desktop_open_folder)) }
        }
    }
}

/** Speed limit: slider over [LIMIT_STEPS] + exact numeric field (KiB/s, 0 = unlimited). */
@Composable
private fun LimitField(label: String, value: Int, onValue: (Int) -> Unit) {
    var slider by rememberSaveable(value) { mutableFloatStateOf(limitStepIndex(value).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        NumberField(
            label = label,
            value = value,
            hint = stringResource(Res.string.torrent_settings_limit_hint),
            error = stringResource(Res.string.torrent_settings_limit_invalid, TorrentSettings.MAX_LIMIT_KIB),
            parse = TorrentFieldRules::limit,
            onValue = onValue,
            suffix = stringResource(Res.string.torrent_settings_kib),
        )
        Slider(
            value = slider,
            onValueChange = { slider = it },
            onValueChangeFinished = { onValue(LIMIT_STEPS[slider.roundToInt().coerceIn(LIMIT_STEPS.indices)]) },
            valueRange = 0f..LIMIT_STEPS.lastIndex.toFloat(),
            steps = LIMIT_STEPS.size - 2,
        )
    }
}

/** Numeric text field saving only valid values; the error replaces the hint in supportingText. */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    hint: String,
    error: String,
    parse: (String) -> Int?,
    onValue: (Int) -> Unit,
    suffix: String? = null,
) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    val invalid = parse(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() }.take(MAX_DIGITS)
            parse(text)?.let { if (it != value) onValue(it) }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(label) },
        singleLine = true,
        isError = invalid,
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else {
            null
        },
        supportingText = { Text(if (invalid) error else hint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private const val MAX_DIGITS = 7
