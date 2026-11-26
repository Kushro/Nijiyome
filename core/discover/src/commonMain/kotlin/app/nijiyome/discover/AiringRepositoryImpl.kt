// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.discover

import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.AiringRepository
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.FollowedShow
import app.nijiyome.model.ListStatus
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.Media
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.PER_PAGE_AIRING
import app.nijiyome.model.inBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Parity wave B (docs/redesign/16-parity-wave-b-contract-android.md §3.1, worker r; checklist home.calendar.*, details.track-show,
 * home.followed-updates): [AiringRepository] over `MetadataRepository.airingSchedule` (AniList `Page.airingSchedules`; the response
 * cache lives in `MetadataRepositoryImpl`, plus a 10 min in-memory window cache here), followed shows in `storeDir/followed.json`, the
 * last visit and the last reminder run in `storeDir/stamps.json`. `newSinceLastVisit` = aired episodes (since the visit stamp, at most
 * [MAX_LOOKBACK_SEC] back) of the followed shows plus the WATCHING entries of [lists] when bound. Reminders: [dueReminders] is called by
 * [AiringReminderWorker] (periodic, 30 min) and returns the followed episodes with `remind == true` aired since its previous run, only
 * while [remindersEnabled] (`AppSettings.airingReminders`).
 */
class AiringRepositoryImpl(
    private val metadata: MetadataRepository,
    private val lists: ListsRepository? = null,
    private val storeDir: File,
    private val remindersEnabled: () -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
) : AiringRepository, AppDataWiper {
    @Serializable
    internal data class Stamps(val visitedAtSec: Long? = null, val remindedUpToSec: Long? = null)

    private val followedStore = JsonFileStore(File(storeDir, "followed.json"), ListSerializer(FollowedShow.serializer())) { emptyList() }
    private val stampStore = JsonFileStore(File(storeDir, "stamps.json"), Stamps.serializer()) { Stamps() }
    private val lock = Mutex()

    private val _followed = MutableStateFlow(followedStore.readBlocking())
    override val followed: StateFlow<List<FollowedShow>> = _followed.asStateFlow()

    @Volatile private var windowCache: Triple<Pair<Long, Long>, Long, List<AiringEpisode>>? = null

    private fun nowSec(): Long = clock() / 1000

    override fun isFollowed(ref: MediaRef): Flow<Boolean> = _followed.map { list -> list.any { sameTitle(it.ref, ref) } }.distinctUntilChanged()

    override suspend fun follow(media: Media, remind: Boolean): Unit = lock.withLock {
        val next = _followed.value.filterNot { sameTitle(it.ref, media.ref) } +
            FollowedShow(ref = media.ref, title = media.title, poster = media.cover, followedAtMs = clock(), remind = remind)
        _followed.value = next
        followedStore.write(next)
    }

    override suspend fun unfollow(ref: MediaRef): Unit = lock.withLock {
        val next = _followed.value.filterNot { sameTitle(it.ref, ref) }
        _followed.value = next
        followedStore.write(next)
    }

    override suspend fun setReminder(ref: MediaRef, enabled: Boolean): Unit = lock.withLock {
        val next = _followed.value.map { if (sameTitle(it.ref, ref)) it.copy(remind = enabled) else it }
        _followed.value = next
        followedStore.write(next)
    }

    override suspend fun schedule(fromEpochSec: Long, toEpochSec: Long): List<AiringEpisode> {
        if (toEpochSec <= fromEpochSec) return emptyList()
        val key = fromEpochSec to toEpochSec
        windowCache?.let { (k, at, list) -> if (k == key && clock() - at < WINDOW_CACHE_MS) return list }
        val out = inBackground { fetchPages(fromEpochSec, toEpochSec) }
        val result = AiringLogic.dedupe(out)
        windowCache = Triple(key, clock(), result)
        return result
    }

    /**
     * The whole `[from, to)` range as one paginated `airingSchedules` query (perPage [PER_PAGE_AIRING]). 0.3.1: after the first
     * page, the next ones are asked in waves of [AiringLogic.pageWaves] issued together, which the provider's batcher sends as one
     * aliased request each (a busy month: 3 requests instead of 6).
     */
    private suspend fun fetchPages(fromEpochSec: Long, toEpochSec: Long): List<AiringEpisode> = coroutineScope {
        val out = mutableListOf<AiringEpisode>()
        val maxPages = AiringLogic.maxPagesFor(fromEpochSec, toEpochSec)
        for (wave in AiringLogic.pageWaves(maxPages)) {
            val chunks = wave.map { page -> async { metadata.airingSchedule(fromEpochSec, toEpochSec, page) } }.awaitAll()
            for (chunk in chunks) {
                out += chunk
                // QA 0.3: the mapper drops rows (adult titles, rows without media), so a full AniList page can arrive with
                // fewer than PER_PAGE_AIRING items; stopping on `< PER_PAGE_AIRING` cut the week after ~49 slots (Saturday
                // and Sunday came out empty). Only a clearly short page ends the window.
                if (AiringLogic.endsWindow(chunk.size)) return@coroutineScope out
            }
        }
        out
    }

    /** Refs of the shows whose new episodes matter: followed + WATCHING / REPEATING of the list. */
    private suspend fun trackedRefs(): List<MediaRef> {
        val watching = try {
            lists?.entries(null)?.first().orEmpty().filter { it.status == ListStatus.WATCHING || it.status == ListStatus.REPEATING }.map { it.ref }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        return _followed.value.map { it.ref } + watching
    }

    override suspend fun newSinceLastVisit(): List<AiringEpisode> {
        val now = nowSec()
        val since = AiringLogic.lookbackStart(stampStore.read().visitedAtSec, now, MAX_LOOKBACK_SEC, DEFAULT_LOOKBACK_SEC)
        val tracked = trackedRefs()
        if (tracked.isEmpty()) return emptyList()
        return AiringLogic.forTracked(schedule(since, now + 1), tracked).sortedByDescending { it.airingAtEpochSec }
    }

    override suspend fun markVisited(): Unit = lock.withLock {
        stampStore.write(stampStore.read().copy(visitedAtSec = nowSec()))
    }

    /**
     * Followed episodes (remind on) that aired after the previous call; advances the stamp. Empty when reminders are off (the stamp still
     * moves so turning them back on does not replay old episodes).
     */
    suspend fun dueReminders(): List<AiringEpisode> {
        val now = nowSec()
        val stamps = stampStore.read()
        val from = stamps.remindedUpToSec?.coerceAtLeast(now - MAX_LOOKBACK_SEC) ?: (now - FIRST_RUN_LOOKBACK_SEC)
        val reminded = _followed.value.filter { it.remind }.map { it.ref }
        val due = if (!remindersEnabled() || reminded.isEmpty()) emptyList() else AiringLogic.forTracked(schedule(from, now + 1), reminded)
        lock.withLock { stampStore.write(stampStore.read().copy(remindedUpToSec = now + 1)) }
        return due
    }

    /** "Restablecer la app": forgets followed shows, the stamps and the window cache. */
    override suspend fun wipe() {
        lock.withLock {
            _followed.value = emptyList()
            windowCache = null
            storeDir.deleteRecursively()
        }
    }

    companion object {
        const val DIR_NAME: String = "discover"

        /** Notification channel of the reminders (created by [AiringReminderWorker]). */
        const val CHANNEL_ID: String = "airing"

        private const val MAX_PAGES = 6
        private const val WINDOW_CACHE_MS = 10L * 60_000
        internal const val MAX_LOOKBACK_SEC = 14L * 86_400
        internal const val DEFAULT_LOOKBACK_SEC = 7L * 86_400
        private const val FIRST_RUN_LOOKBACK_SEC = 30L * 60

        internal fun sameTitle(a: MediaRef, b: MediaRef): Boolean =
            (a.anilistId != null && a.anilistId == b.anilistId) || (a.malId != null && a.malId == b.malId) || (a.kitsuId != null && a.kitsuId == b.kitsuId)
    }
}

/** Pure helpers of the calendar (JVM-tested). */
internal object AiringLogic {
    fun dedupe(list: List<AiringEpisode>): List<AiringEpisode> =
        list.distinctBy { "${it.media.ref.anilistId}:${it.media.ref.malId}:${it.episode}" }.sortedBy { it.airingAtEpochSec }

    /**
     * MES-01: a 7-day week fits inside 6 pages (~300 slots), while a 35–42 day month grid needs up to ~20 pages in a busy season
     * (stopping at 6 cut the month off around day 13).
     */
    fun maxPagesFor(fromEpochSec: Long, toEpochSec: Long): Int {
        val days = ((toEpochSec - fromEpochSec).coerceAtLeast(0L) + 86_399L) / 86_400L
        return when {
            days <= 10L -> 6
            days <= 21L -> 12
            else -> 24
        }
    }

    /** Pages of a range asked together: page 1 alone (most weeks fit in it), then 2-3, then waves of 3 up to [maxPages]. */
    fun pageWaves(maxPages: Int): List<List<Int>> {
        if (maxPages <= 0) return emptyList()
        val waves = mutableListOf(listOf(1))
        if (maxPages >= 2) waves += (2..minOf(3, maxPages)).toList()
        if (maxPages >= 4) waves += (4..maxPages).chunked(3)
        return waves
    }

    /** A page this short ends the window (see QA 0.3 above: rows the mapper dropped make a full page look shorter). */
    fun endsWindow(size: Int): Boolean = size == 0 || size < PER_PAGE_AIRING / 2

    fun forTracked(schedule: List<AiringEpisode>, tracked: List<MediaRef>): List<AiringEpisode> =
        schedule.filter { ep -> tracked.any { AiringRepositoryImpl.sameTitle(it, ep.media.ref) } }

    /** Start of the "since last visit" window: the stamp, never older than [maxBack]; [defaultBack] without a stamp. */
    fun lookbackStart(visitedAtSec: Long?, nowSec: Long, maxBack: Long, defaultBack: Long): Long =
        (visitedAtSec ?: (nowSec - defaultBack)).coerceIn(nowSec - maxBack, nowSec)

    /** Local-day buckets of a week view: day index 0..6 from [weekStartSec] (`ZoneOffset` given in seconds). */
    fun dayIndex(airingAtSec: Long, weekStartSec: Long): Int = ((airingAtSec - weekStartSec) / 86_400).toInt()
}
