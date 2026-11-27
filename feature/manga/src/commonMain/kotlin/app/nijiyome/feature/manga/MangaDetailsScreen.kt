// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.designsystem.motion.cardToDetailsTarget
import app.nijiyome.manga.ChapterText
import app.nijiyome.manga.MangaInfo
import app.nijiyome.manga.MangaRanking
import app.nijiyome.manga.MangaRepositoryImpl
import app.nijiyome.model.ListStatus
import app.nijiyome.model.LoadState
import app.nijiyome.model.MangaReaderArgs
import app.nijiyome.model.MatureContentMode
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.ReadEntry
import app.nijiyome.model.SourceProblem
import app.nijiyome.model.TitleLanguage
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.format.formatCount
import app.nijiyome.ui.format.formatDecimal1
import app.nijiyome.ui.labels.characterRoleLabel
import app.nijiyome.ui.labels.genreLabel
import app.nijiyome.ui.labels.mediaSourceLabel
import app.nijiyome.ui.labels.readLabelRes
import app.nijiyome.ui.labels.staffRoleLabel
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.alternativeTitle
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.ListStatusPicker
import app.nijiyome.ui.patterns.ProgressStepper
import app.nijiyome.ui.patterns.UndoSnackbar
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.rememberSelectionState
import app.nijiyome.ui.patterns.rememberUndoController
import app.nijiyome.ui.patterns.toListStatus
import app.nijiyome.ui.state.screenInsets
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.upscale.UpscaleImageKind

/**
 * `Route.Details(MediaKind.MANGA, anilistId)` (MNG-06 to MNG-11) in the Sparkles design (artifact screen `manga`, the `ficha` view):
 * header with cover, facts, "Seguir en el cap. N", the read counter and the six list statuses one tap away; chapters by source with
 * filter, order, language, scanlation group, volumes and multi-selection (mark read / unread with undo, download); the information
 * aside (facts, scores, synopsis, genres and tags, anime adaptation, series, characters, recommendations). Phones: tabs Capítulos /
 * Información under the header.
 */
