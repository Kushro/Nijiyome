// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import app.nijiyome.feature.novel.resources.Res
import app.nijiyome.feature.novel.resources.*
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.labels.genreLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.designsystem.motion.homeEntrance
import app.nijiyome.designsystem.motion.loadedReveal
import app.nijiyome.manga.ChapterText
import app.nijiyome.model.ContinueReadingItem
import app.nijiyome.model.ListStatus
import app.nijiyome.model.LoadState
import app.nijiyome.model.MangaStructure
import app.nijiyome.model.Media
import app.nijiyome.model.MediaSort
import app.nijiyome.model.NovelChapter
import app.nijiyome.model.NovelProgress
import app.nijiyome.model.NovelReaderArgs
import app.nijiyome.model.ReadEntry
import app.nijiyome.novel.NovelRepositoryImpl
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.labels.readLabelRes
import app.nijiyome.ui.look.cardWidth
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.media.COVER_ASPECT_RATIO
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.UndoController
import app.nijiyome.ui.patterns.UndoSnackbar
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.rememberUndoController
import app.nijiyome.ui.state.screenInsets
import org.koin.compose.viewmodel.koinViewModel

/**
 * `Route.Novels(query)` (NOV-01 / NOV-02): the novel home, which follows the manga home of the Sparkles design (artifact screen
 * `manga`, medium Novelas): header with the medium switch, search with filter chips (genre, status, origin, sort apart), featured,
 * "Seguir leyendo", new chapters of what you follow (dismiss with undo), Tendencias, Populares and genres. [onMediaClick] opens
 * `Route.NovelDetails`. New, optional: [onOpenManga] shows the Manga / Novelas switch, [onOpenLibrary] the "Biblioteca" link.
 */
