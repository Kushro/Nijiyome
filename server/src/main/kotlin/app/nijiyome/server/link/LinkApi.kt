// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/api.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The `Nijiyome-Link: v1` header every answer carries (the phone checks it: `LinkHttp.CONTRACT_HEADER`). */
const val CONTRACT_HEADER = "Nijiyome-Link"
const val CONTRACT_VERSION = "v1"

/**
 * Link v1 error catalogue (contracts/link/openapi.v1.json `x-errors`, the M0 rows the server produces). Messages are FIXED: never
 * paths, secrets, tokens or pairing codes. `no_player` (wave B) is not part of the table: see [noPlayerBody].
 */
enum class ErrorCode(val code: String, val status: Int, val message: String) {
    CLIENT_CERTIFICATE_REQUIRED("client_certificate_required", 401, "a client certificate is required"),
    DEVICE_NOT_AUTHORIZED("device_not_authorized", 403, "this device is not authorized"),
    PAIRING_CLOSED("pairing_closed", 409, "no pairing window is open"),
    PAIRING_CODE_INVALID("pairing_code_invalid", 403, "the pairing code is invalid"),
    PAIRING_ATTEMPTS_EXCEEDED("pairing_attempts_exceeded", 429, "too many failed attempts; the pairing window is closed"),
    CSR_INVALID("csr_invalid", 400, "the certificate signing request is invalid"),
    REQUEST_INVALID("request_invalid", 400, "the request is invalid"),
    BODY_TOO_LARGE("body_too_large", 413, "the request body is too large"),
    BODY_TIMEOUT("body_timeout", 408, "the request body was not received in time"),
    PROFILE_UNAVAILABLE("profile_unavailable", 422, "the requested profile is not available"),
    LIBRARY_ITEM_NOT_FOUND("library_item_not_found", 404, "the library item was not found"),
    SOURCE_UNAVAILABLE("source_unavailable", 503, "the source is not available"),
    SESSION_NOT_FOUND("session_not_found", 404, "session or resource not found"),
    SESSION_GONE("session_gone", 410, "the session is closed"),
    SESSION_NOT_READY("session_not_ready", 409, "the session is still being prepared"),
    SEGMENT_WAIT_TIMEOUT("segment_wait_timeout", 503, "the segment is not ready yet"),
    SERVER_BUSY("server_busy", 503, "the server is busy"),
    INTERNAL("internal", 500, "internal server error"),
    URL_SOURCE_DENIED("url_source_denied", 403, "the url source is not allowed"),
    /** 0.4.1: a media file of a failed session; final for the session (the phone opens a new one at its position). */
    SEGMENT_UNAVAILABLE("segment_unavailable", 410, "the segment cannot be produced; open the session again"),
    ;

    /** `{"error":{"code","message"}}` with the fixed message. */
    fun body(): JsonObject = buildJsonObject {
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }
}

/** An error answer of a handler. */
class ApiException(val error: ErrorCode) : Exception(error.code)

fun api(error: ErrorCode): Nothing = throw ApiException(error)

/** `no_player` of `POST /v1/control` (409) and `GET /v1/player` (404): wave B, outside the M0 catalogue. */
const val NO_PLAYER = "no_player"

fun noPlayerBody(): JsonObject = buildJsonObject {
    putJsonObject("error") {
        put("code", NO_PLAYER)
        put("message", "no player is open on this PC")
    }
}

/** JSON of the server's answers: unknown values are omitted (never `null`), defaults that matter are written. */
internal object Wire {
    val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }

    /** For answers whose `false` / default fields are omitted on the wire (`HistoryPushResult.ignored`). */
    val sparse = Json {
        explicitNulls = false
        encodeDefaults = false
    }

    /** Reading the phone's bodies: unknown keys are tolerated only where the contract says so (see each route). */
    val lenient = Json { ignoreUnknownKeys = true }
}
