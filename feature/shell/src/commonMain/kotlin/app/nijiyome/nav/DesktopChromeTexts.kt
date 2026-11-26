// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.rd_s1_mon_title
import app.nijiyome.feature.shell.resources.rd_s1_mon_unavailable
import app.nijiyome.feature.shell.resources.rd_s1_mon_stopped
import app.nijiyome.feature.shell.resources.rd_s1_mon_sessions_one
import app.nijiyome.feature.shell.resources.rd_s1_mon_sessions_other
import app.nijiyome.feature.shell.resources.rd_s1_mon_idle
import app.nijiyome.feature.shell.resources.rd_s1_mon_link_settings
import app.nijiyome.feature.shell.resources.rd_s1_mon_close
import app.nijiyome.feature.shell.resources.rd_s1_mon_devices
import app.nijiyome.feature.shell.resources.rd_s1_mon_server_off
import app.nijiyome.feature.shell.resources.rd_s1_mon_no_streaming
import app.nijiyome.feature.shell.resources.rd_s1_mon_torrents
import app.nijiyome.feature.shell.resources.rd_s1_mon_open_transfers
import app.nijiyome.feature.shell.resources.rd_s1_mon_no_torrents
import app.nijiyome.feature.shell.resources.rd_s1_mon_memory
import app.nijiyome.feature.shell.resources.rd_s1_mon_link_cache
import app.nijiyome.feature.shell.resources.rd_s1_mon_budget
import app.nijiyome.feature.shell.resources.rd_s1_mon_app_ram
import app.nijiyome.feature.shell.resources.rd_s1_mon_disk_cache
import app.nijiyome.feature.shell.resources.rd_s1_mon_torrent_storage
import app.nijiyome.feature.shell.resources.rd_s1_mon_free
import app.nijiyome.feature.shell.resources.rd_s1_mon_active_playback
import app.nijiyome.feature.shell.resources.rd_s1_mon_paused
import app.nijiyome.feature.shell.resources.rd_s1_mon_buffer_full
import app.nijiyome.feature.shell.resources.rd_s1_mon_processing
import app.nijiyome.feature.shell.resources.rd_s1_mon_library
import app.nijiyome.feature.shell.resources.rd_s1_mon_no_upscaling
import app.nijiyome.feature.shell.resources.rd_s1_mon_stop
import app.nijiyome.feature.shell.resources.rd_s1_mon_cached_ram
import app.nijiyome.feature.shell.resources.rd_s1_mon_ram_cache
import app.nijiyome.feature.shell.resources.rd_s1_mon_lead
import app.nijiyome.feature.shell.resources.rd_s1_mon_segments
import app.nijiyome.feature.shell.resources.rd_s1_mon_pace
import app.nijiyome.feature.shell.resources.rd_s1_mon_peers
import app.nijiyome.feature.shell.resources.rd_s1_tray_state_downloading
import app.nijiyome.feature.shell.resources.rd_s1_tray_state_seeding
import app.nijiyome.feature.shell.resources.rd_s1_tray_state_initializing
import app.nijiyome.feature.shell.resources.rd_s1_tray_state_active
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
 * Texts of the desktop Link monitor dialog, in the app language. Templates keep `%1$s` placeholders for [String.format];
 * [sessionsOne] / [sessionsOther] are the subtitle for one / several active sessions.
 */
@Immutable
data class LinkMonitorTexts(
    val title: String,
    val unavailable: String,
    val stopped: String,
    val sessionsOne: String,
    val sessionsOther: String,
    val idle: String,
    val linkSettings: String,
    val close: String,
    val devices: String,
    val serverOff: String,
    val noStreaming: String,
    val torrents: String,
    val openTransfers: String,
    val noTorrents: String,
    val memory: String,
    val linkCache: String,
    val budget: String,
    val appRam: String,
    val diskCache: String,
    val torrentStorage: String,
    val free: String,
    val activePlayback: String,
    val paused: String,
    val bufferFull: String,
    val processing: String,
    val library: String,
    val noUpscaling: String,
    val stop: String,
    val cachedRam: String,
    val ramCache: String,
    val lead: String,
    val segments: String,
    val pace: String,
    val peers: String,
)

@Composable
fun rememberLinkMonitorTexts(): LinkMonitorTexts = LinkMonitorTexts(
    title = stringResource(Res.string.rd_s1_mon_title),
    unavailable = stringResource(Res.string.rd_s1_mon_unavailable),
    stopped = stringResource(Res.string.rd_s1_mon_stopped),
    sessionsOne = stringResource(Res.string.rd_s1_mon_sessions_one),
    sessionsOther = stringResource(Res.string.rd_s1_mon_sessions_other),
    idle = stringResource(Res.string.rd_s1_mon_idle),
    linkSettings = stringResource(Res.string.rd_s1_mon_link_settings),
    close = stringResource(Res.string.rd_s1_mon_close),
    devices = stringResource(Res.string.rd_s1_mon_devices),
    serverOff = stringResource(Res.string.rd_s1_mon_server_off),
    noStreaming = stringResource(Res.string.rd_s1_mon_no_streaming),
    torrents = stringResource(Res.string.rd_s1_mon_torrents),
    openTransfers = stringResource(Res.string.rd_s1_mon_open_transfers),
    noTorrents = stringResource(Res.string.rd_s1_mon_no_torrents),
    memory = stringResource(Res.string.rd_s1_mon_memory),
    linkCache = stringResource(Res.string.rd_s1_mon_link_cache),
    budget = stringResource(Res.string.rd_s1_mon_budget),
    appRam = stringResource(Res.string.rd_s1_mon_app_ram),
    diskCache = stringResource(Res.string.rd_s1_mon_disk_cache),
    torrentStorage = stringResource(Res.string.rd_s1_mon_torrent_storage),
    free = stringResource(Res.string.rd_s1_mon_free),
    activePlayback = stringResource(Res.string.rd_s1_mon_active_playback),
    paused = stringResource(Res.string.rd_s1_mon_paused),
    bufferFull = stringResource(Res.string.rd_s1_mon_buffer_full),
    processing = stringResource(Res.string.rd_s1_mon_processing),
    library = stringResource(Res.string.rd_s1_mon_library),
    noUpscaling = stringResource(Res.string.rd_s1_mon_no_upscaling),
    stop = stringResource(Res.string.rd_s1_mon_stop),
    cachedRam = stringResource(Res.string.rd_s1_mon_cached_ram),
    ramCache = stringResource(Res.string.rd_s1_mon_ram_cache),
    lead = stringResource(Res.string.rd_s1_mon_lead),
    segments = stringResource(Res.string.rd_s1_mon_segments),
    pace = stringResource(Res.string.rd_s1_mon_pace),
    peers = stringResource(Res.string.rd_s1_mon_peers),
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
    val stateDownloading: String = "Downloading",
    val stateSeeding: String = "Seeding",
    val stateInitializing: String = "Starting",
    val stateActive: String = "Active",
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
    stateDownloading = getString(Res.string.rd_s1_tray_state_downloading),
    stateSeeding = getString(Res.string.rd_s1_tray_state_seeding),
    stateInitializing = getString(Res.string.rd_s1_tray_state_initializing),
    stateActive = getString(Res.string.rd_s1_tray_state_active),
)
