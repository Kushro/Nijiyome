// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.ui.platform.KeepScreenOn
import app.nijiyome.ui.platform.PlatformBackHandler
import app.nijiyome.ui.platform.ReaderWindowFullscreen
import app.nijiyome.ui.platform.ScreenBrightness
import app.nijiyome.ui.platform.ViewerEscape
import app.nijiyome.ui.platform.SystemBarsHidden
import app.nijiyome.ui.platform.windowBrightnessAvailable
import app.nijiyome.ui.platform.isLandscapeOrientation
import coil3.compose.LocalPlatformContext
import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Keyboard
import app.nijiyome.designsystem.motion.ControlsEdge
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.PlayerControlsTransition
import app.nijiyome.ui.patterns.isCompactWindow
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.AdaptiveSheetScrollColumn
import app.nijiyome.manga.ChapterLists
import app.nijiyome.manga.ChapterOrder
import app.nijiyome.manga.ChapterText
import app.nijiyome.manga.ReaderKeymap
import app.nijiyome.manga.key
import app.nijiyome.model.ChapterPage
import app.nijiyome.model.LoadState
import app.nijiyome.model.MangaReaderArgs
import app.nijiyome.model.ReaderDirection
import app.nijiyome.model.ReaderFit
import app.nijiyome.model.ReaderMode
import app.nijiyome.model.ReaderSettings
import app.nijiyome.model.SettingsSection
import app.nijiyome.ui.state.userMessage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * `Route.MangaReader(args)` (checklist manga.reader.*): paginado / vertical / webtoon, doble página, LTR/RTL, ajuste, fondo, brillo,
 * recorte, carga ansiosa / perezosa + precarga (70 % → siguiente capítulo), scroll infinito, carril de miniaturas, navegador de
 * capítulos, auto-scroll, zoom (pellizco, doble toque, Ctrl+rueda, teclas), pantalla completa, ocultar chrome, progreso, captura,
 * cambio de extensión si fallan las páginas, guardar progreso al salir, atajos reasignables. Offline with `args.offline`.
 */
