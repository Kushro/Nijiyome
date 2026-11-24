// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.SIDE_SHEET_MIN_WIDTH_DP
import app.nijiyome.model.ProcessingLocation
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import app.nijiyome.player.QualityLevel
import app.nijiyome.player.QualityReason
import app.nijiyome.ui.platform.windowWidthDp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Milestone 3: the words, the status chip and the sheet container of the "Mejoras" panel (EnhancementPanel.kt), shared
// so the subtitle style sheet (Settings) is common too.

// ---- words ---------------------------------------------------------------------------------------------------------------------------

internal fun presetLabelRes(id: String): StringResource = when (id) {
    QualityPresets.ANIME4K_A -> Res.string.player_fx_preset_anime4k_a
    QualityPresets.ANIME4K_AA -> Res.string.player_fx_preset_anime4k_aa
    QualityPresets.ANIME4K_B -> Res.string.player_fx_preset_anime4k_b
    QualityPresets.ANIME4K_C -> Res.string.player_fx_preset_anime4k_c
    QualityPresets.ANIME4K_BB -> Res.string.player_fx_preset_anime4k_bb
    QualityPresets.ANIME4K_CA -> Res.string.player_fx_preset_anime4k_ca
    QualityPresets.FSR -> Res.string.player_fx_preset_fsr
    QualityPresets.FIDELITY -> Res.string.player_fx_preset_fidelity
    QualityPresets.CAS -> Res.string.player_fx_preset_cas
    QualityPresets.ARTCNN -> Res.string.player_fx_preset_artcnn
    QualityPresets.NVSCALER -> Res.string.player_fx_preset_nvscaler
    else -> Res.string.player_fx_preset_off
}

internal fun presetHintRes(id: String): StringResource? = when (id) {
    QualityPresets.OFF -> Res.string.player_fx_hint_off
    QualityPresets.ANIME4K_A -> Res.string.player_fx_hint_anime4k_a
    QualityPresets.ANIME4K_AA -> Res.string.player_fx_hint_anime4k_aa
    QualityPresets.ANIME4K_B -> Res.string.player_fx_hint_anime4k_b
    QualityPresets.ANIME4K_C -> Res.string.player_fx_hint_anime4k_c
    QualityPresets.ANIME4K_BB -> Res.string.player_fx_hint_anime4k_bb
    QualityPresets.ANIME4K_CA -> Res.string.player_fx_hint_anime4k_ca
    QualityPresets.FSR -> Res.string.player_fx_hint_fsr
    QualityPresets.FIDELITY -> Res.string.player_fx_hint_fidelity
    QualityPresets.CAS -> Res.string.player_fx_hint_cas
    QualityPresets.NVSCALER -> Res.string.player_fx_hint_nvscaler
    else -> null
}

/**
 * Short es/en text of a [QualityReason] for the UI and the notices. Milestone 4: moved with its strings from :core:player's Android
 * resources (QualityReasonLabels.kt there), so the shared player UI reads them on both targets.
 */
internal val QualityReason.labelRes: StringResource
    get() = when (this) {
        QualityReason.RENDERER_UNSUPPORTED -> Res.string.quality_reason_renderer
        QualityReason.SHADER_ERROR -> Res.string.quality_reason_shader_error
        QualityReason.RES_8K -> Res.string.quality_reason_8k
        QualityReason.RES_4K -> Res.string.quality_reason_4k
        QualityReason.REMOTE_PROCESSING -> Res.string.quality_reason_remote
        QualityReason.FRAME_GEN -> Res.string.quality_reason_frame_gen
        QualityReason.BATTERY_LOW -> Res.string.quality_reason_battery
        QualityReason.THERMAL -> Res.string.quality_reason_thermal
        QualityReason.FRAME_DROPS -> Res.string.quality_reason_drops
        QualityReason.PIP -> Res.string.quality_reason_pip
        QualityReason.VIEWPORT -> Res.string.quality_reason_viewport
        QualityReason.METADATA_PENDING -> Res.string.quality_reason_pending
    }

internal fun tierLabelRes(tier: QualityTier): StringResource = when (tier) {
    QualityTier.FAST -> Res.string.player_fx_tier_fast
    QualityTier.BALANCED -> Res.string.player_fx_tier_balanced
    QualityTier.HQ -> Res.string.player_fx_tier_hq
}

/** [ChipWords] from this module's resources (es / en), read once per composition of [rememberChipWords]. */
private data class ResourceChipWords(
    private val presets: Map<String, String>,
    private val presetOff: String,
    private val tiers: Map<QualityTier, String>,
    override val noEnhancements: String,
    override val restricted: String,
    override val paused: String,
    private val frameGenTemplate: String,
    override val deband: String,
    override val sharpen: String,
    override val smoothing: String,
    override val media3: String,
    override val pc: String,
    override val pcNothing: String,
    private val pcAnime4kTemplate: String,
    private val pcAnime4kPlain: String,
    private val pcLsfgTemplate: String,
    private val pcLsfgPlain: String,
    override val pcLsfgAuto: String,
    override val pcFrc: String,
    override val mixedPcScales: String,
    private val mixedFgTemplate: String,
    private val mixedFgPlain: String,
    private val switchingTo: Map<ProcessingLocation, String>,
) : ChipWords {
    override fun preset(id: String) = presets[id] ?: presetOff
    override fun tier(tier: QualityTier) = tiers.getValue(tier)
    override fun frameGen(multiplier: Int) = frameGenTemplate.withArg(multiplier)
    override fun pcAnime4k(height: Int?) = height?.let { pcAnime4kTemplate.withArg(it) } ?: pcAnime4kPlain
    override fun pcLsfg(multiplier: Int?) = multiplier?.let { pcLsfgTemplate.withArg(it) } ?: pcLsfgPlain
    override fun mixedPhoneFg(multiplier: Int?) = multiplier?.let { mixedFgTemplate.withArg(it) } ?: mixedFgPlain
    override fun switching(to: ProcessingLocation) = switchingTo.getValue(to)
}

