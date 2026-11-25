// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.feature.link

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.motionFadeIn
import app.nijiyome.designsystem.motion.motionFadeOut
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.feature.link.resources.Res
import app.nijiyome.feature.link.resources.*
import app.nijiyome.model.ExtPasswordResult
import app.nijiyome.model.ExtensionServerLogEntry
import app.nijiyome.model.FirewallResult
import app.nijiyome.model.LinkActiveSession
import app.nijiyome.model.LinkDeviceHistoryMode
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.LinkServerDevice
import app.nijiyome.model.LinkServerPorts
import app.nijiyome.model.LinkServerState
import app.nijiyome.model.LinkStructure
import app.nijiyome.model.QrMatrix
import app.nijiyome.ui.format.relativeTime
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.patterns.FolderPathPicker
import app.nijiyome.ui.patterns.PortCheck
import app.nijiyome.ui.patterns.PortPicker
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Sparkles S9: Nijiyome Link on the desktop, the PC side. Everything comes from LinkServerController (registered on desktop only): what
// this PC serves, the paired phones and tablets, the active sessions with their RAM cache (the monitor, LNK-18), the profile offer, the
// Link server and the extension server with their pairing windows. Nothing here asks for LinkRepository, which desktop does not register.

private const val PC_NODE = "pc"

@Composable
internal fun LinkServerScreen(
    controller: LinkServerController,
    onBack: () -> Unit,
    modifier: Modifier,
    onOpenLinkSettings: (() -> Unit)?,
    onOpenMonitor: (() -> Unit)?,
    onOpenTorrents: (() -> Unit)?,
    onOpenLogs: (() -> Unit)?,
    onOpenExtensionServers: (() -> Unit)?,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(PC_NODE) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    var extPairing by rememberSaveable { mutableStateOf(false) }
    val openPairing = {
        controller.openPairing()
        pairing = true
    }
    val openExtPairing = {
        controller.openExtensionPairing()
        extPairing = true
    }
    val s = ServerCtx(controller, state, onOpenLinkSettings, onOpenMonitor, onOpenTorrents, onOpenLogs, onOpenExtensionServers, openPairing, openExtPairing)
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            LinkTopBar(onBack = onBack, meta = serverMeta(state)) {
                if (onOpenMonitor != null) {
                    OutlinedButton(onClick = onOpenMonitor, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Outlined.Monitor, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.rd_s9_srv_open_monitor), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Button(onClick = openPairing, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s9_srv_pair_device), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1280.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ServerErrorBanner(s)
                // The one switch of this screen on its own structure (CONTRATO-COMPOSE: once, at the root).
                when (structures.link) {
                    LinkStructure.MAP -> ServerMapTop(s, selected, onSelect = { selected = it })
                    LinkStructure.PANEL -> ServerCardsTop(s, selected, onSelect = { selected = it })
                }
                ServerPanels(s)
            }
        }
    }
    if (pairing) {
        PairingSheet(
            title = stringResource(Res.string.rd_s9_srv_pair_title),
            hint = stringResource(Res.string.rd_s9_srv_pair_hint),
            code = state.pairing?.code?.let { LinkServerFormat.groupCode(it) },
            copyText = state.pairing?.uri,
            qr = state.pairing?.qr,
            expiresAtMs = state.pairing?.expiresAtMs,
            fingerprint = state.fingerprint,
            onRenew = controller::openPairing,
            onDismiss = {
                controller.closePairing()
                pairing = false
            },
        )
    }
    if (extPairing) {
        PairingSheet(
            title = stringResource(Res.string.rd_s9_srv_ext_pair_title),
            hint = stringResource(Res.string.rd_s9_srv_ext_pair_hint),
            code = state.extPairing?.code,
            copyText = state.extPairing?.link,
            qr = state.extPairing?.qr,
            expiresAtMs = state.extPairing?.expiresAtMs,
            fingerprint = null,
            onRenew = controller::openExtensionPairing,
            onDismiss = {
                controller.closeExtensionPairing()
                extPairing = false
            },
        )
    }
}

/** What the server panels share: the controller, its state and the ways out of the screen. */
private class ServerCtx(
    val controller: LinkServerController,
    val state: LinkServerState,
    val onOpenLinkSettings: (() -> Unit)?,
    val onOpenMonitor: (() -> Unit)?,
    val onOpenTorrents: (() -> Unit)?,
    val onOpenLogs: (() -> Unit)?,
    val onOpenExtensionServers: (() -> Unit)?,
    val openPairing: () -> Unit,
    val openExtPairing: () -> Unit,
)

/** Pure helpers of the server screen (JVM-tested). */
internal object LinkServerFormat {
    /** "K7QM2XRP4D" -> "K7QM 2XRP 4D". */
    fun groupCode(code: String): String = code.filter { !it.isWhitespace() }.chunked(4).joinToString(" ")

    fun mb(bytes: Long): Long = bytes / (1024L * 1024L)

    /** "12:41" / "1:02:03" of a position in seconds. */
    fun clock(secs: Double): String = LinkLogic.clock(secs.toInt().coerceAtLeast(0), null)

