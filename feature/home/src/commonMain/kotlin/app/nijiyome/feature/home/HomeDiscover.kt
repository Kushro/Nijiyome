// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadState
import app.nijiyome.model.ExtensionHomeRow
import app.nijiyome.model.FollowedShow
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.HomeRowIds
import app.nijiyome.model.ListEntry
import app.nijiyome.model.ListStatus
import app.nijiyome.model.LoadState
import app.nijiyome.model.MatureContentMode
import app.nijiyome.model.Media
import app.nijiyome.model.MediaRef
import app.nijiyome.model.RecommendationItem
import app.nijiyome.model.torrent.TorrentLibraryItem

// Parity wave B (docs/redesign/16-parity-wave-b-contract-android.md §3.1, worker r): the rows Home gains in wave B. Kept apart from
// HomeUiState.feeds (the wave android rows) so each new row loads, fails and retries on its own.

/** Top 10 tabs (classic TopAnimeSection: Hoy / Semana / Mes). */
enum class TopWindow { TODAY, WEEK, MONTH }

/** One "infinite" genre row appended when Home is scrolled to the end (classic InfiniteHomeSections). */
data class GenreRow(val genre: String, val state: LoadState<List<Media>>)

/** "Porque viste <title>" (classic BecauseYouWatched). */
data class BecauseRow(val sourceTitle: String, val state: LoadState<List<Media>>)

/** Row "Descargando": torrents of :core:torrent (q) + web downloads of :core:download (r). */
data class DownloadActivity(val torrentActive: Int, val webActive: Int, val web: List<DownloadItem>, val torrents: List<TorrentLibraryItem>) {
    val total: Int get() = torrentActive + webActive
}

data class HomeDiscoverState(
    val latest: LoadState<List<AiringEpisode>> = LoadState.Loading,
    val topWindow: TopWindow = TopWindow.TODAY,
    val top10: LoadState<List<Media>> = LoadState.Loading,
    val upcoming: LoadState<List<Media>> = LoadState.Loading,
    val favourites: LoadState<List<Media>> = LoadState.Loading,
    val because: BecauseRow? = null,
    val genreCloud: List<String> = emptyList(),
    val genreRows: List<GenreRow> = emptyList(),
    /** True once every genre of [genreCloud] has a row. */
    val genreRowsDone: Boolean = false,
    val followed: LoadState<List<AiringEpisode>> = LoadState.Loading,
    val forYou: LoadState<List<RecommendationItem>> = LoadState.Loading,
    val activity: DownloadActivity? = null,
    /** extension id -> display name (custom-provider rows show the NAME, not the id). */
    val extensionNames: Map<String, String> = emptyMap(),
    val matureContent: MatureContentMode = MatureContentMode.WARN,
    /** Sparkles (S2): what airs today in the user's zone (agenda "Hoy en emisión" and the "Esta noche" row). */
    val today: LoadState<List<AiringEpisode>> = LoadState.Loading,
    /** Sparkles (S2): the user's AniList / MAL list, for the status, progress and "new" flags of the cards. */
    val listEntries: List<ListEntry> = emptyList(),
    /** Sparkles (S2): shows followed in the calendar, with their per-title reminder switch. */
    val followedShows: List<FollowedShow> = emptyList(),
) {
    private val entriesByAni: Map<Int, ListEntry> by lazy { listEntries.mapNotNull { e -> e.ref.anilistId?.let { it to e } }.toMap() }
    private val entriesByMal: Map<Int, ListEntry> by lazy { listEntries.mapNotNull { e -> e.ref.malId?.let { it to e } }.toMap() }

    /** The list entry of [ref], matched by AniList or MAL id. */
    fun entryFor(ref: MediaRef): ListEntry? = ref.anilistId?.let(entriesByAni::get) ?: ref.malId?.let(entriesByMal::get)

    fun followedShow(ref: MediaRef): FollowedShow? = followedShows.firstOrNull { sameRef(it.ref, ref) }

    /** The bell of a title is on: followed with its reminder switch on. */
    fun reminds(ref: MediaRef): Boolean = followedShow(ref)?.remind == true

    /** Followed in the calendar or WATCHING / REPEATING on the list. */
    fun isTracked(ref: MediaRef): Boolean =
        followedShow(ref) != null || entryFor(ref)?.status.let { it == ListStatus.WATCHING || it == ListStatus.REPEATING }

    /** Any wave B row with something to show (used by [HomeUiState.isEmpty]). */
    val hasContent: Boolean
        get() = listOf(latest, top10, upcoming, favourites, followed, today).any { (it as? LoadState.Ok<*>)?.value.let { v -> v is List<*> && v.isNotEmpty() } } ||
            (forYou as? LoadState.Ok)?.value?.isNotEmpty() == true ||
            genreRows.any { (it.state as? LoadState.Ok)?.value?.isNotEmpty() == true } ||
            (activity?.total ?: 0) > 0
}

/** Pure helpers of Home (JVM-tested). */
internal object HomeLogic {
    /** Classic mature detection on the fields the Next `Media` carries: AniList puts every `isAdult` title under the genre Hentai. */
    fun isMature(media: Media): Boolean = media.genres.any { it.equals("Hentai", ignoreCase = true) }

    /** "Últimos episodios": aired in the last 7 days, newest first, one card per series (its latest episode). */
    fun latestEpisodes(schedule: List<AiringEpisode>, nowSec: Long, limit: Int = 20): List<AiringEpisode> =
        schedule.filter { it.airingAtEpochSec <= nowSec }
            .sortedByDescending { it.airingAtEpochSec }
            .distinctBy { it.media.ref.anilistId ?: it.media.ref.malId ?: it.media.title.hashCode() }
            .take(limit)

