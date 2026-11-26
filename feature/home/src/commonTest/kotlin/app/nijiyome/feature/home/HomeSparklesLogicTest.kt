// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.CalendarStructure
import app.nijiyome.model.CalendarView
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.HomeRowIds
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sparkles (S2): the pure parts of the three Home structures and the calendar structures. */
class HomeSparklesLogicTest {
    private fun media(id: Int) = Media(
        ref = MediaRef(anilistId = id), kind = MediaKind.ANIME, title = "A$id", titles = emptyMap(), cover = null, banner = null,
        color = null, synopsis = null, genres = emptyList(), episodes = null, status = null, season = null, year = null, score = null,
        sourceProvider = "anilist",
    )

    private fun history(id: Int, completed: Boolean = false) =
        HistoryEntry("$id", "A$id", null, "$id?ep=1", 1, 10, 100, completed, Instant.EPOCH, anilistId = id, malId = null)

    @Test
    fun tonightJoinsResumeAiredAndUpcoming() {
        val now = 10_000L
        val start = 0L
        val aired = AiringEpisode(media(2), 5, now - 100)
        val watched = AiringEpisode(media(3), 4, now - 200)
        val later = AiringEpisode(media(4), 9, now + 3_600)
        val untracked = AiringEpisode(media(5), 1, now - 50)
        val sameAsResume = AiringEpisode(media(1), 2, now - 10)
        val items = HomeLogic.tonight(
            continueWatching = listOf(history(1), history(9, completed = true)),
            newSinceVisit = listOf(aired),
            today = listOf(watched, later, untracked, sameAsResume),
            tracked = { it.anilistId in setOf(1, 2, 3, 4) },
            progressOf = { ref -> mapOf(2 to 3, 3 to 4)[ref.anilistId] },
            nowSec = now,
            todayStartSec = start,
        )
        assertEquals(listOf("r", "a", "u"), items.map { it.key.substringBefore(':') })
        val a = items[1] as TonightItem.Aired
        assertEquals(2, a.behind)
        assertTrue(a.today)
        assertEquals(4, (items[2] as TonightItem.Upcoming).episode.media.ref.anilistId)
    }

    @Test
    fun agendaPrefersTrackedTitles() {
        val a = AiringEpisode(media(1), 1, 20)
        val b = AiringEpisode(media(2), 1, 10)
        assertEquals(listOf(a), HomeLogic.agenda(listOf(a, b)) { it.anilistId == 1 })
        assertEquals(listOf(b, a), HomeLogic.agenda(listOf(a, b)) { false })
    }

    @Test
    fun airStatesAroundNow() {
        val ep = AiringEpisode(media(1), 1, 1_000)
        assertEquals(AirState.NEXT, HomeLogic.airState(ep, 999))
        assertEquals(AirState.JUST_OUT, HomeLogic.airState(ep, 1_000 + 60))
        assertEquals(AirState.AIRED, HomeLogic.airState(ep, 1_000 + HomeLogic.JUST_OUT_SEC))
    }

    @Test
    fun fullRowOrderKeepsKnownIdsOnce() {
        val order = HomeLogic.fullRowOrder(listOf("top", "nope", "continuar", "top"))
        assertEquals(listOf("top", "continuar"), order.take(2))
        assertEquals(HomeRowIds.DEFAULT_ORDER.toSet(), order.toSet())
        assertEquals(HomeRowIds.DEFAULT_ORDER.size, order.size)
    }

    @Test
    fun availableRowsDropEmptyOnes() {
        val state = HomeUiState(
            extensionRows = LoadState.Ok(emptyList()),
            discover = HomeDiscoverState(
                followed = LoadState.Ok(emptyList()),
                forYou = LoadState.Ok(emptyList()),
                activity = DownloadActivity(0, 0, emptyList<DownloadItem>(), emptyList()),
            ),
        )
        val rows = state.availableRows(tonightCount = 0, hasGenres = false)
        listOf(ROW_CONTINUE, ROW_DOWNLOADS, ROW_FOLLOWED, ROW_FOR_YOU, ROW_BECAUSE, ROW_GENRES, ROW_EXTENSIONS).forEach {
            assertTrue(it !in rows, it)
        }
        // Loading rows stay: their placeholder is the content.
        assertTrue(ROW_LATEST in rows && ROW_TOP in rows && ROW_FEEDS in rows && ROW_MORE in rows)
    }

    @Test
    fun calendarStructuresMapToViews() {
        assertEquals(CalendarView.WEEK, CalendarLayout.viewFor(CalendarStructure.WEEK, CalendarView.DAY))
        assertEquals(CalendarView.MONTH, CalendarLayout.viewFor(CalendarStructure.WEEK, CalendarView.MONTH))
        assertEquals(CalendarView.DAY, CalendarLayout.viewFor(CalendarStructure.DEPARTURES, CalendarView.MONTH))
        assertEquals(CalendarView.AGENDA, CalendarLayout.viewFor(CalendarStructure.AGENDA, CalendarView.WEEK))
        val eps = listOf(AiringEpisode(media(1), 1, 10), AiringEpisode(media(2), 1, 30))
        assertEquals(1, CalendarLayout.nowIndex(eps, 20))
        assertEquals(2, CalendarLayout.nowIndex(eps, 40))
        assertEquals(BoardState.SCHEDULED, CalendarLayout.boardState(eps[1], 20))
    }
}
