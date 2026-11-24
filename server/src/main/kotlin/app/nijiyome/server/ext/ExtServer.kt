// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-server/src/lib.rs and routes.rs (AGPL-3.0, same author).
package app.nijiyome.server.ext

import app.nijiyome.server.http.BodyTooLarge
import app.nijiyome.server.http.KtorServer
import app.nijiyome.server.http.nettyChannel
import app.nijiyome.server.http.readBody
import app.nijiyome.server.net.NetError
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.SafeHttp
import app.nijiyome.server.net.SafeRequest
import app.nijiyome.server.net.originOf
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
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.BindException
import java.net.InetSocketAddress
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtServerConfig(
    /** `<data>/server` (state.json, media cache). */
    val dir: File,
    val port: Int = DEFAULT_EXT_PORT,
    /** Shown in `/health` and to phones; empty = host name. */
    val name: String = "",
    /** Phones must pair (one-time code) unless a viewer password is set. */
    val requirePairing: Boolean = true,
    val proxyEnabled: Boolean = true,
    /** Extra CORS origins (phones send none). */
    val allowedOrigins: List<String> = emptyList(),
    /** Per-source time limit of `/resolve` and `/invoke`. */
    val timeoutMs: Long = 45_000,
    val concurrency: Int = 4,
    /** Addresses to listen on. Empty = the private LAN IPv4 + 127.0.0.1 (never a wildcard); [loopbackOnly] = 127.0.0.1 only. */
    val bindIps: List<String> = emptyList(),
    val loopbackOnly: Boolean = false,
    /** Whether a connection from [java.net.InetAddress] is this PC (bootstrap secret accepted); loopback only. Tests replace it. */
    val localCaller: (java.net.InetAddress?) -> Boolean = { it?.isLoopbackAddress == true },
    /** Test hook: the upstream client of the media proxy and `.kai` downloads. */
    val upstream: SafeHttp? = null,
)

class ExtStartException(message: String, cause: Throwable? = null, val publicBind: Boolean = false) : Exception(message, cause)

/**
 * The integrated extension server (`/api/server/v1`): a phone pairs with a one-time code (or a viewer password) and then calls
 * `/sources`, `/invoke`, `/resolve` and the `/media/…` proxy over the `.kai` extensions installed on this PC, with the classic
 * server's JSON and error catalogue (what :core:extension-server speaks). Plain HTTP on the private LAN (bearer tokens gate it).
 */
