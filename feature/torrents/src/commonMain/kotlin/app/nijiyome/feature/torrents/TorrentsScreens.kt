// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents

import app.nijiyome.feature.torrents.resources.Res
import app.nijiyome.feature.torrents.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.torrents.ui.AddTorrentSheet
import app.nijiyome.feature.torrents.ui.DebridSection
import app.nijiyome.feature.torrents.ui.DetailContent
import app.nijiyome.feature.torrents.ui.HistorySection
import app.nijiyome.feature.torrents.ui.DownloadsBody
import app.nijiyome.feature.torrents.ui.LibraryContent
import app.nijiyome.feature.torrents.ui.MessagesEffect
import app.nijiyome.feature.torrents.ui.TorrentLoadState
import app.nijiyome.feature.torrents.ui.TorrentSettingsBody
import app.nijiyome.feature.torrents.vm.AddTorrentViewModel
import app.nijiyome.feature.torrents.vm.TorrentDetailViewModel
import app.nijiyome.feature.torrents.vm.TorrentDownloadsViewModel
import app.nijiyome.feature.torrents.vm.TorrentLibraryViewModel
import app.nijiyome.feature.torrents.vm.TorrentSettingsViewModel
import app.nijiyome.model.LoadState
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.torrent.TorrentSection
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.state.screenInsets
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// android wave contract (docs/redesign/12 §3.12). Owner: worker f. Names and signatures FIXED (called by :app, feature/library,
// feature/settings, feature/details). Data through TorrentRepository (Koin, torrentModule). Every screen: loading / empty / error.

/** Sections reachable from the client header (the per-torrent ones open from a torrent). */
private val CLIENT_SECTIONS = listOf(
    TorrentSection.LIBRARY,
    TorrentSection.DOWNLOADS,
    // parity wave A (15 §3.9, worker q)
    TorrentSection.HISTORY,
    TorrentSection.DEBRID,
    TorrentSection.SETTINGS,
)

/**
 * Route entry of `Route.Torrents(section, torrentId)`: tablet rail destination and deep links (player "Abrir en el cliente", You ›
 * Descargas, notifications). Per-torrent sections without [torrentId] show a picker over the library or an EmptyState leading to LIBRARY.
 * Expanded: ListDetailPaneScaffold (library | detail). [onNavigate] changes section / torrent in place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun TorrentsScreen(
    section: TorrentSection,
    torrentId: String?,
    onNavigate: (section: TorrentSection, torrentId: String?) -> Unit,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    modifier: Modifier = Modifier,
) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val twoPanes = directive.maxHorizontalPartitions > 1
    val headerSection = if (section.perTorrent) TorrentSection.LIBRARY else section

    // parity wave A (worker q, torrent.add-* / torrent.magnet-link-handler): "Añadir torrent", also opened by a system magnet: / .torrent
    val addVm: AddTorrentViewModel = koinViewModel()
    var addOpen by rememberSaveable { mutableStateOf(false) }
    val incoming by addVm.incoming.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        if (incoming) {
            addOpen = true
            addVm.incomingShown()
        }
    }
    if (addOpen) {
        AddTorrentSheet(
            vm = addVm,
            onDismiss = { addOpen = false },
            onAdded = { id ->
                addOpen = false
                onNavigate(TorrentSection.OVERVIEW, id)
            },
        )
    }

    // Phone with a torrent open: the detail is the whole screen (its back returns to the previous route).
    if (!twoPanes && torrentId != null && (section.perTorrent || section == TorrentSection.LIBRARY)) {
        TorrentDetailScreen(
            torrentId = torrentId,
            onBack = onBack,
            onPlay = onPlay,
            modifier = modifier,
            initialSection = if (section.perTorrent) section else TorrentSection.OVERVIEW,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.nav_torrents)) },
                navigationIcon = {
                    if (!twoPanes) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        addVm.reset()
                        addOpen = true
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(Res.string.torrent_add_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Segmented(
                options = CLIENT_SECTIONS.map { SegmentedOption(it, stringResource(it.labelRes())) },
                selected = headerSection,
                onSelect = { onNavigate(it, if (it == TorrentSection.LIBRARY) torrentId else null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (section) {
                TorrentSection.DOWNLOADS -> DownloadsSection(
                    onNotNow = { onNavigate(TorrentSection.LIBRARY, null) },
                    onOpenTorrent = { onNavigate(TorrentSection.OVERVIEW, it) },
                    modifier = Modifier.fillMaxSize(),
                )
                TorrentSection.SETTINGS -> TorrentSettingsPage(modifier = Modifier.fillMaxSize(), onBack = null)
                TorrentSection.HISTORY -> HistorySection(onOpenTorrent = { onNavigate(TorrentSection.OVERVIEW, it) }, modifier = Modifier.fillMaxSize())
                TorrentSection.DEBRID -> DebridSection(modifier = Modifier.fillMaxSize())
                else -> {
                    if (torrentId == null) {
                        LibraryPane(
                            picking = section.perTorrent,
                            selectedId = null,
                            onOpen = { id -> onNavigate(if (section.perTorrent) section else TorrentSection.OVERVIEW, id) },
                            onOpenLibrary = { onNavigate(TorrentSection.LIBRARY, null) },
                            onPlay = onPlay,
                        )
                    } else {
                        val destination = ThreePaneScaffoldDestinationItem(
                            ListDetailPaneScaffoldRole.Detail,
                            torrentId,
                        )
                        val value = calculateThreePaneScaffoldValue(
                            maxHorizontalPartitions = directive.maxHorizontalPartitions,
                            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
                            currentDestination = destination,
                        )
                        ListDetailPaneScaffold(
                            directive = directive,
                            value = value,
                            listPane = {
                                AnimatedPane {
                                    LibraryPane(
                                        picking = section.perTorrent,
                                        selectedId = torrentId,
                                        onOpen = { id -> onNavigate(if (section.perTorrent) section else TorrentSection.OVERVIEW, id) },
                                        onOpenLibrary = { onNavigate(TorrentSection.LIBRARY, null) },
                                        onPlay = onPlay,
                                    )
                                }
                            },
                            detailPane = {
                                AnimatedPane {
                                    TorrentDetailPane(
                                        torrentId = torrentId,
                                        initialSection = if (section.perTorrent) section else TorrentSection.OVERVIEW,
                                        onBack = { onNavigate(TorrentSection.LIBRARY, null) },
                                        onPlay = onPlay,
                                        showBack = !twoPanes,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** List pane: the library, or a picker for a per-torrent section opened without a torrent. */
