// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.review

import app.nijiyome.server.desktop.DesktopLinkHost
import app.nijiyome.server.ext.ExtServer
import app.nijiyome.server.ext.ExtServerConfig
import app.nijiyome.server.ext.ExtStartException
import app.nijiyome.server.ext.ExtState
import app.nijiyome.server.ext.FakeCatalog
import app.nijiyome.server.ext.MediaProxy
import app.nijiyome.server.link.ControlCommand
import app.nijiyome.server.link.FakeEngine
import app.nijiyome.server.link.LinkEvent
import app.nijiyome.server.link.MemHistory
import app.nijiyome.server.link.Stub
import app.nijiyome.server.link.call
import app.nijiyome.server.link.entry
import app.nijiyome.server.link.get
import app.nijiyome.server.link.harness
import app.nijiyome.server.link.media.BoundedProcess
import app.nijiyome.server.link.media.Lsfg
import app.nijiyome.server.link.media.LsfgEnv
import app.nijiyome.server.link.media.SystemLsfgEnv
import app.nijiyome.server.link.mediaPlaylist
import app.nijiyome.server.link.obj
import app.nijiyome.server.link.plainClient
import app.nijiyome.server.link.text
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.SafeHttp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The milestone 5 external review (findings 1, 2, 5, 6, 9, 13, 14, 15 and 18), one test per finding; each fails without its fix.
 * Everything listens on 127.0.0.1 with ephemeral ports.
 */
class Hito5ReviewTest {
    private val cleanup = mutableListOf<() -> Unit>()

    @AfterTest
    fun tearDown() = cleanup.reversed().forEach { runCatching { it() } }

    private fun tmp(): File = Files.createTempDirectory("review").toFile().also { d -> cleanup += { d.deleteRecursively() } }

    private fun ext(cfg: ExtServerConfig): ExtServer = ExtServer.start(cfg, FakeCatalog()).also { s -> cleanup += { s.stop() } }

    // ---- 1: the extension server binds only private / loopback IPv4 literals, like the Link server ----

    @Test
    fun finding1_theExtensionServerRefusesPublicAndNonLiteralBindAddresses() {
        for (bad in listOf("8.8.8.8", "example.com", "::")) {
            val e = assertFailsWith<ExtStartException>(bad) { ext(ExtServerConfig(File(tmp(), "server"), 0, bindIps = listOf(bad))) }
            assertTrue(e.publicBind, "$bad: ${e.message}")
        }
        val ok = ext(ExtServerConfig(File(tmp(), "server"), 0, bindIps = listOf("127.0.0.1")))
        assertTrue(ok.addresses.all { it.address.isLoopbackAddress })
    }

    // ---- 2: state.json is private and atomic; the bootstrap secret is only taken from this PC ----

    @Test
    fun finding2_stateJsonIsWrittenOwnerOnlyAndAtomically() {
        val dir = File(tmp(), "server").apply { mkdirs() }
        val file = File(dir, "state.json")
        // an older state.json left world-readable gets the same treatment when rewritten
        file.writeText("{}")
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        ExtState.open(dir)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        assertFalse(File(dir, "state.json.tmp").exists())
        assertTrue(Json.parseToJsonElement(file.readText()).jsonObject["bootstrap"]!!.jsonPrimitive.content.length >= 32)
    }

    private fun bootstrapOf(dir: File) = Json.parseToJsonElement(File(dir, "state.json").readText()).jsonObject["bootstrap"]!!.jsonPrimitive.content

    @Test
    fun finding2_theBootstrapSecretIsRefusedFromTheLan() {
        // a caller that is not this PC (what a LAN address is; tests listen on loopback only, so the check is swapped)
        val lanDir = File(tmp(), "server")
        val lan = ext(ExtServerConfig(lanDir, 0, loopbackOnly = true, localCaller = { false }))
        val base = "http://127.0.0.1:${lan.port}/api/server/v1"
        val secret = mapOf(ExtServer.BOOTSTRAP_HEADER to bootstrapOf(lanDir))
        plainClient.call("POST", "$base/setup", "{}", secret).use { assertEquals(403, it.code) }
        plainClient.call("POST", "$base/auth/login", """{"role":"admin"}""", secret).use { assertEquals(403, it.code) }
        // the real rule: loopback only
        val cfg = ExtServerConfig(File(tmp(), "x"))
        assertTrue(cfg.localCaller(InetAddress.getByName("127.0.0.1")))
        assertFalse(cfg.localCaller(InetAddress.getByName("192.168.1.20")))
        assertFalse(cfg.localCaller(null))
        // from this PC it works, and wrong secrets are throttled
        val dir = File(tmp(), "server")
        val local = ext(ExtServerConfig(dir, 0, loopbackOnly = true))
        val lb = "http://127.0.0.1:${local.port}/api/server/v1"
        plainClient.call("POST", "$lb/setup", "{}", mapOf(ExtServer.BOOTSTRAP_HEADER to bootstrapOf(dir))).use { assertEquals(200, it.code) }
        val codes = (1..12).map { plainClient.call("POST", "$lb/setup", "{}", mapOf(ExtServer.BOOTSTRAP_HEADER to "wrong")).use { it.code } }
        assertTrue(429 in codes, "$codes")
    }

