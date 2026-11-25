// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.chrome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.RegionData
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.feature.player.SeekHoverBubble
import app.nijiyome.feature.player.bufferedAheadSecs
import app.nijiyome.feature.player.formatSignedDelta
import app.nijiyome.feature.player.rememberSeekHover
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_seek_legend_downloaded
import app.nijiyome.feature.player.resources.player_seek_legend_pc
import app.nijiyome.feature.player.resources.rd_s5_buffer_ahead
import app.nijiyome.feature.player.resources.rd_s5_legend_marks
import app.nijiyome.feature.player.resources.rd_s5_seek_label
import app.nijiyome.feature.player.resources.rd_s5_seek_value
import app.nijiyome.feature.player.resources.rd_s5_seek_value_chapter
import app.nijiyome.feature.player.resources.rd_s5_seg_ending
import app.nijiyome.feature.player.resources.rd_s5_seg_opening
import app.nijiyome.feature.player.resources.rd_s5_seg_recap
import app.nijiyome.feature.player.resources.rd_s5_time_toggle
import app.nijiyome.feature.player.seekBarRanges
import app.nijiyome.feature.player.seekHover
import app.nijiyome.model.SkipKind
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.format.formatRate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Sparkles S5: the one seek bar of every structure (artifact `seekBar`, region `player-seek`). The track is cut into chapters at the
// intro / ending / recap marks (warn tone), carries the engine buffer and a torrent's pieces ahead of the playhead and, while the PC
// processes (Link PC / Mixto), a thin info line under it with what the PC already made. Pointer: the time and chapter under the mouse
// in a bubble. Touch: tap jumps, drag previews with the time, the difference and the chapter. Colours come from the scheme and the
// direction's signal colours; nothing here asks which direction is active.

