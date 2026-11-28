// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.nijiyome.feature.link

import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.feature.link.resources.Res
import app.nijiyome.feature.link.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.LinkStatusAvatar
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.motionFadeIn
import app.nijiyome.designsystem.motion.motionFadeOut
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.link.LinkHistoryMode
import app.nijiyome.model.DiscoveredPc
import app.nijiyome.model.LinkHandoff
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.LinkState
import app.nijiyome.model.LinkStructure
import app.nijiyome.model.LoadState
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.RemoteAction
import app.nijiyome.ui.format.relativeTime
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.platform.rememberNotificationPermission
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.delay
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.designsystem.scroll.njVerticalScroll

/**
 * Nijiyome Link (Sparkles S9; artifact `screens-c.js` screen `link`), its own screen in the two structures of `structures.link`:
 * [LinkStructure.MAP] (a map of this device and the paired ones with the wire between them, in the direction's `LINK` region) and
 * [LinkStructure.PANEL] (status cards, one per service, with their main action). Name and signature kept for the shell
 * (Route.Link / Route.Linked); the new parameters have defaults.
 *
 * - On a Link client (Android: `LinkRepository` registered) it is the phone side: pairing (QR, code, PCs on the network), connection
 *   with latency and recovery, hand-off, remote control, history sync, the enhancement profile with "Mejorar un stream" inside it,
 *   the PC's extension servers and forgetting the PC.
 * - On a Link server (desktop: `LinkServerController` registered, no `LinkRepository`) it is the PC side ([LinkServerScreen]): what this
 *   PC serves, the paired phones, the sessions (monitor), the profile offer, both servers and pairing windows. It resolves only what the
 *   desktop registers, so it is safe to open there.
 *
 * [onOpenLogs] opens the device logs (Settings › About › Logs); [onOpenLinkSettings], [onOpenMonitor] and [onOpenTorrents] are the
 * desktop's ways to the Link settings, the full Link monitor dialog and Transfers. Null hides each entry.
 */
@Composable
fun LinkedDeviceScreen(
    /** Null on a rail destination (wide windows): no back arrow next to the title (LNK-03). */
    onBack: (() -> Unit)?,
    onPlay: (PlayerArgs) -> Unit,
    modifier: Modifier = Modifier,
    /** docs/redesign/13 §3.10: opens the PC extension server screen; null hides its entry card. */
    onOpenExtensionServers: (() -> Unit)? = null,
    /** Sparkles S9: device logs (Settings › About › Logs). */
    onOpenLogs: (() -> Unit)? = null,
    /** Sparkles S9 (desktop): Settings › Nijiyome Link. */
    onOpenLinkSettings: (() -> Unit)? = null,
    /** Sparkles S9 (desktop): the full Link and system monitor (disk, app memory). */
    onOpenMonitor: (() -> Unit)? = null,
    /** Sparkles S9 (desktop): Transfers, from the monitor's active torrents. */
    onOpenTorrents: (() -> Unit)? = null,
) {
    val koin = getKoin()
    // Which side of Link this build is. Nothing is created that the platform does not register (desktop has no LinkRepository).
    val client = remember(koin) { runCatching { koin.getOrNull<LinkRepository>() }.getOrNull() != null }
    val server = remember(koin) { if (client) null else runCatching { koin.getOrNull<LinkServerController>() }.getOrNull() }
    when {
        client -> LinkClientScreen(onBack, onPlay, modifier, onOpenExtensionServers, onOpenLogs)
        server != null -> LinkServerScreen(server, onBack, modifier, onOpenLinkSettings, onOpenMonitor, onOpenTorrents, onOpenLogs, onOpenExtensionServers)
        else -> LinkUnavailable(onBack, modifier)
    }
}

@Composable
private fun LinkUnavailable(onBack: (() -> Unit)?, modifier: Modifier) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = { LinkTopBar(onBack = onBack, meta = null) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            EmptyArt(EmptyKind.LINK, Modifier.size(120.dp))
            Text(stringResource(Res.string.rd_s9_link_unavailable), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun LinkTopBar(onBack: (() -> Unit)?, meta: String?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(Res.string.rd_s9_link_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
            }
        },
        actions = { actions() },
    )
}

// ================================================================ client (phone)

