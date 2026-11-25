// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.transit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.DirectionDecor
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.EpisodeMarkState
import app.nijiyome.designsystem.direction.LocalCoverAccent
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.RegionData
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_transit_prefix_episode
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

// What Tránsito nocturno draws differently (dir-transit.js `decorate` and dir-transit.css "Regiones"): the TONIGHT track with one stop
// per card, the AIRING departure board, the LINK transfer between the computer line and the phone line, episodes as stations,
// progress as a line with its train, list statuses as ringed stations, and the end-of-line empty state.
internal object TransitDecor : DirectionDecor {

    @Composable
    override fun regionModifier(region: DecorRegion, data: RegionData): Modifier = when (region) {
        DecorRegion.LINK -> transfer(connected = data.connected != false)
        DecorRegion.PLAYER_SEEK -> chapterStations(data.chapterFractions)
        else -> regionModifier(region)
    }

    /** The departure board is a dark island in every mode (artifact `.tr-board`), so text drawn straight on it is board text. */
    @Composable
    override fun regionContentColor(region: DecorRegion): Color =
        if (region == DecorRegion.AIRING) {
            TransitPalettes.boardOf(MaterialTheme.colorScheme, LocalThemeTone.current).boardText
        } else {
            LocalContentColor.current
        }

    @Composable
    override fun regionModifier(region: DecorRegion): Modifier {
        val colors = MaterialTheme.colorScheme
        return when (region) {
            DecorRegion.APP_BACKGROUND, DecorRegion.RAIL -> Modifier.background(colors.background)
            DecorRegion.TOP_BAR -> {
                val hairline = colors.outlineVariant
                Modifier.background(colors.background).drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
            }
            DecorRegion.TONIGHT -> tonightTrack()
            DecorRegion.AIRING -> departureBoard()
            DecorRegion.LINK -> transfer()
            else -> Modifier
        }
    }

