// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.extensions

import app.nijiyome.feature.extensions.resources.Res
import app.nijiyome.feature.extensions.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.extensionserver.ServerExtension
import app.nijiyome.model.InstalledExtension
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// Parity wave B closure (16 §3.4, worker t; checklist server.api.extensions): "Extensions on your PC" section of Extensions, one block per
// linked extension server with the list its `GET /extensions` returns. Pairing and removing servers stay in Linked devices.

internal fun LazyListScope.serverExtensionSections(
    state: ServerExtensionsUiState,
    vm: ServerExtensionsViewModel,
    // BUG-016: the phone's installed extensions, to say when the PC has a newer copy; [updatable] ids have a repository update.
    local: List<InstalledExtension> = emptyList(),
    updatable: Set<String> = emptySet(),
    onUpdate: (String) -> Unit = {},
    /** Desktop: the way into the extension servers screen (pair, enable, remove), under the head. */
    onOpenServers: (() -> Unit)? = null,
) {
    if (!state.available) return
    item(key = "srvext-head", contentType = "head") {
        if (onOpenServers == null) {
            SectionHead(stringResource(Res.string.extensions_server_title), stringResource(Res.string.extensions_server_hint))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHead(stringResource(Res.string.extensions_server_title_pc), stringResource(Res.string.extensions_server_hint_pc))
                FilledTonalButton(onClick = onOpenServers) {
                    Icon(Icons.Outlined.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.extensions_server_manage), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (!state.loaded) {
        item(key = "srvext-loading", contentType = "state") {
            if (rememberAfterDelay()) LoadingState(Modifier.fillMaxWidth().height(96.dp))
        }
        return
    }
    if (state.servers.isEmpty()) {
        item(key = "srvext-empty", contentType = "state") {
            Text(
                stringResource(Res.string.extensions_server_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    state.servers.forEach { block ->
        item(key = "srvext-server:${block.id}", contentType = "server-head") {
            ServerHead(block, onRefresh = { vm.refresh(block.id) })
        }
        when (val load = block.load) {
            ServerExtensionsLoad.Loading -> item(key = "srvext-loading:${block.id}", contentType = "state") {
                if (rememberAfterDelay()) LoadingState(Modifier.fillMaxWidth().height(72.dp))
            }
            ServerExtensionsLoad.Disabled -> item(key = "srvext-disabled:${block.id}", contentType = "state") {
                Text(
                    stringResource(Res.string.extensions_server_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is ServerExtensionsLoad.Err -> item(key = "srvext-error:${block.id}", contentType = "state") {
                ErrorState(
                    message = stringResource(serverExtErrorRes(load.error)),
                    retryLabel = stringResource(UiR.string.ui_retry),
                    onRetry = { vm.refresh(block.id) },
                    compact = true,
                )
            }
            is ServerExtensionsLoad.Ok -> if (load.extensions.isEmpty()) {
                item(key = "srvext-none:${block.id}", contentType = "state") {
                    Text(
                        stringResource(Res.string.extensions_server_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(load.extensions, key = { "srvext:${block.id}/${it.id}" }, contentType = { "server-ext" }) { ext ->
                    val older = olderLocalCopy(ext, local)
                    ServerExtensionRow(
                        ext,
                        olderLocal = older,
                        onUpdate = older?.id?.takeIf { it in updatable }?.let { id -> { onUpdate(id) } },
                        // BUG-055: install the PC's copy even without a repository.
                        onUpdateFromPc = if (older != null && state.canInstall) ({ vm.updateFromPc(block.id, ext) }) else null,
                        pcUpdate = state.pcUpdates[pcUpdateKey(block.id, ext.id)],
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerHead(block: ServerExtensionsBlock, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(block.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = (block.load as? ServerExtensionsLoad.Ok)?.extensions?.size
            Text(
                if (count != null) pluralStringResource(Res.plurals.extensions_server_count, count, count) else block.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (block.load !is ServerExtensionsLoad.Disabled) {
            IconButton(onClick = onRefresh, enabled = block.load !is ServerExtensionsLoad.Loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(Res.string.extensions_server_refresh, block.name))
            }
        }
    }
}

@Composable
private fun ServerExtensionRow(
    ext: ServerExtension,
    olderLocal: InstalledExtension?,
    onUpdate: (() -> Unit)?,
    onUpdateFromPc: (() -> Unit)? = null,
    pcUpdate: PcUpdate? = null,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ext.name.ifBlank { ext.id }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        ext.version.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.extensions_server_version, it) },
                        ext.ecosystem.takeIf { it.isNotBlank() },
                        serverExtensionKinds(ext.kind).takeIf { it.isNotEmpty() }?.map { stringResource(providerKindRes(it)) }?.joinToString(", "),
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Pill(
                    stringResource(if (ext.enabled) Res.string.extensions_server_on else Res.string.extensions_server_off),
                    tone = if (ext.enabled) PillTone.OK else PillTone.NEUTRAL,
                )
            }
            if (olderLocal != null) {
                // BUG-016: the local copy is older, so what this phone finds on its own (PC off) comes from the old build.
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        stringResource(Res.string.extensions_server_newer, ext.name.ifBlank { olderLocal.name }, ext.version, olderLocal.version),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                val running = pcUpdate == PcUpdate.Running
                if (onUpdate != null || onUpdateFromPc != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        if (onUpdate != null) {
                            TextButton(onClick = onUpdate, enabled = !running) {
                                Text(stringResource(Res.string.extensions_server_newer_update), maxLines = 1)
                            }
                        }
                        if (onUpdateFromPc != null) {
                            FilledTonalButton(onClick = onUpdateFromPc, enabled = !running) {
                                if (running) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(stringResource(Res.string.extensions_server_pc_updating), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                                } else {
                                    Text(stringResource(Res.string.extensions_server_pc_update), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            val message = when (pcUpdate) {
                is PcUpdate.Done -> stringResource(Res.string.extensions_updated_to, pcUpdate.name, pcUpdate.version)
                is PcUpdate.Failed -> stringResource(pcUpdateErrorRes(pcUpdate.error))
                else -> null
            }
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pcUpdate is PcUpdate.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

internal fun pcUpdateErrorRes(error: PcUpdateError): StringResource = when (error) {
    PcUpdateError.UNREACHABLE -> Res.string.extensions_server_pc_update_err_unreachable
    PcUpdateError.UNAUTHORIZED -> Res.string.extensions_server_err_unauthorized
    PcUpdateError.NOT_ON_PC -> Res.string.extensions_server_pc_update_err_gone
    PcUpdateError.PC_TOO_OLD -> Res.string.extensions_server_pc_update_err_old_pc
    PcUpdateError.INTEGRITY -> Res.string.extensions_server_pc_update_err_integrity
    PcUpdateError.INSTALL -> Res.string.extensions_server_pc_update_err_install
}

internal fun serverExtErrorRes(error: ServerExtError): StringResource = when (error) {
    ServerExtError.UNREACHABLE -> Res.string.extensions_server_err_unreachable
    ServerExtError.UNAUTHORIZED -> Res.string.extensions_server_err_unauthorized
    ServerExtError.INCOMPATIBLE -> Res.string.extensions_server_err_incompatible
    ServerExtError.OTHER -> Res.string.extensions_server_err_other
}
