// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import app.nijiyome.feature.novel.resources.Res
import app.nijiyome.feature.novel.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.ControlsEdge
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.PlayerControlsTransition
import app.nijiyome.manga.ChapterText
import app.nijiyome.model.LoadState
import app.nijiyome.model.NovelReaderArgs
import app.nijiyome.model.NovelReaderSettings
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.TtsEngine
import app.nijiyome.model.TtsState
import app.nijiyome.model.TtsVoice
import app.nijiyome.novel.NarratorOptions
import app.nijiyome.novel.NovelChapters
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.platform.KeepScreenOn
import app.nijiyome.ui.platform.PlatformBackHandler
import app.nijiyome.ui.platform.ReaderWindowFullscreen
import app.nijiyome.ui.platform.SystemBarsHidden
import app.nijiyome.ui.platform.isLandscapeOrientation
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * `Route.NovelReader(args)` (NOV-09 to NOV-14, NOV-17) in the Sparkles design (artifact screen `lector`, structure `novela`): a
 * column of text in the typography of `NovelReaderSettings`, continuous scroll between chapters, an opaque top bar (Capítulos, Texto y
 * diseño, Pantalla completa, ⋮), the narrator bar (read aloud, pause / resume, next paragraph, stop, voice and engine, speed, sleep
 * timer, the paragraph read highlighted and followed) above a bottom bar with the chapter buttons and the percentage. Esc closes the
 * panel, then leaves fullscreen, then the reader. Progress is saved on leaving; offline with `args.offline`.
 */