    // ---- 5: one session per device under concurrency; a revocation during the preparation refuses the session ----

    @Test
    fun finding5_concurrentCreatesLeaveOneSessionAndARevocationDuringPrepareStartsNothing() {
        val engine = FakeEngine(3, 100, 1)
        val h = harness(engine).also { hh -> cleanup += { hh.close() } }
        val p = h.pair("Pixel")
        // a slow source: the preparations overlap
        val up = Stub { Thread.sleep(400); text(200, mediaPlaylist(3)) }.also { s -> cleanup += { s.close() } }
        val body = """{"source":{"kind":"url","url":"http://127.0.0.1:${up.port}/l.m3u8","container":"hls"}}"""
        val pool = Executors.newFixedThreadPool(8)
        val codes = (1..8).map { pool.submit<Int> { p.client.call("POST", h.url("/v1/sessions"), body).use { it.code } } }.map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()
        assertTrue(codes.all { it == 201 }, "$codes")
        assertEquals(1, h.link.manager.liveSessionIds(p.deviceId).size, "one session per device")

        // revoked while its source is being prepared: no session, no engine run
        val q = h.pair("Moto")
        val started = Collections.synchronizedList(mutableListOf<String>())
        h.link.onEvent { e -> if (e is LinkEvent.SessionStarted && e.deviceId == q.deviceId) started += e.sessionId }
        val runsBefore = engine.starts.size
        val pending = thread { runCatching { q.client.call("POST", h.url("/v1/sessions"), body).close() } }
        Thread.sleep(150)
        assertTrue(h.link.revoke(q.deviceId))
        pending.join(30_000)
        Thread.sleep(300)
        assertTrue(started.isEmpty(), "a session started after the revocation: $started")
        assertTrue(h.link.manager.liveSessionIds(q.deviceId).isEmpty())
        assertEquals(runsBefore, engine.starts.size, "no FFmpeg run for a revoked device")
    }

    // ---- 6: the media proxy streams past the policy timeout ----

