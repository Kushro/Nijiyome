// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.nijiyome.feature.search.resources.Res
import app.nijiyome.feature.search.resources.*
import app.nijiyome.model.ListStatus
import app.nijiyome.model.MediaSort
import app.nijiyome.ui.labels.genreLabel
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.patterns.Facet
import app.nijiyome.ui.patterns.FacetOption
import app.nijiyome.ui.patterns.FacetSelection
import app.nijiyome.ui.patterns.label
import java.time.Year
import org.jetbrains.compose.resources.stringResource

// Sparkles Explorar › Facetas (pattern `fac`): every option filter of the old sheet is a facet chip that says its value, opens its
// panel and clears with ×. The facets map one to one onto SearchFilters (server side) plus "Mi lista" (local).

internal object ExploreFacetIds {
    const val FORMAT = "format"
    const val STATUS = "status"
    const val GENRE = "genre"
    const val YEAR = "year"
    const val SEASON = "season"
    const val MY_LIST = "mylist"
    const val SORT = "sort"
}

/** The facet chips' selection for [filters] and the "Mi lista" facet [myList]. */
internal fun facetSelectionOf(filters: SearchFilters, myList: Set<String>): FacetSelection = buildMap {
    if (filters.formats.isNotEmpty()) put(ExploreFacetIds.FORMAT, filters.formats.toSet())
    filters.status?.let { put(ExploreFacetIds.STATUS, setOf(it)) }
    if (filters.genres.isNotEmpty()) put(ExploreFacetIds.GENRE, filters.genres.toSet())
    filters.year?.let { put(ExploreFacetIds.YEAR, setOf(it.toString())) }
    filters.season?.let { put(ExploreFacetIds.SEASON, setOf(it)) }
    if (myList.isNotEmpty()) put(ExploreFacetIds.MY_LIST, myList)
    filters.sort?.let { put(ExploreFacetIds.SORT, setOf(it.name)) }
}

/**
 * [this] with the option facets of [selection] (the ranges, tags and anything else stay as they are). Picked order is kept for the
 * multi facets already selected, new picks go last.
 */
internal fun SearchFilters.withFacets(selection: FacetSelection): SearchFilters {
    fun multi(id: String, current: List<String>): List<String> {
        val picked = selection[id].orEmpty()
        return current.filter { it in picked } + picked.filter { it !in current }
    }
    fun single(id: String): String? = selection[id].orEmpty().firstOrNull()
    return copy(
        formats = multi(ExploreFacetIds.FORMAT, formats),
        status = single(ExploreFacetIds.STATUS),
        genres = multi(ExploreFacetIds.GENRE, genres),
        year = single(ExploreFacetIds.YEAR)?.toIntOrNull(),
        season = single(ExploreFacetIds.SEASON),
        sort = single(ExploreFacetIds.SORT)?.let { id -> MediaSort.entries.firstOrNull { it.name == id } },
    )
}

/** Active filters outside the facet chips ("Más filtros"): year range, episodes, score and tags. */
internal fun SearchFilters.moreFiltersCount(): Int =
    tags.size +
        (if (yearFrom != null || yearTo != null) 1 else 0) +
        (if (episodesFrom != null || episodesTo != null) 1 else 0) +
        (if (scoreFrom != null || scoreTo != null) 1 else 0)

/** [this] without the "Más filtros" part. */
internal fun SearchFilters.withoutMoreFilters(): SearchFilters =
    copy(tags = emptyList(), yearFrom = null, yearTo = null, episodesFrom = null, episodesTo = null, scoreFrom = null, scoreTo = null)

/** "Mi lista" options: not on the list, then the six statuses. */
internal val MY_LIST_OPTIONS: List<String> = listOf(MY_LIST_OUT) + ListStatus.entries.map { it.name }

/** The facet chips of Explorar, labelled in the user's language. [withMyList] adds "Mi lista" (only when the list is reachable). */
@Composable
internal fun exploreFacets(state: SearchUiState, withMyList: Boolean): List<Facet> {
    val thisYear = remember { Year.now().value }
    val years = remember(thisYear, state.filters.year) { (yearChoices(thisYear) + listOfNotNull(state.filters.year)).distinct() }
    val genres = (state.genreOptions + state.filters.genres).distinct().sorted()
    val facets = mutableListOf(
        Facet(
            ExploreFacetIds.FORMAT,
            stringResource(Res.string.search_format),
            FORMATS.map { FacetOption(it, formatLabel(it)) },
        ),
        Facet(
            ExploreFacetIds.STATUS,
            stringResource(Res.string.search_status),
            STATUSES.map { FacetOption(it, statusLabel(it) ?: it) },
            single = true,
        ),
        Facet(
            ExploreFacetIds.GENRE,
            stringResource(Res.string.search_genre),
            genres.map { FacetOption(it, genreLabel(it)) },
            searchable = true,
        ),
        Facet(
            ExploreFacetIds.YEAR,
            stringResource(Res.string.search_year),
            years.map { FacetOption(it.toString(), it.toString()) },
            single = true,
            searchable = false,
        ),
        Facet(
            ExploreFacetIds.SEASON,
            stringResource(Res.string.search_season),
            SEASONS.map { FacetOption(it, seasonLabel(it)) },
            single = true,
        ),
    )
    if (withMyList) {
        facets += Facet(
            ExploreFacetIds.MY_LIST,
            stringResource(Res.string.rd_s3_facet_my_list),
            MY_LIST_OPTIONS.map { id ->
                val status = ListStatus.entries.firstOrNull { it.name == id }
                FacetOption(id, if (status == null) stringResource(Res.string.rd_s3_facet_my_list_out) else status.slot().label())
            },
        )
    }
    facets += Facet(
        ExploreFacetIds.SORT,
        stringResource(Res.string.search_sort),
        SORTS.map { FacetOption(it.name, stringResource(it.labelRes())) },
        single = true,
    )
    return facets
}
