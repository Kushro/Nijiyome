// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.feature.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.feature.link.resources.Res
import app.nijiyome.feature.link.resources.*
import app.nijiyome.model.LinkProfile
import app.nijiyome.model.LinkProfileFallback
import app.nijiyome.model.ProfileSupport
import org.jetbrains.compose.resources.stringResource

// Sparkles S9 (LNK-06 "lista de 15 radios -> selector de dos ejes Escalado × Cuadros"): the PC's profiles read from their stable ids
// (ProfileName) as two axes. Profiles whose id this build does not know still appear in "Ver todos los perfiles" (the full radio list).

/** The frame-generation axis of a profile. */
sealed interface FrameAxis {
    data object None : FrameAxis
    data class Fixed(val multiplier: Int) : FrameAxis
    data class Auto(val fps: Int) : FrameAxis
}

data class ProfileAxes(val upscale: Int?, val frames: FrameAxis)

/** Pure helpers of the two-axis picker (JVM-tested). */
internal object ProfileGrid {
    fun axesOf(profile: LinkProfile): ProfileAxes? = ProfileName.parse(profile.id)?.let { n ->
        ProfileAxes(
            upscale = n.upscaleHeight,
            frames = when {
                n.multiplier != null -> FrameAxis.Fixed(n.multiplier)
                n.autoFps != null -> FrameAxis.Auto(n.autoFps)
                else -> FrameAxis.None
            },
        )
    }

    /** No upscaling first, then the heights from low to high. */
    fun upscaleOptions(profiles: List<LinkProfile>): List<Int?> =
        profiles.mapNotNull { axesOf(it) }.map { it.upscale }.distinct().sortedWith(nullsFirst(naturalOrder()))

    /** No frames first, then the fixed multipliers, then the automatic targets. */
    fun frameOptions(profiles: List<LinkProfile>): List<FrameAxis> =
        profiles.mapNotNull { axesOf(it)?.frames }.distinct().sortedWith(
            compareBy<FrameAxis> {
                when (it) {
                    FrameAxis.None -> 0
                    is FrameAxis.Fixed -> 1
                    is FrameAxis.Auto -> 2
                }
            }.thenBy {
                when (it) {
                    FrameAxis.None -> 0
                    is FrameAxis.Fixed -> it.multiplier
                    is FrameAxis.Auto -> it.fps
                }
            },
        )

    fun find(profiles: List<LinkProfile>, upscale: Int?, frames: FrameAxis): LinkProfile? =
        profiles.firstOrNull { axesOf(it) == ProfileAxes(upscale, frames) }

    /**
     * The profile for a press on [upscale]: the same frames when that pair exists and plays here, else the first playable one with
     * that upscale (fewest frames first). Null when no profile with [upscale] plays here.
     */
    fun pickUpscale(profiles: List<LinkProfile>, upscale: Int?, keepFrames: FrameAxis?, playable: (LinkProfile) -> Boolean): LinkProfile? {
        keepFrames?.let { f -> find(profiles, upscale, f)?.takeIf(playable)?.let { return it } }
        return frameOptions(profiles).firstNotNullOfOrNull { f -> find(profiles, upscale, f)?.takeIf(playable) }
    }
}

/**
 * Perfil de mejora: what the PC does with what plays here, as Escalado × Cuadros chips. A chip that cannot be used says why under its
 * row (BUG-132: the phone's decoder; profiles measured as not sustainable are not offered). Under it, the chosen profile with its
 * sustained state and output size, the "may drop frames" note (BUG-135), the fallback notice and the full list.
 */