@Composable
private fun LinkClientScreen(
    onBack: (() -> Unit)?,
    onPlay: (PlayerArgs) -> Unit,
    modifier: Modifier,
    onOpenExtensionServers: (() -> Unit)?,
    onOpenLogs: (() -> Unit)?,
) {
    val viewModel: LinkViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnPlay by rememberUpdatedState(onPlay)
    val snackbar = LocalAppSnackbar.current
    val handedOff = stringResource(Res.string.linked_continue_done)
    val failedPrefix = stringResource(Res.string.linked_action_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LinkEvent.NavigateToPlayer -> currentOnPlay(event.args)
                LinkEvent.HandedOff -> snackbar.show(handedOff)
                is LinkEvent.Failed -> snackbar.show(failedPrefix.format(linkErrorText(event.error)))
            }
        }
    }
    // parity wave B: the "Enviar a dispositivo" notification needs POST_NOTIFICATIONS; asked here once a PC is paired (16 §0.12).
    val notifyPermission = rememberNotificationPermission()
    val paired = state.link is LinkState.Paired
    LaunchedEffect(paired) {
        if (paired && !notifyPermission.isGranted()) notifyPermission.request()
    }
    // parity wave B: GET /v1/player every 2 s while this screen is visible (link.remote-control); it also measures the latency.
    LaunchedEffect(viewModel, paired) { if (paired) viewModel.pollRemote() }
    val actions = ClientActions(
        onPair = viewModel::pair,
        isValid = viewModel::isValidPairingText,
        onPairDiscovered = viewModel::pairDiscovered,
        fingerprintOf = viewModel::fingerprintOf,
        canPairDiscovered = viewModel.canPairDiscovered,
        onRetry = viewModel::retryCapabilities,
        onForget = viewModel::forget,
        onSelectProfile = viewModel::selectProfile,
        onStart = viewModel::startSession,
        onContinue = viewModel::continueOnPc,
        onRemote = viewModel::remote,
        onPlayHandoff = viewModel::playHandoff,
        onDismissHandoff = viewModel::dismissHandoff,
        onSyncHistory = if (viewModel.canSyncHistory) viewModel::syncHistoryNow else null,
        onOpenExtensionServers = onOpenExtensionServers,
    )
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            LinkTopBar(onBack = onBack, meta = clientMeta(state)) {
                if (onOpenLogs != null) {
                    IconButton(onClick = onOpenLogs) { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = stringResource(Res.string.rd_s9_link_logs)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 960.dp).fillMaxWidth().njVerticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // The one switch of this screen on its own structure (CONTRATO-COMPOSE: once, at the root).
                when (structures.link) {
                    LinkStructure.MAP -> ClientMapLayout(state, actions)
                    LinkStructure.PANEL -> ClientPanelLayout(state, actions)
                }
            }
        }
    }
}

/** Everything the client panels can do, shared by both structures. */
internal class ClientActions(
    val onPair: (String) -> Unit,
    val isValid: (String) -> Boolean,
    val onPairDiscovered: (DiscoveredPc, String) -> Unit,
    val fingerprintOf: (DiscoveredPc) -> String?,
    val canPairDiscovered: Boolean,
    val onRetry: () -> Unit,
    val onForget: () -> Unit,
    val onSelectProfile: (String?) -> Unit,
    val onStart: (String) -> Unit,
    val onContinue: () -> Unit,
    val onRemote: (RemoteAction) -> Unit,
    val onPlayHandoff: () -> Unit,
    val onDismissHandoff: () -> Unit,
    val onSyncHistory: (() -> Unit)?,
    val onOpenExtensionServers: (() -> Unit)?,
)

@Composable
private fun clientMeta(state: LinkUiState): String? = when (val l = state.link) {
    is LinkState.Paired -> listOfNotNull(
        state.device?.serverName ?: l.pc.serverName,
        stringResource(if (l.reachable) Res.string.linked_connected else Res.string.linked_unreachable),
        state.latencyMs?.takeIf { l.reachable }?.let { stringResource(Res.string.rd_s9_link_latency, it) },
    ).joinToString(" · ")
    else -> null
}

/** MAP: the phone and its PC joined by the wire (latency on it), then the panels. */
@Composable
private fun ClientMapLayout(state: LinkUiState, a: ClientActions) {
    when (val link = state.link) {
        LinkState.Unpaired, LinkState.Pairing -> UnpairedContent(state, link == LinkState.Pairing, a)
        is LinkState.Failed -> FailedPanel(link.message, a)
        is LinkState.Paired -> {
            state.pendingHandoff?.let { h -> HandoffCard(h, onPlay = a.onPlayHandoff, onDismiss = a.onDismissHandoff) }
            val phone = stringResource(Res.string.rd_s9_link_this_phone)
            val pcName = state.device?.serverName ?: link.pc.serverName
            val pcState = if (link.reachable) MapNodeState.CONNECTED else MapNodeState.FAILED
            DeviceMap(
                hub = MapNode("phone", phone, state.device?.localDeviceName, Icons.Outlined.PhoneAndroid, pcState),
                nodes = listOf(MapNode("pc", pcName, stringResource(if (link.reachable) Res.string.linked_connected else Res.string.linked_unreachable), Icons.Outlined.Computer, pcState)),
                wireLabel = wireLabel(state, link.reachable),
                description = stringResource(Res.string.rd_s9_link_map_description, phone, pcName),
            )
            PairedPanels(state, link, a)
        }
    }
}

