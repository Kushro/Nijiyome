// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.link.LinkCrypto
import app.nijiyome.link.LinkResult
import app.nijiyome.link.PairingClient
import app.nijiyome.link.PairingPayload
import app.nijiyome.link.PayloadResult
import app.nijiyome.model.AppSettings
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.server.link.ErrorCode
import app.nijiyome.server.link.LinkConfig
import app.nijiyome.server.link.LinkServer
import app.nijiyome.server.link.TorrentRequest
import app.nijiyome.server.link.call
import app.nijiyome.server.link.get
import app.nijiyome.server.link.media.Platform
import app.nijiyome.server.link.obj
import app.nijiyome.torrent.DesktopTorrentFolders
import app.nijiyome.torrent.LibtorrentNative
import app.nijiyome.torrent.desktopTorrentModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TcpEndpoint
import org.libtorrent4j.TorrentBuilder
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A Link `torrent` source end to end, offline: FFmpeg generates a clip (video only), libtorrent makes its `.torrent`, a plain libtorrent
 * session seeds it on 127.0.0.1 and dials the desktop engine (W1's TorrentRepositoryImpl, built by the production Koin module, DHT / LSD
 * / UPnP off), to which the .torrent was added ("Añadir torrent"); a paired phone asks the Link server for a session by info-hash,
 * the server reads the engine's loopback stream through its fetcher and FFmpeg turns it into HLS. Then: the session's end hands the
 * torrent back (reader count), a renewal reuses it, and without an engine the source is `source_unavailable`.
 */
class TorrentSourceTest {
    private val dir: File = Files.createTempDirectory("nijiyome-link-torrent").toFile()
    private val cleanup = mutableListOf<() -> Unit>()

    @AfterTest
    fun tearDown() {
        cleanup.reversed().forEach { runCatching { it() } }
        dir.deleteRecursively()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private class FixedSettings(torrent: TorrentSettings) : SettingsRepository {
        override val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings(torrent = torrent))
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }

    private fun engine(port: Int, loadNative: (() -> LibtorrentNative.Result)? = null): KoinApplication {
        val settings = TorrentSettings(listenPort = port, dht = false, lsd = false, pex = false, upnp = false, wifiOnly = false, pauseOnLowBattery = false)
        val folders = DesktopTorrentFolders(File(dir, "state"), File(dir, "Downloads/Nijiyome"), File(dir, "Downloads"), File(dir, "cache"))
        val torrentModule = if (loadNative != null) desktopTorrentModule(folders, loadNative, log = {}) else desktopTorrentModule(folders, log = {})
        return koinApplication { modules(module { single<SettingsRepository> { FixedSettings(settings) }; single { okhttp3.OkHttpClient() }; single<app.nijiyome.model.SecretStore> { MemorySecrets() } }, torrentModule) }.also { k -> cleanup += { k.close() } }
    }

    private fun server(host: DesktopLinkHost): LinkServer = LinkServer.start(
        LinkConfig(dataDir = File(dir, "pc/link"), serverName = "Torrent PC", port = 0, bindIps = listOf("127.0.0.1"), platform = Platform.LINUX),
        host,
    ).also { s -> cleanup += { s.stop() } }

    private fun pair(s: LinkServer): Pair<OkHttpClient, String> {
        val payload = (PairingPayload.parse(s.openPairingWindow().uri()) as PayloadResult.Valid).payload
        val key = LinkCrypto.generateKeyPair()
        val g = (runBlocking { PairingClient().pair(payload, "Pixel", key) } as LinkResult.Ok).value
        return LinkCrypto.httpClient("127.0.0.1", s.caFingerprint, LinkCrypto.ClientKeyManager(key.private, arrayOf(g.certificate))) to g.deviceId
    }

    private fun <T : Any> waitFor(what: String, attempts: Int = 900, f: () -> T?): T {
        repeat(attempts) {
            runCatching(f).getOrNull()?.let { return it }
            Thread.sleep(100)
        }
        error("timed out waiting for $what")
    }

