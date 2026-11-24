// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package app.nijiyome.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import app.nijiyome.designsystem.components.AppSnackbarController
import app.nijiyome.designsystem.components.AppSnackbarHost
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.rememberAppSnackbarController
import app.nijiyome.designsystem.theme.LocalReduceMotion
import app.nijiyome.designsystem.theme.NijiyomeTheme
import app.nijiyome.designsystem.theme.rememberReduceMotion
import app.nijiyome.feature.details.CharacterScreen
import app.nijiyome.feature.details.DetailsScreen
import app.nijiyome.feature.downloads.DownloadsScreen
import app.nijiyome.feature.extensions.ExtensionsScreen
import app.nijiyome.feature.extensions.ProviderItemScreen
import app.nijiyome.feature.extensions.ProviderScreen
import app.nijiyome.feature.extensions.rememberProviderResume
import app.nijiyome.feature.home.CalendarScreen
import app.nijiyome.feature.home.ForYouScreen
import app.nijiyome.feature.home.HomeScreen
import app.nijiyome.feature.library.LibraryScreen
import app.nijiyome.feature.library.LibraryTab
import app.nijiyome.feature.link.ExtensionServersScreen
import app.nijiyome.feature.link.LinkedDeviceScreen
import app.nijiyome.feature.manga.MangaDetailsScreen
import app.nijiyome.feature.manga.MangaHomeScreen
import app.nijiyome.feature.manga.MangaReaderScreen
import app.nijiyome.feature.novel.NovelDetailsScreen
import app.nijiyome.feature.novel.NovelHomeScreen
import app.nijiyome.feature.novel.NovelReaderScreen
import app.nijiyome.feature.player.PlayerScreen
import app.nijiyome.feature.search.ImageSearchScreen
import app.nijiyome.feature.search.SearchScreen
import app.nijiyome.feature.settings.OnboardingScreen
import app.nijiyome.feature.settings.SettingsScreen
import app.nijiyome.feature.social.ActivityThreadScreen
import app.nijiyome.feature.social.NotificationsScreen
import app.nijiyome.feature.social.ProfileScreen
import app.nijiyome.feature.torrents.TorrentsScreen
import app.nijiyome.feature.you.YouScreen
import app.nijiyome.model.AppSettings
import app.nijiyome.model.DownloadsTab
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.ThemeMode
import app.nijiyome.model.UpdateInfo
import app.nijiyome.model.UpdateRepository
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentSection
import app.nijiyome.model.torrent.TorrentServiceStatus
import app.nijiyome.model.Motion
import app.nijiyome.model.RailStyle
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.media.LocalTitleLanguage
import app.nijiyome.ui.state.contrastLevel
import app.nijiyome.ui.state.rememberIsOnline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.getKoin
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.app_active_downloads
import app.nijiyome.feature.shell.resources.app_update_available
import app.nijiyome.feature.shell.resources.app_update_view
import app.nijiyome.feature.shell.resources.shell_not_available
import app.nijiyome.ui.platform.windowHeightDp
import app.nijiyome.ui.platform.windowWidthDp
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Root composable, shared by Android and desktop: theme from the persisted settings (palette, AMOLED, contrast, dynamic color,
 * theme mode) + the app-wide CompositionLocals (LocalReduceMotion, LocalAppSnackbar) + adaptive shell + Navigation3 back stack
 * (12 §3.15, §4 b.1).
 *
 * [platformEffects] is what only the host can do with the settings: on Android the system bar icon style and the per-app language
 * (they need the Activity and live in the app module). It is composed first, outside the theme, as those effects always were.
 */
@Composable
fun NijiyomeApp(
    torrentLinks: MutableStateFlow<TorrentLink?>? = null,
    serverLinks: MutableStateFlow<Boolean>? = null,
    // parity wave A (15 §3.6): OAuth redirect arrived (open Settings › Listas y cuentas) / an image was shared to the app (image search).
    accountLinks: MutableStateFlow<Boolean>? = null,
    imageLinks: MutableStateFlow<String?>? = null,
    // parity wave B (16 §3.12): a ready Route from a notification tap / deep link of the wave (WaveBLinks), pushed once and cleared.
    routeLinks: MutableStateFlow<Route?>? = null,
    // Hito 6 (desktop): the window's app-wide keys (Ctrl+K, Ctrl+B, 1..9, Esc). Android passes none. Before [platformEffects], which
    // Android passes as a trailing lambda.
    shortcuts: ShellShortcuts? = null,
    platformEffects: @Composable (settings: AppSettings, dark: Boolean) -> Unit = { _, _ -> },
) {
    val settingsRepo = koinInject<SettingsRepository>()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    platformEffects(settings, dark)

    NijiyomeTheme(
        paletteId = settings.paletteId,
        amoled = settings.amoled,
        contrast = settings.contrastLevel(),
        dynamicColor = settings.dynamicColor,
        darkTheme = dark,
    ) {
        // 2026-11 UI refresh: Diseño y estilo › Animaciones "Desactivadas" is the same as Reducir movimiento.
        val reduceMotion = rememberReduceMotion(settings.reduceMotion || settings.look.motion == Motion.OFF)
        val snackbar = rememberAppSnackbarController()
        CompositionLocalProvider(
            LocalReduceMotion provides reduceMotion,
            LocalAppSnackbar provides snackbar,
            LocalTitleLanguage provides settings.titleLanguage,
            LocalLook provides settings.look,
        ) {
            // parity wave A, 2026-11-09 amendment (plan 14 §0.1 #11, worker q): first-run wizard until `AppSettings.onboardingDone`;
            // the wizard's ViewModel persists the flag (and "Restablecer la app" clears it), so this just reacts to the settings.
            if (!settings.onboardingDone) {
                OnboardingScreen(onDone = {})
            } else {
                AppShell(snackbar, torrentLinks, serverLinks, accountLinks, imageLinks, routeLinks, shortcuts)
            }
        }
    }
}

