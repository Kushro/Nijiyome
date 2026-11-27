// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nijiyome.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.state.runCatchingApp
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_cancel
import app.nijiyome.upscale.ImageUpscaleSettings
import app.nijiyome.upscale.UpscaleImageKind
import app.nijiyome.upscale.UpscalePauseReason
import app.nijiyome.upscale.UpscaleQuality
import app.nijiyome.upscale.UpscaleQueue
import app.nijiyome.upscale.UpscaleSettingsStore
import app.nijiyome.upscale.UpscaleStatus
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

// v1.3.3 GPU image upscaling (docs/plan-upscaling.md §5): Ajustes › Mejoras › Imágenes. The store and the queue are bound by the
// platform module (step 4) and read with getOrNull: a build without them explains it instead of failing. Real-ESRGAN is the only
// engine of v1.3.3, so there is no engine picker; there is no GPU picker either (the queue uses the GPU that draws the app).

/** The kinds the user can turn on and off, in the order they are listed. */
private val OFFERED_KINDS = listOf(
    UpscaleImageKind.POSTER,
    UpscaleImageKind.BANNER,
    UpscaleImageKind.BACKDROP,
    UpscaleImageKind.EPISODE_THUMB,
    UpscaleImageKind.AVATAR,
)

/** Cache caps offered, in MB (the plan's range is 64 MB to 2 GB). */
private val CACHE_SIZES_MB = listOf(64, 128, 256, 512, 1024, 2048)

/** The cap the queue applies when [ImageUpscaleSettings.cacheMb] is null. */
internal val upscaleDefaultCacheMb: Int get() = if (upscaleOnPhone) 128 else 256

/** Settings group "Imágenes" (anchor `images_upscale`). */
@Composable
internal fun ImageUpscaleGroup() {
    val koin = getKoin()
    val store = remember(koin) { koin.getOrNull<UpscaleSettingsStore>() }
    val queue = remember(koin) { koin.getOrNull<UpscaleQueue>() }
    SettingsGroup(stringResource(Res.string.upscale_img_group), hint = stringResource(Res.string.upscale_img_group_hint)) {
        SettingAnchor("images_upscale") {
            if (store == null || queue == null) {
                SectionHint(stringResource(Res.string.upscale_img_unavailable))
            } else {
                ImageUpscaleRows(store, queue)
            }
        }
    }
}

@Composable
private fun ImageUpscaleRows(store: UpscaleSettingsStore, queue: UpscaleQueue) {
    val s by store.settings.collectAsState()
    val status by queue.status.collectAsState()
    val noGpu = status.pausedBy == UpscalePauseReason.NO_GPU
    val usable = s.enabled && !noGpu

    SwitchRow(
        label = stringResource(Res.string.upscale_img_enabled),
        hint = stringResource(Res.string.upscale_img_enabled_hint),
        checked = s.enabled,
        onChange = { on -> store.update { it.copy(enabled = on) } },
        divider = false,
    )
    UpscaleStatusLine(s, status, noGpu)

    SettingRow(stringResource(Res.string.upscale_img_quality), stringResource(Res.string.upscale_img_quality_hint), stack = true, inline = true) {
        Segmented(
            options = listOf(
                SegmentedOption(UpscaleQuality.FAST, stringResource(Res.string.upscale_img_quality_fast), enabled = usable),
                SegmentedOption(UpscaleQuality.BALANCED, stringResource(Res.string.upscale_img_quality_balanced), enabled = usable),
                SegmentedOption(UpscaleQuality.BEST, stringResource(Res.string.upscale_img_quality_best), enabled = usable),
            ),
            selected = s.quality,
            onSelect = { q -> store.update { it.copy(quality = q) } },
            fillWidth = true,
        )
    }

    SettingRow(stringResource(Res.string.upscale_img_kinds), stringResource(Res.string.upscale_img_kinds_hint), stack = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OFFERED_KINDS.forEach { kind ->
                val on = kind in s.kinds
                FilterChip(
                    selected = on,
                    onClick = { store.update { it.copy(kinds = if (on) it.kinds - kind else it.kinds + kind) } },
                    label = { Text(stringResource(kind.labelRes())) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    enabled = usable,
                )
            }
        }
    }

    if (upscaleOnPhone) {
        SwitchRow(
            label = stringResource(Res.string.upscale_img_charging),
            hint = stringResource(Res.string.upscale_img_charging_hint),
            checked = s.onlyWhileCharging,
            onChange = { on -> store.update { it.copy(onlyWhileCharging = on) } },
            enabled = usable,
        )
    }

    SettingRow(stringResource(Res.string.upscale_img_cache), stringResource(Res.string.upscale_img_cache_hint), stack = true) {
        val current = s.cacheMb ?: upscaleDefaultCacheMb
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CACHE_SIZES_MB.forEach { mb ->
                FilterChip(
                    selected = mb == current,
                    // the platform default stays null, so a later default change still reaches this user
                    onClick = { store.update { it.copy(cacheMb = if (mb == upscaleDefaultCacheMb) null else mb) } },
                    label = { Text(cacheSizeLabel(mb), fontFamily = NijiyomeFonts.jetBrainsMono) },
                )
            }
        }
    }

    ClearUpscaledRow(store, status.cacheBytes)
}

