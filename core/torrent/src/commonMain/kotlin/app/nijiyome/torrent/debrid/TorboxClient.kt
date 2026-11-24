// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent.debrid

import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 1.1 (F7): the TorBox REST API (`/v1/api/...`, docs: api.torbox.app/openapi.json and the TorBox SDK models).
 *
 * - Every answer is the envelope `{success, error, detail, data}`: `success:false` becomes `AppError.Unknown("debrid:torbox:<CODE>")`,
 *   with the auth codes (and 401 / 403) as `debrid:invalid_key` and `COOLDOWN_LIMIT` / 429 as `debrid:rate_limited`.
 * - 429: read-only calls wait (`Retry-After`, else 1 s, 2 s) and try again up to [MAX_RATE_RETRIES] times; `createtorrent` is never
 *   retried on its own (the uncached limit is 60 per hour).
 * - The key goes as `Authorization: Bearer` everywhere except `requestdl`, which TorBox wants with `token` in the query (and only
 *   there: no Bearer on that call). No URL, key or OkHttp exception text ever reaches an [AppException] message or cause, so nothing
 *   that logs an error can leak them.
 * - Links that are not well-formed `https://` URLs without user info are refused (`debrid:bad_link`); redirects are never followed and
 *   bodies are read up to [MAX_BODY_BYTES] (1.1 review #1, #2).
 *
 * [baseUrl] is injectable for the local fake server of the tests only; the app always uses [BASE].
 */
internal class TorboxClient(
    http: OkHttpClient,
    baseUrl: String = BASE,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /**
     * 1.1 review #1: no redirect is ever followed. `requestdl` carries the key in its query: a 3xx (to `http://`, to another host or
     * to the file itself) is an error, never a hop that could resend the key in clear text or to someone else.
     */
    private val http: OkHttpClient = http.withoutRedirects()

    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    /** `/user/me`: the account, or `debrid:invalid_key`. */
    suspend fun user(key: String): DebridUser {
        val data = call(get(url("user/me") { addQueryParameter("settings", "false") }, key), retry = true)
        val o = data as? JsonObject ?: throw AppException(AppError.Unknown("debrid:invalid_key"))
        return DebridUser(o["email"].str() ?: o["customer"].str(), parseInstantMs(o["premium_expires_at"]), plan = o["plan"].long()?.toInt())
    }

    /** `checkcached?format=object`: which of [hashes] (lowercase hex, at most one batch) are cached. */
    suspend fun checkCached(key: String, hashes: List<String>): Set<String> {
        if (hashes.isEmpty()) return emptySet()
        val url = url("torrents/checkcached") {
            addQueryParameter("hash", hashes.joinToString(","))
            addQueryParameter("format", "object")
            addQueryParameter("list_files", "false")
        }
        val keys = when (val data = call(get(url, key), retry = true)) {
            is JsonObject -> data.keys.map { it.lowercase(Locale.ROOT) }.toSet()
            // format=list (or an older answer): [{hash, name, size}]
            is JsonArray -> data.mapNotNull { it.obj()?.get("hash").str()?.lowercase(Locale.ROOT) }.toSet()
            else -> emptySet()
        }
        return hashes.filter { it.lowercase(Locale.ROOT) in keys }.toSet()
    }

    /**
     * `createtorrent` (multipart) from the bare [infoHash] magnet; returns the torrent id. [onlyIfCached] = `add_only_if_cached`: nothing is added when
     * TorBox does not have it (the instant-play path never leaves a half torrent on the account nor spends the uncached quota).
     * A `DUPLICATE_ITEM` answer that carries the id returns it; otherwise it is thrown so the caller looks the hash up.
     */
    suspend fun create(key: String, infoHash: String, onlyIfCached: Boolean): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            // 1.1 review #3: only the info-hash leaves the device (no dn, no trackers with passkeys, no web seeds)
            .addFormDataPart("magnet", hashOnlyMagnet(infoHash))
            .addFormDataPart("add_only_if_cached", onlyIfCached.toString())
            .addFormDataPart("allow_zip", "false")
            .build()
        val request = authed(url("torrents/createtorrent"), key).post(body).build()
        val data = try {
            call(request, retry = false)
        } catch (e: TorboxError) {
            val id = e.data.obj()?.let { it["torrent_id"].str() ?: it["id"].str() }
            if (e.code == "DUPLICATE_ITEM" && !id.isNullOrBlank()) return id
            throw e
        }
        val o = data.obj()
        return (o?.get("torrent_id").str() ?: o?.get("id").str())?.takeIf { it.isNotBlank() }
            ?: throw TorboxError("NOT_CACHED", null)
    }

    /** `mylist?id=`: the torrent [id] on the account (state, files), or null when it is not there. */
    suspend fun info(key: String, id: String): TorboxInfo? {
        val url = url("torrents/mylist") {
            addQueryParameter("id", id)
            addQueryParameter("bypass_cache", "true")
        }
        val data = try {
            call(get(url, key), retry = true)
        } catch (e: TorboxError) {
            if (e.code == "ITEM_NOT_FOUND") return null
            throw e
        }
        return torboxInfoOf(data, id)
    }

    /** The account torrent holding [hash], looked up page by page ([PAGE] items, at most [MAX_PAGES]); null when absent. */
    suspend fun findByHash(key: String, hash: String): String? {
        for (page in 0 until MAX_PAGES) {
            val url = url("torrents/mylist") {
                addQueryParameter("bypass_cache", "true")
                addQueryParameter("offset", (page * PAGE).toString())
                addQueryParameter("limit", PAGE.toString())
            }
            val items = (call(get(url, key), retry = true) as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
            items.firstOrNull { it["hash"].str()?.lowercase(Locale.ROOT) == hash.lowercase(Locale.ROOT) }?.let { return it["id"].str() }
            if (items.size < PAGE) return null
        }
        return null
    }

    /** `requestdl` (token in the query, no Bearer, `redirect=false`): the HTTPS link of one file. */
    suspend fun requestLink(key: String, torrentId: String, fileId: String): String {
        val url = url("torrents/requestdl") {
            addQueryParameter("token", key)
            addQueryParameter("torrent_id", torrentId)
            addQueryParameter("file_id", fileId)
            addQueryParameter("redirect", "false")
        }
        val link = call(Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build(), retry = true).str()
            ?: throw AppException(AppError.Unknown("debrid:bad_link"))
        if (!isSafeHttpsLink(link)) throw AppException(AppError.Unknown("debrid:bad_link"))
        return link
    }

    // ------------------------------------------------------------------------------------------------ transport

    /** Thrown for `success:false`; [code] = TorBox `error`. Its message never carries the URL or the key. */
    class TorboxError(val code: String, val data: kotlinx.serialization.json.JsonElement?) : Exception("debrid:torbox:$code")

    private fun url(path: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder().addPathSegments(path).apply(query).build()

    private fun authed(url: HttpUrl, key: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer $key").cacheControl(CacheControl.FORCE_NETWORK)

    private fun get(url: HttpUrl, key: String): Request = authed(url, key).build()

    /** Sends [request] and returns the envelope's `data`. */
    private suspend fun call(request: Request, retry: Boolean): kotlinx.serialization.json.JsonElement? {
        var attempt = 0
        while (true) {
            val (code, retryAfter, text) = send(request)
            if (code == 429) {
                if (!retry || attempt >= MAX_RATE_RETRIES) throw AppException(AppError.Unknown("debrid:rate_limited"))
                sleep(retryAfter?.coerceIn(0, MAX_WAIT_S)?.times(1000) ?: (1000L shl attempt))
                attempt++
                continue
            }
            val o = parseObject(text)
            val success = o?.get("success").bool()
            if (code == 401 || code == 403) throw AppException(AppError.Unknown("debrid:invalid_key"))
            if (success == false || (o != null && code !in 200..299 && o["error"].str() != null)) {
                val err = o?.get("error").str()?.uppercase(Locale.ROOT)?.takeIf { ERROR_CODE.matches(it) } ?: "UNKNOWN_ERROR"
                when (err) {
                    in AUTH_ERRORS -> throw AppException(AppError.Unknown("debrid:invalid_key"))
                    "COOLDOWN_LIMIT" -> throw AppException(AppError.Unknown("debrid:rate_limited"))
                }
                throw TorboxError(err, o?.get("data"))
            }
            if (code !in 200..299) throw AppException(AppError.Http(code))
            if (o == null) throw AppException(AppError.Unknown("debrid:bad_answer"))
            return o["data"]
        }
    }

    /**
     * One HTTP exchange, cancellable: a coroutine timeout (the [DebridRepositoryImpl.markCached] budget) cancels the OkHttp call
     * instead of waiting for it.
     */
    private suspend fun send(request: Request): Triple<Int, Long?, String> = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request.newBuilder().header("Accept", "application/json").build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // no cause: OkHttp messages can carry the URL (and requestdl's URL carries the key)
                    cont.resumeWithException(AppException(AppError.Offline))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { r -> Triple(r.code, r.header("Retry-After")?.trim()?.toLongOrNull(), readCapped(r, MAX_BODY_BYTES)) }
                    } catch (e: IOException) {
                        cont.resumeWithException(AppException(AppError.Offline))
                        return
                    }
                    cont.resume(result)
                }
            },
        )
    }

    companion object {
        const val BASE = "https://api.torbox.app/v1/api"

        /** Largest API answer read (a `mylist` page of 100 torrents with their files is far below). Bigger = `IOException`. */
        const val MAX_BODY_BYTES = 8L * 1024 * 1024
        const val MAX_RATE_RETRIES = 2
        private const val MAX_WAIT_S = 5L
        const val PAGE = 100
        const val MAX_PAGES = 3
        private val ERROR_CODE = Regex("[A-Z0-9_]{1,48}")
        private val AUTH_ERRORS = setOf("NO_AUTH", "BAD_TOKEN", "AUTH_ERROR")

        /** Codes that mean "TorBox cannot serve it now" for a reason the user should see (instead of "not cached"). */
        val USER_VISIBLE = setOf(
            "ACTIVE_LIMIT", "MONTHLY_LIMIT", "PLAN_RESTRICTED_FEATURE", "DOWNLOAD_TOO_LARGE",
            "DOWNLOAD_SERVER_ERROR", "NO_SERVERS_AVAILABLE_ERROR", "LINK_OFFLINE",
        )
    }
}