class ExtServer private constructor(
    val cfg: ExtServerConfig,
    private val catalog: ExtensionCatalog,
    val state: ExtState,
    val logs: LogBuffer,
    private val proxy: MediaProxy,
    private val upstream: SafeHttp,
    val startedUnix: Long,
) {
    private lateinit var http: KtorServer
    private val lookups = LookupCache()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val addresses: List<InetSocketAddress> get() = http.addresses
    val port: Int get() = http.port

    fun stop() {
        runCatching { http.stop() }
        scope.cancel()
    }

    fun createPairing(origin: String): Pairing = state.tryCreatePairing(origin)
    fun cancelPairing(code: String) = state.cancelPairing(code)
    fun devices(): List<DeviceRecord> = state.devices()

    fun revoke(deviceId: String) {
        state.revoke(deviceId)
        log("revoked", "/devices")
    }

    fun setViewerPassword(password: String?) = state.setViewerPassword(password)
    fun viewerPasswordSet(): Boolean = state.viewerPasswordSet()
    fun pairingRequired(): Boolean = cfg.requirePairing && !state.viewerPasswordSet()
    fun logs(after: Long) = logs.since(after)

    fun clearCache() {
        lookups.clear()
        proxy.cache.clear()
    }

    /** `http://127.0.0.1:<port>/api/server/v1/media/`. */
    fun localMediaBase(): String = "http://127.0.0.1:$port$API_PREFIX/media/"

    /** A loopback proxy URL of [url] + [headers] for this PC's own player (only a loopback peer may use the ticket). */
    fun localMediaUrl(url: String, headers: List<Pair<String, String>>): String = proxy.mint("http://127.0.0.1:$port", url, headers, MediaProxy.LOCAL_SESSION)

    /** `http://<lan ip>:<port>` URLs phones can use (empty while listening on loopback only). */
    fun lanUrls(): List<String> = urlsFor(addresses.map { it.address.hostAddress }, port).takeUnless { cfg.loopbackOnly } ?: emptyList()

    fun displayName(): String = cfg.name.trim().ifEmpty { hostName() }

    private fun anonymousAllowed() = !cfg.requirePairing && !state.viewerPasswordSet()

    /** Whether a ticket minted for [session] may still be served to a caller at [peerLoopback]. */
    fun sessionOk(session: String, peerLoopback: Boolean): Boolean = when {
        session == MediaProxy.LOCAL_SESSION -> peerLoopback
        session.startsWith("public:") -> anonymousAllowed() && session == state.publicSessionId()
        else -> state.device(session) != null
    }

    private fun log(event: String, route: String? = null, name: String? = null, count: Int? = null, durationMs: Long? = null, error: String? = null) {
        logs.push(LogEntry(0, ExtAuth.nowUnix(), event, route, name, count, durationMs, error))
    }

    // ---- edge (CORS, OPTIONS, security headers, request log) -----------------------------------------------------------

    private suspend fun handle(call: ApplicationCall) {
        val started = System.nanoTime()
        val path = call.request.path()
        val method = call.request.httpMethod
        val origin = call.request.header("Origin")
        val host = call.request.header("Host")
        val route = logRoute(path)
        val allowed = origin == null || originAllowed(origin, host, cfg.allowedOrigins)
        val r = call.response
        if (origin != null && allowed) {
            r.header("Access-Control-Allow-Origin", origin)
            r.header("Vary", "Origin")
        }
        r.header("Access-Control-Allow-Headers", "Authorization, Content-Type, Range, X-Nijiyome-Bootstrap")
        r.header("Access-Control-Allow-Methods", "GET, HEAD, POST, PATCH, DELETE, OPTIONS")
        r.header("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, Content-Length")
        r.header("X-Content-Type-Options", "nosniff")
        r.header("Referrer-Policy", "no-referrer")
        if (route != "/media") r.header("Cache-Control", "private, no-store")
        var status = 200
        try {
            status = when {
                // DNS rebinding: a web page that points its own name at this PC sends that name as Host
                !hostAllowed(host) -> fail(call, Failure(403, "host_denied"))
                !allowed -> fail(call, Failure(403, "origin_denied"))
                method == HttpMethod.Options -> empty(call, 204)
                else -> dispatch(call, path.removePrefix(API_PREFIX).takeIf { path.startsWith("$API_PREFIX/") } ?: "", method)
            }
        } catch (f: Failure) {
            status = fail(call, f)
        }
        if (route !in setOf("/health", "/media", "/logs") && method != HttpMethod.Options) {
            log("request", route, method.value, null, (System.nanoTime() - started) / 1_000_000, status.takeIf { it >= 400 }?.toString())
        }
    }

    private suspend fun dispatch(call: ApplicationCall, p: String, m: HttpMethod): Int {
        val seg = p.removePrefix("/").split('/')
        fun only(vararg allowed: HttpMethod, block: suspend () -> Int): suspend () -> Int = {
            if (m in allowed || (HttpMethod.Get in allowed && m == HttpMethod.Head)) block() else throw Failure(405, "method_not_allowed")
        }
        val handler: suspend () -> Int = when {
            p == "/health" -> only(HttpMethod.Get) { json(call, health()) }
            p == "/setup" -> only(HttpMethod.Post) { setup(call) }
            p == "/auth/login" -> only(HttpMethod.Post) { login(call) }
            p == "/auth/pair" -> only(HttpMethod.Post) { pair(call) }
            p == "/auth/logout" -> only(HttpMethod.Post) { logout(call) }
            p == "/auth/pairing" -> only(HttpMethod.Post) { createPairingRoute(call) }
            p == "/auth/password" -> only(HttpMethod.Post) { setPassword(call) }
            p == "/devices" -> only(HttpMethod.Get) { listDevices(call) }
            seg.size == 2 && seg[0] == "devices" -> only(HttpMethod.Delete) { revokeDevice(call, seg[1]) }
            p == "/settings" -> only(HttpMethod.Get, HttpMethod.Post) {
                authorize(call, owner = true)
                if (m == HttpMethod.Post) throw Failure(409, "managed_by_app", "Change these settings in Nijiyome: Settings > Nijiyome Link")
                json(call, ok(settingsJson()))
            }
            p == "/extensions" -> only(HttpMethod.Get, HttpMethod.Post) { if (m == HttpMethod.Post) installExtension(call) else listExtensions(call) }
            seg.size == 2 && seg[0] == "extensions" -> only(HttpMethod.Delete) { removeExtension(call, seg[1]) }
            seg.size == 3 && seg[0] == "extensions" && seg[2] == "package" -> only(HttpMethod.Get) { extensionPackage(call, seg[1]) }
            p == "/repositories" -> only(HttpMethod.Get, HttpMethod.Post) {
                if (m == HttpMethod.Post) addRepository(call) else {
                    authorize(call, owner = false)
                    json(call, ok(reposJson()))
                }
            }
            p == "/sources" -> only(HttpMethod.Get) { sources(call) }
            p == "/invoke" -> only(HttpMethod.Post) { invoke(call) }
            p == "/resolve" -> only(HttpMethod.Post) { resolve(call) }
            p == "/media/refresh" -> only(HttpMethod.Post) { mediaRefresh(call) }
            (seg.size == 2 || seg.size >= 3) && seg[0] == "media" && seg[1].isNotEmpty() -> only(HttpMethod.Get) { media(call, seg[1]) }
            p == "/logs" -> only(HttpMethod.Get) {
                authorize(call, owner = true)
                val after = query(call)["after"]?.toLongOrNull() ?: 0
                val (entries, cursor) = logs.since(after)
                json(call, ok(buildJsonObject {
                    put("entries", buildJsonArray { entries.forEach { add(it.toJson()) } })
                    put("cursor", cursor)
                }))
            }
            p == "/cache" -> only(HttpMethod.Get, HttpMethod.Delete) {
                authorize(call, owner = true)
                if (m == HttpMethod.Delete) {
                    clearCache()
                    json(call, ok(buildJsonObject { put("success", true) }))
                } else {
                    json(call, ok(cacheJson()))
                }
            }
            p == "/export" -> only(HttpMethod.Get) { export(call) }
            p == "/import" -> only(HttpMethod.Post) { import(call) }
            p == "/diagnostics" -> only(HttpMethod.Get) { diagnostics(call) }
            // Node-only feature of the classic server
            p == "/native" -> only(HttpMethod.Post) { throw Failure.NOT_FOUND.withMessage("Native runtimes are a feature of the standalone Node server") }
            else -> { -> throw Failure(404, "route_not_found") }
        }
        return handler()
    }

    // ---- auth ----------------------------------------------------------------------------------------------------------

    private sealed interface Caller {
        val session: String

        class Device(val record: DeviceRecord) : Caller { override val session get() = record.id }
        class Public(override val session: String) : Caller
    }

    private fun bearer(call: ApplicationCall): String? =
        call.request.header("Authorization")?.removePrefix("Bearer ")?.takeIf { call.request.header("Authorization")!!.startsWith("Bearer ") }?.trim()?.takeIf { it.isNotEmpty() }

    private fun authorize(call: ApplicationCall, owner: Boolean): Caller {
        val token = bearer(call)
        if (token != null) {
            val dev = state.authenticate(token) ?: throw Failure.DEVICE_NOT_AUTHORIZED
            if (owner && dev.role != "admin") throw Failure.FORBIDDEN
            return Caller.Device(dev)
        }
        if (!owner && anonymousAllowed()) return Caller.Public(state.publicSessionId())
        throw Failure.AUTHENTICATION_REQUIRED
    }

    private fun health(): JsonObject = ok(buildJsonObject {
        put("protocolVersion", PROTOCOL_VERSION)
        put("serverId", state.serverId())
        put("name", displayName())
        put("platform", "desktop")
        put("desktop", true)
        put("logoUrl", JsonNull)
        put("startedAt", Instant.ofEpochSecond(startedUnix).toString())
        put("uptimeSeconds", (ExtAuth.nowUnix() - startedUnix).coerceAtLeast(0))
        put("setupRequired", false)
        put("accessPasswordRequired", state.viewerPasswordSet())
        put("adminPasswordRequired", false)
        put("pairingRequired", pairingRequired())
        put("dashboardPath", JsonNull)
        put("capabilities", buildJsonArray {
            add(buildJsonObject {
                put("ecosystem", "nijiyome")
                put("available", true)
                put("reason", "Nijiyome .kai extensions installed on this PC")
            })
        })
        putJsonObject("proxy") {
            put("hls", true)
            put("range", true)
            put("enabled", cfg.proxyEnabled)
            put("maxResponseBytes", JsonNull)
        }
    })

    /**
     * The bootstrap secret (owner access for local tools) is taken only from this PC itself: never over the LAN, where it would
     * travel in plain HTTP. The app does not need it (it drives this server in process).
     */
    private fun bootstrapFrom(call: ApplicationCall): Boolean {
        val remote = (call.nettyChannel()?.remoteAddress() as? java.net.InetSocketAddress)?.address
        return cfg.localCaller(remote) && state.bootstrapOk(call.request.header(BOOTSTRAP_HEADER).orEmpty())
    }

    private suspend fun setup(call: ApplicationCall): Int {
        if (state.throttled("admin")) throw Failure(429, "login_throttled")
        if (!bootstrapFrom(call)) {
            state.noteFailure("admin")
            throw Failure(403, "bootstrap_required")
        }
        val (dev, token) = issue("admin", "Owner")
        log("setup", "/setup", dev.label)
        return json(call, tokenAnswer(dev, token))
    }

    private fun issue(role: String, label: String): Pair<DeviceRecord, String> = try {
        state.issue(role, label)
    } catch (e: StateException) {
        throw stateFailure(e)
    }

    private suspend fun login(call: ApplicationCall): Int {
        val input = body(call)
        val role = if (input.str("role") == "admin") "admin" else "viewer"
        val label = input.str("label") ?: "Device"
        if (state.throttled(role)) throw Failure(429, "login_throttled")
        when {
            role == "admin" -> if (!bootstrapFrom(call)) {
                state.noteFailure(role)
                throw Failure(403, "owner_key_required", "Use the setup key of state.json for owner access")
            }
            state.viewerPasswordSet() -> if (!state.verifyViewerPassword(input.str("password").orEmpty())) {
                state.noteFailure(role)
                log("login_failed", "/auth/login", error = "invalid_password")
                throw Failure(401, "invalid_password")
            }
            cfg.requirePairing -> throw Failure.PAIRING_REQUIRED.withMessage("Pair this device from the server owner (QR code or link)")
        }
        val (dev, token) = issue(role, label)
        log("login", "/auth/login", dev.label)
        return json(call, tokenAnswer(dev, token))
    }

    private suspend fun pair(call: ApplicationCall): Int {
        val input = body(call)
        if (state.throttled("pair")) throw Failure(429, "login_throttled")
        val code = when (val c = input["code"]) {
            is JsonPrimitive -> if (c.isString) c.content else c.longOrNull?.toString().orEmpty()
            else -> ""
        }
        val label = input.str("label") ?: "Device"
        // BUG-057: a phone that re-links sends its old token; its old entry is replaced
        val paired = try {
            state.pairReplacing(code, label, bearer(call))
        } catch (e: StateException) {
            throw stateFailure(e)
        }
        if (paired == null) {
            state.noteFailure("pair")
            log("pairing_failed", "/auth/pair", error = "pairing_expired")
            throw Failure.PAIRING_EXPIRED
        }
        log("pairing", "/auth/pair", paired.first.label)
        return json(call, tokenAnswer(paired.first, paired.second))
    }

    private suspend fun logout(call: ApplicationCall): Int {
        val c = authorize(call, owner = false)
        if (c is Caller.Device) {
            runCatching { state.revoke(c.record.id) }
            log("logout", "/auth/logout", c.record.label)
        }
        return json(call, ok(buildJsonObject { put("success", true) }))
    }

    private suspend fun createPairingRoute(call: ApplicationCall): Int {
        authorize(call, owner = true)
        val p = try {
            state.tryCreatePairing(baseUrl(call))
        } catch (e: StateException) {
            throw stateFailure(e)
        }
        log("pairing_created", "/auth/pairing")
        return json(call, ok(pairingJson(p)))
    }

    private suspend fun setPassword(call: ApplicationCall): Int {
        authorize(call, owner = true)
        val input = body(call)
        if (input.str("role")?.let { it != "viewer" } == true) throw Failure(400, "unsupported_role", "Only the viewer password exists on desktop")
        try {
            state.setViewerPassword(input.str("password")?.takeIf { it.isNotEmpty() })
        } catch (e: StateException) {
            throw stateFailure(e)
        }
        log("viewer_password", "/auth/password")
        return json(call, ok(buildJsonObject { put("success", true) }))
    }

    private suspend fun listDevices(call: ApplicationCall): Int {
        val me = authorize(call, owner = true).session
        return json(call, ok(buildJsonArray {
            state.devices().forEach { d ->
                add(buildJsonObject {
                    put("id", d.id)
                    put("label", d.label)
                    put("role", d.role)
                    put("createdAt", d.createdAt)
                    put("lastSeenAt", d.lastSeenAt?.let(::JsonPrimitive) ?: JsonNull)
                    put("current", d.id == me)
                })
            }
        }))
    }

    private suspend fun revokeDevice(call: ApplicationCall, id: String): Int {
        authorize(call, owner = true)
        try {
            state.revoke(id)
        } catch (e: StateException) {
            throw stateFailure(e)
        }
        log("revoked", "/devices")
        return json(call, ok(buildJsonObject { put("success", true) }))
    }

    // ---- extensions / sources ------------------------------------------------------------------------------------------

    private fun sortedExtensions(): List<CatalogEntry> = catalog.list().sortedWith(
        compareByDescending<CatalogEntry> { catalog.manifest(it.id)?.priority ?: 0 }.thenBy { it.name.lowercase() },
    )

    /** One `SourceDescriptor` row of [e] plus its `~read` / `~novel` variants. */
    fun descriptors(e: CatalogEntry): List<JsonObject> {
        val sid = state.serverId()
        val m = catalog.manifest(e.id)
        val available = e.compat != "unsupported"
        val base = buildJsonObject {
            put("id", "$sid/${e.id}")
            put("extensionId", e.id)
            put("name", e.name)
            put("ecosystem", "nijiyome")
            put("enabled", e.enabled)
            put("priority", m?.priority ?: 0)
            put("languages", JsonArray(emptyList()))
            put("kind", "watch")
            put("sourceType", e.kind)
            put("version", e.version)
            put("available", available)
            put("reason", e.compatReason ?: if (available) "Nijiyome .kai extension" else "Not supported by this PC's extension host")
        }
        val out = mutableListOf(base)
        val caps = m?.capabilities.orEmpty()
        fun variant(suffix: String, label: String, kind: String) = JsonObject(
            base + mapOf("id" to JsonPrimitive("$sid/${e.id}~$suffix"), "name" to JsonPrimitive("${e.name} · $label"), "kind" to JsonPrimitive(kind)),
        )
        if ("manga" in caps) out += variant("read", "Manga", "read")
        if ("novel" in caps) out += variant("novel", "Novels", "novel")
        return out
    }

    private suspend fun listExtensions(call: ApplicationCall): Int {
        authorize(call, owner = false)
        return json(call, ok(buildJsonArray {
            sortedExtensions().forEach { e ->
                val m = catalog.manifest(e.id)
                add(buildJsonObject {
                    put("id", e.id)
                    put("manifestId", e.id)
                    put("ecosystem", "nijiyome")
                    put("name", e.name)
                    put("version", e.version)
                    put("type", e.kind)
                    put("kind", e.kind)
                    put("enabled", e.enabled)
                    put("compat", e.compat)
                    put("compatReason", e.compatReason?.let(::JsonPrimitive) ?: JsonNull)
                    put("priority", m?.priority ?: 0)
                    put("capabilities", JsonArray(m?.capabilities.orEmpty().map(::JsonPrimitive)))
                    put("languages", JsonArray(emptyList()))
                    put("hasPreferences", false)
                    put("runtime", "interpreter")
                    put("sources", JsonArray(descriptors(e)))
                    // BUG-055: lets the phone tell whether its copy is the PC's
                    put("bundleSha256", catalog.files(e.id)?.second?.let { JsonPrimitive(sha256Hex(it)) } ?: JsonNull)
                })
            }
        }))
    }

    /** `GET /extensions/{id}/package` (BUG-055): the installed extension as a `.kai` with the SHA-256 of its `bundle.js`. */
    private suspend fun extensionPackage(call: ApplicationCall, id: String): Int {
        authorize(call, owner = false)
        val (manifest, bundle) = withContext(Dispatchers.IO) { catalog.files(id) } ?: throw Failure(404, "extension_not_found")
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                for ((name, data) in listOf("manifest.json" to manifest, "bundle.js" to bundle)) {
                    z.putNextEntry(ZipEntry(name))
                    z.write(data)
                    z.closeEntry()
                }
            }
        }.toByteArray()
        log("extension_package", "/extensions", id)
        call.response.header("Content-Disposition", "attachment; filename=\"${id.replace(Regex("[\"\\\\\r\n]"), "_")}.kai\"")
        call.response.header(BUNDLE_SHA_HEADER, sha256Hex(bundle))
        call.respondBytes(zip, ContentType.Application.Zip, HttpStatusCode.OK)
        return 200
    }

    private suspend fun installExtension(call: ApplicationCall): Int {
        authorize(call, owner = true)
        val url = body(call).str("url")?.trim().orEmpty().toHttpUrlOrNull() ?: throw Failure(400, "invalid_url")
        if (url.scheme != "https") throw Failure(400, "invalid_url", "Use an https:// link to a .kai package")
        val data = try {
            val r = upstream.send(SafeRequest.get(url, emptyList())).response
            if (!r.isSuccessful) {
                r.close()
                throw Failure(502, "bundle_download_failed")
            }
            withContext(Dispatchers.IO) { app.nijiyome.server.net.readCapped(r, MAX_KAI) }
        } catch (e: NetError.TooLarge) {
            throw Failure(413, "response_too_large")
        } catch (e: NetError) {
            throw Failure(502, "bundle_download_failed", e.message ?: "download failed")
        }
        val e = try {
            catalog.install(data)
        } catch (x: CatalogException) {
            throw Failure(422, "invalid_extension", x.message ?: "invalid extension")
        }
        lookups.clear()
        log("extension_installed", "/extensions", e.name)
        return json(call, ok(buildJsonObject {
            put("id", e.id)
            put("name", e.name)
            put("version", e.version)
            put("type", e.kind)
            put("enabled", e.enabled)
        }))
    }

    private suspend fun removeExtension(call: ApplicationCall, id: String): Int {
        authorize(call, owner = true)
        try {
            catalog.remove(id)
        } catch (_: CatalogException) {
            throw Failure(404, "extension_not_found")
        }
        lookups.clear()
        log("extension_removed", "/extensions", id)
        return json(call, ok(buildJsonObject { put("success", true) }))
    }

    private suspend fun addRepository(call: ApplicationCall): Int {
        authorize(call, owner = true)
        val input = body(call)
        val url = input.str("url").orEmpty().toHttpUrlOrNull()?.takeIf { it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty() }
            ?: throw Failure(400, "invalid_url")
        val name = input.str("name") ?: url.host
        val repo = try {
            state.addRepository(name, url.toString())
        } catch (e: StateException) {
            throw stateFailure(e)
        }
        return json(call, ok(repoJson(repo)))
    }

    private suspend fun sources(call: ApplicationCall): Int {
        authorize(call, owner = false)
        return json(call, ok(JsonArray(sortedExtensions().filter { it.enabled }.flatMap(::descriptors))))
    }

    // ---- invoke --------------------------------------------------------------------------------------------------------

    private suspend fun invoke(call: ApplicationCall): Int {
        val caller = authorize(call, owner = false)
        val input = body(call)
        val (extId, novel) = parseSourceId(input.str("sourceId").orEmpty(), state.serverId())
        val route = invokeRoute(input.str("op").orEmpty(), novel) ?: throw Failure(403, "data_operation_required")
        val info = catalog.list().firstOrNull { it.id == extId && it.enabled } ?: throw Failure(404, "source_not_found")
        val args = input["args"] as? JsonObject ?: JsonObject(emptyMap())
        val cacheable = route != "custom/watch"
        val key = ExtAuth.tokenHash(buildJsonArray { add(JsonPrimitive(extId)); add(JsonPrimitive(info.version)); add(JsonPrimitive(route)); add(args) }.toString())
        val started = System.nanoTime()
        val raw: JsonElement = (if (cacheable) lookups.get(key) else null) ?: run {
            val v = try {
                withTimeoutOrNull(cfg.timeoutMs) { catalog.call(extId, route, args) } ?: throw Failure(504, "upstream_timeout")
            } catch (e: CatalogException) {
                log("source_failed", "/invoke", info.name, durationMs = (System.nanoTime() - started) / 1_000_000, error = e.message)
                throw when (e.kind) {
                    CatalogException.Kind.NOT_INSTALLED -> Failure(404, "source_not_found")
                    CatalogException.Kind.UNSUPPORTED_ROUTE -> Failure(422, "unsupported_operation", e.message ?: route)
                    CatalogException.Kind.FAILED -> Failure.upstream(e.message ?: "extension failed")
                }
            }
            if (cacheable) lookups.put(key, v)
            v
        }
        val shaped = if (route == "manga/pages") pagesAsObjects(raw) else raw
        val base = baseUrl(call)
        val result = if (cfg.proxyEnabled) proxify(shaped, null) { u, h -> proxy.mint(base, u, h, caller.session) } else shaped
        val progressive = (input["progressive"] as? JsonPrimitive)?.contentOrNull == "true" && (route == "manga/chapters" || route == "novel/chapters")
        return json(call, ok(if (progressive) buildJsonObject { put("chapters", result); put("partial", false) } else result))
    }

    // ---- resolve -------------------------------------------------------------------------------------------------------

    private class Snapshot(val sources: MutableList<JsonElement>, val statuses: MutableList<JsonObject>) {
        @Synchronized
        fun json(done: Boolean): JsonObject = buildJsonObject {
            put("sources", JsonArray(sources.toList()))
            put("subtitles", JsonArray(emptyList()))
            put("statuses", JsonArray(statuses.toList()))
            if (done) put("done", true)
        }
    }

    private suspend fun runResolve(input: JsonObject, base: String, session: String, publish: (JsonObject) -> Unit): JsonObject {
        val sid = state.serverId()
        val serverName = displayName()
        val jobs = sortedExtensions().filter { it.enabled && it.compat != "unsupported" }.mapNotNull { e ->
            catalog.candidateRoutes(e.id).takeIf { it.isNotEmpty() }?.let { e to it }
        }
        val params = resolveParams(input, cfg.timeoutMs)
        val snap = Snapshot(
            mutableListOf(),
            jobs.map { (e, _) ->
                buildJsonObject {
                    put("provider", "$sid/${e.id}")
                    put("name", e.name)
                    put("ecosystem", "nijiyome")
                    put("kind", "anime")
                    put("status", "loading")
                    put("count", 0)
                    put("durationMs", 0)
                }
            }.toMutableList(),
        )
        publish(snap.json(false))
        val gate = Semaphore(cfg.concurrency.coerceIn(1, 8))
        jobs.mapIndexed { i, (e, routes) ->
            scope.async {
                gate.withPermit {
                    val started = System.nanoTime()
                    val provider = "$sid/${e.id}"
                    val found = mutableListOf<JsonElement>()
                    var error: String? = null
                    for (route in routes) {
                        try {
                            val body = withTimeoutOrNull(cfg.timeoutMs) { catalog.call(e.id, route, params) }
                            if (body == null) error = "timeout" else (body["sources"] as? JsonArray)?.let(found::addAll)
                        } catch (x: CatalogException) {
                            error = x.message
                        }
                    }
                    val seen = HashSet<String>()
                    val fresh = found.filter { s ->
                        val o = s as? JsonObject
                        seen.add(buildJsonArray { listOf("url", "infoHash", "magnet", "quality", "server", "fileIndex").forEach { add(o?.get(it) ?: JsonNull) } }.toString())
                    }.take(500)
                    val count = fresh.size
                    val proxied = fresh.map { s ->
                        val p = if (cfg.proxyEnabled) proxify(s, null) { u, h -> proxy.mint(base, u, h, session) } else s
                        annotateSource(p, provider, e.name, serverName)
                    }
                    val duration = (System.nanoTime() - started) / 1_000_000
                    synchronized(snap) {
                        val room = (2000 - snap.sources.size).coerceAtLeast(0)
                        snap.sources += proxied.take(room)
                        snap.statuses[i] = JsonObject(snap.statuses[i] + buildMap {
                            put("count", JsonPrimitive(count))
                            put("durationMs", JsonPrimitive(duration))
                            put("status", JsonPrimitive(when {
                                count == 0 && error != null -> "error"
                                count == 0 -> "empty"
                                error != null -> "partial"
                                else -> "ok"
                            }))
                            if (error != null) put("error", JsonPrimitive("source_failed"))
                        })
                    }
                    log(if (error != null && count == 0) "source_failed" else "source_completed", "/resolve", e.name, count, duration, error)
                    publish(snap.json(false))
                }
            }
        }.awaitAll()
        return snap.json(true).also(publish)
    }

    private suspend fun resolve(call: ApplicationCall): Int {
        val caller = authorize(call, owner = false)
        val input = body(call)
        val base = baseUrl(call)
        val ndjson = call.request.header("Accept")?.contains("application/x-ndjson") == true
        if (!ndjson) return json(call, ok(runResolve(input, base, caller.session) {}))
        val lines = Channel<String>(Channel.UNLIMITED)
        scope.launch {
            try {
                runResolve(input, base, caller.session) { lines.trySend(Json.encodeToString(JsonObject.serializer(), it) + "\n") }
            } finally {
                lines.close()
            }
        }
        call.response.header("Cache-Control", "no-store")
        call.respondBytesWriter(ContentType.parse("application/x-ndjson"), HttpStatusCode.OK) {
            for (line in lines) {
                writeStringUtf8(line)
                flush()
            }
        }
        return 200
    }

    // ---- media ---------------------------------------------------------------------------------------------------------

    private suspend fun media(call: ApplicationCall, ticket: String): Int {
        val head = call.request.httpMethod == HttpMethod.Head
        val peer = (call.nettyChannel()?.remoteAddress() as? InetSocketAddress)?.address?.isLoopbackAddress == true
        val answer = proxy.serve(head, ticket, call.request.header("Range"), baseUrl(call)) { sessionOk(it, peer) }
        var contentType: ContentType? = null
        var length: Long? = null
        for ((k, v) in answer.headers) {
            when (k) {
                "content-type" -> contentType = runCatching { ContentType.parse(v) }.getOrNull()
                "content-length" -> length = v.toLongOrNull()
                else -> call.response.header(k, v)
            }
        }
        val status = HttpStatusCode.fromValue(answer.status)
        val stream = answer.stream
        if (stream == null) {
            call.respondBytes(answer.body ?: ByteArray(0), contentType, status)
        } else {
            stream.use { r ->
                call.respondBytesWriter(contentType, status, length) {
                    val source = r.body.source()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = withContext(Dispatchers.IO) { source.read(buf) }
                        if (n < 0) break
                        writeFully(buf, 0, n)
                    }
                }
            }
        }
        return answer.status
    }

    private suspend fun mediaRefresh(call: ApplicationCall): Int {
        val caller = authorize(call, owner = false)
        val id = ticketId(body(call).str("ticket").orEmpty())
        val t = proxy.ticket(id) ?: throw Failure(404, "media_not_found")
        if (t.session != caller.session) throw Failure(403, "media_owner_mismatch")
        return json(call, ok(buildJsonObject { put("url", proxy.mint(baseUrl(call), t.url, t.headers, t.session)) }))
    }

    // ---- export / import / diagnostics ---------------------------------------------------------------------------------

    private fun settingsJson(): JsonObject = buildJsonObject {
        put("name", displayName())
        put("requirePairing", cfg.requirePairing)
        put("proxyEnabled", cfg.proxyEnabled)
        put("allowedOrigins", JsonArray(cfg.allowedOrigins.map(::JsonPrimitive)))
        put("timeoutMs", cfg.timeoutMs)
        put("concurrency", cfg.concurrency)
        put("languages", JsonArray(emptyList()))
        put("cacheSeconds", 300)
        put("mediaCacheMb", MediaProxy.MAX_CACHE_BYTES / (1024 * 1024))
        put("flaresolverr", app.nijiyome.network.Outbound.flareSolverr != null)
    }

    private fun cacheJson(): JsonObject {
        val (entries, bytes) = proxy.cache.stats()
        return buildJsonObject {
            put("lookupEntries", lookups.size())
            put("mediaEntries", entries)
            put("mediaBytes", bytes)
            put("maxMediaBytes", MediaProxy.MAX_CACHE_BYTES)
        }
    }

    private fun repoJson(r: Repository) = buildJsonObject {
        put("id", r.id)
        put("name", r.name)
        put("url", r.url)
    }

    private fun reposJson() = JsonArray(state.repositories().map(::repoJson))

    private suspend fun export(call: ApplicationCall): Int {
        authorize(call, owner = true)
        return json(call, ok(buildJsonObject {
            put("schema", 1)
            put("settings", settingsJson())
            put("extensions", buildJsonArray {
                catalog.list().forEach { e ->
                    add(buildJsonObject {
                        put("id", e.id)
                        put("name", e.name)
                        put("version", e.version)
                        put("type", e.kind)
                        put("enabled", e.enabled)
                    })
                }
            })
            put("repositories", reposJson())
        }))
    }

    private suspend fun import(call: ApplicationCall): Int {
        authorize(call, owner = true)
        val input = body(call)
        if ((input["schema"] as? JsonPrimitive)?.longOrNull != 1L) throw Failure(400, "unsupported_schema")
        var added = 0
        (input["repositories"] as? JsonArray)?.forEach { r ->
            val o = r as? JsonObject ?: return@forEach
            val u = o.str("url")?.toHttpUrlOrNull()?.takeIf { it.scheme == "https" } ?: return@forEach
            if (runCatching { state.addRepository(o.str("name") ?: u.host, u.toString()) }.isSuccess) added++
        }
        return json(call, ok(buildJsonObject {
            put("repositories", added)
            put("settings", settingsJson())
        }))
    }

    private suspend fun diagnostics(call: ApplicationCall): Int {
        authorize(call, owner = true)
        val recent = logs.since(0).first.filter { it.event.startsWith("source_") }.reversed().take(100)
        return json(call, ok(buildJsonObject {
            put("platform", "desktop")
            put("protocolVersion", PROTOCOL_VERSION)
            put("extensionCount", catalog.list().size)
            put("recent", buildJsonArray { recent.forEach { add(it.toJson()) } })
            put("cache", cacheJson())
            put("capabilities", buildJsonArray {
                add(buildJsonObject {
                    put("ecosystem", "nijiyome")
                    put("available", true)
                    put("reason", "Nijiyome .kai extensions installed on this PC")
                })
            })
        }))
    }

    // ---- plumbing ------------------------------------------------------------------------------------------------------

    private suspend fun body(call: ApplicationCall): JsonObject {
        val bytes = try {
            call.readBody(MAX_BODY, 10_000)
        } catch (_: BodyTooLarge) {
            throw Failure(413, "body_too_large")
        } catch (_: Exception) {
            throw Failure(400, "invalid_json")
        }
        if (bytes.isEmpty()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject }.getOrNull() ?: throw Failure(400, "invalid_json")
    }

    private fun query(call: ApplicationCall): Map<String, String> = call.request.queryString().split('&').filter { it.isNotEmpty() }.associate {
        it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }

    /** `http://<Host>` of the request (what the phone used to reach us), else the bound address. */
    private fun baseUrl(call: ApplicationCall): String {
        val h = call.request.header("Host")?.takeIf { it.isNotEmpty() && it.length <= 255 && it.all { c -> c.isLetterOrDigit() && c.code < 128 || c in ".:-[]" } }
        return if (h != null) "http://$h" else "http://127.0.0.1:$port"
    }

    private fun stateFailure(e: StateException): Failure = when (e.kind) {
        StateException.Kind.PAIRING_LIMIT -> Failure.PAIRING_LIMIT
        StateException.Kind.DEVICE_LIMIT -> Failure(409, "device_limit")
        StateException.Kind.REPOSITORY_LIMIT -> Failure(409, "repository_limit")
        StateException.Kind.NO_DEVICE -> Failure.NOT_FOUND
        StateException.Kind.PASSWORD -> Failure(400, "password_length", e.message ?: "password_length")
        StateException.Kind.IO -> Failure.INTERNAL
    }

    private suspend fun json(call: ApplicationCall, body: JsonElement, status: Int = 200): Int {
        call.respondText(Json.encodeToString(JsonElement.serializer(), body), ContentType.Application.Json, HttpStatusCode.fromValue(status))
        return status
    }

    private suspend fun fail(call: ApplicationCall, f: Failure): Int {
        call.response.header("Cache-Control", "no-store")
        return json(call, f.body(), f.status)
    }

    private suspend fun empty(call: ApplicationCall, status: Int): Int {
        call.respondBytes(ByteArray(0), null, HttpStatusCode.fromValue(status))
        return status
    }

    companion object {
        const val BOOTSTRAP_HEADER = "X-Nijiyome-Bootstrap"
        const val BUNDLE_SHA_HEADER = "X-Nijiyome-Bundle-Sha256"
        /** Native shells always accepted (the hosted web origins are not). */
        val NATIVE_ORIGINS = listOf("capacitor://localhost", "http://localhost", "https://localhost")
        private const val MAX_BODY = 4 * 1024 * 1024
        private const val MAX_KAI = 20 * 1024 * 1024

        /** Binds the private LAN IPv4 + 127.0.0.1 (or 127.0.0.1 only) and serves until [stop]. */
        fun start(cfg: ExtServerConfig, catalog: ExtensionCatalog): ExtServer {
            val hosts = when {
                cfg.loopbackOnly -> listOf("127.0.0.1")
                cfg.bindIps.isNotEmpty() -> cfg.bindIps
                else -> (app.nijiyome.server.link.LinkServer.detectLanIps() + "127.0.0.1").distinct()
            }
            // like the Link server: only literal private (or loopback) IPv4 addresses, never a wildcard or a public one (plain HTTP
            // carries pairing codes, passwords and tokens)
            for (h in hosts) {
                val ip = if (literalIp(h)) runCatching { java.net.InetAddress.getByName(h) }.getOrNull() else null
                if (ip == null || !app.nijiyome.server.link.LinkServer.isPrivateOrLoopback(ip)) {
                    throw ExtStartException("refusing to listen on $h: not a private or loopback IPv4 address", publicBind = true)
                }
            }
            val state = ExtState.open(cfg.dir)
            val upstream = cfg.upstream ?: SafeHttp(NetPolicy.extensionFetch())
            val s = ExtServer(cfg, catalog, state, LogBuffer(), MediaProxy(cfg.dir, upstream), upstream, ExtAuth.nowUnix())
            try {
                s.http = KtorServer.start(hosts, cfg.port, writeTimeoutSecs = 120, threads = 4) { s.handle(it) }
            } catch (e: BindException) {
                throw ExtStartException("port ${cfg.port} is in use", e)
            }
            s.log("started", name = s.addresses.firstOrNull()?.toString())
            return s
        }

        /** An IPv4 / IPv6 literal (never a host name, which would be resolved, maybe to something else later). */
        internal fun literalIp(h: String): Boolean = h.isNotEmpty() && (h.all { it.isDigit() || it == '.' } || ':' in h)

        /** This PC's host name (`/health` `name` when none is configured). */
        fun hostName(): String {
            val raw = listOf("COMPUTERNAME", "HOSTNAME").firstNotNullOfOrNull { System.getenv(it)?.trim()?.takeIf(String::isNotEmpty) }
                ?: runCatching { File("/etc/hostname").readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
                ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
            return raw?.filterNot { it.isISOControl() }?.take(60)?.takeIf { it.isNotEmpty() } ?: "Nijiyome"
        }

        /** `http://<ip>:<port>` for every LAN address, loopback last. */
        fun urlsFor(ips: List<String>, port: Int): List<String> {
            val out = ips.filter { it != "127.0.0.1" && it != "0.0.0.0" }.map { "http://$it:$port" }.distinct().toMutableList()
            out += "http://127.0.0.1:$port"
            return out
        }

        /** Which `.kai` route an `/invoke` op runs (classic op names and the host route names are both accepted). */
        fun invokeRoute(op: String, novel: Boolean): String? = when (op) {
            "browse", "custom/home" -> "custom/home"
            "search" -> if (novel) "novel/search" else "custom/search"
            "custom/search" -> "custom/search"
            "info", "episodes", "custom/info" -> "custom/info"
            "resolve", "custom/watch" -> "custom/watch"
            "custom/read" -> "custom/read"
            "chapters", "manga/chapters" -> "manga/chapters"
            "pages", "manga/pages" -> "manga/pages"
            "novelChapters", "novel/chapters" -> "novel/chapters"
            "novelContent", "novel/content" -> "novel/content"
            "novel/search" -> "novel/search"
            "providers" -> "providers"
            else -> null
        }

        /** `<serverId>/<extId>[~read|~novel]` -> `(extId, novel variant)`. */
        fun parseSourceId(sourceId: String, serverId: String): Pair<String, Boolean> {
            val s = sourceId.removePrefix("$serverId/")
            val novel = s.endsWith("~novel")
            return s.removeSuffix("~novel").removeSuffix("~read") to novel
        }

        private val PROXIED_KEYS = listOf("url", "imageUrl", "image", "poster", "thumbnail", "icon")
        private val NESTED_KEYS = listOf("subtitles", "pages", "sources", "streams", "metas", "videos", "results", "items", "sections", "chapters")

        private fun headersOf(o: JsonObject): List<Pair<String, String>>? =
            (o["headers"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { k to it } }

        private fun sameOrigin(a: String, b: String): Boolean {
            val x = a.toHttpUrlOrNull() ?: return false
            val y = b.toHttpUrlOrNull() ?: return false
            return originOf(x) == originOf(y)
        }

        /**
         * Rewrites every media / image URL of an extension answer through [mint]: an object's own `headers` (else the parent's,
         * without credentials when the origin changes) travel with the ticket; `headers`, `originalUrl` and `behaviorHints` are
         * removed; embed / custom page URLs stay as they are.
         */
        fun proxify(value: JsonElement, inherited: Pair<String, List<Pair<String, String>>>?, mint: (String, List<Pair<String, String>>) -> String): JsonElement =
            when (value) {
                is JsonArray -> JsonArray(value.map { proxify(it, inherited, mint) })
                is JsonObject -> {
                    val own = headersOf(value)
                    val parentUrl = inherited?.first
                    val parentHeaders = inherited?.second.orEmpty()
                    val type = ((value["sourceType"] ?: value["type"]) as? JsonPrimitive)?.contentOrNull
                    val embed = type == "embed" || type == "custom" || (value["isEmbed"] as? JsonPrimitive)?.contentOrNull == "true"
                    val out = LinkedHashMap(value)
                    for (key in PROXIED_KEYS) {
                        val u = (value[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                        if (!(u.startsWith("http://") || u.startsWith("https://")) || (key == "url" && embed)) continue
                        val headers = own ?: if (parentUrl != null && !sameOrigin(parentUrl, u)) stripCredentials(parentHeaders) else parentHeaders
                        out[key] = JsonPrimitive(mint(u, headers))
                    }
                    out.remove("headers")
                    out.remove("originalUrl")
                    out.remove("behaviorHints")
                    val myUrl = (value["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val myHeaders = own ?: parentHeaders
                    for (key in NESTED_KEYS) {
                        val child = value[key] ?: continue
                        out[key] = proxify(child, if (myUrl != null) myUrl to myHeaders else inherited, mint)
                    }
                    JsonObject(out)
                }
                else -> value
            }

        /** `pages` answers may be plain URL strings: they become `{ imageUrl }`. */
        fun pagesAsObjects(v: JsonElement): JsonElement {
            fun map(a: JsonArray) = JsonArray(a.map { p -> if (p is JsonPrimitive && p.isString) buildJsonObject { put("imageUrl", p.content) } else p })
            return when (v) {
                is JsonArray -> map(v)
                is JsonObject -> (v["pages"] as? JsonArray)?.let { JsonObject(v + ("pages" to map(it))) } ?: v
                else -> v
            }
        }

        /** Route params of the `stream` / `torrent` routes from a `/resolve` body. */
        fun resolveParams(input: JsonObject, timeoutMs: Long): JsonObject {
            val titles = (input["titles"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty().toMutableList()
            input.str("title")?.takeIf { it.isNotEmpty() && it !in titles }?.let { titles.add(0, it) }
            return buildJsonObject {
                put("anilistId", (input["anilistId"] as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0) ?: 0)
                put("titles", JsonArray(titles.map(::JsonPrimitive)))
                put("episode", ((input["episode"] as? JsonPrimitive)?.longOrNull ?: 1).coerceAtLeast(1))
                put("stream", "0")
                put("resolution", "1080p")
                putJsonObject("providerOptions") { put("timeoutMs", minOf(timeoutMs, 25_000)) }
                (input["malId"] as? JsonPrimitive)?.longOrNull?.let { put("malId", it) }
            }
        }

        /** One source of an extension answer with the server's provider fields. */
        fun annotateSource(s: JsonElement, providerKey: String, extName: String, serverName: String): JsonElement {
            val o = (s as? JsonObject)?.let { LinkedHashMap(it) } ?: return s
            if ("sourceType" !in o) o["type"]?.let { o["sourceType"] = it }
            if ("sourceType" !in o && ("infoHash" in o || "magnet" in o)) o["sourceType"] = JsonPrimitive("torrent")
            val sourceName = listOf("sourceName", "providerName", "server", "source").firstNotNullOfOrNull { k -> (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content } ?: extName
            o["sourceName"] = JsonPrimitive(sourceName)
            o["providerKey"] = JsonPrimitive(providerKey)
            o["extensionId"] = JsonPrimitive(providerKey)
            o["extensionName"] = JsonPrimitive(extName)
            o["extensionServerName"] = JsonPrimitive(serverName)
            o["providerName"] = JsonPrimitive("$extName · $serverName")
            o["ecosystem"] = JsonPrimitive("nijiyome")
            return JsonObject(o)
        }

        /** `/auth/login` -> `/auth/login`, `/media/<t>/x` -> `/media`, unknown -> `/unknown`. */
        fun logRoute(path: String): String {
            val parts = path.removePrefix(API_PREFIX).split('/').filter { it.isNotEmpty() }
            val known = setOf("health", "setup", "auth", "extensions", "repositories", "sources", "invoke", "resolve", "settings", "devices", "diagnostics", "export", "import", "native", "cache", "media", "logs")
            val first = parts.firstOrNull()
            return when {
                first == "auth" && parts.getOrNull(1) in setOf("login", "logout", "password", "pair", "pairing") -> "/auth/${parts[1]}"
                first != null && first in known -> "/$first"
                else -> "/unknown"
            }
        }

        /** Whether a browser `Origin` may call the API. */
        fun originAllowed(origin: String, ownHost: String?, extra: List<String>): Boolean =
            (ownHost != null && hostAllowed(ownHost) && origin == "http://$ownHost") || origin in NATIVE_ORIGINS || origin in extra

        /**
         * The `Host` a request may carry: an IP literal (what phones and the media URLs use), `localhost` / `*.localhost`, or a
         * `.local` mDNS name; none of them can be pointed at this PC by a web page's own DNS (rebinding). Absent = HTTP/1.0, allowed.
         */
        fun hostAllowed(host: String?): Boolean {
            val h = (host ?: return true).trim().lowercase()
            if (h.isEmpty()) return false
            val name = when {
                h.startsWith("[") -> return h.substringBefore(']').removePrefix("[").let { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == ':' || c == '.' } }
                else -> h.substringBefore(':')
            }
            if (name.isNotEmpty() && name.all { it.isDigit() || it == '.' } && name.count { it == '.' } == 3) return true
            return name == "localhost" || name.endsWith(".localhost") || (name.endsWith(".local") && name.length > ".local".length)
        }

        /** [raw] may be the bare id or a whole media URL. */
        fun ticketId(raw: String): String = raw.substringAfter("/media/", raw).split('/', '?').first()

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun tokenAnswer(dev: DeviceRecord, token: String) = ok(buildJsonObject {
            put("token", token)
            put("role", dev.role)
            put("deviceId", dev.id)
        })

        fun pairingJson(p: Pairing) = buildJsonObject {
            put("code", p.code)
            put("expiresIn", p.expiresIn)
            put("url", p.url)
            put("deepLink", p.deepLink)
        }
    }
}

/** Bounded cache of unproxied `/invoke` answers: 64 entries, 2 MiB each, 5 minutes (30 s for empty lists). */
class LookupCache {
    private val entries = LinkedHashMap<String, Pair<Long, JsonElement>>()

    @Synchronized
    fun get(key: String): JsonElement? {
        val (at, v) = entries[key] ?: return null
        val ttl = if ((v as? JsonArray)?.isEmpty() == true) EMPTY_TTL_MS else TTL_MS
        return v.takeIf { System.currentTimeMillis() - at < ttl }
    }

    @Synchronized
    fun put(key: String, value: JsonElement) {
        if (value.toString().length > MAX_ENTRY_BYTES) return
        entries.remove(key)
        entries[key] = System.currentTimeMillis() to value
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun size(): Int = entries.values.count { System.currentTimeMillis() - it.first < TTL_MS }

    @Synchronized
    fun clear() = entries.clear()

    companion object {
        const val MAX_ENTRIES = 64
        const val MAX_ENTRY_BYTES = 2 * 1024 * 1024
        const val TTL_MS = 300_000L
        const val EMPTY_TTL_MS = 30_000L
    }
}
