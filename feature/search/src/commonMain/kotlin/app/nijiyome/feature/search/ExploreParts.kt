// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.feature.search.resources.Res
import app.nijiyome.feature.search.resources.*
import app.nijiyome.metadata.TrendWindow
import app.nijiyome.model.AppError
import app.nijiyome.model.CharacterRef
import app.nijiyome.model.ExtensionHomeItem
import app.nijiyome.model.FeedKind
import app.nijiyome.model.ListStatus
import app.nijiyome.model.Media
import app.nijiyome.ui.look.motionListPlacement
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.toCardData
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.mediaKey
import app.nijiyome.ui.patterns.ActionButton
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.ContextMenuArea
import app.nijiyome.ui.patterns.FacetBar
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.patterns.MenuSheetHeader
import app.nijiyome.ui.patterns.label
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.nav_manga
import app.nijiyome.ui.resources.nav_novels
import app.nijiyome.ui.resources.ui_error_title
import app.nijiyome.ui.resources.ui_retry
import app.nijiyome.ui.state.userMessage
import java.util.Locale
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.upscale.UpscaleImageKind
import app.nijiyome.designsystem.scroll.NjLazyVerticalGrid

// Sparkles Explorar (lane S3): the pieces both structures (Facetas, Lienzo) compose. They read the shared SearchUiState and call the
// shared ExploreActions; only the arrangement changes between structures.

/** Desktop shows the keyboard hint of the search field (Ctrl+F here, Ctrl+K anywhere). */
internal expect val exploreKeyboardHints: Boolean

/** What Explorar shows: the search (results, feeds, filters) or the full Para ti page. */
enum class ExploreTab { SEARCH, FOR_YOU }

/** Every action of Explorar, shared by both structures. */
internal data class ExploreActions(
    val onQueryChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onMediaClick: (Media) -> Unit = {},
    val onCharacterClick: (CharacterRef) -> Unit = {},
    val onExtensionItemClick: (ExtensionHomeItem) -> Unit = {},
    val onUseHistory: (String) -> Unit = {},
    val onRemoveHistory: (String) -> Unit = {},
    val onClearHistory: () -> Unit = {},
    val onFiltersChange: (SearchFilters) -> Unit = {},
    val onEnsureTags: () -> Unit = {},
    val onOpenAllFilters: () -> Unit = {},
    val onSelectFeed: (FeedKind) -> Unit = {},
    val onClearFeed: () -> Unit = {},
    val onTrendWindow: (TrendWindow) -> Unit = {},
    val onClearStudio: () -> Unit = {},
    val onScope: (ExploreScope) -> Unit = {},
    val onExtension: (String?) -> Unit = {},
    val onSetAdult: (Boolean) -> Unit = {},
    val onMyList: (Set<String>) -> Unit = {},
    val onResetAll: () -> Unit = {},
    val onBrowseGenre: (String) -> Unit = {},
    val onOpenImageSearch: () -> Unit = {},
    val onOpenExtensions: () -> Unit = {},
    /** Opens the list status picker of a result; null = the list is not reachable here. */
    val onEditStatus: ((Media) -> Unit)? = null,
    /** Puts a result straight into Planning; null = the list is not reachable here. */
    val onQuickAdd: ((Media) -> Unit)? = null,
    val onEnsureTrendingRow: () -> Unit = {},
    val onRetryTrendingRow: () -> Unit = {},
)

/** The Para ti parts Explorar shows, given by the shell from `:feature:home` (this module does not depend on it). */
internal class ExploreSlots(
    val forYouPanel: (@Composable (onGenre: (String) -> Unit, modifier: Modifier) -> Unit)?,
    val forYouPreview: (@Composable (onSeeAll: () -> Unit, onGenre: (String) -> Unit, modifier: Modifier) -> Unit)?,
    val tasteAtlas: (@Composable (modifier: Modifier) -> Unit)?,
)

// ---------------------------------------------------------------------------------------------------------------------------------
// Header, search field, scopes

