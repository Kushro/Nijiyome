// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Icon
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
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.NetProxyKind
import app.nijiyome.model.NetTestResult
import app.nijiyome.model.NetworkSettingsController
import app.nijiyome.ui.patterns.PortPicker
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Desktop network rows (Tatakai Next's server › network), shown only where a NetworkSettingsController is registered. Since the
// Sparkles redesign they live in two places: the outbound proxy and the local media proxy in Ajustes › Sistema, FlareSolverr in
// Ajustes › Fuentes de episodios. The proxy and FlareSolverr keep an explicit "Probar y guardar" (a connection is tested first).

/** A host typed by hand: no spaces, paths, queries or credentials. */
internal fun proxyHostValid(host: String): Boolean = host.isNotBlank() && host.none { it.isWhitespace() || it in "/?#@" }

/** An `http(s)://` URL with a host, as FlareSolverr's endpoint. */
internal fun httpUrlValid(url: String): Boolean {
    val u = url.trim()
    val rest = when {
        u.startsWith("http://") -> u.removePrefix("http://")
        u.startsWith("https://") -> u.removePrefix("https://")
        else -> return false
    }
    return rest.isNotEmpty() && rest.first() != '/' && rest.none { it.isWhitespace() }
}

/** Outbound HTTP / SOCKS5 proxy with optional credentials: draft, then "Probar y guardar". */
@Composable
internal fun ProxySettings(net: NetworkSettingsController) {
    val st by net.state.collectAsState()
    val scope = rememberCoroutineScope()
    var enabled by remember(st.proxyEnabled) { mutableStateOf(st.proxyEnabled) }
    var kind by remember(st.proxyKind) { mutableStateOf(st.proxyKind) }
    var host by remember(st.proxyHost) { mutableStateOf(st.proxyHost) }
    var port by remember(st.proxyPort) { mutableStateOf(st.proxyPort.coerceIn(1, 65535)) }
    var user by remember(st.proxyUsername) { mutableStateOf(st.proxyUsername) }
    var pass by remember(st.hasPassword) { mutableStateOf("") }
    var result by remember { mutableStateOf<NetTestResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val hostOk = proxyHostValid(host)
    val dirty = enabled != st.proxyEnabled || kind != st.proxyKind || host != st.proxyHost || port != st.proxyPort ||
        user != st.proxyUsername || pass.isNotEmpty()
    fun discard() {
        enabled = st.proxyEnabled; kind = st.proxyKind; host = st.proxyHost; port = st.proxyPort.coerceIn(1, 65535)
        user = st.proxyUsername; pass = ""; result = null; saveFailed = false
    }
    fun save() {
        scope.launch {
            saveFailed = !net.saveProxy(enabled, kind, host, port, user, pass.ifEmpty { null })
            if (!saveFailed) pass = ""
        }
    }

    SwitchRow(
        label = stringResource(Res.string.settings_net_proxy),
        hint = stringResource(Res.string.settings_net_proxy_hint),
        checked = enabled,
        onChange = { enabled = it },
        divider = false,
    )
    SettingRow(label = stringResource(Res.string.settings_net_proxy_type)) {
        Segmented(
            options = listOf(SegmentedOption(NetProxyKind.SOCKS5, "SOCKS5"), SegmentedOption(NetProxyKind.HTTP, "HTTP")),
            selected = kind,
            onSelect = { kind = it },
        )
    }
    SettingRow(label = stringResource(Res.string.settings_net_proxy_address), hint = stringResource(Res.string.settings_net_proxy_address_hint), stack = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim().take(253) },
                label = { Text(stringResource(Res.string.settings_net_proxy_host)) },
                placeholder = { Text("127.0.0.1") },
                singleLine = true,
                isError = host.isNotEmpty() && !hostOk,
                supportingText = if (host.isNotEmpty() && !hostOk) ({ Text(stringResource(Res.string.rd_s10_proxy_host_invalid)) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            PortPicker(port = port, onPortChange = { port = it }, label = stringResource(Res.string.settings_net_proxy_port), warnPrivileged = false)
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
    // what goes through the proxy, and the torrents' exception
    Text(
        stringResource(if (st.proxyEnabled && st.proxyKind == NetProxyKind.HTTP) Res.string.settings_net_proxy_scope_http else Res.string.settings_net_proxy_scope),
        style = MaterialTheme.typography.bodySmall,
        color = if (st.proxyEnabled && st.proxyKind == NetProxyKind.HTTP) LocalSignalColors.current.warn else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CommitBar(
        dirty = dirty,
        busy = busy,
        canApply = !enabled || hostOk,
        onDiscard = ::discard,
        applyLabel = stringResource(if (enabled) Res.string.rd_s10_commit_test_save else Res.string.rd_s10_commit_apply),
        onApply = {
            if (!enabled) {
                save()
            } else {
                busy = true
                result = null
                scope.launch {
                    val r = net.testProxy(kind, host, port, user, pass.ifEmpty { null })
                    result = r
                    busy = false
                    if (r is NetTestResult.Ok) save()
                }
            }
        },
        result = {
            TestResultLine(result)
            if (result is NetTestResult.Failed && dirty) {
                TextButton(onClick = ::save, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s10_commit_save_anyway)) }
            }
            if (saveFailed) Text(stringResource(Res.string.settings_net_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        },
    )
}

/** FlareSolverr's URL: validated as a URL, then "Probar y guardar" (empty = off, saved without a test). */
@Composable
internal fun FlareSolverrSettings(net: NetworkSettingsController) {
    val st by net.state.collectAsState()
    val scope = rememberCoroutineScope()
    var url by remember(st.flareSolverrUrl) { mutableStateOf(st.flareSolverrUrl) }
    var result by remember { mutableStateOf<NetTestResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val urlOk = url.isBlank() || httpUrlValid(url)
    fun save() {
        scope.launch { saveFailed = !net.saveFlareSolverr(url) }
    }
    SettingRow(label = stringResource(Res.string.settings_net_flaresolverr), hint = stringResource(Res.string.settings_net_flaresolverr_hint), stack = true, divider = false) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.trim().take(512) },
            placeholder = { Text("http://127.0.0.1:8191") },
            singleLine = true,
            isError = !urlOk,
            supportingText = if (!urlOk) ({ Text(stringResource(Res.string.rd_s10_url_invalid)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    CommitBar(
        dirty = url != st.flareSolverrUrl,
        busy = busy,
        canApply = urlOk,
        onDiscard = { url = st.flareSolverrUrl; result = null; saveFailed = false },
        applyLabel = stringResource(if (url.isBlank()) Res.string.rd_s10_commit_apply else Res.string.rd_s10_commit_test_save),
        onApply = {
            if (url.isBlank()) {
                save()
            } else {
                busy = true
                result = null
                scope.launch {
                    val r = net.testFlareSolverr(url)
                    result = r
                    busy = false
                    if (r is NetTestResult.Ok) save()
                }
            }
        },
        result = {
            TestResultLine(result)
            if (result is NetTestResult.Failed && url != st.flareSolverrUrl) {
                TextButton(onClick = ::save, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s10_commit_save_anyway)) }
            }
            if (saveFailed) Text(stringResource(Res.string.settings_net_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        },
    )
}

/** The player's loopback media proxy (applies on change). */
@Composable
internal fun MediaProxyRow(net: NetworkSettingsController) {
    val st by net.state.collectAsState()
    SwitchRow(
        label = stringResource(Res.string.settings_net_local_proxy),
        hint = stringResource(Res.string.settings_net_local_proxy_hint),
        checked = st.localMediaProxy,
        onChange = net::setLocalMediaProxy,
    )
}

@Composable
private fun TestResultLine(r: NetTestResult?) {
    val signals = LocalSignalColors.current
    when (r) {
        null -> Unit
        is NetTestResult.Ok -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.politeLiveRegion()) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = signals.ok, modifier = Modifier.size(18.dp))
            Text(
                listOfNotNull(stringResource(Res.string.settings_net_test_ok, r.latencyMs.toString()), r.detail).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = signals.ok,
            )
        }
        is NetTestResult.Failed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.politeLiveRegion()) {
            Icon(Icons.Outlined.Error, contentDescription = null, tint = signals.err, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.settings_net_test_failed, r.reason), style = MaterialTheme.typography.bodySmall, color = signals.err)
        }
    }
}