    /** The rail of "Esta noche": an accent track laid under the cards (they draw their own stops on it, see [transitTrackStop]). */
    @Composable
    private fun tonightTrack(): Modifier {
        val accent = MaterialTheme.colorScheme.primary
        val level = LocalMotion.current.level
        val laid = remember { Animatable(if (level == MotionLevel.FULL) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (laid.value < 1f) laid.animateTo(1f, tween(560, easing = TransitEase))
        }
        return Modifier
            .modifierLocalProvider(TransitTonightTrack) { true }
            .drawBehind {
                val y = 12.5.dp.toPx()
                drawLine(accent, Offset(0f, y), Offset(size.width * laid.value, y), strokeWidth = 3.dp.toPx())
            }
            .padding(top = TransitTrackBand)
    }

    /**
     * The airing agenda as a departure board: an enamel island (dark blue on Grafito) in every mode, like real boards. In light mode it
     * uses the palette's dark board; the screen reads [regionContentColor] for the text it draws straight on the board.
     */
    @Composable
    private fun departureBoard(): Modifier {
        val tone = LocalThemeTone.current
        val board = TransitPalettes.boardOf(MaterialTheme.colorScheme, tone)
        val shape = RoundedCornerShape(14.dp)
        val base = Modifier.background(board.boardBg, shape)
        return (if (tone == ThemeTone.OLED) base.border(1.dp, board.boardLine, shape) else base)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    }

    /**
     * Chapter starts as small porcelain stations on the seek bar (artifact `.tr-chapters`), drawn above the bar at its vertical middle.
     * Static: nothing animates, so `rich == false` changes nothing here.
     */
    @Composable
    private fun chapterStations(fractions: List<Float>): Modifier {
        if (fractions.isEmpty()) return Modifier
        val ring = TransitInk.ink
        return Modifier.drawWithContent {
            drawContent()
            val y = size.height / 2f
            val r = 5.dp.toPx()
            for (f in fractions) {
                if (f <= 0f || f >= 1f) continue
                val c = Offset(size.width * f, y)
                drawCircle(Color.White.copy(alpha = 0.6f), r + 1.5.dp.toPx(), c)
                drawCircle(TransitInk.porcelain, r, c)
                drawCircle(ring, r - 1.25.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
            }
        }
    }

    /**
     * Link: the computer line (a) and the phone line (b) meeting at a transfer capsule, drawn above the region's content. Not
     * [connected]: the phone line goes dashed and gray, its stations hollow, and the capsule is outlined in the warning color.
     */
    @Composable
    private fun transfer(connected: Boolean = true): Modifier {
        val colors = MaterialTheme.colorScheme
        val tokens = TransitPalettes.tokensOf(colors, LocalThemeTone.current)
        val a = tokens.trA
        val b = if (connected) tokens.trB else tokens.lineStrong
        val bFill = if (connected) TransitInk.porcelain else colors.surface
        val capStroke = if (connected) colors.onSurface else tokens.warn
        val level = LocalMotion.current.level
        val drawn = remember { Animatable(if (level == MotionLevel.FULL || level == MotionLevel.PERF) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (drawn.value < 1f) drawn.animateTo(1f, tween(480, easing = TransitEase))
        }
        return Modifier
            .drawBehind {
                val y = 20.dp.toPx()
                val left = 6.dp.toPx()
                val right = size.width - 6.dp.toPx()
                val mid = size.width / 2f
                val w = 5.dp.toPx()
                val f = drawn.value
                drawLine(a, Offset(left, y), Offset(left + (mid - left) * f, y), strokeWidth = w)
                if (connected) {
                    drawLine(b, Offset(mid, y), Offset(mid + (right - mid) * f, y), strokeWidth = w)
                } else {
                    val dot = 3.dp.toPx()
                    drawLine(
                        b,
                        Offset(mid, y),
                        Offset(mid + (right - mid) * f, y),
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dot, dot)),
                    )
                }
                val endR = 5.dp.toPx()
                val endStroke = 3.dp.toPx()
                drawCircle(TransitInk.porcelain, endR, Offset(left, y))
                drawCircle(a, endR, Offset(left, y), style = Stroke(endStroke))
                drawCircle(bFill, endR, Offset(right, y))
                drawCircle(b, endR, Offset(right, y), style = Stroke(endStroke))
                val capSize = Size(50.dp.toPx(), 24.dp.toPx())
                val capTopLeft = Offset(mid - capSize.width / 2f, y - capSize.height / 2f)
                val corner = CornerRadius(12.dp.toPx())
                drawRoundRect(TransitInk.porcelain, capTopLeft, capSize, corner)
                val capDash = if (connected) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                drawRoundRect(capStroke, capTopLeft, capSize, corner, style = Stroke(2.5.dp.toPx(), pathEffect = capDash))
                val stR = 6.dp.toPx()
                val stStroke = 3.5.dp.toPx()
                drawCircle(TransitInk.porcelain, stR, Offset(mid - 12.dp.toPx(), y))
                drawCircle(a, stR - stStroke / 2f, Offset(mid - 12.dp.toPx(), y), style = Stroke(stStroke))
                drawCircle(bFill, stR, Offset(mid + 12.dp.toPx(), y))
                drawCircle(b, stR - stStroke / 2f, Offset(mid + 12.dp.toPx(), y), style = Stroke(stStroke))
            }
            .padding(top = 50.dp)
    }

    @Composable
    override fun SectionHeader(title: String, modifier: Modifier, trailing: (@Composable () -> Unit)?) {
        Row(
            modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
    }

    /** An episode is a station: the plate with its number, filled once watched, ringed by the train while in progress. */
    @Composable
    override fun EpisodeMark(state: EpisodeMarkState, number: Int?, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        val line = rememberTransitLine(LocalCoverAccent.current, colors.surface)
        val level = LocalMotion.current.level
        val fadeSpec: AnimationSpec<Color> = if (level == MotionLevel.OFF) snap() else tween(190, easing = TransitEase)
        if (number != null) {
            val prefix = stringResource(Res.string.rd_transit_prefix_episode)
            val fill by animateColorAsState(if (state == EpisodeMarkState.WATCHED) line.ink else TransitInk.porcelain, fadeSpec, label = "stationFill")
            when (state) {
                EpisodeMarkState.UNAIRED -> StationCode(number, prefix, line.ink, modifier, size = 30.dp, pending = true, halo = colors.surface)
                EpisodeMarkState.AIRED -> StationCode(number, prefix, line.ink, modifier, size = 30.dp, halo = colors.surface)
                EpisodeMarkState.IN_PROGRESS -> StationCode(
                    number,
                    prefix,
                    line.ink,
                    modifier.drawBehind {
                        // The train around the plate: an outer ring in the raw line color.
                        drawCircle(line.raw, radius = size.minDimension / 2f + 3.5.dp.toPx(), style = Stroke(2.dp.toPx()))
                    },
                    size = 30.dp,
                    halo = colors.surface,
                )
                EpisodeMarkState.WATCHED -> WatchedPlate(number, fill, modifier)
            }
        } else {
            val target = when (state) {
                EpisodeMarkState.WATCHED -> line.ink
                EpisodeMarkState.UNAIRED -> colors.surface
                else -> TransitInk.porcelain
            }
            val fill by animateColorAsState(target, fadeSpec, label = "stopFill")
            val outline = colors.outline
            val halo = colors.surface
            Box(
                modifier.size(16.dp).clearAndSetSemantics { }.drawBehind {
                    val c = center
                    when (state) {
                        EpisodeMarkState.IN_PROGRESS -> drawTrain(c, 7.dp.toPx(), line.raw, line.ink, halo, 1.dp.toPx())
                        EpisodeMarkState.UNAIRED -> {
                            drawCircle(fill, 4.dp.toPx(), c)
                            drawCircle(
                                outline,
                                4.dp.toPx(),
                                c,
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
                            )
                        }
                        else -> {
                            drawCircle(fill, 5.5.dp.toPx(), c)
                            drawCircle(line.ink, 5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                        }
                    }
                },
            )
        }
    }

    /** A watched station: the plate filled with the line, the number in whichever of ink or porcelain reads on it. */
    @Composable
    private fun WatchedPlate(number: Int, fill: Color, modifier: Modifier) {
        val ink = inkOn(fill)
        val halo = MaterialTheme.colorScheme.surface
        Box(
            modifier
                .size(30.dp)
                .clearAndSetSemantics { }
                .drawBehind {
                    drawCircle(halo, size.minDimension / 2f + 1.5.dp.toPx())
                    drawCircle(fill, size.minDimension / 2f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.coerceAtLeast(0).toString().padStart(2, '0'),
                color = ink,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = transitDisplayFamily()),
            )
        }
    }

    /** A list status is a station ringed in its line color; the current one is filled (artifact status picker). */
    @Composable
    override fun StatusMark(status: StatusSlot, label: String, selected: Boolean, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        val st = LocalStatusColors.current.of(status)
        val pill = CircleShape
        val box = if (selected) {
            modifier
                .background(mix(colors.surfaceContainerHigh, st, 0.2f), pill)
                .border(1.dp, st, pill)
        } else {
            modifier
        }
        Row(
            box.padding(start = 5.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(14.dp).drawBehind {
                    val r = size.minDimension / 2f
                    if (selected) {
                        drawCircle(st, r)
                        drawCircle(TransitInk.porcelain, r - 4.dp.toPx(), style = Stroke(2.dp.toPx()))
                    } else {
                        drawCircle(TransitInk.porcelain, r)
                        drawCircle(st, r - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
                    }
                },
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

    /**
     * With [segments] (episode count) the progress is a line of stations with the train on the next episode; without it a thin track
     * whose stretch ends in a porcelain station (downloads, reading).
     */
    @Composable
    override fun ProgressTrack(progress: Float, modifier: Modifier, segments: Int?) {
        val p = progress.coerceIn(0f, 1f)
        val a11y = modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) }
        if (segments != null && segments in 2..2000) {
            val watched = (p * segments).roundToInt().coerceIn(0, segments)
            val stops = List(segments) { if (it < watched) StopState.WATCHED else StopState.AIRED }
            val line = rememberTransitLine(LocalCoverAccent.current, MaterialTheme.colorScheme.surface)
            TransitRoute(
                stops = stops,
                current = watched.coerceAtMost(segments - 1),
                line = line,
                modifier = a11y,
                height = 14.dp,
                halo = MaterialTheme.colorScheme.surface,
                animateEntrance = true,
            )
        } else {
            val accent = MaterialTheme.colorScheme.primary
            val rail = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
            Box(
                a11y.fillMaxWidth().height(10.dp).drawBehind {
                    val y = size.height / 2f
                    val pad = 5.dp.toPx()
                    val w = size.width - 2 * pad
                    val stroke = 4.dp.toPx()
                    drawLine(rail, Offset(pad, y), Offset(pad + w, y), strokeWidth = stroke, cap = StrokeCap.Round)
                    if (p > 0f) {
                        val end = pad + w * p
                        drawLine(accent, Offset(pad, y), Offset(end, y), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawCircle(TransitInk.porcelain, 5.dp.toPx(), Offset(end, y))
                        drawCircle(accent, 5.dp.toPx() - 1.25.dp.toPx(), Offset(end, y), style = Stroke(2.5.dp.toPx()))
                    }
                },
            )
        }
    }

    /** End of the line: the run, two stations, the terminal and a buffer stop, with the dotted rail that continues past it. */
    @Composable
    override fun EmptyArt(kind: EmptyKind, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        val run = if (kind == EmptyKind.ERROR) colors.error else colors.primary
        val ink = colors.onSurface
        val gap = colors.outline
        Box(modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(176.dp, 44.dp).clearAndSetSemantics { }) {
                val y = 22.dp.toPx()
                drawLine(run, Offset(6.dp.toPx(), y), Offset(112.dp.toPx(), y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(
                    gap,
                    Offset(124.dp.toPx(), y),
                    Offset(170.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
                for (x in listOf(6.dp, 56.dp)) {
                    drawCircle(TransitInk.porcelain, 4.5.dp.toPx() + 1.25.dp.toPx(), Offset(x.toPx(), y))
                    drawCircle(run, 4.5.dp.toPx(), Offset(x.toPx(), y), style = Stroke(2.5.dp.toPx()))
                }
                drawCircle(TransitInk.porcelain, 8.dp.toPx() + 1.75.dp.toPx(), Offset(106.dp.toPx(), y))
                drawCircle(ink, 8.dp.toPx(), Offset(106.dp.toPx(), y), style = Stroke(3.5.dp.toPx()))
                drawLine(
                    if (kind == EmptyKind.ERROR) colors.error else ink,
                    Offset(122.dp.toPx(), 12.dp.toPx()),
                    Offset(122.dp.toPx(), 32.dp.toPx()),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
