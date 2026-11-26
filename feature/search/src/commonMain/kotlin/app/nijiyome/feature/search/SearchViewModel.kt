package app.nijiyome.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nijiyome.metadata.TrendWindow
import app.nijiyome.model.AppError
import app.nijiyome.model.AppLog
import app.nijiyome.model.CharacterRef
import app.nijiyome.model.ExtensionHomeItem
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.FeedKind
import app.nijiyome.model.FeedPage
import app.nijiyome.model.InstalledExtension
import app.nijiyome.model.ListStatus
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.LoadState
import app.nijiyome.model.MatureContentMode
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MediaSearchQuery
import app.nijiyome.model.MediaSort
import app.nijiyome.model.MediaTag
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.PER_PAGE_CHARACTERS
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.TitleLanguage
import app.nijiyome.ui.media.distinctByKey
import app.nijiyome.ui.state.runCatchingApp
import app.nijiyome.ui.state.toAppError
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface SearchResults {
    /** Nothing typed yet (and no feed / genre / studio to browse): show the prompt, the feeds and the recent searches. */
    data object Idle : SearchResults

    data object Loading : SearchResults

    /** The provider returned no items. */
    data object Empty : SearchResults

    /**
     * One kind of hit per search type: [items] (anime), [characters] (type Personajes) or [extensionItems] (an extension chip).
     * [hasNext] comes from the provider (`Page.pageInfo.hasNextPage`).
     */
    data class Ok(
        val items: List<Media>,
        val page: Int,
        val hasNext: Boolean,
        val loadingMore: Boolean = false,
        val characters: List<CharacterRef> = emptyList(),
        val extensionItems: List<ExtensionHomeItem> = emptyList(),
    ) : SearchResults {
        val size: Int get() = items.size + characters.size + extensionItems.size
    }

    data class Err(val cause: AppError) : SearchResults
}

/**
 * Search types (classic unified search). Manga and novels are the reader of wave B and are not offered here yet; [CHARACTERS] is the
 * AniList character search (`MetadataRepository.searchCharacters`).
 */
enum class SearchType { ANIME, CHARACTERS }

/** Sparkles Explorar "Qué buscar": anime and characters search here, manga and novels open their homes, extensions their catalogues. */
enum class ExploreScope { ANIME, CHARACTERS, MANGA, NOVELS, EXTENSIONS }

/**
 * Filters of the bottom sheet (classic `SearchPage` anime filters), applied SERVER-SIDE through [MediaSearchQuery]. [season] / [status]
 * / [formats] hold AniList values (`WINTER`, `RELEASING`, `TV`...); [year] is the exact season year; the ranges are inclusive; [sort] null
 * = automatic (relevance with text, popularity when browsing).
 */
data class SearchFilters(
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val formats: List<String> = emptyList(),
    val year: Int? = null,
    val season: String? = null,
    val status: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val episodesFrom: Int? = null,
    val episodesTo: Int? = null,
    val scoreFrom: Int? = null,
    val scoreTo: Int? = null,
    val sort: MediaSort? = null,
) {
    val activeCount: Int
        get() = genres.size + tags.size + formats.size +
            listOfNotNull(year, season, status, sort).size +
            (if (yearFrom != null || yearTo != null) 1 else 0) +
            (if (episodesFrom != null || episodesTo != null) 1 else 0) +
            (if (scoreFrom != null || scoreTo != null) 1 else 0)

    val isEmpty: Boolean get() = activeCount == 0

    /** Single-genre compatibility (Route.SearchFor(genre)). */
    val genre: String? get() = genres.firstOrNull()

    /** The query AniList receives: these filters + [text] / [studio] / [adult], on top of [base] (a feed mapped to sort / status). */
    fun toQuery(text: String?, adult: Boolean, studio: String? = null, base: MediaSearchQuery = MediaSearchQuery()): MediaSearchQuery {
        val hasText = !text.isNullOrBlank()
        return base.copy(
            query = text?.trim()?.takeIf { it.isNotEmpty() },
            genres = genres,
            tags = tags,
            formats = formats,
            status = status ?: base.status,
            season = season ?: base.season,
            seasonYear = year ?: base.seasonYear,
            yearFrom = yearFrom,
            yearTo = yearTo,
            episodesFrom = episodesFrom,
            episodesTo = episodesTo,
            scoreFrom = scoreFrom,
            scoreTo = scoreTo,
            sort = sort ?: if (hasText) MediaSort.RELEVANCE else base.sort,
            adult = adult,
            studio = studio,
        )
    }
}

