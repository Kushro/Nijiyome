// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.obi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.DirectionDecor
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.EpisodeMarkState
import app.nijiyome.designsystem.direction.LocalCoverAccent
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel

// What Obi draws differently (dir-obi.css Parte 3, regions): shelf edges under rows, a band across the foot of the hero, hairline
// shelf sides on the rail and top bar, status marks as bands of their status color, episode marks as small bands, progress as a
// square shelf strip (one segment per episode), and an empty shelf with two spines. Everything is static except the hero band's
// color change; PERF drops the shelf shadow.
internal object ObiDecor : DirectionDecor {
    /** Height of the band the hero wears at its foot. */
    private val HERO_BAND = 10.dp

    @Composable
    override fun regionModifier(region: DecorRegion): Modifier = when (region) {
        DecorRegion.RAIL -> {
            val line = MaterialTheme.colorScheme.outlineVariant
            Modifier.drawWithContent {
                drawContent()
                val w = 1.dp.toPx()
                drawRect(line, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
            }
        }
        DecorRegion.TOP_BAR -> {
            val line = MaterialTheme.colorScheme.outlineVariant
            Modifier.drawWithContent {
                drawContent()
                val h = 1.dp.toPx()
                drawRect(line, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            }
        }
        DecorRegion.HERO -> heroBand()
        DecorRegion.TONIGHT -> shelf(edge = 8, gap = 12, shade = LocalMotion.current.level != MotionLevel.PERF)
        DecorRegion.ROW -> shelf(edge = 3, gap = 10, shade = false)
        else -> Modifier
    }

    /** The cover's band (or the accent's) across the foot of the hero, in the reserved bottom padding. */
    @Composable
    private fun heroBand(): Modifier {
        val cover = LocalCoverAccent.current
        val target = cover?.let { ObiInk.bandFor(it).bg } ?: MaterialTheme.colorScheme.primary
        val level = LocalMotion.current.level
        val color by animateColorAsState(
            targetValue = target,
            animationSpec = when (level) {
                MotionLevel.FULL -> tween(320, easing = ObiEaseOut)
                MotionLevel.CALM -> tween(140)
                MotionLevel.PERF, MotionLevel.OFF -> snap()
            },
            label = "obiHeroBand",
        )
        return Modifier
            .drawWithContent {
                drawContent()
                val h = HERO_BAND.toPx()
                drawRect(color, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            }
            .padding(bottom = HERO_BAND)
    }

    /** A shelf edge of [edge] dp under the content, [gap] dp below it; TONIGHT adds the edge's soft shadow. */
    @Composable
    private fun shelf(edge: Int, gap: Int, shade: Boolean): Modifier {
        val shelf = obiShelfColor()
        val shadow = Color.Black.copy(alpha = 0.35f)
        return Modifier
            .drawWithContent {
                drawContent()
                val e = edge.dp.toPx()
                drawRect(shelf, topLeft = Offset(0f, size.height - e), size = Size(size.width, e))
                if (shade) {
                    // The edge casts a short shadow below the region (outside its bounds; hidden if a parent clips).
                    val s = 6.dp.toPx()
                    drawRect(
                        Brush.verticalGradient(listOf(shadow, Color.Transparent), startY = size.height, endY = size.height + s),
                        topLeft = Offset(0f, size.height),
                        size = Size(size.width, s),
                    )
                }
            }
            .padding(bottom = (edge + gap).dp)
    }

    @Composable
    override fun EpisodeMark(state: EpisodeMarkState, number: Int?, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(2.dp)
        if (number == null) {
            // A thin spine: ink when watched, accent when next, gray when aired, outline only when it has not aired.
            val fill = when (state) {
                EpisodeMarkState.WATCHED -> scheme.outline
                EpisodeMarkState.IN_PROGRESS -> scheme.primary
                EpisodeMarkState.AIRED -> scheme.onSurface
                EpisodeMarkState.UNAIRED -> Color.Transparent
            }
            Box(
                modifier
                    .size(width = 6.dp, height = 16.dp)
                    .clip(shape)
                    .background(fill)
                    .then(if (state == EpisodeMarkState.UNAIRED) Modifier.border(1.dp, scheme.outline, shape) else Modifier),
            )
            return
        }
        val (bg, fg) = when (state) {
            EpisodeMarkState.WATCHED -> scheme.onSurface.copy(alpha = 0.10f) to scheme.onSurfaceVariant
            EpisodeMarkState.IN_PROGRESS -> scheme.primaryContainer to scheme.onPrimaryContainer
            EpisodeMarkState.AIRED -> scheme.surfaceContainerHigh to scheme.onSurface
            EpisodeMarkState.UNAIRED -> Color.Transparent to scheme.onSurfaceVariant
        }
        val edge = when (state) {
            EpisodeMarkState.IN_PROGRESS -> scheme.primary.copy(alpha = 0.6f)
            EpisodeMarkState.UNAIRED -> scheme.outlineVariant
            else -> null
        }
        Box(
            modifier
                .heightIn(min = 20.dp)
                .widthIn(min = 28.dp)
                .clip(shape)
                .background(bg)
                .then(if (edge != null) Modifier.border(1.5.dp, edge, shape) else Modifier)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = TextStyle(
                    fontFamily = ObiFonts.band(),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    textGeometricTransform = ObiFonts.condensed(0.92f),
                ),
                color = fg,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }

    @Composable
    override fun StatusMark(status: StatusSlot, label: String, selected: Boolean, modifier: Modifier) {
        val st = LocalStatusColors.current.of(status)
        val shape = RoundedCornerShape(2.dp)
        val base = modifier
            .heightIn(min = 28.dp)
            .clip(shape)
        val styled = if (selected) {
            base.background(st)
        } else {
            base.background(st.copy(alpha = 0.16f)).border(1.5.dp, st.copy(alpha = 0.55f), shape)
        }
        Row(
            styled.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!selected) Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(st))
            Text(
                label,
                style = if (selected) {
                    MaterialTheme.typography.labelLarge.copy(fontFamily = ObiFonts.band(), fontWeight = null)
                } else {
                    MaterialTheme.typography.labelLarge
                },
                // Selected: the page color on the status fill (artifact `color: var(--nj-bg)`); each pair is >= 4.5:1.
                color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }

    @Composable
    override fun ProgressTrack(progress: Float, modifier: Modifier, segments: Int?) {
        val p = progress.coerceIn(0f, 1f)
        val track = obiShelfColor()
        val fill = MaterialTheme.colorScheme.primary
        Canvas(
            modifier
                .fillMaxWidth()
                .height(4.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) },
        ) {
            val n = segments ?: 0
            val gap = 2.dp.toPx()
            // One tick per episode while every segment stays at least 4 dp wide; otherwise a plain strip.
            if (n in 2..64 && (size.width - gap * (n - 1)) / n >= 4.dp.toPx()) {
                val w = (size.width - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val x = i * (w + gap)
                    drawRect(track, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    val filled = ((p * n) - i).coerceIn(0f, 1f)
                    if (filled > 0f) drawRect(fill, topLeft = Offset(x, 0f), size = Size(w * filled, size.height))
                }
            } else {
                drawRect(track)
                if (p > 0f) drawRect(fill, size = Size(size.width * p, size.height))
            }
        }
    }

    @Composable
    override fun EmptyArt(kind: EmptyKind, modifier: Modifier) {
        val shelf = obiShelfColor()
        val strong = MaterialTheme.colorScheme.outline
        val soft = MaterialTheme.colorScheme.outlineVariant
        // An empty shelf with two spines (artifact `[data-region="empty"]::before`); an error tips the second one over, a search
        // lays a third one flat, as if someone had been looking.
        Canvas(modifier.size(width = 120.dp, height = 66.dp).clearAndSetSemantics { }) {
            val u = 1.dp.toPx()
            val r = CornerRadius(1.5f * u)
            drawRect(shelf, topLeft = Offset(0f, size.height - 6 * u), size = Size(size.width, 6 * u))
            val base = size.height - 6 * u
            drawRoundRect(strong, topLeft = Offset(18 * u, base - 54 * u), size = Size(14 * u, 54 * u), cornerRadius = r)
            if (kind == EmptyKind.ERROR) {
                rotate(degrees = 24f, pivot = Offset(36 * u, base)) {
                    drawRoundRect(soft, topLeft = Offset(36 * u, base - 46 * u), size = Size(12 * u, 46 * u), cornerRadius = r)
                }
            } else {
                drawRoundRect(soft, topLeft = Offset(36 * u, base - 46 * u), size = Size(12 * u, 46 * u), cornerRadius = r)
            }
            if (kind == EmptyKind.SEARCH) {
                drawRoundRect(strong, topLeft = Offset(56 * u, base - 10 * u), size = Size(40 * u, 10 * u), cornerRadius = r)
            }
        }
    }
}
