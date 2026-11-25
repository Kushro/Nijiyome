// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import app.nijiyome.model.ListStatus
import app.nijiyome.model.MediaSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sparkles Explorar › Facetas: the facet chips map one to one onto the server-side filters and "Mi lista". */
class ExploreFacetsTest {
    private val full = SearchFilters(
        genres = listOf("Action", "Drama"),
        tags = listOf("Magic"),
        formats = listOf("TV"),
        year = 2025,
        season = "FALL",
        status = "RELEASING",
        yearFrom = 2000,
        scoreTo = 80,
        sort = MediaSort.SCORE,
    )

    @Test
    fun `a selection round-trips through the filters and keeps the ranges and tags`() {
        val selection = facetSelectionOf(full, setOf("WATCHING"))
        assertEquals(setOf("Action", "Drama"), selection[ExploreFacetIds.GENRE])
        assertEquals(setOf("2025"), selection[ExploreFacetIds.YEAR])
        assertEquals(setOf("SCORE"), selection[ExploreFacetIds.SORT])
        assertEquals(setOf("WATCHING"), selection[ExploreFacetIds.MY_LIST])
        assertEquals(full, full.withFacets(selection))
    }

    @Test
    fun `clearing a facet drops only that filter and new picks go last`() {
        val selection = facetSelectionOf(full, emptySet()) - ExploreFacetIds.STATUS +
            (ExploreFacetIds.GENRE to setOf("Drama", "Action", "Comedy"))
        val next = full.withFacets(selection)
        assertEquals(null, next.status)
        assertEquals(listOf("Action", "Drama", "Comedy"), next.genres)
        assertEquals(full.tags, next.tags)
        assertEquals(full.scoreTo, next.scoreTo)
    }

    @Test
    fun `more filters counts the ranges and tags and clears them alone`() {
        assertEquals(3, full.moreFiltersCount())
        val cleared = full.withoutMoreFilters()
        assertEquals(0, cleared.moreFiltersCount())
        assertEquals(full.genres, cleared.genres)
        assertEquals(full.sort, cleared.sort)
    }

    @Test
    fun `mi lista matches statuses and the titles off the list`() {
        assertTrue(matchesMyList(ListStatus.WATCHING, setOf("WATCHING")))
        assertFalse(matchesMyList(ListStatus.PAUSED, setOf("WATCHING")))
        assertTrue(matchesMyList(null, setOf(MY_LIST_OUT)))
        assertFalse(matchesMyList(null, setOf("PLANNING")))
        assertEquals(1 + ListStatus.entries.size, MY_LIST_OPTIONS.size)
    }
}
