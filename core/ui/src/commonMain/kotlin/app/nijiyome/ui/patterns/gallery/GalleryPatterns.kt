// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.ui.patterns.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.EpisodeMark
import app.nijiyome.designsystem.direction.EpisodeMarkState
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.AttributeChips
import app.nijiyome.ui.patterns.ContextMenuArea
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.DragDropContainer
import app.nijiyome.ui.patterns.DropAction
import app.nijiyome.ui.patterns.DropHereButton
import app.nijiyome.ui.patterns.DropKind
import app.nijiyome.ui.patterns.Facet
import app.nijiyome.ui.patterns.FacetBar
import app.nijiyome.ui.patterns.FacetOption
import app.nijiyome.ui.patterns.FacetSelection
import app.nijiyome.ui.patterns.FileDropOverlay
import app.nijiyome.ui.patterns.FileDropResult
import app.nijiyome.ui.patterns.FolderPathPicker
import app.nijiyome.ui.patterns.ListEntryRules
import app.nijiyome.ui.patterns.ListStatusEditor
import app.nijiyome.ui.patterns.LivePreviewScaffold
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuDivider
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.patterns.MenuSheetHeader
import app.nijiyome.ui.patterns.MenuSubmenu
import app.nijiyome.ui.patterns.PickedBanner
import app.nijiyome.ui.patterns.PortCheck
import app.nijiyome.ui.patterns.PortPicker
import app.nijiyome.ui.patterns.QuantityPicker
import app.nijiyome.ui.patterns.ReorderableRow
import app.nijiyome.ui.patterns.ResetStyleButton
import app.nijiyome.ui.patterns.RuleChipRow
import app.nijiyome.ui.patterns.RuleOption
import app.nijiyome.ui.patterns.SelectionAction
import app.nijiyome.ui.patterns.SelectionActionBar
import app.nijiyome.ui.patterns.SelectionCheckbox
import app.nijiyome.ui.patterns.SourceAttribute
import app.nijiyome.ui.patterns.SourceChoiceBadge
import app.nijiyome.ui.patterns.TimeRangePicker
import app.nijiyome.ui.patterns.UndoController
import app.nijiyome.ui.patterns.WhyNotRow
import app.nijiyome.ui.patterns.dragSource
import app.nijiyome.ui.patterns.dropTarget
import app.nijiyome.ui.patterns.icon
import app.nijiyome.ui.patterns.label
import app.nijiyome.ui.patterns.moved
import app.nijiyome.ui.patterns.multiSelectable
import app.nijiyome.ui.patterns.rememberDragDropState
import app.nijiyome.ui.patterns.rememberReorderState
import app.nijiyome.ui.patterns.rememberSelectionState
import app.nijiyome.ui.patterns.reorderContainer
import app.nijiyome.ui.patterns.reorderPlacement
import app.nijiyome.ui.patterns.resultTitle
import app.nijiyome.ui.patterns.selectionKeys
import app.nijiyome.ui.patterns.speedUnits
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_gal_already_in
import app.nijiyome.ui.resources.rd_i1_gal_demo_drop
import app.nijiyome.ui.resources.rd_i1_gal_demo_dnd
import app.nijiyome.ui.resources.rd_i1_gal_demo_eps
import app.nijiyome.ui.resources.rd_i1_gal_demo_fac
import app.nijiyome.ui.resources.rd_i1_gal_demo_ctx
import app.nijiyome.ui.resources.rd_i1_gal_demo_pk
import app.nijiyome.ui.resources.rd_i1_gal_demo_reo
import app.nijiyome.ui.resources.rd_i1_gal_demo_src
import app.nijiyome.ui.resources.rd_i1_gal_demo_stp
import app.nijiyome.ui.resources.rd_i1_gal_demo_sub
import app.nijiyome.ui.resources.rd_i1_gal_episode
import app.nijiyome.ui.resources.rd_i1_gal_format
import app.nijiyome.ui.resources.rd_i1_gal_limit
import app.nijiyome.ui.resources.rd_i1_gal_mark_watched
import app.nijiyome.ui.resources.rd_i1_gal_move_to
import app.nijiyome.ui.resources.rd_i1_gal_patterns
import app.nijiyome.ui.resources.rd_i1_gal_play_ep
import app.nijiyome.ui.resources.rd_i1_gal_port
import app.nijiyome.ui.resources.rd_i1_gal_port_free
import app.nijiyome.ui.resources.rd_i1_gal_port_used
import app.nijiyome.ui.resources.rd_i1_gal_preview_text
import app.nijiyome.ui.resources.rd_i1_gal_quality
import app.nijiyome.ui.resources.rd_i1_gal_remove_download
import app.nijiyome.ui.resources.rd_i1_gal_remove_from_list
import app.nijiyome.ui.resources.rd_i1_gal_results
import app.nijiyome.ui.resources.rd_i1_gal_schedule
import app.nijiyome.ui.resources.rd_i1_gal_size
import app.nijiyome.ui.resources.rd_i1_gal_status
import app.nijiyome.ui.resources.rd_i1_gal_change_status
import app.nijiyome.ui.resources.rd_i1_gal_downloads_folder
import app.nijiyome.ui.resources.rd_i1_gal_done_msg
import app.nijiyome.ui.resources.rd_i1_gal_simulate
import app.nijiyome.ui.resources.rd_i1_gal_only_completed
import app.nijiyome.ui.resources.rd_i1_gal_fails_res
import app.nijiyome.ui.resources.rd_i1_gal_download
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay

