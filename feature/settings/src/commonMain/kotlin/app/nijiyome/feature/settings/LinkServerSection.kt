// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.designsystem.components.focusOnOpen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.ExtPasswordResult
import app.nijiyome.model.ExtensionServerLogEntry
import app.nijiyome.model.FirewallResult
import app.nijiyome.model.LinkDeviceHistoryMode
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.LinkServerPorts
import app.nijiyome.model.LinkServerState
import app.nijiyome.model.LinkServerDevice
import app.nijiyome.model.QrMatrix
import app.nijiyome.ui.format.relativeTime
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

/**
 * Settings › Nijiyome Link: the phone's client rows on Android ([LinkSection]); on desktop, where a [LinkServerController] is
 * registered, this PC's server instead ([LinkServerSection]).
 */
@Composable
internal fun LinkSectionForPlatform(state: SettingsUiState, vm: SettingsViewModel, onOpenLinked: () -> Unit) {
    val koin = getKoin()
    val server = remember(koin) { koin.getOrNull<LinkServerController>() }
    if (server != null) {
        LinkServerSection(server)
        // 1.1: Settings › Red (outbound proxy, FlareSolverr, loopback media proxy), desktop only
        val net = remember(koin) { koin.getOrNull<app.nijiyome.model.NetworkSettingsController>() }
        if (net != null) NetworkSettingsSection(net)
    } else {
        LinkSection(state, vm, onOpenLinked)
    }
}

/**
 * The desktop's Link server (after Tatakai Next's desktop Link screen): turn it on, the name phones see, mDNS, the pairing window
 * (code, text to paste and QR, with its countdown), the paired devices (online, last seen, history mode, "use as source", rename,
 * revoke), what this PC offers, the library folder, the port, the firewall (a button on Windows, a hint elsewhere), and the
 * integrated extension server (on / off, its port, its own 6-digit code, QR and `nijiyome://` link, the viewer password, its linked
 * devices and its log).
 */
