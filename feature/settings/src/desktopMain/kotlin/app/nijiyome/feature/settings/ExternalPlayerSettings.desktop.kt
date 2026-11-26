// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import app.nijiyome.ui.platform.rememberFilePicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.feature.settings.resources.settings_engine_external_hint_desktop
import app.nijiyome.feature.settings.resources.settings_engine_mpv_hint_desktop
import app.nijiyome.feature.settings.resources.settings_preset_battery
import app.nijiyome.feature.settings.resources.settings_preset_battery_desktop
import app.nijiyome.feature.settings.resources.settings_deband_hint
import app.nijiyome.feature.settings.resources.settings_deband_hint_desktop
import app.nijiyome.feature.settings.resources.settings_quality_auto_hint
import app.nijiyome.feature.settings.resources.settings_quality_auto_hint_desktop
import app.nijiyome.feature.settings.resources.settings_decoder_hint
import app.nijiyome.feature.settings.resources.settings_decoder_hint_desktop
import app.nijiyome.model.PlayerEngine
import app.nijiyome.feature.settings.resources.settings_fg
import app.nijiyome.feature.settings.resources.settings_fg_auto
import app.nijiyome.feature.settings.resources.settings_fg_dll
import app.nijiyome.feature.settings.resources.settings_fg_dll_hint
import app.nijiyome.feature.settings.resources.settings_fg_found
import app.nijiyome.feature.settings.resources.settings_fg_hint
import app.nijiyome.feature.settings.resources.settings_fg_multiplier
import app.nijiyome.feature.settings.resources.settings_fg_multiplier_hint
import app.nijiyome.feature.settings.resources.settings_fg_needs_mpv
import app.nijiyome.feature.settings.resources.settings_fg_no_dll
import app.nijiyome.feature.settings.resources.settings_fg_no_layer
import app.nijiyome.feature.settings.resources.settings_fg_not_linux
import app.nijiyome.feature.settings.resources.settings_fg_performance
import app.nijiyome.feature.settings.resources.settings_fg_performance_hint
import app.nijiyome.feature.settings.resources.settings_xmpv
import app.nijiyome.feature.settings.resources.settings_xmpv_checking
import app.nijiyome.feature.settings.resources.settings_xmpv_download
import app.nijiyome.feature.settings.resources.settings_xmpv_download_failed
import app.nijiyome.feature.settings.resources.settings_xmpv_downloading
import app.nijiyome.feature.settings.resources.settings_xmpv_enhanced
import app.nijiyome.feature.settings.resources.settings_xmpv_enhanced_hint
import app.nijiyome.feature.settings.resources.settings_xmpv_enhanced_missing
import app.nijiyome.feature.settings.resources.settings_xmpv_found
import app.nijiyome.feature.settings.resources.settings_xmpv_missing
import app.nijiyome.feature.settings.resources.settings_xmpv_path
import app.nijiyome.feature.settings.resources.settings_xmpv_path_hint
import app.nijiyome.feature.settings.resources.settings_xmpv_uosc
import app.nijiyome.feature.settings.resources.settings_xmpv_uosc_hint
import app.nijiyome.player.mpv.external.AnimeBuild
import app.nijiyome.player.mpv.external.ExternalMpvSettingsControl
import app.nijiyome.player.mpv.external.LsfgVk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import java.util.Locale
import app.nijiyome.designsystem.theme.NijiyomeControls

// Hito 4, paso D: Settings › Reproductor on desktop for mpv in its own window (Tatakai Next's tk-ui/src/screens/settings/player.rs:
// the external mpv, "Integrado mejorado" with mpv-anime-build, and frame generation). What cannot run says why instead of hiding.