@Composable
fun MangaDetailsScreen(
    anilistId: Int,
    onBack: () -> Unit,
    onOpenReader: (MangaReaderArgs) -> Unit,
    onOpenAnime: (anilistId: Int) -> Unit,
    onOpenManga: (Media) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenGenre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: MangaDetailsViewModel = koinViewModel(key = "manga-$anilistId", parameters = { parametersOf(anilistId) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val entry by vm.entry.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val downloaded by vm.downloaded.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undo = rememberUndoController()
    val scope = rememberCoroutineScope()
    var sourceSheet by rememberSaveable { mutableStateOf(false) }

    val queuedText = stringResource(Res.string.manga_download_queued_snack)
    val openDownloadsLabel = stringResource(Res.string.manga_open_downloads)
    val noChapterText = stringResource(Res.string.manga_no_chapter)
    val noSourceText = stringResource(Res.string.manga_no_source_found)
    val sourcesLabel = stringResource(Res.string.manga_no_source_found_action)
    val errorPrefix = stringResource(UiR.string.ui_error_title)
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is DetailsEvent.Open -> onOpenReader(ev.args)
                is DetailsEvent.Queued -> scope.launch {
                    val r = snackbar.showSnackbar(queuedText.format(ev.count), actionLabel = openDownloadsLabel)
                    if (r == SnackbarResult.ActionPerformed) onOpenDownloads()
                }
                is DetailsEvent.Failed -> scope.launch { snackbar.showSnackbar(errorPrefix) }
                DetailsEvent.NoChapter -> scope.launch { snackbar.showSnackbar(noChapterText) }
                DetailsEvent.NoSourceFound -> scope.launch {
                    val r = snackbar.showSnackbar(noSourceText, actionLabel = sourcesLabel)
                    if (r == SnackbarResult.ActionPerformed) sourceSheet = true
                }
            }
        }
    }

    val selection = rememberSelectionState<String>()
    Box(modifier.fillMaxSize().windowInsetsPadding(screenInsets)) {
        when (val info = ui.info) {
            LoadState.Loading -> Column {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
                DetailsLoading()
            }
            is LoadState.Err -> Column {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
                ErrorBlock(info.cause, vm::load)
            }
            is LoadState.Ok -> {
                val needsWarning = info.value.isAdult && settings.matureContent == MatureContentMode.WARN && anilistId !in prefs.matureAccepted
                if (needsWarning) {
                    MatureWarningDialog(onConfirm = vm::acceptMature, onDismiss = onBack)
                } else {
                    val readUpTo = entry?.progress ?: 0
                    val chapterState = ChapterSectionState(
                        ui = ui,
                        readUpTo = readUpTo,
                        nextChapter = vm.nextChapterNumber(progress, entry, (ui.chapters as? LoadState.Ok)?.value.orEmpty()),
                        downloadedIds = downloaded.map { MangaRepositoryImpl.downloadId(it) }.toSet(),
                        preferredKey = prefs.preferredSourceByMedia[MangaRepositoryImpl.mediaKey(app.nijiyome.model.MediaRef(anilistId = anilistId))],
                        selection = selection,
                    )
                    MangaFicha(
                        info = info.value,
                        ui = ui,
                        entry = entry,
                        hasProgress = progress != null || readUpTo > 0,
                        chapters = chapterState,
                        blurCover = info.value.isAdult && settings.matureContent == MatureContentMode.BLUR,
                        titleLanguage = settings.mangaTitleLanguage,
                        vm = vm,
                        undo = undo,
                        onBack = onBack,
                        onOpenReader = onOpenReader,
                        onOpenAnime = onOpenAnime,
                        onOpenManga = onOpenManga,
                        onOpenExtensions = onOpenExtensions,
                        onOpenGenre = onOpenGenre,
                        onOpenSourceSheet = { sourceSheet = true },
                    )
                    ChapterSelectionBar(chapterState, vm, undo, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)) {
            UndoSnackbar(undo, Modifier.padding(horizontal = 16.dp))
            SnackbarHost(snackbar)
        }
    }

    if (sourceSheet) {
        SourceSheet(
            sources = (ui.sources as? LoadState.Ok)?.value.orEmpty(),
            current = ui.source,
            preferredKey = prefs.preferredSourceByMedia[MangaRepositoryImpl.mediaKey(app.nijiyome.model.MediaRef(anilistId = anilistId))],
            languages = prefs.languages,
            onSelect = { vm.selectSource(it) },
            onPrefer = vm::setPreferredSource,
            onLanguages = vm::setLanguages,
            onOpenExtensions = onOpenExtensions,
            onDismiss = { sourceSheet = false },
        )
    }
}

@Composable
private fun DetailsLoading() {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LoadingPlaceholder(Modifier.width(140.dp).height(210.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingPlaceholder(Modifier.fillMaxWidth().height(32.dp))
                LoadingPlaceholder(Modifier.fillMaxWidth(0.6f).height(20.dp))
            }
        }
        LoadingPlaceholder(Modifier.fillMaxWidth().height(240.dp))
    }
}

internal val READ_STATUSES = listOf(ListStatus.WATCHING, ListStatus.PLANNING, ListStatus.COMPLETED, ListStatus.PAUSED, ListStatus.DROPPED, ListStatus.REPEATING)

