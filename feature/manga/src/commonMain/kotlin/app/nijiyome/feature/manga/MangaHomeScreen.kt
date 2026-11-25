// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.HeroCarousel
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.designsystem.motion.homeEntrance
import app.nijiyome.designsystem.motion.loadedReveal
import app.nijiyome.manga.ChapterText
import app.nijiyome.manga.MangaRepositoryImpl
import app.nijiyome.model.AppSettings
import app.nijiyome.model.ContinueReadingItem
import app.nijiyome.model.LoadState
import app.nijiyome.model.MangaChapter
import app.nijiyome.model.MangaHome
import app.nijiyome.model.MangaReaderArgs
import app.nijiyome.model.MangaStructure
import app.nijiyome.model.MatureContentMode
import app.nijiyome.model.Media
import app.nijiyome.model.MediaSort
import app.nijiyome.model.ReadEntry
import app.nijiyome.model.ReadingProgress
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.labels.genreLabel
import app.nijiyome.ui.look.cardWidth
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.media.COVER_ASPECT_RATIO
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.UndoSnackbar
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.rememberUndoController
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.state.userMessage
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import kotlinx.coroutines.delay

/**
 * `Route.Manga(query)` (MNG-01 to MNG-04): the manga home in the Sparkles design (artifact screen `manga`, structure `catalogo`):
 * header with the Manga / Novelas switch, search with filter chips that survive the sort, featured carousel, "Seguir leyendo", new
 * chapters of what you read (dismiss with undo), Tendencias / Populares / Mejor valorados, genres and the 18+ zone with its gate.
 * [initialQuery] comes from the Search type chip. [onMediaClick] opens `Route.Details(MANGA, id)`. [onBack] null on wide windows.
 * New, optional: [onOpenNovels] shows the medium switch (to `Route.Novels`), [onOpenLibrary] the "Biblioteca" link of "Seguir leyendo".
 */
