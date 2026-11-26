// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

// android wave contract (docs/redesign/12 §3.14). Name and values FIXED: the shell's `Route.LibraryTab(tab)` serialises it.

/**
 * Deep-link targets of Library (`Route.LibraryTab`). Sparkles reorganises the seven old tabs into a medium switch (Anime, Manga,
 * Novela) and three views (Mi lista, Historial, Favoritos); each value still opens its place, see [destination].
 * TORRENTS left Library for Transferencias: the shell should send `Route.LibraryTab(TORRENTS)` to `Route.Downloads`; until it does,
 * Library opens its own torrents pane for it.
 */
enum class LibraryTab {
    CONTINUE,
    HISTORY,
    LISTS,
    FAVORITES,

    /** parity wave B (16 §3.5 / §3.6, worker s): manga / novel readlists (`MangaLibraryTab` / `NovelLibraryTab`). */
    MANGA,
    NOVELS,
    TORRENTS,
}

/** Lists and Favorites need the lists service (AniList / MAL accounts); the other tabs are always there. */
fun LibraryTab.isAvailable(listsAvailable: Boolean): Boolean =
    listsAvailable || (this != LibraryTab.LISTS && this != LibraryTab.FAVORITES)

/** The medium switch of Library. */
enum class LibraryMedium { ANIME, MANGA, NOVEL }

/** The views of the anime medium. CONTINUE lives at the top of Mi lista. */
enum class LibraryView { MY_LIST, HISTORY, FAVORITES }

/** Where a [LibraryTab] lands in the Sparkles Library. [torrents] = the legacy torrents pane (until the shell routes it away). */
data class LibraryDestination(val medium: LibraryMedium, val view: LibraryView, val torrents: Boolean = false)

fun LibraryTab.destination(listsAvailable: Boolean = true): LibraryDestination = when (this) {
    LibraryTab.CONTINUE, LibraryTab.LISTS -> LibraryDestination(LibraryMedium.ANIME, LibraryView.MY_LIST)
    LibraryTab.HISTORY -> LibraryDestination(LibraryMedium.ANIME, LibraryView.HISTORY)
    LibraryTab.FAVORITES -> LibraryDestination(
        LibraryMedium.ANIME,
        if (listsAvailable) LibraryView.FAVORITES else LibraryView.MY_LIST,
    )
    LibraryTab.MANGA -> LibraryDestination(LibraryMedium.MANGA, LibraryView.MY_LIST)
    LibraryTab.NOVELS -> LibraryDestination(LibraryMedium.NOVEL, LibraryView.MY_LIST)
    LibraryTab.TORRENTS -> LibraryDestination(LibraryMedium.ANIME, LibraryView.MY_LIST, torrents = true)
}
