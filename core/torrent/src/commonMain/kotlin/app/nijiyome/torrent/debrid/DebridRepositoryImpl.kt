// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent.debrid

import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.AppLog
import app.nijiyome.model.Container
import app.nijiyome.model.SecretKeys
import app.nijiyome.model.SecretStore
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.StreamSource
import app.nijiyome.model.torrent.DebridAccount
import app.nijiyome.model.torrent.DebridFile
import app.nijiyome.model.torrent.DebridFileRef
import app.nijiyome.model.torrent.DebridResolved
import app.nijiyome.model.torrent.DebridSettings
import app.nijiyome.model.torrent.EpisodeAlignment
import app.nijiyome.model.torrent.DebridRepository
import app.nijiyome.model.torrent.DebridService
import app.nijiyome.model.torrent.DebridStatus
import app.nijiyome.release.CandidateFile
import app.nijiyome.release.parseRelease
import app.nijiyome.release.pickEpisodeFile
import app.nijiyome.torrent.policy.AlignFile
import app.nijiyome.torrent.policy.alignedIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.9, 2026-11-09 amendment). Owner: worker q.
 *
 * One client per service behind the same interface, ported from the classic `src/core/providers/`:
 * - Real-Debrid: `/rest/1.0/user`, `/torrents/addMagnet`, `/torrents/info`, `/torrents/selectFiles`, `/unrestrict/link`
 *   (its `instantAvailability` endpoint is gone, so availability is learnt at resolve time: a torrent that RD starts downloading
 *   instead of answering "downloaded" is deleted again and reported as not cached).
 * - TorBox: `/v1/api/user/me`, `/torrents/checkcached`, `/createtorrent`, `/mylist`, `/requestdl`.
 * - AllDebrid: `/v4/user`, `/v4/magnet/upload`, `/v4.1/magnet/status`, `/v4/magnet/files`, `/v4/link/unlock`.
 * - Premiumize: `/api/account/info`, `/api/cache/check`, `/api/transfer/directdl`.
 * API keys come from [SecretStore] (`SecretKeys.debridApiKey`), never from settings; the account list (username, premium end,
 * preference order) is kept sealed in the same store under [KEY_ACCOUNTS] and rebuilt at start. [client] is the shared app client.
 *
 * 1.1 (F7): TorBox (the recommended, tested service) goes through [TorboxClient]: envelope errors, HTTPS-only links, 429 back-off,
 * `add_only_if_cached` on the instant path, no full `mylist` per play, a short TTL cache of `checkcached` answers and a time budget
 * for [markCached], and the episode of a pack picked with the manual alignment, then `pickEpisodeFile`, the regex only last.
 * [settings] = `TorrentSettings.debrid` (off = nothing here touches the network). [torboxBaseUrl] exists for the tests' fake server.
 */
