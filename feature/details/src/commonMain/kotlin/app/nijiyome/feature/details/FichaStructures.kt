// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.cardToDetailsTarget
import app.nijiyome.feature.details.resources.Res
import app.nijiyome.feature.details.resources.*
import app.nijiyome.model.DetailsStructure
import app.nijiyome.model.HeaderLayout
import app.nijiyome.model.Media
import app.nijiyome.model.PageStyle
import app.nijiyome.ui.cards.toCardData
import app.nijiyome.ui.labels.genreLabel
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_back
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane S4: the Ficha in the three structures of the artifact (screens-a.js `ficha`), chosen once here from
// Ajustes › Apariencia (`structures.details`). Panel = header, tabs and a side column (Continuar, sources, Enviar); Ruta = the episodes
// as stations of one line; Cine = the artwork bleeds and the episodes live in a drawer (wide) or a sheet under it (phones). All three
// share FichaUi, the view model and DetailsActions.

/** The loaded Ficha (not gated): picks the structure once. */
@Composable
internal fun FichaRoot(media: Media, state: DetailsUiState, actions: DetailsActions) {
    val ui = rememberFichaUi(media, state)
    val lists = rememberEpisodeLists(media, state)
    val aired = remember(lists) { lists.airedNumbers() }
    // a refresh may drop episodes: the selection keeps only the ones that still exist
    LaunchedEffect(aired) { ui.selection.retainOnly(aired) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_FICHA
        when (structures.details) {
            DetailsStructure.PANEL -> FichaPanel(media, state, actions, ui, lists, aired, wide)
            DetailsStructure.ROUTE -> FichaRoute(media, state, actions, ui, lists, aired, wide)
            DetailsStructure.CINEMA -> FichaCinema(media, state, actions, ui, lists, aired, wide)
        }
    }
}

@Composable
private fun rememberFichaHead(media: Media, state: DetailsUiState, place: CardPlace): HeadModel {
    val look = LocalLook.current
    return rememberHeadModel(media, state, look.headerLayout, look.pageStyle == PageStyle.EDITORIAL, place)
}

@Composable
private fun FallbackBanner(state: DetailsUiState, actions: DetailsActions) {
    if (state.fallback != null) {
        app.nijiyome.ui.state.MetadataFallbackBanner(
            state = state.fallback,
            onRetryPrimary = actions.onRetryPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** The list's tabs went under the top bar: the bar shows its copy, the list's one is hidden from TalkBack. */
private class ListChrome(val scrolled: State<Boolean>, val tabsPinned: State<Boolean>, val barRowPx: Int)

@Composable
private fun rememberListChrome(listState: LazyListState): ListChrome {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barRowPx = with(density) { (statusTop + 56.dp).roundToPx() }
    val scrolled = remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val pinned = remember(listState, barRowPx) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TABS_KEY }
            if (info == null) listState.firstVisibleItemIndex > TABS_ITEM else info.offset <= barRowPx + 1
        }
    }
    return remember(scrolled, pinned, barRowPx) { ListChrome(scrolled, pinned, barRowPx) }
}

/** Top of a list-based structure: the selection bar on phones while selecting, else the scroll bar. */
@Composable
private fun FichaListTop(
    media: Media,
    state: DetailsUiState,
    actions: DetailsActions,
    ui: FichaUi,
    listState: LazyListState,
    chrome: ListChrome,
    aired: List<Int>,
    modifier: Modifier = Modifier,
    floatingBack: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    if (isCompactWindow() && ui.selection.active) {
        FichaSelectionTopBar(state, ui, aired, modifier)
    } else {
        FichaScrollBar(
            media, state, actions, ui,
            scrolled = chrome.scrolled.value,
            tabsPinned = chrome.tabsPinned.value,
            onPinnedTab = { t ->
                ui.tab = t
                scope.launch { listState.scrollToItem(TABS_ITEM, scrollOffset = -chrome.barRowPx) }
            },
            modifier = modifier,
            floatingBack = floatingBack,
        )
    }
}

@Composable
private fun ListTabs(ui: FichaUi, chrome: ListChrome, containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.background) {
    DetailsTabs(
        ui.tab,
        onTab = { ui.tab = it },
        modifier = if (chrome.tabsPinned.value) Modifier.clearAndSetSemantics {} else Modifier,
        containerColor = containerColor,
    )
}

private fun bottomInset(nav: Dp): PaddingValues = PaddingValues(bottom = nav + 112.dp)

// ---------------------------------------------------------------- Panel