// Sparkles redesign, lane I1. Live demos of the interaction patterns inside the design gallery, on the bundled sample titles. Every
// state is local: nothing is saved.

@Composable
internal fun PatternsGallery(cards: List<TitleCardData>, undo: UndoController) {
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        GalleryHeading(stringResource(Res.string.rd_i1_gal_patterns), null)
        SelectionDemo(undo)
        StatusDemo(undo)
        DragDemo(cards, undo)
        ReorderDemo(cards, undo)
        FacetsDemo(cards)
        ContextMenuDemo(cards, undo)
        RulesDemo()
        PreviewDemo(undo)
        PickersDemo()
        DropDemo()
    }
}

@Composable
private fun SelectionDemo(undo: UndoController) {
    val selection = rememberSelectionState<Int>()
    var watched by remember { mutableStateOf(setOf(1)) }
    val aired = (1..8).toList()
    val all = (1..10).toList()
    val markLabel = stringResource(Res.string.rd_i1_gal_mark_watched)
    val doneMsg = stringResource(Res.string.rd_i1_gal_done_msg)
    val episodeLabel = stringResource(Res.string.rd_i1_gal_episode)
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_eps)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
            Column(Modifier.fillMaxWidth().selectionKeys(selection) { aired }) {
                all.forEach { n ->
                    val isAired = n in aired
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .multiSelectable(selection, n, order = { aired }, onOpen = {}, enabled = isAired)
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SelectionCheckbox(selection.isSelected(n), visible = selection.active)
                        EpisodeMark(
                            when {
                                !isAired -> EpisodeMarkState.UNAIRED
                                n in watched -> EpisodeMarkState.WATCHED
                                else -> EpisodeMarkState.AIRED
                            },
                            number = n,
                        )
                        Text("$episodeLabel $n", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        SelectionActionBar(
            visible = selection.active,
            count = selection.count,
            onExit = selection::exit,
            actions = listOf(
                SelectionAction(
                    markLabel,
                    Icons.Outlined.Visibility,
                    onClick = {
                        val before = watched
                        watched = watched + selection.selected
                        undo.show(doneMsg, undo = { watched = before })
                        selection.exit()
                    },
                    emphasis = ActionEmphasis.FILLED,
                ),
                SelectionAction(stringResource(Res.string.rd_i1_gal_download), Icons.Outlined.Download, onClick = { selection.exit() }),
                SelectionAction(
                    stringResource(Res.string.rd_i1_gal_remove_download),
                    Icons.Outlined.Delete,
                    onClick = { selection.exit() },
                    emphasis = ActionEmphasis.OUTLINED,
                    confirmLabel = stringResource(Res.string.rd_i1_gal_remove_download) + " (${selection.count})",
                ),
            ),
            onSelectAll = { selection.selectAll(aired) },
        )
    }
}

@Composable
private fun StatusDemo(undo: UndoController) {
    var status by remember { mutableStateOf<StatusSlot?>(StatusSlot.PLANNING) }
    var progress by remember { mutableIntStateOf(0) }
    val doneMsg = stringResource(Res.string.rd_i1_gal_done_msg)
    val onlyCompleted = stringResource(Res.string.rd_i1_gal_only_completed)
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_stp)) {
        ListStatusEditor(
            status = status,
            onStatusChange = { s ->
                val before = status to progress
                progress = ListEntryRules.progressForStatus(s, progress, 12)
                status = s
                undo.show(doneMsg, undo = { status = before.first; progress = before.second })
            },
            progress = progress,
            onProgressChange = { p ->
                progress = p
                status = ListEntryRules.statusForProgress(status, p)
            },
            aired = 8,
            total = 12,
            onRemove = {
                val before = status to progress
                status = null
                progress = 0
                undo.show(doneMsg, undo = { status = before.first; progress = before.second })
            },
            disabledReason = { if (it == StatusSlot.REWATCHING && status != StatusSlot.COMPLETED) onlyCompleted else null },
        )
    }
}