@Composable
fun NovelReaderScreen(
    args: NovelReaderArgs,
    onBack: () -> Unit,
    onReplace: (NovelReaderArgs) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: NovelReaderViewModel = koinViewModel(key = "novel-reader-${args.extensionId}-${args.sourceId}-${args.chapterId}", parameters = { parametersOf(args) })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reader by vm.reader.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ttsState by vm.ttsState.collectAsStateWithLifecycle()
    val ttsParagraph by vm.ttsParagraph.collectAsStateWithLifecycle()
    val ttsError by vm.ttsError.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var chrome by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(reader.fullscreenOnOpen) }
    var sheet by remember { mutableStateOf<NovelSheet?>(null) }
    // A slider being dragged in "Texto y diseño": the text follows it before the value is saved (live preview).
    var preview by remember { mutableStateOf<NovelReaderSettings?>(null) }
    val shown = preview ?: reader
    val compact = isCompactWindow()
    val lastChapter = stringResource(Res.string.novel_reader_last_chapter)

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is NovelReaderEvent.Replace -> onReplace(ev.args)
                NovelReaderEvent.LastChapter -> snackbar.showSnackbar(lastChapter)
            }
        }
    }
    PlatformBackHandler {
        vm.saveProgress()
        onBack()
    }
    if (reader.keepScreenOn || ttsState == TtsState.SPEAKING) KeepScreenOn()
    SystemBarsHidden(fullscreen && !chrome)
    ReaderWindowFullscreen(fullscreen)

    val palette = novelPalette(shown.background)
    val family = remember(shown.font, shown.customFontPath) { novelFontFamily(shown.font, shown.customFontPath) }
    val textStyle = TextStyle(
        fontFamily = family,
        fontSize = shown.sizeSp.sp,
        lineHeight = (shown.sizeSp * shown.lineHeight).sp,
        letterSpacing = dyslexicSpacing(shown.font).em,
        color = palette.text,
        textAlign = if (shown.textAlignJustify) TextAlign.Justify else TextAlign.Start,
    )

    // Flattened rows: (segment, paragraph); paragraph -1 = chapter title, -2 = loading / error, end row = (-1, -3).
    val rows = remember(ui.segments) {
        buildList {
            ui.segments.forEachIndexed { s, seg ->
                add(s to -1)
                when (val t = seg.text) {
                    is LoadState.Ok -> t.value.paragraphs.indices.forEach { add(s to it) }
                    else -> add(s to -2)
                }
            }
            add(-1 to -3)
        }
    }
    // BUG-124: a chapter opened at its start shows its title row (index 0), not the first paragraph with the title above the screen.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = NovelReaderLayout.initialRow(args.startParagraph))
    val landscape = isLandscapeOrientation()
    val density = LocalDensity.current
    var topBarPx by remember(landscape) { mutableIntStateOf(0) }
    var bottomBarPx by remember(landscape) { mutableIntStateOf(0) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPad = if (topBarPx > 0) with(density) { topBarPx.toDp() } + 8.dp else statusTop + NovelReaderLayout.barEstimate(landscape) + 8.dp
    val bottomPad = if (bottomBarPx > 0) with(density) { bottomBarPx.toDp() } + 16.dp else navBottom + NovelReaderLayout.barEstimate(landscape) + 16.dp
    val idle by rememberUpdatedState(ttsState == TtsState.IDLE)
    LaunchedEffect(landscape) { if (NovelReaderLayout.hideBarsOnRotate(landscape)) chrome = false }
    LaunchedEffect(list, landscape) {
        if (!landscape) return@LaunchedEffect
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().collect { scrolling -> if (scrolling && idle) chrome = false }
    }
    LaunchedEffect(list, rows) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            val (s, p) = rows.getOrNull(i) ?: return@collect
            if (s >= 0) vm.onPosition(s, p.coerceAtLeast(0))
        }
    }
    // Continuous scroll: the next chapter is appended when the end row shows up.
    LaunchedEffect(list, rows.size, reader.continuousScroll) {
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= rows.size - 2 }.distinctUntilChanged().collect { atEnd ->
            if (atEnd && reader.continuousScroll && ui.segments.lastOrNull()?.text is LoadState.Ok) vm.appendNext()
        }
    }
    // Narration follows the paragraph being read (scrolls when highlight is on).
    LaunchedEffect(ttsParagraph, ui.narrating) {
        val p = ttsParagraph ?: return@LaunchedEffect
        val s = ui.narrating ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.first == s && it.second == p }
        if (index >= 0 && settings.tts.highlight) list.animateScrollToItem(index)
    }

    Box(
        modifier.fillMaxSize().background(palette.background).readerEscapeKey(
            refocusKey = chrome to (sheet == null),
            refocus = !chrome || sheet == null,
        ) {
            when {
                sheet != null -> sheet = null
                fullscreen -> fullscreen = false
                else -> {
                    vm.saveProgress()
                    onBack()
                }
            }
        },
    ) {
        val first = ui.segments.firstOrNull()
        if (first != null && ui.segments.size == 1 && first.text is LoadState.Loading) {
            Column(Modifier.align(Alignment.Center).widthIn(max = 720.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) { LoadingPlaceholder(Modifier.fillMaxWidth().height(20.dp)) }
            }
        } else if (first != null && ui.segments.size == 1 && first.text is LoadState.Err) {
            Column(Modifier.align(Alignment.Center).padding(24.dp).decor(DecorRegion.EMPTY), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.novel_reader_failed), color = palette.text, style = MaterialTheme.typography.titleMedium)
                Text((first.text as LoadState.Err).cause.userMessage(), color = palette.text, textAlign = TextAlign.Center)
                Button(onClick = vm::retry, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_retry)) }
            }
        } else {
            LazyColumn(
                state = list,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(shown.paragraphSpacingDp.dp),
                contentPadding = PaddingValues(top = topPad, bottom = bottomPad, start = 20.dp, end = 20.dp),
                modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }) },
            ) {
                itemsIndexed(rows, key = { i, r -> "${r.first}:${r.second}:$i" }) { _, (s, p) ->
                    val width = if (shown.maxWidthDp > 0) Modifier.widthIn(max = shown.maxWidthDp.dp) else Modifier
                    when {
                        s == -1 -> Column(width.fillMaxWidth().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(Res.string.novel_reader_end), color = palette.text.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
                            if (vm.hasNext && !reader.continuousScroll) {
                                FilledTonalButton(onClick = vm::goNext, shape = NijiyomeControls.shape) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                                    Text(stringResource(Res.string.novel_reader_next_chapter), modifier = Modifier.padding(start = 8.dp))
                                }
                            } else if (!vm.hasNext) {
                                Text(stringResource(Res.string.novel_reader_last_chapter), color = palette.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        p == -1 -> {
                            val seg = ui.segments[s]
                            val title = (seg.text as? LoadState.Ok)?.value?.title ?: seg.chapter.title
                            Column(width.fillMaxWidth().padding(top = if (s == 0) 0.dp else 48.dp, bottom = 8.dp)) {
                                ChapterText.formatNumber(seg.chapter.number)?.let {
                                    Text(stringResource(Res.string.novel_chapter_n, it), color = palette.text, style = textStyle.copy(fontSize = (shown.sizeSp + 8).sp, lineHeight = ((shown.sizeSp + 8) * 1.25f).sp), modifier = Modifier.semantics { heading() })
                                }
                                title?.let { Text(it, color = palette.text.copy(alpha = 0.75f), style = textStyle.copy(fontSize = (shown.sizeSp + 2).sp, lineHeight = ((shown.sizeSp + 2) * 1.3f).sp)) }
                            }
                        }
                        p == -2 -> when (val t = ui.segments[s].text) {
                            is LoadState.Err -> Column(width.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t.cause.userMessage(), color = palette.text)
                                OutlinedButton(onClick = vm::retry, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_retry)) }
                            }
                            else -> Box(width.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        else -> {
                            val text = (ui.segments[s].text as LoadState.Ok).value.paragraphs[p]
                            val highlighted = settings.tts.highlight && ui.narrating == s && ttsParagraph == p && ttsState != TtsState.IDLE
                            Text(
                                text,
                                style = textStyle,
                                modifier = width.fillMaxWidth()
                                    .then(if (highlighted) Modifier.background(palette.highlight, MaterialTheme.shapes.small) else Modifier)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        // A tap on a paragraph while narrating restarts the narration there.
                                        if (ttsState != TtsState.IDLE) vm.speak(s, p) else chrome = !chrome
                                    },
                            )
                        }
                    }
                }
            }
        }

        if (reader.showProgressBar) {
            LinearProgressIndicator(progress = { ui.fraction }, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding())
        }

        PlayerControlsTransition(visible = chrome, edge = ControlsEdge.TOP, modifier = Modifier.align(Alignment.TopCenter)) {
            NovelBarSurface(Modifier.onSizeChanged { topBarPx = it.height }) {
                Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = if (landscape) 0.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.saveProgress(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.rd_s11_exit_reader)) }
                    val chapterLine = listOfNotNull(
                        ChapterText.formatNumber(ui.chapter?.number)?.let { stringResource(Res.string.rd_s11_chapter_short, it) },
                        ui.chapter?.title,
                        ui.chapter?.translator,
                    ).joinToString(" · ")
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(args.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (chapterLine.isNotBlank()) Text(chapterLine, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { sheet = NovelSheet.CHAPTERS }) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = stringResource(Res.string.novel_reader_chapters)) }
                    IconButton(onClick = { sheet = NovelSheet.SETTINGS }) { Icon(Icons.Outlined.TextFields, contentDescription = stringResource(Res.string.novel_reader_settings)) }
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(
                            if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            contentDescription = stringResource(if (fullscreen) Res.string.rd_s11_fullscreen_exit_key else Res.string.rd_s11_fullscreen_key),
                        )
                    }
                    val allSettings = stringResource(Res.string.novel_reader_all_settings)
                    ContextMenuButton(entries = { listOf(MenuAction(allSettings, { onOpenSettings(SettingsSection.READER) }, icon = Icons.Outlined.Settings)) })
                }
            }
        }
        PlayerControlsTransition(visible = chrome || ttsState != TtsState.IDLE, edge = ControlsEdge.BOTTOM, modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { bottomBarPx = it.height }, horizontalAlignment = Alignment.CenterHorizontally) {
                NarratorBar(
                    state = ttsState,
                    error = ttsError?.let { ttsErrorText(it) },
                    voiceLabel = voiceLabel(settings.tts.voice, settings.tts.engine),
                    speed = settings.tts.speed,
                    sleep = sleep,
                    compact = compact,
                    onPlay = { if (ttsState == TtsState.PAUSED) vm.resume() else vm.speak() },
                    onPause = vm::pause,
                    onNext = vm::nextParagraph,
                    onStop = vm::stopNarration,
                    onVoice = { sheet = NovelSheet.VOICE },
                    onSpeed = vm::setSpeed,
                    onSleep = vm::setSleepTimer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                NovelBarSurface {
                    Row(Modifier.navigationBarsPadding().padding(horizontal = 4.dp, vertical = if (landscape) 0.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = vm::goPrevious, enabled = vm.hasPrevious) { Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(Res.string.novel_reader_previous_chapter)) }
                        Text(
                            if (ui.paragraphs.isNotEmpty()) stringResource(Res.string.novel_reader_percent, (ui.fraction * 100).roundToInt()) else "",
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        IconButton(onClick = vm::goNext, enabled = vm.hasNext) { Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(Res.string.novel_reader_next_chapter)) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))

        when (sheet) {
            NovelSheet.SETTINGS -> NovelPanel(
                title = stringResource(Res.string.novel_reader_settings),
                subtitle = stringResource(Res.string.rd_s11_live_changes),
                compact = compact,
                onDismiss = { sheet = null; preview = null },
                pinned = if (compact) ({ NovelPreview(shown, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }) else null,
            ) {
                NovelTypographyRows(reader, onPreview = { preview = it }) { t -> preview = null; vm.updateReader(t) }
                Box(Modifier.padding(top = 16.dp)) { NovelFlowRows(reader, onUpdate = vm::updateReader) }
                TextButton(onClick = { sheet = null; onOpenSettings(SettingsSection.READER) }, modifier = Modifier.padding(vertical = 8.dp), shape = NijiyomeControls.shape) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Text(stringResource(Res.string.novel_reader_all_settings), modifier = Modifier.padding(start = 6.dp))
                }
            }
            NovelSheet.CHAPTERS -> NovelPanel(title = stringResource(Res.string.novel_reader_chapters), compact = compact, onDismiss = { sheet = null }, scroll = false) {
                val chapters = NovelChapters.sorted(ui.chapters, ascending = true)
                if (chapters.isEmpty()) {
                    Text(stringResource(Res.string.novel_chapters_empty), modifier = Modifier.padding(16.dp))
                } else {
                    val state = rememberLazyListState(initialFirstVisibleItemIndex = chapters.indexOfFirst { it.id == ui.chapter?.id }.coerceAtLeast(0))
                    LazyColumn(state = state, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        itemsIndexed(chapters, key = { _, c -> c.id }) { _, c ->
                            val current = c.id == ui.chapter?.id
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .background(if (current) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.medium)
                                    .clickable { sheet = null; vm.open(c) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (ChapterText.formatNumber(c.number)?.let { stringResource(Res.string.rd_s11_chapter_short, it) } ?: c.id) + (c.title?.let { " · $it" } ?: "") +
                                            if (current) " · " + stringResource(Res.string.rd_s11_reading_now) else "",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(listOfNotNull(c.translator, c.lang?.uppercase()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (c.downloaded) Icon(Icons.Outlined.DownloadDone, contentDescription = stringResource(Res.string.novel_downloaded))
                            }
                        }
                    }
                }
            }
            NovelSheet.VOICE -> NovelPanel(title = stringResource(Res.string.rd_s11_voice_title), compact = compact, onDismiss = { sheet = null }) {
                VoicePanel(vm, settings.tts, onConfigure = { sheet = null; onOpenSettings(SettingsSection.READER) })
            }
            null -> Unit
        }
    }
}

private enum class NovelSheet { SETTINGS, CHAPTERS, VOICE }

@Composable
private fun voiceLabel(voice: String?, engine: TtsEngine): String =
    listOfNotNull(voice ?: stringResource(Res.string.novel_narration_voice_default), engineName(engine)).joinToString(" · ")

@Composable
internal fun engineName(engine: TtsEngine): String = stringResource(
    when (engine) {
        TtsEngine.SYSTEM -> Res.string.novel_narration_engine_system
        TtsEngine.OPENAI -> Res.string.novel_narration_engine_openai
        TtsEngine.ELEVENLABS -> Res.string.novel_narration_engine_elevenlabs
        TtsEngine.OPENAI_COMPATIBLE -> Res.string.novel_narration_engine_compatible
        TtsEngine.KITTEN -> Res.string.novel_narration_engine_kitten
    },
)

/**
 * The narrator bar (NOV-11 / NOV-17): idle, "Leer en voz alta"; active, pause / resume, next paragraph, stop; always the voice chip
 * (opens the voice panel); while narrating, the speed and the sleep timer. A narration error shows its reason.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NarratorBar(
    state: TtsState,
    error: String?,
    voiceLabel: String,
    speed: Float,
    sleep: SleepTimer,
    compact: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSleep: (SleepTimer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        modifier = modifier.widthIn(max = 820.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (state) {
                    TtsState.IDLE -> FilledTonalButton(onClick = onPlay, shape = NijiyomeControls.shape) {
                        Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null)
                        Text(stringResource(Res.string.novel_narration_read_aloud), modifier = Modifier.padding(start = 6.dp))
                    }
                    TtsState.PREPARING -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    TtsState.SPEAKING -> FilledIconButton(onClick = onPause) { Icon(Icons.Outlined.Pause, contentDescription = stringResource(Res.string.novel_narration_pause)) }
                    TtsState.PAUSED -> FilledIconButton(onClick = onPlay) { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.novel_narration_resume)) }
                }
                if (state != TtsState.IDLE) {
                    IconButton(onClick = onNext) { Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(Res.string.rd_s11_next_paragraph)) }
                    IconButton(onClick = onStop) { Icon(Icons.Outlined.Stop, contentDescription = stringResource(Res.string.novel_narration_stop)) }
                }
                AssistChip(
                    onClick = onVoice,
                    label = { Text(voiceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = if (compact) 180.dp else 280.dp)) },
                    leadingIcon = { Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            if (state != TtsState.IDLE) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.novel_narration_speed), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
                    SPEEDS.forEach { s ->
                        FilterChip(selected = kotlin.math.abs(speed - s) < 0.05f, onClick = { onSpeed(s) }, label = { Text(stringResource(Res.string.novel_narration_speed_value, speedText(s))) })
                    }
                    SleepMenu(sleep, onSleep)
                }
            }
        }
    }
}

private val SPEEDS = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)

@Composable
private fun SleepMenu(sleep: SleepTimer, onSleep: (SleepTimer) -> Unit) {
    var open by remember { mutableStateOf(false) }
    @Composable
    fun label(t: SleepTimer): String = stringResource(
        when (t) {
            SleepTimer.OFF -> Res.string.rd_s11_sleep_off
            SleepTimer.MIN_15 -> Res.string.rd_s11_sleep_15
            SleepTimer.MIN_30 -> Res.string.rd_s11_sleep_30
            SleepTimer.END_OF_CHAPTER -> Res.string.rd_s11_sleep_end
        },
    )
    Box {
        FilterChip(
            selected = sleep != SleepTimer.OFF,
            onClick = { open = true },
            label = { Text(stringResource(Res.string.rd_s11_sleep_timer, label(sleep))) },
            leadingIcon = { Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SleepTimer.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(label(t)) },
                    leadingIcon = { if (t == sleep) Icon(Icons.Outlined.Check, contentDescription = null) else Box(Modifier.size(24.dp)) },
                    onClick = { open = false; onSleep(t) },
                )
            }
        }
    }
}

/**
 * The voice panel of the reader (NOV-17 from the reader): the engine as a radio list with its readiness, the voices of the engine with
 * search and "Escuchar", pitch where the engine has it, the highlight switch; keys, models and servers stay in Ajustes ("Configurar").
 */
@Composable
private fun VoicePanel(vm: NovelReaderViewModel, tts: app.nijiyome.model.TtsSettings, onConfigure: () -> Unit) {
    val narrator = vm.narrator
    val options = narrator as? NarratorOptions
    val scope = rememberCoroutineScope()
    val voices by produceState<List<TtsVoice>?>(null, tts.engine, tts.endpoint, tts.model) { value = runCatching { narrator.voices(tts.engine) }.getOrNull().orEmpty() }
    var query by remember(tts.engine) { mutableStateOf("") }
    var testing by remember { mutableStateOf<String?>(null) }
    val sample = stringResource(Res.string.novel_narration_preview_sentence)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NovelGroup(stringResource(Res.string.novel_narration_engine)) {
            NarratorOptions.enginesOf(narrator).forEach { e ->
                val on = e == tts.engine
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = on, role = Role.RadioButton) { vm.updateTts { it.copy(engine = e, voice = null) } },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(if (on) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(engineName(e), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
        val cloud = tts.engine == TtsEngine.OPENAI || tts.engine == TtsEngine.ELEVENLABS || tts.engine == TtsEngine.OPENAI_COMPATIBLE
        if (cloud || tts.engine == TtsEngine.KITTEN) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.rd_s11_voice_configure_hint, engineName(tts.engine)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onConfigure, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s11_configure)) }
                }
            }
        }
        NovelGroup(stringResource(Res.string.novel_narration_voice)) {
            val list = voices
            when {
                list == null -> LoadingPlaceholder(Modifier.fillMaxWidth().height(48.dp))
                list.isEmpty() -> Text(stringResource(Res.string.novel_narration_voices_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    if (list.size > 6) {
                        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text(stringResource(Res.string.rd_s11_voice_search)) }, modifier = Modifier.fillMaxWidth())
                    }
                    val filtered = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.lang?.contains(query, ignoreCase = true) == true }
                    VoiceRow(stringResource(Res.string.novel_narration_voice_default), null, tts.voice == null, testing = false, onPick = { vm.updateTts { it.copy(voice = null) } }, onTest = null)
                    filtered.forEach { v ->
                        VoiceRow(
                            v.label,
                            v.lang,
                            tts.voice == v.id,
                            testing = testing == v.id,
                            onPick = { vm.updateTts { it.copy(voice = v.id) } },
                            onTest = {
                                if (testing == v.id) {
                                    narrator.stop()
                                    testing = null
                                } else {
                                    vm.updateTts { it.copy(voice = v.id) }
                                    testing = v.id
                                    scope.launch {
                                        runCatching { narrator.preview(sample) }
                                        testing = null
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        if (options?.hasPitch(tts.engine) == true) {
            NovelSlider(stringResource(Res.string.novel_narration_pitch), tts.pitch, 0.5f..2f, 14, label = { stringResource(Res.string.novel_narration_speed_value, speedText(it)) }) { v ->
                vm.updateTts { it.copy(pitch = (v * 10).roundToInt() / 10f) }
            }
        }
        NovelSwitch(stringResource(Res.string.novel_narration_highlight), null, tts.highlight) { v -> vm.updateTts { it.copy(highlight = v) } }
    }
}

@Composable
private fun VoiceRow(label: String, lang: String?, selected: Boolean, testing: Boolean, onPick: () -> Unit, onTest: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onPick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                lang?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (onTest != null) {
            IconButton(onClick = onTest) {
                Icon(
                    if (testing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    contentDescription = if (testing) stringResource(Res.string.rd_s11_voice_stop_sample) else stringResource(Res.string.rd_s11_voice_listen, label),
                )
            }
        }
    }
}

/** BUG-124: where the novel reader opens and how much room its bars take. Pure (unit-tested). */
internal object NovelReaderLayout {
    /** Rows are (segment, paragraph) with the chapter title first: paragraph p is row p + 1, the start of a chapter its title row. */
    fun initialRow(startParagraph: Int): Int = if (startParagraph <= 0) 0 else startParagraph + 1

    /** Height of a bar before it is measured (one 48 dp row in landscape; title + chapter line in portrait), insets apart. */
    fun barEstimate(landscape: Boolean) = if (landscape) 48.dp else 64.dp

    /** Turning to landscape hides the bars (two of them left ~6 lines of text). */
    fun hideBarsOnRotate(landscape: Boolean): Boolean = landscape
}