/** A feed of Buscar (Home "Ver todo", the Explorar chips) as an AniList query, so the filters can narrow it server-side. */
internal fun feedQuery(feed: FeedKind, today: LocalDate = LocalDate.now()): MediaSearchQuery = when (feed) {
    FeedKind.TRENDING -> MediaSearchQuery(sort = MediaSort.TRENDING)
    FeedKind.POPULAR -> MediaSearchQuery(sort = MediaSort.POPULARITY)
    FeedKind.TOP_RATED -> MediaSearchQuery(sort = MediaSort.SCORE)
    FeedKind.AIRING -> MediaSearchQuery(status = "RELEASING", sort = MediaSort.POPULARITY)
    FeedKind.UPCOMING -> MediaSearchQuery(status = "NOT_YET_RELEASED", sort = MediaSort.POPULARITY)
    FeedKind.RECENTLY_COMPLETED -> MediaSearchQuery(status = "FINISHED", sort = MediaSort.START_DATE)
    FeedKind.SEASONAL -> MediaSearchQuery(
        season = when (today.monthValue) { in 1..3 -> "WINTER"; in 4..6 -> "SPRING"; in 7..9 -> "SUMMER"; else -> "FALL" },
        seasonYear = today.year,
        sort = MediaSort.POPULARITY,
    )
}

/** Extension chip of the source row: enabled only when the extension has a searchable catalogue (`custom` sources). */
data class ExtensionChip(val extension: InstalledExtension, val searchable: Boolean)

data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults.Idle,
    /** Recent searches, newest first (at most [SEARCH_HISTORY_SIZE]). */
    val history: List<String> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
    /** Feed browsed while the query is empty (Home "Ver todo" -> Route.SearchFor(feed), Explorar chips). */
    val feed: FeedKind? = null,
    /** Time window of the Tendencias feed (classic TrendingPage selector). */
    val trendWindow: TrendWindow = TrendWindow.TODAY,
    /** Studio catalogue (Details studio chip -> Route.SearchFor(studio)). */
    val studio: String? = null,
    val type: SearchType = SearchType.ANIME,
    /** Selected extension chip (search inside that extension); null = metadata. */
    val extensionId: String? = null,
    val extensions: List<ExtensionChip> = emptyList(),
    /** Include 18+ titles (initial value AppSettings.showAdult, then the sheet's toggle). */
    val adult: Boolean = false,
    val titleLanguage: TitleLanguage = TitleLanguage.ROMAJI,
    /** AppSettings.matureContent: BLUR blurs adult result covers until tapped (WARN is asked by the Details gate). */
    val matureContent: MatureContentMode = MatureContentMode.WARN,
    /** Provider catalogues for the sheet (fallback: [KNOWN_GENRES]); tags are loaded on the first sheet open. */
    val genreOptions: List<String> = KNOWN_GENRES,
    val tagOptions: List<MediaTag> = emptyList(),
    val tagsLoading: Boolean = false,
    /**
     * Sparkles Explorar: the "Catálogo de extensión" scope is on. Normally [extensionId] says which catalogue; with no searchable
     * extension installed the scope stays on with [extensionId] null and nothing is searched (the screen offers Fuentes).
     */
    val extensionScope: Boolean = false,
    /** The user's list by `a<anilistId>` / `m<malId>`: the status the result cards show and the "Mi lista" facet filters on. */
    val listStatuses: Map<String, ListStatus> = emptyMap(),
    /** "Mi lista" facet, applied to the loaded results: [MY_LIST_OUT] and / or [ListStatus] names. Empty = off. */
    val myList: Set<String> = emptySet(),
    /** AniList refused the text search and the backup providers answered it: the banner says so and counts down to a retry. */
    val degraded: AppError.RateLimited? = null,
    /** Explorar › Lienzo "En tendencia" row (its own short list, by [trendWindow]); null until the canvas asks for it. */
    val trendingRow: LoadState<List<Media>>? = null,
) {
    /**
     * The loaded anime results; with 18+ off, anything the provider still tags Hentai is dropped (the query already excludes isAdult);
     * then the "Mi lista" facet.
     */
    val shown: List<Media>
        get() = (results as? SearchResults.Ok)?.items
            ?.filter { adult || !it.isProbablyAdult() }
            ?.filter { myList.isEmpty() || matchesMyList(listStatusOf(it.ref), myList) }
            .orEmpty()

    /** The list status of [ref], when the title is on the user's list. */
    fun listStatusOf(ref: MediaRef): ListStatus? =
        ref.anilistId?.let { listStatuses["a$it"] } ?: ref.malId?.let { listStatuses["m$it"] }

    /** Every filter that narrows the results, "Mi lista" included (the empty state offers to drop them all). */
    val narrowingCount: Int get() = filters.activeCount + (if (myList.isEmpty()) 0 else 1)

    /** The scope tab the state is in. */
    val scope: ExploreScope
        get() = when {
            extensionScope || extensionId != null -> ExploreScope.EXTENSIONS
            type == SearchType.CHARACTERS -> ExploreScope.CHARACTERS
            else -> ExploreScope.ANIME
        }

    /** Something is browsed with an empty query (feed, filters, studio). */
    val browsing: Boolean get() = query.isBlank() && (feed != null || studio != null || !filters.isEmpty)
}

