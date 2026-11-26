// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.sheets.EnhancementsSheetContent
import app.nijiyome.feature.player.sheets.PictureControls
import app.nijiyome.feature.player.sheets.SheetDivider
import app.nijiyome.feature.player.sheets.SheetHeader
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.SettingsSection
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.QualityUiState
import java.util.Locale

// 0.4.0 (worker D, artifacts/player-quality-0.4.0-specs/d-player-ui.md): the in-player "Mejoras" panel. Adapted from Enigmata's
// QualitySheet.kt, ImagenContent.kt and ProcessedOnPcPanel.kt (AGPL-3.0-or-later, same author). Sparkles redesign (lane S6): the
// content is the grouped "Mejoras" sheet (sheets/EnhancementsSheet.kt); this entry point keeps its signature for the chrome that
// still opens it, and by default keeps the picture controls at the end (they have their own "Imagen" sheet in the new chrome).

/**
 * The "Mejoras" panel: [EnhancementsSheetContent] under a title with Close, plus the picture controls when [includePicture] (the
 * chrome that opens the separate "Imagen" sheet passes false).
 */
@Composable
internal fun EnhancementPanel(
    vm: PlayerViewModel,
    session: PlayerSession,
    state: PlayerUiState,
    quality: QualityUiState,
    processing: ProcessingUiState,
    /** The phone's frame generation (Android libmpv); null where there is none. */
    frameGen: PhoneFrameGen?,
    statsVisible: Boolean,
    onStatsChange: (Boolean) -> Unit,
    onOpenFgSettings: () -> Unit,
    onClose: () -> Unit,
    includePicture: Boolean = true,
    onOpenSettings: (SettingsSection) -> Unit = { onOpenFgSettings() },
) {
    Column(Modifier.fillMaxWidth()) {
        SheetHeader(stringResource(Res.string.player_fx_title), onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            EnhancementsSheetContent(
                vm = vm,
                session = session,
                state = state,
                quality = quality,
                processing = processing,
                frameGen = frameGen,
                statsVisible = statsVisible,
                onStatsChange = onStatsChange,
                onOpenFgSettings = onOpenFgSettings,
                onOpenSettings = onOpenSettings,
                onClose = onClose,
            )
            if (includePicture && quality.available) {
                SheetDivider()
                PictureControls(session, quality)
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

internal fun fpsText(fps: Double): String =
    if (fps % 1.0 == 0.0) fps.toInt().toString() else String.format(Locale.ROOT, "%.1f", fps)

internal fun decoderRes(mode: DecoderMode): StringResource = when (mode) {
    DecoderMode.AUTO -> Res.string.player_fx_decoder_auto
    DecoderMode.HW -> Res.string.player_fx_decoder_hw
    DecoderMode.HW_PLUS -> Res.string.player_fx_decoder_hw_plus
    DecoderMode.SW -> Res.string.player_fx_decoder_sw
}

// ---- small pieces ----------------------------------------------------------------------------------------------------------------------

@Composable
internal fun LabelText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
internal fun InfoLine(text: String) {
    Row(Modifier.padding(top = 6.dp), Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun WarningLine(text: String) {
    Row(Modifier.padding(top = 6.dp), Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** A setting with a switch; the whole row is the touch target. */
@Composable
internal fun SwitchRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    warning: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

