// SPDX-License-Identifier: AGPL-3.0-or-later
// The desktop player and Nijiyome Link (after Tatakai Next tk-app/src/live/link.rs `note_presence` / `remote_vm` / `send_to_device`,
// AGPL-3.0, same author). Desktop only: Android's player is a Link client and has its own "Enviar a dispositivo" in Details.
package app.nijiyome.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SendToMobile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_send_phone
import app.nijiyome.feature.player.resources.player_send_phone_done
import app.nijiyome.feature.player.resources.player_send_phone_err_denied
import app.nijiyome.feature.player.resources.player_send_phone_err_device
import app.nijiyome.feature.player.resources.player_send_phone_err_internal
import app.nijiyome.feature.player.resources.player_send_phone_err_offline
import app.nijiyome.feature.player.resources.player_send_phone_err_source
import app.nijiyome.feature.player.resources.player_send_phone_failed
import app.nijiyome.feature.player.resources.player_send_phone_sending
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.LinkSource
import app.nijiyome.model.PcPlayerHub
import app.nijiyome.model.PcPlayerState
import app.nijiyome.model.PcRemoteCommand
import app.nijiyome.model.PhoneTarget
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.SendToPhone
import app.nijiyome.model.SendToPhoneRequest
import app.nijiyome.model.SourceKind
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import java.net.InetAddress
import java.net.URI

/**
 * The desktop player screen [content] plus Nijiyome Link: while it is open it is the PC's player for paired phones (its state answers
 * `GET /v1/player`, `POST /v1/control` drives it, embedded libmpv and external mpv alike: both are a [PlayerSession]), and, when a
 * phone is paired, a small "Enviar al teléfono" button (shown with the pointer, like the controls) hands the episode over at the
 * current position. Without the Link services in the graph it is just [content].
 */
