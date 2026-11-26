// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sheets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.player.DownloadAction
import app.nijiyome.feature.player.LocalDownloadArgs
import app.nijiyome.feature.player.keyLabel
import app.nijiyome.feature.player.playerCapabilities
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.model.PlayerChromeStyle
import app.nijiyome.model.PlayerStructure
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.SourceKind
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.ui.look.structures
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

// Sparkles redesign (lane S6): the "Más" sheet (artifact `shMas`, parity PLY-06 / PLY-12 / PLA-03 / PLA-27): the controls layout (the
// touch "Cine / Minimalista" choice is now the player's structure) and the less used actions as one list, the same on both platforms.

@Composable
internal fun MoreSheetContent(
    data: PlayerSheetData,
    actions: PlayerSheetActions,
    openSettings: (SettingsSection) -> Unit,
    onClose: () -> Unit,
) {
    LayoutSection()
    SheetDivider()
    SheetSection(null) {
        val caps = playerCapabilities
        val state = data.state
        val keymap = PlayerKeymap.resolve(state.settings.playerKeymap, caps.desktopShortcuts)
        fun key(action: String): String? = keymap[action]?.takeIf { !caps.touchFirst }
        actions.onScreenshot?.takeIf { caps.screenshot }?.let { shot ->
            ActionRow(Icons.Rounded.PhotoCamera, stringResource(Res.string.player_screenshot), onClick = { onClose(); shot() }, badge = key(PlayerKeymap.SCREENSHOT)?.let { keyLabel(it) })
        }
        LocalDownloadArgs.current?.let { args ->
            DownloadAction(args, state.current?.candidate?.toStream(), state.current?.candidate?.label.orEmpty(), asRow = true)
        }
        TorrentClientRow(data, actions, onClose)
        actions.onOpenPicker?.takeIf { state.canPickSource }?.let { pick ->
            ActionRow(Icons.Rounded.SwapHoriz, stringResource(Res.string.player_pick_source), onClick = { onClose(); pick() })
        }
        actions.onEpisodes?.takeIf { state.canNavigateEpisodes }?.let { episodes ->
            ActionRow(Icons.Rounded.VideoLibrary, stringResource(Res.string.player_episodes), onClick = episodes)
        }
        ActionRow(Icons.Rounded.HighQuality, stringResource(Res.string.player_fx_title), onClick = { actions.onOpenSheet(PlayerSheetKind.ENHANCEMENTS) })
        actions.onShortcuts?.let { shortcuts ->
            ActionRow(
                Icons.Rounded.Keyboard,
                stringResource(Res.string.player_shortcuts),
                onClick = { onClose(); shortcuts() },
                badge = key(PlayerKeymap.HELP)?.let { keyLabel(it) },
            )
        }
        actions.onLock?.takeIf { caps.screenLock }?.let { lock ->
            ActionRow(Icons.Rounded.Lock, stringResource(Res.string.player_lock), onClick = { onClose(); lock() })
        }
        ActionRow(Icons.Rounded.Settings, stringResource(Res.string.rd_s6_player_settings), onClick = { openSettings(SettingsSection.PLAYER) })
    }
}

/** "Abrir en el cliente de torrents": the playing file's torrent; a torrent source served by a debrid service has none, and says so. */
@Composable
private fun TorrentClientRow(data: PlayerSheetData, actions: PlayerSheetActions, onClose: () -> Unit) {
    val open = actions.onOpenTorrent ?: return
    val id = data.state.torrent?.takeIf { it.fileIndex != null }?.torrentId
    val torrentSource = data.state.current?.candidate?.kind == SourceKind.TORRENT
    when {
        id != null -> ActionRow(
            Icons.AutoMirrored.Rounded.OpenInNew,
            stringResource(Res.string.player_torrent_open_client),
            onClick = { onClose(); open(id) },
        )
        torrentSource && data.state.remoteless -> ActionRow(
            Icons.AutoMirrored.Rounded.OpenInNew,
            stringResource(Res.string.player_torrent_open_client),
            onClick = {},
            enabled = false,
            supporting = stringResource(Res.string.rd_s6_torrent_not_in_client),
        )
    }
}

// ---- controls layout ---------------------------------------------------------------------------------------------------------------------

/** The player's structure (Cine, Mínimo, Panel / Vertical), saved in the design choices; the chrome switches at once. */
@Composable
private fun LayoutSection() {
    val koin = getKoin()
    val repo = remember { koin.getOrNull<SettingsRepository>() } ?: return
    val scope = rememberCoroutineScope()
    val touch = playerCapabilities.touchFirst
    val current = structures.player
    SheetSection(stringResource(Res.string.rd_s6_layout)) {
        Segmented(
            options = PlayerStructure.entries.map { SegmentedOption(it, stringResource(layoutLabel(it, touch))) },
            selected = current,
            onSelect = { picked ->
                scope.launch {
                    runCatching {
                        repo.update { s ->
                            val design = s.look.design
                            s.copy(
                                look = s.look.copy(design = design.copy(structures = design.structures.copy(player = picked))),
                                // the touch style before Sparkles, kept in step for anything that still reads it
                                playerChromeStyle = if (picked == PlayerStructure.MINIMAL) PlayerChromeStyle.MINIMAL else PlayerChromeStyle.CINEMA,
                            )
                        }
                    }
                }
            },
            fillWidth = true,
            small = true,
        )
        Hint(stringResource(layoutHint(current, touch)))
    }
}

private fun layoutLabel(structure: PlayerStructure, touch: Boolean): StringResource = when (structure) {
    PlayerStructure.CINEMA -> if (touch) Res.string.rd_s6_layout_cinema else Res.string.rd_s6_layout_classic
    PlayerStructure.MINIMAL -> Res.string.rd_s6_layout_minimal
    PlayerStructure.PANEL -> if (touch) Res.string.rd_s6_layout_vertical else Res.string.rd_s6_layout_panel
}

private fun layoutHint(structure: PlayerStructure, touch: Boolean): StringResource = when (structure) {
    PlayerStructure.CINEMA -> if (touch) Res.string.rd_s6_layout_cinema_hint else Res.string.rd_s6_layout_classic_hint
    PlayerStructure.MINIMAL -> Res.string.rd_s6_layout_minimal_hint
    PlayerStructure.PANEL -> if (touch) Res.string.rd_s6_layout_vertical_hint else Res.string.rd_s6_layout_panel_hint
}
