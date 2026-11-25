// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.feature.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.feature.extensions.resources.Res
import app.nijiyome.feature.extensions.resources.*
import app.nijiyome.model.AppError
import app.nijiyome.model.torrent.DebridAccount
import app.nijiyome.model.torrent.DebridProblem
import app.nijiyome.model.torrent.DebridService
import app.nijiyome.model.torrent.debridProblemOf
import app.nijiyome.ui.format.relativeTime
import app.nijiyome.ui.platform.rememberOpenUrl
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date

// Sparkles S9: Fuentes › Debrid. The accounts (link with a key, check it, unlink with confirmation), their premium state and the three
// switches that used to sit in Torrents › Debrid and Settings › Torrents. A cached torrent then plays at once over HTTPS.

internal fun LazyListScope.debridSection(ctx: SourcesContext) {
    val state = ctx.debridState
    item(key = "debrid-intro", contentType = "hint") { TabHint(stringResource(if (kaiDropAndPaths) Res.string.rd_s9_debrid_intro_desktop else Res.string.rd_s9_debrid_intro_phone)) }
    if (!state.available) {
        item(key = "debrid-unavailable", contentType = "state") { TabHint(stringResource(Res.string.rd_s9_debrid_unavailable)) }
        return
    }
    if (state.accounts.isEmpty()) {
        // The "no account" state: what happens now and what linking changes.
        item(key = "debrid-none", contentType = "banner") {
            Banner(Icons.Outlined.CloudOff, BannerTone.INFO, stringResource(Res.string.rd_s9_debrid_none_title), stringResource(Res.string.rd_s9_debrid_none_body))
        }
    }
    item(key = "debrid-switches", contentType = "switches") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                val s = state.settings
                DebridSwitch(
                    stringResource(Res.string.rd_s9_debrid_use),
                    stringResource(Res.string.rd_s9_debrid_use_hint),
                    checked = s.enabled,
                    onChange = { on -> ctx.debrid.setSettings { it.copy(enabled = on) } },
                )
                DebridSwitch(
                    stringResource(Res.string.rd_s9_debrid_prefer),
                    stringResource(Res.string.rd_s9_debrid_prefer_hint),
                    checked = s.preferCached && s.enabled,
                    enabled = s.enabled,
                    onChange = { on -> ctx.debrid.setSettings { it.copy(preferCached = on) } },
                )
                DebridSwitch(
                    stringResource(Res.string.rd_s9_debrid_download),
                    stringResource(Res.string.rd_s9_debrid_download_hint),
                    checked = s.downloadCached && s.enabled,
                    enabled = s.enabled,
                    onChange = { on -> ctx.debrid.setSettings { it.copy(downloadCached = on) } },
                )
            }
        }
    }
    val order = debridOrder(state.accounts)
    items(order, key = { "debrid:${it.id}" }, contentType = { "debrid" }) { service ->
        val account = state.accounts.firstOrNull { it.service == service }
        DebridServiceCard(
            ctx = ctx,
            service = service,
            account = account,
            position = state.accounts.indexOfFirst { it.service == service }.takeIf { it >= 0 },
        )
    }
    item(key = "debrid-order", contentType = "hint") {
        Text(stringResource(Res.string.rd_s9_debrid_order_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DebridSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun DebridServiceCard(ctx: SourcesContext, service: DebridService, account: DebridAccount?, position: Int?) {
    val state = ctx.debridState
    val error = state.errors[service]
    val linking = state.linking == service
    val checking = state.checking == service
    val openUrl = rememberOpenUrl()
    val clipboard = LocalClipboardManager.current
    var key by remember(service, account) { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                Text(service.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                // TorBox is the tested service; the others keep working as before, untested.
                Pill(
                    stringResource(if (service.recommended) Res.string.rd_s9_debrid_recommended else Res.string.rd_s9_debrid_experimental),
                    tone = if (service.recommended) PillTone.PRIMARY else PillTone.WARN,
                )
                if (position != null) Pill(stringResource(Res.string.rd_s9_debrid_position, position + 1), tone = if (position == 0) PillTone.PRIMARY else PillTone.NEUTRAL)
                Pill(
                    stringResource(if (account != null) Res.string.rd_s9_debrid_linked else Res.string.rd_s9_debrid_not_linked),
                    tone = if (account != null) PillTone.OK else PillTone.NEUTRAL,
                )
            }
            Text(
                stringResource(
                    when {
                        service.instantCheck() -> Res.string.rd_s9_debrid_instant
                        kaiDropAndPaths -> Res.string.rd_s9_debrid_probe_desktop
                        else -> Res.string.rd_s9_debrid_probe_phone
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (account != null) {
                val expired = (account.premiumUntilMs ?: Long.MAX_VALUE) < System.currentTimeMillis()
                Text(
                    listOf(
                        stringResource(Res.string.rd_s9_debrid_account, account.username ?: service.label),
                        account.premiumUntilMs?.let { until ->
                            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(until))
                            stringResource(if (expired) Res.string.rd_s9_debrid_expired else Res.string.rd_s9_debrid_until, date)
                        } ?: stringResource(Res.string.rd_s9_debrid_premium_unknown),
                        stringResource(Res.string.rd_s9_debrid_key_saved),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(Res.string.rd_s9_debrid_linked_since, relativeTime(account.linkedAtMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { ctx.debrid.recheck(service) }, enabled = !checking) {
                        if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(if (checking) Res.string.rd_s9_debrid_checking else Res.string.rd_s9_debrid_check),
                            modifier = Modifier.padding(start = if (checking) 8.dp else 0.dp),
                        )
                    }
                    if (expired) {
                        TextButton(onClick = { openUrl(debridKeyPage(service)) }) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.rd_s9_debrid_renew, service.label), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    TextButton(onClick = { ctx.askUnlinkDebrid(service) }) {
                        Text(stringResource(Res.string.rd_s9_debrid_unlink_ellipsis), color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                OutlinedTextField(
                    value = key,
                    onValueChange = {
                        key = it.trim()
                        if (error != null) ctx.debrid.clearError(service)
                    },
                    label = { Text(stringResource(Res.string.rd_s9_debrid_key)) },
                    placeholder = { Text(stringResource(Res.string.rd_s9_debrid_key_placeholder, service.label)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            runCatching { clipboard.getText()?.text?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { key = it }
                        }) { Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(Res.string.rd_s9_paste)) }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null,
                    supportingText = error?.let { e -> { Text(stringResource(debridProblemRes(e))) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { ctx.debrid.link(service, key) }, enabled = key.isNotBlank() && !linking) {
                        if (linking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.rd_s9_debrid_test_link), modifier = Modifier.padding(start = if (linking) 8.dp else 0.dp))
                    }
                    TextButton(onClick = { openUrl(debridKeyPage(service)) }) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.rd_s9_debrid_get_key, service.label), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** TorBox and Premiumize say before playing whether a torrent is cached; the others are tried when it plays. */
private fun DebridService.instantCheck(): Boolean = this == DebridService.TORBOX || this == DebridService.PREMIUMIZE

/** The service's own reason in words ([debridProblemOf]): a wrong key, a plan limit, offline... */
internal fun debridProblemRes(error: AppError): StringResource = when (debridProblemOf(error)) {
    DebridProblem.INVALID_KEY -> Res.string.rd_s9_debrid_err_invalid_key
    DebridProblem.ACTIVE_LIMIT -> Res.string.rd_s9_debrid_err_active_limit
    DebridProblem.MONTHLY_LIMIT -> Res.string.rd_s9_debrid_err_monthly_limit
    DebridProblem.PLAN -> Res.string.rd_s9_debrid_err_plan
    DebridProblem.TOO_LARGE -> Res.string.rd_s9_debrid_err_too_large
    DebridProblem.RATE_LIMITED -> Res.string.rd_s9_debrid_err_rate_limited
    DebridProblem.NOT_CACHED -> Res.string.rd_s9_debrid_err_not_cached
    DebridProblem.OFFLINE -> Res.string.rd_s9_debrid_err_offline
    DebridProblem.BAD_LINK -> Res.string.rd_s9_debrid_err_bad_link
    DebridProblem.NO_FILE -> Res.string.rd_s9_debrid_err_no_file
    DebridProblem.SERVICE -> Res.string.rd_s9_debrid_err_service
}
