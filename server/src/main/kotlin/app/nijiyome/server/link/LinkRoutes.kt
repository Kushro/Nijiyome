// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/server.rs (routes and handlers; AGPL-3.0, same author).
package app.nijiyome.server.link

import app.nijiyome.link.LibraryDto
import app.nijiyome.link.LinkHistoryModeDto
import app.nijiyome.link.LinkHistoryPushDto
import app.nijiyome.link.LinkHistoryPushResultDto
import app.nijiyome.link.LinkResyncDoneDto
import app.nijiyome.link.PlayerStateDto
import app.nijiyome.link.SessionCreatedDto
import app.nijiyome.link.LinkHistoryPageDto
import app.nijiyome.server.http.BodyTooLarge
import app.nijiyome.server.http.nettyChannel
import app.nijiyome.server.http.readBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.util.TreeMap

/**
 * The routes of Link v1. mTLS on every route except `POST /v1/pairing`, the active device checked PER REQUEST; an unknown route
 * with an authorized device answers `404 session_not_found`, a known route with another method `405` without a body. Every
 * answer carries `Nijiyome-Link: v1`. Bodies have a per-route cap and must arrive within [BODY_TIMEOUT_MS].
 */
internal class LinkRouter(private val s: LinkServer) {
    private val m get() = s.manager

    suspend fun handle(call: ApplicationCall) {
        call.response.header(CONTRACT_HEADER, CONTRACT_VERSION)
        try {
            route(call)
        } catch (e: ApiException) {
            respondError(call, e.error)
        } catch (e: TimeoutCancellationException) {
            respondError(call, ErrorCode.BODY_TIMEOUT)
        }
    }

    private suspend fun route(call: ApplicationCall) {
        val path = call.request.path()
        val method = call.request.httpMethod
        if (path == "/v1/pairing") {
            if (method != HttpMethod.Post) return methodNotAllowed(call)
            return pairing(call)
        }
        val device = authorize(call)
        val seg = path.removePrefix("/").split('/')
        if (seg.firstOrNull() != "v1") api(ErrorCode.SESSION_NOT_FOUND)
        val rest = seg.drop(1)
        when {
            rest == listOf("capabilities") -> on(call, method, HttpMethod.Get) { capabilities(call) }
            rest == listOf("library") -> on(call, method, HttpMethod.Get) { library(call) }
            rest == listOf("history") -> when (method) {
                HttpMethod.Get -> historyPage(call, device)
                HttpMethod.Post -> historyPush(call, device)
                HttpMethod.Delete -> historyClear(call)
                else -> methodNotAllowed(call)
            }
            rest == listOf("history", "mode") -> on(call, method, HttpMethod.Get) { historyMode(call, device) }
            rest == listOf("history", "resync-done") -> on(call, method, HttpMethod.Post) { historyResyncDone(call, device) }
            rest == listOf("devices", "self") -> on(call, method, HttpMethod.Delete) { forgetSelf(call, device) }
            rest == listOf("control") -> on(call, method, HttpMethod.Post) { control(call) }
            rest == listOf("player") -> on(call, method, HttpMethod.Get) { player(call) }
            rest == listOf("events") -> on(call, method, HttpMethod.Get) { events(call, device) }
            rest == listOf("sessions") -> on(call, method, HttpMethod.Post) { createSession(call, device) }
            rest.size == 2 && rest[0] == "sessions" -> when (method) {
                HttpMethod.Get -> respondJson(call, HttpStatusCode.OK, m.stateJson(m.owned(rest[1], device), s.instanceId))
                HttpMethod.Delete -> {
                    // idempotent while remembered (contract): a closed session still in the tombstones answers 204 too, where tk-link
                    // (server.rs delete_session -> sessions.rs owned) answers 410 session_gone; unknown ids stay 404
                    val owned = try {
                        m.owned(rest[1], device)
                    } catch (e: ApiException) {
                        if (e.error != ErrorCode.SESSION_GONE) throw e
                        null
                    }
                    owned?.let { m.close(it.id) }
                    respondEmpty(call, HttpStatusCode.NoContent)
                }
                else -> methodNotAllowed(call)
            }
            rest.size == 3 && rest[0] == "sessions" && rest[2] == "position" -> on(call, method, HttpMethod.Post) { position(call, device, rest[1]) }
            rest.size == 3 && rest[0] == "sessions" -> on(call, method, HttpMethod.Get) { sessionFile(call, device, rest[1], rest[2]) }
            rest.size == 4 && rest[0] == "sessions" && rest[2] == "subtitles" -> on(call, method, HttpMethod.Get) { subtitle(call, device, rest[1], rest[3]) }
            else -> api(ErrorCode.SESSION_NOT_FOUND)
        }
    }

