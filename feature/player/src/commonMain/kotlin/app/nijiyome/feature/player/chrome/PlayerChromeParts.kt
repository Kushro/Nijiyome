// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.SendToMobile
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.DESKTOP_SEEK_STEP_MS
import app.nijiyome.feature.player.EnhancementStatusChip
import app.nijiyome.feature.player.LocalDownloadArgs
import app.nijiyome.feature.player.DownloadAction
import app.nijiyome.feature.player.Sheet
import app.nijiyome.feature.player.aspectLabelRes
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_back
import app.nijiyome.feature.player.resources.player_episodes
import app.nijiyome.feature.player.resources.player_fullscreen_enter
import app.nijiyome.feature.player.resources.player_fullscreen_exit
import app.nijiyome.feature.player.resources.player_fx_title
import app.nijiyome.feature.player.resources.player_lock
import app.nijiyome.feature.player.resources.player_more
import app.nijiyome.feature.player.resources.player_more_options
import app.nijiyome.feature.player.resources.player_next
import app.nijiyome.feature.player.resources.player_pause
import app.nijiyome.feature.player.resources.player_pip
import app.nijiyome.feature.player.resources.player_play
import app.nijiyome.feature.player.resources.player_prev
import app.nijiyome.feature.player.resources.player_screenshot
import app.nijiyome.feature.player.resources.player_seek_back
import app.nijiyome.feature.player.resources.player_seek_back_5
import app.nijiyome.feature.player.resources.player_seek_forward
import app.nijiyome.feature.player.resources.player_seek_forward_5
import app.nijiyome.feature.player.resources.player_send_phone
import app.nijiyome.feature.player.resources.player_shortcuts
import app.nijiyome.feature.player.resources.player_source_chip
import app.nijiyome.feature.player.resources.player_speed_desc
import app.nijiyome.feature.player.resources.player_speed_value
import app.nijiyome.feature.player.resources.player_torrent_batch_next
import app.nijiyome.feature.player.resources.player_torrent_open_client
import app.nijiyome.feature.player.resources.rd_s5_audio_lang
import app.nijiyome.feature.player.resources.rd_s5_hint_brightness
import app.nijiyome.feature.player.resources.rd_s5_hint_double_tap
import app.nijiyome.feature.player.resources.rd_s5_hint_volume
import app.nijiyome.feature.player.resources.rd_s5_picture
import app.nijiyome.feature.player.resources.rd_s5_send_to
import app.nijiyome.feature.player.resources.rd_s5_sub_lang
import app.nijiyome.feature.player.resources.rd_s5_sub_none
import app.nijiyome.feature.player.resources.rd_s5_tracks
import app.nijiyome.feature.player.resources.rd_s5_tracks_summary
import app.nijiyome.feature.player.speedText
import app.nijiyome.feature.player.torrentLine
import app.nijiyome.model.AspectMode
import app.nijiyome.model.SourceKind
import org.jetbrains.compose.resources.stringResource

// Sparkles S5: the pieces the three structures compose (artifact `reproductor`: `.nj-a-ptop`, `.nj-a-pbottom`, `.nj-a-pchip`,
// `.nj-a-minbtn`, `.nj-a-transport`, `.nj-a-rail`). The player keeps its dark OLED scheme; type, shapes and accent are the direction's.

/** The top edge's shade behind the top bar (artifact: black 72 % to transparent). */
internal val TopShade: Brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))

/** The bottom edge's shade behind the seek bar and the actions (black 80 % from the bottom). */
internal val BottomShade: Brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))

/** An icon button of the chrome: 48 dp, the label is its description. */
@Composable
internal fun ChromeIconButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, iconSize: Dp = 24.dp) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(iconSize))
    }
}