    /** Seconds left of a pairing window; 0 when it expired. */
    fun secondsLeft(expiresAtMs: Long, nowMs: Long): Long = ((expiresAtMs - nowMs) / 1000L).coerceAtLeast(0L)

    /** One extension-server log line without secrets: time is formatted by the caller. */
    fun logLine(e: ExtensionServerLogEntry): String = listOfNotNull(
        e.event,
        e.route,
        e.name,
        e.count?.toString(),
        e.durationMs?.let { "$it ms" },
        e.error,
    ).joinToString(" · ")

    /** The cache budget choices of the monitor (MiB). */
    val CACHE_CHOICES = listOf(512, 1024, 2048, 4096)
}

@Composable
private fun serverMeta(state: LinkServerState): String = when {
    !state.enabled -> stringResource(Res.string.rd_s9_srv_meta_off)
    state.starting -> stringResource(Res.string.rd_s9_srv_meta_starting)
    else -> listOf(
        stringResource(Res.string.rd_s9_srv_meta_port, state.port),
        pluralStringResource(Res.plurals.rd_s9_srv_meta_sessions, state.activeSessions.size, state.activeSessions.size),
        pluralStringResource(Res.plurals.rd_s9_srv_meta_devices, state.devices.size, state.devices.size),
    ).joinToString(" · ")
}

/** A failed start says why and how to recover: another port, or a new identity when the keys cannot be opened. */
@Composable
private fun ServerErrorBanner(s: ServerCtx) {
    val error = s.state.error ?: return
    var regen by rememberSaveable { mutableStateOf(false) }
    val alt = LinkServerPorts.alternative(s.state.port, LinkServerPorts.LINK_DEFAULT)
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Text(
                stringResource(
                    when (error) {
                        "port_in_use" -> Res.string.rd_s9_srv_err_port
                        "no_address" -> Res.string.rd_s9_srv_err_address
                        "identity" -> Res.string.rd_s9_srv_err_identity
                        else -> Res.string.rd_s9_srv_err_other
                    },
                    s.state.port,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            when (error) {
                "port_in_use" -> FilledTonalButton(onClick = { s.controller.setPort(alt) }) { Text(stringResource(Res.string.rd_s9_srv_use_port, alt)) }
                "identity" -> FilledTonalButton(onClick = { regen = true }) { Text(stringResource(Res.string.rd_s9_srv_regen)) }
                else -> FilledTonalButton(onClick = { s.controller.setEnabled(true) }) { Text(stringResource(UiR.string.ui_retry)) }
            }
        }
    }
    if (regen) RegenerateDialog(onConfirm = { s.controller.regenerateIdentity(); regen = false }, onDismiss = { regen = false })
}

// ---------------------------------------------------------------- MAP

@Composable
private fun ServerMapTop(s: ServerCtx, selected: String, onSelect: (String) -> Unit) {
    val state = s.state
    val pcName = state.serverName.ifBlank { state.defaultServerName }.ifBlank { stringResource(Res.string.rd_s9_srv_this_pc) }
    val hub = MapNode(
        PC_NODE,
        pcName,
        if (state.running) stringResource(Res.string.rd_s9_srv_this_pc_port, state.port) else stringResource(Res.string.rd_s9_srv_off_short),
        Icons.Outlined.Computer,
        if (state.running) MapNodeState.CONNECTED else MapNodeState.IDLE,
    )
    val nodes = state.devices.take(MAX_MAP_DEVICES).map { d -> deviceNode(d, state.activeSessions.firstOrNull { it.deviceId == d.id }) }
    val session = state.activeSessions.firstOrNull()
    val map = @Composable { m: Modifier ->
        DeviceMap(
            hub = hub,
            nodes = nodes,
            modifier = m,
            selected = selected,
            onSelect = onSelect,
            wireLabel = session?.let { listOfNotNull(it.upscaleLabel, it.frameGenLabel, it.encoder.takeIf { e -> e.isNotBlank() }).joinToString(" · ").ifBlank { it.profileLabel } },
            description = stringResource(Res.string.rd_s9_srv_map_description, pcName, state.devices.size),
            extra = {
                OutlinedButton(onClick = s.openPairing) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s9_srv_pair_short), modifier = Modifier.padding(start = 8.dp))
                }
            },
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 900.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                map(Modifier.weight(1.6f))
                Box(Modifier.weight(1f)) { SelectedPanel(s, selected) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                map(Modifier)
                SelectedPanel(s, selected)
            }
        }
    }
}

private const val MAX_MAP_DEVICES = 6

@Composable
private fun deviceNode(d: LinkServerDevice, session: LinkActiveSession?): MapNode = MapNode(
    id = d.id,
    name = d.name,
    sub = when {
        session != null -> session.title?.let { t -> session.episode?.let { stringResource(Res.string.rd_s9_srv_watching_ep, t, it) } ?: t } ?: stringResource(Res.string.rd_s9_srv_streaming)
        d.online -> stringResource(Res.string.rd_s9_srv_online)
        else -> d.lastSeenMs?.let { stringResource(Res.string.rd_s9_srv_seen, relativeTime(it)) } ?: stringResource(Res.string.rd_s9_srv_never_seen)
    },
    icon = Icons.Outlined.PhoneAndroid,
    state = if (d.online) MapNodeState.CONNECTED else MapNodeState.IDLE,
)