@Composable
private fun AppShell(
    snackbar: AppSnackbarController,
    torrentLinks: MutableStateFlow<TorrentLink?>? = null,
    serverLinks: MutableStateFlow<Boolean>? = null,
    accountLinks: MutableStateFlow<Boolean>? = null,
    imageLinks: MutableStateFlow<String?>? = null,
    routeLinks: MutableStateFlow<Route?>? = null,
    shortcuts: ShellShortcuts? = null,
) {
    val backStack = rememberNavBackStack(RouteSavedState, Route.Home)
    val current = backStack.lastOrNull() as? Route

    // parity wave B (16 §3.12): downloads / airing / AniList notification taps and nijiyome://user/<name> arrive as a Route.
    if (routeLinks != null) {
        LaunchedEffect(routeLinks) {
            routeLinks.collect { route ->
                if (route != null) {
                    if (backStack.lastOrNull() != route) backStack.add(route)
                    routeLinks.value = null
                }
            }
        }
    }

    // parity wave A (15 §3.3): nijiyome://oauth/<provider> came back from the Custom Tab. The redirect itself waits in PendingOAuthRedirect
    // (worker q's ListsRepository finishes the exchange); the shell only shows the Accounts section so the user sees the result.
    if (accountLinks != null) {
        LaunchedEffect(accountLinks) {
            accountLinks.collect { open ->
                if (open) {
                    val target = Route.SettingsSection(SettingsSection.ACCOUNTS)
                    if (backStack.lastOrNull() != target) backStack.add(target)
                    accountLinks.value = false
                }
            }
        }
    }

    // parity wave A (15 §3.1): an image shared to the app opens the image search with it.
    if (imageLinks != null) {
        LaunchedEffect(imageLinks) {
            imageLinks.collect { uri ->
                if (uri != null) {
                    val target = Route.ImageSearch(uri)
                    if (backStack.lastOrNull() != target) backStack.add(target)
                    imageLinks.value = null
                }
            }
        }
    }

    // Torrent notification taps (integration of f's notification extras): open the client section, Back returns to where we were.
    if (torrentLinks != null) {
        LaunchedEffect(torrentLinks) {
            torrentLinks.collect { link ->
                if (link != null) {
                    val target = Route.Torrents(link.section, link.torrentId)
                    if (backStack.lastOrNull() != target) backStack.add(target)
                    torrentLinks.value = null
                }
            }
        }
    }

    // nijiyome://extensionserver/connect (docs/redesign/13 §3.10): MainActivity left the link in PendingServerLink; open the screen,
    // which asks the user before pairing.
    if (serverLinks != null) {
        LaunchedEffect(serverLinks) {
            serverLinks.collect { open ->
                if (open) {
                    if (backStack.lastOrNull() != Route.ExtensionServers) backStack.add(Route.ExtensionServers)
                    serverLinks.value = false
                }
            }
        }
    }

    // ShortNavigationBar on compact windows (< 600 dp), WideNavigationRail from medium width; hidden over the player.
    // BUG-041: a phone in landscape (~430 dp tall) cannot fit the rail's destinations ("Tú" fell off the screen): short windows keep
    // the bottom bar, which always fits.
    val windowWidth = windowWidthDp()
    val windowHeight = windowHeightDp()
    val available = rememberRouteAvailability()
    // The bottom bar relies on the "Tú" page for everything it has no room for. Where that page cannot open (desktop, for now) the
    // rail stays whatever the window size, so no destination is ever out of reach; a short window gets the scrolling rail below.
    val wide = (windowWidth >= WIDE_MIN_WIDTH_DP && windowHeight >= RAIL_MIN_HEIGHT_DP) || !available.offers(Route.You)
    val destinations = remember(wide, available) { topLevelRoutes(wide).filter(available::offers) }
    // 2026-11 UI refresh: Diseño y estilo › Menú lateral applies to the rail only (phones keep the labelled bottom bar). A window too
    // short for every labelled item gets the icons-only rail too, so the last destinations never fall off the screen.
    // Hito 6 (desktop, Tatakai Next Ctrl+B): the shortcut shows the other style until pressed again.
    val slimStyle = (LocalLook.current.railStyle == RailStyle.SLIM) != (shortcuts?.navToggled == true)
    val slimRail = wide && (slimStyle || windowHeight < destinations.size * RAIL_ITEM_DP + 96)
    val selectedTop = backStack.asReversed()
        .firstNotNullOfOrNull { key -> (key as? Route)?.topLevel(wide)?.takeIf { it in destinations } }
    // parity wave B (16 §3.12): the manga / novel readers hide the nav like the player.
    val onPlayer = current is Route.Player || current is Route.MangaReader || current is Route.NovelReader
    // Tablet landscape (824 dp tall, 13 destinations): the icons-only rail did not fit either, Perfil / Ajustes ended under the
    // taskbar and the stock rail cannot scroll. The icons-only rail is drawn here instead ([ScrollableSlimRail]): it scrolls and
    // keeps clear of the system bars.
    val scrollRail = !onPlayer && slimRail
    val suiteType = when {
        onPlayer || scrollRail -> NavigationSuiteType.None
        wide -> NavigationSuiteType.WideNavigationRailCollapsed
        else -> NavigationSuiteType.ShortNavigationBarCompact
    }

    if (shortcuts != null) ShellShortcutHandler(shortcuts, backStack, destinations, onPlayer)

    val torrentStatus by rememberTorrentStatus().collectAsStateWithLifecycle()
    val activeDownloads = torrentStatus.activeDownloads
    val online = rememberIsOnline()
    val showOffline = !online && !onPlayer

    // parity wave A (15 §3.4, checklist system.update-startup-notice): one snackbar per newer release, "Ver" opens Acerca de.
    // Worker q's UpdateRepository publishes it (the contract stub never does); AppSettings.updateCheck is honoured inside the repository.
    val koin = getKoin()
    val latestFlow = remember(koin) { koin.getOrNull<UpdateRepository>()?.latest ?: MutableStateFlow<UpdateInfo?>(null) }
    val latestUpdate by latestFlow.collectAsStateWithLifecycle()
    val updateMessage = latestUpdate?.let { stringResource(Res.string.app_update_available, it.version) }
    val updateAction = stringResource(Res.string.app_update_view)
    LaunchedEffect(latestUpdate?.version) {
        if (updateMessage != null) {
            snackbar.show(updateMessage, updateAction) { backStack.add(Route.SettingsSection(SettingsSection.ABOUT)) }
        }
    }

    NavigationSuiteScaffold(
        navigationItems = {
            val badgeDescription = pluralStringResource(Res.plurals.app_active_downloads, activeDownloads, activeDownloads)
            destinations.forEach { route ->
                val selected = route == selectedTop
                // Badge = active downloads: on Library (compact, Torrents is a Library tab) / on Torrents (rail).
                val badged = activeDownloads > 0 &&
                    ((!wide && route == Route.Library) || (wide && route is Route.Torrents))
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { backStack.navigateTop(route) },
                    icon = {
                        val label = route.label()
                        Icon(route.icon(selected), contentDescription = if (slimRail) label else null)
                    },
                    label = if (slimRail) null else ({ Text(route.label()) }),
                    navigationSuiteType = suiteType,
                    badge = if (badged) {
                        {
                            Badge(Modifier.semantics { contentDescription = badgeDescription }) {
                                Text(if (activeDownloads > 99) "99+" else activeDownloads.toString())
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        },
        navigationSuiteType = suiteType,
        modifier = Modifier.fillMaxSize(),
    ) {
        // The rail does not consume the bottom system inset (the bar does): the banner and snackbar add it themselves there.
        val bottomInsets = if (suiteType == NavigationSuiteType.ShortNavigationBarCompact) WindowInsets(0) else WindowInsets.navigationBars
        Row(Modifier.fillMaxSize()) {
            if (scrollRail) {
                ScrollableSlimRail(destinations, selectedTop, activeDownloads) { backStack.navigateTop(it) }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AppNavDisplay(backStack, wide, available, snackbar, shortcuts)
                    }
                    AnimatedVisibility(visible = showOffline, enter = expandVertically(), exit = shrinkVertically()) {
                        OfflineBanner(Modifier.windowInsetsPadding(bottomInsets))
                    }
                }
                AppSnackbarHost(
                    snackbar,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(bottomInsets)
                        .padding(bottom = if (showOffline) OFFLINE_BANNER_HEIGHT else 0.dp),
                )
            }
        }
    }
}

/** Icons-only rail that scrolls when the destinations do not fit the window height (see `scrollRail`). */
@Composable
private fun ScrollableSlimRail(destinations: List<Route>, selected: Route?, activeDownloads: Int, onSelect: (Route) -> Unit) {
    val badgeDescription = pluralStringResource(Res.plurals.app_active_downloads, activeDownloads, activeDownloads)
    Column(
        Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        destinations.forEach { route ->
            val isSelected = route == selected
            val label = route.label()
            WideNavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(route) },
                icon = {
                    if (activeDownloads > 0 && route is Route.Torrents) {
                        BadgedBox(badge = {
                            Badge(Modifier.semantics { contentDescription = badgeDescription }) {
                                Text(if (activeDownloads > 99) "99+" else activeDownloads.toString())
                            }
                        }) { Icon(route.icon(isSelected), contentDescription = label) }
                    } else {
                        Icon(route.icon(isSelected), contentDescription = label)
                    }
                },
                label = null,
                railExpanded = false,
            )
        }
    }
}

