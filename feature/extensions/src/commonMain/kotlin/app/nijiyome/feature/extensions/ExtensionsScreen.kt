// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.extensions

import app.nijiyome.feature.extensions.resources.Res
import app.nijiyome.feature.extensions.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.platform.rememberFilePicker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.nijiyome.ui.platform.externalDropTarget
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.InfoChip
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.motion.motionFadeIn
import app.nijiyome.designsystem.motion.motionFadeOut
import app.nijiyome.model.ExtLogLevel
import app.nijiyome.model.ExtensionCompat
import app.nijiyome.model.ExtensionDetail
import app.nijiyome.model.ExtensionLogEntry
import app.nijiyome.model.ExtensionRepo
import app.nijiyome.model.SourcesStructure
import app.nijiyome.model.StremioAddon
import app.nijiyome.model.InstalledExtension
import app.nijiyome.model.torrent.DebridService
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Fuentes (Sparkles S9; artifact `screens-c.js` screen `fuentes`). The long Extensions column with five concepts becomes one screen in
 * two structures chosen in Settings › Appearance (`structures.sources`), sharing the view models, the state and every action:
 *
 * - [SourcesStructure.TABS]: Instaladas, Repositorios, Stremio, Proveedores, Servidores en tu PC and Debrid, each in its place
 *   (`SourcesTabs.kt`).
 * - [SourcesStructure.STORE]: a store grid of the extensions of the user's repositories with install / update, the installed list and
 *   the other sections one press away (`SourcesStore.kt`).
 *
 * Debrid (accounts, status and its three switches) lived in Torrents and in Settings › Torrents; it is shown here once.
 *
 * [serversContent] (optional): the full "PC extension servers" content of `:feature:link` (`ExtensionServersContent`), embedded in the
 * Servidores tab when the shell passes it; without it the tab lists what each linked server offers and links to [onOpenServers].
 * [onOpenLink] opens Nijiyome Link (the extension servers' "Configurar" of the artifact) when the shell offers it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: ExtensionsViewModel = koinViewModel(),
    // parity wave B (docs/redesign/16-parity-wave-b-contract-android.md §3.4, worker t): "Proveedores" section (customSources) -> Route.Provider.
    onOpenProvider: (extensionId: String, sourceId: String) -> Unit = { _, _ -> },
    hub: ExtensionsHubViewModel = koinViewModel(),
    // parity wave B closure (16 §3.4, worker t; server.api.extensions): extensions installed on the linked PC servers.
    serverExtensions: ServerExtensionsViewModel = koinViewModel(),
    /**
     * Opens the extension servers screen from the PC servers section; null = that screen is reached elsewhere (Android: You ›
     * Linked device). Desktop has no Linked device screen, so its way in is here (Tatakai Next: Extensiones › Servidores).
     */
    onOpenServers: (() -> Unit)? = null,
    /** Sparkles S9: the Debrid tab (accounts, status, switches). */
    debrid: SourcesDebridViewModel = koinViewModel(),
    /** Sparkles S9: the full extension-server content of `:feature:link`, embedded in the Servidores tab when given. */
    serversContent: (@Composable () -> Unit)? = null,
    /** Sparkles S9: opens Nijiyome Link (Route.Linked) from the Servidores tab; null hides that button. */
    onOpenLink: (() -> Unit)? = null,
    /**
     * Sparkles S9: the debrid section of Transfers (`Route.Torrents(TorrentSection.DEBRID)`, lane S8), for what the DebridRepository
     * contract cannot do from here (the preference order). Null hides that link.
     */
    onOpenDebrid: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hubState by hub.state.collectAsStateWithLifecycle()
    val serverState by serverExtensions.state.collectAsStateWithLifecycle()
    val debridState by debrid.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val invalidText = stringResource(Res.string.extensions_install_invalid)
    val displayName = rememberDocumentDisplayName()
    // SAF: the user picks the .kai package; no storage permission needed. Android may not know the .kai type, so allow any and check the name.
    val pickFile = rememberFilePicker(listOf("*/*"), multiple = false) { uris ->
        val uri = uris.firstOrNull() ?: return@rememberFilePicker
        val name = displayName(uri)
        if (name != null && !name.endsWith(".kai", ignoreCase = true)) {
            snackbar.show(invalidText)
        } else {
            viewModel.install(uri)
        }
    }
    var pendingRemove by remember { mutableStateOf<InstalledExtension?>(null) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    var showInstallUrl by rememberSaveable { mutableStateOf(false) }
    var confirmKill by remember { mutableStateOf(false) }
    var prefsFor by remember { mutableStateOf<InstalledExtension?>(null) }
    var removeRepo by remember { mutableStateOf<ExtensionRepo?>(null) }
    var removeAddon by remember { mutableStateOf<StremioAddon?>(null) }
    var unlinkDebrid by remember { mutableStateOf<DebridService?>(null) }
    HubNoticesEffect(hub)
    DebridNoticesEffect(debrid)
    LaunchedEffect(state.message) {
        if (state.message is InstallMessage.Installed) showInstallUrl = false
    }

    val installedText = (state.message as? InstallMessage.Installed)?.let { stringResource(Res.string.extensions_installed, it.name) }
    LaunchedEffect(state.message) {
        if (state.message is InstallMessage.Installed && installedText != null) {
            snackbar.show(installedText)
            viewModel.dismissMessage()
        }
    }
    val undoLabel = stringResource(Res.string.rd_s9_undo)

    val ctx = SourcesContext(
        state = state,
        hubState = hubState,
        serverState = serverState,
        debridState = debridState,
        viewModel = viewModel,
        hub = hub,
        servers = serverExtensions,
        debrid = debrid,
        onOpenProvider = onOpenProvider,
        onOpenServers = onOpenServers,
        serversContent = serversContent,
        onOpenLink = onOpenLink,
        onOpenDebrid = onOpenDebrid,
        pickFile = pickFile,
        openInstall = { showInstallUrl = true },
        openUpdates = { showUpdates = true },
        killSwitch = { enable -> if (enable) hub.setAllEnabled(true) else confirmKill = true },
        askRemove = { pendingRemove = it },
        openPrefs = { prefsFor = it },
        askRemoveRepo = { removeRepo = it },
        askRemoveAddon = { removeAddon = it },
        askUnlinkDebrid = { unlinkDebrid = it },
        toggle = { ext, on ->
            viewModel.setEnabled(ext.id, on)
            // Confirm or undo (Sparkles rule): the switch acts at once and the snackbar can take it back.
            scope.launch {
                val text = getString(if (on) Res.string.extensions_now_enabled else Res.string.extensions_now_disabled, ext.name)
                snackbar.show(text, undoLabel) { viewModel.setEnabled(ext.id, !on) }
            }
        },
        dropFiles = { files ->
            val kai = files.firstOrNull { it.endsWith(".kai", ignoreCase = true) }
            if (kai == null) snackbar.show(invalidText) else viewModel.install(kai)
            kai != null
        },
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = { SourcesTopBar(ctx, onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            // The one switch of this screen on its own structure (CONTRATO-COMPOSE: once, at the root).
            when (structures.sources) {
                SourcesStructure.TABS -> SourcesTabs(ctx)
                SourcesStructure.STORE -> SourcesStore(ctx)
            }
        }
    }

    pendingRemove?.let { ext ->
        val removedText = stringResource(Res.string.extensions_removed, ext.name)
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(Res.string.extensions_remove_title, ext.name)) },
            text = { Text(stringResource(Res.string.extensions_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(ext.id)
                    snackbar.show(removedText)
                    pendingRemove = null
                }) { Text(stringResource(Res.string.extensions_remove_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text(stringResource(UiR.string.ui_close)) } },
        )
    }
    state.downgrade?.let { d ->
        // BUG-094: an older .kai over a newer installed copy.
        AlertDialog(
            onDismissRequest = viewModel::cancelDowngrade,
            title = { Text(stringResource(Res.string.extensions_downgrade_title, d.name)) },
            text = { Text(stringResource(Res.string.extensions_downgrade_message, d.newVersion, d.installedVersion)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDowngrade) { Text(stringResource(Res.string.extensions_downgrade_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDowngrade) { Text(stringResource(Res.string.extensions_downgrade_cancel)) } },
        )
    }
    if (showUpdates) UpdatesSheet(hubState, hub, onDismiss = { showUpdates = false })
    if (showInstallUrl) {
        InstallUrlDialog(
            installing = state.installing,
            onInstall = viewModel::installFromUrl,
            onDismiss = { showInstallUrl = false },
            // Desktop: a pasted path installs the file (Tatakai Next's path field); the dialog closes on success as for a URL.
            onInstallPath = if (kaiDropAndPaths) viewModel::install else null,
            onPickFile = {
                showInstallUrl = false
                pickFile()
            },
        )
    }
    if (confirmKill) {
        KillSwitchDialog(
            onConfirm = {
                confirmKill = false
                hub.setAllEnabled(false)
            },
            onDismiss = { confirmKill = false },
        )
    }
    prefsFor?.let { ext -> PreferencesDialog(ext.id, ext.name, hub, onDismiss = { prefsFor = null }) }
    removeRepo?.let { r ->
        ConfirmRemoveDialog(
            title = stringResource(Res.string.extensions_repo_remove_title, r.name),
            message = stringResource(Res.string.extensions_repo_remove_message),
            onConfirm = {
                hub.removeRepository(r.url)
                removeRepo = null
            },
            onDismiss = { removeRepo = null },
        )
    }
    removeAddon?.let { a ->
        ConfirmRemoveDialog(
            title = stringResource(Res.string.extensions_remove_title, a.name),
            message = stringResource(Res.string.extensions_stremio_remove_message),
            onConfirm = {
                hub.removeAddon(a.id)
                removeAddon = null
            },
            onDismiss = { removeAddon = null },
        )
    }
    unlinkDebrid?.let { service ->
        ConfirmRemoveDialog(
            title = stringResource(Res.string.rd_s9_debrid_unlink_title, service.label),
            message = stringResource(Res.string.rd_s9_debrid_unlink_body),
            confirmLabel = stringResource(Res.string.rd_s9_debrid_unlink),
            onConfirm = {
                debrid.unlink(service)
                unlinkDebrid = null
            },
            onDismiss = { unlinkDebrid = null },
        )
    }
}

/**
 * What both structures of Fuentes share: the state of the four view models and every action. Built on each composition of
 * [ExtensionsScreen]; the structures only compose it differently.
 */
internal class SourcesContext(
    val state: ExtensionsUiState,
    val hubState: HubUiState,
    val serverState: ServerExtensionsUiState,
    val debridState: DebridUiState,
    val viewModel: ExtensionsViewModel,
    val hub: ExtensionsHubViewModel,
    val servers: ServerExtensionsViewModel,
    val debrid: SourcesDebridViewModel,
    val onOpenProvider: (extensionId: String, sourceId: String) -> Unit,
    val onOpenServers: (() -> Unit)?,
    val serversContent: (@Composable () -> Unit)?,
    val onOpenLink: (() -> Unit)?,
    val onOpenDebrid: (() -> Unit)?,
    val pickFile: () -> Unit,
    val openInstall: () -> Unit,
    val openUpdates: () -> Unit,
    /** true = enable all at once; false = ask first ("Desactivar todas"). */
    val killSwitch: (enable: Boolean) -> Unit,
    val askRemove: (InstalledExtension) -> Unit,
    val openPrefs: (InstalledExtension) -> Unit,
    val askRemoveRepo: (ExtensionRepo) -> Unit,
    val askRemoveAddon: (StremioAddon) -> Unit,
    val askUnlinkDebrid: (DebridService) -> Unit,
    val toggle: (InstalledExtension, Boolean) -> Unit,
    /** Desktop: files dropped on the install panel; true when one was a `.kai`. */
    val dropFiles: (List<String>) -> Boolean,
) {
    val updateCount: Int get() = hubState.updates.size
    val anyEnabled: Boolean get() = state.items.any { it.enabled }
    fun updateFor(id: String): String? = hubState.updates.firstOrNull { it.id == id }?.availableVersion
}

/** Snackbar of the hub's one-shot outcomes. */
@Composable
private fun HubNoticesEffect(hub: ExtensionsHubViewModel) {
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(hub) {
        hub.notices.collect { n ->
            val text = when (n) {
                is HubNotice.Updated -> getString(Res.string.extensions_updated_to, n.name, n.version)
                is HubNotice.UpdatesChecked -> getPluralString(Res.plurals.extensions_updates_found, n.count, n.count)
                is HubNotice.RepoAdded -> getString(Res.string.extensions_repo_added, n.name)
                is HubNotice.AddonInstalled -> getString(Res.string.extensions_installed, n.name)
                is HubNotice.AddonUpdated -> getString(Res.string.extensions_updated_to, n.name, n.version)
                HubNotice.MatchesReset -> getString(Res.string.extensions_stremio_reset_done)
                is HubNotice.AllEnabled -> getString(if (n.enabled) Res.string.extensions_all_enabled else Res.string.extensions_all_disabled)
                is HubNotice.Failed -> getString(Res.string.extensions_action_failed, n.reason)
            }
            snackbar.show(text)
        }
    }
}

/** Snackbar of the Debrid tab's outcomes. */
@Composable
private fun DebridNoticesEffect(debrid: SourcesDebridViewModel) {
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(debrid) {
        debrid.notices.collect { n ->
            val text = when (n) {
                is DebridNotice.Linked -> getString(Res.string.rd_s9_debrid_linked_as, n.account.service.label, n.account.username ?: n.account.service.label)
                is DebridNotice.CheckOk -> getString(Res.string.rd_s9_debrid_check_ok, n.account.service.label, n.account.username ?: n.account.service.label)
                is DebridNotice.CheckFailed -> getString(Res.string.rd_s9_debrid_check_failed, n.service.label, getString(debridProblemRes(n.error)))
                is DebridNotice.Unlinked -> getString(Res.string.rd_s9_debrid_unlinked, n.service.label)
            }
            snackbar.show(text)
        }
    }
}

/** Title, the counts line and the actions: updates and "Instalar .kai" (wide), the updates badge (phone) and the menu (both). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesTopBar(ctx: SourcesContext, onBack: (() -> Unit)?) {
    val compact = isCompactWindow()
    val items = ctx.state.items
    val partial = items.count { it.compat == ExtensionCompat.PARTIAL }
    val meta = listOfNotNull(
        pluralStringResource(Res.plurals.rd_s9_meta_extensions, items.size, items.size),
        partial.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.rd_s9_meta_partial, it, it) },
        ctx.updateCount.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.rd_s9_meta_updates, it, it) },
    ).joinToString(" · ")
    val disableAll = stringResource(Res.string.extensions_disable_all)
    val enableAll = stringResource(Res.string.extensions_enable_all)
    val checkUpdates = stringResource(Res.string.rd_s9_updates_check)
    val installKai = stringResource(Res.string.rd_s9_install_kai)
    val chooseFile = stringResource(Res.string.extensions_install_choose)
    TopAppBar(
        title = {
            Column {
                Text(stringResource(Res.string.rd_s9_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!ctx.state.loading) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                }
            }
        },
        actions = {
            if (compact) {
                val updatesLabel = pluralStringResource(Res.plurals.extensions_updates_badge, ctx.updateCount, ctx.updateCount)
                IconButton(onClick = ctx.openUpdates, modifier = Modifier.semantics { contentDescription = updatesLabel }) {
                    if (ctx.updateCount > 0) {
                        BadgedBox(badge = { Badge { Text(ctx.updateCount.toString()) } }) { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) }
                    } else {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                    }
                }
            } else {
                OutlinedButton(onClick = ctx.openUpdates, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (ctx.updateCount > 0) stringResource(Res.string.rd_s9_updates_n, ctx.updateCount) else checkUpdates,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Button(onClick = ctx.openInstall, enabled = !ctx.state.installing, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(installKai, modifier = Modifier.padding(start = 8.dp))
                }
            }
            // EXT-09: the kill switch lives in the screen's menu (and in the danger zone of Instaladas).
            ContextMenuButton(
                entries = {
                    listOfNotNull(
                        MenuAction(installKai, onClick = ctx.openInstall, icon = Icons.Outlined.Add),
                        MenuAction(chooseFile, onClick = ctx.pickFile, icon = Icons.Outlined.FileOpen),
                        MenuAction(checkUpdates, onClick = { ctx.hub.checkUpdates() }, icon = Icons.Outlined.Refresh),
                        if (items.isNotEmpty()) {
                            MenuAction(
                                if (ctx.anyEnabled) disableAll else enableAll,
                                onClick = { ctx.killSwitch(!ctx.anyEnabled) },
                                icon = Icons.Outlined.PowerSettingsNew,
                                destructive = ctx.anyEnabled,
                            )
                        } else {
                            null
                        },
                    )
                },
            )
        },
    )
}

// ---------------------------------------------------------------- shared pieces of Instaladas

/** The install panel: choose a `.kai`, a link (or a path on desktop), or drop the file on it (desktop). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstallPanel(ctx: SourcesContext, modifier: Modifier = Modifier) {
    val installing = ctx.state.installing
    var hover by remember { mutableStateOf(false) }
    val drop = if (kaiDropAndPaths) {
        Modifier.externalDropTarget(hovering = { hover = it }) { d -> !installing && ctx.dropFiles(d.files) }
    } else {
        Modifier
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (hover) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (hover) 2.dp else 1.dp, if (hover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth().then(drop),
    ) {
        FlowRow(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(
                            when {
                                hover -> Res.string.extensions_install_drop
                                kaiDropAndPaths -> Res.string.rd_s9_install_drop_here
                                else -> Res.string.rd_s9_install_package
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(if (kaiDropAndPaths) Res.string.extensions_install_hint_desktop else Res.string.extensions_install_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = ctx.pickFile, enabled = !installing) {
                    Text(stringResource(if (installing) Res.string.extensions_installing else Res.string.extensions_install_choose))
                }
                TextButton(onClick = ctx.openInstall, enabled = !installing) {
                    Text(stringResource(if (kaiDropAndPaths) Res.string.extensions_install_url_or_path else Res.string.extensions_install_url))
                }
            }
        }
    }
}

/** Banners over the installed list: install failure, every extension off, updates available. */
@Composable
internal fun InstalledBanners(ctx: SourcesContext) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (ctx.state.message as? InstallMessage.Failed)?.let { failed ->
            Banner(
                icon = Icons.Outlined.ErrorOutline,
                tone = BannerTone.ERR,
                title = stringResource(Res.string.rd_s9_install_failed_title),
                body = stringResource(Res.string.extensions_install_failed, failed.reason),
            ) {
                TextButton(onClick = {
                    ctx.viewModel.dismissMessage()
                    ctx.pickFile()
                }) { Text(stringResource(Res.string.rd_s9_install_pick_other)) }
            }
        }
        if (ctx.state.items.isNotEmpty() && ctx.state.items.none { it.enabled }) {
            Banner(
                icon = Icons.Outlined.PowerSettingsNew,
                tone = BannerTone.WARN,
                title = stringResource(Res.string.rd_s9_all_off_title),
                body = stringResource(Res.string.rd_s9_all_off_body),
            ) {
                FilledTonalButton(onClick = { ctx.killSwitch(true) }) { Text(stringResource(Res.string.extensions_enable_all)) }
            }
        }
        if (ctx.updateCount > 0) {
            Banner(
                icon = Icons.Outlined.SystemUpdate,
                tone = BannerTone.INFO,
                title = pluralStringResource(Res.plurals.extensions_updates_badge, ctx.updateCount, ctx.updateCount),
                body = null,
            ) {
                TextButton(onClick = ctx.openUpdates) { Text(stringResource(Res.string.extensions_updates_open)) }
                if (ctx.updateCount > 1) {
                    FilledTonalButton(onClick = { ctx.hub.updateAll() }, enabled = ctx.hubState.updating.isEmpty()) {
                        Text(stringResource(Res.string.extensions_updates_all))
                    }
                }
            }
        }
    }
}

internal enum class BannerTone { INFO, WARN, ERR }

/** A full-width notice with an icon, a bold line, an optional explanation and its actions. No side stripe: a tinted surface. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Banner(
    icon: ImageVector,
    tone: BannerTone,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val signals = LocalSignalColors.current
    val accent = when (tone) {
        BannerTone.INFO -> signals.info
        BannerTone.WARN -> signals.warn
        BannerTone.ERR -> signals.err
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        FlowRow(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

/** No extension installed: the direction's art, what to do, and the two ways in (a file, or a repository / the store). */
@Composable
internal fun InstalledEmpty(ctx: SourcesContext, onBrowse: () -> Unit, browseLabel: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyArt(EmptyKind.SOURCES, Modifier.size(120.dp))
        Text(stringResource(Res.string.extensions_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(Res.string.rd_s9_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ctx.pickFile, enabled = !ctx.state.installing) { Text(stringResource(Res.string.extensions_install_choose)) }
            OutlinedButton(onClick = onBrowse) { Text(browseLabel) }
        }
    }
}

/** "Desactivar todas" / "Activar todas" at the end of Instaladas: nothing is uninstalled and the preferences stay. */
@Composable
internal fun KillZone(ctx: SourcesContext) {
    if (ctx.state.items.isEmpty()) return
    val anyOn = ctx.anyEnabled
    Banner(
        icon = Icons.Outlined.PowerSettingsNew,
        tone = if (anyOn) BannerTone.ERR else BannerTone.INFO,
        title = stringResource(if (anyOn) Res.string.rd_s9_kill_title_off else Res.string.rd_s9_kill_title_on),
        body = stringResource(Res.string.rd_s9_kill_body),
    ) {
        if (anyOn) {
            OutlinedButton(onClick = { ctx.killSwitch(false) }) {
                Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(stringResource(Res.string.rd_s9_kill_action), modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
            }
        } else {
            OutlinedButton(onClick = { ctx.killSwitch(true) }) { Text(stringResource(Res.string.extensions_enable_all)) }
        }
    }
}

@Composable
internal fun ExtensionAvatar(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
internal fun DelayedLoading() {
    if (rememberAfterDelay()) LoadingState(Modifier.fillMaxWidth().height(160.dp))
}

/**
 * One installed extension in the list: initial, name, "version · kind · update available", compatibility pill and its switch. A press
 * selects it (wide: the detail pane beside; phone: the detail unfolds under the row, EXT-04 "detalle plegable").
 */
@Composable
internal fun ExtensionRow(ctx: SourcesContext, ext: InstalledExtension, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val update = ctx.updateFor(ext.id)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 0.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.Button, onClick = onSelect)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExtensionAvatar(ext.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ext.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            ext.version,
                            kindRes(ext.type)?.let { stringResource(it) },
                            update?.let { stringResource(Res.string.rd_s9_update_available, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Pill(stringResource(ext.compat.labelRes()), tone = ext.compat.tone())
            }
            // BUG-110: a neutral label with the name; the switch's own state says "activado" / "desactivado".
            val enabledLabel = stringResource(Res.string.extensions_use_named, ext.name)
            Switch(
                checked = ext.enabled,
                onCheckedChange = { ctx.toggle(ext, it) },
                enabled = ext.compat != ExtensionCompat.UNSUPPORTED,
                modifier = Modifier.semantics { contentDescription = enabledLabel },
            )
        }
    }
}

/** The phone form of a row: the row and, when open, its detail right under it. */
@Composable
internal fun ExtensionRowWithDetail(ctx: SourcesContext, ext: InstalledExtension, open: Boolean, onToggleOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExtensionRow(ctx, ext, selected = open, onSelect = onToggleOpen)
        AnimatedVisibility(open, enter = motionFadeIn(), exit = motionFadeOut()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                ExtensionDetailBody(ctx, ext, showHead = false, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

/**
 * The whole extension: head, update banner, compatibility banner with the technical reason, description, declared permissions and
 * domains, hidden-browser notice, preferences, facts (date, hash, domains, routes), the collapsible log and the danger zone (remove).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtensionDetailBody(ctx: SourcesContext, ext: InstalledExtension, showHead: Boolean = true, modifier: Modifier = Modifier) {
    val detail = ctx.state.details[ext.id] ?: DetailState.Loading
    val logOpen = ext.id in ctx.state.expandedLogs
    val update = ctx.updateFor(ext.id)
    val updating = ext.id in ctx.hubState.updating
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHead) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtensionAvatar(ext.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ext.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        // BUG-075: no raw id and no "custom" kind, as on the desktop.
                        listOfNotNull(stringResource(Res.string.extensions_version, ext.version), kindRes(ext.type)?.let { stringResource(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(stringResource(ext.compat.labelRes()), tone = ext.compat.tone())
            }
        }
        if (update != null) {
            val notes = ctx.hubState.updates.firstOrNull { it.id == ext.id }?.notes?.takeIf { it.isNotBlank() }
            Banner(Icons.Outlined.SystemUpdate, BannerTone.INFO, stringResource(Res.string.rd_s9_version_available, update), notes) {
                FilledTonalButton(onClick = { ctx.hub.update(ext.id) }, enabled = !updating) {
                    Text(stringResource(if (updating) Res.string.extensions_updating else Res.string.extensions_update))
                }
            }
        }
        CompatBanner(ext)
        if (ext.description.isNotBlank()) Text(ext.description, style = MaterialTheme.typography.bodyMedium)
        Permissions(ext.permissions)
        if (ext.browserDomains.isNotEmpty()) {
            InfoChip(pluralStringResource(Res.plurals.extensions_browser, ext.browserDomains.size, ext.browserDomains.size), icon = Icons.Outlined.Public)
            if (!hiddenBrowserAvailable()) {
                Text(stringResource(Res.string.extensions_browser_limited), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (ext.id in ctx.hubState.withPreferences) {
            OutlinedButton(onClick = { ctx.openPrefs(ext) }) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.extensions_prefs), modifier = Modifier.padding(start = 8.dp))
            }
        }
        when (detail) {
            DetailState.Loading -> if (rememberAfterDelay()) SkeletonBox(Modifier.fillMaxWidth(0.6f).height(16.dp))
            DetailState.Failed -> ErrorState(
                message = stringResource(Res.string.extensions_detail_error),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = { ctx.viewModel.retryDetail(ext.id) },
                compact = true,
            )
            is DetailState.Ready -> detail.detail?.let { FootFacts(it) }
        }
        TextButton(onClick = { ctx.viewModel.toggleLog(ext.id) }) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(if (logOpen) Res.string.extensions_hide_logs else Res.string.extensions_logs), modifier = Modifier.padding(start = 8.dp))
        }
        if (logOpen) LogPanel(ext.id, ctx.viewModel)
        Banner(
            icon = Icons.Outlined.Delete,
            tone = BannerTone.ERR,
            title = stringResource(Res.string.rd_s9_remove_named, ext.name),
            body = stringResource(Res.string.rd_s9_remove_body),
        ) {
            OutlinedButton(onClick = { ctx.askRemove(ext) }) {
                Text(stringResource(Res.string.rd_s9_remove_action), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Wide form of Instaladas' detail: the selected extension on its own scrolling pane. */
@Composable
internal fun ExtensionDetailPane(ctx: SourcesContext, ext: InstalledExtension, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        ExtensionDetailBody(ctx, ext, modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Permissions(permissions: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // BUG-075: over a row of sites only, "Accede a" reads better than "Permisos".
        val onlySites = permissions.isNotEmpty() && permissions.all { it.startsWith(DOMAIN_PERMISSION) }
        Text(
            stringResource(if (onlySites) Res.string.extensions_perm_accesses else Res.string.extensions_permissions),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (permissions.isEmpty()) {
                InfoChip(stringResource(Res.string.extensions_no_permissions))
            } else {
                permissions.take(MAX_PERMISSION_CHIPS).forEach { p -> InfoChip(permissionText(p), icon = permissionIcon(p)) }
                if (permissions.size > MAX_PERMISSION_CHIPS) InfoChip(stringResource(Res.string.extensions_more, permissions.size - MAX_PERMISSION_CHIPS))
            }
        }
    }
}

@Composable
private fun CompatBanner(ext: InstalledExtension) {
    val (text, warn) = when (ext.compat) {
        ExtensionCompat.FULL -> return
        ExtensionCompat.PARTIAL -> stringResource(Res.string.extensions_compat_partial_hint) to false
        ExtensionCompat.UNSUPPORTED -> stringResource(Res.string.extensions_cannot_enable) to true
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (warn) Icons.Outlined.WarningAmber else Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                ext.compatReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    // FUE-02: the analyzer's reason is English for the logs; the user reads it explained, the raw line stays below.
                    compatExplanation(reason)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        stringResource(Res.string.rd_s9_compat_detail) + ": " + reason,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/** The reasons `CompatAnalyzer` writes, in the user's language; null for one it does not know (the raw line still shows). */
@Composable
private fun compatExplanation(reason: String): String? {
    val quoted = Regex("'([^']+)'").find(reason)?.groupValues?.get(1)
    return when {
        reason.startsWith("Uses stubbed Node module") && quoted != null -> stringResource(Res.string.rd_s9_compat_stub, quoted)
        reason.startsWith("Uses dynamic require") -> stringResource(Res.string.rd_s9_compat_dynamic)
        reason.endsWith("which the native runtime does not provide") && quoted != null -> stringResource(Res.string.rd_s9_compat_missing, quoted)
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FootFacts(d: ExtensionDetail) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        d.installedAtMs?.let { at ->
            val rel = app.nijiyome.ui.format.midSentence(platformRelativeTimeSpan(at)) // BUG-092: "Actualizada hace 3 horas"
            Text(stringResource(Res.string.extensions_updated, rel), style = style, color = color)
        }
        if (d.hash.isNotEmpty()) Text(stringResource(Res.string.extensions_hash, d.hash), style = style, color = color, fontFamily = FontFamily.Monospace)
        Text(pluralStringResource(Res.plurals.extensions_domains, d.domains.size, d.domains.size), style = style, color = color)
        if (d.sourceRoutes.isNotEmpty()) {
            val routes = d.sourceRoutes.map { r ->
                when (r) {
                    "torrent" -> stringResource(Res.string.extensions_route_torrent)
                    else -> stringResource(Res.string.extensions_route_stream)
                }
            }.distinct().joinToString(", ")
            Text(stringResource(Res.string.extensions_routes, routes), style = style, color = color)
        }
    }
}

@Composable
private fun LogPanel(extensionId: String, viewModel: ExtensionsViewModel) {
    val flow = remember(extensionId) { viewModel.logs(extensionId) }
    val lines by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (lines.isEmpty()) {
                Text(
                    stringResource(Res.string.extensions_logs_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                lines.forEach { LogLine(it) }
            }
        }
    }
}

@Composable
private fun LogLine(entry: ExtensionLogEntry) {
    val (levelRes, color) = when (entry.level) {
        ExtLogLevel.OK -> Res.string.extensions_log_ok to MaterialTheme.colorScheme.primary
        ExtLogLevel.WARN -> Res.string.extensions_log_warn to MaterialTheme.colorScheme.tertiary
        ExtLogLevel.ERR -> Res.string.extensions_log_err to MaterialTheme.colorScheme.error
    }
    Text(
        stringResource(Res.string.extensions_log_line, stringResource(levelRes), entry.op, entry.message),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = if (entry.level == ExtLogLevel.OK) MaterialTheme.colorScheme.onSurface else color,
    )
}

private const val MAX_PERMISSION_CHIPS = 6

private const val DOMAIN_PERMISSION = "network:domain:"

/** BUG-075 (UX-16): what a permission id means for the user, or null to show it as it is (same words as the desktop's `perm_label`). */
internal enum class PermissionLabel { NETWORK, STORAGE, TORRENT }

/** `network:domain:az-animex.com` -> the host; other `network*` / `storage*` / `torrent*` -> a word; anything else -> null. */
internal fun permissionLabel(p: String): Pair<PermissionLabel?, String?> = when {
    p.startsWith(DOMAIN_PERMISSION) -> null to p.removePrefix(DOMAIN_PERMISSION)
    p.startsWith("network") -> PermissionLabel.NETWORK to null
    p.startsWith("storage") -> PermissionLabel.STORAGE to null
    p.startsWith("torrent") -> PermissionLabel.TORRENT to null
    else -> null to null
}

@Composable
private fun permissionText(p: String): String {
    val (kind, host) = permissionLabel(p)
    return host ?: when (kind) {
        PermissionLabel.NETWORK -> stringResource(Res.string.extensions_perm_network)
        PermissionLabel.STORAGE -> stringResource(Res.string.extensions_perm_storage)
        PermissionLabel.TORRENT -> stringResource(Res.string.extensions_perm_torrent)
        null -> p
    }
}

private fun permissionIcon(p: String): ImageVector = when {
    p.startsWith("network") -> Icons.Outlined.Language
    p.startsWith("storage") -> Icons.Outlined.Storage
    else -> Icons.Outlined.WarningAmber
}

internal fun ExtensionCompat.labelRes(): StringResource = when (this) {
    ExtensionCompat.FULL -> Res.string.extensions_compat_full
    ExtensionCompat.PARTIAL -> Res.string.extensions_compat_partial
    ExtensionCompat.UNSUPPORTED -> Res.string.extensions_compat_unsupported
}

internal fun ExtensionCompat.tone(): PillTone = when (this) {
    ExtensionCompat.FULL -> PillTone.OK
    ExtensionCompat.PARTIAL -> PillTone.WARN
    ExtensionCompat.UNSUPPORTED -> PillTone.ERR
}

/** BUG-075: "Torrents" / "Streams web"; a custom extension says nothing (its description does). */
internal fun kindRes(type: String): StringResource? = when (type) {
    "torrent" -> Res.string.extensions_kind_torrent
    "custom" -> null
    else -> Res.string.extensions_kind_onlinestream
}
