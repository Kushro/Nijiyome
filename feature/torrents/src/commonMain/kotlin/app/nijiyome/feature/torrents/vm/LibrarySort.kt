// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.vm

import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentState

/** Sort keys of the desktop torrent library (Tatakai Next's library table: name, episode, size, progress, date; plus the state). */
enum class LibrarySort { ADDED, NAME, EPISODE, SIZE, PROGRESS, STATE }

/** The order of the states when sorting by [LibrarySort.STATE]: what is moving first, then what waits, then what failed. */
private val STATE_ORDER = listOf(TorrentState.DOWNLOADING, TorrentState.SEEDING, TorrentState.INITIALIZING, TorrentState.PAUSED, TorrentState.ERROR)

/**
 * [items] (the engine's newest-first order) sorted by [sort], ascending unless [descending]. Ties keep the incoming order; a missing
 * episode goes last in both directions. Pure, unit tested.
 */
fun sortLibrary(items: List<TorrentLibraryItem>, sort: LibrarySort, descending: Boolean): List<TorrentLibraryItem> {
    if (sort == LibrarySort.EPISODE) {
        val (numbered, rest) = items.partition { it.episode != null }
        val byEpisode = if (descending) numbered.sortedByDescending { it.episode } else numbered.sortedBy { it.episode }
        return byEpisode + rest
    }
    val comparator: Comparator<TorrentLibraryItem> = when (sort) {
        LibrarySort.ADDED -> compareBy { it.addedUnix }
        LibrarySort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.series ?: it.name }
        LibrarySort.SIZE -> compareBy { it.sizeBytes }
        LibrarySort.PROGRESS -> compareBy { it.progress }
        LibrarySort.STATE -> compareBy { STATE_ORDER.indexOf(it.state).let { i -> if (i < 0) STATE_ORDER.size else i } }
        LibrarySort.EPISODE -> error("handled above")
    }
    return items.sortedWith(if (descending) comparator.reversed() else comparator)
}