/** Slim persistent banner while the default network is not validated (prototype `offline.banner`). */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(UiR.string.ui_offline_banner), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Torrent service status for the download badge. Resolved lazily from Koin so the shell keeps working when the torrent engine is
 * not bound (previews, a build without :core:torrent wired).
 */
@Composable
private fun rememberTorrentStatus(): StateFlow<TorrentServiceStatus> {
    val koin = getKoin()
    return remember(koin) {
        runCatching { koin.getOrNull<TorrentRepository>() }.getOrNull()?.status ?: MutableStateFlow(TorrentServiceStatus())
    }
}

@Composable
private fun AppNavDisplay(
    backStack: NavBackStack<NavKey>,
    wide: Boolean,
    available: RouteAvailability,
    snackbar: AppSnackbarController,
    shortcuts: ShellShortcuts?,
) {
    // List-detail on expanded windows: Search/Library are list panes, Details the detail pane.
    // BUG-045: two panes only when the window really is wide and tall enough (read from the configuration, which follows rotation at
    // once); a phone in portrait showed Search + a ~510 px Details with the title split letter by letter.
    // Tablet landscape (Galaxy Tab S9+, 1318 dp): a list pane alone (Library, Search, Calendar...) got half the width and an empty
    // detail pane beside it, so the screen looked like a phone in portrait. Two panes only while a detail is open next to its list.
    val detailOnTop = when (backStack.lastOrNull()) {
        is Route.Details, is Route.Character, is Route.ProviderItem, is Route.NovelDetails, is Route.ActivityThread -> true
        else -> false
    }
    val twoPanes = detailOnTop && windowWidthDp() >= TWO_PANE_MIN_WIDTH_DP && windowHeightDp() >= RAIL_MIN_HEIGHT_DP
    val defaultDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val directive = remember(defaultDirective, twoPanes) {
        defaultDirective.copy(maxHorizontalPartitions = if (twoPanes) defaultDirective.maxHorizontalPartitions.coerceAtMost(2) else 1)
    }
    val listDetail = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    fun openDetails(anilistId: Int) {
        if (backStack.lastOrNull() is Route.Details) backStack.removeLastOrNull()
        backStack.add(Route.Details(MediaKind.ANIME, anilistId))
    }
    // A destination whose services this platform does not have yet says so instead of opening (never the case on Android).
    val notAvailable = stringResource(Res.string.shell_not_available)
    fun push(route: Route) {
        if (!available.offers(route)) {
            snackbar.show(notAvailable)
            return
        }
        if (backStack.lastOrNull() != route) backStack.add(route)
    }
    fun replaceTop(route: Route) {
        backStack.removeLastOrNull()
        backStack.add(route)
    }
    fun play(args: PlayerArgs) = push(Route.Player(args))
    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    fun openTorrent(torrentId: String) = push(Route.Torrents(TorrentSection.OVERVIEW, torrentId))
    fun openSettings(section: SettingsSection) = push(Route.SettingsSection(section))
    fun openGenre(genre: String) = push(Route.SearchFor(genre = genre))
    // parity wave B (16 §3.12): manga details share Route.Details with kind MANGA (screen of s); novels have their own route.
    fun openMangaDetails(anilistId: Int) {
        if (backStack.lastOrNull() is Route.Details) backStack.removeLastOrNull()
        backStack.add(Route.Details(MediaKind.MANGA, anilistId))
    }
    fun openNovelDetails(anilistId: Int) {
        if (backStack.lastOrNull() is Route.NovelDetails) backStack.removeLastOrNull()
        backStack.add(Route.NovelDetails(anilistId))
    }
    /** A `Media` of any kind (social feeds, manga rows): anime -> Details(ANIME), manga -> Details(MANGA). */
    fun openMedia(media: Media) {
        val id = media.ref.anilistId ?: return
        if (media.kind == MediaKind.MANGA) openMangaDetails(id) else openDetails(id)
    }
    fun openDownloads(tab: DownloadsTab = DownloadsTab.ACTIVE) = push(Route.Downloads(tab))
    fun openUser(userName: String) = push(Route.Profile(userName))
    fun openActivity(activityId: Int) = push(Route.ActivityThread(activityId))

    // Reduce motion: instant screen changes (no slide / fade), predictive back included.
    val reduceMotion = LocalReduceMotion.current
    val instant = remember { ContentTransform(EnterTransition.None, ExitTransition.None) }

    NavDisplay(
        backStack = backStack,
        onBack = ::back,
        sceneStrategies = listOf(listDetail),
        transitionSpec = if (reduceMotion) ({ instant }) else defaultTransitionSpec<NavKey>(),
        popTransitionSpec = if (reduceMotion) ({ instant }) else defaultPopTransitionSpec<NavKey>(),
        predictivePopTransitionSpec = if (reduceMotion) ({ _ -> instant }) else defaultPredictivePopTransitionSpec<NavKey>(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        modifier = Modifier.fillMaxSize(),
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(
                    onMediaClick = { media -> media.ref.anilistId?.let { openDetails(it) } },
                    onSearchClick = { backStack.navigateTop(Route.Search) },
                    onPlay = ::play,
                    onOpenFeed = { feed -> push(Route.SearchFor(feed = feed)) },
                    onOpenGenre = ::openGenre,
                    onSearchQuery = { query -> push(Route.SearchFor(query = query)) },
                    onOpenLibrary = { backStack.navigateTop(Route.Library) },
                    // parity wave B (16 §3.12)
                    onOpenCalendar = { backStack.navigateTop(Route.Calendar) },
                    onOpenForYou = { push(Route.ForYou) },
                    onOpenManga = { push(Route.Manga()) },
                    onOpenNovels = { push(Route.Novels()) },
                    onOpenNotifications = { push(Route.Notifications) },
                    onOpenProvider = { ext, source -> push(Route.Provider(ext, source)) },
                    onOpenProviderItem = { ext, source, item -> push(Route.ProviderItem(ext, source, item)) },
                    onResumeProviderItem = rememberProviderResume(
                        onPlay = ::play,
                        onOpenItem = { ext, source, item -> push(Route.ProviderItem(ext, source, item)) },
                    ),
                    onOpenDetails = ::openDetails,
                )
            }
            // parity wave B (16 §3.1, worker r): calendar and "Para ti" sub-screens of Home.
            entry<Route.Calendar>(metadata = ListDetailSceneStrategy.listPane()) {
                // 2026-11 UI refresh: a bar / rail destination now (no back arrow).
                CalendarScreen(onBack = null, onMediaClick = ::openMedia)
            }
            entry<Route.ForYou>(metadata = ListDetailSceneStrategy.listPane()) {
                ForYouScreen(onBack = if (wide) null else ::back, onMediaClick = ::openMedia)
            }
            entry<Route.Search>(metadata = ListDetailSceneStrategy.listPane()) {
                SearchScreen(
                    onMediaClick = { media -> media.ref.anilistId?.let { openDetails(it) } },
                    onOpenImageSearch = { push(Route.ImageSearch()) },
                    // parity wave B (s, manga.route / novel.route): the Manga / Novel type chips open those homes.
                    onOpenMangaSearch = { q -> push(Route.Manga(q.ifBlank { null })) },
                    onOpenNovelSearch = { q -> push(Route.Novels(q.ifBlank { null })) },
                    focusQuery = shortcuts?.focusSearchPending == true,
                    onQueryFocused = { shortcuts?.focusSearchPending = false },
                )
            }
            entry<Route.SearchFor>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                SearchScreen(
                    onMediaClick = { media -> media.ref.anilistId?.let { openDetails(it) } },
                    initialQuery = key.query,
                    initialFeed = key.feed,
                    initialGenre = key.genre,
                    initialStudio = key.studio,
                    onOpenImageSearch = { push(Route.ImageSearch()) },
                    // parity wave B (s, manga.route / novel.route): the Manga / Novel type chips open those homes.
                    onOpenMangaSearch = { q -> push(Route.Manga(q.ifBlank { null })) },
                    onOpenNovelSearch = { q -> push(Route.Novels(q.ifBlank { null })) },
                )
            }
            // parity wave A (15 §3.1, worker o): image search (trace.moe), opened from the search bar or by sharing a picture.
            entry<Route.ImageSearch>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                ImageSearchScreen(
                    onBack = ::back,
                    onMediaClick = { media -> media.ref.anilistId?.let { openDetails(it) } },
                    imageUri = key.imageUri,
                )
            }
            entry<Route.Library>(metadata = ListDetailSceneStrategy.listPane()) {
                LibraryScreen(
                    onOpenDetails = ::openDetails,
                    onPlay = ::play,
                    onExplore = { backStack.navigateTop(Route.Home) },
                    onOpenTorrent = ::openTorrent,
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                    onOpenMangaDetails = ::openMangaDetails,
                    onOpenNovelDetails = ::openNovelDetails,
                )
            }
            entry<Route.LibraryTab>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                LibraryScreen(
                    onOpenDetails = ::openDetails,
                    onPlay = ::play,
                    onExplore = { backStack.navigateTop(Route.Home) },
                    initialTab = key.tab,
                    onOpenTorrent = ::openTorrent,
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                    onOpenMangaDetails = ::openMangaDetails,
                    onOpenNovelDetails = ::openNovelDetails,
                )
            }
            entry<Route.Details>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                if (key.kind == MediaKind.MANGA) {
                    // parity wave B (16 §3.5, worker s): manga details (own screen, same route so every Media click works).
                    MangaDetailsScreen(
                        anilistId = key.anilistId,
                        onBack = ::back,
                        onOpenReader = { args -> push(Route.MangaReader(args)) },
                        onOpenAnime = ::openDetails,
                        onOpenManga = ::openMedia,
                        onOpenExtensions = { push(Route.Extensions) },
                        onOpenDownloads = { openDownloads(DownloadsTab.MANGA) },
                        onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                        onOpenGenre = ::openGenre,
                    )
                } else {
                    DetailsScreen(
                        kind = key.kind,
                        anilistId = key.anilistId,
                        onBack = ::back,
                        onPlay = ::play,
                        onOpenExtensions = { push(Route.Extensions) },
                        onOpenTorrent = ::openTorrent,
                        onOpenSettings = ::openSettings,
                        onOpenLinked = { push(Route.Linked) },
                        // parity wave B (16 §3.2): the Downloads page (web + torrents) replaces the torrent client's Downloads section.
                        onOpenDownloads = { openDownloads(DownloadsTab.ANIME) },
                        onOpenGenre = ::openGenre,
                        onOpenCharacter = { id -> push(Route.Character(id)) },
                        onOpenStudio = { studio -> push(Route.SearchFor(studio = studio)) },
                    )
                }
            }
            // parity wave A (15 §3.1, worker o): character page, detail pane like Details.
            entry<Route.Character>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                CharacterScreen(
                    characterId = key.characterId,
                    onBack = ::back,
                    onMediaClick = { media -> media.ref.anilistId?.let { openDetails(it) } },
                )
            }
            entry<Route.Player> { key ->
                PlayerScreen(
                    args = key.args,
                    onBack = ::back,
                    onReplace = { args -> replaceTop(Route.Player(args)) },
                    onOpenTorrent = ::openTorrent,
                    onOpenSettings = ::openSettings,
                    onOpenExtensions = { push(Route.Extensions) },
                    onOpenLinked = { push(Route.Linked) },
                    // 0.4.1 end screen "Volver a la ficha": back to it when the player was opened from it, else the player
                    // entry becomes the title's page
                    onOpenDetails = { id ->
                        val details = Route.Details(MediaKind.ANIME, id)
                        if (backStack.getOrNull(backStack.size - 2) == details) back() else replaceTop(details)
                    },
                )
            }
            entry<Route.You> {
                YouScreen(
                    onOpenHistory = { push(Route.LibraryTab(LibraryTab.HISTORY)) },
                    // parity wave B (16 §3.2): the Downloads page (web + torrents + chapters + local library).
                    onOpenDownloads = { openDownloads() },
                    onOpenExtensions = { push(Route.Extensions) },
                    onOpenLinked = { push(Route.Linked) },
                    onOpenSettings = { push(Route.Settings) },
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                    onOpenManga = { push(Route.Manga()) },
                    onOpenNovels = { push(Route.Novels()) },
                    onOpenProfile = { push(Route.Profile()) },
                    onOpenNotifications = { push(Route.Notifications) },
                    onOpenForYou = { push(Route.ForYou) },
                )
            }
            entry<Route.Settings> {
                SettingsScreen(
                    onOpenExtensions = { push(Route.Extensions) },
                    onOpenLink = { push(Route.Linked) },
                    onOpenLinked = { push(Route.Linked) },
                    // Rail destination on wide windows (no back arrow); reached from "Tú" on phones.
                    onBack = if (wide) null else ::back,
                )
            }
            entry<Route.SettingsSection> { key ->
                SettingsScreen(
                    onOpenExtensions = { push(Route.Extensions) },
                    onOpenLink = { push(Route.Linked) },
                    initialSection = key.section,
                    onOpenLinked = { push(Route.Linked) },
                    onBack = ::back,
                )
            }
            entry<Route.Extensions> {
                ExtensionsScreen(
                    onBack = ::back,
                    onOpenProvider = { ext, source -> push(Route.Provider(ext, source)) },
                    // desktop: no Linked device screen (not a Link client), so the extension servers are opened from here
                    onOpenServers = if (!available.offers(Route.Linked) && available.offers(Route.ExtensionServers)) {
                        { push(Route.ExtensionServers) }
                    } else {
                        null
                    },
                )
            }
            // parity wave B (16 §3.4, worker r): custom provider pages.
            entry<Route.Provider>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                ProviderScreen(
                    extensionId = key.extensionId,
                    sourceId = key.sourceId,
                    onBack = ::back,
                    onOpenItem = { item -> push(Route.ProviderItem(key.extensionId, key.sourceId, item)) },
                )
            }
            entry<Route.ProviderItem>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                ProviderItemScreen(
                    extensionId = key.extensionId,
                    sourceId = key.sourceId,
                    itemId = key.itemId,
                    onBack = ::back,
                    onPlay = ::play,
                    onOpenReader = { args -> push(Route.MangaReader(args)) },
                    onOpenDetails = ::openDetails,
                )
            }
            // parity wave B (16 §3.2, worker r): Downloads page (rail destination on wide windows: no back arrow).
            entry<Route.Downloads> { key ->
                DownloadsScreen(
                    initialTab = key.tab,
                    onBack = if (wide) null else ::back,
                    onPlay = ::play,
                    onOpenDetails = ::openDetails,
                    onOpenTorrent = ::openTorrent,
                    onOpenMangaReader = { args -> push(Route.MangaReader(args)) },
                    onOpenNovelReader = { args -> push(Route.NovelReader(args)) },
                )
            }
            // parity wave B (16 §3.5 / §3.6, worker s): manga and novels.
            entry<Route.Manga>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                MangaHomeScreen(
                    onMediaClick = ::openMedia,
                    onBack = if (wide) null else ::back,
                    onOpenReader = { args -> push(Route.MangaReader(args)) },
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                    initialQuery = key.query,
                )
            }
            entry<Route.MangaReader> { key ->
                MangaReaderScreen(
                    args = key.args,
                    onBack = ::back,
                    onReplace = { args -> replaceTop(Route.MangaReader(args)) },
                    onOpenSettings = ::openSettings,
                )
            }
            entry<Route.Novels>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                NovelHomeScreen(
                    onMediaClick = { media -> media.ref.anilistId?.let { openNovelDetails(it) } },
                    onBack = if (wide) null else ::back,
                    onOpenReader = { args -> push(Route.NovelReader(args)) },
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                    initialQuery = key.query,
                )
            }
            entry<Route.NovelDetails>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                NovelDetailsScreen(
                    anilistId = key.anilistId,
                    onBack = ::back,
                    onOpenReader = { args -> push(Route.NovelReader(args)) },
                    onOpenExtensions = { push(Route.Extensions) },
                    onOpenDownloads = { openDownloads(DownloadsTab.NOVELS) },
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                    onOpenGenre = ::openGenre,
                )
            }
            entry<Route.NovelReader> { key ->
                NovelReaderScreen(
                    args = key.args,
                    onBack = ::back,
                    onReplace = { args -> replaceTop(Route.NovelReader(args)) },
                    onOpenSettings = ::openSettings,
                )
            }
            // parity wave B (16 §3.7, worker t): AniList social.
            entry<Route.Profile>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                ProfileScreen(
                    userName = key.userName,
                    onBack = if (wide && key.userName == null) null else ::back,
                    onOpenUser = ::openUser,
                    onOpenActivity = ::openActivity,
                    onOpenMedia = ::openMedia,
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                )
            }
            entry<Route.ActivityThread>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                ActivityThreadScreen(activityId = key.activityId, onBack = ::back, onOpenUser = ::openUser, onOpenMedia = ::openMedia)
            }
            entry<Route.Notifications> {
                NotificationsScreen(
                    onBack = ::back,
                    onOpenMedia = ::openMedia,
                    onOpenActivity = ::openActivity,
                    onOpenUser = ::openUser,
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                )
            }
            entry<Route.Link> {
                LinkedDeviceScreen(onBack = ::back, onPlay = ::play, onOpenExtensionServers = { push(Route.ExtensionServers) })
            }
            entry<Route.Linked> {
                LinkedDeviceScreen(onBack = ::back, onPlay = ::play, onOpenExtensionServers = { push(Route.ExtensionServers) })
            }
            entry<Route.ExtensionServers> { ExtensionServersScreen(onBack = ::back) }
            entry<Route.Torrents> { key ->
                TorrentsScreen(
                    section = key.section,
                    torrentId = key.torrentId,
                    onNavigate = { section, torrentId -> replaceTop(Route.Torrents(section, torrentId)) },
                    onBack = ::back,
                    onPlay = ::play,
                )
            }
        },
    )
}

