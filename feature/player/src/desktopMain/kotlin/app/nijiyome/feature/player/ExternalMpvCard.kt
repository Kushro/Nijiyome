// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.SendToMobile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.nijiyome.feature.player.resources.player_pause
import app.nijiyome.feature.player.resources.player_play
import app.nijiyome.feature.player.resources.rd_s5_seek_label
import app.nijiyome.feature.player.resources.rd_s5_send_to
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_back
import app.nijiyome.feature.player.resources.player_external_stop
import app.nijiyome.feature.player.resources.player_external_title
import app.nijiyome.feature.player.resources.player_xmpv_enhanced_fallback
import app.nijiyome.feature.player.resources.player_xmpv_fg
import app.nijiyome.feature.player.resources.player_xmpv_fg_capped
import app.nijiyome.feature.player.resources.player_xmpv_fg_settings
import app.nijiyome.feature.player.resources.player_xmpv_fg_why
import app.nijiyome.feature.player.resources.player_xmpv_hint
import app.nijiyome.feature.player.resources.player_xmpv_mode_app
import app.nijiyome.feature.player.resources.player_xmpv_mode_build
import app.nijiyome.feature.player.resources.player_xmpv_mode_user
import app.nijiyome.feature.player.resources.player_xmpv_paused
import app.nijiyome.feature.player.resources.player_xmpv_sources
import app.nijiyome.feature.player.resources.player_xmpv_starting
import app.nijiyome.feature.player.resources.player_xmpv_without_fg
import app.nijiyome.model.SettingsSection
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.mpv.external.ExternalMpvMode
import app.nijiyome.player.mpv.external.ExternalMpvPlayerSession
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Hito 4, paso D: what the Nijiyome window shows while mpv plays in its own window, as in Tatakai Next (tk-ui/src/screens/player/
// mod.rs, the `tk-xmpv` card, and flow.rs `pump_external`): the title, how mpv runs (its config, frame generation), the time and a
// progress bar, the torrent line, and Stop / Sources / Back. mpv has its own controls and keys; the app keeps none over the video.
// When mpv closes, the app goes back to where the player was opened from, unless the episode ended with a next one to play (the end
// screen then stays, with its countdown). Frame generation that pulled a libmpv user here explains why and offers this episode
// without it.

@Composable
internal fun ExternalMpvBranch(
    vm: PlayerViewModel,
    state: PlayerUiState,
    session: ExternalMpvPlayerSession,
    title: String,
    onBack: () -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val ps by session.state.collectAsState()
    val mode by session.mode.collectAsState()
    val closed by session.windowClosed.collectAsState()
    val back by rememberUpdatedState(onBack)
    val endUi = endScreenState(
        ended = ps.ended,
        loop = ps.loop || state.loop,
        started = ps.started,
        episode = vm.episodeNumber,
        totalEpisodes = vm.totalEpisodes,
        hasNextEpisode = state.hasNextEpisode,
        canNavigateEpisodes = state.canNavigateEpisodes,
        next = state.next,
        episodes = state.episodes.items,
    )?.takeIf { it.next != null }
    // mpv is gone and there is no next episode waiting: nothing left to show here (Tatakai Next: back to where we came from).
    LaunchedEffect(closed, endUi != null) { if (closed && endUi == null) back() }
    if (endUi != null) {
        EpisodeEndScreen(
            ui = endUi.copy(canBrowseEpisodes = false),
            seriesTitle = title,
            poster = state.poster,
            progress = null,
            onPlayNext = vm::playNext,
            onCancelCountdown = {
                vm.cancelNext()
                if (closed) back()
            },
            onReplay = vm::replay,
            onEpisodes = {},
            onDetails = null,
            onBack = onBack,
            onHide = {
                vm.cancelNext()
                if (closed) back()
            },
        )
        return
    }
    // Sparkles S5 (PLY-27): "Enviar a dispositivo" from the card; the result line shows under the buttons for a few seconds.
    var sendMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sendMessage) { if (sendMessage != null) { kotlinx.coroutines.delay(4_000); sendMessage = null } }
    val send = rememberSendToDevice(vm, LocalDownloadArgs.current, session) { sendMessage = it }
    ExternalMpvCard(
        title = title,
        playback = ps,
        // B-OCULTA-02: mpv's own window keeps its controls; the card can pause / resume and seek it too (mpv IPC through the session)
        onTogglePlay = session::togglePlay,
        onSeek = { session.seekTo(it) },
        send = send,
        sendMessage = sendMessage,
        mode = mode,
        torrentLine = state.torrent?.let { torrentLine(it) },
        canPickSource = state.sources.isNotEmpty(),
        onStop = onBack,
        onBack = onBack,
        onOpenPicker = onOpenPicker,
        onWithoutFrameGen = vm::useBuiltInPlayer,
        onFrameGenSettings = { onOpenSettings(SettingsSection.PLAYER) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExternalMpvCard(
    title: String,
    playback: PlaybackState,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    send: SendToDevice?,
    sendMessage: String?,
    mode: ExternalMpvMode,
    torrentLine: String?,
    canPickSource: Boolean,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onOpenPicker: () -> Unit,
    onWithoutFrameGen: () -> Unit,
    onFrameGenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val seekLabel = stringResource(Res.string.rd_s5_seek_label)
    Surface(Modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onBackground) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.OndemandVideo, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(56.dp))
            Text(stringResource(Res.string.player_external_title, "mpv"), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(modeLine(mode), style = MaterialTheme.typography.labelLarge, color = scheme.primary, textAlign = TextAlign.Center)
            mode.frameGenMultiplier?.let { fitted ->
                val requested = mode.frameGenRequested ?: 0
                val hz = mode.refreshHz
                if (requested > fitted && hz != null) {
                    Text(
                        stringResource(Res.string.player_xmpv_fg_capped, requested, fitted, hz.roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!playback.started && playback.error == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.player_xmpv_starting), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val playLabel = stringResource(if (playback.isPlaying) Res.string.player_pause else Res.string.player_play)
                        FilledTonalIconButton(onClick = onTogglePlay) {
                            Icon(if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = playLabel)
                        }
                        Text(
                            "${clock(playback.positionMs)} / ${clock(playback.durationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (!playback.isPlaying && !playback.isBuffering) {
                            Text(stringResource(Res.string.player_xmpv_paused), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    val duration = playback.durationMs.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = dragging ?: playback.positionMs.toFloat().coerceIn(0f, duration),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = { dragging?.let { onSeek(it.toLong()) }; dragging = null },
                        valueRange = 0f..duration,
                        enabled = playback.durationMs > 0,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel },
                    )
                }
            }
            if (torrentLine != null) {
                Text(torrentLine, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant)
            }
            if (mode.forcedByFrameGen) {
                Text(
                    stringResource(Res.string.player_xmpv_fg_why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
            if (mode.enhancedFallback != null) {
                Text(
                    stringResource(Res.string.player_xmpv_enhanced_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
            Text(
                stringResource(Res.string.player_xmpv_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            if (mode.forcedByFrameGen) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    FilledTonalButton(onClick = onWithoutFrameGen) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Text(stringResource(Res.string.player_xmpv_without_fg), Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = onFrameGenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.player_xmpv_fg_settings), Modifier.padding(start = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                Button(onClick = onStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Text(stringResource(Res.string.player_external_stop), Modifier.padding(start = 8.dp))
                }
                if (canPickSource) {
                    FilledTonalButton(onClick = onOpenPicker) {
                        Icon(Icons.Rounded.Tune, contentDescription = null)
                        Text(stringResource(Res.string.player_xmpv_sources), Modifier.padding(start = 8.dp))
                    }
                }
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Text(stringResource(Res.string.player_back), Modifier.padding(start = 8.dp))
                }
                send?.phones?.forEach { phone ->
                    TextButton(onClick = { send.send(phone) }, enabled = !send.busy) {
                        Icon(Icons.Outlined.SendToMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.rd_s5_send_to, phone.name), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (sendMessage != null) {
                Text(sendMessage, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun modeLine(mode: ExternalMpvMode): String {
    val config = when (mode.config) {
        ExternalMpvMode.Kind.USER_CONFIG -> stringResource(Res.string.player_xmpv_mode_user)
        ExternalMpvMode.Kind.ANIME_BUILD -> stringResource(Res.string.player_xmpv_mode_build)
        ExternalMpvMode.Kind.APP_CONFIG -> stringResource(Res.string.player_xmpv_mode_app)
    }
    val fg = mode.frameGenMultiplier?.let { stringResource(Res.string.player_xmpv_fg, it) }
    return listOfNotNull(config, fg).joinToString(" · ")
}

/** `m:ss` or `h:mm:ss`. */
internal fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