@Composable
private fun LibraryPane(
    picking: Boolean,
    selectedId: String?,
    onOpen: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    viewModel: TorrentLibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    MessagesEffect(viewModel.messages, LocalAppSnackbar.current)
    val items = (state as? LoadState.Ok)?.value
    if (picking && items != null && items.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.TouchApp,
            title = stringResource(Res.string.torrent_shell_empty_title),
            message = stringResource(Res.string.torrent_shell_empty_body),
            actionLabel = stringResource(Res.string.torrent_shell_open_library),
            onAction = onOpenLibrary,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val header: (@Composable () -> Unit)? = if (picking) {
        {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.torrent_shell_pick_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.torrent_shell_pick_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        null
    }
    LibraryContent(
        state = state,
        selection = selection,
        onRetry = viewModel::retry,
        onOpen = onOpen,
        onPlay = onPlay,
        onToggleSelect = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onPersist = viewModel::setPersist,
        onRemove = viewModel::remove,
        selectedId = selectedId,
        header = header,
    )
}

@Composable
private fun TorrentDetailPane(
    torrentId: String,
    initialSection: TorrentSection,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    showBack: Boolean,
) {
    val viewModel: TorrentDetailViewModel = koinViewModel(key = "torrent-$torrentId", parameters = { parametersOf(torrentId) })
    DetailContent(viewModel, initialSection, onBack, onPlay, Modifier.fillMaxSize(), showBack = showBack)
}

@Composable
private fun DownloadsSection(
    onNotNow: () -> Unit,
    onOpenTorrent: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TorrentDownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MessagesEffect(viewModel.messages, LocalAppSnackbar.current)
    TorrentLoadState(state, viewModel::retry, modifier, errorTitle = stringResource(Res.string.torrent_downloads_error)) { ui ->
        DownloadsBody(
            ui = ui,
            onAccept = viewModel::acknowledge,
            onNotNow = onNotNow,
            onBackground = viewModel::setBackground,
            onOpenTorrent = onOpenTorrent,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onRemove = viewModel::remove,
            modifier = modifier,
        )
    }
}

/** Body of the "Torrents" tab of Library on phones (11 §3.2): cards, 3-dot menu, multi-select bar. Opening a card calls [onOpenTorrent]. */
@Composable
fun TorrentsLibraryTab(
    onOpenTorrent: (torrentId: String) -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDebrid: (() -> Unit)? = null,
) {
    val viewModel: TorrentLibraryViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    MessagesEffect(viewModel.messages, LocalAppSnackbar.current)
    LibraryContent(
        state = state,
        selection = selection,
        onRetry = viewModel::retry,
        onOpen = onOpenTorrent,
        onPlay = onPlay,
        onToggleSelect = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onPersist = viewModel::setPersist,
        onRemove = viewModel::remove,
        modifier = modifier,
        emptyActionLabel = onOpenDebrid?.let { stringResource(Res.string.torrent_library_configure_debrid) },
        onEmptyAction = onOpenDebrid,
    )
}

/** Detail of one torrent: fixed header + PrimaryScrollableTabRow Overview / Files / Peers / Trackers + HorizontalPager (11 §3.3). */
@Composable
fun TorrentDetailScreen(
    torrentId: String,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    modifier: Modifier = Modifier,
    initialSection: TorrentSection = TorrentSection.OVERVIEW,
) {
    val viewModel: TorrentDetailViewModel = koinViewModel(key = "torrent-$torrentId", parameters = { parametersOf(torrentId) })
    DetailContent(viewModel, initialSection, onBack, onPlay, modifier)
}

/**
 * Settings › Torrents subpage body (11 §3.5): Velocidad, Red, Almacenamiento (SAF tree picker, free space, after-watch), Segundo plano.
 * Immediate save per field through SettingsRepository.setTorrentSettings; inline validation in supportingText. Hosted by worker b's
 * SettingsScreen (and by TorrentsScreen SETTINGS on tablets). [onBack] null = no top bar (embedded).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentSettingsPage(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: TorrentSettingsViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    MessagesEffect(viewModel.messages, LocalAppSnackbar.current)
    if (onBack == null) {
        TorrentSettingsBody(settings, storage, viewModel::update, viewModel::retryStorage, modifier)
    } else {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = screenInsets,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(UiR.string.torrent_section_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                        }
                    },
                )
            },
        ) { padding ->
            TorrentSettingsBody(settings, storage, viewModel::update, viewModel::retryStorage, Modifier.padding(padding))
        }
    }
}
