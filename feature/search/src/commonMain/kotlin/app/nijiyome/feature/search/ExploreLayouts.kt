// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.feature.search.resources.Res
import app.nijiyome.feature.search.resources.*
import app.nijiyome.model.FeedKind
import app.nijiyome.model.LoadState
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.media.mediaKey
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.platform.windowWidthDp
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_retry
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.state.userMessage
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles Explorar: the two structures of `structures.explore`. Same state, view model and actions; only the arrangement differs.

/** What both structures get besides the state and actions. */
internal class ExploreFrame(
    val tab: ExploreTab,
    val onTab: (ExploreTab) -> Unit,
    val queryFocus: FocusRequester,
    val onRestoreHistory: (List<String>) -> Unit,
    /** The list is reachable: the "Mi lista" facet is offered. */
    val withMyList: Boolean,
)

/** Wide enough to keep the scope switch on the search field's line (the artifact's desktop line). */
private const val ONE_LINE_SEARCH_DP = 1100f

/**
 * Facetas: the search on top (field, "Qué buscar", the extension catalogues, the facet chips), then recents, feeds and the trend
 * window while nothing is typed, then the results grid with its count and the grid / list switch.
 */
@Composable
internal fun ExploreFacetsLayout(state: SearchUiState, actions: ExploreActions, slots: ExploreSlots, frame: ExploreFrame, modifier: Modifier = Modifier) {
    val compact = isCompactWindow()
    var listView by rememberSaveable { mutableStateOf(false) }
    val forYou = slots.forYouPanel
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                ExploreHeader(frame.tab, frame.onTab, showTabs = forYou != null, compact = compact)
                if (frame.tab == ExploreTab.SEARCH || forYou == null) {
                    SearchLine(state, actions, frame, compact)
                    ExtensionPicker(state, actions, Modifier.padding(vertical = 4.dp))
                    ExploreFacetBar(state, actions, compact, frame.withMyList, Modifier.padding(vertical = 4.dp))
                }
            }
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding)
        if (frame.tab == ExploreTab.FOR_YOU && forYou != null) {
            forYou({ genre -> browseGenreFromForYou(genre, actions, frame) }, body)
        } else {
            ExploreResultsGrid(
                state = state,
                actions = actions,
                listView = listView && !compact,
                onListView = if (compact) null else ({ v -> listView = v }),
                compact = compact,
                modifier = body,
                header = { discoveryHeader(state, actions, frame, withFeeds = true) },
                idle = { fullSpan("idle") { ExploreIdle(state, actions) } },
            )
        }
    }
}

/**
 * Lienzo: discover without typing. A large search field on top; while it is empty the canvas shows the feeds, the trending row with
 * its time window (Top), the recent searches, Para ti with affinity and the atlas. Typing, a feed or a filter turns the body into
 * the same results as Facetas.
 */
