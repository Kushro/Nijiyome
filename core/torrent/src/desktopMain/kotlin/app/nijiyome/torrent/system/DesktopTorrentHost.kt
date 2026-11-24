// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent.system

import app.nijiyome.model.DesktopNoticeTexts
import app.nijiyome.model.SystemNotifier
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop side of the shared torrent engine: plain folders, no service (the engine lives as long as the app window) and no device
 * monitors. The folder chosen in Settings › Torrents ([chosenFolder]) is where libtorrent writes new torrents, as in Tatakai Next's
 * desktop app; nothing is moved when a download completes. Torrents added before a change keep their folder (their resume data).
 */
internal class DesktopTorrentHost(
    override val stateDir: File,
    private val torrentDownloadDir: File,
    /** "Guardar en Descargas" copies the file here (the user's Downloads folder). */
    private val exportDir: File,
    private val logger: (String) -> Unit = { System.err.println("torrent: $it") },
    /** System notifications (the desktop app binds one); without it the torrent client still shows these states. */
    private val notifier: SystemNotifier? = null,
    /** The download folder chosen in Settings › Torrents (a path); null or blank = [torrentDownloadDir]. */
    private val chosenFolder: () -> String? = { null },
    /** 1.1: the outbound proxy of Settings › Red; libtorrent only uses a SOCKS5 one (an HTTP proxy cannot carry its UDP). */
    private val outbound: StateFlow<app.nijiyome.network.ProxyEndpoint?>? = null,
) : TorrentHost {
    override fun proxy(): TorrentProxy? = torrentProxyOf(outbound?.value)

    // cold: runs only while the engine's own scope collects it (no scope of its own left running)
    override val proxyChanges: kotlinx.coroutines.flow.Flow<Unit> =
        outbound?.map(::torrentProxyOf)?.distinctUntilChanged()?.drop(1)?.map { } ?: kotlinx.coroutines.flow.emptyFlow()

    override val downloadDir: File
        get() = (chosenFolder()?.takeIf { it.isNotBlank() }?.let(::fileOf) ?: torrentDownloadDir).also { it.mkdirs() }

    override val movesToUserFolder: Boolean get() = false

    override val legacyDownloadDir: File? get() = null

    // A desktop has no metered-network or battery signal the JDK can read: always connected, unmetered, not low.
    private val _network = MutableStateFlow(NetworkState(connected = true, metered = false))
    override val network: StateFlow<NetworkState> = _network.asStateFlow()
    private val _batteryLow = MutableStateFlow(false)
    override val batteryLow: StateFlow<Boolean> = _batteryLow.asStateFlow()

    override fun registerMonitors() = Unit

    override fun allocatableBytes(dir: File): Long {
        dir.mkdirs()
        return dir.usableSpace
    }

    override fun usedBytes(dir: File): Long = if (!dir.exists()) 0 else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    override fun copyToFolder(source: File, folder: String): String {
        val dir = fileOf(folder)
        dir.mkdirs()
        if (!dir.isDirectory || !dir.canWrite()) throw IOException("folder not writable")
        return copyInto(source, dir).absolutePath
    }

    override fun deleteMoved(location: String): Boolean = runCatching { fileOf(location).delete() }.getOrDefault(false)

    override fun exportToDownloads(source: File): String {
        exportDir.mkdirs()
        if (!exportDir.isDirectory) throw IOException("unsupported")
        if (source.parentFile?.canonicalFile == exportDir.canonicalFile) return source.absolutePath
        return copyInto(source, exportDir).absolutePath
    }

    override fun openTorrentFile(uri: String): InputStream? = runCatching { FileInputStream(fileOf(uri)) }.getOrNull()

    /** "Episodio N listo" (Android's TorrentNotifications.notifyCompleted, without the "Ver" action). */
    override fun notifyCompleted(torrentId: String, title: String, episode: Int?) {
        notifier?.notify(title, DesktopNoticeTexts.episodeReady(episode))
    }

    override fun notifyError(torrentId: String, message: String) {
        notifier?.notify(DesktopNoticeTexts.torrentProblem, message)
    }

    override fun startService() = Unit

    override fun log(message: String) = logger(message)

    private fun copyInto(source: File, dir: File): File {
        val target = File(dir, source.name)
        val tmp = File(dir, source.name + ".part")
        source.inputStream().use { input -> tmp.outputStream().use { input.copyTo(it, BUFFER) } }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("cannot write ${target.name}")
        }
        return target
    }

    private companion object {
        const val BUFFER = 256 * 1024
    }
}

/** A path or a `file:` URI (what the desktop pickers and older settings hold) as a file. */
internal fun fileOf(pathOrUri: String): File =
    if (pathOrUri.startsWith("file:")) File(URI(pathOrUri)) else File(pathOrUri)

/** The SOCKS5 proxy libtorrent takes from the outbound one; null for none or an HTTP proxy (torrents then connect directly). */
internal fun torrentProxyOf(p: app.nijiyome.network.ProxyEndpoint?): TorrentProxy? =
    p?.takeIf { it.kind == app.nijiyome.network.ProxyKind.SOCKS5 }?.let { TorrentProxy(it.host, it.port, it.username, it.password) }