/**
 * Hito 6 (desktop, Tatakai Next `shortcut_in` / app.rs): Ctrl+K opens Search with its field focused and Ctrl+B switches the rail
 * style anywhere; 1..9 open the rail destinations and Esc goes back (the screen's own handler first) outside the player and the
 * readers, which keep their own keys.
 */
@Composable
private fun ShellShortcutHandler(shortcuts: ShellShortcuts, backStack: NavBackStack<NavKey>, destinations: List<Route>, onPlayer: Boolean) {
    val currentDestinations by rememberUpdatedState(destinations)
    val currentOnPlayer by rememberUpdatedState(onPlayer)
    DisposableEffect(shortcuts, backStack) {
        shortcuts.handler = { shortcut ->
            when (shortcut) {
                ShellShortcut.FocusSearch -> {
                    backStack.navigateTop(Route.Search)
                    shortcuts.focusSearchPending = true
                    true
                }
                ShellShortcut.ToggleNav -> {
                    shortcuts.navToggled = !shortcuts.navToggled
                    true
                }
                is ShellShortcut.NavTo -> {
                    val target = currentDestinations.getOrNull(shortcut.position - 1)
                    if (currentOnPlayer || target == null) {
                        false
                    } else {
                        backStack.navigateTop(target)
                        true
                    }
                }
                ShellShortcut.Back -> when {
                    // The viewers handle Esc on their focused root; one that lost the focus gets it here (its own Esc, focus back).
                    currentOnPlayer -> shortcuts.escapeViewer()
                    shortcuts.back() -> true
                    backStack.size > 1 -> {
                        backStack.removeLastOrNull()
                        true
                    }
                    else -> false
                }
            }
        }
        onDispose { shortcuts.handler = null }
    }
}

