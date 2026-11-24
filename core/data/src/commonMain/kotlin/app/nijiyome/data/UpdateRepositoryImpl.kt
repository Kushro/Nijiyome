// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.UpdateInfo
import app.nijiyome.model.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime

// ==================================================================== update check

/**
 * New-version notice: GitHub Releases API (`https://api.github.com/repos/$REPO/releases/latest`), compared with the installed
 * [installedVersion]; at most one request per 24 h (stamp + last answer in the settings DataStore), none when [enabled] is false
 * unless forced. Until a release with an APK exists the check answers "up to date" (null). Starts one unforced check at creation.
 */
class UpdateRepositoryImpl(
    private val client: OkHttpClient,
    private val installedVersion: String,
    private val enabled: () -> Boolean = { true },
    private val store: DataStore<Preferences>? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope? = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val endpoint: String = "https://api.github.com/repos/$REPO/releases/latest",
    /**
     * Whether a release carries a build for this platform (asset file names). Android: an `.apk`; desktop passes its own (an AppImage
     * on Linux, the installer or the portable zip on Windows), so a release without that build is not offered.
     */
    private val hasBuild: (assetNames: List<String>) -> Boolean = ::hasApk,
) : UpdateRepository {
    private val _latest = MutableStateFlow<UpdateInfo?>(null)
    override val latest: StateFlow<UpdateInfo?> = _latest.asStateFlow()
    private val mutex = Mutex()

    init {
        scope?.launch {
            delay(STARTUP_DELAY_MS)
            runCatching { check(force = false) }
        }
    }

    override suspend fun check(force: Boolean): Result<UpdateInfo?> = mutex.withLock {
        try {
            if (!force && !enabled()) return@withLock Result.success(null)
            val prefs = store?.let { runCatching { it.data.first() }.getOrNull() }
            val last = prefs?.get(KEY_LAST_CHECK) ?: 0L
            if (!force && clock() - last < DAY_MS) {
                // throttled: replay the last answer so the startup notice still shows
                val cached = prefs?.get(KEY_LAST_ANSWER)?.let { runCatching { json.decodeFromString(Cached.serializer(), it) }.getOrNull() }
                val info = cached?.toInfo()?.takeIf { isNewer(it.version, installedVersion) }
                _latest.value = info
                return@withLock Result.success(info)
            }
            val info = withContext(Dispatchers.IO) { fetch() }
            store?.let { s ->
                runCatching {
                    s.edit { p ->
                        p[KEY_LAST_CHECK] = clock()
                        if (info == null) p.remove(KEY_LAST_ANSWER) else p[KEY_LAST_ANSWER] = json.encodeToString(Cached.serializer(), Cached.of(info))
                    }
                }
            }
            val newer = info?.takeIf { isNewer(it.version, installedVersion) }
            _latest.value = newer
            Result.success(newer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(if (e is AppException) e else AppException(AppError.Offline, e))
        }
    }

    private fun fetch(): UpdateInfo? {
        val request = Request.Builder().url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null // no release yet: up to date
            if (!response.isSuccessful) throw AppException(AppError.Http(response.code))
            return parseRelease(response.body.string(), hasBuild)
        }
    }

    @Serializable
    private data class Cached(val version: String, val url: String, val notes: String? = null, val publishedAtMs: Long? = null) {
        fun toInfo() = UpdateInfo(version, url, notes, publishedAtMs)

        companion object {
            fun of(i: UpdateInfo) = Cached(i.version, i.url, i.notes, i.publishedAtMs)
        }
    }

    companion object {
        const val REPO = "Kushro/Nijiyome"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val STARTUP_DELAY_MS = 5_000L
        private val KEY_LAST_CHECK = longPreferencesKey("update_last_check_ms")
        private val KEY_LAST_ANSWER = stringPreferencesKey("update_last_answer_json")
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * `releases/latest` JSON -> [UpdateInfo]; null for drafts, pre-releases and releases without a build for this platform ([hasBuild]; Android:
         * an `.apk` asset, so desktop-only releases of the same repository are skipped). The URL is the release page (`html_url`), the user downloads from there.
         */
        fun parseRelease(body: String, hasBuild: (assetNames: List<String>) -> Boolean = ::hasApk): UpdateInfo? {
            val o = json.parseToJsonElement(body).jsonObject
            if (o["draft"]?.jsonPrimitive?.booleanOrNull == true || o["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
            val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
            val assets = (o["assets"] as? JsonArray)?.mapNotNull { a -> a.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.orEmpty()
            if (!hasBuild(assets)) return null
            val url = o["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://github.com/") }
                ?: "https://github.com/$REPO/releases/tag/$tag"
            val published = o["published_at"]?.jsonPrimitive?.contentOrNull?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            val notes = o["body"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.take(4_000)
            return UpdateInfo(version = tag.trim().removePrefix("v").removePrefix("V"), url = url, notes = notes, publishedAtMs = published)
        }

        /** Android's build of a release: an `.apk` asset. */
        fun hasApk(assetNames: List<String>): Boolean = assetNames.any { it.endsWith(".apk", ignoreCase = true) }

        /**
         * Numeric dotted comparison (`1.10.0 > 1.9.3`), leading `v` ignored; a pre-release suffix (`1.2.0-beta.1`) sorts below the
         * plain version. Unparseable versions are never "newer".
         */
        fun isNewer(candidate: String, installed: String): Boolean {
            val a = parseVersion(candidate) ?: return false
            val b = parseVersion(installed) ?: return true
            val n = maxOf(a.first.size, b.first.size)
            for (i in 0 until n) {
                val x = a.first.getOrElse(i) { 0 }
                val y = b.first.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            // same numbers: a release beats a pre-release of it
            return !a.second && b.second
        }

        /** (numbers, isPreRelease) */
        private fun parseVersion(v: String): Pair<List<Int>, Boolean>? {
            val clean = v.trim().removePrefix("v").removePrefix("V")
            val core = clean.substringBefore('-').substringBefore('+').substringBefore(' ')
            val numbers = core.split('.').map { it.toIntOrNull() ?: return null }
            if (numbers.isEmpty()) return null
            return numbers to clean.contains('-')
        }
    }
}
