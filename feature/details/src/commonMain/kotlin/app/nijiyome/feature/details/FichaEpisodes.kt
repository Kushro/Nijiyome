// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nijiyome.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EpisodeMark
import app.nijiyome.designsystem.direction.EpisodeMarkState
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.WatchedTransition
import app.nijiyome.feature.details.resources.Res
import app.nijiyome.feature.details.resources.*
import app.nijiyome.feature.downloads.DownloadSeasonSlot
import app.nijiyome.feature.social.AniListSocialSlot
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.media.progressFraction
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.ContextMenuArea
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuDivider
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.patterns.MenuSheetHeader
import app.nijiyome.ui.patterns.SelectionAction
import app.nijiyome.ui.patterns.SelectionActionBar
import app.nijiyome.ui.patterns.SelectionCheckbox
import app.nijiyome.ui.patterns.SelectionTopBar
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.multiSelectable
import app.nijiyome.ui.patterns.selectionKeys
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_episode_number
import app.nijiyome.ui.resources.ui_retry
import app.nijiyome.ui.state.userMessage
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane S4: Ficha › Episodios in its three views (Lista, Tarjetas, Rejilla = Diseño y estilo › Episodios), the same
// multi-selection and actions in all of them (artifact patterns `eps` and `ctx`), and the Ruta stations. Click plays; Ctrl / Shift +
// click or a long press select; right click, Shift+F10 or ⋮ open the episode's menu.

/** The episode the Watch button continues (marked "Siguiente"); null when it starts, rewatches or is up to date. */
internal fun DetailsUiState.currentEpisode(): Int? = continueEpisode.takeIf { watchAction == WatchAction.CONTINUE }

/** Episode numbers that can be selected (aired), in display order. */
internal fun EpisodeLists?.airedNumbers(): List<Int> = this?.all.orEmpty().filterNot { it.unaired }.map { it.episode.number }

/** The mark of an episode: not aired, watched, the one in progress, or aired and still to see. */
internal fun episodeMarkState(item: EpisodeItem, watched: Boolean, inProgress: Boolean): EpisodeMarkState = when {
    item.unaired -> EpisodeMarkState.UNAIRED
    watched -> EpisodeMarkState.WATCHED
    inProgress -> EpisodeMarkState.IN_PROGRESS
    else -> EpisodeMarkState.AIRED
}

/** One page of the episode list: the range chips (more than 100 episodes), the folded earlier ones and the rows to draw. */
internal data class EpisodePage(
    val ranges: List<IntRange>,
    val selectedRange: Int,
    val hiddenFrom: Int?,
    val hiddenTo: Int?,
    val items: List<EpisodeItem>,
)

internal fun episodePage(lists: EpisodeLists, state: DetailsUiState, range: Int, showEarlier: Boolean): EpisodePage {
    val searching = state.episodeQuery.isNotBlank()
    val filtered = lists.filtered
    if (filtered.isEmpty()) return EpisodePage(emptyList(), 0, null, null, emptyList())
    // While searching, every match shows (no ranges).
    val count = if (searching) 1 else (filtered.size + RANGE_SIZE - 1) / RANGE_SIZE
    val ranges = if (count > 1) {
        List(count) { i -> filtered[i * RANGE_SIZE].episode.number..filtered[minOf((i + 1) * RANGE_SIZE, filtered.size) - 1].episode.number }
    } else {
        emptyList()
    }
    val selected = range.coerceIn(0, count - 1)
    val full = if (searching) filtered else filtered.drop(selected * RANGE_SIZE).take(RANGE_SIZE)
    val hidden = if (searching || showEarlier) 0 else hiddenLeading(full, state.currentEpisode())
    return EpisodePage(
        ranges = ranges,
        selectedRange = selected,
        hiddenFrom = if (hidden > 0) full.first().episode.number else null,
        hiddenTo = if (hidden > 0) full[hidden - 1].episode.number else null,
        items = if (hidden > 0) full.drop(hidden) else full,
    )
}

// ---------------------------------------------------------------- tabs

