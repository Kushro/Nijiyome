// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.Container
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.Media
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.StreamSource
import app.nijiyome.model.torrent.AfterWatch
import app.nijiyome.model.torrent.EpisodeAlignment
import app.nijiyome.model.torrent.TorrentSessionEntry
import app.nijiyome.model.torrent.ProtocolStatus
import app.nijiyome.model.torrent.StorageLocation
import app.nijiyome.model.torrent.TORRENT_METADATA_TIMEOUT_SECS
import app.nijiyome.model.torrent.TORRENT_PREVIEW_TIMEOUT_DETAIL
import app.nijiyome.model.torrent.TorrentPreviewProgress
import app.nijiyome.model.torrent.TorrentPreview
import app.nijiyome.model.torrent.TorrentPreviewFile
import app.nijiyome.model.torrent.TorrentEvent
import app.nijiyome.model.torrent.TorrentFile
import app.nijiyome.model.torrent.TorrentId
import app.nijiyome.model.torrent.TorrentLabel
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentPeer
import app.nijiyome.model.torrent.TorrentPlayback
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentServiceStatus
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.model.torrent.TorrentState
import app.nijiyome.model.torrent.TorrentStats
import app.nijiyome.model.torrent.TorrentStorage
import app.nijiyome.model.torrent.TorrentTracker
import app.nijiyome.release.CandidateFile
import app.nijiyome.release.isVideoPath
import app.nijiyome.release.parseRelease
import app.nijiyome.release.pickEpisodeFile
import app.nijiyome.torrent.engine.EngineFile
import app.nijiyome.torrent.engine.LibtorrentSession
import app.nijiyome.torrent.engine.engineFiles
import app.nijiyome.torrent.engine.episodeOf
import app.nijiyome.torrent.engine.fileOnDisk
import app.nijiyome.torrent.engine.isPausedFlag
import app.nijiyome.torrent.engine.isSelected
import app.nijiyome.torrent.engine.readTrackers
import app.nijiyome.torrent.engine.toModel
import app.nijiyome.torrent.engine.toModelState
import app.nijiyome.torrent.engine.wantedProgress
import app.nijiyome.torrent.debrid.DebridRepositoryImpl
import app.nijiyome.torrent.policy.AfterWatchAction
import app.nijiyome.torrent.policy.AlignFile
import app.nijiyome.torrent.policy.CacheItem
import app.nijiyome.torrent.policy.PRELOAD_AT
import app.nijiyome.torrent.policy.alignedEpisodes
import app.nijiyome.torrent.policy.alignedIndex
import app.nijiyome.torrent.policy.applySchedule
import app.nijiyome.torrent.policy.cacheEvictions
import app.nijiyome.torrent.policy.preloadTarget
import app.nijiyome.torrent.policy.DeviceConditions
import app.nijiyome.torrent.policy.RunFacts
import app.nijiyome.torrent.policy.afterWatchAction
import app.nijiyome.torrent.policy.closeLeftoverStreams
import app.nijiyome.torrent.policy.countsAsStreaming
import app.nijiyome.torrent.policy.backgroundAllowed
import app.nijiyome.torrent.policy.deadlineWindow
import app.nijiyome.torrent.policy.downloadedRanges
import app.nijiyome.torrent.policy.shouldRun
import app.nijiyome.torrent.policy.uploadLimitBytes
import app.nijiyome.torrent.store.LabelStore
import app.nijiyome.torrent.store.SessionHistoryStore
import app.nijiyome.torrent.store.StoredTorrent
import app.nijiyome.torrent.stream.LoopbackStreamServer
import app.nijiyome.torrent.stream.StreamHandle
import app.nijiyome.torrent.stream.StreamInfo
import app.nijiyome.torrent.stream.StreamProvider
import app.nijiyome.torrent.system.ServiceSnapshot
import app.nijiyome.torrent.system.TorrentHost
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.FileCompletedAlert
import org.libtorrent4j.alerts.TorrentAlert

/**
 * [TorrentRepository] on libtorrent4j 2.1.0-39 (docs/redesign/11, 12 §3.8 / §4 f): on-demand session, `labels.json` + resume data
 * in `filesDir/torrents`, downloads in `noBackupFilesDir/torrent-downloads` (see [downloadDir]), loopback HTTP streaming with sequential download and
 * piece deadlines, after-watch policy, no seeding after completion unless `seed`, network / battery / storage policies and the
 * foreground TorrentService. Single instance (Koin `torrentModule`). Shared by Android and desktop: what differs (folders, monitors,
 * SAF / MediaStore, notifications, the service) is the [TorrentHost]; on desktop the folders are the ones of `DesktopTorrentHost`.
 *
 * Error contract: no metadata within [TORRENT_METADATA_TIMEOUT_SECS] -> `AppError.Offline`; no matching file ->
 * `AppError.Unknown("torrent:no_file:<id>")`; plus `torrent:invalid_source`, `torrent:no_space:<bytes>`, `torrent:background_off`,
 * `torrent:missing:<id>` (not in the session any more), `torrent:moved:<id>` (moved to the SAF folder), `torrent:incomplete`,
 * `torrent:export_unsupported`.
 */
