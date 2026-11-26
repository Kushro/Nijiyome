package app.nijiyome.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.LocalAccentFont
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.homeEntrance
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.model.Density
import app.nijiyome.model.FeedKind
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.HomeRowIds
import app.nijiyome.model.HomeStructure
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.PlayerArgs
import app.nijiyome.ui.format.formatLongDate
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.media.toResumeArgs
import app.nijiyome.ui.patterns.moved
import app.nijiyome.ui.patterns.rememberShowUndo
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_retry
import app.nijiyome.ui.state.rememberIsOnline
import app.nijiyome.ui.state.screenInsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home (Sparkles redesign, lane S2). The structure the user picked in Ajustes › Apariencia (`structures.home`) decides the
 * composition; the three share this view model, state and actions:
 * - Esta noche: what to resume joined with what aired or airs today for the shows you track, the agenda of today with a "now" marker,
 *   Link and downloads beside it, then the rows.
 * - Escenario: the large stage (pausable autoplay) and every row below.
 * - Estantes: the rows first, dense, with Editar Inicio.
 * Rows are in the user's order and can be hidden (`LookPrefs.design.homeRowOrder` / `homeRowsHidden`).
 */
@Composable
fun HomeScreen(
    onMediaClick: (Media) -> Unit,
    onSearchClick: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    // --- android wave contract (docs/redesign/12 §3.14): new callbacks appended, wired by :app (worker b) ---
    /** Row header "Ver todo" of a feed row -> Route.SearchFor(feed = kind). */
    onOpenFeed: (FeedKind) -> Unit = {},
    /** Genre chip -> Route.SearchFor(genre = genre). */
    onOpenGenre: (String) -> Unit = {},
    /** Item of a lazy extension row -> Route.SearchFor(query = title). */
    onSearchQuery: (String) -> Unit = {},
    /** "Continuar viendo › Ver todo" -> Library (Continuar tab). */
    onOpenLibrary: () -> Unit = {},
    // --- parity wave B (docs/redesign/16-parity-wave-b-contract-android.md §3.1 / §3.4, worker r): appended, wired by :app ---
    /** Header of the "Novedades de lo que sigues" / airing row -> Route.Calendar. */
    onOpenCalendar: () -> Unit = {},
    /** Header of the "Para ti" row -> Route.ForYou. */
    onOpenForYou: () -> Unit = {},
    /** Quick chips Manga / Novelas of the greeting (phones: the only way to the readers besides Search / You) -> Route.Manga() / Route.Novels(). */
    onOpenManga: () -> Unit = {},
    onOpenNovels: () -> Unit = {},
    /** Bell of the top bar (badge = SocialRepository.unreadCount, read with koinInject by r) -> Route.Notifications. */
    onOpenNotifications: () -> Unit = {},
    /** Header of a custom-provider row (`ExtensionHomeRow.extensionId / sourceId`) -> Route.Provider(extensionId, sourceId). */
    onOpenProvider: (extensionId: String, sourceId: String) -> Unit = { _, _ -> },
    /** Item of a custom-provider row -> Route.ProviderItem(extensionId, sourceId, itemId) (replaces the title search of wave android). */
    onOpenProviderItem: (extensionId: String, sourceId: String, itemId: String) -> Unit = { _, _, _ -> },
    /** Continue watching card of a provider title (`ext:` history id) -> resume it in the player. */
    onResumeProviderItem: ((HistoryEntry) -> Unit)? = null,
    /** Continue watching: tap or long-press on a card opens its details; only the play button plays (2026-11 user request). */
    onOpenDetails: (anilistId: Int) -> Unit = {},
    // --- Sparkles (S2): appended, wired by the integrator (see the lane report) ---
    /** "Descargando" row, its items and the downloads panel -> Route.Downloads (Transferencias). */
    onOpenDownloads: () -> Unit = {},
    /** Link panel / strip -> Route.Linked. */
    onOpenLink: () -> Unit = {},
    /** "Monitor" of the Link panel (desktop) -> the Link monitor; defaults to the Link screen. */
    onOpenLinkMonitor: (() -> Unit)? = null,
    /** Empty Home: "Fuentes de metadatos" -> Ajustes › Metadatos y descubrir. */
    onOpenMetadataSettings: () -> Unit = {},
    /** "Administrar" of the extension rows -> Route.Extensions (Fuentes). */
    onOpenExtensions: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openEntry: (HistoryEntry) -> Unit = { entry ->
        val provider = HomeLogic.parseProviderHistoryId(entry.animeId)
        when {
            provider != null -> onOpenProviderItem(provider.first, provider.second, provider.third)
            else -> entry.anilistId?.takeIf { it > 0 }?.let(onOpenDetails) ?: onPlay(entry.toResumeArgs())
        }
    }
    val resumeEntry: (HistoryEntry) -> Unit = { entry ->
        val provider = HomeLogic.parseProviderHistoryId(entry.animeId)
        when {
            provider == null -> onPlay(entry.toResumeArgs())
            onResumeProviderItem != null -> onResumeProviderItem(entry)
            else -> onOpenProviderItem(provider.first, provider.second, provider.third)
        }
    }
    HomeContent(
        state = state,
        actions = HomeActions(
            onMediaClick = onMediaClick,
            onSearchClick = onSearchClick,
            onWatch = { media -> onPlay(PlayerArgs(media = media.ref, title = media.title, episode = 1)) },
            onPlayEpisode = { media, episode -> onPlay(PlayerArgs(media = media.ref, title = media.title, episode = episode)) },
            onContinueClick = resumeEntry,
            onResumeEntry = resumeEntry,
            onOpenEntry = openEntry,
            onRetry = viewModel::retry,
            onOpenFeed = onOpenFeed,
            onOpenGenre = onOpenGenre,
            onSearchQuery = onSearchQuery,
            onOpenLibrary = onOpenLibrary,
            onLoadExtensionRows = viewModel::loadExtensionRows,
            onOpenCalendar = {
                viewModel.markFollowedSeen()
                onOpenCalendar()
            },
            onOpenForYou = onOpenForYou,
            onOpenManga = onOpenManga,
            onOpenNovels = onOpenNovels,
            onOpenNotifications = onOpenNotifications,
            onOpenProvider = onOpenProvider,
            onOpenProviderItem = onOpenProviderItem,
            onTopWindow = viewModel::loadTop10,
            onRetryLatest = viewModel::loadLatest,
            onRetryUpcoming = viewModel::loadUpcoming,
            onRetryFavourites = viewModel::loadFavourites,
            onRetryFollowed = viewModel::loadFollowed,
            onRetryForYou = viewModel::loadForYou,
            onRetryBecause = viewModel::retryBecause,
            onRetryToday = viewModel::loadToday,
            onNextGenreRow = viewModel::loadNextGenreRow,
            onRetryGenre = viewModel::retryGenre,
            onRetryPrimary = viewModel::retryPrimaryProvider,
            onRefreshAll = viewModel::refreshAll,
            onToggleReminder = viewModel::toggleReminder,
            onSetRowPrefs = viewModel::setRowPrefs,
            onOpenDownloads = onOpenDownloads,
            onOpenLink = onOpenLink,
            onOpenLinkMonitor = onOpenLinkMonitor ?: onOpenLink,
            onOpenMetadataSettings = onOpenMetadataSettings,
            onOpenExtensions = onOpenExtensions,
        ),
        modifier = modifier,
        unreadNotifications = rememberUnreadNotifications(),
    )
}

