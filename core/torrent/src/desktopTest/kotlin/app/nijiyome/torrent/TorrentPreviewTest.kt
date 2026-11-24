// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.AppSettings
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.StorageLocation
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.torrent.system.DesktopTorrentHost
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.libtorrent4j.TorrentBuilder

/**
 * Desktop "Añadir torrent" with the file list (offline): a generated two-episode `.torrent` is previewed (its files, without adding
 * it), then added with only one file chosen; the engine selects only that one and writes into the folder chosen in Settings ›
 * Torrents, not the default one.
 */
class TorrentPreviewTest {
    private val dir: File = Files.createTempDirectory("nijiyome-torrent-preview").toFile()
    private var engine: TorrentRepositoryImpl? = null

    @AfterTest
    fun tearDown() {
        runCatching { engine?.shutdown() }
        dir.deleteRecursively() // temp dir created above
    }

    private class FixedSettings(torrent: TorrentSettings) : SettingsRepository {
        override val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings(torrent = torrent))
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }

    @Test
    fun aTorrentIsPreviewedThenAddedWithTheChosenFilesIntoTheChosenFolder(): Unit = runBlocking {
        assertIs<LibtorrentNative.Result.Loaded>(LibtorrentNative.load(File(dir, "cache")))
        val pack = File(dir, "seed/Show S01").apply { mkdirs() }
        File(pack, "Show - 01.mkv").writeBytes(Random(1).nextBytes(300_000))
        File(pack, "Show - 02.mkv").writeBytes(Random(2).nextBytes(200_000))
        val bytes = TorrentBuilder().path(pack).pieceSize(16 * 1024).generate().entry().bencode()
        val torrent = File(dir, "Show.torrent").apply { writeBytes(bytes) }
        val uri = torrent.toPath().toUri().toString()

        val chosen = File(dir, "Elegida")
        val port = ServerSocket(0).use { it.localPort }
        val settings = TorrentSettings(
            listenPort = port, dht = false, lsd = false, pex = false, upnp = false, wifiOnly = false, pauseOnLowBattery = false,
            downloadDir = chosen.absolutePath,
        )
        val repo = TorrentRepositoryImpl(
            host = DesktopTorrentHost(
                File(dir, "state"), File(dir, "Default"), File(dir, "Downloads"), logger = {},
                chosenFolder = { chosenTorrentFolder(settings) },
            ),
            settingsRepository = FixedSettings(settings),
            debrid = null,
        ).also { engine = it }

        val preview = repo.preview(uri).getOrThrow()
        assertEquals("Show S01", preview.name)
        assertEquals(listOf("Show - 01.mkv", "Show - 02.mkv"), preview.files.map { it.path.substringAfterLast('/') }.sorted())
        assertEquals(500_000L, preview.files.sumOf { it.sizeBytes })
        // a preview adds nothing
        assertTrue(repo.library.value.isEmpty())

        val second = preview.files.first { it.path.endsWith("Show - 02.mkv") }.index
        val id = repo.addManualFiles(uri, media = null, alignment = null, persist = true, files = setOf(second)).getOrThrow()
        assertEquals(preview.id, id)
        assertEquals(setOf(second), repo.awaitSelected(id, setOf(second)))
        // new torrents go to the chosen folder (Tatakai Next's download_dir), nothing is moved there later
        assertEquals(chosen.absolutePath, repo.storageUsage().downloadDir)
        val path = repo.library.value.single().path
        assertTrue(path.startsWith(chosen.absolutePath), path)
        assertNull(File(dir, "Default").listFiles()?.firstOrNull())
    }

    @Test
    fun theDesktopFolderComesFromTheSettings() {
        assertNull(chosenTorrentFolder(TorrentSettings()))
        assertEquals("/data/T", chosenTorrentFolder(TorrentSettings(downloadDir = " /data/T ")))
        // a folder picked before downloadDir existed (the Android storage choice) still counts
        assertEquals("/old", chosenTorrentFolder(TorrentSettings(storageLocation = StorageLocation.UserFolder("/old"))))
        assertEquals("/data/T", chosenTorrentFolder(TorrentSettings(downloadDir = "/data/T", storageLocation = StorageLocation.UserFolder("/old"))))
    }
}
