// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.library.resources.Res
import app.nijiyome.feature.library.resources.*
import app.nijiyome.feature.manga.MangaLibraryTab
import app.nijiyome.feature.novel.NovelLibraryTab
import app.nijiyome.feature.torrents.TorrentsLibraryTab
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.PlayerArgs
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.state.screenInsets
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_retry

/**
 * Library (Sparkles screen `biblioteca`): a medium switch (Anime, Manga, Novela), the views Mi lista · Historial · Favoritos, and
 * "Sincronizar ahora" in the header and its menu. Mi lista draws the structure picked in Ajustes › Apariencia (Estantes, Tablero or
 * Lista). Torrents moved to Transferencias: the header links there through [onOpenTransfers].
 */
@Composable
fun LibraryScreen(
    onOpenDetails: (anilistId: Int) -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
    /** Where the screen opens (You › Historial opens HISTORY; notifications open TORRENTS). */
    initialTab: LibraryTab = LibraryTab.CONTINUE,
    /** Legacy torrents pane card -> Route.Torrents(OVERVIEW, torrentId). */
    onOpenTorrent: (torrentId: String) -> Unit = {},
    /** "Vincular AniList o MAL" and "Listas y cuentas" -> Route.SettingsSection(ACCOUNTS). */
    onOpenAccounts: () -> Unit = {},
    /** Manga medium row -> Route.Details(MediaKind.MANGA, anilistId). */
    onOpenMangaDetails: (anilistId: Int) -> Unit = {},
    /** Novel medium row -> Route.NovelDetails(anilistId). */
    onOpenNovelDetails: (anilistId: Int) -> Unit = {},
    /** Legacy torrents pane "Configurar debrid" -> Route.Torrents(TorrentSection.DEBRID). */
    onOpenDebrid: () -> Unit = {},
    /**
     * Sparkles S7: "Torrents descargados" -> Transferencias (Route.Downloads, torrents section). Null = not wired yet: the link opens
     * the legacy torrents pane inside Library instead.
     */
    onOpenTransfers: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val currentSnackbar by rememberUpdatedState(snackbar)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LibraryEvent.Synced -> currentSnackbar.show(getString(Res.string.rd_s7_synced))
                is LibraryEvent.SyncFailed -> currentSnackbar.show(
                    getString(Res.string.rd_s7_sync_failed),
                    actionLabel = getString(UiR.string.ui_retry),
                    onAction = viewModel::syncNow,
                )
            }
        }
    }
    LibraryContent(
        state = state,
        initialTab = initialTab,
        actions = LibraryActions(
            onRetry = viewModel::retry,
            onSyncNow = viewModel::syncNow,
            onOpenDetails = onOpenDetails,
            onPlay = onPlay,
            onExplore = onExplore,
            onOpenTorrent = onOpenTorrent,
            onOpenAccounts = onOpenAccounts,
            onOpenMangaDetails = onOpenMangaDetails,
            onOpenNovelDetails = onOpenNovelDetails,
            onOpenDebrid = onOpenDebrid,
            onOpenTransfers = onOpenTransfers,
        ),
        modifier = modifier,
    )
}