/** The node picked on the map or a card: this PC (addresses, port, equipment, fingerprint) or a paired device. */
@Composable
private fun SelectedPanel(s: ServerCtx, selected: String) {
    val device = s.state.devices.firstOrNull { it.id == selected }
    if (device == null) PcPanel(s) else DevicePanel(s, device)
}

@Composable
private fun PcPanel(s: ServerCtx) {
    val state = s.state
    Panel(
        icon = Icons.Outlined.Computer,
        title = state.serverName.ifBlank { state.defaultServerName }.ifBlank { stringResource(Res.string.rd_s9_srv_this_pc) },
        trailing = { Pill(stringResource(if (state.running) Res.string.rd_s9_srv_listening else Res.string.rd_s9_srv_off_short), tone = if (state.running) PillTone.OK else PillTone.NEUTRAL) },
    ) {
        Cap(stringResource(Res.string.rd_s9_srv_addresses), state.addresses.joinToString(", ").ifBlank { "—" })
        Cap(stringResource(Res.string.rd_s9_srv_port), state.port.toString())
        Cap(
            stringResource(Res.string.rd_s9_srv_equipment),
            listOfNotNull(state.gpu, state.encoder, pluralStringResource(Res.plurals.rd_s9_srv_profiles, state.profiles, state.profiles)).joinToString(" · "),
        )
        Cap("FFmpeg", stringResource(if (state.ffmpeg) Res.string.rd_s9_srv_ffmpeg_ok else Res.string.rd_s9_srv_ffmpeg_missing))
        state.fingerprint?.let { fp ->
            Text(stringResource(Res.string.linked_fingerprint_label), style = MaterialTheme.typography.labelLarge)
            Text(fp, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono))
            Hint(stringResource(Res.string.rd_s9_srv_fingerprint_hint))
        }
    }
}

