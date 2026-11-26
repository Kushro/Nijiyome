// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.feature.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.feature.extensions.resources.Res
import app.nijiyome.feature.extensions.resources.*
import app.nijiyome.model.ExtensionRepo
import app.nijiyome.model.ExtensionRepoEntry
import app.nijiyome.model.LoadState
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles S9, structure STORE ("tienda"): the extensions of the user's repositories as a store grid with search, filters, install and
// update (with a confirmation that says what is installed and from where). The installed list and the other sections of Fuentes are one
// press away: beside the grid on wide windows, under it on phones, each section opening in a sheet with the same content as its tab.

/** One store tile: an entry of a repository. */
internal data class StoreItem(val entry: ExtensionRepoEntry, val repoName: String, val repoUrl: String) {
    val key: String get() = "$repoUrl#${entry.id}"
}

internal enum class StoreFilter { ALL, UPDATES, INSTALLED, NEW }

/** The tiles shown for a query, a filter and an optional repository. Pure (tested). */
internal fun storeItems(repos: List<ExtensionRepo>, query: String, filter: StoreFilter, repoUrl: String?): List<StoreItem> {
    val q = query.trim().lowercase()
    return repos.asSequence()
        .filter { repoUrl == null || it.url == repoUrl }
        .flatMap { r -> r.entries.asSequence().map { StoreItem(it, r.name, r.url) } }
        .filter { item ->
            when (filter) {
                StoreFilter.ALL -> true
                StoreFilter.UPDATES -> item.entry.updateAvailable
                StoreFilter.INSTALLED -> item.entry.installed
                StoreFilter.NEW -> !item.entry.installed
            }
        }
        .filter { q.isEmpty() || (it.entry.name + " " + it.entry.description).lowercase().contains(q) }
        .toList()
}

/** The sections the store opens in a sheet. */
private enum class StoreSheet { INSTALLED, REPOS, STREMIO, PROVIDERS, SERVERS, DEBRID }

