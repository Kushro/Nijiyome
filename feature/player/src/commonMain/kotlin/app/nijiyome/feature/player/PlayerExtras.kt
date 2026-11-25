// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.model.PlayerStructure
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.ui.look.structures
import androidx.compose.material.icons.rounded.Settings
import kotlinx.coroutines.launch
import app.nijiyome.player.CueTimeline
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.SleepOption
import app.nijiyome.player.SubtitleDelay
import app.nijiyome.player.ScreenSubtitleSpec
import app.nijiyome.player.SubtitleParser
import app.nijiyome.player.SubtitleText
import app.nijiyome.player.TimedCue
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.delay
import java.io.File
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// parity wave A (docs/redesign/15 §3.2 / §5 p, worker p): the player's new sheets, panels and helpers. Pure UI over PlayerViewModel.

// ---- keyboard shortcuts (player.shortcuts.*) ------------------------------------------------------------------------------------------

/** The `?` panel: every action with its current key (player.shortcuts.overlay). */
@Composable
internal fun ShortcutsPanel(keymap: Map<String, String>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val extras = playerCapabilities.desktopShortcuts
    val resolved = PlayerKeymap.resolve(keymap, extras)
    Surface(modifier.widthIn(max = 460.dp).padding(24.dp), shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.player_shortcuts), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(Res.string.player_shortcuts_close)) }
            }
            // Picture in picture is listed only where the platform has it.
            PlayerKeymap.actions(extras).filter { it != PlayerKeymap.PIP || playerCapabilities.pictureInPicture }.forEach { action ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(actionLabel(action), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(8.dp), color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
                        Text(keyLabel(resolved.getValue(action)), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            // Hito 6 (desktop): the fixed Ctrl+1..6 upscaling presets of Tatakai Next
            if (extras) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.player_key_ctrl_presets), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(8.dp), color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
                        Text("Ctrl+1…6", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// ---- subtitles drawn by the screen ------------------------------------------------------------------------------------------------------

/** Position of the session sampled every 100 ms (subtitle timing needs more than the 500 ms state tick). */
@Composable
internal fun rememberFinePosition(session: PlayerSession, active: Boolean): Long {
    var pos by remember(session) { mutableLongStateOf(session.currentPositionMs()) }
    LaunchedEffect(session, active) {
        while (active) {
            pos = session.currentPositionMs()
            delay(SUBTITLE_TICK_MS)
        }
    }
    return pos
}

/**
 * Every subtitle the screen draws: Media3's primary (engine cues, or parsed external / loaded cues with the exact delay), the secondary
 * track (Media3) at the top, and the translated line when translation is on (Media3 and mpv; mpv then hides its own text).
 */
@Composable
internal fun ScreenSubtitleLayer(
    vm: PlayerViewModel,
    session: PlayerSession,
    ps: PlaybackState,
    style: SubtitleStyle,
    fontFile: File?,
    /** Room the visible controls need at the bottom (0 when hidden); the style's bottom margin applies when it is larger. */
    controlsPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val subs by vm.screenSubs.collectAsState()
    val state by vm.state.collectAsState()
    val translating = vm.translationActive()
    val media3 = session.isMedia3
    val needsTick = media3 || translating
    val pos = rememberFinePosition(session, needsTick)
    val delayMs = ps.subtitleDelayMs

    // Media3 engine cues, recorded for the positive delay of in-stream tracks.
    val timeline = remember(session, ps.loadId) { CueTimeline() }
    val engineLines: List<String> = if (media3) {
        val texts = media3CueTexts(session)
        LaunchedEffect(texts) { timeline.record(session.currentPositionMs(), texts) }
        if (delayMs > 0) timeline.linesAt(SubtitleDelay.lookupPositionMs(pos, delayMs)) else texts
    } else {
        emptyList()
    }
    val primary: List<String> = when {
        subs.primary != null && media3 -> SubtitleParser.activeAt(subs.primary!!, SubtitleDelay.lookupPositionMs(pos, delayMs))
        media3 -> if (ps.subtitlesEnabled || state.customSubtitle != null) engineLines else emptyList()
        translating -> listOfNotNull(ps.subText)
        else -> emptyList()
    }
    LaunchedEffect(translating, media3) { if (!media3) vm.onTranslationDrawing(translating) }
    LaunchedEffect(primary, translating) { if (translating) vm.translateLines(primary) }
    // 0.4.0 (sub-translation): per-event lookup (mpv joins simultaneous events in one `sub-text`), and leftover ASS vector drawings
    // ("m 0 0 l 100 0 ...", what engines leave of `{\p1}` signs) are never drawn as text.
    val shown = if (translating) {
        primary.mapNotNull { line -> SubtitleText.lookup(subs.translated, line) ?: SubtitleText.normalize(line).takeIf { it.isNotEmpty() } }
    } else {
        primary.filterNot { SubtitleText.isDrawingLike(it.trim()) }
    }
    val secondary = subs.secondary?.let { SubtitleParser.activeAt(it, SubtitleDelay.lookupPositionMs(pos, delayMs)) }.orEmpty()

    // 0.4.1: sizes, margins and position follow the video area like libmpv's (ScreenSubtitleSpec), so both engines match.
    // The size unit is the short side of the player (the height in landscape, unchanged there): in portrait the full height
    // (~900 dp) made every line about 2.5x too big. The block is still anchored to the screen's bottom edge, not the video's.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val spec = ScreenSubtitleSpec.of(style, minOf(maxWidth, maxHeight).value)
        val family = rememberSubtitleFontFamily(style.font, fontFile, style.fontFile)
        val sides = spec.marginXDp.dp
        if (secondary.isNotEmpty()) {
            SubtitleLines(secondary, spec, family, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp, start = sides, end = sides))
        }
        if (shown.isNotEmpty()) {
            val lift = maxHeight * spec.liftFraction
            val bottom = maxOf(controlsPadding, spec.marginYDp.dp)
            SubtitleLines(shown, spec, family, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + lift, start = sides, end = sides))
        }
    }
}

