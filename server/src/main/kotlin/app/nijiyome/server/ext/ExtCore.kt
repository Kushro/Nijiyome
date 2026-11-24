// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-server/src/api.rs, auth.rs and logs.rs (AGPL-3.0, same author); the API is the classic server's
// (/api/server/v1, `{ success, data }` / `{ success: false, error: { code, message } }`), which :core:extension-server speaks.
package app.nijiyome.server.ext

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID

/** `GET /health` `protocolVersion` (unchanged since the classic server). */
const val PROTOCOL_VERSION = 1

/** Path prefix of every route. */
const val API_PREFIX = "/api/server/v1"

/** Classic default port. */
const val DEFAULT_EXT_PORT = 8099

/** Any error answer of the API, with the HTTP status the classic server uses. */
class Failure(val status: Int, val code: String, val detail: String? = null) : Exception(code) {
    fun withMessage(m: String) = Failure(status, code, m)

    fun body(): JsonObject = buildJsonObject {
        put("success", false)
        putJsonObject("error") {
            put("code", code)
            put("message", detail ?: code)
        }
    }

    companion object {
        /** 401: no token and the server requires one. */
        val AUTHENTICATION_REQUIRED get() = Failure(401, "authentication_required")
        /** 401: viewer login without a configured password. */
        val PAIRING_REQUIRED get() = Failure(401, "pairing_required")
        /** 401: code unknown, used or expired. */
        val PAIRING_EXPIRED get() = Failure(401, "pairing_expired")
        /** 401: token revoked / unknown. */
        val DEVICE_NOT_AUTHORIZED get() = Failure(401, "device_not_authorized")
        /** 403: a viewer called an owner route. */
        val FORBIDDEN get() = Failure(403, "forbidden")
        /** 409: 50 live pairing codes. */
        val PAIRING_LIMIT get() = Failure(409, "pairing_limit")
        val NOT_FOUND get() = Failure(404, "not_found")
        fun badRequest(m: String) = Failure(400, "bad_request", m)
        fun upstream(m: String) = Failure(502, "upstream_failed", m)
        val INTERNAL get() = Failure(500, "internal")
    }
}

/** The success envelope `{ "success": true, "data": <value> }`. */
fun ok(data: JsonElement): JsonObject = buildJsonObject {
    put("success", true)
    put("data", data)
}

/** Tokens, pairing codes and the viewer password. */
object ExtAuth {
    const val PAIRING_TTL_SECS = 300
    const val PAIRING_LIMIT = 50
    const val PASSWORD_MIN_CHARS = 10
    const val PASSWORD_MAX_CHARS = 1024
    private val random = SecureRandom()

    /** `nijiyome://extensionserver/connect?server=<origin>&code=<code>` (what :core:extension-server's DeepLink parses). */
    fun deepLink(origin: String, code: String): String = "nijiyome://extensionserver/connect?server=${urlEncode(origin)}&code=${urlEncode(code)}"

    /** Percent-encoding of a query value (RFC 3986 unreserved kept). */
    fun urlEncode(s: String): String = buildString {
        for (b in s.toByteArray()) {
            val c = b.toInt() and 0xff
            if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code) {
                append(c.toChar())
            } else {
                append('%').append("%02X".format(c))
            }
        }
    }

    /** Six digits from a secure random source (a 5-minute single-use secret). */
    fun newCode(): String = "%06d".format(random.nextInt(1_000_000))

    /** A fresh bearer token (64 hex). */
    fun newToken(): String = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")

    fun tokenHash(token: String): String = sha256Hex(token.toByteArray())

    fun nowUnix(): Long = Instant.now().epochSecond

    fun passwordLenOk(password: String): Boolean = password.codePointCount(0, password.length) in PASSWORD_MIN_CHARS..PASSWORD_MAX_CHARS

    /** argon2id (v19, m=19456 KiB, t=2, p=1: the Rust crate's defaults) PHC string, salted with 16 random bytes. */
    fun hashPassword(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = argon2(password, salt, M_KIB, T_COST, P_LANES)
        val b64 = Base64.getEncoder().withoutPadding()
        return "\$argon2id\$v=19\$m=$M_KIB,t=$T_COST,p=$P_LANES\$${b64.encodeToString(salt)}\$${b64.encodeToString(hash)}"
    }

    /** Checks [password] against a PHC string of [hashPassword]; any parse error is a mismatch. */
    fun verifyPassword(password: String, phc: String): Boolean {
        if (!passwordLenOk(password)) return false
        val parts = phc.split('$')
        if (parts.size != 6 || parts[1] != "argon2id" || parts[2] != "v=19") return false
        val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toIntOrNull() }
        val m = params["m"] ?: return false
        val t = params["t"] ?: return false
        val p = params["p"] ?: return false
        if (m !in 8..(1 shl 20) || t !in 1..16 || p !in 1..16) return false
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[4]) }.getOrNull() ?: return false
        val want = runCatching { dec.decode(parts[5]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(argon2(password, salt, m, t, p, want.size), want)
    }

    private fun argon2(password: String, salt: ByteArray, m: Int, t: Int, p: Int, len: Int = 32): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(m)
            .withIterations(t)
            .withParallelism(p)
            .withSalt(salt)
            .build()
        val out = ByteArray(len)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(password.toByteArray(), out)
        return out
    }

    /** Constant-time equality of two secrets (compares their SHA-256 digests). */
    fun secretsEqual(a: String, b: String): Boolean = MessageDigest.isEqual(sha256(a.toByteArray()), sha256(b.toByteArray()))

    private const val M_KIB = 19_456
    private const val T_COST = 2
    private const val P_LANES = 1
}

