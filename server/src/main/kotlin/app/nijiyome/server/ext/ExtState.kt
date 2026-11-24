// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-server/src/state.rs (AGPL-3.0, same author).
package app.nijiyome.server.ext

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/** One paired device / token (`GET /devices` row, without the hash). */
@Serializable
data class DeviceRecord(
    val id: String,
    val label: String,
    /** `viewer` | `admin`. */
    val role: String,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
)

/** A catalogue repository mirrored for phones. */
@Serializable
data class Repository(val id: String, val name: String, val url: String)

@Serializable
private data class Persisted(
    val id: String? = null,
    val viewerHash: String? = null,
    val devices: List<DeviceRecord> = emptyList(),
    /** `device id -> sha256(token)`. */
    val tokenHashes: Map<String, String> = emptyMap(),
    /** Secret of `X-Nijiyome-Bootstrap` (owner token for local tools; the app itself never needs HTTP). */
    val bootstrap: String? = null,
    val repositories: List<Repository> = emptyList(),
    /** Changes with the viewer password, so anonymous media tickets minted before stop working. */
    val publicEpoch: String? = null,
)

class StateException(val kind: Kind, message: String = kind.name) : Exception(message) {
    enum class Kind { NO_DEVICE, PAIRING_LIMIT, DEVICE_LIMIT, REPOSITORY_LIMIT, PASSWORD, IO }
}

/**
 * `<data>/server/state.json` = server id, device records, token hashes (never the tokens), the argon2id viewer password hash, the
 * bootstrap secret, repositories and the public epoch; written atomically. Pairing codes and login attempts live in memory only.
 */
class ExtState private constructor(private val file: File, private var data: Persisted) {
    private class LivePairing(val code: String, val expiresUnix: Long)

    private val pairings = mutableListOf<LivePairing>()
    private val attempts = HashMap<String, Pair<Int, Long>>()
    private val seenPersisted = HashMap<String, Long>()

    private fun persist() = writePrivate(file, JSON.encodeToString(Persisted.serializer(), data).toByteArray())

    private inline fun <R> mutate(f: (Persisted) -> Pair<Persisted, R>): R = synchronized(this) {
        val (next, r) = f(data)
        data = next
        persist()
        r
    }

    @Synchronized fun serverId(): String = data.id.orEmpty()

    @Synchronized fun deviceCount(): Int = data.devices.count { it.role == "viewer" }

    @Synchronized fun devices(): List<DeviceRecord> = data.devices.toList()

    @Synchronized fun device(id: String): DeviceRecord? = data.devices.firstOrNull { it.id == id }

    @Synchronized fun viewerPasswordSet(): Boolean = data.viewerHash != null

    /** Session of anonymous viewers (no pairing required, no viewer password): `public:<epoch>`. */
    @Synchronized fun publicSessionId(): String = "public:${data.publicEpoch ?: data.id.orEmpty()}"

    @Synchronized
    fun livePairings(): Int {
        val now = ExtAuth.nowUnix()
        pairings.removeAll { it.expiresUnix <= now }
        return pairings.size
    }

    /** A new single-use code; at the limit the oldest live code is dropped (the HTTP route uses [tryCreatePairing]). */
    @Synchronized
    fun createPairing(origin: String): Pairing {
        val code = ExtAuth.newCode()
        val now = ExtAuth.nowUnix()
        pairings.removeAll { it.expiresUnix <= now || it.code == code }
        while (pairings.size >= ExtAuth.PAIRING_LIMIT) pairings.removeAt(0)
        pairings += LivePairing(code, now + ExtAuth.PAIRING_TTL_SECS)
        val link = ExtAuth.deepLink(origin, code)
        return Pairing(code, ExtAuth.PAIRING_TTL_SECS, link, link)
    }

    /** Drops [code] (the owner closed its pairing window): it no longer pairs. Unknown codes are ignored. */
    @Synchronized
    fun cancelPairing(code: String) {
        pairings.removeAll { ExtAuth.secretsEqual(it.code, code) }
    }

    /** Like [createPairing] but refuses with `pairing_limit` when 50 codes are alive. */
    fun tryCreatePairing(origin: String): Pairing {
        if (livePairings() >= ExtAuth.PAIRING_LIMIT) throw StateException(StateException.Kind.PAIRING_LIMIT)
        return createPairing(origin)
    }

