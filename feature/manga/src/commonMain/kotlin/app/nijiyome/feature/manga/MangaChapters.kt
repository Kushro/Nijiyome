// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
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
import app.nijiyome.manga.ChapterLists
import app.nijiyome.manga.ChapterOrder
import app.nijiyome.manga.ChapterText
import app.nijiyome.manga.MangaRepositoryImpl
import app.nijiyome.manga.key
import app.nijiyome.model.ListProgress
import app.nijiyome.model.LoadState
import app.nijiyome.model.MangaChapter
import app.nijiyome.model.MangaChapterSource
import app.nijiyome.model.MangaReaderArgs
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuDivider
import app.nijiyome.ui.patterns.SelectionAction
import app.nijiyome.ui.patterns.SelectionActionBar
import app.nijiyome.ui.patterns.SelectionCheckbox
import app.nijiyome.ui.patterns.SelectionState
import app.nijiyome.ui.patterns.SelectionTopBar
import app.nijiyome.ui.patterns.UndoController
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.multiSelectable
import app.nijiyome.ui.patterns.selectionKeys
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// MNG-08 to MNG-10: the chapter list of the manga details page (sources as radio cards with their reliability, filter, order,
// language, scanlation group, volumes, multi-selection with undo) and the "Fuentes e idiomas" sheet.

/** Everything the chapter section reads, gathered once by the details screen. */
internal class ChapterSectionState(
    val ui: MangaDetailsUi,
    val readUpTo: Int,
    val nextChapter: Double?,
    val downloadedIds: Set<String>,
    val preferredKey: String?,
    val selection: SelectionState<String>,
) {
    val shownIds: List<String> get() = ui.shown.map(MangaRepositoryImpl::downloadId)

    fun isRead(ch: MangaChapter): Boolean = ListProgress.isChapterRead(ch.number, readUpTo)
}