/** The page: header, then chapters beside the information aside (wide) or under tabs (phones). */
@Composable
private fun MangaFicha(
    info: MangaInfo,
    ui: MangaDetailsUi,
    entry: ReadEntry?,
    hasProgress: Boolean,
    chapters: ChapterSectionState,
    blurCover: Boolean,
    titleLanguage: TitleLanguage,
    vm: MangaDetailsViewModel,
    undo: app.nijiyome.ui.patterns.UndoController,
    onBack: () -> Unit,
    onOpenReader: (MangaReaderArgs) -> Unit,
    onOpenAnime: (Int) -> Unit,
    onOpenManga: (Media) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenSourceSheet: () -> Unit,
) {
    val compact = isCompactWindow()
    val media = info.media
    val title = media.displayTitle(titleLanguage)
    val header: @Composable () -> Unit = {
        FichaHeader(info, ui, entry, title, hasProgress, chapters.nextChapter, blurCover, compact, vm, undo, onBack)
    }
    val aside: @Composable ColumnScope.() -> Unit = {
        InfoPanels(info, ui, titleLanguage, onOpenAnime, onOpenManga, onOpenGenre)
    }
    // The aside goes beside the chapters only when the pane itself has the room (a desktop window opened from the Manga catalogue
    // gives this page a pane, not the window): else the phone layout, tabs Capítulos / Información under the header.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideBySide = !compact && maxWidth >= SIDE_BY_SIDE_MIN
        if (!sideBySide) {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                if (compact && chapters.selection.active) {
                    item(key = "selbar") { ChapterSelectionTopBar(chapters) }
                }
                item(key = "header") { header() }
                item(key = "tabs") {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(Res.string.manga_chapters)) })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(Res.string.manga_info)) })
                    }
                }
                if (tab == 0) {
                    chaptersSection(chapters, vm, onOpenReader, onOpenExtensions, onOpenSourceSheet)
                } else {
                    item(key = "info") { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { aside() } }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 120.dp)) {
                    item(key = "header") { header() }
                    chaptersSection(chapters, vm, onOpenReader, onOpenExtensions, onOpenSourceSheet)
                }
                Column(
                    Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = aside,
                )
            }
        }
    }
}

/** Pane width from which the chapters (at least ~600 dp) and the 380 dp information aside sit side by side. */
private val SIDE_BY_SIDE_MIN = 1000.dp

// ------------------------------------------------------------------------------------------------------------------- header

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FichaHeader(
    info: MangaInfo,
    ui: MangaDetailsUi,
    entry: ReadEntry?,
    title: String,
    hasProgress: Boolean,
    nextChapter: Double?,
    blurCover: Boolean,
    compact: Boolean,
    vm: MangaDetailsViewModel,
    undo: app.nijiyome.ui.patterns.UndoController,
    onBack: () -> Unit,
) {
    val media = info.media
    val key = mangaCardKey(media.ref, media.title)
    // MatureContentMode.BLUR: the cover stays blurred until tapped once.
    var revealed by remember(media.ref) { mutableStateOf(false) }
    val hide = blurCover && !revealed
    val total = info.chapters ?: ui.details?.chapters
    val statusSaved = stringResource(Res.string.rd_s11_status_saved)
    val removed = stringResource(Res.string.rd_s11_removed_from_list)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // the header's inner width (minus its 16 dp padding) decides where "Quitar de la lista" goes
        val besideRemove = !compact && maxWidth - 32.dp >= STATUS_ROW_MIN
        Column(Modifier.fillMaxWidth().decor(DecorRegion.HERO).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, shape = NijiyomeControls.shape) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.manga_title))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(if (compact) 112.dp else 180.dp).clickable(enabled = hide) { revealed = true }, contentAlignment = Alignment.Center) {
                    MediaCover(
                        url = media.cover,
                        contentDescription = stringResource(Res.string.rd_s11_cover_of, title),
                        modifier = Modifier.fillMaxWidth().cardToDetailsTarget(key).then(if (hide) Modifier.blur(24.dp) else Modifier),
                    )
                    if (hide) Text(stringResource(Res.string.manga_mature_tap), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        formatLabel(info.format)?.let { Pill(it, tone = PillTone.PRIMARY) }
                        anilistStatusLabel(info.status)?.let { Pill(it) }
                        originLabel(info.origin)?.let { Pill(it) }
                        media.year?.let { Pill(it.toString()) }
                        if (info.isAdult) Pill("18+", tone = PillTone.WARN)
                    }
                    Text(title, style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    media.alternativeTitle(title)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    if (!compact) ReadActions(ui, entry, total, hasProgress, nextChapter, vm)
                }
            }
            if (compact) ReadActions(ui, entry, total, hasProgress, nextChapter, vm)
            if (entry != null && total != null && total > 0) {
                ProgressTrack(progress = (entry.progress.toFloat() / total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp), segments = total.takeIf { it <= MAX_TRACK_SEGMENTS })
            }
            // MNG-07: the six read statuses one tap away, each change undoable. They keep their names: "Quitar de la lista" sits beside
            // them only where the row has the room for both, else under them.
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
                if (entry != null && besideRemove) {
                    TextButton(onClick = {
                        val previous = entry
                        vm.setStatus(null)
                        undo.show(removed, undo = { vm.restoreEntry(previous) })
                    }, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.list_remove)) }
                }
            }
            if (entry != null && !besideRemove) {
                TextButton(onClick = {
                    val previous = entry
                    vm.setStatus(null)
                    undo.show(removed, undo = { vm.restoreEntry(previous) })
                }, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.list_remove)) }
            }
        }
    }
}