/** "Laid-Off Mage · Ep. 2" and, under it, the episode's own title when known. */
@Composable
internal fun ChromeTitle(model: PlayerChromeModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            model.title,
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        model.episodeTitle?.let {
            Text(it, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A chip of the chrome that opens a sheet or runs an action (BUG-067: a button for TalkBack, not a checkbox). [pressed] = its sheet is
 * open (or the panel's tab). [description] replaces the label for TalkBack when it says more ("Velocidad 1x").
 */
@Composable
internal fun ChromeChip(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    pressed: Boolean = false,
    trailingIcon: ImageVector? = null,
    description: String? = null,
    maxLabelWidth: Dp = 260.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (pressed) scheme.primaryContainer else Color.Black.copy(alpha = 0.35f)
    val content = if (pressed) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = if (pressed) null else BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.3f)),
        modifier = Modifier.semantics {
            role = Role.Button
            if (description != null) contentDescription = description
        },
    ) {
        Row(
            Modifier.heightIn(min = 36.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxLabelWidth),
            )
            if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** An icon over a short label, 56 x 48 dp (the Mínimo bar's `.nj-a-minbtn`). */
@Composable
internal fun MinAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface, description: String? = null) {
    Column(
        Modifier
            .widthIn(min = 56.dp)
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * The status chips: what is enhanced and where (opens Mejoras), the source ("Fuente 1 de 6 · …", opens the picker when there is a
 * choice), "Abrir en el cliente" for a torrent and, with [torrentLine], the torrent's speed / peers line. PLA-04: in every structure.
 */
@Composable
internal fun StatusChips(model: PlayerChromeModel, actions: PlayerChromeActions, modifier: Modifier = Modifier, torrentLine: Boolean = true) {
    val state = model.state
    Row(modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
        EnhancementStatusChip(model.enhancementChip, onClick = { actions.onOpenSheet(Sheet.Enhancements) })
        state.current?.let { cur ->
            val (n, total) = state.position ?: (1 to 1)
            ChromeChip(
                label = stringResource(Res.string.player_source_chip, n, total, "${cur.candidate.extName} · ${cur.candidate.label}"),
                // BUG-054: the torrent type is not the download action's arrow.
                icon = if (cur.candidate.kind == SourceKind.TORRENT) Icons.Rounded.Hub else Icons.Rounded.Language,
                onClick = { if (state.canPickSource) actions.onOpenPicker() },
                trailingIcon = if (state.canPickSource) Icons.Rounded.ExpandMore else null,
            )
        }
        state.torrent?.takeIf { it.fileIndex != null }?.torrentId?.let { id ->
            ChromeChip(stringResource(Res.string.player_torrent_open_client), Icons.AutoMirrored.Rounded.OpenInNew, onClick = { actions.onOpenTorrent(id) })
        }
        if (torrentLine) {
            state.torrent?.takeIf { it.fileIndex != null }?.let { t ->
                Text(torrentLine(t), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }
}

/** Mínimo's "· Kitsune Raws" after the title: the source in one word, opens the picker. */
@Composable
internal fun SourceSuffix(model: PlayerChromeModel, actions: PlayerChromeActions) {
    val c = model.state.current?.candidate ?: return
    Text(
        "· ${c.extName} ${c.label}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge.copy(textDecoration = if (model.state.canPickSource) TextDecoration.Underline else null),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(enabled = model.state.canPickSource, role = Role.Button, onClick = actions.onOpenPicker)
            .padding(4.dp),
    )
}

/** Play / pause; the loading indicator takes its place while buffering (BUG-106). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayButton(model: PlayerChromeModel, actions: PlayerChromeActions, size: Dp) {
    val ps = model.ps
    val label = stringResource(if (ps.isPlaying) Res.string.player_pause else Res.string.player_play)
    FilledIconButton(
        onClick = { model.session.togglePlay(); actions.onInteract() },
        enabled = ps.started,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (model.buffering) {
            LoadingIndicator(Modifier.size(size * 0.7f).semantics { contentDescription = label }, color = LocalContentColor.current)
        } else {
            Icon(if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, label, Modifier.size(size * 0.55f))
        }
    }
}

/** Back by the platform's step (5 s desktop, 10 s touch). */
@Composable
internal fun SeekBackButton(model: PlayerChromeModel, actions: PlayerChromeActions, shaded: Boolean = false) {
    val five = model.capabilities.seekStepMs == DESKTOP_SEEK_STEP_MS
    SeekStepButton(
        icon = if (five) Icons.Rounded.Replay5 else Icons.Rounded.Replay10,
        label = stringResource(if (five) Res.string.player_seek_back_5 else Res.string.player_seek_back),
        shaded = shaded,
    ) { model.session.seekBy(-model.capabilities.seekStepMs); actions.onInteract() }
}

@Composable
internal fun SeekForwardButton(model: PlayerChromeModel, actions: PlayerChromeActions, shaded: Boolean = false) {
    val five = model.capabilities.seekStepMs == DESKTOP_SEEK_STEP_MS
    SeekStepButton(
        icon = if (five) Icons.Rounded.Forward5 else Icons.Rounded.Forward10,
        label = stringResource(if (five) Res.string.player_seek_forward_5 else Res.string.player_seek_forward),
        shaded = shaded,
    ) { model.session.seekBy(model.capabilities.seekStepMs); actions.onInteract() }
}

@Composable
private fun SeekStepButton(icon: ImageVector, label: String, shaded: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = if (shaded) IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f)) else IconButtonDefaults.iconButtonColors(),
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(if (shaded) 28.dp else 24.dp))
    }
}

@Composable
internal fun PrevEpisodeButton(model: PlayerChromeModel, actions: PlayerChromeActions) {
    if (!model.state.hasPrevEpisode) return
    ChromeIconButton(Icons.Rounded.SkipPrevious, stringResource(Res.string.player_prev), { actions.onPrev(); actions.onInteract() })
}

@Composable
internal fun NextEpisodeButton(model: PlayerChromeModel, actions: PlayerChromeActions) {
    if (!model.canGoNext) return
    ChromeIconButton(Icons.Rounded.SkipNext, stringResource(Res.string.player_next), { actions.onNext(); actions.onInteract() })
}

/** The centred transport: previous, back, play, forward, next (artifact `.nj-a-transport`). */
@Composable
internal fun CenterTransport(model: PlayerChromeModel, actions: PlayerChromeActions, modifier: Modifier = Modifier, shaded: Boolean = true, playSize: Dp = 64.dp) {
    Row(modifier, Arrangement.spacedBy(22.dp), Alignment.CenterVertically) {
        PrevEpisodeButton(model, actions)
        SeekBackButton(model, actions, shaded = shaded)
        PlayButton(model, actions, playSize)
        SeekForwardButton(model, actions, shaded = shaded)
        NextEpisodeButton(model, actions)
    }
}

/** PiP and fullscreen, where the platform has them. */
@Composable
internal fun PipAndFullscreen(model: PlayerChromeModel, actions: PlayerChromeActions) {
    if (model.capabilities.pictureInPicture) ChromeIconButton(Icons.Rounded.PictureInPictureAlt, stringResource(Res.string.player_pip), actions.onPip)
    FullscreenButton(model, actions)
}

@Composable
internal fun FullscreenButton(model: PlayerChromeModel, actions: PlayerChromeActions) {
    if (!model.capabilities.fullscreenToggle) return
    ChromeIconButton(
        if (model.fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
        stringResource(if (model.fullscreen) Res.string.player_fullscreen_exit else Res.string.player_fullscreen_enter),
        actions.onToggleFullscreen,
    )
}

@Composable
internal fun BackButton(actions: PlayerChromeActions) {
    ChromeIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.player_back), actions.onBack)
}

/** "Más opciones": the Más sheet (PLY-06 / PLA-03: the old drop-down menu's actions live there now). */
@Composable
internal fun MoreButton(actions: PlayerChromeActions) {
    ChromeIconButton(Icons.Rounded.MoreVert, stringResource(Res.string.player_more_options), { actions.onOpenSheet(Sheet.More); actions.onInteract() })
}

@Composable
internal fun LockButton(model: PlayerChromeModel, actions: PlayerChromeActions) {
    if (model.capabilities.screenLock) ChromeIconButton(Icons.Rounded.Lock, stringResource(Res.string.player_lock), actions.onLock)
}

/**
 * The wide top bar's actions (artifact `deskTop`): download, screenshot, episodes ([episodes] false where the panel has them),
 * shortcuts, send to a phone, PiP, fullscreen.
 */
@Composable
internal fun RowScope.WideTopActions(model: PlayerChromeModel, actions: PlayerChromeActions, episodes: Boolean = true) {
    val state = model.state
    LocalDownloadArgs.current?.let { a -> DownloadAction(a, state.current?.candidate?.toStream(), state.current?.candidate?.label.orEmpty()) }
    if (model.capabilities.screenshot) {
        ChromeIconButton(Icons.Rounded.PhotoCamera, stringResource(Res.string.player_screenshot), actions.onScreenshot, enabled = model.ps.started)
    }
    if (episodes && model.canBrowseEpisodes) {
        ChromeIconButton(Icons.Rounded.VideoLibrary, stringResource(Res.string.player_episodes), { actions.onOpenSheet(Sheet.Episodes) })
    }
    if (model.hasKeyboard) KeyboardButton(actions)
    SendToDeviceButton(model)
    PipAndFullscreen(model, actions)
}

/** The shortcuts panel (`?`). */
@Composable
internal fun KeyboardButton(actions: PlayerChromeActions) {
    ChromeIconButton(Icons.Rounded.Keyboard, stringResource(Res.string.player_shortcuts), actions.onHelp)
}

/** PLY-27: "Enviar a dispositivo"; with several phones, a menu to pick one. */
@Composable
internal fun SendToDeviceButton(model: PlayerChromeModel) {
    val send = model.sendToDevice ?: return
    var menu by remember { mutableStateOf(false) }
    Box {
        val label = send.phones.singleOrNull()?.let { stringResource(Res.string.rd_s5_send_to, it.name) } ?: stringResource(Res.string.player_send_phone)
        ChromeIconButton(
            Icons.Outlined.SendToMobile,
            label,
            { if (send.phones.size == 1) send.send(send.phones.single()) else menu = true },
            enabled = !send.busy,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            send.phones.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { menu = false; send.send(p) }) }
        }
    }
}

/** "Audio JA · Sub ES" for the Pistas chip. */
@Composable
internal fun tracksSummary(model: PlayerChromeModel): String {
    val ps = model.ps
    val audio = ps.audioTracks.firstOrNull { it.selected }?.language?.takeIf { it.isNotBlank() }?.uppercase()
    val sub = ps.subtitles.firstOrNull { it.selected }?.language?.takeIf { it.isNotBlank() }?.uppercase()
    val subOn = ps.subtitlesEnabled || model.state.customSubtitle != null
    val subText = when {
        !subOn -> stringResource(Res.string.rd_s5_sub_none)
        sub != null -> stringResource(Res.string.rd_s5_sub_lang, sub)
        else -> null
    }
    val audioText = audio?.let { stringResource(Res.string.rd_s5_audio_lang, it) }
    return when {
        audioText != null && subText != null -> stringResource(Res.string.rd_s5_tracks_summary, audioText, subText)
        else -> audioText ?: subText ?: stringResource(Res.string.rd_s5_tracks)
    }
}

/** The Pistas chip / tab is offered once playing (load a file, delay and translation live there too). */
internal fun PlayerChromeModel.hasTracks(): Boolean = ps.started || ps.audioTracks.isNotEmpty() || ps.subtitles.isNotEmpty()

/** The sheet buttons of a wide bottom row (artifact `deskBottom` / touch pills): Pistas, Velocidad, Imagen, Más… */
@Composable
internal fun TracksChip(model: PlayerChromeModel, actions: PlayerChromeActions) {
    if (!model.hasTracks()) return
    ChromeChip(tracksSummary(model), Icons.Rounded.Subtitles, { actions.onOpenSheet(Sheet.Tracks); actions.onInteract() }, pressed = model.openSheet.isTracks())
}

@Composable
internal fun SpeedChip(model: PlayerChromeModel, actions: PlayerChromeActions) {
    val speed = speedText(model.ps.speed)
    ChromeChip(
        stringResource(Res.string.player_speed_value, speed),
        Icons.Rounded.Speed,
        { actions.onOpenSheet(Sheet.Speed); actions.onInteract() },
        pressed = model.openSheet == Sheet.Speed,
        description = stringResource(Res.string.player_speed_desc, speed),
    )
}

@Composable
internal fun PictureChip(model: PlayerChromeModel, actions: PlayerChromeActions, label: String? = null) {
    ChromeChip(
        label ?: stringResource(Res.string.rd_s5_picture),
        Icons.Rounded.AspectRatio,
        { actions.onOpenSheet(Sheet.Picture); actions.onInteract() },
        pressed = model.openSheet == Sheet.Picture || model.openSheet == Sheet.Quality,
    )
}

/** The quick framing cycle (Ajustar → Rellenar → Estirar), pressed when not Ajustar. */
@Composable
internal fun AspectChip(model: PlayerChromeModel, actions: PlayerChromeActions) {
    ChromeChip(
        stringResource(aspectLabelRes(model.aspectMode)),
        Icons.Rounded.AspectRatio,
        { actions.onCycleAspect(); actions.onInteract() },
        pressed = model.aspectMode != AspectMode.FIT,
    )
}

@Composable
internal fun EpisodesChip(model: PlayerChromeModel, actions: PlayerChromeActions) {
    if (!model.canBrowseEpisodes) return
    ChromeChip(stringResource(Res.string.player_episodes), Icons.Rounded.VideoLibrary, { actions.onOpenSheet(Sheet.Episodes) }, pressed = model.openSheet == Sheet.Episodes)
}

@Composable
internal fun NextChip(model: PlayerChromeModel, actions: PlayerChromeActions) {
    if (!model.canGoNext) return
    ChromeChip(
        stringResource(if (model.state.torrent?.batch == true) Res.string.player_torrent_batch_next else Res.string.player_next),
        Icons.Rounded.SkipNext,
        actions.onNext,
    )
}

@Composable
internal fun MoreChip(model: PlayerChromeModel, actions: PlayerChromeActions) {
    ChromeChip(stringResource(Res.string.player_more), Icons.Rounded.MoreHoriz, { actions.onOpenSheet(Sheet.More); actions.onInteract() }, pressed = model.openSheet == Sheet.More)
}

@Composable
internal fun EnhancementsMinAction(actions: PlayerChromeActions) {
    MinAction(Icons.Rounded.AutoAwesome, stringResource(Res.string.player_fx_title), { actions.onOpenSheet(Sheet.Enhancements) })
}

/** The Pistas, Mejoras, Imagen and Velocidad sheets count as one tab of the panel each. */
internal fun Sheet?.isTracks(): Boolean = this == Sheet.Tracks || this == Sheet.Subtitles || this == Sheet.Audio || this == Sheet.SubtitleStyle || this == Sheet.SubtitleStyleFull

/**
 * PLA-10: the touch gestures said on the video while the controls are up: "Desliza: brillo" on the left edge, "Desliza: volumen" on the
 * right, "Doble toque" on each half (artifact `.nj-a-rail`, `.nj-a-tapzone`). Decoration: TalkBack reads none of it.
 */
@Composable
internal fun BoxScope.GestureHints() {
    val hint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val style = MaterialTheme.typography.labelSmall
    Column(
        Modifier.align(Alignment.CenterStart).padding(start = 12.dp).clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.BrightnessMedium, contentDescription = null, tint = hint, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.rd_s5_hint_brightness), color = hint, style = style)
    }
    Column(
        Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = hint, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.rd_s5_hint_volume), color = hint, style = style)
    }
    Row(
        Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 88.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.rd_s5_hint_double_tap), color = hint, style = style)
        Text(stringResource(Res.string.rd_s5_hint_double_tap), color = hint, style = style)
    }
}

/** A row of the Más sheet's action list. */
@Composable
internal fun MoreActionRow(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, supporting: String? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = if (supporting != null) ({ Text(supporting) }) else null,
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

/** A shaded strip (the top or bottom of the stage). */
@Composable
internal fun Shade(brush: Brush, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(brush)) { content() }
}