/** The content of the selected tab as list items, shared by the three structures. [route] = Ruta draws the episodes as stations. */
internal fun LazyListScope.fichaTabItems(
    media: Media,
    state: DetailsUiState,
    actions: DetailsActions,
    ui: FichaUi,
    lists: EpisodeLists?,
    cardColumns: Int,
    route: Boolean,
) {
    when (ui.tab) {
        DetailsTab.EPISODES -> {
            fichaEpisodeItems(media, state, actions, ui, lists, cardColumns, route)
            item(key = "season-slot", contentType = "slot") { DownloadSeasonSlot(media) }
        }
        DetailsTab.INFO -> item(key = "info", contentType = "info") { InfoTab(media, state, actions) }
        DetailsTab.CHARACTERS -> charactersItems(state, actions)
        DetailsTab.RELATED -> item(key = "related", contentType = "related") { RelatedTab(media, state, actions) }
        DetailsTab.REVIEWS -> item(key = "reviews", contentType = "reviews") { ReviewsTab(media) }
    }
}

/** SOC-04: AniList reviews in their own tab (write, read more, open in AniList, load more live in the social slot). */
@Composable
internal fun ReviewsTab(media: Media) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.rd_s4_reviews_title), Modifier.padding(horizontal = 16.dp))
        if (rememberSocialAvailable()) {
            AniListSocialSlot(media)
        } else {
            EmptyState(
                icon = Icons.Outlined.Movie,
                title = stringResource(Res.string.rd_s4_reviews_unavailable_title),
                message = stringResource(Res.string.rd_s4_reviews_unavailable),
                modifier = Modifier.decor(DecorRegion.EMPTY),
            )
        }
    }
}

// ---------------------------------------------------------------- episodes