@Composable
internal fun SourcesStore(ctx: SourcesContext) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(StoreFilter.ALL) }
    var repoFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<StoreSheet?>(null) }
    var detailFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<StoreItem?>(null) }
    val repos = ctx.hubState.repos
    val shown = remember(repos, query, filter, repoFilter) { storeItems(repos, query, filter, repoFilter) }
    val openInstalled: (String) -> Unit = { id -> detailFor = id }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.weight(1f).fillMaxHeight().decor(DecorRegion.GRID),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "head", span = { GridItemSpan(maxLineSpan) }, contentType = "head") {
                    val count = repos.sumOf { it.entries.size }
                    SectionHeader(
                        stringResource(Res.string.rd_s9_store_title),
                        trailing = {
                            Text(
                                stringResource(Res.string.rd_s9_store_count, count, repos.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
                item(key = "tools", span = { GridItemSpan(maxLineSpan) }, contentType = "tools") {
                    StoreTools(
                        repos = repos,
                        query = query,
                        onQuery = { query = it },
                        filter = filter,
                        onFilter = { filter = it },
                        repoFilter = repoFilter,
                        onRepoFilter = { repoFilter = it },
                        updates = ctx.updateCount,
                    )
                }
                item(key = "banners", span = { GridItemSpan(maxLineSpan) }, contentType = "banners") { InstalledBanners(ctx) }
                when {
                    repos.isEmpty() -> item(key = "no-repos", span = { GridItemSpan(maxLineSpan) }, contentType = "state") {
                        StoreEmpty(
                            title = stringResource(Res.string.rd_s9_store_no_repos_title),
                            body = stringResource(Res.string.rd_s9_store_no_repos_body),
                        ) {
                            Button(onClick = { sheet = StoreSheet.REPOS }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s9_store_add_repo)) }
                            OutlinedButton(onClick = ctx.pickFile, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.extensions_install_choose)) }
                        }
                    }
                    shown.isEmpty() -> item(key = "no-match", span = { GridItemSpan(maxLineSpan) }, contentType = "state") {
                        StoreEmpty(
                            title = stringResource(Res.string.rd_s9_store_no_match_title),
                            body = stringResource(Res.string.rd_s9_store_no_match_body),
                            icon = Icons.Outlined.SearchOff,
                        ) {
                            TextButton(onClick = {
                                query = ""
                                filter = StoreFilter.ALL
                                repoFilter = null
                            }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s9_store_clear)) }
                        }
                    }
                    else -> items(shown, key = { it.key }, contentType = { "tile" }) { item ->
                        StoreTile(
                            item = item,
                            busy = item.entry.url in ctx.hubState.busy || item.entry.id in ctx.hubState.updating,
                            onInstall = { confirm = item },
                            onOpen = { openInstalled(item.entry.id) },
                        )
                    }
                }
                if (!wide) {
                    item(key = "side", span = { GridItemSpan(maxLineSpan) }, contentType = "side") {
                        StoreSide(ctx, onOpenSheet = { sheet = it }, onOpenExtension = openInstalled)
                    }
                }
            }
            if (wide) {
                Column(
                    Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StoreSide(ctx, onOpenSheet = { sheet = it }, onOpenExtension = openInstalled)
                }
            }
        }
    }

    sheet?.let { s -> SectionSheet(ctx, s, onDismiss = { sheet = null }, onOpenExtension = { id -> detailFor = id }) }
    detailFor?.let { id ->
        val ext = ctx.state.items.firstOrNull { it.id == id }
        if (ext != null) {
            AdaptiveSheet(onDismissRequest = { detailFor = null }) {
                ExtensionDetailBody(ctx, ext, modifier = Modifier.heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }
    }
    confirm?.let { item ->
        InstallConfirmSheet(
            item = item,
            onDismiss = { confirm = null },
            onConfirm = {
                if (item.entry.updateAvailable) ctx.hub.update(item.entry.id) else ctx.hub.installEntry(item.entry.url)
                confirm = null
            },
        )
    }
}

@Composable
private fun StoreTools(
    repos: List<ExtensionRepo>,
    query: String,
    onQuery: (String) -> Unit,
    filter: StoreFilter,
    onFilter: (StoreFilter) -> Unit,
    repoFilter: String?,
    onRepoFilter: (String?) -> Unit,
    updates: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.rd_s9_store_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Clear, contentDescription = stringResource(Res.string.rd_s9_store_clear_search)) } }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StoreFilter.entries.forEach { f ->
                val label = when (f) {
                    StoreFilter.ALL -> stringResource(Res.string.rd_s9_store_filter_all)
                    StoreFilter.UPDATES -> stringResource(Res.string.rd_s9_store_filter_updates, updates)
                    StoreFilter.INSTALLED -> stringResource(Res.string.rd_s9_store_filter_installed)
                    StoreFilter.NEW -> stringResource(Res.string.rd_s9_store_filter_new)
                }
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilter(f) },
                    label = { Text(label) },
                    leadingIcon = if (filter == f) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
        if (repos.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repos.forEach { r ->
                    FilterChip(
                        selected = repoFilter == r.url,
                        onClick = { onRepoFilter(if (repoFilter == r.url) null else r.url) },
                        label = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreTile(item: StoreItem, busy: Boolean, onInstall: () -> Unit, onOpen: () -> Unit) {
    val e = item.entry
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtensionAvatar(e.name)
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(Res.string.rd_s9_store_version_repo, e.version, item.repoName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                e.description.ifBlank { stringResource(Res.string.rd_s9_store_no_description) },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                when {
                    e.updateAvailable -> FilledTonalButton(onClick = onInstall, enabled = !busy, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            if (busy) stringResource(Res.string.extensions_updating) else stringResource(Res.string.rd_s9_store_update_to, e.version),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    e.installed -> {
                        Pill(stringResource(Res.string.extensions_repo_installed), tone = PillTone.OK, icon = Icons.Outlined.Check)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onOpen, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s9_store_open)) }
                    }
                    else -> Button(onClick = onInstall, enabled = !busy, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(if (busy) Res.string.extensions_installing else Res.string.extensions_repo_install),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreEmpty(title: String, body: String, icon: ImageVector? = null, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp).decor(DecorRegion.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon == null) EmptyArt(EmptyKind.SOURCES, Modifier.size(120.dp)) else Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/** The installed extensions (a press opens their detail) and the other sections of Fuentes, each in a sheet. */
@Composable
private fun StoreSide(ctx: SourcesContext, onOpenSheet: (StoreSheet) -> Unit, onOpenExtension: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InstallPanel(ctx)
        SectionHeader(stringResource(Res.string.rd_s9_store_yours))
        val items = ctx.state.items
        when {
            ctx.state.loading -> DelayedLoading()
            items.isEmpty() -> Text(stringResource(Res.string.rd_s9_store_yours_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                items.take(SIDE_LIMIT).forEach { ext -> ExtensionRow(ctx, ext, selected = false, onSelect = { onOpenExtension(ext.id) }) }
                if (items.size > SIDE_LIMIT) {
                    TextButton(onClick = { onOpenSheet(StoreSheet.INSTALLED) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s9_store_all_installed, items.size)) }
                }
            }
        }
        SectionHeader(stringResource(Res.string.rd_s9_store_manage))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column {
                ManageRow(Icons.Outlined.Inventory2, stringResource(Res.string.rd_s9_tab_repos), ctx.hubState.repos.size) { onOpenSheet(StoreSheet.REPOS) }
                if (ctx.hubState.stremioAvailable) {
                    ManageRow(Icons.Outlined.Tv, stringResource(Res.string.rd_s9_tab_stremio), ctx.hubState.addons.size) { onOpenSheet(StoreSheet.STREMIO) }
                }
                ManageRow(Icons.Outlined.Hub, stringResource(Res.string.rd_s9_tab_providers), (ctx.hubState.providers as? LoadState.Ok)?.value?.size) { onOpenSheet(StoreSheet.PROVIDERS) }
                ManageRow(Icons.Outlined.Computer, stringResource(SourcesTab.SERVERS.labelRes()), ctx.serverState.servers.size.takeIf { ctx.serverState.loaded }) { onOpenSheet(StoreSheet.SERVERS) }
                ManageRow(Icons.Outlined.CloudDone, stringResource(Res.string.rd_s9_tab_debrid), ctx.debridState.accounts.size) { onOpenSheet(StoreSheet.DEBRID) }
            }
        }
        KillZone(ctx)
    }
}

private const val SIDE_LIMIT = 6

@Composable
private fun ManageRow(icon: ImageVector, label: String, count: Int?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        count?.let { Text(it.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionSheet(ctx: SourcesContext, sheet: StoreSheet, onDismiss: () -> Unit, onOpenExtension: (String) -> Unit) {
    val title: StringResource = when (sheet) {
        StoreSheet.INSTALLED -> Res.string.rd_s9_tab_installed
        StoreSheet.REPOS -> Res.string.rd_s9_tab_repos
        StoreSheet.STREMIO -> Res.string.rd_s9_tab_stremio
        StoreSheet.PROVIDERS -> Res.string.rd_s9_tab_providers
        StoreSheet.SERVERS -> SourcesTab.SERVERS.labelRes()
        StoreSheet.DEBRID -> Res.string.rd_s9_tab_debrid
    }
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        // Bounded height: the side sheet scrolls its own content on wide windows.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sheetContent(ctx, sheet, onOpenExtension)
        }
    }
}

private fun LazyListScope.sheetContent(ctx: SourcesContext, sheet: StoreSheet, onOpenExtension: (String) -> Unit) {
    when (sheet) {
        StoreSheet.INSTALLED -> items(ctx.state.items, key = { it.id }, contentType = { "extension" }) { ext ->
            ExtensionRow(ctx, ext, selected = false, onSelect = { onOpenExtension(ext.id) })
        }
        StoreSheet.REPOS -> reposSection(ctx.hubState, ctx.hub, ctx.askRemoveRepo, showHead = false)
        StoreSheet.STREMIO -> stremioSection(ctx.hubState, ctx.hub, ctx.askRemoveAddon, showHead = false)
        StoreSheet.PROVIDERS -> providersSection(ctx.hubState, ctx.hub, ctx.onOpenProvider, showHead = false)
        StoreSheet.SERVERS -> serversSection(ctx)
        StoreSheet.DEBRID -> debridSection(ctx)
    }
}

/** "Instalar X" / "Actualizar X": what is installed, from which repository, and a word on trust, before anything runs. */
@Composable
private fun InstallConfirmSheet(item: StoreItem, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val e = item.entry
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (e.updateAvailable) Res.string.rd_s9_store_confirm_update else Res.string.rd_s9_store_confirm_install, e.name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(Res.string.rd_s9_store_version_repo, e.version, item.repoName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.rd_s9_store_permissions_note), style = MaterialTheme.typography.bodySmall)
            }
            Text(item.entry.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_cancel)) }
                Button(onClick = onConfirm, shape = NijiyomeControls.shape) {
                    Icon(if (e.updateAvailable) Icons.Outlined.SystemUpdate else Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (e.updateAvailable) Res.string.extensions_update else Res.string.extensions_repo_install),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
