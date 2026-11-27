// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.tanabata

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.DirectionDecor
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.EpisodeMarkState
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.motion.LocalMotion

/**
 * What Tanabata draws differently (docs/redesign/artefacto/dir-tanabata.css, "Regiones"): bamboo rods over the rows the tanzaku hang
 * from, bamboo-jointed hairline rails on the shell, episodes and agenda, washi paper on the floor by day and on sheets, status as a
 * strip of colored paper with its hole, progress as a rail with bamboo nodes, and a blank tanzaku for empty states.
 *
 * The rod belongs to the row, not to the cards: cards hang from the top edge of the row (y = 0) and sway on their own, the rod stays
 * still. Screens put `Modifier.decor(DecorRegion.TONIGHT / ROW)` on the `LazyRow` itself, with horizontal content padding only.
 */
internal object TanabataDecor : DirectionDecor {

    /** Rod thickness of the "Esta noche" row and of any other row (artifact: 9 px and 7 px). */
    private val tonightRod = 9.dp
    private val rowRod = 7.dp

    @Composable
    override fun regionModifier(region: DecorRegion): Modifier {
        val cs = MaterialTheme.colorScheme
        val tone = LocalThemeTone.current
        val rail = cs.outlineVariant
        return when (region) {
            // By day the floor is tinted washi with soft fibres (on the floor color painted here); by night it stays plain indigo.
            DecorRegion.APP_BACKGROUND -> Modifier
                .background(cs.background)
                .then(if (tone == ThemeTone.LIGHT) Modifier.washi(Color.White, alpha = 0.3f, tile = 240.dp) else Modifier)

            DecorRegion.RAIL -> Modifier.drawWithContent {
                drawContent()
                drawRail(rail, at = size.width - 0.5.dp.toPx(), vertical = true)
            }

            DecorRegion.TOP_BAR -> Modifier.drawWithContent {
                drawContent()
                drawRail(rail, at = size.height - 0.5.dp.toPx())
            }

            DecorRegion.TONIGHT -> rodModifier(tonightRod, tone)
            DecorRegion.ROW -> rodModifier(rowRod, tone)

            DecorRegion.EPISODES, DecorRegion.AIRING -> Modifier.drawBehind { drawRail(rail, at = 0.5.dp.toPx()) }

            // Link: bamboo rails above and below. No paper: the region is often a bare layout (a row of cards on the floor), and a
            // texture with no surface of its own under it would lie straight behind the cards and their text.
            DecorRegion.LINK -> Modifier.drawBehind {
                val inset = 14.dp.toPx()
                drawRail(rail, at = 10.dp.toPx(), from = inset, to = size.width - inset)
                drawRail(rail, at = size.height - 10.dp.toPx(), from = inset, to = size.width - inset)
            }

            // Sheets: a faint grain on the sheet's own surface. Screens put this on the sheet container (whose rounded surface the
            // decor cannot see), so the grain is composed onto what the container paints and never leaks past its corners.
            DecorRegion.SHEET -> Modifier.washiGrain(cs.onSurface, alpha = 0.04f, tile = 200.dp)

            DecorRegion.HERO, DecorRegion.GRID, DecorRegion.STATUS_PICKER, DecorRegion.PLAYER_SEEK, DecorRegion.EMPTY -> Modifier
        }
    }

    /** What the row hangs from: the rod, or the skin's own hanger (branch, line, mat, rod with a paper chain). */
    @Composable
    private fun rodModifier(thickness: androidx.compose.ui.unit.Dp, tone: ThemeTone): Modifier {
        val bamboo = TanabataMaterials.bamboo(tone)
        val thread = TanabataMaterials.thread(tone)
        val skin = LocalTanzakuSkin.current
        return Modifier.drawBehind { drawSkinHanger(skin, bamboo, thread, tone, thickness.toPx()) }
    }

