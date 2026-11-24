// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of Tatakai Next tk-server/tests/server.rs and tests/owner.rs (AGPL-3.0, same author), plus the phone's own client
// (:core:extension-server ExtensionServerClient) against the real server.
package app.nijiyome.server.ext

import app.nijiyome.extensionserver.DeepLink
import app.nijiyome.extensionserver.ExtensionServerClient
import app.nijiyome.extensionserver.ExtensionServerConnection
import app.nijiyome.extensionserver.ExtensionServerException
import app.nijiyome.extensionserver.verifyServerPackage
import app.nijiyome.extensions.ExtensionManifest
import app.nijiyome.extensions.ManifestParser
import app.nijiyome.server.link.call
import app.nijiyome.server.link.get
import app.nijiyome.server.link.json
import app.nijiyome.server.link.obj
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An in-memory catalog: installed `.kai` files and canned route answers. */
class FakeCatalog : ExtensionCatalog {
    class Installed(val entry: CatalogEntry, val manifest: ByteArray, val bundle: ByteArray, val routes: Map<String, JsonObject>)

    val installed = mutableMapOf<String, Installed>()
    val calls = mutableListOf<Pair<String, String>>()

    fun add(manifestJson: String, bundle: String, routes: Map<String, JsonObject> = emptyMap(), enabled: Boolean = true) {
        val m = ManifestParser.parse(manifestJson)
        installed[m.id] = Installed(CatalogEntry(m.id, m.name, m.version, m.type.name.lowercase(), enabled, "full", null), manifestJson.toByteArray(), bundle.toByteArray(), routes)
    }

    override fun list(): List<CatalogEntry> = installed.values.map { it.entry }
    override fun manifest(id: String): ExtensionManifest? = installed[id]?.let { ManifestParser.parse(it.manifest.decodeToString()) }
    override fun files(id: String): Pair<ByteArray, ByteArray>? = installed[id]?.let { it.manifest to it.bundle }
    override fun candidateRoutes(id: String): List<String> = installed[id]?.routes?.keys?.filter { it in setOf("stream", "torrent", "sources") }.orEmpty()

    override suspend fun call(id: String, route: String, params: JsonObject): JsonObject {
        synchronized(calls) { calls += id to route }
        val i = installed[id] ?: throw CatalogException(CatalogException.Kind.NOT_INSTALLED, id)
        return i.routes[route] ?: throw CatalogException(CatalogException.Kind.UNSUPPORTED_ROUTE, route)
    }

    override suspend fun install(bytes: ByteArray): CatalogEntry = throw CatalogException(CatalogException.Kind.FAILED, "not in tests")
    override suspend fun remove(id: String) {
        installed.remove(id) ?: throw CatalogException(CatalogException.Kind.NOT_INSTALLED, id)
    }
}

/** [inner]'s extensions, every route of which fails with [message] (an extension's or the disk's error text). */
class FailingCatalog(private val inner: FakeCatalog, private val message: String) : ExtensionCatalog by inner {
    override suspend fun call(id: String, route: String, params: JsonObject): JsonObject = throw CatalogException(CatalogException.Kind.FAILED, message)
}

class ExtServerTest {
    private val open = mutableListOf<() -> Unit>()

    @AfterTest
    fun tearDown() = open.reversed().forEach { runCatching { it() } }

    private val http = OkHttpClient.Builder().followRedirects(false).build()

    private fun server(dir: File = tmp(), catalog: ExtensionCatalog = FakeCatalog(), port: Int = 0): ExtServer =
        ExtServer.start(ExtServerConfig(File(dir, "server"), port, name = "Test PC", loopbackOnly = true), catalog).also { s -> open += { s.stop() } }

    private fun tmp(): File = Files.createTempDirectory("ext-server").toFile().also { d -> open += { d.deleteRecursively() } }

    private fun base(s: ExtServer) = "http://127.0.0.1:${s.port}/api/server/v1"

    private fun bearer(token: String) = mapOf("Authorization" to "Bearer $token")

