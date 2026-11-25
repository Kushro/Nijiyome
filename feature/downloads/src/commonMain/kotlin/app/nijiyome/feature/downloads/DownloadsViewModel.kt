// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nijiyome.feature.downloads.transfers.BackgroundState
import app.nijiyome.feature.downloads.transfers.TransferItem
import app.nijiyome.feature.downloads.transfers.TransferLimits
import app.nijiyome.feature.downloads.transfers.TransfersLogic
import app.nijiyome.model.AutoDownloadPreview
import app.nijiyome.model.AutoDownloadRule
import app.nijiyome.model.ChapterDownload
import app.nijiyome.model.ChapterDownloadSource
import app.nijiyome.model.ChapterKind
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadProfile
import app.nijiyome.model.DownloadRepository
import app.nijiyome.model.DownloadState
import app.nijiyome.model.DownloadsTab
import app.nijiyome.model.LocalLibraryItem
import app.nijiyome.model.Media
import app.nijiyome.model.MediaRef
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentServiceStatus
import app.nijiyome.model.torrent.TorrentSessionEntry
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.model.torrent.TorrentState
import app.nijiyome.model.torrent.TorrentStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Transferencias draws, already filtered by the search field. */
data class DownloadsUiState(
    /** The section the route asked for (`Route.Downloads(tab)`); Transferencias maps it to its queue, shelves, history or local library. */
    val tab: DownloadsTab = DownloadsTab.ACTIVE,
    val query: String = "",
    val active: List<DownloadItem> = emptyList(),
    val activeTorrents: List<TorrentLibraryItem> = emptyList(),
    /** Completed web episodes grouped by title (Descargado › Anime). */
    val anime: List<TitleGroup> = emptyList(),
    val completedTorrents: List<TorrentLibraryItem> = emptyList(),
    val manga: List<ChapterDownload> = emptyList(),
    val novels: List<ChapterDownload> = emptyList(),
    val history: List<DownloadItem> = emptyList(),
    val library: List<LocalLibraryItem> = emptyList(),
    val rules: List<AutoDownloadRule> = emptyList(),
    val usedBytes: Long? = null,
    /** ETA in seconds per download id (null = not known yet). */
    val eta: Map<String, Long> = emptyMap(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
    // Sparkles (lane S8, Transferencias): one queue over every kind, the stored torrents, the torrent history and the torrent state.
    /** Everything in flight (web, debrid, torrents, chapters), by state; each kind keeps its repository order. */
    val queue: List<TransferItem> = emptyList(),
    /** Complete torrents (seeding or stopped), after the queue in the master list. */
    val stored: List<TransferItem> = emptyList(),
    val torrentSessions: List<TorrentSessionEntry> = emptyList(),
    val torrentIds: Set<String> = emptySet(),
    val torrentsAvailable: Boolean = false,
    val torrentStatus: TorrentServiceStatus = TorrentServiceStatus(),
    val background: BackgroundState = BackgroundState(),
    val limits: TransferLimits = TransferLimits(),
    /** Desktop: folders the local library keeps watching. */
    val folders: List<String> = emptyList(),
)

data class TitleGroup(val ref: MediaRef, val title: String, val poster: String?, val episodes: List<DownloadItem>) {
    val bytes: Long get() = episodes.sumOf { it.totalBytes ?: it.downloadedBytes }
}

/** One-shot messages for the snackbar. */
sealed interface DownloadsEvent {
    data class Checked(val queued: Int) : DownloadsEvent
    data class Verified(val broken: Int) : DownloadsEvent
    data class Imported(val added: Int) : DownloadsEvent
    data class Preview(val preview: AutoDownloadPreview?) : DownloadsEvent
    data class Failed(val cause: Throwable) : DownloadsEvent

    /** A torrent of the history was added again (or is still in the library): open its detail. */
    data class OpenTorrent(val id: String) : DownloadsEvent
}

/** Pure filters / grouping of the Downloads page (JVM-tested). */
internal object DownloadsLogic {
    fun matches(query: String, vararg fields: String?): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return fields.any { it?.contains(q, ignoreCase = true) == true }
    }

    fun active(items: List<DownloadItem>): List<DownloadItem> = items.filter {
        it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PAUSED || it.state == DownloadState.FAILED
    }

    fun groups(items: List<DownloadItem>): List<TitleGroup> = items
        .filter { it.state == DownloadState.COMPLETED || it.state == DownloadState.BROKEN }
        .groupBy { it.ref }
        .map { (ref, eps) ->
            val first = eps.maxBy { it.completedAtMs ?: it.createdAtMs }
            TitleGroup(ref, first.title, first.poster, eps.sortedBy { it.episode })
        }
        .sortedByDescending { g -> g.episodes.maxOf { it.completedAtMs ?: it.createdAtMs } }

    fun torrentActive(t: TorrentLibraryItem): Boolean = t.progress < 1.0 && t.state != TorrentState.SEEDING

    /**
     * BUG-085: what "Ocupa" says: the web downloads' storage plus what the torrents already hold on disk (a paused 240 MB torrent
     * read "Ocupa 0 B"). null while neither is known.
     */
    fun usedBytes(webBytes: Long?, torrents: List<TorrentLibraryItem>): Long? {
        val torrentBytes = torrents.sumOf { (it.sizeBytes * it.progress.coerceIn(0.0, 1.0)).toLong() }
        return if (webBytes == null && torrents.isEmpty()) null else (webBytes ?: 0L) + torrentBytes
    }

    /** Remaining seconds from two samples of the progress (0..1) of one download; null while it cannot be estimated. */
    fun eta(p0: Double, t0Ms: Long, p1: Double, t1Ms: Long): Long? {
        val dp = p1 - p0
        val dt = t1Ms - t0Ms
        if (dp <= 0.0 || dt <= 0 || p1 >= 1.0) return null
        return ((1.0 - p1) * dt / dp / 1000.0).toLong()
    }
}