internal data class LibraryActions(
    val onRetry: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onOpenDetails: (Int) -> Unit = {},
    val onPlay: (PlayerArgs) -> Unit = {},
    val onExplore: () -> Unit = {},
    val onOpenTorrent: (String) -> Unit = {},
    val onOpenAccounts: () -> Unit = {},
    val onOpenMangaDetails: (Int) -> Unit = {},
    val onOpenNovelDetails: (Int) -> Unit = {},
    val onOpenDebrid: () -> Unit = {},
    val onOpenTransfers: (() -> Unit)? = null,
)

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    initialTab: LibraryTab,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val listsAvailable = remember(koin) { koin.getOrNull<ListsRepository>() != null }
    // Keyed on initialTab so Route.LibraryTab(tab) re-selects its place; the user's own choice survives configuration changes.
    val start = remember(initialTab, listsAvailable) { initialTab.destination(listsAvailable) }
    var medium by rememberSaveable(start) { mutableIntStateOf(start.medium.ordinal) }
    var view by rememberSaveable(start) { mutableIntStateOf(start.view.ordinal) }
    var torrents by rememberSaveable(start) { mutableStateOf(start.torrents) }
    val openTorrents: () -> Unit = actions.onOpenTransfers ?: { torrents = true }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            // Scaffold's contentWindowInsets only pad the content; the top bar takes the status bar itself.
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                if (torrents) {
                    TorrentsHeader(onBack = { torrents = false }, onOpenTransfers = actions.onOpenTransfers)
                } else {
                    LibraryHeader(
                        state = state,
                        medium = LibraryMedium.entries[medium],
                        onMedium = { medium = it.ordinal },
                        onSyncNow = actions.onSyncNow,
                        onOpenAccounts = actions.onOpenAccounts,
                        onOpenTorrents = openTorrents,
                    )
                    if (LibraryMedium.entries[medium] == LibraryMedium.ANIME) {
                        LibraryViewTabs(
                            selected = LibraryView.entries[view],
                            favoritesAvailable = listsAvailable,
                            onSelect = { view = it.ordinal },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val content = Modifier.fillMaxSize()
            when {
                torrents -> TorrentsLibraryTab(
                    onOpenTorrent = actions.onOpenTorrent,
                    onPlay = actions.onPlay,
                    modifier = content,
                    onOpenDebrid = actions.onOpenDebrid,
                )
                LibraryMedium.entries[medium] == LibraryMedium.MANGA -> MangaLibraryTab(
                    onOpenDetails = actions.onOpenMangaDetails,
                    onOpenAccounts = actions.onOpenAccounts,
                    modifier = content,
                )
                LibraryMedium.entries[medium] == LibraryMedium.NOVEL -> NovelLibraryTab(
                    onOpenDetails = actions.onOpenNovelDetails,
                    onOpenAccounts = actions.onOpenAccounts,
                    modifier = content,
                )
                else -> when (LibraryView.entries[view]) {
                    LibraryView.MY_LIST -> if (listsAvailable) {
                        MyListPane(state.continueWatching, actions, content)
                    } else {
                        ContinueOnlyPane(state.continueWatching, actions, content)
                    }
                    LibraryView.HISTORY -> HistoryPane(state.history, actions, content)
                    LibraryView.FAVORITES -> FavoritesPane(actions, content)
                }
            }
        }
    }
}

/**
 * Title, the medium switch, the accounts menu (Sincronizar ahora, Listas y cuentas, Torrents descargados) and the sync line
 * ("Local en este dispositivo", last sync, "Sincronizar ahora" or its progress).
 */
@Composable
private fun LibraryHeader(
    state: LibraryUiState,
    medium: LibraryMedium,
    onMedium: (LibraryMedium) -> Unit,
    onSyncNow: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenTorrents: () -> Unit,
) {
    val compact = isCompactWindow()
    val syncLabel = stringResource(Res.string.library_sync_now)
    val accountsLabel = stringResource(Res.string.rd_s7_accounts)
    val torrentsLabel = stringResource(Res.string.rd_s7_torrents_downloaded)
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(Res.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (!compact) MediumSwitch(medium, onMedium)
            Spacer(Modifier.weight(1f))
            if (!compact) {
                TextButton(onClick = onOpenTorrents) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(torrentsLabel, modifier = Modifier.padding(start = 6.dp))
                }
            }
            ContextMenuButton(
                entries = {
                    listOf(
                        MenuAction(syncLabel, onClick = onSyncNow, icon = Icons.Outlined.Sync, enabled = !state.syncing),
                        MenuAction(accountsLabel, onClick = onOpenAccounts, icon = Icons.Outlined.ManageAccounts),
                        MenuAction(torrentsLabel, onClick = onOpenTorrents, icon = Icons.Outlined.SwapVert),
                    )
                },
                contentDescription = stringResource(Res.string.rd_s7_menu),
            )
        }
        if (compact) MediumSwitch(medium, onMedium, fill = true, modifier = Modifier.padding(end = 8.dp))
        SyncLine(state, onSyncNow)
    }
}

