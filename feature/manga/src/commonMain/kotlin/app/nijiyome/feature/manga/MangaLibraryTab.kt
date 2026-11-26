// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.look.slot
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nijiyome.manga.ChapterText
import app.nijiyome.manga.MangaCatalog
import app.nijiyome.manga.MangaRepositoryImpl
import app.nijiyome.model.ContinueReadingItem
import app.nijiyome.model.ListProvider
import app.nijiyome.model.ListStatus
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.MangaRepository
import app.nijiyome.model.ReadEntry
import app.nijiyome.model.ReadingProgress
import app.nijiyome.ui.labels.readLabelRes
import app.nijiyome.ui.media.MediaCover
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class MangaLibraryViewModel(
    repo: MangaRepository,
    private val catalog: MangaCatalog,
    lists: ListsRepository?,
) : ViewModel() {
    val entries: StateFlow<List<ReadEntry>?> = repo.readlist(null).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastRead: StateFlow<List<ReadingProgress>> = repo.lastRead(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Seguir leyendo": saved pages + readlist entries being read (ListProgress.mergeContinueReading). */
    val continueReading: StateFlow<List<ContinueReadingItem>> = combine(repo.lastRead(CONTINUE_LIMIT), repo.readlist(null)) { p, l ->
        mangaContinueReading(p, l, CONTINUE_LIMIT)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val anilistLinked: StateFlow<Boolean> = (lists?.accounts?.map { a -> a.any { it.provider == ListProvider.ANILIST } } ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Pulls the AniList manga list (and retries pending pushes) when an account is linked. */
    fun sync() {
        if (_syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            runCatching { catalog.pullReadlist() }
            _syncing.value = false
        }
    }
}

/**
 * Library › Manga (`LibraryTab.MANGA`; checklist manga.readlist): status chips (labels `readLabelRes()`), rows with "x / y capítulos",
 * "Seguir leyendo"; empty state → [onOpenAccounts] when nothing local and no AniList account. Local first; AniList progress when linked.
 */
@Composable
fun MangaLibraryTab(onOpenDetails: (anilistId: Int) -> Unit, onOpenAccounts: () -> Unit, modifier: Modifier = Modifier) {
    val vm: MangaLibraryViewModel = koinViewModel()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val lastRead by vm.lastRead.collectAsStateWithLifecycle()
    val continueReading by vm.continueReading.collectAsStateWithLifecycle()
    val linked by vm.anilistLinked.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    LaunchedEffect(linked) { if (linked) vm.sync() }
    ReadlistContent(
        entries = entries,
        continueItems = continueReading.mapNotNull { c ->
            val id = c.ref.anilistId ?: return@mapNotNull null
            val p = lastRead.firstOrNull { it.mediaId == c.mediaId }?.takeIf { c.resume }
            val line = if (p != null) {
                stringResource(Res.string.manga_continue_line, ChapterText.formatNumber(p.chapterNumber) ?: "?", p.page + 1, p.totalPages.coerceAtLeast(1))
            } else {
                stringResource(Res.string.manga_chapter_n, ChapterText.formatNumber(c.chapter) ?: "?")
            }
            ContinueItem(id, c.title, c.poster, line)
        },
        linked = linked,
        syncing = syncing,
        onSync = vm::sync,
        onOpenDetails = onOpenDetails,
        onOpenAccounts = onOpenAccounts,
        emptyTitle = stringResource(Res.string.manga_library_empty),
        emptyMessage = stringResource(if (linked) Res.string.manga_library_empty_linked else Res.string.manga_library_empty_message),
        modifier = modifier,
    )
}

private const val CONTINUE_LIMIT = 10

internal data class ContinueItem(val anilistId: Int, val title: String, val poster: String?, val line: String)

/**
 * The readlist (MNG-05): status chips with counts, "Seguir leyendo" as continue cards, one row per title with its list status mark
 * and a progress track. Shared shape with the novel tab.
 */
@Composable
internal fun ReadlistContent(
    entries: List<ReadEntry>?,
    continueItems: List<ContinueItem>,
    linked: Boolean,
    syncing: Boolean,
    onSync: () -> Unit,
    onOpenDetails: (Int) -> Unit,
    onOpenAccounts: () -> Unit,
    emptyTitle: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    var filter by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<ListStatus?>(ListStatus.WATCHING) }
    Column(modifier) {
        if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(Res.string.manga_library_all)) })
                READ_STATUSES.forEach { s ->
                    val count = entries?.count { it.status == s } ?: 0
                    FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text("${stringResource(s.readLabelRes())} · $count") })
                }
            }
            if (linked) IconButton(onClick = onSync, enabled = !syncing) { Icon(Icons.Outlined.Sync, contentDescription = stringResource(Res.string.manga_library_sync)) }
        }
        val list = entries
        when {
            list == null -> CardRowPlaceholder()
            list.isEmpty() && continueItems.isEmpty() -> ReadingEmpty(
                kind = EmptyKind.LIBRARY,
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = emptyTitle,
                message = emptyMessage,
                actionLabel = if (linked) null else stringResource(Res.string.manga_library_link),
                onAction = if (linked) null else onOpenAccounts,
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                val shown = if (filter == null) list else list.filter { it.status == filter }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (continueItems.isNotEmpty()) {
                        item(key = "continue") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeader(stringResource(Res.string.manga_continue_reading), Modifier.padding(horizontal = 16.dp))
                                LazyRow(Modifier.fillMaxWidth().decor(DecorRegion.TONIGHT), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(continueItems, key = { _, c -> c.anilistId }) { i, c ->
                                        val data = TitleCardData(key = "manga:${c.anilistId}", title = c.title, coverUrl = c.poster, subtitle = c.line, nextLabel = c.line)
                                        TitleCard(data, onClick = { onOpenDetails(c.anilistId) }, context = CardContext.CONTINUE, modifier = Modifier.cardSway(i).cardToDetailsSource(data.key))
                                    }
                                }
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item(key = "none") {
                            Text(stringResource(Res.string.manga_library_none_in_status), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(shown, key = { "${it.ref.anilistId}-${it.ref.malId}" }) { e -> ReadlistRow(e, onOpenDetails) }
                }
            }
        }
    }
}

@Composable
private fun ReadlistRow(e: ReadEntry, onOpenDetails: (Int) -> Unit) {
    val label = stringResource(e.status.readLabelRes())
    Row(
        Modifier.fillMaxWidth().clickable(enabled = e.ref.anilistId != null) { e.ref.anilistId?.let(onOpenDetails) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(48.dp)) { MediaCover(url = e.poster, modifier = Modifier.fillMaxWidth().cardToDetailsSource("manga:${e.ref.anilistId ?: e.title}")) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusMark(e.status.slot(), label = label, selected = true)
                Text(stringResource(Res.string.manga_progress_label, e.progress, e.totalChapters?.toString() ?: "?"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.totalChapters?.takeIf { it > 0 }?.let { total ->
                ProgressTrack(progress = (e.progress.toFloat() / total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth(), segments = total.takeIf { it <= 52 })
            }
        }
    }
}
