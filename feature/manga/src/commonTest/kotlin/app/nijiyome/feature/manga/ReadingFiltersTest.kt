// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.model.LoadState
import app.nijiyome.model.MangaChapter
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MediaSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFiltersTest {
    @Test
    fun aSortOtherThanTheDefaultKeepsTheResultsOpen() {
        assertFalse(MangaFilters().active)
        assertTrue(MangaFilters(sort = MediaSort.TRENDING).active)
        assertEquals(MediaSort.TRENDING, MangaFilters(sort = MediaSort.TRENDING).toQuery().sort)
    }

    @Test
    fun theGroupFilterNarrowsTheShownChapters() {
        fun ch(n: Int, group: String) = MangaChapter(id = "c$n", extensionId = "e", sourceId = "s", itemId = "i", number = n.toDouble(), group = group)
        val ui = MangaDetailsUi(chapters = LoadState.Ok(listOf(ch(1, "A"), ch(2, "B"), ch(3, "A"))), tools = ChapterTools(group = "A"))
        assertEquals(listOf("c3", "c1"), ui.shown.map { it.id })
        assertEquals(listOf("A", "B"), ui.groups)
    }

    @Test
    fun mangaCardsAreKeyedAsReadingMaterial() {
        assertEquals("manga:42", mangaCardKey(MediaRef(anilistId = 42), "x"))
        assertEquals("manga:x", mangaCardKey(MediaRef(), "x"))
    }
}