private fun LazyListScope.fichaEpisodeItems(
    media: Media,
    state: DetailsUiState,
    actions: DetailsActions,
    ui: FichaUi,
    lists: EpisodeLists?,
    cardColumns: Int,
    route: Boolean,
) {
    when (val eps = state.episodes) {
        LoadState.Loading -> item(key = "episodes-loading", contentType = "loading") { DelayedLoading() }
        is LoadState.Err -> item(key = "episodes-error", contentType = "error") {
            ErrorState(message = eps.cause.userMessage(), retryLabel = stringResource(UiR.string.ui_retry), onRetry = actions.onRetry, compact = true)
        }
        is LoadState.Ok -> {
            if (lists == null || lists.all.isEmpty()) {
                item(key = "episodes-empty", contentType = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Movie,
                        title = stringResource(Res.string.details_no_episodes_title),
                        message = stringResource(Res.string.details_no_episodes),
                        modifier = Modifier.decor(DecorRegion.EMPTY),
                    )
                }
                return
            }
            item(key = "episode-toolbar", contentType = "toolbar") { EpisodeToolbar(state, actions, ui, route) }
            val page = episodePage(lists, state, ui.range, ui.showEarlier)
            if (page.items.isEmpty()) {
                item(key = "episodes-no-match", contentType = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = stringResource(Res.string.details_episode_search_empty_title),
                        message = stringResource(Res.string.details_episode_search_empty, state.episodeQuery.trim()),
                        actionLabel = stringResource(Res.string.details_episode_search_clear),
                        onAction = { actions.onEpisodeQuery("") },
                        modifier = Modifier.decor(DecorRegion.EMPTY),
                    )
                }
                return
            }
            if (page.ranges.isNotEmpty()) {
                item(key = "ranges", contentType = "ranges") { RangeChips(page, onRange = { ui.range = it }) }
            }
            if (page.hiddenFrom != null && page.hiddenTo != null) {
                item(key = "episodes-earlier", contentType = "earlier") { EarlierEpisodesRow(page.hiddenFrom, page.hiddenTo) { ui.showEarlier = true } }
            }
            val aired = lists.airedNumbers()
            if (route) {
                item(key = "route", contentType = "route") { RouteStations(page.items, state, actions, ui, aired) }
                item(key = "route-detail", contentType = "route-detail") { RouteDetail(media, page.items, state, actions, ui) }
                return
            }
            val fallbackArt = state.artwork?.fanart ?: media.banner ?: media.cover
            val columns = when (state.episodeView) {
                EpisodeView.LIST -> 1
                EpisodeView.CARDS -> cardColumns.coerceAtLeast(1)
                EpisodeView.COMPACT -> (cardColumns + 1) * 2
            }
            page.items.chunked(columns).forEach { row ->
                item(key = "ep-${state.episodeView}-${row.first().episode.number}", contentType = "episode-${state.episodeView}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .decor(DecorRegion.EPISODES)
                            .selectionKeys(ui.selection) { aired }
                            .padding(horizontal = 16.dp, vertical = if (state.episodeView == EpisodeView.LIST) 3.dp else 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (state.episodeView == EpisodeView.COMPACT) 8.dp else 12.dp),
                    ) {
                        row.forEach { item ->
                            FichaEpisode(item, state, actions, ui, aired, fallbackArt, Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * Search, the view (Lista / Tarjetas / Rejilla), "Seleccionar" on wide windows and how selecting works. Under [TOOLBAR_ONE_ROW] (the
 * Cine drawer, a narrow pane) the search takes its own row, so neither it nor "Seleccionar" is squeezed to a sliver.
 */
@Composable
private fun EpisodeToolbar(state: DetailsUiState, actions: DetailsActions, ui: FichaUi, route: Boolean) {
    val compact = isCompactWindow()
    val search: @Composable (Modifier) -> Unit = { m ->
        OutlinedTextField(
            value = state.episodeQuery,
            onValueChange = actions.onEpisodeQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (state.episodeQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { actions.onEpisodeQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.details_episode_search_clear))
                    }
                }
            } else {
                null
            },
            placeholder = {
                Text(
                    stringResource(if (compact) Res.string.details_episode_search else Res.string.rd_s4_search_wide),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = m,
        )
    }
    val views: @Composable () -> Unit = {
        Segmented(
            options = listOf(
                SegmentedOption(EpisodeView.LIST, stringResource(Res.string.rd_s4_view_list), icon = Icons.AutoMirrored.Outlined.ViewList),
                SegmentedOption(EpisodeView.CARDS, stringResource(Res.string.rd_s4_view_cards), icon = Icons.Outlined.ViewAgenda),
                SegmentedOption(EpisodeView.COMPACT, stringResource(Res.string.rd_s4_view_grid), icon = Icons.Outlined.GridView),
            ),
            selected = state.episodeView,
            onSelect = actions.onEpisodeView,
            small = true,
            iconOnly = compact,
        )
    }
    val select: @Composable () -> Unit = {
        if (!compact) {
            val selecting = ui.selection.active
            OutlinedButton(onClick = { if (selecting) ui.selection.exit() else ui.selection.start() }, shape = NijiyomeControls.shape) {
                Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (selecting) Res.string.rd_s4_select_done else Res.string.rd_s4_select),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // phones keep one row: their view switch is icons only
        val oneRow = compact || maxWidth >= TOOLBAR_ONE_ROW
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (route) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(if (compact) Res.string.rd_s4_route_hint_touch else Res.string.rd_s4_route_hint_wide),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    select()
                }
            } else if (oneRow) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    search(Modifier.weight(1f))
                    views()
                    select()
                }
            } else {
                search(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { views() }
                    select()
                }
            }
            if (!route) {
                Text(
                    stringResource(if (compact) Res.string.rd_s4_hint_touch else Res.string.rd_s4_hint_wide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Width from which search, views and "Seleccionar" share one row. */
private val TOOLBAR_ONE_ROW = 600.dp

@Composable
private fun RangeChips(page: EpisodePage, onRange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        page.ranges.forEachIndexed { i, r ->
            FilterChip(selected = i == page.selectedRange, onClick = { onRange(i) }, label = { Text(stringResource(Res.string.details_range, r.first, r.last)) })
        }
    }
}

/** The text of an episode's tag: when it airs, "Visto", "Siguiente", or nothing. */
@Composable
private fun episodeTag(item: EpisodeItem, state: DetailsUiState, watched: Boolean, current: Boolean): String? {
    val n = item.episode.number
    return when {
        item.unaired -> {
            val next = state.detailsOrNull?.nextAiring?.takeIf { it.episode == n }
            val left = next?.let { countdown(it.airingAtEpochSec, state.clockSec ?: (System.currentTimeMillis() / 1000)) }
            when {
                left != null -> stringResource(Res.string.rd_s4_tag_airs, whenText(left))
                item.episode.airDate != null -> stringResource(Res.string.details_airs_on, formatAirDate(item.episode.airDate!!))
                else -> stringResource(Res.string.rd_s4_tag_no_date)
            }
        }
        watched -> stringResource(Res.string.rd_s4_tag_watched)
        current -> stringResource(Res.string.rd_s4_tag_next)
        else -> null
    }
}

/** The menu of one episode (right click, Shift+F10, ⋮ and, on a phone, ⋮ as a sheet). */
@Composable
private fun episodeMenu(item: EpisodeItem, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, aired: List<Int>): () -> List<MenuEntry> {
    val n = item.episode.number
    val watched = state.isWatched(n)
    val play = stringResource(Res.string.rd_s4_ep_play, n)
    val mark = stringResource(if (watched) Res.string.details_mark_unwatched else Res.string.details_mark_watched, n)
    val download = stringResource(Res.string.details_download_episode, n)
    val sources = stringResource(Res.string.rd_s4_ep_sources)
    val select = stringResource(Res.string.rd_s4_ep_select)
    val selectAll = stringResource(Res.string.rd_s4_select_aired, aired.size)
    return {
        buildList {
            add(MenuAction(play, icon = Icons.Filled.PlayArrow, enabled = !item.unaired, onClick = { actions.onEpisodeClick(n) }))
            if (state.panel?.episode == n) add(MenuAction(sources, icon = Icons.AutoMirrored.Outlined.ViewList, onClick = { actions.onOpenPicker(false) }))
            actions.onToggleWatched?.let { toggle ->
                add(MenuAction(mark, icon = if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, enabled = !item.unaired, onClick = { toggle(n) }))
            }
            add(MenuAction(download, icon = Icons.Outlined.Download, enabled = !item.unaired && n !in state.downloaded, onClick = { actions.onDownloadEpisode(n) }))
            add(MenuDivider)
            add(MenuAction(select, icon = Icons.Outlined.CheckBox, enabled = !item.unaired, onClick = { ui.selection.toggle(n) }))
            add(MenuAction(selectAll, icon = Icons.Outlined.SelectAll, enabled = aired.isNotEmpty(), onClick = { ui.selection.selectAll(aired) }))
        }
    }
}

/** One episode in the current view, with the selection, the menu and the watched mark. */
@Composable
private fun FichaEpisode(
    item: EpisodeItem,
    state: DetailsUiState,
    actions: DetailsActions,
    ui: FichaUi,
    aired: List<Int>,
    fallbackArt: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val compact = isCompactWindow()
    val episode = item.episode
    val n = episode.number
    val watched = state.isWatched(n)
    val current = n == state.currentEpisode()
    val history = state.progress[n]
    val fraction = history?.takeIf { !it.completed && !watched }?.progressFraction()
    val selection = ui.selection
    val selected = selection.isSelected(n)
    val markState = episodeMarkState(item, watched, inProgress = current || fraction != null)
    val title = episode.title?.takeIf { it.isNotBlank() }
    val heading = if (title != null) stringResource(Res.string.details_episode_title, n, title) else stringResource(UiR.string.ui_episode_number, n)
    val tag = episodeTag(item, state, watched, current)
    val resume = state.resumeEntry?.takeIf { current }
    val flags = buildList {
        if (episode.isFiller) add(stringResource(Res.string.details_filler))
        if (n in state.downloaded) add(stringResource(Res.string.details_downloaded))
    }
    val meta = if (resume != null) {
        stringResource(Res.string.details_resume_at, formatDuration(resume.progressSec.toLong()))
    } else {
        (flags + listOfNotNull(episodeMeta(episode, withRating = true))).joinToString(" · ").takeIf { it.isNotEmpty() }
    }
    val entries = episodeMenu(item, state, actions, ui, aired)
    val view = state.episodeView
    val container = when {
        selected -> scheme.secondaryContainer
        current -> scheme.surfaceContainerHigh
        else -> scheme.surfaceContainerLow
    }
    ContextMenuArea(entries = entries, modifier = modifier, sheetHeader = { MenuSheetHeader(heading) }) { _ ->
        Surface(
            color = container,
            contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (current && !selected) Modifier.border(1.dp, scheme.primary, MaterialTheme.shapes.medium) else Modifier)
                .clip(MaterialTheme.shapes.medium)
                .multiSelectable(selection, n, order = { aired }, onOpen = { actions.onEpisodeClick(n) }, enabled = !item.unaired),
        ) {
            val mark: @Composable () -> Unit = {
                WatchedTransition(watched) { w ->
                    EpisodeMark(if (w) EpisodeMarkState.WATCHED else markState.takeIf { it != EpisodeMarkState.WATCHED } ?: EpisodeMarkState.AIRED, number = n)
                }
            }
            val side: @Composable () -> Unit = {
                EpisodeSide(item, state, actions, tag, watched, current, compact, entries)
            }
            when (view) {
                EpisodeView.LIST -> Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SelectionCheckbox(selected, visible = selection.active && !item.unaired)
                    mark()
                    if (!compact) EpisodeThumb(episode, fallbackArt, Modifier.width(112.dp), numbered = false)
                    EpisodeText(heading, meta, fraction, Modifier.weight(1f))
                    side()
                }
                EpisodeView.CARDS -> Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        EpisodeThumb(episode, fallbackArt, Modifier.fillMaxWidth())
                        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SelectionCheckbox(selected, visible = selection.active && !item.unaired)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        mark()
                        EpisodeText(heading, meta, fraction, Modifier.weight(1f), lines = 2)
                    }
                    episode.summary?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        side()
                    }
                }
                EpisodeView.COMPACT -> Column(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(8.dp).semantics { contentDescription = listOfNotNull(heading, tag).joinToString(", ") },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { mark() }
                    Text(
                        title ?: tag.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    SelectionCheckbox(selected, visible = selection.active && !item.unaired)
                }
            }
        }
    }
}

@Composable
private fun EpisodeText(heading: String, meta: String?, fraction: Float?, modifier: Modifier = Modifier, lines: Int = 1) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall, maxLines = lines, overflow = TextOverflow.Ellipsis)
        meta?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (fraction != null) {
            app.nijiyome.designsystem.direction.ProgressTrack(progress = fraction, modifier = Modifier.widthIn(max = 160.dp).padding(top = 4.dp))
        }
    }
}

