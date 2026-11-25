// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

import app.nijiyome.model.ListEntry
import app.nijiyome.model.ListStatus
import app.nijiyome.model.MediaRef
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** parity wave A (worker q, lists.library-sort-filter). */
class ListsSortFilterTest {
    private fun e(id: Int, title: String, progress: Int, total: Int?, score: Int?, updated: Long) =
        ListEntry(MediaRef(anilistId = id), title, null, ListStatus.WATCHING, progress, total, score, updatedAtMs = updated)

    private val list = listOf(
        e(1, "Ésta", progress = 6, total = 12, score = 70, updated = 10),
        e(2, "alpha", progress = 1, total = 24, score = null, updated = 30),
        e(3, "Zeta", progress = 12, total = 12, score = 90, updated = 20),
        e(4, "beta", progress = 3, total = null, score = 70, updated = 5),
    )

    @Test
    fun sorts() {
        assertEquals(listOf(2, 3, 1, 4), list.sortedFor(ListSort.UPDATED).map { it.ref.anilistId })
        assertEquals(listOf(2, 4, 1, 3), list.sortedFor(ListSort.TITLE, Locale.ENGLISH).map { it.ref.anilistId })
        assertEquals(listOf(3, 1, 2, 4), list.sortedFor(ListSort.PROGRESS).map { it.ref.anilistId })
        assertEquals(listOf(3, 1, 4, 2), list.sortedFor(ListSort.SCORE).map { it.ref.anilistId })
    }

    @Test
    fun sortsByStatusInLibraryOrder() {
        val mixed = listOf(
            list[0].copy(status = ListStatus.COMPLETED),
            list[1].copy(status = ListStatus.PLANNING),
            list[2].copy(status = ListStatus.WATCHING),
            list[3].copy(status = ListStatus.PLANNING),
        )
        // Watching, then Planning (newest change first), then Completed.
        assertEquals(listOf(3, 2, 4, 1), mixed.sortedFor(ListSort.STATUS).map { it.ref.anilistId })
    }

    @Test
    fun everyLibraryTabStillOpensAPlace() {
        assertEquals(LibraryDestination(LibraryMedium.ANIME, LibraryView.MY_LIST), LibraryTab.CONTINUE.destination())
        assertEquals(LibraryDestination(LibraryMedium.ANIME, LibraryView.MY_LIST), LibraryTab.LISTS.destination())
        assertEquals(LibraryDestination(LibraryMedium.ANIME, LibraryView.HISTORY), LibraryTab.HISTORY.destination())
        assertEquals(LibraryDestination(LibraryMedium.ANIME, LibraryView.FAVORITES), LibraryTab.FAVORITES.destination())
        assertEquals(LibraryDestination(LibraryMedium.ANIME, LibraryView.MY_LIST), LibraryTab.FAVORITES.destination(listsAvailable = false))
        assertEquals(LibraryMedium.MANGA, LibraryTab.MANGA.destination().medium)
        assertEquals(LibraryMedium.NOVEL, LibraryTab.NOVELS.destination().medium)
        assertEquals(true, LibraryTab.TORRENTS.destination().torrents)
    }

    @Test
    fun entryKeyMatchesTheCardKey() {
        assertEquals("anime:1", list[0].key)
    }

    @Test
    fun filterIgnoresCaseAndAccents() {
        assertEquals(listOf(1), list.filteredBy("esta").map { it.ref.anilistId })
        assertEquals(listOf(3, 4), list.filteredBy("ETA").map { it.ref.anilistId })
        assertEquals(list, list.filteredBy("  "))
    }

    @Test
    fun progressFractionAndMediaConversion() {
        assertEquals(0.5f, list[0].progressFraction())
        assertEquals(null, list[3].progressFraction())
        val media = list[0].toMedia()
        assertEquals(list[0].ref, media.ref)
        assertEquals(12, media.episodes)
    }
}