@Composable
private fun DevicePanel(s: ServerCtx, d: LinkServerDevice) {
    var renaming by rememberSaveable(d.id) { mutableStateOf(false) }
    var revoking by rememberSaveable(d.id) { mutableStateOf(false) }
    Panel(
        icon = Icons.Outlined.PhoneAndroid,
        title = d.name,
        trailing = {
            Pill(
                if (d.online) stringResource(Res.string.rd_s9_srv_online) else d.lastSeenMs?.let { stringResource(Res.string.rd_s9_srv_seen, relativeTime(it)) } ?: stringResource(Res.string.rd_s9_srv_never_seen),
                tone = if (d.online) PillTone.OK else PillTone.NEUTRAL,
            )
        },
    ) {
        Hint(listOf(d.platform, pluralStringResource(Res.plurals.rd_s9_srv_contributed, d.contributed, d.contributed)).filter { it.isNotBlank() }.joinToString(" · "))
        Text(stringResource(Res.string.rd_s9_srv_history_with), style = MaterialTheme.typography.labelLarge)
        Segmented(
            options = listOf(
                SegmentedOption(LinkDeviceHistoryMode.FULL, stringResource(Res.string.rd_s9_link_history_full)),
                SegmentedOption(LinkDeviceHistoryMode.RECEIVE_ONLY, stringResource(Res.string.rd_s9_link_history_receive)),
                SegmentedOption(LinkDeviceHistoryMode.PAUSED, stringResource(Res.string.rd_s9_link_history_paused)),
            ),
            selected = d.historyMode,
            onSelect = { s.controller.setHistoryMode(d.id, it) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (d.historyMode == LinkDeviceHistoryMode.FULL) {
                OutlinedButton(onClick = { s.controller.useAsHistorySource(d.id) }, enabled = !d.resyncing) {
                    Icon(Icons.Outlined.SyncAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(if (d.resyncing) Res.string.rd_s9_srv_resyncing else Res.string.rd_s9_srv_use_source), modifier = Modifier.padding(start = 8.dp))
                }
            }
            TextButton(onClick = { renaming = true }) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rd_s9_srv_rename), modifier = Modifier.padding(start = 8.dp))
            }
            s.onOpenLogs?.let { open ->
                TextButton(onClick = open) {
                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s9_srv_logs), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        DangerRow(stringResource(Res.string.rd_s9_srv_revoke_title), stringResource(Res.string.rd_s9_srv_revoke_hint), stringResource(Res.string.rd_s9_srv_revoke_action)) { revoking = true }
    }
    if (renaming) {
        var name by rememberSaveable(d.id) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(Res.string.rd_s9_srv_rename_title)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(64) }, label = { Text(stringResource(Res.string.rd_s9_srv_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { s.controller.rename(d.id, name.trim()); renaming = false }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.rd_s9_srv_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
    if (revoking) {
        ConfirmDanger(
            title = stringResource(Res.string.rd_s9_srv_revoke_confirm_title),
            body = stringResource(Res.string.rd_s9_srv_revoke_confirm_body, d.name),
            confirm = stringResource(Res.string.rd_s9_srv_revoke_confirm),
            onConfirm = { s.controller.revoke(d.id); revoking = false },
            onDismiss = { revoking = false },
        )
    }
}

// ---------------------------------------------------------------- PANEL

@Composable
private fun ServerCardsTop(s: ServerCtx, selected: String, onSelect: (String) -> Unit) {
    val state = s.state
    FlowRow(
        Modifier.fillMaxWidth().decor(DecorRegion.LINK),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Outlined.Router,
            title = stringResource(Res.string.rd_s9_srv_link_server),
            state = stringResource(if (state.running) Res.string.rd_s9_srv_listening else Res.string.rd_s9_srv_off_short),
            tone = if (state.running) PillTone.OK else PillTone.NEUTRAL,
            text = if (state.running) stringResource(Res.string.rd_s9_srv_port_addresses, state.port, state.addresses.joinToString(", ")) else stringResource(Res.string.rd_s9_srv_off_body),
        ) { TextButton(onClick = { s.controller.setEnabled(!state.enabled) }) { Text(stringResource(if (state.enabled) Res.string.rd_s9_srv_turn_off else Res.string.rd_s9_srv_turn_on)) } }
        state.devices.forEach { d ->
            val session = state.activeSessions.firstOrNull { it.deviceId == d.id }
            StatCard(
                icon = Icons.Outlined.PhoneAndroid,
                title = d.name,
                state = stringResource(if (d.online) Res.string.rd_s9_srv_connected else Res.string.rd_s9_srv_idle),
                tone = if (d.online) PillTone.OK else PillTone.NEUTRAL,
                text = deviceNode(d, session).sub.orEmpty(),
            ) { TextButton(onClick = { onSelect(d.id) }) { Text(stringResource(Res.string.rd_s9_srv_adjust)) } }
        }
        StatCard(
            icon = Icons.Outlined.Memory,
            title = stringResource(Res.string.rd_s9_srv_cache),
            state = stringResource(Res.string.rd_s9_srv_cache_value, LinkServerFormat.mb(state.totalCacheBytes), state.cacheLimitMb),
            tone = PillTone.NEUTRAL,
            text = pluralStringResource(Res.plurals.rd_s9_srv_meta_sessions, state.activeSessions.size, state.activeSessions.size),
        ) { s.onOpenMonitor?.let { open -> TextButton(onClick = open) { Text(stringResource(Res.string.rd_s9_srv_open_monitor)) } } }
        val extBusy = state.extError == "port_in_use"
        StatCard(
            icon = Icons.Outlined.Extension,
            title = stringResource(Res.string.rd_s9_srv_ext_server),
            state = stringResource(
                when {
                    extBusy -> Res.string.rd_s9_srv_port_busy
                    state.extRunning -> Res.string.rd_s9_srv_serving
                    else -> Res.string.rd_s9_srv_off_short
                },
            ),
            tone = when {
                extBusy -> PillTone.ERR
                state.extRunning -> PillTone.OK
                else -> PillTone.NEUTRAL
            },
            text = stringResource(Res.string.rd_s9_srv_ext_card, state.extPort, state.extDevices.size),
        ) {
            if (extBusy) {
                val alt = LinkServerPorts.alternative(state.extPort, LinkServerPorts.EXT_DEFAULT)
                FilledTonalButton(onClick = { s.controller.setExtensionPort(alt) }) { Text(stringResource(Res.string.rd_s9_srv_use_port, alt)) }
            } else if (state.extRunning) {
                TextButton(onClick = s.openExtPairing) { Text(stringResource(Res.string.rd_s9_srv_new_code)) }
            }
        }
        StatCard(
            icon = Icons.Outlined.History,
            title = stringResource(Res.string.rd_s9_link_history),
            state = stringResource(if (state.devices.any { it.resyncing }) Res.string.rd_s9_srv_resyncing else Res.string.rd_s9_srv_synced),
            tone = PillTone.OK,
            text = pluralStringResource(Res.plurals.rd_s9_srv_contributed, state.devices.sumOf { it.contributed }, state.devices.sumOf { it.contributed }),
        ) {}
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 900.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { SelectedPanel(s, selected) }
                Box(Modifier.weight(1f)) { PairedList(s, selected, onSelect) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectedPanel(s, selected)
                PairedList(s, selected, onSelect)
            }
        }
    }
}

@Composable
private fun PairedList(s: ServerCtx, selected: String, onSelect: (String) -> Unit) {
    Panel(
        icon = Icons.Outlined.PhoneAndroid,
        title = stringResource(Res.string.rd_s9_srv_paired),
        trailing = { FilledTonalButton(onClick = s.openPairing) { Text(stringResource(Res.string.rd_s9_srv_pair_short)) } },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = selected == PC_NODE, role = Role.Button, onValueChange = { onSelect(PC_NODE) }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Computer, contentDescription = null)
            Text(stringResource(Res.string.rd_s9_srv_this_pc), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        if (s.state.devices.isEmpty()) Hint(stringResource(Res.string.rd_s9_srv_no_devices))
        s.state.devices.forEach { d ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = selected == d.id, role = Role.Button, onValueChange = { onSelect(d.id) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Hint(pluralStringResource(Res.plurals.rd_s9_srv_contributed, d.contributed, d.contributed))
                }
                Pill(stringResource(if (d.online) Res.string.rd_s9_srv_connected else Res.string.rd_s9_srv_idle), tone = if (d.online) PillTone.OK else PillTone.NEUTRAL)
            }
        }
    }
}