/** Every callback of the Home content, grouped so previews / tests can pass no-ops. */
internal data class HomeActions(
    val onMediaClick: (Media) -> Unit = {},
    val onSearchClick: () -> Unit = {},
    val onWatch: (Media) -> Unit = {},
    val onPlayEpisode: (Media, Int) -> Unit = { _, _ -> },
    val onContinueClick: (HistoryEntry) -> Unit = {},
    /** Plays a history entry (provider titles resume through their extension). */
    val onResumeEntry: (HistoryEntry) -> Unit = {},
    /** Opens the details of a history entry (provider item page for `ext:` ids). */
    val onOpenEntry: (HistoryEntry) -> Unit = {},
    val onRetry: (FeedKind) -> Unit = {},
    val onOpenFeed: (FeedKind) -> Unit = {},
    val onOpenGenre: (String) -> Unit = {},
    val onSearchQuery: (String) -> Unit = {},
    val onOpenLibrary: () -> Unit = {},
    val onLoadExtensionRows: () -> Unit = {},
    // parity wave B (16 §3.1)
    val onOpenCalendar: () -> Unit = {},
    val onOpenForYou: () -> Unit = {},
    val onOpenManga: () -> Unit = {},
    val onOpenNovels: () -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
    val onOpenProvider: (String, String) -> Unit = { _, _ -> },
    val onOpenProviderItem: (String, String, String) -> Unit = { _, _, _ -> },
    val onTopWindow: (TopWindow) -> Unit = {},
    val onRetryLatest: () -> Unit = {},
    val onRetryUpcoming: () -> Unit = {},
    val onRetryFavourites: () -> Unit = {},
    val onRetryFollowed: () -> Unit = {},
    val onRetryForYou: () -> Unit = {},
    val onRetryBecause: () -> Unit = {},
    val onRetryToday: () -> Unit = {},
    val onNextGenreRow: (List<String>) -> Unit = {},
    val onRetryGenre: (String) -> Unit = {},
    val onRetryPrimary: () -> Unit = {},
    // Sparkles (S2)
    val onRefreshAll: () -> Unit = {},
    /** Bell of an episode; returns the bell's new state (null = no airing service). */
    val onToggleReminder: (Media) -> Boolean? = { null },
    val onSetRowPrefs: (order: List<String>, hidden: Set<String>) -> Unit = { _, _ -> },
    val onOpenDownloads: () -> Unit = {},
    val onOpenLink: () -> Unit = {},
    val onOpenLinkMonitor: () -> Unit = {},
    val onOpenMetadataSettings: () -> Unit = {},
    val onOpenExtensions: () -> Unit = {},
)