    private suspend inline fun on(call: ApplicationCall, method: HttpMethod, allowed: HttpMethod, block: () -> Unit) {
        if (method == allowed || (allowed == HttpMethod.Get && method == HttpMethod.Head)) block() else methodNotAllowed(call)
    }

    /** The device of the connection's client certificate, or why not (401 without one, 403 unknown / revoked / expired). */
    private fun authorize(call: ApplicationCall): String {
        val info = call.nettyChannel()?.attr(CONN_INFO)?.get() ?: api(ErrorCode.INTERNAL)
        if (!info.hasCert) api(ErrorCode.CLIENT_CERTIFICATE_REQUIRED)
        val device = info.deviceId ?: api(ErrorCode.DEVICE_NOT_AUTHORIZED)
        val now = LinkServer.now()
        if (!s.devices.isActive(device, now)) api(ErrorCode.DEVICE_NOT_AUTHORIZED)
        // `link.last-seen`: every authenticated request refreshes last-seen and the platform
        s.devices.touch(device, now, call.request.header("User-Agent"))
        return device
    }

    // ---- handlers -----------------------------------------------------------------------------------------------------

    private suspend fun pairing(call: ApplicationCall) {
        val b = readObject(call, 16 * 1024)
        if (b.keys != setOf("code", "device_name", "csr_pem", "platform")) api(ErrorCode.REQUEST_INVALID)
        val code = b.str("code")
        val name = b.str("device_name")
        val csr = b.str("csr_pem")
        val platform = b.str("platform")
        val nameLen = name.codePointCount(0, name.length)
        if (platform != "android" || nameLen == 0 || nameLen > 64 || csr.length > 8192 || code.length > 32) api(ErrorCode.REQUEST_INVALID)
        val issued = try {
            s.pairing.redeem(code, csr, name)
        } catch (e: PairingException) {
            api(e.error)
        }
        s.emit(LinkEvent.DevicePaired(issued.deviceId, app.nijiyome.link.LinkCsr.sanitizeDeviceName(name)))
        respondJson(
            call,
            HttpStatusCode.Created,
            buildJsonObject {
                put("device_id", issued.deviceId)
                put("client_cert_pem", issued.clientCertPem)
                put("ca_cert_pem", issued.caCertPem)
                put("server_name", s.serverName)
            },
        )
    }

    private suspend fun capabilities(call: ApplicationCall) = respondJson(call, HttpStatusCode.OK, s.capabilities())

    private suspend fun library(call: ApplicationCall) {
        val items = withContext(Dispatchers.IO) { s.host.library() }
        respondDto(call, LibraryDto.serializer(), LibraryDto(items.take(Library.MAX_ITEMS), items.size > Library.MAX_ITEMS))
    }

    private suspend fun historyPage(call: ApplicationCall, device: String) {
        val q = query(call, setOf("since", "limit"))
        val since = q["since"]?.let { it.toLongOrNull() ?: api(ErrorCode.REQUEST_INVALID) } ?: 0
        // a u64 above Long.MAX_VALUE is still "as many as possible" (500), never a wrapped negative
        val limitRaw = q["limit"]?.let { v -> v.toULongOrNull()?.let { u -> if (u > Long.MAX_VALUE.toULong()) Long.MAX_VALUE else u.toLong() } ?: api(ErrorCode.REQUEST_INVALID) }
        if (since < 0) api(ErrorCode.REQUEST_INVALID)
        val mode = s.devices.historyMode(device)
        if (!mode.servesPulls) return respondDto(call, LinkHistoryPageDto.serializer(), History.pausedPage(since))
        val limit = History.clampLimit(limitRaw)
        val rows = withContext(Dispatchers.IO) { s.host.historySince(since, limit + 1) }
        respondDto(call, LinkHistoryPageDto.serializer(), History.page(rows, limit, since, mode))
    }