/** From this width the six named statuses and "Quitar de la lista" share one row. */
private val STATUS_ROW_MIN = 860.dp

/** "Seguir en el cap. N" / "Empezar a leer" and the "Leídos" counter (− / +). */
@Composable
private fun ReadActions(ui: MangaDetailsUi, entry: ReadEntry?, total: Int?, hasProgress: Boolean, nextChapter: Double?, vm: MangaDetailsViewModel) {
    FlowRowCompat {
        Button(onClick = vm::readNext, enabled = !ui.opening, shape = NijiyomeControls.shape) {
            if (ui.opening) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            val n = ChapterText.formatNumber(nextChapter)
            Text(
                when {
                    n == null -> stringResource(if (hasProgress) Res.string.manga_continue else Res.string.rd_s11_start_reading)
                    hasProgress -> stringResource(Res.string.rd_s11_resume_chapter, n)
                    else -> stringResource(Res.string.manga_read_chapter, n)
                },
            )
        }
        if (entry != null) {
            ProgressStepper(
                value = entry.progress,
                onValueChange = vm::setProgress,
                max = total ?: Int.MAX_VALUE,
                total = total,
                label = stringResource(Res.string.rd_s11_read_count),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ------------------------------------------------------------------------------------------------------------------- aside

/** The information panels (artifact `aside`): facts and scores, synopsis, genres and tags, adaptation, series, characters, recs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.InfoPanels(
    info: MangaInfo,
    ui: MangaDetailsUi,
    titleLanguage: TitleLanguage,
    onOpenAnime: (Int) -> Unit,
    onOpenManga: (Media) -> Unit,
    onOpenGenre: (String) -> Unit,
) {
    val media = info.media
    Panel(stringResource(Res.string.manga_info)) { Facts(info, ui) }
    media.synopsis?.let { synopsis ->
        Panel(stringResource(Res.string.rd_s11_synopsis)) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            Text(synopsis, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { expanded = !expanded }, shape = NijiyomeControls.shape) { Text(stringResource(if (expanded) Res.string.manga_less else Res.string.manga_more)) }
        }
    }
    if (media.genres.isNotEmpty() || info.tags.isNotEmpty()) {
        Panel(stringResource(Res.string.manga_row_genres)) {
            var spoilers by rememberSaveable { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                media.genres.forEach { g -> SuggestionChip(onClick = { onOpenGenre(g) }, label = { Text(genreLabel(g)) }) }
            }
            val tags = info.tags.filter { spoilers || !it.isSpoiler }
            if (tags.isNotEmpty()) {
                Text(stringResource(Res.string.rd_s11_tags), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Text(tags.joinToString(" · ") { t -> t.rank?.let { r -> "${t.name} $r %" } ?: t.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (info.tags.any { it.isSpoiler }) {
                TextButton(onClick = { spoilers = !spoilers }, shape = NijiyomeControls.shape) { Text(stringResource(if (spoilers) Res.string.manga_hide_spoilers else Res.string.manga_show_spoilers)) }
            }
        }
    }
    // Anime adaptation (MNG-06).
    val adaptation = info.relations.firstOrNull { it.media.kind == MediaKind.ANIME && it.relation == "ADAPTATION" }
        ?: info.relations.firstOrNull { it.media.kind == MediaKind.ANIME }
    adaptation?.media?.ref?.anilistId?.let { animeId ->
        Panel(stringResource(Res.string.manga_anime_adaptation)) {
            Row(
                Modifier.fillMaxWidth().clickable { onOpenAnime(animeId) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.LiveTv, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(adaptation.media.displayTitle(titleLanguage), style = MaterialTheme.typography.titleSmall)
                    Text(listOfNotNull(adaptation.media.year?.toString(), anilistStatusLabel(adaptation.media.status)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
    }
    val tree = info.relations.filter { it !== adaptation }
    if (tree.isNotEmpty()) {
        AsideRow(stringResource(Res.string.manga_series_tree)) {
            itemsIndexed(tree, key = { _, r -> "${r.relation}-${r.media.ref.anilistId}" }) { i, rel ->
                val data = rel.media.mangaCard(titleLanguage, subtitle = relationLabel(rel.relation))
                TitleCard(
                    data,
                    onClick = {
                        val id = rel.media.ref.anilistId
                        if (rel.media.kind == MediaKind.ANIME && id != null) onOpenAnime(id) else onOpenManga(rel.media)
                    },
                    context = CardContext.ROW,
                    modifier = Modifier.cardSway(i).cardToDetailsSource(data.key),
                )
            }
        }
    }
    if (info.characters.isNotEmpty()) {
        AsideRow(stringResource(Res.string.manga_characters)) {
            items(info.characters, key = { it.character.id }) { c ->
                Column(Modifier.width(88.dp)) {
                    MediaCover(url = c.character.image, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, kind = UpscaleImageKind.AVATAR)
                    Text(c.character.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                    c.role?.let { Text(characterRoleLabel(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    if (info.recommendations.isNotEmpty()) {
        AsideRow(stringResource(Res.string.rd_s11_if_you_liked)) {
            itemsIndexed(info.recommendations, key = { i, r -> "${r.media.ref.anilistId ?: r.media.title}-$i" }) { i, r ->
                val data = r.media.mangaCard(titleLanguage)
                TitleCard(data, onClick = { onOpenManga(r.media) }, context = CardContext.ROW, modifier = Modifier.cardSway(i).cardToDetailsSource(data.key))
            }
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun AsideRow(title: String, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        LazyRow(Modifier.fillMaxWidth().decor(DecorRegion.ROW), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(info: MangaInfo, ui: MangaDetailsUi) {
    val mu = ui.mangaUpdates
    val d = ui.details
    @Composable
    fun Fact(label: String, value: String?) {
        if (value.isNullOrBlank()) return
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    Fact(stringResource(Res.string.manga_fact_authors), info.authors.map { a -> a.staff.name to a.role?.let { staffRoleLabel(it) } }.joinToString(", ") { (name, role) -> role?.let { "$name ($it)" } ?: name })
    Fact(stringResource(Res.string.manga_fact_chapters), (info.chapters ?: d?.chapters ?: mu?.latestChapter)?.toString())
    Fact(stringResource(Res.string.manga_fact_volumes), (info.volumes ?: d?.volumes)?.toString())
    Fact(stringResource(Res.string.manga_fact_status), anilistStatusLabel(info.status))
    Fact(stringResource(Res.string.manga_fact_publication), mu?.status)
    Fact(stringResource(Res.string.manga_fact_publishers), mu?.publishers?.joinToString(", "))
    Fact(stringResource(Res.string.manga_fact_serialization), mu?.serialization)
    Fact(stringResource(Res.string.manga_fact_licensed), mu?.let { stringResource(if (it.licensed) Res.string.manga_yes else Res.string.manga_no) })
    Fact(stringResource(Res.string.manga_fact_start), info.startDate)
    Fact(stringResource(Res.string.manga_fact_end), info.endDate)
    Fact(stringResource(Res.string.manga_fact_origin), originLabel(info.origin))
    Fact(stringResource(Res.string.manga_fact_source), info.source?.let { mediaSourceLabel(it) })
    // Scores (BUG-126: they wrap, with clear labels and the language's thousands separator).
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        info.media.score?.let { ScoreBox(stringResource(Res.string.manga_score_anilist), stringResource(Res.string.manga_score_percent, it)) }
        info.meanScore?.let { ScoreBox(stringResource(Res.string.manga_score_mean), stringResource(Res.string.manga_score_percent, it)) }
        mu?.rating?.let { ScoreBox(stringResource(Res.string.manga_score_mangaupdates), formatDecimal1(it / 10.0)) }
        info.popularity?.let { ScoreBox(stringResource(Res.string.manga_score_popularity), formatCount(it.toLong())) }
        info.favourites?.let { ScoreBox(stringResource(Res.string.manga_score_favourites), formatCount(it.toLong())) }
    }
    if (info.rankings.isNotEmpty()) {
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            info.rankings.take(4).forEach { Pill(rankingLabel(it)) }
        }
    }
}

@Composable
private fun ScoreBox(label: String, value: String) {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun relationLabel(relation: String): String = when (relation.uppercase()) {
    "PREQUEL" -> stringResource(Res.string.manga_rel_prequel)
    "SEQUEL" -> stringResource(Res.string.manga_rel_sequel)
    "SIDE_STORY" -> stringResource(Res.string.manga_rel_side_story)
    "SPIN_OFF" -> stringResource(Res.string.manga_rel_spin_off)
    "ALTERNATIVE" -> stringResource(Res.string.manga_rel_alternative)
    "PARENT" -> stringResource(Res.string.manga_rel_parent)
    "ADAPTATION" -> stringResource(Res.string.manga_rel_adaptation)
    "SOURCE" -> stringResource(Res.string.manga_rel_source)
    else -> stringResource(Res.string.manga_rel_other)
}

/** BUG-111: an AniList ranking in the app's language ("#97 mejor valorado de todos los tiempos", "#1 más popular de 2018"). */
@Composable
internal fun rankingLabel(r: MangaRanking): String {
    val res = rankingLabelRes(r) ?: return "#${r.rank} ${r.context}"
    return if (r.allTime) stringResource(res, r.rank) else stringResource(res, r.rank, r.year ?: 0)
}

/** The string of [r], or null when AniList sent a type (or a dated ranking without its year) this page does not word. */
internal fun rankingLabelRes(r: MangaRanking): org.jetbrains.compose.resources.StringResource? = when {
    r.type == "RATED" && r.allTime -> Res.string.manga_ranking_rated_all_time
    r.type == "POPULAR" && r.allTime -> Res.string.manga_ranking_popular_all_time
    r.year == null -> null
    r.type == "RATED" -> Res.string.manga_ranking_rated_year
    r.type == "POPULAR" -> Res.string.manga_ranking_popular_year
    else -> null
}

/** BUG-099: "sin resultados" / "tiempo agotado" / "falló" (same words in the reader's source list). */
@Composable
internal fun sourceProblemLabel(p: SourceProblem): String = stringResource(
    when (p) {
        SourceProblem.NOT_FOUND -> Res.string.manga_source_problem_not_found
        SourceProblem.TIMEOUT -> Res.string.manga_source_problem_timeout
        SourceProblem.FAILED -> Res.string.manga_source_problem_failed
    },
)

internal const val MAX_TRACK_SEGMENTS = 52