/** The end of a row: the tag, then mark watched and download (wide) or ⋮ (phones, a sheet with the same actions). */
@Composable
private fun EpisodeSide(
    item: EpisodeItem,
    state: DetailsUiState,
    actions: DetailsActions,
    tag: String?,
    watched: Boolean,
    current: Boolean,
    compact: Boolean,
    entries: () -> List<MenuEntry>,
) {
    val scheme = MaterialTheme.colorScheme
    val n = item.episode.number
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tag?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 4.dp)) {
                val icon = when {
                    item.unaired -> Icons.Outlined.Schedule
                    watched -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.PlayCircle
                }
                Icon(icon, contentDescription = null, tint = if (item.unaired) scheme.onSurfaceVariant else scheme.primary, modifier = Modifier.size(16.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = if (current || watched) scheme.primary else scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (item.unaired) return@Row
        if (compact) {
            ContextMenuButton(entries = entries, contentDescription = stringResource(Res.string.rd_s4_ep_more, n))
            return@Row
        }
        actions.onToggleWatched?.let { toggle ->
            IconButton(onClick = { toggle(n) }) {
                Icon(
                    if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (watched) Res.string.details_mark_unwatched else Res.string.details_mark_watched, n),
                )
            }
        }
        val done = n in state.downloaded
        IconButton(onClick = { actions.onDownloadEpisode(n) }, enabled = !done) {
            Icon(
                if (done) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                contentDescription = stringResource(if (done) Res.string.rd_s4_ep_downloaded else Res.string.details_download_episode, n),
            )
        }
    }
}

