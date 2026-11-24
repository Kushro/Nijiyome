// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.SIDE_SHEET_MIN_WIDTH_DP
import app.nijiyome.designsystem.components.SectionCard
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.link.GroupedProfileList
import app.nijiyome.feature.link.ProfileList
import app.nijiyome.feature.link.linkDegradedText
import app.nijiyome.feature.link.linkEncoderText
import app.nijiyome.feature.link.linkFrameGenText
import app.nijiyome.feature.link.linkUpscaleText
import app.nijiyome.feature.link.profileLabel
import app.nijiyome.model.AspectMode
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.ImageAdjust
import app.nijiyome.model.LinkProcessingInfo
import app.nijiyome.model.LinkUpscale
import app.nijiyome.model.ProcessingLocation
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import app.nijiyome.model.SharpenLevel
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.QualityLevel
import app.nijiyome.player.QualityUiState
import app.nijiyome.player.decoderInUseLabel
import java.util.Locale
import kotlin.math.roundToInt

// 0.4.0 (worker D, artifacts/player-quality-0.4.0-specs/d-player-ui.md): the in-player "Mejoras" panel and its status chip.
// Adapted from Enigmata's QualitySheet.kt ("Procesar en: Teléfono | PC | Mixto" on top, then engine / tier / presets / sharpen /
// status reasons), ImagenContent.kt (quick FG card, zoom, aspect, decoder, image filters) and ProcessedOnPcPanel.kt ("what the PC
// does"), AGPL-3.0-or-later, same author, redrawn with Nijiyome's design system. Every change applies to the running playback (the
// engines do it live); only "Usar como predeterminado" writes Settings.

// ---- panel -------------------------------------------------------------------------------------------------------------------------------

/**
 * The "Mejoras" panel. Sections: a. Procesar en (Teléfono | PC | Mixto), b. what the PC does + its profiles (PC / Mixto),
 * c. upscaling on the phone (libmpv) or "Las mejoras necesitan MPV" (Media3), d. the phone's frame generation, e. picture
 * (aspect, zoom, filters, decoder), f. statistics overlay switch.
 */
