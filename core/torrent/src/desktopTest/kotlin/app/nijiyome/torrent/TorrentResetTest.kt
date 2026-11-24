// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.AppSettings
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.SecretStore
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.libtorrent4j.TorrentBuilder
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Hallazgo 7 de la revisión: "Restablecer la app" sweeps `<data>/torrents` and the app then closes Koin, whose shutdown of the
 * engine used to write labels, history and resume data back into the swept folder (the old torrents came back after the reset).
 * The desktop module now stops a running engine as an [AppDataWiper], before the sweep.
 */
class TorrentResetTest {
    private val dir: File = Files.createTempDirectory("nijiyome-torrent-reset").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively() // temp dir created above
    }

    private class FixedSettings(torrent: TorrentSettings) : SettingsRepository {
        override val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings(torrent = torrent))
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }

    private object NoSecrets : SecretStore {
        override suspend fun get(key: String): String? = null
        override suspend fun put(key: String, value: String?) = Unit
        override suspend fun clear() = Unit
    }

    private fun base(port: Int) = module {
        single<SettingsRepository> {
            FixedSettings(TorrentSettings(listenPort = port, dht = false, lsd = false, pex = false, upnp = false, wifiOnly = false, pauseOnLowBattery = false))
        }
        single { OkHttpClient() }
        single<SecretStore> { NoSecrets }
    }

    @Test
    fun aResetStopsTheEngineBeforeTheSweepAndNothingIsWrittenBack(): Unit = runBlocking {
        val cache = File(dir, "cache")
        assertIs<LibtorrentNative.Result.Loaded>(LibtorrentNative.load(cache))
        val state = File(dir, "torrents")
        val folders = DesktopTorrentFolders(stateDir = state, downloadDir = File(dir, "dl"), exportDir = File(dir, "export"), nativeCacheDir = cache)
        val port = ServerSocket(0).use { it.localPort }
        val app = koinApplication { modules(base(port), desktopTorrentModule(folders, log = {})) }
        val repo = app.koin.get<TorrentRepository>()
        assertIs<TorrentRepositoryImpl>(repo)
        // A torrent in the library, as the user had before the reset.
        val content = File(dir, "seed/Clip - 01.mkv").apply { parentFile.mkdirs(); writeBytes(Random(1).nextBytes(256 * 1024)) }
        val bytes = TorrentBuilder().path(content).pieceSize(16 * 1024).generate().entry().bencode()
        val torrentFile = File(dir, "Clip.torrent").apply { writeBytes(bytes) }
        val media = Media(
            ref = MediaRef(anilistId = 1), kind = MediaKind.ANIME, title = "Clip", titles = emptyMap(), cover = null, banner = null, color = null,
            synopsis = null, genres = emptyList(), episodes = 1, status = null, season = null, year = null, score = null, sourceProvider = "test",
        )
        val hash = repo.addManual(torrentFile.toPath().toUri().toString(), media, alignment = null, persist = true).getOrThrow()
        assertEquals(TorrentInfo(bytes).infoHash().toHex(), hash)
        assertTrue(File(state, "labels.json").isFile, "the library is on disk before the reset")

        // DesktopAppReset: every wiper, then the sweep of the data folder, then the app closes Koin to restart.
        app.koin.getAll<AppDataWiper>().forEach { it.wipe() }
        assertTrue(state.deleteRecursively())
        app.close()

        assertFalse(File(state, "labels.json").exists(), "nothing wrote the old library back")
        assertFalse(File(state, "history.json").exists())
        assertTrue(state.listFiles().isNullOrEmpty(), "state folder: ${state.list()?.toList()}")
    }

    @Test
    fun aResetDoesNotLoadAnEngineNobodyUsed(): Unit = runBlocking {
        val folders = DesktopTorrentFolders(File(dir, "torrents"), File(dir, "dl"), File(dir, "export"), File(dir, "cache"))
        val app = koinApplication {
            modules(base(0), desktopTorrentModule(folders, loadNative = { error("the engine must not be loaded by a reset") }, log = {}))
        }
        app.koin.getAll<AppDataWiper>().forEach { it.wipe() }
        app.close()
    }
}