/**
 * Transferencias (Sparkles lane S8, before: parity wave B Downloads page of worker r): web downloads from [downloads], torrents from
 * [torrents] (library, status, session history), chapters from every [chapters] source, the local library, search, ETA, auto-download
 * rules, and the background / speed settings of [settings]. One state for both structures of the screen.
 */
class DownloadsViewModel(
    private val downloads: DownloadRepository,
    private val torrents: TorrentRepository? = null,
    private val chapters: List<ChapterDownloadSource> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val settings: SettingsRepository? = null,
) : ViewModel() {
    val items: StateFlow<List<DownloadItem>> = downloads.items

    private val local = MutableStateFlow(Local())
    private val samples = HashMap<String, Pair<Double, Long>>()
    private val _events = MutableSharedFlow<DownloadsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DownloadsEvent> = _events

    private data class Local(val tab: DownloadsTab = DownloadsTab.ACTIVE, val query: String = "", val used: Long? = null, val busy: Boolean = false)

    /** Keys removed with an undo still pending: hidden from every list until [commitRemoval] or [restore]. */
    private val hidden = MutableStateFlow<Set<String>>(emptySet())

    private val chapterFlow: Flow<List<ChapterDownload>> =
        if (chapters.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(chapters.map { src -> src.downloads().catch { emit(emptyList()) } }) { lists -> lists.toList().flatten() }
        }

    private val sources = combine(
        downloads.items,
        downloads.history(),
        downloads.library,
        downloads.rules,
        torrents?.library?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
    ) { web, hist, lib, rules, tor -> Sources(web, hist, lib, rules, tor) }

    private data class Sources(
        val web: List<DownloadItem>,
        val history: List<DownloadItem>,
        val library: List<LocalLibraryItem>,
        val rules: List<AutoDownloadRule>,
        val torrents: List<TorrentLibraryItem>,
    )

    private data class Extra(
        val sessions: List<TorrentSessionEntry> = emptyList(),
        val status: TorrentServiceStatus = TorrentServiceStatus(),
        val torrent: TorrentSettings? = null,
        val webKib: Int = 0,
        val folders: List<String> = emptyList(),
    )

    private val extra: Flow<Extra> = combine(
        torrents?.sessionHistory()?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
        torrents?.status ?: flowOf(TorrentServiceStatus()),
        settings?.settings?.map { it.torrent } ?: flowOf(null),
        settings?.settings?.map { it.downloads.speedLimitKib } ?: flowOf(0),
        downloads.libraryFolders.catch { emit(emptyList()) },
    ) { sessions, status, torrent, webKib, folders -> Extra(sessions, status, torrent, webKib, folders) }

    val state: StateFlow<DownloadsUiState> = combine(local, sources, chapterFlow, extra, hidden) { l, all, allChapters, x, gone ->
        val q = l.query
        val s = if (gone.isEmpty()) {
            all
        } else {
            all.copy(web = all.web.filterNot { "w:" + it.id in gone }, library = all.library.filterNot { "l:" + it.id in gone })
        }
        val ch = if (gone.isEmpty()) allChapters else allChapters.filterNot { "c:" + it.id in gone }
        DownloadsUiState(
            tab = l.tab,
            query = q,
            active = DownloadsLogic.active(s.web).filter { DownloadsLogic.matches(q, it.title, it.sourceLabel) },
            activeTorrents = s.torrents.filter { DownloadsLogic.torrentActive(it) && DownloadsLogic.matches(q, it.name, it.series) },
            anime = DownloadsLogic.groups(s.web).filter { DownloadsLogic.matches(q, it.title) },
            completedTorrents = s.torrents.filter { !DownloadsLogic.torrentActive(it) && DownloadsLogic.matches(q, it.name, it.series) },
            manga = ch.filter { it.kind == ChapterKind.MANGA && DownloadsLogic.matches(q, it.title, it.chapterLabel) },
            novels = ch.filter { it.kind == ChapterKind.NOVEL && DownloadsLogic.matches(q, it.title, it.chapterLabel) },
            history = s.history.filter { DownloadsLogic.matches(q, it.title, it.sourceLabel) },
            library = s.library.filter { DownloadsLogic.matches(q, it.fileName, it.title) },
            rules = s.rules,
            usedBytes = DownloadsLogic.usedBytes(l.used, s.torrents),
            eta = etaOf(s.web),
            loaded = true,
            busy = l.busy,
            queue = TransfersLogic.queue(s.web, s.torrents, ch).filter { TransfersLogic.search(it, q) },
            stored = TransfersLogic.storedTorrents(s.torrents).filter { TransfersLogic.search(it, q) },
            torrentSessions = x.sessions.filter { DownloadsLogic.matches(q, it.name, it.series) },
            torrentIds = s.torrents.map { it.id }.toSet(),
            torrentsAvailable = torrents != null,
            torrentStatus = x.status,
            background = x.torrent?.let { BackgroundState(acknowledged = it.backgroundAck, enabled = it.backgroundDownloads) } ?: BackgroundState(),
            limits = TransferLimits(
                downKib = x.torrent?.downloadLimitKib ?: 0,
                upKib = x.torrent?.uploadLimitKib ?: 0,
                webKib = x.webKib,
                schedule = x.torrent?.schedule,
            ),
            folders = x.folders,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    init {
        refreshUsage()
    }

    private fun etaOf(web: List<DownloadItem>): Map<String, Long> {
        val now = clock()
        val out = HashMap<String, Long>()
        web.filter { it.state == DownloadState.DOWNLOADING }.forEach { item ->
            val first = samples.getOrPut(item.id) { item.progress to now }
            DownloadsLogic.eta(first.first, first.second, item.progress, now)?.let { out[item.id] = it }
        }
        samples.keys.retainAll(web.filter { it.state == DownloadState.DOWNLOADING }.map { it.id }.toSet())
        return out
    }

    fun selectTab(tab: DownloadsTab) = local.update { it.copy(tab = tab) }

    fun setQuery(q: String) = local.update { it.copy(query = q) }

    fun refreshUsage() {
        viewModelScope.launch {
            val used = runCatching { downloads.storageUsedBytes() }.getOrNull()
            local.update { it.copy(used = used) }
        }
    }

    fun pause(id: String) = launch { downloads.pause(id) }
    fun resume(id: String) = launch { downloads.resume(id) }
    fun retry(id: String) = launch { downloads.retry(id) }
    fun remove(id: String) = launch {
        downloads.remove(id)
        refreshUsage()
    }
    fun clearHistory() = launch { downloads.clearHistory() }

    fun removeTitle(group: TitleGroup) = launch {
        group.episodes.forEach { downloads.remove(it.id) }
        refreshUsage()
    }

    fun pauseTorrent(id: String) = launch { torrents?.pause(id) }
    fun resumeTorrent(id: String) = launch { torrents?.resume(id) }

    fun removeChapter(c: ChapterDownload) = launch { chapters.firstOrNull { it.kind == c.kind }?.remove(c.id) }
    fun retryChapter(c: ChapterDownload) = launch { chapters.firstOrNull { it.kind == c.kind }?.retry(c.id) }

    fun verify() = busy {
        val broken = downloads.verifyIntegrity()
        _events.tryEmit(DownloadsEvent.Verified(broken.size))
    }

    fun checkNow() = busy {
        downloads.checkNow().fold(
            onSuccess = { _events.tryEmit(DownloadsEvent.Checked(it)) },
            onFailure = { _events.tryEmit(DownloadsEvent.Failed(it)) },
        )
    }

    fun setRule(rule: AutoDownloadRule) = launch { downloads.setRule(rule) }

    fun addRule(ref: MediaRef, title: String, poster: String?, profile: DownloadProfile) =
        launch { downloads.setRule(AutoDownloadRule(ref = ref, title = title, poster = poster, profile = profile)) }

    fun preview(ref: MediaRef) = launch { _events.tryEmit(DownloadsEvent.Preview(downloads.preview(ref))) }

    fun importFiles(uris: List<String>) = busy {
        downloads.importFiles(uris).fold(
            onSuccess = { _events.tryEmit(DownloadsEvent.Imported(it.size)) },
            onFailure = { _events.tryEmit(DownloadsEvent.Failed(it)) },
        )
    }

    fun scanFolder(treeUri: String) = busy {
        downloads.scanFolder(treeUri).fold(
            onSuccess = { _events.tryEmit(DownloadsEvent.Imported(it.size)) },
            onFailure = { _events.tryEmit(DownloadsEvent.Failed(it)) },
        )
    }

    fun matchLocal(id: String, media: Media?, episode: Int?) = launch { downloads.matchLocal(id, media, episode) }
    fun removeLocal(id: String, deleteFile: Boolean) = launch { downloads.removeLocal(id, deleteFile) }

    // ------------------------------------------------------------------ Transferencias (lane S8)

    /** Live figures of one torrent row (speed, peers, ETA); null without the torrent engine. */
    fun torrentStats(id: String): Flow<TorrentStats>? = torrents?.stats(id)?.catch { }

    fun pauseItems(items: Collection<TransferItem>) = launch {
        items.forEach { item ->
            // chapter sources have no pause
            if (item.torrent != null) {
                torrents?.pause(item.id)
            } else if (item.web != null) {
                downloads.pause(item.id)
            }
        }
    }

    /** Resume a paused one, retry a failed one. */
    fun resumeItems(items: Collection<TransferItem>) = launch {
        items.forEach { item ->
            val chapter = item.chapter
            if (item.torrent != null) {
                torrents?.resume(item.id)
            } else if (item.web != null) {
                if (item.canRetry) downloads.retry(item.id) else downloads.resume(item.id)
            } else if (chapter != null && item.canRetry) {
                chapters.firstOrNull { it.kind == chapter.kind }?.retry(chapter.id)
            }
        }
    }

    /** Removes now (after the confirmation): web downloads and their files, torrents with or without their data, chapters. */
    fun removeItems(items: Collection<TransferItem>, deleteFiles: Boolean) = launch {
        items.forEach { item ->
            val chapter = item.chapter
            if (item.torrent != null) {
                torrents?.remove(item.id, deleteFiles)
            } else if (item.web != null) {
                downloads.remove(item.id)
            } else if (chapter != null) {
                chapters.firstOrNull { it.kind == chapter.kind }?.remove(chapter.id)
            }
        }
        refreshUsage()
    }

    fun setKeep(items: Collection<TransferItem>, keep: Boolean) = launch {
        items.filter { it.torrent != null }.forEach { torrents?.setPersist(it.id, keep) }
    }

    /** Hides [keys] (`w:` web, `l:` local, `c:` chapter) until the undo runs out ([commitRemoval]) or is used ([restore]). */
    fun hide(keys: Collection<String>) = hidden.update { it + keys }

    fun restore(keys: Collection<String>) = hidden.update { it - keys.toSet() }

    /** The removal behind an undo: web episodes, chapters (by [chapterKind]) and local library entries (keeping or deleting the file). */
    fun commitRemoval(keys: Collection<String>, deleteLocalFile: Boolean = false, chapterKind: ChapterKind? = null) = launch {
        try {
            keys.forEach { key ->
                val id = key.substringAfter(':')
                when (key.substringBefore(':')) {
                    "w" -> downloads.remove(id)
                    "l" -> downloads.removeLocal(id, deleteLocalFile)
                    "c" -> chapters.firstOrNull { it.kind == chapterKind }?.remove(id)
                    else -> Unit
                }
            }
        } finally {
            hidden.update { it - keys.toSet() }
            refreshUsage()
        }
    }

    /** "Entendido, activar": stores the acknowledgement and turns background downloads on. */
    fun acknowledgeBackground() = launch {
        val repo = settings ?: return@launch
        repo.acknowledgeBackgroundDownloads()
        repo.setTorrentSettings(repo.settings.value.torrent.copy(backgroundAck = true, backgroundDownloads = true))
    }

    fun setBackground(enabled: Boolean) = launch {
        val repo = settings ?: return@launch
        repo.setTorrentSettings(repo.settings.value.torrent.copy(backgroundDownloads = enabled))
    }

    /** Quick limits of the torrents: download, upload and the schedule (`TorrentSettings`, sanitized by the repository). */
    fun setTorrentLimits(transform: (TorrentSettings) -> TorrentSettings) = launch {
        val repo = settings ?: return@launch
        repo.setTorrentSettings(transform(repo.settings.value.torrent))
    }

    /** Desktop: total speed of the web downloads (`DownloadSettings.speedLimitKib`, 0 = unlimited). */
    fun setWebSpeedLimit(kib: Int) = launch {
        settings?.update { it.copy(downloads = it.downloads.copy(speedLimitKib = kib).sanitized()) }
    }

    /** Empties both histories; the torrents of the library and the downloaded files stay. */
    fun clearAllHistory() = launch {
        downloads.clearHistory()
        torrents?.clearSessionHistory()
    }

    /** "Volver a añadir": a torrent that left the library is added again (streamed, not kept) by its info-hash, then opened. */
    fun reopenTorrent(entry: TorrentSessionEntry) = launch {
        val repo = torrents ?: return@launch
        if (entry.id in state.value.torrentIds) {
            _events.tryEmit(DownloadsEvent.OpenTorrent(entry.id))
        } else {
            val id = repo.addManual(entry.id, media = null, alignment = null, persist = false).getOrThrow()
            _events.tryEmit(DownloadsEvent.OpenTorrent(id))
        }
    }

    /** Desktop: stop watching a folder of the local library (its videos stay). */
    fun forgetFolder(folder: String) = launch { downloads.forgetLibraryFolder(folder) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else _events.tryEmit(DownloadsEvent.Failed(it)) }
        }
    }

    private fun busy(block: suspend () -> Unit) {
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                runCatching { block() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else _events.tryEmit(DownloadsEvent.Failed(it)) }
            } finally {
                local.update { it.copy(busy = false) }
                refreshUsage()
            }
        }
    }
}