@Composable
fun MangaReaderScreen(
    args: MangaReaderArgs,
    onBack: () -> Unit,
    onReplace: (MangaReaderArgs) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: MangaReaderViewModel = koinViewModel(key = "reader-${args.extensionId}-${args.sourceId}-${args.chapterId}", parameters = { parametersOf(args) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reader by vm.reader.collectAsStateWithLifecycle()
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var chrome by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(reader.fullscreenOnOpen) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var autoScroll by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var jumpTo by remember { mutableStateOf<Int?>(null) }
    var stepRequest by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val compact = isCompactWindow()
    var zonesShown by remember { mutableStateOf(false) }

    val noMatch = stringResource(Res.string.manga_reader_no_match)
    val noNext = stringResource(Res.string.manga_reader_last_chapter)
    val savedTo = stringResource(Res.string.manga_reader_captured)
    val captureFailed = stringResource(Res.string.manga_reader_capture_failed)
    val failedText = stringResource(UiR.string.ui_error_title)
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is ReaderEvent.Replace -> onReplace(ev.args)
                ReaderEvent.NoMatch -> snackbar.showSnackbar(noMatch)
                ReaderEvent.NoNextChapter -> snackbar.showSnackbar(noNext)
                is ReaderEvent.Failed -> snackbar.showSnackbar(failedText)
            }
        }
    }

    PlatformBackHandler {
        vm.saveProgress()
        onBack()
    }

    // Window: screen kept on while reading, brightness override, immersive while fullscreen (each restored on leaving).
    KeepScreenOn()
    ScreenBrightness(reader.brightness)
    SystemBarsHidden(fullscreen && !chrome)
    ReaderWindowFullscreen(fullscreen)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // BUG-127: in landscape the bars take about half of the height. They hide when the phone turns to landscape and on every page
    // turn there (the centre tap still brings them back); portrait keeps them until the centre tap.
    val landscape = isLandscapeOrientation()
    LaunchedEffect(landscape) { if (ReaderChrome.hideOnRotate(landscape)) chrome = false }
    var lastPage by remember { mutableIntStateOf(ui.page) }
    LaunchedEffect(ui.page) {
        if (ReaderChrome.hideOnPageTurn(landscape, lastPage, ui.page)) chrome = false
        lastPage = ui.page
    }

    // Cache warming: eager = whole chapter, lazy = the next N pages; and the next chapter once prefetched at 70 %.
    val pages = ui.pages
    LaunchedEffect(pages, ui.page, reader.eagerLoading, reader.prefetchPages, reader.cropBorders) {
        if (pages.isEmpty()) return@LaunchedEffect
        val range = ReaderMath.warmRange(ui.page, pages.size, reader.eagerLoading, reader.prefetchPages)
        warmPages(context, pages.slice(range), reader.cropBorders)
    }
    LaunchedEffect(ui.prefetched) {
        if (ui.prefetched.isNotEmpty()) warmPages(context, ui.prefetched.take(reader.prefetchPages.coerceAtLeast(3)), reader.cropBorders)
    }

    val background = readerBackground(reader.background)
    val chapterLine = ui.chapter?.let { c -> listOfNotNull(ChapterText.formatNumber(c.number)?.let { stringResource(Res.string.rd_s11_chapter_short, it) }, c.title).joinToString(" · ") }.orEmpty()
    val chapterShort = ChapterText.formatNumber(ui.chapter?.number)?.let { stringResource(Res.string.rd_s11_chapter_short, it) }.orEmpty()
    val onBackground = if (reader.background == "white") Color.Black else if (reader.background == "theme") MaterialTheme.colorScheme.onBackground else Color.White

    fun toggleChrome() {
        if (reader.hideChromeOnTap) chrome = !chrome
    }
    fun zoomBy(factor: Float) {
        scale = (scale * factor).coerceIn(1f, 5f)
        if (scale == 1f) offset = Offset.Zero
    }

    fun escape() {
        when (readerEscape(sheetOpen = sheet != null, fullscreen = fullscreen)) {
            ReaderEscape.CLOSE_SHEET -> sheet = null
            ReaderEscape.LEAVE_FULLSCREEN -> fullscreen = false
            ReaderEscape.EXIT -> {
                vm.saveProgress()
                onBack()
            }
        }
    }
    if (desktopReaderKeys) {
        // A focused bar button goes away with the bars (or a sheet closes): the keys come back to the reader. An Esc that still
        // reaches the window runs the same Esc (ViewerEscape).
        LaunchedEffect(chrome, sheet == null) { if (!chrome || sheet == null) runCatching { focus.requestFocus() } }
        ViewerEscape(focus) { escape() }
    }

    fun handleAction(action: String): Boolean {
        when (action) {
            ReaderKeymap.NEXT_PAGE, ReaderKeymap.SCROLL_DOWN -> stepRequest = 1
            ReaderKeymap.PREV_PAGE, ReaderKeymap.SCROLL_UP -> stepRequest = -1
            ReaderKeymap.NEXT_CHAPTER -> vm.goNext()
            ReaderKeymap.PREV_CHAPTER -> vm.goPrevious()
            ReaderKeymap.TOGGLE_FULLSCREEN -> fullscreen = !fullscreen
            ReaderKeymap.TOGGLE_MODE -> vm.updateReader { it.copy(mode = ReaderMode.entries[(it.mode.ordinal + 1) % ReaderMode.entries.size]) }
            ReaderKeymap.ZOOM_IN -> if (reader.zoomShortcuts) zoomBy(1.25f) else return false
            ReaderKeymap.ZOOM_OUT -> if (reader.zoomShortcuts) zoomBy(0.8f) else return false
            ReaderKeymap.TOGGLE_CHROME -> chrome = !chrome
            ReaderKeymap.RELOAD -> vm.retry()
            else -> return false
        }
        return true
    }

    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val key = readerKeyName(e) ?: return@onPreviewKeyEvent false
                // With a panel open only Esc belongs to the reader (sliders and lists keep their arrows).
                if (sheet != null && key != "ESCAPE") return@onPreviewKeyEvent false
                // Desktop (Tatakai Next): Esc closes the sheet, then leaves fullscreen, then the reader. Android: Back does that.
                if (key == "ESCAPE" && desktopReaderKeys) {
                    escape()
                    return@onPreviewKeyEvent true
                }
                if (key in ReaderKeymap.RESERVED) return@onPreviewKeyEvent false
                val action = ReaderKeymap.actionFor(reader.keymap, key) ?: return@onPreviewKeyEvent false
                handleAction(directedAction(action, reader.mode, reader.direction, followDirection = desktopReaderKeys))
            },
    ) {
        val segment = ui.segments.getOrNull(0)
        when {
            segment == null -> Unit
            segment.pages is LoadState.Loading && ui.segments.size == 1 -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            segment.pages is LoadState.Err && ui.segments.size == 1 -> PagesError(
                message = (segment.pages as LoadState.Err).cause.userMessage(),
                onRetry = vm::retry,
                onSwitch = { sheet = ReaderSheet.SOURCES; if (ui.alternatives == null) vm.loadAlternatives() },
                color = onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> ZoomBox(
                scale = scale,
                offset = offset,
                ctrlWheel = reader.zoomShortcuts,
                onTransform = { s, o -> scale = s; offset = o },
                onTap = { fraction ->
                    if (zonesShown) {
                        zonesShown = false
                        return@ZoomBox
                    }
                    // Paged: left / right thirds turn the page (mirrored in RTL), the middle toggles the chrome.
                    if (reader.mode == ReaderMode.PAGED && scale == 1f) {
                        val rtl = reader.direction == ReaderDirection.RTL
                        when {
                            fraction < 0.33f -> stepRequest = if (rtl) 1 else -1
                            fraction > 0.67f -> stepRequest = if (rtl) -1 else 1
                            else -> toggleChrome()
                        }
                    } else {
                        toggleChrome()
                    }
                },
            ) {
                if (reader.mode == ReaderMode.PAGED) {
                    PagedReader(
                        ui = ui,
                        reader = reader,
                        startPage = args.startPage,
                        scrollEnabled = scale == 1f,
                        step = stepRequest,
                        jumpTo = jumpTo,
                        onStepConsumed = { stepRequest = 0 },
                        onJumpConsumed = { jumpTo = null },
                        onPage = { vm.onPage(0, it) },
                        onEnd = vm::goNext,
                        hasNext = vm.hasNext,
                        onError = vm::onPageError,
                        onLoaded = vm::onPageLoaded,
                        onSwitch = { sheet = ReaderSheet.SOURCES; if (ui.alternatives == null) vm.loadAlternatives() },
                        textColor = onBackground,
                    )
                } else {
                    ScrollReader(
                        ui = ui,
                        reader = reader,
                        startPage = args.startPage,
                        autoScroll = autoScroll && reader.autoScrollSpeed > 0,
                        step = stepRequest,
                        jumpTo = jumpTo,
                        onStepConsumed = { stepRequest = 0 },
                        onJumpConsumed = { jumpTo = null },
                        onPage = vm::onPage,
                        onAppendNext = vm::appendNext,
                        onNext = vm::goNext,
                        hasNext = vm.hasNext,
                        onError = vm::onPageError,
                        onLoaded = vm::onPageLoaded,
                        onSwitch = { sheet = ReaderSheet.SOURCES; if (ui.alternatives == null) vm.loadAlternatives() },
                        textColor = onBackground,
                    )
                }
            }
        }

        // Desktop: the window has no brightness of its own, so the pages are dimmed (Tatakai Next: CSS brightness, 20..100 %).
        if (!windowBrightnessAvailable && reader.brightness >= 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 1f - reader.brightness.coerceIn(0.2f, 1f))))
        }

        // Progress indicator (always, small) when the chrome is hidden.
        if (reader.showProgress && !chrome && pages.isNotEmpty()) {
            Text(
                if (reader.mode == ReaderMode.PAGED) "${ui.page + 1} / ${pages.size}" else chapterShort,
                color = onBackground,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp)
                    .background(background.copy(alpha = 0.6f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 2.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (zonesShown && reader.mode == ReaderMode.PAGED) TapZonesOverlay(rtl = reader.direction == ReaderDirection.RTL)

        val capture: () -> Unit = {
            pages.getOrNull(ui.page)?.let { page ->
                scope.launch {
                    val path = runCatching { capturePage(context, page, "${args.title}-${ChapterText.formatNumber(ui.chapter?.number) ?: "x"}-${ui.page + 1}") }.getOrNull()
                    snackbar.showSnackbar(if (path != null) savedTo.format(path) else captureFailed)
                }
            }
        }
        val menu = mangaReaderMenu(
            compact = compact,
            fullscreen = fullscreen,
            onSources = { sheet = ReaderSheet.SOURCES; vm.loadAlternatives() },
            onCapture = capture,
            onReload = vm::retry,
            onFullscreen = { fullscreen = !fullscreen },
            onAllSettings = { onOpenSettings(SettingsSection.READER) },
        )
        PlayerControlsTransition(visible = chrome, edge = ControlsEdge.TOP, modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(
                title = args.title,
                subtitle = listOfNotNull(chapterLine.ifBlank { null }, ui.source?.extensionName).joinToString(" · "),
                fullscreen = fullscreen,
                compact = compact,
                onBack = {
                    vm.saveProgress()
                    onBack()
                },
                onChapters = { sheet = ReaderSheet.CHAPTERS },
                onSettings = { sheet = ReaderSheet.SETTINGS },
                onFullscreen = { fullscreen = !fullscreen },
                menu = menu,
            )
        }
        PlayerControlsTransition(visible = chrome, edge = ControlsEdge.BOTTOM, modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBar(
                page = ui.page,
                total = pages.size,
                paged = reader.mode == ReaderMode.PAGED,
                rtl = reader.mode == ReaderMode.PAGED && reader.direction == ReaderDirection.RTL,
                compact = compact || landscape,
                hasPrevious = vm.hasPrevious,
                hasNext = vm.hasNext,
                autoScroll = autoScroll,
                autoSpeed = reader.autoScrollSpeed,
                onAutoScroll = {
                    if (!autoScroll && reader.autoScrollSpeed <= 0) vm.updateReader { it.copy(autoScrollSpeed = DEFAULT_AUTO_SPEED) }
                    autoScroll = !autoScroll
                },
                onAutoSpeed = { px ->
                    vm.updateReader { it.copy(autoScrollSpeed = px) }
                    autoScroll = true
                },
                onPrevious = vm::goPrevious,
                onNext = vm::goNext,
                onJump = { jumpTo = it },
                onPages = { sheet = ReaderSheet.RAIL },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))

        when (sheet) {
            ReaderSheet.SETTINGS -> ReaderPanel(
                title = stringResource(Res.string.manga_reader_settings),
                subtitle = stringResource(Res.string.rd_s11_settings_mode_only),
                compact = compact,
                onDismiss = { sheet = null },
            ) {
                ReaderSettingsGroups(
                    reader,
                    onUpdate = vm::updateReader,
                    modeOnly = true,
                    onShowZones = {
                        zonesShown = !zonesShown
                        if (zonesShown && !compact) sheet = null
                    },
                    zonesShown = zonesShown,
                )
                TextButton(onClick = { sheet = null; onOpenSettings(SettingsSection.READER) }, modifier = Modifier.padding(vertical = 8.dp), shape = NijiyomeControls.shape) {
                    Icon(Icons.Outlined.Keyboard, contentDescription = null)
                    Text(stringResource(Res.string.rd_s11_all_reader_settings), modifier = Modifier.padding(start = 6.dp))
                }
            }
            ReaderSheet.CHAPTERS -> ReaderPanel(title = stringResource(Res.string.manga_reader_chapters), compact = compact, onDismiss = { sheet = null }, scroll = false) {
                val list = ChapterLists.sorted(ui.chapters, ChapterOrder.ASC)
                if (list.isEmpty()) {
                    Text(stringResource(Res.string.manga_chapters_empty), modifier = Modifier.padding(16.dp))
                } else {
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = list.indexOfFirst { it.id == ui.chapter?.id }.coerceAtLeast(0))
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        itemsIndexed(list, key = { _, c -> c.id }) { _, c ->
                            val current = c.id == ui.chapter?.id
                            ChapterSheetRow(
                                title = (ChapterText.formatNumber(c.number)?.let { stringResource(Res.string.rd_s11_chapter_short, it) } ?: c.id) +
                                    (c.title?.let { " · $it" } ?: "") + if (current) " · " + stringResource(Res.string.rd_s11_reading_now) else "",
                                detail = listOfNotNull(c.group, c.lang?.uppercase()).joinToString(" · "),
                                current = current,
                                downloaded = c.downloaded,
                                onClick = { sheet = null; vm.open(c) },
                            )
                        }
                    }
                }
            }
            ReaderSheet.SOURCES -> ReaderPanel(
                title = stringResource(Res.string.manga_reader_switch_source),
                subtitle = stringResource(Res.string.manga_reader_switch_hint),
                compact = compact,
                onDismiss = { sheet = null },
            ) {
                when (val alts = ui.alternatives) {
                    null, LoadState.Loading -> LoadingPlaceholder(Modifier.fillMaxWidth().height(120.dp))
                    is LoadState.Err -> ErrorBlock(alts.cause, vm::loadAlternatives)
                    is LoadState.Ok -> if (alts.value.isEmpty()) {
                        Text(stringResource(Res.string.manga_no_sources), modifier = Modifier.padding(16.dp))
                    } else {
                        alts.value.forEach { s ->
                            val currentSource = s.key == ui.source?.key
                            ChapterSheetRow(
                                title = s.extensionName,
                                detail = s.problem?.let { sourceProblemLabel(it) } ?: sourceFacts(s),
                                current = currentSource,
                                downloaded = false,
                                enabled = !currentSource && s.problem == null,
                                onClick = { sheet = null; vm.switchSource(s) },
                            )
                        }
                    }
                }
            }
            ReaderSheet.RAIL -> ReaderPanel(
                title = stringResource(Res.string.manga_reader_pages),
                subtitle = if (pages.isNotEmpty()) stringResource(Res.string.manga_reader_page_of, ui.page + 1, pages.size) else null,
                compact = compact,
                onDismiss = { sheet = null },
                scroll = false,
            ) {
                val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (ui.page - 2).coerceAtLeast(0))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(84.dp),
                    state = grid,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    gridItemsIndexed(pages, key = { i, p -> "$i-${p.url}" }) { i, p ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(onClickLabel = stringResource(Res.string.manga_reader_page_n, i + 1)) { sheet = null; jumpTo = i },
                        ) {
                            AsyncImage(
                                model = pageRequest(context, p, cropBorders = false),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(116.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                                    .then(if (i == ui.page) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier),
                            )
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            null -> Unit
        }
    }
}

/** One row of the reader's chapter or source sheet: current one marked, downloaded mark, 48 dp. */
@Composable
private fun ChapterSheetRow(title: String, detail: String, current: Boolean, downloaded: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { if (current) selected = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (enabled || current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (downloaded) Icon(Icons.Outlined.DownloadDone, contentDescription = stringResource(Res.string.manga_downloaded))
    }
}

private enum class ReaderSheet { SETTINGS, CHAPTERS, SOURCES, RAIL }


/**
 * Pinch zoom + pan (only while zoomed, so swipes still turn pages), double tap toggles 1x / 2.5x (checklist manga.reader.zoom),
 * Ctrl + wheel zooms on keyboards (manga.reader.behavior). [onTap] gets the horizontal tap position 0..1.
 */
@Composable
private fun ZoomBox(
    scale: Float,
    offset: Offset,
    ctrlWheel: Boolean,
    onTransform: (Float, Offset) -> Unit,
    onTap: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        fun clamp(s: Float, o: Offset): Offset {
            val maxX = (widthPx * (s - 1f)) / 2f
            val maxY = (heightPx * (s - 1f)) / 2f
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }
        val currentScale by androidx.compose.runtime.rememberUpdatedState(scale)
        val currentOffset by androidx.compose.runtime.rememberUpdatedState(offset)
        val currentOnTap by androidx.compose.runtime.rememberUpdatedState(onTap)
        val currentOnTransform by androidx.compose.runtime.rememberUpdatedState(onTransform)
        // BUG-113: the gesture handlers restart when the box changes size (rotation), so a tap is measured against the current
        // width, not the portrait one (in landscape the centre used to count as the right third and turned the page).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(widthPx, heightPx) {
                    detectTapGestures(
                        onDoubleTap = { pos ->
                            if (currentScale > 1f) {
                                currentOnTransform(1f, Offset.Zero)
                            } else {
                                val s = 2.5f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                currentOnTransform(s, clamp(s, (center - pos) * (s - 1f)))
                            }
                        },
                        onTap = { pos -> currentOnTap(pos.x / size.width.coerceAtLeast(1)) },
                    )
                }
                .pointerInput(widthPx, heightPx, ctrlWheel) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                if (ctrlWheel && event.keyboardModifiers.isCtrlPressed) {
                                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    val s = (currentScale * if (dy < 0) 1.1f else 0.9f).coerceIn(1f, 5f)
                                    currentOnTransform(s, if (s == 1f) Offset.Zero else clamp(s, currentOffset))
                                    event.changes.forEach { it.consume() }
                                }
                                continue
                            }
                            val pointers = event.changes.count { it.pressed }
                            if (pointers >= 2 || (currentScale > 1f && pointers == 1)) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val s = (currentScale * zoom).coerceIn(1f, 5f)
                                val o = if (s == 1f) Offset.Zero else clamp(s, currentOffset + pan)
                                if (s != currentScale || o != currentOffset) currentOnTransform(s, o)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            content()
        }
    }
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.positionChanged(): Boolean = position != previousPosition

