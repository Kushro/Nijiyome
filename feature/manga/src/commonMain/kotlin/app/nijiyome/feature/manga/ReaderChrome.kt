// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.AdaptiveSheetScrollColumn
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.ui.patterns.ContextMenuButton
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuDivider
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// The manga reader's chrome in the Sparkles design (artifact screen `lector`): an opaque top bar (back, title and "Cap. 180 · Epílogo
// · Kumo", Capítulos, Ajustes, Pantalla completa, ⋮), a one-row bottom bar (chapter buttons, page slider or auto-scroll, Páginas) and
// the reader's own panel for sheets (docked at the side on wide windows so the page stays visible, a bottom sheet on phones).

/** The bars' surface: opaque; under the PERF motion level it stays plain (no tonal elevation). */
@Composable
internal fun ReaderBarSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (LocalMotion.current.rich) 2.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
internal fun ReaderTopBar(
    title: String,
    subtitle: String,
    fullscreen: Boolean,
    compact: Boolean,
    onBack: () -> Unit,
    onChapters: () -> Unit,
    onSettings: () -> Unit,
    onFullscreen: () -> Unit,
    menu: () -> List<MenuEntry>,
) {
    ReaderBarSurface {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.rd_s11_exit_reader)) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onChapters) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = stringResource(Res.string.manga_reader_chapters)) }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(Res.string.manga_reader_settings)) }
            if (!compact) {
                IconButton(onClick = onFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = stringResource(if (fullscreen) Res.string.rd_s11_fullscreen_exit_key else Res.string.rd_s11_fullscreen_key),
                    )
                }
            }
            ContextMenuButton(entries = menu, contentDescription = stringResource(Res.string.manga_more_options))
        }
    }
}

/** The ⋮ entries of the manga reader (MNG-13): change source, save the page image, reload, fullscreen (phones), all settings. */
@Composable
internal fun mangaReaderMenu(
    compact: Boolean,
    fullscreen: Boolean,
    onSources: () -> Unit,
    onCapture: () -> Unit,
    onReload: () -> Unit,
    onFullscreen: () -> Unit,
    onAllSettings: () -> Unit,
): () -> List<MenuEntry> {
    val switch = stringResource(Res.string.manga_reader_switch_source)
    val capture = stringResource(Res.string.manga_reader_capture)
    val reload = stringResource(Res.string.manga_key_reload)
    val full = stringResource(if (fullscreen) Res.string.manga_reader_exit_fullscreen else Res.string.manga_reader_fullscreen)
    val all = stringResource(Res.string.manga_reader_all_settings)
    return {
        buildList {
            add(MenuAction(switch, onSources, icon = Icons.Outlined.SwapHoriz))
            add(MenuAction(capture, onCapture, icon = Icons.Outlined.Image))
            add(MenuAction(reload, onReload, icon = Icons.Outlined.Refresh, shortcut = "R"))
            if (compact) add(MenuAction(full, onFullscreen, icon = if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen))
            add(MenuDivider)
            add(MenuAction(all, onAllSettings, icon = Icons.Outlined.Settings))
        }
    }
}

/**
 * The bottom bar: previous chapter, then the page slider (paged; laid out right to left in RTL, BUG-112) or the auto-scroll button
 * with its speeds (vertical / webtoon), the pages sheet and the next chapter.
 */
@Composable
internal fun ReaderBottomBar(
    page: Int,
    total: Int,
    paged: Boolean,
    rtl: Boolean,
    compact: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    autoScroll: Boolean,
    autoSpeed: Int,
    onAutoScroll: () -> Unit,
    onAutoSpeed: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
    onPages: () -> Unit,
) {
    ReaderBarSurface {
        Row(Modifier.navigationBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = hasPrevious) { Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(Res.string.rd_s11_previous_chapter_key)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                when {
                    paged && total > 1 -> PageSlider(page, total, rtl, onJump)
                    paged && total == 0 -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp))
                    paged -> Text(stringResource(Res.string.manga_reader_page_of, 1, 1), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    else -> AutoScrollControls(autoScroll, autoSpeed, showSpeeds = !compact || autoScroll, onAutoScroll, onAutoSpeed)
                }
            }
            IconButton(onClick = onPages) { Icon(Icons.Outlined.ViewCarousel, contentDescription = stringResource(Res.string.manga_reader_pages)) }
            IconButton(onClick = onNext, enabled = hasNext) { Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(Res.string.rd_s11_next_chapter_key)) }
        }
    }
}