@Composable
internal fun LinkServerSection(server: LinkServerController) {
    val st by server.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.pairing, st.extPairing) {
        while (st.pairing != null || st.extPairing != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    var revoking by remember { mutableStateOf<LinkServerDevice?>(null) }
    var renaming by remember { mutableStateOf<LinkServerDevice?>(null) }

    SectionHint(stringResource(Res.string.settings_linksrv_hint))
    SwitchRow(
        label = stringResource(Res.string.settings_linksrv_enable),
        hint = stringResource(Res.string.settings_linksrv_enable_hint),
        checked = st.enabled,
        onChange = server::setEnabled,
    )
    val status = when {
        st.starting -> stringResource(Res.string.settings_linksrv_starting)
        st.error == "port_in_use" -> stringResource(Res.string.settings_linksrv_error_port, st.port.toString())
        st.error == "no_address" -> stringResource(Res.string.settings_linksrv_error_address)
        st.error != null -> stringResource(Res.string.settings_linksrv_error_other)
        st.running -> stringResource(Res.string.settings_linksrv_running, st.addresses.ifEmpty { listOf(":${st.port}") }.joinToString(", "))
        else -> stringResource(Res.string.settings_linksrv_stopped)
    }
    Text(status, style = MaterialTheme.typography.bodyMedium, color = if (st.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if (st.error == "port_in_use") {
        val alt = LinkServerPorts.alternative(st.port, LinkServerPorts.LINK_DEFAULT).takeIf { it != st.extPort }
            ?: LinkServerPorts.alternative(st.port + 10, LinkServerPorts.LINK_DEFAULT)
        TextButton(onClick = { server.setPort(alt) }) { Text(stringResource(Res.string.settings_linksrv_try_port, alt.toString())) }
    }
    st.fingerprint?.let { fp ->
        Text(stringResource(Res.string.settings_linksrv_fingerprint, fp), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        Text(stringResource(Res.string.settings_linksrv_fingerprint_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (st.running && !st.ffmpeg) {
        Text(stringResource(Res.string.settings_linksrv_no_ffmpeg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
    }

    // pairing
    Text(stringResource(Res.string.settings_linksrv_pair), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    SectionHint(stringResource(Res.string.settings_linksrv_pair_hint))
    val pairing = st.pairing
    if (pairing == null) {
        FilledTonalButton(onClick = server::openPairing, enabled = !st.starting) {
            Icon(Icons.Outlined.PhonelinkSetup, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(Res.string.settings_linksrv_pair), modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            pairing.qr?.let { QrCode(it, stringResource(Res.string.settings_linksrv_qr)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.settings_linksrv_code), style = MaterialTheme.typography.labelLarge)
                Text(pairing.code.chunked(5).joinToString(" "), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                val left = ((pairing.expiresAtMs - now) / 1000).coerceAtLeast(0)
                Text(stringResource(Res.string.settings_linksrv_expires, left.toString()), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(pairing.uri)) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(Res.string.settings_linksrv_copy), modifier = Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = server::closePairing) { Text(stringResource(Res.string.settings_linksrv_cancel)) }
                }
            }
        }
    }

    // devices
    Text(stringResource(Res.string.settings_linksrv_devices), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    if (st.devices.isEmpty()) {
        Text(stringResource(Res.string.settings_linksrv_no_devices), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (d in st.devices) DeviceRow(d, server, onRevoke = { revoking = d }, onRename = { renaming = d })

    // this PC + options
    Text(stringResource(Res.string.settings_linksrv_caps), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    if (st.running) {
        Text(
            stringResource(Res.string.settings_linksrv_caps_line, st.gpu ?: "GPU ?", st.encoder ?: "—", st.profiles.toString()),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    TextSetting(
        label = stringResource(Res.string.settings_linksrv_name),
        hint = stringResource(Res.string.settings_linksrv_name_hint, st.defaultServerName),
        value = st.serverName,
        onSave = server::setServerName,
    )
    SwitchRow(
        label = stringResource(Res.string.settings_linksrv_announce),
        hint = stringResource(Res.string.settings_linksrv_announce_hint),
        checked = st.announce,
        onChange = server::setAnnounce,
    )
    TextSetting(
        label = stringResource(Res.string.settings_linksrv_library),
        hint = stringResource(Res.string.settings_linksrv_library_hint),
        value = st.libraryFolder,
        onSave = server::setLibraryFolder,
    )
    PortSetting(
        label = stringResource(Res.string.settings_linksrv_port),
        hint = stringResource(Res.string.settings_linksrv_port_hint, LinkServerPorts.LINK_DEFAULT.toString()),
        value = st.port,
        other = st.extPort,
        onSave = server::setPort,
    )
    FirewallRow(st, server)

    // integrated extension server
    Text(stringResource(Res.string.settings_linksrv_ext), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    SwitchRow(
        label = stringResource(Res.string.settings_linksrv_ext_enable),
        hint = stringResource(Res.string.settings_linksrv_ext_hint),
        checked = st.extEnabled,
        onChange = server::setExtensionServerEnabled,
    )
    if (st.extError != null) {
        val text = if (st.extError == "port_in_use") {
            stringResource(Res.string.settings_linksrv_error_port, st.extPort.toString())
        } else {
            stringResource(Res.string.settings_linksrv_ext_error)
        }
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (st.extError == "port_in_use") {
            val alt = LinkServerPorts.alternative(st.extPort, LinkServerPorts.EXT_DEFAULT).takeIf { it != st.port }
                ?: LinkServerPorts.alternative(st.extPort + 10, LinkServerPorts.EXT_DEFAULT)
            TextButton(onClick = { server.setExtensionPort(alt) }) { Text(stringResource(Res.string.settings_linksrv_try_port, alt.toString())) }
        }
    }
    if (st.extEnabled) {
        PortSetting(
            label = stringResource(Res.string.settings_linksrv_port),
            hint = stringResource(Res.string.settings_linksrv_port_hint, LinkServerPorts.EXT_DEFAULT.toString()),
            value = st.extPort,
            other = st.port,
            onSave = server::setExtensionPort,
        )
    }
    if (st.extRunning) {
        st.extUrls.firstOrNull()?.let { Text(stringResource(Res.string.settings_linksrv_ext_running, it), style = MaterialTheme.typography.bodyMedium) }
        SectionHint(stringResource(Res.string.settings_linksrv_ext_pair_hint))
        val ep = st.extPairing
        if (ep == null) {
            FilledTonalButton(onClick = server::openExtensionPairing) { Text(stringResource(Res.string.settings_linksrv_ext_pair)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                ep.qr?.let { QrCode(it, stringResource(Res.string.settings_linksrv_qr)) }
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.settings_linksrv_code), style = MaterialTheme.typography.labelLarge)
                    Text(ep.code, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                    Text(stringResource(Res.string.settings_linksrv_expires, ((ep.expiresAtMs - now) / 1000).coerceAtLeast(0).toString()), style = MaterialTheme.typography.bodySmall)
                    // for a device without a camera (another PC) or a phone that opens links: the nijiyome:// link itself
                    Text(stringResource(Res.string.settings_linksrv_ext_link), style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Text(ep.link, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(ep.link)) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text(stringResource(Res.string.settings_linksrv_ext_copy_link), modifier = Modifier.padding(start = 8.dp))
                        }
                        TextButton(onClick = server::closeExtensionPairing) { Text(stringResource(Res.string.settings_linksrv_cancel)) }
                    }
                }
            }
        }
        ExtPasswordSetting(st, server)
        if (st.extDevices.isNotEmpty()) {
            Text(stringResource(Res.string.settings_linksrv_ext_devices), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            for (d in st.extDevices) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.label, style = MaterialTheme.typography.bodyLarge)
                        d.lastSeenMs?.let { Text(stringResource(Res.string.settings_linksrv_last_seen, relativeTime(it)), style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = { server.revokeExtensionDevice(d.id) }) { Text(stringResource(Res.string.settings_linksrv_revoke)) }
                }
            }
        }
        ExtLogs(st.extLogs)
    }

    revoking?.let { d ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(Res.string.settings_linksrv_revoke_title, d.name)) },
            text = { Text(stringResource(Res.string.settings_linksrv_revoke_body)) },
            confirmButton = {
                TextButton(onClick = {
                    server.revoke(d.id)
                    revoking = null
                }) { Text(stringResource(Res.string.settings_linksrv_revoke)) }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text(stringResource(Res.string.settings_linksrv_cancel)) } },
        )
    }
    renaming?.let { d ->
        var name by remember(d.id) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(Res.string.settings_linksrv_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(64) }, singleLine = true, modifier = Modifier.focusOnOpen()) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    server.rename(d.id, name)
                    renaming = null
                }) { Text(stringResource(Res.string.settings_linksrv_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(Res.string.settings_linksrv_cancel)) } },
        )
    }
}

@Composable
private fun DeviceRow(d: LinkServerDevice, server: LinkServerController, onRevoke: () -> Unit, onRename: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                val seen = when {
                    d.online -> stringResource(Res.string.settings_linksrv_online)
                    d.lastSeenMs != null -> stringResource(Res.string.settings_linksrv_last_seen, relativeTime(d.lastSeenMs!!))
                    else -> stringResource(Res.string.settings_linksrv_never_seen)
                }
                Text(listOf(d.platform.ifBlank { null }, seen).filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.contributed > 0) Text(stringResource(Res.string.settings_linksrv_contributed, d.contributed.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.resyncing) Text(stringResource(Res.string.settings_linksrv_resyncing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onRename) { Text(stringResource(Res.string.settings_linksrv_rename)) }
            TextButton(onClick = onRevoke) { Text(stringResource(Res.string.settings_linksrv_revoke), color = MaterialTheme.colorScheme.error) }
        }
        SettingRow(label = stringResource(Res.string.settings_linksrv_history_mode), stack = true, divider = false) {
            Segmented(
                options = LinkDeviceHistoryMode.entries.map { m ->
                    SegmentedOption(
                        value = m,
                        label = stringResource(
                            when (m) {
                                LinkDeviceHistoryMode.FULL -> Res.string.settings_linksrv_mode_full
                                LinkDeviceHistoryMode.RECEIVE_ONLY -> Res.string.settings_linksrv_mode_receive
                                LinkDeviceHistoryMode.PAUSED -> Res.string.settings_linksrv_mode_paused
                            },
                        ),
                    )
                },
                selected = d.historyMode,
                onSelect = { server.setHistoryMode(d.id, it) },
                fillWidth = true,
            )
        }
        if (d.historyMode == LinkDeviceHistoryMode.FULL && !d.resyncing) {
            TextButton(onClick = { server.useAsHistorySource(d.id) }) { Text(stringResource(Res.string.settings_linksrv_use_source)) }
        }
    }
}

/** A port field: saved only when it is a valid port that the other server does not use. */
@Composable
private fun PortSetting(label: String, hint: String, value: Int, other: Int, onSave: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val port = text.trim().toIntOrNull()
    val valid = port != null && LinkServerPorts.isValid(port) && port != other
    SettingRow(label = label, hint = hint, stack = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(5) },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                supportingText = if (text.isNotEmpty() && !valid) {
                    { Text(stringResource(Res.string.settings_linksrv_port_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.widthIn(max = 160.dp),
            )
            TextButton(enabled = valid && port != value, onClick = { port?.let(onSave) }) { Text(stringResource(Res.string.settings_linksrv_save)) }
        }
    }
}

/** Windows: "Permitir en red privada" (UAC prompt) and its outcome; elsewhere a hint with the ports to open. */
@Composable
private fun FirewallRow(st: LinkServerState, server: LinkServerController) {
    val ports = listOf(st.port, st.extPort).filter { it > 0 }.distinct()
    if (st.firewallButton) {
        SettingRow(label = stringResource(Res.string.settings_linksrv_firewall), hint = stringResource(Res.string.settings_linksrv_firewall_hint, ports.joinToString(", ")), stack = true) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = server::allowPrivateNetwork, enabled = st.firewall != FirewallResult.RUNNING) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(Res.string.settings_linksrv_firewall), modifier = Modifier.padding(start = 8.dp))
                }
                val result = when (st.firewall) {
                    FirewallResult.RUNNING -> Res.string.settings_linksrv_firewall_busy
                    FirewallResult.ADDED -> Res.string.settings_linksrv_firewall_done
                    FirewallResult.CANCELLED -> Res.string.settings_linksrv_firewall_cancelled
                    FirewallResult.FAILED -> Res.string.settings_linksrv_firewall_failed
                    null -> null
                }
                result?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (st.firewall == FirewallResult.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        SettingRow(
            label = stringResource(Res.string.settings_linksrv_firewall_other),
            hint = stringResource(Res.string.settings_linksrv_firewall_other_hint, ports.joinToString(", "), ports.joinToString(",")),
            stack = true,
        ) {}
    }
}

/**
 * The extension server's viewer password: set (10..1024 characters) or remove. The typed text lives only in this field (not saved
 * in any state) and is cleared once sent; the server keeps only its hash.
 */
@Composable
private fun ExtPasswordSetting(st: LinkServerState, server: LinkServerController) {
    var password by remember { mutableStateOf("") }
    val ok = LinkServerPorts.passwordOk(password)
    SettingRow(label = stringResource(Res.string.settings_linksrv_ext_password), hint = stringResource(Res.string.settings_linksrv_ext_password_hint), stack = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (st.extPasswordSet) Res.string.settings_linksrv_ext_password_set else Res.string.settings_linksrv_ext_password_not_set),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(LinkServerPorts.PASSWORD_MAX * 2) },
                    label = { Text(stringResource(Res.string.settings_linksrv_ext_password_field)) },
                    singleLine = true,
                    isError = password.isNotEmpty() && !ok,
                    supportingText = if (password.isNotEmpty() && !ok) {
                        { Text(stringResource(Res.string.settings_linksrv_ext_password_short, LinkServerPorts.PASSWORD_MIN.toString(), LinkServerPorts.PASSWORD_MAX.toString())) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = ok, onClick = {
                    server.setExtensionPassword(password)
                    password = ""
                }) { Text(stringResource(Res.string.settings_linksrv_save)) }
            }
            if (st.extPasswordSet) {
                TextButton(onClick = { server.setExtensionPassword(null) }) {
                    Text(stringResource(Res.string.settings_linksrv_ext_password_clear), color = MaterialTheme.colorScheme.error)
                }
            }
            val result = when (st.extPasswordResult) {
                ExtPasswordResult.SAVED -> Res.string.settings_linksrv_ext_password_saved
                ExtPasswordResult.CLEARED -> Res.string.settings_linksrv_ext_password_cleared
                ExtPasswordResult.FAILED, ExtPasswordResult.INVALID -> Res.string.settings_linksrv_ext_password_failed
                null -> null
            }
            result?.let {
                val bad = st.extPasswordResult == ExtPasswordResult.FAILED || st.extPasswordResult == ExtPasswordResult.INVALID
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The extension server's latest requests and errors, folded by default (newest first). */
@Composable
private fun ExtLogs(logs: List<ExtensionServerLogEntry>) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.settings_linksrv_ext_logs, logs.size.toString()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { open = !open }) {
            Text(stringResource(if (open) Res.string.settings_linksrv_ext_logs_hide else Res.string.settings_linksrv_ext_logs_show))
        }
    }
    if (!open) return
    if (logs.isEmpty()) {
        Text(stringResource(Res.string.settings_linksrv_ext_logs_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (e in logs) {
            Text(
                logLine(e, relativeTime(e.atMs)),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (e.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** `hace 2 min · source_failed · /resolve · Toko · 3 · 120 ms · timeout`, with the parts the entry has. Pure. */
internal fun logLine(e: ExtensionServerLogEntry, time: String): String = listOfNotNull(
    time,
    e.event,
    e.route,
    e.name,
    e.count?.toString(),
    e.durationMs?.let { "$it ms" },
    e.error,
).joinToString(" · ")

@Composable
private fun TextSetting(label: String, hint: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    SettingRow(label = label, hint = hint, stack = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(enabled = text != value, onClick = { onSave(text) }) { Text(stringResource(Res.string.settings_linksrv_save)) }
        }
    }
}

/** A QR code drawn module by module on a white quiet zone (scannable in dark theme too). */
@Composable
private fun QrCode(qr: QrMatrix, description: String) {
    val side = 200.dp
    Canvas(
        Modifier
            .size(side)
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .semantics { contentDescription = description },
    ) {
        val cell = size.minDimension / qr.size
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr[x, y]) drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
