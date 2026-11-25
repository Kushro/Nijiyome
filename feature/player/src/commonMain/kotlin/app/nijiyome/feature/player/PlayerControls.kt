// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.SendToMobile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.player.chrome.CinemaChrome
import app.nijiyome.feature.player.chrome.MinimalChrome
import app.nijiyome.feature.player.chrome.MiniChrome
import app.nijiyome.feature.player.chrome.MoreActionRow
import app.nijiyome.feature.player.chrome.PanelSideLayout
import app.nijiyome.feature.player.chrome.PanelVerticalLayout
import app.nijiyome.feature.player.chrome.PlayerChromeActions
import app.nijiyome.feature.player.chrome.PlayerChromeModel
import app.nijiyome.feature.player.chrome.panelShowsTabs
import app.nijiyome.feature.player.chrome.rememberSeekUiState
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.model.AspectMode
import app.nijiyome.model.PlayerStructure
import app.nijiyome.model.SettingsSection
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.player.PlayerSession
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.platform.ViewerEscape
import app.nijiyome.ui.platform.windowWidthDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

// 0.4.0 (worker D): Enhancements = the "Mejoras" panel (EnhancementPanel.kt); it replaces the BUG-022 "Perfil de mejora" sheet (the
// PC's profiles are one of its sections).
// 0.4.1: SubtitleStyle = the subtitle sheet's "Estilo" panel, SubtitleStyleFull = its "Más opciones" (SubtitleStyleEditor.kt).
// Sparkles S5: Tracks (audio + subtitles) and Picture (quality + framing) are the grouped sheets of the redesign's chrome; Quality,
// Subtitles and Audio stay for anything that still opens them on their own.
internal enum class Sheet { Quality, Subtitles, Audio, Speed, More, Episodes, Enhancements, SubtitleStyle, SubtitleStyleFull, Tracks, Picture }

