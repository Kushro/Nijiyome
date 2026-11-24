@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.library

import app.nijiyome.model.ListsRepository
import androidx.compose.runtime.remember
import app.nijiyome.feature.library.resources.Res
import app.nijiyome.feature.library.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.feature.manga.MangaLibraryTab
import app.nijiyome.feature.novel.NovelLibraryTab
import app.nijiyome.feature.torrents.TorrentsLibraryTab
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.LoadState
import app.nijiyome.model.PlayerArgs
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.media.MediaCard
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.progressFraction
import app.nijiyome.ui.media.toResumeArgs
import app.nijiyome.ui.state.LoadStateContent
import app.nijiyome.ui.state.screenInsets
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(
    onOpenDetails: (anilistId: Int) -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
    // --- android wave contract (docs/redesign/12 §3.14): new callbacks appended, wired by :app (worker b) ---
    /** Tab shown first (You › Historial opens HISTORY; notifications / badges open TORRENTS). */
    initialTab: LibraryTab = LibraryTab.CONTINUE,
    /** Torrents tab card -> Route.Torrents(OVERVIEW, torrentId). */
    onOpenTorrent: (torrentId: String) -> Unit = {},
    // --- parity wave A (15 §3.3, worker q) ---
    /** Lists / Favorites empty state "Vincular AniList o MAL" -> Route.SettingsSection(ACCOUNTS). */
    onOpenAccounts: () -> Unit = {},
    // --- parity wave B (16 §3.5 / §3.6, worker s; wired by :app) ---
    /** Manga readlist row -> Route.Details(MediaKind.MANGA, anilistId). */
    onOpenMangaDetails: (anilistId: Int) -> Unit = {},
    /** Novel readlist row -> Route.NovelDetails(anilistId). */
    onOpenNovelDetails: (anilistId: Int) -> Unit = {},
    /** Empty Torrents tab "Configurar debrid" -> Route.Torrents(TorrentSection.DEBRID). */
    onOpenDebrid: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LibraryEvent.Synced -> snackbar.show(getString(Res.string.library_synced))
                is LibraryEvent.SyncFailed -> snackbar.show(getString(Res.string.library_sync_failed))
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
    /** parity wave A (worker q) */
    val onOpenAccounts: () -> Unit = {},
    /** parity wave B (worker s) */
    val onOpenMangaDetails: (Int) -> Unit = {},
    val onOpenNovelDetails: (Int) -> Unit = {},
    val onOpenDebrid: () -> Unit = {},
)

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    initialTab: LibraryTab,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    // Keyed on initialTab so Route.LibraryTab(tab) re-selects it; the user's own choice survives configuration changes.
    val koin = getKoin()
    val tabs = remember(koin) { LibraryTab.entries.filter { it.isAvailable(koin.getOrNull<ListsRepository>() != null) } }
    val startTab = if (initialTab in tabs) initialTab else LibraryTab.CONTINUE
    var tab by rememberSaveable(startTab) { mutableIntStateOf(startTab.ordinal) }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            // Scaffold's contentWindowInsets only pad the content; the top bar takes the status bar itself.
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                LibraryHeader(state, actions.onSyncNow)
                LibraryTabs(tabs, selected = LibraryTab.entries[tab], activeDownloads = state.activeDownloads, onSelect = { tab = it.ordinal })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (LibraryTab.entries[tab]) {
                LibraryTab.CONTINUE -> ContinueTab(state.continueWatching, actions)
                LibraryTab.HISTORY -> HistoryTab(state.history, actions)
                // parity wave A (15 §3.3, worker q)
                LibraryTab.LISTS -> ListsTab(
                    onOpenDetails = actions.onOpenDetails,
                    onOpenAccounts = actions.onOpenAccounts,
                    modifier = Modifier.fillMaxSize(),
                )
                LibraryTab.FAVORITES -> FavoritesTab(
                    onOpenDetails = actions.onOpenDetails,
                    onOpenAccounts = actions.onOpenAccounts,
                    modifier = Modifier.fillMaxSize(),
                )
                LibraryTab.TORRENTS -> TorrentsLibraryTab(
                    onOpenTorrent = actions.onOpenTorrent,
                    onPlay = actions.onPlay,
                    modifier = Modifier.fillMaxSize(),
                    onOpenDebrid = actions.onOpenDebrid,
                )
                // parity wave B (16 §3.5 / §3.6, worker s): readlists; the same `onOpenDetails` opens Route.Details(MANGA, id) / NovelDetails
                // through `actions.onOpenMangaDetails` / `onOpenNovelDetails` (wired by :app).
                LibraryTab.MANGA -> MangaLibraryTab(
                    onOpenDetails = actions.onOpenMangaDetails,
                    onOpenAccounts = actions.onOpenAccounts,
                    modifier = Modifier.fillMaxSize(),
                )
                LibraryTab.NOVELS -> NovelLibraryTab(
                    onOpenDetails = actions.onOpenNovelDetails,
                    onOpenAccounts = actions.onOpenAccounts,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(state: LibraryUiState, onSyncNow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.library_title), style = MaterialTheme.typography.headlineMedium)
                // BUG-084: "Local en este dispositivo" was cut to "Local en est…" beside the title on a phone; there it says "Local"
                // and the full text is its description.
                val localFull = stringResource(Res.string.library_local)
                val compact = app.nijiyome.ui.platform.windowWidthDp() < COMPACT_WIDTH_DP
                Pill(
                    if (compact) stringResource(Res.string.library_local_short) else localFull,
                    modifier = Modifier.semantics { contentDescription = localFull },
                    tone = PillTone.PRIMARY,
                )
            }
            Text(
                state.lastSyncedAt?.let { stringResource(Res.string.library_synced_at, relativeTime(it)) }
                    ?: stringResource(Res.string.library_never_synced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.syncing) {
            val label = stringResource(Res.string.library_syncing)
            LoadingIndicator(Modifier.size(40.dp).semantics { contentDescription = label })
        } else {
            TextButton(onClick = onSyncNow) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.library_sync_now), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun LibraryTabs(tabs: List<LibraryTab>, selected: LibraryTab, activeDownloads: Int, onSelect: (LibraryTab) -> Unit) {
    // BUG-084: seven fixed tabs got ~56 dp each in portrait and split letter by letter ("Con/tinu/ar"); they scroll instead.
    PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0), edgePadding = 16.dp) {
        tabs.forEach { t ->
            Tab(
                selected = t == selected,
                onClick = { onSelect(t) },
                text = {
                    if (t == LibraryTab.TORRENTS && activeDownloads > 0) {
                        val badge = stringResource(Res.string.library_torrents_badge, activeDownloads)
                        // Badge beside the label: BadgedBox anchors it over the end of the text and hides the last letters.
                        Row(
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = badge },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(stringResource(t.labelRes()), maxLines = 1, softWrap = false)
                            Badge { Text(activeDownloads.toString()) }
                        }
                    } else {
                        Text(stringResource(t.labelRes()), maxLines = 1, softWrap = false)
                    }
                },
            )
        }
    }
}