    private suspend fun historyPush(call: ApplicationCall, device: String) {
        val o = readObject(call, History.MAX_BODY)
        if (o.keys != setOf("items")) api(ErrorCode.REQUEST_INVALID)
        val push = try {
            Wire.lenient.decodeFromJsonElement(LinkHistoryPushDto.serializer(), o)
        } catch (_: IllegalArgumentException) {
            api(ErrorCode.REQUEST_INVALID)
        }
        if (push.items.isEmpty() || push.items.size > History.MAX_ROWS) api(ErrorCode.REQUEST_INVALID)
        val mode = s.devices.historyMode(device)
        if (!mode.acceptsPushes) {
            return respondJson(call, HttpStatusCode.OK, Wire.sparse.encodeToJsonElement(LinkHistoryPushResultDto.serializer(), LinkHistoryPushResultDto(0, true, mode.wire)))
        }
        val authoritative = s.devices.resyncOpen(device, LinkServer.now())
        val rows = push.items.mapNotNull(History::entryOf)
        val merged = withContext(Dispatchers.IO) { s.host.historyMergeFrom(device, rows, authoritative) }
        respondJson(call, HttpStatusCode.OK, Wire.sparse.encodeToJsonElement(LinkHistoryPushResultDto.serializer(), LinkHistoryPushResultDto(merged, false, mode.wire)))
    }

    private suspend fun historyMode(call: ApplicationCall, device: String) {
        val answer = LinkHistoryModeDto(s.devices.historyMode(device).wire, s.devices.resyncOpen(device, LinkServer.now()))
        respondDto(call, LinkHistoryModeDto.serializer(), answer)
    }

    private suspend fun historyResyncDone(call: ApplicationCall, device: String) {
        val notified = s.devices.finishResync(device, LinkServer.now())
        respondDto(call, LinkResyncDoneDto.serializer(), LinkResyncDoneDto(notified))
    }

    private suspend fun historyClear(call: ApplicationCall) {
        val ok = withContext(Dispatchers.IO) { s.host.historyClear() }
        if (!ok) api(ErrorCode.INTERNAL)
        respondEmpty(call, HttpStatusCode.NoContent)
    }

    private suspend fun forgetSelf(call: ApplicationCall, device: String) {
        val channel = call.nettyChannel()
        // every other connection of the device closes now; this one after its answer
        channel?.let(s.connections::spare)
        s.devices.revoke(device, LinkServer.now())
        m.closeForDevice(device)
        s.emit(LinkEvent.DeviceRevoked(device))
        respondEmpty(call, HttpStatusCode.NoContent)
        channel?.eventLoop()?.execute { channel.close() }
    }

    private suspend fun control(call: ApplicationCall) {
        val cmd = ControlCommand.parse(readObject(call, 4 * 1024)) ?: api(ErrorCode.REQUEST_INVALID)
        if (!ControlCommand.valid(cmd)) api(ErrorCode.REQUEST_INVALID)
        val opens = cmd is ControlCommand.Open
        if (!opens && s.host.playerState() == null) return respondJson(call, HttpStatusCode.Conflict, noPlayerBody())
        if (s.host.control(cmd)) respondEmpty(call, HttpStatusCode.NoContent) else respondJson(call, HttpStatusCode.Conflict, noPlayerBody())
    }

    private suspend fun player(call: ApplicationCall) {
        val p = s.host.playerState() ?: return respondJson(call, HttpStatusCode.NotFound, noPlayerBody())
        respondDto(call, PlayerStateDto.serializer(), p)
    }

    /** `GET /v1/events?wait=`: long-poll of the calling device's mailbox (`200` + events, or `204` after `wait`). */
    private suspend fun events(call: ApplicationCall, device: String) {
        val q = query(call, setOf("wait"))
        val wait = clampWait(q["wait"]?.let { it.toUIntOrNull()?.toLong() ?: api(ErrorCode.REQUEST_INVALID) })
        val deadline = System.nanoTime() + wait * 1_000_000_000L
        while (true) {
            // read before the check, so an event queued in between still wakes us
            val v = s.devices.eventsVersion.value
            val events = s.devices.takeEvents(device, LinkServer.now())
            if (events.isNotEmpty()) return respondJson(call, HttpStatusCode.OK, eventsPage(events))
            val left = (deadline - System.nanoTime()) / 1_000_000
            if (left <= 0 || withTimeoutOrNull(left) { s.devices.eventsVersion.first { it != v } } == null) {
                return respondEmpty(call, HttpStatusCode.NoContent)
            }
            if (!s.devices.isActive(device, LinkServer.now())) api(ErrorCode.DEVICE_NOT_AUTHORIZED)
        }
    }