@Composable
internal fun ExploreCanvasLayout(state: SearchUiState, actions: ExploreActions, slots: ExploreSlots, frame: ExploreFrame, modifier: Modifier = Modifier) {
    val compact = isCompactWindow()
    var listView by rememberSaveable { mutableStateOf(false) }
    val forYou = slots.forYouPanel
    val discovery = state.results == SearchResults.Idle && state.scope == ExploreScope.ANIME && state.query.isBlank()
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                ExploreHeader(frame.tab, frame.onTab, showTabs = forYou != null, compact = compact)
                if (frame.tab == ExploreTab.SEARCH || forYou == null) {
                    ExploreSearchField(
                        state,
                        actions,
                        frame.queryFocus,
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        showFilters = compact && !discovery,
                        big = !compact,
                    )
                    ExploreScopeSwitch(state, actions, compact, if (compact) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (!discovery) {
                        ExtensionPicker(state, actions, Modifier.padding(vertical = 4.dp))
                        ExploreFacetBar(state, actions, compact, frame.withMyList, Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
    ) { padding ->
        val body = Modifier.fillMaxSize().padding(padding)
        when {
            frame.tab == ExploreTab.FOR_YOU && forYou != null -> forYou({ genre -> browseGenreFromForYou(genre, actions, frame) }, body)
            discovery -> CanvasSections(state, actions, slots, frame, compact, body)
            else -> ExploreResultsGrid(
                state = state,
                actions = actions,
                listView = listView && !compact,
                onListView = if (compact) null else ({ v -> listView = v }),
                compact = compact,
                modifier = body,
                header = { discoveryHeader(state, actions, frame, withFeeds = true) },
                idle = { fullSpan("idle") { ExploreIdle(state, actions) } },
            )
        }
    }
}

private fun browseGenreFromForYou(genre: String, actions: ExploreActions, frame: ExploreFrame) {
    actions.onBrowseGenre(genre)
    frame.onTab(ExploreTab.SEARCH)
}

/** The field and "Qué buscar": one line on very wide windows, two lines otherwise; tabs under the field on phones. */
@Composable
private fun SearchLine(state: SearchUiState, actions: ExploreActions, frame: ExploreFrame, compact: Boolean) {
    when {
        compact -> {
            ExploreSearchField(state, actions, frame.queryFocus, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), showFilters = true)
            ExploreScopeSwitch(state, actions, compact = true)
        }
        windowWidthDp() >= ONE_LINE_SEARCH_DP -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExploreSearchField(state, actions, frame.queryFocus, Modifier.weight(1f))
            ExploreScopeSwitch(state, actions, compact = false)
        }
        else -> {
            ExploreSearchField(state, actions, frame.queryFocus, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            ExploreScopeSwitch(state, actions, compact = false, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

/** While nothing is typed in the anime scope: recent searches, the feeds and, for Tendencias, the time window (SRCH-03 / SRCH-04). */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.discoveryHeader(
    state: SearchUiState,
    actions: ExploreActions,
    frame: ExploreFrame,
    withFeeds: Boolean,
) {
    if (state.scope != ExploreScope.ANIME || state.query.isNotBlank()) return
    if (state.history.isNotEmpty()) fullSpan("recents") { RecentSearches(state, actions, frame.onRestoreHistory) }
    if (withFeeds) {
        fullSpan("feeds") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.search_explore),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FeedChips(state, actions)
            }
        }
    }
    if (state.feed == FeedKind.TRENDING) {
        fullSpan("window") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.rd_s3_trend_of),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TrendWindowSwitch(state, actions)
            }
        }
    }
}

/** The canvas: feeds, trending row (Top), recents, Para ti and the atlas (side by side on wide windows). */
@Composable
private fun CanvasSections(
    state: SearchUiState,
    actions: ExploreActions,
    slots: ExploreSlots,
    frame: ExploreFrame,
    compact: Boolean,
    modifier: Modifier,
) {
    val preview = slots.forYouPreview
    val atlas = slots.tasteAtlas
    val onSeeAll: () -> Unit = { if (slots.forYouPanel != null) frame.onTab(ExploreTab.FOR_YOU) }
    LazyColumn(modifier, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "feeds") { FeedChips(state, actions, Modifier.padding(horizontal = 16.dp)) }
        item(key = "trending") { TrendingSection(state, actions) }
        if (state.history.isNotEmpty()) {
            item(key = "recents") { RecentSearches(state, actions, frame.onRestoreHistory, Modifier.padding(horizontal = 16.dp)) }
        }
        if (preview != null || atlas != null) {
            item(key = "taste") {
                if (compact) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        preview?.invoke(onSeeAll, actions.onBrowseGenre, Modifier.fillMaxWidth())
                        atlas?.invoke(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        preview?.invoke(onSeeAll, actions.onBrowseGenre, Modifier.weight(1f))
                        atlas?.invoke(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Top in the canvas: "En tendencia" with its time window and "Ver todo" (the Tendencias feed in the results). */
@Composable
private fun TrendingSection(state: SearchUiState, actions: ExploreActions) {
    LaunchedEffect(Unit) { actions.onEnsureTrendingRow() }
    val revealed = remember { mutableStateListOf<String>() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            stringResource(Res.string.search_feed_trending),
            Modifier.padding(horizontal = 16.dp),
            trailing = { TextButton(onClick = { actions.onSelectFeed(FeedKind.TRENDING) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s3_see_all)) } },
        )
        TrendWindowSwitch(state, actions, Modifier.padding(horizontal = 16.dp))
        when (val row = state.trendingRow) {
            null, LoadState.Loading -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(6) { LoadingPlaceholder(Modifier.width(120.dp).aspectRatio(0.62f)) }
            }
            is LoadState.Err -> ErrorState(
                message = row.cause.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = actions.onRetryTrendingRow,
                compact = true,
            )
            is LoadState.Ok -> if (row.value.isEmpty()) {
                Text(
                    stringResource(Res.string.rd_s3_trending_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                LazyRow(
                    Modifier.decor(DecorRegion.ROW),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(row.value, key = { _, m -> "t-" + mediaKey(m) }) { i, media ->
                        RankedCard(i + 1) { ResultCard(media, state, revealed, actions, CardContext.ROW, Modifier.cardSway(i), index = i) }
                    }
                }
            }
        }
    }
}
