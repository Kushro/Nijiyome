// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.sumi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.DirectionDecor
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.EpisodeMarkState
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_sumi_watched
import org.jetbrains.compose.resources.stringResource

/**
 * What Sumi y hanko draws differently. Everything else is clean Material 3 with the Sumi type, color and radii. The seal is
 * the list status and the watched mark, the ensō is progress and the airing "now", an ink wash marks the "Esta noche" row and
 * the kitsune inside the ensō is the empty art. Seals are never used as ornament.
 */
internal object SumiDecor : DirectionDecor {

    @Composable
    override fun regionModifier(region: DecorRegion): Modifier {
        val scheme = MaterialTheme.colorScheme
        val rich = LocalMotion.current.rich
        return when (region) {
            // A hairline under the title bar and beside the rail, the way paper meets ink (`.nj-topbar`, `.nj-rail`).
            DecorRegion.TOP_BAR -> Modifier.drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(scheme.outlineVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            DecorRegion.RAIL -> Modifier.drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(scheme.outlineVariant.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            // Esta noche: an ink wash with brushed edges behind the focus row, drawn a little past its bounds (no layout
            // change). Without decorative cost (PERF, OFF) it is a flat 4 % tint.
            DecorRegion.TONIGHT -> Modifier.drawWithCache {
                val bleed = 12.dp.toPx()
                val wash = sumiWashPath(Size(size.width + bleed, size.height + bleed * 2))
                val washInk = scheme.onSurface.copy(alpha = if (scheme.background.isLightInk()) 0.05f else 0.045f)
                onDrawBehind {
                    if (rich) {
                        translate(left = -bleed / 2f, top = -bleed) { drawPath(wash, washInk) }
                    } else {
                        drawRect(scheme.onSurface.copy(alpha = 0.04f), topLeft = Offset(0f, -bleed / 2f), size = Size(size.width, size.height + bleed))
                    }
                }
            }
            // Sheets and side panels: a paper edge on top.
            DecorRegion.SHEET -> Modifier.drawBehind {
                drawLine(scheme.outlineVariant, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            else -> Modifier
        }
    }

    @Composable
    override fun SectionHeader(title: String, modifier: Modifier, trailing: (@Composable () -> Unit)?) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (trailing != null) trailing()
        }
    }

    /**
     * Watched = the round 済 seal, stamped when it lands. In progress = an open ensō traced in the accent. Aired = an empty ensō
     * in the line ink, unaired = a dotted one. The episode number sits inside the ring when the screen passes it.
     */
    @Composable
    override fun EpisodeMark(state: EpisodeMarkState, number: Int?, modifier: Modifier) {
        val isWatched = state == EpisodeMarkState.WATCHED
        // Computed above the branch so the stamp survives the switch from the ring to the seal.
        val stamp = Modifier.sumiStamp(isWatched, animate = isWatched)
        if (isWatched) {
            val watched = stringResource(Res.string.rd_sumi_watched)
            WatchedSeal(
                modifier
                    .then(stamp)
                    .semantics { contentDescription = watched },
                halo = MaterialTheme.colorScheme.background,
            )
            return
        }
        val scheme = MaterialTheme.colorScheme
        val ink = when (state) {
            EpisodeMarkState.IN_PROGRESS -> scheme.primary
            EpisodeMarkState.AIRED -> scheme.outline
            else -> scheme.outlineVariant
        }
        val dotted = state == EpisodeMarkState.UNAIRED
        Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp)) {
                val d = size.minDimension
                if (dotted) {
                    drawCircle(
                        ink,
                        radius = d * 0.42f,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))),
                    )
                } else {
                    drawPath(sumiEnsoPath(d / 2f, d / 2f, d * 0.4f, d * (if (state == EpisodeMarkState.IN_PROGRESS) 0.14f else 0.1f), -50f, 325f, 36), ink)
                }
            }
            if (number != null && number in 0..999) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (state == EpisodeMarkState.UNAIRED) scheme.onSurfaceVariant else scheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }

    /**
     * A list status as a seal tag: the seal with its kanji and the name beside it (the name is always there, so nobody needs to
     * read 見 or 予). The current status is inked and stamps when it changes; the others are blank seals in the line ink.
     */
    @Composable
    override fun StatusMark(status: StatusSlot, label: String, selected: Boolean, modifier: Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HankoSeal(
                status,
                modifier = Modifier.sumiStamp(selected, animate = selected).clearAndSetSemantics { },
                size = 26.dp,
                blank = !selected,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    /** The ink line (`.sm-line`); with episode segments up to 24, a short gap between episodes, like brush dabs. */
    @Composable
    override fun ProgressTrack(progress: Float, modifier: Modifier, segments: Int?) {
        val p = progress.coerceIn(0f, 1f)
        val sem = modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) }
        if (segments == null || segments < 2 || segments > 24) {
            SumiInkLine(p, sem)
            return
        }
        val fill = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        Canvas(sem.fillMaxWidth().height(3.dp)) {
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (segments - 1)) / segments
            val filled = p * segments
            for (i in 0 until segments) {
                val x = i * (w + gap)
                drawRect(track, topLeft = Offset(x, 0f), size = Size(w, size.height))
                val part = (filled - i).coerceIn(0f, 1f)
                if (part > 0f) drawRect(fill, topLeft = Offset(x, 0f), size = Size(w * part, size.height))
            }
        }
    }

    override val hasBrandMark: Boolean get() = true

    /** The kitsune and the 虹 seal on its paper tile, as the artifact's title bar shows it. */
    @Composable
    override fun BrandMark(modifier: Modifier) {
        SumiMark(modifier, tile = true)
    }

    /** The kitsune inside the ensō, in the ink of the tone. Link draws two ensō, apart and the second faded when unlinked. */
    @Composable
    override fun EmptyArt(kind: EmptyKind, modifier: Modifier) {
        if (kind == EmptyKind.LINK) {
            val a = MaterialTheme.colorScheme.primary
            val b = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            Canvas(modifier.size(width = 112.dp, height = 64.dp).clearAndSetSemantics { }) {
                val r = size.height * 0.36f
                val w = size.height * 0.09f
                drawPath(sumiEnsoPath(size.width * 0.3f, size.height / 2f, r, w, -60f, 320f, 40), a)
                drawPath(sumiEnsoPath(size.width * 0.74f, size.height / 2f, r, w, 120f, 320f, 40), b)
            }
            return
        }
        val ink = if (kind == EmptyKind.ERROR) LocalSignalColors.current.err else MaterialTheme.colorScheme.onSurfaceVariant
        SumiMark(modifier.size(112.dp), ink = ink)
    }
}

/** True for a paper background (light tone), to pick the wash strength. */
private fun Color.isLightInk(): Boolean = red + green + blue > 1.5f