    /** Consumes a code: the device record + raw token when valid (unknown, used or expired -> null). */
    fun pair(code: String, label: String): Pair<DeviceRecord, String>? {
        val c = code.trim()
        if (c.isEmpty() || c.length > 64) return null
        val found = synchronized(this) {
            val now = ExtAuth.nowUnix()
            pairings.removeAll { it.expiresUnix <= now }
            val i = pairings.indexOfFirst { ExtAuth.secretsEqual(it.code, c) }
            if (i >= 0) pairings.removeAt(i)
            i >= 0
        }
        if (!found) return null
        return issue("viewer", label)
    }

    /** BUG-057: a phone that re-links presents its old token; once the code is accepted, that viewer's entry is replaced. */
    fun pairReplacing(code: String, label: String, previous: String?): Pair<DeviceRecord, String>? {
        val old = previous?.let(::deviceOfToken)?.takeIf { it.role == "viewer" }
        val paired = pair(code, label)
        if (paired != null && old != null) runCatching { revoke(old.id) }
        return paired
    }

    @Synchronized
    private fun deviceOfToken(token: String): DeviceRecord? {
        if (token.isEmpty() || token.length > 256) return null
        val hash = ExtAuth.tokenHash(token)
        val id = data.tokenHashes.entries.firstOrNull { ExtAuth.secretsEqual(it.value, hash) }?.key ?: return null
        return data.devices.firstOrNull { it.id == id }
    }

    /** A device token for [role] (`viewer` | `admin`); the raw token is returned once, only its hash is kept. */
    fun issue(role: String, label: String): Pair<DeviceRecord, String> {
        val token = ExtAuth.newToken()
        val now = ExtAuth.nowUnix()
        val record = DeviceRecord(UUID.randomUUID().toString().replace("-", ""), sanitizeLabel(label), if (role == "admin") "admin" else "viewer", now, now)
        mutate { d ->
            if (d.devices.size >= DEVICE_LIMIT) throw StateException(StateException.Kind.DEVICE_LIMIT)
            d.copy(devices = d.devices + record, tokenHashes = d.tokenHashes + (record.id to ExtAuth.tokenHash(token))) to Unit
        }
        return record to token
    }

    /** The device of a bearer token (null = unknown or revoked); refreshes its `lastSeenAt` (on disk at most once a minute). */
    fun authenticate(token: String): DeviceRecord? {
        if (token.isEmpty() || token.length > 256) return null
        val hash = ExtAuth.tokenHash(token)
        val now = ExtAuth.nowUnix()
        return synchronized(this) {
            val id = data.tokenHashes.entries.firstOrNull { ExtAuth.secretsEqual(it.value, hash) }?.key ?: return null
            val dev = data.devices.firstOrNull { it.id == id } ?: return null
            val updated = dev.copy(lastSeenAt = now)
            data = data.copy(devices = data.devices.map { if (it.id == id) updated else it })
            if (now - (seenPersisted[id] ?: 0) >= LAST_SEEN_PERSIST_SECS) {
                seenPersisted[id] = now
                runCatching { persist() }
            }
            updated
        }
    }

    fun revoke(deviceId: String) {
        mutate { d ->
            if (d.devices.none { it.id == deviceId }) throw StateException(StateException.Kind.NO_DEVICE)
            d.copy(devices = d.devices.filter { it.id != deviceId }, tokenHashes = d.tokenHashes - deviceId) to Unit
        }
    }

    /** Stores the argon2id hash of [password] (null clears); live codes are dropped and the public epoch rotates. */
    fun setViewerPassword(password: String?) {
        val hash = when {
            password == null -> null
            !ExtAuth.passwordLenOk(password) ->
                throw StateException(StateException.Kind.PASSWORD, "use between ${ExtAuth.PASSWORD_MIN_CHARS} and ${ExtAuth.PASSWORD_MAX_CHARS} characters")
            else -> ExtAuth.hashPassword(password)
        }
        synchronized(this) { pairings.clear() }
        mutate { d -> d.copy(viewerHash = hash, publicEpoch = UUID.randomUUID().toString().replace("-", "")) to Unit }
    }

    fun verifyViewerPassword(password: String): Boolean {
        val hash = synchronized(this) { data.viewerHash } ?: return false
        return ExtAuth.verifyPassword(password, hash)
    }