@Composable
fun MangaHomeScreen(
    onMediaClick: (Media) -> Unit,
    onBack: (() -> Unit)?,
    onOpenReader: (MangaReaderArgs) -> Unit,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String? = null,
    onOpenNovels: (() -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
) {
    val vm: MangaHomeViewModel = koinViewModel()
    LaunchedEffect(initialQuery) { vm.applyInitial(initialQuery) }
    val home by vm.home.collectAsStateWithLifecycle()
    val continueReading by vm.continueReading.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    var pendingMature by remember { mutableStateOf<Media?>(null) }
    var gateOpen by rememberSaveable { mutableStateOf(false) }

    // Adult titles of the 18+ zone follow matureContent: WARN asks once per title, BLUR hides the cover until tapped, SHOW opens.
    val openAdult: (Media) -> Unit = { media ->
        val id = media.ref.anilistId
        if (settings.matureContent == MatureContentMode.WARN && id != null && id !in prefs.matureAccepted) pendingMature = media else onMediaClick(media)
    }
    val state = MangaHomeState(home, continueReading, settings, prefs.adultGateAccepted, filters, results)
    val actions = MangaHomeActions(
        onBack = onBack,
        onRefresh = vm::refresh,
        onQuery = vm::setQuery,
        onSearch = vm::search,
        onFilters = vm::setFilters,
        onClear = vm::clearSearch,
        onLoadMore = vm::loadMore,
        onOpen = onMediaClick,
        onOpenAdult = openAdult,
        onOpenReader = onOpenReader,
        onShowAdult = { gateOpen = true },
        onHideAdult = vm::hideAdultZone,
        onLoadAdult = vm::loadAdultZone,
        onDismissUpdate = vm::dismissUpdate,
        onOpenNovels = onOpenNovels,
        onOpenLibrary = onOpenLibrary,
    )

    when (structures.manga) {
        MangaStructure.CATALOG -> MangaCatalogHome(state, actions, modifier)
    }

    if (gateOpen) {
        AlertDialog(
            onDismissRequest = { gateOpen = false },
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
            title = { Text(stringResource(Res.string.manga_adult_gate_title)) },
            text = { Text(stringResource(Res.string.manga_adult_gate_message)) },
            confirmButton = {
                Button(onClick = {
                    gateOpen = false
                    vm.acceptAdultGate()
                }) { Text(stringResource(Res.string.manga_adult_gate_confirm)) }
            },
            dismissButton = { TextButton(onClick = { gateOpen = false }) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
    pendingMature?.let { media ->
        MatureWarningDialog(
            onConfirm = {
                pendingMature = null
                media.ref.anilistId?.let(vm::acceptMature)
                onMediaClick(media)
            },
            onDismiss = { pendingMature = null },
        )
    }
}

internal data class MangaHomeState(
    val home: MangaHome,
    val continueReading: List<ContinueReadingItem>,
    val settings: AppSettings,
    val adultAccepted: Boolean,
    val filters: MangaFilters,
    val results: SearchResultsUi,
)

internal class MangaHomeActions(
    val onBack: (() -> Unit)?,
    val onRefresh: () -> Unit,
    val onQuery: (String) -> Unit,
    val onSearch: () -> Unit,
    val onFilters: ((MangaFilters) -> MangaFilters) -> Unit,
    val onClear: () -> Unit,
    val onLoadMore: () -> Unit,
    val onOpen: (Media) -> Unit,
    val onOpenAdult: (Media) -> Unit,
    val onOpenReader: (MangaReaderArgs) -> Unit,
    val onShowAdult: () -> Unit,
    val onHideAdult: () -> Unit,
    val onLoadAdult: () -> Unit,
    val onDismissUpdate: (String) -> Unit,
    val onOpenNovels: (() -> Unit)?,
    val onOpenLibrary: (() -> Unit)?,
)

/** Structure `catalogo`: header, search, filters, then the results grid or the feed. */
@Composable
private fun MangaCatalogHome(state: MangaHomeState, actions: MangaHomeActions, modifier: Modifier) {
    val compact = isCompactWindow()
    val undo = rememberUndoController()
    Box(modifier.fillMaxSize().windowInsetsPadding(screenInsets)) {
        Column(Modifier.fillMaxSize()) {
            ReadingPageHeader(
                title = stringResource(Res.string.manga_title),
                medium = ReadingMedium.MANGA,
                onBack = actions.onBack,
                onRefresh = actions.onRefresh,
                onSwitchMedium = actions.onOpenNovels?.let { open -> { m -> if (m == ReadingMedium.NOVEL) open() } },
                compact = compact,
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingSearchField(
                    query = state.filters.query,
                    placeholder = stringResource(Res.string.manga_search_hint),
                    showClear = state.filters.query.isNotEmpty(),
                    onQuery = actions.onQuery,
                    onSubmit = actions.onSearch,
                    onClear = actions.onClear,
                )
                MangaFilterBar(state.filters, state.home.genres, actions.onFilters, actions.onClear)
            }
            if (state.filters.active) {
                MangaResults(state, actions, Modifier.weight(1f))
            } else {
                MangaFeed(state, actions, undo, compact, Modifier.weight(1f))
            }
        }
        UndoSnackbar(undo, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

// ---------------------------------------------------------------------------------------------------------------- filters

/** Origin, status, genre, year and, apart, the sort (sorting never clears the filters); the active ones below with their ×. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaFilterBar(filters: MangaFilters, genres: List<String>, onFilters: ((MangaFilters) -> MangaFilters) -> Unit, onClearAll: () -> Unit) {
    val any = stringResource(Res.string.manga_filter_any)
    val thisYear = remember { LocalDate.now().year }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChipMenu(
                label = stringResource(Res.string.manga_filter_origin),
                value = null,
                options = listOf<String?>(null) + MangaFilters.ORIGINS,
                selected = filters.origin,
                active = filters.origin != null,
                optionLabel = { originLabel(it) ?: any },
                onSelect = { v -> onFilters { it.copy(origin = v) } },
            )
            FilterChipMenu(
                label = stringResource(Res.string.manga_filter_status),
                value = null,
                options = listOf<String?>(null) + MangaFilters.STATUSES,
                selected = filters.status,
                active = filters.status != null,
                optionLabel = { anilistStatusLabel(it) ?: any },
                onSelect = { v -> onFilters { it.copy(status = v) } },
            )
            FilterChipMenu(
                label = stringResource(Res.string.manga_filter_genre),
                value = null,
                options = listOf<String?>(null) + genres,
                selected = filters.genre,
                active = filters.genre != null,
                optionLabel = { it?.let { g -> genreLabel(g) } ?: any },
                onSelect = { v -> onFilters { it.copy(genre = v) } },
            )
            FilterChipMenu(
                label = stringResource(Res.string.manga_filter_year),
                value = null,
                options = listOf<Int?>(null) + (thisYear + 1 downTo 1950).toList(),
                selected = filters.year,
                active = filters.year != null,
                optionLabel = { it?.toString() ?: any },
                onSelect = { v -> onFilters { it.copy(year = v) } },
            )
            Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
            val relevance = filters.query.isNotBlank() && filters.sort == MediaSort.POPULARITY
            FilterChipMenu(
                label = stringResource(Res.string.manga_filter_sort),
                value = if (relevance) sortLabel(MediaSort.RELEVANCE) else sortLabel(filters.sort),
                options = MangaFilters.SORTS,
                selected = filters.sort,
                optionLabel = { sortLabel(it) },
                onSelect = { v -> onFilters { it.copy(sort = v) } },
                sort = true,
                active = filters.sort != MediaSort.POPULARITY,
            )
        }
        val active = buildList {
            filters.origin?.let { o -> add(ActiveFilter("${stringResource(Res.string.manga_filter_origin)}: ${originLabel(o)}") { onFilters { it.copy(origin = null) } }) }
            filters.status?.let { s -> add(ActiveFilter("${stringResource(Res.string.manga_filter_status)}: ${anilistStatusLabel(s)}") { onFilters { it.copy(status = null) } }) }
            filters.genre?.let { g -> add(ActiveFilter("${stringResource(Res.string.manga_filter_genre)}: ${genreLabel(g)}") { onFilters { it.copy(genre = null) } }) }
            filters.year?.let { y -> add(ActiveFilter("${stringResource(Res.string.manga_filter_year)}: $y") { onFilters { it.copy(year = null) } }) }
        }
        ActiveFilterRow(active, onClearAll)
    }
}

// ---------------------------------------------------------------------------------------------------------------- results

@Composable
private fun MangaResults(state: MangaHomeState, actions: MangaHomeActions, modifier: Modifier) {
    val results = state.results
    val lang = state.settings.mangaTitleLanguage
    Column(modifier.fillMaxWidth()) {
        when (val st = results.state) {
            null, LoadState.Loading -> ResultsPlaceholder()
            is LoadState.Err -> ErrorBlock(st.cause, actions.onSearch)
            is LoadState.Ok -> if (st.value.isEmpty()) {
                ReadingEmpty(
                    kind = EmptyKind.SEARCH,
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(Res.string.manga_search_empty),
                    message = stringResource(Res.string.manga_search_empty_message),
                    actionLabel = stringResource(Res.string.rd_s11_filters_clear),
                    onAction = actions.onClear,
                )
            } else {
                val grid = rememberLazyGridState()
                LaunchedEffect(grid, st.value.size) {
                    snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last -> if (last >= st.value.size - 6) actions.onLoadMore() }
                }
                val sortText = sortLabel(if (state.filters.query.isNotBlank() && state.filters.sort == MediaSort.POPULARITY) MediaSort.RELEVANCE else state.filters.sort)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cardWidth()),
                    state = grid,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().decor(DecorRegion.GRID).loadedReveal(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
                        SectionHeader(
                            if (state.filters.query.isNotBlank()) stringResource(Res.string.rd_s11_results_for, state.filters.query.trim()) else stringResource(Res.string.rd_s11_results),
                            trailing = { Text(stringResource(Res.string.rd_s11_sorted_by, sortText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                    }
                    itemsIndexed(st.value, key = { i, m -> "${m.ref.anilistId ?: m.title}-$i" }) { _, media ->
                        val data = media.mangaCard(lang, subtitle = listOfNotNull(media.year?.toString(), anilistStatusLabel(media.status)).joinToString(" · ").ifBlank { null })
                        TitleCard(data, onClick = { actions.onOpen(media) }, context = CardContext.GRID, modifier = Modifier.cardToDetailsSource(data.key))
                    }
                    if (results.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
                    }
                    results.moreError?.let { err ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(err.userMessage(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = actions.onLoadMore) { Text(stringResource(UiR.string.ui_retry)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResultsPlaceholder() {
    val w = cardWidth()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(w),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize().decor(DecorRegion.GRID),
    ) {
        items(12) { LoadingPlaceholder(Modifier.fillMaxWidth().height(w / COVER_ASPECT_RATIO)) }
    }
}

// ---------------------------------------------------------------------------------------------------------------- feed

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaFeed(
    state: MangaHomeState,
    actions: MangaHomeActions,
    undo: app.nijiyome.ui.patterns.UndoController,
    compact: Boolean,
    modifier: Modifier,
) {
    val home = state.home
    val lang = state.settings.mangaTitleLanguage
    // News dismissed in this visit: hidden at once, committed (dismissUpdate) when the undo expires.
    var hiddenNews by remember { mutableStateOf(emptySet<String>()) }
    val dismissedText = stringResource(Res.string.rd_s11_news_dismissed)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        var section = 0
        item(key = "hero") {
            FeatureSection(home.hero, state, actions, compact, Modifier.homeEntrance(0))
        }
        section++
        val stillLoading = listOf(home.hero, home.trending, home.popular, home.topRated).any { it is LoadState.Loading }
        if (stillLoading) item(key = "still-loading") { StillLoadingHint() }
        if (state.continueReading.isNotEmpty()) {
            val index = section++
            item(key = "continue") {
                Column(Modifier.homeEntrance(index), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        stringResource(Res.string.manga_continue_reading),
                        Modifier.padding(horizontal = 16.dp),
                        trailing = actions.onOpenLibrary?.let { open -> { TextButton(onClick = open) { Text(stringResource(Res.string.rd_s11_library)) } } },
                    )
                    LazyRow(
                        Modifier.fillMaxWidth().decor(DecorRegion.TONIGHT),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(state.continueReading, key = { _, c -> c.mediaId }) { i, c ->
                            val p = home.lastRead.firstOrNull { it.mediaId == c.mediaId }?.takeIf { c.resume }
                            val data = continueCardData(c, p)
                            TitleCard(
                                data,
                                onClick = { if (p == null) actions.onOpen(c.toMedia()) else actions.onOpenReader(resumeArgs(p)) },
                                context = CardContext.CONTINUE,
                                show = CardShow(progress = true, status = true, next = true),
                                modifier = Modifier.cardSway(i).cardToDetailsSource(data.key),
                            )
                        }
                    }
                }
            }
        }
        val news = home.followedUpdates.filter { (entry, _) -> MangaRepositoryImpl.mediaKey(entry.ref)?.let { it !in hiddenNews } ?: false }
        if (news.isNotEmpty() || state.continueReading.isNotEmpty()) {
            val index = section++
            item(key = "news") {
                Column(Modifier.homeEntrance(index).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(Res.string.manga_followed_updates))
                    if (news.isEmpty()) {
                        ReadingEmpty(EmptyKind.GENERIC, Icons.Outlined.NotificationsNone, stringResource(Res.string.rd_s11_up_to_date), stringResource(Res.string.rd_s11_up_to_date_manga))
                    } else {
                        news.forEach { (entry, chapter) ->
                            val mediaId = MangaRepositoryImpl.mediaKey(entry.ref) ?: return@forEach
                            NewsRow(
                                entry = entry,
                                chapter = chapter,
                                onRead = {
                                    actions.onDismissUpdate(mediaId)
                                    actions.onOpenReader(
                                        MangaReaderArgs(
                                            mediaId = mediaId, title = entry.title, poster = entry.poster, extensionId = chapter.extensionId,
                                            sourceId = chapter.sourceId, itemId = chapter.itemId, chapterId = chapter.id, chapterNumber = chapter.number,
                                            anilistId = entry.ref.anilistId,
                                        ),
                                    )
                                },
                                onDismiss = {
                                    hiddenNews = hiddenNews + mediaId
                                    undo.show(dismissedText, undo = { hiddenNews = hiddenNews - mediaId }, onCommit = { actions.onDismissUpdate(mediaId) })
                                },
                            )
                        }
                    }
                }
            }
        }
        listOf(
            Triple("trending", Res.string.manga_row_trending, MediaSort.TRENDING),
            Triple("popular", Res.string.manga_row_popular, MediaSort.POPULARITY),
            Triple("top", Res.string.manga_row_top_rated, MediaSort.SCORE),
        ).forEach { (key, title, sort) ->
            val index = section++
            val rowState = when (key) {
                "trending" -> home.trending
                "popular" -> home.popular
                else -> home.topRated
            }
            item(key = key) {
                MediaCardRow(
                    title = stringResource(title),
                    state = rowState,
                    onRetry = actions.onRefresh,
                    onOpen = actions.onOpen,
                    cardOf = { it.mangaCard(lang) },
                    // POPULARITY is the default sort, so "Ver todo" of Populares has nothing to sort by.
                    onSeeAll = if (sort != MediaSort.POPULARITY) ({ actions.onFilters { f -> f.copy(sort = sort) } }) else null,
                    modifier = Modifier.homeEntrance(index),
                )
            }
        }
        if (home.genres.isNotEmpty()) {
            val index = section++
            item(key = "genres") {
                Column(Modifier.homeEntrance(index).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(Res.string.manga_row_genres))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        home.genres.forEach { g -> SuggestionChip(onClick = { actions.onFilters { MangaFilters(genre = g) } }, label = { Text(genreLabel(g)) }) }
                    }
                }
            }
        }
        val adultIndex = section
        item(key = "adult") { AdultZone(state, actions, Modifier.homeEntrance(adultIndex)) }
    }
}

/** Featured: the hero titles one at a time (carousel), big card beside its facts, synopsis and "Ver ficha" / "Seguir en el cap. N". */
@Composable
private fun FeatureSection(hero: LoadState<List<Media>>, state: MangaHomeState, actions: MangaHomeActions, compact: Boolean, modifier: Modifier) {
    when (hero) {
        LoadState.Loading -> LoadingPlaceholder(modifier.fillMaxWidth().padding(horizontal = 16.dp).height(if (compact) 220.dp else 320.dp))
        is LoadState.Err -> ErrorBlock(hero.cause, actions.onRefresh, modifier)
        is LoadState.Ok -> if (hero.value.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).decor(DecorRegion.HERO),
            ) {
                HeroCarousel(items = hero.value, key = { it.ref.anilistId ?: it.title.hashCode() }) { media, _ ->
                    FeatureSlide(media, state, actions, compact)
                }
            }
        }
    }
}

@Composable
private fun FeatureSlide(media: Media, state: MangaHomeState, actions: MangaHomeActions, compact: Boolean) {
    val lang = state.settings.mangaTitleLanguage
    val data = media.mangaCard(lang)
    // "Seguir en el cap. N" when this featured title is one you are reading.
    val reading = state.continueReading.firstOrNull { it.ref.anilistId != null && it.ref.anilistId == media.ref.anilistId }
    val saved = reading?.let { r -> state.home.lastRead.firstOrNull { it.mediaId == r.mediaId }?.takeIf { r.resume } }
    val facts = listOfNotNull(stringResource(Res.string.rd_s11_featured), formatLabel(media.format), anilistStatusLabel(media.status)).joinToString(" · ")
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(facts, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(media.displayTitle(lang), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            media.synopsis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = if (compact) 3 else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 560.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saved != null) {
                    Button(onClick = { actions.onOpenReader(resumeArgs(saved)) }) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.rd_s11_resume_chapter, ChapterText.formatNumber(saved.chapterNumber) ?: "?"))
                    }
                    FilledTonalButton(onClick = { actions.onOpen(media) }) { Text(stringResource(Res.string.manga_hero_open)) }
                } else {
                    Button(onClick = { actions.onOpen(media) }) { Text(stringResource(Res.string.manga_hero_open)) }
                }
            }
        }
    }
    if (compact) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(112.dp)) { TitleCard(data, onClick = { actions.onOpen(media) }, context = CardContext.HERO, modifier = Modifier.cardToDetailsSource(data.key)) }
            Box(Modifier.weight(1f)) { text() }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(220.dp)) { TitleCard(data, onClick = { actions.onOpen(media) }, context = CardContext.HERO, modifier = Modifier.cardToDetailsSource(data.key)) }
            Box(Modifier.weight(1f)) { text() }
        }
    }
}