    private suspend fun createSession(call: ApplicationCall, device: String) {
        val req = parseCreate(readObject(call, 4 * 1024))
        val created = m.create(device, req)
        respondDto(call, SessionCreatedDto.serializer(), created, HttpStatusCode.Created)
    }

    private suspend fun position(call: ApplicationCall, device: String, id: String) {
        val o = readObject(call, 1024)
        if (o.keys != setOf("position_secs", "paused")) api(ErrorCode.REQUEST_INVALID)
        val pos = (o["position_secs"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: api(ErrorCode.REQUEST_INVALID)
        val paused = (o["paused"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: api(ErrorCode.REQUEST_INVALID)
        if (!pos.isFinite() || pos < 0) api(ErrorCode.REQUEST_INVALID)
        m.heartbeat(m.owned(id, device), pos, paused)
        respondEmpty(call, HttpStatusCode.NoContent)
    }

    private suspend fun sessionFile(call: ApplicationCall, device: String, id: String, file: String) {
        val session = m.owned(id, device)
        when (file) {
            "master.m3u8" -> respondMedia(call, m.masterPlaylist(session).toByteArray(), "application/vnd.apple.mpegurl", noStore = true)
            "media.m3u8" -> respondMedia(call, m.mediaPlaylist(session).toByteArray(), "application/vnd.apple.mpegurl", noStore = true)
            "init.mp4" -> respondMedia(call, m.initSegment(session), "video/mp4", noStore = false)
            else -> {
                // no source is longer than a day: a larger index (e.g. a u32 that would wrap to a negative Int) is not a segment
                val k = file.removePrefix("seg_").removeSuffix(".m4s").takeIf { file.startsWith("seg_") && file.endsWith(".m4s") }?.toUIntOrNull()
                    ?.takeIf { it <= MAX_SEGMENT_INDEX.toUInt() }?.toInt()
                    ?: api(ErrorCode.SESSION_NOT_FOUND)
                respondMedia(call, m.segment(session, k), "video/iso.segment", noStore = false)
            }
        }
    }

    private suspend fun subtitle(call: ApplicationCall, device: String, id: String, file: String) {
        val session = m.owned(id, device)
        val (n, format) = Subtitles.parseFileName(file) ?: api(ErrorCode.SESSION_NOT_FOUND)
        respondMedia(call, m.subtitle(session, n, format), format.contentType, noStore = true)
    }

    // ---- body / query parsing -----------------------------------------------------------------------------------------

    private suspend fun readObject(call: ApplicationCall, limit: Int): JsonObject {
        val bytes = try {
            call.readBody(limit, BODY_TIMEOUT_MS)
        } catch (_: BodyTooLarge) {
            api(ErrorCode.BODY_TOO_LARGE)
        } catch (e: TimeoutCancellationException) {
            api(ErrorCode.BODY_TIMEOUT)
        } catch (e: ApiException) {
            throw e
        } catch (_: Exception) {
            api(ErrorCode.REQUEST_INVALID)
        }
        return try {
            Wire.lenient.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: api(ErrorCode.REQUEST_INVALID)
    }

    /** `?k=v&...` with only [allowed] keys, each at most once (anything else is `400 request_invalid`, never plain text). */
    private fun query(call: ApplicationCall, allowed: Set<String>): Map<String, String> {
        val raw = call.request.queryString()
        if (raw.isEmpty()) return emptyMap()
        val out = HashMap<String, String>()
        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val k = runCatching { URLDecoder.decode(part.substringBefore('='), "UTF-8") }.getOrNull() ?: api(ErrorCode.REQUEST_INVALID)
            val v = runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: api(ErrorCode.REQUEST_INVALID)
            if (k !in allowed || out.put(k, v) != null) api(ErrorCode.REQUEST_INVALID)
        }
        return out
    }

    // ---- answers ------------------------------------------------------------------------------------------------------

    private suspend fun respondError(call: ApplicationCall, e: ErrorCode) {
        if (e == ErrorCode.SEGMENT_WAIT_TIMEOUT) call.response.header("Retry-After", "1")
        respondJson(call, HttpStatusCode.fromValue(e.status), e.body())
    }

    private suspend fun <T> respondDto(call: ApplicationCall, serializer: KSerializer<T>, value: T, status: HttpStatusCode = HttpStatusCode.OK) =
        respondJson(call, status, Wire.json.encodeToJsonElement(serializer, value))

    private suspend fun respondJson(call: ApplicationCall, status: HttpStatusCode, body: JsonElement) {
        call.respondText(Wire.json.encodeToString(JsonElement.serializer(), body), ContentType.Application.Json, status)
    }

    private suspend fun respondEmpty(call: ApplicationCall, status: HttpStatusCode) {
        call.respondBytes(ByteArray(0), null, status)
    }

    private suspend fun methodNotAllowed(call: ApplicationCall) = respondEmpty(call, HttpStatusCode.MethodNotAllowed)

    private suspend fun respondMedia(call: ApplicationCall, body: ByteArray, type: String, noStore: Boolean) {
        if (noStore) call.response.header("Cache-Control", "no-store")
        call.respondBytes(body, ContentType.parse(type), HttpStatusCode.OK)
    }

    companion object {
        const val BODY_TIMEOUT_MS = 10_000L

        private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: api(ErrorCode.REQUEST_INVALID)

        private fun JsonObject.optStr(key: String): String? = when (val v = this[key]) {
            null, JsonNull -> null
            is JsonPrimitive -> if (v.isString) v.content else api(ErrorCode.REQUEST_INVALID)
            else -> api(ErrorCode.REQUEST_INVALID)
        }

        private fun JsonObject.u32(key: String): Long? = when (val v = this[key]) {
            null -> null
            is JsonPrimitive -> v.takeIf { !it.isString }?.longOrNull?.takeIf { it in 0..UInt.MAX_VALUE.toLong() } ?: api(ErrorCode.REQUEST_INVALID)
            else -> api(ErrorCode.REQUEST_INVALID)
        }

        /** `POST /v1/sessions` body: `source` (by `kind`), `profile?`, `audio_track?`, `start_secs?`; unknown fields refused. */
        fun parseCreate(o: JsonObject): CreateRequest {
            if (!setOf("source", "profile", "audio_track", "start_secs").containsAll(o.keys)) api(ErrorCode.REQUEST_INVALID)
            val src = o["source"] as? JsonObject ?: api(ErrorCode.REQUEST_INVALID)
            fun only(vararg keys: String) {
                if (!(setOf("kind") + keys).containsAll(src.keys)) api(ErrorCode.REQUEST_INVALID)
            }
            val source = when (src.str("kind")) {
                "library" -> {
                    only("item_id")
                    SourceRequest.Library(src.str("item_id"))
                }
                "session" -> {
                    only("session_id")
                    SourceRequest.Session(src.str("session_id"))
                }
                "torrent" -> {
                    only("uri", "media_id", "episode")
                    SourceRequest.Torrent(src.str("uri"), src.str("media_id"), src.u32("episode") ?: api(ErrorCode.REQUEST_INVALID))
                }
                "url" -> {
                    only("url", "headers", "extension_id", "container")
                    val headers = TreeMap<String, String>()
                    when (val h = src["headers"]) {
                        null -> Unit
                        is JsonObject -> h.forEach { (k, v) -> headers[k] = (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: api(ErrorCode.REQUEST_INVALID) }
                        else -> api(ErrorCode.REQUEST_INVALID)
                    }
                    SourceRequest.Url(src.str("url"), headers, src.optStr("extension_id"), src.optStr("container"))
                }
                else -> api(ErrorCode.REQUEST_INVALID)
            }
            val start = when (val v = o["start_secs"]) {
                null -> 0.0
                is JsonPrimitive -> v.takeIf { !it.isString }?.doubleOrNull ?: api(ErrorCode.REQUEST_INVALID)
                else -> api(ErrorCode.REQUEST_INVALID)
            }
            return CreateRequest(source, o.optStr("profile"), o.u32("audio_track") ?: 0, start)
        }
    }
}
