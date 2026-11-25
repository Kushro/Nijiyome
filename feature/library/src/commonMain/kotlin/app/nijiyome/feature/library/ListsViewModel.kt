// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nijiyome.model.AppError
import app.nijiyome.model.CharacterRef
import app.nijiyome.model.LinkedAccount
import app.nijiyome.model.ListEntry
import app.nijiyome.model.ListStatus
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.patterns.ListEntryRules
import app.nijiyome.ui.state.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

// State of Library › Mi lista (the three structures share it) and Library › Favoritos. Parity wave A origin (worker q), Sparkles S7.

/** Display order of the statuses (artifact `ORDER`): shelves, board columns and status sort. */
val LibraryStatusOrder: List<ListStatus> = listOf(
    ListStatus.WATCHING,
    ListStatus.PLANNING,
    ListStatus.PAUSED,
    ListStatus.REPEATING,
    ListStatus.COMPLETED,
    ListStatus.DROPPED,
)

/** Sort orders of Mi lista (the list structure's sortable columns; the other structures use the same one). */
enum class ListSort { UPDATED, TITLE, PROGRESS, SCORE, STATUS }

/** Sorted copy in the natural direction of [sort]; ties keep the repository order (newest change first). */
internal fun List<ListEntry>.sortedFor(sort: ListSort, locale: Locale = Locale.getDefault()): List<ListEntry> = when (sort) {
    ListSort.UPDATED -> sortedByDescending { it.updatedAtMs }
    ListSort.TITLE -> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        sortedWith { a, b -> collator.compare(a.title, b.title) }
    }
    ListSort.PROGRESS -> sortedWith(compareByDescending<ListEntry> { it.progressFraction() ?: -1f }.thenByDescending { it.progress })
    ListSort.SCORE -> sortedWith(compareByDescending<ListEntry> { it.score ?: -1 }.thenByDescending { it.updatedAtMs })
    ListSort.STATUS -> sortedWith(compareBy<ListEntry> { LibraryStatusOrder.indexOf(it.status) }.thenByDescending { it.updatedAtMs })
}

/** Case- and accent-insensitive title filter; blank = everything. */
internal fun List<ListEntry>.filteredBy(query: String): List<ListEntry> {
    val q = query.trim().normalizedForSearch()
    if (q.isEmpty()) return this
    return filter { it.title.normalizedForSearch().contains(q) || it.notes?.normalizedForSearch()?.contains(q) == true }
}

