// SPDX-License-Identifier: AGPL-3.0-or-later
// After Tatakai Next tk-server/src/upstream.rs (FlareSolverr, AGPL-3.0, same author).
package app.nijiyome.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** What FlareSolverr returned for a challenge: the cookies to send (one `Cookie` value) and the User-Agent they belong to. */
data class Clearance(val cookieHeader: String, val userAgent: String)

/**
 * The user's FlareSolverr (Settings › Red), as in Tatakai Next: a request that gets a Cloudflare challenge is solved once through it
 * and retried with the `cf_clearance` cookie and User-Agent it returns; that clearance is reused for the host for 10 minutes.
 */
object FlareSolverr {
    const val CLEARANCE_TTL_MS = 10 * 60 * 1000L
    /** A solve takes 15-45 s. */
    const val SOLVE_TIMEOUT_MS = 60_000L

    private val JSON = Json { ignoreUnknownKeys = true }
    private val JSON_TYPE = "application/json".toMediaType()

    /** The user's endpoint reached through the outbound route (a FlareSolverr on this PC or the LAN is reached directly). */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().outbound().readTimeout(SOLVE_TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS)
            .callTimeout(SOLVE_TIMEOUT_MS + 10_000, TimeUnit.MILLISECONDS).build()
    }

    /** `http(s)://host[:port][/path]` without a trailing slash; null when it is not one. */
    fun normalize(endpoint: String?): String? {
        val t = endpoint?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        val u = t.toHttpUrlOrNull() ?: return null
        if (u.username.isNotEmpty() || u.password.isNotEmpty() || u.query != null || u.fragment != null) return null
        return t
    }

    /** Whether an answer is a Cloudflare challenge page (what FlareSolverr can solve). */
    fun isChallenge(code: Int, header: (String) -> String?): Boolean {
        if (code != 403 && code != 429 && code != 503) return false
        val get = { n: String -> header(n).orEmpty().lowercase(Locale.ROOT) }
        return get("cf-mitigated") == "challenge" || ("cloudflare" in get("server") && "text/html" in get("content-type"))
    }

    /** A `request.get` answer (`{ status: "ok", solution: { cookies: [{name, value}], userAgent } }`); null when it has no clearance. */
    fun parseSolution(body: String): Clearance? = runCatching {
        val v = JSON.parseToJsonElement(body).jsonObject
        if (v["status"]?.jsonPrimitive?.contentOrNull != "ok") return null
        val sol = v["solution"]?.jsonObject ?: return null
        val cookies = sol["cookies"]?.jsonArray.orEmpty().mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            val n = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = o["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            "$n=$value".takeIf { s -> s.none { it == '\r' || it == '\n' || it == ';' } }
        }
        val ua = sol["userAgent"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (cookies.isEmpty() || ua.isEmpty() || ua.any { it == '\r' || it == '\n' }) return null
        Clearance(cookies.joinToString("; "), ua)
    }.getOrNull()

    /**
     * `POST <endpoint>/v1 { cmd: "request.get", url, maxTimeout, proxy? }`. With an outbound proxy set, FlareSolverr's browser is told
     * to fetch through it too (`proxy: { url, username, password }`), so the challenge is never solved from the user's own address
     * and the `cf_clearance` belongs to the proxy's address the app's requests come from.
     */
    fun solve(
        endpoint: String,
        url: String,
        timeoutMs: Long = SOLVE_TIMEOUT_MS,
        http: OkHttpClient = client,
        proxy: ProxyEndpoint? = Outbound.proxy,
    ): Result<Clearance> = runCatching {
        val body = solveBody(url, timeoutMs, proxy).toRequestBody(JSON_TYPE)
        http.newCall(Request.Builder().url("${endpoint.trimEnd('/')}/v1").post(body).build()).execute().use { r ->
            val text = r.body.string()
            parseSolution(text) ?: throw IOException(
                runCatching { JSON.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                    ?: "FlareSolverr returned no clearance (HTTP ${r.code})",
            )
        }
    }

    /** The JSON of a `request.get` (pure). */
    fun solveBody(url: String, timeoutMs: Long, proxy: ProxyEndpoint?): String = buildJsonObject {
        put("cmd", "request.get")
        put("url", url)
        put("maxTimeout", timeoutMs)
        if (proxy != null) {
            put(
                "proxy",
                buildJsonObject {
                    put("url", proxy.uri())
                    if (proxy.hasCredentials) {
                        put("username", proxy.username.orEmpty())
                        put("password", proxy.password.orEmpty())
                    }
                },
            )
        }
    }.toString()

    /** "Probar": `GET <endpoint>/` answers `{ msg: "FlareSolverr is ready!", version }`; the version is the detail. */
    fun probe(endpoint: String, timeoutMs: Long = 10_000): ProbeResult {
        val e = normalize(endpoint) ?: return ProbeResult.Failed("use an http:// or https:// address")
        val http = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        val started = System.nanoTime()
        return try {
            http.newCall(Request.Builder().url("$e/").build()).execute().use { r ->
                val v = runCatching { JSON.parseToJsonElement(r.body.string()).jsonObject }.getOrNull()
                    ?: return ProbeResult.Failed("not a FlareSolverr endpoint (no JSON answer)")
                val ready = v["msg"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT)?.contains("ready") == true ||
                    v["version"]?.jsonPrimitive?.isString == true
                if (!ready) return ProbeResult.Failed("not a FlareSolverr endpoint")
                ProbeResult.Ok((System.nanoTime() - started) / 1_000_000, v["version"]?.jsonPrimitive?.contentOrNull)
            }
        } catch (e: IOException) {
            ProbeResult.Failed(e.message?.take(200) ?: e.javaClass.simpleName)
        }
    }
}

/** FlareSolverr clearances per host, valid for [FlareSolverr.CLEARANCE_TTL_MS]. */
class ClearanceCache(private val now: () -> Long = System::currentTimeMillis) {
    private val map = ConcurrentHashMap<String, Pair<Long, Clearance>>()

    fun get(host: String): Clearance? {
        val (at, c) = map[host.lowercase(Locale.ROOT)] ?: return null
        return c.takeIf { now() - at < FlareSolverr.CLEARANCE_TTL_MS }
    }

    fun put(host: String, c: Clearance) {
        val t = now()
        map.entries.removeIf { t - it.value.first >= FlareSolverr.CLEARANCE_TTL_MS }
        map[host.lowercase(Locale.ROOT)] = t to c
    }

    fun clear() = map.clear()
}

/**
 * Application interceptor of the clients that fetch from sources (extensions, the media proxy): a host with a fresh clearance gets
 * its cookie and User-Agent; a Cloudflare challenge is solved once through [endpoint] (when set) and the request is retried.
 * Without an endpoint it does nothing.
 */
class FlareSolverrInterceptor(
    private val endpoint: () -> String? = { Outbound.flareSolverr },
    private val cache: ClearanceCache = shared,
    private val solver: (endpoint: String, url: String) -> Result<Clearance> = { e, u -> FlareSolverr.solve(e, u) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val fs = endpoint() ?: return chain.proceed(chain.request())
        val req = chain.request()
        val host = req.url.host
        val first = chain.proceed(withClearance(req, cache.get(host)))
        if (!FlareSolverr.isChallenge(first.code, first::header)) return first
        val c = solver(fs, req.url.toString()).getOrNull() ?: return first
        first.close()
        cache.put(host, c)
        return chain.proceed(withClearance(req, c))
    }

    companion object {
        /** One cache for the whole app (as Tatakai Next's per-route caches, but shared by the extension host and the media proxy). */
        val shared = ClearanceCache()

        fun withClearance(req: Request, c: Clearance?): Request {
            if (c == null) return req
            val cookie = req.header("Cookie")?.takeIf { it.isNotBlank() }?.let { "$it; ${c.cookieHeader}" } ?: c.cookieHeader
            return req.newBuilder().header("User-Agent", c.userAgent).header("Cookie", cookie).build()
        }
    }
}