    private fun pairToken(s: ExtServer, label: String = "Pixel"): Pair<String, String> {
        val p = s.createPairing("http://127.0.0.1:${s.port}")
        val d = http.call("POST", "${base(s)}/auth/pair", """{"code":"${p.code}","label":"$label"}""").obj()["data"]!!.jsonObject
        return d["token"]!!.jsonPrimitive.content to d["deviceId"]!!.jsonPrimitive.content
    }

    // ---- server.rs ----

    @Test
    fun healthPairingSourcesAndRevocation() {
        val s = server()
        val health = http.get("${base(s)}/health").obj()
        assertEquals("true", health["success"]!!.jsonPrimitive.content)
        val d = health["data"]!!.jsonObject
        assertEquals(1, d["protocolVersion"]!!.jsonPrimitive.int)
        assertEquals("true", d["pairingRequired"]!!.jsonPrimitive.content)
        assertEquals("desktop", d["platform"]!!.jsonPrimitive.content)
        assertEquals("Test PC", d["name"]!!.jsonPrimitive.content)
        assertEquals(401, http.get("${base(s)}/sources").code)

        val pairing = s.createPairing("http://127.0.0.1:${s.port}")
        val paired = http.call("POST", "${base(s)}/auth/pair", """{"code":"${pairing.code}","label":"Pixel"}""").obj()["data"]!!.jsonObject
        val token = paired["token"]!!.jsonPrimitive.content
        val device = paired["deviceId"]!!.jsonPrimitive.content
        assertEquals(401, http.call("POST", "${base(s)}/auth/pair", """{"code":"${pairing.code}"}""").code, "single use")
        assertEquals(JsonArray(emptyList()), http.call("GET", "${base(s)}/sources", headers = bearer(token)).obj()["data"])
        assertEquals(JsonArray(emptyList()), http.call("GET", "${base(s)}/extensions", headers = bearer(token)).obj()["data"])
        assertEquals(1, s.devices().size)

        val cursorBefore = s.logs(0).second
        val nd = http.call("POST", "${base(s)}/resolve", """{"anilistId":21,"title":"One Piece","episode":1}""", bearer(token) + ("Accept" to "application/x-ndjson")).body.string()
        assertEquals("true", (Json.parseToJsonElement(nd.trim().lines().last()) as JsonObject)["done"]!!.jsonPrimitive.content)
        assertTrue(s.logs(0).second > cursorBefore, "every request is logged")

        s.revoke(device)
        assertEquals("device_not_authorized", http.call("GET", "${base(s)}/sources", headers = bearer(token)).obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun viewerPasswordGatesManualLogin() {
        val s = server()
        s.setViewerPassword("a long viewer password")
        assertEquals("true", http.get("${base(s)}/health").obj()["data"]!!.jsonObject["accessPasswordRequired"]!!.jsonPrimitive.content)
        assertEquals(401, http.call("POST", "${base(s)}/auth/login", """{"role":"viewer","password":"wrong password!"}""").code)
        val good = http.call("POST", "${base(s)}/auth/login", """{"role":"viewer","password":"a long viewer password","label":"Tablet"}""").obj()
        assertEquals(64, good["data"]!!.jsonObject["token"]!!.jsonPrimitive.content.length)
    }

    @Test
    fun installedExtensionsDownloadAsKaiPackages() {
        val catalog = FakeCatalog()
        val manifest = """{"id":"t.ext","name":"T","version":"1.0.2","type":"custom","description":"d","permissions":[]}"""
        val bundle = "module.exports = { hello: 1 };"
        catalog.add(manifest, bundle)
        val s = server(catalog = catalog)
        assertEquals(401, http.get("${base(s)}/extensions/t.ext/package").code, "never without auth")
        val (token, _) = pairToken(s)
        val r = http.call("GET", "${base(s)}/extensions/t.ext/package", headers = bearer(token))
        assertEquals(200, r.code)
        assertEquals("application/zip", r.header("Content-Type"))
        assertEquals("attachment; filename=\"t.ext.kai\"", r.header("Content-Disposition"))
        val sha = r.header("X-Nijiyome-Bundle-Sha256")
        val expected = MessageDigest.getInstance("SHA-256").digest(bundle.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, sha)
        val zip = r.body.bytes()
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                files[e.name] = z.readAllBytes()
            }
        }
        assertEquals(setOf("manifest.json", "bundle.js"), files.keys)
        assertTrue(files["manifest.json"]!!.contentEquals(manifest.toByteArray()) && files["bundle.js"]!!.contentEquals(bundle.toByteArray()))
        assertEquals(expected, http.call("GET", "${base(s)}/extensions", headers = bearer(token)).obj()["data"]!!.jsonArray[0].jsonObject["bundleSha256"]!!.jsonPrimitive.content)
        for (dots in listOf("..", "%2E%2E")) assertEquals(404, http.call("GET", "${base(s)}/extensions/$dots/package", headers = bearer(token)).code)
        for (id in listOf("nope", "..%2F..%2Fserver", "t.ext%2F..%2F..")) {
            val x = http.call("GET", "${base(s)}/extensions/$id/package", headers = bearer(token))
            assertEquals(404, x.code, id)
            val v = x.obj()
            assertEquals("false", v["success"]!!.jsonPrimitive.content, id)
            assertEquals("extension_not_found", v["error"]!!.jsonObject["code"]!!.jsonPrimitive.content, id)
        }
    }

