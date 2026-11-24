// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi::class)

package app.nijiyome.desktop

import androidx.compose.material3.LocalBasicAlertDialogOverride
import androidx.compose.runtime.CompositionLocalProvider
import app.nijiyome.designsystem.components.LocalModalTracker
import app.nijiyome.designsystem.components.ModalTracker
import app.nijiyome.designsystem.components.TrackedAlertDialogOverride
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.nijiyome.data.DesktopAppReset
import app.nijiyome.data.PROP_ANILIST_CLIENT_ID
import app.nijiyome.data.PROP_MAL_CLIENT_ID
import app.nijiyome.data.PendingOAuthRedirect
import app.nijiyome.desktop.system.AppLanguageProvider
import app.nijiyome.desktop.system.AppRestarter
import app.nijiyome.desktop.system.DesktopLocale
import app.nijiyome.desktop.system.BuildInfo
import app.nijiyome.desktop.system.DesktopSystem
import app.nijiyome.desktop.system.DiscordPresence
import app.nijiyome.desktop.system.InstanceChannel
import app.nijiyome.desktop.system.LaunchArgs
import app.nijiyome.desktop.system.LaunchRouter
import app.nijiyome.desktop.system.LinuxUiScale
import app.nijiyome.desktop.system.ScaledDensity
import app.nijiyome.extensionserver.PendingServerLink
import app.nijiyome.desktop.system.NijiyomeTray
import app.nijiyome.desktop.system.TrayTexts
import app.nijiyome.desktop.system.connectDiscord
import app.nijiyome.desktop.system.desktopSystemModule
import app.nijiyome.desktop.system.discordEndpoints
import app.nijiyome.desktop.system.runPresence
import app.nijiyome.desktop.system.trayIconFiles
import app.nijiyome.desktop.system.windowTitle
import app.nijiyome.feature.settings.DesktopAppProperties
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.MediaRef
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.SettingsRepository
import app.nijiyome.nav.NijiyomeApp
import app.nijiyome.nav.Route
import app.nijiyome.ui.platform.AppWindow
import app.nijiyome.social.DesktopSocialPoller
import app.nijiyome.nav.ShellShortcuts
import androidx.compose.ui.platform.InterceptPlatformTextInput
import app.nijiyome.ui.platform.DesktopBackDispatcher
import app.nijiyome.ui.platform.LocalAppWindow
import app.nijiyome.ui.platform.LocalDesktopBackDispatcher
import app.nijiyome.ui.platform.LocalViewerKeyRelay
import app.nijiyome.ui.platform.ViewerKeyRelay
import app.nijiyome.ui.platform.PIP_MIN_SIZE
import app.nijiyome.ui.state.rememberIsOnline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import app.nijiyome.server.desktop.NetworkFile
import app.nijiyome.network.outbound
import org.koin.core.context.stopKoin
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.Locale
import javax.swing.JOptionPane
import kotlin.system.exitProcess

// Smallest window the screens are laid out for.
private const val MIN_WINDOW_WIDTH = 720
private const val MIN_WINDOW_HEIGHT = 540