    /** `X-Nijiyome-Bootstrap` check (owner access for local tools). */
    @Synchronized
    fun bootstrapOk(secret: String): Boolean {
        val stored = data.bootstrap ?: return false
        return secret.isNotEmpty() && ExtAuth.secretsEqual(stored, secret)
    }

    /** Counts one failed attempt for [role]; false once the budget of the window is spent. */
    @Synchronized
    fun noteFailure(role: String): Boolean {
        val now = ExtAuth.nowUnix()
        var (n, until) = attempts[role] ?: (0 to now + ATTEMPT_WINDOW_SECS)
        if (until <= now) {
            n = 0
            until = now + ATTEMPT_WINDOW_SECS
        }
        n++
        attempts[role] = n to until
        return n <= MAX_ATTEMPTS
    }

    @Synchronized
    fun throttled(role: String): Boolean {
        val (n, until) = attempts[role] ?: return false
        return until > ExtAuth.nowUnix() && n >= MAX_ATTEMPTS
    }

    @Synchronized fun repositories(): List<Repository> = data.repositories.toList()

    fun addRepository(name: String, url: String): Repository {
        val row = Repository(UUID.randomUUID().toString().replace("-", ""), name.trim().take(100), url)
        mutate { d ->
            when {
                d.repositories.any { it.url == url } -> d to Unit
                d.repositories.size >= REPOSITORY_LIMIT -> throw StateException(StateException.Kind.REPOSITORY_LIMIT)
                else -> d.copy(repositories = d.repositories + row) to Unit
            }
        }
        return repositories().firstOrNull { it.url == url } ?: row
    }

    fun removeRepository(id: String): Boolean = mutate { d ->
        val next = d.repositories.filter { it.id != id }
        d.copy(repositories = next) to (next.size != d.repositories.size)
    }

    companion object {
        const val DEVICE_LIMIT = 200
        const val REPOSITORY_LIMIT = 50
        private const val LAST_SEEN_PERSIST_SECS = 60L
        const val MAX_ATTEMPTS = 10
        const val ATTEMPT_WINDOW_SECS = 60L
        private val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Loads `<dir>/state.json` (missing = a fresh state with a new server id and bootstrap secret). A corrupt file is an error. */
        /**
         * [bytes] into [file], readable by this user only (`0600`, set when the temporary file is created, before anything is
         * written) and atomically (a reader sees the old or the new file, never half of one). state.json holds the bootstrap
         * secret and the password / token hashes.
         */
        internal fun writePrivate(file: File, bytes: ByteArray) {
            val tmp = File(file.path + ".tmp").toPath()
            java.nio.file.Files.deleteIfExists(tmp)
            val posix = runCatching {
                java.nio.file.Files.createFile(tmp, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")))
            }.isSuccess
            if (!posix) {
                // not POSIX (Windows): the data folder is under the user's profile; keep the file to its owner as far as Java can
                java.nio.file.Files.createFile(tmp)
                tmp.toFile().run {
                    setReadable(false, false)
                    setWritable(false, false)
                    setReadable(true, true)
                    setWritable(true, true)
                }
            }
            java.nio.file.Files.write(tmp, bytes)
            try {
                java.nio.file.Files.move(tmp, file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(tmp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (e: java.nio.file.FileSystemException) {
                throw IOException("could not write ${file.name}", e)
            }
        }

        fun open(dir: File): ExtState {
            dir.mkdirs()
            val file = File(dir, "state.json")
            var data = if (file.isFile) JSON.decodeFromString(Persisted.serializer(), file.readText()) else Persisted()
            if (data.id == null) data = data.copy(id = UUID.randomUUID().toString().replace("-", ""))
            if (data.bootstrap == null) data = data.copy(bootstrap = ExtAuth.newToken())
            // a hash that is not argon2 (a contract stub, never a real password) is dropped instead of locking everyone out
            if (data.viewerHash?.startsWith("\$argon2") == false) data = data.copy(viewerHash = null)
            return ExtState(file, data).also { it.persist() }
        }
    }
}

/** `POST /auth/pairing` answer (`url == deepLink`). */
data class Pairing(val code: String, val expiresIn: Int, val url: String, val deepLink: String)

/** Labels as the classic server stores them: trimmed, control characters removed, at most 100 characters, `Device` when empty. */
fun sanitizeLabel(label: String): String = label.trim().filterNot { it.isISOControl() }.take(100).ifEmpty { "Device" }