/** The External engine on desktop: which mpv, and the enhanced mode (mpv-anime-build) with its download. */
@Composable
internal actual fun ExternalPlayerSettingsRows(state: SettingsUiState, vm: SettingsViewModel) {
    val control = rememberExternalControl() ?: return
    val prefs by control.store.prefs.collectAsState()

    // mpv: found (with its version) or missing; checked again when the path changes.
    val mpvLine by produceState<String?>(null, prefs.mpvPath) {
        val mpv = withContext(Dispatchers.IO) { control.mpv() }
        value = if (mpv == null) "" else (control.mpvVersion(mpv)?.let { "$it · ${mpv.path}" } ?: mpv.path)
    }
    val mpvHint = when (val line = mpvLine) {
        null -> stringResource(Res.string.settings_xmpv_checking)
        "" -> stringResource(Res.string.settings_xmpv_missing)
        else -> stringResource(Res.string.settings_xmpv_found, line)
    }
    SettingRow(stringResource(Res.string.settings_xmpv), mpvHint, stack = true) {
        PathField(
            value = prefs.mpvPath.orEmpty(),
            label = stringResource(Res.string.settings_xmpv_path),
            hint = stringResource(Res.string.settings_xmpv_path_hint),
            onDone = { path -> control.store.update { it.copy(mpvPath = path) } },
        )
    }

    // The enhanced mode: offered off with its reason until the build is downloaded. The download is the control's (one at a time,
    // it goes on if Settings closes); this only shows it.
    val install by control.install.collectAsState()
    val buildState = remember(install) { control.animeBuildState() }
    val progress = (install as? ExternalMpvSettingsControl.Install.Running)?.progress
    val failure = (install as? ExternalMpvSettingsControl.Install.Failed)?.message
    val size = megabytes(AnimeBuild.ZIP_BYTES)
    val downloaded = buildState != AnimeBuild.State.NOT_DOWNLOADED
    SwitchRow(
        label = stringResource(Res.string.settings_xmpv_enhanced),
        checked = prefs.enhanced && downloaded,
        onChange = { on -> control.store.update { it.copy(enhanced = on) } },
        hint = if (downloaded) {
            stringResource(Res.string.settings_xmpv_enhanced_hint)
        } else {
            stringResource(Res.string.settings_xmpv_enhanced_hint) + " " + stringResource(Res.string.settings_xmpv_enhanced_missing, size)
        },
        enabled = downloaded,
    )
    if (!downloaded) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val p = progress
            if (p != null) {
                Text(stringResource(Res.string.settings_xmpv_downloading, (p * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            } else {
                FilledTonalButton(onClick = control::startInstall, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.settings_xmpv_download, size)) }
            }
            failure?.let {
                Text(stringResource(Res.string.settings_xmpv_download_failed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (prefs.enhanced && downloaded) {
        SwitchRow(
            label = stringResource(Res.string.settings_xmpv_uosc),
            checked = prefs.enhancedUosc,
            onChange = { on -> control.store.update { it.copy(enhancedUosc = on) } },
            hint = stringResource(Res.string.settings_xmpv_uosc_hint),
        )
    }
}

internal actual val externalEngineHintRes: StringResource = Res.string.settings_engine_external_hint_desktop
internal actual val mpvEngineHintRes: StringResource = Res.string.settings_engine_mpv_hint_desktop
internal actual val offeredEngines: List<PlayerEngine> = listOf(PlayerEngine.MPV, PlayerEngine.EXTERNAL)

private val desktopHints: Map<StringResource, StringResource> by lazy {
    mapOf(
        Res.string.settings_preset_battery to Res.string.settings_preset_battery_desktop,
        Res.string.settings_deband_hint to Res.string.settings_deband_hint_desktop,
        Res.string.settings_quality_auto_hint to Res.string.settings_quality_auto_hint_desktop,
        Res.string.settings_decoder_hint to Res.string.settings_decoder_hint_desktop,
        Res.string.settings_about_body to Res.string.settings_about_body_desktop,
        Res.string.settings_accounts_no_client_id to Res.string.settings_accounts_no_client_id_desktop,
        Res.string.settings_accounts_transfer_hint to Res.string.settings_accounts_transfer_hint_desktop,
        // Listas y cuentas: "this phone" reads "this PC".
        Res.string.settings_accounts_hint to Res.string.settings_accounts_hint_desktop,
        Res.string.settings_accounts_unlink_body to Res.string.settings_accounts_unlink_body_desktop,
        Res.string.settings_accounts_conflict_local to Res.string.settings_accounts_conflict_local_desktop,
        Res.string.settings_accounts_write_back_hint to Res.string.settings_accounts_write_back_hint_desktop,
        Res.string.settings_accounts_sync_mode_body_first to Res.string.settings_accounts_sync_mode_body_first_desktop,
        Res.string.settings_accounts_sync_mode_body to Res.string.settings_accounts_sync_mode_body_desktop,
        Res.string.settings_accounts_mode_import_hint to Res.string.settings_accounts_mode_import_hint_desktop,
        Res.string.settings_accounts_mode_export_hint to Res.string.settings_accounts_mode_export_hint_desktop,
        Res.string.settings_accounts_history_note to Res.string.settings_accounts_history_note_desktop,
        Res.string.settings_onboarding_lists_body to Res.string.settings_onboarding_lists_body_desktop,
        // Hito 6 (D1): Datos and the wizard's welcome said "this phone" / "continue on your PC".
        Res.string.settings_data_history_hint to Res.string.settings_data_history_hint_desktop,
        Res.string.settings_data_history_clear_body to Res.string.settings_data_history_clear_body_desktop,
        Res.string.settings_data_reset_hint to Res.string.settings_data_reset_hint_desktop,
        Res.string.settings_onboarding_welcome_body to Res.string.settings_onboarding_welcome_body_desktop,
    )
}

internal actual fun platformHint(res: StringResource): StringResource = desktopHints[res] ?: res

internal actual val mpvTlsSettingShown: Boolean = false

/** Frame generation (v1.3.2): embedded zero-copy LSFG inside the built-in player (Linux + Windows), or lsfg-vk on external mpv. */
@Composable
internal actual fun FrameGenSettingsSlot() {
    val control = rememberExternalControl() ?: return
    val prefs by control.store.prefs.collectAsState()
    val embeddedProbe by produceState<app.nijiyome.player.mpv.framegen.DesktopFgProbe?>(null, prefs.lsfgDll) {
        value = withContext(Dispatchers.IO) {
            app.nijiyome.player.mpv.framegen.DesktopFrameGenController.probeEmbeddedAvailability(prefs.lsfgDll)
        }
    }
    val detectedDll by produceState<java.io.File?>(null, prefs.lsfgDll) {
        value = withContext(Dispatchers.IO) {
            app.nijiyome.player.mpv.framegen.DesktopFrameGenNative.findLosslessDll(prefs.lsfgDll)
        }
    }
    val availability by produceState<LsfgVk.Availability?>(null, prefs.lsfgDll) {
        value = withContext(Dispatchers.IO) { control.frameGen() }
    }
    val mpvMissing by produceState(false, prefs.mpvPath) { value = withContext(Dispatchers.IO) { control.mpv() == null } }
    val emb = embeddedProbe
    val a = availability
    val embeddedUsable = emb?.ok == true
    val externalUsable = a?.available == true && !mpvMissing
    val usable = embeddedUsable || externalUsable

    val reason = when {
        embeddedUsable -> {
            val mode = if (emb.zeroCopyVk) "zero-copy" else "staging"
            val gpu = emb.deviceName.ifBlank { "Vulkan" }
            stringResource(Res.string.settings_fg_embedded_ready, mode, gpu)
        }
        externalUsable -> a.dll?.let { stringResource(Res.string.settings_fg_found, it.name) }
        emb?.errorCode == "no_dll" || emb?.errorCode == "dll_unreadable" || emb?.errorCode == "missing_spirv" ->
            stringResource(Res.string.settings_fg_no_dll)
        a?.missing == LsfgVk.Missing.LAYER -> stringResource(Res.string.settings_fg_no_layer)
        a?.missing == LsfgVk.Missing.DLL -> stringResource(Res.string.settings_fg_no_dll)
        a?.missing == LsfgVk.Missing.NOT_LINUX -> stringResource(Res.string.settings_fg_not_linux)
        else -> null
    }
    val hint = listOfNotNull(
        stringResource(Res.string.settings_fg_hint),
        reason,
        stringResource(Res.string.settings_fg_needs_mpv).takeIf { !embeddedUsable && mpvMissing },
    ).joinToString(" ")

    SwitchRow(
        label = stringResource(Res.string.settings_fg),
        checked = prefs.frameGen && usable,
        onChange = { on -> control.store.update { it.copy(frameGen = on) } },
        hint = hint,
        enabled = usable,
    )
    if (prefs.frameGen && usable) {
        SettingRow(stringResource(Res.string.settings_fg_multiplier), stringResource(Res.string.settings_fg_multiplier_hint), stack = true) {
            Segmented(
                options = listOf(
                    SegmentedOption(0, stringResource(Res.string.settings_fg_auto)),
                    SegmentedOption(2, "×2"),
                    SegmentedOption(3, "×3"),
                    SegmentedOption(4, "×4"),
                ),
                selected = prefs.frameGenMultiplier,
                onSelect = { m -> control.store.update { it.copy(frameGenMultiplier = m) } },
                small = true,
                fillWidth = true,
            )
        }
        SwitchRow(
            label = stringResource(Res.string.settings_fg_performance),
            checked = prefs.frameGenPerformance,
            onChange = { on -> control.store.update { it.copy(frameGenPerformance = on) } },
            hint = stringResource(Res.string.settings_fg_performance_hint),
        )
        SwitchRow(
            label = stringResource(Res.string.settings_fg_anti_artifacts),
            checked = prefs.frameGenAntiArtifacts,
            onChange = { on -> control.store.update { it.copy(frameGenAntiArtifacts = on) } },
            hint = stringResource(Res.string.settings_fg_anti_artifacts_hint),
        )
    }
    SettingRow(stringResource(Res.string.settings_fg_dll), stringResource(Res.string.settings_fg_dll_hint), stack = true) {
        PathField(
            value = prefs.lsfgDll.orEmpty(),
            label = stringResource(Res.string.settings_fg_dll),
            hint = detectedDll?.path ?: a?.dll?.path.orEmpty(),
            onDone = { path -> control.store.update { it.copy(lsfgDll = path) } },
        )
    }
}

@Composable
private fun rememberExternalControl(): ExternalMpvSettingsControl? {
    val koin = getKoin()
    return remember { koin.getOrNull<ExternalMpvSettingsControl>() }
}

/**
 * An executable or shader file: "Elegir archivo…" opens the system file chooser (choose, don't type); the path can still be typed
 * and is saved when the field loses focus or with Enter. "Automático" clears it (empty = found on its own).
 */
@Composable
internal fun PathField(value: String, label: String, hint: String, onDone: (String?) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(value) { text = value }
    val pick = rememberFilePicker(emptyList(), multiple = false) { files -> files.firstOrNull()?.let { onDone(it) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            placeholder = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone(text.trim().ifEmpty { null }) }),
            modifier = Modifier.fillMaxWidth().onFocusLost { if (text != value) onDone(text.trim().ifEmpty { null }) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = pick, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s10_path_pick)) }
            if (value.isNotEmpty()) TextButton(onClick = { onDone(null) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s10_path_auto)) }
        }
    }
}

private fun Modifier.onFocusLost(block: () -> Unit): Modifier = onFocusChanged { if (!it.isFocused) block() }

private fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
