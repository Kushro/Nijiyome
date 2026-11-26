// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.ControlsEdge
import app.nijiyome.designsystem.motion.PlayerControlsTransition
import app.nijiyome.feature.player.BufferingOverlay
import app.nijiyome.feature.player.PlayerVolumeControl
import app.nijiyome.feature.player.Sheet
import app.nijiyome.feature.player.aboveTransport
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_quality
import app.nijiyome.feature.player.resources.player_quality_height
import app.nijiyome.feature.player.sourceQualityChip
import app.nijiyome.ui.platform.windowWidthDp
import org.jetbrains.compose.resources.stringResource

// Sparkles S5, structure Cine (artifact `reproductor` › `cine`): the full cinematic chrome over the video. Pointer: a top bar with the
// title, the status chips and the actions, and a bottom bar with the seek bar, the transport, the time, the volume and the sheet chips.
// Touch: a top bar with lock / PiP / fullscreen / Más, the status chips under it, the gesture hints and a centred transport, and the
// sheet chips under the seek bar.

/** The Cine chrome, drawn over the video (its blocks show and hide with [visible] through `PlayerControlsTransition`). */
@Composable
internal fun BoxScope.CinemaChrome(model: PlayerChromeModel, actions: PlayerChromeActions, visible: Boolean, inPanel: Boolean = false) {
    if (model.capabilities.touchFirst) TouchCinema(model, actions, visible) else PointerCinema(model, actions, visible, inPanel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.PointerCinema(model: PlayerChromeModel, actions: PlayerChromeActions, visible: Boolean, inPanel: Boolean) {
    val compact = windowWidthDp() < COMPACT_WIDTH_DP
    PlayerControlsTransition(visible, ControlsEdge.TOP, Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(TopShade).statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
                BackButton(actions)
                if (compact) {
                    // BUG-060: on a narrow window the secondary actions go to the Más sheet and the chips get a row of their own
                    ChromeTitle(model, Modifier.weight(1f))
                    SendToDeviceButton(model)
                    PipAndFullscreen(model, actions)
                    MoreButton(actions)
                } else {
                    ChromeTitle(model, Modifier.widthIn(max = 320.dp))
                    Spacer(Modifier.width(8.dp))
                    StatusChips(model, actions, Modifier.weight(1f))
                    WideTopActions(model, actions, episodes = !inPanel)
                }
            }
            if (compact) StatusChips(model, actions, Modifier.padding(start = 52.dp, top = 2.dp))
        }
    }
    if (model.buffering) {
        PlayerControlsTransition(visible, ControlsEdge.CENTER, Modifier.align(Alignment.Center)) {
            BufferingOverlay(model.state, Modifier.padding(horizontal = 16.dp), showIndicator = false)
        }
    }
    PlayerControlsTransition(visible, ControlsEdge.BOTTOM, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(BottomShade).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 8.dp)) {
            PlayerSeekBar(model, actions.onInteract)
            PlayerSeekLegend(model, Modifier.padding(start = 4.dp, bottom = 2.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
                PrevEpisodeButton(model, actions)
                SeekBackButton(model, actions)
                PlayButton(model, actions, 48.dp)
                SeekForwardButton(model, actions)
                NextEpisodeButton(model, actions)
                PlayerTimeText(model, actions.onInteract)
                PlayerChapterTag(model)
                // Hito 6 (desktop): the engine's own volume (PlayerVolumeControl.kt)
                if (model.capabilities.volumeControls) PlayerVolumeControl(model.session, actions.onInteract, showLabel = false)
                Spacer(Modifier.weight(1f))
                if (!inPanel) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                        SheetChips(model, actions)
                    }
                }
            }
            // REP-02: beside the Panel's side panel the stage is too narrow for the transport and the chips on one line (the last chip
            // was cut against the panel); as in the artifact, the chips take a line of their own and wrap.
            if (inPanel) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SheetChips(model, actions)
                }
            }
        }
    }
}

/** Pistas, Calidad, Velocidad, Imagen and Más: the pointer chrome's sheet chips. */
@Composable
private fun SheetChips(model: PlayerChromeModel, actions: PlayerChromeActions) {
    TracksChip(model, actions)
    QualityChip(model, actions)
    SpeedChip(model, actions)
    PictureChip(model, actions)
    MoreChip(model, actions)
}

@Composable
private fun BoxScope.TouchCinema(model: PlayerChromeModel, actions: PlayerChromeActions, visible: Boolean) {
    PlayerControlsTransition(visible, ControlsEdge.TOP, Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(TopShade).statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp), Alignment.CenterVertically) {
                BackButton(actions)
                ChromeTitle(model, Modifier.weight(1f))
                LockButton(model, actions)
                if (model.hasKeyboard) KeyboardButton(actions)
                PipAndFullscreen(model, actions)
                MoreButton(actions)
            }
            // PLA-04: the enhancement and source chips in every layout and width
            StatusChips(model, actions, Modifier.padding(start = 48.dp, top = 2.dp), torrentLine = false)
        }
    }
    PlayerControlsTransition(visible, ControlsEdge.CENTER, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            GestureHints()
            CenterTransport(model, actions, Modifier.align(Alignment.Center))
            // BUG-106: the buffering text sits above the transport, where neither the row nor the top bar covers it
            if (model.buffering) BufferingOverlay(model.state, Modifier.aboveTransport().padding(horizontal = 16.dp), showIndicator = false)
        }
    }
    PlayerControlsTransition(visible, ControlsEdge.BOTTOM, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().background(BottomShade).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlayerSeekBar(model, actions.onInteract)
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                PlayerTimeText(model, actions.onInteract)
                PlayerSeekLegend(model, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                TracksChip(model, actions)
                SpeedChip(model, actions)
                AspectChip(model, actions)
                PictureChip(model, actions)
                EpisodesChip(model, actions)
                NextChip(model, actions)
            }
        }
    }
}

/** Desktop "Calidad 1080p": the playing source's quality (switching quality switches source) or the stream's height. */
@Composable
internal fun QualityChip(model: PlayerChromeModel, actions: PlayerChromeActions) {
    val ps = model.ps
    val sourceQuality = if (model.capabilities.sourceQualities && ps.videoHeights.isEmpty()) sourceQualityChip(model.state) else null
    if (ps.videoHeights.isEmpty() && sourceQuality == null) return
    val label = sourceQuality ?: ps.selectedHeight?.let { stringResource(Res.string.player_quality_height, it) } ?: stringResource(Res.string.player_quality)
    ChromeChip(label, Icons.Rounded.HighQuality, { actions.onOpenSheet(Sheet.Quality); actions.onInteract() }, pressed = model.openSheet == Sheet.Quality)
}

/** BUG-060: below this width the top bar moves its secondary actions to the Más sheet and the chips to a row of their own. */
internal const val COMPACT_WIDTH_DP = 600f
