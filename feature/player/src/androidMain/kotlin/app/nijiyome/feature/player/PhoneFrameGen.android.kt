// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
// Android resources of :core:framegen (FgR) next to this module's Res
import androidx.compose.ui.res.stringResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.framegen.FgProfile
import app.nijiyome.framegen.FgStatsStatus
import app.nijiyome.framegen.FgStatsView
import app.nijiyome.framegen.FgUnavailableReason
import app.nijiyome.framegen.FrameGenController
import app.nijiyome.framegen.FrameGenMultiplier
import app.nijiyome.framegen.FrameGenText
import app.nijiyome.framegen.FrameGenUiState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.mpv.MpvPlayerSession
import app.nijiyome.feature.player.sheets.Caption
import app.nijiyome.feature.player.sheets.Hint
import app.nijiyome.feature.player.sheets.NoteTone
import app.nijiyome.feature.player.sheets.SheetSection
import app.nijiyome.feature.player.sheets.StateNote
import app.nijiyome.feature.player.sheets.ToggleRow
import app.nijiyome.framegen.R as FgR
import app.nijiyome.designsystem.theme.NijiyomeControls

// Milestone 4: the phone frame generation of the player screen (:core:framegen is an Android-only module and its texts are its
// Android resources): the "Mejoras" card and the statistics line, moved verbatim from EnhancementPanel.kt / EnhancementStats.kt, and
// the state / notice wiring moved from PlayerContent.

@Composable
internal actual fun rememberPhoneFrameGen(vm: PlayerViewModel, session: PlayerSession): PhoneFrameGen? {
    val frameGen = remember(session) { (session as? MpvPlayerSession)?.frameGen }
    val fgState = frameGen?.state?.collectAsStateWithLifecycle()?.value
    // FG failing in the middle of a video gets a notice (the card says it too); ineligibility is only shown in the panel.
    LaunchedEffect(frameGen) {
        val fg = frameGen ?: return@LaunchedEffect
        var last = fg.state.value.unavailable
        fg.state.collect { s ->
            val r = s.unavailable
            if (r != last && r?.kind == FgUnavailableReason.Kind.FAILED && s.enabled) vm.postNotice(PlayerNotice.FrameGen(r, s.noInputTimeout))
            last = r
        }
    }
    return if (frameGen != null && fgState != null) AndroidPhoneFrameGen(frameGen, fgState) else null
}

private class AndroidPhoneFrameGen(private val fg: FrameGenController, private val s: FrameGenUiState) : PhoneFrameGen {
    override val activeMultiplier: Int? get() = s.takeIf { it.active }?.multiplier

    override val problem: Boolean
        get() = s.enabled && s.unavailable != null && s.unavailable?.kind != FgUnavailableReason.Kind.SUSPENDED

    @Composable
    override fun Section(onOpenSettings: () -> Unit) = FrameGenSection(fg, s, onOpenSettings)

    @Composable
    override fun StatsLine(line: @Composable (text: String, color: Color?) -> Unit) {
        val v = FgStatsView.from(s, s.liveFps)
        when {
            v.status == FgStatsStatus.ACTIVE || v.status == FgStatsStatus.PAUSED -> line(
                stringResource(Res.string.player_stats_fg, fpsText(v.inFps), fpsText(v.outFps), v.multiplier, fps2(v.latencyMs)),
                if (v.overloaded) Color(0xFFFFD180) else null,
            )
            v.reason != null -> line(stringResource(Res.string.player_stats_fg_status, stringResource(FrameGenText.reason(v.reason!!, v.noInputTimeout))), null)
            else -> line(stringResource(Res.string.player_stats_fg_status, stringResource(FgR.string.fg_state_off)), null)
        }
    }
}

@Composable
internal actual fun phoneFrameGenReason(detail: String): PhoneFrameGenReason? {
    val reason = FgUnavailableReason.entries.firstOrNull { it.name.equals(detail, ignoreCase = true) } ?: return null
    return PhoneFrameGenReason(stringResource(FrameGenText.reason(reason)), missingShaders = reason == FgUnavailableReason.SHADERS)
}

