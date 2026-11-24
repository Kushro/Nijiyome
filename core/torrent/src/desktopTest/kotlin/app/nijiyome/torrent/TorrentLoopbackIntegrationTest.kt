// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.AppSettings
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.torrent.system.DesktopTorrentHost
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TcpEndpoint
import org.libtorrent4j.TorrentBuilder
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack

/**
 * The desktop engine end to end, offline: a generated file and its `.torrent`, added the way "Añadir torrent" does (a `file://`
 * URI from the desktop picker) to the engine under test (TorrentRepositoryImpl over DesktopTorrentHost, DHT / LSD / UPnP off), which
 * plays it; a plain libtorrent session seeds it on loopback and dials the engine; the loopback streaming server must answer whole
 * and `Range` requests with the right bytes, and the file must end up in the engine's download folder.
 */
class TorrentLoopbackIntegrationTest {
    private val dir: File = Files.createTempDirectory("nijiyome-torrent-it").toFile()
    private var seeder: SessionManager? = null
    private var engine: TorrentRepositoryImpl? = null

    @AfterTest
    fun tearDown() {
        runCatching { engine?.shutdown() }
        runCatching { seeder?.stop() }
        dir.deleteRecursively() // temp dir created above
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private class FixedSettings(torrent: TorrentSettings) : SettingsRepository {
        override val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings(torrent = torrent))
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }

