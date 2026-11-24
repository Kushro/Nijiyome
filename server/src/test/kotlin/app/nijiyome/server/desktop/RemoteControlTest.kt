// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.link.LinkCrypto
import app.nijiyome.link.LinkResult
import app.nijiyome.link.PairingClient
import app.nijiyome.link.PairingPayload
import app.nijiyome.link.PayloadResult
import app.nijiyome.model.LinkSource
import app.nijiyome.model.PcPlayerHub
import app.nijiyome.model.PcPlayerState
import app.nijiyome.model.PcRemoteCommand
import app.nijiyome.model.SendToPhoneRequest
import app.nijiyome.server.link.ControlCommand
import app.nijiyome.server.link.LinkConfig
import app.nijiyome.server.link.LinkServer
import app.nijiyome.server.link.TestHost
import app.nijiyome.server.link.call
import app.nijiyome.server.link.get
import app.nijiyome.server.link.media.Platform
import app.nijiyome.server.link.obj
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Remote control of the desktop player (tk-link `/v1/control`, `/v1/player`; tk-app live/link.rs `control` / `player_state`) through
 * [PcPlayerHub], the way the player screen plugs in, and "Enviar a dispositivo" (tk-app `send_to_device`: a session owned by the phone
 * plus the `handoff` its events long-poll delivers) through [DesktopServers].
 */
class RemoteControlTest {
    private val dir: File = Files.createTempDirectory("nijiyome-link-remote").toFile()
    private val cleanup = mutableListOf<() -> Unit>()

    @AfterTest
    fun tearDown() {
        cleanup.reversed().forEach { runCatching { it() } }
        dir.deleteRecursively()
    }

    private fun pair(s: LinkServer, uri: String = s.openPairingWindow().uri()): Pair<OkHttpClient, String> {
        val payload = (PairingPayload.parse(uri) as PayloadResult.Valid).payload
        val key = LinkCrypto.generateKeyPair()
        val g = (runBlocking { PairingClient().pair(payload, "Pixel", key) } as LinkResult.Ok).value
        return LinkCrypto.httpClient("127.0.0.1", s.caFingerprint, LinkCrypto.ClientKeyManager(key.private, arrayOf(g.certificate))) to g.deviceId
    }

    private fun code(r: okhttp3.Response): String = r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    @Test
    fun thePhoneSeesAndDrivesThePlayerOpenOnThePc(): Unit = runBlocking {
        val hub = PcPlayerHub()
        val host = DesktopLinkHost(null, null, { null }, { emptyList() }, player = hub)
        val s = LinkServer.start(LinkConfig(dataDir = File(dir, "link"), serverName = "PC", port = 0, bindIps = listOf("127.0.0.1"), platform = Platform.LINUX), host)
        cleanup += { s.stop() }
        val (phone, _) = pair(s)
        val base = "https://127.0.0.1:${s.port}"
        fun control(body: String) = phone.call("POST", "$base/v1/control", body)

        // no player open: 404 / 409 no_player, except "open"
        phone.get("$base/v1/player").use { r ->
            assertEquals(404, r.code)
            assertEquals("no_player", code(r))
        }
        control("""{"command":"toggle"}""").use { r ->
            assertEquals(409, r.code)
            assertEquals("no_player", code(r))
        }
        // subscribed right away (the request below blocks this thread until the server answered)
        val opened = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { hub.opens.first() } }
        control("""{"command":"open","mediaId":"21","episode":3,"positionSecs":120}""").use { assertEquals(204, it.code) }
        assertEquals(PcRemoteCommand.Open("21", 3, 120), opened.await())

        // the player screen attaches and reports what it plays
        val owner = Any()
        val got = Collections.synchronizedList(mutableListOf<PcRemoteCommand>())
        hub.attach(owner) { got += it; true }
        hub.publish(owner, PcPlayerState(playing = true, title = "One Piece", episode = 3, positionSecs = 125, durationSecs = 1440, mediaId = "21"))
        val st = phone.get("$base/v1/player").obj()
        assertEquals("true", st["playing"]!!.jsonPrimitive.content)
        assertEquals("One Piece", st["title"]!!.jsonPrimitive.content)
        assertEquals(125, st["positionSecs"]!!.jsonPrimitive.content.toInt())
        assertEquals("21", st["mediaId"]!!.jsonPrimitive.content)

        // `play` while playing changes nothing and never reaches the player; the rest does
        control("""{"command":"play"}""").use { assertEquals(204, it.code) }
        for (c in listOf("""{"command":"pause"}""", """{"command":"toggle"}""", """{"command":"next"}""", """{"command":"prev"}""", """{"command":"seek","seconds":90}""")) {
            control(c).use { assertEquals(204, it.code, c) }
        }
        assertEquals(listOf(PcRemoteCommand.Pause, PcRemoteCommand.Toggle, PcRemoteCommand.Next, PcRemoteCommand.Prev, PcRemoteCommand.Seek(90)), got.toList())
        hub.publish(owner, PcPlayerState(playing = false, title = "One Piece", episode = 3, positionSecs = 90, durationSecs = 1440, mediaId = "21"))
        control("""{"command":"pause"}""").use { assertEquals(204, it.code) }
        assertEquals(5, got.size, "pause while paused is redundant")
        // malformed commands are refused before the player
        control("""{"command":"seek","seconds":90000}""").use { assertEquals(400, it.code) }