/** What the three structures share about the window around them. */
private class HomeFrame(
    val topInset: Dp,
    val wide: Boolean,
    /** Wide enough for the side column of Esta noche. */
    val aside: Boolean,
    val online: Boolean,
    val unread: Int,
    val showGreeting: Boolean,
    val onEdit: () -> Unit,
)

@Composable
internal fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    unreadNotifications: Int = 0,
) {
    val gate = rememberMatureGate(state.discover.matureContent)
    val look = LocalLook.current
    val prefs = look.design
    val nowSec = rememberNowSec()
    val online = rememberIsOnline()
    var editing by rememberSaveable { mutableStateOf(false) }
    var feedName by rememberSaveable { mutableStateOf(FeedKind.AIRING.name) }
    val d = state.discover
    val todayStart = remember(nowSec / 3600) { startOfDay(nowSec) }
    val tonight = remember(state.continueWatching, d, nowSec, todayStart) {
        HomeLogic.tonight(
            continueWatching = state.continueWatching,
            newSinceVisit = (d.followed as? LoadState.Ok)?.value.orEmpty(),
            today = (d.today as? LoadState.Ok)?.value.orEmpty(),
            tracked = d::isTracked,
            progressOf = { ref -> d.entryFor(ref)?.progress },
            nowSec = nowSec,
            todayStartSec = todayStart,
        )
    }
    val genres = d.genreCloud.ifEmpty { state.genres }
    val structure = structures.home
    // The stage already shows the trending titles; without it, Trending joins the catalogue (the former "Solo filas" layout).
    val feedKinds = (if (structure == HomeStructure.STAGE) state.rows else state.feeds).map { it.kind }
    val feed = feedKinds.firstOrNull { it.name == feedName } ?: feedKinds.firstOrNull() ?: FeedKind.AIRING
    val rows = prefs.homeRows(state.availableRows(tonight.size, genres.isNotEmpty()))
    val fullOrder = HomeLogic.fullRowOrder(prefs.homeRowOrder)
    val because = d.because?.sourceTitle
    val hiddenMessages = HomeRowIds.DEFAULT_ORDER.associateWith { id -> stringResource(Res.string.rd_s2_row_hidden_msg, homeRowTitle(id, because)) }
    val resetMessage = stringResource(Res.string.rd_s2_edit_reset_msg)
    val showUndo = rememberShowUndo()
    val hide: (String) -> Unit = { id ->
        val before = prefs.homeRowsHidden
        actions.onSetRowPrefs(fullOrder, before + id)
        showUndo(hiddenMessages[id].orEmpty()) { actions.onSetRowPrefs(fullOrder, before) }
    }
    Scaffold(modifier = modifier, contentWindowInsets = screenInsets) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WIDE_MIN
            val ctx = HomeRowCtx(
                state = state,
                actions = actions,
                gate = gate,
                tonight = tonight,
                genres = genres,
                feedKinds = feedKinds,
                feed = feed,
                onFeed = { feedName = it.name },
                editing = editing,
                onHide = hide,
                nowSec = nowSec,
                compact = !wide,
            )
            val panelOpen = editing && wide
            val frame = HomeFrame(
                topInset = padding.calculateTopPadding(),
                wide = wide,
                aside = maxWidth - (if (panelOpen) EDIT_PANEL_WIDTH else 0.dp) >= ASIDE_MIN,
                online = online,
                unread = unreadNotifications,
                showGreeting = look.showGreeting,
                onEdit = { editing = !editing },
            )
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (structure) {
                        HomeStructure.TONIGHT -> HomeTonightLayout(ctx, rows, frame)
                        HomeStructure.STAGE -> HomeStageLayout(ctx, rows, frame)
                        HomeStructure.SHELVES -> HomeShelvesLayout(ctx, rows, frame)
                    }
                }
                if (editing) {
                    HomeEditPanel(
                        order = fullOrder,
                        hidden = prefs.homeRowsHidden,
                        because = because,
                        onMove = { from, to -> actions.onSetRowPrefs(fullOrder.moved(from, to), prefs.homeRowsHidden) },
                        onToggle = { id ->
                            val h = prefs.homeRowsHidden
                            actions.onSetRowPrefs(fullOrder, if (id in h) h - id else h + id)
                        },
                        onReset = {
                            val order = prefs.homeRowOrder
                            val hidden = prefs.homeRowsHidden
                            actions.onSetRowPrefs(emptyList(), emptySet())
                            showUndo(resetMessage) { actions.onSetRowPrefs(order, hidden) }
                        },
                        onDone = { editing = false },
                        compact = !wide,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Esta noche

@Composable
private fun HomeTonightLayout(ctx: HomeRowCtx, rows: List<String>, frame: HomeFrame) {
    val rest = rows.filterNot { it == ROW_CONTINUE || it == ROW_DOWNLOADS }
    val a = ctx.actions
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            HomeList(topPadding = frame.topInset) {
                if (frame.wide) {
                    item(key = "header", contentType = "header") {
                        HomeHeader(
                            title = stringResource(Res.string.rd_s2_tonight),
                            subtitle = tonightSubtitle(ctx.tonight.size, frame.showGreeting),
                            accent = true,
                            frame = frame,
                            actions = a,
                        )
                    }
                } else {
                    phoneTop(ctx, frame)
                }
                notices(ctx, frame)
                if (ctx.state.isEmpty) {
                    emptyHome(ctx, frame)
                    return@HomeList
                }
                item(key = "tonight", contentType = "tonight") {
                    val m = Modifier.homeEntrance(0)
                    when {
                        ctx.tonight.isEmpty() -> TonightEmpty(a.onOpenCalendar, m)
                        frame.wide -> Column(m.fillMaxWidth()) {
                            TonightRow(ctx)
                            // HOME-07 "Ver todo" -> Biblioteca › Continuar (the wide header has no row title to carry it).
                            TextButton(onClick = a.onOpenLibrary, modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) {
                                Text(stringResource(Res.string.rd_s2_tonight_all))
                            }
                        }
                        else -> HomeSection(stringResource(Res.string.rd_s2_tonight), m, onSeeAll = a.onOpenLibrary) { TonightRow(ctx) }
                    }
                }
                item(key = "agenda", contentType = "agenda") { TodayAgenda(ctx, Modifier.homeEntrance(1)) }
                if (!frame.aside) {
                    item(key = "status", contentType = "status") {
                        StatusBlock(ctx, frame.wide, Modifier.homeEntrance(2))
                    }
                }
                homeRows(rest, ctx, firstIndex = 3)
                item(key = "end", contentType = "end") { EndRow(frame.onEdit, a.onRefreshAll) }
            }
        }
        if (frame.aside) {
            Column(
                Modifier
                    .width(ASIDE_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(top = frame.topInset + 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LinkPanel(a.onOpenLink, a.onOpenLinkMonitor)
                TransfersPanel(ctx.discover.activity, a.onOpenDownloads)
            }
        }
    }
}

/** Link and downloads when there is no side column: side by side on wide windows, a Link strip and the downloads row on phones. */
@Composable
private fun StatusBlock(ctx: HomeRowCtx, wide: Boolean, modifier: Modifier) {
    val a = ctx.actions
    val activity = ctx.discover.activity?.takeIf { it.total > 0 }
    if (wide) {
        Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { LinkPanel(a.onOpenLink, a.onOpenLinkMonitor) }
            Box(Modifier.weight(1f)) { TransfersPanel(ctx.discover.activity, a.onOpenDownloads) }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkPanel(a.onOpenLink, a.onOpenLinkMonitor, Modifier.padding(horizontal = 16.dp), compact = true)
            if (activity != null) {
                HomeSection(stringResource(Res.string.home_downloading, activity.total), onSeeAll = a.onOpenDownloads) {
                    DownloadsStrip(activity, a.onOpenDownloads)
                }
            }
        }
    }
}

@Composable
private fun TonightEmpty(onOpenCalendar: () -> Unit, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).decor(DecorRegion.EMPTY),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.rd_s2_tonight_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenCalendar) { Text(stringResource(Res.string.rd_s2_open_calendar)) }
        }
    }
}

