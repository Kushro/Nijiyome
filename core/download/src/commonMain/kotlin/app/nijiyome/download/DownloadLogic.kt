// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.AutoDownloadRule
import app.nijiyome.model.Container
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadProfile
import app.nijiyome.model.DownloadState
import app.nijiyome.model.DownloadsSummary
import app.nijiyome.model.Episode
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.MediaRef
import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SourceKind
import app.nijiyome.release.isVideoPath
import app.nijiyome.release.parseRelease
import java.io.File
import java.time.LocalDate

/**
 * Pure rules of the web downloads (parity wave B, 16 §3.2, worker r), JVM-tested in `DownloadRepositoryImplTest`. Port of the classic
 * `download-monitor.ts` (queue states), `automation-engine.ts` (auto-download: new aired episodes past the last one, profile caps) and
 * `MobileDownloadsUI.tsx` (integrity, free watched episodes).
 */
internal object DownloadLogic {
    const val GIB: Long = 1024L * 1024L * 1024L

    /** Max episodes one rule queues per check (classic automation queues at most a few at a time). */
    const val MAX_AUTO_PER_RULE = 3

    /** `HistoryEntry.animeId` shape: `"<anilistId>"`, `"mal:<id>"`, or `"kitsu:<id>"` as a last resort. */
    fun mediaId(ref: MediaRef): String =
        HistoryEntry.animeIdOf(ref.anilistId, ref.malId) ?: ref.kitsuId?.let { "kitsu:$it" } ?: "unknown"

    /** `DownloadItem.id` = Media3 `DownloadRequest.id` = `HistoryEntry.episodeId` shape. */
    fun itemId(ref: MediaRef, episode: Int): String = "${mediaId(ref)}?ep=$episode"

    /** Directory name of a title under the download folder (no `:` / `?` so it is a valid file name everywhere). */
    fun dirName(ref: MediaRef): String = mediaId(ref).replace(':', '_')

    fun fileName(episode: Int, container: Container): String = "%03d.%s".format(episode, extensionOf(container))

    fun extensionOf(container: Container): String = when (container) {
        Container.HLS -> "ts"
        else -> "mp4"
    }

    fun mimeOf(container: Container): String = when (container) {
        Container.HLS -> "video/mp2t"
        else -> "video/mp4"
    }

    /** The active statuses (counted by You / Home and kept out of History). */
    fun isActive(state: DownloadState): Boolean =
        state == DownloadState.QUEUED || state == DownloadState.DOWNLOADING || state == DownloadState.PAUSED

    fun summary(items: List<DownloadItem>): DownloadsSummary = DownloadsSummary(
        active = items.count { it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING },
        completed = items.count { it.state == DownloadState.COMPLETED },
        failed = items.count { it.state == DownloadState.FAILED || it.state == DownloadState.BROKEN },
        bytes = items.filter { it.state == DownloadState.COMPLETED }.sumOf { it.totalBytes ?: it.downloadedBytes },
    )

    /** Newest first (the order of `DownloadRepository.items`). */
    fun sorted(items: Collection<DownloadItem>): List<DownloadItem> = items.sortedByDescending { it.createdAtMs }

    /** History tab: finished or failed, newest finish first. */
    fun history(items: List<DownloadItem>, limit: Int): List<DownloadItem> = items
        .filter { it.state == DownloadState.COMPLETED || it.state == DownloadState.FAILED || it.state == DownloadState.BROKEN }
        .sortedByDescending { it.completedAtMs ?: it.createdAtMs }
        .take(limit)

    /** Storage limit (0 = unlimited): true when [usedBytes] already reaches the limit. */
    fun overLimit(usedBytes: Long, limitGib: Int): Boolean = limitGib > 0 && usedBytes >= limitGib * GIB

    /** Max resolution allowed by a profile (null = no cap). */
    fun capOf(profile: DownloadProfile): Int? = when (profile) {
        DownloadProfile.BEST -> null
        DownloadProfile.BALANCED_1080 -> 1080
        DownloadProfile.SAVER_720 -> 720
    }

