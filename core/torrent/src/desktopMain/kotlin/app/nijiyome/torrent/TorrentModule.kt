// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.StorageLocation
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.torrent.system.DesktopTorrentHost
import java.io.File
import app.nijiyome.model.torrent.DebridRepository
import app.nijiyome.torrent.debrid.DebridRepositoryImpl
import app.nijiyome.model.AppDataWiper
import org.koin.dsl.bind
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Where the desktop torrent engine keeps its files (chosen by the desktop app). */
data class DesktopTorrentFolders(
    /** `labels.json`, `history.json`, resume data and `.torrent` copies (under the app's data folder). */
    val stateDir: File,
    /** Where libtorrent writes the downloads (default `<Downloads>/Nijiyome`). */
    val downloadDir: File,
    /** "Guardar en Descargas" target (the user's Downloads folder). */
    val exportDir: File,
    /** libtorrent4j's native library is unpacked under `<nativeCacheDir>/native`. */
    val nativeCacheDir: File,
)

/**
 * Desktop counterpart of the Android `torrentModule`: the shared engine ([TorrentRepositoryImpl] on libtorrent4j) over a
 * [DesktopTorrentHost]. libtorrent4j's native library is loaded the first time something asks for the [TorrentRepository]; when it
 * cannot load, [UnavailableTorrentRepository] is bound instead and the app goes on without torrents. Closing Koin (app exit) saves
 * resume data and stops the session.
 */
fun desktopTorrentModule(
    folders: DesktopTorrentFolders,
    loadNative: () -> LibtorrentNative.Result = { LibtorrentNative.load(folders.nativeCacheDir) },
    log: (String) -> Unit = { System.err.println("torrent: $it") },
): Module = module {
    single<TorrentRepository> {
        when (val native = loadNative()) {
            is LibtorrentNative.Result.Loaded -> {
                log("libtorrent ${native.version} loaded from ${native.library}")
                TorrentRepositoryImpl(
                    host = run {
                        val settings = get<SettingsRepository>()
                        DesktopTorrentHost(
                            folders.stateDir,
                            folders.downloadDir,
                            chosenFolder = { chosenTorrentFolder(settings.settings.value.torrent) },
                            exportDir = folders.exportDir,
                            logger = log,
                            notifier = getOrNull(),
                            outbound = app.nijiyome.network.Outbound.proxyFlow,
                        )
                    },
                    settingsRepository = get(),
                    debrid = getOrNull(),
                ).also { engine -> get<TorrentEngineStop>().register(engine::shutdown) }
            }
            is LibtorrentNative.Result.Failed -> {
                log("no torrent engine: ${native.reason}")
                UnavailableTorrentRepository()
            }
        }
    } onClose { (it as? TorrentRepositoryImpl)?.shutdown() }
    // "Restablecer la app": the engine stops (resume data and labels written, session closed) BEFORE the data folder is swept,
    // so nothing rewrites <data>/torrents afterwards. A second shutdown at exit finds the session stopped and writes nothing.
    single { TorrentEngineStop() } bind AppDataWiper::class
    // Debrid services (instant HTTPS playback), with their API keys in the desktop SecretStore (dataModule), as on Android.
    single {
        // 1.1 (F7): Settings › Torrents › Debrid (enabled / prefer cached / download cached)
        val settings = getOrNull<app.nijiyome.model.SettingsRepository>()
        DebridRepositoryImpl(
            client = get(),
            secrets = get(),
            debridSettings = { settings?.settings?.value?.torrent?.debrid ?: app.nijiyome.model.torrent.DebridSettings() },
        )
    }
    single<DebridRepository> { get<DebridRepositoryImpl>() }
    single { PendingMagnetLink() }
}

/**
 * The download folder of Settings › Torrents on desktop: `TorrentSettings.downloadDir`, or a folder picked before it existed
 * (`StorageLocation.UserFolder`, which Android moves to on completion); null = the default one.
 */
internal fun chosenTorrentFolder(s: TorrentSettings): String? =
    s.downloadDir.trim().ifBlank { (s.storageLocation as? StorageLocation.UserFolder)?.treeUri?.trim().orEmpty() }.ifBlank { null }

/**
 * Stops the torrent engine for "Restablecer la app" (an [AppDataWiper]: the reset runs every wiper before it sweeps the data folder).
 * Only an engine that was created is stopped; one nobody asked for is not loaded just to stop it.
 */
class TorrentEngineStop : AppDataWiper {
    @Volatile
    private var stop: (() -> Unit)? = null

    fun register(stop: () -> Unit) {
        this.stop = stop
    }

    override suspend fun wipe() {
        val s = stop ?: return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { s() }
    }
}