@Composable
private fun PagedReader(
    ui: MangaReaderUi,
    reader: ReaderSettings,
    startPage: Int,
    scrollEnabled: Boolean,
    step: Int,
    jumpTo: Int?,
    onStepConsumed: () -> Unit,
    onJumpConsumed: () -> Unit,
    onPage: (Int) -> Unit,
    onEnd: () -> Unit,
    hasNext: Boolean,
    onError: (String) -> Unit,
    onLoaded: (String) -> Unit,
    onSwitch: () -> Unit,
    textColor: Color,
) {
    val pages = ui.pages
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Double page only makes sense on wide screens (landscape / tablets).
        val double = reader.doublePage && maxWidth > maxHeight
        val spreads = remember(pages.size, double) { ReaderMath.spreads(pages.size, double) }
        val initial = remember(spreads) { ReaderMath.spreadOf(spreads, startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
        val pager = rememberPagerState(initialPage = initial, pageCount = { spreads.size + 1 })
        LaunchedEffect(pager, spreads) {
            snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { index ->
                val spread = spreads.getOrNull(index)
                if (spread != null) onPage(spread.last()) else if (pages.isNotEmpty()) onPage(pages.size - 1)
            }
        }
        LaunchedEffect(step) {
            if (step == 0) return@LaunchedEffect
            val target = (pager.currentPage + step).coerceIn(0, spreads.size)
            if (target == pager.currentPage && step > 0 && pager.currentPage == spreads.size) onEnd()
            pager.animateScrollToPage(target)
            onStepConsumed()
        }
        LaunchedEffect(jumpTo) {
            val j = jumpTo ?: return@LaunchedEffect
            pager.scrollToPage(ReaderMath.spreadOf(spreads, j))
            onJumpConsumed()
        }
        HorizontalPager(
            state = pager,
            reverseLayout = reader.direction == ReaderDirection.RTL,
            userScrollEnabled = scrollEnabled,
            beyondViewportPageCount = if (reader.eagerLoading) 3 else 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val spread = spreads.getOrNull(index)
            if (spread == null) {
                EndCard(hasNext = hasNext, onNext = onEnd, color = textColor)
            } else {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ordered = if (reader.direction == ReaderDirection.RTL) spread.reversed() else spread
                    ordered.forEach { i ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            PageImage(pages[i], i, reader, inList = false, failed = pages[i].url in ui.failedPages, onError = onError, onLoaded = onLoaded, onSwitch = onSwitch, textColor = textColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollReader(
    ui: MangaReaderUi,
    reader: ReaderSettings,
    startPage: Int,
    autoScroll: Boolean,
    step: Int,
    jumpTo: Int?,
    onStepConsumed: () -> Unit,
    onJumpConsumed: () -> Unit,
    onPage: (Int, Int) -> Unit,
    onAppendNext: () -> Unit,
    onNext: () -> Unit,
    hasNext: Boolean,
    onError: (String) -> Unit,
    onLoaded: (String) -> Unit,
    onSwitch: () -> Unit,
    textColor: Color,
) {
    // Flattened list: (segment, page) per row; -1 page = chapter header, -2 = loading / error / end row of a segment.
    val rows = remember(ui.segments) {
        buildList {
            ui.segments.forEachIndexed { s, seg ->
                if (s > 0) add(s to -1)
                when (val p = seg.pages) {
                    is LoadState.Ok -> p.value.indices.forEach { add(s to it) }
                    else -> add(s to -2)
                }
            }
            add(-1 to -3) // end row
        }
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceIn(0, (rows.size - 1).coerceAtLeast(0)))
    val gap = if (reader.mode == ReaderMode.WEBTOON) 0.dp else reader.pageGapDp.dp
    LaunchedEffect(list, rows) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            val (s, p) = rows.getOrNull(i) ?: return@collect
            if (s >= 0 && p >= 0) onPage(s, p)
        }
    }
    val atEnd by remember(rows) { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= rows.size - 2 } }
    LaunchedEffect(atEnd, reader.infiniteScroll) { if (atEnd && reader.infiniteScroll) onAppendNext() }
    LaunchedEffect(step) {
        if (step == 0) return@LaunchedEffect
        val viewport = list.layoutInfo.viewportSize.height.toFloat()
        list.animateScrollBy(viewport * 0.85f * step)
        onStepConsumed()
    }
    LaunchedEffect(jumpTo) {
        val j = jumpTo ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.first == ui.current && it.second == j }
        if (index >= 0) list.scrollToItem(index)
        onJumpConsumed()
    }
    // Auto-scroll (checklist manga.reader.behavior): px per second from the settings.
    LaunchedEffect(autoScroll, reader.autoScrollSpeed) {
        if (!autoScroll) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            list.scrollBy(reader.autoScrollSpeed * dt)
        }
    }
    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(rows, key = { i, r -> "${r.first}:${r.second}:$i" }) { _, (s, p) ->
            when {
                s == -1 -> EndCard(hasNext = hasNext, onNext = onNext, color = textColor, infinite = reader.infiniteScroll)
                p == -1 -> {
                    val c = ui.segments[s].chapter
                    Text(
                        ChapterText.formatNumber(c.number)?.let { stringResource(Res.string.manga_chapter_n, it) } ?: (c.title ?: c.id),
                        color = textColor,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
                p == -2 -> when (val st = ui.segments[s].pages) {
                    is LoadState.Err -> PagesError(st.cause.userMessage(), onRetry = onAppendNext, onSwitch = onSwitch, color = textColor, modifier = Modifier.padding(32.dp))
                    else -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                else -> {
                    val page = (ui.segments[s].pages as LoadState.Ok).value[p]
                    PageImage(page, p, reader, inList = true, failed = page.url in ui.failedPages, onError = onError, onLoaded = onLoaded, onSwitch = onSwitch, textColor = textColor)
                }
            }
        }
    }
}

@Composable
private fun PageImage(
    page: ChapterPage,
    index: Int,
    reader: ReaderSettings,
    inList: Boolean,
    failed: Boolean,
    onError: (String) -> Unit,
    onLoaded: (String) -> Unit,
    onSwitch: () -> Unit,
    textColor: Color,
) {
    val context = LocalPlatformContext.current
    var retry by remember(page.url) { mutableIntStateOf(0) }
    var loaded by remember(page.url, retry) { mutableStateOf(false) }
    var error by remember(page.url, retry) { mutableStateOf(false) }
    val request = remember(page.url, retry, reader.cropBorders) { pageRequest(context, page, reader.cropBorders, retry) }
    val fit = if (reader.mode == ReaderMode.WEBTOON) ReaderFit.WIDTH else reader.fit
    val maxWidth = if (reader.maxWidthDp > 0) Modifier.widthIn(max = reader.maxWidthDp.dp) else Modifier
    val sizing = when {
        inList && (fit == ReaderFit.WIDTH || fit == ReaderFit.BOTH) -> Modifier.fillMaxWidth()
        inList -> Modifier.wrapContentSize()
        fit == ReaderFit.WIDTH -> Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        fit == ReaderFit.HEIGHT -> Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())
        fit == ReaderFit.BOTH -> Modifier.fillMaxSize()
        else -> Modifier.wrapContentSize()
    }
    val scale = when (fit) {
        ReaderFit.WIDTH -> ContentScale.FillWidth
        ReaderFit.HEIGHT -> ContentScale.FillHeight
        ReaderFit.BOTH -> ContentScale.Fit
        ReaderFit.NATURAL -> ContentScale.None
    }
    Box(maxWidth.then(if (inList && !loaded) Modifier.fillMaxWidth().heightIn(min = 320.dp) else Modifier), contentAlignment = Alignment.Center) {
        if (error || failed && !loaded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(stringResource(Res.string.manga_reader_page_failed, index + 1), color = textColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { retry++ }, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_retry)) }
                    Button(onClick = onSwitch, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.manga_reader_switch_source)) }
                }
            }
        } else {
            AsyncImage(
                model = request,
                contentDescription = stringResource(Res.string.manga_reader_page_n, index + 1),
                contentScale = scale,
                modifier = sizing,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            loaded = true
                            onLoaded(page.url)
                        }
                        is AsyncImagePainter.State.Error -> {
                            error = true
                            onError(page.url)
                        }
                        else -> Unit
                    }
                },
            )
            if (!loaded) CircularProgressIndicator(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun PagesError(message: String, onRetry: () -> Unit, onSwitch: () -> Unit, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.manga_reader_pages_failed), style = MaterialTheme.typography.titleMedium, color = color)
        Text(message, style = MaterialTheme.typography.bodySmall, color = color, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_retry)) }
            Button(onClick = onSwitch, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.manga_reader_switch_source)) }
        }
    }
}

@Composable
private fun EndCard(hasNext: Boolean, onNext: () -> Unit, color: Color, infinite: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.manga_reader_chapter_end), color = color, style = MaterialTheme.typography.titleMedium)
        if (hasNext) {
            if (!infinite) Button(onClick = onNext, modifier = Modifier.padding(top = 12.dp), shape = NijiyomeControls.shape) { Text(stringResource(Res.string.manga_reader_next_chapter)) }
        } else {
            Text(stringResource(Res.string.manga_reader_last_chapter), color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** BUG-127: when the reader hides its bars by itself. Pure (unit-tested). */
internal object ReaderChrome {
    /** Turning to landscape hides them (they would cover about half of the page). */
    fun hideOnRotate(landscape: Boolean): Boolean = landscape

    /** A page turn in landscape hides them; the first page shown (or the same page) does not. */
    fun hideOnPageTurn(landscape: Boolean, previousPage: Int, page: Int): Boolean = landscape && previousPage != page
}