@Composable
internal fun ProfileAxisPicker(
    profiles: List<LinkProfile>,
    selected: String,
    unavailable: Map<String, ProfileSupport>,
    mayDropFrames: Set<String>,
    fallback: LinkProfileFallback?,
    allProfiles: List<LinkProfile>,
    onSelect: (String) -> Unit,
) {
    val playable: (LinkProfile) -> Boolean = { it.id !in unavailable }
    val current = profiles.firstOrNull { it.id == selected }
    val axes = current?.let { ProfileGrid.axesOf(it) }
    val ups = ProfileGrid.upscaleOptions(profiles)
    val frames = ProfileGrid.frameOptions(profiles)
    var showAll by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ups.isNotEmpty() && frames.isNotEmpty()) {
            AxisRow(stringResource(Res.string.rd_s9_axis_upscale)) {
                ups.forEach { up ->
                    val pick = ProfileGrid.pickUpscale(profiles, up, axes?.frames, playable)
                    AxisChip(
                        label = upscaleLabel(up),
                        selected = axes != null && axes.upscale == up,
                        enabled = pick != null,
                        onClick = { pick?.let { onSelect(it.id) } },
                    )
                }
            }
            // Why an upscale is not offered (every profile with it is out of reach of this phone).
            ups.filter { up -> ProfileGrid.pickUpscale(profiles, up, null, playable) == null }.forEach { up ->
                val reason = profiles.firstOrNull { ProfileGrid.axesOf(it)?.upscale == up }?.let { p -> unavailable[p.id]?.let { profileSupportText(it) } }
                if (reason != null) Hint(stringResource(Res.string.rd_s9_axis_reason, upscaleLabel(up), reason))
            }
            AxisRow(stringResource(Res.string.rd_s9_axis_frames)) {
                frames.forEach { f ->
                    val target = axes?.let { ProfileGrid.find(profiles, it.upscale, f) }
                    AxisChip(
                        label = frameLabel(f),
                        selected = axes != null && axes.frames == f,
                        enabled = target != null && playable(target),
                        onClick = { target?.let { onSelect(it.id) } },
                    )
                }
            }
        }
        if (current != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(profileLabel(current), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                Pill(sustainText(current), tone = if (current.measuredSustain == "true") PillTone.OK else PillTone.NEUTRAL)
            }
            Text(
                stringResource(Res.string.rd_s9_profile_output, current.outputWidth, current.outputHeight),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current.id in mayDropFrames) Hint(stringResource(Res.string.linked_profile_may_drop_frames))
        }
        if (fallback != null) {
            val from = allProfiles.firstOrNull { it.id == fallback.from }
            val to = allProfiles.firstOrNull { it.id == fallback.to }
            if (from != null && to != null) Hint(stringResource(Res.string.linked_profile_fallback, profileLabel(from), profileLabel(to)))
        }
        // Every profile (the ones this build cannot place on the axes too), with the reason of the disabled ones.
        TextButton(onClick = { showAll = !showAll }) {
            Icon(if (showAll) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(if (showAll) Res.string.rd_s9_profile_hide_all else Res.string.rd_s9_profile_show_all, profiles.size))
        }
        if (showAll || ups.isEmpty()) {
            ProfileList(profiles = profiles, selected = selected, unavailable = unavailable, mayDropFrames = mayDropFrames, onSelect = onSelect)
        }
    }
}

@Composable
private fun AxisRow(label: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.selectableGroup(),
        ) { chips() }
    }
}

@Composable
private fun AxisChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (selected) Res.string.rd_s9_selected else Res.string.rd_s9_not_selected)
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        modifier = Modifier.semantics { stateDescription = state },
    )
}

@Composable
private fun upscaleLabel(height: Int?): String =
    if (height == null) stringResource(Res.string.rd_s9_axis_up_none) else stringResource(Res.string.linked_profile_anime4k, height)

@Composable
private fun frameLabel(f: FrameAxis): String = when (f) {
    FrameAxis.None -> stringResource(Res.string.rd_s9_axis_fg_none)
    is FrameAxis.Fixed -> stringResource(Res.string.rd_s9_axis_fg_fixed, f.multiplier)
    is FrameAxis.Auto -> stringResource(Res.string.rd_s9_axis_fg_auto, f.fps)
}

@Composable
internal fun sustainText(profile: LinkProfile): String = stringResource(
    when (profile.measuredSustain) {
        "true" -> Res.string.linked_sustain_ok
        "false" -> Res.string.linked_sustain_no
        else -> Res.string.linked_sustain_unknown
    },
)