fun main(args: Array<String>) {
    // `--version`: the packaged version, before any window, data directory or lock exists (packaging smoke checks).
    if (args.any { it == "--version" || it == "-V" }) {
        println("nijiyome ${BuildInfo.load().version.ifEmpty { "dev" }}")
        return
    }
    // Before anything else: libtorrent4j needs the JVM's signal chaining (Linux; see SignalChaining).
    SignalChaining.relaunchIfNeeded(args)
    val paths = AppPaths.resolve()
    paths.prepare()?.let { problem -> fatal(Startup.dataDir(problem), exitCode = 1) }
    val launch = LaunchArgs.parse(args.toList())
    // One app per data directory: a second launch hands its arguments to the running one (which shows its window) and leaves.
    val lock = InstanceLock.acquire(paths.dataDir) ?: run {
        if (InstanceChannel.send(paths.dataDir, args.toList())) exitProcess(0)
        fatal(Startup.alreadyRunning(), exitCode = 2)
    }
    // A reset that could not delete everything (files locked on Windows) finishes before anything opens them.
    DesktopAppReset.finishPending(paths.dataDir)
    val build = BuildInfo.load()
    val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    val restarter = AppRestarter()
    val folders = UserFolders.resolve(paths)
    // Settings › Red: the outbound proxy and FlareSolverr apply before the first client exists (no request leaves directly first).
    NetworkFile.applyAtStart(paths.dataDir)
    // Images (Coil) through a client with the outbound proxy's HTTP credentials too (the JVM selector already gives it the proxy).
    coil3.SingletonImageLoader.setSafe { ctx ->
        coil3.ImageLoader.Builder(ctx)
            .components { add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { okhttp3.OkHttpClient.Builder().outbound().build() })) }
            .build()
    }
    startKoin {
        properties(
            buildMap {
                put(DesktopAppProperties.VERSION, build.version)
                put(DesktopAppProperties.DATA_DIR, paths.dataDir.absolutePath)
                build.anilistClientId?.let { put(PROP_ANILIST_CLIENT_ID, it) }
                build.malClientId?.let { put(PROP_MAL_CLIENT_ID, it) }
            },
        )
        modules(desktopModules(paths, folders, build.version) + desktopSystemModule(paths.dataDir, build, windows, restarter))
    }
    val koin = GlobalContext.get()
    val settingsRepo = koin.get<SettingsRepository>()
    // Ajustes › Idioma before the first window, so the tray menu and the first frame are in it too.
    DesktopLocale.apply(settingsRepo.settings.value.language)
    val linkServer = koin.getOrNull<LinkServerController>()
    val social = koin.getOrNull<DesktopSocialPoller>()
    val system = koin.get<DesktopSystem>()
    system.reconcileAutostart()
    val router = LaunchRouter(TorrentLinkEntry(koin.get()), koin.getOrNull<PendingOAuthRedirect>(), koin.getOrNull<PendingServerLink>())
    // A login that came back through the loopback listener opens Settings › Listas y cuentas, as Android's redirect does.
    val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    koin.getOrNull<PendingOAuthRedirect>()?.link?.filterNotNull()?.onEach { router.accountLinks.value = true }?.launchIn(background)
    val presence = build.discordAppId?.let { id -> DiscordPresence(id, connect = { connectDiscord(discordEndpoints(windows), windows) }) }
    background.runPresence(system, presence)
    // `nijiyome <magnet | file.torrent | nijiyome://… | video file>` (and the same forwarded by a later launch).
    router.open(launch.target)
    // v1.1 (F2): the desktop's scale (Xft.dpi) that AWT ignores on Linux, applied to Compose's density (LinuxUiScale).
    val uiScale = LinuxUiScale.factor
    var showWindow: () -> Unit = {}
    val channel = InstanceChannel.listen(paths.dataDir) { forwarded ->
        EventQueue.invokeLater {
            router.open(LaunchArgs.parse(forwarded).target)
            showWindow()
        }
    }
    try {
        application(exitProcessOnExit = false) {
            restarter.exit = ::exitApplication
            val prefs by system.prefs.collectAsState()
            val appSettings by settingsRepo.settings.collectAsState()
            val trayAvailable by system.trayAvailable.collectAsState()
            val online = rememberIsOnline()
            val link = linkServer?.state?.collectAsState()?.value
            val linkPort = link?.takeIf { it.running }?.port
            val trayOn = prefs.trayIcon && trayAvailable
            // Started by the autostart entry with "Iniciar minimizado": only the tray icon, until Show.
            var visible by remember { mutableStateOf(!(launch.minimized && prefs.startMinimized && trayOn)) }
            val windowState = rememberWindowState(size = DpSize(1280.dp * uiScale, 800.dp * uiScale))
            val appWindow = remember(windowState) { AppWindow(windowState) }
            // Esc runs the screens' back handlers (a panel, a sheet) before the shell goes back; Ctrl+K, Ctrl+B and 1..9 as Tatakai Next.
            val backDispatcher = remember { DesktopBackDispatcher() }
            val viewerKeys = remember { ViewerKeyRelay() }
            // v1.1: the dialogs and modal sheets open right now; while one is, the app-wide keys stay off.
            val modals = remember { ModalTracker() }
            val shortcuts = remember(backDispatcher) {
                ShellShortcuts(platformBack = backDispatcher::dispatch, viewerEscape = viewerKeys::escape, modalOpen = { modals.isOpen })
            }
            val icon = remember { appIcon() }
            val show = {
                visible = true
                windowState.isMinimized = false
            }
            showWindow = show
            val trayIcons = remember { trayIconFiles(File(paths.cacheDir, "tray")) }
            // The tray menu in the chosen language from the start and as soon as it changes (its texts are a parameter, so a new
            // language recomposes the menu; they never depend on when the default locale was last set).
            DesktopLocale.apply(appSettings.language)
            val trayTexts = TrayTexts.forLocale(DesktopLocale.localeFor(appSettings.language))
            if (trayOn && trayIcons != null) NijiyomeTray(trayIcons, trayTexts, onShow = show, onQuit = ::exitApplication)
            // No tray any more (turned off, or it went away) while the window is hidden: show it, or the app could not be reached.
            LaunchedEffect(trayOn) { if (!trayOn) visible = true }
            Window(
                onCloseRequest = { if (trayOn && prefs.closeToTray) visible = false else exitApplication() },
                // The language is applied above, before the tray and the title, so the "Sin conexión" chip follows it too.
                title = windowTitle(online, linkPort),
                icon = icon,
                state = windowState,
                visible = visible,
                // The player's picture in picture: the window itself, small and above the others.
                alwaysOnTop = appWindow.pictureInPicture,
                // Only the key presses no screen used: a text field keeps its digits, the player and the readers their keys.
                onKeyEvent = shortcuts::onKeyEvent,
            ) {
                // A comfortable minimum. The shell keeps the rail at any size on desktop (it has no "Tú" page yet). Picture in
                // picture allows the small window of Tatakai Next.
                window.minimumSize = if (appWindow.pictureInPicture) {
                    Dimension((PIP_MIN_SIZE.width.value * uiScale).toInt(), (PIP_MIN_SIZE.height.value * uiScale).toInt())
                } else {
                    Dimension((MIN_WINDOW_WIDTH * uiScale).toInt(), (MIN_WINDOW_HEIGHT * uiScale).toInt())
                }
                LaunchedEffect(visible) {
                    if (visible) {
                        if (window.extendedState and Frame.ICONIFIED != 0) window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                        window.toFront()
                        window.requestFocus()
                    }
                }
                // Back to the front: the AniList unread count is asked again (at most once a minute), as Tatakai Next's rail badge.
                DisposableEffect(window, social) {
                    val listener = object : WindowAdapter() {
                        override fun windowGainedFocus(e: WindowEvent?) {
                            social?.refreshNow()
                        }
                    }
                    window.addWindowFocusListener(listener)
                    onDispose { window.removeWindowFocusListener(listener) }
                }
                // Nijiyome Link: a paired phone's "Continuar en la PC" opens the player through the same route links.
                LinkRemoteOpens(router.routeLinks)
                // The shell shared with Android: theme, navigation and every screen, in the language of Ajustes › Idioma (changing
                // it there or in the wizard redraws the texts in place).
                ScaledDensity(uiScale) {
                AppLanguageProvider(appSettings.language) {
                    CompositionLocalProvider(
                    LocalAppWindow provides appWindow,
                    LocalDesktopBackDispatcher provides backDispatcher,
                    LocalViewerKeyRelay provides viewerKeys,
                    LocalModalTracker provides modals,
                    LocalBasicAlertDialogOverride provides TrackedAlertDialogOverride(modals),
                ) {
                        // Every text field's input session goes through here: while one is open, the digits are text, not shortcuts.
                        InterceptPlatformTextInput(
                            interceptor = { request, next ->
                                shortcuts.textInputStarted()
                                try {
                                    next.startInputMethod(request)
                                } finally {
                                    shortcuts.textInputEnded()
                                }
                            },
                        ) {
                            NijiyomeApp(
                                torrentLinks = router.torrentLinks,
                                serverLinks = router.serverLinks,
                                accountLinks = router.accountLinks,
                                routeLinks = router.routeLinks,
                                shortcuts = shortcuts,
                            )
                        }
                    }
                }
                }
            }
        }
    } finally {
        // v1.1 review #5: leave the session bus (MPRIS) before the rest closes.
        app.nijiyome.feature.player.mpris.DesktopSystemMediaControls.shutdown()
        channel?.close()
        presence?.close()
        background.cancel()
        // Closes the database, the HTTP cache, the torrent session and the background scope (the modules' onClose) before leaving.
        stopKoin()
        lock.release()
    }
    if (restarter.requested) {
        // After "Restablecer la app": the same command again, without the link or file this run was started with.
        AppRestarter.ownCommand()?.let(AppRestarter::restartCommand)?.let(AppRestarter::relaunch)
    }
    exitWithWatchdog(0)
}

