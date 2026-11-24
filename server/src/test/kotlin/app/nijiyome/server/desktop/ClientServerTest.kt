// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.database.HistoryRow
import app.nijiyome.database.NijiyomeDatabase
import app.nijiyome.database.open
import app.nijiyome.link.AesGcmKeyCipher
import app.nijiyome.link.LibraryDto
import app.nijiyome.link.LinkHistoryRemoteImpl
import app.nijiyome.link.LinkHistoryRow
import app.nijiyome.link.LinkKeyVault
import app.nijiyome.link.LinkRepositoryImpl
import app.nijiyome.model.LinkSource
import app.nijiyome.model.LinkState
import app.nijiyome.server.link.LinkConfig
import app.nijiyome.server.link.LinkServer
import app.nijiyome.server.link.media.Platform
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.time.Instant
import javax.crypto.KeyGenerator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The phone's real Link code (`:core:link` `LinkRepositoryImpl` + `LinkHistoryRemoteImpl`, software key cipher instead of the
 * Android keystore) against the real desktop server in-process: the desktop host over a real Room database and a library folder
 * with a clip FFmpeg generates (video only, no audio track), the production engine (FFmpeg, software encoder), 127.0.0.1 and an
 * ephemeral port. Pair with the payload text, read capabilities and the library, play the clip (master → media → init → segments),
 * push and pull history, revoke. Skipped when this machine has no FFmpeg.
 */
class ClientServerTest {
    private val cleanup = mutableListOf<() -> Unit>()

    @AfterTest
    fun tearDown() = cleanup.reversed().forEach { runCatching { it() } }

    private fun ffmpeg(): File? = (System.getenv("NIJIYOME_FFMPEG")?.let(::File) ?: File("/usr/bin/ffmpeg")).takeIf { it.isFile }

