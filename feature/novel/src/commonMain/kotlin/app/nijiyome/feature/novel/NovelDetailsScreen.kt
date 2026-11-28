// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import app.nijiyome.feature.novel.resources.Res
import app.nijiyome.feature.novel.resources.*
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.labels.StaffCredit
import app.nijiyome.ui.labels.TranslationCredit
import app.nijiyome.ui.labels.languageName
import app.nijiyome.ui.labels.shortNameList
import app.nijiyome.ui.labels.staffCredits
import app.nijiyome.ui.labels.staffRoleLabelRes
import app.nijiyome.ui.labels.genreLabel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.EpisodeMark
import app.nijiyome.designsystem.direction.EpisodeMarkState
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.WatchedTransition
import app.nijiyome.designsystem.motion.cardToDetailsTarget
import app.nijiyome.manga.ChapterText
import app.nijiyome.model.ListProgress
import app.nijiyome.model.LoadState
import app.nijiyome.model.NovelChapter
import app.nijiyome.model.NovelReaderArgs
import app.nijiyome.model.NovelSourceSummary
import app.nijiyome.model.ReadEntry
import app.nijiyome.model.SourceProblem
import app.nijiyome.novel.NovelChapters
import app.nijiyome.novel.NovelRepositoryImpl
import app.nijiyome.ui.labels.readLabelRes
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.alternativeTitle
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.ListStatusPicker
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.ProgressStepper
import app.nijiyome.ui.patterns.SelectionAction
import app.nijiyome.ui.patterns.SelectionActionBar
import app.nijiyome.ui.patterns.SelectionCheckbox
import app.nijiyome.ui.patterns.SelectionState
import app.nijiyome.ui.patterns.SelectionTopBar
import app.nijiyome.ui.patterns.UndoController
import app.nijiyome.ui.patterns.UndoSnackbar
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.multiSelectable
import app.nijiyome.ui.patterns.rememberSelectionState
import app.nijiyome.ui.patterns.rememberUndoController
import app.nijiyome.ui.patterns.selectionKeys
import app.nijiyome.ui.patterns.toListStatus
import app.nijiyome.ui.state.screenInsets
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.designsystem.scroll.NjLazyColumn
import app.nijiyome.designsystem.scroll.njVerticalScroll

/**
 * `Route.NovelDetails(anilistId)` (NOV-04 to NOV-08), following the manga details page of the Sparkles design: header with cover,
 * "Seguir en el cap. N", the read counter and the six list statuses one tap away; sources by extension with "Revisar todas las
 * fuentes"; chapters with filter, order, translator and language, multi-selection (mark read / unread with undo, download); the
 * information aside (facts with authors and translators, synopsis, genres). Phones: tabs Capítulos / Información.
 */