internal fun String.normalizedForSearch(): String =
    java.text.Normalizer.normalize(lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** progress / total, null when the total is unknown. */
internal fun ListEntry.progressFraction(): Float? = totalEpisodes?.takeIf { it > 0 }?.let { (progress.toFloat() / it).coerceIn(0f, 1f) }

/** Stable key of an entry (selection, drag and drop, lazy keys); matches the `TitleCardData.key` of the same title. */
internal val ListEntry.key: String get() = "anime:" + (ref.anilistId ?: ref.malId ?: ref.kitsuId ?: title)

/** Minimal [Media] for repository writes made from a [ListEntry] (Library cards, the editor sheet). */
internal fun ListEntry.toMedia(): Media = Media(
    ref = ref,
    kind = MediaKind.ANIME,
    title = title,
    titles = emptyMap(),
    cover = poster,
    banner = null,
    color = null,
    synopsis = null,
    genres = emptyList(),
    episodes = totalEpisodes,
    status = null,
    season = null,
    year = null,
    score = null,
    sourceProvider = "lists",
)

data class ListsUiState(
    /** Status chip of the filter bar; null = every status ("Todo"). */
    val filter: ListStatus? = null,
    val sort: ListSort = ListSort.UPDATED,
    /** The sort runs against its natural direction (a second click on the same column). */
    val sortReversed: Boolean = false,
    val query: String = "",
    /** Every entry that matches [query], sorted; each structure groups or filters it. */
    val all: LoadState<List<ListEntry>> = LoadState.Loading,
    /** Count per status of the entries that match [query] (chips, shelf and column heads). */
    val counts: Map<ListStatus, Int> = emptyMap(),
    /** The list has entries before the title filter (empty library vs. no match). */
    val hasAny: Boolean = false,
    val favorites: LoadState<List<ListEntry>> = LoadState.Loading,
    val favoriteCharacters: List<CharacterRef> = emptyList(),
    val accounts: List<LinkedAccount> = emptyList(),
    val pendingChanges: Int = 0,
) {
    /** [all] narrowed to the [filter] chip. */
    val visible: List<ListEntry>
        get() = (all as? LoadState.Ok)?.value?.let { list -> filter?.let { f -> list.filter { it.status == f } } ?: list }.orEmpty()
}

sealed interface ListsEvent {
    /** A write landed locally while offline / with pending pushes: "Se sincronizará". */
    data object Queued : ListsEvent
    data class Failed(val cause: AppError) : ListsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListsViewModel(private val lists: ListsRepository) : ViewModel() {

    private data class Controls(
        val filter: ListStatus? = null,
        val sort: ListSort = ListSort.UPDATED,
        val reversed: Boolean = false,
        val query: String = "",
    )

    private val controls = MutableStateFlow(Controls())
    private val reload = MutableStateFlow(0)
    private val _events = Channel<ListsEvent>(Channel.BUFFERED)
    val events: Flow<ListsEvent> = _events.receiveAsFlow()

    private val all: Flow<LoadState<List<ListEntry>>> = reload.flatMapLatest {
        lists.entries(null).asLoadState()
    }

    private val favorites: Flow<LoadState<List<ListEntry>>> = reload.flatMapLatest {
        lists.favorites().asLoadState()
    }

    private val characters: Flow<List<CharacterRef>> = lists.favoriteCharacters().catch { emit(emptyList()) }

    val state: StateFlow<ListsUiState> = combine(
        controls,
        all,
        favorites,
        characters,
        combine(lists.accounts, lists.pendingChanges) { a, p -> a to p },
    ) { c, everything, favs, chars, (accounts, pending) ->
        val matching = (everything as? LoadState.Ok)?.value?.filteredBy(c.query)
        val counts = matching?.groupingBy { it.status }?.eachCount().orEmpty()
        val shown: LoadState<List<ListEntry>> = when (everything) {
            is LoadState.Ok -> LoadState.Ok(matching.orEmpty().sortedFor(c.sort).let { if (c.reversed) it.reversed() else it })
            is LoadState.Err -> everything
            LoadState.Loading -> LoadState.Loading
        }
        ListsUiState(
            filter = c.filter,
            sort = c.sort,
            sortReversed = c.reversed,
            query = c.query,
            all = shown,
            counts = counts,
            hasAny = (everything as? LoadState.Ok)?.value?.isNotEmpty() == true,
            favorites = favs,
            favoriteCharacters = chars,
            accounts = accounts,
            pendingChanges = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState())

    fun setFilter(status: ListStatus?) = controls.value.let { controls.value = it.copy(filter = status) }

    /** Picks [sort] in its natural direction. */
    fun setSort(sort: ListSort) = controls.value.let { controls.value = it.copy(sort = sort, reversed = false) }

    /** A column head: the same column flips the direction, another one starts in its natural direction. */
    fun toggleSort(sort: ListSort) = controls.value.let {
        controls.value = if (it.sort == sort) it.copy(reversed = !it.reversed) else it.copy(sort = sort, reversed = false)
    }

    fun setQuery(query: String) = controls.value.let { controls.value = it.copy(query = query.take(MAX_QUERY)) }

    fun clearFilters() = controls.value.let { controls.value = it.copy(query = "", filter = null) }

    fun retry() {
        reload.value += 1
    }

    fun setStatus(entry: ListEntry, status: ListStatus?) = write { lists.setStatus(entry.toMedia(), status) }

    /**
     * Moves [entries] to [status] (drag to a column, "Mover a", the batch bar) and applies the artifact's rule: Completed fills the
     * progress when the total is known.
     */
    fun moveTo(entries: List<ListEntry>, status: ListStatus) = writeAll(
        entries.filter { it.status != status }.flatMap { e ->
            val progress = ListEntryRules.progressForStatus(status.slot(), e.progress, e.totalEpisodes)
            buildList<suspend () -> Result<Unit>> {
                add { lists.setStatus(e.toMedia(), status) }
                if (status == ListStatus.COMPLETED && progress != e.progress) add { lists.setProgress(e.ref, progress) }
            }
        },
    )

    /** "+1 episodio" of the batch bar; never past the total. */
    fun addEpisode(entries: List<ListEntry>) = writeAll(
        entries.filter { e -> e.totalEpisodes?.let { e.progress < it } ?: true }
            .map<ListEntry, suspend () -> Result<Unit>> { e -> { lists.setProgress(e.ref, e.progress + 1) } },
    )

    /** "Quitar de la lista" (favorites are kept). */
    fun remove(entries: List<ListEntry>) =
        writeAll(entries.map<ListEntry, suspend () -> Result<Unit>> { e -> { lists.setStatus(e.toMedia(), null) } })

    /** Undo of a move, a progress change or a removal: puts each entry back as it was (status, progress, score). */
    fun restore(entries: List<ListEntry>) = writeAll(
        entries.flatMap { e ->
            listOf<suspend () -> Result<Unit>>(
                { lists.setStatus(e.toMedia(), e.status) },
                { lists.setProgress(e.ref, e.progress) },
                { lists.setScore(e.ref, e.score) },
                // setProgress may move Planning to Watching: the status goes last so it wins.
                { lists.setStatus(e.toMedia(), e.status) },
            )
        },
    )

    fun setProgress(ref: MediaRef, episode: Int) = write { lists.setProgress(ref, episode.coerceAtLeast(0)) }

    fun setScore(ref: MediaRef, score: Int?) = write { lists.setScore(ref, score?.coerceIn(0, 100)) }

    fun setFavorite(entry: ListEntry, favorite: Boolean) = write { lists.setFavorite(entry.toMedia(), favorite) }

    fun removeCharacter(character: CharacterRef) = write { lists.setCharacterFavorite(character, false) }

    fun restoreCharacter(character: CharacterRef) = write { lists.setCharacterFavorite(character, true) }

    private fun write(block: suspend () -> Result<Unit>) = writeAll(listOf(block))

    /** Runs [blocks] in order in one coroutine; the first failure stops the rest and is reported. */
    private fun writeAll(blocks: List<suspend () -> Result<Unit>>) {
        if (blocks.isEmpty()) return
        viewModelScope.launch {
            var failure: Throwable? = null
            for (block in blocks) {
                val result = try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
                failure = result.exceptionOrNull()
                if (failure != null) break
            }
            val error = failure
            if (error != null) {
                _events.trySend(ListsEvent.Failed(error.toAppError()))
            } else if (lists.accounts.value.isNotEmpty() && lists.pendingChanges.value > 0) {
                _events.trySend(ListsEvent.Queued)
            }
        }
    }

    private fun Flow<List<ListEntry>>.asLoadState(): Flow<LoadState<List<ListEntry>>> =
        map<List<ListEntry>, LoadState<List<ListEntry>>> { LoadState.Ok(it) }
            .onStart { emit(LoadState.Loading) }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(LoadState.Err(e.toAppError()))
            }

    private companion object {
        const val MAX_QUERY = 100
    }
}
