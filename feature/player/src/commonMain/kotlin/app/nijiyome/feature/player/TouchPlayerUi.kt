// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.horizontalFadingEdge
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.model.AspectMode
import app.nijiyome.model.LinkProcessingInfo
import app.nijiyome.model.PlayerChromeStyle
import app.nijiyome.model.SkipKind
import app.nijiyome.model.SourceKind
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.format.formatRate
import app.nijiyome.ui.platform.windowWidthDp
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// v1.2.0 Wave 6: Modern Android touch player controls supporting two user-configurable visual styles:
// - PlayerChromeStyle.CINEMA (edge gradients, slim 4dp->8dp morphing multi-layer seekbar, glass pills)
// - PlayerChromeStyle.MINIMAL (bare vector transport, razor 3dp->7dp seekbar, inline buffer telemetry, icon-first bottom bar)
// Desktop keeps its original pointer/keyboard Controls unchanged.

private val AMBER_SEGMENT = Color(0xFFF5B841)
private val CYAN_PC_LINK = Color(0xFF66D9EF)
private val GLASS_BG = Color(0xFF0E0E15).copy(alpha = 0.62f)
private val GLASS_PILL_BG = Color.White.copy(alpha = 0.13f)
private val GLASS_BORDER = Color.White.copy(alpha = 0.18f)
private val SCRUB_BUBBLE_BG = Color(0xFF12111A).copy(alpha = 0.94f)

/** Cycles the 3 quick phone framing modes: Fit -> Fill -> Stretch -> Fit. */
internal fun nextQuickAspect(current: AspectMode): AspectMode = when (current) {
    AspectMode.FIT -> AspectMode.FILL
    AspectMode.FILL -> AspectMode.STRETCH
    else -> AspectMode.FIT
}

internal fun aspectShortLabel(mode: AspectMode): String = when (mode) {
    AspectMode.FIT -> "Ajustar"
    AspectMode.FILL -> "Rellenar"
    AspectMode.STRETCH -> "Estirar"
    AspectMode.RATIO_16_9 -> "16:9"
    AspectMode.RATIO_4_3 -> "4:3"
}

internal fun skipSegmentLabel(kind: SkipKind): String = when (kind) {
    SkipKind.INTRO -> "Opening"
    SkipKind.OUTRO -> "Ending"
    SkipKind.RECAP -> "Resumen"
}

/** Seconds of buffered playback ahead of the current playhead (torrent ready span or engine buffer). */
internal fun bufferedAheadSecs(positionMs: Long, durationMs: Long, bufferedMs: Long, ranges: SeekBarRanges): Long {
    if (durationMs <= 0L) return 0L
    val fromSpan = ranges.buffered?.let { (it.width * durationMs / 1000.0).roundToLong() }
    val fromEngine = ((bufferedMs - positionMs) / 1000L).coerceAtLeast(0L)
    return (fromSpan ?: fromEngine).coerceAtLeast(0L)
}