class DebridRepositoryImpl(
    client: OkHttpClient,
    private val secrets: SecretStore,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope? = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val pollDelayMs: Long = POLL_MS,
    private val debridSettings: () -> DebridSettings = { DebridSettings() },
    torboxBaseUrl: String = TorboxClient.BASE,
    rateLimitSleep: suspend (Long) -> Unit = { delay(it) },
    private val cacheTtlMs: Long = CACHE_TTL_MS,
    private val markBudgetMs: Long = MARK_BUDGET_MS,
) : DebridRepository {

    // 1.1 review #1: no redirects on the debrid APIs (some of their URLs carry the key)
    private val http: OkHttpClient = client.newBuilder().callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS).build().withoutRedirects()
    private val torbox = TorboxClient(http, torboxBaseUrl, rateLimitSleep)

    /** TorBox `checkcached` answers: hash -> (cached, when). */
    private val torboxCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    /** hash -> TorBox torrent id seen this run (no `mylist` lookup on the next play / link refresh). */
    private val torboxIds = ConcurrentHashMap<String, String>()

    /** hash -> when the last debrid attempt failed: the engine's own instant path does not try it again right away. */
    private val failedAt = ConcurrentHashMap<String, Long>()

    /**
     * Manual episode alignment of a torrent (Torrents › align), set by the torrent engine, which owns the label store. Null = none.
     */
    @Volatile
    var alignmentOf: (String) -> EpisodeAlignment? = { null }

    override val settings: DebridSettings get() = debridSettings()
    private val _accounts = MutableStateFlow<List<DebridAccount>>(emptyList())
    override val accounts: StateFlow<List<DebridAccount>> = _accounts.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var loaded = false
    private val loadJob: Job?

    @Serializable
    private data class StoredAccount(val service: DebridService, val username: String? = null, val premiumUntilMs: Long? = null, val linkedAtMs: Long)

    init {
        loadJob = scope?.launch { loadSafe() }
    }

    private suspend fun loadSafe() {
        try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "debrid account load failed: ${errorCode(e)}")
        }
    }

    /** Ensures the sealed account list has been loaded before reading [_accounts] (prevents lazy startup race on Android). */
    private suspend fun ensureLoaded() {
        if (loaded) return
        loadJob?.join()
        if (!loaded) loadSafe()
    }

    /** Reads the sealed account list (only services whose key is still stored). Public for tests / a reset. */
    suspend fun load() = mutex.withLock {
        val raw = secrets.get(KEY_ACCOUNTS)
        val stored = raw?.let { runCatching { debridJson.decodeFromString(ListSerializer(StoredAccount.serializer()), it) }.getOrNull() }.orEmpty()
        _accounts.value = stored.filter { secrets.get(SecretKeys.debridApiKey(it.service.id)) != null }.map { it.toModel() }
        loaded = true
    }

    // ------------------------------------------------------------------------------------------------ accounts

    override suspend fun link(service: DebridService, apiKey: String): Result<DebridAccount> = try {
        ensureLoaded()
        val key = apiKey.trim()
        if (key.isEmpty() || key.any { it.isWhitespace() }) throw AppException(AppError.Unknown("debrid:invalid_key"))
        val user = userOf(service, key)
        val account = DebridAccount(service, user.username, user.premiumUntilMs, clock())
        mutex.withLock {
            secrets.put(SecretKeys.debridApiKey(service.id), key)
            _accounts.value = _accounts.value.filter { it.service != service } + account
            persist()
        }
        AppLog.i(TAG, "linked ${service.id}")
        Result.success(account)
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        // 401 / 403 from /user = the key is wrong
        val code = (e.error as? AppError.Http)?.code
        Result.failure(if (code == 401 || code == 403) AppException(AppError.Unknown("debrid:invalid_key")) else e)
    } catch (e: Exception) {
        Result.failure(e.toApp())
    }

    override suspend fun recheck(service: DebridService): Result<DebridAccount> = try {
        ensureLoaded()
        val key = keyOf(service) ?: throw AppException(AppError.Unknown("debrid:not_linked:${service.id}"))
        val user = userOf(service, key)
        val previous = _accounts.value.firstOrNull { it.service == service }
        val account = DebridAccount(service, user.username, user.premiumUntilMs, previous?.linkedAtMs ?: clock())
        mutex.withLock {
            _accounts.value = _accounts.value.map { if (it.service == service) account else it }
            persist()
        }
        Result.success(account)
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        val code = (e.error as? AppError.Http)?.code
        Result.failure(if (code == 401 || code == 403) AppException(AppError.Unknown("debrid:invalid_key")) else e)
    } catch (e: Exception) {
        Result.failure(e.toApp())
    }

    /** The account behind [key]; `debrid:invalid_key` when the service does not accept it. */
    private suspend fun userOf(service: DebridService, key: String): DebridUser =
        if (service == DebridService.TORBOX) {
            try {
                torbox.user(key)
            } catch (e: TorboxClient.TorboxError) {
                throw AppException(AppError.Unknown("debrid:torbox:${e.code}"))
            }
        } else {
            fetchUser(service, key) ?: throw AppException(AppError.Unknown("debrid:invalid_key"))
        }

    override suspend fun unlink(service: DebridService) {
        ensureLoaded()
        if (service == DebridService.TORBOX) {
            torboxIds.clear()
            torboxCache.clear()
        }
        mutex.withLock {
            secrets.put(SecretKeys.debridApiKey(service.id), null)
            _accounts.value = _accounts.value.filter { it.service != service }
            persist()
        }
        AppLog.i(TAG, "unlinked ${service.id}")
    }

    /** Moves [service] to the front: the first linked service that has a torrent is the one that plays it (preference order). */
    override suspend fun prefer(service: DebridService) {
        ensureLoaded()
        mutex.withLock {
            val list = _accounts.value
            val hit = list.firstOrNull { it.service == service } ?: return
            _accounts.value = listOf(hit) + list.filter { it.service != service }
            persist()
        }
    }

    // ------------------------------------------------------------------------------------------------ availability

    override suspend fun cached(infoHashes: List<String>): Map<String, Set<DebridService>> {
        if (!settings.enabled) return emptyMap()
        ensureLoaded()
        val hashes = infoHashes.map { it.lowercase(Locale.ROOT) }.filter { HEX40.matches(it) }.distinct()
        if (hashes.isEmpty()) return emptyMap()
        val out = HashMap<String, MutableSet<DebridService>>()
        for (account in _accounts.value) {
            val key = keyOf(account.service) ?: continue
            val hits = try {
                when (account.service) {
                    DebridService.TORBOX -> torboxCached(key, hashes)
                    DebridService.PREMIUMIZE -> hashes.chunked(CACHE_BATCH).flatMap { batch ->
                        val form = FormBody.Builder().add("apikey", key).apply { batch.forEach { add("items[]", it) } }.build()
                        parsePremiumizeCached(get(Request.Builder().url("$PREMIUMIZE/cache/check").post(form).build()), batch)
                    }.toSet()
                    // no instant-availability endpoint: learnt when playing (fastResolve)
                    DebridService.REAL_DEBRID, DebridService.ALLDEBRID -> emptySet()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // only the error code: never the exception (its text or cause may name the URL)
                AppLog.w(TAG, "cache check failed on ${account.service.id}: ${errorCode(e)}")
                emptySet()
            }
            hits.forEach { out.getOrPut(it) { LinkedHashSet() } += account.service }
        }
        return out
    }

    /**
     * TorBox availability with a [cacheTtlMs] memory of the answers: only hashes not asked recently go to `checkcached`, in batches of
     * [CACHE_BATCH], at most [MAX_NEW_HASHES] per call (a long list fills in over the next lookups).
     */
    private suspend fun torboxCached(key: String, hashes: List<String>): Set<String> {
        val now = clock()
        val fresh = hashes.filter { h -> torboxCache[h]?.let { now - it.second < cacheTtlMs } == true }
        val ask = (hashes - fresh.toSet()).take(MAX_NEW_HASHES)
        val batches = ask.chunked(CACHE_BATCH)
        if (batches.size == 1) {
            val batch = batches[0]
            val hits = torbox.checkCached(key, batch)
            val at = clock()
            batch.forEach { torboxCache[it] = (it in hits) to at }
        } else if (batches.size > 1) {
            coroutineScope {
                batches.map { batch ->
                    async {
                        val hits = torbox.checkCached(key, batch)
                        val at = clock()
                        batch.forEach { torboxCache[it] = (it in hits) to at }
                    }
                }.awaitAll()
            }
        }
        // 1.1 review #8: an answer past its TTL that was not asked again this time is not trusted
        val at = clock()
        if (torboxCache.size > MAX_CACHE_ENTRIES) torboxCache.entries.removeIf { at - it.value.second >= cacheTtlMs }
        return hashes.filter { h -> torboxCache[h]?.let { it.first && at - it.second < cacheTtlMs } == true }.toSet()
    }

    override suspend fun markCached(candidates: List<SourceCandidate>): List<SourceCandidate> {
        if (!settings.enabled) return candidates
        ensureLoaded()
        val linked = _accounts.value.map { it.service }
        if (linked.isEmpty()) return candidates
        val hashes = candidates.filter { it.kind == SourceKind.TORRENT }.mapNotNull { it.torrent?.infoHash }
        if (hashes.isEmpty()) return candidates
        // a slow service never holds the list back: past the budget the list goes on unmarked
        val map = withTimeoutOrNull(markBudgetMs) { runCatching { cached(hashes) }.getOrNull() }
        if (map == null) {
            AppLog.w(TAG, "markCached timed out after ${markBudgetMs}ms for ${hashes.size} hashes")
            return candidates
        }
        return candidates.map { c -> c.copy(debrid = debridStatusFor(c, linked, map) ?: c.debrid) }
    }

    // ------------------------------------------------------------------------------------------------ files / resolve

    override suspend fun files(service: DebridService, infoHash: String, magnet: String?): Result<List<DebridFile>> = try {
        val prepared = prepare(service, infoHash, magnet, maxPolls = FULL_POLLS)
        Result.success(
            prepared.files.mapIndexed { i, f ->
                DebridFile(index = i, name = f.name.substringAfterLast('/'), sizeBytes = f.size, episode = episodeOf(f.name))
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toApp())
    }

    override suspend fun resolve(service: DebridService, infoHash: String, magnet: String?, fileIndex: Int?, episode: Int?): Result<StreamSource> =
        resolveFile(service, infoHash, magnet, fileIndex, episode, emptyList()).map { it.stream }

    override suspend fun resolveFile(
        service: DebridService,
        infoHash: String,
        magnet: String?,
        fileIndex: Int?,
        episode: Int?,
        titles: List<String>,
    ): Result<DebridResolved> = resolveWith(service, infoHash, magnet, fileIndex, episode, titles, FULL_POLLS)

    /**
     * Used by the torrent engine before starting libtorrent (checklist sources.debrid.realdebrid etc.): the first linked service, in
     * preference order, that serves [infoHash] instantly. Services with a cache endpoint are asked first; Real-Debrid / AllDebrid are
     * probed with a short poll. Null = play it with libtorrent. 1.1 (F7): a hash whose debrid attempt just failed (the player tried
     * it a moment ago) is not tried again, so a failure costs one attempt before libtorrent, not two.
     */
    suspend fun instantStream(infoHash: String, magnet: String?, episode: Int?, titles: List<String> = emptyList()): DebridResolved? {
        if (!settings.enabled) return null
        ensureLoaded()
        val linked = _accounts.value.map { it.service }
        if (linked.isEmpty()) return null
        val hash = infoHash.lowercase(Locale.ROOT)
        if (failedRecently(hash)) return null
        val cachedOn = runCatching { cached(listOf(hash))[hash].orEmpty() }.getOrDefault(emptySet())
        val order = linked.filter { it in cachedOn } + linked.filter { it in PROBED && it !in cachedOn }
        for (service in order) {
            val result = resolveWith(service, hash, magnet, null, episode, titles, if (service in cachedOn) FULL_POLLS else PROBE_POLLS)
            result.getOrNull()?.let { return it }
            AppLog.i(TAG, "${service.id} cannot serve it now: ${result.exceptionOrNull()?.let(::errorCode)}")
        }
        return null
    }

    /** True while [infoHash] failed on a debrid service less than [FAILED_SKIP_MS] ago. */
    fun failedRecently(infoHash: String): Boolean =
        failedAt[infoHash.lowercase(Locale.ROOT)]?.let { clock() - it < FAILED_SKIP_MS } == true

    private suspend fun resolveWith(
        service: DebridService,
        infoHash: String,
        magnet: String?,
        fileIndex: Int?,
        episode: Int?,
        titles: List<String>,
        maxPolls: Int,
    ): Result<DebridResolved> = try {
        if (!settings.enabled) throw AppException(AppError.Unknown("debrid:disabled"))
        val hash = infoHash.lowercase(Locale.ROOT)
        val prepared = prepare(service, hash, magnet, maxPolls)
        val file = chooseDebridFile(prepared.files, fileIndex, episode, titles, alignmentOf(hash))
            ?: throw AppException(AppError.Unknown("debrid:no_file"))
        val url = prepared.unlock(file)
        // 1.1 (F7): HTTPS only (Tatakai Next's tk-core `is_https`)
        if (!isSafeHttpsLink(url)) throw AppException(AppError.Unknown("debrid:bad_link"))
        val tags = runCatching { parseRelease(file.name.substringAfterLast('/')) }.getOrNull()
        failedAt.remove(hash)
        Result.success(
            DebridResolved(
                stream = StreamSource(
                    url = url,
                    headers = emptyMap(),
                    container = Container.PROGRESSIVE,
                    quality = tags?.resolution?.label()?.takeIf { it.isNotEmpty() },
                    provider = service.label,
                    extensionId = TORRENT_EXTENSION_ID,
                ),
                // 1.1 review #3: the reference keeps the info-hash only (it is stored in downloads.json)
                file = DebridFileRef(service, hash, file.id, file.name.substringAfterLast('/')),
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failedAt[infoHash.lowercase(Locale.ROOT)] = clock()
        Result.failure(e.toApp())
    }

    override suspend fun refreshLink(file: DebridFileRef): Result<String> = try {
        if (!settings.enabled) throw AppException(AppError.Unknown("debrid:disabled"))
        val url = when (file.service) {
            DebridService.TORBOX -> {
                val key = keyOf(file.service) ?: throw AppException(AppError.Unknown("debrid:not_linked:${file.service.id}"))
                val hash = file.infoHash.lowercase(Locale.ROOT)
                val known = torboxIds[hash]
                try {
                    torbox.requestLink(key, known ?: throw TorboxClient.TorboxError("ITEM_NOT_FOUND", null), file.fileId)
                } catch (e: TorboxClient.TorboxError) {
                    // 1.1 review #9: only "not on the account any more" adds it again; any other refusal is the user's reason
                    if (e.code != "ITEM_NOT_FOUND") throw AppException(AppError.Unknown("debrid:torbox:${e.code}"))
                    torboxIds.remove(hash)
                    val prepared = prepareTorbox(key, hash, FULL_POLLS)
                    // the file ids may change when the torrent is added again: same id, else the same file name
                    val match = prepared.files.firstOrNull { it.id == file.fileId }
                        ?: prepared.files.firstOrNull { it.name.substringAfterLast('/') == file.fileName }
                        ?: throw AppException(AppError.Unknown("debrid:no_file"))
                    prepared.unlock(match)
                }
            }
            else -> {
                // the other services: resolve the same file again by name
                val prepared = prepare(file.service, file.infoHash.lowercase(Locale.ROOT), null, FULL_POLLS)
                val match = prepared.files.firstOrNull { it.id == file.fileId }
                    ?: prepared.files.firstOrNull { it.name.substringAfterLast('/') == file.fileName }
                    ?: throw AppException(AppError.Unknown("debrid:no_file"))
                prepared.unlock(match)
            }
        }
        if (!isSafeHttpsLink(url)) throw AppException(AppError.Unknown("debrid:bad_link"))
        Result.success(url)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toApp())
    }

    /** The torrent added (or found) on the service, its files and how to turn one into an HTTPS link. */
    private class Prepared(val files: List<RemoteFile>, val unlock: suspend (RemoteFile) -> String)

    private suspend fun prepare(service: DebridService, infoHash: String, magnet: String?, maxPolls: Int): Prepared {
        val key = keyOf(service) ?: throw AppException(AppError.Unknown("debrid:not_linked:${service.id}"))
        val hash = infoHash.lowercase(Locale.ROOT)
        // 1.1 review #3: only the info-hash goes to the service, whatever the source's magnet carried (dn, private trackers, ws)
        val magnetUri = hashOnlyMagnet(hash)
        return when (service) {
            DebridService.REAL_DEBRID -> prepareRealDebrid(key, magnetUri, maxPolls)
            DebridService.TORBOX -> prepareTorbox(key, hash, maxPolls)
            DebridService.ALLDEBRID -> prepareAllDebrid(key, magnetUri, maxPolls)
            DebridService.PREMIUMIZE -> {
                val form = FormBody.Builder().add("apikey", key).add("src", magnetUri).build()
                val files = parsePremiumizeContent(get(Request.Builder().url("$PREMIUMIZE/transfer/directdl").post(form).build()))
                if (files.isEmpty()) throw NotCachedException(service)
                Prepared(files) { it.id }
            }
        }
    }

    private suspend fun prepareRealDebrid(key: String, magnet: String, maxPolls: Int): Prepared {
        val added = parseObject(get(Request.Builder().url("$RD/torrents/addMagnet").bearer(key).post(FormBody.Builder().add("magnet", magnet).build()).build()))
        val id = added?.get("id").str()?.takeIf { it.isNotBlank() } ?: throw AppException(AppError.Unknown("debrid:rejected"))
        var info: RdInfo? = null
        for (attempt in 0 until maxPolls) {
            info = parseRdInfo(get(Request.Builder().url("$RD/torrents/info/$id").bearer(key).build()))
            if (info != null && info.status in RD_FAILED) throw AppException(AppError.Unknown("debrid:failed:${info.status}"))
            if (info != null && info.files.isNotEmpty() && info.status != "magnet_conversion") break
            delay(pollDelayMs)
        }
        val files = info?.files.orEmpty()
        if (files.isEmpty()) {
            rdDelete(key, id)
            throw NotCachedException(DebridService.REAL_DEBRID)
        }
        return Prepared(files) { file ->
            get(Request.Builder().url("$RD/torrents/selectFiles/$id").bearer(key).post(FormBody.Builder().add("files", file.id).build()).build())
            var links: List<String> = emptyList()
            for (attempt in 0 until maxPolls) {
                val now = parseRdInfo(get(Request.Builder().url("$RD/torrents/info/$id").bearer(key).build()))
                if (now != null && now.status in RD_FAILED) throw AppException(AppError.Unknown("debrid:failed:${now.status}"))
                if (now != null && now.status == "downloaded" && now.links.isNotEmpty()) {
                    links = now.links
                    break
                }
                if (now != null && now.status in RD_NOT_CACHED && attempt >= RD_NOT_CACHED_AFTER) {
                    // RD started downloading: not instantly available; do not leave a half torrent on the account
                    rdDelete(key, id)
                    throw NotCachedException(DebridService.REAL_DEBRID)
                }
                delay(pollDelayMs)
            }
            val link = links.firstOrNull() ?: run {
                rdDelete(key, id)
                throw NotCachedException(DebridService.REAL_DEBRID)
            }
            val unrestricted = parseObject(get(Request.Builder().url("$RD/unrestrict/link").bearer(key).post(FormBody.Builder().add("link", link).build()).build()))
            unrestricted?.get("download").str() ?: throw AppException(AppError.Unknown("debrid:bad_link"))
        }
    }

    private suspend fun rdDelete(key: String, id: String) {
        runCatching { get(Request.Builder().url("$RD/torrents/delete/$id").bearer(key).delete().build()) }
    }

    /**
     * 1.1 (F7): the torrent on TorBox without reading the whole account list: the id this run already knows, else `createtorrent`
     * with `add_only_if_cached` (TorBox answers with the existing id for a torrent already on the account; a `DUPLICATE_ITEM` without
     * id is looked up page by page). Then a short poll of `mylist?id=` until the files are present.
     */
    private suspend fun prepareTorbox(key: String, hash: String, maxPolls: Int): Prepared {
        var id = torboxIds[hash]
        var info = id?.let { runCatching { torbox.info(key, it) }.getOrNull() }
        if (info == null) {
            id = try {
                torbox.create(key, hash, onlyIfCached = true)
            } catch (e: TorboxClient.TorboxError) {
                when (e.code) {
                    "DUPLICATE_ITEM" -> torbox.findByHash(key, hash)
                    in TorboxClient.USER_VISIBLE -> throw AppException(AppError.Unknown("debrid:torbox:${e.code}"))
                    // add_only_if_cached on something TorBox does not have
                    else -> null
                }
            } ?: run {
                torboxCache[hash] = false to clock()
                throw NotCachedException(DebridService.TORBOX)
            }
            torboxIds[hash] = id
        }
        val torrentId = id ?: throw NotCachedException(DebridService.TORBOX)
        for (attempt in 0 until maxPolls) {
            if (info?.present == true && info.files.isNotEmpty()) break
            if (attempt > 0 || info != null) delay(pollDelayMs)
            info = torbox.info(key, torrentId)
            info?.error?.let { throw AppException(AppError.Unknown("debrid:failed:${it.take(40)}")) }
        }
        val ready = info?.takeIf { it.present && it.files.isNotEmpty() } ?: throw NotCachedException(DebridService.TORBOX)
        torboxCache[hash] = true to clock()
        return Prepared(ready.files) { file ->
            try {
                torbox.requestLink(key, torrentId, file.id)
            } catch (e: TorboxClient.TorboxError) {
                throw AppException(AppError.Unknown("debrid:torbox:${e.code}"))
            }
        }
    }

    private suspend fun prepareAllDebrid(key: String, magnet: String, maxPolls: Int): Prepared {
        val upload = parseObject(get(Request.Builder().url("$ALLDEBRID_V4/magnet/upload").bearer(key).post(FormBody.Builder().add("magnets[]", magnet).build()).build()))
        val first = upload?.get("data").obj()?.get("magnets").arr()?.firstOrNull().obj()
        if (first?.get("error") != null) throw AppException(AppError.Unknown("debrid:rejected"))
        val id = first?.get("id").str() ?: throw AppException(AppError.Unknown("debrid:rejected"))
        var ready = false
        for (attempt in 0 until maxPolls) {
            val status = parseAllDebridStatus(
                get(Request.Builder().url("$ALLDEBRID_V41/magnet/status").bearer(key).post(FormBody.Builder().add("id", id).build()).build()),
                id,
            )
            if (status != null && status > 4) throw AppException(AppError.Unknown("debrid:failed:$status"))
            if (status == 4) {
                ready = true
                break
            }
            delay(pollDelayMs)
        }
        if (!ready) throw NotCachedException(DebridService.ALLDEBRID)
        val files = parseAllDebridFiles(
            get(Request.Builder().url("$ALLDEBRID_V4/magnet/files").bearer(key).post(FormBody.Builder().add("id[]", id).build()).build()),
            id,
        )
        if (files.isEmpty()) throw AppException(AppError.Unknown("debrid:no_file"))
        return Prepared(files) { file ->
            val o = parseObject(get(Request.Builder().url("$ALLDEBRID_V4/link/unlock").bearer(key).post(FormBody.Builder().add("link", file.id).build()).build()))
            o?.get("data").obj()?.get("link").str() ?: throw AppException(AppError.Unknown("debrid:bad_link"))
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private suspend fun fetchUser(service: DebridService, key: String): DebridUser? {
        val request = when (service) {
            DebridService.REAL_DEBRID -> Request.Builder().url("$RD/user").bearer(key).build()
            DebridService.TORBOX -> error("TorBox goes through TorboxClient")
            DebridService.ALLDEBRID -> Request.Builder().url("$ALLDEBRID_V4/user").bearer(key).build()
            DebridService.PREMIUMIZE -> Request.Builder()
                .url("$PREMIUMIZE/account/info".toHttpUrl().newBuilder().addQueryParameter("apikey", key).build())
                .build()
        }
        return parseUser(service, get(request))
    }

    private suspend fun keyOf(service: DebridService): String? = secrets.get(SecretKeys.debridApiKey(service.id))?.takeIf { it.isNotBlank() }

    /** Body of a successful answer; `AppError.Http(code)` otherwise, `AppError.Offline` on network failures. Never logs the URL. */
    private suspend fun get(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request.newBuilder().header("Accept", "application/json").build()).execute().use { response ->
                val body = readCapped(response, TorboxClient.MAX_BODY_BYTES)
                if (!response.isSuccessful) throw AppException(AppError.Http(response.code))
                body
            }
        } catch (e: IOException) {
            // 1.1 (F7): no cause: OkHttp's messages may carry the URL, and some of these URLs carry the key
            throw AppException(AppError.Offline)
        }
    }

    private fun Request.Builder.bearer(key: String): Request.Builder = header("Authorization", "Bearer $key")

    private suspend fun persist() {
        val list = _accounts.value.map { StoredAccount(it.service, it.username, it.premiumUntilMs, it.linkedAtMs) }
        secrets.put(KEY_ACCOUNTS, debridJson.encodeToString(ListSerializer(StoredAccount.serializer()), list))
    }

    private fun StoredAccount.toModel() = DebridAccount(service, username, premiumUntilMs, linkedAtMs)

    private fun Exception.toApp(): AppException = when (this) {
        is AppException -> this
        is NotCachedException -> AppException(AppError.Unknown(message ?: "debrid:not_cached"))
        is TorboxClient.TorboxError -> AppException(AppError.Unknown("debrid:torbox:$code"))
        is IOException -> AppException(AppError.Offline)
        else -> AppException(AppError.Unknown("debrid:${this::class.simpleName ?: "error"}"))
    }

    /** What a log line may say about [e]: the AppError text (codes only), never a message that could hold a URL. */
    private fun errorCode(e: Throwable): String = when (e) {
        is AppException -> e.error.toString()
        is NotCachedException -> e.message.orEmpty()
        is TorboxClient.TorboxError -> "torbox:${e.code}"
        else -> e::class.simpleName ?: "error"
    }

    private fun episodeOf(name: String): Int? = runCatching { parseRelease(name.substringAfterLast('/')).episodes?.first }.getOrNull()

    companion object {
        private const val TAG = "debrid"
        const val KEY_ACCOUNTS = "debrid.accounts"
        const val TORRENT_EXTENSION_ID = "torrent"
        private const val RD = "https://api.real-debrid.com/rest/1.0"
        private const val ALLDEBRID_V4 = "https://api.alldebrid.com/v4"
        private const val ALLDEBRID_V41 = "https://api.alldebrid.com/v4.1"
        private const val PREMIUMIZE = "https://www.premiumize.me/api"
        private const val CALL_TIMEOUT_S = 20L
        private const val POLL_MS = 1_000L
        private const val FULL_POLLS = 30
        private const val CACHE_TTL_MS = 10 * 60_000L
        private const val MARK_BUDGET_MS = 12_000L
        private const val MAX_NEW_HASHES = 100
        private const val MAX_CACHE_ENTRIES = 2_000
        private const val FAILED_SKIP_MS = 2 * 60_000L
        private const val PROBE_POLLS = 6
        private const val RD_NOT_CACHED_AFTER = 2
        private const val CACHE_BATCH = 50
        private val HEX40 = Regex("[0-9a-f]{40}")
        private val PROBED = setOf(DebridService.REAL_DEBRID, DebridService.ALLDEBRID)

        /**
         * 1.1 (F7): the file of a torrent to play: [fileIndex] when given; else the manual episode [alignment] of the torrent; else
         * `pickEpisodeFile` with the series [titles] (the same picker as the libtorrent engine: skips NCOP / specials / other
         * seasons); only then the episode regex / largest video of the classic client.
         */
        internal fun chooseDebridFile(
            files: List<RemoteFile>,
            fileIndex: Int?,
            episode: Int?,
            titles: List<String>,
            alignment: EpisodeAlignment?,
        ): RemoteFile? {
            fileIndex?.let { i -> return files.getOrNull(i) }
            val ep = episode?.takeIf { it > 0 } ?: return selectBestVideo(files, episode)
            if (alignment != null) {
                val videos = files.withIndex().filter { isDebridVideo(it.value.path) }
                val aligned = alignedIndex(videos.map { (i, f) -> AlignFile(i, f.path, episodeNumber(f.name)) }, alignment, ep)
                if (aligned != null) return files[aligned]
            }
            val picked = runCatching {
                pickEpisodeFile(files.mapIndexed { i, f -> CandidateFile(i, f.path, f.size) }, ep, titles)
            }.getOrNull()
            if (picked != null) files.getOrNull(picked)?.let { return it }
            // 1.1 review #5: the regex only among files that name this episode; a pack without it = no file (`debrid:no_file`), never
            // the biggest video of the pack
            return selectBestVideo(files.filter { matchesEpisode(it.name.substringAfterLast('/'), ep) }, ep)
        }

        private fun episodeNumber(name: String): Int? =
            runCatching { parseRelease(name.substringAfterLast('/')).episodes?.first }.getOrNull()

        /**
         * [SourceCandidate.debrid] for one candidate: the first linked service (preference order) that has it cached; otherwise
         * "not cached" on the first linked service that can tell; otherwise null (only RD / AllDebrid linked: unknown until played).
         */
        internal fun debridStatusFor(c: SourceCandidate, linked: List<DebridService>, cached: Map<String, Set<DebridService>>): DebridStatus? {
            if (c.kind != SourceKind.TORRENT) return null
            val hash = c.torrent?.infoHash?.lowercase(Locale.ROOT) ?: return null
            val on = cached[hash].orEmpty()
            linked.firstOrNull { it in on }?.let { return DebridStatus(it, cached = true) }
            linked.firstOrNull { it !in PROBED }?.let { return DebridStatus(it, cached = false) }
            return null
        }
    }
}