internal fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
internal fun sha256Hex(b: ByteArray): String = sha256(b).joinToString("") { "%02x".format(it) }

/** One request / event of the log ring (never tokens, cookies or full media URLs). */
data class LogEntry(
    val id: Long,
    val atUnix: Long,
    /** `request`, `source_completed`, `source_failed`, `pairing`, `revoked`, ... */
    val event: String,
    val route: String? = null,
    val name: String? = null,
    val count: Int? = null,
    val durationMs: Long? = null,
    val error: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("atUnix", atUnix)
        put("event", event)
        route?.let { put("route", it) }
        name?.let { put("name", it) }
        count?.let { put("count", it) }
        durationMs?.let { put("durationMs", it) }
        error?.let { put("error", it) }
    }
}

/** A bounded ring of [LogEntry] with a monotonic cursor (`GET /logs?after=`). */
class LogBuffer {
    private var last = 0L
    private val entries = ArrayDeque<LogEntry>()

    /** Appends; the id is assigned here, and the error text is made safe to show ([safeError]). */
    @Synchronized
    fun push(entry: LogEntry): Long {
        last++
        if (entries.size >= CAPACITY) entries.pollFirst()
        entries.addLast(entry.copy(id = last, error = safeError(entry.error)))
        return last
    }

    /** Entries with `id > after` and the newest id (the next `after`). */
    @Synchronized
    fun since(after: Long): Pair<List<LogEntry>, Long> = entries.filter { it.id > after } to last

    companion object {
        const val CAPACITY = 500

        /** Longest error text kept, in code points (then "…"). */
        const val ERROR_MAX = 200

        private val CONTROL = Regex("[\\p{Cc}\\p{Zl}\\p{Zp}\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]+")
        // Where a URL or path ends: before a space or quote, leaving a trailing ":", ",", ";", "." or ")" to the sentence.
        private const val END = """(?=[:,;.)]*(?:[\s'"]|$))"""
        private val URL = Regex("""\b([A-Za-z][A-Za-z0-9+.-]*)://(?:[^\s/?#@'"]*@)?([^\s/?#'"]*?)(?:[/?#][^\s'"]*?)?$END""")
        private val FILE_URI = Regex("""\bfile:[^\s'"]*?$END""", RegexOption.IGNORE_CASE)
        // A path runs on past spaces while the next word still has a separator ("C:\Program Files\x").
        private val WINDOWS_PATH = Regex("""(?<![\w])(?:[A-Za-z]:[\\/]|\\\\)[^\s'"]*?(?: [^\s'"\\/]*[\\/][^\s'"]*?)*$END""")
        private val UNIX_PATH = Regex(
            """(?<![\w.:/~-])(?:~/|/(?:home|Users|tmp|var|private|root|mnt|media|opt|data|storage|run|proc|etc|usr|srv|sdcard)(?=[/\s'"]|$)""" +
                """|/[^\s/'"]+/[^\s/'"]+/)[^\s'"]*?(?: [^\s'"/]*/[^\s'"]*?)*$END""",
        )
        private val SPACES = Regex("""\s+""")

        /**
         * [raw] as the log shows it (Settings › Nijiyome Link, `/logs`, `/diagnostics`): control and line-breaking characters become
         * spaces, URLs keep only their scheme and host (no user, path, query or fragment), absolute local paths become `<path>`,
         * and the text is cut to [ERROR_MAX] code points. An extension's or the disk's message never carries the machine's paths
         * or a full upstream URL into the log.
         */
        fun safeError(raw: String?): String? {
            if (raw == null) return null
            var s = CONTROL.replace(raw, " ")
            s = FILE_URI.replace(s, "<path>")
            s = URL.replace(s) { m -> "${m.groupValues[1]}://${m.groupValues[2]}" }
            s = WINDOWS_PATH.replace(s, "<path>")
            s = UNIX_PATH.replace(s, "<path>")
            s = SPACES.replace(s, " ").trim()
            if (s.codePointCount(0, s.length) > ERROR_MAX) s = s.substring(0, s.offsetByCodePoints(0, ERROR_MAX)).trimEnd() + "…"
            return s
        }
    }
}