    private fun heightOf(q: Quality): Int? = when (q) {
        Quality.UNKNOWN -> null
        Quality.P480 -> 480
        Quality.P720 -> 720
        Quality.P1080 -> 1080
        Quality.P1440 -> 1440
        Quality.P2160 -> 2160
    }

    /**
     * Web candidates a profile accepts, best first: BEST keeps the ranking; the capped profiles drop what is above the cap (unknown
     * quality passes: HLS masters are capped again by [pickVariant]). [preferredServer] / [preferredLang] move matching sources first.
     */
    fun candidatesFor(
        ranked: List<RankedSource>,
        profile: DownloadProfile,
        preferredServer: String? = null,
        preferredLang: String? = null,
    ): List<RankedSource> {
        val cap = capOf(profile)
        val web = ranked.filter { r ->
            val c = r.candidate
            r.extEnabled && c.kind == SourceKind.WEB && c.web != null && (cap == null || (heightOf(c.quality) ?: 0) <= cap)
        }
        val server = preferredServer?.lowercase()
        val lang = preferredLang?.lowercase()
        return web.sortedWith(
            compareByDescending<RankedSource> { r -> server != null && r.candidate.label.lowercase().contains(server) }
                .thenByDescending { r -> lang != null && (lang in r.candidate.dubs || lang in r.candidate.subs) },
        )
    }

    /** One variant of an HLS master: height (null = unknown) and peak bitrate. */
    data class Variant(val index: Int, val height: Int?, val bitrate: Int)

    /** The variant a profile downloads: the best under the cap, or the smallest one when every variant is above it. */
    fun pickVariant(variants: List<Variant>, profile: DownloadProfile): Variant? {
        if (variants.isEmpty()) return null
        val cap = capOf(profile)
        val order = compareBy<Variant>({ it.height ?: 0 }, { it.bitrate })
        val allowed = if (cap == null) variants else variants.filter { (it.height ?: 0) <= cap }
        return allowed.maxWithOrNull(order) ?: variants.minWithOrNull(order)
    }

    /** Integrity of a completed item given what the file looks like: missing, empty or shorter than what was written = broken. */
    fun isBroken(item: DownloadItem, exists: Boolean, length: Long): Boolean {
        if (item.state != DownloadState.COMPLETED) return false
        if (!exists || length <= 0L) return true
        val expected = item.totalBytes ?: return false
        return length < expected
    }

    /** `autoEvictWatched`: completed downloads whose history row is completed (same title id + episode). */
    fun watchedToEvict(items: List<DownloadItem>, history: List<HistoryEntry>): List<DownloadItem> {
        val done = history.filter { it.completed }.map { it.animeId to it.episode }.toSet()
        return items.filter { it.state == DownloadState.COMPLETED && (mediaId(it.ref) to it.episode) in done }
    }

    /**
     * Auto-download: aired episodes after `rule.lastEpisode` not downloaded yet, oldest first, at most [MAX_AUTO_PER_RULE]. An episode
     * with no air date counts as aired when its number is ≤ [airedCount] (AniList `nextAiringEpisode - 1` / episode count).
     */
    fun newEpisodes(rule: AutoDownloadRule, episodes: List<Episode>, have: Set<Int>, today: LocalDate, airedCount: Int? = null): List<Int> =
        episodes.asSequence()
            .filter { it.number > rule.lastEpisode && it.number !in have }
            .filter { ep -> aired(ep, today, airedCount) }
            .map { it.number }
            .distinct()
            .sorted()
            .take(MAX_AUTO_PER_RULE)
            .toList()

    private fun aired(ep: Episode, today: LocalDate, airedCount: Int?): Boolean {
        val date = ep.airDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        return when {
            date != null -> !date.isAfter(today)
            airedCount != null -> ep.number <= airedCount
            else -> true
        }
    }

    /** Episodes of the season sheet that still need a download (not completed nor already queued). */
    fun missingEpisodes(wanted: List<Int>, existing: Map<Int, DownloadItem>): List<Int> =
        wanted.distinct().sorted().filter { ep ->
            val cur = existing[ep]
            cur == null || cur.state == DownloadState.FAILED || cur.state == DownloadState.BROKEN
        }

    /** Local library: release-name parse of a file name (title + first episode); null fields when the name says nothing. */
    data class LocalGuess(val title: String?, val episode: Int?)