/** Panel: header, facts, status, actions and tabs; wide windows add the side column (Continuar, sources, Enviar a dispositivo). */
@Composable
private fun FichaPanel(media: Media, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, lists: EpisodeLists?, aired: List<Int>, wide: Boolean) {
    val head = rememberFichaHead(media, state, if (wide) CardPlace.SIDE_COLUMN else CardPlace.STACKED)
    val listState = rememberLazyListState()
    val chrome = rememberListChrome(listState)
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = bottomInset(nav)) {
                    item(key = "header", contentType = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            FichaHero(media, state, actions, compact = !wide)
                            Column(Modifier.padding(horizontal = FichaGutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                FichaFacts(head, media, state)
                                FichaStatusRow(media, state, actions)
                                FichaActionsRow(media, state, actions, ui)
                                if (!wide) FichaWatchBlock(head, media, state, actions)
                            }
                            FallbackBanner(state, actions)
                        }
                    }
                    item(key = TABS_KEY, contentType = "tabs") { ListTabs(ui, chrome) }
                    fichaTabItems(media, state, actions, ui, lists, cardColumns = if (wide) 2 else 1, route = false)
                }
                FichaListTop(media, state, actions, ui, listState, chrome, aired, Modifier.align(Alignment.TopStart))
                FichaSelectionBar(state, actions, ui, aired, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
            }
            if (wide) {
                Column(
                    Modifier
                        .width(SIDE_COLUMN)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(16.dp)
                        .padding(bottom = nav),
                ) {
                    FichaSidePanels(head, media, state, actions)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Ruta

/** Ruta: a compact title row with the Watch button, status and actions, and the episodes as stations of a line with a detail card. */
@Composable
private fun FichaRoute(media: Media, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, lists: EpisodeLists?, aired: List<Int>, wide: Boolean) {
    val head = rememberFichaHead(media, state, CardPlace.STACKED)
    val listState = rememberLazyListState()
    val chrome = rememberListChrome(listState)
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = bottomInset(nav)) {
            item(key = "header", contentType = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (wide) {
                        RouteTitleRow(media, state, actions, head)
                    } else {
                        FichaHero(media, state, actions, compact = true)
                    }
                    Column(Modifier.padding(horizontal = FichaGutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!wide) FichaFacts(head, media, state)
                        FichaStatusRow(media, state, actions)
                        FichaActionsRow(media, state, actions, ui)
                        if (!wide) {
                            WatchButton(state, actions, Modifier.fillMaxWidth(), height = 56.dp, sub = head.nextUp.progress?.let { resumeSub(it) }, progress = head.nextUp.progress?.fraction)
                            SourceLine(state, actions, inCard = true, withEpisode = true)
                        }
                    }
                    FallbackBanner(state, actions)
                }
            }
            item(key = TABS_KEY, contentType = "tabs") { ListTabs(ui, chrome) }
            fichaTabItems(media, state, actions, ui, lists, cardColumns = if (wide) 2 else 1, route = true)
        }
        FichaListTop(media, state, actions, ui, listState, chrome, aired, Modifier.align(Alignment.TopStart), floatingBack = !wide)
        FichaSelectionBar(state, actions, ui, aired, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

/** Ruta on wide windows: back, a small poster, the title and the facts, and the Watch button at the end of the row. */
@Composable
private fun RouteTitleRow(media: Media, state: DetailsUiState, actions: DetailsActions, head: HeadModel) {
    val key = media.toCardData().key
    val title = media.displayTitle(state.titleLanguage)
    Row(
        Modifier
            .fillMaxWidth()
            .decor(DecorRegion.HERO, rememberHeroRegion(media, state))
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
        MediaCover(url = media.cover, modifier = Modifier.width(64.dp).cardToDetailsTarget(key))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
            TitleQuickActions(title = title, media = media, actions = actions, adultBadge = state.detailsOrNull?.isAdult == true || media.looksAdult())
            FichaFacts(head, media, state)
        }
        WatchButton(state, actions, Modifier.widthIn(min = 240.dp, max = 320.dp), height = 56.dp)
    }
}

// ---------------------------------------------------------------- Cine

/**
 * Cine: the artwork bleeds to the edges under a veil; the title, facts, status, actions and the Watch button sit on it. Wide windows
 * keep the tabs in a side drawer ("Episodios y más" opens and hides it); phones put them in a sheet right under the artwork.
 */
@Composable
private fun FichaCinema(media: Media, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, lists: EpisodeLists?, aired: List<Int>, wide: Boolean) {
    val head = rememberFichaHead(media, state, CardPlace.STACKED)
    if (wide) CinemaWide(media, state, actions, ui, lists, aired, head) else CinemaPhone(media, state, actions, ui, lists, aired, head)
}

/** The full-bleed artwork with its veil ([horizontal] = the veil darkens the start side, where the text is). */
@Composable
private fun CinemaBackdrop(media: Media, state: DetailsUiState, horizontal: Boolean, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val art = headerBackdrop(media, state.artwork)
    Box(modifier.decor(DecorRegion.HERO, rememberHeroRegion(media, state)).cardToDetailsTarget(media.toCardData().key)) {
        AsyncImage(
            model = art.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (state.coverBlurred) Modifier.blur(24.dp) else Modifier),
        )
        // The veil keeps every line of text at full contrast whatever the artwork is.
        val veil = if (horizontal) {
            // lighter than a wall: the art is the point of Cine, the text side stays dark enough for it
            Brush.horizontalGradient(listOf(bg.copy(alpha = 0.9f), bg.copy(alpha = 0.7f), bg.copy(alpha = 0.12f)))
        } else {
            Brush.verticalGradient(listOf(bg.copy(alpha = 0.45f), bg.copy(alpha = 0.88f), bg))
        }
        Box(Modifier.matchParentSize().background(veil))
    }
}