    // ---- owner.rs ----

    private fun bootstrapSecret(dir: File): String = (Json.parseToJsonElement(File(dir, "server/state.json").readText()) as JsonObject)["bootstrap"]!!.jsonPrimitive.content

    @Test
    fun handleReportsStatusAndLocalUrls() {
        val s = server()
        assertNotEquals(0, s.port)
        assertTrue(s.addresses.first().address.isLoopbackAddress)
        assertEquals(0, s.state.deviceCount())
        assertTrue(s.state.serverId().isNotEmpty())
        assertTrue(s.pairingRequired())
        assertTrue(s.lanUrls().isEmpty(), "loopback only: nothing for phones")
        assertEquals("http://127.0.0.1:${s.port}/api/server/v1/media/", s.localMediaBase())
        assertTrue(s.localMediaUrl("https://cdn.example/v/index.m3u8", listOf("Referer" to "https://x/")).startsWith(s.localMediaBase()))
        s.setViewerPassword("a long viewer password")
        assertTrue(s.viewerPasswordSet())
        assertFalse(s.pairingRequired())
    }

    @Test
    fun aBusyPortIsABindErrorAndStopFreesIt() {
        val s = ExtServer.start(ExtServerConfig(File(tmp(), "server"), 0, loopbackOnly = true), FakeCatalog())
        val port = s.port
        val busy = runCatching { ExtServer.start(ExtServerConfig(File(tmp(), "server"), port, loopbackOnly = true), FakeCatalog()) }.exceptionOrNull()
        assertTrue(busy is ExtStartException, "$busy")
        s.stop()
        val again = ExtServer.start(ExtServerConfig(File(tmp(), "server"), port, loopbackOnly = true), FakeCatalog())
        again.stop()
    }