// ---------------------------------------------------------------- Escenario

@Composable
private fun HomeStageLayout(ctx: HomeRowCtx, rows: List<String>, frame: HomeFrame) {
    val hero = ctx.state.hero
    val fullBleed = hero is LoadState.Ok && hero.value.isNotEmpty() || hero is LoadState.Loading
    HomeList(topPadding = if (fullBleed) 0.dp else frame.topInset) {
        item(key = "stage", contentType = "stage") { StageHero(ctx, frame, Modifier.homeEntrance(0)) }
        if (!frame.wide) phoneTop(ctx, frame)
        notices(ctx, frame)
        if (ctx.state.isEmpty) {
            emptyHome(ctx, frame)
            return@HomeList
        }
        homeRows(rows, ctx, firstIndex = 1)
        item(key = "end", contentType = "end") { EndRow(frame.onEdit, ctx.actions.onRefreshAll) }
    }
}

/** The stage: the trending pager drawn under the status bar; a placeholder while it loads, a compact error with retry if it fails. */
@Composable
private fun StageHero(ctx: HomeRowCtx, frame: HomeFrame, modifier: Modifier) {
    val a = ctx.actions
    when (val hero = ctx.state.hero) {
        is LoadState.Ok -> if (hero.value.isNotEmpty()) {
            Spotlight(
                items = hero.value,
                art = ctx.state.heroArt,
                continueWatching = ctx.state.continueWatching,
                autoplayMs = LocalLook.current.heroAutoplayMs(),
                onOpen = { media -> ctx.gate.open(media) { a.onMediaClick(media) } },
                onWatch = a.onWatch,
                onContinue = a.onResumeEntry,
                modifier = modifier,
                decorHero = true,
                topEnd = { if (!frame.wide) NotificationBell(frame.unread, a.onOpenNotifications, onScrim = true) },
            )
        } else {
            Box(modifier.fillMaxWidth().height(frame.topInset))
        }
        is LoadState.Err -> app.nijiyome.ui.state.AppErrorState(
            cause = hero.cause,
            onRetry = { a.onRetry(HERO_FEED) },
            compact = true,
            modifier = modifier.padding(horizontal = 16.dp),
        )
        LoadState.Loading -> LoadingPlaceholder(
            modifier.fillMaxWidth().height(if (frame.wide) STAGE_HEIGHT_WIDE else STAGE_HEIGHT_PHONE).decor(DecorRegion.HERO),
            shape = MaterialTheme.shapes.extraSmall,
        )
    }
}