@Composable
internal actual fun frameGenNoticeReason(notice: PlayerNotice.FrameGen): String =
    stringResource(FrameGenText.reason(notice.reason as FgUnavailableReason, notice.noInput))

// d. Phone frame generation (the "Mejoras" sheet's section) -----------------------------------------------------------------------------

// Sparkles redesign (lane S6): a section of the grouped "Mejoras" sheet (SheetSection) instead of a card at the bottom of a long panel.
@Composable
private fun FrameGenSection(fg: FrameGenController, s: FrameGenUiState, onOpenFgSettings: () -> Unit) {
    // the expensive eligibility facts (library, imported shaders) are probed when the section is shown
    LaunchedEffect(fg) { fg.refresh() }
    val reason = s.unavailable
    val ineligible = reason?.kind == FgUnavailableReason.Kind.INELIGIBLE
    SheetSection(stringResource(Res.string.player_fx_fg_title)) {
        val status = when {
            s.active -> stringResource(FgR.string.fg_state_active, s.multiplier)
            !s.eligibilityKnown && reason == null -> stringResource(FgR.string.fg_state_checking)
            reason != null -> stringResource(FrameGenText.reason(reason, s.noInputTimeout))
            s.enabled -> stringResource(FgR.string.fg_state_ready)
            else -> stringResource(FgR.string.fg_state_off)
        }
        ToggleRow(
            title = stringResource(FgR.string.fg_enabled),
            checked = s.enabled,
            onChange = fg::setEnabled,
            hint = status,
            enabled = !ineligible || s.enabled,
            hintIsWarning = reason != null && reason.kind != FgUnavailableReason.Kind.SUSPENDED,
        )
        if (reason == FgUnavailableReason.SHADERS || s.shadersImported == false) {
            TextButton(onClick = onOpenFgSettings, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_fx_fg_import)) }
        }
        if (s.active) {
            val view = FgStatsView.from(s, s.liveFps)
            if (view.status == FgStatsStatus.ACTIVE && view.inFps > 0.0) {
                Text(
                    stringResource(Res.string.player_fx_fg_live, fpsText(view.inFps), fpsText(view.outFps), view.multiplier),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (view.overloaded) StateNote(stringResource(FgR.string.fg_state_overloaded), NoteTone.WARN)
        }
        if (s.experimental) Hint(stringResource(FgR.string.fg_state_experimental))
        if (!ineligible) {
            Caption(stringResource(FgR.string.fg_multiplier))
            val selectedMultiplier = if (s.auto) FrameGenMultiplier.AUTO else FrameGenMultiplier.of(s.multiplier)
            Segmented(
                options = FrameGenMultiplier.entries.map { m ->
                    SegmentedOption(m, if (m == FrameGenMultiplier.AUTO) stringResource(FgR.string.fg_multiplier_auto) else stringResource(FgR.string.fg_multiplier_n, m.n))
                },
                selected = selectedMultiplier,
                onSelect = fg::setMultiplier,
                fillWidth = true,
                small = true,
            )
            Hint(stringResource(FgR.string.fg_multiplier_hint))
            Caption(stringResource(FgR.string.fg_profile))
            // CUSTOM is set up in Settings; it only shows here while it is the one in use
            val profiles = listOf(FgProfile.SAVER, FgProfile.BALANCED, FgProfile.QUALITY) + listOfNotNull(FgProfile.CUSTOM.takeIf { s.profile == it })
            Segmented(
                options = profiles.map { SegmentedOption(it, stringResource(FrameGenText.profile(it))) },
                selected = s.profile,
                onSelect = fg::setProfile,
                fillWidth = true,
                small = true,
            )
            Hint(stringResource(FgR.string.fg_profile_hint))
        }
    }
}