@Composable
fun NovelDetailsScreen(
    anilistId: Int,
    onBack: () -> Unit,
    onOpenReader: (NovelReaderArgs) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenGenre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: NovelDetailsViewModel = koinViewModel(key = "novel-$anilistId", parameters = { parametersOf(anilistId) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entry by vm.entry.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val downloaded by vm.downloaded.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undo = rememberUndoController()
    val scope = rememberCoroutineScope()
    val queued = stringResource(Res.string.novel_download_queued_snack)
    val openDownloads = stringResource(Res.string.novel_open_downloads)
    val noChapter = stringResource(Res.string.novel_no_chapter)
    val failed = stringResource(UiR.string.ui_error_title)
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is NovelEvent.Open -> onOpenReader(ev.args)
                is NovelEvent.Queued -> scope.launch {
                    if (snackbar.showSnackbar(queued.format(ev.count), actionLabel = openDownloads) == SnackbarResult.ActionPerformed) onOpenDownloads()
                }
                is NovelEvent.Failed -> scope.launch { snackbar.showSnackbar(failed) }
                NovelEvent.NoChapter -> scope.launch { snackbar.showSnackbar(noChapter) }
            }
        }
    }
    val lang = settings.mangaTitleLanguage
    val selection = rememberSelectionState<String>()
    val compact = isCompactWindow()
    Box(modifier.fillMaxSize().windowInsetsPadding(screenInsets)) {
        when (val info = ui.info) {
            LoadState.Loading -> Column {
                // a way back while the title loads
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LoadingPlaceholder(Modifier.width(140.dp).height(210.dp))
                    LoadingPlaceholder(Modifier.fillMaxWidth().height(240.dp))
                }
            }
            is LoadState.Err -> Column {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
                NovelError(info.cause, vm::load)
            }
            is LoadState.Ok -> {
                val listProgress = entry?.progress ?: 0
                val state = NovelChapterState(
                    ui = ui,
                    readUpTo = listProgress,
                    nextChapter = vm.nextChapterNumber(progress, entry, (ui.chapters as? LoadState.Ok)?.value.orEmpty()),
                    downloaded = downloaded,
                    selection = selection,
                )
                val started = progress != null || listProgress > 0
                val header: @Composable () -> Unit = {
                    NovelHeader(info.value, entry, info.value.media.displayTitle(lang), started, state.nextChapter, compact, vm, undo, onBack)
                }
                val aside: @Composable ColumnScope.() -> Unit = { NovelInfo(info.value, onOpenGenre) }
                // The aside goes beside the chapters only when the pane itself has the room (opened from the Novelas catalogue on a
                // desktop window this page is a pane, not the window): else the phone layout, tabs Capítulos / Información.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (compact || maxWidth < SIDE_BY_SIDE_MIN) {
                        var tab by rememberSaveable { mutableIntStateOf(0) }
                        NjLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                            if (compact && selection.active) item(key = "selbar") { NovelSelectionTopBar(state) }
                            item(key = "header") { header() }
                            item(key = "tabs") {
                                TabRow(selectedTabIndex = tab) {
                                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(Res.string.novel_chapters)) })
                                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(Res.string.novel_info)) })
                                }
                            }
                            if (tab == 0) novelChapters(state, vm, onOpenReader, onOpenExtensions) else item(key = "info") { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { aside() } }
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            NjLazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 120.dp)) {
                                item(key = "header") { header() }
                                novelChapters(state, vm, onOpenReader, onOpenExtensions)
                            }
                            Column(
                                Modifier.width(380.dp).fillMaxHeight().njVerticalScroll(rememberScrollState(), scrollToTopOnReselect = false).padding(top = 16.dp, end = 16.dp, bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                content = aside,
                            )
                        }
                    }
                }
                NovelSelectionBar(state, vm, undo, Modifier.align(Alignment.BottomCenter))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)) {
            UndoSnackbar(undo, Modifier.padding(horizontal = 16.dp))
            SnackbarHost(snackbar)
        }
    }
}

