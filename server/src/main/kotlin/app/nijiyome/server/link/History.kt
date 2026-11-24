// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/history.rs (AGPL-3.0, same author). CANONICAL wire shared with :core:link
// LinkHistoryClient.kt (the row and page DTOs are the phone's own).
package app.nijiyome.server.link

import app.nijiyome.link.LinkHistoryPageDto
import app.nijiyome.link.LinkHistoryRow
import app.nijiyome.model.HistoryEntry
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Watch-history sync between the user's own devices over the mTLS Link: `GET /v1/history?since=&limit=` (rows with
 * `watchedAt > since`, oldest first), `POST /v1/history` (newest `watchedAt` wins, tie -> higher progress), `DELETE /v1/history`,
 * and the 2026-11 per-device modes (`full` / `receive_only` / `paused`) with "Use this device as the source".
 */
object History {
    /** Most rows per page / per push (= the phone's `LinkClient.MAX_HISTORY_ROWS`). */
    const val MAX_ROWS = 500

    /** Body cap of `POST /v1/history`. */
    const val MAX_BODY = 256 * 1024

    fun clampLimit(limit: Long?): Int = (limit ?: MAX_ROWS.toLong()).coerceIn(1, MAX_ROWS.toLong()).toInt()

    /** Entry -> wire row: unknown poster / duration / ids omitted, `watchedAt` in UTC RFC 3339. */
    fun rowOf(e: HistoryEntry): LinkHistoryRow = LinkHistoryRow(
        animeId = e.animeId,
        title = e.title,
        poster = e.poster?.takeIf { it.isNotEmpty() },
        episodeId = e.episodeId,
        episode = e.episode,
        progressSec = e.progressSec,
        durationSec = e.durationSec?.takeIf { it > 0 },
        completed = e.completed,
        watchedAt = DateTimeFormatter.ISO_INSTANT.format(e.watchedAt),
        anilistId = e.anilistId,
        malId = e.malId,
    )

    /** Wire row -> entry; null when `watchedAt` does not parse or the row has neither an AniList nor a MAL id (E9: never stored). */
    fun entryOf(r: LinkHistoryRow): HistoryEntry? {
        if (r.anilistId == null && r.malId == null) return null
        val at = parseInstant(r.watchedAt) ?: return null
        return HistoryEntry(
            animeId = r.animeId,
            title = r.title,
            poster = r.poster?.takeIf { it.isNotEmpty() },
            episodeId = r.episodeId,
            episode = r.episode,
            progressSec = r.progressSec,
            durationSec = r.durationSec?.takeIf { it > 0 },
            completed = r.completed,
            watchedAt = at,
            anilistId = r.anilistId,
            malId = r.malId,
        )
    }

    fun parseInstant(s: String): Instant? = runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()

    /**
     * The page of [rows] already filtered to `watchedAt > since` and ordered oldest first (asked with `limit + 1`): more than
     * [limit] rows marks it truncated; the cursor is the `watchedAt` of the last row served, or [sinceUnix] when empty.
     */
    fun page(rows: List<HistoryEntry>, limit: Int, sinceUnix: Long, mode: HistoryMode? = null): LinkHistoryPageDto {
        val l = limit.coerceIn(1, MAX_ROWS)
        val served = rows.take(l)
        return LinkHistoryPageDto(
            items = served.map(::rowOf),
            nextSinceUnix = served.lastOrNull()?.watchedAt?.epochSecond ?: sinceUnix,
            truncated = rows.size > l,
            mode = mode?.wire,
        )
    }

    /** What a pull answers for a `paused` device: nothing, not truncated, the cursor unchanged. */
    fun pausedPage(sinceUnix: Long) = LinkHistoryPageDto(emptyList(), sinceUnix, truncated = false, mode = HistoryMode.PAUSED.wire)

    /** The devices a finished resync from [source] fans out to: active ones other than [source] whose mode is not `paused`. */
    fun resyncTargets(devices: List<Device>, source: String, now: Long): List<String> =
        devices.filter { it.id != source && it.isActive(now) && it.historyMode != HistoryMode.PAUSED }.map { it.id }

    /** BUG-096: the poster to keep for a row of [episodeId] that came without one: that episode's, else the newest of the series. */
    fun knownPoster(rows: List<HistoryEntry>, episodeId: String): String? {
        val usable = rows.filter { !it.poster.isNullOrEmpty() }
        return (usable.firstOrNull { it.episodeId == episodeId } ?: usable.maxByOrNull { it.watchedAt })?.poster
    }

    /** Newest `watchedAt` wins; on a tie the higher progress wins (`tk_core::HistoryEntry::wins_over`). */
    fun winsOver(a: HistoryEntry, b: HistoryEntry): Boolean = a.watchedAt > b.watchedAt || (a.watchedAt == b.watchedAt && a.progressSec > b.progressSec)
}

/** How the PC treats one paired device's history (`devices.json`, default `full`). */
enum class HistoryMode(val wire: String) {
    /** Pushes merge and pulls serve the PC history. */
    FULL("full"),
    /** Pulls are served; pushes are accepted and ignored. */
    RECEIVE_ONLY("receive_only"),
    /** Pushes are ignored and pulls answer an empty page. */
    PAUSED("paused"),
    ;

    val acceptsPushes: Boolean get() = this == FULL
    val servesPulls: Boolean get() = this != PAUSED

    companion object {
        fun fromWire(s: String?): HistoryMode? = entries.firstOrNull { it.wire == s }
    }
}