@Composable
fun NovelHomeScreen(
    onMediaClick: (Media) -> Unit,
    onBack: (() -> Unit)?,
    onOpenReader: (NovelReaderArgs) -> Unit,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String? = null,
    onOpenManga: (() -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
) {
    val vm: NovelHomeViewModel = koinViewModel()
    LaunchedEffect(initialQuery) { vm.applyInitial(initialQuery) }
    val home by vm.home.collectAsStateWithLifecycle()
    val continueReading by vm.continueReading.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val lang = settings.mangaTitleLanguage
    val compact = isCompactWindow()
    val undo = rememberUndoController()
    // The novel home has the manga home's single structure (`MangaStructure.CATALOG`).
    when (structures.manga) {
        MangaStructure.CATALOG -> Box(modifier.fillMaxSize().windowInsetsPadding(screenInsets)) {
            Column(Modifier.fillMaxSize()) {
                ReadingPageHeader(
                    title = stringResource(Res.string.novel_title),
                    medium = ReadingMedium.NOVEL,
                    onBack = onBack,
                    onRefresh = vm::refresh,
                    onSwitchMedium = onOpenManga?.let { open -> { m -> if (m == ReadingMedium.MANGA) open() } },
                    compact = compact,
                )
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingSearchField(
                        query = filters.query,
                        placeholder = stringResource(Res.string.novel_search_hint),
                        showClear = filters.query.isNotEmpty(),
                        onQuery = vm::setQuery,
                        onSubmit = vm::search,
                        onClear = vm::clear,
                    )
                    NovelFilterBar(filters, home.genres, vm::setFilters, vm::clear)
                }
                if (filters.active) {
                    NovelResults(filters, results, lang, onMediaClick, vm::search, vm::loadMore, vm::clear, Modifier.weight(1f))
                } else {
                    NovelFeed(
                        home = home,
                        continueReading = continueReading,
                        lang = lang,
                        compact = compact,
                        undo = undo,
                        onOpen = onMediaClick,
                        onOpenReader = onOpenReader,
                        onRefresh = vm::refresh,
                        onFilters = vm::setFilters,
                        onDismissUpdate = vm::dismissUpdate,
                        onOpenLibrary = onOpenLibrary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            UndoSnackbar(undo, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
private fun NovelFilterBar(filters: NovelFilters, genres: List<String>, onFilters: ((NovelFilters) -> NovelFilters) -> Unit, onClearAll: () -> Unit) {
    val any = stringResource(Res.string.novel_filter_any)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChipMenu(
                label = stringResource(Res.string.novel_filter_genre),
                value = null,
                options = listOf<String?>(null) + genres,
                selected = filters.genre,
                optionLabel = { it?.let { g -> genreLabel(g) } ?: any },
                onSelect = { v -> onFilters { it.copy(genre = v) } },
                active = filters.genre != null,
            )
            FilterChipMenu(
                label = stringResource(Res.string.novel_filter_status),
                value = null,
                options = listOf<String?>(null) + NovelFilters.STATUSES,
                selected = filters.status,
                optionLabel = { novelStatusLabel(it) ?: any },
                onSelect = { v -> onFilters { it.copy(status = v) } },
                active = filters.status != null,
            )
            FilterChipMenu(
                label = stringResource(Res.string.novel_filter_origin),
                value = null,
                options = listOf<String?>(null) + NovelFilters.ORIGINS,
                selected = filters.origin,
                optionLabel = { novelOriginLabel(it) ?: any },
                onSelect = { v -> onFilters { it.copy(origin = v) } },
                active = filters.origin != null,
            )
            val relevance = filters.query.isNotBlank() && filters.sort == MediaSort.POPULARITY
            FilterChipMenu(
                label = stringResource(Res.string.novel_filter_sort),
                value = novelSortLabel(if (relevance) MediaSort.RELEVANCE else filters.sort),
                options = NovelFilters.SORTS,
                selected = filters.sort,
                optionLabel = { novelSortLabel(it) },
                onSelect = { v -> onFilters { it.copy(sort = v) } },
                sort = true,
                active = filters.sort != MediaSort.POPULARITY,
            )
        }
        val active = buildList {
            filters.genre?.let { g -> add(ActiveFilter("${stringResource(Res.string.novel_filter_genre)}: ${genreLabel(g)}") { onFilters { it.copy(genre = null) } }) }
            filters.status?.let { s -> add(ActiveFilter("${stringResource(Res.string.novel_filter_status)}: ${novelStatusLabel(s)}") { onFilters { it.copy(status = null) } }) }
            filters.origin?.let { o -> add(ActiveFilter("${stringResource(Res.string.novel_filter_origin)}: ${novelOriginLabel(o)}") { onFilters { it.copy(origin = null) } }) }
        }
        ActiveFilterRow(active, onClearAll)
    }
}

@Composable
private fun NovelResults(
    filters: NovelFilters,
    results: NovelResultsUi,
    lang: app.nijiyome.model.TitleLanguage,
    onOpen: (Media) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        when (val st = results.state) {
            null, LoadState.Loading -> {
                val w = cardWidth()
                LazyVerticalGrid(GridCells.Adaptive(w), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false, modifier = Modifier.fillMaxSize().decor(DecorRegion.GRID)) {
                    items(12) { LoadingPlaceholder(Modifier.fillMaxWidth().height(w / COVER_ASPECT_RATIO)) }
                }
            }
            is LoadState.Err -> NovelError(st.cause, onRetry)
            is LoadState.Ok -> if (st.value.isEmpty()) {
                ReadingEmpty(
                    kind = EmptyKind.SEARCH,
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(Res.string.novel_search_empty),
                    message = stringResource(Res.string.novel_search_empty_message),
                    actionLabel = stringResource(Res.string.rd_s11_filters_clear),
                    onAction = onClear,
                )
            } else {
                val grid = rememberLazyGridState()
                LaunchedEffect(grid, st.value.size) {
                    snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { if (it >= st.value.size - 6) onLoadMore() }
                }
                val sortText = novelSortLabel(if (filters.query.isNotBlank() && filters.sort == MediaSort.POPULARITY) MediaSort.RELEVANCE else filters.sort)
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
                            if (filters.query.isNotBlank()) stringResource(Res.string.rd_s11_results_for, filters.query.trim()) else stringResource(Res.string.rd_s11_results),
                            trailing = { Text(stringResource(Res.string.rd_s11_sorted_by, sortText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                    }
                    items(st.value.size, key = { "${st.value[it].ref.anilistId ?: it}-$it" }) { i ->
                        val m = st.value[i]
                        val data = m.novelCard(lang, subtitle = listOfNotNull(m.year?.toString(), novelStatusLabel(m.status)).joinToString(" · ").ifBlank { null })
                        TitleCard(data, onClick = { onOpen(m) }, context = CardContext.GRID, modifier = Modifier.cardToDetailsSource(data.key))
                    }
                    if (results.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelFeed(
    home: app.nijiyome.model.NovelHome,
    continueReading: List<ContinueReadingItem>,
    lang: app.nijiyome.model.TitleLanguage,
    compact: Boolean,
    undo: UndoController,
    onOpen: (Media) -> Unit,
    onOpenReader: (NovelReaderArgs) -> Unit,
    onRefresh: () -> Unit,
    onFilters: ((NovelFilters) -> NovelFilters) -> Unit,
    onDismissUpdate: (String) -> Unit,
    onOpenLibrary: (() -> Unit)?,
    modifier: Modifier,
) {
    var hiddenNews by remember { mutableStateOf(emptySet<String>()) }
    val dismissedText = stringResource(Res.string.rd_s11_news_dismissed)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        var section = 0
        item(key = "hero") { NovelFeature(home.hero, home, continueReading, lang, compact, onOpen, onOpenReader, onRefresh, Modifier.homeEntrance(0)) }
        section++
        if (continueReading.isNotEmpty()) {
            val index = section++
            item(key = "continue") {
                Column(Modifier.homeEntrance(index), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        stringResource(Res.string.novel_continue_reading),
                        Modifier.padding(horizontal = 16.dp),
                        trailing = onOpenLibrary?.let { open -> { TextButton(onClick = open) { Text(stringResource(Res.string.rd_s11_library)) } } },
                    )
                    LazyRow(Modifier.fillMaxWidth().decor(DecorRegion.TONIGHT), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(continueReading, key = { _, c -> c.mediaId }) { i, c ->
                            val p = home.lastRead.firstOrNull { it.mediaId == c.mediaId }?.takeIf { c.resume }
                            val data = novelContinueCard(c, p)
                            TitleCard(
                                data,
                                onClick = { if (p == null) onOpen(c.toMedia()) else onOpenReader(novelResumeArgs(p)) },
                                context = CardContext.CONTINUE,
                                show = CardShow(progress = true, status = true, next = true),
                                modifier = Modifier.cardSway(i).cardToDetailsSource(data.key),
                            )
                        }
                    }
                }
            }
        }
        val news = home.followedUpdates.filter { (entry, _) -> NovelRepositoryImpl.mediaKey(entry.ref)?.let { it !in hiddenNews } ?: false }
        if (news.isNotEmpty() || continueReading.isNotEmpty()) {
            val index = section++
            item(key = "news") {
                Column(Modifier.homeEntrance(index).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(Res.string.novel_followed_updates))
                    if (news.isEmpty()) {
                        ReadingEmpty(EmptyKind.GENERIC, Icons.Outlined.NotificationsNone, stringResource(Res.string.rd_s11_up_to_date), stringResource(Res.string.rd_s11_up_to_date_novels))
                    } else {
                        news.forEach { (entry, ch) ->
                            val mediaId = NovelRepositoryImpl.mediaKey(entry.ref) ?: return@forEach
                            NovelNewsRow(
                                entry, ch,
                                onRead = {
                                    onDismissUpdate(mediaId)
                                    onOpenReader(NovelReaderArgs(mediaId, entry.title, entry.poster, ch.extensionId, ch.sourceId, ch.itemId, ch.id, ch.number, anilistId = entry.ref.anilistId))
                                },
                                onDismiss = {
                                    hiddenNews = hiddenNews + mediaId
                                    undo.show(dismissedText, undo = { hiddenNews = hiddenNews - mediaId }, onCommit = { onDismissUpdate(mediaId) })
                                },
                            )
                        }
                    }
                }
            }
        }
        listOf(
            Triple("trending", Res.string.novel_row_trending, MediaSort.TRENDING),
            Triple("popular", Res.string.novel_row_popular, MediaSort.POPULARITY),
        ).forEach { (key, title, sort) ->
            val index = section++
            val rowState = if (key == "trending") home.trending else home.popular
            item(key = key) {
                MediaCardRow(
                    title = stringResource(title),
                    state = rowState,
                    onRetry = onRefresh,
                    onOpen = onOpen,
                    cardOf = { it.novelCard(lang) },
                    // POPULARITY is the default sort, so "Ver todo" of Populares has nothing to filter by.
                    onSeeAll = if (sort != MediaSort.POPULARITY) ({ onFilters { f -> f.copy(sort = sort) } }) else null,
                    modifier = Modifier.homeEntrance(index),
                )
            }
        }
        if (home.genres.isNotEmpty()) {
            val index = section
            item(key = "genres") {
                Column(Modifier.homeEntrance(index).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(Res.string.novel_row_genres))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        home.genres.forEach { g -> SuggestionChip(onClick = { onFilters { NovelFilters(genre = g) } }, label = { Text(genreLabel(g)) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelFeature(
    hero: LoadState<List<Media>>,
    home: app.nijiyome.model.NovelHome,
    continueReading: List<ContinueReadingItem>,
    lang: app.nijiyome.model.TitleLanguage,
    compact: Boolean,
    onOpen: (Media) -> Unit,
    onOpenReader: (NovelReaderArgs) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    when (hero) {
        LoadState.Loading -> LoadingPlaceholder(modifier.fillMaxWidth().padding(horizontal = 16.dp).height(if (compact) 220.dp else 320.dp))
        is LoadState.Err -> NovelError(hero.cause, onRefresh, modifier)
        is LoadState.Ok -> if (hero.value.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).decor(DecorRegion.HERO)) {
                // phones keep the multi-browse carousel; wide windows one title at a time (MAN-01)
                val slide: @Composable (Media) -> Unit = { media ->
                    val data = media.novelCard(lang)
                    val reading = continueReading.firstOrNull { it.ref.anilistId != null && it.ref.anilistId == media.ref.anilistId }
                    val saved = reading?.let { r -> home.lastRead.firstOrNull { it.mediaId == r.mediaId }?.takeIf { r.resume } }
                    val text: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(listOfNotNull(stringResource(Res.string.rd_s11_featured), novelStatusLabel(media.status)).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(media.displayTitle(lang), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                            media.synopsis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = if (compact) 3 else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 560.dp)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (saved != null) {
                                    Button(onClick = { onOpenReader(novelResumeArgs(saved)) }) { Text(stringResource(Res.string.rd_s11_resume_chapter, ChapterText.formatNumber(saved.chapterNumber) ?: "?")) }
                                    FilledTonalButton(onClick = { onOpen(media) }) { Text(stringResource(Res.string.novel_hero_open)) }
                                } else {
                                    Button(onClick = { onOpen(media) }) { Text(stringResource(Res.string.novel_hero_open)) }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(if (compact) 16.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp), verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Bottom) {
                        Box(Modifier.width(if (compact) 112.dp else 220.dp)) { TitleCard(data, onClick = { onOpen(media) }, context = CardContext.HERO, modifier = Modifier.cardToDetailsSource(data.key)) }
                        Box(Modifier.weight(1f)) { text() }
                    }
                }
                if (compact) {
                    HeroCarousel(items = hero.value, key = { it.ref.anilistId ?: it.title.hashCode() }) { media, _ -> slide(media) }
                } else {
                    FeaturePager(items = hero.value, key = { it.ref.anilistId ?: it.title.hashCode() }) { media -> slide(media) }
                }
            }
        }
    }
}

@Composable
private fun NovelNewsRow(entry: ReadEntry, chapter: NovelChapter, onRead: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MediaCover(url = entry.poster, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.novel_followed_new_chapter, ChapterText.formatNumber(chapter.number) ?: "?"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onRead) { Text(stringResource(Res.string.novel_read)) }
        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s11_news_dismiss, entry.title)) }
    }
}

/** The card of a novel "Seguir leyendo" entry: progress inside the chapter, "Cap. N" as the next label. */
@Composable
internal fun novelContinueCard(c: ContinueReadingItem, p: NovelProgress?): TitleCardData {
    val chapter = ChapterText.formatNumber(p?.chapterNumber ?: c.chapter) ?: "?"
    return TitleCardData(
        key = novelCardKey(c.ref, c.mediaId),
        title = p?.title ?: c.title,
        coverUrl = p?.poster ?: c.poster,
        subtitle = if (p != null) stringResource(Res.string.novel_continue_line, chapter, (p.offset * 100).toInt()) else stringResource(Res.string.novel_chapter_n, chapter),
        progress = p?.offset?.coerceIn(0f, 1f),
        nextLabel = stringResource(Res.string.rd_s11_chapter_short, chapter),
    )
}

internal fun novelResumeArgs(p: NovelProgress): NovelReaderArgs = NovelReaderArgs(
    p.mediaId, p.title, p.poster, p.extensionId, p.sourceId, p.itemId, p.chapterId, p.chapterNumber, p.paragraph,
    anilistId = NovelRepositoryImpl.refOf(p.mediaId)?.anilistId,
)

/** Library › Novelas (NOV-03; `LibraryTab.NOVELS`): status chips with counts, continue cards, rows with status mark and progress. */
@Composable
fun NovelLibraryTab(onOpenDetails: (anilistId: Int) -> Unit, onOpenAccounts: () -> Unit, modifier: Modifier = Modifier) {
    val vm: NovelLibraryViewModel = koinViewModel()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val lastRead by vm.lastRead.collectAsStateWithLifecycle()
    val continueReading by vm.continueReading.collectAsStateWithLifecycle()
    val linked by vm.linked.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<ListStatus?>(ListStatus.WATCHING) }
    LaunchedEffect(linked) { if (linked) vm.sync() }
    Column(modifier) {
        if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(Res.string.novel_library_all)) })
                NOVEL_READ_STATUSES.forEach { s ->
                    FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text("${stringResource(s.readLabelRes())} · ${entries?.count { it.status == s } ?: 0}") })
                }
            }
            if (linked) IconButton(onClick = vm::sync, enabled = !syncing) { Icon(Icons.Outlined.Sync, contentDescription = stringResource(Res.string.novel_library_sync)) }
        }
        val list = entries ?: return@Column
        if (list.isEmpty() && continueReading.isEmpty()) {
            ReadingEmpty(
                kind = EmptyKind.LIBRARY,
                icon = Icons.Outlined.AutoStories,
                title = stringResource(Res.string.novel_library_empty),
                message = stringResource(if (linked) Res.string.novel_library_empty_linked else Res.string.novel_library_empty_message),
                actionLabel = if (linked) null else stringResource(Res.string.novel_library_link),
                onAction = if (linked) null else onOpenAccounts,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }
        val shown = if (filter == null) list else list.filter { it.status == filter }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (continueReading.isNotEmpty()) {
                item(key = "continue") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(stringResource(Res.string.novel_continue_reading), Modifier.padding(horizontal = 16.dp))
                        LazyRow(Modifier.fillMaxWidth().decor(DecorRegion.TONIGHT), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(continueReading, key = { _, c -> c.mediaId }) { i, c ->
                                val p = lastRead.firstOrNull { it.mediaId == c.mediaId }?.takeIf { c.resume }
                                val data = novelContinueCard(c, p)
                                TitleCard(data, onClick = { c.ref.anilistId?.let(onOpenDetails) }, context = CardContext.CONTINUE, modifier = Modifier.cardSway(i).cardToDetailsSource(data.key))
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) item(key = "none") { Text(stringResource(Res.string.novel_library_none_in_status), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shown, key = { "${it.ref.anilistId}-${it.ref.malId}" }) { e ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = e.ref.anilistId != null) { e.ref.anilistId?.let(onOpenDetails) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(48.dp)) { MediaCover(url = e.poster, modifier = Modifier.fillMaxWidth().cardToDetailsSource(novelCardKey(e.ref, e.title))) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusMark(e.status.slot(), label = stringResource(e.status.readLabelRes()), selected = true)
                            Text(stringResource(Res.string.novel_progress_label, e.progress, e.totalChapters?.toString() ?: "?"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        e.totalChapters?.takeIf { it > 0 }?.let { total ->
                            ProgressTrack(progress = (e.progress.toFloat() / total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth(), segments = total.takeIf { it <= 52 })
                        }
                    }
                }
            }
        }
    }
}

internal val NOVEL_READ_STATUSES = listOf(ListStatus.WATCHING, ListStatus.PLANNING, ListStatus.COMPLETED, ListStatus.PAUSED, ListStatus.DROPPED, ListStatus.REPEATING)
