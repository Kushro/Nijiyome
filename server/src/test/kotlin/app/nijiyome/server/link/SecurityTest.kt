// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.link

import app.nijiyome.link.LinkCrypto
import app.nijiyome.link.LinkResult
import app.nijiyome.link.PairingClient
import app.nijiyome.link.PairingPayload as PhonePayload
import app.nijiyome.link.PayloadResult
import app.nijiyome.server.ext.ExtServer
import app.nijiyome.server.ext.ExtServerConfig
import app.nijiyome.server.ext.FakeCatalog
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The security properties the brief asks for, beyond the ported e2e / SSRF tests: no access without pairing, revoked certificates
 * refused, `..` never escapes, body and connection caps, and nothing listening anywhere but the configured addresses.
 */
class SecurityTest {
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() = open.reversed().forEach { runCatching { it.close() } }

    private fun err(r: okhttp3.Response): String = r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    @Test
    fun withoutAPairedCertificateOnlyPairingAnswers() {
        val h = harness(FakeEngine(1, 10, 1)).also { open += it }
        val boot = h.bootstrap()
        for (path in listOf("/v1/capabilities", "/v1/library", "/v1/history", "/v1/history/mode", "/v1/player", "/v1/events", "/v1/sessions/x", "/v1/sessions/x/master.m3u8")) {
            boot.get(h.url(path)).use { r ->
                assertEquals(401, r.code, path)
                assertEquals("client_certificate_required", err(r), path)
            }
        }
        boot.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:1/x"}}""").use { assertEquals(401, it.code) }
        boot.call("DELETE", h.url("/v1/history")).use { assertEquals(401, it.code) }
        // plain HTTP on the TLS port never reaches a route
        val plain = runCatching { plainClient.get("http://127.0.0.1:${h.link.port}/v1/capabilities").use { it.code } }.getOrNull()
        assertNotEquals(200, plain)
        // a certificate this PC never issued (signed by another PC's CA) is refused at the handshake or with 401/403
        val other = harness(FakeEngine(1, 10, 1)).also { open += it }
        val payload = (PhonePayload.parse(other.link.openPairingWindow().uri()) as PayloadResult.Valid).payload
        val key = LinkCrypto.generateKeyPair()
        val grant = (runBlocking { PairingClient().pair(payload, "Elsewhere", key) } as LinkResult.Ok).value
        val foreign = LinkCrypto.httpClient("127.0.0.1", h.link.caFingerprint, LinkCrypto.ClientKeyManager(key.private, arrayOf(grant.certificate)))
        val code = runCatching { foreign.get(h.url("/v1/capabilities")).use { it.code } }.getOrNull()
        assertTrue(code == null || code == 401 || code == 403, "foreign certificate: $code")
    }

    @Test
    fun aRevokedCertificateIsRefusedEvenOnAFreshConnection() {
        val h = harness(FakeEngine(1, 10, 1)).also { open += it }
        val p = h.pair("Pixel")
        p.client.get(h.url("/v1/capabilities")).use { assertEquals(200, it.code) }
        assertTrue(h.link.revoke(p.deviceId))
        for (i in 0 until 3) {
            p.client.connectionPool.evictAll()
            val code = try {
                p.client.get(h.url("/v1/capabilities")).use { r -> r.code.also { if (it == 403) assertEquals("device_not_authorized", err(r)) } }
            } catch (_: IOException) {
                null // refused during the handshake (bad_certificate)
            }
            assertTrue(code == null || code == 403, "attempt $i: $code")
        }
    }

    @Test
    fun dotDotNeverEscapesTheSessionOrTheLibrary() {
        val host = TestHost()
        val engine = FakeEngine(3, 200, 1)
        val h = harness(engine) { it.host = host }.also { open += it }
        val p = h.pair("Pixel")
        val up = Stub { text(200, mediaPlaylist(3)) }.also { open += it }
        val sid = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:${up.port}/l.m3u8","container":"hls"}}""").obj()["session_id"]!!.jsonPrimitive.content
        waitUntil("init") { p.client.get(h.url("/v1/sessions/$sid/init.mp4")).use { it.code == 200 } }
        for (path in listOf(
            "/v1/sessions/$sid/%2e%2e%2f%2e%2e%2fdevices.json",
            "/v1/sessions/$sid/..%2Fca.der",
            "/v1/sessions/$sid/subtitles/..%2F..%2Fdevices.json",
            "/v1/sessions/$sid/subtitles/%2e%2e",
            "/v1/sessions/$sid/seg_..%2F1.m4s",
            "/v1/sessions/%2e%2e/capabilities",
            "/v1/sessions/../capabilities",
            "/v1/%2e%2e/v1/capabilities",
            "/v1/sessions/$sid/../../capabilities",
        )) {
            // raw over the paired TLS connection: OkHttp would resolve `%2e%2e` itself before sending
            val (status, body) = rawGet(h, p, path)
            assertTrue(status == 404 || status == 400, "$path -> $status")
            assertTrue("BEGIN" !in body && "fingerprint" !in body, "$path leaked: ${body.take(80)}")
        }
        for (id in listOf("../../link/devices.json", "..", "/etc/passwd", "C:\\Windows\\win.ini")) {
            p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"library","item_id":${kotlinx.serialization.json.JsonPrimitive(id)}}}""").use { r ->
                // ids are 32 hex digits: anything else is refused before the library is looked at
                val code = r.code to err(r)
                assertTrue(code == (400 to "request_invalid") || code == (404 to "library_item_not_found"), "$id -> $code")
            }
        }
    }

    /** One HTTP/1.1 request written verbatim on a TLS socket made with the paired client's certificate. */
    private fun rawGet(h: Harness, p: Paired, path: String): Pair<Int, String> {
        val tls = p.client.sslSocketFactory.createSocket(Socket("127.0.0.1", h.link.port), "127.0.0.1", h.link.port, true) as javax.net.ssl.SSLSocket
        tls.use { s ->
            s.soTimeout = 10_000
            s.startHandshake()
            s.outputStream.write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray())
            s.outputStream.flush()
            val text = s.inputStream.readAllBytes().decodeToString()
            val status = text.substringAfter(' ').substringBefore(' ').toIntOrNull() ?: -1
            return status to text.substringAfter("\r\n\r\n", "")
        }
    }

    @Test
    fun requestBodiesAreCapped() {
        val h = harness(FakeEngine(1, 10, 1)).also { open += it }
        val p = h.pair("Pixel")
        val big = "{\"items\":[" + "\"x\",".repeat(100_000) + "\"x\"]}"
        p.client.call("POST", h.url("/v1/history"), big).use { r ->
            assertEquals(413, r.code)
            assertEquals("body_too_large", err(r))
        }
        p.client.call("POST", h.url("/v1/sessions"), "{\"pad\":\"" + "a".repeat(8 * 1024) + "\"}").use { assertEquals(413, it.code) }
        p.client.call("POST", h.url("/v1/control"), "{\"pad\":\"" + "a".repeat(8 * 1024) + "\"}").use { assertEquals(413, it.code) }
        h.link.openPairingWindow()
        h.bootstrap().call("POST", h.url("/v1/pairing"), "{\"pad\":\"" + "a".repeat(64 * 1024) + "\"}").use { assertEquals(413, it.code) }
        // a capped upload does not cost a pairing attempt nor break the server
        p.client.get(h.url("/v1/capabilities")).use { assertEquals(200, it.code) }
    }

    @Test
    fun theSixtyFifthConnectionIsClosedAtOnce() {
        val h = harness(FakeEngine(1, 10, 1)).also { open += it }
        val sockets = (0 until LinkConnections.MAX_CONNECTIONS).map { Socket("127.0.0.1", h.link.port).also { s -> open += s } }
        Thread.sleep(300)
        Socket("127.0.0.1", h.link.port).use { extra ->
            extra.soTimeout = 3_000
            val r = try {
                extra.getInputStream().read()
            } catch (_: SocketTimeoutException) {
                -2
            } catch (_: IOException) {
                -1
            }
            assertEquals(-1, r, "the server closes a connection over the cap")
        }
        sockets.forEach { it.close() }
        // once they are gone, connections are accepted again
        waitUntil("slots free") { runCatching { h.pair("Later") }.isSuccess }
    }

    @Test
    fun nothingListensOutsideTheConfiguredAddresses() {
        val h = harness(FakeEngine(1, 10, 1)).also { open += it }
        assertTrue(h.link.localAddresses.isNotEmpty())
        assertTrue(h.link.localAddresses.all { it.address.isLoopbackAddress }, "${h.link.localAddresses}")
        assertTrue(h.link.helperAddresses.size >= 2)
        assertTrue(h.link.helperAddresses.all { it.address.hostAddress == "127.0.0.1" }, "${h.link.helperAddresses}")
        val dir = Files.createTempDirectory("ext-bind").toFile().also { d -> open += AutoCloseable { d.deleteRecursively() } }
        val ext = ExtServer.start(ExtServerConfig(File(dir, "server"), 0, loopbackOnly = true), FakeCatalog()).also { s -> open += AutoCloseable { s.stop() } }
        assertTrue(ext.addresses.all { it.address.hostAddress == "127.0.0.1" }, "${ext.addresses}")
        assertTrue(ext.lanUrls().isEmpty())
        // the same ports on this machine's LAN addresses (if it has any) refuse the connection
        val ports = h.link.localAddresses.map { it.port } + h.link.helperAddresses.map { it.port } + ext.addresses.map { it.port }
        for (ip in LinkServer.detectLanIps()) for (port in ports) {
            val refused = try {
                Socket().use { it.connect(InetSocketAddress(ip, port), 1_000) }
                false
            } catch (_: ConnectException) {
                true
            } catch (_: SocketTimeoutException) {
                true
            }
            assertTrue(refused, "$ip:$port accepts connections")
        }
        // a public or wildcard bind is refused before anything listens
        for (bad in listOf("0.0.0.0", "8.8.8.8", "::")) {
            val e = runCatching { harness(FakeEngine(1, 10, 1)) { it.bindIps = listOf(bad) }.also { open += it } }.exceptionOrNull()
            assertTrue(e is LinkStartException, "$bad: $e")
        }
    }
}