// The formatted words are read with this mark as their argument and filled in when the chip asks for them (ChipWords is not
// composable); the mark is a private-use character that no translation contains.
private const val ARG_MARK = "\uE000"

private fun String.withArg(value: Int): String = replace(ARG_MARK, value.toString())

/** Every preset id [presetLabelRes] names (any other id reads as "off", like the resource helper). */
private val PRESET_IDS = listOf(
    QualityPresets.ANIME4K_A, QualityPresets.ANIME4K_AA, QualityPresets.ANIME4K_B, QualityPresets.ANIME4K_C, QualityPresets.ANIME4K_BB,
    QualityPresets.ANIME4K_CA, QualityPresets.FSR, QualityPresets.FIDELITY, QualityPresets.CAS, QualityPresets.ARTCNN,
    QualityPresets.NVSCALER,
)

@Composable
internal fun rememberChipWords(): ChipWords = ResourceChipWords(
    presets = PRESET_IDS.associateWith { stringResource(presetLabelRes(it)) },
    presetOff = stringResource(Res.string.player_fx_preset_off),
    tiers = QualityTier.entries.associateWith { stringResource(tierLabelRes(it)) },
    noEnhancements = stringResource(Res.string.player_fx_none),
    restricted = stringResource(Res.string.player_fx_restricted),
    paused = stringResource(Res.string.player_fx_paused),
    frameGenTemplate = stringResource(Res.string.player_fx_fg, ARG_MARK),
    deband = stringResource(Res.string.player_fx_deband),
    sharpen = stringResource(Res.string.player_fx_sharpen),
    smoothing = stringResource(Res.string.player_fx_smoothing),
    media3 = stringResource(Res.string.player_fx_media3),
    pc = stringResource(Res.string.player_fx_pc),
    pcNothing = stringResource(Res.string.player_fx_pc_nothing),
    pcAnime4kTemplate = stringResource(Res.string.player_fx_pc_anime4k, ARG_MARK),
    pcAnime4kPlain = stringResource(Res.string.player_fx_pc_anime4k_plain),
    pcLsfgTemplate = stringResource(Res.string.player_fx_pc_lsfg, ARG_MARK),
    pcLsfgPlain = stringResource(Res.string.player_fx_pc_lsfg_plain),
    pcLsfgAuto = stringResource(Res.string.player_fx_pc_lsfg_auto),
    pcFrc = stringResource(Res.string.player_fx_pc_frc),
    mixedPcScales = stringResource(Res.string.player_fx_mixed_pc),
    mixedFgTemplate = stringResource(Res.string.player_fx_mixed_fg, ARG_MARK),
    mixedFgPlain = stringResource(Res.string.player_fx_mixed_fg_plain),
    switchingTo = mapOf(
        ProcessingLocation.PHONE to stringResource(Res.string.player_fx_switching_phone),
        ProcessingLocation.PC to stringResource(Res.string.player_fx_switching_pc),
        ProcessingLocation.MIXED to stringResource(Res.string.player_fx_switching_mixed),
    ),
)

/** "Anime4K A+A · HQ" of a level (no FG, no status), or "Sin escalado". */
@Composable
internal fun levelText(level: QualityLevel): String =
    if (level.off) stringResource(Res.string.player_fx_preset_off)
    else listOfNotNull(stringResource(presetLabelRes(level.preset)), level.tier?.let { stringResource(tierLabelRes(it)) }).joinToString(CHIP_SEPARATOR)

// ---- status chip -----------------------------------------------------------------------------------------------------------------------

/** The top bar's status chip: what runs and where; a tap opens the panel. */
@Composable
internal fun EnhancementStatusChip(chip: EnhancementChip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = chipLabel(chip, rememberChipWords())
    val tone = chipTone(chip)
    val scheme = MaterialTheme.colorScheme
    val on = scheme.onBackground
    val (container, content) = when (tone) {
        ChipTone.ACTIVE -> scheme.primaryContainer to scheme.onPrimaryContainer
        ChipTone.WARNING -> scheme.errorContainer to scheme.onErrorContainer
        ChipTone.NEUTRAL -> Color.Transparent to on
    }
    val description = stringResource(Res.string.player_fx_chip_desc, label)
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            when {
                chip is EnhancementChip.Switching -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = content)
                tone == ChipTone.WARNING -> Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp))
                chip is EnhancementChip.Pc -> Icon(Icons.Rounded.Computer, null, Modifier.size(18.dp))
                chip is EnhancementChip.Mixed -> Icon(Icons.Rounded.Devices, null, Modifier.size(18.dp))
                else -> Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
            }
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = container, labelColor = content, leadingIconContentColor = content),
        border = if (tone == ChipTone.NEUTRAL) AssistChipDefaults.assistChipBorder(enabled = true) else null,
        modifier = modifier.semantics { contentDescription = description },
    )
}

// ---- container ---------------------------------------------------------------------------------------------------------------------------

/**
 * Side sheet on wide windows (tablet, phone in landscape: 400 dp, the video stays visible beside it), bottom sheet on a phone in
 * portrait. [AdaptiveSheet] scrolls its content itself on the wide branch; the bottom sheet branch scrolls here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnhancementSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val wide = windowWidthDp() >= SIDE_SHEET_MIN_WIDTH_DP
    if (wide) {
        AdaptiveSheet(onDismissRequest = onDismiss, content = content)
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
            TrackModal()
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp), content = content)
        }
    }
}