/**
 * The running engine: video surface, subtitles (Media3), gestures, controls, overlays, PiP. Milestone 4: shared; the platform parts
 * (surface, gestures, window, PiP, volume, screenshots, frame generation) are the slots of PlayerScreenPlatform.kt.
 *
 * Sparkles S5: the chrome is one of the three structures of `design.structures.player` (Cine, Mínimo, Panel), switched once at the
 * root below. They share this state and these actions; only the composition changes (chrome/).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlayerContent(
    vm: PlayerViewModel,
    session: PlayerSession,
    state: PlayerUiState,
    title: String,
    flash: PlayerNotice?,
    onFlashDone: () -> Unit,
    onBack: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenTorrent: (String) -> Unit,
    /** 0.4.0 (worker D): the panel's "Importar Lossless.dll" (Settings › Reproductor holds the phone FG section). */
    onOpenSettings: (app.nijiyome.model.SettingsSection) -> Unit = {},
    /** 0.4.1 end screen: the title being played (series title, list progress) and "Volver a la ficha" (null = no page). */
    args: app.nijiyome.model.PlayerArgs? = null,
    onOpenDetails: (() -> Unit)? = null,
) {
    val ps by session.state.collectAsStateWithLifecycle()
    val platform = rememberPlayerPlatformActions()
    val capabilities = playerCapabilities
    val scheme = MaterialTheme.colorScheme
    val structure = structures.player
    val touch = capabilities.touchFirst

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var inPip by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    // --- parity wave A (worker p) ---
    // Sparkles S5: the touch Panel structure is the vertical player, which starts out of fullscreen (fullscreen is its Cine view).
    val startFullscreen = initialFullscreen() && !(touch && structure == PlayerStructure.PANEL)
    var fullscreen by rememberSaveable { mutableStateOf(startFullscreen) }
    var fullscreenFor by rememberSaveable { mutableStateOf(structure) }
    LaunchedEffect(structure) {
        // Picking another layout in the Más sheet: the vertical player leaves fullscreen, Cine / Mínimo go back to it (touch).
        if (touch && fullscreenFor != structure) {
            fullscreenFor = structure
            fullscreen = structure != PlayerStructure.PANEL
        }
    }
    var panelTab by rememberSaveable { mutableStateOf(Sheet.Enhancements) }
    val panelTabs = structure == PlayerStructure.PANEL && panelShowsTabs(touch, fullscreen, windowWidthDp())
    var locked by rememberSaveable { mutableStateOf(false) }
    var lockButtonVisible by remember { mutableStateOf(true) }
    var helpOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val koin = getKoin()
    val tools = remember { koin.subtitleToolsAccess() }
    val settings = state.settings
    // 0.4.1: the style panel's unsaved draft while a slider moves (the screen-drawn lines follow it; mpv gets it from the VM).
    var stylePreview by remember { mutableStateOf<app.nijiyome.model.SubtitleStyle?>(null) }
    val subtitleStyle = stylePreview ?: settings.subtitleStyle
    val fontFile = remember(subtitleStyle.fontFile) { tools?.fontFile(subtitleStyle.fontFile) }
    val focus = remember { FocusRequester() }
    val subtitlePicker = rememberSubtitleFilePicker(vm::loadSubtitleFile)
    val episodeForShot = if (state.remoteless) vm.episodeForFiles else null
    // ---- 0.4.0 (worker D): what is enhanced and where. `session.quality` is per session (a new one after every engine / location
    // switch): collecting the session's flow follows it. Phone frame generation is app-wide and only exists on libmpv.
    val quality by session.quality.collectAsStateWithLifecycle()
    val processing by vm.processing.collectAsStateWithLifecycle()
    // The phone's frame generation of this session (Android libmpv; it also posts the notice when FG fails mid-video).
    val frameGen = rememberPhoneFrameGen(vm, session)
    var statsVisible by rememberSaveable { mutableStateOf(false) }
    var localAspect by rememberSaveable { mutableStateOf(AspectMode.FIT) }
    val activeAspect = if (quality.available) quality.requested.aspect else localAspect
    fun setAspect(mode: AspectMode) {
        localAspect = mode
        session.setAspect(mode, quality.requested.zoomPercent)
        hud = Hud(Hud.Kind.Aspect, aspect = mode)
        interaction++
    }
    fun cycleAspect() = setAspect(nextQuickAspect(activeAspect))
    val chip = enhancementChip(
        location = processing.location,
        switchingTo = processing.switchingTo,
        pc = processing.pc,
        quality = quality,
        phoneFgMultiplier = frameGen?.activeMultiplier,
        phoneFgProblem = frameGen?.problem == true,
    )
    fun screenshot() {
        scope.launch { vm.onScreenshot(platform.screenshot(session, vm.titleForFiles, episodeForShot)) }
    }
    // The panel's tabs take the sheets while they show (Panel structure); otherwise a sheet opens over the video.
    fun openSheet(kind: Sheet) {
        if (panelTabs) panelTab = kind else sheet = kind
        interaction++
    }
    val sendToDevice = rememberSendToDevice(vm, args, session) { hud = Hud(Hud.Kind.Message, label = it) }

    // The window itself (orientation, bars, brightness) is restored by PlayerWindowScope in PlayerBody, which outlives this
    // content while the next episode resolves; keep-awake, fullscreen, background playback, PiP and pause on stop are here.
    PlayerContentEffects(
        session = session,
        isPlaying = ps.isPlaying,
        fullscreen = fullscreen,
        backgroundPlayback = settings.backgroundPlayback,
        isInPip = { inPip },
        onPipChange = { inPip = it },
    )
    LaunchedEffect(locked, lockButtonVisible) {
        if (locked && lockButtonVisible) {
            delay(LOCK_BUTTON_TIMEOUT_MS)
            lockButtonVisible = false
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun runAction(action: String) {
        // Hito 6 (desktop): speed, tracks, subtitle delay, skip and loop keys (DesktopPlayerKeys.kt)
        if (capabilities.desktopShortcuts && isDesktopAction(action)) {
            runDesktopAction(action, vm, session, state, capabilities.speeds)?.let { hud = it }
            return
        }
        when (action) {
            PlayerKeymap.PLAY_PAUSE -> session.togglePlay()
            PlayerKeymap.SEEK_BACK -> session.seekBy(-capabilities.seekStepMs)
            PlayerKeymap.SEEK_FORWARD -> session.seekBy(capabilities.seekStepMs)
            PlayerKeymap.FULLSCREEN -> fullscreen = !fullscreen
            PlayerKeymap.MUTE -> platform.adjustVolume(session, VolumeStep.TOGGLE_MUTE)?.let { hud = it }
            PlayerKeymap.PIP -> platform.enterPictureInPicture()
            PlayerKeymap.PREV_EPISODE -> vm.playPrevious()
            PlayerKeymap.NEXT_EPISODE -> if (state.hasNextEpisode && state.remoteless) vm.playNext()
            PlayerKeymap.VOLUME_UP -> platform.adjustVolume(session, VolumeStep.RAISE)?.let { hud = it }
            PlayerKeymap.VOLUME_DOWN -> platform.adjustVolume(session, VolumeStep.LOWER)?.let { hud = it }
            PlayerKeymap.SCREENSHOT -> screenshot()
            PlayerKeymap.SUBS_TOGGLE -> {
                val st = session.state.value
                if (st.subtitlesEnabled || state.customSubtitle != null) {
                    vm.selectSubtitle(null)
                } else {
                    st.subtitles.firstOrNull()?.let { vm.selectSubtitle(it.groupIndex) }
                }
            }
            PlayerKeymap.HELP -> helpOpen = !helpOpen
        }
    }

    // 0.4.0 (worker D): the quality engine caps the tier in PiP and the phone FG pauses there (MpvPlayerSession.setPictureInPicture).
    // Keyed on the session too, so a session made by a switch while in PiP hears it.
    LaunchedEffect(inPip, session) { session.setPictureInPicture(inPip) }

    // Controls stay up until the first frame (resolving / buffering), then hide after a few seconds of playback.
    val keepUp = !ps.started || state.phase == PlayerPhase.RESOLVING
    LaunchedEffect(controlsVisible, interaction, ps.isPlaying, sheet, keepUp) {
        if (controlsVisible && ps.isPlaying && sheet == null && !keepUp) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(hud) {
        val h = hud ?: return@LaunchedEffect
        delay(if (h.kind == Hud.Kind.Message) MESSAGE_TIMEOUT_MS else HUD_TIMEOUT_MS)
        hud = null
    }

    // ---- 0.4.1: the end screen (EndScreen.kt) once the episode played to its end, on every engine and location ----
    val endUi = endScreenState(
        ended = ps.ended,
        loop = ps.loop || state.loop,
        started = ps.started,
        episode = vm.episodeNumber,
        totalEpisodes = vm.totalEpisodes,
        hasNextEpisode = state.hasNextEpisode,
        canNavigateEpisodes = state.canNavigateEpisodes,
        next = state.next,
        episodes = state.episodes.items,
    )
    // "Ocultar" shows the last frame and the controls (the compact next card keeps counting) until the episode ends again
    var endHidden by remember { mutableStateOf(false) }
    val ended = endUi != null
    LaunchedEffect(ended) {
        if (ended) {
            locked = false // the lock is for watching; at the end the screen must be usable
            // the next episode's title / thumbnail (the list the Episodes sheet loads; once per player)
            if (state.canNavigateEpisodes && state.hasNextEpisode) vm.loadEpisodes()
        } else {
            endHidden = false
        }
    }
    val endVisible = endUi != null && !endHidden && !inPip

    // Hito 6 (Tatakai Next): on desktop Esc closes the shortcuts panel or the open sheet, then leaves fullscreen, then the player.
    fun escape(): Boolean = when {
        capabilities.desktopShortcuts && (helpOpen || sheet != null || !fullscreen) -> {
            when {
                helpOpen -> helpOpen = false
                sheet != null -> sheet = null
                else -> onBack()
            }
            true
        }
        fullscreen && capabilities.escapeLeavesFullscreen -> {
            fullscreen = false
            true
        }
        else -> false
    }
    if (capabilities.desktopShortcuts) {
        // A focused bar button goes away with the bars (or a sheet / the help panel closes): the keys come back to the player. An Esc
        // that still reaches the window runs the same Esc (ViewerEscape).
        LaunchedEffect(controlsVisible || keepUp, sheet == null && !helpOpen) {
            if (!(controlsVisible || keepUp) || (sheet == null && !helpOpen)) runCatching { focus.requestFocus() }
        }
        ViewerEscape(focus) { escape() }
    }

    val resolving = state.phase == PlayerPhase.RESOLVING
    val buffering = (ps.isBuffering || resolving) && ps.error == null
    val shown = controlsVisible || keepUp
    val pcInfo = processing.pc.takeIf { processing.location != app.nijiyome.model.ProcessingLocation.PHONE }
    val seekUi = rememberSeekUiState()
    val model = PlayerChromeModel(
        session = session,
        ps = ps,
        state = state,
        buffering = buffering,
        title = title,
        episodeTitle = state.episodes.items.firstOrNull { it.number == vm.episodeNumber }?.title?.takeIf { it.isNotBlank() },
        aspectMode = activeAspect,
        fullscreen = fullscreen,
        enhancementChip = chip,
        pc = pcInfo,
        capabilities = capabilities,
        hasKeyboard = hasHardwareKeyboard(),
        sendToDevice = sendToDevice,
        openSheet = if (panelTabs) panelTab else sheet,
        seek = seekUi,
    )
    val chromeActions = PlayerChromeActions(
        onBack = onBack,
        onPip = platform::enterPictureInPicture,
        onOpenSheet = ::openSheet,
        onOpenPicker = onOpenPicker,
        onOpenTorrent = onOpenTorrent,
        onNext = vm::playNext,
        onPrev = vm::playPrevious,
        onInteract = { interaction++ },
        onLock = { locked = true; lockButtonVisible = true; sheet = null },
        onScreenshot = { screenshot() },
        onToggleFullscreen = { fullscreen = !fullscreen },
        onHelp = { helpOpen = true },
        onCycleAspect = ::cycleAspect,
    )

    // The video with everything drawn over it; [chrome] is the structure's controls ([controlsHeight]: the room they take at the
    // bottom, for the subtitles and the skip / next cards).
    val stage: @Composable (Modifier, Dp, @Composable BoxScope.(Boolean) -> Unit) -> Unit = { modifier, controlsHeight, chrome ->
        Box(modifier.background(Color.Black)) {
            PlayerVideoSurface(session, Modifier.fillMaxSize(), aspectMode = activeAspect)
            ScreenSubtitleLayer(
                vm = vm,
                session = session,
                ps = ps,
                style = subtitleStyle,
                fontFile = fontFile,
                controlsPadding = if (shown && !inPip && !locked) controlsHeight else 0.dp,
                modifier = Modifier.navigationBarsPadding(),
            )

            if (!inPip && locked) {
                LockOverlay(visible = lockButtonVisible, onShow = { lockButtonVisible = true }, onUnlock = { locked = false; controlsVisible = true })
            }
            if (!inPip && !locked) {
                PlayerGestureLayer(
                    session = session,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onHud = { hud = it },
                    controlsShown = shown || sheet != null,
                    onPointerMoved = {
                        controlsVisible = true
                        interaction++
                    },
                    onToggleFullscreen = { fullscreen = !fullscreen },
                )

                // BUG-106: with the controls up, the chrome shows the spinner in the play button and the text out of the transport's way.
                if (buffering && !shown) {
                    BufferingOverlay(state, Modifier.align(Alignment.Center))
                }
                hud?.let {
                    if (touch) {
                        TouchGestureOverlay(it)
                    } else {
                        HudPill(it, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp))
                    }
                }
                // BUG-131: the whole time a Link profile change runs (the result comes as a notice below)
                if (state.linkProfileChanging && hud == null) {
                    LinkProfileChangingPill(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp))
                }
                // 0.4.0 (worker D): a "Procesar en" switch runs: visible with the controls hidden too, with Cancel
                processing.switchingTo?.let { to ->
                    if (hud == null && !state.linkProfileChanging) {
                        ProcessingSwitchPill(to, onCancel = vm::cancelProcessingSwitch, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp))
                    }
                }
                if (statsVisible) {
                    EnhancementStatsOverlay(
                        quality = quality,
                        processing = processing,
                        frameGen = frameGen,
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = if (shown) 112.dp else 16.dp),
                    )
                }
                val flashToast: @Composable () -> Unit = {
                    flash?.let {
                        FlashToast(
                            it,
                            onFlashDone,
                            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
                            onUndo = { session.undoAutoDowngrade(); onFlashDone() },
                        )
                    }
                }
                if (!endVisible) flashToast()

                val skip = state.markers.skipAt(ps.positionMs / 1000.0)
                if (skip != null && ps.started) {
                    SkipButton(
                        kind = skip.first,
                        onClick = { vm.seekBySkip(); interaction++ },
                        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = controlsHeight + 4.dp),
                    )
                }
                state.next?.takeIf { !endVisible }?.let { next ->
                    NextEpisodeCard(
                        next = next,
                        onPlayNow = vm::playNext,
                        onCancel = vm::cancelNext,
                        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = controlsHeight + 4.dp),
                    )
                }

                chrome(shown)

                if (endVisible && endUi != null) {
                    EndScreenHost(
                        vm = vm,
                        ui = endUi,
                        args = args,
                        seriesTitle = args?.title ?: title,
                        poster = state.poster,
                        autoListProgress = settings.autoListProgress,
                        onEpisodes = { openSheet(Sheet.Episodes) },
                        onDetails = onOpenDetails,
                        onBack = onBack,
                        onHide = { endHidden = true; controlsVisible = true },
                    )
                    flashToast()
                }
                state.translation.progress?.let { p ->
                    PretranslateOverlay(p, onSkip = vm::cancelPretranslate, modifier = Modifier.align(Alignment.Center))
                }
                if (helpOpen) {
                    Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.5f)).clickable { helpOpen = false }, contentAlignment = Alignment.Center) {
                        ShortcutsPanel(settings.playerKeymap, onDismiss = { helpOpen = false })
                    }
                }
            }
        }
    }

    // 0.4.1 (subtitle settings): "Estilo" and "Más opciones" preview slider steps live and save when the finger lifts.
    val styleSheets = setOf(Sheet.SubtitleStyle, Sheet.SubtitleStyleFull)
    val styleOpen = sheet in styleSheets || (panelTabs && panelTab in styleSheets)
    LaunchedEffect(styleOpen) { if (!styleOpen) stylePreview = null }
    val previewStyle: (app.nijiyome.model.SubtitleStyle) -> Unit = { stylePreview = it; vm.previewSubtitleStyle(it) }
    val saveStyle: (app.nijiyome.model.SubtitleStyle) -> Unit = { stylePreview = it; vm.saveSubtitleStyle(it) }

    // What each sheet holds, shared by the sheets over the video and the Panel structure's tabs ([close]: the sheet closes, the panel
    // goes back from a style step to Pistas).
    val sheetBody: @Composable (Sheet, () -> Unit) -> Unit = { kind, close ->
        val audioRows: @Composable () -> Unit = {
            if (ps.audioTracks.isEmpty()) {
                Text(
                    stringResource(Res.string.rd_s5_no_audio_tracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                val anySelected = ps.audioTracks.any { it.selected }
                ps.audioTracks.forEach { a ->
                    val rowLabel = listOfNotNull(a.label, a.detail).joinToString(" · ")
                    SheetRow(rowLabel, a.selected) { session.selectAudio(a.id); close() }
                }
                SheetRow(stringResource(Res.string.rd_s5_audio_off), !anySelected) {
                    session.selectAudio(app.nijiyome.player.AUDIO_TRACK_OFF)
                    close()
                }
            }
        }
        val subtitleRows: @Composable () -> Unit = {
            SubtitlesSheetContent(
                vm = vm,
                session = session,
                ps = ps,
                state = state,
                onLoadFile = if (capabilities.subtitleFile) ({ close(); subtitlePicker() }) else null,
                onDone = close,
                onOpenStyle = { openSheet(Sheet.SubtitleStyle) },
            )
        }
        val qualityRows: @Composable () -> Unit = {
            // Hito 6 (desktop): the qualities of the episode's sources; picking one switches source (SourceQualities.kt)
            if (capabilities.sourceQualities && ps.videoHeights.isEmpty()) {
                SourceQualityRows(vm, state, close)
            } else {
                SheetRow(stringResource(Res.string.player_quality_auto), ps.selectedHeight == null) { session.selectVideoHeight(null); close() }
                ps.videoHeights.forEach { h ->
                    SheetRow(stringResource(Res.string.player_quality_height, h), ps.selectedHeight == h) { session.selectVideoHeight(h); close() }
                }
            }
        }
        when (kind) {
            Sheet.Enhancements -> EnhancementPanel(
                vm = vm,
                session = session,
                state = state,
                quality = quality,
                processing = processing,
                frameGen = frameGen,
                statsVisible = statsVisible,
                onStatsChange = { statsVisible = it },
                onOpenFgSettings = { close(); session.pause(); onOpenSettings(SettingsSection.PLAYER) },
                onClose = close,
            )
            Sheet.SubtitleStyle -> SubtitleStyleQuickPanel(
                style = settings.subtitleStyle,
                onPreview = previewStyle,
                onChange = saveStyle,
                onMore = { openSheet(Sheet.SubtitleStyleFull) },
                onClose = close,
                showAss = ps.fullAss,
            )
            Sheet.SubtitleStyleFull -> SubtitleStyleEditor(
                style = settings.subtitleStyle,
                onChange = saveStyle,
                onPreview = previewStyle,
                onClose = close,
            )
            Sheet.Quality -> qualityRows()
            Sheet.Subtitles -> subtitleRows()
            Sheet.Audio -> audioRows()
            Sheet.Tracks -> Column {
                SectionLabel(stringResource(Res.string.player_audio))
                audioRows()
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionLabel(stringResource(Res.string.player_subtitles))
                subtitleRows()
            }
            Sheet.Picture -> Column {
                val hasQuality = ps.videoHeights.isNotEmpty() || (capabilities.sourceQualities && sourceQualityChip(state) != null)
                if (hasQuality) {
                    SectionLabel(stringResource(Res.string.player_quality))
                    qualityRows()
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                SectionLabel(stringResource(Res.string.rd_s5_aspect))
                // B-OCULTA-03: 16:9 and 4:3 too, which the quick cycle skips
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Segmented(
                        options = AspectMode.entries.map { SegmentedOption(it, stringResource(aspectLabelRes(it))) },
                        selected = activeAspect,
                        onSelect = { setAspect(it) },
                        small = true,
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.rd_s5_picture_more)) },
                    supportingContent = { Text(stringResource(Res.string.rd_s5_picture_more_hint)) },
                    leadingContent = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { openSheet(Sheet.Enhancements) },
                )
            }
            Sheet.Speed -> Column {
                SectionLabel(stringResource(Res.string.rd_s5_speed))
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    capabilities.speeds.forEach { sp ->
                        FilterChip(
                            selected = ps.speed == sp,
                            onClick = { session.setSpeed(sp) },
                            label = { Text(stringResource(Res.string.player_speed_value, speedText(sp))) },
                            leadingIcon = if (ps.speed == sp) ({ Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
                        )
                    }
                }
                Text(
                    stringResource(if (touch) Res.string.rd_s5_speed_hold_hint else Res.string.rd_s5_speed_keys_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                SleepTimerRows(vm, state)
            }
            Sheet.More -> MoreSheetContent(
                vm = vm,
                state = state,
                actions = {
                    // PLY-06 / PLA-03: the old "Más opciones" menu
                    if (capabilities.screenshot) {
                        MoreActionRow(Icons.Rounded.PhotoCamera, stringResource(Res.string.player_screenshot), { close(); screenshot() }, enabled = ps.started)
                    }
                    LocalDownloadArgs.current?.let { a ->
                        DownloadAction(a, state.current?.candidate?.toStream(), state.current?.candidate?.label.orEmpty(), asMenuItem = true, onDone = close)
                    }
                    state.torrent?.takeIf { it.fileIndex != null }?.torrentId?.let { id ->
                        MoreActionRow(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(Res.string.player_torrent_open_client), { close(); onOpenTorrent(id) })
                    }
                    if (state.canPickSource) {
                        MoreActionRow(Icons.Rounded.Language, stringResource(Res.string.rd_s5_change_source), { close(); onOpenPicker() })
                    }
                    if (model.canBrowseEpisodes) {
                        MoreActionRow(Icons.Rounded.VideoLibrary, stringResource(Res.string.player_episodes), { openSheet(Sheet.Episodes) })
                    }
                    MoreActionRow(Icons.Rounded.AutoAwesome, stringResource(Res.string.player_fx_title), { openSheet(Sheet.Enhancements) })
                    if (capabilities.screenLock) {
                        MoreActionRow(Icons.Rounded.Lock, stringResource(Res.string.player_lock), { close(); chromeActions.onLock() })
                    }
                    if (model.hasKeyboard) {
                        MoreActionRow(Icons.Rounded.Keyboard, stringResource(Res.string.player_shortcuts), { close(); helpOpen = true })
                    }
                    sendToDevice?.let { send ->
                        send.phones.forEach { p ->
                            MoreActionRow(Icons.Outlined.SendToMobile, stringResource(Res.string.rd_s5_send_to, p.name), { close(); send.send(p) }, enabled = !send.busy)
                        }
                    }
                },
                onOpenSettings = { close(); session.pause(); onOpenSettings(SettingsSection.PLAYER) },
            )
            Sheet.Episodes -> EpisodesSheetContent(vm, state, current = vm.episodeNumber, onPick = { n -> close(); vm.playEpisode(n) })
        }
    }
    val panelClose: () -> Unit = { if (panelTab in styleSheets) panelTab = Sheet.Tracks }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || locked) return@onPreviewKeyEvent false
                val key = playerKeyName(e) ?: return@onPreviewKeyEvent false
                if (key == "ESCAPE" && escape()) return@onPreviewKeyEvent true
                if (capabilities.desktopShortcuts) {
                    // Ctrl+1..6 picks an upscaling preset; any other Ctrl / Alt / Meta combination is not a player key (Ctrl+F is not F).
                    if (e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) {
                        val preset = ctrlPreset(key)?.takeIf { e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed } ?: return@onPreviewKeyEvent false
                        session.setQuality(preset, quality.requested.tier)
                        hud = Hud(Hud.Kind.Preset, label = preset)
                        interaction++
                        return@onPreviewKeyEvent true
                    }
                }
                val action = PlayerKeymap.actionFor(key, settings.playerKeymap, capabilities.desktopShortcuts) ?: return@onPreviewKeyEvent false
                // Enter only skips while there is a segment to skip; otherwise it stays with the focused button
                if (action == PlayerKeymap.SKIP_SEGMENT && state.markers.skipAt(ps.positionMs / 1000.0) == null) return@onPreviewKeyEvent false
                // 0.4.1: over the end screen the arrows / Enter move between its buttons; only "next episode" keeps its key
                if (endVisible) {
                    if (action != PlayerKeymap.NEXT_EPISODE || endUi?.next == null) return@onPreviewKeyEvent false
                    vm.playNext()
                    return@onPreviewKeyEvent true
                }
                runAction(action)
                interaction++
                true
            },
    ) {
        if (inPip) {
            // Picture in picture: the video alone, whatever the layout
            stage(Modifier.fillMaxSize(), 0.dp) { }
        } else {
            // Sparkles S5: the one switch on the player's structure
            when (structure) {
                PlayerStructure.CINEMA -> stage(Modifier.fillMaxSize(), CINEMA_CONTROLS_DP) { v -> CinemaChrome(model, chromeActions, v) }
                PlayerStructure.MINIMAL -> stage(Modifier.fillMaxSize(), MINIMAL_CONTROLS_DP) { v -> MinimalChrome(model, chromeActions, v) }
                PlayerStructure.PANEL -> when {
                    !panelTabs -> stage(Modifier.fillMaxSize(), CINEMA_CONTROLS_DP) { v -> CinemaChrome(model, chromeActions, v) }
                    touch -> PanelVerticalLayout(
                        model = model,
                        actions = chromeActions,
                        tab = panelTab,
                        onTab = { panelTab = it },
                        stage = { m -> stage(m, MINI_CONTROLS_DP) { v -> MiniChrome(model, chromeActions, v) } },
                        body = { t -> sheetBody(t, panelClose) },
                    )
                    else -> PanelSideLayout(
                        tab = panelTab,
                        onTab = { panelTab = it },
                        stage = { m -> stage(m, CINEMA_CONTROLS_DP) { v -> CinemaChrome(model, chromeActions, v, inPanel = true) } },
                        body = { t -> sheetBody(t, panelClose) },
                    )
                }
            }
        }
    }

    sheet?.let { kind ->
        // v1.2.0: side sheet in landscape / wide windows so the anime stays visible while picking audio, subs, speed or episodes;
        // bottom sheet on narrow portrait phones. Mejoras and the subtitle style steps draw their own header.
        EnhancementSheet(onDismiss = { sheet = null }) {
            if (kind != Sheet.Enhancements && kind !in styleSheets) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sheetTitle(kind), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { sheet = null }) {
                        Icon(Icons.Rounded.Close, stringResource(Res.string.player_fx_close))
                    }
                }
            }
            sheetBody(kind) { sheet = null }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun sheetTitle(kind: Sheet): String = when (kind) {
    Sheet.Quality -> stringResource(Res.string.player_quality)
    Sheet.Subtitles -> stringResource(Res.string.player_subtitles)
    Sheet.Audio -> stringResource(Res.string.player_audio)
    Sheet.Speed -> stringResource(Res.string.rd_s5_speed_timer)
    Sheet.More -> stringResource(Res.string.player_more)
    Sheet.Episodes -> stringResource(Res.string.player_episodes)
    Sheet.Tracks -> stringResource(Res.string.rd_s5_tracks)
    Sheet.Picture -> stringResource(Res.string.rd_s5_picture)
    Sheet.Enhancements -> stringResource(Res.string.player_fx_title)
    Sheet.SubtitleStyle, Sheet.SubtitleStyleFull -> stringResource(Res.string.player_subtitles)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/**
 * 0.4.1: [EpisodeEndScreen] wired to the view model and to the list row of the title (local store only, no network): the
 * progress line moves from "marcado como visto" to "progreso actualizado" once the history layer advanced the list.
 */
@Composable
private fun EndScreenHost(
    vm: PlayerViewModel,
    ui: EndScreenUi,
    args: app.nijiyome.model.PlayerArgs?,
    seriesTitle: String,
    poster: String?,
    autoListProgress: Boolean,
    onEpisodes: () -> Unit,
    onDetails: (() -> Unit)?,
    onBack: () -> Unit,
    onHide: () -> Unit,
) {
    val koin = getKoin()
    val lists = remember { koin.getOrNull<app.nijiyome.model.ListsRepository>() }
    val ref = args?.media
    val trackable = ref != null && (ref.anilistId != null || ref.malId != null || args.provider != null) && ui.episode != null
    val entryFlow = remember(lists, ref) { if (lists != null && ref != null && (ref.anilistId != null || ref.malId != null)) lists.entry(ref) else null }
    val entry = entryFlow?.collectAsStateWithLifecycle(initialValue = null)?.value
    val accounts = lists?.accounts?.collectAsStateWithLifecycle()?.value.orEmpty()
    EpisodeEndScreen(
        ui = ui,
        seriesTitle = seriesTitle,
        poster = poster,
        progress = endProgress(trackable, vm.episodeNumber, entry, autoListProgress, accounts),
        onPlayNext = vm::playNext,
        onCancelCountdown = vm::cancelNext,
        onReplay = vm::replay,
        onEpisodes = onEpisodes,
        onDetails = onDetails,
        onBack = onBack,
        onHide = onHide,
    )
}

@Composable
private fun SheetRow(text: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { if (selected) Icon(Icons.Rounded.Check, contentDescription = null) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

internal data class Hud(
    val kind: Kind,
    val percent: Int = 0,
    val seconds: Int = 0,
    val muted: Boolean = false,
    /** Hito 6 (desktop keys): the speed, track or preset id shown, and whether a track / the loop is on. S5: a [Kind.Message]'s text. */
    val label: String? = null,
    val on: Boolean = false,
    /** Hito 6: the subtitle delay shown ([Kind.SubDelay]). */
    val millis: Long = 0,
    /** v1.2.0 touch gestures: scrub target / duration / buffered ms and double-tap side (-1 left, +1 right). */
    val targetMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val doubleTapSide: Int = 0,
    /** Sparkles S5: the framing picked ([Kind.Aspect]), worded where it is drawn. */
    val aspect: AspectMode? = null,
) {
    enum class Kind { Brightness, Volume, Seek, Hold, Speed, Subtitles, Audio, SubDelay, Loop, Preset, Aspect, Scrub, DoubleTapSeek, Message }
}

/** BUG-131: "Cambiando perfil…" while the PC opens the session with the new profile. */
@Composable
private fun LinkProfileChangingPill(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(50), color = scheme.surfaceContainerHigh.copy(alpha = 0.9f), contentColor = scheme.onSurface) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.player_link_profile_changing), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** 0.4.0 (worker D): "Cambiando a la PC…" while a "Procesar en" switch runs (the video keeps playing where it was), with Cancel. */
@Composable
private fun ProcessingSwitchPill(to: app.nijiyome.model.ProcessingLocation, onCancel: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(50), color = scheme.surfaceContainerHigh.copy(alpha = 0.9f), contentColor = scheme.onSurface) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(chipLabel(EnhancementChip.Switching(to), rememberChipWords()), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            androidx.compose.material3.TextButton(onClick = onCancel) { Text(stringResource(Res.string.player_fx_switch_cancel)) }
        }
    }
}

