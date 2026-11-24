// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link

import app.nijiyome.feature.link.resources.Res
import app.nijiyome.feature.link.resources.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import app.nijiyome.model.LinkFrameGen
import app.nijiyome.model.LinkProcessingInfo
import app.nijiyome.model.LinkProfileGroup
import app.nijiyome.model.LinkProfileGroupKind
import app.nijiyome.model.LinkUpscale
import app.nijiyome.model.ProfileSupport
import java.util.Locale

// 0.4.0 (worker C): the words of "what the PC does" and the grouped profile list, for the player (worker D draws them).

/** "Anime4K A → 1920×1080" / "Sin escalado · 1920×1080"; null without an output size. */
@Composable
fun linkUpscaleText(info: LinkProcessingInfo): String? {
    val w = info.outputWidth ?: return null
    val h = info.outputHeight ?: return null
    return when (val u = info.upscale) {
        LinkUpscale.None -> stringResource(Res.string.linked_pc_upscale_none, w, h)
        is LinkUpscale.Anime4k -> u.mode?.let { mode ->
            stringResource(Res.string.linked_pc_upscale_anime4k_mode, mode.uppercase(Locale.ROOT) + (u.variant?.let { " (${it.uppercase(Locale.ROOT)})" } ?: ""), w, h)
        } ?: stringResource(Res.string.linked_pc_upscale_anime4k, w, h)
    }
}

/** "LSFG ×5 → 119.88 fps", "LSFG automático: ×5 → 119.88 fps", "Sin generación de cuadros"... */
@Composable
fun linkFrameGenText(info: LinkProcessingInfo): String = when (val fg = info.frameGen) {
    LinkFrameGen.None -> stringResource(Res.string.linked_pc_fg_none)
    LinkFrameGen.FrcAmf -> stringResource(Res.string.linked_pc_fg_frc)
    is LinkFrameGen.Lsfg -> {
        val n = fg.multiplier
        val fps = fg.outFps
        when {
            n != null && fps != null -> stringResource(Res.string.linked_pc_fg_fixed_fps, n, fpsText(fps))
            n != null -> stringResource(Res.string.linked_pc_fg_fixed, n)
            else -> "LSFG" // a profile the phone does not know, from an older PC
        }
    }
    is LinkFrameGen.LsfgAuto -> {
        val n = fg.multiplier
        val fps = fg.outFps
        if (n != null && fps != null) stringResource(Res.string.linked_pc_fg_auto_chosen, n, fpsText(fps))
        else stringResource(Res.string.linked_pc_fg_auto, fg.targetFps ?: 120)
    }
}

/** "h264_vaapi · 11 Mb/s"; null when the PC did not say (older PC). */
@Composable
fun linkEncoderText(info: LinkProcessingInfo): String? {
    val encoder = info.encoder ?: return null
    val kbps = info.videoKbps ?: return encoder
    return stringResource(Res.string.linked_pc_encoder, encoder, String.format(Locale.ROOT, "%.0f", kbps / 1000.0))
}

/** "Tu PC bajó el perfil (era Anime4K a 1440p)" when the PC degraded the session. */
@Composable
fun linkDegradedText(info: LinkProcessingInfo, labelOf: @Composable (String) -> String): String? =
    info.degradedFrom?.let { stringResource(Res.string.linked_pc_degraded, labelOf(it)) }

private fun fpsText(fps: Double): String = if (fps % 1.0 == 0.0) fps.toInt().toString() else String.format(Locale.ROOT, "%.2f", fps).trimEnd('0').trimEnd('.')

/**
 * 0.4.0: the in-player profile list in groups ([app.nijiyome.model.groupedLinkProfiles]): a header per group
 * ([groupLabel]) over the same radio rows as [ProfileList] (disabled rows with the reason, the "may drop frames" note).
 */
@Composable
fun GroupedProfileList(
    groups: List<LinkProfileGroup>,
    selected: String,
    groupLabel: @Composable (LinkProfileGroupKind) -> String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        groups.forEach { group ->
            Text(
                groupLabel(group.kind),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
            )
            ProfileList(
                profiles = group.rows.map { it.profile },
                selected = selected,
                unavailable = group.rows.filter { it.support != ProfileSupport.Supported }.associate { it.profile.id to it.support },
                mayDropFrames = group.rows.filter { it.mayDropFrames }.mapTo(HashSet()) { it.profile.id },
                onSelect = onSelect,
            )
        }
    }
}
