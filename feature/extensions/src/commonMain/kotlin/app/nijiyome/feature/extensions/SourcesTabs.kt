// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class)

package app.nijiyome.feature.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.extensions.resources.Res
import app.nijiyome.feature.extensions.resources.*
import app.nijiyome.model.LoadState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.designsystem.scroll.NjLazyColumn

// Sparkles S9, structure TABS ("pestanas"): the five concepts of the old Extensions column and Debrid, each on its own tab with its
// count. Every tab is a LazyColumn of the same sections the store structure opens in sheets.

internal enum class SourcesTab { INSTALLED, REPOS, STREMIO, PROVIDERS, SERVERS, DEBRID }

/** Width from which Instaladas shows the list and the selected extension side by side. */
private val TWO_PANE_MIN = 900.dp

@Composable
internal fun SourcesTabs(ctx: SourcesContext) {
    var tab by rememberSaveable { mutableStateOf(SourcesTab.INSTALLED) }
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp) {
            SourcesTab.entries.forEach { t ->
                Tab(
                    selected = t == tab,
                    onClick = { tab = t },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(t.labelRes()), maxLines = 1)
                            tabCount(ctx, t)?.let { n ->
                                Text(n.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                )
            }
        }
        when (tab) {
            SourcesTab.INSTALLED -> InstalledTab(ctx, onBrowse = { tab = SourcesTab.REPOS })
            SourcesTab.REPOS -> TabList { reposSection(ctx.hubState, ctx.hub, ctx.askRemoveRepo, showHead = false) }
            SourcesTab.STREMIO -> TabList { stremioSection(ctx.hubState, ctx.hub, ctx.askRemoveAddon, showHead = false) }
            SourcesTab.PROVIDERS -> TabList { providersSection(ctx.hubState, ctx.hub, ctx.onOpenProvider, showHead = false) }
            SourcesTab.SERVERS -> TabList { serversSection(ctx) }
            SourcesTab.DEBRID -> TabList { debridSection(ctx) }
        }
    }
}

internal fun SourcesTab.labelRes(): StringResource = when (this) {
    SourcesTab.INSTALLED -> Res.string.rd_s9_tab_installed
    SourcesTab.REPOS -> Res.string.rd_s9_tab_repos
    SourcesTab.STREMIO -> Res.string.rd_s9_tab_stremio
    SourcesTab.PROVIDERS -> Res.string.rd_s9_tab_providers
    // The desktop serves and borrows extensions between PCs; on the phone it is "your PC's extensions".
    SourcesTab.SERVERS -> if (kaiDropAndPaths) Res.string.rd_s9_tab_servers else Res.string.rd_s9_tab_servers_phone
    SourcesTab.DEBRID -> Res.string.rd_s9_tab_debrid
}

/** The number beside each tab (installed, repositories, addons, catalogs, linked servers, linked debrid accounts); null hides it. */
private fun tabCount(ctx: SourcesContext, tab: SourcesTab): Int? = when (tab) {
    SourcesTab.INSTALLED -> ctx.state.items.size.takeIf { !ctx.state.loading }
    SourcesTab.REPOS -> ctx.hubState.repos.size
    SourcesTab.STREMIO -> ctx.hubState.addons.size.takeIf { ctx.hubState.stremioAvailable }
    SourcesTab.PROVIDERS -> (ctx.hubState.providers as? LoadState.Ok)?.value?.size
    SourcesTab.SERVERS -> ctx.serverState.servers.size.takeIf { ctx.serverState.available && ctx.serverState.loaded }
    SourcesTab.DEBRID -> ctx.debridState.accounts.size.takeIf { ctx.debridState.available }
}

@Composable
private fun TabList(content: LazyListScope.() -> Unit) {
    NjLazyColumn(
        modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Instaladas: the install panel, the banners, the list and the danger zone. Wide windows put the selected extension beside the list;
 * narrow ones unfold it under its row.
 */
@Composable
private fun InstalledTab(ctx: SourcesContext, onBrowse: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val items = ctx.state.items
    val browseLabel = stringResource(Res.string.rd_s9_browse_repos)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TWO_PANE_MIN && items.isNotEmpty()
        if (twoPane) {
            val current = items.firstOrNull { it.id == selected } ?: items.first()
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NjLazyColumn(
                    modifier = Modifier.width(420.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "install", contentType = "install") { InstallPanel(ctx) }
                    item(key = "banners", contentType = "banners") { InstalledBanners(ctx) }
                    items(items, key = { it.id }, contentType = { "extension" }) { ext ->
                        ExtensionRow(ctx, ext, selected = ext.id == current.id, onSelect = { selected = ext.id })
                    }
                    item(key = "kill", contentType = "kill") { KillZone(ctx) }
                }
                ExtensionDetailPane(ctx, current, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            NjLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "install", contentType = "install") { InstallPanel(ctx) }
                item(key = "banners", contentType = "banners") { InstalledBanners(ctx) }
                when {
                    ctx.state.loading -> item(key = "loading", contentType = "state") { DelayedLoading() }
                    items.isEmpty() -> item(key = "empty", contentType = "state") { InstalledEmpty(ctx, onBrowse = onBrowse, browseLabel = browseLabel) }
                    else -> items(items, key = { it.id }, contentType = { "extension" }) { ext ->
                        ExtensionRowWithDetail(ctx, ext, open = ext.id == selected, onToggleOpen = { selected = if (selected == ext.id) null else ext.id })
                    }
                }
                item(key = "kill", contentType = "kill") { KillZone(ctx) }
            }
        }
    }
}

/**
 * Servidores en tu PC (phone: Extensiones de tu PC): what each linked extension server offers, its state and "update from the PC".
 * Connecting, enabling and removing servers is the extension-server content of `:feature:link`: embedded when the shell passes it,
 * else one press away ([SourcesContext.onOpenServers]).
 */
internal fun LazyListScope.serversSection(ctx: SourcesContext) {
    item(key = "srv-intro", contentType = "hint") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabHint(stringResource(if (kaiDropAndPaths) Res.string.rd_s9_servers_hint_desktop else Res.string.rd_s9_servers_hint_phone))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ctx.serversContent == null && ctx.onOpenServers != null) {
                    FilledTonalButton(onClick = ctx.onOpenServers, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.rd_s9_servers_configure), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                ctx.onOpenLink?.let { open ->
                    TextButton(onClick = open, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.rd_s9_servers_open_link), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    ctx.serversContent?.let { content -> item(key = "srv-content", contentType = "servers-content") { content() } }
    if (!ctx.serverState.available && ctx.serversContent == null) {
        item(key = "srv-unavailable", contentType = "state") { TabHint(stringResource(Res.string.rd_s9_servers_unavailable)) }
        return
    }
    serverExtensionSections(
        ctx.serverState,
        ctx.servers,
        local = ctx.state.items,
        updatable = ctx.hubState.updates.map { it.id }.toSet(),
        onUpdate = { ctx.hub.update(it) },
        onOpenServers = null,
    )
}