/** Switching tabs resets the stack to `[Home]` or `[Home, tab]` so Back from a tab returns to Home. */
private fun NavBackStack<NavKey>.navigateTop(route: Route) {
    val target = if (route == Route.Home) listOf<NavKey>(Route.Home) else listOf(Route.Home, route)
    if (toList() == target) return
    clear()
    addAll(target)
}

private const val WIDE_MIN_WIDTH_DP = 600

/** Approximate height of a labelled rail item (icon + label + padding). */
private const val RAIL_ITEM_DP = 64

/** BUG-041 / BUG-045: below this height (a phone in landscape) the rail does not fit and two panes are too cramped. */
private const val RAIL_MIN_HEIGHT_DP = 480

/** BUG-045: list + detail side by side from the expanded width class. */
private const val TWO_PANE_MIN_WIDTH_DP = 840
private val OFFLINE_BANNER_HEIGHT = 40.dp

private fun Route.icon(selected: Boolean): ImageVector = when (this) {
    Route.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    Route.Search -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
    Route.Library -> if (selected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary
    is Route.Torrents -> if (selected) Icons.Filled.Download else Icons.Outlined.Download
    Route.Extensions -> if (selected) Icons.Filled.Extension else Icons.Outlined.Extension
    Route.Linked, Route.Link -> if (selected) Icons.Filled.Computer else Icons.Outlined.Computer
    Route.Settings -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    // parity wave B (16 §3.12)
    is Route.Manga -> if (selected) Icons.Filled.MenuBook else Icons.Outlined.MenuBook
    is Route.Novels -> if (selected) Icons.Filled.AutoStories else Icons.Outlined.AutoStories
    is Route.Downloads -> if (selected) Icons.Filled.CloudDownload else Icons.Outlined.CloudDownload
    is Route.Profile -> if (selected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle
    // 2026-11 UI refresh
    Route.Calendar -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    Route.ForYou -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    else -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

@Composable
private fun Route.label(): String = when (this) {
    Route.Home -> stringResource(UiR.string.nav_home)
    Route.Search -> stringResource(UiR.string.nav_search)
    Route.Library -> stringResource(UiR.string.nav_library)
    is Route.Torrents -> stringResource(UiR.string.nav_torrents)
    Route.Extensions -> stringResource(UiR.string.nav_extensions)
    Route.Linked, Route.Link -> stringResource(UiR.string.nav_linked)
    Route.Settings -> stringResource(UiR.string.nav_settings)
    // parity wave B (16 §3.12): shared labels of core/ui
    is Route.Manga -> stringResource(UiR.string.nav_manga)
    is Route.Novels -> stringResource(UiR.string.nav_novels)
    is Route.Downloads -> stringResource(UiR.string.nav_downloads)
    is Route.Profile -> stringResource(UiR.string.nav_profile)
    Route.Calendar -> stringResource(UiR.string.ui_nav_calendar)
    Route.ForYou -> stringResource(UiR.string.ui_nav_for_you)
    else -> stringResource(UiR.string.nav_you)
}
