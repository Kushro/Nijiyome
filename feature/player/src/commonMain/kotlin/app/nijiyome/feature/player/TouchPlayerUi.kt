// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.rd_s5_aspect_16_9
import app.nijiyome.feature.player.resources.rd_s5_aspect_4_3
import app.nijiyome.feature.player.resources.rd_s5_aspect_fill
import app.nijiyome.feature.player.resources.rd_s5_aspect_fit
import app.nijiyome.feature.player.resources.rd_s5_aspect_stretch
import app.nijiyome.model.AspectMode
import app.nijiyome.ui.format.formatDuration
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong

// v1.2.0 touch gestures, Sparkles S5: the controls themselves moved to chrome/ (one composition per PlayerStructure); what stays here
// are the pure helpers and the overlays of the touch gestures (side rails, double-tap arc, horizontal scrub), drawn in the player's
// scheme so they take the direction's accent.

/** Cycles the 3 quick phone framing modes: Fit -> Fill -> Stretch -> Fit. */
internal fun nextQuickAspect(current: AspectMode): AspectMode = when (current) {
    AspectMode.FIT -> AspectMode.FILL
    AspectMode.FILL -> AspectMode.STRETCH
    else -> AspectMode.FIT
}

/** The short name of a framing mode ("Ajustar", "16:9"). */
internal fun aspectLabelRes(mode: AspectMode): StringResource = when (mode) {
    AspectMode.FIT -> Res.string.rd_s5_aspect_fit
    AspectMode.FILL -> Res.string.rd_s5_aspect_fill
    AspectMode.STRETCH -> Res.string.rd_s5_aspect_stretch
    AspectMode.RATIO_16_9 -> Res.string.rd_s5_aspect_16_9
    AspectMode.RATIO_4_3 -> Res.string.rd_s5_aspect_4_3
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

// ---- Rich touch gesture overlays: vertical side bars, double-tap arcs, horizontal scrub HUD ----

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
            Hud.Kind.Aspect -> {
                val scheme = MaterialTheme.colorScheme
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp),
                    shape = CircleShape,
                    color = scheme.surfaceContainerHighest,
                    contentColor = scheme.onSurface,
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.AspectRatio, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        Text(hud.aspect?.let { stringResource(aspectLabelRes(it)) } ?: hud.label.orEmpty(), style = MaterialTheme.typography.labelLarge)
                    }
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
        color = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (muted) scheme.onSurfaceVariant else scheme.primary, modifier = Modifier.size(18.dp))
            Box(
                Modifier
                    .width(5.dp)
                    .height(104.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurface.copy(alpha = 0.2f)),
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
                "${if (muted) 0 else percent}%",
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
    val on = MaterialTheme.colorScheme.onSurface
    Box(modifier) {
        Box(
            Modifier
                .align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight(0.74f)
                .fillMaxWidth(0.36f)
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        if (onLeft) listOf(on.copy(alpha = 0.22f), on.copy(alpha = 0.06f))
                        else listOf(on.copy(alpha = 0.06f), on.copy(alpha = 0.22f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (onLeft) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = on,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    if (seconds >= 0) "+${seconds}s" else "${seconds}s",
                    color = on,
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
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
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
                val track = scheme.onSurface
                Canvas(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)) {
                    val w = size.width
                    val h = size.height
                    drawRect(track.copy(alpha = 0.2f))
                    if (bufFrac > playedFrac) {
                        drawRect(track.copy(alpha = 0.52f), Offset(w * playedFrac, 0f), Size(w * (bufFrac - playedFrac), h))
                    }
                    drawRect(scheme.primary, Offset.Zero, Size(w * playedFrac, h))
                }
            }
        }
    }
}