internal fun formatSignedDelta(deltaSecs: Long): String {
    val sign = if (deltaSecs >= 0) "+" else "-"
    val total = abs(deltaSecs)
    val m = total / 60
    val s = total % 60
    return if (m > 0) "$sign$m:${s.toString().padStart(2, '0')}" else "${sign}${s}s"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TouchControls(
    style: PlayerChromeStyle,
    session: PlayerSession,
    ps: PlaybackState,
    state: PlayerUiState,
    buffering: Boolean,
    title: String,
    aspectMode: AspectMode,
    onCycleAspect: () -> Unit,
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
    pc: LinkProcessingInfo?,
    capabilities: PlayerCapabilities,
) {
    val minimal = style == PlayerChromeStyle.MINIMAL
    val compact = windowWidthDp() < 600
    val hasKeyboard = hasHardwareKeyboard()
    val scheme = MaterialTheme.colorScheme
    val on = Color.White

    // Edge gradients only: keeps the middle ~40% of the anime frame 100% clear and vibrant.
    val scrimBrush = remember(minimal) {
        if (minimal) {
            Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.72f),
                0.24f to Color.Transparent,
                0.66f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.84f),
            )
        } else {
            Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.76f),
                0.22f to Color.Black.copy(alpha = 0.24f),
                0.42f to Color.Transparent,
                0.58f to Color.Transparent,
                0.76f to Color.Black.copy(alpha = 0.34f),
                1.0f to Color.Black.copy(alpha = 0.86f),
            )
        }
    }

    Box(Modifier.fillMaxSize().background(scrimBrush)) {
        // ---- Top bar ----
        val topChipsScroll = rememberScrollState()
        val topChips: @Composable () -> Unit = {
            Row(
                Modifier.horizontalFadingEdge(topChipsScroll, edgeWidth = 20.dp).horizontalScroll(topChipsScroll),
                Arrangement.spacedBy(6.dp),
                Alignment.CenterVertically,
            ) {
                EnhancementStatusChip(enhancementChip, onClick = { onOpenSheet(Sheet.Enhancements) })
                state.current?.let { cur ->
                    val (n, total) = state.position ?: (1 to 1)
                    TouchPill(
                        onClick = { if (state.canPickSource) onOpenPicker() },
                        icon = if (cur.candidate.kind == SourceKind.TORRENT) Icons.Rounded.Hub else Icons.Rounded.Language,
                        label = stringResource(Res.string.player_source_chip, n, total, "${cur.candidate.extName} · ${cur.candidate.label}"),
                        trailingIcon = if (state.canPickSource) Icons.Rounded.ExpandMore else null,
                        maxLabelWidth = 220.dp,
                    )
                }
                state.torrent?.takeIf { it.fileIndex != null }?.torrentId?.let { id ->
                    TouchPill(
                        onClick = { onOpenTorrent(id) },
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = stringResource(Res.string.player_torrent_open_client),
                    )
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

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .align(Alignment.TopCenter),
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.player_back), tint = on)
                }
                Column(Modifier.weight(1f)) {
                    if (minimal) {
                        val sourceSuffix = state.current?.candidate?.let { " · ${it.extName} ${it.label}" }.orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = title,
                                color = on,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1f), 4f),
                                ),
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (!compact && sourceSuffix.isNotEmpty()) {
                                Text(
                                    text = sourceSuffix,
                                    color = on.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clickable(enabled = state.canPickSource) { onOpenPicker() }
                                        .widthIn(max = 180.dp),
                                )
                            }
                        }
                    } else {
                        Text(
                            title,
                            color = on,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f),
                            ),
                        )
                        if (!compact) {
                            Spacer(Modifier.height(2.dp))
                            topChips()
                        }
                    }
                }
                if (minimal && !compact) {
                    EnhancementStatusChip(enhancementChip, onClick = { onOpenSheet(Sheet.Enhancements) })
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
                            if (state.canPickSource && minimal) {
                                DropdownMenuItem(
                                    text = { Text("Cambiar fuente") },
                                    onClick = { menu = false; onOpenPicker() },
                                    leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                                )
                            }
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
                    LocalDownloadArgs.current?.let { a -> DownloadAction(a, state.current?.candidate?.toStream(), state.current?.candidate?.label.orEmpty()) }
                    if (capabilities.screenLock) IconButton(onClick = onLock) { Icon(Icons.Rounded.LockOpen, stringResource(Res.string.player_lock), tint = on) }
                    if (capabilities.screenshot) {
                        IconButton(onClick = onScreenshot, enabled = ps.started) { Icon(Icons.Rounded.PhotoCamera, stringResource(Res.string.player_screenshot), tint = on) }
                    }
                    if (state.remoteless && !state.looseFile) {
                        IconButton(onClick = { onOpenSheet(Sheet.Episodes) }) { Icon(Icons.Rounded.FormatListNumbered, stringResource(Res.string.player_episodes), tint = on) }
                    }
                    if (!minimal) {
                        IconButton(onClick = { onOpenSheet(Sheet.Enhancements) }) { Icon(Icons.Rounded.AutoAwesome, stringResource(Res.string.player_fx_title), tint = on) }
                    }
                    if (hasKeyboard) IconButton(onClick = onHelp) { Icon(Icons.Rounded.Keyboard, stringResource(Res.string.player_shortcuts), tint = on) }
                    pipAndFullscreen()
                }
            }
            if (compact) {
                Box(Modifier.padding(start = 48.dp, top = 2.dp)) { topChips() }
            }
        }

        // ---- Center transport ----
        Row(
            Modifier.align(Alignment.Center),
            Arrangement.spacedBy(if (minimal) 34.dp else 22.dp),
            Alignment.CenterVertically,
        ) {
            if (state.hasPrevEpisode) {
                IconButton(onClick = { onPrev(); onInteract() }) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(Res.string.player_prev), tint = on, modifier = Modifier.size(28.dp))
                }
            }
            if (minimal) {
                IconButton(onClick = { session.seekBy(-capabilities.seekStepMs); onInteract() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Replay10, stringResource(Res.string.player_seek_back), tint = on, modifier = Modifier.size(34.dp))
                }
                val playLabel = stringResource(if (ps.isPlaying) Res.string.player_pause else Res.string.player_play)
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable(enabled = ps.started) { session.togglePlay(); onInteract() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (buffering) {
                        LoadingIndicator(Modifier.size(44.dp).semantics { contentDescription = playLabel }, color = on)
                    } else {
                        Icon(
                            if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            playLabel,
                            tint = on,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                IconButton(onClick = { session.seekBy(capabilities.seekStepMs); onInteract() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Forward10, stringResource(Res.string.player_seek_forward), tint = on, modifier = Modifier.size(34.dp))
                }
            } else {
                // Cinema glass circular transport
                Surface(
                    onClick = { session.seekBy(-capabilities.seekStepMs); onInteract() },
                    shape = CircleShape,
                    color = GLASS_BG,
                    border = BorderStroke(1.dp, GLASS_BORDER),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Replay10, stringResource(Res.string.player_seek_back), tint = on, modifier = Modifier.size(24.dp))
                    }
                }
                val playLabel = stringResource(if (ps.isPlaying) Res.string.player_pause else Res.string.player_play)
                Surface(
                    onClick = { session.togglePlay(); onInteract() },
                    enabled = ps.started,
                    shape = CircleShape,
                    color = scheme.primary.copy(alpha = 0.94f),
                    contentColor = scheme.onPrimary,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(62.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (buffering) {
                            LoadingIndicator(Modifier.size(38.dp).semantics { contentDescription = playLabel }, color = scheme.onPrimary)
                        } else {
                            Icon(if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, playLabel, Modifier.size(34.dp))
                        }
                    }
                }
                Surface(
                    onClick = { session.seekBy(capabilities.seekStepMs); onInteract() },
                    shape = CircleShape,
                    color = GLASS_BG,
                    border = BorderStroke(1.dp, GLASS_BORDER),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Forward10, stringResource(Res.string.player_seek_forward), tint = on, modifier = Modifier.size(24.dp))
                    }
                }
            }
            if (state.hasNextEpisode && state.remoteless) {
                IconButton(onClick = { onNext(); onInteract() }) {
                    Icon(Icons.Rounded.SkipNext, stringResource(Res.string.player_next), tint = on, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (buffering) {
            BufferingOverlay(state, Modifier.aboveTransport().padding(horizontal = 16.dp), showIndicator = false)
        }

        // ---- Bottom seek bar + actions ----
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (minimal) 4.dp else 8.dp),
        ) {
            TouchSeekBar(
                session = session,
                ps = ps,
                state = state,
                pc = pc,
                minimal = minimal,
                onInteract = onInteract,
            )

            val activeAudioLang = ps.audioTracks.firstOrNull { it.selected }?.language?.takeIf { it.isNotBlank() }?.uppercase()
            val activeSubLang = ps.subtitles.firstOrNull { it.selected }?.language?.takeIf { it.isNotBlank() }?.uppercase()
            val sourceQuality = if (capabilities.sourceQualities && ps.videoHeights.isEmpty()) sourceQualityChip(state) else null
            val qualityLabel = sourceQuality ?: ps.selectedHeight?.let { stringResource(Res.string.player_quality_height, it) }
            val hasQuality = ps.videoHeights.isNotEmpty() || sourceQuality != null

            if (minimal) {
                MinimalBottomBar(
                    compact = compact,
                    ps = ps,
                    state = state,
                    activeAudioLang = activeAudioLang,
                    activeSubLang = activeSubLang,
                    hasQuality = hasQuality,
                    qualityLabel = qualityLabel ?: stringResource(Res.string.player_quality),
                    aspectMode = aspectMode,
                    onCycleAspect = { onCycleAspect(); onInteract() },
                    onOpenSheet = onOpenSheet,
                    onNext = onNext,
                )
            } else {
                val actionsScroll = rememberScrollState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalFadingEdge(actionsScroll, edgeWidth = 24.dp)
                        .horizontalScroll(actionsScroll),
                    Arrangement.spacedBy(8.dp),
                    Alignment.CenterVertically,
                ) {
                    if (ps.audioTracks.isNotEmpty() || ps.started) {
                        val audioLabel = if (activeAudioLang != null) "${stringResource(Res.string.player_audio)} · $activeAudioLang" else stringResource(Res.string.player_audio)
                        TouchPill(
                            onClick = { onOpenSheet(Sheet.Audio) },
                            icon = Icons.Rounded.Audiotrack,
                            label = audioLabel,
                        )
                    }
                    if (ps.subtitles.isNotEmpty() || ps.started) {
                        val subLabel = if (activeSubLang != null && ps.subtitlesEnabled) "${stringResource(Res.string.player_subtitles)} · $activeSubLang" else stringResource(Res.string.player_subtitles)
                        TouchPill(
                            onClick = { onOpenSheet(Sheet.Subtitles) },
                            icon = Icons.Rounded.Subtitles,
                            label = subLabel,
                        )
                    }
                    if (hasQuality) {
                        TouchPill(
                            onClick = { onOpenSheet(Sheet.Quality) },
                            icon = Icons.Rounded.HighQuality,
                            label = qualityLabel ?: stringResource(Res.string.player_quality),
                        )
                    }
                    TouchPill(
                        onClick = { onOpenSheet(Sheet.Speed) },
                        icon = Icons.Rounded.Speed,
                        label = stringResource(Res.string.player_speed_value, speedText(ps.speed)),
                        contentDesc = stringResource(Res.string.player_speed_desc, speedText(ps.speed)),
                    )
                    TouchPill(
                        onClick = { onCycleAspect(); onInteract() },
                        icon = Icons.Rounded.AspectRatio,
                        label = aspectShortLabel(aspectMode),
                        highlighted = aspectMode != AspectMode.FIT,
                    )
                    if (state.hasNextEpisode && state.remoteless) {
                        TouchPill(
                            onClick = onNext,
                            icon = Icons.Rounded.SkipNext,
                            label = stringResource(
                                if (state.torrent?.batch == true) Res.string.player_torrent_batch_next else Res.string.player_next,
                            ),
                        )
                    }
                    TouchPill(
                        onClick = { onOpenSheet(Sheet.More) },
                        icon = Icons.Rounded.MoreHoriz,
                        label = stringResource(Res.string.player_more),
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalBottomBar(
    compact: Boolean,
    ps: PlaybackState,
    state: PlayerUiState,
    activeAudioLang: String?,
    activeSubLang: String?,
    hasQuality: Boolean,
    qualityLabel: String,
    aspectMode: AspectMode,
    onCycleAspect: () -> Unit,
    onOpenSheet: (Sheet) -> Unit,
    onNext: () -> Unit,
) {
    val on = Color.White.copy(alpha = 0.92f)
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.horizontalFadingEdge(scroll, edgeWidth = 20.dp).horizontalScroll(scroll) else Modifier),
        horizontalArrangement = if (compact) Arrangement.spacedBy(14.dp) else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ps.audioTracks.isNotEmpty() || ps.started) {
                MinimalActionItem(Icons.Rounded.Audiotrack, activeAudioLang ?: "Audio", on) { onOpenSheet(Sheet.Audio) }
            }
            if (ps.subtitles.isNotEmpty() || ps.started) {
                MinimalActionItem(Icons.Rounded.Subtitles, if (ps.subtitlesEnabled) activeSubLang ?: "Sub" else "Off", on) { onOpenSheet(Sheet.Subtitles) }
            }
            MinimalActionItem(Icons.Rounded.Speed, stringResource(Res.string.player_speed_value, speedText(ps.speed)), on) { onOpenSheet(Sheet.Speed) }
            if (hasQuality) {
                MinimalActionItem(Icons.Rounded.HighQuality, qualityLabel, on) { onOpenSheet(Sheet.Quality) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MinimalActionItem(
                Icons.Rounded.AspectRatio,
                if (aspectMode == AspectMode.FIT) null else aspectShortLabel(aspectMode),
                if (aspectMode == AspectMode.FIT) on else MaterialTheme.colorScheme.primary,
                onCycleAspect,
            )
            IconButton(onClick = { onOpenSheet(Sheet.Enhancements) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.AutoAwesome, stringResource(Res.string.player_fx_title), tint = on, modifier = Modifier.size(20.dp))
            }
            if (state.hasNextEpisode && state.remoteless) {
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SkipNext, stringResource(Res.string.player_next), tint = on, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = { onOpenSheet(Sheet.More) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.MoreHoriz, stringResource(Res.string.player_more), tint = on, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MinimalActionItem(icon: ImageVector, label: String?, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        if (label != null) {
            Text(label, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun TouchPill(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    trailingIcon: ImageVector? = null,
    highlighted: Boolean = false,
    contentDesc: String? = null,
    maxLabelWidth: androidx.compose.ui.unit.Dp = 160.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (highlighted) scheme.primary.copy(alpha = 0.24f) else GLASS_PILL_BG
    val border = if (highlighted) scheme.primary.copy(alpha = 0.45f) else GLASS_BORDER
    val fg = if (highlighted) scheme.primary else Color.White
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.semantics {
            role = Role.Button
            if (contentDesc != null) contentDescription = contentDesc
        },
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxLabelWidth),
            )
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = fg.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---- Multi-layer continuous custom seekbar with tactile scrub preview bubble ----

@Composable
internal fun TouchSeekBar(
    session: PlayerSession,
    ps: PlaybackState,
    state: PlayerUiState,
    pc: LinkProcessingInfo?,
    minimal: Boolean,
    onInteract: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragStartMs by remember { mutableLongStateOf(0L) }
    var showRemaining by remember { mutableStateOf(false) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val duration = ps.durationMs.coerceAtLeast(1L)
    val enabled = ps.durationMs > 0L
    val liveFraction = (ps.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val shownFraction = if (dragging) dragFraction else liveFraction
    val shownMs = if (dragging) (dragFraction * duration).roundToLong() else ps.positionMs.coerceIn(0L, duration)

    val marks = state.markers.barMarks(ps.durationMs / 1000.0)
    val ranges = seekBarRanges(ps.positionMs, ps.durationMs, ps.bufferedMs, state.torrent?.marks.orEmpty(), pc)
    val aheadSecs = bufferedAheadSecs(ps.positionMs, ps.durationMs, ps.bufferedMs, ranges)
    val torrentSpeed = state.torrent?.takeIf { it.downBps > 0L }?.let { formatRate(it.downBps) }
    val scheme = MaterialTheme.colorScheme

    val trackHeight by animateDpAsState(
        targetValue = when {
            minimal && dragging -> 7.dp
            minimal -> 3.5.dp
            dragging -> 8.dp
            else -> 4.5.dp
        },
        label = "touchSeekTrackHeight",
    )
    val thumbRadius by animateDpAsState(
        targetValue = when {
            minimal && dragging -> 8.dp
            minimal -> 5.5.dp
            dragging -> 9.dp
            else -> 6.5.dp
        },
        label = "touchSeekThumbRadius",
    )

    val density = LocalDensity.current

    Column(Modifier.fillMaxWidth()) {
        // Minimal mode places compact timestamp + telemetry cleanly above the razor bar.
        if (minimal) {
            MinimalTimeTelemetryRow(
                shownMs = shownMs,
                durationMs = ps.durationMs,
                aheadSecs = aheadSecs,
                torrentSpeed = torrentSpeed,
                showRemaining = showRemaining,
                onToggleRemaining = { showRemaining = !showRemaining; onInteract() },
            )
        }

        // Tactile floating scrub preview bubble while dragging
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            if (dragging && enabled) {
                val deltaSecs = (shownMs - dragStartMs) / 1000L
                val activeSkip = state.markers.skipAt(shownMs / 1000.0)?.first
                var bubbleWidthPx by remember { mutableFloatStateOf(160f) }
                val centerX = shownFraction * maxW
                val xOffset = (centerX - bubbleWidthPx / 2f).coerceIn(0f, (maxW - bubbleWidthPx).coerceAtLeast(0f))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SCRUB_BUBBLE_BG,
                    border = BorderStroke(1.dp, GLASS_BORDER),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .offset { IntOffset(xOffset.roundToInt(), with(density) { (-6).dp.roundToPx() }) }
                        .onSizeChanged { bubbleWidthPx = it.width.toFloat() },
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatDuration(shownMs / 1000L),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            formatSignedDelta(deltaSecs),
                            color = scheme.primary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            fontFamily = FontFamily.Monospace,
                        )
                        if (activeSkip != null) {
                            Surface(shape = RoundedCornerShape(5.dp), color = AMBER_SEGMENT.copy(alpha = 0.24f)) {
                                Text(
                                    skipSegmentLabel(activeSkip),
                                    color = AMBER_SEGMENT,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        } else if (aheadSecs >= 3L) {
                            Text(
                                "Buffer +${aheadSecs}s",
                                color = CYAN_PC_LINK,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        // Interactive continuous Canvas seekbar (34dp hit target, zero 8dp thumb gap!)
        Box(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                        session.seekTo((frac * duration).roundToLong())
                        onInteract()
                    }
                }
                .pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragStartMs = session.currentPositionMs()
                            dragFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            dragging = true
                            onInteract()
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                            onInteract()
                        },
                        onDragEnd = {
                            if (dragging) {
                                session.seekTo((dragFraction * duration).roundToLong())
                                dragging = false
                            }
                        },
                        onDragCancel = { dragging = false },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val w = size.width
                val cy = size.height / 2f
                val th = trackHeight.toPx()
                val top = cy - th / 2f
                val cr = CornerRadius(th / 2f)

                // 1. Base inactive track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(0f, top),
                    size = Size(w, th),
                    cornerRadius = cr,
                )

                fun drawSpan(span: BarSpan, color: Color) {
                    val l = (w * span.left).coerceIn(0f, w)
                    val r = (w * span.right).coerceIn(0f, w)
                    if (r > l) {
                        drawRoundRect(color = color, topLeft = Offset(l, top), size = Size(r - l, th), cornerRadius = cr)
                    }
                }

                // 2. PC Link processed range (furthest layer)
                ranges.pcProcessed?.let {
                    drawSpan(it, CYAN_PC_LINK.copy(alpha = if (ranges.pcEstimated) 0.40f else 0.58f))
                }
                // 3. Torrent downloaded pieces across the file
                ranges.downloaded.forEach {
                    drawSpan(it, Color.White.copy(alpha = 0.34f))
                }
                // 4. Engine / torrent ready buffer continuously from playhead (NO 8dp gap!)
                ranges.buffered?.let {
                    drawSpan(it, Color.White.copy(alpha = 0.62f))
                }

                // 5. Played progress in vivid primary
                val playedW = (w * shownFraction).coerceIn(0f, w)
                if (playedW > 0f) {
                    if (dragging) {
                        drawRoundRect(
                            color = scheme.primary.copy(alpha = 0.28f),
                            topLeft = Offset(0f, top - 2.dp.toPx()),
                            size = Size(playedW, th + 4.dp.toPx()),
                            cornerRadius = CornerRadius((th + 4.dp.toPx()) / 2f),
                        )
                    }
                    drawRoundRect(
                        color = scheme.primary,
                        topLeft = Offset(0f, top),
                        size = Size(playedW, th),
                        cornerRadius = cr,
                    )
                }

                // 6. Opening / Ending / Recap markers in amber directly on the track
                marks.forEach { m ->
                    val ml = (w * m.left).coerceIn(0f, w)
                    val mw = (w * m.width).coerceAtLeast(3.dp.toPx()).coerceAtMost(w - ml)
                    drawRoundRect(
                        color = AMBER_SEGMENT.copy(alpha = if (m.kind == SkipKind.INTRO) 0.94f else 0.78f),
                        topLeft = Offset(ml, top),
                        size = Size(mw, th),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }

                // 7. Thumb knob with soft halo
                val r = thumbRadius.toPx()
                val cx = playedW.coerceIn(r, (w - r).coerceAtLeast(r))
                drawCircle(
                    color = scheme.primary.copy(alpha = if (dragging) 0.32f else 0.20f),
                    radius = r + if (dragging) 5.dp.toPx() else 3.dp.toPx(),
                    center = Offset(cx, cy),
                )
                drawCircle(color = scheme.primary, radius = r, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = (r - 2.2.dp.toPx()).coerceAtLeast(2.dp.toPx()), center = Offset(cx, cy))
            }
        }

        // Cinema mode places timestamp + compact buffer/Link/OP-ED legend cleanly underneath.
        if (!minimal) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                Arrangement.spacedBy(8.dp),
                Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clickable { showRemaining = !showRemaining; onInteract() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatDuration(shownMs / 1000L),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        if (ps.durationMs > 0L) {
                            if (showRemaining) "  -${formatDuration(((ps.durationMs - shownMs).coerceAtLeast(0L)) / 1000L)}"
                            else " / ${formatDuration(ps.durationMs / 1000L)}"
                        } else " / --:--",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (aheadSecs >= 2L) {
                            TouchLegendDot(Color.White.copy(alpha = 0.72f), "Buffer +${aheadSecs}s")
                        }
                        if (ranges.pcProcessed != null) {
                            TouchLegendDot(CYAN_PC_LINK, "PC")
                        }
                        if (marks.isNotEmpty()) {
                            TouchLegendDot(AMBER_SEGMENT, "OP/ED")
                        }
                    }
                }

                if (torrentSpeed != null) {
                    Text(
                        torrentSpeed,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalTimeTelemetryRow(
    shownMs: Long,
    durationMs: Long,
    aheadSecs: Long,
    torrentSpeed: String?,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(shownMs / 1000L),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    if (durationMs > 0L) " / ${formatDuration(durationMs / 1000L)}" else " / --:--",
                    color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            val telemetry = buildList {
                if (aheadSecs >= 2L) add("Buffer +${aheadSecs}s")
                if (torrentSpeed != null) add(torrentSpeed)
            }.joinToString(" · ")
            if (telemetry.isNotEmpty()) {
                Text(
                    telemetry,
                    color = CYAN_PC_LINK.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
        if (durationMs > 0L) {
            val rem = ((durationMs - shownMs).coerceAtLeast(0L)) / 1000L
            Text(
                if (showRemaining) formatDuration(durationMs / 1000L) else "-${formatDuration(rem)}",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onToggleRemaining).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TouchLegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = Color.White.copy(alpha = 0.80f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// ---- Rich touch gesture overlays: vertical side bars, double-tap ripple arcs, horizontal scrub HUD ----

@Composable
internal fun TouchGestureOverlay(hud: Hud, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (hud.kind) {
            Hud.Kind.Brightness -> VerticalGestureRail(
                icon = Icons.Rounded.BrightnessMedium,
                percent = hud.percent,
                muted = false,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
            )
            Hud.Kind.Volume -> VerticalGestureRail(
                icon = if (hud.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                percent = if (hud.muted) 0 else hud.percent,
                muted = hud.muted,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
            )
            Hud.Kind.DoubleTapSeek -> DoubleTapSeekArc(
                seconds = hud.seconds,
                onLeft = hud.doubleTapSide < 0,
                modifier = Modifier.fillMaxSize(),
            )
            Hud.Kind.Scrub -> HorizontalScrubHud(
                hud = hud,
                modifier = Modifier.align(Alignment.Center),
            )
            Hud.Kind.Aspect -> Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp),
                shape = CircleShape,
                color = SCRUB_BUBBLE_BG,
                border = BorderStroke(1.dp, GLASS_BORDER),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AspectRatio, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(hud.label.orEmpty(), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            else -> HudPill(hud, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp))
        }
    }
}

@Composable
private fun VerticalGestureRail(icon: ImageVector, percent: Int, muted: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = SCRUB_BUBBLE_BG,
        border = BorderStroke(1.dp, GLASS_BORDER),
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (muted) Color.White.copy(alpha = 0.55f) else scheme.primary, modifier = Modifier.size(18.dp))
            Box(
                Modifier
                    .width(5.dp)
                    .height(104.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
            Text(
                if (muted) "0%" else "$percent%",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun DoubleTapSeekArc(seconds: Int, onLeft: Boolean, modifier: Modifier = Modifier) {
    val shape = if (onLeft) {
        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    } else {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    }
    Box(modifier) {
        Box(
            Modifier
                .align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight(0.74f)
                .fillMaxWidth(0.36f)
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        if (onLeft) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))
                        else listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.22f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (onLeft) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    if (seconds >= 0) "+${seconds}s" else "${seconds}s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun HorizontalScrubHud(hud: Hud, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dur = hud.durationMs.coerceAtLeast(1L)
    val playedFrac = (hud.targetMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    val bufFrac = (hud.bufferedMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SCRUB_BUBBLE_BG,
        border = BorderStroke(1.dp, GLASS_BORDER),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.width(210.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(hud.targetMs / 1000L),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    formatSignedDelta(hud.seconds.toLong()),
                    color = scheme.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (hud.durationMs > 0L) {
                Canvas(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)) {
                    val w = size.width
                    val h = size.height
                    drawRect(Color.White.copy(alpha = 0.20f))
                    if (bufFrac > playedFrac) {
                        drawRect(Color.White.copy(alpha = 0.52f), Offset(w * playedFrac, 0f), Size(w * (bufFrac - playedFrac), h))
                    }
                    drawRect(scheme.primary, Offset.Zero, Size(w * playedFrac, h))
                }
            }
        }
    }
}