    @Test
    fun finding6_aDirectVideoStreamsThroughTheProxyLongerThanTheTimeout() = runBlocking {
        // an upstream that sends a "video" in chunks for longer than the client's 1 s timeout (each gap shorter than it)
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).also { s -> cleanup += { s.close() } }
        val chunk = ByteArray(64 * 1024) { 7 }
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { sock ->
                    val input = sock.getInputStream()
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
                    val out = sock.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\ncontent-type: video/mp4\r\ncontent-length: ${chunk.size * 4}\r\nconnection: close\r\n\r\n".toByteArray())
                    repeat(4) {
                        out.write(chunk)
                        out.flush()
                        Thread.sleep(600)
                    }
                }
            }
        }
        val policy = NetPolicy.extensionFetch().copy(timeoutMs = 1_000, allowLoopback = true)
        val proxy = MediaProxy(tmp(), SafeHttp(policy))
        val url = proxy.mint("http://127.0.0.1:1", "http://127.0.0.1:${server.localPort}/ep.mp4", emptyList(), "s")
        val id = ExtServer.ticketId(url)
        val answer = proxy.serve(false, id, null, "http://127.0.0.1:1") { true }
        val bytes = answer.stream!!.use { it.body.bytes() }
        assertEquals(chunk.size * 4, bytes.size)
    }

    // ---- 9: temple-lsfg is found where the packages put it ----

    @Test
    fun finding9_theHelperIsFoundInThePackagedResourcesFolder() {
        val props = mapOf(
            "compose.application.resources.dir" to "/opt/nijiyome/lib/app/resources",
            "jpackage.app-path" to "/opt/nijiyome/bin/Nijiyome",
            "java.home" to "/opt/nijiyome/lib/runtime",
        )
        val dirs = SystemLsfgEnv.helperDirsFrom(props::get)
        assertEquals(File("/opt/nijiyome/lib/app/resources"), dirs.first())
        val helper = File("/opt/nijiyome/lib/app/resources/${Lsfg.HELPER_FILE_NAME}")
        val env = object : LsfgEnv {
            override fun variable(name: String): String? = null
            override fun helperDirs() = dirs
            override fun isFile(path: File) = path == helper
            override fun readText(path: File): String? = null
            override fun steamRoots(): List<File> = emptyList()
        }
        assertEquals(helper, Lsfg.findHelper(env))
        // a development run (no packaged resources) keeps the launcher / JVM folders
        assertEquals(listOf(File("/jdk/bin"), File("/jdk")), SystemLsfgEnv.helperDirsFrom(mapOf("java.home" to "/jdk")::get))
    }

    // ---- 13: wire integers are bounded, never wrapped ----

    @Test
    fun finding13_hugeIndexesAndLimitsAreBoundedNotWrapped() {
        val host = MemHistory()
        repeat(600) { host.rows += entry(it + 1, 60, 1_800_000_000L + it) }
        val engine = FakeEngine(3, 100, 1)
        val h = harness(engine) { it.host = host }.also { hh -> cleanup += { hh.close() } }
        val p = h.pair("Pixel")
        val page = p.client.get(h.url("/v1/history?limit=9223372036854775808")).obj()
        assertEquals(500, (page["items"] as JsonArray).size, "a huge limit is the largest page, not one row")
        val up = Stub { text(200, mediaPlaylist(3)) }.also { s -> cleanup += { s.close() } }
        val sid = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:${up.port}/l.m3u8","container":"hls"}}""").obj()["session_id"]!!.jsonPrimitive.content
        for (k in listOf("4294967295", "2147483648", "43201")) {
            p.client.get(h.url("/v1/sessions/$sid/seg_$k.m4s")).use { r ->
                assertEquals(404, r.code, k)
                assertEquals("session_not_found", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
            }
        }
        assertTrue(engine.starts.none { it < 0 }, "${engine.starts}")
        val open = DesktopLinkHost.remoteCommand(ControlCommand.Open("21", 4_294_967_295L, 4_294_967_295L)) as app.nijiyome.model.PcRemoteCommand.Open
        assertEquals(Int.MAX_VALUE, open.episode)
        assertEquals(Int.MAX_VALUE, open.positionSecs)
    }

    // ---- 14: DNS rebinding: a foreign Host is refused ----

    private fun raw(port: Int, host: String, origin: String?): String = Socket("127.0.0.1", port).use { s ->
        s.soTimeout = 5_000
        val o = origin?.let { "Origin: $it\r\n" }.orEmpty()
        s.getOutputStream().write("GET /api/server/v1/health HTTP/1.1\r\nHost: $host\r\n${o}Connection: close\r\n\r\n".toByteArray())
        s.getInputStream().readAllBytes().decodeToString()
    }

    @Test
    fun finding14_aRebindingHostIsRefusedAndGetsNoCors() {
        val s = ext(ExtServerConfig(File(tmp(), "server"), 0, loopbackOnly = true))
        val evil = raw(s.port, "evil.example:${s.port}", "http://evil.example:${s.port}")
        assertTrue(evil.startsWith("HTTP/1.1 403"), evil.lineSequence().first())
        assertTrue("host_denied" in evil)
        assertFalse("access-control-allow-origin: http://evil.example" in evil.lowercase())
        for (ok in listOf("127.0.0.1:${s.port}", "localhost:${s.port}", "mi-pc.local:${s.port}", "[::1]:${s.port}")) {
            assertTrue(raw(s.port, ok, null).startsWith("HTTP/1.1 200"), ok)
        }
        assertTrue(ExtServer.hostAllowed(null))
        assertFalse(ExtServer.hostAllowed("192.168.1.20.evil.example"))
        assertFalse(ExtServer.originAllowed("http://evil.example:8099", "evil.example:8099", emptyList()))
        assertTrue(ExtServer.originAllowed("http://192.168.1.20:8099", "192.168.1.20:8099", emptyList()))
    }

    // ---- 15: playlists pointing outside http(s) are never handed to the player ----

    @Test
    fun finding15_playlistsWithOtherSchemesAreRefused() {
        val base = "https://cdn.example/hls/index.m3u8".toHttpUrl()
        val mint: (okhttp3.HttpUrl) -> String = { "/media/x/${it.encodedPath.substringAfterLast('/')}" }
        for (bad in listOf("file:///etc/passwd", "data:video/mp2t;base64,AAAA", "skd://key", "javascript:alert(1)")) {
            assertNull(MediaProxy.rewritePlaylist("#EXTM3U\n#EXTINF:2,\n$bad\n", base, mint), "segment $bad")
            assertNull(MediaProxy.rewritePlaylist("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"$bad\"\n#EXTINF:2,\na.ts\n", base, mint), "key $bad")
        }
        val ok = MediaProxy.rewritePlaylist("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k.bin\"\n#EXTINF:2,\na.ts\n", base, mint)!!
        assertTrue("URI=\"/media/x/k.bin\"" in ok && "\n/media/x/a.ts\n" in ok, ok)
    }

    // ---- 18: a hung ffprobe / ffmpeg never blocks the reader ----

    @Test
    fun finding18_aHungToolIsKilledAtItsDeadline() {
        val started = System.nanoTime()
        assertNull(BoundedProcess.run(listOf("/bin/sh", "-c", "sleep 30"), 1024, 500))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000, "the watchdog killed it (and its child)")
        assertEquals("hi\n", BoundedProcess.run(listOf("/bin/sh", "-c", "echo hi"), 1024, 5_000))
        // the library probe with an ffprobe that hangs
        val dir = tmp()
        val fake = File(dir, "ffprobe").apply {
            writeText("#!/bin/sh\nsleep 30\n")
            setExecutable(true)
        }
        val t0 = System.nanoTime()
        assertNull(DesktopLinkHost.probe(fake, File(dir, "x.mkv"), timeoutMs = 500))
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000)
    }
}