@Composable
internal fun HudPill(hud: Hud, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(50), color = scheme.surfaceContainerHigh.copy(alpha = 0.85f), contentColor = scheme.onSurface) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            when (hud.kind) {
                Hud.Kind.Brightness -> {
                    Icon(Icons.Rounded.BrightnessMedium, stringResource(Res.string.player_brightness))
                    Text(stringResource(Res.string.player_percent, hud.percent))
                }
                Hud.Kind.Volume -> {
                    if (hud.muted) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(Res.string.player_volume))
                        Text(stringResource(Res.string.player_volume_muted))
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(Res.string.player_volume))
                        Text(stringResource(Res.string.player_percent, hud.percent))
                    }
                }
                Hud.Kind.Seek -> Text(stringResource(Res.string.player_seek_seconds, String.format(java.util.Locale.getDefault(), "%+d", hud.seconds)))
                Hud.Kind.Hold -> {
                    Icon(Icons.Rounded.Speed, contentDescription = null)
                    Text(stringResource(Res.string.player_speed_hold))
                }
                Hud.Kind.Aspect -> Text(hud.aspect?.let { stringResource(aspectLabelRes(it)) } ?: hud.label.orEmpty())
                else -> DesktopHudContent(hud)
            }
        }
    }
}

internal fun speedText(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')

private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val HUD_TIMEOUT_MS = 900L
private const val MESSAGE_TIMEOUT_MS = 3_000L
internal const val SEEK_STEP_MS = 10_000L
private const val LOCK_BUTTON_TIMEOUT_MS = 2_500L

/** Sparkles S5: the room each structure's bottom controls take (subtitles lift above it; the skip / next cards sit on it). */
private val CINEMA_CONTROLS_DP = 136.dp
private val MINIMAL_CONTROLS_DP = 104.dp
private val MINI_CONTROLS_DP = 52.dp

/** Size of the play button, the tallest control of the centred transport row. */
private val TRANSPORT_PLAY_DP = 64.dp

/** Space between the transport row and the buffering card above it. */
private val TRANSPORT_GAP_DP = 12.dp

/**
 * BUG-106: lays the element out horizontally centred with its bottom edge [TRANSPORT_GAP_DP] above the centred transport row, whatever
 * its height. Use it on a child of a full-size Box.
 */
internal fun Modifier.aboveTransport(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    layout(width, height) {
        val bottom = height / 2 - (TRANSPORT_PLAY_DP / 2 + TRANSPORT_GAP_DP).roundToPx()
        placeable.place((width - placeable.width) / 2, (bottom - placeable.height).coerceAtLeast(0))
    }
}