internal fun LazyListScope.chaptersSection(
    state: ChapterSectionState,
    vm: MangaDetailsViewModel,
    onOpenReader: (MangaReaderArgs) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSourceSheet: () -> Unit,
) {
    val ui = state.ui
    item(key = "chapters-header") { ChaptersHeader(state, vm, onOpenSourceSheet) }
    when (val sources = ui.sources) {
        LoadState.Loading -> item(key = "sources-loading") { LoadingPlaceholder(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 72.dp)) }
        is LoadState.Err -> item(key = "sources-error") { ErrorBlock(sources.cause, vm::loadSources) }
        is LoadState.Ok -> {
            if (sources.value.isEmpty()) {
                item(key = "no-sources") {
                    ReadingEmpty(
                        kind = EmptyKind.SOURCES,
                        icon = Icons.Outlined.Extension,
                        title = stringResource(Res.string.manga_no_sources),
                        message = stringResource(Res.string.manga_no_sources_message),
                        actionLabel = stringResource(Res.string.manga_open_extensions),
                        onAction = onOpenExtensions,
                    )
                }
                return
            }
            item(key = "sources") { SourceCards(sources.value, ui.source, state.preferredKey, vm::selectSource) }
            item(key = "tools") { ChapterTools(ui, vm) }
            when (val chapters = ui.chapters) {
                LoadState.Loading -> item(key = "chapters-loading") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { LoadingPlaceholder(Modifier.fillMaxWidth().heightIn(min = 56.dp)) }
                    }
                }
                is LoadState.Err -> item(key = "chapters-error") { ErrorBlock(chapters.cause, vm::refreshChapters) }
                is LoadState.Ok -> {
                    val shown = ui.shown
                    when {
                        ui.noSourceFound -> item(key = "unmatched-all") {
                            ReadingEmpty(EmptyKind.SOURCES, Icons.Outlined.Translate, stringResource(Res.string.manga_no_source_found_long), null, actionLabel = stringResource(Res.string.manga_source_settings), onAction = onOpenSourceSheet)
                        }
                        ui.source?.itemId == null -> item(key = "unmatched") { Text(stringResource(Res.string.manga_source_unmatched), modifier = Modifier.padding(16.dp)) }
                        shown.isEmpty() -> item(key = "chapters-empty") {
                            ReadingEmpty(
                                EmptyKind.SEARCH, Icons.Outlined.FilterList, stringResource(Res.string.manga_chapters_empty), stringResource(Res.string.rd_s11_chapters_empty_hint),
                                actionLabel = stringResource(Res.string.rd_s11_filters_clear),
                                onAction = { vm.setTools { it.copy(query = "", lang = null, group = null) } },
                            )
                        }
                        ui.tools.byVolume -> ChapterLists.byVolume(shown).forEach { (volume, list) ->
                            item(key = "vol-$volume") {
                                Text(
                                    volume?.let { stringResource(Res.string.manga_volume_n, it) } ?: stringResource(Res.string.manga_volume_none),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(list, key = { "c-" + MangaRepositoryImpl.downloadId(it) }) { ch -> ChapterRow(ch, state, vm, onOpenReader) }
                        }
                        else -> items(shown, key = { "c-" + MangaRepositoryImpl.downloadId(it) }) { ch -> ChapterRow(ch, state, vm, onOpenReader) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChaptersHeader(state: ChapterSectionState, vm: MangaDetailsViewModel, onOpenSourceSheet: () -> Unit) {
    val ui = state.ui
    val count = (ui.chapters as? LoadState.Ok)?.value?.size ?: ui.source?.chapterCount
    val refresh = stringResource(Res.string.rd_s11_refresh_list)
    val forget = stringResource(Res.string.rd_s11_forget_matches)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(
            if (count != null) stringResource(Res.string.rd_s11_chapters_count, count) else stringResource(Res.string.manga_chapters),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!state.selection.active) {
                        TextButton(onClick = { state.selection.start() }) {
                            Icon(Icons.Outlined.Checklist, contentDescription = null)
                            Text(stringResource(Res.string.rd_s11_select), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    ContextMenuButton(
                        entries = {
                            listOf(
                                MenuAction(refresh, vm::refreshChapters, icon = Icons.Outlined.Refresh),
                                MenuDivider,
                                MenuAction(forget, vm::clearCache, icon = Icons.Outlined.DeleteSweep, destructive = true),
                            )
                        },
                        contentDescription = stringResource(Res.string.rd_s11_chapter_actions),
                    )
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.rd_s11_source), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenSourceSheet) {
                Icon(Icons.Outlined.Translate, contentDescription = null)
                Text(stringResource(Res.string.manga_source_settings), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** The sources as a radio group of cards: name (star = preferred), "N caps. · EN · ES" or why it found nothing, reliability. */
@Composable
private fun SourceCards(sources: List<MangaChapterSource>, current: MangaChapterSource?, preferredKey: String?, onSelect: (MangaChapterSource) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sources.forEach { s ->
            val selected = current?.key == s.key
            val enabled = s.itemId != null
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(min = 150.dp, max = 220.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onSelect(s) },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.extensionName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (preferredKey == s.key) Icon(Icons.Outlined.Star, contentDescription = stringResource(Res.string.manga_preferred_source), modifier = Modifier.size(16.dp))
                    }
                    Text(sourceFacts(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (enabled) ProgressTrack(progress = s.reliability.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** "200 caps. · EN · ES · 87 %", or BUG-099's reason ("sin resultados") when the source found nothing. */
@Composable
internal fun sourceFacts(s: MangaChapterSource): String {
    s.problem?.let { return sourceProblemLabel(it) }
    if (s.itemId == null) return stringResource(Res.string.manga_source_unmatched)
    return listOfNotNull(
        s.chapterCount?.let { stringResource(Res.string.rd_s11_chapters_short, it) },
        s.languages.take(3).joinToString(" · ").uppercase().ifBlank { null },
        stringResource(Res.string.rd_s11_reliability, (s.reliability * 100).toInt()),
    ).joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterTools(ui: MangaDetailsUi, vm: MangaDetailsViewModel) {
    val all = (ui.chapters as? LoadState.Ok)?.value.orEmpty()
    val langs = ChapterLists.languages(all)
    val groups = ui.groups
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ui.tools.query,
                onValueChange = { q -> vm.setTools { it.copy(query = q) } },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.manga_chapter_filter_hint)) },
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.widthIn(min = 240.dp, max = 520.dp),
            )
            Segmented(
                options = listOf(
                    SegmentedOption(ChapterOrder.DESC, stringResource(Res.string.rd_s11_order_newest)),
                    SegmentedOption(ChapterOrder.ASC, stringResource(Res.string.rd_s11_order_oldest)),
                ),
                selected = ui.tools.order,
                onSelect = { o -> vm.setTools { it.copy(order = o) } },
                small = true,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (langs.size > 1) {
                FilterChip(selected = ui.tools.lang == null, onClick = { vm.setTools { it.copy(lang = null) } }, label = { Text(stringResource(Res.string.rd_s11_all_languages)) })
                langs.forEach { l ->
                    FilterChip(
                        selected = ui.tools.lang == l,
                        onClick = { vm.setTools { it.copy(lang = l) } },
                        label = { Text(Locale.forLanguageTag(l).getDisplayLanguage(Locale.getDefault()).replaceFirstChar(Char::uppercase).ifBlank { l }) },
                    )
                }
            }
            if (groups.size > 1) {
                FilterChipMenu(
                    label = stringResource(Res.string.rd_s11_group),
                    value = ui.tools.group,
                    options = listOf<String?>(null) + groups,
                    selected = ui.tools.group,
                    optionLabel = { it ?: stringResource(Res.string.rd_s11_all_groups) },
                    onSelect = { g -> vm.setTools { it.copy(group = g) } },
                )
            }
            FilterChip(selected = ui.tools.byVolume, onClick = { vm.setTools { it.copy(byVolume = !it.byVolume) } }, label = { Text(stringResource(Res.string.manga_group_volume)) })
            Text(stringResource(Res.string.manga_chapter_count, ui.shown.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One chapter: its mark (read / next / unread), "Cap. N · title", group · language · date · volume, downloaded, read toggle. */
@Composable
private fun ChapterRow(ch: MangaChapter, state: ChapterSectionState, vm: MangaDetailsViewModel, onOpenReader: (MangaReaderArgs) -> Unit) {
    val id = MangaRepositoryImpl.downloadId(ch)
    val isDownloaded = ch.downloaded || id in state.downloadedIds
    val read = state.isRead(ch)
    val next = state.nextChapter != null && ch.number == state.nextChapter
    val selection = state.selection
    val date = ch.releasedAtMs?.let { remember(it) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } }
    val number = ChapterText.formatNumber(ch.number)
    Row(
        Modifier
            .fillMaxWidth()
            .decor(DecorRegion.EPISODES)
            .selectionKeys(selection) { state.shownIds }
            .multiSelectable(selection, id, order = { state.shownIds }, onOpen = { onOpenReader(vm.argsFor(ch.copy(downloaded = isDownloaded))) })
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionCheckbox(selected = selection.isSelected(id), visible = selection.active)
        WatchedTransition(read) { r ->
            EpisodeMark(
                when {
                    r -> EpisodeMarkState.WATCHED
                    next -> EpisodeMarkState.IN_PROGRESS
                    else -> EpisodeMarkState.AIRED
                },
                number = ListProgress.chapterIndex(ch.number),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (number?.let { stringResource(Res.string.rd_s11_chapter_short, it) } ?: (ch.title ?: ch.id)) +
                        (if (number != null) ch.title?.takeIf { t -> t.isNotBlank() }?.let { t -> " · $t" } ?: "" else ""),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (next) Pill(stringResource(Res.string.rd_s11_next), tone = PillTone.PRIMARY)
            }
            Text(
                listOfNotNull(ch.group, ch.lang?.uppercase(), date, ch.volume?.let { stringResource(Res.string.rd_s11_volume_short, it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isDownloaded) Icon(Icons.Outlined.DownloadDone, contentDescription = stringResource(Res.string.manga_downloaded), modifier = Modifier.size(20.dp))
        if (!selection.active && number != null) {
            IconButton(onClick = { vm.toggleRead(ch) }) {
                Icon(
                    if (read) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = stringResource(if (read) Res.string.manga_mark_unread else Res.string.manga_mark_read, number),
                    tint = if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The batch bar of the chapter selection: mark read / unread (undoable) and download N (MNG-09). */
@Composable
internal fun ChapterSelectionBar(state: ChapterSectionState, vm: MangaDetailsViewModel, undo: UndoController, modifier: Modifier = Modifier) {
    val selection = state.selection
    val count = selection.count
    val markedRead = stringResource(Res.string.rd_s11_marked_read)
    val markedUnread = stringResource(Res.string.rd_s11_marked_unread)
    val actions = if (count == 0) {
        emptyList()
    } else {
        listOf(
            SelectionAction(stringResource(Res.string.rd_s11_mark_read), Icons.Outlined.DoneAll, onClick = {
                vm.markChapters(selection.selected, read = true)?.let { revert -> undo.show(markedRead, undo = revert) }
                selection.exit()
            }),
            SelectionAction(stringResource(Res.string.rd_s11_mark_unread), Icons.Outlined.RemoveDone, onClick = {
                vm.markChapters(selection.selected, read = false)?.let { revert -> undo.show(markedUnread, undo = revert) }
                selection.exit()
            }),
            SelectionAction(stringResource(Res.string.rd_s11_download_n, count), Icons.Outlined.Download, onClick = {
                vm.downloadChapters(selection.selected)
                selection.exit()
            }, emphasis = ActionEmphasis.FILLED),
        )
    }
    // The selection follows the visible chapters (a filter or a source change drops what is no longer listed).
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

/** Phones: while selecting, the count, leave, span, all and unread at the top. */
@Composable
internal fun ChapterSelectionTopBar(state: ChapterSectionState) {
    val ui = state.ui
    SelectionTopBar(
        count = state.selection.count,
        onExit = state.selection::exit,
        onSelectSpan = { state.selection.fillSpan(state.shownIds) },
        onSelectAll = { state.selection.selectAll(state.shownIds) },
        onSelectUnwatched = {
            val unread = ui.shown.filterNot(state::isRead).map(MangaRepositoryImpl::downloadId).toSet()
            state.selection.selectWhere(state.shownIds) { it in unread }
        },
    )
}

// ------------------------------------------------------------------------------------------------------------------- source sheet

/**
 * "Fuentes e idiomas" (MNG-10): the preferred languages in order (up, remove, add), the source for this title (Automático or one
 * source, which also opens its chapters) and the way to the extensions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceSheet(
    sources: List<MangaChapterSource>,
    current: MangaChapterSource?,
    preferredKey: String?,
    languages: List<String>,
    onSelect: (MangaChapterSource) -> Unit,
    onPrefer: (MangaChapterSource?) -> Unit,
    onLanguages: (List<String>) -> Unit,
    onOpenExtensions: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun nameOf(code: String) = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar(Char::uppercase).ifBlank { code }
    AdaptiveSheet(onDismissRequest = onDismiss, modifier = Modifier.decor(DecorRegion.SHEET)) {
        Text(stringResource(Res.string.manga_source_settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.rd_s11_languages_order), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.manga_preferred_languages_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            languages.forEachIndexed { i, code ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nameOf(code), modifier = Modifier.weight(1f))
                    IconButton(onClick = { onLanguages(languages.toMutableList().also { l -> l[i] = l[i - 1].also { l[i - 1] = l[i] } }) }, enabled = i > 0) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(Res.string.rd_s11_language_up, nameOf(code)))
                    }
                    IconButton(onClick = { onLanguages(languages - code) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s11_language_remove, nameOf(code)))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LANGUAGE_CHOICES.filter { it !in languages }.forEach { code ->
                    FilterChip(selected = false, onClick = { onLanguages(languages + code) }, label = { Text(nameOf(code)) }, leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) })
                }
            }
            Text(stringResource(Res.string.rd_s11_source_for_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            SourceRadio(
                title = stringResource(Res.string.manga_source_auto),
                detail = stringResource(Res.string.manga_source_auto_hint),
                selected = preferredKey == null,
                enabled = true,
                onClick = { onPrefer(null) },
            )
            sources.forEach { s ->
                SourceRadio(
                    title = s.extensionName + if (current?.key == s.key) " · " + stringResource(Res.string.rd_s11_showing) else "",
                    detail = sourceFacts(s),
                    selected = preferredKey == s.key,
                    enabled = s.itemId != null,
                    onClick = {
                        onPrefer(s)
                        onSelect(s)
                    },
                )
            }
            TextButton(onClick = onOpenExtensions) {
                Icon(Icons.Outlined.Extension, contentDescription = null)
                Text(stringResource(Res.string.rd_s11_manage_extensions), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun SourceRadio(title: String, detail: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal val LANGUAGE_CHOICES = listOf("en", "es", "pt", "fr", "de", "it", "ru", "id", "vi", "th", "ja", "ko", "zh", "ar", "tr", "pl")