/** PANEL: a card per service with its main action, then the panels. A failed link opens on its recovery. */
@Composable
private fun ClientPanelLayout(state: LinkUiState, a: ClientActions) {
    when (val link = state.link) {
        LinkState.Unpaired, LinkState.Pairing -> UnpairedContent(state, link == LinkState.Pairing, a)
        is LinkState.Failed -> FailedPanel(link.message, a)
        is LinkState.Paired -> {
            state.pendingHandoff?.let { h -> HandoffCard(h, onPlay = a.onPlayHandoff, onDismiss = a.onDismissHandoff) }
            val online = link.reachable
            val cards = mutableListOf<@Composable (Modifier) -> Unit>()
            cards.add { m ->
                StatCard(
                    icon = Icons.Outlined.Link,
                    title = state.device?.serverName ?: link.pc.serverName,
                    state = stringResource(if (online) Res.string.linked_connected else Res.string.linked_unreachable),
                    tone = if (online) PillTone.OK else PillTone.ERR,
                    text = state.latencyMs?.takeIf { online }?.let { stringResource(Res.string.rd_s9_link_latency, it) } ?: stringResource(Res.string.linked_unreachable_hint),
                    modifier = m,
                ) {
                    if (online) {
                        TextButton(onClick = a.onContinue, enabled = state.continueEntry != null && !state.handingOff, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.linked_continue)) }
                    } else {
                        TextButton(onClick = a.onRetry, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_retry)) }
                    }
                }
            }
            cards.add { m ->
                val caps = (state.capabilities as? LoadState.Ok)?.value
                val current = caps?.profiles?.firstOrNull { it.id == (state.selectedProfile ?: caps.defaultProfile) }
                StatCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(Res.string.linked_profile),
                    state = current?.let { sustainText(it) } ?: stringResource(Res.string.rd_s9_link_profile_unknown),
                    tone = if (current?.measuredSustain == "true") PillTone.OK else PillTone.NEUTRAL,
                    text = current?.let { profileLabel(it) } ?: stringResource(Res.string.rd_s9_link_profile_default),
                    modifier = m,
                ) {}
            }
            cards.add { m ->
                StatCard(
                    icon = Icons.Outlined.History,
                    title = stringResource(Res.string.rd_s9_link_history),
                    state = stringResource(historyStateRes(state)),
                    tone = if (state.historyMode == LinkHistoryMode.PAUSED) PillTone.WARN else PillTone.OK,
                    text = state.lastHistorySyncMs?.let { stringResource(Res.string.rd_s9_link_history_synced, relativeTime(it)) } ?: stringResource(Res.string.rd_s9_link_history_not_yet),
                    modifier = m,
                ) {
                    a.onSyncHistory?.let { sync -> TextButton(onClick = sync, enabled = !state.historySyncing, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s9_link_sync_now)) } }
                }
            }
            cards.add { m ->
                StatCard(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = stringResource(Res.string.linked_remote),
                    state = stringResource(
                        when {
                            state.remote == null -> Res.string.rd_s9_link_remote_idle_short
                            state.remote.playing -> Res.string.linked_remote_playing
                            else -> Res.string.linked_remote_paused
                        },
                    ),
                    tone = if (state.remote?.playing == true) PillTone.OK else PillTone.NEUTRAL,
                    text = state.remote?.title ?: stringResource(Res.string.linked_remote_idle),
                    modifier = m,
                ) {
                    val remote = state.remote
                    if (remote != null) {
                        TextButton(onClick = { a.onRemote(RemoteAction.PLAY_PAUSE) }, enabled = !state.remoteBusy, shape = NijiyomeControls.shape) {
                            Text(stringResource(if (remote.playing) Res.string.linked_remote_pause else Res.string.linked_remote_play))
                        }
                    }
                }
            }
            a.onOpenExtensionServers?.let { open ->
                cards.add { m ->
                    StatCard(
                        icon = Icons.Outlined.Extension,
                        title = stringResource(Res.string.extserver_entry_title),
                        state = stringResource(Res.string.rd_s9_link_servers_state),
                        tone = PillTone.NEUTRAL,
                        text = stringResource(Res.string.extserver_entry_body),
                        modifier = m,
                    ) { TextButton(onClick = open, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s9_link_manage)) } }
                }
            }
            StatCardGrid(cards)
            PairedPanels(state, link, a)
        }
    }
}

