// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.feature.player.sheets.Caption
import app.nijiyome.feature.player.sheets.Hint
import app.nijiyome.feature.player.sheets.NoteTone
import app.nijiyome.feature.player.sheets.SheetSection
import app.nijiyome.feature.player.sheets.StateNote
import app.nijiyome.feature.player.sheets.ToggleRow
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.mpv.DesktopMpvPlayerSession
import app.nijiyome.player.mpv.framegen.DesktopFgTransport
import app.nijiyome.player.mpv.framegen.DesktopFgUnavailable
import app.nijiyome.player.mpv.framegen.DesktopFrameGenController
import app.nijiyome.player.mpv.framegen.DesktopFrameGenState
import org.jetbrains.compose.resources.stringResource

/**
 * v1.3.2: embedded desktop Frame Generation controls in the player's Mejoras drawer,
 * live top-bar badge (`FG ×N`), and Estadísticas overlay line.
 */
@Composable
internal fun rememberDesktopFrameGen(session: PlayerSession): PhoneFrameGen? {
    val controller = remember(session) { (session as? DesktopMpvPlayerSession)?.frameGen } ?: return null
    val state = controller.state.collectAsState().value
    return DesktopEmbeddedFrameGen(controller, state)
}

private class DesktopEmbeddedFrameGen(
    private val fg: DesktopFrameGenController,
    private val s: DesktopFrameGenState,
) : PhoneFrameGen {
    override val activeMultiplier: Int? get() = s.takeIf { it.active }?.multiplier

    override val problem: Boolean get() = s.enabled && s.unavailable != null

    @Composable
    override fun Section(onOpenSettings: () -> Unit) {
        LaunchedEffect(fg) { fg.refreshEligibility() }
        val transportLabel = when (s.transport) {
            DesktopFgTransport.GL_VULKAN_ZERO_COPY -> stringResource(Res.string.player_fx_fg_transport_gl_vk)
            DesktopFgTransport.D3D12_VULKAN_ZERO_COPY -> stringResource(Res.string.player_fx_fg_transport_d3d12_vk)
            DesktopFgTransport.STAGING -> stringResource(Res.string.player_fx_fg_transport_staging)
            DesktopFgTransport.NONE -> ""
        }
        val unavailText = unavailableLabel(s.unavailable, s.errorDetail)
        val status = when {
            s.active -> stringResource(Res.string.player_fx_fg_state_on, s.multiplier, transportLabel)
            !s.eligibilityKnown && s.unavailable == null -> stringResource(Res.string.player_fx_fg_state_checking)
            unavailText != null -> unavailText
            s.enabled -> stringResource(Res.string.player_fx_fg_state_ready)
            else -> stringResource(Res.string.player_fx_fg_state_off)
        }
        val ineligible = s.unavailable != null && s.unavailable != DesktopFgUnavailable.FAILED

        SheetSection(stringResource(Res.string.player_fx_fg_title_desktop)) {
            ToggleRow(
                title = stringResource(Res.string.player_fx_fg_enabled),
                checked = s.enabled,
                onChange = fg::setEnabled,
                hint = status,
                enabled = !ineligible || s.enabled,
                hintIsWarning = s.unavailable != null,
            )
            if (s.unavailable == DesktopFgUnavailable.NO_DLL) {
                TextButton(onClick = onOpenSettings, shape = NijiyomeControls.shape) {
                    Text(stringResource(Res.string.player_fx_fg_open_settings))
                }
            }
            if (s.active && s.inFps > 0.0) {
                Text(
                    stringResource(Res.string.player_fx_fg_live, fpsText(s.inFps), fpsText(s.outFps), s.multiplier),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (s.active && s.overloaded) {
                StateNote(stringResource(Res.string.player_fx_fg_state_overloaded), NoteTone.WARN)
            }
            if (!ineligible) {
                Caption(stringResource(Res.string.player_fx_fg_multiplier))
                Segmented(
                    options = listOf(
                        SegmentedOption(0, stringResource(Res.string.player_fx_fg_multiplier_auto)),
                        SegmentedOption(2, "×2"),
                        SegmentedOption(3, "×3"),
                        SegmentedOption(4, "×4"),
                    ),
                    selected = s.requestedMultiplier,
                    onSelect = fg::setMultiplier,
                    fillWidth = true,
                    small = true,
                )
                Hint(stringResource(Res.string.player_fx_fg_multiplier_hint))
                Caption(stringResource(Res.string.player_fx_fg_mode))
                Segmented(
                    options = listOf(
                        SegmentedOption(false, stringResource(Res.string.player_fx_fg_mode_quality)),
                        SegmentedOption(true, stringResource(Res.string.player_fx_fg_mode_performance)),
                    ),
                    selected = s.performance,
                    onSelect = fg::setPerformance,
                    fillWidth = true,
                    small = true,
                )
                ToggleRow(
                    title = stringResource(Res.string.player_fx_fg_anti_artifacts),
                    checked = s.antiArtifacts,
                    onChange = fg::setAntiArtifacts,
                    hint = stringResource(Res.string.player_fx_fg_anti_artifacts_hint),
                )
            }
        }
    }

    @Composable
    override fun StatsLine(line: @Composable (text: String, color: Color?) -> Unit) {
        when {
            s.active -> line(
                stringResource(Res.string.player_stats_fg, fpsText(s.inFps), fpsText(s.outFps), s.multiplier, fps2(s.computeMs)),
                if (s.overloaded) Color(0xFFFFD180) else null,
            )
            s.unavailable != null -> {
                val reason = unavailableLabel(s.unavailable, s.errorDetail) ?: return
                line(stringResource(Res.string.player_stats_fg_status, reason), null)
            }
            else -> line(
                stringResource(Res.string.player_stats_fg_status, stringResource(Res.string.player_fx_fg_state_off)),
                null,
            )
        }
    }
}

@Composable
private fun unavailableLabel(u: DesktopFgUnavailable?, detail: String): String? = when (u) {
    null -> null
    DesktopFgUnavailable.NO_LIBRARY -> stringResource(Res.string.player_fx_fg_state_no_lib)
    DesktopFgUnavailable.NO_DLL -> stringResource(Res.string.player_fx_fg_state_no_dll)
    DesktopFgUnavailable.NO_VULKAN -> stringResource(Res.string.player_fx_fg_state_no_vulkan)
    DesktopFgUnavailable.DEVICE_UNSUPPORTED -> stringResource(Res.string.player_fx_fg_state_unsupported)
    DesktopFgUnavailable.FAILED -> stringResource(Res.string.player_fx_fg_state_failed, detail.ifBlank { "error" })
}