/**
 * Leaves the process. Everything that keeps data (database, settings, torrent session) is closed already; a library shutdown hook
 * that never returns must not keep a closed app alive (it would hold the window's process and, after a restart, its AppImage
 * mount), so after 8 s the threads are written to stderr and after 12 s the process is halted.
 */
private fun exitWithWatchdog(code: Int): Nothing {
    Thread({
        Thread.sleep(8_000)
        System.err.println("exit: still running 8 s after exit; threads:")
        Thread.getAllStackTraces().forEach { (thread, stack) ->
            System.err.println("\"${thread.name}\" ${thread.state}")
            stack.take(14).forEach { System.err.println("    at $it") }
        }
    }, "exit-report").apply { isDaemon = true }.start()
    Thread({
        Thread.sleep(12_000)
        Runtime.getRuntime().halt(code)
    }, "exit-watchdog").apply { isDaemon = true }.start()
    exitProcess(code)
}

/** The player route of a local video file, or null when [path] is not a file. Nothing is looked up online for it. */
internal fun localFileRoute(path: String): Route? {
    val file = File(path).takeIf { it.isFile } ?: return null
    return Route.Player(PlayerArgs(media = MediaRef(), title = file.nameWithoutExtension, episode = 1, localUri = file.absolutePath, looseFile = true))
}

/** Startup messages shown before any shared resource can be loaded: English, or Spanish on a Spanish system. */
private object Startup {
    private val spanish get() = Locale.getDefault().language == "es"
    fun dataDir(problem: String) =
        if (spanish) "Nijiyome no puede usar su carpeta de datos: $problem" else "Nijiyome cannot use its data folder: $problem"
    fun alreadyRunning() = if (spanish) "Nijiyome ya está abierto." else "Nijiyome is already running."
}

private fun fatal(message: String, exitCode: Int): Nothing {
    System.err.println(message)
    if (!GraphicsEnvironment.isHeadless()) {
        runCatching { JOptionPane.showMessageDialog(null, message, "Nijiyome", JOptionPane.ERROR_MESSAGE) }
    }
    exitProcess(exitCode)
}
