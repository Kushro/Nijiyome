// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import app.nijiyome.data.PendingOAuthRedirect
import app.nijiyome.desktop.TorrentLinkEntry
import app.nijiyome.extensionserver.PendingServerLink
import app.nijiyome.desktop.localFileRoute
import app.nijiyome.feature.settings.DesktopAppRestart
import app.nijiyome.model.NowPlaying
import app.nijiyome.model.NowPlayingInfo
import app.nijiyome.model.NowViewing
import app.nijiyome.nav.Route
import com.kdroid.composetray.tray.api.Tray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Where launch targets go in the running app (this launch's arguments and those a later launch forwarded): torrents to W1's
 * [TorrentLinkEntry], OAuth redirects to [PendingOAuthRedirect] (Settings › Listas y cuentas opens), extension server pairing
 * links to [PendingServerLink] (the servers screen opens and asks), profiles and video files to the shell as a ready route. The
 * same destinations as Android's MainActivity.
 */
class LaunchRouter(
    private val torrents: TorrentLinkEntry,
    private val oauth: PendingOAuthRedirect?,
    /** Where an extension server pairing link waits for its screen (null = no extension server client in the graph). */
    private val serverLink: PendingServerLink? = null,
    val accountLinks: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val routeLinks: MutableStateFlow<Route?> = MutableStateFlow(null),
    /** The shell opens the extension servers screen when this turns true (Android's `serverLinks`). */
    val serverLinks: MutableStateFlow<Boolean> = MutableStateFlow(false),
) {
    /** The torrent library opened by a torrent link (the shell's `torrentLinks`). */
    val torrentLinks get() = torrents.links

    fun open(target: LaunchTarget?) {
        when (target) {
            null -> Unit
            is LaunchTarget.Torrent -> torrents.open(target.raw)
            is LaunchTarget.OAuth -> oauth?.let {
                it.offer(target.uri)
                accountLinks.value = true
            }
            is LaunchTarget.Profile -> routeLinks.value = Route.Profile(target.name)
            is LaunchTarget.VideoFile -> localFileRoute(target.path)?.let { routeLinks.value = it }
            is LaunchTarget.ExtensionServer -> serverLink?.let {
                it.offer(target.uri)
                serverLinks.value = true
            }
        }
    }
}

/** "Restablecer la app" asks for a restart: the window closes, and [main] starts the app again once everything is closed. */
class AppRestarter : DesktopAppRestart {
    @Volatile
    var requested: Boolean = false
        private set

    @Volatile
    var exit: () -> Unit = {}

    override fun restart() {
        requested = true
        exit()
    }

    companion object {
        /**
         * How to start this app again: the AppImage file (`$APPIMAGE`; the running mount goes away with this process), the jpackage
         * launcher, or else this process's command line (`/proc/self/cmdline` on Linux, the process info elsewhere; a development run).
         */
        fun ownCommand(
            env: (String) -> String? = System::getenv,
            appPath: String? = System.getProperty("jpackage.app-path"),
            cmdline: () -> ByteArray? = { runCatching { File("/proc/self/cmdline").readBytes() }.getOrNull() },
        ): List<String>? {
            env("APPIMAGE")?.takeIf { it.isNotBlank() && File(it).isFile }?.let { return listOf(it) }
            appPath?.takeIf { it.isNotBlank() && File(it).isFile }?.let { return listOf(it) }
            val raw = cmdline()
            val parts = if (raw != null) {
                String(raw, Charsets.UTF_8).split('\u0000').dropLastWhile { it.isEmpty() }
            } else {
                val info = ProcessHandle.current().info()
                val cmd = info.command().orElse(null) ?: return null
                listOf(cmd) + info.arguments().orElse(emptyArray()).toList()
            }
            return parts.takeIf { it.isNotEmpty() }
        }

        /** [command] without the link, file or `--minimized` this run was started with (the executable itself always stays). */
        fun restartCommand(command: List<String>): List<String>? {
            val exe = command.firstOrNull() ?: return null
            return listOf(exe) + command.drop(1).filter { arg -> LaunchArgs.targetOf(arg) == null && arg != LaunchArgs.MINIMIZED }
        }

        /** The environment of a fresh launch: what this run's launcher, AppImage mount and signal chaining set is left out. */
        fun freshEnvironment(env: MutableMap<String, String>) {
            // The jpackage launcher's second stage (see SignalChaining), the relaunch marker, this mount's paths.
            listOf("_JPACKAGE_LAUNCHER", "NIJIYOME_JSIG", "APPDIR", "APPIMAGE", "ARGV0", "OWD").forEach(env::remove)
            // libjsig from this run's runtime (an AppImage mount that goes away with it); the new run preloads its own.
            env["LD_PRELOAD"]?.let { value ->
                val rest = value.split(' ', ':').filter { it.isNotBlank() && !it.contains("libjsig") }
                if (rest.isEmpty()) env.remove("LD_PRELOAD") else env["LD_PRELOAD"] = rest.joinToString(" ")
            }
        }

        /** Starts the app again (detached; it takes the instance lock once this process is gone). */
        fun relaunch(command: List<String>) {
            runCatching {
                ProcessBuilder(command).inheritIO().apply { freshEnvironment(environment()) }.start()
            }.onFailure { System.err.println("restart: ${it.message}") }
        }
    }
}

/**
 * The native title bar's text (Tatakai Next's title bar chips, Hito 6 D1): "Nijiyome", then "Link :<port>" while the Link server
 * runs and "Sin conexión" while there is no network. Pure.
 */
fun windowTitle(online: Boolean, linkPort: Int?, spanish: Boolean = Locale.getDefault().language == "es"): String = buildList {
    add("Nijiyome")
    if (linkPort != null && linkPort > 0) add("Link :$linkPort")
    if (!online) add(if (spanish) "Sin conexión" else "Offline")
}.joinToString(" · ")

/** Tray menu texts (English, or Spanish on a Spanish system). */
data class TrayTexts(val show: String, val quit: String) {
    companion object {
        /** The menu in [locale]: the language of Ajustes › Idioma (`DesktopLocale.localeFor`), not whatever the default is now. */
        fun forLocale(locale: Locale): TrayTexts =
            if (locale.language == "es") TrayTexts("Mostrar Nijiyome", "Salir") else TrayTexts("Show Nijiyome", "Quit")
    }
}

/** The tray icon files (PNG for Linux, ICO for Windows), copied once from the app's resources to [dir]. Null when missing. */
fun trayIconFiles(dir: File): Pair<File, File>? = runCatching {
    dir.mkdirs()
    fun copy(resource: String, name: String): File {
        val target = File(dir, name)
        if (!target.isFile) {
            val bytes = AppRestarter::class.java.getResourceAsStream(resource)?.use { it.readBytes() } ?: error("missing $resource")
            target.writeBytes(bytes)
        }
        return target
    }
    copy("/icons/nijiyome.png", "nijiyome.png") to copy("/icons/nijiyome.ico", "nijiyome.ico")
}.getOrNull()

/**
 * Quantizes volatile telemetry (JVM RAM, cache sizes, torrent speeds/progress) into coarse buckets so ComposeNativeTray's
 * D-Bus menu (`libdbusmenu` / KDE Plasma) is not torn down and rebuilt every few seconds while the user hovers a submenu.
 */
internal fun TrayLiveStatus.quantizedForTray(): TrayLiveStatus {
    fun bucket(bytes: Long, stepBytes: Long): Long = if (bytes <= 0L) 0L else ((bytes + stepBytes / 2) / stepBytes) * stepBytes
    val memStep = 32L * 1024L * 1024L // 32 MiB buckets
    val speedStep = 512L * 1024L // 512 KiB/s buckets
    val linkQuant = link?.let { l ->
        l.copy(
            totalCacheBytes = bucket(l.totalCacheBytes, memStep),
            activeSessions = l.activeSessions.map { s ->
                s.copy(
                    positionSecs = 0.0,
                    cachedBytes = bucket(s.cachedBytes, memStep),
                    cachedSegments = 0,
                    cachedFromSecs = null,
                    cachedToSecs = null,
                )
            },
        )
    }
    val torQuant = activeTorrents.map { t ->
        t.copy(
            progress = ((t.progress * 20.0).toInt() / 20.0).coerceIn(0.0, 1.0), // 5% steps
            downBps = bucket(t.downBps, speedStep),
            upBps = bucket(t.upBps, speedStep),
            peers = 0,
        )
    }
    return copy(
        link = linkQuant,
        activeTorrents = torQuant,
        totalDownBps = bucket(totalDownBps, speedStep),
        totalUpBps = bucket(totalUpBps, speedStep),
        jvmUsedBytes = bucket(jvmUsedBytes, memStep),
        diskCacheBytes = bucket(diskCacheBytes, memStep),
        torrentStorageBytes = bucket(torrentStorageBytes, memStep),
        torrentFreeBytes = 0L,
    )
}

/**
 * The tray icon (ComposeNativeTray: StatusNotifierItem over D-Bus on Linux, the notification area on Windows) with Show and Quit;
 * a click on the icon shows the window too. The icon goes as image files: the library's Compose-rendered icons call a Skia API
 * that the Compose version of this app no longer has.
 */
@Composable
fun ApplicationScope.NijiyomeTray(
    icons: Pair<File, File>,
    texts: TrayTexts,
    live: TrayLiveStatus = TrayLiveStatus(),
    onMenuOpened: () -> Unit = {},
    onShow: () -> Unit,
    onOpenMonitor: () -> Unit = onShow,
    onOpenTorrents: () -> Unit = onShow,
    onOpenSettings: () -> Unit = onShow,
    onQuit: () -> Unit,
) {
    val stableShow by rememberUpdatedState(onShow)
    val stableMonitor by rememberUpdatedState(onOpenMonitor)
    val stableTorrents by rememberUpdatedState(onOpenTorrents)
    val stableSettings by rememberUpdatedState(onOpenSettings)
    val stableQuit by rememberUpdatedState(onQuit)
    val stableMenuOpened by rememberUpdatedState(onMenuOpened)

    // Keep lambda references stable across unrelated recompositions so ComposeNativeTray does not call
    // nativeResetMenu() on D-Bus (which collapses/flickers hovered submenus on KDE Plasma).
    val primaryAction = remember { { stableShow() } }
    val menuOpenedAction = remember { { stableMenuOpened() } }

    val es = live.spanish
    val link = live.link
    val activeSessions = link?.activeSessions.orEmpty()
    val tooltip = remember(live, texts) {
        when {
            activeSessions.isNotEmpty() -> {
                val s = activeSessions.first()
                val pipe = listOfNotNull(s.upscaleLabel, s.frameGenLabel).joinToString(" + ").ifBlank { s.encoder }
                "Nijiyome · Link: ${s.deviceName} ($pipe)"
            }
            live.totalDownBps > 0 -> "Nijiyome · ↓ ${app.nijiyome.ui.format.formatRate(live.totalDownBps)}"
            link?.running == true -> "Nijiyome · Link :${link.port}"
            else -> "Nijiyome"
        }
    }
    val menuContent: TrayMenuBuilder.() -> Unit = remember(live, texts) {
        {
            // 1. Live Link status section
            val linkHeader = when {
                activeSessions.isNotEmpty() -> {
                    val s = activeSessions.first()
                    val title = s.title?.take(28)?.let { if ((s.title?.length ?: 0) > 28) "$it…" else it }
                        ?: if (es) "Reproduciendo" else "Streaming"
                    val pipe = listOfNotNull(s.upscaleLabel?.substringBefore(" ·"), s.frameGenLabel).joinToString(" + ")
                    val suffix = if (pipe.isNotBlank()) " · $pipe" else ""
                    if (es) "Link activo: ${s.deviceName} — $title$suffix"
                    else "Link active: ${s.deviceName} — $title$suffix"
                }
                link?.running == true -> {
                    val devs = link.devices.size
                    if (es) "Link :${link.port} · En espera ($devs disp.)"
                    else "Link :${link.port} · Idle ($devs devices)"
                }
                else -> if (es) "Link · Desactivado" else "Link · Stopped"
            }
            Item(linkHeader) { stableMonitor() }

            if (activeSessions.size > 1) {
                SubMenu(if (es) "Sesiones Link (${activeSessions.size})" else "Link Sessions (${activeSessions.size})") {
                    activeSessions.forEach { s ->
                        val title = s.title?.take(24) ?: s.sourceKind
                        val pipe = listOfNotNull(s.upscaleLabel?.substringBefore(" ·"), s.frameGenLabel).joinToString(" + ")
                        Item("${s.deviceName}: $title ${if (pipe.isNotBlank()) "($pipe)" else ""}".trim()) { stableMonitor() }
                    }
                }
            }

            // 2. Active torrents & speeds
            val torrentLine = if (live.activeTorrents.isNotEmpty()) {
                val n = live.activeTorrents.size
                val down = app.nijiyome.ui.format.formatRate(live.totalDownBps)
                val up = app.nijiyome.ui.format.formatRate(live.totalUpBps)
                "Torrents ($n): ↓ $down · ↑ $up"
            } else {
                if (es) "Torrents: sin descargas activas" else "Torrents: idle"
            }
            if (live.activeTorrents.isNotEmpty()) {
                SubMenu(torrentLine) {
                    live.activeTorrents.take(6).forEach { t ->
                        val shortName = if (t.name.length > 34) t.name.take(33) + "…" else t.name
                        val pct = (t.progress * 100).toInt()
                        Item("$shortName · $pct% · ↓ ${app.nijiyome.ui.format.formatRate(t.downBps)}") { stableTorrents() }
                    }
                    Divider()
                    Item(if (es) "Abrir biblioteca de Torrents" else "Open Torrents") { stableTorrents() }
                }
            } else {
                Item(torrentLine) { stableTorrents() }
            }

            // 3. Performance, RAM & Cache telemetry submenu
            SubMenu(if (es) "Rendimiento y caché" else "Performance & Cache") {
                val ramUsed = app.nijiyome.ui.format.formatBytes(live.jvmUsedBytes)
                val ramMax = app.nijiyome.ui.format.formatBytes(live.jvmMaxBytes)
                Item(if (es) "RAM app: ~$ramUsed / $ramMax" else "App RAM: ~$ramUsed / $ramMax", isEnabled = false)
                val linkCache = app.nijiyome.ui.format.formatBytes(link?.totalCacheBytes ?: 0L)
                val linkLimit = "${link?.cacheLimitMb ?: 1024} MB"
                Item(if (es) "Caché Link (RAM): $linkCache / $linkLimit" else "Link cache (RAM): $linkCache / $linkLimit", isEnabled = false)
                val disk = app.nijiyome.ui.format.formatBytes(live.diskCacheBytes)
                val torDisk = app.nijiyome.ui.format.formatBytes(live.torrentStorageBytes)
                Item(if (es) "Caché disco: $disk · Torrents: $torDisk" else "Disk cache: $disk · Torrents: $torDisk", isEnabled = false)
                Divider()
                Item(if (es) "Abrir Monitor de Link y sistema" else "Open Link & System Monitor") { stableMonitor() }
            }

            Divider()
            Item(texts.show) { stableShow() }
            Item(if (es) "Monitor de Link y estado" else "Link & Status Monitor") { stableMonitor() }
            Item(if (es) "Ajustes" else "Settings") { stableSettings() }
            Divider()
            Item(texts.quit) { stableQuit() }
        }
    }
    Tray(
        iconPath = icons.first.absolutePath,
        windowsIconPath = icons.second.absolutePath,
        tooltip = tooltip,
        primaryAction = primaryAction,
        onMenuOpened = menuOpenedAction,
        menuContent = menuContent,
    )
}

/**
 * Feeds Discord Rich Presence: the episode the player shows, the title of the Details page on screen ("Viendo la ficha de…"), or
 * "browsing" while the app is open; nothing when the setting is off or no Discord application is configured.
 */
fun CoroutineScope.runPresence(system: DesktopSystem, presence: DiscordPresence?) {
    if (presence == null) return
    launch {
        combine(system.prefs, NowPlaying.current, NowViewing.title) { prefs, now, viewing ->
            presenceFor(prefs.discordPresence, now, viewing)
        }.distinctUntilChanged().collect { presence.update(it) }
    }
}

/** The presence for the setting, the episode playing and the Details page on screen; the player wins over the page under it. */
fun presenceFor(enabled: Boolean, now: NowPlayingInfo?, viewing: String?): PresenceState = when {
    !enabled -> PresenceState.Clear
    now != null -> PresenceState.Watching(now)
    !viewing.isNullOrBlank() -> PresenceState.Viewing(viewing)
    else -> PresenceState.Browsing
}