/** One new chapter of a followed title: thumb, "Nuevo: Cap. N", Leer and a dismiss ×. */
@Composable
private fun NewsRow(entry: ReadEntry, chapter: MangaChapter, onRead: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaCover(url = entry.poster, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(Res.string.manga_followed_new_chapter, ChapterText.formatNumber(chapter.number) ?: "?"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onRead) { Text(stringResource(Res.string.manga_read)) }
        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s11_news_dismiss, entry.title)) }
    }
}

/** 18+ zone (MNG-03 / MNG-04): needs showAdult and the one-time gate; covers follow matureContent. */
@Composable
private fun AdultZone(state: MangaHomeState, actions: MangaHomeActions, modifier: Modifier) {
    val adult = state.home.adult
    val settings = state.settings
    LaunchedEffect(settings.showAdult, state.adultAccepted, adult == null) {
        if (settings.showAdult && state.adultAccepted && adult == null) actions.onLoadAdult()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Shield, contentDescription = null)
                Text(stringResource(Res.string.manga_adult_zone), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                if (adult != null && state.adultAccepted) TextButton(onClick = actions.onHideAdult) { Text(stringResource(Res.string.manga_adult_hide)) }
            }
            when {
                !settings.showAdult -> Text(stringResource(Res.string.manga_adult_disabled), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                !state.adultAccepted -> {
                    Text(stringResource(Res.string.rd_s11_adult_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = actions.onShowAdult) { Text(stringResource(Res.string.manga_adult_show)) }
                }
                adult == null -> CardRowPlaceholder(Modifier.padding(horizontal = 0.dp))
                else -> AdultRow(adult, settings, actions)
            }
        }
    }
}