        // the screen leaves: no player again
        hub.detach(owner)
        phone.get("$base/v1/player").use { assertEquals(404, it.code) }
        // a stale screen that detaches after a newer one took over does not clear it
        val a = Any()
        val b = Any()
        hub.attach(a) { true }
        hub.attach(b) { true }
        hub.publish(b, PcPlayerState(true, null, 1, 0, null, null))
        hub.detach(a)
        phone.get("$base/v1/player").use { assertEquals(200, it.code) }
    }

    @Test
    fun wireCommandsMapToThePlayersCommands() {
        assertEquals(PcRemoteCommand.Toggle, DesktopLinkHost.remoteCommand(ControlCommand.Toggle))
        assertEquals(PcRemoteCommand.Seek(9), DesktopLinkHost.remoteCommand(ControlCommand.Seek(9)))
        assertEquals(PcRemoteCommand.Open("mal:5114", 2, 0), DesktopLinkHost.remoteCommand(ControlCommand.Open("mal:5114", 2)))
        val noPlayer = DesktopLinkHost(null, null, { null }, { emptyList() })
        assertEquals(false, noPlayer.control(ControlCommand.Open("21", 1)))
        assertEquals(null, noPlayer.playerState())
    }

    /** A host whose library is one generated clip and whose FFmpeg is the real one. */
    private class ClipHost(private val clip: File, private val ffmpeg: File) : TestHost() {
        init {
            files[app.nijiyome.server.link.Library.idFor(clip)] = clip
        }
        override fun ffmpegPath(): File = ffmpeg
    }

    @Test
    fun sendToPhoneStartsASessionForThePhoneAndQueuesTheHandoff(): Unit = runBlocking {
        val ffmpeg = File(System.getenv("NIJIYOME_FFMPEG") ?: "/usr/bin/ffmpeg")
        if (!ffmpeg.isFile) return@runBlocking println("ffmpeg not installed: skipped")
        val clip = File(dir, "Ep 03.mkv")
        val gen = ProcessBuilder(
            ffmpeg.path, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=24:duration=6",
            "-an", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", clip.path,
        ).redirectErrorStream(true).start()
        assertEquals(0, gen.waitFor(), gen.inputStream.readAllBytes().decodeToString())
        ServerSettings.save(File(dir, "link/server-settings.json"), ServerSettings(linkEnabled = true, port = 0, announce = false, bindIps = listOf("127.0.0.1")))
        val servers = DesktopServers(dir, { ClipHost(clip, ffmpeg) }, null, announcer = { _, _, _, _, _ -> app.nijiyome.server.link.Announcement {} }, bindIps = listOf("127.0.0.1"))
        cleanup += { servers.close() }
        servers.start()
        repeat(100) { if (!servers.state.value.running) Thread.sleep(50) }
        servers.openPairing()
        repeat(100) { if (servers.state.value.pairing == null) Thread.sleep(50) }
        val link = servers.state.value
        assertTrue(link.running && link.pairing != null, "$link")
        val linkServer = servers.linkServer!!
        val (phone, deviceId) = pair(linkServer, link.pairing!!.uri)
        repeat(100) { if (servers.phones.value.isEmpty()) Thread.sleep(50) }
        assertEquals(listOf(deviceId), servers.phones.value.map { it.id })

        val item = app.nijiyome.server.link.Library.idFor(clip)
        val ok = servers.send(deviceId, SendToPhoneRequest(mediaId = "21", episode = 3, positionSecs = 2, title = "One Piece", source = LinkSource.Library(item)))
        assertTrue(ok.isSuccess, "$ok")
        val events = phone.get("https://127.0.0.1:${linkServer.port}/v1/events?wait=5").obj()["events"] as JsonArray
        val handoff = events.single().jsonObject
        assertEquals("handoff", handoff["type"]!!.jsonPrimitive.content)
        assertEquals("21", handoff["mediaId"]!!.jsonPrimitive.content)
        assertEquals(3, handoff["episode"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, handoff["positionSecs"]!!.jsonPrimitive.content.toInt())
        assertEquals("One Piece", handoff["title"]!!.jsonPrimitive.content)
        val sid = handoff["sessionId"]!!.jsonPrimitive.content
        // the session belongs to that phone, which can play it
        assertEquals(200, phone.get("https://127.0.0.1:${linkServer.port}/v1/sessions/$sid").use { it.code })

        // refusals come back as the Link error code; unknown phones are refused
        val missing = servers.send(deviceId, SendToPhoneRequest("21", 3, 0, "x", LinkSource.Library("0".repeat(32))))
        assertEquals("library_item_not_found", missing.exceptionOrNull()?.message)
        val stranger = servers.send("00000000-0000-4000-8000-000000000000", SendToPhoneRequest("21", 3, 0, "x", LinkSource.Library(item)))
        assertEquals("device_not_authorized", stranger.exceptionOrNull()?.message)
    }
}