/** "Explorar" title (wide windows), the keyboard hint and the Buscar / Para ti switch. */
@Composable
internal fun ExploreHeader(tab: ExploreTab, onTab: (ExploreTab) -> Unit, showTabs: Boolean, compact: Boolean) {
    if (compact && !showTabs) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!compact) {
            Text(
                stringResource(Res.string.rd_s3_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        Box(Modifier.weight(1f))
        if (!compact && exploreKeyboardHints) {
            // EXP-03: a quiet hint (regular body text with a keyboard icon), lighter than the content.
            Row(
                Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(Res.string.rd_s3_keys_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showTabs) {
            Segmented(
                options = listOf(
                    SegmentedOption(ExploreTab.SEARCH, stringResource(Res.string.rd_s3_tab_search), icon = Icons.Outlined.Search),
                    SegmentedOption(ExploreTab.FOR_YOU, stringResource(Res.string.rd_s3_tab_for_you), icon = Icons.Outlined.AutoAwesome),
                ),
                selected = tab,
                onSelect = onTab,
                small = true,
            )
        }
    }
}

/** The query field: live search, Enter submits (and records it), clear, image search and, on phones, the whole filters sheet. */
@Composable
internal fun ExploreSearchField(
    state: SearchUiState,
    actions: ExploreActions,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
    showFilters: Boolean = false,
    big: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val extensionName = state.extensions.firstOrNull { it.extension.id == state.extensionId }?.extension?.name
    val placeholder = when {
        state.scope == ExploreScope.CHARACTERS -> stringResource(Res.string.search_hint_characters)
        state.scope == ExploreScope.EXTENSIONS && extensionName != null -> stringResource(Res.string.rd_s3_hint_extension, extensionName)
        else -> stringResource(Res.string.search_hint)
    }
    TextField(
        value = state.query,
        onValueChange = actions.onQueryChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = actions.onClear) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.search_clear))
                    }
                }
                IconButton(onClick = actions.onOpenImageSearch) {
                    Icon(Icons.Outlined.ImageSearch, contentDescription = stringResource(Res.string.search_image_open))
                }
                if (showFilters && state.scope == ExploreScope.ANIME) {
                    val active = state.narrowingCount
                    val label = if (active > 0) stringResource(Res.string.search_filters_active, active) else stringResource(Res.string.search_filters)
                    IconButton(onClick = actions.onOpenAllFilters) {
                        BadgedBox(badge = { if (active > 0) Badge { Text(active.toString()) } }) {
                            Icon(Icons.Outlined.Tune, contentDescription = label)
                        }
                    }
                }
            }
        },
        shape = if (big) MaterialTheme.shapes.large else CircleShape,
        textStyle = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            disabledIndicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            actions.onSubmit()
            focusManager.clearFocus()
        }),
        modifier = modifier.focusRequester(focus),
    )
}

private data class ScopeChoice(val scope: ExploreScope, val label: String, val icon: ImageVector)

@Composable
private fun scopeChoices(compact: Boolean): List<ScopeChoice> = listOf(
    ScopeChoice(ExploreScope.ANIME, stringResource(Res.string.search_type_anime), Icons.Outlined.Movie),
    ScopeChoice(ExploreScope.CHARACTERS, stringResource(Res.string.search_type_characters), Icons.Outlined.Person),
    ScopeChoice(ExploreScope.MANGA, stringResource(UiR.string.nav_manga), Icons.AutoMirrored.Filled.MenuBook),
    ScopeChoice(ExploreScope.NOVELS, stringResource(UiR.string.nav_novels), Icons.Filled.AutoStories),
    ScopeChoice(
        ExploreScope.EXTENSIONS,
        stringResource(if (compact) Res.string.rd_s3_scope_extensions_short else Res.string.rd_s3_scope_extensions),
        Icons.Outlined.Extension,
    ),
)

/**
 * "Qué buscar", apart from the filters (SRCH-02): anime, characters, manga, novels (these two open their own homes with the query)
 * and the extensions' catalogues. Tabs on phones, a segmented button on wide windows.
 */
