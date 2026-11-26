// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.nijiyome.feature.link

import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.feature.link.resources.Res
import app.nijiyome.feature.link.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.extensionserver.ServerStatus
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * "PC extension server" (docs/redesign/13 §3.10, D8): connect this phone to the extension server of Nijiyome on a Windows / Linux PC
 * (scan the QR, paste the `nijiyome://extensionserver/connect` link, or address + viewer password), manage the linked servers and read
 * what linking implies (advantages, disadvantages, extras, coming soon). A link opened from outside the app asks before pairing.
 */
@Composable
fun ExtensionServersScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ExtensionServersViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExtensionServersEvent.Connected -> snackbar.show(getString(Res.string.extserver_connected, event.name))
            }
        }
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = app.nijiyome.ui.state.screenInsets,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(platformHint(Res.string.extserver_title)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(Res.string.extserver_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExtensionServersBody(viewModel, state)
            }
        }
    }
}

/**
 * Sparkles S9: the extension-server content without its own scaffold or scroll, so Fuentes › Servidores en tu PC can embed it (the shell
 * passes `serversContent = { ExtensionServersContent() }` to `ExtensionsScreen`). Connect (QR, pasted link, address and viewer password),
 * the linked servers (enable, remove with confirmation, state), the folded "Más información" and the confirmation of an incoming
 * `nijiyome://` link.
 */
@Composable
fun ExtensionServersContent(modifier: Modifier = Modifier) {
    val viewModel: ExtensionServersViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExtensionServersEvent.Connected -> snackbar.show(getString(Res.string.extserver_connected, event.name))
            }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExtensionServersBody(viewModel, state)
    }
}

