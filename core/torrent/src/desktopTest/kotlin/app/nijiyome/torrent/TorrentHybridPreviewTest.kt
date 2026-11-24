// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.AppSettings
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.torrent.system.DesktopTorrentHost
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.ErrorCode
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentBuilder
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.settings_pack

/**
 * "Añadir torrent" with the preview for a hybrid (v1 + v2) torrent added by its v1 hash, offline: libtorrent4j's own fetchMagnet
 * compared the handle's best hash (the truncated v2 once the metadata arrives) with the magnet's v1 and waited out its timeout. The
 * preview must get the files from a loopback seeder found through a local tracker, well before that timeout, keep the v1 hash as the
 * id, and the add must keep only the chosen file.
 */
class TorrentHybridPreviewTest {
    private val dir: File = Files.createTempDirectory("nijiyome-torrent-hybrid").toFile()
    private var seeder: SessionManager? = null
    private var engine: TorrentRepositoryImpl? = null
    private var http: HttpServer? = null

    @AfterTest
    fun tearDown() {
        runCatching { engine?.shutdown() }
        runCatching { seeder?.stop() }
        runCatching { http?.stop(0) }
        dir.deleteRecursively() // temp dir created above
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private class FixedSettings(torrent: TorrentSettings) : SettingsRepository {
        override val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings(torrent = torrent))
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }

    @Test
    fun aHybridMagnetByItsV1HashIsPreviewed(): Unit = runBlocking {
        assertIs<LibtorrentNative.Result.Loaded>(LibtorrentNative.load(File(dir, "cache")))
        val content = File(dir, "seed/Hybrid Show").apply { mkdirs() }
        val video = Random(11).nextBytes(1024 * 1024 + 777)
        File(content, "Hybrid Show 01.mkv").writeBytes(video)
        File(content, "Hybrid Show 01.ass").writeBytes(Random(12).nextBytes(9_000))
        // Default flags of libtorrent 2: a hybrid torrent.
        val info = TorrentInfo(TorrentBuilder().path(content).pieceSize(16 * 1024).generate().entry().bencode())
        val hashes = info.swig().info_hashes()
        assertTrue(hashes.has_v1() && hashes.has_v2(), "the fixture is hybrid")
        val v1 = hashes.getV1().to_hex()

        val seederPort = freePort()
        val s = SessionManager(false).also { seeder = it }
        s.start(
            SessionParams(
                SettingsPack().apply {
                    listenInterfaces("127.0.0.1:$seederPort")
                    setEnableDht(false)
                    setEnableLsd(false)
                    setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
                    setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
                },
            ),
        )
        val ec = ErrorCode(error_code())
        val seedHandle = SessionHandle(s.swig()).addTorrent(
            AddTorrentParams().apply {
                setTorrentInfo(info)
                setSavePath(content.parentFile.absolutePath)
                setFlags(TorrentFlags.SEED_MODE)
            },
            ec,
        )
        assertTrue(!ec.isError && seedHandle.isValid, ec.message)

        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also { http = it }
        server.createContext("/announce") { ex ->
            val peers = byteArrayOf(127, 0, 0, 1, (seederPort shr 8).toByte(), seederPort.toByte())
            val body = "d8:intervali5e5:peers${peers.size}:".toByteArray(Charsets.ISO_8859_1) + peers + "e".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        val magnet = "magnet:?xt=urn:btih:$v1&dn=Hybrid+Show" +
            "&tr=${java.net.URLEncoder.encode("http://127.0.0.1:${server.address.port}/announce", "UTF-8")}"

        val settings = TorrentSettings(
            listenPort = freePort(), dht = false, lsd = false, pex = false, upnp = false, wifiOnly = false, pauseOnLowBattery = false,
            backgroundDownloads = true, backgroundAck = true, downloadDir = File(dir, "Torrents").absolutePath,
        )
        val repo = TorrentRepositoryImpl(
            host = DesktopTorrentHost(
                File(dir, "state"), File(dir, "Default"), File(dir, "Downloads"), logger = {},
                chosenFolder = { chosenTorrentFolder(settings) },
            ),
            settingsRepository = FixedSettings(settings),
            debrid = null,
        ).also { engine = it }

        val started = System.nanoTime()
        // Well under the 90 s metadata timeout the old fetchMagnet always ran into.
        val preview = withTimeout(45_000) { repo.preview(magnet) }.getOrThrow()
        println("[hybrid-preview] metadata in ${(System.nanoTime() - started) / 1_000_000} ms: ${preview.files}")
        assertEquals(v1, preview.id)
        assertEquals(setOf("Hybrid Show/Hybrid Show 01.mkv", "Hybrid Show/Hybrid Show 01.ass"), preview.files.map { it.path }.toSet())

        val mkv = preview.files.single { it.path.endsWith(".mkv") }
        val id = repo.addManualFiles(magnet, media = null, alignment = null, persist = true, files = setOf(mkv.index)).getOrThrow()
        assertEquals(v1, id)
        assertEquals(setOf(mkv.index), repo.awaitSelected(id, setOf(mkv.index)))
        // The tracker list is read from libtorrent's own vector while it lives: before, libtorrent4j's wrappers pointed into a vector
        // the GC could free, and reading endpoints failed at random ("vector size is too large to fit into a Java int").
        val trackerUrl = "http://127.0.0.1:${server.address.port}/announce"
        repeat(300) { i ->
            if (i % 10 == 0) System.gc()
            assertTrue(repo.trackers(id).any { it.url == trackerUrl }, "round $i")
        }
        // Not checked: the download itself. In this setup a hybrid torrent added by its v1 hash got no data in 60 s, with or without
        // the preview (inherited; without it the labels are also missed, since libtorrent's alerts name it by the truncated v2 hash
        // once its metadata is in, while magnet and label use the v1 one). Open finding of v1.1 F1, not investigated further.
    }
}