@Composable
private fun DragDemo(cards: List<TitleCardData>, undo: UndoController) {
    val dnd = rememberDragDropState<String>()
    val columns = listOf(StatusSlot.WATCHING, StatusSlot.PLANNING, StatusSlot.PAUSED)
    var statusOf by remember(cards) { mutableStateOf(cards.associate { it.key to (it.listStatus ?: StatusSlot.PLANNING) }) }
    val doneMsg = stringResource(Res.string.rd_i1_gal_done_msg)
    val labels = columns.associateWith { it.label() }
    val moveTo = columns.associateWith { stringResource(Res.string.rd_i1_gal_move_to, labels.getValue(it)) }
    val already = columns.associateWith { stringResource(Res.string.rd_i1_gal_already_in, labels.getValue(it)) }
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_dnd)) {
        dnd.picked?.let { key -> PickedBanner(cards.first { it.key == key }.title, onCancel = dnd::cancel) }
        DragDropContainer(dnd, itemLabel = { key -> cards.first { it.key == key }.title }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                columns.forEach { column ->
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 200.dp)
                            .dropTarget(
                                dnd,
                                key = column,
                                label = moveTo.getValue(column),
                                refuse = { key -> if (statusOf[key] == column) already.getValue(column) else null },
                                onDrop = { key, _ ->
                                    val before = statusOf
                                    statusOf = statusOf + (key to column)
                                    undo.show(doneMsg, undo = { statusOf = before })
                                },
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusMark(column, labels.getValue(column), Modifier.weight(1f), selected = true)
                            DropHereButton(dnd, column)
                        }
                        cards.filter { statusOf[it.key] == column }.forEach { card ->
                            TitleCard(
                                card,
                                onClick = {},
                                context = CardContext.COMPACT,
                                modifier = Modifier.fillMaxWidth().dragSource(dnd, card.key),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderDemo(cards: List<TitleCardData>, undo: UndoController) {
    var order by remember(cards) { mutableStateOf(cards.map { it.key }) }
    val doneMsg = stringResource(Res.string.rd_i1_gal_done_msg)
    val reorder = rememberReorderState(onMove = { from, to ->
        val before = order
        order = order.moved(from, to)
        undo.show(doneMsg, undo = { order = before })
    })
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_reo)) {
        LazyColumn(state = reorder.listState, modifier = Modifier.fillMaxWidth().height(280.dp).reorderContainer(reorder)) {
            itemsIndexed(order, key = { _, k -> k }) { i, k ->
                val card = cards.first { it.key == k }
                ReorderableRow(reorder, i, order.size, card.title, Modifier.reorderPlacement(), supporting = card.subtitle)
            }
        }
    }
}

@Composable
private fun FacetsDemo(cards: List<TitleCardData>) {
    var selection by remember { mutableStateOf<FacetSelection>(emptyMap()) }
    val statusLabel = stringResource(Res.string.rd_i1_gal_status)
    val formatLabel = stringResource(Res.string.rd_i1_gal_format)
    fun matches(card: TitleCardData, except: String?): Boolean {
        val st = selection["status"].orEmpty()
        val fm = selection["format"].orEmpty()
        val statusOk = card.listStatus?.let { it.name in st } ?: false
        val formatOk = card.format?.let { it in fm } ?: false
        return (except == "status" || st.isEmpty() || statusOk) && (except == "format" || fm.isEmpty() || formatOk)
    }
    val results = cards.filter { matches(it, null) }
    val facets = listOf(
        Facet(
            "status",
            statusLabel,
            StatusSlot.entries.map { s -> FacetOption(s.name, s.label(), cards.count { matches(it, "status") && it.listStatus == s }) },
        ),
        Facet(
            "format",
            formatLabel,
            cards.mapNotNull { it.format }.distinct().map { f -> FacetOption(f, f, cards.count { matches(it, "format") && it.format == f }) },
        ),
    )
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_fac)) {
        FacetBar(
            facets,
            selection,
            onSelectionChange = { selection = it },
            resultCount = results.size,
            resultText = stringResource(Res.string.rd_i1_gal_results, results.size, cards.size),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            results.forEach { TitleCard(it, onClick = {}, context = CardContext.GRID, modifier = Modifier.width(120.dp)) }
        }
    }
}