@Composable
internal fun ExploreScopeSwitch(state: SearchUiState, actions: ExploreActions, compact: Boolean, modifier: Modifier = Modifier) {
    val choices = scopeChoices(compact)
    if (compact) {
        PrimaryScrollableTabRow(selectedTabIndex = choices.indexOfFirst { it.scope == state.scope }.coerceAtLeast(0), modifier = modifier, edgePadding = 16.dp) {
            choices.forEach { c ->
                Tab(selected = c.scope == state.scope, onClick = { actions.onScope(c.scope) }, text = { Text(c.label, maxLines = 1) })
            }
        }
    } else {
        // EXP-02: the segments share the width of the widest one, measured by its longest word, so "Catálogo de extensión" was cut;
        // non-breaking spaces make every label measure whole. Compact labels, as in the artifact.
        Segmented(
            options = choices.map { SegmentedOption(it.scope, it.label.replace(' ', ' '), icon = it.icon) },
            selected = state.scope,
            onSelect = actions.onScope,
            modifier = modifier,
            small = true,
        )
    }
}

/** The extension catalogues to search in (scope Extensiones); one without its own catalogue is disabled and says why. */
@Composable
internal fun ExtensionPicker(state: SearchUiState, actions: ExploreActions, modifier: Modifier = Modifier) {
    if (state.scope != ExploreScope.EXTENSIONS || state.extensions.isEmpty()) return
    val snackbar = LocalAppSnackbar.current
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.extensions.forEach { chip ->
            val ext = chip.extension
            if (chip.searchable) {
                FilterChip(
                    selected = state.extensionId == ext.id,
                    onClick = { actions.onExtension(ext.id) },
                    label = { Text(ext.name) },
                    leadingIcon = { Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            } else {
                val reason = stringResource(Res.string.search_extension_not_searchable, ext.name)
                Box {
                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = { Text(ext.name) },
                        leadingIcon = { Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.semantics { stateDescription = reason },
                    )
                    Box(Modifier.matchParentSize().clickable { snackbar.show(reason) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Facets

/**
 * The filters as facets (SRCH-09): format, status, genre, year, season, "Mi lista" and sort as chips with their own panel (pattern
 * `fac`), the studio being browsed, and on wide windows "Más filtros" (ranges and tags) and the 18+ chip. Phones get the rest from
 * the whole filters sheet behind the search field's filter button.
 */
@Composable
internal fun ExploreFacetBar(state: SearchUiState, actions: ExploreActions, compact: Boolean, withMyList: Boolean, modifier: Modifier = Modifier) {
    if (state.scope != ExploreScope.ANIME) return
    val facets = exploreFacets(state, withMyList)
    val selection = facetSelectionOf(state.filters, state.myList)
    val count = state.shown.size
    val onSelection: (Map<String, Set<String>>) -> Unit = { sel ->
        val next = state.filters.withFacets(sel)
        if (next != state.filters) actions.onFiltersChange(next)
        val mine = sel[ExploreFacetIds.MY_LIST].orEmpty()
        if (mine != state.myList) actions.onMyList(mine)
    }
    val studio: @Composable () -> Unit = {
        state.studio?.let { RemovableChip(stringResource(Res.string.search_studio_chip, it), actions.onClearStudio) }
    }
    if (compact) {
        Column(modifier) {
            if (state.studio != null) Row(Modifier.padding(horizontal = 16.dp)) { studio() }
            FacetBar(facets, selection, onSelectionChange = onSelection, resultCount = count, compact = true)
        }
    } else {
        FlowRow(
            modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            studio()
            FacetBar(facets, selection, onSelectionChange = onSelection, resultCount = count, compact = false)
            MoreFiltersChip(state, actions)
            AdultChip(state.adult, actions.onSetAdult)
        }
    }
}

/** "Más filtros": year range, episodes, score and tags in a popover; applied on "Listo" (sliders do not search on every step). */
@Composable
private fun MoreFiltersChip(state: SearchUiState, actions: ExploreActions) {
    var open by remember { mutableStateOf(false) }
    val active = state.filters.moreFiltersCount()
    val label = if (active > 0) stringResource(Res.string.rd_s3_more_filters_on, active) else stringResource(Res.string.rd_s3_more_filters)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            FilterChip(
                selected = active > 0,
                onClick = {
                    actions.onEnsureTags()
                    open = !open
                },
                label = { Text(label, maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            if (open) {
                var draft by remember(state.filters) { mutableStateOf(state.filters) }
                DropdownMenu(expanded = true, onDismissRequest = { open = false }) {
                    Column(
                        Modifier.width(380.dp).padding(horizontal = 16.dp, vertical = 8.dp).decor(DecorRegion.SHEET),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RangeAndTagFilters(draft, onDraftChange = { draft = it }, tagOptions = state.tagOptions, tagsLoading = state.tagsLoading)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { draft = draft.withoutMoreFilters() }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.search_filters_clear)) }
                            Box(Modifier.weight(1f))
                            Button(onClick = {
                                if (draft != state.filters) actions.onFiltersChange(draft)
                                open = false
                            }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s3_done)) }
                        }
                    }
                }
            }
        }
        if (active > 0) {
            IconButton(onClick = { actions.onFiltersChange(state.filters.withoutMoreFilters()) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.search_remove_filter, stringResource(Res.string.rd_s3_more_filters)),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Include 18+ titles: a toggle chip (the Details gate still asks before showing an adult title). */
@Composable
private fun AdultChip(adult: Boolean, onChange: (Boolean) -> Unit) {
    val description = stringResource(Res.string.search_adult)
    FilterChip(
        selected = adult,
        onClick = { onChange(!adult) },
        label = { Text(stringResource(Res.string.search_adult_badge)) },
        leadingIcon = if (adult) {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
internal fun RemovableChip(label: String, onRemove: () -> Unit) {
    val description = stringResource(Res.string.search_remove_filter, label)
    FilterChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.semantics { contentDescription = description },
    )
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Feeds, trend window, recent searches

internal val EXPLORE_FEEDS = listOf(
    FeedKind.TRENDING, FeedKind.POPULAR, FeedKind.TOP_RATED, FeedKind.AIRING, FeedKind.SEASONAL, FeedKind.UPCOMING,
    FeedKind.RECENTLY_COMPLETED,
)

private fun FeedKind.icon(): ImageVector = when (this) {
    FeedKind.TRENDING -> Icons.AutoMirrored.Outlined.TrendingUp
    FeedKind.POPULAR -> Icons.Outlined.Groups
    FeedKind.TOP_RATED -> Icons.Outlined.Star
    FeedKind.AIRING -> Icons.Outlined.Sensors
    FeedKind.SEASONAL -> Icons.Outlined.Eco
    FeedKind.UPCOMING -> Icons.Outlined.Event
    FeedKind.RECENTLY_COMPLETED -> Icons.Outlined.TaskAlt
}

/** The seven discovery feeds (SRCH-04); the browsed one stays pressed and a second tap drops it. */
@Composable
internal fun FeedChips(state: SearchUiState, actions: ExploreActions, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EXPLORE_FEEDS.forEach { feed ->
            val on = state.feed == feed && state.query.isBlank()
            FilterChip(
                selected = on,
                onClick = { if (on) actions.onClearFeed() else actions.onSelectFeed(feed) },
                label = { Text(stringResource(feed.labelRes())) },
                leadingIcon = { Icon(feed.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** Top (SRCH-03): today, this week, this month, all time. */
@Composable
internal fun TrendWindowSwitch(state: SearchUiState, actions: ExploreActions, modifier: Modifier = Modifier) {
    Segmented(
        // ALT-05: whole labels ("Esta semana" was cut at its longest word), see ExploreScopeSwitch.
        options = TrendWindow.entries.map { SegmentedOption(it, stringResource(it.labelRes()).replace(' ', ' ')) },
        selected = state.trendWindow,
        onSelect = actions.onTrendWindow,
        small = true,
        modifier = modifier,
    )
}

/** Recent searches (SRCH-04): run one, remove one or all (both with Undo). */
@Composable
internal fun RecentSearches(state: SearchUiState, actions: ExploreActions, onRestore: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    if (state.history.isEmpty()) return
    val snackbar = LocalAppSnackbar.current
    val undo = stringResource(Res.string.rd_s3_undo)
    val cleared = stringResource(Res.string.rd_s3_recent_cleared)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.rd_s3_recent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.history.forEach { entry ->
                val removed = stringResource(Res.string.rd_s3_recent_removed, entry)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { actions.onUseHistory(entry) },
                        label = { Text(entry, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
                        leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    IconButton(onClick = {
                        val before = state.history
                        actions.onRemoveHistory(entry)
                        snackbar.show(removed, undo) { onRestore(before) }
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.search_history_remove, entry), modifier = Modifier.size(18.dp))
                    }
                }
            }
            TextButton(onClick = {
                val before = state.history
                actions.onClearHistory()
                snackbar.show(cleared, undo) { onRestore(before) }
            }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.search_history_clear)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Results

internal fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

/**
 * The scrolling body of a search: [header] (feeds, recents...), the rate-limit notice, then by state the [idle] content, loading
 * cards, the results grid (or list), characters, an extension catalogue, empty and error states, and infinite scroll.
 */
@Composable
internal fun ExploreResultsGrid(
    state: SearchUiState,
    actions: ExploreActions,
    listView: Boolean,
    onListView: ((Boolean) -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    header: LazyGridScope.() -> Unit = {},
    idle: LazyGridScope.() -> Unit = {},
) {
    val revealed = remember { mutableStateListOf<String>() }
    val columns = if (listView) GridCells.Fixed(1) else GridCells.Adaptive(if (compact) 104.dp else 136.dp)
    NjLazyVerticalGrid(
        columns = columns,
        modifier = modifier.decor(DecorRegion.GRID),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()
        state.degraded?.let { limited -> fullSpan("degraded") { RateLimitBanner(limited, actions.onRetry) } }
        when (val results = state.results) {
            SearchResults.Idle -> idle()
            SearchResults.Loading -> items(12, key = { "loading-$it" }, contentType = { "loading" }) {
                if (rememberAfterDelay()) {
                    LoadingPlaceholder(Modifier.fillMaxWidth().aspectRatio(if (listView) 6f else 0.62f))
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(if (listView) 6f else 0.62f))
                }
            }
            SearchResults.Empty -> fullSpan("empty") { ExploreEmpty(state, actions) }
            is SearchResults.Err -> fullSpan("error") { ExploreError(results.cause, state, actions) }
            is SearchResults.Ok -> when {
                results.characters.isNotEmpty() -> {
                    fullSpan("count") { ResultsCount(results.characters.size) }
                    items(results.characters, key = { "c-${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "character" }) { c ->
                        CharacterRow(c, onClick = { actions.onCharacterClick(c) })
                    }
                    if (results.hasNext) fullSpan("load-more") { LoadMoreFooter(results, actions.onLoadMore) }
                }
                results.extensionItems.isNotEmpty() -> {
                    fullSpan("count") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ResultsCount(results.extensionItems.size)
                            HintLine(stringResource(Res.string.rd_s3_extension_hint))
                        }
                    }
                    items(results.extensionItems, key = { "x-${it.id}-${it.title}" }, contentType = { "ext" }) { item ->
                        TitleCard(
                            TitleCardData(key = "ext:${item.id}:${item.title}", title = item.title, coverUrl = item.poster),
                            onClick = { actions.onExtensionItemClick(item) },
                            context = if (listView) CardContext.COMPACT else CardContext.GRID,
                        )
                    }
                }
                state.shown.isEmpty() -> fullSpan("empty") { ExploreEmpty(state, actions, filteredOut = true) }
                else -> {
                    fullSpan("count") { ResultsHeader(state.shown.size, state.adult, listView, onListView) }
                    items(state.shown, key = { "m-" + mediaKey(it) }, contentType = { "media" }) { media ->
                        // v1.3.5 list placement: filtering / sorting the shown results glides the cards to their new cells.
                        ResultCard(media, state, revealed, actions, if (listView) CardContext.COMPACT else CardContext.GRID, motionListPlacement())
                    }
                    if (results.hasNext) fullSpan("load-more") { LoadMoreFooter(results, actions.onLoadMore) }
                }
            }
        }
    }
}

@Composable
private fun HintLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultsCount(count: Int, modifier: Modifier = Modifier) {
    Text(
        pluralStringResource(Res.plurals.search_results, count, count),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** "N results", "+18 included" and, on wide windows, the grid / list switch. */
@Composable
private fun ResultsHeader(count: Int, adult: Boolean, listView: Boolean, onListView: ((Boolean) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultsCount(count)
        if (adult) {
            Text(
                stringResource(Res.string.search_adult_on),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1f))
        if (onListView != null) {
            IconToggleButton(checked = !listView, onCheckedChange = { if (it) onListView(false) }) {
                Icon(Icons.Outlined.GridView, contentDescription = stringResource(Res.string.rd_s3_view_grid))
            }
            IconToggleButton(checked = listView, onCheckedChange = { if (it) onListView(true) }) {
                Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = stringResource(Res.string.rd_s3_view_list))
            }
        }
    }
}

/**
 * One anime result as the user's card style (SRCH-05), with the list status when the title is on the list. With mature content set
 * to Blur an adult title shows no cover until the first tap (the next one opens it), as before. Long press or right click opens the
 * title's menu: open, add to Planning, change its status.
 */
@Composable
internal fun ResultCard(
    media: Media,
    state: SearchUiState,
    revealed: MutableList<String>,
    actions: ExploreActions,
    context: CardContext,
    modifier: Modifier = Modifier,
    index: Int = -1,
) {
    val key = mediaKey(media)
    val blurred = searchCoverBlurred(state.matureContent, media, key, revealed.toSet())
    val status = state.listStatusOf(media.ref)
    val base = media.toCardData(state.titleLanguage)
    val data = base.copy(
        listStatus = status?.slot(),
        listStatusLabel = status?.slot()?.label(),
        coverUrl = if (blurred) null else base.coverUrl,
        subtitle = if (blurred) stringResource(Res.string.search_mature_tap) else base.subtitle,
    )
    val open: () -> Unit = { if (blurred) revealed += key else actions.onMediaClick(media) }
    val openLabel = stringResource(Res.string.rd_s3_menu_open)
    val statusLabel = stringResource(if (status == null) Res.string.rd_s3_menu_add else Res.string.rd_s3_menu_change)
    val planningLabel = stringResource(Res.string.rd_s3_menu_planning, ListStatus.PLANNING.slot().label())
    val entries: () -> List<MenuEntry> = {
        buildList {
            add(MenuAction(openLabel, open, icon = Icons.AutoMirrored.Outlined.OpenInNew))
            val quickAdd = actions.onQuickAdd
            if (quickAdd != null && status == null) add(MenuAction(planningLabel, { quickAdd(media) }, icon = Icons.AutoMirrored.Outlined.PlaylistAdd))
            actions.onEditStatus?.let { edit -> add(MenuAction(statusLabel, { edit(media) })) }
        }
    }
    ContextMenuArea(entries = entries, modifier = modifier, sheetHeader = { MenuSheetHeader(data.title) }) { menu ->
        TitleCard(
            data,
            onClick = open,
            context = context,
            show = CardShow(score = true),
            onLongClick = menu::showSheet,
            modifier = Modifier.cardToDetailsSource(data.key),
            index = index,
        )
    }
}

@Composable
private fun CharacterRow(c: CharacterRef, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaCover(url = c.image, modifier = Modifier.width(48.dp), shape = CircleShape, aspectRatio = 1f, kind = UpscaleImageKind.AVATAR)
        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Infinite scroll: reaching the footer loads the next page once per page; after a failure the button retries by hand. */
@Composable
private fun LoadMoreFooter(results: SearchResults.Ok, onLoadMore: () -> Unit) {
    LaunchedEffect(results.page) { onLoadMore() }
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        if (results.loadingMore) {
            LoadingIndicator(Modifier.size(40.dp))
        } else {
            OutlinedButton(onClick = onLoadMore, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.search_load_more)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// States

@Composable
private fun StateBlock(
    kind: EmptyKind,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().decor(DecorRegion.EMPTY).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmptyArt(kind)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions()
        }
    }
}

/** Nothing typed yet: what this scope searches, or how to get a catalogue when no extension has one. */
@Composable
internal fun ExploreIdle(state: SearchUiState, actions: ExploreActions) {
    when {
        state.scope == ExploreScope.EXTENSIONS && state.extensionId == null -> StateBlock(
            EmptyKind.SOURCES,
            stringResource(Res.string.rd_s3_no_catalogue_title),
            stringResource(Res.string.rd_s3_no_catalogue_body),
        ) {
            ActionButton(stringResource(Res.string.rd_s3_open_sources), actions.onOpenExtensions, icon = Icons.Outlined.Extension, emphasis = ActionEmphasis.FILLED)
        }
        state.scope == ExploreScope.CHARACTERS -> StateBlock(
            EmptyKind.SEARCH,
            stringResource(Res.string.search_idle_characters_title),
            stringResource(Res.string.search_idle_characters_message),
        )
        state.scope == ExploreScope.EXTENSIONS -> StateBlock(
            EmptyKind.SEARCH,
            stringResource(Res.string.search_idle_title),
            stringResource(Res.string.search_idle_extension_message),
        )
        else -> StateBlock(EmptyKind.SEARCH, stringResource(Res.string.search_idle_title), stringResource(Res.string.search_idle_message))
    }
}

/** No results, with a way to widen the search: drop the filters, clear the query, try the extensions or the metadata. */
@Composable
private fun ExploreEmpty(state: SearchUiState, actions: ExploreActions, filteredOut: Boolean = false) {
    val q = state.query.trim()
    when (state.scope) {
        ExploreScope.CHARACTERS -> StateBlock(
            EmptyKind.SEARCH,
            stringResource(Res.string.rd_s3_characters_empty, q),
            stringResource(Res.string.rd_s3_characters_empty_body),
        ) {
            ActionButton(stringResource(Res.string.search_clear), actions.onClear)
        }
        ExploreScope.EXTENSIONS -> {
            val name = state.extensions.firstOrNull { it.extension.id == state.extensionId }?.extension?.name.orEmpty()
            StateBlock(EmptyKind.SEARCH, stringResource(Res.string.rd_s3_extension_empty, q, name), null) {
                ActionButton(stringResource(Res.string.rd_s3_search_metadata), { actions.onScope(ExploreScope.ANIME) }, emphasis = ActionEmphasis.FILLED)
                ActionButton(stringResource(Res.string.search_clear), actions.onClear)
            }
        }
        else -> {
            val n = state.narrowingCount
            val message = when {
                n > 0 -> pluralStringResource(Res.plurals.rd_s3_filters_on, n, n)
                q.isNotEmpty() -> stringResource(Res.string.search_empty_message, q)
                else -> stringResource(Res.string.search_empty_filtered)
            }
            StateBlock(EmptyKind.SEARCH, stringResource(Res.string.search_empty_title), message) {
                if (n > 0) {
                    ActionButton(
                        pluralStringResource(Res.plurals.rd_s3_remove_filters, n, n),
                        actions.onResetAll,
                        icon = Icons.Outlined.FilterAltOff,
                        emphasis = ActionEmphasis.FILLED,
                    )
                }
                state.studio?.let { s -> ActionButton(stringResource(Res.string.search_remove_filter, s), actions.onClearStudio) }
                if (q.isNotEmpty() && !filteredOut) ActionButton(stringResource(Res.string.search_clear), actions.onClear)
                if (q.isNotEmpty() && state.extensions.any { it.searchable }) {
                    ActionButton(
                        stringResource(Res.string.rd_s3_search_extensions),
                        { actions.onScope(ExploreScope.EXTENSIONS) },
                        icon = Icons.Outlined.Extension,
                        emphasis = ActionEmphasis.OUTLINED,
                    )
                }
            }
        }
    }
}

/** Real error states: offline, AniList limiting, an extension catalogue failing (retry, or search the metadata / open Fuentes). */
@Composable
private fun ExploreError(error: AppError, state: SearchUiState, actions: ExploreActions) {
    val retry = stringResource(UiR.string.ui_retry)
    when {
        state.scope == ExploreScope.EXTENSIONS && state.extensionId != null -> {
            val name = state.extensions.firstOrNull { it.extension.id == state.extensionId }?.extension?.name ?: state.extensionId.orEmpty()
            StateBlock(EmptyKind.ERROR, stringResource(Res.string.rd_s3_extension_failed, name), error.userMessage()) {
                ActionButton(retry, actions.onRetry, emphasis = ActionEmphasis.FILLED)
                ActionButton(stringResource(Res.string.rd_s3_search_metadata), { actions.onScope(ExploreScope.ANIME) })
                ActionButton(stringResource(Res.string.rd_s3_open_sources), actions.onOpenExtensions, emphasis = ActionEmphasis.OUTLINED)
            }
        }
        error is AppError.RateLimited -> {
            val left = rememberSecondsLeft(error, error.retryAfterMs)
            val wait = when {
                left == null -> null
                left > 0 -> stringResource(Res.string.rd_s3_retry_in, formatSeconds(left))
                else -> stringResource(Res.string.rd_s3_retry_ready)
            }
            StateBlock(
                EmptyKind.ERROR,
                stringResource(Res.string.rd_s3_limited_title),
                listOfNotNull(error.userMessage(), wait).joinToString(" "),
            ) {
                ActionButton(retry, actions.onRetry, emphasis = ActionEmphasis.FILLED)
            }
        }
        error is AppError.Offline -> StateBlock(EmptyKind.ERROR, stringResource(Res.string.rd_s3_offline_title), error.userMessage()) {
            ActionButton(retry, actions.onRetry, icon = Icons.Outlined.CloudOff, emphasis = ActionEmphasis.FILLED)
        }
        else -> StateBlock(EmptyKind.ERROR, stringResource(UiR.string.ui_error_title), error.userMessage()) {
            ActionButton(retry, actions.onRetry, emphasis = ActionEmphasis.FILLED)
        }
    }
}

/** Seconds left until [millis] runs out, counting down once per second; null when the provider gave no wait. */
@Composable
private fun rememberSecondsLeft(key: Any, millis: Long?): Int? {
    if (millis == null) return null
    var left by remember(key) { mutableStateOf(((millis + 999) / 1000).toInt()) }
    LaunchedEffect(key) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }
    return left
}

private fun formatSeconds(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

/** AniList refused and the backup providers answered (BUG-146): say so, count down, retry now, or hide it. */
@Composable
private fun RateLimitBanner(error: AppError.RateLimited, onRetry: () -> Unit) {
    var hidden by rememberSaveable(error.provider) { mutableStateOf(false) }
    if (hidden) return
    val left = rememberSecondsLeft(error, error.retryAfterMs)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.HourglassTop, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.rd_s3_limited_banner), style = MaterialTheme.typography.bodyMedium)
                if (left != null) {
                    Text(
                        if (left > 0) stringResource(Res.string.rd_s3_retry_in, formatSeconds(left)) else stringResource(Res.string.rd_s3_retry_ready),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            TextButton(onClick = onRetry, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s3_retry_now)) }
            IconButton(onClick = { hidden = true }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.rd_s3_hide_notice))
            }
        }
    }
}