/** One status card of the PANEL structure: icon, title, state pill, one line and its main action. */
@Composable
internal fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    state: String,
    tone: PillTone,
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
                Pill(state, tone = tone)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row { action() }
        }
    }
}

private fun historyStateRes(state: LinkUiState) = when {
    state.historyResyncing -> Res.string.linked_history_resyncing
    state.historyMode == LinkHistoryMode.RECEIVE_ONLY -> Res.string.rd_s9_link_history_receive
    state.historyMode == LinkHistoryMode.PAUSED -> Res.string.rd_s9_link_history_paused
    else -> Res.string.rd_s9_link_history_full
}

@Composable
private fun wireLabel(state: LinkUiState, reachable: Boolean): String? {
    if (!reachable) return stringResource(Res.string.linked_unreachable)
    val ms = state.latencyMs ?: return stringResource(Res.string.rd_s9_link_https)
    return stringResource(Res.string.rd_s9_link_wire, ms)
}

/** The panels under the map or the cards: connection, remote, history, profile (with Mejorar un stream), PC extensions, forget. */
@Composable
private fun PairedPanels(state: LinkUiState, paired: LinkState.Paired, a: ClientActions) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusCard(state, paired, a)
                    RemotePanel(state, onRemote = a.onRemote)
                    HistoryPanel(state, a)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProfilePanel(state, a)
                    a.onOpenExtensionServers?.let { ExtensionServerCard(onOpen = it) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusCard(state, paired, a)
                RemotePanel(state, onRemote = a.onRemote)
                HistoryPanel(state, a)
                ProfilePanel(state, a)
                a.onOpenExtensionServers?.let { ExtensionServerCard(onOpen = it) }
            }
        }
    }
}

// ---------------------------------------------------------------- unpaired

@Composable
private fun UnpairedContent(state: LinkUiState, linkPairing: Boolean, a: ClientActions) {
    val pairing = state.pairing || linkPairing
    var scanning by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    val permission = rememberCameraPermission { granted ->
        denied = !granted
        scanning = granted
    }
    Text(stringResource(Res.string.linked_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(
        Modifier.fillMaxWidth().decor(DecorRegion.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmptyArt(EmptyKind.LINK, Modifier.size(120.dp))
        Text(stringResource(Res.string.linked_none), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.rd_s9_link_none_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Panel(icon = Icons.Outlined.QrCode, title = stringResource(Res.string.linked_pair_title)) {
        if (scanning) {
            QrScanner(
                onResult = {
                    scanning = false
                    a.onPair(it)
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large),
            )
            TextButton(onClick = { scanning = false }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.linked_scan_cancel)) }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (qrCameraAvailable) {
                    Button(onClick = { if (permission.isGranted()) scanning = true else permission.request() }, enabled = !pairing, shape = NijiyomeControls.shape) {
                        if (pairing) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(if (pairing) Res.string.linked_pairing else Res.string.linked_scan), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                FilledTonalButton(onClick = { pasting = true }, enabled = !pairing, shape = NijiyomeControls.shape) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(Res.string.linked_paste), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (denied) Text(stringResource(Res.string.linked_camera_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        // Pairing failures with their recovery: an expired code asks the PC for a new one, a wrong code is checked again...
        state.pairProblem?.let { p ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(pairProblemTitle(p)), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(pairProblemBody(p)), style = MaterialTheme.typography.bodySmall)
                }
            }
        } ?: state.pairError?.let { Text(it.userMessage(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        Hint(stringResource(Res.string.linked_camera))
    }
    DiscoveredPanel(
        pcs = LinkLogic.others(state.discovered, null, null),
        pairing = pairing,
        enabled = a.canPairDiscovered,
        fingerprintOf = a.fingerprintOf,
        onPair = a.onPairDiscovered,
    )
    WhyLinkPanel()
    if (pasting) {
        PasteCodeDialog(
            isValid = a.isValid,
            onDismiss = { pasting = false },
            onPair = {
                pasting = false
                a.onPair(it)
            },
        )
    }
}

private fun pairProblemTitle(p: PairProblem) = when (p) {
    PairProblem.EXPIRED -> Res.string.rd_s9_link_pair_expired_title
    PairProblem.WRONG_CODE -> Res.string.rd_s9_link_pair_wrong_title
    PairProblem.TOO_MANY -> Res.string.rd_s9_link_pair_toomany_title
    PairProblem.UNREACHABLE -> Res.string.rd_s9_link_pair_unreachable_title
}

private fun pairProblemBody(p: PairProblem) = when (p) {
    PairProblem.EXPIRED -> Res.string.rd_s9_link_pair_expired_body
    PairProblem.WRONG_CODE -> Res.string.rd_s9_link_pair_wrong_body
    PairProblem.TOO_MANY -> Res.string.rd_s9_link_pair_toomany_body
    PairProblem.UNREACHABLE -> Res.string.rd_s9_link_pair_unreachable_body
}

/** "¿Para qué enlazar el teléfono con la PC?" folded (LNK-16 style: long fixed text becomes a collapsible). */
@Composable
private fun WhyLinkPanel() {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().toggleable(value = open, role = Role.Button, onValueChange = { open = it }).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.rd_s9_link_why_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(open, enter = motionFadeIn(), exit = motionFadeOut()) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Res.string.rd_s9_link_why_1,
                        Res.string.rd_s9_link_why_2,
                        Res.string.rd_s9_link_why_3,
                        Res.string.rd_s9_link_why_4,
                        Res.string.rd_s9_link_why_5,
                    ).forEach { res ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasteCodeDialog(isValid: (String) -> Boolean, onDismiss: () -> Unit, onPair: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val invalid = showError && !isValid(code)
    val readClipboard = rememberClipboardReader()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.QrCode, contentDescription = null) },
        title = { Text(stringResource(Res.string.linked_paste)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.linked_paste_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        showError = false
                    },
                    label = { Text(stringResource(Res.string.linked_code_label)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(Res.string.linked_paste_invalid)) }
                    } else {
                        null
                    },
                    trailingIcon = {
                        IconButton(onClick = { readClipboard()?.let { code = it; showError = false } }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(Res.string.rd_s9_link_paste_clipboard))
                        }
                    },
                    textStyle = TextStyle(fontFamily = NijiyomeFonts.jetBrainsMono),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (isValid(code)) onPair(code.trim()) else showError = true }, enabled = code.isNotBlank(), shape = NijiyomeControls.shape) {
                Text(stringResource(Res.string.linked_pair))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

// ---------------------------------------------------------------- failed

/** LNK-11: the link failed. What may be wrong, then Reintentar and Olvidar PC. */
@Composable
private fun FailedPanel(message: String, a: ClientActions) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, LocalSignalColors.current.err),
        modifier = Modifier.fillMaxWidth().decor(DecorRegion.LINK),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.LinkOff, contentDescription = null, tint = LocalSignalColors.current.err, modifier = Modifier.size(32.dp))
            Text(stringResource(Res.string.linked_problem), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RecoveryTips()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = a.onRetry, shape = NijiyomeControls.shape) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(UiR.string.ui_retry), modifier = Modifier.padding(start = 8.dp))
                }
                ForgetButton(a.onForget)
            }
        }
    }
}

