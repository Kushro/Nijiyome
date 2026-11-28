// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** v1.3.5: the pure parts of the five "Esta noche" layouts (grouping and the grid's "+N" cell). */
class HomeTonightLayoutsTest {
    private fun media(id: Int) = Media(
        ref = MediaRef(anilistId = id), kind = MediaKind.ANIME, title = "A$id", titles = emptyMap(), cover = null, banner = null,
        color = null, synopsis = null, genres = emptyList(), episodes = null, status = null, season = null, year = null, score = null,
        sourceProvider = "anilist",
    )

    private fun resume(id: Int) =
        TonightItem.Resume(HistoryEntry("$id", "A$id", null, "$id?ep=1", 1, 10, 100, false, Instant.EPOCH, anilistId = id, malId = null))

    private fun aired(id: Int, at: Long) = TonightItem.Aired(AiringEpisode(media(id), 1, at), behind = 0, today = true)

    private fun later(id: Int, at: Long) = TonightItem.Upcoming(AiringEpisode(media(id), 1, at))

    @Test
    fun groupsDropEmptyKindsAndSortLaterByTime() {
        val items = listOf(resume(1), later(2, 300), aired(3, 50), later(4, 200))
        val groups = tonightGroups(items, listOf(TonightGroupKind.AIRED, TonightGroupKind.RESUME, TonightGroupKind.LATER))
        assertEquals(listOf(TonightGroupKind.AIRED, TonightGroupKind.RESUME, TonightGroupKind.LATER), groups.map { it.kind })
        assertEquals(listOf(4, 2), groups.last().items.map { (it as TonightItem.Upcoming).episode.media.ref.anilistId })
        assertEquals(listOf(TonightGroupKind.RESUME), tonightGroups(listOf(resume(1)), listOf(TonightGroupKind.AIRED, TonightGroupKind.RESUME)).map { it.kind })
    }

    @Test
    fun gridKeepsEverythingWhileTwoRodsHoldIt() {
        assertEquals(4, tonightGridShown(count = 4, perRow = 6))
        assertEquals(12, tonightGridShown(count = 12, perRow = 6))
        // 13 on two rods of 6: eleven cards and the "+2" cell.
        assertEquals(11, tonightGridShown(count = 13, perRow = 6))
        assertEquals(1, tonightGridShown(count = 5, perRow = 1))
    }
}