/** "Mi lista" option for titles that are not on the list. */
const val MY_LIST_OUT = "out"

/** A title with [status] (null = not on the list) passes the "Mi lista" facet [picked]. */
internal fun matchesMyList(status: ListStatus?, picked: Set<String>): Boolean =
    if (status == null) MY_LIST_OUT in picked else status.name in picked

/** AniList's adult titles carry the Hentai genre ([Media] has no isAdult flag); used to mark / drop them. */
fun Media.isProbablyAdult(): Boolean = genres.any { it.equals("Hentai", ignoreCase = true) }

/** What one fetch asks for; two equal requests are the same search. */
internal data class SearchRequest(
    val query: String,
    val type: SearchType,
    val extensionId: String?,
    val feed: FeedKind?,
    val window: TrendWindow,
    val studio: String?,
    val filters: SearchFilters,
    val adult: Boolean,
    /** Catalogue scope with no catalogue picked: nothing to search. */
    val extensionScope: Boolean = false,
) {
    val idle: Boolean
        get() = when {
            extensionScope && extensionId == null -> true
            type == SearchType.CHARACTERS || extensionId != null -> query.isBlank()
            else -> query.isBlank() && feed == null && studio == null && filters.isEmpty
        }
}

/** Tendencias with a time window other than "today" (:core:metadata `TrendingFeed`, bound in searchModule). */
fun interface TrendingSource {
    suspend fun page(window: TrendWindow, page: Int, adult: Boolean): FeedPage
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val metadata: MetadataRepository,
    debounceMillis: Long = DEFAULT_DEBOUNCE_MS,
    private val historyStore: SearchHistoryStore = InMemorySearchHistoryStore(),
    private val extensions: ExtensionRepository? = null,
    settings: SettingsRepository? = null,
    private val trending: TrendingSource? = null,
    /** Sparkles: the user's list, for the status on each result card, the "Mi lista" facet and changing a status from a result. */
    private val lists: ListsRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SearchUiState(
            history = historyStore.load(),
            adult = settings?.settings?.value?.showAdult ?: false,
            titleLanguage = settings?.settings?.value?.titleLanguage ?: TitleLanguage.ROMAJI,
            matureContent = settings?.settings?.value?.matureContent ?: MatureContentMode.WARN,
        ),
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queries = MutableStateFlow("")
    private var lastRequest: SearchRequest? = null
    private var loadMoreJob: Job? = null
    /** BUG-146: the text request whose first page came from the plain-search fallback (its next pages come from there too). */
    private var fallbackRequest: SearchRequest? = null
    private var appliedInitial: List<Any?>? = null
    private var tagsRequested = false
    private var lastShowAdult: Boolean? = settings?.settings?.value?.showAdult
    /** The error that sent the last text search to the backup providers (BUG-146). */
    private var fallbackCause: AppError? = null
    private var trendingRowJob: Job? = null

    init {
        viewModelScope.launch {
            queries
                .debounce(debounceMillis)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { runSearch() }
        }
        if (extensions != null) {
            viewModelScope.launch {
                extensions.installed
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        val chips = list.filter { it.enabled }.map { ExtensionChip(it, searchable = it.type == CUSTOM_TYPE) }
                        _state.update { s ->
                            val keep = s.extensionId?.takeIf { id -> chips.any { it.extension.id == id && it.searchable } }
                            // The catalogue scope picks the first searchable one when the extensions arrive after it was chosen.
                            val pick = keep ?: if (s.extensionScope) chips.firstOrNull { it.searchable }?.extension?.id else null
                            s.copy(extensions = chips, extensionId = pick)
                        }
                    }
            }
        }
        if (settings != null) {
            viewModelScope.launch {
                settings.settings.collect { s ->
                    // The Settings toggle is the initial value; a change there wins over the sheet's last choice.
                    val adultChanged = lastShowAdult != null && lastShowAdult != s.showAdult
                    lastShowAdult = s.showAdult
                    _state.update { it.copy(titleLanguage = s.titleLanguage, matureContent = s.matureContent, adult = if (adultChanged) s.showAdult else it.adult) }
                    if (adultChanged) refresh()
                }
            }
        }
        if (lists != null) {
            viewModelScope.launch {
                lists.entries(null)
                    .catch { emit(emptyList()) }
                    .collect { entries ->
                        val byKey = HashMap<String, ListStatus>()
                        entries.forEach { e ->
                            e.ref.anilistId?.let { byKey["a$it"] = e.status }
                            e.ref.malId?.let { byKey["m$it"] = e.status }
                        }
                        _state.update { it.copy(listStatuses = byKey) }
                    }
            }
        }
        viewModelScope.launch {
            runCatchingApp { metadata.genres(MediaKind.ANIME) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { genres -> _state.update { it.copy(genreOptions = genres) } }
        }
    }

    /**
     * Pre-fills the screen from Route.SearchFor (query / feed / genre / studio). Applied once per distinct value set, so coming back to
     * the screen keeps what the user typed since.
     */
    fun applyInitial(query: String?, feed: FeedKind?, genre: String?, studio: String? = null) {
        val key = listOf(query, feed, genre, studio)
        if (key == appliedInitial) return
        appliedInitial = key
        if (query == null && feed == null && genre == null && studio == null) return
        _state.update {
            it.copy(
                query = query.orEmpty(),
                feed = feed,
                filters = SearchFilters(genres = listOfNotNull(genre)),
                studio = studio,
                type = SearchType.ANIME,
                extensionId = null,
                extensionScope = false,
            )
        }
        queries.value = query.orEmpty()
        refresh()
    }

    /**
     * EXP-01: Explorar › Facetas opens on Tendencias (its time window and results grid), as in the artifact, instead of an empty page.
     * Once per screen, and only when nothing else was asked for (a query, feed, genre, studio, filter or another scope).
     */
    fun openOnTrending() {
        if (openedOnTrending) return
        openedOnTrending = true
        val s = _state.value
        val untouched = s.query.isBlank() && s.feed == null && s.studio == null && s.filters == SearchFilters() &&
            s.scope == ExploreScope.ANIME && s.results == SearchResults.Idle
        if (untouched) selectFeed(FeedKind.TRENDING)
    }

    private var openedOnTrending = false

    /** Explorar: a taste or genre chip browses that genre (query, feed and studio dropped; the other filters stay). */
    fun browseGenre(genre: String) {
        _state.update {
            it.copy(
                query = "",
                feed = null,
                studio = null,
                filters = it.filters.copy(genres = listOf(genre)),
                type = SearchType.ANIME,
                extensionId = null,
                extensionScope = false,
            )
        }
        queries.value = ""
        refresh()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        queries.value = value
    }

    /** IME "search": runs now and records the query in the recent searches. */
    fun submit() {
        recordHistory(_state.value.query)
        refresh()
    }

    fun useHistory(query: String) {
        onQueryChange(query)
        submit()
    }

    fun removeHistory(query: String) {
        val next = _state.value.history.filter { it != query }
        historyStore.save(next)
        _state.update { it.copy(history = next) }
    }

    /** Settings › Metadatos › "Borrar búsquedas recientes" (also offered under the list). */
    fun clearHistory() {
        historyStore.save(emptyList())
        _state.update { it.copy(history = emptyList()) }
    }

    /** Undo of removing one or all recent searches: puts [entries] back as they were. */
    fun restoreHistory(entries: List<String>) {
        val next = entries.take(SEARCH_HISTORY_SIZE)
        historyStore.save(next)
        _state.update { it.copy(history = next) }
    }

    /** The store may have been cleared from Settings while this screen was in the back stack. */
    fun reloadHistory() {
        val stored = historyStore.load()
        if (stored != _state.value.history) _state.update { it.copy(history = stored) }
    }

    fun clear() = onQueryChange("")

    fun setFilters(filters: SearchFilters) {
        _state.update { it.copy(filters = filters) }
        refresh()
    }

    /** "Limpiar": every filter off, the search runs again. */
    fun resetFilters() = setFilters(SearchFilters())

    /** Explorar empty state "Quitar los N filtros": the sheet's filters and the "Mi lista" facet. */
    fun resetAllFilters() {
        _state.update { it.copy(myList = emptySet()) }
        resetFilters()
    }

    /** "Mi lista" facet: filters the loaded results locally (no new request). */
    fun setMyList(picked: Set<String>) = _state.update { it.copy(myList = picked) }

    /** The list can be edited from a result (a lists repository is bound). */
    val canEditList: Boolean get() = lists != null

    /**
     * Context menu / status picker of a result: puts [media] on the list with [status] (null removes it). [onDone] gets the status
     * it had before (for Undo) and whether it worked.
     */
    fun setListStatus(media: Media, status: ListStatus?, onDone: (previous: ListStatus?, ok: Boolean) -> Unit = { _, _ -> }) {
        val repo = lists ?: return
        val previous = _state.value.listStatusOf(media.ref)
        viewModelScope.launch {
            val ok = runCatchingApp { repo.setStatus(media, status).getOrThrow() }.isSuccess
            onDone(previous, ok)
        }
    }

    fun setAdult(adult: Boolean) {
        if (adult == _state.value.adult) return
        _state.update { it.copy(adult = adult) }
        refresh()
    }

    /** Applies the sheet's draft (filters + 18+) as one search. */
    fun applySheet(filters: SearchFilters, adult: Boolean) {
        _state.update { it.copy(filters = filters, adult = adult) }
        refresh()
    }

    /** Explorar chip / Home "Ver todo": browse [feed] (query cleared, studio dropped). */
    fun selectFeed(feed: FeedKind) {
        _state.update { it.copy(feed = feed, query = "", studio = null, type = SearchType.ANIME, extensionId = null, extensionScope = false) }
        queries.value = ""
        refresh()
    }

    /** Drops the browsed feed chip. */
    fun clearFeed() {
        _state.update { it.copy(feed = null) }
        refresh()
    }

    fun setTrendWindow(window: TrendWindow) {
        if (window == _state.value.trendWindow) return
        _state.update { it.copy(trendWindow = window) }
        if (_state.value.trendingRow != null) loadTrendingRow()
        refresh()
    }

    /** Explorar › Lienzo: the "En tendencia" row, loaded once (again when the window changes, or after a failure). */
    fun ensureTrendingRow() {
        val row = _state.value.trendingRow
        if (row == null || row is LoadState.Err) loadTrendingRow()
    }

    fun loadTrendingRow() {
        val window = _state.value.trendWindow
        val adult = _state.value.adult
        _state.update { it.copy(trendingRow = LoadState.Loading) }
        trendingRowJob?.cancel()
        trendingRowJob = viewModelScope.launch {
            val r = runCatchingApp {
                val page = if (window != TrendWindow.TODAY && trending != null) {
                    trending.page(window, 1, adult)
                } else {
                    metadata.feed(MediaKind.ANIME, FeedKind.TRENDING, 1)
                }
                page.items.distinctByKey().filter { adult || !it.isProbablyAdult() }.take(TRENDING_ROW_SIZE)
            }
            _state.update {
                it.copy(trendingRow = r.fold(onSuccess = { v -> LoadState.Ok(v) }, onFailure = { e -> LoadState.Err(e.toAppError()) }))
            }
        }
    }

    /** Explorar scope tabs: anime (metadata), characters or an extension's own catalogue. Manga and novels navigate away. */
    fun selectScope(scope: ExploreScope) {
        when (scope) {
            ExploreScope.ANIME -> _state.update { it.copy(type = SearchType.ANIME, extensionId = null, extensionScope = false) }
            ExploreScope.CHARACTERS -> _state.update { it.copy(type = SearchType.CHARACTERS, extensionId = null, extensionScope = false) }
            ExploreScope.EXTENSIONS -> _state.update { s ->
                val id = s.extensionId ?: s.extensions.firstOrNull { it.searchable }?.extension?.id
                s.copy(type = SearchType.ANIME, extensionId = id, extensionScope = true)
            }
            ExploreScope.MANGA, ExploreScope.NOVELS -> return
        }
        refresh()
    }

    fun clearStudio() {
        _state.update { it.copy(studio = null) }
        refresh()
    }

    fun setType(type: SearchType) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type, extensionId = if (type == SearchType.ANIME) it.extensionId else null) }
        refresh()
    }

    /** Extension chip (null = back to the metadata source). Only searchable chips can be selected. */
    fun selectExtension(id: String?) {
        val chip = id?.let { i -> _state.value.extensions.firstOrNull { it.extension.id == i } }
        if (id != null && chip?.searchable != true) return
        _state.update { it.copy(extensionId = id, type = SearchType.ANIME, extensionScope = id != null) }
        refresh()
    }

    /** An extension result: look its title up in the metadata provider (back to the metadata source). */
    fun openExtensionItem(item: ExtensionHomeItem) {
        _state.update { it.copy(extensionId = null, type = SearchType.ANIME, feed = null, studio = null, extensionScope = false) }
        onQueryChange(item.title)
        submit()
    }

    /** First open of the filters sheet: the AniList tag catalogue. */
    fun ensureTags() {
        if (tagsRequested) return
        tagsRequested = true
        _state.update { it.copy(tagsLoading = true) }
        viewModelScope.launch {
            val tags = runCatchingApp { metadata.tags(MediaKind.ANIME) }.getOrNull().orEmpty()
            if (tags.isEmpty()) tagsRequested = false
            _state.update { it.copy(tagOptions = tags, tagsLoading = false) }
        }
    }

    /** Re-runs the current request immediately (Retry button). */
    fun retry() {
        lastRequest = null
        refresh()
    }

    fun loadMore() {
        val current = _state.value.results as? SearchResults.Ok ?: return
        val request = lastRequest ?: return
        if (!current.hasNext || current.loadingMore) return
        _state.update { it.copy(results = current.copy(loadingMore = true)) }
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            runCatchingApp { fetch(request, current.page + 1) }
                .onSuccess { next ->
                    if (lastRequest != request) return@onSuccess
                    _state.update {
                        it.copy(
                            results = SearchResults.Ok(
                                items = (current.items + next.items).distinctByKey(),
                                page = next.page,
                                hasNext = next.hasNext,
                                characters = (current.characters + next.characters).distinctBy { c -> c.id },
                                extensionItems = current.extensionItems,
                            ),
                        )
                    }
                }
                .onFailure {
                    if (lastRequest == request) _state.update { s -> s.copy(results = current.copy(loadingMore = false)) }
                }
        }
    }

    private fun currentRequest(): SearchRequest {
        val s = _state.value
        return SearchRequest(
            query = s.query.trim(),
            type = s.type,
            extensionId = s.extensionId,
            feed = s.feed?.takeIf { s.query.isBlank() },
            window = s.trendWindow,
            studio = s.studio,
            filters = s.filters,
            adult = s.adult,
            extensionScope = s.extensionScope,
        )
    }

    /**
     * Runs the current request now. Never cancels an in-flight run: a duplicate request is skipped by [runSearch] and a superseded
     * one drops its result (it compares [lastRequest] after the fetch).
     */
    private fun refresh() {
        viewModelScope.launch { runSearch() }
    }

    private fun recordHistory(query: String) {
        val next = _state.value.history.withSearch(query)
        if (next == _state.value.history) return
        historyStore.save(next)
        _state.update { it.copy(history = next) }
    }

    private suspend fun runSearch() {
        val s = _state.value
        val request = currentRequest()
        val running = s.results is SearchResults.Loading || s.results is SearchResults.Ok || s.results is SearchResults.Empty
        if (request == lastRequest && running) return
        loadMoreJob?.cancel()
        lastRequest = request
        if (request.idle) {
            _state.update { it.copy(results = SearchResults.Idle, degraded = null) }
            return
        }
        _state.update { it.copy(results = SearchResults.Loading) }
        val result = runCatchingApp { fetch(request, 1) }
        if (lastRequest != request) return
        val degraded = (fallbackCause as? AppError.RateLimited)?.takeIf { fallbackRequest == request && result.isSuccess }
        _state.update {
            it.copy(
                results = result.fold(
                    onSuccess = { ok -> if (ok.size == 0) SearchResults.Empty else ok },
                    onFailure = { e -> SearchResults.Err(e.toAppError()) },
                ),
                degraded = degraded,
            )
        }
    }

    private suspend fun fetch(request: SearchRequest, page: Int): SearchResults.Ok {
        if (request.type == SearchType.CHARACTERS) {
            val list = metadata.searchCharacters(request.query, page)
            return SearchResults.Ok(emptyList(), page, hasNext = list.size >= PER_PAGE_CHARACTERS, characters = list)
        }
        val extId = request.extensionId
        if (extId != null) {
            val items = extensions?.searchExtension(extId, request.query).orEmpty()
            return SearchResults.Ok(emptyList(), 1, hasNext = false, extensionItems = items)
        }
        val feed = request.feed
        val p: FeedPage = when {
            request.studio != null ->
                metadata.searchAdvanced(MediaKind.ANIME, request.filters.toQuery(request.query, request.adult, request.studio), page)
            request.query.isNotEmpty() -> textSearch(request, page)
            feed == FeedKind.TRENDING && request.window != TrendWindow.TODAY && request.filters.isEmpty && trending != null ->
                trending.page(request.window, page, request.adult)
            feed != null && request.filters.isEmpty && !request.adult ->
                metadata.feed(MediaKind.ANIME, feed, page)
            feed != null ->
                metadata.searchAdvanced(MediaKind.ANIME, request.filters.toQuery(null, request.adult, base = feedQuery(feed)), page)
            else ->
                metadata.searchAdvanced(MediaKind.ANIME, request.filters.toQuery(null, request.adult), page)
        }
        val items = p.items.distinctByKey().filter { request.adult || !it.isProbablyAdult() }
        return SearchResults.Ok(items, p.page, p.hasNext)
    }

    /**
     * BUG-146: a text search goes to AniList's filtered search first (same results and order as always). When that fails (AniList
     * limiting or not answering) and the search is text only (no filters: the plain search cannot honour them), the plain
     * [MetadataRepository.search] answers through the fallback providers (Kitsu, ...), and so do the next pages of that query. If
     * the fallback fails too, the original error stays (it is the one that says AniList is limiting requests).
     */
    private suspend fun textSearch(request: SearchRequest, page: Int): FeedPage {
        if (page > 1 && fallbackRequest == request) return metadata.search(MediaKind.ANIME, request.query, page)
        val advanced = try {
            metadata.searchAdvanced(MediaKind.ANIME, request.filters.toQuery(request.query, request.adult), page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (page > 1 || !request.filters.isEmpty) throw e
            val plain = try {
                metadata.search(MediaKind.ANIME, request.query, page)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                throw e
            }
            AppLog.i(TAG, "text search: filtered search failed (${e.toAppError()}), answered by the plain search (${plain.items.size} results)")
            fallbackRequest = request
            fallbackCause = e.toAppError()
            return plain
        }
        if (fallbackRequest == request) fallbackRequest = null
        return advanced
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 350L
        const val TRENDING_ROW_SIZE = 12
        private const val TAG = "search"
        private const val CUSTOM_TYPE = "custom"
    }
}

/** Provider genre vocabulary fallback (AniList genre names, shown untranslated like Media.genres). */
internal val KNOWN_GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
    "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
)