@Composable
private fun RecoveryTips() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Res.string.rd_s9_link_tip_wifi, Res.string.rd_s9_link_tip_open, Res.string.rd_s9_link_tip_tunnel).forEach { res ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodySmall)
                Text(stringResource(res), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------------------------------------------------------- paired

@Composable
private fun StatusCard(state: LinkUiState, paired: LinkState.Paired, a: ClientActions) {
    val online = paired.reachable
    val device = state.device
    Surface(
        color = if (online) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (online) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LinkStatusAvatar(connected = online, icon = Icons.Outlined.Computer)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(if (online) Res.string.linked_connected else Res.string.linked_unreachable), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${device?.serverName ?: paired.pc.serverName} · https://${paired.pc.host}:${paired.pc.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (online) {
                        state.latencyMs?.let { ms ->
                            Text(
                                stringResource(
                                    when (LinkLogic.latencyTone(ms)) {
                                        LatencyTone.GOOD -> Res.string.rd_s9_link_latency_good
                                        LatencyTone.FAIR -> Res.string.rd_s9_link_latency_fair
                                        else -> Res.string.rd_s9_link_latency_slow
                                    },
                                    ms,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    device?.fingerprint?.let { fp ->
                        // BUG-090 / BUG-105: the label in the normal font on its own line, only the fingerprint in monospace below; "Ver
                        // detalles" shows the whole one in rows of 4 groups.
                        var full by rememberSaveable { mutableStateOf(false) }
                        val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono)
                        Column(Modifier.padding(top = 4.dp)) {
                            Text(stringResource(Res.string.linked_fingerprint_label), style = MaterialTheme.typography.bodySmall)
                            if (full) {
                                LinkLogic.fingerprintRows(fp).forEach { Text(it, style = mono, maxLines = 1, softWrap = false) }
                            } else {
                                Text(LinkLogic.shortFingerprint(fp), style = mono, maxLines = 1, softWrap = false)
                            }
                            TextButton(
                                onClick = { full = !full },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                                shape = NijiyomeControls.shape,
                            ) {
                                Text(
                                    stringResource(if (full) Res.string.linked_fingerprint_hide else Res.string.linked_fingerprint_show),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = TextDecoration.Underline,
                                )
                            }
                        }
                    }
                    device?.lastSeenMs?.let {
                        // Re-read every 30 s so "visto hace N min" stays true while the screen is open (link.last-seen).
                        val tick by produceState(0, it) { while (true) { delay(30_000); value++ } }
                        val label = remember(it, tick) { it }
                        Text(stringResource(Res.string.linked_last_seen, relativeTime(label)), style = MaterialTheme.typography.bodySmall)
                    }
                    device?.localDeviceName?.let {
                        Text(stringResource(Res.string.linked_registered_as, it), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!online) {
                Text(stringResource(Res.string.linked_unreachable_hint), style = MaterialTheme.typography.bodySmall)
                RecoveryTips()
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (online) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = a.onContinue, enabled = state.continueEntry != null && !state.handingOff, shape = NijiyomeControls.shape) {
                            if (state.handingOff) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.Outlined.Cast, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text(stringResource(Res.string.linked_continue), modifier = Modifier.padding(start = 8.dp))
                        }
                        Text(
                            state.continueEntry?.let { e ->
                                stringResource(Res.string.linked_continue_what, e.title, e.episode, LinkLogic.clock(e.progressSec, null))
                            } ?: stringResource(Res.string.linked_continue_none),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    FilledTonalButton(onClick = a.onRetry, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(UiR.string.ui_retry), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            // BUG-090: forgetting the PC belongs to this card, as a secondary destructive action on its own line.
            ForgetButton(a.onForget)
        }
    }
}

/** History sync with the PC: how the PC treats this phone's history, a running resync, the last sync and "Sincronizar ahora". */
@Composable
private fun HistoryPanel(state: LinkUiState, a: ClientActions) {
    Panel(icon = Icons.Outlined.History, title = stringResource(Res.string.rd_s9_link_history), trailing = { Pill(stringResource(historyStateRes(state))) }) {
        Text(
            state.lastHistorySyncMs?.let { stringResource(Res.string.rd_s9_link_history_synced, relativeTime(it)) } ?: stringResource(Res.string.rd_s9_link_history_not_yet),
            style = MaterialTheme.typography.bodyMedium,
        )
        // 2026-11 history per device: how the PC treats this phone's history, and a running resync
        LinkLogic.historyNotices(state.historyMode, state.historyResyncing).forEach { notice ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notice == HistoryNotice.RESYNCING) LoadingIndicator(Modifier.size(16.dp))
                Hint(
                    stringResource(
                        when (notice) {
                            HistoryNotice.RECEIVE_ONLY -> Res.string.linked_history_receive_only
                            HistoryNotice.PAUSED -> Res.string.linked_history_paused
                            HistoryNotice.RESYNCING -> Res.string.linked_history_resyncing
                        },
                    ),
                )
            }
        }
        a.onSyncHistory?.let { sync ->
            OutlinedButton(onClick = sync, enabled = !state.historySyncing, shape = NijiyomeControls.shape) {
                if (state.historySyncing) LoadingIndicator(Modifier.size(18.dp)) else Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(if (state.historySyncing) Res.string.rd_s9_link_syncing else Res.string.rd_s9_link_sync_now), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * Perfil de mejora (LNK-06) with its two axes, the PC's capabilities, and "Mejorar un stream por URL" inside it (contract decision:
 * Link › Perfil de mejora; "Procesar en" stays in the player's Mejoras sheet).
 */
@Composable
private fun ProfilePanel(state: LinkUiState, a: ClientActions) {
    Panel(icon = Icons.Outlined.AutoAwesome, title = stringResource(Res.string.linked_profile)) {
        Hint(stringResource(Res.string.rd_s9_link_profile_hint))
        when (val caps = state.capabilities) {
            null, LoadState.Loading -> if (rememberAfterDelay(150)) {
                SkeletonBox(Modifier.fillMaxWidth().aspectRatio(5f))
                SkeletonBox(Modifier.fillMaxWidth(0.6f).aspectRatio(8f))
            }
            is LoadState.Err -> app.nijiyome.designsystem.components.ErrorState(
                message = caps.cause.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = a.onRetry,
                compact = true,
            )
            is LoadState.Ok -> {
                val c = caps.value
                Cap(stringResource(Res.string.linked_caps_server), "${c.serverName} · ${c.serverVersion}")
                Cap(stringResource(Res.string.linked_caps_frame_gen), stringResource(if (c.clientFrameGenerationAllowed) Res.string.linked_yes else Res.string.linked_no))
                // Prototype: profiles measured as not sustainable are hidden.
                val usable = c.profiles.filter { it.measuredSustain != "false" }
                if (usable.isEmpty()) {
                    Hint(stringResource(Res.string.linked_profiles_none))
                } else {
                    // BUG-132: the profiles this phone cannot decode stay visible, disabled with the reason; never the selected one
                    val playable = usable.filter { it.id !in state.unsupportedProfiles }
                    val current = state.selectedProfile?.takeIf { id -> playable.any { it.id == id } }
                        ?: c.defaultProfile.takeIf { id -> playable.any { it.id == id } }
                        ?: (playable.firstOrNull() ?: usable.first()).id
                    ProfileAxisPicker(
                        profiles = usable,
                        selected = current,
                        unavailable = state.unsupportedProfiles,
                        mayDropFrames = state.mayDropFramesProfiles,
                        fallback = state.profileFallback,
                        allProfiles = c.profiles,
                        onSelect = { a.onSelectProfile(it) },
                    )
                }
            }
        }
        androidx.compose.material3.HorizontalDivider()
        EnhanceSection(state, a.onStart)
    }
}

@Composable
private fun EnhanceSection(state: LinkUiState, onStart: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val readClipboard = rememberClipboardReader()
    Text(stringResource(Res.string.linked_enhance_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Hint(stringResource(Res.string.rd_s9_link_enhance_hint))
    val canStart = !state.startingSession && url.isNotBlank() && state.capabilities is LoadState.Ok
    val problem = state.enhanceProblem?.let { enhanceProblemText(it) }
    // BUG-024: a URL keyboard without auto-capitalisation or correction, and "Go" on the keyboard (the button can sit under it)
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(stringResource(Res.string.linked_enhance_url)) },
        // BUG-104: only the URL's own problem goes under the field; the PC's answer is the red line below
        supportingText = { Text(LinkLogic.fieldProblem(state.enhanceProblem)?.let { enhanceProblemText(it) } ?: stringResource(Res.string.linked_enhance_hint)) },
        isError = state.enhanceProblem == EnhanceProblem.INVALID_URL || (url.isNotBlank() && !LinkLogic.enhanceUrlValid(url)),
        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { readClipboard()?.let { url = it } }) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(Res.string.rd_s9_link_paste_clipboard))
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { if (canStart) onStart(url) }),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.enhanceProblem != null && LinkLogic.fieldProblem(state.enhanceProblem) == null) {
        Text(problem.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    state.sessionError?.let { Text(it.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    Button(onClick = { onStart(url) }, enabled = canStart, shape = NijiyomeControls.shape) {
        if (state.startingSession) LoadingIndicator(Modifier.padding(end = 8.dp).size(20.dp))
        Text(stringResource(if (state.startingSession) Res.string.linked_enhance_starting else Res.string.linked_enhance_start))
    }
}

@Composable
private fun enhanceProblemText(p: EnhanceProblem): String = stringResource(
    when (p) {
        EnhanceProblem.INVALID_URL -> Res.string.linked_enhance_invalid
        EnhanceProblem.REJECTED -> Res.string.linked_enhance_rejected
        EnhanceProblem.TIMEOUT -> Res.string.linked_enhance_timeout
    },
)

@Composable
private fun RemotePanel(state: LinkUiState, onRemote: (RemoteAction) -> Unit) {
    val remote = state.remote
    Panel(icon = Icons.Outlined.PhoneAndroid, title = stringResource(Res.string.linked_remote)) {
        if (remote == null) {
            Hint(stringResource(Res.string.linked_remote_idle))
        } else {
            Text(
                remote.title?.let { t -> remote.episode?.let { stringResource(Res.string.linked_remote_now_episode, t, it) } ?: t }
                    ?: stringResource(Res.string.linked_remote_now_unknown),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (remote.playing) Res.string.linked_remote_playing else Res.string.linked_remote_paused) + " · " +
                    LinkLogic.clock(remote.positionSec, remote.durationSec),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val enabled = remote != null && !state.remoteBusy
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onRemote(RemoteAction.PREV) }, enabled = enabled) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(Res.string.linked_remote_prev))
            }
            FilledIconButton(onClick = { onRemote(RemoteAction.PLAY_PAUSE) }, enabled = enabled, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (remote?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (remote?.playing == true) Res.string.linked_remote_pause else Res.string.linked_remote_play),
                )
            }
            FilledTonalIconButton(onClick = { onRemote(RemoteAction.NEXT) }, enabled = enabled) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(Res.string.linked_remote_next))
            }
        }
    }
}

/** "La PC te envió …" (link.send-to-device): play the PC's Link session here or dismiss it. */
@Composable
private fun HandoffCard(h: LinkHandoff, onPlay: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                h.title?.let { stringResource(Res.string.linked_handoff_title, it) } ?: stringResource(Res.string.linked_handoff_title_generic),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    h.episode?.let { stringResource(Res.string.linked_handoff_episode, it) },
                    stringResource(Res.string.linked_handoff_from, LinkLogic.clock(h.positionSec, null)),
                    handoffAge(h.receivedAtMs),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, shape = NijiyomeControls.shape) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(Res.string.linked_handoff_play), modifier = Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.linked_handoff_dismiss)) }
            }
        }
    }
}

