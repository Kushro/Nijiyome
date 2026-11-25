// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.link.GroupedProfileList
import app.nijiyome.feature.link.ProfileList
import app.nijiyome.feature.link.linkDegradedText
import app.nijiyome.feature.link.linkEncoderText
import app.nijiyome.feature.link.linkFrameGenText
import app.nijiyome.feature.link.linkUpscaleText
import app.nijiyome.feature.link.profileLabel
import app.nijiyome.feature.player.EnhancementChip
import app.nijiyome.feature.player.InfoLine
import app.nijiyome.feature.player.PhoneFrameGen
import app.nijiyome.feature.player.PlayerUiState
import app.nijiyome.feature.player.PlayerViewModel
import app.nijiyome.feature.player.ProcessingBlock
import app.nijiyome.feature.player.ProcessingUiState
import app.nijiyome.feature.player.RESTRICTING_REASONS
import app.nijiyome.feature.player.WarningLine
import app.nijiyome.feature.player.chipLabel
import app.nijiyome.feature.player.labelRes
import app.nijiyome.feature.player.levelText
import app.nijiyome.feature.player.linkProfileGroupLabel
import app.nijiyome.feature.player.phoneFrameGenReason
import app.nijiyome.feature.player.playerCapabilities
import app.nijiyome.feature.player.presetHintRes
import app.nijiyome.feature.player.presetLabelRes
import app.nijiyome.feature.player.processingBlockText
import app.nijiyome.feature.player.processingLocationHint
import app.nijiyome.feature.player.rememberChipWords
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.feature.player.tierLabelRes
import app.nijiyome.model.LinkProcessingInfo
import app.nijiyome.model.LinkUpscale
import app.nijiyome.model.ProcessingLocation
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.SharpenLevel
import app.nijiyome.player.PresetOption
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.QualityUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Sparkles redesign (lane S6): the "Mejoras" sheet (artifact `shMejoras`). Same actions as the 0.4.0 panel (EnhancementPanel.kt keeps
// its entry point): every change applies to the running playback; only "Usar como predeterminado" writes Settings. The picture
// controls moved to the "Imagen" sheet (PictureSheet.kt).

/**
 * The "Mejoras" sheet: statistics overlay switch, "Procesar en" (phone, PC, mixed) with why an option cannot be chosen, what the PC
 * does and its profiles, upscaling on this device grouped by family (mode, level, sharpening, deband, smoothing, automatic
 * adjustments) or "needs MPV" on Media3, and frame generation.
 */
@Composable
internal fun EnhancementsSheetContent(
    vm: PlayerViewModel,
    session: PlayerSession,
    state: PlayerUiState,
    quality: QualityUiState,
    processing: ProcessingUiState,
    frameGen: PhoneFrameGen?,
    statsVisible: Boolean,
    onStatsChange: (Boolean) -> Unit,
    onOpenFgSettings: () -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onClose: () -> Unit,
) {
    val location = processing.location
    val mpv = quality.available
    ToggleRow(
        title = stringResource(Res.string.player_fx_stats),
        hint = stringResource(Res.string.player_fx_stats_hint),
        checked = statsVisible,
        onChange = onStatsChange,
        icon = Icons.Rounded.Insights,
    )
    if (ProcessingSection(vm, processing, onOpenFgSettings, onOpenSettings)) SheetDivider()
    if (location != ProcessingLocation.PHONE) {
        PcSection(vm, state, processing)
        SheetDivider()
    }
    when {
        location == ProcessingLocation.PHONE && !mpv -> Media3Section(vm, onClose)
        location == ProcessingLocation.PHONE -> UpscalingSection(vm, session, state, quality)
        location == ProcessingLocation.MIXED -> SheetSection(stringResource(Res.string.rd_s6_upscaling)) {
            StateNote(stringResource(Res.string.player_fx_mixed_note))
        }
        else -> SheetSection(stringResource(Res.string.rd_s6_upscaling)) {
            StateNote(stringResource(Res.string.player_processing_local_off))
        }
    }
    // Frame generation only exists on libmpv; strict PC suspends it (the PC already processes everything).
    if (frameGen != null && mpv && location != ProcessingLocation.PC) {
        SheetDivider()
        frameGen.Section(onOpenFgSettings)
    } else if (frameGen == null && !playerCapabilities.touchFirst) {
        SheetDivider()
        DesktopFrameGenSection(onOpenSettings)
    }
}

// ---- Procesar en ------------------------------------------------------------------------------------------------------------------------

/** "Procesar en". Returns whether it drew anything (a desktop with nothing else to choose shows nothing). */
@Composable
private fun ProcessingSection(
    vm: PlayerViewModel,
    processing: ProcessingUiState,
    onOpenFgSettings: () -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
): Boolean {
    val touch = playerCapabilities.touchFirst
    val others = ProcessingLocation.entries.filter { it != processing.location }
    // The desktop is the PC: the choice only means something when another place can actually take the video.
    if (!touch && processing.switchingTo == null && others.none { processing.option(it).available }) return false
    val selected = processing.switchingTo ?: processing.location
    SheetSection(stringResource(Res.string.player_processing_title)) {
        Segmented(
            options = ProcessingLocation.entries.map { loc ->
                val option = processing.option(loc)
                SegmentedOption(
                    value = loc,
                    label = stringResource(shortLocation(loc, touch)),
                    enabled = processing.switchingTo == null && (option.available || loc == processing.location),
                    icon = when (loc) {
                        ProcessingLocation.PHONE -> if (touch) Icons.Rounded.Smartphone else Icons.Rounded.Computer
                        ProcessingLocation.PC -> Icons.Rounded.Computer
                        ProcessingLocation.MIXED -> Icons.Rounded.Devices
                    },
                    description = stringResource(processingLocationHint(loc)),
                )
            },
            selected = selected,
            onSelect = vm::setProcessingLocation,
            fillWidth = true,
        )
        Hint(stringResource(processingLocationHint(selected)))
        processing.switchingTo?.let { to ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(chipLabel(EnhancementChip.Switching(to), rememberChipWords()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = vm::cancelProcessingSwitch) { Text(stringResource(Res.string.player_fx_switch_cancel)) }
            }
        }
        // Why the other places cannot be chosen now, one note per reason, with the way out when there is one.
        val blocked = others.mapNotNull { loc ->
            val option = processing.option(loc)
            val block = option.block ?: return@mapNotNull null
            val fg = option.detail?.takeIf { block == ProcessingBlock.PHONE_FG_UNAVAILABLE }?.let { phoneFrameGenReason(it) }
            loc to Blocked(block, stringResource(processingBlockText(block)) + (fg?.let { " " + it.text } ?: ""), fg?.missingShaders == true)
        }
        blocked.groupBy { it.second }.forEach { (why, locs) ->
            val names = locs.map { stringResource(shortLocation(it.first, touch)) }.joinToString(", ")
            val text = stringResource(Res.string.player_fx_blocked_line, names, why.text)
            when {
                why.block == ProcessingBlock.NOT_PAIRED || why.block == ProcessingBlock.PC_OFFLINE -> StateNote(
                    text,
                    tone = if (why.block == ProcessingBlock.PC_OFFLINE) NoteTone.WARN else NoteTone.INFO,
                    action = stringResource(Res.string.rd_s6_open_link_settings),
                    onAction = { onOpenSettings(SettingsSection.LINK) },
                )
                why.missingShaders -> StateNote(text, NoteTone.WARN, stringResource(Res.string.player_fx_fg_import), onOpenFgSettings)
                else -> StateNote(text)
            }
        }
    }
    return true
}

/** Why a place cannot take the video, as the sheet says it ([text] includes the frame generation reason). */
private data class Blocked(val block: ProcessingBlock, val text: String, val missingShaders: Boolean)

private fun shortLocation(location: ProcessingLocation, touch: Boolean): StringResource = when (location) {
    ProcessingLocation.PHONE -> if (touch) Res.string.player_fx_where_phone else Res.string.rd_s6_where_this_device
    ProcessingLocation.PC -> Res.string.player_fx_where_pc
    ProcessingLocation.MIXED -> Res.string.player_fx_where_mixed
}

// ---- what the PC does --------------------------------------------------------------------------------------------------------------------

@Composable
private fun PcSection(vm: PlayerViewModel, state: PlayerUiState, processing: ProcessingUiState) {
    val info = processing.pc
    val name = info?.pcName ?: processing.pcName
    SheetSection(name?.let { stringResource(Res.string.player_fx_pc_card_named, it) } ?: stringResource(Res.string.player_fx_pc_card)) {
        if (info == null) StateNote(stringResource(Res.string.player_fx_pc_waiting)) else PcRows(info, state)
    }
    val groups = processing.profileGroups
    if (groups.isNotEmpty() || state.linkProfiles.isNotEmpty()) {
        SheetSection(stringResource(Res.string.player_link_profile)) {
            Hint(stringResource(Res.string.player_link_profile_hint))
            if (processing.profileChanging || state.linkProfileChanging) {
                Row(Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
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
    if (info.fallingBehind) StateNote(stringResource(Res.string.player_processing_pc_falling_behind), NoteTone.WARN)
    linkDegradedText(info) { id -> profiles.firstOrNull { it.id == id }?.let { profileLabel(it) } ?: id }?.let { StateNote(it, NoteTone.WARN) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

// ---- upscaling on this device ---------------------------------------------------------------------------------------------------------

@Composable
private fun Media3Section(vm: PlayerViewModel, onClose: () -> Unit) {
    SheetSection(stringResource(Res.string.player_fx_media3_title)) {
        Text(stringResource(Res.string.player_fx_media3_body), style = MaterialTheme.typography.bodyMedium)
        if (vm.canSwitchToMpvNow()) {
            Button(onClick = { if (vm.switchToMpvByUser()) onClose() }) { Text(stringResource(Res.string.quality_switch_to_mpv)) }
        } else {
            StateNote(stringResource(Res.string.player_fx_media3_cannot))
        }
    }
}

@Composable
private fun UpscalingSection(vm: PlayerViewModel, session: PlayerSession, state: PlayerUiState, q: QualityUiState) {
    val req = q.requested
    val touch = playerCapabilities.touchFirst
    val keys = playerCapabilities.desktopShortcuts
    // The family's mode used last in this sheet, so going back to Anime4K restores it.
    var lastMode by rememberSaveable { mutableStateOf<String?>(null) }
    val families = familyChoices(q.presets)
    val current = EnhancementFamily.of(req.preset)
    val currentChoice = families.firstOrNull { it.family == current }
    SheetSection(stringResource(if (touch) Res.string.rd_s6_upscaling_phone else Res.string.rd_s6_upscaling)) {
        Text(stringResource(Res.string.player_fx_now, levelText(q.effective)), style = MaterialTheme.typography.bodyLarge)
        q.reasons.forEach { r -> if (r in RESTRICTING_REASONS) WarningLine(stringResource(r.labelRes)) else InfoLine(stringResource(r.labelRes)) }
        q.autoDowngrade?.let { d ->
            StateNote(
                stringResource(d.reason.labelRes) + " (" + levelText(d.from) + ")",
                NoteTone.WARN,
                action = stringResource(Res.string.quality_undo),
                onAction = session::undoAutoDowngrade,
            )
        }

        val tiles = families.map { choice ->
            val single = choice.presets.singleOrNull()?.id
            OptionTile(
                value = choice.family,
                title = stringResource(familyTitle(choice.family)),
                description = choice.reason?.let { stringResource(it.labelRes) } ?: stringResource(familyHint(choice.family)),
                enabled = choice.available || choice.family == current,
                badge = single?.takeIf { keys }?.let { ctrlShortcut(it) }?.let { stringResource(Res.string.rd_s6_ctrl_key, it) },
            )
        } + desktopOnlyTiles(families, touch)
        Caption(stringResource(Res.string.rd_s6_family))
        OptionTiles(tiles, selected = current, onSelect = { family ->
            val choice = families.firstOrNull { it.family == family } ?: return@OptionTiles
            presetForFamily(choice, req.preset, lastMode)?.let { session.setQuality(it, req.tier) }
        })

        if (currentChoice?.hasModes == true) {
            Caption(stringResource(Res.string.rd_s6_mode))
            ChoiceChips(
                choices = currentChoice.presets.map { p -> modeChoice(p, keys) },
                selected = req.preset,
                onSelect = { id -> lastMode = id; session.setQuality(id, req.tier) },
            )
        }
        presetHintRes(req.preset)?.takeIf { currentChoice?.hasModes == true }?.let { Hint(stringResource(it)) }

        if (req.preset in QualityPresets.TIERED) {
            Caption(stringResource(Res.string.rd_s6_tier))
            Segmented(
                options = QualityTier.entries.map { SegmentedOption(it, stringResource(tierLabelRes(it))) },
                selected = req.tier,
                onSelect = { session.setQuality(req.preset, it) },
                fillWidth = true,
                small = true, // "Equilibrado" fits a phone's third of the width
            )
            Hint(stringResource(Res.string.player_fx_tier_hint))
        }

        // The player's changes are for this playback; "Usar como predeterminado" writes Settings.
        val isDefault = QualityPresets.sanitize(state.settings.playerPreset) == req.preset && state.settings.playerQuality.tier == req.tier
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Hint(stringResource(Res.string.player_fx_session_only), Modifier.weight(1f))
            TextButton(onClick = { vm.saveQualityAsDefault(req.preset, req.tier) }, enabled = !isDefault) {
                Text(stringResource(if (isDefault) Res.string.player_fx_is_default else Res.string.player_fx_save_default))
            }
        }

        Caption(stringResource(Res.string.player_fx_sharpen))
        Segmented(
            options = SharpenLevel.entries.map { SegmentedOption(it, stringResource(sharpenRes(it))) },
            selected = req.sharpen,
            onSelect = session::setSharpen,
            fillWidth = true,
            small = true,
        )
        ToggleRow(
            title = stringResource(Res.string.player_fx_deband),
            hint = stringResource(Res.string.player_fx_deband_hint),
            checked = req.deband.enabled,
            onChange = { session.setDeband(req.deband.copy(enabled = it)) },
        )
        ToggleRow(
            title = stringResource(Res.string.player_fx_smoothing_title),
            hint = if (req.smoothing && q.frameGenActive) stringResource(Res.string.quality_reason_frame_gen) else stringResource(Res.string.player_fx_smoothing_hint),
            checked = req.smoothing,
            onChange = session::setSmoothing,
        )
        ToggleRow(
            title = stringResource(Res.string.player_fx_auto),
            hint = stringResource(Res.string.player_fx_auto_hint),
            checked = req.autoAdjust,
            onChange = session::setAutoAdjust,
        )
    }
}

@Composable
private fun modeChoice(p: PresetOption, keys: Boolean): Choice<String> = Choice(
    value = p.id,
    label = modeLabel(p.id).ifEmpty { stringResource(presetLabelRes(p.id)) },
    enabled = p.available,
    badge = ctrlShortcut(p.id)?.takeIf { keys }?.let { stringResource(Res.string.rd_s6_ctrl_key, it) },
    reason = p.unavailableReason?.let { stringResource(it.labelRes) },
)

/** NVScaler's shader ships only with the desktop build: on a phone it is shown, disabled, so its absence is explained. */
@Composable
private fun desktopOnlyTiles(families: List<FamilyChoice>, touch: Boolean): List<OptionTile<EnhancementFamily>> =
    if (touch && families.none { it.family == EnhancementFamily.NVSCALER }) {
        listOf(
            OptionTile(
                value = EnhancementFamily.NVSCALER,
                title = stringResource(Res.string.player_fx_preset_nvscaler),
                description = stringResource(Res.string.rd_s6_desktop_only),
                enabled = false,
            ),
        )
    } else {
        emptyList()
    }

private fun familyTitle(family: EnhancementFamily): StringResource = when (family) {
    EnhancementFamily.NONE -> Res.string.player_fx_preset_off
    EnhancementFamily.ANIME4K -> Res.string.rd_s6_family_anime4k
    EnhancementFamily.FSR -> Res.string.player_fx_preset_fsr
    EnhancementFamily.FIDELITY -> Res.string.player_fx_preset_fidelity
    EnhancementFamily.CAS -> Res.string.player_fx_preset_cas
    EnhancementFamily.NVSCALER -> Res.string.player_fx_preset_nvscaler
    EnhancementFamily.ARTCNN -> Res.string.player_fx_preset_artcnn
}

private fun familyHint(family: EnhancementFamily): StringResource = when (family) {
    EnhancementFamily.NONE -> Res.string.player_fx_hint_off
    EnhancementFamily.ANIME4K -> Res.string.rd_s6_family_anime4k_hint
    EnhancementFamily.FSR -> Res.string.player_fx_hint_fsr
    EnhancementFamily.FIDELITY -> Res.string.player_fx_hint_fidelity
    EnhancementFamily.CAS -> Res.string.player_fx_hint_cas
    EnhancementFamily.NVSCALER -> Res.string.player_fx_hint_nvscaler
    EnhancementFamily.ARTCNN -> Res.string.rd_s6_family_artcnn_hint
}

internal fun sharpenRes(level: SharpenLevel): StringResource = when (level) {
    SharpenLevel.OFF -> Res.string.player_fx_sharpen_off
    SharpenLevel.LOW -> Res.string.player_fx_sharpen_low
    SharpenLevel.MEDIUM -> Res.string.player_fx_sharpen_medium
    SharpenLevel.HIGH -> Res.string.player_fx_sharpen_high
}

// ---- frame generation on desktop ------------------------------------------------------------------------------------------------------

/** Desktop frame generation is lsfg-vk in mpv's own window: set up in Settings, the sheet says so and links there. */
@Composable
private fun DesktopFrameGenSection(onOpenSettings: (SettingsSection) -> Unit) {
    SheetSection(stringResource(Res.string.rd_s6_fg_title)) {
        Hint(stringResource(Res.string.rd_s6_fg_desktop_hint))
        TextButton(onClick = { onOpenSettings(SettingsSection.PLAYER) }) { Text(stringResource(Res.string.rd_s6_fg_desktop_settings)) }
    }
}