@Composable
private fun ContextMenuDemo(cards: List<TitleCardData>, undo: UndoController) {
    val doneMsg = stringResource(Res.string.rd_i1_gal_done_msg)
    val play = stringResource(Res.string.rd_i1_gal_play_ep)
    val mark = stringResource(Res.string.rd_i1_gal_mark_watched)
    val change = stringResource(Res.string.rd_i1_gal_change_status)
    val remove = stringResource(Res.string.rd_i1_gal_remove_from_list)
    val labels = StatusSlot.entries.associateWith { it.label() }
    var statusOf by remember(cards) { mutableStateOf(cards.associate { it.key to it.listStatus }) }
    fun entries(card: TitleCardData): List<MenuEntry> = listOf(
        MenuAction(play, onClick = {}, icon = Icons.Outlined.PlayCircle),
        MenuAction(mark, onClick = { undo.show(doneMsg, undo = {}) }, icon = Icons.Outlined.CheckCircle),
        MenuSubmenu(
            change,
            StatusSlot.entries.map { s ->
                MenuAction(labels.getValue(s), onClick = {
                    val before = statusOf
                    statusOf = statusOf + (card.key to s)
                    undo.show(doneMsg, undo = { statusOf = before })
                }, icon = s.icon, checked = statusOf[card.key] == s)
            },
            icon = Icons.Outlined.Bookmark,
        ),
        MenuDivider,
        MenuAction(remove, onClick = {
            val before = statusOf
            statusOf = statusOf + (card.key to null)
            undo.show(doneMsg, undo = { statusOf = before })
        }, icon = Icons.Outlined.Delete, destructive = true, enabled = statusOf[card.key] != null),
    )
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_ctx)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.take(4).forEach { card ->
                val status = statusOf[card.key]
                val shown = card.copy(listStatus = status, listStatusLabel = status?.let { labels.getValue(it) })
                Column(Modifier.width(140.dp)) {
                    ContextMenuArea(
                        entries = { entries(card) },
                        sheetHeader = {
                            MenuSheetHeader(card.title) { if (status != null) StatusMark(status, labels.getValue(status), selected = true) }
                        },
                    ) { menu ->
                        TitleCard(shown, onClick = {}, context = CardContext.GRID, onLongClick = menu::showSheet)
                    }
                    ContextMenuButton(entries = { entries(card) }, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}

@Composable
private fun RulesDemo() {
    var minRes by remember { mutableStateOf("1080p") }
    val resolutions = listOf("720p", "1080p", "2160p")
    val chosen = listOf(SourceAttribute("res", "1080p"), SourceAttribute("codec", "HEVC 10-bit"), SourceAttribute("subs", "Subs ES"))
    val other = listOf(SourceAttribute("res", "720p"), SourceAttribute("codec", "HEVC 10-bit"), SourceAttribute("subs", "Subs EN"))
    val fails = if (resolutions.indexOf("720p") < resolutions.indexOf(minRes)) listOf(stringResource(Res.string.rd_i1_gal_fails_res, minRes)) else emptyList()
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_src)) {
        RuleChipRow(
            label = stringResource(Res.string.rd_i1_gal_quality),
            options = resolutions.map { RuleOption(it, it) },
            selected = setOf(minRes),
            onToggle = { minRes = it },
            single = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AttributeChips(chosen, Modifier.weight(1f))
            SourceChoiceBadge(manual = false)
        }
        AttributeChips(other, compareTo = chosen)
        WhyNotRow(fails)
    }
}