@Composable
private fun ExtensionServersBody(viewModel: ExtensionServersViewModel, state: ExtensionServersUiState) {
    Text(
        stringResource(platformHint(Res.string.extserver_intro)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.error?.let { ErrorBanner(errorText(it), onDismiss = viewModel::clearError) }
    ConnectPanel(
        connecting = state.connecting,
        isPlausible = viewModel::isPlausibleLink,
        onLink = viewModel::connectWithText,
        onManual = viewModel::connectManual,
    )
    ServersPanel(
        state = state,
        onEnabled = viewModel::setEnabled,
        onRemove = viewModel::remove,
    )
    InfoPanel()
    state.pendingLink?.let { link ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPending,
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
            title = { Text(stringResource(Res.string.extserver_confirm_title)) },
            text = { Text(stringResource(platformHint(Res.string.extserver_confirm_body), link.server)) },
            confirmButton = { TextButton(onClick = viewModel::confirmPending, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.extserver_connect)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissPending, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- connect

@Composable
private fun ConnectPanel(
    connecting: Boolean,
    isPlausible: (String) -> Boolean,
    onLink: (String) -> Unit,
    onManual: (String, String) -> Unit,
) {
    var scanning by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    // Not saveable on purpose: the password must not end up in the saved instance state.
    var password by remember { mutableStateOf("") }
    val permission = rememberCameraPermission { granted ->
        denied = !granted
        scanning = granted
    }

    Panel(icon = Icons.Outlined.QrCode, title = stringResource(platformHint(Res.string.extserver_pair_title))) {
        Hint(stringResource(platformHint(Res.string.extserver_pair_hint)))
        if (scanning) {
            QrScanner(
                onResult = {
                    scanning = false
                    onLink(it)
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large),
            )
            TextButton(onClick = { scanning = false }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.extserver_scan_cancel)) }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (qrCameraAvailable) {
                    Button(
                        onClick = {
                            if (permission.isGranted()) scanning = true else permission.request()
                        },
                        enabled = !connecting,
                        shape = NijiyomeControls.shape,
                    ) {
                        if (connecting) {
                            LoadingIndicator(Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Text(
                            stringResource(if (connecting) Res.string.extserver_connecting else Res.string.extserver_scan),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    FilledTonalButton(onClick = { pasting = true }, enabled = !connecting, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(Res.string.extserver_paste_link), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    // no camera (desktop without a webcam): pasting the link is the main way in
                    Button(onClick = { pasting = true }, enabled = !connecting, shape = NijiyomeControls.shape) {
                        if (connecting) {
                            LoadingIndicator(Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Text(
                            stringResource(if (connecting) Res.string.extserver_connecting else Res.string.extserver_paste_link),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        if (denied) {
            Text(
                stringResource(Res.string.extserver_camera_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizontalDivider()
        Text(stringResource(Res.string.extserver_manual_title), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(Res.string.extserver_url_label)) },
            placeholder = { Text(stringResource(Res.string.extserver_url_placeholder)) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = NijiyomeFonts.jetBrainsMono),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.extserver_password_label)) },
            supportingText = { Text(stringResource(platformHint(Res.string.extserver_password_hint))) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onManual(url, password)
                password = ""
            },
            enabled = !connecting && url.isNotBlank(),
            shape = NijiyomeControls.shape,
        ) { Text(stringResource(if (connecting) Res.string.extserver_connecting else Res.string.extserver_connect)) }
        Hint(stringResource(Res.string.extserver_lan_only_http))
        Hint(stringResource(Res.string.extserver_away_from_home))
    }
    if (pasting) {
        PasteLinkDialog(
            isPlausible = isPlausible,
            onDismiss = { pasting = false },
            onConnect = {
                pasting = false
                onLink(it)
            },
        )
    }
}

@Composable
private fun PasteLinkDialog(isPlausible: (String) -> Boolean, onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    val readClipboard = rememberClipboardReader()
    // BUG-012: the button says "paste", so the dialog opens with the clipboard's pairing link already in the field (only when it looks like
    // one: never a random clipboard text). The field stays editable for a hand-typed link.
    var text by rememberSaveable { mutableStateOf(readClipboard()?.takeIf { isPlausible(it) }.orEmpty()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val invalid = showError && !isPlausible(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.QrCode, contentDescription = null) },
        title = { Text(stringResource(Res.string.extserver_paste_link)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    showError = false
                },
                label = { Text(stringResource(Res.string.extserver_link_label)) },
                placeholder = { Text(stringResource(Res.string.extserver_link_placeholder)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(Res.string.extserver_err_invalid_link)) }
                } else {
                    null
                },
                textStyle = TextStyle(fontFamily = NijiyomeFonts.jetBrainsMono),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.focusOnOpen().fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (isPlausible(text)) onConnect(text.trim()) else showError = true },
                enabled = text.isNotBlank(),
                shape = NijiyomeControls.shape,
            ) { Text(stringResource(Res.string.extserver_connect)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

// ---------------------------------------------------------------- servers

@Composable
private fun ServersPanel(state: ExtensionServersUiState, onEnabled: (String, Boolean) -> Unit, onRemove: (String) -> Unit) {
    Panel(icon = Icons.Outlined.Computer, title = stringResource(Res.string.extserver_servers_title)) {
        when {
            !state.loaded -> if (rememberAfterDelay(150)) {
                SkeletonBox(Modifier.fillMaxWidth().aspectRatio(6f))
            }
            state.servers.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Extension,
                title = stringResource(Res.string.extserver_none),
                message = stringResource(platformHint(Res.string.extserver_none_body)),
            )
            else -> {
                state.servers.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider()
                    ServerItem(
                        row,
                        // BUG-014: the "not responding" note sits under the server that does not respond, and says whether another
                        // linked server still answers, so an online PC listed below an old dead entry does not look broken.
                        othersOnline = state.servers.any { it !== row && it.connection.enabled && it.status is ServerStatus.Online },
                        onEnabled = { onEnabled(row.connection.id, it) },
                        onRemove = { onRemove(row.connection.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerItem(row: ServerRow, othersOnline: Boolean, onEnabled: (Boolean) -> Unit, onRemove: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val conn = row.connection
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(conn.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    conn.url,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = conn.enabled, onCheckedChange = onEnabled)
            IconButton(onClick = { confirming = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.extserver_remove))
            }
        }
        StatusPill(enabled = conn.enabled, status = row.status)
        if (conn.enabled && row.status == ServerStatus.Offline) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Hint(
                    stringResource(
                        if (othersOnline) Res.string.extserver_offline_fallback_others else Res.string.extserver_offline_fallback,
                    ),
                )
            }
        }
        if (conn.enabled && row.status == ServerStatus.Unauthorized) {
            Text(
                stringResource(Res.string.extserver_unauthorized),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(Res.string.extserver_remove_title)) },
            text = { Text(stringResource(platformHint(Res.string.extserver_remove_body), conn.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onRemove()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = NijiyomeControls.shape,
                ) { Text(stringResource(Res.string.extserver_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
}

@Composable
private fun StatusPill(enabled: Boolean, status: ServerStatus) {
    if (!enabled) {
        Pill(stringResource(Res.string.extserver_disabled))
        return
    }
    when (status) {
        is ServerStatus.Online -> Pill(
            status.sourceCount?.let { pluralStringResource(Res.plurals.extserver_online_sources, it, it) } ?: stringResource(Res.string.extserver_online),
            tone = PillTone.OK,
        )
        ServerStatus.Offline -> Pill(stringResource(Res.string.extserver_offline), tone = PillTone.ERR)
        ServerStatus.Unauthorized -> Pill(stringResource(Res.string.extserver_revoked), tone = PillTone.WARN)
        ServerStatus.Checking -> Pill(stringResource(Res.string.extserver_checking))
        ServerStatus.Unknown -> Pill(stringResource(Res.string.extserver_unknown))
    }
}

// ---------------------------------------------------------------- D8 information

@Composable
private fun InfoPanel() {
    // Sparkles S9 (LNK-16): the long fixed text is folded under "Más información".
    var open by rememberSaveable { mutableStateOf(false) }
    Panel(
        icon = Icons.Outlined.Info,
        title = stringResource(platformHint(Res.string.extserver_benefits_title)),
        trailing = {
            TextButton(onClick = { open = !open }, shape = NijiyomeControls.shape) {
                Text(stringResource(if (open) Res.string.rd_s9_less_info else Res.string.rd_s9_more_info))
            }
        },
    ) {
        if (open) InfoBody()
    }
}

@Composable
private fun InfoBody() {
    run {
        InfoList(
            stringResource(Res.string.extserver_pros_title),
            listOf(
                platformHint(Res.string.extserver_pro_other_ecosystems),
                Res.string.extserver_pro_install_once,
                platformHint(Res.string.extserver_pro_pc_scrapes),
                Res.string.extserver_pro_shared_cache,
                platformHint(Res.string.extserver_pro_battery),
            ),
        )
        InfoList(
            stringResource(Res.string.extserver_cons_title),
            listOf(
                platformHint(Res.string.extserver_con_pc_on),
                Res.string.extserver_con_tunnel,
                platformHint(Res.string.extserver_con_video_proxy),
                Res.string.extserver_con_lan_port,
                Res.string.extserver_con_latency,
            ),
        )
        InfoList(
            stringResource(Res.string.extserver_extras_title),
            listOf(
                Res.string.extserver_extra_qr,
                Res.string.extserver_extra_revocable,
                Res.string.extserver_extra_fallback,
                Res.string.extserver_extra_viewer_password,
            ),
        )
    }
}

@Composable
private fun InfoList(title: String, items: List<StringResource>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        items.forEach { res ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun errorText(error: ExtServerError): String = when (error) {
    ExtServerError.InvalidLink -> stringResource(Res.string.extserver_err_invalid_link)
    is ExtServerError.InsecureHost -> error.host?.takeIf { it.isNotBlank() }
        ?.let { stringResource(Res.string.extserver_err_insecure_host, it) }
        ?: stringResource(Res.string.extserver_err_insecure_generic)
    ExtServerError.InvalidUrl -> stringResource(Res.string.extserver_err_invalid_url)
    ExtServerError.ExpiredLink -> stringResource(Res.string.extserver_err_expired_link)
    ExtServerError.Unreachable -> stringResource(Res.string.extserver_err_unreachable)
    ExtServerError.WrongPassword -> stringResource(Res.string.extserver_err_wrong_password)
    ExtServerError.PairingRequired -> stringResource(platformHint(Res.string.extserver_err_pairing_required))
    ExtServerError.Incompatible -> stringResource(Res.string.extserver_err_incompatible)
    ExtServerError.SetupRequired -> stringResource(Res.string.extserver_err_setup_required)
    ExtServerError.Throttled -> stringResource(Res.string.extserver_err_throttled)
    is ExtServerError.Other -> stringResource(Res.string.extserver_err_generic, error.code)
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) }
        }
    }
}