// ---------------------------------------------------------------- panels under both structures

@Composable
private fun ServerPanels(s: ServerCtx) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 900.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MonitorPanel(s)
                    ProfileOfferPanel(s)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LinkServerPanel(s)
                    ExtServerPanel(s)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MonitorPanel(s)
                ProfileOfferPanel(s)
                LinkServerPanel(s)
                ExtServerPanel(s)
            }
        }
    }
}

/** LNK-18 / DESK-05 as a section of Link: sessions with their RAM window and Stop, the cache budget, app memory, and the ways out. */
@Composable
private fun MonitorPanel(s: ServerCtx) {
    val state = s.state
    Panel(
        icon = Icons.Outlined.Cast,
        title = pluralStringResource(Res.plurals.rd_s9_srv_sessions_title, state.activeSessions.size, state.activeSessions.size),
        trailing = { s.onOpenMonitor?.let { open -> TextButton(onClick = open) { Text(stringResource(Res.string.rd_s9_srv_open_monitor)) } } },
    ) {
        if (state.activeSessions.isEmpty()) {
            Hint(stringResource(Res.string.rd_s9_srv_no_sessions))
        } else {
            state.activeSessions.forEach { SessionRow(it, onStop = { s.controller.stopSession(it.sessionId) }) }
        }
        HorizontalDivider()
        Text(stringResource(Res.string.rd_s9_srv_cache_budget), style = MaterialTheme.typography.labelLarge)
        Segmented(
            options = LinkServerFormat.CACHE_CHOICES.map { mb -> SegmentedOption(mb, if (mb >= 1024) stringResource(Res.string.rd_s9_srv_gb, mb / 1024) else stringResource(Res.string.rd_s9_srv_mb, mb)) },
            selected = LinkServerFormat.CACHE_CHOICES.minByOrNull { kotlin.math.abs(it - state.cacheLimitMb) } ?: 1024,
            onSelect = { s.controller.setCacheLimitMb(it) },
        )
        Meter(
            stringResource(Res.string.rd_s9_srv_cache_total),
            stringResource(Res.string.rd_s9_srv_cache_value, LinkServerFormat.mb(state.totalCacheBytes), state.cacheLimitMb * state.activeSessions.size.coerceAtLeast(1)),
            LinkServerFormat.mb(state.totalCacheBytes).toFloat() / (state.cacheLimitMb * state.activeSessions.size.coerceAtLeast(1)).coerceAtLeast(1),
        )
        val ram by produceState(appRam()) { while (true) { delay(5_000); value = appRam() } }
        Meter(stringResource(Res.string.rd_s9_srv_app_ram), stringResource(Res.string.rd_s9_srv_cache_value, ram.first, ram.second), ram.first.toFloat() / ram.second.coerceAtLeast(1))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.onOpenTorrents?.let { open ->
                TextButton(onClick = open) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s9_srv_open_transfers), modifier = Modifier.padding(start = 8.dp))
                }
            }
            s.onOpenLinkSettings?.let { open ->
                TextButton(onClick = open) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s9_srv_link_settings), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** (used MB, max MB) of this app's memory. */
private fun appRam(): Pair<Long, Long> {
    val r = Runtime.getRuntime()
    return LinkServerFormat.mb(r.totalMemory() - r.freeMemory()) to LinkServerFormat.mb(r.maxMemory())
}

@Composable
private fun Meter(label: String, value: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SessionRow(x: LinkActiveSession, onStop: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val cachedFrom = x.cachedFromSecs
    val cachedTo = x.cachedToSecs
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                Pill(x.deviceName, tone = PillTone.PRIMARY, icon = Icons.Outlined.PhoneAndroid)
                Pill(stringResource(if (x.paused) Res.string.rd_s9_srv_paused else Res.string.rd_s9_srv_processing), tone = if (x.paused) PillTone.NEUTRAL else PillTone.OK)
                Pill(x.sourceKind)
                if (x.throttled) Pill(stringResource(Res.string.rd_s9_srv_throttled), tone = PillTone.WARN)
            }
            Text(
                x.title?.let { t -> x.episode?.let { stringResource(Res.string.rd_s9_srv_watching_ep, t, it) } ?: t } ?: stringResource(Res.string.rd_s9_srv_streaming),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOfNotNull(x.upscaleLabel, x.frameGenLabel, x.encoder.takeIf { it.isNotBlank() }).forEach { Pill(it) }
            }
            CacheBar(x)
            Text(
                listOfNotNull(
                    LinkServerFormat.clock(x.positionSecs) + (x.durationSecs?.let { " / " + LinkServerFormat.clock(it) } ?: ""),
                    cachedFrom?.let { f -> cachedTo?.let { t -> stringResource(Res.string.rd_s9_srv_ram_window, LinkServerFormat.clock(f), LinkServerFormat.clock(t)) } },
                    stringResource(Res.string.rd_s9_srv_cache_mb, LinkServerFormat.mb(x.cachedBytes)),
                    stringResource(Res.string.rd_s9_srv_lead, x.leadSecs.toInt()),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { confirm = true }) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(Res.string.rd_s9_srv_stop), modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirm) {
        ConfirmDanger(
            title = stringResource(Res.string.rd_s9_srv_stop_title, x.deviceName),
            body = stringResource(Res.string.rd_s9_srv_stop_body),
            confirm = stringResource(Res.string.rd_s9_srv_stop),
            onConfirm = { onStop(); confirm = false },
            onDismiss = { confirm = false },
        )
    }
}