@Composable
internal fun DesktopLinkRemote(vm: PlayerViewModel, args: PlayerArgs, content: @Composable () -> Unit) {
    val koin = getKoin()
    val hub = remember(koin) { koin.getOrNull<PcPlayerHub>() }
    val sender = remember(koin) { koin.getOrNull<SendToPhone>() }
    val state by vm.state.collectAsState()
    val session = state.session
    val scope = rememberCoroutineScope()

    if (hub != null) {
        val owner = remember { Any() }
        DisposableEffect(hub, owner) { onDispose { hub.detach(owner) } }
        DisposableEffect(hub, session) {
            hub.attach(owner) { cmd ->
                val s = session ?: return@attach false
                scope.launch(Dispatchers.Main) { applyRemote(cmd, vm, s) }
                true
            }
            onDispose { }
        }
        LaunchedEffect(hub, session) {
            val s = session ?: return@LaunchedEffect hub.publish(owner, null)
            s.state.collect { pb -> hub.publish(owner, playerState(args, pb)) }
        }
    }

    if (sender == null) {
        content()
        return
    }
    val phones by sender.phones.collectAsState()
    var lastMove by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<SendStatus?>(null) }
    LaunchedEffect(lastMove, status) {
        now = System.currentTimeMillis()
        delay(SHOW_MS)
        now = System.currentTimeMillis()
    }
    Box(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    lastMove = System.currentTimeMillis()
                }
            }
        },
    ) {
        content()
        val source = sendableSource(state, args)
        val visible = phones.isNotEmpty() && source != null && (menu || status != null || now - lastMove < SHOW_MS)
        if (visible && source != null) {
            fun send(phone: PhoneTarget) {
                menu = false
                val position = ((session?.currentPositionMs() ?: args.startMs) / 1000).toInt().coerceAtLeast(0)
                status = SendStatus.Sending
                scope.launch {
                    val r = sender.send(
                        phone.id,
                        SendToPhoneRequest(
                            mediaId = HistoryEntry.animeIdOf(args.media.anilistId, args.media.malId),
                            episode = args.episode,
                            positionSecs = position,
                            title = args.title,
                            source = source,
                        ),
                    )
                    status = r.fold({ SendStatus.Done(phone.name) }, { SendStatus.Failed(it.message ?: "") })
                    if (r.isSuccess) session?.pause()
                    delay(SHOW_MS)
                    status = null
                }
            }
            val label = stringResource(Res.string.player_send_phone)
            Column(Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp), horizontalAlignment = Alignment.End) {
                Box {
                    FilledTonalIconButton(
                        onClick = { if (phones.size == 1) send(phones.single()) else menu = true },
                        enabled = status != SendStatus.Sending,
                        modifier = Modifier.semantics { contentDescription = label },
                    ) { Icon(Icons.Outlined.SendToMobile, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        for (p in phones) DropdownMenuItem(text = { Text(p.name) }, onClick = { send(p) })
                    }
                }
                status?.let { st ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            when (st) {
                                SendStatus.Sending -> stringResource(Res.string.player_send_phone_sending)
                                is SendStatus.Done -> stringResource(Res.string.player_send_phone_done, st.name)
                                is SendStatus.Failed -> stringResource(Res.string.player_send_phone_failed, localizedSendError(st.code))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val SHOW_MS = 3_000L

private sealed interface SendStatus {
    data object Sending : SendStatus
    data class Done(val name: String) : SendStatus
    data class Failed(val code: String) : SendStatus
}

@Composable
private fun localizedSendError(code: String): String = when (code) {
    "internal" -> stringResource(Res.string.player_send_phone_err_internal)
    "source_unavailable", "library_item_not_found" -> stringResource(Res.string.player_send_phone_err_source)
    "url_source_denied" -> stringResource(Res.string.player_send_phone_err_denied)
    "link_off" -> stringResource(Res.string.player_send_phone_err_offline)
    "device_not_authorized" -> stringResource(Res.string.player_send_phone_err_device)
    else -> code.ifBlank { stringResource(Res.string.player_send_phone_err_internal) }
}

/** A phone's command on the open player (main thread). Next / previous only when there is one, like the controls. */
internal fun applyRemote(cmd: PcRemoteCommand, vm: PlayerViewModel, session: PlayerSession) {
    when (cmd) {
        PcRemoteCommand.Play -> session.play()
        PcRemoteCommand.Pause -> session.pause()
        PcRemoteCommand.Toggle -> session.togglePlay()
        is PcRemoteCommand.Seek -> session.seekTo(cmd.seconds * 1000)
        PcRemoteCommand.Next -> if (vm.state.value.hasNextEpisode) vm.playNext()
        PcRemoteCommand.Prev -> if (vm.state.value.hasPrevEpisode) vm.playPrevious()
        is PcRemoteCommand.Open -> Unit // the shell opens the player (PcPlayerHub.opens)
    }
}

/** What `GET /v1/player` reports for this screen. Pure. */
internal fun playerState(args: PlayerArgs, pb: PlaybackState): PcPlayerState = PcPlayerState(
    playing = pb.isPlaying,
    title = args.title.takeIf { it.isNotBlank() },
    episode = args.episode.takeIf { it > 0 },
    positionSecs = (pb.positionMs / 1000).toInt().coerceAtLeast(0),
    durationSecs = (pb.durationMs / 1000).toInt().takeIf { it > 0 },
    mediaId = HistoryEntry.animeIdOf(args.media.anilistId, args.media.malId),
)

/**
 * The source a phone's Link session can read for what plays now (tk-app `session_source` / the torrent first): the torrent (its
 * magnet or info-hash; the PC streams it with its own engine) or a public http(s) stream with its headers. Local files, loopback
 * streams and embed pages cannot be sent: null. Pure.
 */
internal fun sendableSource(state: PlayerUiState, args: PlayerArgs): LinkSource? {
    if (args.localUri != null) return null
    val animeId = HistoryEntry.animeIdOf(args.media.anilistId, args.media.malId) ?: "0"
    args.torrentId?.let { return LinkSource.Torrent(it, animeId, args.episode) }
    val c = state.current?.candidate ?: return null
    return when (c.kind) {
        SourceKind.TORRENT -> c.torrent?.let { t -> LinkSource.Torrent(t.magnet ?: t.infoHash, animeId, args.episode) }
        else -> c.toStream()?.takeIf { publicHttp(it.url) }?.let { LinkSource.Url(it.url, it.headers) }
    }
}

/** http(s) to a host that is not this machine (the phone's session fetches it through the PC's SSRF-safe fetcher). Pure. */
internal fun publicHttp(url: String): Boolean {
    val u = runCatching { URI(url) }.getOrNull() ?: return false
    if (u.scheme?.lowercase() !in setOf("http", "https")) return false
    val host = u.host?.trim('[', ']')?.lowercase() ?: return false
    if (host == "localhost" || host.endsWith(".localhost")) return false
    val literal = host.all { it.isDigit() || it == '.' } || ':' in host
    return !(literal && runCatching { InetAddress.getByName(host).isLoopbackAddress }.getOrDefault(true))
}
