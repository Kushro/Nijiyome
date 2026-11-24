// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.AppSettings
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.torrent.system.DesktopTorrentHost
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
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
 * "Añadir torrent" with the preview, offline, shaped like the free Big Buck Bunny magnet (a folder with the `.mp4`, subtitles and a
 * poster; `tr=` + `ws=`): the preview gets the metadata from a loopback seeder found through the magnet's tracker (a local HTTP
 * tracker), then the seeder goes away and the add with only the `.mp4` chosen (previewCache + parseMagnetUri + setTorrentInfo,
 * addManualFiles) must still announce to the magnet's tracker and download the whole `.mp4` from the magnet's web seed alone (a local
 * HTTP server serving the files).
 */
class TorrentWebSeedPreviewTest {
    private val dir: File = Files.createTempDirectory("nijiyome-torrent-webseed").toFile()
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

    private fun HttpExchange.reply(code: Int, body: ByteArray, headers: Map<String, String> = emptyMap()) {
        headers.forEach { (k, v) -> responseHeaders.add(k, v) }
        sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        responseBody.use { it.write(body) }
    }

    @Test
    fun aPreviewedMagnetKeepsItsTrackerAndWebSeedAndDownloadsFromTheWebSeed(): Unit = runBlocking {
        assertIs<LibtorrentNative.Result.Loaded>(LibtorrentNative.load(File(dir, "cache")))
        val content = File(dir, "seed/Big Buck Bunny").apply { mkdirs() }
        val video = Random(7).nextBytes(2 * 1024 * 1024 + 4_321)
        File(content, "Big Buck Bunny.mp4").writeBytes(video)
        File(content, "Big Buck Bunny.en.srt").writeBytes(Random(8).nextBytes(20_000))
        File(content, "poster.jpg").writeBytes(Random(9).nextBytes(50_000))
        val info = TorrentInfo(
            // v1 only, like Big Buck Bunny's: libtorrent4j's fetchMagnet misses the metadata of a hybrid torrent added by its v1 hash
            TorrentBuilder().path(content).pieceSize(16 * 1024).flags(TorrentBuilder.V1_ONLY).generate().entry().bencode(),
        )
        val hash = info.infoHash().toHex()

        // The seeder, on loopback: only there for the metadata of the preview.
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

        // The magnet's tracker (hands out the seeder while it is up) and web seed (the files, with ranges), one local HTTP server.
        val seederUp = AtomicBoolean(true)
        val announces = ConcurrentLinkedQueue<String>()
        val webSeedRequests = ConcurrentLinkedQueue<String>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also { http = it }
        server.createContext("/announce") { ex ->
            announces += ex.requestURI.rawQuery.orEmpty()
            val peers = if (seederUp.get()) byteArrayOf(127, 0, 0, 1, (seederPort shr 8).toByte(), seederPort.toByte()) else ByteArray(0)
            ex.reply(200, "d8:intervali5e5:peers${peers.size}:".toByteArray(Charsets.ISO_8859_1) + peers + "e".toByteArray())
        }
        server.createContext("/ws/") { ex ->
            val rel = URI(ex.requestURI.rawPath).path.removePrefix("/ws/")
            val file = File(content.parentFile, rel)
            if (!file.isFile) return@createContext ex.reply(404, ByteArray(0))
            val bytes = file.readBytes()
            val range = ex.requestHeaders.getFirst("Range")
            webSeedRequests += "$rel $range"
            val m = range?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it.trim()) }
            if (m == null) return@createContext ex.reply(200, bytes)
            val from = m.groupValues[1].toInt()
            val to = m.groupValues[2].toIntOrNull()?.coerceAtMost(bytes.size - 1) ?: (bytes.size - 1)
            ex.reply(206, bytes.copyOfRange(from, to + 1), mapOf("Content-Range" to "bytes $from-$to/${bytes.size}"))
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"
        val magnet = "magnet:?xt=urn:btih:$hash&dn=Big+Buck+Bunny&tr=${java.net.URLEncoder.encode("$base/announce", "UTF-8")}" +
            "&ws=${java.net.URLEncoder.encode("$base/ws/", "UTF-8")}"

        // The engine under test: nothing online (no DHT / LSD / UPnP), background downloads accepted so a manual add runs.
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

        val preview = runCatching { withTimeout(90_000) { repo.preview(magnet) }.getOrThrow() }
            .getOrElse { throw AssertionError("no preview; announces=$announces seederPeers=${seedHandle.status().numPeers()}", it) }
        assertEquals(hash, preview.id)
        assertTrue(announces.isNotEmpty(), "the preview found the seeder through the magnet's tracker")

        // The seeder goes: from now on the data can only come from the web seed.
        seedHandle.pause()
        s.stop()
        seederUp.set(false)
        announces.clear()

        val mp4 = preview.files.single { it.path.endsWith("Big Buck Bunny.mp4") }
        val id = repo.addManualFiles(magnet, media = null, alignment = null, persist = true, files = setOf(mp4.index)).getOrThrow()
        assertEquals(hash, id)
        assertEquals(setOf(mp4.index), repo.awaitSelected(id, setOf(mp4.index)))
        // a torrent of its own, not the preview's metadata-only one (upload mode, saved under state/preview)
        val path = repo.library.value.single().path
        assertTrue(path.startsWith(settings.downloadDir), path)

        runCatching {
            withTimeout(120_000) {
                while (repo.files(id).single { it.index == mp4.index }.progress < 1.0) delay(250)
            }
        }.onFailure {
            throw AssertionError(
                "not downloaded: library=${repo.library.value} files=${repo.files(id)} webSeed=$webSeedRequests " +
                    "announces=${announces.size} trackers=${repo.trackers(id)}",
                it,
            )
        }
        val downloaded = File(settings.downloadDir, "Big Buck Bunny/Big Buck Bunny.mp4")
        assertContentEquals(video, downloaded.readBytes())
        assertTrue(webSeedRequests.any { it.startsWith("Big Buck Bunny/Big Buck Bunny.mp4") }, "fetched from the web seed: $webSeedRequests")
        assertTrue(announces.any { "info_hash=" in it }, "announced to the magnet's tracker after the add")
        assertTrue(repo.trackers(id).any { it.url == "$base/announce" }, "the magnet's tracker is on the torrent: ${repo.trackers(id)}")
    }

    @Test
    fun theFlagsOfAPreviewHandleNeverReachATorrentOfOurs() {
        assertIs<LibtorrentNative.Result.Loaded>(LibtorrentNative.load(File(dir, "cache")))
        // what a 1.0.0 resume file of a torrent stuck on its preview handle carries
        val stuck = TorrentFlags.UPLOAD_MODE.or_(TorrentFlags.STOP_WHEN_READY).or_(TorrentFlags.AUTO_MANAGED)
        val flags = app.nijiyome.torrent.engine.LibtorrentSession.addFlags(stuck, paused = false, pex = true, dontDownloadByDefault = true)
        assertTrue(flags.and_(TorrentFlags.UPLOAD_MODE).to_int() == 0, "upload mode cleared")
        assertTrue(flags.and_(TorrentFlags.STOP_WHEN_READY).to_int() == 0, "stop-when-ready cleared")
        assertTrue(flags.and_(TorrentFlags.AUTO_MANAGED).to_int() == 0)
    }
}
