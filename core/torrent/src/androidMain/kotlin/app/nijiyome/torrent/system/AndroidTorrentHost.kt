// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent.system

import android.content.Context
import android.net.Uri
import app.nijiyome.torrent.TorrentService
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.StateFlow

/**
 * Android side of the shared torrent engine: app-private folders, ConnectivityManager / battery monitors, SAF and MediaStore copies,
 * the torrent notifications and the foreground [TorrentService]. Same calls the engine made before it was shared.
 */
internal class AndroidTorrentHost(context: Context) : TorrentHost {
    private val app = context.applicationContext
    private val networkMonitor = NetworkMonitor(app)
    private val batteryMonitor = BatteryMonitor(app)
    private val storage = StorageOps(app)

    override val stateDir: File = File(app.filesDir, "torrents")

    /**
     * Internal app storage, not `getExternalFilesDir`: on devices with FUSE-BPF (Galaxy Tab S9+, Android 16) libtorrent's file
     * opens under `/storage/emulated/0/Android/data` made the MediaProvider FUSE daemon abort
     * (`NodeTracker::CheckTracked ... active_nodes_.find(node)` in `pf_open`), and Android killed the app with it: the episode never
     * got past 0 %. Internal storage does not go through FUSE. Exporting to Downloads / a SAF folder is unchanged.
     */
    override val downloadDir: File
        get() = File(app.noBackupFilesDir, "torrent-downloads").also { it.mkdirs() }

    /** Where downloads went before 0.3.5. Torrents with data there keep it (their resume data points there); empty ones move. */
    override val legacyDownloadDir: File?
        get() = app.getExternalFilesDir("torrents")

    override val network: StateFlow<NetworkState> get() = networkMonitor.state
    override val batteryLow: StateFlow<Boolean> get() = batteryMonitor.low

    override fun registerMonitors() {
        networkMonitor.register()
        batteryMonitor.register()
    }

    override fun allocatableBytes(dir: File): Long = storage.allocatableBytes(dir)
    override fun usedBytes(dir: File): Long = storage.usedBytes(dir)
    override fun copyToFolder(source: File, folder: String): String = storage.copyToTree(source, folder)
    override fun deleteMoved(location: String): Boolean = storage.deleteDocument(location)
    override fun exportToDownloads(source: File): String = storage.exportToDownloads(source)
    override fun openTorrentFile(uri: String): InputStream? = app.contentResolver.openInputStream(Uri.parse(uri))

    override fun notifyCompleted(torrentId: String, title: String, episode: Int?) =
        TorrentNotifications.notifyCompleted(app, torrentId, title, episode)

    override fun notifyError(torrentId: String, message: String) = TorrentNotifications.notifyError(app, torrentId, message)

    override fun startService() = TorrentService.start(app)

    override fun log(message: String) {
        android.util.Log.i("torrent", message)
    }
}