// ---------------------------------------------------------------- Ruta

/** Ruta: the episodes as stations of one line, watched / next / to see / not aired; a station opens its detail below. */
@Composable
private fun RouteStations(stations: List<EpisodeItem>, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, aired: List<Int>) {
    val scheme = MaterialTheme.colorScheme
    val focus = ui.focusEpisode ?: state.continueEpisode
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = stations.indexOfFirst { it.episode.number == focus }.coerceAtLeast(0))
    val line = scheme.outline
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().decor(DecorRegion.EPISODES).selectionKeys(ui.selection) { aired },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(stations, key = { "st-${it.episode.number}" }) { item ->
            val n = item.episode.number
            val watched = state.isWatched(n)
            val current = n == state.currentEpisode()
            val selected = ui.selection.isSelected(n)
            val stateWord = stringResource(
                when {
                    item.unaired -> Res.string.rd_s4_state_unaired
                    watched -> Res.string.rd_s4_state_watched
                    current -> Res.string.rd_s4_state_next
                    else -> Res.string.rd_s4_state_unwatched
                },
            )
            val description = stringResource(Res.string.rd_s4_station_cd, n, stateWord)
            val tag = episodeTag(item, state, watched, current)
            val markState = episodeMarkState(item, watched, inProgress = current)
            val focused = n == focus
            Column(
                Modifier
                    .width(120.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (selected) scheme.secondaryContainer else if (focused) scheme.surfaceContainerHigh else scheme.surface.copy(alpha = 0f))
                    .then(if (focused) Modifier.border(1.dp, scheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    .multiSelectable(ui.selection, n, order = { aired }, onOpen = { ui.focusEpisode = n }, enabled = !item.unaired, onDisabledClick = { ui.focusEpisode = n })
                    .semantics { contentDescription = description }
                    .drawBehind {
                        // the line between stations, through the middle of the mark; dashed where the episodes have not aired
                        val y = 46.dp.toPx()
                        drawLine(
                            line,
                            Offset(0f, y),
                            Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = if (item.unaired) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())) else null,
                        )
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    SelectionCheckbox(selected, visible = ui.selection.active && !item.unaired)
                }
                WatchedTransition(watched) { w -> EpisodeMark(if (w) EpisodeMarkState.WATCHED else markState, number = n) }
                Text(stringResource(Res.string.rd_s4_station_ep, n), style = MaterialTheme.typography.labelLarge)
                Text(
                    tag ?: episodeMeta(item.episode, withRating = false).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2, // "Hace 1 mes · 24 min" in full (ALT-09)
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Ruta: the focused station in full: art, title, resume point, play, sources, mark watched, download and the source line. */
@Composable
private fun RouteDetail(media: Media, items: List<EpisodeItem>, state: DetailsUiState, actions: DetailsActions, ui: FichaUi) {
    val focus = ui.focusEpisode ?: state.continueEpisode
    val item = items.firstOrNull { it.episode.number == focus } ?: items.firstOrNull() ?: return
    val episode = item.episode
    val n = episode.number
    val watched = state.isWatched(n)
    val current = n == state.currentEpisode()
    val compact = isCompactWindow()
    val scheme = MaterialTheme.colorScheme
    val fallbackArt = state.artwork?.fanart ?: media.banner ?: media.cover
    val total = state.totalEpisodes
    val kicker = listOfNotNull(
        if (total != null) stringResource(Res.string.rd_s4_route_ep_of, n, total) else stringResource(Res.string.rd_s4_route_ep, n),
        if (item.unaired) episodeTag(item, state, watched, current) else episodeMeta(episode, withRating = false),
    ).joinToString(" · ")
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        val body: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kicker, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(
                    episode.title?.takeIf { it.isNotBlank() } ?: stringResource(UiR.string.ui_episode_number, n),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val resume = state.resumeEntry?.takeIf { current }
                resume?.progressFraction()?.let { app.nijiyome.designsystem.direction.ProgressTrack(progress = it) }
                resume?.let {
                    Text(stringResource(Res.string.details_resume_at, formatDuration(it.progressSec.toLong())), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (item.unaired) {
                    Text(stringResource(Res.string.rd_s4_not_aired_yet), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                } else {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { actions.onEpisodeClick(n) }, shape = NijiyomeControls.shape) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(if (current) Res.string.details_continue_short else Res.string.rd_s4_play), modifier = Modifier.padding(start = 8.dp))
                        }
                        if (state.panel?.episode == n) {
                            FilledTonalButton(onClick = { actions.onOpenPicker(false) }, shape = NijiyomeControls.shape) {
                                Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(Res.string.rd_s4_ep_sources), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        actions.onToggleWatched?.let { toggle ->
                            OutlinedButton(onClick = { toggle(n) }, shape = NijiyomeControls.shape) {
                                Icon(if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(if (watched) Res.string.rd_s4_mark_unwatched else Res.string.rd_s4_mark_watched), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        val done = n in state.downloaded
                        OutlinedButton(onClick = { actions.onDownloadEpisode(n) }, enabled = !done, shape = NijiyomeControls.shape) {
                            Icon(if (done) Icons.Outlined.DownloadDone else Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(if (done) Res.string.rd_s4_downloaded else Res.string.rd_s4_action_download), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (state.panel?.episode == n) SourceLine(state, actions, inCard = true)
                }
            }
        }
        if (compact) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EpisodeThumb(episode, fallbackArt, Modifier.fillMaxWidth())
                body(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                EpisodeThumb(episode, fallbackArt, Modifier.width(320.dp))
                body(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- selection bars

/**
 * The batch bar of the episode selection (wide: a floating pill; phones: a bottom toolbar): mark the selected ones watched or not
 * watched (undoable), download them, select every aired one, leave.
 */
@Composable
internal fun FichaSelectionBar(
    state: DetailsUiState,
    actions: DetailsActions,
    ui: FichaUi,
    aired: List<Int>,
    modifier: Modifier = Modifier,
    compact: Boolean = isCompactWindow(),
) {
    val selection = ui.selection
    val count = selection.count
    val allWatched = count > 0 && selection.selected.all { state.isWatched(it) }
    val markLabel = stringResource(if (allWatched) Res.string.rd_s4_mark_n_unwatched else Res.string.rd_s4_mark_n_watched, count)
    val downloadLabel = stringResource(Res.string.rd_s4_download_n, count)
    val list = if (count == 0) {
        emptyList()
    } else {
        buildList {
            actions.onMarkEpisodes?.let { mark ->
                add(
                    SelectionAction(markLabel, if (allWatched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, onClick = {
                        mark(selection.selected, !allWatched)
                        selection.exit()
                    }, emphasis = ActionEmphasis.FILLED),
                )
            }
            add(
                SelectionAction(downloadLabel, Icons.Outlined.Download, onClick = {
                    actions.onDownloadEpisodes(selection.selected)
                    selection.exit()
                }),
            )
        }
    }
    SelectionActionBar(
        visible = selection.active,
        count = count,
        actions = list,
        onExit = selection::exit,
        modifier = modifier,
        onSelectAll = { selection.selectAll(aired) },
        selectAllLabel = stringResource(Res.string.rd_s4_select_aired, aired.size),
        emptyHint = stringResource(if (compact) Res.string.rd_s4_select_empty_touch else Res.string.rd_s4_select_empty_wide),
        compact = compact,
    )
}

/** Phones: while selecting, this bar replaces the Ficha's top bar (count, leave, span, all, unwatched). */
@Composable
internal fun FichaSelectionTopBar(state: DetailsUiState, ui: FichaUi, aired: List<Int>, modifier: Modifier = Modifier) {
    val selection = ui.selection
    SelectionTopBar(
        count = selection.count,
        onExit = selection::exit,
        modifier = modifier.statusBarsPadding(),
        onSelectSpan = { selection.fillSpan(aired) },
        onSelectAll = { selection.selectAll(aired) },
        onSelectUnwatched = { selection.selectWhere(aired) { !state.isWatched(it) } },
    )
}