/** Pre-translation progress over the paused video, with "Play now". */
@Composable
internal fun PretranslateOverlay(progress: Pair<Int, Int>, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.widthIn(max = 420.dp).padding(24.dp), shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Translate, contentDescription = null, tint = scheme.primary)
            Text(stringResource(Res.string.player_pretranslating, progress.first, progress.second), textAlign = TextAlign.Center)
            LinearProgressIndicator(
                progress = { if (progress.second > 0) progress.first.toFloat() / progress.second else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onSkip) { Text(stringResource(Res.string.player_pretranslate_skip)) }
        }
    }
}

// ---- sheets -----------------------------------------------------------------------------------------------------------------------------

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** Episode list over the player (player.jump-to-episode): loading / error / empty states, the current one marked. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EpisodesSheetContent(vm: PlayerViewModel, state: PlayerUiState, current: Int, onPick: (Int) -> Unit) {
    LaunchedEffect(Unit) { vm.loadEpisodes() }
    val eps = state.episodes
    Column(Modifier.heightIn(min = 200.dp, max = 560.dp)) {
        SheetTitle(stringResource(Res.string.player_episodes))
        when {
            eps.loading -> {
                val show = rememberAfterDelay(150)
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { if (show) LoadingIndicator(Modifier.size(48.dp)) }
            }
            eps.error != null -> ErrorState(
                message = eps.error.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = { vm.loadEpisodes(force = true) },
                title = stringResource(Res.string.player_episodes_error),
                compact = true,
            )
            eps.loaded && eps.items.isEmpty() -> EmptyState(icon = Icons.Rounded.PlayArrow, title = stringResource(Res.string.player_episodes_empty))
            else -> LazyColumn {
                items(eps.items, key = { it.number }) { e ->
                    val title = e.title?.takeIf { it.isNotBlank() }
                    ListItem(
                        headlineContent = {
                            Text(
                                if (title != null) stringResource(Res.string.player_episode_row_title, e.number, title) else stringResource(Res.string.player_episode_row, e.number),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = if (e.number == current) ({ Text(stringResource(Res.string.player_episode_current)) }) else null,
                        trailingContent = { if (e.number == current) Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = e.number != current) { onPick(e.number) },
                    )
                }
            }
        }
    }
}

/** Locked screen (player.android.screen-lock): only the lock button, which hides after a moment and comes back on tap. */
@Composable
internal fun LockOverlay(visible: Boolean, onShow: () -> Unit, onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { onShow() },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (visible) {
            Column(Modifier.padding(start = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onUnlock) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.player_unlock), Modifier.padding(start = 8.dp))
                }
                Text(stringResource(Res.string.player_locked_hint), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

internal const val SUBTITLE_TICK_MS = 100L