@Composable
private fun MediumSwitch(medium: LibraryMedium, onMedium: (LibraryMedium) -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    Segmented(
        options = listOf(
            SegmentedOption(LibraryMedium.ANIME, stringResource(Res.string.rd_s7_medium_anime), icon = Icons.Outlined.Movie),
            SegmentedOption(LibraryMedium.MANGA, stringResource(Res.string.rd_s7_medium_manga), icon = Icons.Outlined.MenuBook),
            SegmentedOption(LibraryMedium.NOVEL, stringResource(Res.string.rd_s7_medium_novel), icon = Icons.Outlined.AutoStories),
        ),
        selected = medium,
        onSelect = onMedium,
        modifier = modifier,
        fillWidth = fill,
    )
}

/** "Local en este dispositivo" · last sync · "Sincronizar ahora" (a spinner while it runs; errors come back on the snackbar). */
@Composable
private fun SyncLine(state: LibraryUiState, onSyncNow: () -> Unit) {
    val compact = isCompactWindow()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // BUG-084: on a phone the pill says "Local" and the full text is its description.
        val localFull = stringResource(Res.string.library_local)
        Pill(
            if (compact) stringResource(Res.string.library_local_short) else localFull,
            modifier = Modifier.semantics { contentDescription = localFull },
            tone = PillTone.PRIMARY,
            icon = Icons.Outlined.PhoneAndroid,
        )
        Text(
            state.lastSyncedAt?.let { stringResource(Res.string.library_synced_at, relativeTime(it)) }
                ?: stringResource(Res.string.library_never_synced),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
        )
        if (state.syncing) {
            val label = stringResource(Res.string.library_syncing)
            Row(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LoadingIndicator(Modifier.size(32.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            TextButton(onClick = onSyncNow) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.library_sync_now), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun LibraryViewTabs(selected: LibraryView, favoritesAvailable: Boolean, onSelect: (LibraryView) -> Unit) {
    val views = LibraryView.entries.filter { it != LibraryView.FAVORITES || favoritesAvailable }
    val index = views.indexOf(selected).coerceAtLeast(0)
    val tabs: @Composable () -> Unit = {
        views.forEach { v ->
            Tab(
                selected = v == selected,
                onClick = { onSelect(v) },
                text = {
                    Text(
                        stringResource(
                            when (v) {
                                LibraryView.MY_LIST -> Res.string.rd_s7_tab_my_list
                                LibraryView.HISTORY -> Res.string.library_tab_history
                                LibraryView.FAVORITES -> Res.string.library_tab_favorites
                            },
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
    }
    // BIB-02: wide windows get compact tabs at the start (the artifact); thirds of the whole width are the phone pattern.
    if (isCompactWindow()) {
        PrimaryTabRow(selectedTabIndex = index, tabs = tabs)
    } else {
        PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 16.dp, tabs = tabs)
    }
}

/** The legacy torrents pane, while the shell still sends `LibraryTab.TORRENTS` here: back to Library and the way to Transferencias. */
@Composable
private fun TorrentsHeader(onBack: () -> Unit, onOpenTransfers: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.rd_s7_back_to_library))
            }
            Text(
                stringResource(Res.string.rd_s7_torrents_downloaded),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onOpenTransfers != null) {
                TextButton(onClick = onOpenTransfers) { Text(stringResource(Res.string.rd_s7_open_transfers)) }
            }
        }
        Text(
            stringResource(Res.string.rd_s7_torrents_moved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