/** Position over the RAM-cached window (artifact `.nj-c-cachebar`). */
@Composable
private fun CacheBar(x: LinkActiveSession) {
    val duration = x.durationSecs?.takeIf { it > 0 } ?: return
    val from = ((x.cachedFromSecs ?: x.positionSecs) / duration).toFloat().coerceIn(0f, 1f)
    val to = ((x.cachedToSecs ?: x.positionSecs) / duration).toFloat().coerceIn(from, 1f)
    val pos = (x.positionSecs / duration).toFloat().coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val window = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val marker = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small).background(track)) {
        drawRect(window, topLeft = androidx.compose.ui.geometry.Offset(size.width * from, 0f), size = androidx.compose.ui.geometry.Size(size.width * (to - from), size.height))
        drawRect(marker, topLeft = androidx.compose.ui.geometry.Offset(size.width * pos - 1.5f, 0f), size = androidx.compose.ui.geometry.Size(3f, size.height))
    }
}

/** What this PC offers to the phones (each phone chooses its profile in its own Link screen). */
@Composable
private fun ProfileOfferPanel(s: ServerCtx) {
    val state = s.state
    Panel(icon = Icons.Outlined.AutoAwesome, title = stringResource(Res.string.linked_profile)) {
        Hint(stringResource(Res.string.rd_s9_srv_profile_hint))
        Cap(stringResource(Res.string.rd_s9_srv_gpu), state.gpu ?: "—")
        Cap(stringResource(Res.string.rd_s9_srv_encoder), state.encoder ?: "—")
        Cap(stringResource(Res.string.rd_s9_srv_profiles_label), pluralStringResource(Res.plurals.rd_s9_srv_profiles, state.profiles, state.profiles))
        if (!state.ffmpeg) Text(stringResource(Res.string.rd_s9_srv_ffmpeg_missing), color = LocalSignalColors.current.err, style = MaterialTheme.typography.bodySmall)
    }
}

/** Servidor Link: on/off, announce, port (picker with a free-port test; applied with one press), library folder, firewall, identity. */
@Composable
private fun LinkServerPanel(s: ServerCtx) {
    val state = s.state
    val c = s.controller
    var regen by rememberSaveable { mutableStateOf(false) }
    Panel(icon = Icons.Outlined.Router, title = stringResource(Res.string.rd_s9_srv_link_server)) {
        SwitchLine(
            stringResource(Res.string.rd_s9_srv_link_on),
            if (state.enabled) stringResource(Res.string.rd_s9_srv_link_on_hint, state.addresses.joinToString(", ").ifBlank { "—" }) else stringResource(Res.string.rd_s9_srv_off_body),
            state.enabled,
        ) { c.setEnabled(it) }
        SwitchLine(stringResource(Res.string.rd_s9_srv_announce), stringResource(Res.string.rd_s9_srv_announce_hint), state.announce) { c.setAnnounce(it) }
        PortRow(state.port, LinkServerPorts.LINK_DEFAULT, stringResource(Res.string.rd_s9_srv_port), running = state.running) { c.setPort(it) }
        FolderPathPicker(
            path = state.libraryFolder.ifBlank { null },
            onPathChange = { c.setLibraryFolder(it) },
            label = stringResource(Res.string.rd_s9_srv_library),
            hint = stringResource(Res.string.rd_s9_srv_library_hint),
        )
        if (state.libraryFolder.isNotBlank()) TextButton(onClick = { c.setLibraryFolder("") }) { Text(stringResource(Res.string.rd_s9_srv_library_none)) }
        Firewall(s)
        DangerRow(stringResource(Res.string.rd_s9_srv_regen_title), stringResource(Res.string.rd_s9_srv_regen_hint), stringResource(Res.string.rd_s9_srv_regen)) { regen = true }
    }
    if (regen) RegenerateDialog(onConfirm = { c.regenerateIdentity(); regen = false }, onDismiss = { regen = false })
}

@Composable
private fun Firewall(s: ServerCtx) {
    val state = s.state
    var open by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    TextButton(onClick = { open = !open }) {
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.rd_s9_srv_firewall), modifier = Modifier.padding(start = 8.dp))
    }
    AnimatedVisibility(open, enter = motionFadeIn(), exit = motionFadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.firewallButton) {
                FilledTonalButton(onClick = { s.controller.allowPrivateNetwork() }, enabled = state.firewall != FirewallResult.RUNNING) { Text(stringResource(Res.string.rd_s9_srv_firewall_allow)) }
                state.firewall?.let { r ->
                    Hint(
                        stringResource(
                            when (r) {
                                FirewallResult.RUNNING -> Res.string.rd_s9_srv_firewall_running
                                FirewallResult.ADDED -> Res.string.rd_s9_srv_firewall_added
                                FirewallResult.CANCELLED -> Res.string.rd_s9_srv_firewall_cancelled
                                FirewallResult.FAILED -> Res.string.rd_s9_srv_firewall_failed
                            },
                        ),
                    )
                }
            } else {
                Hint(stringResource(Res.string.rd_s9_srv_firewall_linux))
                listOf(
                    "sudo ufw allow ${state.port}/tcp && sudo ufw allow ${state.extPort}/tcp",
                    "sudo firewall-cmd --add-port=${state.port}/tcp --add-port=${state.extPort}/tcp --permanent",
                ).forEach { cmd ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cmd, style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono), modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { clipboard.setText(AnnotatedString(cmd)) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(Res.string.rd_s9_srv_copy_command))
                        }
                    }
                }
            }
        }
    }
}