    @Test
    fun aTorrentSeededOverLoopbackBecomesALinkSession() {
        val ffmpeg = File(System.getenv("NIJIYOME_FFMPEG") ?: "/usr/bin/ffmpeg")
        if (!ffmpeg.isFile) return println("ffmpeg not installed: skipped")
        // libtorrent's native library first (the engine's own loader, as the app does): any libtorrent4j class needs it
        val native = LibtorrentNative.load(File(dir, "cache"))
        assertTrue(native is LibtorrentNative.Result.Loaded, "$native")
        // the clip and its torrent
        val seedDir = File(dir, "seed").apply { mkdirs() }
        val clip = File(seedDir, "Clip - 01.mkv")
        val gen = ProcessBuilder(
            ffmpeg.path, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=24:duration=8",
            "-an", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-g", "24", clip.path,
        ).redirectErrorStream(true).start()
        val out = gen.inputStream.readAllBytes().decodeToString()
        assertEquals(0, gen.waitFor(), out)
        val torrentBytes = TorrentBuilder().path(clip).pieceSize(64 * 1024).generate().entry().bencode()
        val info = TorrentInfo(torrentBytes)
        val hash = info.infoHash().toHex()

        // the engine under test (production Koin module) and the Link server over the desktop host
        val port = freePort()
        val repo = engine(port) { native }.koin.get<TorrentRepository>()
        val host = DesktopLinkHost(null, null, { null }, { emptyList() }, torrentEngine = { repo })
        val s = server(host)
        // a torrent session answers once the engine has the metadata and the first bytes (up to TORRENT_OPEN_TIMEOUT_MS)
        val phone = pair(s).first.newBuilder().readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).callTimeout(130, java.util.concurrent.TimeUnit.SECONDS).build()

        // the seeder: a running seed on loopback that keeps dialing the engine (libtorrent alone would wait a minute between tries)
        val pack = SettingsPack().apply {
            listenInterfaces("127.0.0.1:${freePort()}")
            setEnableDht(false)
            setEnableLsd(false)
            setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
            setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
        }
        val seeder = SessionManager(false)
        seeder.start(SessionParams(pack))
        cleanup += { seeder.stop() }
        val seed = AddTorrentParams().apply {
            setTorrentInfo(info)
            setSavePath(seedDir.absolutePath)
            setFlags(TorrentFlags.SEED_MODE)
        }
        val ec = org.libtorrent4j.ErrorCode(org.libtorrent4j.swig.error_code())
        val seedHandle = SessionHandle(seeder.swig()).addTorrent(seed, ec)
        assertTrue(!ec.isError && seedHandle.isValid, ec.message)
        // "Añadir torrent" with the .torrent (as W1's own test: offline, a magnet's metadata would have to come from a peer first), then
        // the phone asks for a session by info-hash; the seeder dials once the engine plays it (libtorrent retries a dropped peer
        // only a minute later)
        val torrentFile = File(dir, "Clip.torrent").apply { writeBytes(torrentBytes) }
        assertEquals(hash, runBlocking { repo.addManual(torrentFile.toPath().toUri().toString(), null, alignment = null, persist = false) }.getOrThrow())
        val pending = java.util.concurrent.CompletableFuture.supplyAsync {
            phone.call("POST", "https://127.0.0.1:${s.port}/v1/sessions", """{"source":{"kind":"torrent","uri":"$hash","media_id":"1","episode":1}}""")
        }
        Thread.sleep(1_500)
        val dialing = java.util.concurrent.atomic.AtomicBoolean(true)
        val dialer = thread(isDaemon = true) {
            while (dialing.get()) {
                runCatching { seedHandle.swig().connect_peer(TcpEndpoint("127.0.0.1", port).swig()) }
                Thread.sleep(2_000)
            }
        }
        cleanup += {
            dialing.set(false)
            dialer.join(3_000)
        }
        val created = pending.get(130, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(201, created.code, created.peekBody(2048).string() + " engine: " + repo.library.value)
        val sid = created.obj()["session_id"]!!.jsonPrimitive.content
        assertEquals(1, host.readerCount(hash))
        val base = "https://127.0.0.1:${s.port}/v1/sessions/$sid"
        val media = waitFor("media.m3u8") { phone.get("$base/media.m3u8").use { r -> if (r.code == 200) r.body.string() else null } }
        val segs = media.lines().filter { it.startsWith("seg_") }
        assertTrue(segs.size >= 4, media)
        val init = waitFor("init.mp4") { phone.get("$base/init.mp4").use { r -> if (r.code == 200) r.body.bytes() else null } }
        assertEquals("ftyp", String(init, 4, 4, Charsets.ISO_8859_1))
        val seg0 = waitFor("seg_0") { phone.get("$base/seg_0.m4s").use { r -> if (r.code == 200) r.body.bytes() else null } }
        assertTrue(seg0.size > 1000)
        println("Link torrent session: ${phone.get(base).use { it.body.string() }}")
        assertTrue(repo.library.value.any { it.id.equals(hash, ignoreCase = true) }, "the engine holds the torrent")

        // a renewal (profile change / reopen) reads the same torrent; the replaced session gives its reader back
        val renewed = phone.call("POST", "https://127.0.0.1:${s.port}/v1/sessions", """{"source":{"kind":"session","session_id":"$sid"},"start_secs":2.0}""")
        assertEquals(201, renewed.code, renewed.peekBody(2048).string())
        val sid2 = renewed.obj()["session_id"]!!.jsonPrimitive.content
        waitFor("one reader after the renewal") { host.readerCount(hash).takeIf { it == 1 } }
        assertEquals(204, phone.call("DELETE", "https://127.0.0.1:${s.port}/v1/sessions/$sid2").code)
        waitFor("no reader after the end") { host.readerCount(hash).takeIf { it == 0 } }
    }

