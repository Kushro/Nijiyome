// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.NetProxyKind
import app.nijiyome.model.NetTestResult
import app.nijiyome.model.NetworkSettingsController
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Nijiyome Link › Red (desktop, Tatakai Next's server › network rows): the outbound HTTP / SOCKS5 proxy with optional
 * credentials and "Probar", FlareSolverr with "Probar", and the player's loopback media proxy. Shown only where a
 * [NetworkSettingsController] is registered (Android has none).
 */
@Composable
internal fun NetworkSettingsSection(net: NetworkSettingsController) {
    val st by net.state.collectAsState()
    val scope = rememberCoroutineScope()

    Text(stringResource(Res.string.settings_net_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    SectionHint(stringResource(Res.string.settings_net_hint))

    // ---- outbound proxy ----
    var enabled by remember(st.proxyEnabled) { mutableStateOf(st.proxyEnabled) }
    var kind by remember(st.proxyKind) { mutableStateOf(st.proxyKind) }
    var host by remember(st.proxyHost) { mutableStateOf(st.proxyHost) }
    var port by remember(st.proxyPort) { mutableStateOf(st.proxyPort.toString()) }
    var user by remember(st.proxyUsername) { mutableStateOf(st.proxyUsername) }
    var pass by remember(st.hasPassword) { mutableStateOf("") }
    var proxyResult by remember { mutableStateOf<NetTestResult?>(null) }
    var proxyBusy by remember { mutableStateOf(false) }
    var proxySaved by remember { mutableStateOf<Boolean?>(null) }
    val portNum = port.trim().toIntOrNull()
    val proxyValid = host.isNotBlank() && portNum != null && portNum in 1..65535 && host.none { it.isWhitespace() || it in "/?#@" }
    val dirty = enabled != st.proxyEnabled || kind != st.proxyKind || host != st.proxyHost || port != st.proxyPort.toString() ||
        user != st.proxyUsername || pass.isNotEmpty()

    SwitchRow(
        label = stringResource(Res.string.settings_net_proxy),
        hint = stringResource(Res.string.settings_net_proxy_hint),
        checked = enabled,
        onChange = { enabled = it },
    )
    SettingRow(label = stringResource(Res.string.settings_net_proxy_type), hint = null) {
        Segmented(
            options = listOf(SegmentedOption(NetProxyKind.SOCKS5, "SOCKS5"), SegmentedOption(NetProxyKind.HTTP, "HTTP")),
            selected = kind,
            onSelect = { kind = it },
        )
    }
    SettingRow(label = stringResource(Res.string.settings_net_proxy_address), hint = stringResource(Res.string.settings_net_proxy_address_hint), stack = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim().take(253) },
                label = { Text(stringResource(Res.string.settings_net_proxy_host)) },
                placeholder = { Text("127.0.0.1") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { v -> port = v.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(Res.string.settings_net_proxy_port)) },
                singleLine = true,
                isError = port.isNotEmpty() && (portNum == null || portNum !in 1..65535),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
    SettingRow(label = stringResource(Res.string.settings_net_proxy_credentials), hint = stringResource(Res.string.settings_net_proxy_credentials_hint), stack = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = user,
                onValueChange = { user = it.take(255) },
                label = { Text(stringResource(Res.string.settings_net_proxy_user)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it.take(255) },
                label = { Text(stringResource(if (st.hasPassword) Res.string.settings_net_proxy_password_kept else Res.string.settings_net_proxy_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(enabled = proxyValid && !proxyBusy, onClick = {
            proxyBusy = true
            proxyResult = null
            scope.launch {
                proxyResult = net.testProxy(kind, host, portNum ?: 0, user, pass.ifEmpty { null })
                proxyBusy = false
            }
        }) { Text(stringResource(if (proxyBusy) Res.string.settings_net_testing else Res.string.settings_net_test)) }
        TextButton(enabled = dirty && (!enabled || proxyValid), onClick = {
            scope.launch {
                proxySaved = net.saveProxy(enabled, kind, host, portNum ?: st.proxyPort, user, pass.ifEmpty { null })
                pass = ""
            }
        }) { Text(stringResource(Res.string.settings_linksrv_save)) }
    }
    TestResultText(proxyResult)
    if (proxySaved == false) Text(stringResource(Res.string.settings_net_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    // what goes through the proxy, and the torrents' exception
    Text(
        stringResource(if (st.proxyEnabled && st.proxyKind == NetProxyKind.HTTP) Res.string.settings_net_proxy_scope_http else Res.string.settings_net_proxy_scope),
        style = MaterialTheme.typography.bodySmall,
        color = if (st.proxyEnabled && st.proxyKind == NetProxyKind.HTTP) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // ---- FlareSolverr ----
    var fs by remember(st.flareSolverrUrl) { mutableStateOf(st.flareSolverrUrl) }
    var fsResult by remember { mutableStateOf<NetTestResult?>(null) }
    var fsBusy by remember { mutableStateOf(false) }
    var fsSaved by remember { mutableStateOf<Boolean?>(null) }
    SettingRow(label = stringResource(Res.string.settings_net_flaresolverr), hint = stringResource(Res.string.settings_net_flaresolverr_hint), stack = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fs,
                onValueChange = { fs = it.trim().take(512) },
                placeholder = { Text("http://127.0.0.1:8191") },
                singleLine = true,
                modifier = Modifier.widthIn(min = 280.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(enabled = fs.isNotBlank() && !fsBusy, onClick = {
                    fsBusy = true
                    fsResult = null
                    scope.launch {
                        fsResult = net.testFlareSolverr(fs)
                        fsBusy = false
                    }
                }) { Text(stringResource(if (fsBusy) Res.string.settings_net_testing else Res.string.settings_net_test)) }
                TextButton(enabled = fs != st.flareSolverrUrl, onClick = { scope.launch { fsSaved = net.saveFlareSolverr(fs) } }) {
                    Text(stringResource(Res.string.settings_linksrv_save))
                }
            }
            TestResultText(fsResult)
            if (fsSaved == false) Text(stringResource(Res.string.settings_net_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    // ---- loopback media proxy ----
    SwitchRow(
        label = stringResource(Res.string.settings_net_local_proxy),
        hint = stringResource(Res.string.settings_net_local_proxy_hint),
        checked = st.localMediaProxy,
        onChange = net::setLocalMediaProxy,
    )
}

@Composable
private fun TestResultText(r: NetTestResult?) {
    when (r) {
        null -> Unit
        is NetTestResult.Ok -> Text(
            listOfNotNull(stringResource(Res.string.settings_net_test_ok, r.latencyMs.toString()), r.detail).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        is NetTestResult.Failed -> Text(
            stringResource(Res.string.settings_net_test_failed, r.reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