/** Servidor de extensiones: on/off, port, state, new pairing code, viewer password, devices with access, the request log. */
@Composable
private fun ExtServerPanel(s: ServerCtx) {
    val state = s.state
    val c = s.controller
    var revoke by remember { mutableStateOf<String?>(null) }
    var password by rememberSaveable { mutableStateOf(false) }
    var logs by rememberSaveable { mutableStateOf(false) }
    Panel(icon = Icons.Outlined.Extension, title = stringResource(Res.string.rd_s9_srv_ext_server)) {
        SwitchLine(stringResource(Res.string.rd_s9_srv_ext_on), stringResource(Res.string.rd_s9_srv_ext_on_hint), state.extEnabled) { c.setExtensionServerEnabled(it) }
        if (state.extEnabled) {
            PortRow(state.extPort, LinkServerPorts.EXT_DEFAULT, stringResource(Res.string.rd_s9_srv_port), running = state.extRunning) { c.setExtensionPort(it) }
            when {
                state.extError == "port_in_use" -> {
                    val alt = LinkServerPorts.alternative(state.extPort, LinkServerPorts.EXT_DEFAULT)
                    Text(stringResource(Res.string.rd_s9_srv_ext_port_busy, state.extPort), color = LocalSignalColors.current.err, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { c.setExtensionPort(alt) }) { Text(stringResource(Res.string.rd_s9_srv_use_port, alt)) }
                }
                state.extError != null -> Text(stringResource(Res.string.rd_s9_srv_ext_error, state.extError.orEmpty()), color = LocalSignalColors.current.err, style = MaterialTheme.typography.bodyMedium)
                state.extRunning -> Text(stringResource(Res.string.rd_s9_srv_ext_serving, state.extUrls.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = s.openExtPairing, enabled = state.extRunning) {
                    Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s9_srv_new_code_phone), modifier = Modifier.padding(start = 8.dp))
                }
                s.onOpenExtensionServers?.let { open -> TextButton(onClick = open) { Text(stringResource(Res.string.rd_s9_srv_other_pcs)) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (state.extPasswordSet) Res.string.rd_s9_srv_pw_set else Res.string.rd_s9_srv_pw_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.extPasswordSet) {
                    TextButton(onClick = { c.setExtensionPassword(null) }) { Text(stringResource(Res.string.rd_s9_srv_pw_remove)) }
                } else {
                    TextButton(onClick = { password = true }, enabled = state.extRunning) { Text(stringResource(Res.string.rd_s9_srv_pw_create)) }
                }
            }
            state.extPasswordResult?.let { r ->
                Hint(
                    stringResource(
                        when (r) {
                            ExtPasswordResult.SAVED -> Res.string.rd_s9_srv_pw_saved
                            ExtPasswordResult.CLEARED -> Res.string.rd_s9_srv_pw_cleared
                            ExtPasswordResult.INVALID -> Res.string.rd_s9_srv_pw_invalid
                            ExtPasswordResult.FAILED -> Res.string.rd_s9_srv_pw_failed
                        },
                        LinkServerPorts.PASSWORD_MIN,
                    ),
                )
            }
            Text(stringResource(Res.string.rd_s9_srv_ext_devices), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
            if (state.extDevices.isEmpty()) Hint(stringResource(Res.string.rd_s9_srv_ext_devices_none))
            state.extDevices.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        d.lastSeenMs?.let { Hint(stringResource(Res.string.rd_s9_srv_seen, relativeTime(it))) }
                    }
                    TextButton(onClick = { revoke = d.id }) { Text(stringResource(Res.string.rd_s9_srv_revoke_action), color = MaterialTheme.colorScheme.error) }
                }
            }
            TextButton(onClick = { logs = !logs }) {
                Icon(if (logs) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rd_s9_srv_ext_logs, state.extLogs.size), modifier = Modifier.padding(start = 8.dp))
            }
            AnimatedVisibility(logs, enter = motionFadeIn(), exit = motionFadeOut()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (state.extLogs.isEmpty()) Hint(stringResource(Res.string.rd_s9_srv_ext_logs_empty))
                        state.extLogs.forEach { e ->
                            Text(
                                relativeTime(e.atMs) + " · " + LinkServerFormat.logLine(e),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                                color = if (e.error != null) LocalSignalColors.current.err else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
    revoke?.let { id ->
        ConfirmDanger(
            title = stringResource(Res.string.rd_s9_srv_ext_revoke_title),
            body = stringResource(Res.string.rd_s9_srv_ext_revoke_body),
            confirm = stringResource(Res.string.rd_s9_srv_ext_revoke),
            onConfirm = { c.revokeExtensionDevice(id); revoke = null },
            onDismiss = { revoke = null },
        )
    }
    if (password) {
        // Not saveable on purpose: the password must not end up in the saved instance state.
        var pw by remember { mutableStateOf("") }
        val ok = LinkServerPorts.passwordOk(pw)
        AlertDialog(
            onDismissRequest = { password = false },
            title = { Text(stringResource(Res.string.rd_s9_srv_pw_create)) },
            text = {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text(stringResource(Res.string.rd_s9_srv_pw_label)) },
                    supportingText = { Text(stringResource(Res.string.rd_s9_srv_pw_rule, LinkServerPorts.PASSWORD_MIN)) },
                    isError = pw.isNotEmpty() && !ok,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            },
            confirmButton = { TextButton(onClick = { c.setExtensionPassword(pw); pw = ""; password = false }, enabled = ok) { Text(stringResource(Res.string.rd_s9_srv_save)) } },
            dismissButton = { TextButton(onClick = { password = false }) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
}

/** A port chosen with the stepper and tested for being free; changing it restarts the server, so it is applied with one press. */
@Composable
private fun PortRow(port: Int, default: Int, label: String, running: Boolean, onApply: (Int) -> Unit) {
    var draft by rememberSaveable(port) { mutableStateOf(port.takeIf { LinkServerPorts.isValid(it) } ?: default) }
    val inUseByUs = stringResource(Res.string.rd_s9_srv_port_ours)
    PortPicker(
        port = draft,
        onPortChange = { draft = it },
        label = label,
        defaultPort = default,
        onTest = { p ->
            when {
                !LinkServerPorts.isValid(p) -> PortCheck(false, getString(Res.string.rd_s9_srv_port_range, LinkServerPorts.MIN, LinkServerPorts.MAX))
                p == port && running -> PortCheck(true, inUseByUs)
                withContext(Dispatchers.IO) { runCatching { java.net.ServerSocket(p).close() }.isSuccess } -> PortCheck(true, getString(Res.string.rd_s9_srv_port_free, p))
                else -> {
                    val alt = LinkServerPorts.alternative(p, default)
                    PortCheck(false, getString(Res.string.rd_s9_srv_port_taken, p), suggestion = alt)
                }
            }
        },
    )
    if (draft != port) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onApply(draft) }, enabled = LinkServerPorts.isValid(draft)) { Text(stringResource(Res.string.rd_s9_srv_port_apply, draft)) }
            Hint(stringResource(Res.string.rd_s9_srv_port_restart), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SwitchLine(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(hint)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun DangerRow(title: String, hint: String, action: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalSignalColors.current.err.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Hint(hint)
            }
            OutlinedButton(onClick = onClick) { Text(action, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ConfirmDanger(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

@Composable
private fun RegenerateDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) = ConfirmDanger(
    title = stringResource(Res.string.rd_s9_srv_regen_confirm_title),
    body = stringResource(Res.string.rd_s9_srv_regen_confirm_body),
    confirm = stringResource(Res.string.rd_s9_srv_regen),
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

/**
 * A pairing window: the QR, the code, how long it is valid, the PC's fingerprint and "copy". When it expires it says so and offers a
 * new code (the pairing-expired state); closing the sheet closes the window.
 */
@Composable
private fun PairingSheet(
    title: String,
    hint: String,
    code: String?,
    copyText: String?,
    qr: QrMatrix?,
    expiresAtMs: Long?,
    fingerprint: String?,
    onRenew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalAppSnackbar.current
    val copied = stringResource(Res.string.rd_s9_srv_copied)
    val now by produceState(System.currentTimeMillis(), expiresAtMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val left = expiresAtMs?.let { LinkServerFormat.secondsLeft(it, now) }
    val expired = left == 0L
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().semantics { heading() })
            Text(hint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
            when {
                code == null -> {
                    Hint(stringResource(Res.string.rd_s9_srv_pair_opening))
                    FilledTonalButton(onClick = onRenew) { Text(stringResource(Res.string.rd_s9_srv_pair_new)) }
                }
                expired -> {
                    Text(stringResource(Res.string.rd_s9_srv_pair_expired), style = MaterialTheme.typography.titleMedium, color = LocalSignalColors.current.err)
                    Hint(stringResource(Res.string.rd_s9_srv_pair_expired_body))
                    Button(onClick = onRenew) { Text(stringResource(Res.string.rd_s9_srv_pair_new)) }
                }
                else -> {
                    qr?.let { QrMatrixImage(it, stringResource(Res.string.rd_s9_srv_qr_description), Modifier.width(240.dp)) }
                    Text(code, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono))
                    left?.let { Hint(stringResource(Res.string.rd_s9_srv_pair_valid, it)) }
                    fingerprint?.let {
                        Text(stringResource(Res.string.rd_s9_srv_pc_fingerprint), style = MaterialTheme.typography.labelLarge)
                        Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono))
                    }
                    copyText?.let { t ->
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(t))
                            snackbar.show(copied)
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.rd_s9_srv_copy_pairing), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(Res.string.rd_s9_srv_close_window)) }
        }
    }
}