// ---------------------------------------------------------------- Estantes

@Composable
private fun HomeShelvesLayout(ctx: HomeRowCtx, rows: List<String>, frame: HomeFrame) {
    HomeList(topPadding = frame.topInset) {
        item(key = "header", contentType = "header") {
            if (frame.wide) {
                HomeHeader(
                    title = stringResource(Res.string.rd_s2_home_title),
                    subtitle = listOfNotNull(
                        greeting().takeIf { frame.showGreeting },
                        stringResource(Res.string.rd_s2_shelves_sub),
                    ).joinToString(" · "),
                    accent = false,
                    frame = frame,
                    actions = ctx.actions,
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.rd_s2_home_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    NotificationBell(frame.unread, ctx.actions.onOpenNotifications)
                    IconButton(onClick = frame.onEdit) {
                        Icon(Icons.Outlined.DashboardCustomize, contentDescription = stringResource(Res.string.rd_s2_edit_title))
                    }
                }
            }
        }
        if (!frame.wide) phoneTop(ctx, frame, bell = false)
        notices(ctx, frame)
        if (ctx.state.isEmpty || rows.isEmpty()) {
            emptyHome(ctx, frame)
            return@HomeList
        }
        homeRows(rows, ctx)
        item(key = "end", contentType = "end") { EndRow(frame.onEdit, ctx.actions.onRefreshAll) }
    }
}

// ---------------------------------------------------------------- shared pieces

/** The Home list with the status-bar scrim once it scrolls (BUG-062: the clock never sits on a poster). */
@Composable
private fun HomeList(topPadding: Dp, content: LazyListScope.() -> Unit) {
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val dense = LocalLook.current.density == Density.COMPACT
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 24.dp),
            content = content,
        )
        AnimatedVisibility(scrolled, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            )
        }
    }
}

