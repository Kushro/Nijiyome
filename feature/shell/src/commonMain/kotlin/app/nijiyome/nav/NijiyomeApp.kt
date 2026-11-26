// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package app.nijiyome.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import app.nijiyome.designsystem.components.AppSnackbarController
import app.nijiyome.designsystem.components.AppSnackbarHost
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.rememberAppSnackbarController
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.CardToDetailsEntry
import app.nijiyome.designsystem.motion.CardToDetailsHost
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionDurations
import app.nijiyome.designsystem.motion.cardToDetailsEnterTransform
import app.nijiyome.designsystem.motion.cardToDetailsPopTransform
import app.nijiyome.designsystem.motion.motionFadeIn
import app.nijiyome.designsystem.motion.motionFadeOut
import app.nijiyome.designsystem.motion.rememberLowEndDevice
import app.nijiyome.designsystem.theme.LocalReduceMotion
import app.nijiyome.designsystem.theme.NijiyomeTheme
import app.nijiyome.designsystem.theme.rememberReduceMotion
import app.nijiyome.feature.details.CharacterScreen
import app.nijiyome.feature.details.DetailsScreen
import app.nijiyome.feature.downloads.DownloadsScreen
import app.nijiyome.feature.downloads.TransfersTorrentSlots
import app.nijiyome.feature.extensions.ExtensionsScreen
import app.nijiyome.feature.extensions.ProviderItemScreen
import app.nijiyome.feature.extensions.ProviderScreen
import app.nijiyome.feature.extensions.rememberProviderResume
import app.nijiyome.feature.home.CalendarScreen
import app.nijiyome.feature.home.ForYouPanel
import app.nijiyome.feature.home.ForYouPreview
import app.nijiyome.feature.home.HomeScreen
import app.nijiyome.feature.home.TasteAtlas
import app.nijiyome.feature.library.LibraryScreen
import app.nijiyome.feature.library.LibraryTab
import app.nijiyome.feature.link.ExtensionServersContent
import app.nijiyome.feature.link.ExtensionServersScreen
import app.nijiyome.feature.link.LinkedDeviceScreen
import app.nijiyome.feature.manga.MangaDetailsScreen
import app.nijiyome.feature.manga.MangaHomeScreen
import app.nijiyome.feature.manga.MangaReaderScreen
import app.nijiyome.feature.novel.NovelDetailsScreen
import app.nijiyome.feature.novel.NovelHomeScreen
import app.nijiyome.feature.novel.NovelReaderScreen
import app.nijiyome.feature.player.PlayerScreen
import app.nijiyome.feature.search.ExploreTab
import app.nijiyome.feature.search.ImageSearchScreen
import app.nijiyome.feature.search.SearchScreen
import app.nijiyome.feature.settings.OnboardingScreen
import app.nijiyome.feature.settings.SettingsModalDialog
import app.nijiyome.feature.settings.SettingsScreen
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.app_active_downloads
import app.nijiyome.feature.shell.resources.app_update_available
import app.nijiyome.feature.shell.resources.app_update_view
import app.nijiyome.feature.shell.resources.shell_not_available
import app.nijiyome.feature.social.ActivityThreadScreen
import app.nijiyome.feature.social.NotificationsScreen
import app.nijiyome.feature.social.ProfileScreen
import app.nijiyome.feature.torrents.TorrentsScreen
import app.nijiyome.feature.torrents.TransfersAddTorrentHost
import app.nijiyome.feature.torrents.TransfersTorrentDetail
import app.nijiyome.feature.you.YouScreen
import app.nijiyome.model.AppSettings
import app.nijiyome.model.DownloadRepository
import app.nijiyome.model.DownloadsSummary
import app.nijiyome.model.DownloadsTab
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.Motion
import app.nijiyome.model.NavStyle
import app.nijiyome.model.NetworkSettingsController
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.RailStyle
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.ThemeMode
import app.nijiyome.model.UpdateInfo
import app.nijiyome.model.UpdateRepository
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentSection
import app.nijiyome.model.torrent.TorrentServiceStatus
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.directionSpec
import app.nijiyome.ui.look.rememberMotionChoices
import app.nijiyome.ui.media.LocalTitleLanguage
import app.nijiyome.ui.platform.windowHeightDp
import app.nijiyome.ui.platform.windowWidthDp
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_offline_banner
import app.nijiyome.ui.state.LocalScreenInsets
import app.nijiyome.ui.state.contrastLevel
import app.nijiyome.ui.state.rememberIsOnline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
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
    /** Desktop custom title bar rendered inside the active theme (null on Android). It reads [LocalShellChrome]. */
    windowTopBar: (@Composable () -> Unit)? = null,
    /**
     * Sparkles file drop (desktop): opens a dropped magnet link or `.torrent` the way the command line does; true when it was taken.
     * Non-null turns the window-wide drop layer on. Android passes none (files arrive by Open with / Share).
     */
    openExternal: ((String) -> Boolean)? = null,
    /** Desktop: opens the Link monitor dialog (Inicio's Link panel "Monitor"). Null on Android: that button opens the Link screen. */
    openLinkMonitor: (() -> Unit)? = null,
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
    val chrome = remember { ShellChrome() }

    NijiyomeTheme(
        paletteId = settings.paletteId,
        amoled = settings.amoled,
        contrast = settings.contrastLevel(),
        dynamicColor = settings.dynamicColor,
        darkTheme = dark,
        direction = settings.look.design.directionSpec(),
        directionPalette = settings.look.design.palette,
    ) {
        // 2026-11 UI refresh: Diseño y estilo › Animaciones "Desactivadas" is the same as Reducir movimiento.
        val reduceMotion = rememberReduceMotion(settings.reduceMotion || settings.look.motion == Motion.OFF)
        val snackbar = rememberAppSnackbarController()
        CompositionLocalProvider(
            LocalReduceMotion provides reduceMotion,
            LocalAppSnackbar provides snackbar,
            LocalTitleLanguage provides settings.titleLanguage,
            LocalLook provides settings.look,
            LocalMotion provides rememberMotionChoices(settings.look.design, reduce = reduceMotion, lowEndDevice = rememberLowEndDevice()),
            LocalShellChrome provides chrome,
        ) {
            val shell: @Composable () -> Unit = {
                if (!settings.onboardingDone) {
                    // parity wave A, 2026-11-09 amendment (plan 14 §0.1 #11, worker q): first-run wizard until `AppSettings.onboardingDone`;
                    // the wizard's ViewModel persists the flag (and "Restablecer la app" clears it), so this just reacts to the settings.
                    OnboardingScreen(onDone = {})
                } else {
                    AppShell(
                        snackbar, chrome, torrentLinks, serverLinks, accountLinks, imageLinks, routeLinks, shortcuts,
                        hostTopBar = windowTopBar != null, openExternal = openExternal, openLinkMonitor = openLinkMonitor,
                    )
                }
            }
            // Sparkles: the direction paints the window behind everything (paper, night sky); screens draw on top of it.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).decor(DecorRegion.APP_BACKGROUND)) {
                if (windowTopBar != null) {
                    Column(Modifier.fillMaxSize()) {
                        windowTopBar()
                        Box(Modifier.weight(1f).fillMaxWidth()) { shell() }
                    }
                } else {
                    shell()
                }
            }
        }
    }
}

