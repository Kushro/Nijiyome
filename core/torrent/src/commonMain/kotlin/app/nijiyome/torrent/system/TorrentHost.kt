// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent.system

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.StateFlow

/** Default network facts (11 §7): `NET_CAPABILITY_NOT_METERED` decides `wifiOnly`; a VPN does not change the policy. */
data class NetworkState(val connected: Boolean, val metered: Boolean)

/** Aggregated engine state the foreground notification and the service need (internal; derived by TorrentRepositoryImpl). */
data class ServiceSnapshot(
    /** Something streams, plays or downloads: the service must stay in the foreground. */
    val demand: Boolean = false,
    val streaming: Boolean = false,
    /** Title of the streamed episode (label title), when streaming. */
    val streamingTitle: String? = null,
    /** Torrents transferring in the background (not streaming). */
    val downloads: Int = 0,
    val downBps: Long = 0,
    val upBps: Long = 0,
    /** Aggregated 0..1 progress of the background downloads; null = none. */
    val progress: Double? = null,
    val pausedBySystem: Boolean = false,
)

/** A SOCKS5 proxy for libtorrent (host names resolved by the proxy). */
data class TorrentProxy(val host: String, val port: Int, val username: String? = null, val password: String? = null) {
    override fun toString(): String = "TorrentProxy($host:$port, credentials=${if (username.isNullOrEmpty()) "no" else "yes"})"
}

/**
 * What the shared torrent engine (TorrentRepositoryImpl) needs from its platform: where its files go, the device facts of the run
 * policy, storage operations, notifications and the service that keeps the process alive. Android: `AndroidTorrentHost` (Context,
 * SAF, MediaStore, the foreground service); desktop: `DesktopTorrentHost` (plain folders, no service).
 */
internal interface TorrentHost {
    /** `labels.json`, `history.json`, resume data and `.torrent` copies. */
    val stateDir: File

    /** Where libtorrent writes (created on access). */
    val downloadDir: File

    /** A previous download folder whose torrents keep their data there; null = none. */
    val legacyDownloadDir: File?

    val network: StateFlow<NetworkState>

    /**
     * 1.1 (Settings › Red, desktop): the SOCKS5 proxy libtorrent sends peers, trackers and the DHT through; null = direct. Android has
     * none, so its session never gets proxy settings.
     */
    fun proxy(): TorrentProxy? = null

    /** Emits when [proxy] changes (desktop; a cold flow collected by the engine's own scope). Android: never. */
    val proxyChanges: kotlinx.coroutines.flow.Flow<Unit> get() = kotlinx.coroutines.flow.emptyFlow()

    /** Battery low (or battery saver on): the `pauseOnLowBattery` policy. */
    val batteryLow: StateFlow<Boolean>

    /** Starts following the network and battery (once). */
    fun registerMonitors()

    /** Free space for new data on the volume of [dir]. Blocking. */
    fun allocatableBytes(dir: File): Long

    /** Recursive size of [dir]. Blocking. */
    fun usedBytes(dir: File): Long

    /**
     * Android: a folder chosen in Settings (`StorageLocation.UserFolder`) receives the completed files, which are then dropped from
     * the session (SAF move-on-complete). false (desktop): the chosen folder is [downloadDir] itself and nothing is moved.
     */
    val movesToUserFolder: Boolean get() = true

    /** Move-on-complete: copies [source] into the user's folder [folder] (`StorageLocation.UserFolder.treeUri`); returns where it went. */
    fun copyToFolder(source: File, folder: String): String

    /** Deletes what [copyToFolder] wrote. */
    fun deleteMoved(location: String): Boolean

    /** "Guardar en Descargas": copies [source] to the user's downloads; returns where it went. `IOException("unsupported")` = cannot. */
    fun exportToDownloads(source: File): String

    /** Opens a `.torrent` the user picked ([uri] as the platform's file picker returns it); null = cannot be opened. */
    fun openTorrentFile(uri: String): InputStream?

    fun notifyCompleted(torrentId: String, title: String, episode: Int?)

    fun notifyError(torrentId: String, message: String)

    /** Keeps the process alive while the engine has work (Android: the foreground TorrentService). */
    fun startService()

    fun log(message: String)
}