@Composable
private fun AdultRow(adult: LoadState<List<Media>>, settings: AppSettings, actions: MangaHomeActions) {
    val lang = settings.mangaTitleLanguage
    // BLUR: a title's cover stays hidden until its first tap ("Toca para ver"); the second tap opens it.
    var revealed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val hides = settings.matureContent.blurs()
    val tapToSee = stringResource(Res.string.manga_mature_tap)
    when (adult) {
        LoadState.Loading -> CardRowPlaceholder()
        is LoadState.Err -> ErrorBlock(adult.cause, actions.onLoadAdult)
        is LoadState.Ok -> if (adult.value.isEmpty()) {
            Text(stringResource(Res.string.manga_search_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            CardLazyRow(
                adult.value,
                cardOf = { m ->
                    val data = m.mangaCard(lang)
                    if (hides && data.key !in revealed) data.copy(coverUrl = null, subtitle = tapToSee) else data
                },
                onClick = { m, data -> if (hides && data.key !in revealed) revealed = revealed + data.key else actions.onOpenAdult(m) },
            )
        }
    }
}

/** The card of a "Seguir leyendo" entry: progress inside the chapter and "Cap. N" as the next label. */
@Composable
internal fun continueCardData(c: ContinueReadingItem, p: ReadingProgress?): TitleCardData {
    val chapter = ChapterText.formatNumber(p?.chapterNumber ?: c.chapter) ?: "?"
    return TitleCardData(
        key = mangaCardKey(c.ref, c.mediaId),
        title = p?.title ?: c.title,
        coverUrl = p?.poster ?: c.poster,
        subtitle = if (p != null) {
            stringResource(Res.string.manga_continue_line, chapter, p.page + 1, p.totalPages.coerceAtLeast(1))
        } else {
            stringResource(Res.string.manga_chapter_n, chapter)
        },
        progress = p?.let { if (it.totalPages > 0) (it.page + 1f) / it.totalPages else 0f },
        nextLabel = stringResource(Res.string.rd_s11_chapter_short, chapter),
    )
}

internal fun resumeArgs(p: ReadingProgress): MangaReaderArgs = MangaReaderArgs(
    mediaId = p.mediaId, title = p.title, poster = p.poster, extensionId = p.extensionId, sourceId = p.sourceId,
    itemId = p.itemId, chapterId = p.chapterId, chapterNumber = p.chapterNumber, startPage = p.page,
    anilistId = MangaRepositoryImpl.refOf(p.mediaId)?.anilistId,
)

/** Desktop parity (BUG-121): what a long wait in skeletons says, by elapsed time; null = nothing yet. Pure (unit-tested). */
internal fun stillLoadingRes(elapsedMs: Long): org.jetbrains.compose.resources.StringResource? = when {
    elapsedMs >= 20_000 -> Res.string.manga_home_still_loading_slow
    elapsedMs >= 6_000 -> Res.string.manga_home_still_loading
    else -> null
}

@Composable
private fun StillLoadingHint() {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        delay(6_000); elapsed = 6_000
        delay(14_000); elapsed = 20_000
    }
    stillLoadingRes(elapsed)?.let { res ->
        Text(
            stringResource(res),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