@Composable
private fun PageSlider(page: Int, total: Int, rtl: Boolean, onJump: (Int) -> Unit) {
    var drag by remember(page, total) { mutableFloatStateOf(page.toFloat()) }
    val outer = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else outer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${drag.toInt() + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            Slider(
                value = drag,
                onValueChange = { drag = it },
                onValueChangeFinished = { onJump(drag.toInt()) },
                valueRange = 0f..(total - 1).toFloat(),
                modifier = Modifier.weight(1f).decor(DecorRegion.PLAYER_SEEK),
            )
            Text("$total", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        }
    }
}

/** Auto-scroll by hand (artifact `cascada`): play / pause and three speeds (Lenta 60, Media 160, Rápida 320 px/s). */
@Composable
private fun AutoScrollControls(on: Boolean, speed: Int, showSpeeds: Boolean, onToggle: () -> Unit, onSpeed: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = stringResource(if (on) Res.string.rd_s11_autoscroll_stop else Res.string.manga_reader_auto_scroll)
        if (on) {
            FilledIconButton(onClick = onToggle) { Icon(Icons.Outlined.Pause, contentDescription = label) }
        } else {
            FilledTonalIconButton(onClick = onToggle) { Icon(Icons.Outlined.PlayArrow, contentDescription = label) }
        }
        if (showSpeeds) {
            AUTO_SPEEDS.forEach { (px, res) ->
                FilterChip(selected = speed == px, onClick = { onSpeed(px) }, label = { Text(stringResource(res)) })
            }
        }
    }
}

private val AUTO_SPEEDS = listOf(60 to Res.string.rd_s11_speed_slow, 160 to Res.string.rd_s11_speed_medium, 320 to Res.string.rd_s11_speed_fast)

/** Auto-scroll speed used when the setting is off and the play button is pressed. */
internal const val DEFAULT_AUTO_SPEED = 160

/** The tap zones drawn over the page (paged): previous / menu / next thirds, mirrored in RTL. */
@Composable
internal fun BoxScope.TapZonesOverlay(rtl: Boolean) {
    val previous = stringResource(Res.string.rd_s11_zone_previous)
    val next = stringResource(Res.string.rd_s11_zone_next)
    Row(Modifier.matchParentSize()) {
        Zone(if (rtl) next else previous)
        Zone(stringResource(Res.string.rd_s11_zone_menu))
        Zone(if (rtl) previous else next)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Zone(label: String) {
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .border(1.dp, MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

/**
 * The reader's panel for its sheets. Wide windows: docked at the end of the reader, full height, without a scrim (the page or the
 * text stays visible, so typography changes are seen live). Phones: a bottom sheet. [pinned] stays above the scrolling content;
 * [scroll] = false when the content scrolls by itself (a lazy grid or list).
 */
@Composable
internal fun BoxScope.ReaderPanel(
    title: String,
    compact: Boolean,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    pinned: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = stringResource(UiR.string.ui_close)) }
        }
    }
    if (compact) {
        // LEC-03: the direction's sheet decor goes on the content, inside the sheet's surface (on the sheet's own modifier it was
        // drawn under the surface and only showed past the rounded corners as stray marks).
        AdaptiveSheet(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().decor(DecorRegion.SHEET)) {
                header()
                pinned?.invoke()
                if (scroll) {
                    AdaptiveSheetScrollColumn(Modifier.padding(horizontal = 16.dp), content = content)
                } else {
                    Column(Modifier.heightIn(max = 520.dp).padding(horizontal = 16.dp), content = content)
                }
            }
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 2.dp,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(400.dp).padding(8.dp),
        ) {
            Column(Modifier.fillMaxSize().decor(DecorRegion.SHEET).statusBarsPadding().navigationBarsPadding()) {
                header()
                pinned?.invoke()
                Column(
                    Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}
