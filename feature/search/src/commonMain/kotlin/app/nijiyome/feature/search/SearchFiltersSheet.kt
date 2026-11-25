package app.nijiyome.feature.search

import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.feature.search.resources.Res
import app.nijiyome.feature.search.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.model.MediaSort
import app.nijiyome.model.MediaTag
import app.nijiyome.ui.labels.genreLabel
import java.time.Year
import kotlin.math.roundToInt

internal val SEASONS = listOf("WINTER", "SPRING", "SUMMER", "FALL")
internal val STATUSES = listOf("RELEASING", "FINISHED", "NOT_YET_RELEASED", "CANCELLED", "HIATUS")
internal val FORMATS = listOf("TV", "TV_SHORT", "MOVIE", "OVA", "ONA", "SPECIAL", "MUSIC")
internal val SORTS = listOf(
    MediaSort.RELEVANCE, MediaSort.POPULARITY, MediaSort.TRENDING, MediaSort.SCORE, MediaSort.START_DATE, MediaSort.TITLE,
    MediaSort.FAVOURITES, MediaSort.EPISODES,
)
internal const val YEARS = 12

/** The exact-year choices: next year down to [YEARS] chips. */
internal fun yearChoices(thisYear: Int): List<Int> = ((thisYear + 1) downTo thisYear - YEARS + 2).toList()
private const val TAGS_COLLAPSED = 24

/** Slider bounds; a thumb at its bound means "no limit" on that side (the filter is not sent). */
internal const val YEAR_MIN = 1960
internal const val EPISODES_MAX = 200
internal const val SCORE_MAX = 100

/** Thumb positions -> filter bounds: a thumb resting on the slider bound is no limit (null). */
internal fun rangeToBounds(from: Int, to: Int, min: Int, max: Int): Pair<Int?, Int?> =
    (if (from <= min) null else from) to (if (to >= max) null else to)