/** Wide header of Esta noche and Estantes: title, date line, Editar Inicio and Actualizar. */
@Composable
private fun HomeHeader(title: String, subtitle: String, accent: Boolean, frame: HomeFrame, actions: HomeActions) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val font: FontFamily? = if (accent) LocalAccentFont.current else null
            // T-03: one page-title size on every screen (headlineMedium, the artifact's 27 px); only Home keeps the accent face.
            Text(title, style = MaterialTheme.typography.headlineMedium, fontFamily = font, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = frame.onEdit) {
            Icon(Icons.Outlined.DashboardCustomize, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.rd_s2_edit_title), modifier = Modifier.padding(start = 8.dp))
        }
        IconButton(onClick = actions.onRefreshAll) {
            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(Res.string.rd_s2_refresh))
        }
    }
}

/** Phones: search pill and bell, the quick chips, and the greeting when it is on. */
private fun LazyListScope.phoneTop(ctx: HomeRowCtx, frame: HomeFrame, bell: Boolean = true) {
    item(key = "phone-top", contentType = "phone-top") {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (frame.showGreeting) {
                Text(greeting(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchPill(ctx.actions.onSearchClick, Modifier.weight(1f))
                if (bell) NotificationBell(frame.unread, ctx.actions.onOpenNotifications)
            }
            QuickChips(ctx.actions)
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.height(56.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.rd_s2_search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Offline notice and the AniList rate-limit banner (live countdown, Retry the primary provider). */
private fun LazyListScope.notices(ctx: HomeRowCtx, frame: HomeFrame) {
    if (!frame.online) {
        item(key = "offline", contentType = "notice") { OfflineNotice(ctx.actions.onRefreshAll) }
    }
    if (ctx.state.fallback != null) {
        item(key = "metadata-fallback", contentType = "notice") {
            app.nijiyome.ui.state.MetadataFallbackBanner(
                state = ctx.state.fallback,
                onRetryPrimary = ctx.actions.onRetryPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun OfflineNotice(onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = LocalSignalColors.current.warn)
            Text(stringResource(Res.string.rd_s2_offline_notice), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(stringResource(UiR.string.ui_retry)) }
        }
    }
}

/** Empty Home: why (offline or no metadata), Retry and the way to the metadata sources. */
private fun LazyListScope.emptyHome(ctx: HomeRowCtx, frame: HomeFrame) {
    item(key = "empty", contentType = "empty") {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).decor(DecorRegion.EMPTY),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EmptyArt(if (frame.online) EmptyKind.GENERIC else EmptyKind.ERROR)
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(if (frame.online) Res.string.home_empty_title else Res.string.rd_s2_offline_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (frame.online) Res.string.rd_s2_empty_body else Res.string.rd_s2_offline_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ctx.actions.onRefreshAll) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(UiR.string.ui_retry), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = ctx.actions.onOpenMetadataSettings) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.home_sources), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** End of every structure: Editar Inicio and Actualizar (phones reach them here; the stage has no header). */
@Composable
private fun EndRow(onEdit: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        OutlinedButton(onClick = onEdit) {
            Icon(Icons.Outlined.DashboardCustomize, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.rd_s2_edit_title), modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.rd_s2_refresh), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** "Good evening" by the local hour (< 12, < 19). */
@Composable
private fun greeting(): String {
    val hour = remember { LocalTime.now().hour }
    return stringResource(
        when {
            hour < 12 -> Res.string.home_greeting_morning
            hour < 19 -> Res.string.home_greeting_afternoon
            else -> Res.string.home_greeting_evening
        },
    )
}

/** "Miércoles 25 de noviembre · 6 para ver", with the greeting first when it is on. */
@Composable
private fun tonightSubtitle(count: Int, withGreeting: Boolean): String {
    val date = formatLongDate(LocalDate.now())
    val line = stringResource(Res.string.rd_s2_tonight_sub, date, count)
    return if (withGreeting) greeting() + " · " + line else line
}

private fun startOfDay(nowSec: Long): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()
}

private val WIDE_MIN = 600.dp
private val ASIDE_MIN = 1100.dp
private val ASIDE_WIDTH = 340.dp
private val EDIT_PANEL_WIDTH = 360.dp
private val STAGE_HEIGHT_WIDE = 440.dp
private val STAGE_HEIGHT_PHONE = 500.dp
