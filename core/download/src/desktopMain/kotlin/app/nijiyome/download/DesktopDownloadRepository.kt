// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.DesktopNoticeTexts
import app.nijiyome.model.SystemNotifier
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.AutoDownloadPreview
import app.nijiyome.model.AutoDownloadRule
import app.nijiyome.model.Container
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadProfile
import app.nijiyome.model.DownloadRepository
import app.nijiyome.model.DownloadRequest
import app.nijiyome.model.DownloadSettings
import app.nijiyome.model.DownloadState
import app.nijiyome.model.DownloadsSummary
import app.nijiyome.model.EmbedSniffer
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.ListStatus
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.LocalLibraryItem
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.StreamSource
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * [DownloadRepository] on desktop: the queue, history, auto-download rules and local library of the Android repository over
 * [DesktopDownloadEngine] instead of Media3. Downloads go to `DownloadSettings.directoryTreeUri` (a folder path) or [defaultFolder],
 * as `<folder>/<Title>/<Title> - 01.ts|mp4`; the folder of each download is fixed when it is queued. At most `maxConcurrent` run at
 * once; pausing (or closing the app) keeps what was written and the next run goes on from there. The local library is the files
 * the user imported and the folders they scanned, which stay watched ([LibraryWatcher]) so new videos there join on their own.
 *
 * Same private JSON stores as Android ([DownloadStore], under [storeDir]), plus the desktop targets and library folders.
 */