    @Test
    fun thePhonesLinkCodePairsPlaysSyncsHistoryAndIsRevoked() = runBlocking {
        val ffmpeg = ffmpeg() ?: return@runBlocking println("ffmpeg not installed: skipped")
        if (!File(ffmpeg.parentFile, "ffprobe").isFile) return@runBlocking println("ffprobe not installed: skipped")
        val root = Files.createTempDirectory("nijiyome-client-server").toFile().also { d -> cleanup += { d.deleteRecursively() } }
        val library = File(root, "library").apply { mkdirs() }
        val clip = File(library, "Test Clip 01.mkv")
        val gen = ProcessBuilder(
            ffmpeg.path, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=24:duration=8",
            "-an", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-g", "24", clip.path,
        ).redirectErrorStream(true).start()
        val genOut = gen.inputStream.readAllBytes().decodeToString()
        assertEquals(0, gen.waitFor(), genOut)

        // the PC: Room history + desktop host + production engine
        val db = NijiyomeDatabase.open(File(root, "pc/nijiyome.db")).also { d -> cleanup += { d.close() } }
        val history = RoomLinkHistory(db.history(), File(root, "pc/link/history-origins.json"))
        val host = DesktopLinkHost(history, null, { library }, { emptyList() })
        val server = LinkServer.start(
            // production detection: the VA-API render node when there is one, software (libx264 / OpenH264) otherwise
            LinkConfig(dataDir = File(root, "pc/link"), serverName = "Test PC", port = 0, bindIps = listOf("127.0.0.1"), platform = Platform.LINUX),
            host,
        ).also { s -> cleanup += { s.stop() } }

        // the phone: the real repository with a software-sealed key
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val repo = LinkRepositoryImpl(LinkKeyVault(File(root, "phone"), AesGcmKeyCipher { aes }))
        val payload = server.openPairingWindow().uri()
        val paired = repo.pair(payload, "Pixel 8").getOrThrow()
        assertEquals("Test PC", paired.serverName)
        assertIs<LinkState.Paired>(repo.state.value)
        assertEquals(1, server.devices().size)

        val caps = repo.capabilities().getOrThrow()
        assertTrue(caps.profiles.any { it.id == "p-none" }, "${caps.profiles}")

        val http = assertNotNull(repo.playbackClient())
        fun fetch(url: String): okhttp3.Response = http.newCall(Request.Builder().url(url).build()).execute()
        // the phone has no library screen yet: the library is read with its own mTLS client and its own DTO
        val lib = fetch("https://127.0.0.1:${server.port}/v1/library").use { kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(LibraryDto.serializer(), it.body.string()) }
        assertEquals(1, lib.items.size, "$lib")
        val item = lib.items.single()
        assertEquals("Test Clip 01", item.title)

        val session = repo.startSession(LinkSource.Library(item.id), "p-none").getOrThrow()
        val stateUrl = session.playbackUrl.removeSuffix("/master.m3u8")
        val master = waitFor("master.m3u8", { fetch(stateUrl).use { it.body.string() } }) { fetch(session.playbackUrl).use { r -> if (r.code == 200) r.body.string() else null } }
        println("Link session: ${fetch(stateUrl).use { it.body.string() }}")
        assertTrue(master.startsWith("#EXTM3U"), master)
        val mediaUrl = session.playbackUrl.toHttpUrl().resolve("media.m3u8")!!.toString()
        val media = waitFor("media.m3u8") { fetch(mediaUrl).use { r -> if (r.code == 200) r.body.string() else null } }
        assertTrue("#EXT-X-MAP:URI=\"init.mp4\"" in media, media)
        val segs = media.lines().filter { it.startsWith("seg_") }
        assertTrue(segs.size >= 4, "an 8 s clip is at least 4 two-second segments: $media")
        val init = waitFor("init.mp4") { fetch(mediaUrl.replace("media.m3u8", "init.mp4")).use { r -> if (r.code == 200) r.body.bytes() else null } }
        assertTrue(String(init, 4, 4, Charsets.ISO_8859_1) == "ftyp", "init.mp4 starts with an ftyp box")
        for (s in segs.take(2)) {
            val seg = waitFor(s) { fetch(mediaUrl.replace("media.m3u8", s)).use { r -> if (r.code == 200) r.body.bytes() else null } }
            assertTrue(seg.size > 1000, "$s is ${seg.size} bytes")
            val boxes = String(seg, 4, 4, Charsets.ISO_8859_1)
            assertTrue(boxes == "styp" || boxes == "moof", "$s starts with $boxes")
        }
        assertTrue(repo.heartbeat(session.sessionId, 2.0, false).isSuccess)
        repo.stop(session.sessionId)

        // history: phone -> PC (merged into Room) and PC -> phone
        val remote = LinkHistoryRemoteImpl(repo)
        val now = Instant.now().epochSecond
        val pushed = remote.push(
            listOf(LinkHistoryRow("21", "One Piece", null, "21?ep=1", 1, 600, 1440, false, Instant.ofEpochSecond(now - 60).toString(), 21, null)),
        )
        assertEquals(1, pushed.merged)
        val inRoom = assertNotNull(db.history().get("21?ep=1"))
        assertEquals(600, inRoom.progressSec)
        db.history().upsert(HistoryRow("1535?ep=3", "1535", "Death Note", null, 3, 100, 1380, false, (now - 30) * 1000, 1535, null))
        val page = remote.since(0)
        assertEquals(setOf("21?ep=1", "1535?ep=3"), page.items.map { it.episodeId }.toSet())
        assertEquals(mapOf(server.devices().single().id to 1), history.originCounts())

        // revoke: the phone loses access at once
        assertTrue(server.revoke(server.devices().single().id))
        val after = repo.capabilities()
        assertTrue(after.isFailure, "a revoked phone must not read the capabilities")
    }

    private fun <T : Any> waitFor(what: String, diag: () -> String = { "" }, f: () -> T?): T {
        repeat(300) {
            runCatching(f).getOrNull()?.let { return it }
            Thread.sleep(100)
        }
        error("timed out waiting for $what ${runCatching(diag).getOrElse { it.toString() }}")
    }
}