    @Test
    fun setupNeedsTheBootstrapSecretAndOwnerRoutesNeedAnAdmin() {
        val dir = tmp()
        val s = server(dir)
        val denied = http.call("POST", "${base(s)}/setup")
        assertEquals(403, denied.code)
        val body = denied.obj()
        assertEquals("false", body["success"]!!.jsonPrimitive.content)
        assertEquals("bootstrap_required", body["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)

        val owner = http.call("POST", "${base(s)}/setup", headers = mapOf("X-Nijiyome-Bootstrap" to bootstrapSecret(dir))).obj()["data"]!!.jsonObject
        assertEquals("admin", owner["role"]!!.jsonPrimitive.content)
        val admin = owner["token"]!!.jsonPrimitive.content
        val adminId = owner["deviceId"]!!.jsonPrimitive.content

        val pairing = http.call("POST", "${base(s)}/auth/pairing", headers = bearer(admin)).obj()["data"]!!.jsonObject
        assertEquals(300, pairing["expiresIn"]!!.jsonPrimitive.int)
        val deep = pairing["deepLink"]!!.jsonPrimitive.content
        assertTrue(deep.startsWith("nijiyome://extensionserver/connect?server="))
        // the phone's own parser reads it
        val link = DeepLink.parse(deep) ?: error("the phone does not read $deep")
        assertEquals(pairing["code"]!!.jsonPrimitive.content, link.code)
        val paired = http.call("POST", "${base(s)}/auth/pair", """{"code":"${link.code}","label":"Pixel"}""").obj()["data"]!!.jsonObject
        val viewer = paired["token"]!!.jsonPrimitive.content
        assertEquals("viewer", paired["role"]!!.jsonPrimitive.content)

        val forbidden = http.call("GET", "${base(s)}/devices", headers = bearer(viewer))
        assertEquals(403, forbidden.code)
        assertEquals("forbidden", forbidden.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        val rows = http.call("GET", "${base(s)}/devices", headers = bearer(admin)).obj()["data"]!!.jsonArray
        assertEquals(2, rows.size)
        assertEquals("true", rows.first { it.jsonObject["id"]!!.jsonPrimitive.content == adminId }.jsonObject["current"]!!.jsonPrimitive.content)
        assertTrue(rows.any { it.jsonObject["label"]!!.jsonPrimitive.content == "Pixel" && it.jsonObject["current"]!!.jsonPrimitive.content == "false" })
        assertEquals(1, s.state.deviceCount())
    }

    @Test
    fun logoutRevokesTheCallingToken() {
        val s = server()
        val p = s.createPairing("http://127.0.0.1")
        val token = http.call("POST", "${base(s)}/auth/pair", """{"code":"${p.code}"}""").obj()["data"]!!.jsonObject["token"]!!.jsonPrimitive.content
        assertEquals(1, s.devices().size)
        assertEquals("Device", s.devices()[0].label, "missing label")
        assertEquals(200, http.call("POST", "${base(s)}/auth/logout", headers = bearer(token)).code)
        assertTrue(s.devices().isEmpty())
        assertEquals(401, http.call("GET", "${base(s)}/sources", headers = bearer(token)).code)
    }

    @Test
    fun numericCodesPairAndBadCodesAreThrottled() {
        val s = server()
        var p = s.createPairing("http://127.0.0.1")
        while (p.code.startsWith('0')) p = s.createPairing("http://127.0.0.1")
        assertEquals(200, http.call("POST", "${base(s)}/auth/pair", """{"code":${p.code.toInt()}}""").code)
        repeat(ExtState.MAX_ATTEMPTS) {
            val r = http.call("POST", "${base(s)}/auth/pair", """{"code":"nope"}""")
            assertEquals(401, r.code)
            assertEquals("pairing_expired", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        }
        val throttled = http.call("POST", "${base(s)}/auth/pair", """{"code":"nope"}""")
        assertEquals(429, throttled.code)
        assertEquals("login_throttled", throttled.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun malformedBodiesUnknownRoutesAndNativeUseTheEnvelope() {
        val s = server()
        val bad = http.call("POST", "${base(s)}/auth/pair", "{ nope")
        assertEquals(400, bad.code)
        assertEquals("invalid_json", bad.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        val missing = http.get("${base(s)}/does-not-exist")
        assertEquals(404, missing.code)
        val m = missing.obj()
        assertEquals("false", m["success"]!!.jsonPrimitive.content)
        assertEquals("route_not_found", m["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(404, http.call("POST", "${base(s)}/native").code)
        val login = http.call("POST", "${base(s)}/auth/login", """{"role":"viewer","password":"whatever long"}""")
        assertEquals(401, login.code)
        assertEquals("pairing_required", login.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    // ---- the phone's own client against the real server ----

    @Test
    fun thePhonesExtensionServerClientPairsListsInvokesResolvesAndDownloads() = runBlocking {
        val catalog = FakeCatalog()
        val manifest = """{"id":"toko","name":"Toko","version":"2.1.0","type":"custom","description":"d","permissions":["network:domain:cdn.example"],"capabilities":["manga"]}"""
        val bundle = "module.exports = {};"
        val stream = buildJsonObject {
            put("sources", buildJsonArray {
                add(buildJsonObject {
                    put("url", "https://cdn.example/ep1/index.m3u8")
                    put("type", "hls")
                    put("server", "Vidstream")
                    put("headers", buildJsonObject { put("Referer", "https://site.example/") })
                })
            })
        }
        catalog.add(manifest, bundle, mapOf("stream" to stream, "custom/home" to buildJsonObject { put("items", JsonArray(emptyList())) }))
        val s = server(catalog = catalog)
        val url = "http://127.0.0.1:${s.port}"
        val client = ExtensionServerClient(OkHttpClient.Builder().followRedirects(false).build())
        val health = client.health(url)
        assertEquals(1, health.protocolVersion)
        assertTrue(health.pairingRequired && health.desktop)

        val pairing = s.createPairing(url)
        val token = client.pair(url, DeepLink.parse(pairing.deepLink)!!.code, "Pixel 8")
        assertEquals("viewer", token.role)
        val conn = ExtensionServerConnection("c1", health.serverId!!, url, health.name, token.token)
        val sources = client.sources(conn)
        assertEquals(listOf("${health.serverId}/toko", "${health.serverId}/toko~read"), sources.map { it.id })
        val exts = client.extensions(conn)
        assertEquals("toko", exts.single().id)

        // the package verifies with the phone's own check
        val pkg = client.extensionPackage(conn, "toko")
        assertNull(verifyServerPackage(pkg, "toko", exts.single().bundleSha256))

        val home = client.invoke(conn, "${health.serverId}/toko", "browse", JsonObject(emptyMap()))
        assertTrue(home is JsonObject)
        // resolve streams NDJSON snapshots; the stream URL now goes through this PC's media proxy with its Referer
        val snapshots = client.resolve(conn, buildJsonObject { put("anilistId", 154587); put("title", "Frieren"); put("episode", 1) }).toList()
        val last = snapshots.last()
        assertTrue(last.complete)
        val src = last.sourceObjects.single()
        val proxied = src["url"]!!.jsonPrimitive.content
        assertTrue(proxied.startsWith("$url/api/server/v1/media/") && proxied.endsWith("/index.m3u8"), proxied)
        assertEquals("Toko · Test PC", src["providerName"]!!.jsonPrimitive.content)
        assertFalse("headers" in src, "the headers stay in the PC's ticket")
        val ticket = s.state.let { proxied.substringAfter("/media/").substringBefore('/') }
        assertTrue(ticket.length == 64)

        // revoked: the phone sees `unauthorized`
        s.revoke(s.devices().single().id)
        val e = runCatching { client.sources(conn) }.exceptionOrNull() as ExtensionServerException
        assertTrue(e.unauthorized, e.code)
    }

    // ---- what the log shows of an extension's error ----

    @Test
    fun theLogKeepsNoLocalPathsFullUrlsOrLineBreaksOfAnError() {
        val home = System.getProperty("user.home")
        assertEquals(null, LogBuffer.safeError(null))
        val unix = LogBuffer.safeError("ENOENT: no such file, open '$home/.local/share/nijiyome/ext/toko/bundle.js'\n    at Object.openSync (node:fs:573)")!!
        assertFalse(home in unix || "\n" in unix || ".local" in unix, unix)
        assertTrue(unix.startsWith("ENOENT: no such file, open '<path>'"), unix)
        val win = LogBuffer.safeError("C:\\Users\\Jane Doe\\AppData\\Roaming\\Nijiyome\\ext\\x.js (The system cannot find the file)")!!
        assertEquals("<path> (The system cannot find the file)", win)
        assertEquals("open <path> failed", LogBuffer.safeError("open \\\\nas\\share\\anime\\ep1.mkv failed"))
        assertEquals("read <path>: denied", LogBuffer.safeError("read file:///home/jane/x.json: denied"))
        assertEquals("see <path>", LogBuffer.safeError("see ~/Videos/x.mkv"))
        assertEquals("at <path>.", LogBuffer.safeError("at /srv/media/anime/ep1.mkv."))
        assertEquals("failed (<path>), retrying", LogBuffer.safeError("failed (/opt/nijiyome/lib/x.so), retrying"))
        assertEquals("<path> failed", LogBuffer.safeError("/home/jane doe/x.js failed"))
        // URLs keep scheme and host only: no user, path, query (tokens, keys) or fragment.
        assertEquals(
            "HTTP 403 from https://cdn.example.org:8443 after redirect",
            LogBuffer.safeError("HTTP 403 from https://user:pw@cdn.example.org:8443/hls/ep1.m3u8?token=s3cr3t&sig=abc#t=1 after redirect"),
        )
        // Control, line-separator and bidi characters become spaces; ordinary text and short routes stay.
        assertEquals("a b c d", LogBuffer.safeError("a\r\nb\u0000c\u2028\u202Ed"))
        assertEquals("unsupported route custom/watch, 3 ~ 4", LogBuffer.safeError("unsupported route custom/watch, 3 ~ 4"))
        // Cut to ERROR_MAX code points, never in the middle of a surrogate pair.
        val long = LogBuffer.safeError("😀".repeat(500))!!
        assertEquals(LogBuffer.ERROR_MAX + 1, long.codePointCount(0, long.length))
        assertTrue(long.endsWith("…") && !Character.isHighSurrogate(long[long.length - 2]), long)
    }

    @Test
    fun aFailingExtensionsMessageReachesTheLogSanitizedFromInvokeAndResolve() = runBlocking {
        val fake = FakeCatalog()
        val manifest = """{"id":"toko","name":"Toko","version":"2.1.0","type":"custom","description":"d","permissions":[],"capabilities":["manga"]}"""
        fake.add(manifest, "module.exports = {};", mapOf("stream" to buildJsonObject { }, "custom/home" to buildJsonObject { }))
        val home = System.getProperty("user.home")
        val secret = "Error: fetch https://site.example/api/ep?id=1&token=s3cr3t failed\n  at $home/.local/share/nijiyome/ext/toko/bundle.js:12\r\n" + "x".repeat(400)
        val s = server(catalog = FailingCatalog(fake, secret))
        val url = "http://127.0.0.1:${s.port}"
        val client = ExtensionServerClient(OkHttpClient.Builder().followRedirects(false).build())
        val health = client.health(url)
        val token = client.pair(url, DeepLink.parse(s.createPairing(url).deepLink)!!.code, "Pixel 8")
        val conn = ExtensionServerConnection("c1", health.serverId!!, url, health.name, token.token)
        assertTrue(runCatching { client.invoke(conn, "${health.serverId}/toko", "browse", JsonObject(emptyMap())) }.isFailure)
        client.resolve(conn, buildJsonObject { put("anilistId", 1); put("title", "T"); put("episode", 1) }).toList()
        val failed = s.logs(0).first.filter { it.event == "source_failed" }
        assertEquals(setOf("/invoke", "/resolve"), failed.map { it.route }.toSet(), failed.toString())
        for (e in failed) {
            val text = e.error!!
            assertFalse(home in text || "s3cr3t" in text || "token=" in text || "/api/ep" in text || '\n' in text || '\r' in text, text)
            assertTrue(text.startsWith("Error: fetch https://site.example failed at <path>"), text)
            assertTrue(text.codePointCount(0, text.length) <= LogBuffer.ERROR_MAX + 1 && text.endsWith("…"), text)
            // The JSON of /logs and /diagnostics is built from the same entry.
            assertEquals(text, e.toJson()["error"]!!.jsonPrimitive.content)
        }
    }
}