    fun guessLocal(fileName: String): LocalGuess {
        val tags = runCatching { parseRelease(fileName) }.getOrNull()
        return LocalGuess(tags?.title?.takeIf { it.isNotBlank() }, tags?.episodes?.first)
    }

    fun isVideo(name: String): Boolean = isVideoPath(name)

    /** Stable id of a library file (its uri). */
    fun localId(uri: String): String = "local:" + uri.hashCode().toUInt().toString(16) + ":" + uri.length

    /** Same title: AniList id, else MAL id, else Kitsu id when neither of the others is known. */
    fun sameRef(a: MediaRef, b: MediaRef): Boolean =
        (a.anilistId != null && a.anilistId == b.anilistId) || (a.malId != null && a.malId == b.malId) ||
            (a.anilistId == null && a.malId == null && a.kitsuId != null && a.kitsuId == b.kitsuId)

    /** Bytes of every regular file below [dir]. */
    fun folderBytes(dir: File): Long = if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/** 1.1 (F7): the `debrid:*` code of a failed link refresh (the download's error text; never a URL). */
internal fun debridErrorCode(e: Throwable): String =
    ((e as? app.nijiyome.model.AppException)?.error as? app.nijiyome.model.AppError.Unknown)?.msg?.takeIf { it.startsWith("debrid:") }
        ?: if ((e as? app.nijiyome.model.AppException)?.error is app.nijiyome.model.AppError.Offline) "debrid:offline" else "debrid:refresh_failed"

/**
 * 1.1 (F7): Media3 cache key of a debrid download. Debrid links change every time they are renewed; with the URL as the key (the
 * default) a resumed download would start again from zero. The key stays the same for the same file of the same torrent.
 */
internal fun debridCacheKey(ref: app.nijiyome.model.torrent.DebridFileRef): String = "debrid:${ref.service.id}:${ref.infoHash.lowercase()}:${ref.fileId}"

/** 1.1 review #6: the outcome of asking a debrid service for a fresh link (no shared "last error" state). */
internal sealed interface DebridRenewal {
    data class Ok(val url: String) : DebridRenewal

    /** [code] = the `debrid:*` code to show (`DownloadItem.error`). */
    data class Failed(val code: String) : DebridRenewal
}

/** 1.1 review #6: a fresh link for [ref] from [repo]; never throws (cancellation aside). */
internal suspend fun renewDebridLink(ref: app.nijiyome.model.torrent.DebridFileRef, repo: app.nijiyome.model.torrent.DebridRepository?): DebridRenewal {
    if (repo == null) return DebridRenewal.Failed("debrid:not_linked:${ref.service.id}")
    return repo.refreshLink(ref).fold(onSuccess = { DebridRenewal.Ok(it) }, onFailure = { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        DebridRenewal.Failed(debridErrorCode(e))
    })
}

private val URL_IN_TEXT = Regex("""(?i)\b[a-z][a-z0-9+.-]*://\S+""")

/** 1.1 review #11: [text] without any URL in it (a signed CDN link must not end up in downloads.json or on screen). */
internal fun redactUrls(text: String): String = URL_IN_TEXT.replace(text, "<url>")

/**
 * 1.1 review #7 / #11: the error text stored for a failed download. A debrid code (`debrid:torbox:ACTIVE_LIMIT`) is kept bare (the
 * Downloads page translates it); for a debrid download anything else is stored without URLs. Other downloads: [raw] as before.
 */
internal fun storedDownloadError(raw: String?, debrid: Boolean, fallback: String): String {
    val text = raw ?: return fallback
    DEBRID_CODE.find(text)?.let { return it.value }
    return (if (debrid) redactUrls(text) else text).take(200)
}

private val DEBRID_CODE = Regex("""debrid:[A-Za-z0-9_:]+""")

/** 1.1 review #1 (Android Media3 downloads): this request goes through the no-downgrade client (https, to a debrid link's host). */
internal fun httpsOnlyRequest(url: okhttp3.HttpUrl, httpsOnlyHosts: Set<String>): Boolean = url.isHttps && url.host in httpsOnlyHosts