    @Test
    fun withoutAnEngineATorrentSourceIsSourceUnavailable(): Unit = runBlocking {
        val req = TorrentRequest("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", "1", 1)
        val none = DesktopLinkHost(null, null, { null }, { emptyList() })
        assertEquals(ErrorCode.SOURCE_UNAVAILABLE, assertFailsWith<app.nijiyome.server.link.ApiException> { none.openTorrent(req) }.error)
        // the production module when libtorrent's native library does not load binds an engine that is TorrentEngineUnavailable
        val unavailable = engine(freePort()) { LibtorrentNative.Result.Failed("no native library (test)") }.koin.get<TorrentRepository>()
        val host = DesktopLinkHost(null, null, { null }, { emptyList() }, torrentEngine = { unavailable })
        assertEquals(ErrorCode.SOURCE_UNAVAILABLE, assertFailsWith<app.nijiyome.server.link.ApiException> { host.openTorrent(req) }.error)
        assertEquals(false, host.reuseTorrent("0123456789abcdef0123456789abcdef01234567"))
        // through the route: 503 source_unavailable. The machine's FFmpeg is left out (a runner may have none, and then the
        // profile check answers 422 first): a canned FFmpeg with a software encoder, so the source is what decides.
        val noFfmpegNeeded = object : app.nijiyome.server.link.LinkHost by host {
            override fun ffmpegPath(): File = File(dir, "canned-ffmpeg")
        }
        val s = LinkServer.start(
            LinkConfig(
                dataDir = File(dir, "pc/link"), serverName = "Torrent PC", port = 0, bindIps = listOf("127.0.0.1"), platform = Platform.LINUX,
                runner = app.nijiyome.server.link.CannedRunner, discoverRenderNode = false,
            ),
            noFfmpegNeeded,
        ).also { cleanup += { it.stop() } }
        val (phone, _) = pair(s)
        phone.call("POST", "https://127.0.0.1:${s.port}/v1/sessions", """{"source":{"kind":"torrent","uri":"${req.uri}","media_id":"1","episode":1}}""").use { r ->
            assertEquals(503, r.code)
            assertEquals("source_unavailable", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun theEnginesRefusalsMapToTheSessionErrors() {
        assertEquals(ErrorCode.LIBRARY_ITEM_NOT_FOUND, DesktopLinkHost.torrentError(app.nijiyome.model.AppException(app.nijiyome.model.AppError.Unknown("torrent:no_file:abc"))))
        assertEquals(ErrorCode.SOURCE_UNAVAILABLE, DesktopLinkHost.torrentError(app.nijiyome.model.AppException(app.nijiyome.model.AppError.Offline)))
        assertEquals(ErrorCode.SOURCE_UNAVAILABLE, DesktopLinkHost.torrentError(IllegalStateException("x")))
        assertEquals(app.nijiyome.model.MediaRef(anilistId = 21), DesktopLinkHost.refOf("21"))
        assertEquals(app.nijiyome.model.MediaRef(malId = 5114), DesktopLinkHost.refOf("mal:5114"))
    }

    /** The production torrent module also builds Debrid, which needs the shared OkHttpClient and a SecretStore. */
    private class MemorySecrets : app.nijiyome.model.SecretStore {
        private val map = mutableMapOf<String, String>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override suspend fun clear() = map.clear()
    }
}