internal class NovelChapterState(
    val ui: NovelDetailsUi,
    val readUpTo: Int,
    val nextChapter: Double?,
    val downloaded: Set<String>,
    val selection: SelectionState<String>,
) {
    val shownIds: List<String> get() = ui.shown.map(NovelRepositoryImpl::downloadId)

    fun isRead(ch: NovelChapter): Boolean = ListProgress.isChapterRead(ch.number, readUpTo)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelHeader(
    info: app.nijiyome.manga.MangaInfo,
    entry: ReadEntry?,
    title: String,
    started: Boolean,
    nextChapter: Double?,
    compact: Boolean,
    vm: NovelDetailsViewModel,
    undo: UndoController,
    onBack: () -> Unit,
) {
    val media = info.media
    val total = entry?.totalChapters ?: info.chapters
    val statusSaved = stringResource(Res.string.rd_s11_status_saved)
    val removed = stringResource(Res.string.rd_s11_removed_from_list)
    val remove: () -> Unit = {
        val previous = entry
        vm.setStatus(null)
        undo.show(removed, undo = { vm.restoreEntry(previous) })
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // the header's inner width (minus its 16 dp padding) decides whether "Quitar de la lista" fits beside the six named statuses
        val besideRemove = !compact && maxWidth - 32.dp >= STATUS_ROW_MIN
        Column(Modifier.fillMaxWidth().decor(DecorRegion.HERO).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, shape = NijiyomeControls.shape) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.novel_title))
            }
            val actions: @Composable () -> Unit = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::readNext, shape = NijiyomeControls.shape) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val n = ChapterText.formatNumber(nextChapter)
                        Text(
                            when {
                                n == null -> stringResource(if (started) Res.string.novel_continue else Res.string.rd_s11_start_reading)
                                started -> stringResource(Res.string.rd_s11_resume_chapter, n)
                                else -> stringResource(Res.string.novel_read_chapter, n)
                            },
                        )
                    }
                    if (entry != null) {
                        ProgressStepper(value = entry.progress, onValueChange = vm::setProgress, max = total ?: Int.MAX_VALUE, total = total, label = stringResource(Res.string.rd_s11_read_count))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp)) {
                MediaCover(
                    url = media.cover,
                    contentDescription = stringResource(Res.string.rd_s11_cover_of, title),
                    modifier = Modifier.width(if (compact) 112.dp else 180.dp).cardToDetailsTarget(novelCardKey(media.ref, media.title)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(stringResource(Res.string.novel_format_novel), tone = PillTone.PRIMARY)
                        novelStatusLabel(info.status)?.let { Pill(it) }
                        novelOriginLabel(info.origin)?.let { Pill(it) }
                        media.year?.let { Pill(it.toString()) }
                    }
                    Text(title, style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    media.alternativeTitle(title)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    if (!compact) actions()
                }
            }
            if (compact) actions()
            if (entry != null && total != null && total > 0) {
                ProgressTrack(progress = (entry.progress.toFloat() / total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp), segments = total.takeIf { it <= 52 })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ListStatusPicker(
                    selected = entry?.status?.slot(),
                    onSelect = { slot ->
                        val previous = entry
                        vm.setStatus(slot.toListStatus())
                        undo.show(statusSaved, undo = { vm.restoreEntry(previous) })
                    },
                    modifier = Modifier.weight(1f),
                    labelOf = { stringResource(it.toListStatus().readLabelRes()) },
                    compact = compact,
                )
                if (entry != null && besideRemove) TextButton(onClick = remove, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.list_remove)) }
            }
            if (entry != null && !besideRemove) TextButton(onClick = remove, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.list_remove)) }
        }
    }
}

/** Pane width from which the chapters (at least ~600 dp) and the 380 dp information aside sit side by side. */
private val SIDE_BY_SIDE_MIN = 1000.dp

/** From this width the six named statuses and "Quitar de la lista" share one row. */
private val STATUS_ROW_MIN = 860.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.NovelInfo(info: app.nijiyome.manga.MangaInfo, onOpenGenre: (String) -> Unit) {
    val media = info.media
    InfoPanel(stringResource(Res.string.novel_info)) {
        @Composable
        fun Fact(label: String, value: String?) {
            if (value.isNullOrBlank()) return
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        // BUG-114 / BUG-133: authors with their roles in Spanish (no AniList notes); translators apart, by language.
        val credits = remember(info.authors) { staffCredits(info.authors) }
        Fact(stringResource(Res.string.novel_fact_authors), authorsLine(credits.authors))
        credits.translations.forEach { group -> TranslationFact(group) }
        Fact(stringResource(Res.string.novel_fact_volumes), info.volumes?.toString())
        Fact(stringResource(Res.string.novel_fact_chapters), info.chapters?.toString())
        Fact(stringResource(Res.string.novel_fact_status), novelStatusLabel(info.status))
        Fact(stringResource(Res.string.novel_fact_start), info.startDate)
        Fact(stringResource(Res.string.novel_fact_end), info.endDate)
        Fact(stringResource(Res.string.novel_fact_score), media.score?.let { "$it %" })
        Fact(stringResource(Res.string.novel_fact_origin), novelOriginLabel(info.origin))
    }
    media.synopsis?.let { s ->
        InfoPanel(stringResource(Res.string.rd_s11_synopsis)) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            Text(s, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { expanded = !expanded }, shape = NijiyomeControls.shape) { Text(stringResource(if (expanded) Res.string.novel_less else Res.string.novel_more)) }
        }
    }
    if (media.genres.isNotEmpty()) {
        InfoPanel(stringResource(Res.string.novel_row_genres)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                media.genres.forEach { g -> SuggestionChip(onClick = { onOpenGenre(g) }, label = { Text(genreLabel(g)) }) }
            }
        }
    }
}

