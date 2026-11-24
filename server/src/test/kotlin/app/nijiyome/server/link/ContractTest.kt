// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.link

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Response
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The server against `contracts/link/openapi.v1.json`: the error catalog (code, HTTP status, fixed message), and a scripted run
 * of the real server that calls every documented operation, then checks each answer: its status is one the contract lists for
 * that operation, it carries `Nijiyome-Link: v1`, its media type is the documented one and a JSON body validates against the
 * documented schema (a small validator for the JSON Schema subset the contract uses). Undocumented methods answer 405.
 */
class ContractTest {
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() = open.reversed().forEach { runCatching { it.close() } }

    private val contract: JsonObject by lazy {
        val f = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "contracts/link/openapi.v1.json") }.first { it.isFile }
        Json.parseToJsonElement(f.readText()).jsonObject
    }
    private val components get() = contract["components"]!!.jsonObject

    // ---- error catalog ----

    @Test
    fun errorCatalogMatchesTheServerCodes() {
        val catalog = contract["x-errors"]!!.jsonObject["codes"]!!.jsonArray.map { it.jsonObject }
        val byCode = catalog.associateBy { it["code"]!!.jsonPrimitive.content }
        for (e in ErrorCode.entries) {
            val c = assertNotNull(byCode[e.code], "${e.code} is not in the contract")
            assertEquals(c["http"]!!.jsonPrimitive.content.toInt(), e.status, e.code)
            assertEquals(c["message"]!!.jsonPrimitive.content, e.message, e.code)
        }
        val produced = catalog.filter { it["m0"]?.jsonPrimitive?.booleanOrNull != false }.map { it["code"]!!.jsonPrimitive.content }
        val missing = produced - ErrorCode.entries.map { it.code }.toSet() - setOf("no_player")
        assertTrue(missing.isEmpty(), "codes the contract says the server produces but it does not know: $missing")
        val schemaCodes = components["schemas"]!!.jsonObject["Error"]!!.jsonObject["properties"]!!.jsonObject["error"]!!.jsonObject["properties"]!!
            .jsonObject["code"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertTrue(ErrorCode.entries.all { it.code in schemaCodes })
    }

    // ---- the two decisions where tk-link and the contract disagreed (coordinator, 2026-11-23) ----

    @Test
    fun theHistoryPageCarriesTheDeviceModeAsTkLinkDoesAndTheContractNowDocuments() {
        val h = harness(FakeEngine(1, 10, 1)) { it.host = MemHistory() }.also { open += it }
        val p = h.pair("Pixel")
        val page = p.client.get(h.url("/v1/history?since=0")).obj()
        assertEquals("full", page["mode"]!!.jsonPrimitive.content)
        h.link.setHistoryMode(p.deviceId, HistoryMode.RECEIVE_ONLY)
        assertEquals("receive_only", p.client.get(h.url("/v1/history")).obj()["mode"]!!.jsonPrimitive.content)
        val schema = components["schemas"]!!.jsonObject["HistoryPage"]!!.jsonObject
        assertEquals("#/components/schemas/HistoryMode", schema["properties"]!!.jsonObject["mode"]!!.jsonObject["\$ref"]!!.jsonPrimitive.content)
        assertTrue("mode" !in schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }, "optional: older phones ignore it")
    }

    @Test
    fun deletingARememberedSessionIsIdempotentWhereTkLinkAnswered410() {
        val h = harness(FakeEngine(2, 100, 1)).also { open += it }
        val p = h.pair("Pixel")
        val up = Stub { text(200, mediaPlaylist(2)) }.also { open += it }
        val sid = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:${up.port}/l.m3u8","container":"hls"}}""").obj()["session_id"]!!.jsonPrimitive.content
        assertEquals(204, p.client.call("DELETE", h.url("/v1/sessions/$sid")).code)
        assertEquals(204, p.client.call("DELETE", h.url("/v1/sessions/$sid")).code, "remembered: 204 again (tk-link: 410 session_gone)")
        assertEquals(410, p.client.get(h.url("/v1/sessions/$sid")).code, "other routes still report the closed session as gone")
        p.client.call("DELETE", h.url("/v1/sessions/00000000-0000-4000-8000-000000000000")).use { r ->
            assertEquals(404, r.code)
            assertEquals("session_not_found", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        }
    }

    // ---- routes, statuses, headers, schemas ----

    private class Answer(val template: String, val method: String, val status: Int, val contract: String?, val contentType: String?, val body: String)

    private val answers = mutableListOf<Answer>()

    private fun record(template: String, method: String, r: Response): Answer = r.use {
        Answer(template, method.lowercase(), it.code, it.header("Nijiyome-Link"), it.header("Content-Type"), it.body.string()).also(answers::add)
    }

    @Test
    fun everyDocumentedOperationAnswersAsTheContractSays() {
        val host = MemHistory()
        val clip = File.createTempFile("contract", ".mkv").apply { writeBytes(ByteArray(64)); deleteOnExit() }
        host.files["0123456789abcdef0123456789abcdef"] = clip
        val engine = FakeEngine(4, 500, 1)
        val h = harness(engine) { it.host = host }.also { open += it }
        val upstream = Stub { text(200, mediaPlaylist(4)) }.also { open += it }

        // pairing: closed window, wrong code and a good one (the real client, through the harness)
        val boot = h.bootstrap()
        record("/v1/pairing", "POST", boot.call("POST", h.url("/v1/pairing"), """{"code":"0000000000","csr_pem":"x","device_name":"x","platform":"android"}"""))
        h.link.openPairingWindow()
        record("/v1/pairing", "POST", boot.call("POST", h.url("/v1/pairing"), """{"code":"0000000000","csr_pem":"${app.nijiyome.link.LinkCsr.createPem(app.nijiyome.link.LinkCrypto.generateKeyPair(), "x").replace("\n", "\\n")}","device_name":"x","platform":"android"}"""))
        record("/v1/pairing", "POST", boot.call("POST", h.url("/v1/pairing"), "{"))
        h.link.closePairingWindow()
        val p = h.pair("Pixel")
        val c = p.client
        fun u(path: String) = h.url(path)

        record("/v1/capabilities", "GET", c.get(u("/v1/capabilities")))
        record("/v1/library", "GET", c.get(u("/v1/library")))

        val created = record(
            "/v1/sessions", "POST",
            c.call("POST", u("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:${upstream.port}/list.m3u8","container":"hls"}}"""),
        )
        assertEquals(201, created.status, created.body)
        val sid = Json.parseToJsonElement(created.body).jsonObject["session_id"]!!.jsonPrimitive.content
        record("/v1/sessions", "POST", c.call("POST", u("/v1/sessions"), """{"source":{"kind":"library","item_id":"nope"}}"""))
        record("/v1/sessions", "POST", c.call("POST", u("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:1/x.m3u8"},"profile":"p-nope"}"""))
        record("/v1/sessions", "POST", c.call("POST", u("/v1/sessions"), """{"bogus":1}"""))
        waitUntil("init.mp4") { c.get(u("/v1/sessions/$sid/init.mp4")).use { it.code == 200 } }
        waitUntil("segment 0") { c.get(u("/v1/sessions/$sid/seg_0.m4s")).use { it.code == 200 } }
        record("/v1/sessions/{id}", "GET", c.get(u("/v1/sessions/$sid")))
        record("/v1/sessions/{id}", "GET", c.get(u("/v1/sessions/00000000-0000-4000-8000-000000000000")))
        record("/v1/sessions/{id}/master.m3u8", "GET", c.get(u("/v1/sessions/$sid/master.m3u8")))
        record("/v1/sessions/{id}/media.m3u8", "GET", c.get(u("/v1/sessions/$sid/media.m3u8")))
        record("/v1/sessions/{id}/init.mp4", "GET", c.get(u("/v1/sessions/$sid/init.mp4")))
        record("/v1/sessions/{id}/seg_{k}.m4s", "GET", c.get(u("/v1/sessions/$sid/seg_0.m4s")))
        record("/v1/sessions/{id}/seg_{k}.m4s", "GET", c.get(u("/v1/sessions/nope/seg_0.m4s")))
        record("/v1/sessions/{id}/subtitles/{file}", "GET", c.get(u("/v1/sessions/$sid/subtitles/sub_9.vtt")))
        record("/v1/sessions/{id}/position", "POST", c.call("POST", u("/v1/sessions/$sid/position"), """{"position_secs":1.0,"paused":false}"""))
        record("/v1/sessions/{id}/position", "POST", c.call("POST", u("/v1/sessions/$sid/position"), """{"position_secs":"x"}"""))

        record("/v1/history", "POST", c.call("POST", u("/v1/history"), """{"items":[]}"""))
        record("/v1/history", "POST", c.call("POST", u("/v1/history"), "[1]"))
        record("/v1/history", "GET", c.get(u("/v1/history?since=0&limit=10")))
        record("/v1/history", "GET", c.get(u("/v1/history?since=x")))
        record("/v1/history/mode", "GET", c.get(u("/v1/history/mode")))
        record("/v1/history/resync-done", "POST", c.call("POST", u("/v1/history/resync-done")))
        record("/v1/history", "DELETE", c.call("DELETE", u("/v1/history")))
        record("/v1/control", "POST", c.call("POST", u("/v1/control"), """{"command":"play"}"""))
        record("/v1/control", "POST", c.call("POST", u("/v1/control"), """{"command":"dance"}"""))
        record("/v1/player", "GET", c.get(u("/v1/player")))
        record("/v1/events", "GET", c.get(u("/v1/events?wait=1")))
        record("/v1/events", "GET", c.get(u("/v1/events?wait=x")))
        record("/v1/sessions/{id}", "DELETE", c.call("DELETE", u("/v1/sessions/$sid")))
        record("/v1/sessions/{id}", "DELETE", c.call("DELETE", u("/v1/sessions/$sid")))
        record("/v1/sessions/{id}/media.m3u8", "GET", c.get(u("/v1/sessions/$sid/media.m3u8")))

        // undocumented methods on documented paths: 405, no body
        val paths = contract["paths"]!!.jsonObject
        for ((template, ops) in paths) {
            val concrete = template.replace("{id}", sid).replace("{k}", "0").replace("{file}", "sub_0.vtt")
            for (m in listOf("GET", "POST", "PUT", "DELETE", "PATCH")) {
                if (m.lowercase() in ops.jsonObject) continue
                val client = if (template == "/v1/pairing") boot else c
                client.call(m, u(concrete), if (m == "GET" || m == "DELETE") null else "{}").use { r ->
                    assertEquals(405, r.code, "$m $template")
                    assertEquals("v1", r.header("Nijiyome-Link"), "$m $template")
                }
            }
        }

        // the last one: forgetting the device itself
        record("/v1/devices/self", "DELETE", c.call("DELETE", u("/v1/devices/self")))
        // unauthenticated / revoked
        record("/v1/capabilities", "GET", boot.get(u("/v1/capabilities")))
        // the forgotten client may already be refused at the TLS layer: then there is no HTTP answer to check
        runCatching { c.get(u("/v1/capabilities")) }.getOrNull()?.let { record("/v1/capabilities", "GET", it) }

        // every operation of the contract was called at least once
        val called = answers.map { it.template to it.method }.toSet()
        val documented = paths.flatMap { (t, ops) -> ops.jsonObject.keys.filter { it != "parameters" }.map { t to it } }.toSet()
        assertEquals(emptySet(), documented - called, "operations never exercised")

        val problems = answers.flatMap { check(it) }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun check(a: Answer): List<String> {
        val where = "${a.method.uppercase()} ${a.template} -> ${a.status}"
        val op = contract["paths"]!!.jsonObject[a.template]?.jsonObject?.get(a.method)?.jsonObject ?: return listOf("$where: not in the contract")
        var resp = op["responses"]!!.jsonObject[a.status.toString()]?.jsonObject ?: return listOf("$where: undocumented status (${a.body.take(200)})")
        resp["\$ref"]?.let { resp = resolve(it.jsonPrimitive.content) }
        val out = mutableListOf<String>()
        if (a.contract != "v1") out += "$where: Nijiyome-Link header is ${a.contract}"
        val content = resp["content"]?.jsonObject
        if (content == null) {
            if (a.body.isNotEmpty()) out += "$where: body where the contract has none: ${a.body.take(120)}"
            return out
        }
        val type = a.contentType?.substringBefore(';')?.trim()
        val media = content.keys.firstOrNull { it.substringBefore(';').trim() == type } ?: return out + "$where: content-type $type, documented ${content.keys}"
        if (media == "application/json") {
            val schema = content[media]!!.jsonObject["schema"]!!.jsonObject
            val body = runCatching { Json.parseToJsonElement(a.body) }.getOrElse { return out + "$where: body is not JSON" }
            val errs = mutableListOf<String>()
            validate(schema, body, "$", errs)
            out += errs.map { "$where: $it" }
        }
        return out
    }

    private fun resolve(ref: String): JsonObject {
        var node: JsonElement = contract
        for (part in ref.removePrefix("#/").split('/')) node = node.jsonObject[part]!!
        return node.jsonObject
    }

    @Test
    fun aPlayerOnThePcAndATorrentSourceAnswerAsTheContractSays() {
        val host = PlayerHost()
        host.playing = app.nijiyome.link.PlayerStateDto(playing = true, title = "One Piece", episode = 3, positionSecs = 125, durationSecs = 1440, mediaId = "21")
        val h = harness(FakeEngine(3, 200, 1), http = app.nijiyome.server.net.SafeHttp(app.nijiyome.server.net.NetPolicy.linkSource(false))) { it.host = host }.also { open += it }
        val stub = Stub { Canned(200, listOf("content-type" to "video/x-matroska"), ByteArray(64)) }.also { open += it }
        host.torrent = OpenedTorrent("http://127.0.0.1:${stub.port}/tok123/0123456789abcdef0123456789abcdef01234567/0/ep3.mkv", "0123456789abcdef0123456789abcdef01234567")
        val c = h.pair("Pixel").client
        fun u(path: String) = h.url(path)
        record("/v1/player", "GET", c.get(u("/v1/player")))
        for (body in listOf("""{"command":"toggle"}""", """{"command":"seek","seconds":90}""", """{"command":"open","mediaId":"21","episode":3,"positionSecs":120}""")) {
            assertEquals(204, record("/v1/control", "POST", c.call("POST", u("/v1/control"), body)).status, body)
        }
        val created = record(
            "/v1/sessions", "POST",
            c.call("POST", u("/v1/sessions"), """{"source":{"kind":"torrent","uri":"magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567","media_id":"21","episode":3}}"""),
        )
        assertEquals(201, created.status, created.body)
        val sid = Json.parseToJsonElement(created.body).jsonObject["session_id"]!!.jsonPrimitive.content
        record("/v1/sessions/{id}", "GET", c.get(u("/v1/sessions/$sid")))
        assertEquals(3, host.got.size)
        host.playing = null
        record("/v1/player", "GET", c.get(u("/v1/player")))
        record("/v1/control", "POST", c.call("POST", u("/v1/control"), """{"command":"play"}"""))
        assertEquals(listOf(404, 409), answers.takeLast(2).map { it.status })
        val problems = answers.flatMap { check(it) }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /** The JSON Schema subset of the contract: \$ref, type, const, enum, required, properties, additionalProperties, items, bounds, oneOf. */
    private fun validate(schema0: JsonObject, v: JsonElement, at: String, errs: MutableList<String>) {
        val schema = schema0["\$ref"]?.let { resolve(it.jsonPrimitive.content) } ?: schema0
        schema["oneOf"]?.jsonArray?.let { alts ->
            val ok = alts.count { alt -> mutableListOf<String>().also { validate(alt.jsonObject, v, at, it) }.isEmpty() }
            if (ok == 0) errs += "$at matches none of oneOf"
        }
        schema["type"]?.let { t ->
            val types = (t as? JsonArray)?.map { it.jsonPrimitive.content } ?: listOf(t.jsonPrimitive.content)
            if (types.none { typeOk(it, v) }) {
                errs += "$at is not ${types.joinToString("|")}: $v"
                return
            }
        }
        schema["const"]?.let { if (it != v) errs += "$at is $v, const $it" }
        schema["enum"]?.jsonArray?.let { if (v !in it) errs += "$at is $v, not in ${it.joinToString()}" }
        val prim = v as? JsonPrimitive
        if (prim != null && v !is JsonNull) {
            val n = if (prim.isString) null else prim.doubleOrNull
            schema["minimum"]?.jsonPrimitive?.doubleOrNull?.let { if (n != null && n < it) errs += "$at = $n < $it" }
            schema["maximum"]?.jsonPrimitive?.doubleOrNull?.let { if (n != null && n > it) errs += "$at = $n > $it" }
            if (prim.isString) {
                schema["maxLength"]?.jsonPrimitive?.longOrNull?.let { if (prim.content.length > it) errs += "$at longer than $it" }
                schema["minLength"]?.jsonPrimitive?.longOrNull?.let { if (prim.content.length < it) errs += "$at shorter than $it" }
                schema["pattern"]?.jsonPrimitive?.content?.let { if (!Regex(it).containsMatchIn(prim.content)) errs += "$at '${prim.content}' does not match $it" }
            }
        }
        if (v is JsonObject) {
            schema["required"]?.jsonArray?.forEach { k -> if (k.jsonPrimitive.content !in v) errs += "$at.${k.jsonPrimitive.content} is required" }
            val props = schema["properties"]?.jsonObject.orEmpty()
            for ((k, child) in v) {
                val ps = props[k]?.jsonObject
                when {
                    ps != null -> validate(ps, child, "$at.$k", errs)
                    schema["additionalProperties"] is JsonObject -> validate(schema["additionalProperties"]!!.jsonObject, child, "$at.$k", errs)
                    schema["additionalProperties"]?.jsonPrimitive?.booleanOrNull == false -> errs += "$at.$k is not in the schema"
                }
            }
        }
        if (v is JsonArray) {
            schema["maxItems"]?.jsonPrimitive?.longOrNull?.let { if (v.size > it) errs += "$at has more than $it items" }
            schema["items"]?.jsonObject?.let { item -> v.forEachIndexed { i, e -> validate(item, e, "$at[$i]", errs) } }
        }
    }

    private fun typeOk(t: String, v: JsonElement): Boolean = when (t) {
        "object" -> v is JsonObject
        "array" -> v is JsonArray
        "null" -> v is JsonNull
        "string" -> v is JsonPrimitive && v !is JsonNull && v.isString
        "boolean" -> v is JsonPrimitive && v !is JsonNull && !v.isString && v.booleanOrNull != null
        "integer" -> v is JsonPrimitive && v !is JsonNull && !v.isString && v.longOrNull != null
        "number" -> v is JsonPrimitive && v !is JsonNull && !v.isString && v.doubleOrNull != null
        else -> true
    }
}
