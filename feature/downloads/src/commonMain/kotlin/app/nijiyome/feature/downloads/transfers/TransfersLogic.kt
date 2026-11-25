// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads.transfers

import androidx.compose.runtime.Immutable
import app.nijiyome.model.ChapterDownload
import app.nijiyome.model.ChapterKind
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadState
import app.nijiyome.model.torrent.BandwidthSchedule
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentSessionEntry
import app.nijiyome.model.torrent.TorrentState

// Sparkles redesign, lane S8 (Transferencias): one queue for web downloads, debrid transfers, torrents and chapter downloads. Built from
// the existing repositories (`DownloadRepository`, `TorrentRepository`, `ChapterDownloadSource`); no store of its own.

/** What a row of the queue is. Every row says it in text and with an icon. */
enum class TransferKind { WEB, DEBRID, TORRENT, MANGA, NOVEL }

/** The state a row shows (text + signal color + icon, never color alone). */
enum class TransferPhase { STARTING, DOWNLOADING, QUEUED, PAUSED, FAILED, SEEDING, COMPLETE }

/** The state filter of the queue and the master list (artifact: todos / bajando / compartiendo / pausa / error). */
enum class TransferFilter { ALL, DOWNLOADING, SEEDING, PAUSED, ERROR }

/** Sort keys of the master list (artifact: fecha / nombre / progreso / estado, plus size). */
enum class TransferSort { ADDED, NAME, SIZE, PROGRESS, STATE }

/** One transfer of any kind. Exactly one of [web], [torrent], [chapter] is set. */
@Immutable
data class TransferItem(
    /** Unique over every kind: `w:` web or debrid, `t:` torrent, `c:` chapter. */
    val key: String,
    val id: String,
    val kind: TransferKind,
    val title: String,
    val episode: Int? = null,
    val chapterLabel: String? = null,
    val poster: String? = null,
    val source: String = "",
    val sizeBytes: Long? = null,
    val doneBytes: Long = 0,
    val progress: Double = 0.0,
    val phase: TransferPhase,
    /** Raw error (code or message) of a failed web download or torrent; the screen turns it into text. */
    val error: String? = null,
    /** Torrents: kept after watching (`persist`); null for the other kinds. */
    val keep: Boolean? = null,
    val addedAtMs: Long = 0,
    /** Files of a torrent (batch when > 1). */
    val files: Int = 0,
    val web: DownloadItem? = null,
    val torrent: TorrentLibraryItem? = null,
    val chapter: ChapterDownload? = null,
) {
    val isTorrent: Boolean get() = torrent != null
    val canPause: Boolean get() = phase == TransferPhase.DOWNLOADING || phase == TransferPhase.QUEUED || phase == TransferPhase.STARTING
    val canResume: Boolean get() = phase == TransferPhase.PAUSED
    val canRetry: Boolean get() = phase == TransferPhase.FAILED
}

/** One line of the merged history (web downloads and torrent sessions). */
@Immutable
data class TransferHistoryRow(
    val key: String,
    val title: String,
    val atMs: Long,
    val torrent: Boolean,
    val failed: Boolean,
    val removed: Boolean,
    val web: DownloadItem? = null,
    val session: TorrentSessionEntry? = null,
)

/** History filter (artifact: Todo / Torrents / Web). */
enum class HistoryFilter { ALL, TORRENTS, WEB }

/** Torrent and web speed limits and the schedule, as stored today (`TorrentSettings`, `DownloadSettings.speedLimitKib`). */
@Immutable
data class TransferLimits(
    val downKib: Int = 0,
    val upKib: Int = 0,
    val webKib: Int = 0,
    val schedule: BandwidthSchedule? = null,
)

/** The background downloads notice: acknowledged once, then the switch. */
@Immutable
data class BackgroundState(val acknowledged: Boolean = true, val enabled: Boolean = true)

/**
 * The extension id a debrid stream carries (`DebridRepositoryImpl` builds it with `TORRENT_EXTENSION_ID` of :core:torrent, which this
 * module cannot see). A web download with it is a debrid transfer.
 */
internal const val DEBRID_EXTENSION_ID = "torrent"

/** Pure building blocks of Transferencias (JVM-tested). */
internal object TransfersLogic {
    fun webPhase(state: DownloadState): TransferPhase = when (state) {
        DownloadState.QUEUED -> TransferPhase.QUEUED
        DownloadState.DOWNLOADING -> TransferPhase.DOWNLOADING
        DownloadState.PAUSED -> TransferPhase.PAUSED
        DownloadState.FAILED, DownloadState.BROKEN -> TransferPhase.FAILED
        DownloadState.COMPLETED, DownloadState.REMOVING -> TransferPhase.COMPLETE
    }

    fun torrentPhase(t: TorrentLibraryItem): TransferPhase = when (t.state) {
        TorrentState.INITIALIZING -> TransferPhase.STARTING
        TorrentState.DOWNLOADING -> if (t.progress >= 1.0) TransferPhase.COMPLETE else TransferPhase.DOWNLOADING
        TorrentState.SEEDING -> TransferPhase.SEEDING
        TorrentState.PAUSED -> if (t.progress >= 1.0) TransferPhase.COMPLETE else TransferPhase.PAUSED
        TorrentState.ERROR -> TransferPhase.FAILED
    }

    fun ofWeb(d: DownloadItem): TransferItem = TransferItem(
        key = "w:" + d.id,
        id = d.id,
        kind = if (d.extId == DEBRID_EXTENSION_ID) TransferKind.DEBRID else TransferKind.WEB,
        title = d.title,
        episode = d.episode,
        poster = d.poster,
        source = d.sourceLabel,
        sizeBytes = d.totalBytes,
        doneBytes = d.downloadedBytes,
        progress = d.progress.coerceIn(0.0, 1.0),
        phase = webPhase(d.state),
        error = d.error,
        addedAtMs = d.createdAtMs,
        web = d,
    )