@Composable
private fun AppShell(
    snackbar: AppSnackbarController,
    chrome: ShellChrome,
    torrentLinks: MutableStateFlow<TorrentLink?>? = null,
    serverLinks: MutableStateFlow<Boolean>? = null,
    accountLinks: MutableStateFlow<Boolean>? = null,
    imageLinks: MutableStateFlow<String?>? = null,
    routeLinks: MutableStateFlow<Route?>? = null,
    shortcuts: ShellShortcuts? = null,
    hostTopBar: Boolean = false,
    openExternal: ((String) -> Boolean)? = null,
    openLinkMonitor: (() -> Unit)? = null,
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

    val koin = getKoin()
    val settingsRepo = koinInject<SettingsRepository>()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // ShortNavigationBar on compact windows (< 600 dp), the Sparkles rail from medium width; hidden over the player.
    // BUG-041: a phone in landscape (~430 dp tall) cannot fit the rail's destinations ("Tú" fell off the screen): short windows keep
    // the bottom bar, which always fits.
    val windowWidth = windowWidthDp()
    val windowHeight = windowHeightDp()
    val available = rememberRouteAvailability()
    // The bottom bar relies on the "Tú" page for everything it has no room for. Where that page cannot open (desktop, for now) the
    // rail stays whatever the window size, so no destination is ever out of reach; a short window gets the scrolling rail below.
    val wide = (windowWidth >= WIDE_MIN_WIDTH_DP && windowHeight >= RAIL_MIN_HEIGHT_DP) || !available.offers(Route.You)
    // Sparkles navigation (Ajustes › Apariencia): rail, by medium or commands. Wide windows only; phones keep the bottom bar.
    val navStyle = if (wide) settings.look.design.nav else NavStyle.RAIL
    val rail = remember(navStyle, available) { railGroups(navStyle).filter(available::offers) }
    // In rail order: what the bar / rail shows and what 1..9 jump to.
    val destinations = remember(wide, rail, available) {
        if (wide) rail.ordered else TopLevelRoutesCompact.filter(available::offers)
    }
    // Diseño y estilo › Menú lateral (labelled / slim), inverted by Ctrl+B for the session. The command navigation starts icon-only.
    val railStyleSlim = LocalLook.current.railStyle == RailStyle.SLIM || navStyle == NavStyle.COMMAND
    val slimStyle = railStyleSlim != (shortcuts?.navToggled == true)
    val selectedTop = backStack.asReversed()
        .firstNotNullOfOrNull { key -> (key as? Route)?.topLevel(wide)?.takeIf { it in destinations } }
    val medium = backStack.asReversed().firstNotNullOfOrNull { (it as? Route)?.medium() } ?: ShellMedium.ANIME
    // parity wave B (16 §3.12): the manga / novel readers hide the nav like the player.
    val onPlayer = current is Route.Player || current is Route.MangaReader || current is Route.NovelReader
    val useCustomRail = !onPlayer && wide
    val suiteType = when {
        onPlayer || useCustomRail -> NavigationSuiteType.None
        else -> NavigationSuiteType.ShortNavigationBarCompact
    }
    val shellTopBar = useCustomRail && !hostTopBar && navStyle != NavStyle.RAIL

    var paletteOpen by remember { mutableStateOf(false) }
    var settingsModalOpen by remember { mutableStateOf(false) }
    var settingsModalSection by remember { mutableStateOf<SettingsSection?>(null) }
    var settingsModalItemId by remember { mutableStateOf<String?>(null) }

    val allowedSettingsSections = remember(koin) {
        val hasLists = koin.getOrNull<ListsRepository>() != null
        val hasNetwork = koin.getOrNull<NetworkSettingsController>() != null
        SettingsSection.entries.filterTo(linkedSetOf()) { s ->
            (hasLists || s != SettingsSection.ACCOUNTS) && (hasNetwork || s != SettingsSection.NETWORK)
        }
    }

    val notAvailable = stringResource(Res.string.shell_not_available)

    /** Opens [route] from the shell (palette, drop layer): a rail / bar destination resets the stack, anything else stacks. */
    fun openRoute(route: Route) {
        when {
            !available.offers(route) -> snackbar.show(notAvailable)
            route in destinations -> backStack.navigateTop(route)
            backStack.lastOrNull() != route -> backStack.add(route)
        }
    }

    /**
     * Settings at [section]: the modal over the current screen on desktop, the screen elsewhere. [itemId] (a `SettingsCatalog` id) is
     * the exact setting to land on (Settings scrolls to it and highlights it).
     */
    fun openSettings(section: SettingsSection?, itemId: String?) {
        val top = backStack.lastOrNull()
        if (top == Route.Settings || top is Route.SettingsSection) {
            // AJ-07: already on the Settings page: go to the setting there instead of stacking the modal over the page.
            if (section != null) {
                val target = Route.SettingsSection(section, itemId)
                if (top != target) {
                    // The rail's Settings page stays under it (Back returns there); a section page is replaced.
                    if (top is Route.SettingsSection) backStack.removeLastOrNull()
                    backStack.add(target)
                }
            }
        } else if (wide && shortcuts != null) {
            settingsModalSection = section
            settingsModalItemId = itemId
            settingsModalOpen = true
        } else {
            val r = if (section != null) Route.SettingsSection(section, itemId) else Route.Settings
            if (backStack.lastOrNull() != r) backStack.add(r)
        }
    }

    if (shortcuts != null) {
        LaunchedEffect(backStack.size) { shortcuts.canGoBack = backStack.size > 1 }
        ShellShortcutHandler(
            shortcuts = shortcuts,
            backStack = backStack,
            destinations = destinations,
            onPlayer = onPlayer,
            onOpenPalette = { paletteOpen = true },
            onOpenSettings = { sec -> openSettings(sec, null) },
        )
    }

    // What the host's title bar draws (medium selector, command field, bell) follows the shell.
    val openNotifications: (() -> Unit)? = remember(available, backStack) {
        if (available.offers(Route.Notifications)) {
            { if (backStack.lastOrNull() != Route.Notifications) backStack.add(Route.Notifications) }
        } else {
            null
        }
    }
    SideEffect {
        chrome.ready = true
        chrome.nav = navStyle
        chrome.wide = wide
        chrome.viewer = onPlayer
        chrome.medium = medium
        chrome.settingsInRail = useCustomRail && Route.Settings in destinations
        chrome.selectMedium = { m -> backStack.navigateTop(m.home()) }
        chrome.openPalette = { paletteOpen = true }
        chrome.openNotifications = openNotifications
    }
    DisposableEffect(chrome) { onDispose { chrome.ready = false } }

    val activeDownloads = rememberActiveDownloads()
    val online = rememberIsOnline()
    val showOffline = !online && !onPlayer

    // parity wave A (15 §3.4, checklist system.update-startup-notice): one snackbar per newer release, "Ver" opens Acerca de.
    val latestFlow = remember(koin) { koin.getOrNull<UpdateRepository>()?.latest ?: MutableStateFlow<UpdateInfo?>(null) }
    val latestUpdate by latestFlow.collectAsStateWithLifecycle()
    val updateMessage = latestUpdate?.let { stringResource(Res.string.app_update_available, it.version) }
    val updateAction = stringResource(Res.string.app_update_view)
    LaunchedEffect(latestUpdate?.version) {
        if (updateMessage != null) {
            snackbar.show(updateMessage, updateAction) { backStack.add(Route.SettingsSection(SettingsSection.ABOUT)) }
        }
    }

    // Sparkles: one notice after updating (the first-run wizard of a new install marks it seen); either button marks it seen.
    val showIntro = !settings.look.design.introSeen && !onPlayer
    fun markIntroSeen() {
        scope.launch { settingsRepo.update { it.copy(look = it.look.copy(design = it.look.design.copy(introSeen = true))) } }
    }

    // Provide accurate screen insets to all child Scaffolds (0dp bottom on compact bar since NavigationSuiteScaffold consumes it).
    val childInsets = if (suiteType == NavigationSuiteType.ShortNavigationBarCompact) {
        WindowInsets.statusBars
    } else {
        WindowInsets.statusBars.union(WindowInsets.navigationBars)
    }
    val openPalette: () -> Unit = remember { { paletteOpen = true } }

    CompositionLocalProvider(
        LocalScreenInsets provides childInsets,
        LocalShellMedium provides medium,
        LocalOpenCommandPalette provides openPalette,
    ) {
        FileDropHost(enabled = openExternal != null, openExternal = openExternal, onOpenRoute = ::openRoute, modifier = Modifier.fillMaxSize()) {
            NavigationSuiteScaffold(
                navigationItems = {
                    val badgeDescription = pluralStringResource(Res.plurals.app_active_downloads, activeDownloads, activeDownloads)
                    destinations.forEach { route ->
                        val selected = route == selectedTop
                        // Sparkles: the active-downloads badge sits on Tú (Transferencias is a row of Tú on phones).
                        val badged = activeDownloads > 0 && route == Route.You
                        NavigationSuiteItem(
                            selected = selected,
                            onClick = { backStack.navigateTop(route) },
                            icon = { Icon(route.icon(selected), contentDescription = null) },
                            label = { Text(route.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            ) {
                // The rail does not consume the bottom system inset (the bar does): the banner and snackbar add it themselves there.
                val bottomInsets = if (suiteType == NavigationSuiteType.ShortNavigationBarCompact) WindowInsets(0) else WindowInsets.navigationBars
                Row(Modifier.fillMaxSize()) {
                    if (useCustomRail) {
                        ShellRail(
                            groups = rail,
                            selected = selectedTop,
                            activeDownloads = activeDownloads,
                            slim = slimStyle,
                            onSelect = { backStack.navigateTop(it) },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize()) {
                            // Wide windows without a host bar (tablets, desktop with the native title bar) get the selector / command bar here.
                            if (shellTopBar) ShellTopBar(Modifier.windowInsetsPadding(WindowInsets.statusBars))
                            AnimatedVisibility(visible = showIntro, enter = expandVertically(), exit = shrinkVertically()) {
                                IntroNotice(
                                    onOpen = {
                                        markIntroSeen()
                                        openSettings(SettingsSection.APPEARANCE, null)
                                    },
                                    onDismiss = { markIntroSeen() },
                                    modifier = if (shellTopBar || hostTopBar) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars),
                                )
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                // Sparkles motion "ficha": the card and the Details header meet inside this host.
                                CardToDetailsHost {
                                    AppNavDisplay(
                                        backStack, wide, available, snackbar, shortcuts,
                                        openSettingsAt = { section -> openSettings(section, null) },
                                        openLinkMonitor = openLinkMonitor,
                                    )
                                }
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

        if (paletteOpen) {
            // Every screen this device offers: the rail / bar first (with their number keys), then the ones reached from inside.
            val paletteScreens = remember(available) {
                (TopLevelRoutesWide + listOf(Route.ForYou, Route.Notifications, Route.ImageSearch()))
                    .filter { it != Route.You && available.offers(it) }
            }
            NijiyomeCommandPalette(
                settings = settings,
                allowedSections = allowedSettingsSections,
                destinations = destinations.filter { it != Route.You },
                numbered = if (shortcuts != null) destinations.take(9) else emptyList(),
                extraScreens = paletteScreens,
                fullScreen = !wide,
                actions = PaletteActions(
                    onNavigate = { route -> openRoute(route) },
                    onOpenSettings = { section, itemId -> openSettings(section, itemId) },
                    onSearchCatalog = { q -> backStack.add(Route.SearchFor(query = q)) },
                    onToggleAmoled = { scope.launch { settingsRepo.update { it.copy(amoled = !it.amoled) } } },
                    onCycleThemeMode = {
                        scope.launch {
                            settingsRepo.update { cur ->
                                val next = when (cur.themeMode) {
                                    ThemeMode.SYSTEM -> ThemeMode.DARK
                                    ThemeMode.DARK -> ThemeMode.LIGHT
                                    ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                }
                                cur.copy(themeMode = next)
                            }
                        }
                    },
                    onToggleRail = if (shortcuts != null && wide) ({ shortcuts.navToggled = !shortcuts.navToggled }) else null,
                    onSetNav = if (wide) {
                        { style -> scope.launch { settingsRepo.update { it.copy(look = it.look.copy(design = it.look.design.copy(nav = style))) } } }
                    } else {
                        null
                    },
                    onGoBack = if (shortcuts != null) ({ shortcuts.navigateBack() }) else null,
                    keyboard = shortcuts != null,
                ),
                onDismiss = { paletteOpen = false },
            )
        }

        if (settingsModalOpen) {
            SettingsModalDialog(
                initialSection = settingsModalSection,
                initialItemId = settingsModalItemId,
                onDismiss = { settingsModalOpen = false },
                onOpenExtensions = { backStack.navigateTop(Route.Extensions) },
                onOpenLink = { if (available.offers(Route.Linked)) backStack.add(Route.Linked) },
                onOpenLinked = { if (available.offers(Route.Linked)) backStack.add(Route.Linked) },
                onOpenDebrid = { backStack.add(Route.Torrents(TorrentSection.DEBRID)) },
                onOpenCalendar = { backStack.navigateTop(Route.Calendar) },
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
 * Active downloads for the badge: torrents downloading plus web / chapter downloads in progress. Resolved lazily from Koin so the shell
 * keeps working when a service is not bound (previews, desktop builds without one).
 */
@Composable
private fun rememberActiveDownloads(): Int {
    val koin = getKoin()
    val torrents = remember(koin) {
        runCatching { koin.getOrNull<TorrentRepository>() }.getOrNull()?.status ?: MutableStateFlow(TorrentServiceStatus())
    }
    val web = remember(koin) {
        runCatching { koin.getOrNull<DownloadRepository>() }.getOrNull()?.summary ?: MutableStateFlow(DownloadsSummary())
    }
    val torrentStatus by torrents.collectAsStateWithLifecycle()
    val webSummary by web.collectAsStateWithLifecycle()
    return torrentStatus.activeDownloads + webSummary.active
}

@Composable
private fun AppNavDisplay(
    backStack: NavBackStack<NavKey>,
    wide: Boolean,
    available: RouteAvailability,
    snackbar: AppSnackbarController,
    shortcuts: ShellShortcuts?,
    openSettingsAt: (SettingsSection) -> Unit,
    openLinkMonitor: (() -> Unit)?,
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
    fun openTorrent(torrentId: String) = push(Route.Torrents(TorrentSection.FILES, torrentId))
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

    // Explorar's Para ti pieces live in :feature:home; Explorar (:feature:search) takes them as slots.
    val forYouPanel: @Composable (onGenre: (String) -> Unit, modifier: Modifier) -> Unit = { onGenre, m ->
        ForYouPanel(onMediaClick = ::openMedia, modifier = m, onGenre = onGenre)
    }
    val forYouPreview: @Composable (onSeeAll: () -> Unit, onGenre: (String) -> Unit, modifier: Modifier) -> Unit = { onSeeAll, onGenre, m ->
        ForYouPreview(::openMedia, onSeeAll, onGenre, m)
    }
    val tasteAtlas: @Composable (modifier: Modifier) -> Unit = { m -> TasteAtlas(::openMedia, m) }

    /**
     * Transferencias (S8) with the torrent client inside it (:feature:downloads cannot see :feature:torrents, so the shell hands
     * the torrent detail and "Añadir torrent" in as slots). [torrentId] opens on that torrent, at [section] when it is a per-torrent one.
     */
    val transfers: @Composable (tab: DownloadsTab, torrentId: String?, section: TorrentSection) -> Unit = { tab, torrentId, section ->
        DownloadsScreen(
            initialTab = tab,
            onBack = if (wide && torrentId == null) null else ::back,
            onPlay = ::play,
            onOpenDetails = ::openDetails,
            onOpenTorrent = ::openTorrent,
            onOpenMangaReader = { args -> push(Route.MangaReader(args)) },
            onOpenNovelReader = { args -> push(Route.NovelReader(args)) },
            torrentSlots = TransfersTorrentSlots(
                detail = { id, onDetailBack, showBack, m ->
                    TransfersTorrentDetail(
                        torrentId = id,
                        onBack = onDetailBack,
                        onPlay = ::play,
                        showBack = showBack,
                        modifier = m,
                        initialSection = if (id == torrentId && section.perTorrent) section else TorrentSection.OVERVIEW,
                    )
                },
                addTorrent = { open, onOpenChange, onAdded -> TransfersAddTorrentHost(open, onOpenChange, onAdded) },
            ),
            initialTorrentId = torrentId,
            onOpenSettings = { openSettingsAt(SettingsSection.TORRENTS) },
            onOpenDebrid = { push(Route.Torrents(TorrentSection.DEBRID)) },
        )
    }

    // Reduce motion: instant screen changes (no slide / fade), predictive back included.
    val reduceMotion = LocalReduceMotion.current
    val instant = remember { ContentTransform(EnterTransition.None, ExitTransition.None) }
    /** Nijiyome Link: the client on Android, the server (with the desktop monitor) on desktop; the screen picks the side. */
    val linkedDevice: @Composable () -> Unit = {
        LinkedDeviceScreen(
            onBack = ::back,
            onPlay = ::play,
            onOpenExtensionServers = if (available.offers(Route.ExtensionServers)) ({ push(Route.ExtensionServers) }) else null,
            onOpenLogs = { openSettingsAt(SettingsSection.ABOUT) },
            onOpenLinkSettings = { openSettingsAt(SettingsSection.LINK) },
            onOpenMonitor = openLinkMonitor,
            onOpenTorrents = { openDownloads() },
        )
    }

    // Sparkles motion "ficha": opening or leaving a Details page uses the card-to-details screen transform; the rest keeps the defaults.
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val baseEnter = remember { defaultTransitionSpec<NavKey>() }
    val basePop = remember { defaultPopTransitionSpec<NavKey>() }
    // MOV-04: where the platform's default screen change is a hard cut (desktop), screens cross-fade at the motion level instead
    // (200 ms in, 140 ms out; calm 150 ms; off none). Platforms with their own default transition keep it.
    val screenFade = motionFadeIn(MotionDurations.STATE) togetherWith motionFadeOut(MotionDurations.STATE)
    fun ContentTransform.orScreenFade(): ContentTransform =
        if (targetContentEnter == EnterTransition.None && initialContentExit == ExitTransition.None) screenFade else this
    // Gives each entry its AnimatedVisibilityScope so the poster and the Details header share bounds.
    val cardToDetailsDecorator = remember {
        NavEntryDecorator<NavKey>({ _ -> }) { entry -> CardToDetailsEntry(LocalNavAnimatedContentScope.current) { entry.Content() } }
    }

    NavDisplay(
        backStack = backStack,
        onBack = ::back,
        sceneStrategies = listOf(listDetail),
        transitionSpec = {
            when {
                reduceMotion -> instant
                targetState.entries.lastOrNull().isCardToDetailsTarget() -> motion.cardToDetailsEnterTransform(density)
                else -> baseEnter().orScreenFade()
            }
        },
        popTransitionSpec = {
            when {
                reduceMotion -> instant
                initialState.entries.lastOrNull().isCardToDetailsTarget() -> motion.cardToDetailsPopTransform(density)
                else -> basePop().orScreenFade()
            }
        },
        predictivePopTransitionSpec = if (reduceMotion) ({ _ -> instant }) else defaultPredictivePopTransitionSpec<NavKey>(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            cardToDetailsDecorator,
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
                    // Sparkles (S2)
                    onOpenDownloads = { openDownloads() },
                    onOpenLink = { push(Route.Linked) },
                    onOpenLinkMonitor = openLinkMonitor,
                    onOpenMetadataSettings = { openSettingsAt(SettingsSection.METADATA) },
                    onOpenExtensions = { push(Route.Extensions) },
                )
            }
            // parity wave B (16 §3.1, worker r): calendar and "Para ti" sub-screens of Home.
            entry<Route.Calendar>(metadata = ListDetailSceneStrategy.listPane()) {
                // 2026-11 UI refresh: a bar / rail destination now (no back arrow).
                CalendarScreen(onBack = null, onMediaClick = ::openMedia)
            }
            entry<Route.ForYou>(metadata = ListDetailSceneStrategy.listPane()) {
                // Sparkles: Para ti is a tab of Explorar (S3); Route.ForYou opens Explorar on it.
                SearchScreen(
                    onMediaClick = { media -> media.ref.anilistId?.let { openDetails(it) } },
                    onOpenImageSearch = { push(Route.ImageSearch()) },
                    onOpenMangaSearch = { q -> push(Route.Manga(q.ifBlank { null })) },
                    onOpenNovelSearch = { q -> push(Route.Novels(q.ifBlank { null })) },
                    initialTab = ExploreTab.FOR_YOU,
                    forYouPanel = forYouPanel,
                    forYouPreview = forYouPreview,
                    tasteAtlas = tasteAtlas,
                    onOpenExtensions = { push(Route.Extensions) },
                )
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
                    forYouPanel = forYouPanel,
                    forYouPreview = forYouPreview,
                    tasteAtlas = tasteAtlas,
                    onOpenExtensions = { push(Route.Extensions) },
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
                    forYouPanel = forYouPanel,
                    forYouPreview = forYouPreview,
                    tasteAtlas = tasteAtlas,
                    onOpenExtensions = { push(Route.Extensions) },
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
                    onOpenDebrid = { push(Route.Torrents(TorrentSection.DEBRID)) },
                    onOpenTransfers = { openDownloads() },
                )
            }
            entry<Route.LibraryTab>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                // Sparkles: the torrents left Library for Transferencias.
                if (key.tab == LibraryTab.TORRENTS && available.offers(Route.Downloads())) {
                    transfers(DownloadsTab.ACTIVE, null, TorrentSection.LIBRARY)
                } else {
                    LibraryScreen(
                        onOpenDetails = ::openDetails,
                        onPlay = ::play,
                        onExplore = { backStack.navigateTop(Route.Home) },
                        initialTab = key.tab,
                        onOpenTorrent = ::openTorrent,
                        onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                        onOpenMangaDetails = ::openMangaDetails,
                        onOpenNovelDetails = ::openNovelDetails,
                        onOpenDebrid = { push(Route.Torrents(TorrentSection.DEBRID)) },
                        onOpenTransfers = { openDownloads() },
                    )
                }
            }
            // MAN-04 / NOV-01: manga and novel Details are never a detail pane. They have their own main + information columns;
            // beside the catalog (or Biblioteca) they became three squeezed columns. They open full width, like the artifact.
            entry<Route.Details>(
                metadata = { key: Route.Details ->
                    if (key.kind == MediaKind.MANGA) CardToDetailsMetadata else ListDetailSceneStrategy.detailPane() + CardToDetailsMetadata
                },
            ) { key ->
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
                    onOpenTorrents = { push(Route.Torrents()) },
                )
            }
            entry<Route.Settings> {
                SettingsScreen(
                    onOpenExtensions = { push(Route.Extensions) },
                    onOpenLink = { push(Route.Linked) },
                    onOpenLinked = { push(Route.Linked) },
                    // Rail destination on wide windows (no back arrow); reached from "Tú" on phones.
                    onBack = if (wide) null else ::back,
                    onOpenDebrid = { push(Route.Torrents(TorrentSection.DEBRID)) },
                    onOpenCalendar = { backStack.navigateTop(Route.Calendar) },
                )
            }
            entry<Route.SettingsSection> { key ->
                SettingsScreen(
                    onOpenExtensions = { push(Route.Extensions) },
                    onOpenLink = { push(Route.Linked) },
                    initialSection = key.section,
                    onOpenLinked = { push(Route.Linked) },
                    onBack = ::back,
                    onOpenDebrid = { push(Route.Torrents(TorrentSection.DEBRID)) },
                    initialItemId = key.itemId,
                    onOpenCalendar = { backStack.navigateTop(Route.Calendar) },
                )
            }
            entry<Route.Extensions> {
                ExtensionsScreen(
                    onBack = if (wide) null else ::back,
                    onOpenProvider = { ext, source -> push(Route.Provider(ext, source)) },
                    // Expose Extension Servers on both Android and Desktop whenever available.
                    onOpenServers = if (available.offers(Route.ExtensionServers)) {
                        { push(Route.ExtensionServers) }
                    } else {
                        null
                    },
                    // Sparkles S9: the Servidores tab embeds :feature:link's content (it needs the extension-server service).
                    serversContent = if (available.offers(Route.ExtensionServers)) {
                        { ExtensionServersContent() }
                    } else {
                        null
                    },
                    onOpenLink = if (available.offers(Route.Linked)) ({ push(Route.Linked) }) else null,
                    onOpenDebrid = { push(Route.Torrents(TorrentSection.DEBRID)) },
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
            entry<Route.Downloads> { key -> transfers(key.tab, null, TorrentSection.LIBRARY) }
            // parity wave B (16 §3.5 / §3.6, worker s): manga and novels.
            entry<Route.Manga>(metadata = ListDetailSceneStrategy.listPane()) { key ->
                MangaHomeScreen(
                    onMediaClick = ::openMedia,
                    onBack = if (wide) null else ::back,
                    onOpenReader = { args -> push(Route.MangaReader(args)) },
                    onOpenAccounts = { openSettings(SettingsSection.ACCOUNTS) },
                    initialQuery = key.query,
                    onOpenNovels = { backStack.navigateTop(Route.Novels()) },
                    // Library on the manga medium (LibraryTab.MANGA selects it).
                    onOpenLibrary = { push(Route.LibraryTab(LibraryTab.MANGA)) },
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
                    onOpenManga = { backStack.navigateTop(Route.Manga()) },
                    // Library on the novel medium (LibraryTab.NOVELS selects it).
                    onOpenLibrary = { push(Route.LibraryTab(LibraryTab.NOVELS)) },
                )
            }
            entry<Route.NovelDetails>(metadata = CardToDetailsMetadata) { key ->
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
            entry<Route.Link> { linkedDevice() }
            entry<Route.Linked> { linkedDevice() }
            entry<Route.ExtensionServers> { ExtensionServersScreen(onBack = ::back) }
            entry<Route.Torrents> { key ->
                // Sparkles: the torrent client is a section of Transferencias. A torrent opens its detail there; the client's lists
                // go to Transferencias; Debrid and the torrent settings keep their own pages until S9 / S10 take them.
                val inTransfers = key.section != TorrentSection.DEBRID && key.section != TorrentSection.SETTINGS &&
                    (key.torrentId != null || !key.section.perTorrent) && available.offers(Route.Downloads())
                if (inTransfers) {
                    val tab = if (key.torrentId == null && key.section == TorrentSection.HISTORY) DownloadsTab.HISTORY else DownloadsTab.ACTIVE
                    transfers(tab, key.torrentId, key.section)
                } else {
                    TorrentsScreen(
                        section = key.section,
                        torrentId = key.torrentId,
                        onNavigate = { section, torrentId -> replaceTop(Route.Torrents(section, torrentId)) },
                        onBack = ::back,
                        onPlay = ::play,
                    )
                }
            }
        },
    )
}

/**
 * Desktop keys: Ctrl+K opens the command palette, Ctrl+, (and Ctrl+P, kept as an alias) Settings, Ctrl+B switches the rail style;
 * 1..9 open the first nine visible destinations ([destinations], rail order) and Esc goes back (the screen's own handler first)
 * outside the player and the readers, which keep their own keys.
 */
@Composable
private fun ShellShortcutHandler(
    shortcuts: ShellShortcuts,
    backStack: NavBackStack<NavKey>,
    destinations: List<Route>,
    onPlayer: Boolean,
    onOpenPalette: () -> Unit,
    onOpenSettings: (SettingsSection?) -> Unit,
) {
    val currentDestinations by rememberUpdatedState(destinations)
    val currentOnPlayer by rememberUpdatedState(onPlayer)
    val currentPalette by rememberUpdatedState(onOpenPalette)
    val currentSettings by rememberUpdatedState(onOpenSettings)
    DisposableEffect(shortcuts, backStack) {
        shortcuts.openPaletteHandler = { currentPalette() }
        shortcuts.openSettingsHandler = { sec -> currentSettings(sec) }
        shortcuts.goBackHandler = {
            if (!shortcuts.back() && backStack.size > 1) backStack.removeLastOrNull()
        }
        shortcuts.handler = { shortcut ->
            when (shortcut) {
                ShellShortcut.FocusSearch -> {
                    currentPalette()
                    true
                }
                ShellShortcut.OpenSettings -> {
                    if (currentOnPlayer) false else {
                        currentSettings(null)
                        true
                    }
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

/** Marks the entries a card opens (Details, NovelDetails): their screen transition is the card-to-details one. */
private const val CARD_TO_DETAILS_KEY = "nijiyome.cardToDetails"
private val CardToDetailsMetadata: Map<String, Any> = mapOf(CARD_TO_DETAILS_KEY to true)

private fun NavEntry<*>?.isCardToDetailsTarget(): Boolean = this?.metadata?.containsKey(CARD_TO_DETAILS_KEY) == true

/** Switching tabs resets the stack to `[Home]` or `[Home, tab]` so Back from a tab returns to Home. */
private fun NavBackStack<NavKey>.navigateTop(route: Route) {
    val target = if (route == Route.Home) listOf<NavKey>(Route.Home) else listOf(Route.Home, route)
    if (toList() == target) return
    clear()
    addAll(target)
}

private const val WIDE_MIN_WIDTH_DP = 600

/** BUG-041 / BUG-045: below this height (a phone in landscape) the rail does not fit and two panes are too cramped. */
private const val RAIL_MIN_HEIGHT_DP = 480

/** BUG-045: list + detail side by side from the expanded width class. */
private const val TWO_PANE_MIN_WIDTH_DP = 840
private val OFFLINE_BANNER_HEIGHT = 40.dp
