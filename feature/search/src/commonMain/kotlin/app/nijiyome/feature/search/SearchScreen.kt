// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.details.CharacterScreen
import app.nijiyome.feature.search.resources.Res
import app.nijiyome.feature.search.resources.*
import app.nijiyome.metadata.TrendWindow
import app.nijiyome.model.ExploreStructure
import app.nijiyome.model.FeedKind
import app.nijiyome.model.ListStatus
import app.nijiyome.model.Media
import app.nijiyome.model.MediaSort
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.ListStatusPicker
import app.nijiyome.ui.patterns.label
import app.nijiyome.ui.patterns.toListStatus
import app.nijiyome.ui.platform.PlatformBackHandler
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sparkles Explorar (`Route.Search` / `Route.SearchFor`, lane S3): Buscar and Para ti in one screen, in the structure the user picked
 * (`structures.explore`): Facetas (search with facet chips, results first) or Lienzo (discovery canvas first, search on top). Both share
 * this view model, state and actions: text search with recents, "Qué buscar" scopes (anime, characters, manga and novels that open
 * their homes, extension catalogues), every filter of the old sheet as facets, the feeds of Home "Ver todo", Top with its time window,
 * studio browsing, image search, and Para ti / Atlas from `:feature:home` through the slots below.
 */
@Composable
fun SearchScreen(
    onMediaClick: (Media) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
    /** Pre-filled query / feed / genre from Route.SearchFor (null = plain Search). */
    initialQuery: String? = null,
    initialFeed: FeedKind? = null,
    initialGenre: String? = null,
    /** Studio / producer browse from Details (`Route.SearchFor(studio)`): fed into `MediaSearchQuery.studio`. */
    initialStudio: String? = null,
    /** Image-search entry of the search field -> Route.ImageSearch(). */
    onOpenImageSearch: () -> Unit = {},
    /** "Qué buscar" Manga / Novelas open those homes with the typed query (Route.Manga(query) / Route.Novels(query)). */
    onOpenMangaSearch: (query: String) -> Unit = {},
    onOpenNovelSearch: (query: String) -> Unit = {},
    /** Desktop: while true the query field takes the focus, then [onQueryFocused] is called. */
    focusQuery: Boolean = false,
    onQueryFocused: () -> Unit = {},
    // --- Sparkles (lane S3), all with defaults; the integrator wires them in feature/shell ---
    /** Opens Explorar on a tab: [ExploreTab.FOR_YOU] for `Route.ForYou`. */
    initialTab: ExploreTab = ExploreTab.SEARCH,
    /** Para ti page (`:feature:home` `ForYouPanel`); null hides the Buscar / Para ti switch. */
    forYouPanel: (@Composable (onGenre: (String) -> Unit, modifier: Modifier) -> Unit)? = null,
    /** Lienzo's Para ti section (`:feature:home` `ForYouPreview`). */
    forYouPreview: (@Composable (onSeeAll: () -> Unit, onGenre: (String) -> Unit, modifier: Modifier) -> Unit)? = null,
    /** Lienzo's atlas section (`:feature:home` `TasteAtlas`). */
    tasteAtlas: (@Composable (modifier: Modifier) -> Unit)? = null,
    /** "Abrir Fuentes" when no extension has a catalogue or one fails (Route.Extensions). */
    onOpenExtensions: () -> Unit = {},
) {
    val facets = structures.explore == ExploreStructure.FACETS
    LaunchedEffect(initialQuery, initialFeed, initialGenre, initialStudio) {
        viewModel.applyInitial(initialQuery, initialFeed, initialGenre, initialStudio)
        if (facets) viewModel.openOnTrending()
    }
    // Recent searches may have been cleared from Settings › Metadatos while this screen sat in the back stack.
    LaunchedEffect(Unit) { viewModel.reloadHistory() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable(initialTab) { mutableStateOf(if (forYouPanel == null) ExploreTab.SEARCH else initialTab) }
    // A character hit opens its page in place (SRCH-08); back or Esc returns to the list.
    var openCharacter by rememberSaveable { mutableStateOf<Int?>(null) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var statusFor by remember { mutableStateOf<Media?>(null) }
    val queryFocus = remember { FocusRequester() }
    val currentState by rememberUpdatedState(state)

    val characterId = openCharacter
    if (characterId != null) {
        PlatformBackHandler { openCharacter = null }
        CharacterScreen(
            characterId = characterId,
            onBack = { openCharacter = null },
            onMediaClick = onMediaClick,
            modifier = modifier,
        )
        return
    }

    // Ctrl+K of the shell lands here with a query or asks for the focus; Ctrl+F focuses the field from anywhere in Explorar.
    LaunchedEffect(focusQuery, tab) {
        if (focusQuery && tab == ExploreTab.SEARCH) {
            runCatching { queryFocus.requestFocus() }
            onQueryFocused()
        }
    }

    val listChanges = rememberListChanges(viewModel)
    val actions = ExploreActions(
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::submit,
        onClear = viewModel::clear,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onMediaClick = onMediaClick,
        onCharacterClick = { openCharacter = it.id },
        onExtensionItemClick = viewModel::openExtensionItem,
        onUseHistory = viewModel::useHistory,
        onRemoveHistory = viewModel::removeHistory,
        onClearHistory = viewModel::clearHistory,
        onFiltersChange = viewModel::setFilters,
        onEnsureTags = viewModel::ensureTags,
        onOpenAllFilters = {
            viewModel.ensureTags()
            filtersOpen = true
        },
        onSelectFeed = viewModel::selectFeed,
        onClearFeed = viewModel::clearFeed,
        onTrendWindow = viewModel::setTrendWindow,
        onClearStudio = viewModel::clearStudio,
        onScope = { scope ->
            when (scope) {
                ExploreScope.MANGA -> onOpenMangaSearch(currentState.query.trim())
                ExploreScope.NOVELS -> onOpenNovelSearch(currentState.query.trim())
                else -> viewModel.selectScope(scope)
            }
        },
        onExtension = viewModel::selectExtension,
        onSetAdult = viewModel::setAdult,
        onMyList = viewModel::setMyList,
        onResetAll = viewModel::resetAllFilters,
        onBrowseGenre = viewModel::browseGenre,
        onOpenImageSearch = onOpenImageSearch,
        onOpenExtensions = onOpenExtensions,
        onEditStatus = if (viewModel.canEditList) ({ media -> statusFor = media }) else null,
        onQuickAdd = if (viewModel.canEditList) ({ media -> listChanges(media, ListStatus.PLANNING) }) else null,
        onEnsureTrendingRow = viewModel::ensureTrendingRow,
        onRetryTrendingRow = viewModel::loadTrendingRow,
    )
    val slots = ExploreSlots(forYouPanel, forYouPreview, tasteAtlas)
    val frame = ExploreFrame(
        tab = tab,
        onTab = { tab = it },
        queryFocus = queryFocus,
        onRestoreHistory = viewModel::restoreHistory,
        withMyList = viewModel.canEditList,
    )
    val keys = Modifier.onPreviewKeyEvent { e ->
        val focusKey = e.type == KeyEventType.KeyDown && e.key == Key.F && (e.isCtrlPressed || e.isMetaPressed)
        if (focusKey) {
            tab = ExploreTab.SEARCH
            runCatching { queryFocus.requestFocus() }
        }
        focusKey
    }

    when (structures.explore) {
        ExploreStructure.FACETS -> ExploreFacetsLayout(state, actions, slots, frame, modifier.then(keys))
        ExploreStructure.CANVAS -> ExploreCanvasLayout(state, actions, slots, frame, modifier.then(keys))
    }

    if (filtersOpen) {
        SearchFiltersSheet(
            current = state.filters,
            adult = state.adult,
            genreOptions = (state.genreOptions + state.filters.genres).distinct().sorted(),
            tagOptions = state.tagOptions,
            tagsLoading = state.tagsLoading,
            onApply = { filters, adult ->
                filtersOpen = false
                viewModel.applySheet(filters, adult)
            },
            onDismiss = { filtersOpen = false },
        )
    }
    statusFor?.let { media ->
        ListStatusSheet(
            media = media,
            current = state.listStatusOf(media.ref),
            onPick = { status ->
                statusFor = null
                listChanges(media, status)
            },
            onDismiss = { statusFor = null },
        )
    }
}

/**
 * Changes a result's list status and offers Undo (back to the status it had), or says it failed. The messages are resolved here so
 * the view model's callback can use them.
 */
@Composable
private fun rememberListChanges(viewModel: SearchViewModel): (Media, ListStatus?) -> Unit {
    val snackbar = LocalAppSnackbar.current
    val undo = stringResource(Res.string.rd_s3_undo)
    val failed = stringResource(Res.string.rd_s3_status_failed)
    val setTemplate = stringResource(Res.string.rd_s3_status_set, TITLE_SLOT, STATUS_SLOT)
    val removedTemplate = stringResource(Res.string.rd_s3_status_removed, TITLE_SLOT)
    val labels = ListStatus.entries.associateWith { it.slot().label() }
    val language = viewModel.state.value.titleLanguage
    return { media, status ->
        viewModel.setListStatus(media, status) { previous, ok ->
            if (!ok) {
                snackbar.show(failed)
            } else {
                val title = media.displayTitle(language)
                val message = if (status == null) {
                    removedTemplate.replace(TITLE_SLOT, title)
                } else {
                    setTemplate.replace(TITLE_SLOT, title).replace(STATUS_SLOT, labels[status].orEmpty())
                }
                snackbar.show(message, undo) { viewModel.setListStatus(media, previous) }
            }
        }
    }
}

private const val TITLE_SLOT = "\u0001"
private const val STATUS_SLOT = "\u0002"

/** The list status picker of a result (pattern `stp`): six statuses, and "Quitar de mi lista" when it is on the list. */
@Composable
private fun ListStatusSheet(media: Media, current: ListStatus?, onPick: (ListStatus?) -> Unit, onDismiss: () -> Unit) {
    AdaptiveSheet(onDismissRequest = onDismiss, modifier = Modifier.decor(DecorRegion.SHEET)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.rd_s3_status_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(media.displayTitle(app.nijiyome.ui.media.LocalTitleLanguage.current), style = MaterialTheme.typography.bodyMedium)
            ListStatusPicker(selected = current?.slot(), onSelect = { onPick(it.toListStatus()) })
            if (current != null) {
                TextButton(onClick = { onPick(null) }) { Text(stringResource(Res.string.rd_s3_status_remove)) }
            }
        }
    }
}

internal fun SearchType.labelRes(): StringResource = when (this) {
    SearchType.ANIME -> Res.string.search_type_anime
    SearchType.CHARACTERS -> Res.string.search_type_characters
}

internal fun TrendWindow.labelRes(): StringResource = when (this) {
    TrendWindow.TODAY -> Res.string.search_trend_today
    TrendWindow.WEEK -> Res.string.search_trend_week
    TrendWindow.MONTH -> Res.string.search_trend_month
    TrendWindow.ALL_TIME -> Res.string.search_trend_all_time
}

internal fun MediaSort.labelRes(): StringResource = when (this) {
    MediaSort.POPULARITY -> Res.string.search_sort_popularity
    MediaSort.TRENDING -> Res.string.search_sort_trending
    MediaSort.SCORE -> Res.string.search_sort_score
    MediaSort.START_DATE -> Res.string.search_sort_start_date
    MediaSort.TITLE -> Res.string.search_sort_title
    MediaSort.FAVOURITES -> Res.string.search_sort_favourites
    MediaSort.EPISODES -> Res.string.search_sort_episodes
    MediaSort.RELEVANCE -> Res.string.search_sort_relevance
}

internal fun FeedKind.labelRes(): StringResource = when (this) {
    FeedKind.TRENDING -> Res.string.search_feed_trending
    FeedKind.POPULAR -> Res.string.search_feed_popular
    FeedKind.TOP_RATED -> Res.string.search_feed_top_rated
    FeedKind.AIRING -> Res.string.search_feed_airing
    FeedKind.UPCOMING -> Res.string.search_feed_upcoming
    FeedKind.SEASONAL -> Res.string.search_feed_seasonal
    FeedKind.RECENTLY_COMPLETED -> Res.string.search_feed_completed
}

@Composable
internal fun seasonLabel(season: String): String = when (season.uppercase()) {
    "WINTER" -> stringResource(Res.string.search_season_winter)
    "SPRING" -> stringResource(Res.string.search_season_spring)
    "SUMMER" -> stringResource(Res.string.search_season_summer)
    "FALL" -> stringResource(Res.string.search_season_fall)
    else -> season
}

@Composable
internal fun statusLabel(status: String): String? = when (status.uppercase()) {
    "RELEASING" -> stringResource(Res.string.search_status_releasing)
    "FINISHED" -> stringResource(Res.string.search_status_finished)
    "NOT_YET_RELEASED" -> stringResource(Res.string.search_status_upcoming)
    "CANCELLED" -> stringResource(Res.string.search_status_cancelled)
    "HIATUS" -> stringResource(Res.string.search_status_hiatus)
    else -> null
}

@Composable
internal fun formatLabel(format: String): String = when (format.uppercase()) {
    "TV" -> stringResource(Res.string.search_format_tv)
    "TV_SHORT" -> stringResource(Res.string.search_format_tv_short)
    "MOVIE" -> stringResource(Res.string.search_format_movie)
    "OVA" -> stringResource(Res.string.search_format_ova)
    "ONA" -> stringResource(Res.string.search_format_ona)
    "SPECIAL" -> stringResource(Res.string.search_format_special)
    "MUSIC" -> stringResource(Res.string.search_format_music)
    else -> format
}

internal fun statusTone(status: String): PillTone = when (status.uppercase()) {
    "RELEASING" -> PillTone.OK
    "NOT_YET_RELEASED", "HIATUS" -> PillTone.WARN
    "CANCELLED" -> PillTone.ERR
    else -> PillTone.NEUTRAL
}