    fun ofTorrent(t: TorrentLibraryItem): TransferItem = TransferItem(
        key = "t:" + t.id,
        id = t.id,
        kind = TransferKind.TORRENT,
        title = t.series ?: t.name,
        episode = t.episode,
        poster = t.poster,
        source = t.name.takeIf { t.series != null }.orEmpty(),
        sizeBytes = t.sizeBytes.takeIf { it > 0 },
        doneBytes = (t.sizeBytes * t.progress.coerceIn(0.0, 1.0)).toLong(),
        progress = t.progress.coerceIn(0.0, 1.0),
        phase = torrentPhase(t),
        keep = t.persist,
        addedAtMs = t.addedUnix * 1000,
        files = t.files,
        torrent = t,
    )

    fun ofChapter(c: ChapterDownload): TransferItem = TransferItem(
        key = "c:" + c.id,
        id = c.id,
        kind = if (c.kind == ChapterKind.MANGA) TransferKind.MANGA else TransferKind.NOVEL,
        title = c.title,
        chapterLabel = c.chapterLabel,
        poster = c.poster,
        sizeBytes = c.bytes.takeIf { it > 0 },
        doneBytes = (c.bytes * c.progress.coerceIn(0.0, 1.0)).toLong(),
        progress = c.progress.coerceIn(0.0, 1.0),
        phase = webPhase(c.state),
        addedAtMs = c.createdAtMs,
        chapter = c,
    )

    private val PHASE_ORDER = listOf(
        TransferPhase.DOWNLOADING, TransferPhase.STARTING, TransferPhase.QUEUED, TransferPhase.PAUSED, TransferPhase.FAILED,
        TransferPhase.SEEDING, TransferPhase.COMPLETE,
    )

    /**
     * Everything in flight, one list: web and debrid downloads that are not finished, torrents not complete, chapters not finished.
     * The repositories keep no combined order, so each kind keeps its own order and the list goes by state (moving, waiting, paused,
     * failed), stable within a state.
     */
    fun queue(web: List<DownloadItem>, torrents: List<TorrentLibraryItem>, chapters: List<ChapterDownload>): List<TransferItem> {
        val all = web.filter { it.state in WEB_IN_FLIGHT }.map(::ofWeb) +
            torrents.filter { torrentInFlight(it) }.map(::ofTorrent) +
            chapters.filter { it.state in WEB_IN_FLIGHT }.map(::ofChapter)
        return all.sortedBy { PHASE_ORDER.indexOf(it.phase) }
    }

    /** Torrents already complete (seeding or stopped): the client's stored torrents, listed after the queue in the master list. */
    fun storedTorrents(torrents: List<TorrentLibraryItem>): List<TransferItem> =
        torrents.filterNot { torrentInFlight(it) }.map(::ofTorrent)

    fun torrentInFlight(t: TorrentLibraryItem): Boolean = t.progress < 1.0 && t.state != TorrentState.SEEDING

    private val WEB_IN_FLIGHT = setOf(DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.PAUSED, DownloadState.FAILED)

    fun matches(item: TransferItem, filter: TransferFilter): Boolean = when (filter) {
        TransferFilter.ALL -> true
        TransferFilter.DOWNLOADING -> item.phase == TransferPhase.DOWNLOADING || item.phase == TransferPhase.STARTING
        TransferFilter.SEEDING -> item.phase == TransferPhase.SEEDING
        TransferFilter.PAUSED -> item.phase == TransferPhase.PAUSED || item.phase == TransferPhase.QUEUED
        TransferFilter.ERROR -> item.phase == TransferPhase.FAILED
    }

    fun search(item: TransferItem, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return item.title.contains(q, ignoreCase = true) || item.source.contains(q, ignoreCase = true) ||
            item.chapterLabel?.contains(q, ignoreCase = true) == true
    }

    fun sort(items: List<TransferItem>, sort: TransferSort, descending: Boolean): List<TransferItem> {
        val comparator: Comparator<TransferItem> = when (sort) {
            TransferSort.ADDED -> compareBy { it.addedAtMs }
            TransferSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            TransferSort.SIZE -> compareBy { it.sizeBytes ?: 0L }
            TransferSort.PROGRESS -> compareBy { it.progress }
            TransferSort.STATE -> compareBy { PHASE_ORDER.indexOf(it.phase) }
        }
        return items.sortedWith(if (descending) comparator.reversed() else comparator)
    }

    /** Web history and torrent sessions in one list, newest first. */
    fun history(web: List<DownloadItem>, sessions: List<TorrentSessionEntry>, filter: HistoryFilter): List<TransferHistoryRow> {
        val w = if (filter == HistoryFilter.TORRENTS) emptyList() else web.map { d ->
            TransferHistoryRow(
                key = "hw:" + d.id, title = d.title, atMs = d.completedAtMs ?: d.createdAtMs, torrent = false,
                failed = d.state == DownloadState.FAILED || d.state == DownloadState.BROKEN, removed = false, web = d,
            )
        }
        val t = if (filter == HistoryFilter.WEB) emptyList() else sessions.map { s ->
            TransferHistoryRow(
                key = "ht:" + s.id + ":" + s.addedUnix, title = s.series ?: s.name, atMs = (s.removedUnix ?: s.addedUnix) * 1000, torrent = true,
                failed = false, removed = s.removedUnix != null, session = s,
            )
        }
        return (w + t).sortedByDescending { it.atMs }
    }

    /** Ids of the selected keys by kind, for the batch actions. */
    fun idsOf(items: Collection<TransferItem>, kind: (TransferItem) -> Boolean): List<String> = items.filter(kind).map { it.id }
}