/** BUG-138: "hace 3 min" of a send at least a minute old (refreshed every 30 s), null for a fresh or undated one. */
@Composable
private fun handoffAge(receivedAtMs: Long?): String? {
    if (receivedAtMs == null) return null
    val now by produceState(System.currentTimeMillis(), receivedAtMs) {
        while (true) {
            delay(HANDOFF_AGE_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    return if (LinkLogic.handoffAgeShown(receivedAtMs, now)) relativeTime(receivedAtMs, now) else null
}

private const val HANDOFF_AGE_TICK_MS = 30_000L

/** PCs announcing `_nijiyome-link._tcp` (link.discovery): "Emparejar" shows the fingerprint (TOFU) and asks the code the PC shows. */
@Composable
private fun DiscoveredPanel(
    pcs: List<DiscoveredPc>,
    pairing: Boolean,
    enabled: Boolean,
    fingerprintOf: (DiscoveredPc) -> String?,
    onPair: (DiscoveredPc, String) -> Unit,
) {
    var target by remember { mutableStateOf<DiscoveredPc?>(null) }
    Panel(icon = Icons.Outlined.Wifi, title = stringResource(Res.string.linked_discovered_title)) {
        if (pcs.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(24.dp))
                Hint(stringResource(Res.string.linked_discovered_searching))
            }
        } else {
            pcs.forEach { pc ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Computer, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(pc.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pc.host}:${pc.port}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono))
                    }
                    FilledTonalButton(onClick = { target = pc }, enabled = enabled && !pairing, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.linked_pair)) }
                }
            }
        }
    }
    target?.let { pc ->
        DiscoveredPairDialog(pc, fingerprintOf(pc), onDismiss = { target = null }) { code ->
            target = null
            onPair(pc, code)
        }
    }
}

