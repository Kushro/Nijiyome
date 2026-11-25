// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.ControlsEdge
import app.nijiyome.designsystem.motion.PlayerControlsTransition
import app.nijiyome.feature.player.BufferingOverlay
import app.nijiyome.feature.player.EnhancementStatusChip
import app.nijiyome.feature.player.PlayerVolumeControl
import app.nijiyome.feature.player.Sheet
import app.nijiyome.feature.player.aspectLabelRes
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_episodes
import app.nijiyome.feature.player.resources.player_more
import app.nijiyome.feature.player.resources.player_next
import app.nijiyome.feature.player.resources.player_prev
import app.nijiyome.feature.player.resources.player_speed_desc
import app.nijiyome.feature.player.resources.player_speed_value
import app.nijiyome.feature.player.resources.rd_s5_picture
import app.nijiyome.feature.player.resources.rd_s5_sub_none
import app.nijiyome.feature.player.resources.rd_s5_tracks
import app.nijiyome.feature.player.speedText
import app.nijiyome.model.AspectMode
import app.nijiyome.ui.platform.windowWidthDp
import org.jetbrains.compose.resources.stringResource

// Sparkles S5, structure Mínimo (artifact `reproductor` › `minimal`): bare controls. A thin top bar (back, title, the source in one word,
// the enhancement chip) and one low bar: play, a thin seek bar, the time and icon-over-label actions (48 dp targets, PLA-09). No
// centred transport: on touch, double tap seeks ±10 s and the bar's play button plays / pauses.

/** The Mínimo chrome, drawn over the video. */
@Composable
internal fun BoxScope.MinimalChrome(model: PlayerChromeModel, actions: PlayerChromeActions, visible: Boolean) {
    val touch = model.capabilities.touchFirst
    val compact = windowWidthDp() < COMPACT_WIDTH_DP
    PlayerControlsTransition(visible, ControlsEdge.TOP, Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(TopShade).statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
                BackButton(actions)
                Row(Modifier.weight(1f), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                    ChromeTitle(model, Modifier.weight(1f, fill = false))
                    if (!compact) SourceSuffix(model, actions)
                }
                // PLA-04: what is enhanced stays in sight in every layout
                if (!compact) EnhancementStatusChip(model.enhancementChip, onClick = { actions.onOpenSheet(Sheet.Enhancements) })
                if (touch) {
                    LockButton(model, actions)
                    if (model.hasKeyboard) KeyboardButton(actions)
                    PipAndFullscreen(model, actions)
                    MoreButton(actions)
                } else if (compact) {
                    PipAndFullscreen(model, actions)
                    MoreButton(actions)
                } else {
                    SendToDeviceButton(model)
                }
            }
            if (compact) StatusChips(model, actions, Modifier.padding(start = 48.dp, top = 2.dp), torrentLine = false)
        }
    }
    if (model.buffering) {
        PlayerControlsTransition(visible, ControlsEdge.CENTER, Modifier.align(Alignment.Center)) {
            BufferingOverlay(model.state, Modifier.padding(horizontal = 16.dp), showIndicator = false)
        }
    }
    PlayerControlsTransition(visible, ControlsEdge.BOTTOM, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().background(BottomShade).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (compact || touch) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                    PlayButton(model, actions, 40.dp)
                    PlayerSeekBar(model, actions.onInteract, Modifier.weight(1f), thin = true)
                    PlayerTimeText(model, actions.onInteract, remainingOnly = touch)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(2.dp), Alignment.CenterVertically) {
                    MinimalActions(model, actions)
                    if (!touch) FullscreenButton(model, actions)
                }
            } else {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                    PlayButton(model, actions, 40.dp)
                    PlayerSeekBar(model, actions.onInteract, Modifier.weight(1f), thin = true)
                    PlayerTimeText(model, actions.onInteract)
                    if (model.capabilities.volumeControls) PlayerVolumeControl(model.session, actions.onInteract, showLabel = false)
                    MinimalActions(model, actions)
                    FullscreenButton(model, actions)
                }
            }
        }
    }
}

/** Pistas, Velocidad, Mejoras, Imagen, the framing cycle (touch), Episodios, previous / next and Más, as icon-over-label buttons. */
@Composable
private fun MinimalActions(model: PlayerChromeModel, actions: PlayerChromeActions) {
    val touch = model.capabilities.touchFirst
    val ps = model.ps
    val accent = MaterialTheme.colorScheme.primary
    if (model.hasTracks()) {
        val sub = ps.subtitles.firstOrNull { it.selected }?.language?.takeIf { it.isNotBlank() }?.uppercase()
        val subOn = ps.subtitlesEnabled || model.state.customSubtitle != null
        val label = when {
            !touch -> stringResource(Res.string.rd_s5_tracks)
            !subOn -> stringResource(Res.string.rd_s5_sub_none)
            else -> sub ?: stringResource(Res.string.rd_s5_tracks)
        }
        MinAction(Icons.Rounded.Subtitles, label, { actions.onOpenSheet(Sheet.Tracks); actions.onInteract() }, description = tracksSummary(model))
    }
    val speed = speedText(ps.speed)
    MinAction(
        Icons.Rounded.Speed,
        stringResource(Res.string.player_speed_value, speed),
        { actions.onOpenSheet(Sheet.Speed); actions.onInteract() },
        description = stringResource(Res.string.player_speed_desc, speed),
    )
    EnhancementsMinAction(actions)
    MinAction(Icons.Rounded.AspectRatio, stringResource(Res.string.rd_s5_picture), { actions.onOpenSheet(Sheet.Picture); actions.onInteract() })
    if (touch) {
        val fit = model.aspectMode == AspectMode.FIT
        MinAction(
            Icons.Rounded.AspectRatio,
            stringResource(aspectLabelRes(model.aspectMode)),
            { actions.onCycleAspect(); actions.onInteract() },
            tint = if (fit) MaterialTheme.colorScheme.onSurface else accent,
        )
    }
    if (model.canBrowseEpisodes) {
        MinAction(Icons.Rounded.VideoLibrary, stringResource(Res.string.player_episodes), { actions.onOpenSheet(Sheet.Episodes) })
    }
    if (model.state.hasPrevEpisode) MinAction(Icons.Rounded.SkipPrevious, stringResource(Res.string.player_prev), actions.onPrev)
    if (model.canGoNext) MinAction(Icons.Rounded.SkipNext, stringResource(Res.string.player_next), actions.onNext)
    if (!touch) MinAction(Icons.Rounded.MoreHoriz, stringResource(Res.string.player_more), { actions.onOpenSheet(Sheet.More); actions.onInteract() })
}