internal class TorrentRepositoryImpl(
    /** Platform side (Android: Context, SAF, notifications, the foreground service; desktop: plain folders). */
    private val host: TorrentHost,
    private val settingsRepository: SettingsRepository,
    /** parity wave A (worker q, sources.debrid.*): a torrent cached on a linked debrid service plays as HTTPS, without libtorrent. */
    private val debrid: DebridRepositoryImpl? = null,
) : TorrentRepository, StreamProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateDir = host.stateDir
    /** Android: internal app storage (see AndroidTorrentHost); desktop: the configured torrent folder. */
    private val downloadDir: File
        get() = host.downloadDir

    /** Android: where downloads went before 0.3.5. Torrents with data there keep it (their resume data points there); empty ones move. */
    private val legacyDownloadDir: File?
        get() = host.legacyDownloadDir

    private val labels = LabelStore(File(stateDir, "labels.json"))

    init {
        // 1.1 (F7): a pack played through debrid honours the manual episode alignment of the same torrent
        debrid?.alignmentOf = { id -> labels[id]?.alignment }
    }
    private val history = SessionHistoryStore(File(stateDir, "history.json"))
    @Volatile private var scheduleActive = false
    private val session = LibtorrentSession(stateDir, ::onAlert, proxy = { host.proxy() })
    private val server = LoopbackStreamServer(this)
    private val engineMutex = Mutex()
    private val tickLock = Any()
    private val pieceSignal = Object()

    private val runtimes = ConcurrentHashMap<String, Runtime>()
    /** Metadata fetched by [preview] (info-hash -> bencoded `.torrent`), used by the add that follows. */
    private val previewCache = ConcurrentHashMap<String, ByteArray>()
    private val finishing = ConcurrentHashMap.newKeySet<String>()
    private val uiCollectors = AtomicInteger()
    @Volatile private var pausedBySystem = false
    @Volatile private var idleSinceMs = 0L
    @Volatile private var tickerJob: Job? = null
    @Volatile private var tickCount = 0L

    /** Set by [TorrentService] (onCreate / onDestroy). */
    @Volatile internal var serviceRunning = false

    private val _events = MutableSharedFlow<TorrentEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<TorrentEvent> = _events.asSharedFlow()

    private val _status = MutableStateFlow(TorrentServiceStatus())
    override val status: StateFlow<TorrentServiceStatus> = _status.asStateFlow()

    private val _snapshot = MutableStateFlow(ServiceSnapshot())

    /** What the foreground service renders (internal to :core:torrent). */
    internal val serviceSnapshot: StateFlow<ServiceSnapshot> = _snapshot.asStateFlow()

    private val live = MutableStateFlow<Map<String, LiveInfo>>(emptyMap())

    override val library: StateFlow<List<TorrentLibraryItem>> =
        combine(labels.torrents, live) { stored, liveInfo -> buildLibrary(stored, liveInfo) }
            .stateIn(scope, SharingStarted.Eagerly, buildLibrary(labels.torrents.value, emptyMap()))

    /** The sanitized settings with the bandwidth schedule applied for the current minute (torrent.bandwidth-schedule). */
    private val settings: TorrentSettings get() = applySchedule(settingsRepository.settings.value.torrent.sanitized(), minuteOfDay())

    private fun minuteOfDay(): Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }

    init {
        host.registerMonitors()
        refreshIdleStatus()
        scope.launch {
            settingsRepository.settings.map { it.torrent.sanitized() }.distinctUntilChanged().collect { onSettingsChanged() }
        }
        // 1.1: Settings › Red changed the SOCKS5 proxy (desktop; Android's never changes, so this never fires there)
        scope.launch { host.proxyChanges.collect { onSettingsChanged() } }
        scope.launch {
            combine(host.network, host.batteryLow) { net, low -> net to low }.distinctUntilChanged().collect { onConditionsChanged() }
        }
        scope.launch { labels.torrents.collect { if (!session.isRunning) refreshIdleStatus() } }
        scope.launch {
            // desktop: with background downloads on, the engine starts with the app to keep saved downloads going
            delay(STARTUP_DELAY_MS)
            if (hasBackgroundWork() && backgroundAllowedNow()) {
                runCatching { ensureStarted() }
                tickSafely()
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // TorrentRepository
    // ------------------------------------------------------------------------------------------------------------------------

    override suspend fun play(source: String, media: Media, episode: Int, fileIndex: Int?): Result<TorrentPlayback> =
        withContext(Dispatchers.IO) {
            var playingId: String? = null
            try {
                val (id, magnet) = resolveSource(source)
                playingId = id
                val existing = labels[id]
                if (existing?.movedUri != null) throw AppException(AppError.Unknown("torrent:moved:$id"))
                // parity wave A (sources.debrid.*): nothing local yet and a linked debrid service has it -> direct HTTPS stream
                val local = (if (session.isRunning) session.find(id) else null) != null || (existing?.progress ?: 0.0) > 0.0
                if (fileIndex == null && !local) {
                    debridPlayback(id, magnet, media, episode)?.let { return@withContext Result.success(it) }
                }
                val s = settings
                val rt = runtime(id)
                rt.endJob?.cancel()
                rt.ended = false
                rt.playing = true
                val label = labelFor(media, episode, existing?.label)
                labels.upsert(
                    (existing ?: StoredTorrent(id = id, magnet = magnet, addedUnix = nowUnix())).copy(
                        label = label,
                        persist = (existing?.persist ?: false) || s.afterWatch == AfterWatch.KEEP,
                        userPaused = false,
                    ),
                )
                val handle = addOrGet(id, magnet)
                startServiceIfNeeded()
                val info = awaitMetadata(handle)
                if (info == null) {
                    rt.playing = false
                    discardIfTransient(id)
                    throw AppException(AppError.Offline)
                }
                onMetadataKnown(id, handle, info)
                val files = info.engineFiles()
                val index = fileIndex?.takeIf { i -> files.any { it.index == i } }
                    ?: alignedFor(labels[id]?.alignment, files, episode)
                    ?: pickFile(files, episode, titlesOf(media))
                    ?: throw AppException(AppError.Unknown("torrent:no_file:$id"))
                val file = files.first { it.index == index }
                val have = handle.fileProgress(TorrentHandle.PIECE_GRANULARITY).getOrElse(index) { 0L }
                val needed = file.size - have
                if (needed > 0 && host.allocatableBytes(downloadDir) < needed) {
                    throw AppException(AppError.Unknown("torrent:no_space:$needed"))
                }
                selectFile(id, handle, index, sequential = true)
                prefetchEdges(handle, file)
                tickSafely()
                val tags = runCatching { parseRelease(file.name) }.getOrNull()
                val stream = StreamSource(
                    url = server.url(id, index, file.name),
                    headers = emptyMap(),
                    container = Container.PROGRESSIVE,
                    quality = tags?.resolution?.label()?.takeIf { it.isNotEmpty() },
                    provider = tags?.group ?: PROVIDER_NAME,
                    extensionId = PROVIDER_ID,
                )
                val batch = files.count { isVideoPath(it.path) } > 1
                recordHistory(id, info.name(), labels[id]?.label, episode, origin = labels[id]?.origin ?: "play")
                Result.success(
                    TorrentPlayback(
                        args = PlayerArgs(media = media.ref, title = media.title, episode = episode, torrentId = id, fileIndex = index),
                        stream = stream,
                        torrentId = id,
                        fileIndex = index,
                        fileName = file.name,
                        batch = batch,
                    ),
                )
            } catch (e: CancellationException) {
                playingId?.let { runtimes[it]?.playing = false }
                throw e
            } catch (e: Exception) {
                playingId?.let { id -> runtimes[id]?.let { if (it.openStreams() == 0) it.playing = false } }
                tickSafely()
                Result.failure(e.asAppException())
            }
        }

    override suspend fun playbackSuspended(torrentId: TorrentId) {
        // 0.4.0 (Link "PC" from the player): like playbackEnded (not playing, the tick pauses it) but nothing is removed: the
        // player may switch back to the phone and must find what it already downloaded (it restarted from 0 %).
        val rt = runtimes[torrentId] ?: return
        rt.endJob?.cancel()
        rt.playing = false
        rt.ended = true
        tickSafely()
        rt.endJob = scope.launch {
            delay(BATCH_GRACE_MS)
            closeLeftoverStreams(rt.ended, rt.handles) { it.close() }
            val handle = if (session.isRunning) session.find(torrentId) else null
            runCatching {
                handle?.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                handle?.clearPieceDeadlines()
            }
            handle?.let(session::requestResume)
            tickSafely()
        }
    }

    override suspend fun playbackEnded(torrentId: TorrentId, completed: Boolean) {
        val rt = runtimes[torrentId] ?: return
        rt.endJob?.cancel()
        // BUG-021: the grace only keeps the torrent around (the next episode of a pack reuses it); it no longer counts as playing, so
        // the run policy pauses it at once unless it is a kept / background download (it went on at full speed for 20 s and then
        // deleted what it had just downloaded). Playing it again within the grace sets `playing` back and resumes it.
        rt.playing = false
        // BUG-021 (r2): the player's loopback reads take seconds to close and kept the torrent "streaming", so it still ran ~15 s
        // at full speed. From now on they do not count; the tick below pauses it unless it is kept or a background download.
        rt.ended = true
        tickSafely()
        rt.endJob = scope.launch {
            delay(BATCH_GRACE_MS)
            val stored = labels[torrentId] ?: return@launch
            // BUG-021 (r3): the closed player's reads (blocked on a piece of the paused torrent) would keep it "open" for good.
            val closed = closeLeftoverStreams(rt.ended, rt.handles) { it.close() }
            if (closed > 0) host.log("closed $closed leftover stream(s) of $torrentId after the grace")
            var waited = 0L
            while (rt.ended && rt.openStreams() > 0 && waited < LEFTOVER_WAIT_MS) {
                delay(LEFTOVER_POLL_MS)
                waited += LEFTOVER_POLL_MS
            }
            val handle = if (session.isRunning) session.find(torrentId) else null
            try {
                handle?.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                handle?.clearPieceDeadlines()
            } catch (_: Exception) {
                // handle went away
            }
            val s = settings
            val complete = stored.completed || runCatching { handle?.status(true)?.isFinished == true }.getOrDefault(false)
            val action = afterWatchAction(
                persist = stored.persist,
                backgroundDownload = stored.background,
                backgroundEnabled = s.backgroundDownloads && s.backgroundAck,
                complete = complete,
                seed = stored.seed,
                openStreams = rt.openStreams(),
                watchedToEnd = completed,
            )
            if (action == AfterWatchAction.REMOVE_WITH_FILES) {
                remove(torrentId, deleteFiles = true)
            } else {
                handle?.let(session::requestResume)
                tickSafely()
            }
        }
    }

    override suspend fun addDownload(source: String, media: Media, episode: Int?): Result<TorrentId> = withContext(Dispatchers.IO) {
        try {
            val s = settings
            if (!(s.backgroundDownloads && s.backgroundAck)) throw AppException(AppError.Unknown("torrent:background_off"))
            val (id, magnet) = resolveSource(source)
            val existing = labels[id]
            if (existing?.movedUri != null) throw AppException(AppError.Unknown("torrent:moved:$id"))
            labels.upsert(
                (existing ?: StoredTorrent(id = id, magnet = magnet, addedUnix = nowUnix())).copy(
                    label = labelFor(media, episode, existing?.label),
                    persist = true,
                    background = true,
                    wholeTorrent = episode == null || existing?.wholeTorrent == true,
                    pendingEpisode = episode,
                    userPaused = false,
                    completed = false,
                ),
            )
            val handle = addOrGet(id, magnet)
            recordHistory(id, labels[id]?.name.orEmpty(), labels[id]?.label, episode, origin = "download")
            val titles = titlesOf(media)
            scope.launch { finishBackgroundAdd(id, handle, titles, episode) }
            startServiceIfNeeded()
            tickSafely()
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.asAppException())
        }
    }

    override fun stats(id: TorrentId): Flow<TorrentStats> = flow {
        ensureStarted()
        while (currentCoroutineContextActive()) {
            val handle = session.find(id) ?: throw AppException(AppError.Unknown("torrent:missing:$id"))
            emit(buildStats(id, handle))
            delay(TICK_MS)
        }
    }.trackCollector().flowOn(Dispatchers.IO)

    override suspend fun files(id: TorrentId): List<TorrentFile> = withContext(Dispatchers.IO) {
        ensureStarted()
        val handle = session.find(id) ?: throw AppException(AppError.Unknown("torrent:missing:$id"))
        val info = handle.torrentFile() ?: return@withContext emptyList()
        val progress = handle.fileProgress(TorrentHandle.PIECE_GRANULARITY)
        val priorities = handle.filePriorities()
        val rt = runtimes[id]
        val all = info.engineFiles()
        val aligned = labels[id]?.alignment?.let { a -> alignedEpisodes(alignFiles(all), a) }
        all.map { f ->
            val done = progress.getOrElse(f.index) { 0L }
            TorrentFile(
                index = f.index,
                path = f.path,
                name = f.name,
                sizeBytes = f.size,
                downloadedBytes = done,
                progress = if (f.size <= 0) 1.0 else (done.toDouble() / f.size).coerceIn(0.0, 1.0),
                selected = priorities.getOrNull(f.index)?.isSelected() == true,
                streams = rt?.streams?.get(f.index)?.get() ?: 0,
                episode = aligned?.get(f.index) ?: episodeOf(f.name),
            )
        }
    }

    override fun peers(id: TorrentId): Flow<List<TorrentPeer>> = flow {
        ensureStarted()
        while (currentCoroutineContextActive()) {
            val handle = session.find(id) ?: throw AppException(AppError.Unknown("torrent:missing:$id"))
            emit(handle.peerInfo().map { it.toModel() }.sortedByDescending { it.downBps })
            delay(TICK_MS)
        }
    }.trackCollector().flowOn(Dispatchers.IO)

    override suspend fun trackers(id: TorrentId): List<TorrentTracker> = withContext(Dispatchers.IO) {
        ensureStarted()
        val handle = session.find(id) ?: throw AppException(AppError.Unknown("torrent:missing:$id"))
        handle.readTrackers { it.toModel() }
    }

    override suspend fun remove(id: TorrentId, deleteFiles: Boolean): Unit = withContext(Dispatchers.IO) {
        val stored = labels[id]
        runtimes.remove(id)?.endJob?.cancel()
        val handle = if (session.isRunning) session.find(id) else null
        val finalStatus = handle?.let { h -> runCatching { h.status(true) }.getOrNull() }
        history.markRemoved(id, nowUnix(), finalStatus?.totalWantedDone(), finalStatus?.allTimeUpload())
        if (handle != null) {
            session.remove(handle, deleteFiles)
        } else if (deleteFiles && stored != null) {
            // Desktop: the download folder may have changed since it was added; the folder it was written to is in its resume data.
            // (Android: its two folders, as before.)
            val savedIn = if (host.movesToUserFolder) {
                null
            } else {
                runCatching { session.readResume(id)?.getSavePath() }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::File)
            }
            deleteLocalFiles(stored, listOfNotNull(downloadDir, legacyDownloadDir, savedIn).distinct())
        }
        if (deleteFiles) stored?.movedUri?.let(host::deleteMoved)
        session.resumeFile(id).delete()
        torrentFile(id).delete()
        labels.remove(id)
        _events.tryEmit(TorrentEvent.Removed(id))
        tickSafely()
    }

    override suspend fun pause(id: TorrentId): Unit = withContext(Dispatchers.IO) {
        labels.update(id) { it.copy(userPaused = true) }
        runtimes[id]?.userResumed = false
        session.find(id)?.let { handle ->
            runCatching { handle.pause() }
            session.requestResume(handle)
        }
        tickSafely()
    }

    override suspend fun resume(id: TorrentId): Unit = withContext(Dispatchers.IO) {
        labels.update(id) { it.copy(userPaused = false) }
        pausedBySystem = false
        runtime(id).userResumed = true
        runCatching { ensureStarted() }
        tickSafely()
        startServiceIfNeeded()
    }

    override suspend fun setPersist(id: TorrentId, persist: Boolean): Unit = withContext(Dispatchers.IO) {
        labels.update(id) { it.copy(persist = persist) }
        tickSafely()
    }

    override suspend fun setSeeding(id: TorrentId, seed: Boolean): Unit = withContext(Dispatchers.IO) {
        labels.update(id) { it.copy(seed = seed) }
        tickSafely()
    }

    override suspend fun downloadFile(id: TorrentId, fileIndex: Int): Unit = withContext(Dispatchers.IO) {
        try {
            ensureStarted()
            val handle = session.find(id) ?: throw AppException(AppError.Unknown("torrent:missing:$id"))
            val info = handle.torrentFile() ?: return@withContext
            val file = info.engineFiles().firstOrNull { it.index == fileIndex } ?: return@withContext
            val have = handle.fileProgress(TorrentHandle.PIECE_GRANULARITY).getOrElse(fileIndex) { 0L }
            val needed = file.size - have
            if (needed > 0 && host.allocatableBytes(downloadDir) < needed) {
                throw AppException(AppError.Unknown("torrent:no_space:$needed"))
            }
            selectFile(id, handle, fileIndex, sequential = false)
            labels.update(id) { it.copy(userPaused = false, completed = false) }
            runtime(id).userResumed = true
            tickSafely()
            startServiceIfNeeded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(TorrentEvent.Error(id, e.asAppException().error.toString()))
        }
    }

    override suspend fun seekMarks(id: TorrentId, fileIndex: Int): List<ClosedFloatingPointRange<Double>> = withContext(Dispatchers.IO) {
        if (!session.isRunning) return@withContext emptyList()
        val handle = session.find(id) ?: return@withContext emptyList()
        val file = handle.torrentFile()?.engineFiles()?.firstOrNull { it.index == fileIndex } ?: return@withContext emptyList()
        try {
            downloadedRanges(file.span) { handle.havePiece(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun exportToDownloads(id: TorrentId, fileIndex: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val stored = labels[id]
            if (stored?.movedUri != null) throw AppException(AppError.Unknown("torrent:moved:$id"))
            ensureStarted()
            val handle = session.find(id) ?: throw AppException(AppError.Unknown("torrent:missing:$id"))
            val file = handle.torrentFile()?.engineFiles()?.firstOrNull { it.index == fileIndex }
                ?: throw AppException(AppError.Unknown("torrent:no_file:$id"))
            val done = handle.fileProgress(TorrentHandle.PIECE_GRANULARITY).getOrElse(fileIndex) { 0L }
            if (done < file.size) throw AppException(AppError.Unknown("torrent:incomplete"))
            val source = handle.fileOnDisk(file)
            val uri = try {
                host.exportToDownloads(source)
            } catch (e: IOException) {
                if (e.message == "unsupported") throw AppException(AppError.Unknown("torrent:export_unsupported"), e) else throw e
            }
            Result.success(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.asAppException())
        }
    }

    override suspend fun storageUsage(): TorrentStorage = withContext(Dispatchers.IO) {
        TorrentStorage(
            usedBytes = host.usedBytes(downloadDir) + (legacyDownloadDir?.let(host::usedBytes) ?: 0L),
            freeBytes = host.allocatableBytes(downloadDir),
            location = settings.storageLocation,
            downloadDir = downloadDir.absolutePath,
        )
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // parity wave A (15 §3.9, worker q): manual add, alignment, session history, re-announce
    // ------------------------------------------------------------------------------------------------------------------------

    override suspend fun addManual(source: String, media: Media?, alignment: EpisodeAlignment?, persist: Boolean): Result<TorrentId> =
        addManualInternal(source, media, alignment, persist, files = null)

    override suspend fun addManualFiles(source: String, media: Media?, alignment: EpisodeAlignment?, persist: Boolean, files: Set<Int>): Result<TorrentId> =
        addManualInternal(source, media, alignment, persist, files)

    /**
     * Desktop "Añadir torrent" preview: a `.torrent` is read as is, a magnet / info-hash asks the swarm for its metadata (nothing is
     * downloaded); a torrent already in the session answers from it. The metadata is kept so the add that follows does not wait again.
     */
    override suspend fun preview(source: String): Result<TorrentPreview> = preview(source) {}

    override suspend fun preview(source: String, onProgress: (TorrentPreviewProgress) -> Unit): Result<TorrentPreview> = withContext(Dispatchers.IO) {
        try {
            val raw = source.trim()
            // A magnet keeps its own hash as the id (the v1 one of a hybrid torrent: the add that follows uses it too).
            var magnetId: String? = null
            val ti: TorrentInfo = if (raw.startsWith("content://") || raw.startsWith("file://")) {
                parseTorrent(readTorrentBytes(raw))
            } else {
                val (id, magnet) = resolveSource(raw)
                magnetId = id
                ensureStarted()
                session.find(id)?.takeUnless { isPreviewHandle(it) }?.torrentFile()
                    ?: (previewCache[id] ?: fetchPreview(magnet, onProgress).also { awaitPreviewGone(id) })
                        ?.let { bytes -> parseTorrent(bytes).also { previewCache[id] = bytes } }
                    ?: throw AppException(AppError.Unknown(TORRENT_PREVIEW_TIMEOUT_DETAIL))
            }
            val id = magnetId ?: ti.infoHash().toHex().lowercase(Locale.ROOT)
            val files = ti.engineFiles().map { TorrentPreviewFile(it.index, it.path, it.size) }
            Result.success(TorrentPreview(id = id, name = ti.name(), sizeBytes = ti.totalSize(), files = files))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.asAppException())
        }
    }

    /** The metadata of a magnet for the preview, waiting at most [TORRENT_METADATA_TIMEOUT_SECS] and reporting every second. */
    private fun fetchPreview(magnet: String, onProgress: (TorrentPreviewProgress) -> Unit): ByteArray? {
        val timeout = TORRENT_METADATA_TIMEOUT_SECS.toInt()
        return session.fetchMagnet(magnet, timeout, previewDir()) { elapsed, peers ->
            runCatching { onProgress(TorrentPreviewProgress(elapsed, timeout, peers)) }
        }
    }

    private suspend fun addManualInternal(source: String, media: Media?, alignment: EpisodeAlignment?, persist: Boolean, files: Set<Int>?): Result<TorrentId> =
        withContext(Dispatchers.IO) {
            try {
                val raw = source.trim()
                val fromFile = raw.startsWith("content://") || raw.startsWith("file://")
                val id: String
                val magnet: String
                var fileParams: AddTorrentParams? = null
                if (fromFile) {
                    val bytes = readTorrentBytes(raw)
                    val ti = try {
                        TorrentInfo(bytes)
                    } catch (e: Exception) {
                        throw AppException(AppError.Unknown("torrent:invalid_file"), e)
                    }
                    id = ti.infoHash().toHex().lowercase(Locale.ROOT)
                    magnet = magnetFor(id)
                    stateDir.mkdirs()
                    torrentFile(id).writeBytes(bytes)
                    fileParams = AddTorrentParams().also { it.setTorrentInfo(ti) }
                } else {
                    val resolved = resolveSource(raw)
                    id = resolved.first
                    magnet = resolved.second
                    // A preview already fetched the metadata: add with it (and the magnet's trackers) instead of waiting again.
                    val cached = previewCache[id]
                    // libtorrent4j removes its metadata-only torrent (upload mode, saved under preview/) asynchronously: the add must
                    // not attach to it (it would never download, and its resume data would keep it so after a restart).
                    if (cached != null) awaitPreviewGone(id)
                    if (cached != null && session.find(id) == null && session.readResume(id) == null) {
                        runCatching {
                            val ti = TorrentInfo(cached)
                            val params = if (magnet.startsWith("magnet:", ignoreCase = true)) AddTorrentParams.parseMagnetUri(magnet) else AddTorrentParams()
                            params.setTorrentInfo(ti)
                            stateDir.mkdirs()
                            torrentFile(id).writeBytes(cached)
                            fileParams = params
                        }
                    }
                }
                previewCache.remove(id)
                val existing = labels[id]
                if (existing?.movedUri != null) throw AppException(AppError.Unknown("torrent:moved:$id"))
                val origin = if (raw.lowercase(Locale.ROOT).startsWith("magnet:")) "magnet" else "manual"
                labels.upsert(
                    (existing ?: StoredTorrent(id = id, magnet = magnet, addedUnix = nowUnix())).copy(
                        label = media?.let { labelFor(it, null, existing?.label) } ?: existing?.label,
                        persist = persist || existing?.persist == true,
                        background = persist || existing?.background == true,
                        // Files chosen in the preview: only those (plus what was selected before) download.
                        wholeTorrent = (files == null && persist) || existing?.wholeTorrent == true,
                        selected = if (files == null) existing?.selected.orEmpty() else (existing?.selected.orEmpty() + files).distinct(),
                        alignment = alignment ?: existing?.alignment,
                        origin = existing?.origin?.takeIf { existing.addedUnix > 0 && it != "play" } ?: origin,
                        userPaused = false,
                        completed = false,
                    ),
                )
                val handle = fileParams?.let { addWithParams(id, it) } ?: addOrGet(id, magnet)
                // a .torrent carries its metadata: no METADATA_RECEIVED alert will come
                handle.torrentFile()?.let { onMetadataKnown(id, handle, it) }
                recordHistory(id, labels[id]?.name.orEmpty(), labels[id]?.label, null, origin)
                startServiceIfNeeded()
                tickSafely()
                Result.success(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e.asAppException())
            }
        }

    override suspend fun setAlignment(id: TorrentId, media: Media?, alignment: EpisodeAlignment?): Result<Unit> = withContext(Dispatchers.IO) {
        val updated = labels.update(id) { t ->
            t.copy(alignment = alignment, label = media?.let { m -> labelFor(m, t.label?.episode, t.label) } ?: t.label)
        }
        if (updated == null) Result.failure(AppException(AppError.Unknown("torrent:missing:$id"))) else Result.success(Unit)
    }

    override fun sessionHistory(limit: Int): Flow<List<TorrentSessionEntry>> = history.entries.map { it.take(limit.coerceAtLeast(0)) }

    override suspend fun clearSessionHistory() = withContext(Dispatchers.IO) { history.clear() }

    override suspend fun reannounce(id: TorrentId): Unit = withContext(Dispatchers.IO) {
        ensureStarted()
        val handle = session.find(id) ?: throw AppException(AppError.Unknown("torrent:missing:$id"))
        runCatching { addCustomTrackers(handle, settings.trackers) }
        runCatching { handle.forceReannounce() }
        runCatching { handle.forceDHTAnnounce() }
        Unit
    }

    private suspend fun debridPlayback(id: String, magnet: String, media: Media, episode: Int): TorrentPlayback? {
        val d = debrid ?: return null
        if (d.accounts.value.isEmpty()) return null
        val resolved = try {
            d.instantStream(id, magnet, episode, titlesOf(media))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        recordHistory(id, media.title, labelFor(media, episode, labels[id]?.label), episode, origin = "play")
        return TorrentPlayback(
            args = PlayerArgs(media = media.ref, title = media.title, episode = episode),
            stream = resolved.stream,
            torrentId = id,
            fileIndex = -1,
            // 1.1 (F7): the file's own name, never a piece of the CDN link
            fileName = resolved.file.fileName,
            batch = false,
        )
    }

    private fun recordHistory(id: String, name: String, label: TorrentLabel?, episode: Int?, origin: String) {
        history.record(
            TorrentSessionEntry(
                id = id,
                name = name.ifBlank { label?.title ?: id },
                mediaId = label?.mediaId?.takeIf { it.isNotBlank() },
                series = label?.title,
                episode = episode ?: label?.episode,
                addedUnix = nowUnix(),
                origin = origin,
            ),
        )
    }

    private fun alignFiles(files: List<EngineFile>): List<AlignFile> =
        files.filter { isVideoPath(it.path) }.map { AlignFile(it.index, it.path, episodeOf(it.name)) }

    private fun alignedFor(alignment: EpisodeAlignment?, files: List<EngineFile>, episode: Int): Int? =
        alignment?.let { alignedIndex(alignFiles(files), it, episode) }

    private fun addCustomTrackers(handle: TorrentHandle, trackers: List<String>) {
        if (trackers.isEmpty()) return
        val have = handle.readTrackers { it.url }.toSet()
        trackers.filter { it !in have }.forEach { handle.addTracker(AnnounceEntry(it)) }
    }

    /** torrent.preload-next: once the streamed file is 80 % downloaded, queue the next episode's file at low priority. */
    private fun maybePreload(t: StoredTorrent, handle: TorrentHandle, info: TorrentInfo, rt: Runtime) {
        val videos = info.engineFiles().filter { isVideoPath(it.path) }
        if (videos.size < 2) return
        val aligned = t.alignment?.let { a -> alignedEpisodes(alignFiles(videos), a) }
        val list = videos.map { AlignFile(it.index, it.path, aligned?.get(it.index) ?: episodeOf(it.name)) }
        val progress = handle.fileProgress(TorrentHandle.PIECE_GRANULARITY)
        for ((index, count) in rt.streams) {
            if (count.get() <= 0) continue
            val file = videos.firstOrNull { it.index == index } ?: continue
            val fraction = if (file.size <= 0) 1.0 else progress.getOrElse(index) { 0L }.toDouble() / file.size
            if (fraction < PRELOAD_AT) continue
            val next = preloadTarget(list, index) ?: continue
            if (!rt.preloaded.add(next)) continue
            if (!handle.filePriority(next).isSelected()) handle.filePriority(next, Priority.LOW)
            labels.update(t.id) { it.copy(selected = (it.selected + next).distinct()) }
        }
    }

    /** torrent.cache-limit: streamed (not kept) torrents beyond `cacheLimitMib` are removed with their files, oldest first. */
    private fun enforceCacheLimit(s: TorrentSettings, liveMap: Map<String, LiveInfo>) {
        val items = labels.torrents.value.values
            .filter { !it.persist && !it.background && !it.seed && it.movedUri == null }
            .map { t ->
                val size = liveMap[t.id]?.sizeBytes ?: t.sizeBytes
                val progress = liveMap[t.id]?.progress ?: t.progress
                val rt = runtimes[t.id]
                CacheItem(t.id, t.addedUnix, (size * progress).toLong(), inUse = (rt?.openStreams() ?: 0) > 0 || rt?.playing == true)
            }
        cacheEvictions(items, s.cacheLimitMib.toLong() * 1024 * 1024).forEach { id -> scope.launch { remove(id, deleteFiles = true) } }
    }

    private suspend fun addWithParams(id: String, params: AddTorrentParams): TorrentHandle {
        ensureStarted()
        session.find(id)?.let { return it }
        prepareParams(params, settings, paused = false, labels[id])
        val handle = session.add(params)
        _events.tryEmit(TorrentEvent.Added(id))
        return handle
    }

    private fun torrentFile(id: String): File = File(stateDir, "$id.torrent")

    private fun parseTorrent(bytes: ByteArray): TorrentInfo = try {
        TorrentInfo(bytes)
    } catch (e: Exception) {
        throw AppException(AppError.Unknown("torrent:invalid_file"), e)
    }

    private fun torrentFileParams(id: String): AddTorrentParams? {
        val file = torrentFile(id).takeIf { it.isFile } ?: return null
        return runCatching { AddTorrentParams().also { it.setTorrentInfo(TorrentInfo(file.readBytes())) } }.getOrNull()
    }

    private fun readTorrentBytes(uri: String): ByteArray {
        val input = host.openTorrentFile(uri) ?: throw AppException(AppError.Unknown("torrent:invalid_file"))
        return input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_TORRENT_FILE_BYTES) throw AppException(AppError.Unknown("torrent:invalid_file"))
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Service hooks (TorrentService)
    // ------------------------------------------------------------------------------------------------------------------------

    /** Notification "Pausar todo". */
    internal fun pauseAll() {
        scope.launch {
            labels.torrents.value.values.filter { !it.completed }.forEach { t ->
                labels.update(t.id) { it.copy(userPaused = true) }
                runtimes[t.id]?.userResumed = false
            }
            tickSafely()
        }
    }

    /** Android 15 `onTimeout` of the dataSync service: pause background work until the user resumes. */
    internal fun pauseBySystem() {
        pausedBySystem = true
        tickSafely()
        refreshIdleStatus()
    }

    /** "Reanudar" of the paused-by-system notification. */
    internal fun resumeAfterSystemPause() {
        pausedBySystem = false
        scope.launch {
            runCatching { ensureStarted() }
            tickSafely()
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // StreamProvider (loopback server)
    // ------------------------------------------------------------------------------------------------------------------------

    override fun info(infoHash: String, fileIndex: Int): StreamInfo? {
        val handle = session.find(infoHash) ?: return null
        val file = handle.torrentFile()?.engineFiles()?.firstOrNull { it.index == fileIndex } ?: return null
        return StreamInfo(file.name, file.size)
    }

    override fun open(infoHash: String, fileIndex: Int): StreamHandle? {
        val handle = session.find(infoHash) ?: return null
        val file = handle.torrentFile()?.engineFiles()?.firstOrNull { it.index == fileIndex } ?: return null
        try {
            if (!handle.filePriority(fileIndex).isSelected()) selectFile(infoHash, handle, fileIndex, sequential = true)
            handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            if (handle.status(true).isPausedFlag()) handle.resume()
        } catch (_: Exception) {
            return null
        }
        val rt = runtime(infoHash)
        rt.streams.getOrPut(fileIndex) { AtomicInteger() }.incrementAndGet()
        _events.tryEmit(TorrentEvent.StreamOpened(infoHash, fileIndex))
        tickSafely()
        return TorrentStream(infoHash, handle, file, rt).also { rt.handles += it }
    }

    private inner class TorrentStream(
        private val id: String,
        private val handle: TorrentHandle,
        private val file: EngineFile,
        private val rt: Runtime,
    ) : StreamHandle {
        override val info = StreamInfo(file.name, file.size)
        private val window = deadlineWindow(file.span.pieceLength)
        private val deadlinePieces = HashSet<Int>()
        private var lastPiece = -1
        private var raf: RandomAccessFile? = null
        @Volatile private var closed = false

        override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed) throw IOException("closed")
            if (position >= file.size) return -1
            val piece = file.span.pieceAt(position)
            awaitPiece(piece)
            val available = minOf(length.toLong(), file.span.pieceEndInFile(piece) - position, file.size - position).toInt()
            if (available <= 0) return -1
            val f = raf ?: RandomAccessFile(handle.fileOnDisk(file), "r").also { raf = it }
            f.seek(position)
            val read = f.read(buffer, offset, available)
            return if (read <= 0) throw IOException("short read") else read
        }

        private fun awaitPiece(piece: Int) {
            prioritize(piece)
            val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS
            while (true) {
                if (closed) throw IOException("closed")
                val have = try {
                    handle.isValid && handle.havePiece(piece)
                } catch (_: Exception) {
                    throw IOException("torrent removed")
                }
                if (have) return
                if (!handle.isValid) throw IOException("torrent removed")
                if (System.currentTimeMillis() > deadline) throw IOException("timeout waiting for piece $piece")
                synchronized(pieceSignal) { pieceSignal.wait(PIECE_POLL_MS) }
            }
        }

        /** Deadlines on the [window] pieces from the read position; a seek drops the deadlines this stream set before. */
        private fun prioritize(piece: Int) {
            if (piece == lastPiece) return
            val jumped = lastPiece < 0 || piece < lastPiece || piece > lastPiece + window / 2
            if (!jumped) {
                lastPiece = piece
                return
            }
            try {
                val last = file.span.lastPiece
                val wanted = (piece..minOf(piece + window - 1, last)).toSet()
                deadlinePieces.filter { it !in wanted }.forEach { handle.resetPieceDeadline(it) }
                deadlinePieces.retainAll(wanted)
                wanted.sorted().forEachIndexed { i, p ->
                    if (!handle.havePiece(p)) {
                        handle.setPieceDeadline(p, DEADLINE_BASE_MS + i * DEADLINE_STEP_MS)
                        deadlinePieces += p
                    }
                }
            } catch (_: Exception) {
                // the handle may be gone; the read loop reports it
            }
            lastPiece = piece
        }

        override fun close() {
            if (closed) return
            closed = true
            rt.handles -= this
            runCatching { raf?.close() }
            synchronized(pieceSignal) { pieceSignal.notifyAll() }
            rt.streams[file.index]?.decrementAndGet()
            _events.tryEmit(TorrentEvent.StreamClosed(id, file.index))
            tickSafely()
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Engine lifecycle
    // ------------------------------------------------------------------------------------------------------------------------

    private suspend fun ensureStarted() {
        if (session.isRunning && server.isRunning) return
        engineMutex.withLock {
            if (session.isRunning && server.isRunning) return
            val s = settings
            val net = host.network.value
            if (!session.isRunning) {
                session.start(s, uploadLimitBytes(s, net.metered), lsdAllowed = !net.metered)
                restoreAll(s)
            }
            if (!server.isRunning) server.start()
            idleSinceMs = 0
            startTicker()
        }
    }

    private fun restoreAll(s: TorrentSettings) {
        for (t in labels.torrents.value.values) {
            if (t.movedUri != null || session.find(t.id) != null) continue
            try {
                val params = session.readResume(t.id) ?: torrentFileParams(t.id) ?: AddTorrentParams.parseMagnetUri(t.magnet ?: magnetFor(t.id))
                prepareParams(params, s, paused = true, t)
                val handle = session.add(params)
                handle.torrentFile()?.let { applySelection(handle, it, t) }
            } catch (e: Exception) {
                _events.tryEmit(TorrentEvent.Error(t.id, e.message ?: "restore failed"))
            }
        }
    }

    /**
     * App exit (desktop: the Koin `onClose` of its torrent module): saves resume data, stops the session and the loopback server and
     * cancels the background work. Blocking. Android never calls it (the engine lives as long as the process).
     */
    internal fun shutdown() {
        // No new reads, and the open responses are interrupted.
        runCatching { server.close() }
        // Nothing may touch a libtorrent handle once the session is gone (a tick or a stream read in the middle of a native call
        // crashed the process at exit): the background work ends first, then the session stops under the tick lock.
        val job = scope.coroutineContext[Job]
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(SHUTDOWN_JOIN_MS) { job?.children?.toList()?.forEach { it.cancelAndJoin() } }
        }
        synchronized(tickLock) { runCatching { kotlinx.coroutines.runBlocking { shutdownEngine() } } }
        job?.cancel()
    }

    private suspend fun shutdownEngine() {
        engineMutex.withLock {
            if (!session.isRunning) return
            tickerJob?.cancel()
            tickerJob = null
            persistProgress()
            val handles = labels.torrents.value.keys.mapNotNull { session.find(it) }
            session.stop(handles)
            server.stop()
            live.value = emptyMap()
            idleSinceMs = 0
        }
        refreshIdleStatus()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                tickSafely()
                delay(TICK_MS)
            }
        }
    }

    private fun onSettingsChanged() {
        if (session.isRunning) {
            val s = settings
            val net = host.network.value
            runCatching { session.applySettings(s, uploadLimitBytes(s, net.metered), lsdAllowed = !net.metered) }
            labels.torrents.value.keys.forEach { id ->
                session.find(id)?.let { h ->
                    runCatching { h.swig().set_max_connections(s.maxPeers) }
                    runCatching { addCustomTrackers(h, s.trackers) }
                }
            }
            tickSafely()
        } else {
            refreshIdleStatus()
            maybeStartForBackground()
        }
    }

    private fun onConditionsChanged() {
        if (session.isRunning) {
            onSettingsChanged()
        } else {
            refreshIdleStatus()
            maybeStartForBackground()
        }
    }

    private fun maybeStartForBackground() {
        if (hasBackgroundWork() && backgroundAllowedNow()) {
            scope.launch {
                runCatching { ensureStarted() }
                tickSafely()
            }
        }
    }

    private fun hasBackgroundWork(): Boolean {
        val s = settings
        if (!(s.backgroundDownloads && s.backgroundAck)) return false
        return labels.torrents.value.values.any { it.movedUri == null && !it.completed && !it.userPaused && (it.background || it.persist) }
    }

    private fun backgroundAllowedNow(): Boolean = backgroundAllowed(settings, conditions())

    private fun conditions(): DeviceConditions {
        val net = host.network.value
        return DeviceConditions(connected = net.connected, metered = net.metered, batteryLow = host.batteryLow.value, pausedBySystem = pausedBySystem)
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Tick: run policy, live info, status, service demand, idle shutdown
    // ------------------------------------------------------------------------------------------------------------------------

    private fun tickSafely() {
        scope.launch {
            try {
                tick()
            } catch (_: Exception) {
                // a vanished handle must not stop the ticker
            }
        }
    }

    private fun tick() {
        synchronized(tickLock) { tickLocked() }
    }

    private fun tickLocked() {
        if (!session.isRunning) {
            refreshIdleStatus()
            return
        }
        tickCount++
        val s = settings
        val inWindow = s.schedule?.contains(minuteOfDay()) == true
        if (inWindow != scheduleActive) {
            // crossing a bandwidth-schedule boundary: apply the other limits to the running session
            scheduleActive = inWindow
            scope.launch { onSettingsChanged() }
        }
        val cond = conditions()
        val liveMap = HashMap<String, LiveInfo>()
        var downBps = 0L
        var upBps = 0L
        var downloads = 0
        var wanted = 0L
        var wantedDone = 0L
        var streamingTitle: String? = null
        for (t in labels.torrents.value.values) {
            if (t.movedUri != null) continue
            val handle = session.find(t.id) ?: continue
            val st = handle.status(true)
            val info = handle.torrentFile()
            val complete = st.hasMetadata() && st.totalWanted() > 0 && st.isFinished
            val progress = st.wantedProgress()
            liveMap[t.id] = LiveInfo(
                state = st.toModelState(),
                progress = if (complete) 1.0 else progress,
                sizeBytes = info?.totalSize() ?: t.sizeBytes,
                files = info?.engineFiles()?.size ?: t.files,
                path = runCatching { handle.savePath() }.getOrDefault(""),
            )
            if (complete != t.completed) onCompletionChanged(t, handle, complete)
            val rt = runtimes[t.id]
            val streaming = countsAsStreaming(rt?.openStreams() ?: 0, rt?.ended == true)
            val playing = rt?.playing == true
            if (streaming && streamingTitle == null) streamingTitle = t.label?.title ?: info?.name()
            if (streaming && s.preloadNext && info != null && rt != null && tickCount % PRELOAD_EVERY_TICKS == 0L) {
                runCatching { maybePreload(t, handle, info, rt) }
            }
            val run = shouldRun(
                RunFacts(
                    streaming = streaming,
                    playing = playing,
                    userPaused = t.userPaused,
                    userResumed = rt?.userResumed == true,
                    complete = complete,
                    persist = t.persist,
                    backgroundDownload = t.background,
                    seed = t.seed,
                ),
                s,
                cond,
            )
            val paused = st.isPausedFlag()
            if (run && paused) {
                handle.resume()
            } else if (!run && !paused) {
                handle.pause()
                session.requestResume(handle)
            }
            if (run && !streaming && !playing && !complete) {
                downloads++
                wanted += st.totalWanted()
                wantedDone += st.totalWantedDone()
            }
            if (run) {
                downBps += st.downloadPayloadRate().toLong().coerceAtLeast(0)
                upBps += st.uploadPayloadRate().toLong().coerceAtLeast(0)
            }
            if (tickCount % RESUME_EVERY_TICKS == 0L && run && handle.needSaveResumeData()) session.requestResume(handle)
        }
        live.value = liveMap
        if (tickCount % PERSIST_EVERY_TICKS == 0L) persistProgress()
        if (s.cacheLimitMib > 0 && tickCount % PERSIST_EVERY_TICKS == 0L) enforceCacheLimit(s, liveMap)

        val anyStreaming = runtimes.values.any { countsAsStreaming(it.openStreams(), it.ended) }
        val anyPlaying = runtimes.values.any { it.playing }
        publishStatus(s, cond, running = true, anyStreaming = anyStreaming)
        val demand = anyStreaming || anyPlaying || downloads > 0
        _snapshot.value = ServiceSnapshot(
            demand = demand,
            streaming = anyStreaming,
            streamingTitle = streamingTitle,
            downloads = downloads,
            downBps = downBps,
            upBps = upBps,
            progress = if (wanted > 0) wantedDone.toDouble() / wanted else null,
            pausedBySystem = pausedBySystem,
        )
        if (demand) startServiceIfNeeded()

        // engine idle shutdown: nothing streams, plays, downloads and no screen collects stats for 30 s
        val now = System.currentTimeMillis()
        if (!demand && uiCollectors.get() == 0) {
            if (idleSinceMs == 0L) {
                idleSinceMs = now
            } else if (now - idleSinceMs >= IDLE_SHUTDOWN_MS) {
                idleSinceMs = 0
                scope.launch { shutdownEngine() }
            }
        } else {
            idleSinceMs = 0
        }
    }

    private fun onCompletionChanged(t: StoredTorrent, handle: TorrentHandle, complete: Boolean) {
        labels.update(t.id) { it.copy(completed = complete, progress = if (complete) 1.0 else it.progress) }
        if (!complete) return
        _events.tryEmit(TorrentEvent.Completed(t.id))
        session.requestResume(handle)
        if (t.background) {
            host.notifyCompleted(t.id, t.label?.title ?: t.name.ifBlank { t.id }, t.label?.episode)
        }
        scope.launch { maybeMoveToUserFolder(t.id) }
    }

    private fun publishStatus(s: TorrentSettings, cond: DeviceConditions, running: Boolean, anyStreaming: Boolean) {
        val eligible = labels.torrents.value.values.filter {
            it.movedUri == null && !it.completed && !it.userPaused && (it.background || it.persist) && s.backgroundDownloads && s.backgroundAck
        }
        val streamingIds = runtimes.filterValues { it.openStreams() > 0 }.keys
        _status.value = TorrentServiceStatus(
            running = running,
            activeDownloads = eligible.count { it.id !in streamingIds },
            streaming = anyStreaming,
            metered = cond.metered,
            waitingForWifi = eligible.isNotEmpty() && s.wifiOnly && (cond.metered || !cond.connected),
            pausedLowBattery = eligible.isNotEmpty() && s.pauseOnLowBattery && cond.batteryLow,
            pausedBySystem = pausedBySystem,
        )
    }

    private fun refreshIdleStatus() {
        if (session.isRunning) return
        publishStatus(settings, conditions(), running = false, anyStreaming = false)
        _snapshot.value = ServiceSnapshot(pausedBySystem = pausedBySystem)
    }

    private fun persistProgress() {
        val current = live.value
        current.forEach { (id, info) ->
            labels.update(id) { t ->
                if (kotlin.math.abs(t.progress - info.progress) < 0.005 && t.sizeBytes == info.sizeBytes && t.files == info.files) {
                    t
                } else {
                    t.copy(progress = info.progress, sizeBytes = info.sizeBytes, files = info.files)
                }
            }
        }
    }

    private fun startServiceIfNeeded() {
        if (!serviceRunning) host.startService()
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Alerts
    // ------------------------------------------------------------------------------------------------------------------------

    private fun onAlert(alert: Alert<*>) {
        when (alert.type()) {
            AlertType.PIECE_FINISHED -> synchronized(pieceSignal) { pieceSignal.notifyAll() }
            AlertType.METADATA_RECEIVED -> {
                val handle = (alert as TorrentAlert<*>).handle()
                // The metadata-only torrent of a desktop preview is not ours, and LibtorrentSession.fetchMagnet removes it as soon as
                // this alert is out: checked here, while it is still valid (reading it later from the scope crashed in native code).
                if (isPreviewHandle(handle)) return
                val id = handle.infoHash().toHex()
                scope.launch {
                    handle.torrentFile()?.let { onMetadataKnown(id, handle, it) }
                    _events.tryEmit(TorrentEvent.MetadataReady(id))
                }
            }
            AlertType.FILE_COMPLETED -> {
                val a = alert as FileCompletedAlert
                _events.tryEmit(TorrentEvent.FileCompleted(a.handle().infoHash().toHex(), a.index()))
                synchronized(pieceSignal) { pieceSignal.notifyAll() }
            }
            AlertType.TORRENT_FINISHED -> tickSafely()
            AlertType.TORRENT_ERROR, AlertType.FILE_ERROR -> {
                val handle = (alert as TorrentAlert<*>).handle()
                if (handle.isValid) _events.tryEmit(TorrentEvent.Error(handle.infoHash().toHex(), alert.message()))
            }
            AlertType.STATE_CHANGED -> {
                val handle = (alert as TorrentAlert<*>).handle()
                if (handle.isValid) {
                    val state = runCatching { handle.status(true).toModelState() }.getOrDefault(TorrentState.INITIALIZING)
                    _events.tryEmit(TorrentEvent.StateChanged(handle.infoHash().toHex(), state))
                }
            }
            else -> Unit
        }
    }

    private fun onMetadataKnown(id: String, handle: TorrentHandle, info: TorrentInfo) {
        val files = info.engineFiles()
        val stored = labels.update(id) {
            it.copy(name = info.name(), files = files.size, sizeBytes = info.totalSize())
        } ?: return
        applySelection(handle, info, stored)
        session.requestResume(handle)
        val pending = stored.pendingEpisode
        if (pending != null && id !in finishing) {
            val titles = listOfNotNull(stored.label?.title).filter { it.isNotBlank() }
            scope.launch { finishBackgroundAdd(id, handle, titles, pending) }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------------------

    private suspend fun addOrGet(id: String, magnet: String): TorrentHandle {
        ensureStarted()
        session.find(id)?.let { return it }
        val params = session.readResume(id) ?: AddTorrentParams.parseMagnetUri(magnet)
        prepareParams(params, settings, paused = false, labels[id])
        val handle = session.add(params)
        _events.tryEmit(TorrentEvent.Added(id))
        return handle
    }

    private fun prepareParams(params: AddTorrentParams, s: TorrentSettings, paused: Boolean, stored: StoredTorrent?) {
        val savePath = params.getSavePath()
        if (savePath.isNullOrBlank() || isUnder(savePath, previewDir())) {
            // (a torrent saved by the 1.0.0 desktop while still attached to its preview's metadata-only handle goes to the real folder)
            params.setSavePath(downloadDir.absolutePath)
        } else if (isLegacyDir(savePath) && stored?.hasNoData() != false) {
            // The empty (often sparse, full-size) file left there is removed so storage usage does not count it.
            stored?.let { deleteLocalFiles(it, listOfNotNull(legacyDownloadDir)) }
            params.setSavePath(downloadDir.absolutePath)
        }
        params.setFlags(LibtorrentSession.addFlags(params.getFlags(), paused = paused, pex = s.pex, dontDownloadByDefault = true))
        params.setMaxConnections(s.maxPeers)
        // parity wave A (torrent.custom-trackers): the user's trackers are announced on every new torrent
        if (s.trackers.isNotEmpty()) {
            val current = runCatching { params.getTrackers() }.getOrNull().orEmpty()
            params.setTrackers((current + s.trackers).distinct())
        }
    }

    /** Re-applies the stored file selection (all files for whole-torrent downloads). */
    private fun applySelection(handle: TorrentHandle, info: TorrentInfo, stored: StoredTorrent) {
        val count = info.files().numFiles()
        val wanted: Set<Int> = if (stored.wholeTorrent) (0 until count).toSet() else stored.selected.toSet()
        if (wanted.isEmpty()) return
        val current = handle.filePriorities()
        val priorities = Array(count) { i ->
            when {
                i in wanted -> current.getOrNull(i)?.takeIf { it.isSelected() } ?: Priority.DEFAULT
                else -> current.getOrNull(i)?.takeIf { it.isSelected() } ?: Priority.IGNORE
            }
        }
        if (!priorities.contentEquals(current)) handle.prioritizeFiles(priorities)
    }

    private fun selectFile(id: String, handle: TorrentHandle, index: Int, sequential: Boolean) {
        if (!handle.filePriority(index).isSelected()) handle.filePriority(index, Priority.DEFAULT)
        if (sequential) handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
        labels.update(id) { it.copy(selected = (it.selected + index).distinct(), completed = false) }
        if (handle.status(true).isPausedFlag()) handle.resume()
        session.requestResume(handle)
    }

    /** First and last pieces of the file first: the container index (MKV cues, MP4 moov) is often at the end. */
    private fun prefetchEdges(handle: TorrentHandle, file: EngineFile) {
        try {
            val span = file.span
            val pieces = listOf(span.firstPiece, span.firstPiece + 1, span.lastPiece - 1, span.lastPiece)
                .filter { it in span.firstPiece..span.lastPiece }
                .distinct()
            pieces.forEachIndexed { i, p -> if (!handle.havePiece(p)) handle.setPieceDeadline(p, DEADLINE_BASE_MS + i * DEADLINE_STEP_MS) }
        } catch (_: Exception) {
            // best effort
        }
    }

    private suspend fun awaitMetadata(handle: TorrentHandle): TorrentInfo? {
        val deadline = System.currentTimeMillis() + TORRENT_METADATA_TIMEOUT_SECS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (!handle.isValid) return null
            handle.torrentFile()?.let { return it }
            delay(METADATA_POLL_MS)
        }
        return null
    }

    private suspend fun finishBackgroundAdd(id: String, handle: TorrentHandle, titles: List<String>, episode: Int?) {
        if (!finishing.add(id)) return
        try {
            val info = awaitMetadata(handle)
            if (info == null) {
                _events.tryEmit(TorrentEvent.Error(id, "torrent:no_metadata"))
                return
            }
            val files = info.engineFiles()
            val selected: List<Int> = if (episode == null) {
                files.map { it.index }
            } else {
                val index = pickFile(files, episode, titles)
                if (index == null) {
                    labels.update(id) { it.copy(pendingEpisode = null) }
                    _events.tryEmit(TorrentEvent.Error(id, "torrent:no_file:$id"))
                    host.notifyError(id, labels[id]?.label?.title ?: info.name())
                    return
                }
                listOf(index)
            }
            val progress = handle.fileProgress(TorrentHandle.PIECE_GRANULARITY)
            val needed = files.filter { it.index in selected }.sumOf { (it.size - progress.getOrElse(it.index) { 0L }).coerceAtLeast(0) }
            if (needed > 0 && host.allocatableBytes(downloadDir) < needed) {
                labels.update(id) { it.copy(userPaused = true, pendingEpisode = null) }
                _events.tryEmit(TorrentEvent.Error(id, "torrent:no_space:$needed"))
                host.notifyError(id, labels[id]?.label?.title ?: info.name())
                tickSafely()
                return
            }
            val stored = labels.update(id) { it.copy(selected = (it.selected + selected).distinct(), pendingEpisode = null) } ?: return
            applySelection(handle, info, stored)
            session.requestResume(handle)
            tickSafely()
        } catch (e: Exception) {
            _events.tryEmit(TorrentEvent.Error(id, e.message ?: "download failed"))
        } finally {
            finishing.remove(id)
        }
    }

    /** SAF move-on-complete (11 §6 a): copy the completed selected files to the user's folder, then drop the local copy. */
    private suspend fun maybeMoveToUserFolder(id: String) {
        if (!host.movesToUserFolder) return
        val tree = (settings.storageLocation as? StorageLocation.UserFolder)?.treeUri ?: return
        val stored = labels[id] ?: return
        if (!(stored.background || stored.persist) || stored.seed || stored.movedUri != null) return
        val rt = runtimes[id]
        if ((rt?.openStreams() ?: 0) > 0 || rt?.playing == true) return
        val handle = session.find(id) ?: return
        val info = handle.torrentFile() ?: return
        val progress = handle.fileProgress(TorrentHandle.PIECE_GRANULARITY)
        val files = info.engineFiles().filter { f -> handle.filePriority(f.index).isSelected() && progress.getOrElse(f.index) { 0L } >= f.size }
        if (files.isEmpty()) return
        try {
            var first: String? = null
            for (f in files) {
                val uri = host.copyToFolder(handle.fileOnDisk(f), tree)
                if (first == null) first = uri
            }
            session.remove(handle, deleteFiles = true)
            session.resumeFile(id).delete()
            labels.update(id) { it.copy(movedUri = first, completed = true, progress = 1.0) }
            tickSafely()
        } catch (e: Exception) {
            _events.tryEmit(TorrentEvent.Error(id, "torrent:move_failed"))
        }
    }

    private fun discardIfTransient(id: String) {
        val stored = labels[id] ?: return
        if (stored.persist || stored.background) return
        scope.launch { remove(id, deleteFiles = true) }
    }

    private fun deleteLocalFiles(stored: StoredTorrent, roots: List<File> = listOfNotNull(downloadDir, legacyDownloadDir)) {
        val name = stored.name.ifBlank { return }
        for (dir in roots) {
            val root = dir.canonicalFile
            val target = File(root, name).canonicalFile
            if (target.path.startsWith(root.path + File.separator)) target.deleteRecursively()
        }
    }

    /** Where libtorrent4j's fetchMagnet keeps the metadata-only torrent of a desktop preview. */
    private fun previewDir(): File = File(stateDir, "preview")

    private fun isUnder(path: String, dir: File): Boolean = runCatching {
        File(path).canonicalFile.toPath().startsWith(dir.canonicalFile.toPath())
    }.getOrDefault(false)

    private fun isPreviewHandle(handle: TorrentHandle): Boolean = runCatching { isUnder(handle.savePath(), previewDir()) }.getOrDefault(false)

    /**
     * Waits (bounded) until the preview's metadata-only torrent of [id] has left the session; removes it again if it is still there.
     * Only ever finds something after a desktop preview.
     */
    private suspend fun awaitPreviewGone(id: String) {
        repeat(PREVIEW_GONE_POLLS) { i ->
            val h = session.find(id) ?: return
            if (!isPreviewHandle(h)) return
            if (i == PREVIEW_GONE_POLLS / 2) runCatching { session.remove(h, deleteFiles = false) }
            delay(PREVIEW_GONE_POLL_MS)
        }
    }

    private fun isLegacyDir(path: String): Boolean {
        val legacy = legacyDownloadDir ?: return false
        return runCatching { File(path).canonicalPath == legacy.canonicalPath }.getOrDefault(false)
    }

    /** Nothing downloaded yet: safe to start over in [downloadDir] (the stuck-at-0 % case on FUSE-BPF devices). */
    private fun StoredTorrent.hasNoData(): Boolean = !completed && progress <= 0.0

    private fun buildStats(id: String, handle: TorrentHandle): TorrentStats {
        val st = handle.status(true)
        val info = handle.torrentFile()
        val stored = labels[id]
        val s = settings
        val streaming = (runtimes[id]?.openStreams() ?: 0) > 0
        val wanted = st.totalWanted()
        val done = st.totalWantedDone()
        val down = st.downloadPayloadRate().toLong().coerceAtLeast(0)
        val metered = host.network.value.metered
        val error = st.errorCode().takeIf { it.isError }?.message
        return TorrentStats(
            id = id,
            name = info?.name() ?: st.name().ifBlank { stored?.name?.ifBlank { null } ?: stored?.label?.title ?: id },
            infoHash = id,
            state = st.toModelState(),
            error = error,
            progress = st.wantedProgress(),
            downloadedBytes = done,
            uploadedBytes = st.allTimeUpload(),
            totalBytes = wanted,
            sizeBytes = info?.totalSize() ?: stored?.sizeBytes ?: 0,
            pieces = info?.numPieces() ?: 0,
            pieceBytes = info?.pieceLength()?.toLong() ?: 0,
            piecesHave = st.numPieces(),
            downBps = down,
            upBps = st.uploadPayloadRate().toLong().coerceAtLeast(0),
            seeders = st.numSeeds(),
            leechers = (st.numPeers() - st.numSeeds()).coerceAtLeast(0),
            peers = st.numPeers(),
            etaSecs = if (down > 0 && wanted > done) (wanted - done) / down else null,
            streaming = streaming,
            // libtorrent4j's TorrentStatus durations are milliseconds (swig total_milliseconds), not seconds.
            elapsedSecs = st.activeDuration() / 1000,
            protocol = ProtocolStatus(
                dht = s.dht,
                lsd = s.lsd && !metered,
                pex = s.pex,
                nat = s.upnp,
                forwarding = s.upnp && st.hasIncoming(),
                persisting = stored?.persist == true,
                streaming = streaming,
            ),
        )
    }

    private fun buildLibrary(stored: Map<String, StoredTorrent>, liveInfo: Map<String, LiveInfo>): List<TorrentLibraryItem> =
        stored.values.sortedByDescending { it.addedUnix }.map { t ->
            val l = liveInfo[t.id]
            TorrentLibraryItem(
                id = t.id,
                name = t.name.ifBlank { t.label?.title ?: t.id },
                mediaId = t.label?.mediaId?.takeIf { it.isNotBlank() },
                series = t.label?.title,
                episode = t.label?.episode,
                poster = t.label?.poster,
                files = l?.files ?: t.files,
                sizeBytes = l?.sizeBytes ?: t.sizeBytes,
                progress = if (t.completed) 1.0 else l?.progress ?: t.progress,
                state = l?.state ?: TorrentState.PAUSED,
                addedUnix = t.addedUnix,
                persist = t.persist,
                path = t.movedUri ?: l?.path?.takeIf { it.isNotBlank() }?.let { File(it, t.name).path } ?: File(downloadDir, t.name).path,
                seed = t.seed,
            )
        }

    private fun resolveSource(source: String): Pair<String, String> {
        val s = source.trim()
        val lower = s.lowercase(Locale.ROOT)
        if (HEX40.matches(lower)) return lower to (labels[lower]?.magnet ?: magnetFor(lower))
        if (lower.startsWith("magnet:")) {
            val params = try {
                AddTorrentParams.parseMagnetUri(s)
            } catch (e: Exception) {
                throw AppException(AppError.Unknown("torrent:invalid_source"), e)
            }
            val hashes = params.getInfoHashes()
            val id = (if (hashes.hasV1()) hashes.getV1() else hashes.getBest()).toHex().lowercase(Locale.ROOT)
            return id to s
        }
        throw AppException(AppError.Unknown("torrent:invalid_source"))
    }

    private fun pickFile(files: List<EngineFile>, episode: Int, titles: List<String>): Int? = try {
        pickEpisodeFile(files.map { CandidateFile(it.index, it.path, it.size) }, episode, titles)
    } catch (_: Throwable) {
        // the release parser is worker a's port; a failure means "let the user pick in Files"
        null
    }

    private fun labelFor(media: Media, episode: Int?, previous: TorrentLabel?): TorrentLabel = TorrentLabel(
        mediaId = HistoryEntry.animeIdOf(media.ref.anilistId, media.ref.malId) ?: previous?.mediaId.orEmpty(),
        title = media.title,
        episode = episode,
        poster = media.cover ?: previous?.poster,
    )

    private fun titlesOf(media: Media): List<String> =
        (listOf(media.title) + media.titles.values).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun runtime(id: String): Runtime = runtimes.getOrPut(id) { Runtime() }

    private fun <T> Flow<T>.trackCollector(): Flow<T> =
        onStart { uiCollectors.incrementAndGet() }.onCompletion { uiCollectors.decrementAndGet() }

    private suspend fun currentCoroutineContextActive(): Boolean = kotlinx.coroutines.currentCoroutineContext().isActive

    private fun Exception.asAppException(): AppException = when (this) {
        is AppException -> this
        is IOException -> AppException(AppError.Unknown(message ?: "io"), this)
        else -> AppException(AppError.Unknown(message ?: this::class.java.simpleName), this)
    }

    /** Per-torrent runtime facts (not persisted). */
    private class Runtime {
        val streams = ConcurrentHashMap<Int, AtomicInteger>()
        @Volatile var playing: Boolean = false
        /** BUG-021: the player closed ([playbackEnded]); cleared by the next [play]. */
        @Volatile var ended: Boolean = false
        @Volatile var userResumed: Boolean = false
        @Volatile var endJob: Job? = null
        /** Files already queued by the next-episode preload. */
        val preloaded: MutableSet<Int> = ConcurrentHashMap.newKeySet()

        fun openStreams(): Int = streams.values.sumOf { it.get().coerceAtLeast(0) }

        /** BUG-021 (r3): the open loopback streams, closed when the grace of a closed player ends. */
        val handles: MutableSet<StreamHandle> = ConcurrentHashMap.newKeySet()
    }

    /** Engine-side facts merged into [library]. */
    private data class LiveInfo(val state: TorrentState, val progress: Double, val sizeBytes: Long, val files: Int, val path: String)

    companion object {
        const val PROVIDER_ID: String = "torrent"
        private const val PROVIDER_NAME = "Torrent"
        private const val TICK_MS = 1_000L
        private const val BATCH_GRACE_MS = 20_000L
        /** BUG-021 (r3): after closing the leftover streams, how long the grace waits for their counts to drop (a read in flight). */
        private const val LEFTOVER_WAIT_MS = 10_000L
        private const val LEFTOVER_POLL_MS = 1_000L
        private const val PREVIEW_GONE_POLLS = 50
        private const val PREVIEW_GONE_POLL_MS = 100L
        private const val IDLE_SHUTDOWN_MS = 30_000L
        private const val STARTUP_DELAY_MS = 1_500L
        private const val METADATA_POLL_MS = 250L
        private const val READ_TIMEOUT_MS = 120_000L
        private const val PIECE_POLL_MS = 200L
        private const val DEADLINE_BASE_MS = 300
        private const val DEADLINE_STEP_MS = 150
        private const val PERSIST_EVERY_TICKS = 30L
        private const val RESUME_EVERY_TICKS = 60L
        private const val PRELOAD_EVERY_TICKS = 5L
        private const val MAX_TORRENT_FILE_BYTES = 10L * 1024 * 1024
        private val HEX40 = Regex("[0-9a-f]{40}")

        private fun magnetFor(id: String): String = "magnet:?xt=urn:btih:$id"

        /** [shutdown]: how long the background work gets to end before the session stops anyway. */
        private const val SHUTDOWN_JOIN_MS = 3_000L

        private fun nowUnix(): Long = System.currentTimeMillis() / 1000
    }
}
