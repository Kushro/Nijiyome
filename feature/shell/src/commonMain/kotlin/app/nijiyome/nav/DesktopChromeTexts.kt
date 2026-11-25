// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.rd_s1_tb_back
import app.nijiyome.feature.shell.resources.rd_s1_tb_close
import app.nijiyome.feature.shell.resources.rd_s1_tb_link_active
import app.nijiyome.feature.shell.resources.rd_s1_tb_maximize
import app.nijiyome.feature.shell.resources.rd_s1_tb_minimize
import app.nijiyome.feature.shell.resources.rd_s1_tb_offline
import app.nijiyome.feature.shell.resources.rd_s1_tb_restore
import app.nijiyome.feature.shell.resources.rd_s1_tb_settings
import app.nijiyome.feature.shell.resources.rd_s1_tray_app_ram
import app.nijiyome.feature.shell.resources.rd_s1_tray_disk_cache
import app.nijiyome.feature.shell.resources.rd_s1_tray_link_active
import app.nijiyome.feature.shell.resources.rd_s1_tray_link_cache
import app.nijiyome.feature.shell.resources.rd_s1_tray_link_idle
import app.nijiyome.feature.shell.resources.rd_s1_tray_link_sessions
import app.nijiyome.feature.shell.resources.rd_s1_tray_link_stopped
import app.nijiyome.feature.shell.resources.rd_s1_tray_monitor
import app.nijiyome.feature.shell.resources.rd_s1_tray_open_monitor
import app.nijiyome.feature.shell.resources.rd_s1_tray_open_transfers
import app.nijiyome.feature.shell.resources.rd_s1_tray_performance
import app.nijiyome.feature.shell.resources.rd_s1_tray_quit
import app.nijiyome.feature.shell.resources.rd_s1_tray_settings
import app.nijiyome.feature.shell.resources.rd_s1_tray_show
import app.nijiyome.feature.shell.resources.rd_s1_tray_streaming
import app.nijiyome.feature.shell.resources.rd_s1_tray_torrents_active
import app.nijiyome.feature.shell.resources.rd_s1_tray_torrents_idle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

// The desktop window's own texts (title bar, tray) live in this module's resources: the desktop app module has no Compose
// resources of its own and this module's generated `Res` is internal.

/** Labels of the desktop title bar, in the app language. */
@Immutable
data class TitleBarTexts(
    val back: String,
    val settings: String,
    val minimize: String,
    val maximize: String,
    val restore: String,
    val close: String,
    val offline: String,
    val linkActive: String,
)

@Composable
fun rememberTitleBarTexts(): TitleBarTexts = TitleBarTexts(
    back = stringResource(Res.string.rd_s1_tb_back),
    settings = stringResource(Res.string.rd_s1_tb_settings),
    minimize = stringResource(Res.string.rd_s1_tb_minimize),
    maximize = stringResource(Res.string.rd_s1_tb_maximize),
    restore = stringResource(Res.string.rd_s1_tb_restore),
    close = stringResource(Res.string.rd_s1_tb_close),
    offline = stringResource(Res.string.rd_s1_tb_offline),
    linkActive = stringResource(Res.string.rd_s1_tb_link_active),
)

/**
 * The tray menu's texts. The tray is composed outside the window (no locale-aware composition there), so they are read once per
 * language with [loadTrayStrings] after the process locale is set. Templates keep `%1$s` placeholders for [String.format].
 */
@Immutable
data class TrayStrings(
    val show: String = "Show Nijiyome",
    val quit: String = "Quit",
    val streaming: String = "Streaming",
    val linkActive: String = "Link active: %1\$s · %2\$s%3\$s",
    val linkIdle: String = "Link :%1\$s · Waiting (%2\$s devices)",
    val linkStopped: String = "Link · Stopped",
    val linkSessions: String = "Link sessions (%1\$s)",
    val torrentsActive: String = "Torrents (%1\$s): ↓ %2\$s · ↑ %3\$s",
    val torrentsIdle: String = "Torrents: no active downloads",
    val openTransfers: String = "Open Transfers",
    val performance: String = "Performance and cache",
    val appRam: String = "App RAM: ~%1\$s / %2\$s",
    val linkCache: String = "Link cache (RAM): %1\$s / %2\$s",
    val diskCache: String = "Disk cache: %1\$s · Torrents: %2\$s",
    val openMonitor: String = "Open Link and system monitor",
    val monitor: String = "Link and status monitor",
    val settings: String = "Settings",
)

/** The tray texts in the current process locale (desktop `DesktopLocale.apply` first). */
suspend fun loadTrayStrings(): TrayStrings = TrayStrings(
    show = getString(Res.string.rd_s1_tray_show),
    quit = getString(Res.string.rd_s1_tray_quit),
    streaming = getString(Res.string.rd_s1_tray_streaming),
    linkActive = getString(Res.string.rd_s1_tray_link_active),
    linkIdle = getString(Res.string.rd_s1_tray_link_idle),
    linkStopped = getString(Res.string.rd_s1_tray_link_stopped),
    linkSessions = getString(Res.string.rd_s1_tray_link_sessions),
    torrentsActive = getString(Res.string.rd_s1_tray_torrents_active),
    torrentsIdle = getString(Res.string.rd_s1_tray_torrents_idle),
    openTransfers = getString(Res.string.rd_s1_tray_open_transfers),
    performance = getString(Res.string.rd_s1_tray_performance),
    appRam = getString(Res.string.rd_s1_tray_app_ram),
    linkCache = getString(Res.string.rd_s1_tray_link_cache),
    diskCache = getString(Res.string.rd_s1_tray_disk_cache),
    openMonitor = getString(Res.string.rd_s1_tray_open_monitor),
    monitor = getString(Res.string.rd_s1_tray_monitor),
    settings = getString(Res.string.rd_s1_tray_settings),
)