@Composable
private fun DiscoveredPairDialog(pc: DiscoveredPc, fingerprint: String?, onDismiss: () -> Unit, onPair: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val valid = code.filter { it.isLetterOrDigit() }.length == 10
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
        title = { Text(stringResource(Res.string.linked_discovered_pair_title, pc.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.linked_discovered_pair_body), style = MaterialTheme.typography.bodyMedium)
                // BUG-105: the same readable rows as the "PC conectada" card, to compare group by group with the PC
                Column {
                    Text(stringResource(Res.string.linked_fingerprint_label), style = MaterialTheme.typography.bodySmall)
                    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono)
                    (fingerprint?.let { LinkLogic.fingerprintRows(it) } ?: listOf("?")).forEach { Text(it, style = mono, maxLines = 1, softWrap = false) }
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(14) },
                    label = { Text(stringResource(Res.string.linked_code_label)) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = NijiyomeFonts.jetBrainsMono),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPair(code.trim()) }, enabled = valid && fingerprint != null, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.linked_discovered_pair_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

/** Short readable text of a Link failure for the snackbar. */
private suspend fun linkErrorText(error: app.nijiyome.model.AppError): String = when (error) {
    is app.nijiyome.model.AppError.Http -> if (error.code == 409) getString(Res.string.linked_remote_no_player) else getString(Res.string.linked_error_http, error.code)
    app.nijiyome.model.AppError.Offline -> getString(Res.string.linked_unreachable)
    else -> getString(Res.string.linked_problem)
}

@Composable
private fun ForgetButton(onForget: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    // BUG-090: a secondary, destructive action (error-coloured text button) next to the card's main action
    TextButton(onClick = { confirming = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), shape = NijiyomeControls.shape) {
        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(Res.string.linked_forget), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
            title = { Text(stringResource(Res.string.linked_forget_title)) },
            text = { Text(stringResource(Res.string.linked_forget_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onForget()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = NijiyomeControls.shape,
                ) { Text(stringResource(Res.string.linked_forget)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- PC extension server entry (docs/redesign/13 §3.10)

@Composable
private fun ExtensionServerCard(onOpen: () -> Unit) {
    Panel(icon = Icons.Outlined.Extension, title = stringResource(Res.string.extserver_entry_title)) {
        Hint(stringResource(Res.string.extserver_entry_body))
        FilledTonalButton(onClick = onOpen, shape = NijiyomeControls.shape) {
            Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(Res.string.extserver_entry_open), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