/** The seek bar. [thin] = the Mínimo / Panel bar (a slimmer track and thumb). */
@Composable
internal fun PlayerSeekBar(
    model: PlayerChromeModel,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
    thin: Boolean = false,
) {
    val session = model.session
    val ps = model.ps
    val state = model.state
    val seek = model.seek
    val touch = model.capabilities.touchFirst
    val duration = ps.durationMs.coerceAtLeast(1L)
    val enabled = ps.durationMs > 0L
    val shownMs = seek.shownMs(ps)
    val shownFraction = (shownMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val marks = state.markers.barMarks(ps.durationMs / 1000.0)
    val ranges = seekBarRanges(ps.positionMs, ps.durationMs, ps.bufferedMs, state.torrent?.marks.orEmpty(), model.pc)
    val scheme = MaterialTheme.colorScheme
    val signal = LocalSignalColors.current
    val accent = scheme.primary
    val text = scheme.onSurface
    val hover = rememberSeekHover()
    val active = seek.dragging || hover.fraction != null
    val animated = LocalMotion.current.level != MotionLevel.OFF
    val base = if (thin) 4.dp else 5.dp
    val trackHeight by animateDpAsState(
        targetValue = if (active) base + 4.dp else base,
        animationSpec = if (animated) tween(120) else snap(),
        label = "seekTrack",
    )
    val thumbRadius by animateDpAsState(
        targetValue = (if (thin) 6.dp else 8.dp) + (if (seek.dragging) 2.dp else 0.dp),
        animationSpec = if (animated) tween(120) else snap(),
        label = "seekThumb",
    )
    var barWidth by remember { mutableFloatStateOf(1f) }
    var barHeight by remember { mutableIntStateOf(0) }

    val opening = stringResource(Res.string.rd_s5_seg_opening)
    val ending = stringResource(Res.string.rd_s5_seg_ending)
    val recap = stringResource(Res.string.rd_s5_seg_recap)
    val chapterAt: (Long) -> String? = { ms ->
        when (state.markers.skipAt(ms / 1000.0)?.first) {
            SkipKind.INTRO -> opening
            SkipKind.OUTRO -> ending
            SkipKind.RECAP -> recap
            null -> null
        }
    }
    val seekLabel = stringResource(Res.string.rd_s5_seek_label)
    val position = formatDuration(shownMs / 1000)
    val total = if (enabled) formatDuration(ps.durationMs / 1000) else "--:--"
    val chapterNow = chapterAt(shownMs)
    val valueText = if (chapterNow != null) {
        stringResource(Res.string.rd_s5_seek_value_chapter, position, total, chapterNow)
    } else {
        stringResource(Res.string.rd_s5_seek_value, position, total)
    }

    // Chapter starts for directions that mark them on the bar: where each marked segment begins and where it ends (the next one starts).
    val chapters = remember(marks) {
        marks.flatMap { listOf(it.left, it.left + it.width) }.filter { it > 0.002f && it < 0.998f }.distinct().sorted()
    }
    Column(modifier.decor(DecorRegion.PLAYER_SEEK, RegionData(chapterFractions = chapters))) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (touch) 36.dp else 28.dp)
                .seekHover(hover, enabled = !touch && enabled)
                .onSizeChanged {
                    barWidth = it.width.toFloat().coerceAtLeast(1f)
                    barHeight = it.height
                }
                .pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        session.seekTo(((offset.x / barWidth).coerceIn(0f, 1f) * duration).roundToLong())
                        onInteract()
                    }
                }
                .pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            seek.dragStartMs = session.currentPositionMs()
                            seek.dragFraction = (offset.x / barWidth).coerceIn(0f, 1f)
                            seek.dragging = true
                            onInteract()
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            seek.dragFraction = (change.position.x / barWidth).coerceIn(0f, 1f)
                            onInteract()
                        },
                        onDragEnd = {
                            if (seek.dragging) {
                                session.seekTo((seek.dragFraction * duration).roundToLong())
                                seek.dragging = false
                            }
                        },
                        onDragCancel = { seek.dragging = false },
                    )
                }
                .semantics {
                    contentDescription = seekLabel
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(shownMs.toFloat(), 0f..duration.toFloat())
                    if (enabled) {
                        setProgress { target ->
                            session.seekTo(target.toLong().coerceIn(0L, duration))
                            onInteract()
                            true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                val w = size.width
                val cy = size.height / 2f
                val th = trackHeight.toPx()
                val top = cy - th / 2f
                fun run(left: Float, right: Float, color: Color, y: Float = top, h: Float = th) {
                    val a = (w * left).coerceIn(0f, w)
                    val b = (w * right).coerceIn(0f, w)
                    if (b > a) drawRoundRect(color, Offset(a, y), Size(b - a, h), CornerRadius(h / 2f))
                }
                // the track, the chapters of the marks, what is already here, what was played
                run(0f, 1f, text.copy(alpha = 0.26f))
                marks.forEach { run(it.left, it.left + it.width, signal.warn.copy(alpha = if (it.kind == SkipKind.INTRO) 0.55f else 0.42f)) }
                ranges.downloaded.forEach { run(it.left, it.right, text.copy(alpha = 0.34f)) }
                ranges.buffered?.let { run(it.left, it.right, text.copy(alpha = 0.52f)) }
                run(0f, shownFraction, accent)
                // chapter cuts: a small gap where a mark starts or ends
                val gap = 3.dp.toPx()
                marks.flatMap { listOf(it.left, it.left + it.width) }
                    .filter { it > 0.002f && it < 0.998f }
                    .forEach { f -> drawRect(Color.Black, Offset(w * f - gap / 2f, top - 1f), Size(gap, th + 2f), blendMode = BlendMode.Clear) }
                // what the PC already processed: a thin line under the track
                ranges.pcProcessed?.let {
                    val lh = 3.dp.toPx()
                    run(it.left, it.right, signal.info.copy(alpha = if (ranges.pcEstimated) 0.6f else 1f), y = top + th + 2.dp.toPx(), h = lh)
                }
                val r = thumbRadius.toPx()
                val cx = (w * shownFraction).coerceIn(r, (w - r).coerceAtLeast(r))
                drawCircle(Color.Black.copy(alpha = 0.35f), r + 3.dp.toPx(), Offset(cx, cy))
                drawCircle(accent, r, Offset(cx, cy))
            }
        }
        // Pointer: the time (and chapter) under the mouse. Touch: the dragged time, how far from where the drag started, and the chapter
        // or the buffer ahead there.
        if (!touch) SeekHoverBubble(hover, ps.durationMs, labelAt = chapterAt)
        if (seek.dragging && enabled) {
            val aheadSecs = bufferedAheadSecs(ps.positionMs, ps.durationMs, ps.bufferedMs, ranges)
            AboveBar(seek.dragFraction, barWidth, barHeight) {
                Surface(shape = MaterialTheme.shapes.small, color = scheme.surfaceContainerHighest, contentColor = scheme.onSurface) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                        Text(position, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), fontFamily = FontFamily.Monospace)
                        Text(
                            formatSignedDelta((shownMs - seek.dragStartMs) / 1000L),
                            color = accent,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            fontFamily = FontFamily.Monospace,
                        )
                        when {
                            chapterNow != null -> Text(chapterNow, color = signal.warn, style = MaterialTheme.typography.labelSmall)
                            aheadSecs >= 3L -> Text(
                                stringResource(Res.string.rd_s5_buffer_ahead, aheadSecs.toInt()),
                                color = signal.info,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Lays [content] centred on [fraction] of the bar right above it (the bar is [barHeight] px tall), taking no room. */
@Composable
private fun AboveBar(fraction: Float, barWidth: Float, barHeight: Int, content: @Composable () -> Unit) {
    Box(
        Modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(0, 0) {
                val x = (fraction * barWidth - p.width / 2f).roundToInt().coerceIn(0, (barWidth.roundToInt() - p.width).coerceAtLeast(0))
                p.place(x, -barHeight - p.height - 4.dp.roundToPx())
            }
        },
    ) { content() }
}

/**
 * "2:05 / 24:00" (or "2:05 · −21:55"); a tap switches between total and remaining. [remainingOnly] = the Mínimo touch bar's "−21:55"
 * (a tap shows the total instead).
 */
@Composable
internal fun PlayerTimeText(model: PlayerChromeModel, onInteract: () -> Unit, modifier: Modifier = Modifier, remainingOnly: Boolean = false) {
    val ps = model.ps
    val seek = model.seek
    val shown = seek.shownMs(ps)
    val known = ps.durationMs > 0L
    val now = formatDuration(shown / 1000)
    val total = if (known) formatDuration(ps.durationMs / 1000) else "--:--"
    val left = if (known) "−" + formatDuration((ps.durationMs - shown).coerceAtLeast(0L) / 1000) else "--:--"
    val label = when {
        remainingOnly -> if (seek.showRemaining) "$now / $total" else left
        seek.showRemaining -> "$now · $left"
        else -> "$now / $total"
    }
    val toggle = stringResource(Res.string.rd_s5_time_toggle)
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClickLabel = toggle, role = Role.Button) { seek.showRemaining = !seek.showRemaining; onInteract() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** The chapter the playhead is in ("Opening"), as a small tag next to the time; nothing outside the marks. */
@Composable
internal fun PlayerChapterTag(model: PlayerChromeModel, modifier: Modifier = Modifier) {
    val kind = model.state.markers.skipAt(model.seek.shownMs(model.ps) / 1000.0)?.first ?: return
    val scheme = MaterialTheme.colorScheme
    Text(
        stringResource(skipSegmentRes(kind)),
        color = scheme.onSurface.copy(alpha = 0.85f),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(scheme.onSurface.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** What the bar's colours mean, when there is more than a plain buffer: buffer ahead, downloaded, the PC, OP/ED, torrent speed. */
@Composable
internal fun PlayerSeekLegend(model: PlayerChromeModel, modifier: Modifier = Modifier) {
    val ps = model.ps
    val state = model.state
    val ranges = seekBarRanges(ps.positionMs, ps.durationMs, ps.bufferedMs, state.torrent?.marks.orEmpty(), model.pc)
    val aheadSecs = bufferedAheadSecs(ps.positionMs, ps.durationMs, ps.bufferedMs, ranges)
    val marks = !state.markers.isEmpty && ps.durationMs > 0L
    val speed = state.torrent?.takeIf { it.downBps > 0L }?.let { formatRate(it.downBps) }
    val signal = LocalSignalColors.current
    val text = MaterialTheme.colorScheme.onSurface
    if (aheadSecs < 2L && !ranges.showLegend && !marks && speed == null) return
    Row(modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        if (aheadSecs >= 2L) LegendItem(text.copy(alpha = 0.52f), stringResource(Res.string.rd_s5_buffer_ahead, aheadSecs.toInt()))
        if (ranges.downloaded.isNotEmpty()) LegendItem(text.copy(alpha = 0.34f), stringResource(Res.string.player_seek_legend_downloaded))
        if (ranges.pcProcessed != null) LegendItem(signal.info, stringResource(Res.string.player_seek_legend_pc))
        if (marks) LegendItem(signal.warn, stringResource(Res.string.rd_s5_legend_marks))
        if (speed != null) {
            Text(speed, color = text.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** The name of a marked segment. */
internal fun skipSegmentRes(kind: SkipKind): org.jetbrains.compose.resources.StringResource = when (kind) {
    SkipKind.INTRO -> Res.string.rd_s5_seg_opening
    SkipKind.OUTRO -> Res.string.rd_s5_seg_ending
    SkipKind.RECAP -> Res.string.rd_s5_seg_recap
}