    /**
     * Coordinates of a provider title in `HistoryEntry.animeId` (`"ext:<extension>:<source>:<item>"`, `ProviderEntryRef.historyId`).
     * Extension ids may contain `:` (`server:<id>/<ext>`), so the source and the item are taken from the end.
     */
    fun parseProviderHistoryId(animeId: String): Triple<String, String, String>? {
        if (!animeId.startsWith("ext:")) return null
        val rest = animeId.removePrefix("ext:")
        val item = rest.substringAfterLast(':', "")
        val head = rest.substringBeforeLast(':', "")
        val source = head.substringAfterLast(':', "")
        val ext = head.substringBeforeLast(':', "")
        if (ext.isEmpty() || source.isEmpty() || item.isEmpty()) return null
        return Triple(ext, source, item)
    }

    /** Header label of an extension row: "<row> · <extension name>" when the name is known. */
    fun extensionRowSubtitle(row: ExtensionHomeRow, names: Map<String, String>): String = names[row.extensionId] ?: row.extensionId

    fun activity(torrentActive: Int, web: List<DownloadItem>, torrents: List<TorrentLibraryItem>): DownloadActivity {
        val webActive = web.filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }
        val torrentRows = torrents.filter { it.progress < 1.0 && it.state != app.nijiyome.model.torrent.TorrentState.PAUSED }
        return DownloadActivity(torrentActive, webActive.size, webActive, torrentRows)
    }

    private fun seriesKey(media: Media): String = media.ref.anilistId?.let { "a$it" } ?: media.ref.malId?.let { "m$it" } ?: "t:" + media.title

    /**
     * The "Esta noche" row: the first titles to resume, then what came out for tracked shows (since the last visit or earlier today)
     * and is not watched yet, then what airs later today, then the rest of the resume list. One card per series in each group.
     */
    fun tonight(
        continueWatching: List<HistoryEntry>,
        newSinceVisit: List<AiringEpisode>,
        today: List<AiringEpisode>,
        tracked: (MediaRef) -> Boolean,
        progressOf: (MediaRef) -> Int?,
        nowSec: Long,
        todayStartSec: Long,
    ): List<TonightItem> {
        val resume: List<TonightItem> = continueWatching.filter { !it.completed }.map { TonightItem.Resume(it) }
        val resumed = continueWatching.mapNotNull { it.anilistId }.toSet()
        fun inResume(ep: AiringEpisode) = ep.media.ref.anilistId?.let { it in resumed } == true
        val aired = (newSinceVisit + today.filter { tracked(it.media.ref) && it.airingAtEpochSec <= nowSec })
            .sortedByDescending { it.airingAtEpochSec }
            .distinctBy { seriesKey(it.media) }
            .filterNot { inResume(it) }
            .mapNotNull { ep ->
                val seen = progressOf(ep.media.ref)
                if (seen != null && seen >= ep.episode) return@mapNotNull null
                TonightItem.Aired(ep, behind = seen?.let { ep.episode - it } ?: 0, today = ep.airingAtEpochSec >= todayStartSec)
            }
        val airedKeys = aired.map { seriesKey(it.episode.media) }.toSet()
        val upcoming = today.filter { tracked(it.media.ref) && it.airingAtEpochSec > nowSec }
            .sortedBy { it.airingAtEpochSec }
            .distinctBy { seriesKey(it.media) }
            .filterNot { seriesKey(it.media) in airedKeys || inResume(it) }
            .map { TonightItem.Upcoming(it) }
        return resume.take(RESUME_FIRST) + aired + upcoming + resume.drop(RESUME_FIRST)
    }

    /** Agenda "Hoy en emisión": the tracked shows of today, or the whole day when none is tracked; in time order. */
    fun agenda(today: List<AiringEpisode>, tracked: (MediaRef) -> Boolean): List<AiringEpisode> =
        today.filter { tracked(it.media.ref) }.ifEmpty { today }.sortedBy { it.airingAtEpochSec }

    fun airState(ep: AiringEpisode, nowSec: Long): AirState = when {
        ep.airingAtEpochSec > nowSec -> AirState.NEXT
        nowSec - ep.airingAtEpochSec < JUST_OUT_SEC -> AirState.JUST_OUT
        else -> AirState.AIRED
    }

    /** Every Home row id in the user's order (known ids only, no duplicates), hidden ones included: the edit panel's list. */
    fun fullRowOrder(order: List<String>): List<String> {
        val listed = order.filter { it in HomeRowIds.DEFAULT_ORDER }.distinct()
        return listed + HomeRowIds.DEFAULT_ORDER.filterNot { it in listed }
    }

    private const val RESUME_FIRST = 2
    const val JUST_OUT_SEC = 30 * 60L
}

/** One card of the "Esta noche" row. [key] is unique inside the row. */
internal sealed interface TonightItem {
    val key: String

    data class Resume(val entry: HistoryEntry) : TonightItem {
        override val key: String get() = "r:" + entry.episodeId
    }

    /** A new episode is out; [behind] = episodes out that the list says are unseen (0 = unknown). */
    data class Aired(val episode: AiringEpisode, val behind: Int, val today: Boolean) : TonightItem {
        override val key: String get() = "a:" + episode.media.title + ":" + (episode.media.ref.anilistId ?: episode.media.ref.malId) + ":" + episode.episode
    }

    data class Upcoming(val episode: AiringEpisode) : TonightItem {
        override val key: String get() = "u:" + episode.media.title + ":" + (episode.media.ref.anilistId ?: episode.media.ref.malId) + ":" + episode.episode
    }
}

/** Airing state of an episode of today: out in the last half hour, out earlier, or still to come. */
internal enum class AirState { JUST_OUT, AIRED, NEXT }