/** "GPU: … · 3 en cola · escalando · última: 84 ms por imagen", or why it is paused / unavailable. */
@Composable
private fun UpscaleStatusLine(s: ImageUpscaleSettings, status: UpscaleStatus, noGpu: Boolean) {
    val parts = buildList {
        when {
            noGpu -> add(stringResource(Res.string.upscale_img_status_no_gpu))
            !s.enabled -> add(stringResource(Res.string.upscale_img_status_off))
            else -> {
                status.gpuName?.let { add(stringResource(Res.string.upscale_img_status_gpu, it)) }
                val paused = status.pausedBy
                if (paused != null) {
                    add(stringResource(Res.string.upscale_img_status_paused, stringResource(paused.labelRes())))
                } else {
                    add(stringResource(if (status.running) Res.string.upscale_img_status_running else Res.string.upscale_img_status_idle))
                }
                if (status.queued > 0) add(stringResource(Res.string.upscale_img_status_queued, status.queued))
                status.lastMillis?.let { add(stringResource(Res.string.upscale_img_status_last, it.toString())) }
            }
        }
    }
    SettingRow(stringResource(Res.string.upscale_img_status), parts.joinToString(" · ")) {}
}

/** "Borrar imágenes escaladas (38 MB)", confirmed in place (no dialog). */
@Composable
private fun ClearUpscaledRow(store: UpscaleSettingsStore, bytes: Long) {
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val size = bytes.takeIf { it > 0 }?.let { formatBytes(it) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (!confirming) {
            OutlinedButton(
                onClick = { confirming = true },
                enabled = !clearing && bytes > 0,
                shape = NijiyomeControls.shape,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(
                    if (size != null) stringResource(Res.string.upscale_img_clear_used, size) else stringResource(Res.string.upscale_img_clear),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            Text(
                if (size != null) stringResource(Res.string.upscale_img_clear_confirm, size) else stringResource(Res.string.upscale_img_clear_confirm_unknown),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.politeLiveRegion(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirming = false }, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) }
                TextButton(
                    onClick = {
                        confirming = false
                        clearing = true
                        scope.launch {
                            // a failed clear only leaves the figure as it was; the status line shows the bytes still used
                            runCatchingApp { store.clearCache() }
                            clearing = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = NijiyomeControls.shape,
                ) { Text(stringResource(Res.string.upscale_img_clear_action)) }
            }
        }
    }
}

/** "64 MB" … "2 GB" (binary steps, as the cap is set; units are not translated). */
private fun cacheSizeLabel(mb: Int): String = if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024} GB" else "$mb MB"

private fun UpscaleImageKind.labelRes(): StringResource = when (this) {
    UpscaleImageKind.POSTER -> Res.string.upscale_img_kind_poster
    UpscaleImageKind.BANNER -> Res.string.upscale_img_kind_banner
    UpscaleImageKind.BACKDROP -> Res.string.upscale_img_kind_backdrop
    UpscaleImageKind.EPISODE_THUMB -> Res.string.upscale_img_kind_episode
    UpscaleImageKind.AVATAR -> Res.string.upscale_img_kind_avatar
    UpscaleImageKind.OTHER -> Res.string.upscale_img_kind_poster
}

private fun UpscalePauseReason.labelRes(): StringResource = when (this) {
    UpscalePauseReason.DISABLED -> Res.string.upscale_img_pause_disabled
    UpscalePauseReason.NO_GPU -> Res.string.upscale_img_status_no_gpu
    UpscalePauseReason.PLAYER_OPEN -> Res.string.upscale_img_pause_player
    UpscalePauseReason.FRAMEGEN_ACTIVE -> Res.string.upscale_img_pause_framegen
    UpscalePauseReason.LINK_BUSY -> Res.string.upscale_img_pause_link
    UpscalePauseReason.APP_HIDDEN -> Res.string.upscale_img_pause_hidden
    UpscalePauseReason.BATTERY_SAVER -> Res.string.upscale_img_pause_battery_saver
    UpscalePauseReason.LOW_BATTERY -> Res.string.upscale_img_pause_low_battery
    UpscalePauseReason.HOT -> Res.string.upscale_img_pause_hot
    UpscalePauseReason.NOT_CHARGING -> Res.string.upscale_img_pause_not_charging
    UpscalePauseReason.ENGINE_FAILED -> Res.string.upscale_img_pause_engine_failed
}