    private fun get(url: String, range: String? = null): Pair<Int, ByteArray> {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        if (range != null) c.setRequestProperty("Range", range)
        c.connectTimeout = 10_000
        c.readTimeout = 60_000
        return try {
            c.responseCode to c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    @Test
    fun seedsDownloadsAndStreamsOverLoopback() = streamOverLoopback(verify = null, extraFirstFile = 0)

    /**
     * 1.1.2: the Windows path (TorrentHost.verifyStreamedPieces) on any OS: every piece served is first checked on disk, by its
     * SHA-1 when it lies wholly in the file, or after a confirmed cache flush when it is shared with the neighbour file (a
     * two-file torrent whose first file does not end on a piece boundary).
     */
    @Test
    fun streamedPiecesAreCheckedOnDiskFirst() = streamOverLoopback(verify = true, extraFirstFile = 100_003)

    /** [verify] null: the host's own default (on only on Windows). */
    private fun streamOverLoopback(verify: Boolean?, extraFirstFile: Int): Unit = runBlocking {
        assertIs<LibtorrentNative.Result.Loaded>(LibtorrentNative.load(File(dir, "cache")))
        // The content: one "episode" (random bytes; the stream server does not look inside), maybe after another file.
        val seedDir = File(dir, "seed").apply { mkdirs() }
        val data = Random(42).nextBytes(3 * 1024 * 1024 + 12_345)
        val root = if (extraFirstFile > 0) File(seedDir, "Clip").apply { mkdirs() } else seedDir
        if (extraFirstFile > 0) File(root, "Clip - 00.nfo").writeBytes(Random(7).nextBytes(extraFirstFile))
        val video = File(root, "Clip - 01.mkv").apply { writeBytes(data) }
        val torrentBytes = TorrentBuilder().path(if (extraFirstFile > 0) root else video).pieceSize(64 * 1024).generate().entry().bencode()
        val info = TorrentInfo(torrentBytes)
        val hash = info.infoHash().toHex()
        val torrentFile = File(dir, "Clip.torrent").apply { writeBytes(torrentBytes) }

        // The engine under test, listening on a known port, nothing that goes online.
        val port = freePort()
        val settings = TorrentSettings(listenPort = port, dht = false, lsd = false, pex = false, upnp = false, wifiOnly = false, pauseOnLowBattery = false)
        val downloads = File(dir, "Downloads/Nijiyome")
        val logs = java.util.Collections.synchronizedList(mutableListOf<String>())
        val repo = TorrentRepositoryImpl(
            host = if (verify == null) {
                DesktopTorrentHost(File(dir, "state"), downloads, File(dir, "Downloads"), logger = { logs += it })
            } else {
                DesktopTorrentHost(File(dir, "state"), downloads, File(dir, "Downloads"), logger = { logs += it }, verifyStreamedPieces = verify)
            },
            settingsRepository = FixedSettings(settings),
            debrid = null,
        ).also { engine = it }

        // The seeder: checks the file it has, then connects to the engine.
        val pack = SettingsPack().apply {
            listenInterfaces("127.0.0.1:${freePort()}")
            setEnableDht(false)
            setEnableLsd(false)
            setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
            setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
        }
        val s = SessionManager(false).also { seeder = it }
        s.start(SessionParams(pack))

        val media = Media(
            ref = MediaRef(anilistId = 1), kind = MediaKind.ANIME, title = "Clip", titles = emptyMap(), cover = null, banner = null, color = null,
            synopsis = null, genres = emptyList(), episodes = 1, status = null, season = null, year = null, score = null, sourceProvider = "test",
        )
        // "Añadir torrent" with the .torrent file (as the picker hands it over: a path), then play its episode.
        assertEquals(hash, repo.addManual(torrentFile.toPath().toUri().toString(), media, alignment = null, persist = false).getOrThrow())
        val playback = async(Dispatchers.IO) { repo.play(hash, media, episode = 1, fileIndex = null) }
        val result = withTimeout(90_000) { playback.await() }.getOrThrow()
        // Only now the seeder dials the engine: the engine pauses a torrent that is merely added (not playing) on its next tick,
        // and libtorrent waits a minute before dialing a peer that dropped it again.
        val seed = org.libtorrent4j.AddTorrentParams().apply {
            setTorrentInfo(info)
            setSavePath(seedDir.absolutePath)
            // a running seed from the start: not paused, not left to the session's queue (which stops and restarts it once)
            setFlags(TorrentFlags.SEED_MODE)
        }
        val ec = org.libtorrent4j.ErrorCode(org.libtorrent4j.swig.error_code())
        val seedHandle = org.libtorrent4j.SessionHandle(s.swig()).addTorrent(seed, ec)
        assertTrue(!ec.isError && seedHandle.isValid, ec.message)
        // Dial the engine, again every couple of seconds in case a connection drops (libtorrent itself would wait a minute).
        val dialer = launch(Dispatchers.IO) {
            while (true) {
                runCatching { seedHandle.swig().connect_peer(TcpEndpoint("127.0.0.1", port).swig()) }
                delay(2_000)
            }
        }

        assertEquals(hash, result.torrentId)
        assertEquals("Clip - 01.mkv", result.fileName.substringAfterLast('/').substringAfterLast('\\'))
        val url = result.stream.url
        assertTrue(url.startsWith("http://127.0.0.1:"), url)

        // A range in the middle, then the whole file (both wait for the pieces they need).
        val (code, part) = withContext(Dispatchers.IO) { get(url, "bytes=1000000-1065535") }
        assertEquals(206, code)
        assertContentEquals(data.copyOfRange(1_000_000, 1_065_536), part)
        val (whole, all) = withContext(Dispatchers.IO) { get(url) }
        assertEquals(200, whole)
        assertContentEquals(data, all)
        val (tailCode, tail) = withContext(Dispatchers.IO) { get(url, "bytes=-100") }
        assertEquals(206, tailCode)
        assertContentEquals(data.copyOfRange(data.size - 100, data.size), tail)
        // The engine wrote it under its download folder.
        assertTrue(File(if (extraFirstFile > 0) File(downloads, "Clip") else downloads, "Clip - 01.mkv").isFile)
        if (verify == true && extraFirstFile > 0) {
            // the file's first piece is shared with the .nfo: served after a flush libtorrent confirmed
            assertTrue(logs.any { "cache flush" in it }, "no flush logged: $logs")
            assertTrue(logs.none { "CACHE_FLUSHED" in it }, "a flush was not confirmed: $logs")
        }
        dialer.cancelAndJoin()
        repo.playbackEnded(hash, completed = true)
    }
}