    /** Section title over a bamboo rail (artifact `.nj-section-head`). */
    @Composable
    override fun SectionHeader(title: String, modifier: Modifier, trailing: (@Composable () -> Unit)?) {
        val rail = MaterialTheme.colorScheme.outlineVariant
        Row(
            modifier
                .fillMaxWidth()
                .drawBehind { drawRail(rail, at = size.height - 0.5.dp.toPx()) }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
    }

    /**
     * A small strip of paper per episode: green when watched, bermellón with the lantern ring when it is the one in progress (the next
     * to watch), blank kinari when aired, a dashed outline when it has not aired yet. Prints [number] in ink when given.
     */
    @Composable
    override fun EpisodeMark(state: EpisodeMarkState, number: Int?, modifier: Modifier) {
        val tone = LocalThemeTone.current
        val shape = RoundedCornerShape(2.dp)
        val ground = MaterialTheme.colorScheme.background
        val sized = if (number != null) modifier.defaultMinSize(minWidth = 22.dp, minHeight = 26.dp) else modifier.size(12.dp, 18.dp)
        val box = when (state) {
            EpisodeMarkState.UNAIRED -> {
                val dash = MaterialTheme.colorScheme.outline
                sized.drawBehind {
                    val s = 1.dp.toPx()
                    drawRoundRect(
                        color = dash,
                        topLeft = Offset(s / 2f, s / 2f),
                        size = Size(size.width - s, size.height - s),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                        style = Stroke(width = s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                    )
                }
            }
            else -> {
                val paper = when (state) {
                    EpisodeMarkState.WATCHED -> TanabataMaterials.paper(StatusSlot.COMPLETED, tone)
                    EpisodeMarkState.IN_PROGRESS -> TanabataMaterials.paper(StatusSlot.WATCHING, tone)
                    else -> TanabataMaterials.blankPaper(tone)
                }
                val ringed = if (state == EpisodeMarkState.IN_PROGRESS) {
                    sized.border(1.5.dp, TanabataMaterials.lantern(tone), RoundedCornerShape(3.dp)).padding(2.dp)
                } else {
                    sized
                }
                ringed.tanzakuPaper(paper, shape, ink = if (state == EpisodeMarkState.IN_PROGRESS) 0.5f else 0f, elevated = false, fibreTile = 96.dp)
            }
        }
        Box(
            box.drawWithContent {
                drawContent()
                if (state != EpisodeMarkState.UNAIRED) drawHole(Offset(size.width / 2f, 3.5.dp.toPx()), ground, radius = 1.5.dp.toPx())
            },
            contentAlignment = Alignment.Center,
        ) {
            if (number != null) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (state == EpisodeMarkState.UNAIRED) MaterialTheme.colorScheme.onSurfaceVariant else TanabataMaterials.ink,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 3.dp, end = 3.dp, top = 6.dp, bottom = 2.dp),
                )
            }
        }
    }

    /**
     * Status as a minimal tanzaku with its hole (artifact `.nj-status` and the status picker): the current status is the colored paper
     * with ink text; the others are a light wash of their paper with a paper-colored edge.
     */
    @Composable
    override fun StatusMark(status: StatusSlot, label: String, selected: Boolean, modifier: Modifier) {
        val tone = LocalThemeTone.current
        val paper = TanabataMaterials.paper(status, tone)
        val shape = RoundedCornerShape(3.dp)
        val ground = MaterialTheme.colorScheme.background
        val surface = if (selected) {
            modifier.tanzakuPaper(paper, shape, elevated = true, fibreTile = 120.dp)
        } else {
            modifier
                .border(1.dp, paper.copy(alpha = 0.55f), shape)
                .background(paper.copy(alpha = 0.14f), shape)
        }
        Row(
            surface
                .drawWithContent {
                    drawContent()
                    drawHole(Offset(9.dp.toPx(), size.height / 2f), ground)
                }
                .padding(start = 17.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (selected) TanabataMaterials.ink else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    /** A 4 dp rail in the accent over a faint bamboo track; with [segments], one bamboo node between episodes when they fit. */
    @Composable
    override fun ProgressTrack(progress: Float, modifier: Modifier, segments: Int?) {
        val p = progress.coerceIn(0f, 1f)
        val fill = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        Canvas(
            modifier
                .size(240.dp, 4.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) },
        ) {
            val r = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            val n = segments ?: 0
            val gap = 2.dp.toPx()
            if (n in 2..120 && size.width / n >= 6.dp.toPx()) {
                val seg = (size.width - gap * (n - 1)) / n
                val filled = p * n
                for (i in 0 until n) {
                    val x = i * (seg + gap)
                    drawRoundRect(track, Offset(x, 0f), Size(seg, size.height), r)
                    val part = (filled - i).coerceIn(0f, 1f)
                    if (part > 0f) drawRoundRect(fill, Offset(x, 0f), Size(seg * part, size.height), r)
                }
            } else {
                drawRoundRect(track, Offset.Zero, size, r)
                if (p > 0f) drawRoundRect(fill, Offset.Zero, Size(size.width * p, size.height), r)
            }
        }
    }

    /** A blank tanzaku hanging from a piece of rod: no wish written yet (artifact `[data-region="empty"]::before`). */
    @Composable
    override fun EmptyArt(kind: EmptyKind, modifier: Modifier) {
        val tone = LocalThemeTone.current
        val bamboo = TanabataMaterials.bamboo(tone)
        val thread = TanabataMaterials.thread(tone)
        val paper = TanabataMaterials.blankPaper(tone)
        val ground = MaterialTheme.colorScheme.background
        val shadow = TanabataMaterials.paperShadow(tone)
        val washi = rememberWashi()
        val rich = LocalMotion.current.rich
        Canvas(modifier.size(110.dp, 112.dp)) {
            val u = size.width / 110f
            drawBambooRod(bamboo, top = 4f * u, thickness = 7f * u)
            // An error leaves the strip hanging by a broken thread; everything else is simply blank.
            val broken = kind == EmptyKind.ERROR
            rotate(if (broken) -7f else -3f, pivot = Offset(55f * u, 6f * u)) {
                val paperTop = 26f * u
                val paperLeft = 39f * u
                val paperSize = Size(32f * u, 84f * u)
                if (rich) drawRect(shadow.copy(alpha = shadow.alpha * 0.5f), Offset(paperLeft + 1f * u, paperTop + 3f * u), paperSize)
                drawRect(paper, Offset(paperLeft, paperTop), paperSize)
                drawWashi(washi, washiFilter(TanabataMaterials.fibre(paper), TanabataMaterials.FIBRE_ALPHA), 96f * u, Offset(paperLeft, paperTop), paperSize)
                drawHole(Offset(55f * u, 34f * u), ground, radius = 3f * u)
                if (broken) {
                    drawThread(thread, x = 55f * u, top = 6f * u, bottom = 20f * u, knot = false, dashed = true)
                } else {
                    drawThread(thread, x = 55f * u, top = 6f * u, bottom = 36.5f * u)
                }
            }
        }
    }
}