/** Title, genres, facts, status, actions and the Watch block of Cine. */
@Composable
private fun CinemaInfo(media: Media, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, head: HeadModel, modifier: Modifier = Modifier) {
    val title = media.displayTitle(state.titleLanguage)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectionContainer {
                Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
            if (media.genres.isNotEmpty()) {
                Text(
                    media.genres.map { genreLabel(it) }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TitleQuickActions(title = title, media = media, actions = actions, adultBadge = state.detailsOrNull?.isAdult == true || media.looksAdult())
            if (state.coverBlurred) MatureReveal(actions)
        }
        FichaFacts(head, media, state)
        FichaStatusRow(media, state, actions)
        FichaActionsRow(media, state, actions, ui)
        FichaWatchBlock(head, media, state, actions)
    }
}

@Composable
private fun CinemaWide(media: Media, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, lists: EpisodeLists?, aired: List<Int>, head: HeadModel) {
    val drawerState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        CinemaBackdrop(media, state, horizontal = true, modifier = Modifier.matchParentSize())
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FloatingBack(actions.onBack, Modifier.padding(start = 0.dp))
                CinemaInfo(media, state, actions, ui, head, Modifier.widthIn(max = 720.dp))
                FallbackBanner(state, actions)
                FilledTonalButton(onClick = { ui.drawerOpen = !ui.drawerOpen }, modifier = Modifier.heightIn(min = 48.dp), shape = NijiyomeControls.shape) {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (ui.drawerOpen) Res.string.rd_s4_drawer_hide else Res.string.rd_s4_drawer_show),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (ui.drawerOpen) {
                val sheetLabel = stringResource(Res.string.rd_s4_sheet_label)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .width(CINEMA_DRAWER)
                        .fillMaxHeight()
                        .decor(DecorRegion.SHEET)
                        .semantics { paneTitle = sheetLabel },
                ) {
                    Column(Modifier.statusBarsPadding()) {
                        DetailsTabs(ui.tab, onTab = { ui.tab = it }, modifier = Modifier, containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        Box(Modifier.weight(1f)) {
                            LazyColumn(Modifier.fillMaxSize(), state = drawerState, contentPadding = PaddingValues(bottom = 112.dp)) {
                                fichaTabItems(media, state, actions, ui, lists, cardColumns = 1, route = false)
                            }
                            FichaSelectionBar(state, actions, ui, aired, Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaPhone(media: Media, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, lists: EpisodeLists?, aired: List<Int>, head: HeadModel) {
    val listState = rememberLazyListState()
    val chrome = rememberListChrome(listState)
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = bottomInset(nav)) {
            item(key = "header", contentType = "header") {
                Box(Modifier.fillMaxWidth()) {
                    CinemaBackdrop(media, state, horizontal = false, modifier = Modifier.matchParentSize())
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 160.dp, start = FichaGutter, end = FichaGutter, bottom = 24.dp)) {
                        CinemaInfo(media, state, actions, ui, head)
                    }
                }
                FallbackBanner(state, actions)
            }
            item(key = TABS_KEY, contentType = "tabs") {
                Surface(
                    color = sheetColor,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // the sheet decor inside the rounded surface, not under it (stray corner marks)
                    Column(Modifier.decor(DecorRegion.SHEET).padding(top = 8.dp)) { ListTabs(ui, chrome, containerColor = sheetColor) }
                }
            }
            fichaTabItems(media, state, actions, ui, lists, cardColumns = 1, route = false)
        }
        FichaListTop(media, state, actions, ui, listState, chrome, aired, Modifier.align(Alignment.TopStart))
        FichaSelectionBar(state, actions, ui, aired, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

/** Index of [TABS_KEY] in the lists (header, tabs). */
private const val TABS_ITEM = 1
private val WIDE_FICHA = 840.dp
private val SIDE_COLUMN = 380.dp
private val CINEMA_DRAWER = 440.dp