@Composable
private fun PreviewDemo(undo: UndoController) {
    var size by remember { mutableFloatStateOf(22f) }
    val doneMsg = stringResource(Res.string.rd_i1_gal_done_msg)
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_sub)) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            LivePreviewScaffold(
                preview = {
                    Box(
                        Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium).background(Color(0xFF1B2238)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            stringResource(Res.string.rd_i1_gal_preview_text),
                            fontSize = size.sp,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                },
                stacked = false,
                controlsWidth = 240.dp,
            ) {
                Text(stringResource(Res.string.rd_i1_gal_size, size.toInt()), style = MaterialTheme.typography.labelLarge)
                Slider(value = size, onValueChange = { size = it }, valueRange = 12f..40f)
                ResetStyleButton(enabled = size != 22f) {
                    val before = size
                    size = 22f
                    undo.show(doneMsg, undo = { size = before })
                }
            }
        }
    }
}

@Composable
private fun PickersDemo() {
    var port by remember { mutableIntStateOf(47321) }
    var folder by remember { mutableStateOf<String?>("/home/user/Anime") }
    var start by remember { mutableIntStateOf(60) }
    var end by remember { mutableIntStateOf(8 * 60) }
    var limit by remember { mutableLongStateOf(5 * 1024L) }
    val free = stringResource(Res.string.rd_i1_gal_port_free)
    val used = stringResource(Res.string.rd_i1_gal_port_used)
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_pk)) {
        PortPicker(
            port = port,
            onPortChange = { port = it },
            label = stringResource(Res.string.rd_i1_gal_port),
            defaultPort = 47321,
            onTest = { p ->
                delay(600)
                if (p == 8099) PortCheck(false, used, suggestion = 47321) else PortCheck(true, free)
            },
        )
        FolderPathPicker(path = folder, onPathChange = { folder = it }, label = stringResource(Res.string.rd_i1_gal_downloads_folder))
        TimeRangePicker(start, end, onChange = { s, e -> start = s; end = e }, label = stringResource(Res.string.rd_i1_gal_schedule))
        QuantityPicker(
            baseValue = limit,
            onBaseValueChange = { limit = it },
            units = speedUnits(),
            label = stringResource(Res.string.rd_i1_gal_limit),
            min = 1,
            presets = listOf(1024L, 5 * 1024L, 10 * 1024L),
        )
    }
}

@Composable
private fun DropDemo() {
    var hovering by remember { mutableStateOf<Pair<DropKind, String>?>(null) }
    var result by remember { mutableStateOf<Pair<DropKind, String>?>(null) }
    val samples = listOf(
        DropKind.EXTENSION to "toko-1.4.0.kai",
        DropKind.TORRENT to "Grand Blue S02 [1080p].torrent",
        DropKind.MAGNET to "magnet:?xt=urn:btih:…&dn=Koori no Jouheki 07",
        DropKind.VIDEO to "Laid-Off Mage - 08 [1080p].mkv",
        DropKind.SUBTITLE to "Laid-Off Mage - 08.es.ass",
        DropKind.UNSUPPORTED to "capturas.zip",
    )
    LaunchedEffect(hovering) {
        val h = hovering ?: return@LaunchedEffect
        delay(900)
        hovering = null
        result = h
    }
    GalleryBlock(stringResource(Res.string.rd_i1_gal_demo_drop)) {
        Text(stringResource(Res.string.rd_i1_gal_simulate), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            samples.forEach { (kind, name) -> OutlinedButton(onClick = { hovering = kind to name }) { Text(kind.label()) } }
        }
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            FileDropOverlay(visible = hovering != null, kind = hovering?.first ?: DropKind.UNKNOWN, name = hovering?.second)
        }
    }
    result?.let { (kind, name) ->
        val close = { result = null }
        FileDropResult(
            kind = kind,
            name = name,
            onDismiss = close,
            actions = if (kind == DropKind.UNSUPPORTED) emptyList() else listOf(DropAction(kind.resultTitle(), close, ActionEmphasis.FILLED)),
        )
    }
}