@Composable
private fun InfoPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------- chapters

private fun LazyListScope.novelChapters(
    state: NovelChapterState,
    vm: NovelDetailsViewModel,
    onOpenReader: (NovelReaderArgs) -> Unit,
    onOpenExtensions: () -> Unit,
) {
    val ui = state.ui
    item(key = "chapters-header") {
        val count = (ui.chapters as? LoadState.Ok)?.value?.size
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)) {
            SectionHeader(
                if (count != null) stringResource(Res.string.rd_s11_chapters_count, count) else stringResource(Res.string.novel_chapters),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!state.selection.active) {
                            TextButton(onClick = { state.selection.start() }, shape = NijiyomeControls.shape) {
                                Icon(Icons.Outlined.Checklist, contentDescription = null)
                                Text(stringResource(Res.string.rd_s11_select), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        val refresh = stringResource(Res.string.rd_s11_refresh_list)
                        val checkAll = stringResource(Res.string.rd_s11_check_all_sources)
                        ContextMenuButton(
                            entries = {
                                listOf(
                                    MenuAction(refresh, vm::refreshChapters, icon = Icons.Outlined.Refresh),
                                    MenuAction(checkAll, vm::checkAll, icon = Icons.Outlined.FactCheck, enabled = !ui.checking),
                                )
                            },
                            contentDescription = stringResource(Res.string.rd_s11_chapter_actions),
                        )
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.rd_s11_source), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::checkAll, enabled = !ui.checking, shape = NijiyomeControls.shape) {
                    if (ui.checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.FactCheck, contentDescription = null)
                    Text(stringResource(Res.string.rd_s11_check_all_sources), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
    when (val sources = ui.sources) {
        LoadState.Loading -> item(key = "src-loading") { LoadingPlaceholder(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 72.dp)) }
        is LoadState.Err -> item(key = "src-err") { NovelError(sources.cause, vm::loadSources) }
        is LoadState.Ok -> if (sources.value.isEmpty()) {
            item(key = "no-src") {
                ReadingEmpty(
                    kind = EmptyKind.SOURCES,
                    icon = Icons.Outlined.Extension,
                    title = stringResource(Res.string.novel_no_sources),
                    message = stringResource(Res.string.novel_no_sources_message),
                    actionLabel = stringResource(Res.string.novel_open_extensions),
                    onAction = onOpenExtensions,
                )
            }
        } else {
            item(key = "sources") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.value.forEach { s -> SourceCard(s, selected = ui.source?.extensionId == s.extensionId && ui.source?.sourceId == s.sourceId) { vm.selectSource(s) } }
                }
            }
            item(key = "tools") { NovelChapterTools(ui, vm) }
            when (val chapters = ui.chapters) {
                LoadState.Loading -> item(key = "ch-loading") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(6) { LoadingPlaceholder(Modifier.fillMaxWidth().heightIn(min = 56.dp)) } }
                }
                is LoadState.Err -> item(key = "ch-err") { NovelError(chapters.cause, vm::refreshChapters) }
                is LoadState.Ok -> {
                    val shown = ui.shown
                    if (shown.isEmpty()) {
                        item(key = "ch-empty") { ReadingEmpty(EmptyKind.SEARCH, Icons.Outlined.FilterList, stringResource(Res.string.novel_chapters_empty), null) }
                    }
                    items(shown, key = { "c-" + NovelRepositoryImpl.downloadId(it) }) { ch -> NovelChapterRow(ch, state, vm, onOpenReader) }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(s: NovelSourceSummary, selected: Boolean, onClick: () -> Unit) {
    val enabled = s.itemId != null
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.widthIn(min = 150.dp, max = 240.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.extensionName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    s.itemId == null -> when (s.problem) {
                        SourceProblem.TIMEOUT -> stringResource(Res.string.novel_source_problem_timeout)
                        SourceProblem.FAILED -> stringResource(Res.string.novel_source_problem_failed)
                        else -> stringResource(Res.string.novel_source_unmatched)
                    }
                    else -> listOfNotNull(
                        s.chapterCount?.let { stringResource(Res.string.rd_s11_chapters_short, it) } ?: stringResource(Res.string.novel_source_unchecked),
                        s.languages.joinToString(" · ").uppercase().ifBlank { null },
                        s.translator,
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun NovelChapterTools(ui: NovelDetailsUi, vm: NovelDetailsViewModel) {
    val all = (ui.chapters as? LoadState.Ok)?.value.orEmpty()
    val translators = NovelChapters.translators(all)
    val langs = NovelChapters.languages(all)
    val any = stringResource(Res.string.novel_filter_any)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.novel_chapter_filter_hint)) },
                modifier = Modifier.weight(1f).widthIn(max = 520.dp),
            )
            Segmented(
                options = listOf(SegmentedOption(false, stringResource(Res.string.rd_s11_order_newest)), SegmentedOption(true, stringResource(Res.string.rd_s11_order_oldest))),
                selected = ui.ascending,
                onSelect = { if (it != ui.ascending) vm.toggleOrder() },
                small = true,
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (langs.size > 1) {
                FilterChip(selected = ui.lang == null, onClick = { vm.setLang(null) }, label = { Text(stringResource(Res.string.rd_s11_all_languages)) })
                langs.forEach { l ->
                    FilterChip(selected = ui.lang == l, onClick = { vm.setLang(l) }, label = { Text(Locale.forLanguageTag(l).getDisplayLanguage(Locale.getDefault()).replaceFirstChar(Char::uppercase).ifBlank { l }) })
                }
            }
            if (translators.isNotEmpty()) {
                FilterChipMenu(
                    label = stringResource(Res.string.novel_translator),
                    value = ui.translator,
                    options = listOf<String?>(null) + translators,
                    selected = ui.translator,
                    optionLabel = { it ?: any },
                    onSelect = vm::setTranslator,
                )
            }
            Text(stringResource(Res.string.novel_chapter_count, ui.shown.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NovelChapterRow(ch: NovelChapter, state: NovelChapterState, vm: NovelDetailsViewModel, onOpenReader: (NovelReaderArgs) -> Unit) {
    val id = NovelRepositoryImpl.downloadId(ch)
    val isDownloaded = ch.downloaded || id in state.downloaded
    val read = state.isRead(ch)
    val next = state.nextChapter != null && ch.number == state.nextChapter
    val selection = state.selection
    // the last month as "hace 3 d" (the app's words, as the artifact shows), older ones as a date
    val date = ch.releasedAtMs?.let { ms ->
        if (System.currentTimeMillis() - ms in 0L until RECENT_CHAPTER_MS) {
            app.nijiyome.ui.format.relativeTime(ms)
        } else {
            remember(ms) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms)) }
        }
    }
    val number = ChapterText.formatNumber(ch.number)
    Row(
        Modifier
            .fillMaxWidth()
            .decor(DecorRegion.EPISODES)
            .selectionKeys(selection) { state.shownIds }
            .multiSelectable(selection, id, order = { state.shownIds }, onOpen = { onOpenReader(vm.argsFor(ch, offline = isDownloaded)) })
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionCheckbox(selected = selection.isSelected(id), visible = selection.active)
        WatchedTransition(read) { r ->
            EpisodeMark(if (r) EpisodeMarkState.WATCHED else if (next) EpisodeMarkState.IN_PROGRESS else EpisodeMarkState.AIRED, number = ListProgress.chapterIndex(ch.number))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    // never the source's internal id: a chapter without number nor title says so
                    (number?.let { stringResource(Res.string.rd_s11_chapter_short, it) }
                        ?: ch.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.rd_s11_chapter_unnumbered)) +
                        (if (number != null) ch.title?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "" else ""),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (next) Pill(stringResource(Res.string.rd_s11_next), tone = PillTone.PRIMARY)
            }
            Text(listOfNotNull(ch.translator, ch.lang?.uppercase(), date).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (isDownloaded) Icon(Icons.Outlined.DownloadDone, contentDescription = stringResource(Res.string.novel_downloaded), modifier = Modifier.size(20.dp))
        if (!selection.active && number != null) {
            IconButton(onClick = { vm.toggleRead(ch) }) {
                Icon(
                    if (read) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = stringResource(if (read) Res.string.novel_mark_unread else Res.string.novel_mark_read, number),
                    tint = if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NovelSelectionBar(state: NovelChapterState, vm: NovelDetailsViewModel, undo: UndoController, modifier: Modifier) {
    val selection = state.selection
    val count = selection.count
    val markedRead = stringResource(Res.string.rd_s11_marked_read)
    val markedUnread = stringResource(Res.string.rd_s11_marked_unread)
    val actions = if (count == 0) {
        emptyList()
    } else {
        listOf(
            SelectionAction(stringResource(Res.string.rd_s11_mark_read), Icons.Outlined.DoneAll, onClick = {
                vm.markChapters(selection.selected, read = true)?.let { undo.show(markedRead, undo = it) }
                selection.exit()
            }),
            SelectionAction(stringResource(Res.string.rd_s11_mark_unread), Icons.Outlined.RemoveDone, onClick = {
                vm.markChapters(selection.selected, read = false)?.let { undo.show(markedUnread, undo = it) }
                selection.exit()
            }),
            SelectionAction(stringResource(Res.string.rd_s11_download_n, count), Icons.Outlined.Download, onClick = {
                vm.downloadChapters(selection.selected)
                selection.exit()
            }, emphasis = ActionEmphasis.FILLED),
        )
    }
    LaunchedEffect(state.shownIds) { selection.retainOnly(state.shownIds) }
    SelectionActionBar(
        visible = selection.active,
        count = count,
        actions = actions,
        onExit = selection::exit,
        modifier = modifier,
        onSelectAll = { selection.selectAll(state.shownIds) },
        selectAllLabel = stringResource(Res.string.rd_s11_select_visible),
        emptyHint = stringResource(if (isCompactWindow()) Res.string.rd_s11_select_hint_touch else Res.string.rd_s11_select_hint_wide),
    )
}

@Composable
private fun NovelSelectionTopBar(state: NovelChapterState) {
    SelectionTopBar(
        count = state.selection.count,
        onExit = state.selection::exit,
        onSelectSpan = { state.selection.fillSpan(state.shownIds) },
        onSelectAll = { state.selection.selectAll(state.shownIds) },
        onSelectUnwatched = {
            val unread = state.ui.shown.filterNot(state::isRead).map(NovelRepositoryImpl::downloadId).toSet()
            state.selection.selectWhere(state.shownIds) { it in unread }
        },
    )
}

/** BUG-133: "Rifujin na Magonote (Guion), Sirotaka (Ilustración)": each author once, their base roles in Spanish. */
@Composable
private fun authorsLine(authors: List<StaffCredit>): String = authors.map { a ->
    val roles = a.roles.map { r -> staffRoleLabelRes(r)?.let { stringResource(it) } ?: r }
    if (roles.isEmpty()) a.name else "${a.name} (${roles.joinToString(", ")})"
}.joinToString(", ")

/**
 * BUG-133: "Traducción al inglés: Kevin Frane, Paul Cuneo, Alyssa Orton-Niioka y 3 más" (or "Traducción: …" when AniList does not
 * say the language); with more than three names a tap shows them all.
 */
@Composable
private fun TranslationFact(group: TranslationCredit) {
    val locale = currentUiLocale()
    val label = group.languageCode?.let { stringResource(Res.string.novel_translation_language, languageName(it, locale)) }
        ?: stringResource(Res.string.novel_translation)
    val (shown, more) = shortNameList(group.names)
    var expanded by rememberSaveable(group) { mutableStateOf(false) }
    val names = when {
        expanded || more == 0 -> group.names.joinToString(", ")
        else -> stringResource(Res.string.novel_names_more, shown, more)
    }
    val text = androidx.compose.ui.text.buildAnnotatedString {
        withStyle(MaterialTheme.typography.labelLarge.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$label: ") }
        append(names)
    }
    val toggle = if (more > 0) {
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(if (expanded) Res.string.novel_show_fewer_names else Res.string.novel_show_all_names)) { expanded = !expanded }
    } else {
        Modifier
    }
    Box(Modifier.fillMaxWidth().then(toggle).padding(vertical = 3.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Chapters released within this long show a relative time ("hace 3 d"). */
private const val RECENT_CHAPTER_MS = 30L * 24 * 60 * 60 * 1000