class DesktopDownloadRepository internal constructor(
    private val settings: () -> DownloadSettings,
    private val extensions: ExtensionRepository,
    private val metadata: MetadataRepository,
    private val history: HistoryRepository,
    private val lists: ListsRepository?,
    private val storeDir: File,
    private val defaultFolder: File,
    private val http: OkHttpClient,
    private val sniffer: () -> EmbedSniffer?,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Settling time of a file that appeared in a watched folder before it is imported (copies in progress). */
    private val watchSettleMs: Long = WATCH_SETTLE_MS,
    /** Period of the auto-download check while the app runs (Android: WorkManager every 30 min); 0 = never. */
    private val autoCheckEveryMs: Long = AUTO_CHECK_MS,
    /** The first auto-download check after the app starts (Tatakai Next: 90 s), then every [autoCheckEveryMs]. */
    private val firstAutoCheckMs: Long = FIRST_AUTO_CHECK_MS,
    private val engine: DesktopDownloadEngine = DesktopDownloadEngine(http),
    /** "Episode downloaded" / "download failed" system notifications (Android's DownloadNotifications); none when unbound. */
    private val notifier: SystemNotifier? = null,
    /** 1.1 (F7): renews the link of a debrid (TorBox) download when it starts or resumes; none when debrid is not bound. */
    private val debrid: () -> app.nijiyome.model.torrent.DebridRepository? = { null },
) : DownloadRepository, AppDataWiper, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DownloadStore(storeDir)
    private val mutex = Mutex()
    private val queueLock = Mutex()

    private val records = MutableStateFlow<Map<String, StoredDownload>>(emptyMap())
    private val targets = ConcurrentHashMap<String, String>()
    private val hidden = MutableStateFlow<Set<String>>(emptySet())
    private val running = ConcurrentHashMap<String, Job>()
    private val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()

    override val items: StateFlow<List<DownloadItem>> =
        records.map { m -> DownloadLogic.sorted(m.values.map { it.item }) }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val summary: StateFlow<DownloadsSummary> = items.map(DownloadLogic::summary).stateIn(scope, SharingStarted.Eagerly, DownloadsSummary())

    private val _rules = MutableStateFlow<List<AutoDownloadRule>>(emptyList())
    override val rules: StateFlow<List<AutoDownloadRule>> = _rules.asStateFlow()
    private val _library = MutableStateFlow<List<LocalLibraryItem>>(emptyList())
    override val library: StateFlow<List<LocalLibraryItem>> = _library.asStateFlow()

    private val _libraryFolders = MutableStateFlow<List<String>>(emptyList())
    override val libraryFolders: StateFlow<List<String>> = _libraryFolders.asStateFlow()

    /** Videos the user removed from the library (kept on disk): a rescan or the watcher does not bring them back. */
    private val ignored = MutableStateFlow<Set<String>>(emptySet())
    private val pendingWatch = ConcurrentHashMap<String, Long>()
    private val watcher: LibraryWatcher? = runCatching { LibraryWatcher(::onWatchedVideo) }.getOrNull()

    init {
        scope.launch {
            val stored = store.downloads()
            targets.putAll(store.targets())
            // A download that was running when the app closed goes on; REMOVING items never came back.
            records.value = stored.filter { it.item.state != DownloadState.REMOVING }.map { rec ->
                if (rec.item.state == DownloadState.DOWNLOADING) rec.copy(item = rec.item.copy(state = DownloadState.QUEUED)) else rec
            }.associateBy { it.item.id }
            hidden.value = store.hiddenHistory().toSet()
            _rules.value = store.rules()
            _library.value = store.library()
            _libraryFolders.value = store.libraryFolders()
            ignored.value = store.ignoredLibraryFiles().toSet()
            loaded.complete(Unit)
            _libraryFolders.value.forEach { watchAndRescan(it) }
            runCatching { verifyIntegrity() }
            pump()
        }
        scope.launch { evictWatchedLoop() }
        scope.launch { settingsLoop() }
        if (autoCheckEveryMs > 0) scope.launch { autoCheckLoop() }
    }

    /** The folder new downloads go to. */
    internal fun currentFolder(): File = settings().directoryTreeUri?.takeIf { it.isNotBlank() }?.let(::fileOf) ?: defaultFolder

    // ------------------------------------------------------------------ queue

    /** Starts queued downloads while fewer than `maxConcurrent` run (oldest first). */
    private suspend fun pump() {
        loaded.await()
        queueLock.withLock {
            val max = settings().sanitized().maxConcurrent
            val queued = records.value.values.map { it.item }
                .filter { it.state == DownloadState.QUEUED && !running.containsKey(it.id) }
                .sortedBy { it.createdAtMs }
            for (item in queued) {
                if (running.size >= max) break
                val rec = records.value[item.id] ?: continue
                // Register in `running` before starting so a fast-failing job cannot run `finally` before insertion.
                val job = scope.launch(start = CoroutineStart.LAZY) { run(rec) }
                running[item.id] = job
                job.invokeOnCompletion { running.remove(item.id, job) }
                job.start()
            }
        }
    }

    private suspend fun run(rec: StoredDownload) {
        val id = rec.item.id
        try {
            update(id, persist = true) { it.copy(state = DownloadState.DOWNLOADING, error = null) }
            val base = File(targets[id] ?: baseFor(rec.item).path)
            val requestUrl = rec.debrid?.let { ref -> renewDebrid(id, ref) } ?: rec.requestUrl
            val job = DesktopDownloadEngine.Job(requestUrl, rec.stream.headers, rec.item.container, base)
            var lastPersist = 0L
            // A "file" that is an HLS playlist is downloaded as HLS: the row says so from then on (and a resume goes on as HLS).
            val onHls = { update(id, persist = true) { it.copy(container = Container.HLS) } }
            val file = engine.download(job, onHls) { p ->
                val now = clock()
                val persist = now - lastPersist >= PERSIST_MS
                if (persist) lastPersist = now
                update(id, persist = persist) {
                    if (it.state != DownloadState.DOWNLOADING) it else it.copy(
                        progress = p.fraction?.coerceIn(0.0, 1.0) ?: it.progress,
                        downloadedBytes = p.bytes,
                        totalBytes = p.total ?: it.totalBytes,
                    )
                }
            }
            val written = file.length()
            update(id, persist = true) {
                it.copy(
                    state = DownloadState.COMPLETED,
                    progress = 1.0,
                    downloadedBytes = written,
                    totalBytes = written,
                    localUri = file.absolutePath,
                    error = null,
                    completedAtMs = clock(),
                )
            }
            // 1.1 review #11: a finished debrid download keeps no CDN link (a retry asks the service for a new one)
            if (rec.debrid != null) {
                mutex.withLock {
                    records.update { m -> m[id]?.let { r -> m + (id to r.copy(requestUrl = "", stream = r.stream.copy(url = ""))) } ?: m }
                    persist()
                }
            }
            records.value[id]?.item?.let { notifier?.notify(DesktopNoticeTexts.downloaded(it.title, it.episode), it.sourceLabel) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 1.1 review #7 / #11: a debrid code is stored bare (the Downloads page translates it) and a debrid error keeps no URL
            update(id, persist = true) { it.copy(state = DownloadState.FAILED, error = storedDownloadError(e.message, rec.debrid != null, ERR_FAILED)) }
            records.value[id]?.item?.let { notifier?.notify(DesktopNoticeTexts.downloadFailed(it.title, it.episode), it.sourceLabel) }
        } finally {
            val current = currentCoroutineContext()[Job]
            if (current != null) running.remove(id, current) else running.remove(id)
            scope.launch { pump() }
        }
    }

    private fun baseFor(item: DownloadItem): File = DownloadNames.episodeBase(currentFolder(), item.title, item.episode)

    override suspend fun enqueue(request: DownloadRequest): Result<DownloadItem> = runCatchingIo {
        loaded.await()
        val stream = request.stream
        val ref = request.media.ref
        if (stream.container == Container.DASH) throw AppException(AppError.Extension(stream.extensionId, ERR_DASH))
        val s = settings().sanitized()
        if (DownloadLogic.overLimit(storageUsedBytes(), s.storageLimitGib)) throw AppException(AppError.Extension(stream.extensionId, ERR_LIMIT))
        val id = DownloadLogic.itemId(ref, request.episode)
        records.value[id]?.let { existing ->
            if (existing.item.state != DownloadState.FAILED && existing.item.state != DownloadState.BROKEN) return@runCatchingIo existing.item
        }
        val requestUrl = if (stream.container == Container.HLS) engine.resolveHls(stream.url, stream.headers, s.autoProfile) else stream.url
        val item = DownloadItem(
            id = id,
            ref = ref,
            title = request.media.title,
            poster = request.media.cover,
            episode = request.episode,
            extId = stream.extensionId,
            sourceLabel = request.sourceLabel,
            container = if (stream.container == Container.HLS) Container.HLS else Container.MP4,
            state = DownloadState.QUEUED,
            createdAtMs = clock(),
        )
        // 1.1 review #3: the stored reference keeps the info-hash only, never the source's full magnet
        val rec = StoredDownload(item = item, stream = stream, requestUrl = requestUrl, media = request.media, debrid = request.debrid?.copy(magnet = null))
        mutex.withLock {
            targets[id] = baseFor(item).path
            records.update { it + (id to rec) }
            persist()
        }
        hidden.update { it - id }
        pump()
        item
    }

    /**
     * 1.1 (F7): a fresh link for a debrid download (debrid links expire; the partial file stays and the engine goes on from its size
     * with a Range request). Stored so the record shows the link in use; a failure fails the download with the service's reason.
     */
    private suspend fun renewDebrid(id: String, ref: app.nijiyome.model.torrent.DebridFileRef): String {
        val url = when (val r = renewDebridLink(ref, debrid())) {
            is DebridRenewal.Ok -> r.url
            is DebridRenewal.Failed -> throw AppException(AppError.Unknown(r.code))
        }
        mutex.withLock {
            records.update { m -> m[id]?.let { r -> m + (id to r.copy(requestUrl = url, stream = r.stream.copy(url = url))) } ?: m }
            persist()
        }
        return url
    }

    override suspend fun enqueueSeason(media: Media, episodes: List<Int>, preferredServer: String?, preferredAudioLang: String?): Result<List<DownloadItem>> =
        runCatchingIo {
            val existing = records.value.values.map { it.item }.filter { DownloadLogic.sameRef(it.ref, media.ref) }.associateBy { it.episode }
            val todo = DownloadLogic.missingEpisodes(episodes, existing)
            val queued = ArrayList<DownloadItem>()
            var lastError: Throwable? = null
            for (ep in todo) {
                val r = resolveStream(media, ep, preferredServer, preferredAudioLang, settings().autoProfile)
                if (r == null) {
                    lastError = AppException(AppError.Extension("", ERR_NO_SOURCE))
                    continue
                }
                enqueue(DownloadRequest(media, ep, r.first, r.second)).onSuccess { queued += it }.onFailure { lastError = it }
            }
            if (queued.isEmpty() && todo.isNotEmpty()) throw lastError ?: AppException(AppError.Extension("", ERR_NO_SOURCE))
            queued
        }

    private suspend fun resolveStream(media: Media, episode: Int, server: String?, lang: String?, profile: DownloadProfile): Pair<StreamSource, String>? =
        resolveDownloadStream(extensions, sniffer, media, episode, server, lang, profile)

    override suspend fun pause(id: String) {
        update(id, persist = true) { if (it.state == DownloadState.COMPLETED) it else it.copy(state = DownloadState.PAUSED) }
        running[id]?.cancelAndJoin()
        // the worker's last progress update may have raced the pause
        update(id, persist = true) { if (it.state == DownloadState.DOWNLOADING) it.copy(state = DownloadState.PAUSED) else it }
    }

    override suspend fun resume(id: String) {
        if (records.value[id] == null) return
        update(id, persist = true) { if (it.state == DownloadState.PAUSED || it.state == DownloadState.FAILED) it.copy(state = DownloadState.QUEUED, error = null) else it }
        pump()
    }

    override suspend fun remove(id: String) {
        val rec = records.value[id] ?: return
        update(id, persist = false) { it.copy(state = DownloadState.REMOVING) }
        running[id]?.cancelAndJoin()
        withContext(Dispatchers.IO) {
            rec.item.localUri?.let { deleteFile(it) }
            targets[id]?.let { engine.discardPartials(File(it)) }
        }
        mutex.withLock {
            records.update { it - id }
            targets.remove(id)
            persist()
        }
    }

    override suspend fun retry(id: String) {
        val rec = records.value[id] ?: return
        running[id]?.cancelAndJoin()
        val media = rec.media
        withContext(Dispatchers.IO) {
            rec.item.localUri?.let { deleteFile(it) }
            targets[id]?.let { engine.discardPartials(File(it)) }
        }
        // URLs of extensions expire: re-resolve the same server when the title is known, else reuse the stored stream.
        // 1.1 (F7): a debrid download keeps its file; run() asks the service for a fresh link.
        val fresh = if (rec.debrid != null) null else media?.let { resolveStream(it, rec.item.episode, rec.item.sourceLabel, settings().preferredAudioLang, settings().autoProfile) }
        val stream = fresh?.first ?: rec.stream
        val requestUrl = runCatchingIo {
            if (stream.container == Container.HLS) engine.resolveHls(stream.url, stream.headers, settings().autoProfile) else stream.url
        }.getOrDefault(rec.requestUrl)
        val next = rec.copy(
            stream = stream,
            requestUrl = requestUrl,
            item = rec.item.copy(state = DownloadState.QUEUED, progress = 0.0, downloadedBytes = 0, localUri = null, error = null, completedAtMs = null),
        )
        mutex.withLock {
            records.update { it + (id to next) }
            persist()
        }
        pump()
    }

    override fun history(limit: Int): Flow<List<DownloadItem>> =
        combine(items, hidden) { list, h -> DownloadLogic.history(list.filter { it.id !in h }, limit) }

    override suspend fun clearHistory() {
        val failed = records.value.values.filter { it.item.state == DownloadState.FAILED || it.item.state == DownloadState.BROKEN }.map { it.item.id }
        withContext(Dispatchers.IO) { failed.forEach { id -> targets[id]?.let { engine.discardPartials(File(it)) } } }
        mutex.withLock {
            records.update { m -> m - failed.toSet() }
            failed.forEach { targets.remove(it) }
            persist()
        }
        val done = records.value.values.filter { it.item.state == DownloadState.COMPLETED }.map { it.item.id }
        hidden.value = done.toSet()
        withContext(Dispatchers.IO) { store.saveHiddenHistory(done) }
    }

    override suspend fun localUriFor(ref: MediaRef, episode: Int): String? = withContext(Dispatchers.IO) {
        loaded.await()
        val done = records.value.values.map { it.item }
            .firstOrNull { it.state == DownloadState.COMPLETED && it.episode == episode && DownloadLogic.sameRef(it.ref, ref) && it.localUri != null }
        done?.localUri?.takeIf { stat(it).first }
            ?: _library.value.firstOrNull { it.ref != null && DownloadLogic.sameRef(it.ref!!, ref) && it.episode == episode }?.uri?.takeIf { stat(it).first }
    }

    override fun downloadedEpisodes(ref: MediaRef): Flow<Map<Int, DownloadItem>> = items.map { list ->
        list.filter { it.state == DownloadState.COMPLETED && it.localUri != null && DownloadLogic.sameRef(it.ref, ref) }.associateBy { it.episode }
    }.distinctUntilChanged()

    // ------------------------------------------------------------------ auto-download (same rules as Android)

    override suspend fun setRule(rule: AutoDownloadRule?) {
        if (rule == null) return
        val next = _rules.value.filterNot { DownloadLogic.sameRef(it.ref, rule.ref) } + rule
        _rules.value = next
        withContext(Dispatchers.IO) { store.saveRules(next) }
    }

    override suspend fun preview(ref: MediaRef): AutoDownloadPreview? = withContext(Dispatchers.IO) {
        val rule = _rules.value.firstOrNull { DownloadLogic.sameRef(it.ref, ref) } ?: return@withContext null
        val have = records.value.values.map { it.item }.filter { DownloadLogic.sameRef(it.ref, ref) && it.state != DownloadState.FAILED }.map { it.episode }.toSet()
        val eps = runCatching { metadata.episodes(ref) }.getOrDefault(emptyList())
        val next = DownloadLogic.newEpisodes(rule, eps, have, LocalDate.now())
        val sizes = records.value.values.map { it.item }.filter { DownloadLogic.sameRef(it.ref, ref) && it.state == DownloadState.COMPLETED }.mapNotNull { it.totalBytes }
        AutoDownloadPreview(rule, next, if (sizes.isEmpty() || next.isEmpty()) null else sizes.average().toLong() * next.size)
    }

    override suspend fun checkNow(): Result<Int> = runCatchingIo {
        val s = settings().sanitized()
        if (DownloadLogic.overLimit(storageUsedBytes(), s.storageLimitGib)) throw AppException(AppError.Extension("", ERR_LIMIT))
        val watching = lists?.let { l ->
            runCatching { l.entries(null).first() }.getOrNull()
                ?.filter { it.status == ListStatus.WATCHING || it.status == ListStatus.REPEATING }
        }
        var count = 0
        for (rule in _rules.value.filter { it.enabled }) {
            if (!watching.isNullOrEmpty() && watching.none { DownloadLogic.sameRef(it.ref, rule.ref) }) continue
            val have = records.value.values.map { it.item }.filter { DownloadLogic.sameRef(it.ref, rule.ref) && it.state != DownloadState.FAILED }.map { it.episode }.toSet()
            val eps = runCatching { metadata.episodes(rule.ref) }.getOrDefault(emptyList())
            val next = DownloadLogic.newEpisodes(rule, eps, have, LocalDate.now())
            var last = rule.lastEpisode
            if (next.isNotEmpty()) {
                val media = runCatching { metadata.media(MediaKind.ANIME, rule.ref) }.getOrNull()
                if (media != null) {
                    for (ep in next) {
                        val r = resolveStream(media, ep, s.preferredServer, s.preferredAudioLang, rule.profile) ?: continue
                        enqueue(DownloadRequest(media, ep, r.first, r.second)).onSuccess {
                            count++
                            last = maxOf(last, ep)
                        }
                    }
                }
            }
            setRule(rule.copy(lastEpisode = last, lastCheckedMs = clock()))
        }
        count
    }

    private suspend fun autoCheckLoop() {
        loaded.await()
        // A first pass soon after start (Tatakai Next: 90 s), not only after the first period.
        delay(firstAutoCheckMs.coerceAtMost(autoCheckEveryMs))
        while (true) {
            if (settings().autoCheck && _rules.value.any { it.enabled }) runCatching { checkNow() }
            delay(autoCheckEveryMs)
        }
    }

    /** Settings › Descargas: a higher `maxConcurrent` starts waiting downloads at once. */
    private suspend fun settingsLoop() {
        var last = settings().sanitized().maxConcurrent
        while (true) {
            delay(SETTINGS_POLL_MS)
            val now = settings().sanitized().maxConcurrent
            if (now != last) {
                last = now
                pump()
            }
        }
    }

    // ------------------------------------------------------------------ local library

    override suspend fun importFiles(uris: List<String>): Result<List<LocalLibraryItem>> = runCatchingIo {
        loaded.await()
        val added = uris.mapNotNull { u ->
            val f = fileOf(u)
            if (!f.isFile) null else proposeMatch(libraryItem(f))
        }
        // Importing a removed video by hand brings it back (and the watcher follows it again).
        val back = added.map { it.uri }.filter { it in ignored.value }
        if (back.isNotEmpty()) saveIgnored(ignored.value - back.toSet())
        addToLibrary(added)
    }

    override suspend fun scanFolder(treeUri: String): Result<List<LocalLibraryItem>> = runCatchingIo {
        loaded.await()
        val root = fileOf(treeUri)
        if (!root.isDirectory) throw AppException(AppError.Unknown(ERR_FOLDER))
        val path = root.absolutePath
        if (path !in _libraryFolders.value) {
            _libraryFolders.update { it + path }
            store.saveLibraryFolders(_libraryFolders.value)
        }
        watcher?.watch(root)
        scanInto(root)
    }

    private suspend fun scanInto(root: File): List<LocalLibraryItem> {
        val known = _library.value.map { it.uri }.toSet() + downloadedFiles() + ignored.value
        val found = LibraryScan.walkVideos(root).filter { it.absolutePath !in known }.map { proposeMatch(libraryItem(it)) }
        return addToLibrary(found)
    }

    private fun watchAndRescan(folder: String) {
        val root = File(folder)
        watcher?.watch(root)
        scope.launch { runCatching { scanInto(root) } }
    }

    /** A video appeared in a watched folder: import it once its size stopped changing (a copy in progress keeps growing). */
    private fun onWatchedVideo(file: File) {
        val path = file.absolutePath
        if (pendingWatch.put(path, clock()) != null) return
        scope.launch {
            try {
                var size = -1L
                while (true) {
                    delay(watchSettleMs)
                    val now = if (file.isFile) file.length() else -1L
                    if (now < 0) return@launch
                    if (now == size) break
                    size = now
                }
                if (path in _library.value.map { it.uri } || path in downloadedFiles() || path in ignored.value) return@launch
                addToLibrary(listOf(proposeMatch(libraryItem(file))))
            } finally {
                pendingWatch.remove(path)
            }
        }
    }

    /** The finished files of the download queue (they are listed as downloads, not as library items). */
    private fun downloadedFiles(): Set<String> = records.value.values.mapNotNull { it.item.localUri }.toSet()

    private fun libraryItem(file: File): LocalLibraryItem {
        val path = file.absolutePath
        val guess = DownloadLogic.guessLocal(file.name)
        return LocalLibraryItem(
            id = DownloadLogic.localId(path),
            uri = path,
            fileName = file.name,
            sizeBytes = file.length(),
            title = guess.title,
            episode = guess.episode,
            addedAtMs = clock(),
        )
    }

    /** First metadata hit of the parsed title (the user corrects it with `matchLocal`). */
    private suspend fun proposeMatch(item: LocalLibraryItem): LocalLibraryItem {
        val title = item.title ?: return item
        val hit = runCatching { metadata.search(MediaKind.ANIME, title).items.firstOrNull() }.getOrNull() ?: return item
        return item.copy(ref = hit.ref, title = hit.title, poster = hit.cover)
    }

    private suspend fun addToLibrary(items: List<LocalLibraryItem>): List<LocalLibraryItem> = mutex.withLock {
        val known = _library.value.map { it.uri }.toSet()
        val fresh = items.filter { it.uri !in known }.distinctBy { it.uri }
        if (fresh.isEmpty()) return@withLock fresh
        val next = _library.value + fresh
        _library.value = next
        withContext(Dispatchers.IO) { store.saveLibrary(next) }
        fresh
    }

    override suspend fun matchLocal(id: String, media: Media?, episode: Int?) {
        val next = _library.value.map {
            if (it.id != id) it else it.copy(ref = media?.ref, title = media?.title ?: it.title, poster = media?.cover, episode = episode)
        }
        _library.value = next
        withContext(Dispatchers.IO) { store.saveLibrary(next) }
    }

    override suspend fun removeLocal(id: String, deleteFile: Boolean) {
        val item = _library.value.firstOrNull { it.id == id } ?: return
        if (deleteFile) withContext(Dispatchers.IO) { deleteFile(item.uri) }
        // "Quitar entrada" keeps the file: remember it so the next start's rescan does not add it again.
        if (!deleteFile) saveIgnored(ignored.value + item.uri)
        val next = _library.value.filterNot { it.id == id }
        _library.value = next
        withContext(Dispatchers.IO) { store.saveLibrary(next) }
    }

    override suspend fun forgetLibraryFolder(folder: String) {
        loaded.await()
        val path = fileOf(folder).absolutePath
        val next = _libraryFolders.value.filterNot { it == path || it == folder }
        if (next.size == _libraryFolders.value.size) return
        _libraryFolders.value = next
        withContext(Dispatchers.IO) {
            store.saveLibraryFolders(next)
            watcher?.unwatch(File(path))
        }
    }

    private suspend fun saveIgnored(next: Set<String>) {
        ignored.value = next
        withContext(Dispatchers.IO) { store.saveIgnoredLibraryFiles(next.sorted()) }
    }

    override suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        // The download folder may hold other things (the torrents' folder is its parent by default): count only what this queue wrote.
        val done = records.value.values.mapNotNull { it.item.localUri }.sumOf { stat(it).second }
        val partial = targets.values.sumOf { base -> DownloadLogic.folderBytes(DownloadNames.partsDir(File(base))) }
        done + partial
    }

    override suspend fun verifyIntegrity(): List<DownloadItem> = withContext(Dispatchers.IO) {
        val broken = records.value.values.map { it.item }.filter { item ->
            val uri = item.localUri
            val (exists, length) = if (uri == null) false to 0L else stat(uri)
            DownloadLogic.isBroken(item, exists, length)
        }
        broken.forEach { b -> update(b.id, persist = false) { it.copy(state = DownloadState.BROKEN, error = ERR_BROKEN) } }
        if (broken.isNotEmpty()) mutex.withLock { persist() }
        broken.mapNotNull { records.value[it.id]?.item }
    }

    /** `autoEvictWatched`: deletes completed episodes once their history row says watched. */
    private suspend fun evictWatchedLoop() {
        combine(history.history(EVICT_HISTORY).catch { emit(emptyList()) }, items) { h, list -> DownloadLogic.watchedToEvict(list, h) }
            .distinctUntilChanged()
            .collect { victims -> if (settings().autoEvictWatched) victims.forEach { remove(it.id) } }
    }

    // ------------------------------------------------------------------ reset / exit

    /** "Restablecer la app": removes every download and its files, the library (not the user's files) and the rules. */
    override suspend fun wipe() {
        running.values.toList().forEach { it.cancelAndJoin() }
        val recs = records.value.values.toList()
        records.value = emptyMap()
        hidden.value = emptySet()
        _rules.value = emptyList()
        _library.value = emptyList()
        _libraryFolders.value = emptyList()
        ignored.value = emptySet()
        withContext(Dispatchers.IO) {
            recs.forEach { rec ->
                rec.item.localUri?.let { deleteFile(it) }
                targets[rec.item.id]?.let { engine.discardPartials(File(it)) }
            }
            targets.clear()
            storeDir.deleteRecursively()
        }
    }

    /** App exit: stops the running downloads where they are (they go on at the next start) and the folder watcher. */
    override fun close() {
        runCatching { watcher?.close() }
        val snapshot = records.value.values.toList()
        runCatching { store.saveDownloads(snapshot) }
        scope.cancel()
    }

    // ------------------------------------------------------------------ helpers

    private fun update(id: String, persist: Boolean, transform: (DownloadItem) -> DownloadItem) {
        records.update { m -> m[id]?.let { rec -> m + (id to rec.copy(item = transform(rec.item))) } ?: m }
        if (persist) scope.launch { mutex.withLock { persist() } }
    }

    private fun persist() {
        runCatching { store.saveDownloads(records.value.values.toList()) }
        runCatching { store.saveTargets(targets.toMap()) }
    }

    private fun stat(uri: String): Pair<Boolean, Long> {
        val f = fileOf(uri)
        return f.isFile to (if (f.isFile) f.length() else 0L)
    }

    private fun deleteFile(uri: String) {
        runCatching { fileOf(uri).delete() }
    }

    private suspend fun <T> runCatchingIo(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val PERSIST_MS = 5_000L
        private const val WATCH_SETTLE_MS = 2_000L
        private const val AUTO_CHECK_MS = 30 * 60_000L
        private const val FIRST_AUTO_CHECK_MS = 90_000L
        private const val SETTINGS_POLL_MS = 2_000L
        private const val EVICT_HISTORY = 500

        // The error codes of the Android repository (localised by :feature:downloads, downloadErrorText).
        const val ERR_DASH = "dash_not_supported"
        const val ERR_LIMIT = "storage_limit"
        const val ERR_NO_SOURCE = "no_source"
        const val ERR_FAILED = "failed"
        const val ERR_BROKEN = "broken"
        const val ERR_FOLDER = "folder_unavailable"
    }
}

/** A path or a `file:` URI as a file. */
internal fun fileOf(pathOrUri: String): File = if (pathOrUri.startsWith("file:")) File(URI(pathOrUri)) else File(pathOrUri)
