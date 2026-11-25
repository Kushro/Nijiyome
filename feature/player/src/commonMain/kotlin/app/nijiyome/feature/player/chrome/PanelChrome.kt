// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.ControlsEdge
import app.nijiyome.designsystem.motion.PlayerControlsTransition
import app.nijiyome.feature.player.Sheet
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_episodes
import app.nijiyome.feature.player.resources.player_fx_title
import app.nijiyome.feature.player.resources.player_more
import app.nijiyome.feature.player.resources.player_pip
import app.nijiyome.feature.player.resources.rd_s5_panel_label
import app.nijiyome.feature.player.resources.rd_s5_picture
import app.nijiyome.feature.player.resources.rd_s5_speed
import app.nijiyome.feature.player.resources.rd_s5_tracks
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Sparkles S5, structure Panel (artifact `reproductor` › `panel`). Pointer, wide window: the video with the Cine chrome and, beside it,
// a fixed 420 dp panel with the six groups as tabs (Pistas, Mejoras, Imagen, Velocidad, Episodios, Más); the chrome's sheet buttons
// pick the tab instead of opening a sheet. Touch: the vertical player, a 16:9 video with a light chrome, the title and the status chips
// under it, then the tabs and their content; fullscreen turns it into the Cine chrome (landscape) until it is left.

/** The panel's tabs, in the artifact's order. */
internal val PanelTabs: List<Sheet> = listOf(Sheet.Tracks, Sheet.Enhancements, Sheet.Picture, Sheet.Speed, Sheet.Episodes, Sheet.More)

/** The window is wide enough for the side panel next to the video. */
internal const val PANEL_MIN_WIDTH_DP = 900f

/**
 * Whether the Panel structure shows its tabs now (else it falls back to the Cine chrome with sheets): touch when not fullscreen (the
 * vertical player), pointer on a wide window. Pure.
 */
internal fun panelShowsTabs(touch: Boolean, fullscreen: Boolean, windowWidthDp: Float): Boolean =
    if (touch) !fullscreen else windowWidthDp >= PANEL_MIN_WIDTH_DP

/** The tab a sheet belongs to (the subtitle style steps are Pistas, the old Calidad / Audio / Subtítulos sheets their group). */
internal fun panelTabOf(sheet: Sheet): Sheet = when (sheet) {
    Sheet.Subtitles, Sheet.Audio, Sheet.SubtitleStyle, Sheet.SubtitleStyleFull, Sheet.Tracks -> Sheet.Tracks
    Sheet.Quality, Sheet.Picture -> Sheet.Picture
    else -> sheet
}

/** Pointer: the stage (video + Cine chrome) and the fixed side panel. */
@Composable
internal fun PanelSideLayout(
    tab: Sheet,
    onTab: (Sheet) -> Unit,
    stage: @Composable (Modifier) -> Unit,
    body: @Composable (Sheet) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        stage(Modifier.weight(1f).fillMaxHeight())
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier
                .width(420.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .decor(DecorRegion.SHEET),
        ) {
            PanelTabRow(tab, onTab)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 40.dp)) { body(tab) }
        }
    }
}

/** Touch: the vertical player. [stage] draws the 16:9 video with [MiniChrome]. */
@Composable
internal fun PanelVerticalLayout(
    model: PlayerChromeModel,
    actions: PlayerChromeActions,
    tab: Sheet,
    onTab: (Sheet) -> Unit,
    stage: @Composable (Modifier) -> Unit,
    body: @Composable (Sheet) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        stage(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChromeTitle(model)
            StatusChips(model, actions)
        }
        PanelTabRow(tab, onTab)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).decor(DecorRegion.SHEET).padding(bottom = 24.dp)) { body(tab) }
    }
}

/** The light chrome of the vertical player's 16:9 video: back and PiP, ±10 s and play, a thin seek bar, the time and fullscreen. */
@Composable
internal fun BoxScope.MiniChrome(model: PlayerChromeModel, actions: PlayerChromeActions, visible: Boolean) {
    PlayerControlsTransition(visible, ControlsEdge.TOP, Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(TopShade).padding(start = 4.dp, end = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(actions)
            Spacer(Modifier.weight(1f))
            LockButton(model, actions)
            if (model.capabilities.pictureInPicture) PipButton(actions)
        }
    }
    PlayerControlsTransition(visible, ControlsEdge.CENTER, Modifier.align(Alignment.Center)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            SeekBackButton(model, actions, shaded = true)
            PlayButton(model, actions, 52.dp)
            SeekForwardButton(model, actions, shaded = true)
        }
    }
    PlayerControlsTransition(visible, ControlsEdge.BOTTOM, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(BottomShade).padding(start = 8.dp, end = 4.dp, top = 16.dp), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
            PlayerSeekBar(model, actions.onInteract, Modifier.weight(1f), thin = true)
            PlayerTimeText(model, actions.onInteract)
            FullscreenButton(model, actions)
        }
    }
}

@Composable
private fun PipButton(actions: PlayerChromeActions) {
    ChromeIconButton(Icons.Rounded.PictureInPictureAlt, stringResource(Res.string.player_pip), actions.onPip)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PanelTabRow(tab: Sheet, onTab: (Sheet) -> Unit) {
    val selected = PanelTabs.indexOf(panelTabOf(tab)).coerceAtLeast(0)
    val label = stringResource(Res.string.rd_s5_panel_label)
    PrimaryTabRow(
        selectedTabIndex = selected,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    ) {
        PanelTabs.forEachIndexed { i, t ->
            val (icon, name) = panelTabLook(t)
            Tab(
                selected = i == selected,
                onClick = { onTab(t) },
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                text = { Text(stringResource(name), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            )
        }
    }
}

private fun panelTabLook(tab: Sheet): Pair<ImageVector, StringResource> = when (tab) {
    Sheet.Enhancements -> Icons.Rounded.AutoAwesome to Res.string.player_fx_title
    Sheet.Picture -> Icons.Rounded.AspectRatio to Res.string.rd_s5_picture
    Sheet.Speed -> Icons.Rounded.Speed to Res.string.rd_s5_speed
    Sheet.Episodes -> Icons.Rounded.VideoLibrary to Res.string.player_episodes
    Sheet.More -> Icons.Rounded.MoreHoriz to Res.string.player_more
    else -> Icons.Rounded.Subtitles to Res.string.rd_s5_tracks
}