@OptIn(ExperimentalLayoutApi::class)
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
) {
    val location = processing.location
    val mpv = quality.available
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.player_fx_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(Res.string.player_fx_close)) }
        }

        ProcessingSection(vm, processing, onOpenFgSettings)

        if (location != ProcessingLocation.PHONE) {
            PcSection(vm, state, processing)
        }
        when {
            location == ProcessingLocation.PHONE && !mpv -> Media3Section(vm, onClose)
            location == ProcessingLocation.PHONE -> PhoneQualitySection(vm, session, state, quality)
            location == ProcessingLocation.MIXED -> Note(stringResource(Res.string.player_fx_mixed_note))
            else -> Note(stringResource(Res.string.player_processing_local_off))
        }
        // FG only exists on libmpv; strict PC suspends it (the PC already processes everything).
        if (frameGen != null && mpv && location != ProcessingLocation.PC) {
            frameGen.Section(onOpenFgSettings)
        }
        if (mpv) PictureSection(session, quality)

        SectionCard {
            SwitchRow(
                title = stringResource(Res.string.player_fx_stats),
                hint = stringResource(Res.string.player_fx_stats_hint),
                checked = statsVisible,
                onChange = onStatsChange,
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

// a. Procesar en ----------------------------------------------------------------------------------------------------------------------------

@Composable
private fun ProcessingSection(vm: PlayerViewModel, processing: ProcessingUiState, onOpenFgSettings: () -> Unit) {
    val selected = processing.switchingTo ?: processing.location
    SectionCard(title = stringResource(Res.string.player_processing_title)) {
        Segmented(
            options = ProcessingLocation.entries.map { loc ->
                val option = processing.option(loc)
                SegmentedOption(
                    value = loc,
                    label = stringResource(shortLocationRes(loc)),
                    enabled = processing.switchingTo == null && (option.available || loc == processing.location),
                    description = stringResource(processingLocationHint(loc)),
                )
            },
            selected = selected,
            onSelect = vm::setProcessingLocation,
            fillWidth = true,
        )
        processing.switchingTo?.let { to ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(chipLabel(EnhancementChip.Switching(to), rememberChipWords()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = vm::cancelProcessingSwitch) { Text(stringResource(Res.string.player_fx_switch_cancel)) }
            }
        }
        // What each location does, in one line, and why it cannot be chosen now (Enigmata's QualitySheet does the same).
        var fgShaders = false
        ProcessingLocation.entries.forEach { loc ->
            val option = processing.option(loc)
            val why = option.block?.takeIf { loc != processing.location }?.let { block ->
                val fgReason = option.detail?.let { d -> phoneFrameGenReason(d) }
                    ?.takeIf { block == ProcessingBlock.PHONE_FG_UNAVAILABLE }
                if (fgReason?.missingShaders == true) fgShaders = true
                stringResource(processingBlockText(block)) + (fgReason?.let { " " + it.text } ?: "")
            }
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(
                    stringResource(processingLocationLabel(loc)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (loc == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(stringResource(processingLocationHint(loc)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                why?.let { InfoLine(it) }
            }
        }
        if (fgShaders) {
            TextButton(onClick = onOpenFgSettings) { Text(stringResource(Res.string.player_fx_fg_import)) }
        }
    }
}

private fun shortLocationRes(location: ProcessingLocation): StringResource = when (location) {
    ProcessingLocation.PHONE -> Res.string.player_fx_where_phone
    ProcessingLocation.PC -> Res.string.player_fx_where_pc
    ProcessingLocation.MIXED -> Res.string.player_fx_where_mixed
}

// b. What the PC does ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun PcSection(vm: PlayerViewModel, state: PlayerUiState, processing: ProcessingUiState) {
    val info = processing.pc
    val name = info?.pcName ?: processing.pcName
    SectionCard(title = name?.let { stringResource(Res.string.player_fx_pc_card_named, it) } ?: stringResource(Res.string.player_fx_pc_card)) {
        if (info == null) {
            InfoLine(stringResource(Res.string.player_fx_pc_waiting))
        } else {
            PcRows(info, state)
        }
    }
    val groups = processing.profileGroups
    if (groups.isNotEmpty() || state.linkProfiles.isNotEmpty()) {
        SectionCard(title = stringResource(Res.string.player_link_profile)) {
            Text(stringResource(Res.string.player_link_profile_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (processing.profileChanging || state.linkProfileChanging) {
                Row(Modifier.padding(top = 8.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.player_link_profile_changing), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val selected = processing.profile ?: state.linkProfile.orEmpty()
            if (groups.isNotEmpty()) {
                // grouped and filtered like the Linked screen ("Solo escalado" only in Mixto)
                GroupedProfileList(groups = groups, selected = selected, groupLabel = { stringResource(linkProfileGroupLabel(it)) }) { id ->
                    vm.setLinkProfile(id)
                }
            } else {
                ProfileList(state.linkProfiles, selected = selected, unavailable = state.linkUnsupported, mayDropFrames = state.linkMayDropFrames) { id ->
                    vm.changeLinkProfile(id)
                }
            }
        }
    }
}

@Composable
private fun PcRows(info: LinkProcessingInfo, state: PlayerUiState) {
    val profiles = state.linkProfiles
    val profile = profiles.firstOrNull { it.id == info.profileId }?.let { profileLabel(it) } ?: info.profileLabel ?: info.profileId
    InfoRow(stringResource(Res.string.player_fx_pc_row_profile), profile)
    val upscale = linkUpscaleText(info) ?: when (info.upscale) {
        is LinkUpscale.Anime4k -> stringResource(Res.string.player_fx_pc_anime4k_plain)
        LinkUpscale.None -> stringResource(Res.string.player_fx_pc_no_upscale)
    }
    InfoRow(stringResource(Res.string.player_fx_pc_row_upscale), upscale)
    InfoRow(stringResource(Res.string.player_fx_pc_row_fg), linkFrameGenText(info))
    linkEncoderText(info)?.let { InfoRow(stringResource(Res.string.player_fx_pc_row_codec), it) }
    info.leadSecs?.let { lead ->
        val secs = lead.roundToInt().coerceAtLeast(0)
        InfoRow(
            stringResource(Res.string.player_fx_pc_row_lead),
            stringResource(if (info.throttled) Res.string.player_fx_pc_throttled else Res.string.player_fx_pc_lead_value, secs),
        )
    }
    if (info.fallingBehind) WarningLine(stringResource(Res.string.player_processing_pc_falling_behind))
    linkDegradedText(info) { id -> profiles.firstOrNull { it.id == id }?.let { profileLabel(it) } ?: id }?.let { WarningLine(it) }
}

// c. Upscaling on the phone / Media3 ----------------------------------------------------------------------------------------------------

@Composable
private fun Media3Section(vm: PlayerViewModel, onClose: () -> Unit) {
    SectionCard(title = stringResource(Res.string.player_fx_media3_title)) {
        Text(stringResource(Res.string.player_fx_media3_body), style = MaterialTheme.typography.bodyMedium)
        if (vm.canSwitchToMpvNow()) {
            Button(onClick = { if (vm.switchToMpvByUser()) onClose() }, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(Res.string.quality_switch_to_mpv))
            }
        } else {
            InfoLine(stringResource(Res.string.player_fx_media3_cannot))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneQualitySection(vm: PlayerViewModel, session: PlayerSession, state: PlayerUiState, q: QualityUiState) {
    val req = q.requested
    SectionCard(title = stringResource(Res.string.player_fx_phone_section)) {
        // What runs now and why it differs from the choice
        Text(stringResource(Res.string.player_fx_now, levelText(q.effective)), style = MaterialTheme.typography.bodyLarge)
        q.reasons.forEach { r ->
            if (r in RESTRICTING_REASONS) WarningLine(stringResource(r.labelRes)) else InfoLine(stringResource(r.labelRes))
        }
        q.autoDowngrade?.let { d ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                Text(
                    stringResource(d.reason.labelRes) + " (" + levelText(d.from) + ")",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = session::undoAutoDowngrade) {
                    Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.quality_undo))
                }
            }
        }

        // Presets
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            q.presets.forEach { p ->
                FilterChip(
                    selected = req.preset == p.id,
                    onClick = { session.setQuality(p.id, req.tier) },
                    enabled = p.available,
                    label = { Text(stringResource(presetLabelRes(p.id))) },
                )
            }
        }
        presetHintRes(req.preset)?.let { InfoLine(stringResource(it)) }
        q.presets.filter { !it.available }.forEach { p ->
            p.unavailableReason?.let { InfoLine(stringResource(Res.string.player_fx_blocked_line, stringResource(presetLabelRes(p.id)), stringResource(it.labelRes))) }
        }

        // Tier
        LabelText(stringResource(Res.string.player_fx_tier))
        val tiered = req.preset in QualityPresets.TIERED
        Segmented(
            options = QualityTier.entries.map { SegmentedOption(it, stringResource(tierLabelRes(it)), enabled = tiered) },
            selected = req.tier,
            onSelect = { session.setQuality(req.preset, it) },
            fillWidth = true,
            small = true, // "Equilibrado" fits a phone's third of the width
        )
        InfoLine(stringResource(Res.string.player_fx_tier_hint))

        // Persisting: the player's changes are for this playback; "Usar como predeterminado" writes Settings
        val isDefault = QualityPresets.sanitize(state.settings.playerPreset) == req.preset && state.settings.playerQuality.tier == req.tier
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.player_fx_session_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.saveQualityAsDefault(req.preset, req.tier) }, enabled = !isDefault) {
                Text(stringResource(if (isDefault) Res.string.player_fx_is_default else Res.string.player_fx_save_default))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LabelText(stringResource(Res.string.player_fx_sharpen))
        Segmented(
            options = SharpenLevel.entries.map { SegmentedOption(it, stringResource(sharpenRes(it))) },
            selected = req.sharpen,
            onSelect = session::setSharpen,
            fillWidth = true,
        )
        SwitchRow(
            title = stringResource(Res.string.player_fx_deband),
            hint = stringResource(Res.string.player_fx_deband_hint),
            checked = req.deband.enabled,
            onChange = { session.setDeband(req.deband.copy(enabled = it)) },
        )
        SwitchRow(
            title = stringResource(Res.string.player_fx_smoothing_title),
            hint = if (req.smoothing && q.frameGenActive) stringResource(Res.string.quality_reason_frame_gen) else stringResource(Res.string.player_fx_smoothing_hint),
            checked = req.smoothing,
            onChange = session::setSmoothing,
        )
        SwitchRow(
            title = stringResource(Res.string.player_fx_auto),
            hint = stringResource(Res.string.player_fx_auto_hint),
            checked = req.autoAdjust,
            onChange = session::setAutoAdjust,
        )
    }
}

private fun sharpenRes(level: SharpenLevel): StringResource = when (level) {
    SharpenLevel.OFF -> Res.string.player_fx_sharpen_off
    SharpenLevel.LOW -> Res.string.player_fx_sharpen_low
    SharpenLevel.MEDIUM -> Res.string.player_fx_sharpen_medium
    SharpenLevel.HIGH -> Res.string.player_fx_sharpen_high
}

// d. Phone frame generation: PhoneFrameGen.Section (androidMain; its texts are :core:framegen's Android resources) ---------------------

internal fun fpsText(fps: Double): String =
    if (fps % 1.0 == 0.0) fps.toInt().toString() else String.format(Locale.ROOT, "%.1f", fps)

// e. Picture --------------------------------------------------------------------------------------------------------------------------

@Composable
private fun PictureSection(session: PlayerSession, q: QualityUiState) {
    val req = q.requested
    SectionCard(title = stringResource(Res.string.player_fx_image)) {
        LabelText(stringResource(Res.string.player_fx_aspect))
        Segmented(
            options = AspectMode.entries.map { SegmentedOption(it, stringResource(aspectRes(it))) },
            selected = req.aspect,
            onSelect = { session.setAspect(it, req.zoomPercent) },
            small = true,
        )
        SliderRow(
            label = stringResource(Res.string.player_fx_zoom),
            value = req.zoomPercent,
            range = app.nijiyome.player.QualityRequest.MIN_ZOOM..app.nijiyome.player.QualityRequest.MAX_ZOOM,
            step = 5,
            valueText = stringResource(Res.string.player_fx_percent, req.zoomPercent),
            onChange = { session.setAspect(req.aspect, it) },
        )
        val img = req.image
        listOf(
            Triple(Res.string.player_fx_brightness, img.brightness) { v: Int -> img.copy(brightness = v) },
            Triple(Res.string.player_fx_contrast, img.contrast) { v: Int -> img.copy(contrast = v) },
            Triple(Res.string.player_fx_saturation, img.saturation) { v: Int -> img.copy(saturation = v) },
            Triple(Res.string.player_fx_gamma, img.gamma) { v: Int -> img.copy(gamma = v) },
            Triple(Res.string.player_fx_hue, img.hue) { v: Int -> img.copy(hue = v) },
        ).forEach { (label, value, change) ->
            SliderRow(
                label = stringResource(label),
                value = value,
                range = ImageAdjust.MIN..ImageAdjust.MAX,
                step = 1,
                valueText = if (value > 0) "+$value" else "$value",
                onChange = { session.setImage(change(it)) },
            )
        }
        if (!img.isNeutral || req.zoomPercent != 100 || req.aspect != AspectMode.FIT) {
            TextButton(onClick = { session.setImage(ImageAdjust()); session.setAspect(AspectMode.FIT, 100) }) {
                Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.player_fx_reset))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LabelText(stringResource(Res.string.player_fx_decoder))
        Segmented(
            options = DecoderMode.entries.map { SegmentedOption(it, stringResource(decoderRes(it))) },
            selected = req.decoder,
            onSelect = session::setDecoder,
            fillWidth = true,
        )
        decoderInUseLabel(q.decoderInUse)?.let { InfoLine(stringResource(Res.string.player_fx_decoder_in_use, it)) }
        InfoLine(stringResource(Res.string.player_fx_decoder_hint))
    }
}

private fun aspectRes(mode: AspectMode): StringResource = when (mode) {
    AspectMode.FIT -> Res.string.player_fx_aspect_fit
    AspectMode.FILL -> Res.string.player_fx_aspect_fill
    AspectMode.STRETCH -> Res.string.player_fx_aspect_stretch
    AspectMode.RATIO_16_9 -> Res.string.player_fx_aspect_16_9
    AspectMode.RATIO_4_3 -> Res.string.player_fx_aspect_4_3
}

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

@Composable
private fun Note(text: String) {
    SectionCard { InfoLine(text) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
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

/** An integer slider ([step] apart) applied while dragging: mpv takes these properties live and cheaply. */
@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, step: Int, valueText: String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val snapped = ((v / step).roundToInt() * step).coerceIn(range.first, range.last)
                if (snapped != value) onChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics { contentDescription = "$label $valueText" },
        )
    }
}