private fun LibraryTab.labelRes(): StringResource = when (this) {
    LibraryTab.CONTINUE -> Res.string.library_tab_continue
    LibraryTab.HISTORY -> Res.string.library_tab_history
    LibraryTab.LISTS -> Res.string.library_tab_lists
    LibraryTab.FAVORITES -> Res.string.library_tab_favorites
    LibraryTab.MANGA -> Res.string.library_tab_manga
    LibraryTab.NOVELS -> Res.string.library_tab_novels
    LibraryTab.TORRENTS -> Res.string.library_tab_torrents
}

@Composable
private fun ContinueTab(state: LoadState<List<HistoryEntry>>, actions: LibraryActions) {
    LoadStateContent(state = state, onRetry = actions.onRetry, loading = { DelayedLoading() }) { entries ->
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.VideoLibrary,
                title = stringResource(Res.string.library_empty_title),
                message = stringResource(Res.string.library_empty_message),
                actionLabel = stringResource(Res.string.library_explore),
                onAction = actions.onExplore,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { "c-" + it.episodeId }, contentType = { "entry" }) { entry ->
                    MediaCard(
                        title = entry.title,
                        coverUrl = entry.poster,
                        onClick = { actions.onPlay(entry.toResumeArgs()) },
                        subtitle = if (entry.completed || entry.progressSec <= 0) {
                            stringResource(Res.string.library_episode, entry.episode)
                        } else {
                            stringResource(Res.string.library_episode_at, entry.episode, formatDuration(entry.progressSec.toLong()))
                        },
                        progress = if (entry.completed) null else entry.progressFraction(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(state: LoadState<List<HistoryEntry>>, actions: LibraryActions) {
    LoadStateContent(state = state, onRetry = actions.onRetry, loading = { DelayedLoading() }) { entries ->
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(Res.string.library_history_empty_title),
                message = stringResource(Res.string.library_history_empty_message),
                actionLabel = stringResource(Res.string.library_explore),
                onAction = actions.onExplore,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(entries, key = { "h-" + it.episodeId + "-" + it.watchedAt.toEpochMilli() }, contentType = { "entry" }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onClick = { entry.anilistId?.let(actions.onOpenDetails) ?: actions.onPlay(entry.toResumeArgs()) },
                    )
                }
            }
        }
    }
}

/** Prototype history `.ep` row: thumbnail, title + Visto pill, "Episodio N · visto hace 2 h", chevron. */
@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaCover(url = entry.poster, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.completed) Pill(stringResource(Res.string.library_watched), tone = PillTone.OK)
            }
            Text(
                stringResource(Res.string.library_watched_on, entry.episode, relativeTime(entry.watchedAt.toEpochMilli())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "hace 5 min" / "5 minutes ago" in the app locale (platform formatter), lowercase: it always follows other words (BUG-092). */
@Composable
private fun relativeTime(epochMs: Long): String {
    return app.nijiyome.ui.format.midSentence(platformRelativeTimeSpan(epochMs))
}

@Composable
private fun DelayedLoading() {
    if (rememberAfterDelay()) LoadingState()
}

/** BUG-084: below this width (a phone in portrait, ~393 dp) the header pill uses its short label. */
private const val COMPACT_WIDTH_DP = 600