/**
 * Filters bottom sheet (classic `SearchPage` anime filters), applied SERVER-SIDE: genres, tags (spoiler tags hidden unless the
 * switch is on), format, season year + season, status, year / episode / score ranges, sort, 18+, "Limpiar" and Apply. Draft until Apply.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchFiltersSheet(
    current: SearchFilters,
    adult: Boolean,
    genreOptions: List<String>,
    tagOptions: List<MediaTag>,
    tagsLoading: Boolean,
    onApply: (SearchFilters, adult: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    var draftAdult by remember(adult) { mutableStateOf(adult) }
    val thisYear = remember { Year.now().value }
    val years = remember(thisYear) { yearChoices(thisYear) }
    AdaptiveSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.search_filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            FilterGroup(stringResource(Res.string.search_sort)) {
                SORTS.forEach { s ->
                    FilterChip(
                        selected = draft.sort == s,
                        onClick = { draft = draft.copy(sort = if (draft.sort == s) null else s) },
                        label = { Text(stringResource(s.labelRes())) },
                    )
                }
            }
            FilterGroup(stringResource(Res.string.search_genre)) {
                genreOptions.forEach { g ->
                    val on = g in draft.genres
                    FilterChip(
                        selected = on,
                        onClick = { draft = draft.copy(genres = if (on) draft.genres - g else draft.genres + g) },
                        label = { Text(genreLabel(g)) },
                    )
                }
            }
            FilterGroup(stringResource(Res.string.search_format)) {
                FORMATS.forEach { f ->
                    val on = f in draft.formats
                    FilterChip(
                        selected = on,
                        onClick = { draft = draft.copy(formats = if (on) draft.formats - f else draft.formats + f) },
                        label = { Text(formatLabel(f)) },
                    )
                }
            }
            FilterGroup(stringResource(Res.string.search_status)) {
                STATUSES.forEach { s ->
                    FilterChip(
                        selected = draft.status == s,
                        onClick = { draft = draft.copy(status = if (draft.status == s) null else s) },
                        label = { Text(statusLabel(s) ?: s) },
                    )
                }
            }
            FilterGroup(stringResource(Res.string.search_year)) {
                years.forEach { y ->
                    FilterChip(
                        selected = draft.year == y,
                        onClick = { draft = draft.copy(year = if (draft.year == y) null else y) },
                        label = { Text(y.toString()) },
                    )
                }
            }
            FilterGroup(stringResource(Res.string.search_season)) {
                SEASONS.forEach { s ->
                    FilterChip(
                        selected = draft.season == s,
                        onClick = { draft = draft.copy(season = if (draft.season == s) null else s) },
                        label = { Text(seasonLabel(s)) },
                    )
                }
            }
            RangeAndTagFilters(draft, onDraftChange = { draft = it }, tagOptions = tagOptions, tagsLoading = tagsLoading)
            SwitchRow(stringResource(Res.string.search_adult), draftAdult, supporting = stringResource(Res.string.search_adult_hint)) {
                draftAdult = it
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = {
                    draft = SearchFilters()
                    onApply(SearchFilters(), draftAdult)
                }) { Text(stringResource(Res.string.search_filters_clear)) }
                Button(onClick = { onApply(draft, draftAdult) }) { Text(stringResource(Res.string.search_apply)) }
            }
        }
    }
}

/**
 * The year / episode / score ranges and the tag picker (spoiler switch, filter box, "Show all N tags"). Shared by the phone's whole
 * filters sheet and the wide window's "Más filtros" facet.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RangeAndTagFilters(
    draft: SearchFilters,
    onDraftChange: (SearchFilters) -> Unit,
    tagOptions: List<MediaTag>,
    tagsLoading: Boolean,
) {
    var showSpoilers by remember { mutableStateOf(false) }
    var tagQuery by remember { mutableStateOf("") }
    var allTags by remember { mutableStateOf(false) }
    val thisYear = remember { Year.now().value }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RangeFilter(
            title = stringResource(Res.string.search_year_range),
            from = draft.yearFrom,
            to = draft.yearTo,
            min = YEAR_MIN,
            max = thisYear + 1,
            onChange = { f, t -> onDraftChange(draft.copy(yearFrom = f, yearTo = t)) },
        )
        RangeFilter(
            title = stringResource(Res.string.search_episodes),
            from = draft.episodesFrom,
            to = draft.episodesTo,
            min = 0,
            max = EPISODES_MAX,
            onChange = { f, t -> onDraftChange(draft.copy(episodesFrom = f, episodesTo = t)) },
        )
        RangeFilter(
            title = stringResource(Res.string.search_score),
            from = draft.scoreFrom,
            to = draft.scoreTo,
            min = 0,
            max = SCORE_MAX,
            steps = SCORE_MAX / 5 - 1,
            onChange = { f, t -> onDraftChange(draft.copy(scoreFrom = f, scoreTo = t)) },
        )
        // Tags: AniList catalogue, filtered by a text box; spoiler tags only with the switch on.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.search_tags), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SwitchRow(stringResource(Res.string.search_tags_spoilers), showSpoilers) { showSpoilers = it }
            when {
                tagsLoading -> Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                tagOptions.isEmpty() -> Text(
                    stringResource(Res.string.search_tags_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    OutlinedTextField(
                        value = tagQuery,
                        onValueChange = { tagQuery = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(Res.string.search_tags_filter)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val visible = visibleTags(tagOptions, draft.tags, tagQuery, showSpoilers)
                    val shown = if (allTags || tagQuery.isNotBlank()) visible else visible.take(TAGS_COLLAPSED)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        shown.forEach { tag ->
                            val on = tag.name in draft.tags
                            FilterChip(
                                selected = on,
                                onClick = { onDraftChange(draft.copy(tags = if (on) draft.tags - tag.name else draft.tags + tag.name)) },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                    if (!allTags && tagQuery.isBlank() && visible.size > TAGS_COLLAPSED) {
                        TextButton(onClick = { allTags = true }) {
                            Text(stringResource(Res.string.search_tags_all, visible.size))
                        }
                    }
                }
            }
        }
    }
}

/** Selected tags first, then the matches of [query]; spoiler tags hidden unless [spoilers] (a selected one always stays). */
internal fun visibleTags(all: List<MediaTag>, selected: List<String>, query: String, spoilers: Boolean): List<MediaTag> {
    val q = query.trim()
    val matches = all.filter { t ->
        (spoilers || !t.isSpoiler || t.name in selected) && (q.isEmpty() || t.name.contains(q, ignoreCase = true))
    }
    return matches.sortedBy { if (it.name in selected) 0 else 1 }
}

@Composable
internal fun SwitchRow(title: String, checked: Boolean, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeFilter(
    title: String,
    from: Int?,
    to: Int?,
    min: Int,
    max: Int,
    onChange: (Int?, Int?) -> Unit,
    steps: Int = 0,
) {
    val lo = (from ?: min).coerceIn(min, max)
    val hi = (to ?: max).coerceIn(lo, max)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                stringResource(
                    Res.string.search_range_value,
                    if (from == null) stringResource(Res.string.search_range_any) else lo.toString(),
                    if (to == null) stringResource(Res.string.search_range_any) else hi.toString(),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        RangeSlider(
            value = lo.toFloat()..hi.toFloat(),
            onValueChange = { r ->
                val (f, t) = rangeToBounds(r.start.roundToInt(), r.endInclusive.roundToInt(), min, max)
                onChange(f, t)
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = steps,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chips()
        }
    }
}
