// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AssistChip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import app.nijiyome.ui.platform.ViewerEscape
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.layout.layout
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.model.SkipKind
import app.nijiyome.model.SourceKind
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.player.PlayerSession
import app.nijiyome.model.LinkProcessingInfo
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.platform.windowWidthDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

// 0.4.0 (worker D): Enhancements = the "Mejoras" panel (EnhancementPanel.kt); it replaces the BUG-022 "Perfil de mejora" sheet (the
// PC's profiles are one of its sections).
// 0.4.1: SubtitleStyle = the subtitle sheet's "Estilo" panel, SubtitleStyleFull = its "Más opciones" (SubtitleStyleEditor.kt).
private enum class Sheet { Quality, Subtitles, Audio, Speed, More, Episodes, Enhancements, SubtitleStyle, SubtitleStyleFull }

/**
 * The running engine: video surface, subtitles (Media3), gestures, controls, overlays, PiP. Milestone 4: shared; the platform parts
 * (surface, gestures, window, PiP, volume, screenshots, frame generation) are the slots of PlayerScreenPlatform.kt.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var inPip by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    // --- parity wave A (worker p) ---
    val startFullscreen = initialFullscreen()
    var fullscreen by rememberSaveable { mutableStateOf(startFullscreen) }
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
        if (hud != null) {
            delay(HUD_TIMEOUT_MS)
            hud = null
        }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
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
        PlayerVideoSurface(session, Modifier.fillMaxSize())
        ScreenSubtitleLayer(
            vm = vm,
            session = session,
            ps = ps,
            style = subtitleStyle,
            fontFile = fontFile,
            controlsPadding = if (controlsVisible && !inPip && !locked) 136.dp else 0.dp,
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
                controlsShown = controlsVisible || keepUp || sheet != null,
                onPointerMoved = {
                    controlsVisible = true
                    interaction++
                },
                onToggleFullscreen = { fullscreen = !fullscreen },
            )

            val resolving = state.phase == PlayerPhase.RESOLVING
            val buffering = (ps.isBuffering || resolving) && ps.error == null
            // BUG-106: with the controls up, Controls shows the spinner in the play button and the text above the transport row
            // (centred here, the −10 / play / +10 row was drawn over it).
            if (buffering && !(controlsVisible || keepUp)) {
                BufferingOverlay(state, Modifier.align(Alignment.Center))
            }
            hud?.let { HudPill(it, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp)) }
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
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = if (controlsVisible || keepUp) 112.dp else 16.dp),
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
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 140.dp),
                )
            }
            state.next?.takeIf { !endVisible }?.let { next ->
                NextEpisodeCard(
                    next = next,
                    onPlayNow = vm::playNext,
                    onCancel = vm::cancelNext,
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 140.dp),
                )
            }

            AnimatedVisibility(controlsVisible || keepUp, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Controls(
                    session = session,
                    ps = ps,
                    state = state,
                    buffering = buffering,
                    title = title,
                    onBack = onBack,
                    onPip = platform::enterPictureInPicture,
                    onOpenSheet = { sheet = it; interaction++ },
                    onOpenPicker = onOpenPicker,
                    onOpenTorrent = onOpenTorrent,
                    onNext = vm::playNext,
                    onInteract = { interaction++ },
                    fullscreen = fullscreen,
                    onPrev = vm::playPrevious,
                    onLock = { locked = true; lockButtonVisible = true; sheet = null },
                    onScreenshot = { screenshot() },
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onHelp = { helpOpen = true },
                    enhancementChip = chip,
                    pc = processing.pc.takeIf { processing.location != app.nijiyome.model.ProcessingLocation.PHONE },
                    capabilities = capabilities,
                )
            }
            if (endVisible && endUi != null) {
                EndScreenHost(
                    vm = vm,
                    ui = endUi,
                    args = args,
                    seriesTitle = args?.title ?: title,
                    poster = state.poster,
                    autoListProgress = settings.autoListProgress,
                    onEpisodes = { sheet = Sheet.Episodes },
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

    if (sheet == Sheet.Enhancements) {
        EnhancementSheet(onDismiss = { sheet = null }) {
            EnhancementPanel(
                vm = vm,
                session = session,
                state = state,
                quality = quality,
                processing = processing,
                frameGen = frameGen,
                statsVisible = statsVisible,
                onStatsChange = { statsVisible = it },
                onOpenFgSettings = { sheet = null; session.pause(); onOpenSettings(app.nijiyome.model.SettingsSection.PLAYER) },
                onClose = { sheet = null },
            )
        }
    }
    // 0.4.1 (subtitle settings): "Estilo" and "Más opciones" sit beside the video in landscape (side sheet), so the subtitles stay
    // visible while they change; slider steps are previewed live and saved when the finger lifts.
    LaunchedEffect(sheet) { if (sheet != Sheet.SubtitleStyle && sheet != Sheet.SubtitleStyleFull) stylePreview = null }
    val previewStyle: (app.nijiyome.model.SubtitleStyle) -> Unit = { stylePreview = it; vm.previewSubtitleStyle(it) }
    val saveStyle: (app.nijiyome.model.SubtitleStyle) -> Unit = { stylePreview = it; vm.saveSubtitleStyle(it) }
    if (sheet == Sheet.SubtitleStyle) {
        EnhancementSheet(onDismiss = { sheet = null }) {
            SubtitleStyleQuickPanel(
                style = settings.subtitleStyle,
                onPreview = previewStyle,
                onChange = saveStyle,
                onMore = { sheet = Sheet.SubtitleStyleFull },
                onClose = { sheet = null },
                showAss = ps.fullAss,
            )
        }
    }
    if (sheet == Sheet.SubtitleStyleFull) {
        EnhancementSheet(onDismiss = { sheet = null }) {
            SubtitleStyleEditor(
                style = settings.subtitleStyle,
                onChange = saveStyle,
                onPreview = previewStyle,
                onClose = { sheet = null },
            )
        }
    }
    sheet?.takeIf { it != Sheet.Enhancements && it != Sheet.SubtitleStyle && it != Sheet.SubtitleStyleFull }?.let { kind ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
            TrackModal()
            when (kind) {
                // Hito 6 (desktop): the qualities of the episode's sources; picking one switches source (SourceQualities.kt)
                Sheet.Quality -> if (capabilities.sourceQualities && ps.videoHeights.isEmpty()) {
                    SourceQualityRows(vm, state) { sheet = null }
                } else {
                    SheetRow(stringResource(Res.string.player_quality_auto), ps.selectedHeight == null) { session.selectVideoHeight(null); sheet = null }
                    ps.videoHeights.forEach { h ->
                        SheetRow(stringResource(Res.string.player_quality_height, h), ps.selectedHeight == h) { session.selectVideoHeight(h); sheet = null }
                    }
                }
                Sheet.Subtitles -> SubtitlesSheetContent(
                    vm = vm,
                    session = session,
                    ps = ps,
                    state = state,
                    onLoadFile = if (capabilities.subtitleFile) ({ sheet = null; subtitlePicker() }) else null,
                    onDone = { sheet = null },
                    onOpenStyle = { sheet = Sheet.SubtitleStyle },
                )
                Sheet.More -> MoreSheetContent(vm, state)
                Sheet.Episodes -> EpisodesSheetContent(vm, state, current = vm.episodeNumber, onPick = { n -> sheet = null; vm.playEpisode(n) })
                Sheet.Audio -> ps.audioTracks.forEach { a ->
                    SheetRow(a.label, a.selected) { session.selectAudio(a.id); sheet = null }
                }
                Sheet.Enhancements, Sheet.SubtitleStyle, Sheet.SubtitleStyleFull -> Unit // side / bottom sheets above
                Sheet.Speed -> capabilities.speeds.forEach { sp ->
                    SheetRow(stringResource(Res.string.player_speed_value, speedText(sp)), ps.speed == sp) { session.setSpeed(sp); sheet = null }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
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
        modifier = Modifier.clickable(onClick = onClick),
    )
}

internal data class Hud(
    val kind: Kind,
    val percent: Int = 0,
    val seconds: Int = 0,
    val muted: Boolean = false,
    /** Hito 6 (desktop keys): the speed, track or preset id shown, and whether a track / the loop is on. */
    val label: String? = null,
    val on: Boolean = false,
    /** Hito 6: the subtitle delay shown ([Kind.SubDelay]). */
    val millis: Long = 0,
) {
    enum class Kind { Brightness, Volume, Seek, Hold, Speed, Subtitles, Audio, SubDelay, Loop, Preset }
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
private fun HudPill(hud: Hud, modifier: Modifier) {
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
                else -> DesktopHudContent(hud)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Controls(
    session: PlayerSession,
    ps: PlaybackState,
    state: PlayerUiState,
    buffering: Boolean,
    title: String,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onOpenSheet: (Sheet) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenTorrent: (String) -> Unit,
    onNext: () -> Unit,
    onInteract: () -> Unit,
    fullscreen: Boolean,
    onPrev: () -> Unit,
    onLock: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onHelp: () -> Unit,
    enhancementChip: EnhancementChip,
    /** 0.4.1: what the PC does (PC / Mixto), for the seek bar's "Procesado por la PC" run; null on the phone. */
    pc: LinkProcessingInfo? = null,
    /** Milestone 4: the platform controls offered (PiP, fullscreen, lock, screenshot); a missing one is not shown. */
    capabilities: PlayerCapabilities = playerCapabilities,
) {
    val scheme = MaterialTheme.colorScheme
    val on = scheme.onBackground
    Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.45f))) {
        // Top bar: back, title + source chip, "Open in the client", PiP. BUG-060: on a compact width (a phone in portrait) the 7-8
        // icons left the title and the source chip (the only way to change source while playing) with 0 dp: there the title row keeps
        // back, PiP, fullscreen and a "More options" menu (download, lock, screenshot, episodes / Link profile, shortcuts), and the
        // chips get a row of their own.
        val compact = windowWidthDp() < COMPACT_TOP_BAR_DP
        // BUG-061: keyboard shortcuts only mean something with a hardware keyboard.
        val hasKeyboard = hasHardwareKeyboard()
        val chips: @Composable () -> Unit = {
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                // 0.4.0 (worker D): what is enhanced and where, first so it never scrolls out of sight; opens "Mejoras"
                EnhancementStatusChip(enhancementChip, onClick = { onOpenSheet(Sheet.Enhancements) })
                state.current?.let { cur ->
                    val (n, total) = state.position ?: (1 to 1)
                    AssistChip(
                        onClick = { if (state.canPickSource) onOpenPicker() },
                        label = {
                            Text(
                                stringResource(Res.string.player_source_chip, n, total, "${cur.candidate.extName} · ${cur.candidate.label}"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                // BUG-054: the torrent type is not the download action's arrow.
                                if (cur.candidate.kind == SourceKind.TORRENT) Icons.Rounded.Hub else Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = if (state.canPickSource) ({ Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) }) else null,
                        colors = AssistChipDefaults.assistChipColors(labelColor = on, leadingIconContentColor = on, trailingIconContentColor = on),
                    )
                }
                state.torrent?.takeIf { it.fileIndex != null }?.torrentId?.let { id ->
                    AssistChip(
                        onClick = { onOpenTorrent(id) },
                        label = { Text(stringResource(Res.string.player_torrent_open_client), maxLines = 1) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = on, leadingIconContentColor = on),
                    )
                }
                state.torrent?.takeIf { it.fileIndex != null }?.let { t ->
                    Text(torrentLine(t), color = on.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
            }
        }
        val pipAndFullscreen: @Composable () -> Unit = {
            if (capabilities.pictureInPicture) {
                IconButton(onClick = onPip) { Icon(Icons.Rounded.PictureInPictureAlt, stringResource(Res.string.player_pip), tint = on) }
            }
            if (capabilities.fullscreenToggle) {
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        stringResource(if (fullscreen) Res.string.player_fullscreen_exit else Res.string.player_fullscreen_enter),
                        tint = on,
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp).align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.player_back), tint = on) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = on, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    if (!compact) chips()
                }
                if (compact) {
                    pipAndFullscreen()
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true; onInteract() }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(Res.string.player_more_options), tint = on)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            LocalDownloadArgs.current?.let { a ->
                                DownloadAction(a, state.current?.candidate?.toStream(), state.current?.candidate?.label.orEmpty(), asMenuItem = true, onDone = { menu = false })
                            }
                            if (capabilities.screenLock) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.player_lock)) },
                                    onClick = { menu = false; onLock() },
                                    leadingIcon = { Icon(Icons.Rounded.LockOpen, contentDescription = null) },
                                )
                            }
                            if (capabilities.screenshot) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.player_screenshot)) },
                                    onClick = { menu = false; onScreenshot() },
                                    enabled = ps.started,
                                    leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                                )
                            }
                            if (state.remoteless && !state.looseFile) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.player_episodes)) },
                                    onClick = { menu = false; onOpenSheet(Sheet.Episodes) },
                                    leadingIcon = { Icon(Icons.Rounded.FormatListNumbered, contentDescription = null) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.player_fx_title)) },
                                onClick = { menu = false; onOpenSheet(Sheet.Enhancements) },
                                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                            )
                            if (hasKeyboard) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.player_shortcuts)) },
                                    onClick = { menu = false; onHelp() },
                                    leadingIcon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
                                )
                            }
                        }
                    }
                } else {
                    LocalDownloadArgs.current?.let { a -> DownloadAction(a, state.current?.candidate?.toStream(), state.current?.candidate?.label.orEmpty()) } // parity wave B (r): downloads.player-button
                    if (capabilities.screenLock) IconButton(onClick = onLock) { Icon(Icons.Rounded.LockOpen, stringResource(Res.string.player_lock), tint = on) }
                    if (capabilities.screenshot) {
                        IconButton(onClick = onScreenshot, enabled = ps.started) { Icon(Icons.Rounded.PhotoCamera, stringResource(Res.string.player_screenshot), tint = on) }
                    }
                    if (state.remoteless && !state.looseFile) {
                        IconButton(onClick = { onOpenSheet(Sheet.Episodes) }) { Icon(Icons.Rounded.FormatListNumbered, stringResource(Res.string.player_episodes), tint = on) }
                    }
                    // BUG-022 / 0.4.0: the PC's profiles, local enhancements and frame generation all live in "Mejoras"
                    IconButton(onClick = { onOpenSheet(Sheet.Enhancements) }) { Icon(Icons.Rounded.AutoAwesome, stringResource(Res.string.player_fx_title), tint = on) }
                    if (hasKeyboard) IconButton(onClick = onHelp) { Icon(Icons.Rounded.Keyboard, stringResource(Res.string.player_shortcuts), tint = on) }
                    pipAndFullscreen()
                }
            }
            if (compact) Box(Modifier.padding(start = 52.dp)) { chips() }
        }

        // Transport
        Row(Modifier.align(Alignment.Center), Arrangement.spacedBy(24.dp), Alignment.CenterVertically) {
            if (state.hasPrevEpisode) {
                IconButton(onClick = { onPrev(); onInteract() }) { Icon(Icons.Rounded.SkipPrevious, stringResource(Res.string.player_prev), tint = on) }
            }
            val fiveSeconds = capabilities.seekStepMs == DESKTOP_SEEK_STEP_MS
            FilledTonalIconButton(onClick = { session.seekBy(-capabilities.seekStepMs); onInteract() }, Modifier.size(56.dp)) {
                if (fiveSeconds) {
                    Icon(Icons.Rounded.Replay5, stringResource(Res.string.player_seek_back_5))
                } else {
                    Icon(Icons.Rounded.Replay10, stringResource(Res.string.player_seek_back))
                }
            }
            FilledIconButton(onClick = { session.togglePlay(); onInteract() }, Modifier.size(TRANSPORT_PLAY_DP), enabled = ps.started) {
                val playLabel = stringResource(if (ps.isPlaying) Res.string.player_pause else Res.string.player_play)
                if (buffering) {
                    // BUG-106: the loading indicator takes the play button's place instead of sitting under the row
                    LoadingIndicator(Modifier.size(48.dp).semantics { contentDescription = playLabel }, color = LocalContentColor.current)
                } else {
                    Icon(if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, playLabel, Modifier.size(40.dp))
                }
            }
            FilledTonalIconButton(onClick = { session.seekBy(capabilities.seekStepMs); onInteract() }, Modifier.size(56.dp)) {
                if (fiveSeconds) {
                    Icon(Icons.Rounded.Forward5, stringResource(Res.string.player_seek_forward_5))
                } else {
                    Icon(Icons.Rounded.Forward10, stringResource(Res.string.player_seek_forward))
                }
            }
            if (state.hasNextEpisode && state.remoteless) {
                IconButton(onClick = { onNext(); onInteract() }) { Icon(Icons.Rounded.SkipNext, stringResource(Res.string.player_next), tint = on) }
            }
        }

        // BUG-106: the buffering text ("Tu PC está preparando el video desde este punto…") goes in the strip above the transport row,
        // where neither the row nor the top bar covers it (below it there is no room in landscape before the seek bar).
        if (buffering) {
            BufferingOverlay(state, Modifier.aboveTransport().padding(horizontal = 16.dp), showIndicator = false)
        }

        // Seek bar + chips
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SeekBar(session, ps, state, pc, onInteract, hoverTime = !capabilities.touchFirst)
            // (Android keeps the row's default top alignment; the desktop volume button is taller than the chips)
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp), if (capabilities.volumeControls) Alignment.CenterVertically else Alignment.Top) {
                // Hito 6 (desktop): mute button and volume slider of the engine's own volume (PlayerVolumeControl.kt)
                if (capabilities.volumeControls) PlayerVolumeControl(session, onInteract)
                // BUG-067: "Más" first, so it is not the chip that falls off a phone's width.
                PlayerChip(
                    onClick = { onOpenSheet(Sheet.More) },
                    label = { Text(stringResource(Res.string.player_more)) },
                    leadingIcon = { Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(18.dp)) },
                )
                val sourceQuality = if (capabilities.sourceQualities && ps.videoHeights.isEmpty()) sourceQualityChip(state) else null
                if (ps.videoHeights.isNotEmpty() || sourceQuality != null) {
                    PlayerChip(
                        onClick = { onOpenSheet(Sheet.Quality) },
                        label = { Text(sourceQuality ?: ps.selectedHeight?.let { stringResource(Res.string.player_quality_height, it) } ?: stringResource(Res.string.player_quality)) },
                        leadingIcon = { Icon(Icons.Rounded.HighQuality, null, Modifier.size(18.dp)) },
                    )
                }
                // Always offered once playing: "Load file…", delay and translation live in this sheet.
                if (ps.subtitles.isNotEmpty() || ps.started) {
                    PlayerChip(
                        onClick = { onOpenSheet(Sheet.Subtitles) },
                        label = { Text(stringResource(Res.string.player_subtitles)) },
                        leadingIcon = { Icon(Icons.Rounded.Subtitles, null, Modifier.size(18.dp)) },
                    )
                }
                if (ps.audioTracks.size > 1) {
                    PlayerChip(
                        onClick = { onOpenSheet(Sheet.Audio) },
                        label = { Text(stringResource(Res.string.player_audio)) },
                        leadingIcon = { Icon(Icons.Rounded.Audiotrack, null, Modifier.size(18.dp)) },
                    )
                }
                PlayerChip(
                    onClick = { onOpenSheet(Sheet.Speed) },
                    label = { Text(stringResource(Res.string.player_speed_value, speedText(ps.speed))) },
                    description = stringResource(Res.string.player_speed_desc, speedText(ps.speed)),
                    leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp)) },
                )
                if (state.hasNextEpisode && state.remoteless) {
                    PlayerChip(
                        onClick = onNext,
                        label = {
                            Text(
                                stringResource(
                                    if (state.torrent?.batch == true) Res.string.player_torrent_batch_next else Res.string.player_next,
                                ),
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

/**
 * BUG-067: the bottom actions open sheets, so they are buttons ([AssistChip], read by TalkBack as "button"), not [FilterChip]s that
 * TalkBack announced as checked / unchecked boxes. [description] replaces the label for TalkBack when it says more ("Velocidad 1x").
 */
@Composable
private fun PlayerChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    description: String? = null,
) {
    AssistChip(
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        modifier = Modifier.semantics {
            role = Role.Button
            if (description != null) contentDescription = description
        },
    )
}

/**
 * Slider whose track carries, over the part not played yet (0.4.1, "como en PC"): what the phone already has ("Descargado": the
 * engine's buffer ahead of the playhead in a light primary, a torrent's other downloaded pieces in a neutral tone) and, while the
 * PC processes (Link PC / Mixto), the range the PC already processed ("Procesado por la PC", tertiary). Intro / outro / recap
 * marks are a thin line through the middle. A small legend under the bar names the colours when there is more than a plain buffer.
 * Colours come from the scheme only; the runs are computed by [seekBarRanges].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    session: PlayerSession,
    ps: PlaybackState,
    state: PlayerUiState,
    pc: LinkProcessingInfo?,
    onInteract: () -> Unit,
    /** Hito 6 (pointer): the time under the mouse in a bubble over the bar (SeekHover.kt). */
    hoverTime: Boolean = false,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = ps.durationMs.coerceAtLeast(1)
    val shown = if (dragging) dragValue else ps.positionMs.toFloat().coerceIn(0f, duration.toFloat())
    val scheme = MaterialTheme.colorScheme
    val marks = state.markers.barMarks(ps.durationMs / 1000.0)
    val ranges = seekBarRanges(ps.positionMs, ps.durationMs, ps.bufferedMs, state.torrent?.marks.orEmpty(), pc)
    val colors = seekBarColors(ranges.pcEstimated)
    Column {
        // BUG-068: everything is drawn inside the slider's own track (16 dp in M3 Expressive), not as a loose strip under it.
        val playedFraction = (shown / duration.toFloat()).coerceIn(0f, 1f)
        val seekHover = rememberSeekHover()
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it; onInteract() },
            onValueChangeFinished = { session.seekTo(dragValue.toLong()); dragging = false },
            valueRange = 0f..duration.toFloat(),
            enabled = ps.durationMs > 0,
            modifier = Modifier.seekHover(seekHover, enabled = hoverTime && ps.durationMs > 0),
            track = { sliderState ->
                Box(contentAlignment = Alignment.Center) {
                    SliderDefaults.Track(sliderState = sliderState, enabled = ps.durationMs > 0)
                    Canvas(Modifier.fillMaxWidth().height(SEEK_TRACK_DP).clip(RoundedCornerShape(50))) {
                        val w = size.width
                        val h = size.height
                        val r = CornerRadius(SEEK_RUN_CORNER_DP.toPx())
                        // the inactive track starts past the thumb and its gap
                        val from = w * playedFraction + SEEK_THUMB_GAP_DP.toPx()
                        fun run(span: BarSpan, color: androidx.compose.ui.graphics.Color) {
                            val l = maxOf(w * span.left, from)
                            val rgt = minOf(w * span.right, w)
                            if (rgt > l) drawRoundRect(color, Offset(l, 0f), Size(rgt - l, h), r)
                        }
                        // furthest-reaching first: the PC is ahead of what the phone downloaded
                        ranges.pcProcessed?.let { run(it, colors.pc) }
                        ranges.downloaded.forEach { run(it, colors.have) }
                        ranges.buffered?.let { run(it, colors.buffered) }
                        val mh = SEEK_MARK_DP.toPx()
                        marks.forEach { m ->
                            drawRoundRect(
                                colors.mark.copy(alpha = if (m.kind == SkipKind.INTRO) 0.9f else 0.6f),
                                Offset(w * m.left, (h - mh) / 2),
                                Size(w * m.width, mh),
                                CornerRadius(mh / 2),
                            )
                        }
                    }
                }
            },
        )
        SeekHoverBubble(seekHover, ps.durationMs)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Text(formatDuration(shown.toLong() / 1000), color = scheme.onBackground, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (ranges.showLegend) SeekLegend(ranges, colors)
            }
            Text(
                if (ps.durationMs > 0) formatDuration(ps.durationMs / 1000) else "--:--",
                color = scheme.onBackground.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** 0.4.1: the seek bar's run colours, solid (composited over the inactive track) so overlapping runs never mix into a muddy tone. */
private data class SeekBarColors(
    val buffered: androidx.compose.ui.graphics.Color,
    val have: androidx.compose.ui.graphics.Color,
    val pc: androidx.compose.ui.graphics.Color,
    val mark: androidx.compose.ui.graphics.Color,
)

@Composable
private fun seekBarColors(pcEstimated: Boolean): SeekBarColors {
    val scheme = MaterialTheme.colorScheme
    val track = SliderDefaults.colors().inactiveTrackColor
    return SeekBarColors(
        buffered = scheme.primary.copy(alpha = 0.55f).compositeOver(track),
        have = scheme.onSurface.copy(alpha = 0.3f).compositeOver(track),
        // an estimate from the PC's lead (older PC) is drawn a little softer than the PC's own range
        pc = scheme.tertiary.copy(alpha = if (pcEstimated) 0.6f else 0.85f).compositeOver(track),
        mark = scheme.onSurface,
    )
}

/** "■ Descargado  ■ Procesado por la PC" between the two times (one line, shrinks on a narrow phone). */
@Composable
private fun SeekLegend(ranges: SeekBarRanges, colors: SeekBarColors) {
    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        if (ranges.hasDownloaded) LegendItem(colors.buffered, stringResource(Res.string.player_seek_legend_downloaded))
        if (ranges.pcProcessed != null) LegendItem(colors.pc, stringResource(Res.string.player_seek_legend_pc))
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun speedText(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')

/** BUG-060: below this width the top bar moves its secondary actions to a menu and the chips to a row of their own. */
private const val COMPACT_TOP_BAR_DP = 600
private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val HUD_TIMEOUT_MS = 900L
internal const val SEEK_STEP_MS = 10_000L
private const val LOCK_BUTTON_TIMEOUT_MS = 2_500L

/** 0.4.1 seek bar: the M3 Expressive track height, the thumb's half width + its gap, the run corners and the marks' line. */
private val SEEK_TRACK_DP = 16.dp
private val SEEK_THUMB_GAP_DP = 8.dp
private val SEEK_RUN_CORNER_DP = 4.dp
private val SEEK_MARK_DP = 4.dp

/** Size of the play button, the tallest control of the centred transport row. */
private val TRANSPORT_PLAY_DP = 80.dp

/** Space between the transport row and the buffering card above it. */
private val TRANSPORT_GAP_DP = 12.dp

/**
 * BUG-106: lays the element out horizontally centred with its bottom edge [TRANSPORT_GAP_DP] above the centred transport row, whatever
 * its height. Use it on a child of a full-size Box.
 */
private fun Modifier.aboveTransport(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    layout(width, height) {
        val bottom = height / 2 - (TRANSPORT_PLAY_DP / 2 + TRANSPORT_GAP_DP).roundToPx()
        placeable.place((width - placeable.width) / 2, (bottom - placeable.height).coerceAtLeast(0))
    }
}
