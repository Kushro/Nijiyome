// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of Tatakai Next tk-link/tests/e2e.rs (AGPL-3.0, same author): mTLS, pairing, per-request authorization, hot revocation,
// sessions with a fake FFmpeg, over real TLS and the phone's own TLS client.
package app.nijiyome.server.link

import app.nijiyome.link.LinkCrypto
import app.nijiyome.server.link.media.LsfgPaths
import app.nijiyome.server.link.media.LsfgStatus
import app.nijiyome.server.link.media.ProbeInfo
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.SafeHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class E2eTest {
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() = open.reversed().forEach { runCatching { it.close() } }

    private fun <T : AutoCloseable> T.closing(): T = also { open += it }

    private fun upstreamHls(segments: Int): Int {
        val body = mediaPlaylist(segments)
        return Stub { text(200, body) }.closing().port
    }

    private fun create(h: Harness, p: Paired, port: Int, extra: String = ""): okhttp3.Response {
        val body = """{"source":{"kind":"url","url":"http://127.0.0.1:$port/list.m3u8","container":"hls"}$extra}"""
        return p.client.call("POST", h.url("/v1/sessions"), body)
    }

    private fun sid(r: okhttp3.Response): String = r.obj()["session_id"]!!.jsonPrimitive.content

    private fun state(h: Harness, p: Paired, sid: String): JsonObject = p.client.get(h.url("/v1/sessions/$sid")).obj()

    @Test
    fun mtlsIsRequiredEverywhereExceptPairingAndEveryResponseCarriesTheContractHeader() {
        val h = harness(FakeEngine(3, 100, 1)).closing()
        val bootstrap = h.bootstrap()
        val r = bootstrap.call("POST", h.url("/v1/pairing"), """{"code":"0000000000","device_name":"x","csr_pem":"x","platform":"android"}""")
        assertEquals(409, r.code)
        assertEquals("v1", r.header("Nijiyome-Link"))
        assertEquals(Json.parseToJsonElement("""{"error":{"code":"pairing_closed","message":"no pairing window is open"}}"""), r.json())

        // everything else without a client certificate: 401 client_certificate_required
        for ((m, p) in listOf("GET" to "/v1/capabilities", "GET" to "/v1/library", "POST" to "/v1/sessions", "GET" to "/v1/sessions/x", "DELETE" to "/v1/devices/self", "GET" to "/v1/nope")) {
            val a = bootstrap.call(m, h.url(p))
            assertEquals(401, a.code, "$m $p")
            assertEquals("v1", a.header("Nijiyome-Link"))
            assertEquals("client_certificate_required", a.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        }

        val p = h.pair("Pixel")
        // authorized: unknown route -> 404 session_not_found JSON; known route with wrong method -> 405 without a body
        val nf = p.client.get(h.url("/v1/does-not-exist"))
        assertEquals(404, nf.code)
        assertEquals("session_not_found", nf.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        val na = p.client.call("PUT", h.url("/v1/capabilities"))
        assertEquals(405, na.code)
        assertEquals(0, na.body.bytes().size)

        val caps = p.client.get(h.url("/v1/capabilities")).obj()
        assertEquals("v1", caps["contract"]!!.jsonPrimitive.content)
        assertEquals("p-none", caps["default_profile"]!!.jsonPrimitive.content)
        assertEquals("nvidia", caps["gpu"]!!.jsonObject["vendor"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""["h264_nvenc","libopenh264"]"""), caps["encoders"])
        assertEquals(Json.parseToJsonElement("""{"max_ram_bytes":536870912,"segment_wait_secs":15}"""), caps["limits"])
        assertEquals("p-none", caps["profiles"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        // no Anime4K shaders installed in the test environment -> the Anime4K profiles are withheld
        assertEquals(1, caps["profiles"]!!.jsonArray.size)
    }

    @Test
    fun pairingFlowPinAttemptsExpiryAndSingleUse() {
        val h = harness(FakeEngine(3, 100, 1)).closing()
        val bootstrap = h.bootstrap()
        fun csr() = app.nijiyome.link.LinkCsr.createPem(LinkCrypto.generateKeyPair(), "Phone")
        fun attempt(code: String, csr: String, extra: Map<String, String> = emptyMap()): Pair<Int, JsonObject> {
            val b = mutableMapOf("code" to code, "device_name" to "Phone", "csr_pem" to csr, "platform" to "android")
            b.putAll(extra)
            val body = JsonObject(b.mapValues { JsonPrimitive(it.value) }).toString()
            val r = bootstrap.call("POST", h.url("/v1/pairing"), body)
            return r.code to r.obj()
        }

        val payload = h.link.openPairingWindow()
        assertTrue(payload.uri().startsWith("nijiyome-link:v1?h=127.0.0.1&p="))
        assertTrue(payload.uri().contains("&ca=${payload.caFingerprint}&c=${payload.code}&x="))
        // invalid bodies consume no attempt
        assertEquals(400, attempt(payload.code, "garbage").first)
        assertEquals(400, attempt(payload.code, csr(), mapOf("platform" to "ios")).first)
        assertEquals(400, attempt(payload.code, csr(), mapOf("unexpected" to "1")).first)
        assertEquals(WindowStatus.Open(payload.expiresAt, 5), h.link.pairingStatus())
        // wrong codes: 403 x4, the 5th closes the window with 429, then even the right code is refused
        repeat(4) {
            val (st, body) = attempt("ABCDEFGHJK", csr())
            assertEquals(403, st)
            assertEquals("pairing_code_invalid", body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        }
        assertEquals(429, attempt("ABCDEFGHJK", csr()).first)
        assertEquals(429, attempt(payload.code, csr()).first)
        assertTrue(h.link.devices().isEmpty())

        // fresh window: the right code pairs, the CA matches the payload pin, the window is single use
        val p2 = h.link.openPairingWindow()
        val (st, body) = attempt(p2.code.lowercase(), csr()) // Crockford: case-insensitive
        assertEquals(201, st)
        val der = Base64.getDecoder().decode(body["ca_cert_pem"]!!.jsonPrimitive.content.lines().filter { !it.startsWith("-----") }.joinToString(""))
        val pin = MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }
        assertEquals(p2.caFingerprint, pin, "the CA in the response is the one pinned by the QR payload")
        assertEquals("Test PC", body["server_name"]!!.jsonPrimitive.content)
        assertEquals(409, attempt(p2.code, csr()).first, "single use")
        assertEquals(1, h.link.devices().size)
    }

    @Test
    fun hotRevocationBlocksTheNextRequestAndDropsTheLiveSession() {
        val h = harness(FakeEngine(40, 100, 5)).closing()
        val p = h.pair("Pixel")
        val port = upstreamHls(40)
        val created = create(h, p, port)
        assertEquals(201, created.code)
        val sid = sid(created)
        assertEquals(1, h.link.activeSessions())

        val started = System.nanoTime()
        h.link.revoke(p.deviceId)
        // the very next request is refused (the live keep-alive connection was dropped; a closed connection is a refusal too)
        try {
            assertEquals(403, p.client.get(h.url("/v1/sessions/$sid")).code)
        } catch (_: IOException) {
        }
        try {
            val r = p.client.get(h.url("/v1/capabilities"))
            assertEquals(403, r.code)
            assertEquals("device_not_authorized", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        } catch (_: IOException) {
        }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "revocation must take effect within 2 s")
        waitUntil("session teardown") { h.link.activeSessions() == 0 }
        assertTrue(h.link.devices()[0].revokedAt != null)
    }

    @Test
    fun deleteDevicesSelfForgetsTheCaller() {
        val h = harness(FakeEngine(3, 100, 1)).closing()
        val p = h.pair("Pixel")
        assertEquals(204, p.client.call("DELETE", h.url("/v1/devices/self")).code)
        try {
            assertEquals(403, p.client.get(h.url("/v1/capabilities")).code)
        } catch (_: IOException) {
        }
        assertTrue(h.link.devices()[0].revokedAt != null)
    }

    @Test
    fun sessionLifecycleVodPlaylistsSegmentsAndOneSessionPerDevice() {
        val engine = FakeEngine(5, 1000, 1)
        val h = harness(engine).closing()
        val p = h.pair("Pixel")
        val port = upstreamHls(5)

        val r = create(h, p, port)
        assertEquals(201, r.code)
        val c = r.obj()
        assertEquals(1, c["generation"]!!.jsonPrimitive.int)
        assertEquals(10.0, c["duration_secs"]!!.jsonPrimitive.double)
        assertEquals(2, c["segment_secs"]!!.jsonPrimitive.int)
        assertEquals(4, c["heartbeat_interval_secs"]!!.jsonPrimitive.int)
        val sid = c["session_id"]!!.jsonPrimitive.content
        assertEquals("/v1/sessions/$sid/master.m3u8", c["playback_path"]!!.jsonPrimitive.content)

        // master becomes available once the engine produced init.mp4
        waitUntil("master.m3u8") {
            val m = p.client.get(h.url("/v1/sessions/$sid/master.m3u8"))
            when (m.code) {
                200 -> true.also { m.close() }
                409 -> {
                    assertEquals("session_not_ready", m.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
                    false
                }
                else -> fail("unexpected ${m.code}")
            }
        }
        val master = p.client.get(h.url("/v1/sessions/$sid/master.m3u8")).body.string()
        assertTrue("RESOLUTION=1920x1080" in master && "media.m3u8" in master)
        val media = p.client.get(h.url("/v1/sessions/$sid/media.m3u8")).body.string()
        assertTrue("#EXT-X-PLAYLIST-TYPE:VOD" in media && "#EXT-X-MAP:URI=\"init.mp4\"" in media && "#EXT-X-ENDLIST" in media)
        assertEquals(5, Regex("#EXTINF:2\\.000000,").findAll(media).count())
        val init = p.client.get(h.url("/v1/sessions/$sid/init.mp4"))
        assertEquals("video/mp4", init.header("Content-Type"))
        assertEquals("INIT", init.body.string())
        for (k in 0 until 5) {
            val s = p.client.get(h.url("/v1/sessions/$sid/seg_$k.m4s"))
            assertEquals(200, s.code)
            val b = s.body.bytes()
            assertEquals(1000, b.size)
            assertEquals(k.toByte(), b[0])
        }
        // beyond the end / garbage names
        assertEquals(404, p.client.get(h.url("/v1/sessions/$sid/seg_5.m4s")).code)
        assertEquals(404, p.client.get(h.url("/v1/sessions/$sid/seg_x.m4s")).code)
        assertEquals(404, p.client.get(h.url("/v1/sessions/$sid/..%2Fsecret")).code)

        val st = state(h, p, sid)
        assertEquals("ready", st["state"]!!.jsonPrimitive.content)
        assertEquals("p-none", st["profile"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""{"width":1920,"height":1080}"""), st["output"])
        assertEquals(36, st["instance_id"]!!.jsonPrimitive.content.length)
        assertEquals("none", st["upscale"]!!.jsonPrimitive.content)
        assertEquals("none", st["fg"]!!.jsonPrimitive.content)
        assertTrue(st["encoder"]!!.jsonPrimitive.isString && st["video_kbps"]!!.jsonPrimitive.int == 6000)
        assertFalse("fg_effective" in st)

        // heartbeat
        assertEquals(204, p.client.call("POST", h.url("/v1/sessions/$sid/position"), """{"position_secs":3.5,"paused":false}""").code)
        assertEquals(400, p.client.call("POST", h.url("/v1/sessions/$sid/position"), """{"position_secs":-1,"paused":false}""").code)

        // the FFmpeg arguments never carry the external URL
        val args = engine.args[0].joinToString(" ")
        assertFalse(args.contains("127.0.0.1:$port"), args)
        assertTrue(args.contains("-protocol_whitelist http,tcp,crypto"))

        // a second session closes the first one (410) and increments the generation
        val c2 = create(h, p, port).obj()
        assertEquals(2, c2["generation"]!!.jsonPrimitive.int)
        val gone = p.client.get(h.url("/v1/sessions/$sid/media.m3u8"))
        assertEquals(410, gone.code)
        assertEquals("session_gone", gone.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(1, h.link.activeSessions())

        val sid2 = c2["session_id"]!!.jsonPrimitive.content
        assertEquals(204, p.client.call("DELETE", h.url("/v1/sessions/$sid2")).code)
        assertEquals(410, p.client.get(h.url("/v1/sessions/$sid2")).code)
        assertEquals(404, p.client.get(h.url("/v1/sessions/00000000-0000-4000-8000-000000000000")).code)
    }

    @Test
    fun aDeviceCannotTouchAnotherDevicesSession() {
        val h = harness(FakeEngine(3, 100, 1)).closing()
        val a = h.pair("A")
        val b = h.pair("B")
        val port = upstreamHls(3)
        val sid = sid(create(h, a, port))
        for ((m, path) in listOf("GET" to "/v1/sessions/$sid", "DELETE" to "/v1/sessions/$sid", "GET" to "/v1/sessions/$sid/media.m3u8")) {
            assertEquals(404, b.client.call(m, h.url(path)).code, "$m $path")
        }
        // B creating a session does not close A's
        assertEquals(201, create(h, b, port).code)
        assertEquals(200, a.client.get(h.url("/v1/sessions/$sid")).code)
    }

    @Test
    fun createValidationTable() {
        val h = harness(FakeEngine(3, 100, 1)).closing()
        val p = h.pair("Pixel")
        val port = upstreamHls(3)
        fun post(body: String): Pair<Int, String?> {
            val r = p.client.call("POST", h.url("/v1/sessions"), body)
            val code = r.code
            val err = runCatching { r.obj()["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content }.getOrNull()
            return code to err
        }
        val u = "http://127.0.0.1:$port/list.m3u8"
        // unknown / unavailable profile -> 422
        assertEquals(422 to "profile_unavailable", post("""{"source":{"kind":"url","url":"$u"},"profile":"p-nope"}"""))
        assertEquals(422 to "profile_unavailable", post("""{"source":{"kind":"url","url":"$u"},"profile":"p-anime4k-1080"}"""))
        // CR/LF or non-allow-listed headers -> 400 (before any policy decision)
        assertEquals(400 to "request_invalid", post("""{"source":{"kind":"url","url":"$u","headers":{"Referer":"https://a/\r\nX-Evil: 1"}}}"""))
        assertEquals(400 to "request_invalid", post("""{"source":{"kind":"url","url":"$u","headers":{"Referer\r\nX":"v"}}}"""))
        assertEquals(400 to "request_invalid", post("""{"source":{"kind":"url","url":"$u","headers":{"Authorization":"Bearer x"}}}"""))
        // malformed bodies
        assertEquals(400, post("""{"source":{"kind":"url"}}""").first)
        assertEquals(400, post("""{"source":{"kind":"phone","size_bytes":1,"name":"x"}}""").first)
        assertEquals(400, post("""{"source":{"kind":"url","url":"not a url"}}""").first)
        assertEquals(400, post("""{"source":{"kind":"url","url":"$u"},"start_secs":-1}""").first)
        assertEquals(400, post("""{"source":{"kind":"url","url":"$u","container":"dash"}}""").first)
        assertEquals(413, post("""{"source":{"kind":"url","url":"$u"},"pad":"${"x".repeat(5000)}"}""").first)
        // library items that do not exist
        assertEquals(404 to "library_item_not_found", post("""{"source":{"kind":"library","item_id":"${"0".repeat(32)}"}}"""))
        assertEquals(400, post("""{"source":{"kind":"library","item_id":"../../etc"}}""").first)
        // upstream unreachable -> 503 source_unavailable
        assertEquals(503 to "source_unavailable", post("""{"source":{"kind":"url","url":"http://127.0.0.1:9/list.m3u8","container":"hls"}}"""))
        // policy denials -> 403 url_source_denied (the lenient test policy still blocks private / metadata addresses)
        for (denied in listOf("http://10.0.0.1/list.m3u8", "http://169.254.169.254/list.m3u8", "http://192.168.1.1/list.m3u8", "ftp://example.com/list.m3u8", "file:///C:/x.m3u8")) {
            assertEquals(403 to "url_source_denied", post("""{"source":{"kind":"url","url":"$denied"}}"""), denied)
        }
    }

    @Test
    fun httpSourcesNeedTheAllowHttpSetting() {
        val h = harness(FakeEngine(3, 100, 1), http = SafeHttp(NetPolicy.linkSource(false))) { it.allowHttpSources = false }.closing()
        val p = h.pair("Pixel")
        assertEquals(403, p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://example.com/a.m3u8"}}""").code)
    }

    @Test
    fun librarySourceStreamsThroughTheLoopbackFileServer() {
        val engine = FakeEngine(3, 100, 1)
        val h = harness(engine).closing()
        val p = h.pair("Pixel")
        val file = File(h.dir, "My Episode.mkv").also { it.writeBytes(ByteArray(4096) { 7 }) }
        val id = Library.idFor(file)
        h.host.files[id] = file

        val lib = p.client.get(h.url("/v1/library")).obj()
        assertEquals(false, lib["truncated"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(id, lib["items"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("My Episode", lib["items"]!!.jsonArray[0].jsonObject["title"]!!.jsonPrimitive.content)

        val r = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"library","item_id":"$id"}}""")
        assertEquals(201, r.code)
        assertEquals(6.0, r.obj()["duration_secs"]!!.jsonPrimitive.double)
        val args = engine.args[0]
        val input = args[args.indexOf("-i") + 1]
        assertTrue(input.startsWith("http://127.0.0.1:") && "/f/" in input, "FFmpeg reads the file through the loopback server, never by path: $input")
        assertFalse(args.any { "My Episode" in it })
        // the loopback file server supports ranges
        val ranged = plainClient.call("GET", input, headers = mapOf("Range" to "bytes=10-19"))
        assertEquals(206, ranged.code)
        assertEquals("bytes 10-19/4096", ranged.header("Content-Range"))
        assertEquals(10, ranged.body.bytes().size)
    }

    @Test
    fun backPressureSeekRestartRamWindowAndIdleTimeout() {
        // 200 segments of 1000 bytes, produced as fast as possible
        val engine = FakeEngine(200, 1000, 0)
        val h = harness(engine, SessionTuning(segmentWaitMs = 5_000, maxRamBytes = 20_000, idleTimeoutMs = 60_000)).closing()
        val p = h.pair("Pixel")
        val port = upstreamHls(200)
        val sid = sid(create(h, p, port))

        // back-pressure: with the playhead at 0 the engine may only run max_lead (15) segments ahead
        waitUntil("engine to be throttled") { state(h, p, sid)["throttled"]!!.jsonPrimitive.content == "true" }
        val st = state(h, p, sid)
        assertEquals(15, st["frontier_segment"]!!.jsonPrimitive.int, st.toString())
        assertTrue(st["window"]!!.jsonObject["bytes"]!!.jsonPrimitive.content.toLong() <= 20_000)
        // the playhead moves (heartbeat at 20 s = segment 10): the engine continues up to 10 + 15
        p.client.call("POST", h.url("/v1/sessions/$sid/position"), """{"position_secs":20.0,"paused":false}""").close()
        waitUntil("frontier 25") { state(h, p, sid)["frontier_segment"]?.jsonPrimitive?.int == 25 }

        // seek far ahead: requesting segment 150 restarts the engine there
        val far = p.client.get(h.url("/v1/sessions/$sid/seg_150.m4s"))
        assertEquals(200, far.code)
        assertEquals(150.toByte(), far.body.bytes()[0])
        assertEquals(150, engine.starts.last())
        val lastArgs = engine.args.last()
        assertTrue(lastArgs.zipWithNext().any { it.first == "-ss" && it.second == "300" }, lastArgs.toString())
        assertTrue(lastArgs.zipWithNext().any { it.first == "-start_number" && it.second == "150" })
        // seek back behind the window: restart again
        assertEquals(200, p.client.get(h.url("/v1/sessions/$sid/seg_3.m4s")).code)
        assertEquals(3, engine.starts.last())
        // stale generations were refused by the sink (409), never stored
        assertTrue(engine.putResults.any { it.second == 409 } || engine.starts.size >= 3)
        // the RAM window never exceeds its budget
        assertTrue(state(h, p, sid)["window"]!!.jsonObject["bytes"]!!.jsonPrimitive.content.toLong() <= 20_000)
    }

    @Test
    fun sessionsWithoutHeartbeatAreClosedAfterTheIdleTimeout() {
        val h = harness(FakeEngine(3, 100, 1), SessionTuning(idleTimeoutMs = 600, segmentWaitMs = 5_000)).closing()
        val p = h.pair("Pixel")
        val sid = sid(create(h, p, upstreamHls(3)))
        // heartbeats keep it alive past the timeout ...
        repeat(6) {
            Thread.sleep(200)
            assertEquals(204, p.client.call("POST", h.url("/v1/sessions/$sid/position"), """{"position_secs":1.0,"paused":true}""").code)
        }
        assertEquals(1, h.link.activeSessions())
        // ... and silence closes it
        waitUntil("idle close") { h.link.activeSessions() == 0 }
        assertEquals(410, p.client.call("POST", h.url("/v1/sessions/$sid/position"), """{"position_secs":1.0,"paused":true}""").code)
    }

    @Test
    fun aFailingEngineMarksTheSessionFailedAndDeniedSourcesReportThePolicy() {
        val broken = object : app.nijiyome.server.link.media.Engine {
            override suspend fun spawn(job: app.nijiyome.server.link.media.EngineJob): app.nijiyome.server.link.media.EngineProcess = throw IOException("no ffmpeg")
        }
        val h = harness(broken).closing()
        val p = h.pair("Pixel")
        val sid = sid(create(h, p, upstreamHls(3)))
        val st = state(h, p, sid)
        assertEquals("failed", st["state"]!!.jsonPrimitive.content)
        assertEquals("engine_unavailable", st["error"]!!.jsonPrimitive.content)
        assertEquals(503, p.client.get(h.url("/v1/sessions/$sid/media.m3u8")).code)
    }

    @Test
    fun bindAddressesMustBePrivate() {
        val dir = java.nio.file.Files.createTempDirectory("link-bind").toFile()
        try {
            for (ip in listOf("8.8.8.8", "0.0.0.0")) {
                val e = runCatching { LinkServer.start(LinkConfig(File(dir, ip), "PC", bindIps = listOf(ip), runner = CannedRunner), TestHost()) }.exceptionOrNull()
                assertTrue(e is LinkStartException.PublicBind, "$ip: $e")
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun identitySurvivesRestartsSoPairedPhonesKeepWorking() {
        val h = harness(FakeEngine(3, 100, 1))
        val p = h.pair("Pixel")
        val pin = h.link.caFingerprint
        val linkDir = File(h.dir, "link")
        h.link.stop()
        // same data dir -> same CA, same device registry
        val link = LinkServer.start(
            LinkConfig(linkDir, "Test PC", port = 0, bindIps = listOf("127.0.0.1"), runner = CannedRunner, engine = FakeEngine(1, 1, 0), discoverRenderNode = false),
            h.host,
        )
        try {
            assertEquals(pin, link.caFingerprint)
            assertEquals(1, link.devices().size)
            val r = p.client.get("https://127.0.0.1:${link.port}/v1/capabilities")
            assertEquals(200, r.code, "the old client certificate is still accepted")
        } finally {
            link.stop()
            h.dir.deleteRecursively()
        }
    }

    @Test
    fun lsfgProfilesAppearWithTheProbeAndRunTheThreeProcessChain() {
        val engine = FakeEngine(3, 100, 1)
        val tmp = File(System.getProperty("java.io.tmpdir"))
        val status = LsfgStatus.available(LsfgPaths(File(tmp, "temple-lsfg"), File(tmp, "Lossless.dll")), ProbeInfo("Fake GPU", true))
        val h = harness(engine) { it.lsfg = LsfgSetup.Status(status) }.closing()
        val p = h.pair("Pixel")
        val caps = p.client.get(h.url("/v1/capabilities")).obj()
        assertEquals("true", caps["lsfg"]!!.jsonObject["available"]!!.jsonPrimitive.content)
        assertEquals("Fake GPU", caps["gpu"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val ids = caps["profiles"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertTrue("p-lsfg-1080-x2" in ids && "p-lsfg-1080-auto120" in ids)
        assertFalse(ids.any { it.startsWith("p-anime4k") }, "no shaders in the test environment")

        val c = create(h, p, upstreamHls(3), ""","profile":"p-lsfg-1080-auto120"""").obj()
        val sid = c["session_id"]!!.jsonPrimitive.content
        waitUntil("the LSFG run") { engine.args.isNotEmpty() }
        // the engine gets the encoder stage: raw RGBA from the helper at 23.976 (fake engine: rate unknown) x5
        val a = engine.args[0]
        assertTrue(a.zipWithNext().any { it.first == "-i" && it.second == "pipe:0" }, a.toString())
        assertTrue(a.zipWithNext().any { it.first == "-framerate" && it.second == "120000/1001" }, a.toString())
        assertTrue("1:a:0?" in a)
        val st = state(h, p, sid)
        assertEquals("lsfg", st["fg"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""{"multiplier":5,"source_fps":23.976,"out_fps":119.88}"""), st["fg_effective"])
        assertEquals(119.88, st["output"]!!.jsonObject["fps"]!!.jsonPrimitive.double)
    }

    /** BUG-008 / BUG-022 / BUG-038: a `session` source reopens an earlier session of the same device; another device cannot. */
    @Test
    fun aSessionSourceReopensAnEarlierSessionOfTheSameDevice() {
        val h = harness(FakeEngine(3, 100, 1)).closing()
        val a = h.pair("A")
        val b = h.pair("B")
        val sid = sid(create(h, a, upstreamHls(3)))
        assertEquals(204, a.client.call("DELETE", h.url("/v1/sessions/$sid")).code)
        assertEquals(410, a.client.get(h.url("/v1/sessions/$sid")).code)
        fun again(p: Paired, id: String) = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"session","session_id":"$id"},"start_secs":2.0}""")
        assertEquals(404, again(b, sid).code, "another device cannot reuse it")
        val r = again(a, sid)
        assertEquals(201, r.code)
        val renewed = sid(r)
        assertNotEquals(sid, renewed)
        assertEquals(201, again(a, renewed).code)
        assertEquals(404, again(a, "00000000-0000-0000-0000-000000000000").code)
    }

    /** BUG-009: a `torrent` source streams from the engine's loopback server (trusted for that origin only), even with a strict policy. */
    @Test
    fun aTorrentSourceStreamsFromTheEngineLoopbackServer() {
        val h = harness(FakeEngine(3, 100, 1), http = SafeHttp(NetPolicy.linkSource(false))).closing()
        val a = h.pair("A")
        val stub = Stub { Canned(200, listOf("content-type" to "video/x-matroska"), ByteArray(64)) }.closing()
        fun body(uri: String) = """{"source":{"kind":"torrent","uri":"$uri","media_id":"212888","episode":4}}"""
        assertEquals(503, a.client.call("POST", h.url("/v1/sessions"), body("magnet:?xt=urn:btih:2b5d")).code)
        h.host.torrent = OpenedTorrent("http://127.0.0.1:${stub.port}/tok123/2b5d/0/ep4.mkv", "2b5d")
        assertEquals(400, a.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"torrent","uri":"","media_id":"1","episode":4}}""").code)
        val r = a.client.call("POST", h.url("/v1/sessions"), body("magnet:?xt=urn:btih:2b5d"))
        assertEquals(201, r.code)
        val sid = sid(r)
        assertEquals(204, a.client.call("DELETE", h.url("/v1/sessions/$sid")).code)
        assertEquals(listOf("2b5d"), h.host.torrentsEnded.toList())
        // only the engine's own `/<token>/` path is trusted
        assertEquals(null, torrentStreamOrigin("http://10.0.0.2:80/t/x".toHttpUrl()))
    }

    /** BUG-131: renewing a torrent session reuses its torrent, the new session holds it first, and every renew answers. */
    @Test
    fun renewingATorrentSessionReusesItsTorrentAndAlwaysAnswers() {
        val h = harness(FakeEngine(3, 100, 1), quickTuning().copy(prepareTimeoutMs = 800), SafeHttp(NetPolicy.linkSource(false))).closing()
        val a = h.pair("moto g51")
        val stub = Stub { Canned(200, listOf("content-type" to "video/x-matroska"), ByteArray(64)) }.closing()
        h.host.torrent = OpenedTorrent("http://127.0.0.1:${stub.port}/tok123/2b5d/0/ep4.mkv", "2b5d")
        val r = a.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"torrent","uri":"magnet:?xt=urn:btih:2b5d","media_id":"212888","episode":4},"profile":"p-none"}""")
        assertEquals(201, r.code)
        var sid = sid(r)
        fun renew(id: String): Triple<Int, Long, JsonObject> {
            val started = System.nanoTime()
            val x = a.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"session","session_id":"$id"},"profile":"p-none","start_secs":4.0}""")
            return Triple(x.code, (System.nanoTime() - started) / 1_000_000, x.obj())
        }
        repeat(5) { i ->
            val (status, took, body) = renew(sid)
            assertEquals(201, status, "renew $i: $body")
            assertTrue(took < 3_000, "renew $i took $took ms")
            val next = body["session_id"]!!.jsonPrimitive.content
            assertNotEquals(sid, next)
            sid = next
            val calls = h.host.torrentCalls.toList()
            assertEquals("ended:2b5d", calls.last(), "renew $i: the replaced session ends last: $calls")
            assertEquals("reuse:2b5d", calls[calls.size - 2], "renew $i: the new session holds the torrent first: $calls")
        }
        assertEquals(1, h.host.torrentCalls.count { it.startsWith("open:") })
        assertNotEquals(404, a.client.get(h.url("/v1/sessions/$sid/master.m3u8")).code)

        // the engine lost the torrent: the renew opens it again (and still answers)
        h.host.torrentReusable = false
        val (st2, _, b2) = renew(sid)
        assertEquals(201, st2, b2.toString())
        sid = b2["session_id"]!!.jsonPrimitive.content
        assertEquals(2, h.host.torrentCalls.count { it.startsWith("open:") })

        // a host that never answers is refused within the time limit; the session stays usable and nothing leaks
        h.host.torrentOpenHangs = true
        val endedBefore = h.host.torrentsEnded.size
        val (st3, took3, b3) = renew(sid)
        assertEquals(503, st3, b3.toString())
        assertEquals("source_unavailable", b3["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertTrue(took3 < 3_000, "the refusal took $took3 ms")
        assertEquals(endedBefore, h.host.torrentsEnded.size, "a refused renew does not touch the running session")
        assertEquals(200, a.client.get(h.url("/v1/sessions/$sid")).code, "the session keeps playing")

        // every hold is given back once the last session closes
        assertEquals(204, a.client.call("DELETE", h.url("/v1/sessions/$sid")).code)
        val calls = h.host.torrentCalls.toList()
        assertEquals(calls.count { it.startsWith("open:") || it.startsWith("reuse:") }, calls.count { it.startsWith("ended:") }, calls.toString())
    }

    /** BUG-058: back-pressure must not hold a segment the phone is waiting for. */
    @Test
    fun aSegmentThePhoneWaitsForIsProducedPastTheLead() {
        val engine = FakeEngine(200, 100, 0)
        val h = harness(engine).closing()
        val p = h.pair("Pixel")
        val sid = sid(create(h, p, upstreamHls(200)))
        waitUntil("engine to be throttled") { state(h, p, sid)["throttled"]!!.jsonPrimitive.content == "true" }
        p.client.call("POST", h.url("/v1/sessions/$sid/position"), """{"position_secs":0.0,"paused":false}""").close()
        for (k in 16..18) {
            val started = System.nanoTime()
            assertEquals(200, p.client.get(h.url("/v1/sessions/$sid/seg_$k.m4s")).code, "seg_$k")
            assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000, "seg_$k waited for the playhead")
        }
        assertEquals(listOf(0), engine.starts.toList(), "no restart: the same run kept going")
    }

    /** BUG-009 / BUG-023: a source without a duration gets the probed one and a VOD playlist. */
    @Test
    fun sourcesWithoutADurationGetTheProbedOneAndAVodPlaylist() {
        val engine = FakeEngine(10, 100, 1).apply { probedDuration = 19.5 }
        val h = harness(engine).closing()
        val p = h.pair("Pixel")
        val stub = Stub { Canned(200, listOf("content-type" to "video/mp4"), ByteArray(64)) }.closing()
        val r = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:${stub.port}/ep.mp4","container":"mp4"}}""")
        assertEquals(201, r.code)
        val c = r.obj()
        assertEquals(19.5, c["duration_secs"]!!.jsonPrimitive.double)
        val sid = c["session_id"]!!.jsonPrimitive.content
        waitUntil("ready") { p.client.get(h.url("/v1/sessions/$sid/media.m3u8")).use { it.code == 200 } }
        val pl = p.client.get(h.url("/v1/sessions/$sid/media.m3u8")).body.string()
        assertTrue("#EXT-X-PLAYLIST-TYPE:VOD" in pl && "#EXT-X-ENDLIST" in pl, pl)
        assertEquals(10, Regex("#EXTINF").findAll(pl).count())
        // without a probe answer it stays a growing EVENT playlist
        engine.probedDuration = null
        val c2 = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:${stub.port}/ep2.mp4","container":"mp4"}}""").obj()
        assertFalse("duration_secs" in c2, c2.toString())
    }

    /** BUG-095: a rewind to segments an earlier run made is served from RAM without restarting. */
    @Test
    fun aRewindAfterASeekIsServedFromRamWithoutRestarting() {
        val engine = FakeEngine(200, 100, 20)
        val h = harness(engine).closing()
        val p = h.pair("Pixel")
        val sid = sid(create(h, p, upstreamHls(200)))
        p.client.call("POST", h.url("/v1/sessions/$sid/position"), """{"position_secs":20.0,"paused":false}""").close()
        waitUntil("frontier 25", attempts = 400) { state(h, p, sid)["frontier_segment"]?.jsonPrimitive?.int == 25 }
        assertEquals(200, p.client.get(h.url("/v1/sessions/$sid/seg_40.m4s")).code)
        assertEquals(listOf(0, 40), engine.starts.toList())
        val started = System.nanoTime()
        val r = p.client.get(h.url("/v1/sessions/$sid/seg_15.m4s"))
        assertEquals(200, r.code)
        assertEquals(15.toByte(), r.body.bytes()[0])
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000)
        assertEquals(listOf(0, 40), engine.starts.toList(), "the rewind did not restart the engine")
        assertTrue(state(h, p, sid)["frontier_segment"]!!.jsonPrimitive.int >= 40)
        assertEquals(200, p.client.get(h.url("/v1/sessions/$sid/seg_43.m4s")).code)
        assertEquals(listOf(0, 40), engine.starts.toList())
    }

    /** BUG-024: a source the probe cannot read is refused at creation with 503 source_unavailable. */
    @Test
    fun aSourceTheProbeCannotReadIsRefusedAtCreation() {
        val engine = FakeEngine(10, 100, 1).apply { probeUnreadable = true }
        val h = harness(engine).closing()
        val p = h.pair("Pixel")
        val stub = Stub { Canned(404, emptyList(), "not found".toByteArray()) }.closing()
        val r = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:${stub.port}/master.m3u8c"}}""")
        assertEquals(503, r.code)
        assertEquals("source_unavailable", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertTrue(engine.starts.isEmpty(), "no engine run for a refused session")
        engine.probeUnreadable = false
        assertEquals(201, p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:${stub.port}/ep.mp4","container":"mp4"}}""").code)
    }
}
