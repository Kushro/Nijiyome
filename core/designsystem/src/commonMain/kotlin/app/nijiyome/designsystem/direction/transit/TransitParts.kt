// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.transit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.modifier.ProvidableModifierLocal
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// The pieces of Tránsito nocturno shared by its decoration and its three cards (`:core:ui` cards/transit): the line color of a series,
// the station plate ("E09", like G09 on the Ginza line) and the route of episodes with the train ("you are here").
// Everything is drawn with Modifier.drawBehind, so moving the train or the drawn length only redraws.

/** Exponential ease out of the artifact (`cubic-bezier(.16, 1, .3, 1)`). */
val TransitEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

/** WCAG contrast ratio of two opaque colors. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * The colors of one series line. [raw] is the series color, [ink] the same color pushed toward white (dark) or black (light) until it
 * reaches 3:1 on the surface it is drawn on, [track] the aired-but-unwatched stretch.
 */
@Immutable
data class TransitLine(val raw: Color, val ink: Color, val track: Color)

/** Line colors for [seriesColor] (the cover color; null = the palette accent), legible on [surface]. */
fun transitLine(seriesColor: Color, tone: ThemeTone, surface: Color): TransitLine {
    val dark = tone != ThemeTone.LIGHT
    val toward = if (dark) White else Black
    var t = if (dark) 0.24f else 0.34f
    var ink = mix(seriesColor, toward, t)
    while (contrastRatio(ink, surface) < 3f && t < 0.86f) {
        t += 0.08f
        ink = mix(seriesColor, toward, t)
    }
    return TransitLine(raw = seriesColor, ink = ink, track = mix(surface, ink, 0.34f))
}

/** [transitLine] for the current theme, drawn on surfaceContainerHigh (cards, panels). */
@Composable
fun rememberTransitLine(seriesColor: Color?, surface: Color = MaterialTheme.colorScheme.surfaceContainerHigh): TransitLine {
    val base = seriesColor ?: MaterialTheme.colorScheme.primary
    val tone = LocalThemeTone.current
    return remember(base, tone, surface) { transitLine(base, tone, surface) }
}

/** Ink or porcelain, whichever reads on [fill]. */
fun inkOn(fill: Color): Color =
    if (contrastRatio(TransitInk.ink, fill) >= contrastRatio(TransitInk.porcelain, fill)) TransitInk.ink else TransitInk.porcelain

// ---- Station plate ----

/**
 * The station plate: a porcelain disc ringed in the line color with the prefix ([prefix], "E" or "C") over the number, two digits at
 * least. [pending] = the next episode has not aired yet (surface disc, dashed ring). Decorative: the card carries the text.
 */
@Composable
fun StationCode(
    number: Int,
    prefix: String,
    line: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    pending: Boolean = false,
    halo: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val digits = number.coerceAtLeast(0).toString().padStart(2, '0')
    val outline = MaterialTheme.colorScheme.outline
    val pendingFill = MaterialTheme.colorScheme.surface
    val textColor = if (pending) MaterialTheme.colorScheme.onSurface else TransitInk.ink
    val family = transitDisplayFamily()
    val density = LocalDensity.current
    val prefixSize = with(density) { (size * 0.25f).toSp() }
    val numberFactor = when (digits.length) {
        2 -> 0.4f
        3 -> 0.3f
        else -> 0.24f
    }
    val numberSize = with(density) { (size * numberFactor).toSp() }
    val trim = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { }
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(halo, radius = r + 1.5.dp.toPx())
                if (pending) {
                    drawCircle(pendingFill, radius = r)
                    val ring = 2.dp.toPx()
                    drawCircle(outline, radius = r - ring / 2f, style = Stroke(ring))
                    val dash = 1.5.dp.toPx()
                    drawCircle(
                        line,
                        radius = r - 3.dp.toPx() - dash / 2f,
                        style = Stroke(dash, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                    )
                } else {
                    drawCircle(TransitInk.porcelain, radius = r)
                    val ring = r * 2f * 0.11f
                    drawCircle(line, radius = r - ring / 2f, style = Stroke(ring))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (size >= 28.dp) {
                Text(
                    prefix,
                    color = textColor,
                    maxLines = 1,
                    style = TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = prefixSize,
                        lineHeight = prefixSize,
                        letterSpacing = 0.02.em,
                        lineHeightStyle = trim,
                    ),
                )
            }
            Text(
                digits,
                color = textColor,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Black,
                    fontSize = numberSize,
                    lineHeight = numberSize,
                    letterSpacing = (-0.02).em,
                    fontFeatureSettings = "tnum",
                    lineHeightStyle = trim,
                ),
            )
        }
    }
}

// ---- Route ----

/** One station of a route: watched, aired and not watched, not aired yet, delayed. */
enum class StopState { WATCHED, AIRED, UNAIRED, DELAYED }

/** Which stations to draw for a long series (more than [max]): the ends, the [keys] and an even sample (artifact `pick`). */
fun sampleStops(count: Int, keys: List<Int>, max: Int): List<Int> {
    if (count <= max) return List(count) { it }
    val set = mutableSetOf(0, count - 1)
    keys.filter { it in 0 until count }.forEach { set.add(it) }
    val step = maxOf(1, (count.toFloat() / (max - 4)).roundToInt())
    var i = step
    while (i < count - 1) {
        set.add(i)
        i += step
    }
    return set.sorted()
}

/** Geometry of a route; [strip] = the large line above an episode list. */
private class RouteSpec(val strip: Boolean) {
    val pad = if (strip) 12.dp else 5.dp
    val future = if (strip) 2.dp else 1.5.dp
    val track = if (strip) 5.dp else 3.dp
    val run = if (strip) 6.dp else 4.dp
    val stop = if (strip) 5.dp else 3.5.dp
    val stopSmall = if (strip) 4.5.dp else 3.dp
    val stroke = if (strip) 2.5.dp else 2.dp
    val train = if (strip) 10.dp else 7.dp
}

/**
 * The line of a series with one station per episode (sampled past [maxStops]) and the train at [current] (-1 = none).
 * [line] = [TransitLine] of the series, [halo] = the surface under the route (stations not aired are filled with it),
 * [muted] = paused or dropped (the train turns grey), [emphasized] = the card is hovered or focused (the run glows),
 * [openEnd] = the total is unknown (the line ends in a dotted stretch), [labels] = print key station numbers under the line
 * ([labelPrefix] + number, starting at [firstNumber]). [animateEntrance] draws the line left to right and brings the train in, once,
 * under the full motion level (performance mode keeps only the stroke; calm and off show the final state).
 */
@Composable
fun TransitRoute(
    stops: List<StopState>,
    current: Int,
    line: TransitLine,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    strip: Boolean = false,
    muted: Boolean = false,
    emphasized: Boolean = false,
    openEnd: Boolean = false,
    labels: Boolean = false,
    labelPrefix: String = "E",
    firstNumber: Int = 1,
    maxStops: Int = if (strip) 30 else 26,
    halo: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    animateEntrance: Boolean = false,
) {
    val n = stops.size
    val spec = remember(strip) { RouteSpec(strip) }
    val colors = MaterialTheme.colorScheme
    val warn = LocalSignalColors.current.warn
    val level = LocalMotion.current.level
    val lastW = stops.lastIndexOf(StopState.WATCHED)
    val lastA = stops.indexOfLast { it != StopState.UNAIRED }
    val shown = remember(stops, current, maxStops) { sampleStops(n, listOf(lastW, lastA, current), maxStops) }
    val span = if (openEnd) 0.82f else 1f
    fun fx(i: Int): Float = (if (n <= 1) 0.5f else i.toFloat() / (n - 1)) * span

    val drawFromZero = animateEntrance && (level == MotionLevel.FULL || level == MotionLevel.PERF)
    val drawn = remember { Animatable(if (drawFromZero) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (drawn.value < 1f) drawn.animateTo(1f, tween(520, easing = TransitEase))
    }
    val target = if (current >= 0) fx(current) else 0f
    val trainFromStart = animateEntrance && level == MotionLevel.FULL
    val train = remember { Animatable(if (trainFromStart) 0f else target) }
    var arrived by remember { mutableStateOf(!trainFromStart) }
    LaunchedEffect(target, level) {
        when {
            !arrived -> {
                delay(380)
                train.animateTo(target, tween(500, easing = TransitEase))
                arrived = true
            }
            level == MotionLevel.FULL -> train.animateTo(target, tween(320, easing = TransitEase))
            else -> train.snapTo(target)
        }
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontFamily = transitDisplayFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = if (strip) 11.sp else 10.5.sp,
        fontFeatureSettings = "tnum",
    )
    val labelGap = if (strip) 8.dp else 4.dp
    val labelBand = if (labels) labelGap + 16.dp else 0.dp
    val keySet = remember(n, current, lastA) { setOf(0, n - 1, current, lastA) }
    val allLabels = strip && n <= 13
    val runColor = when {
        muted -> colors.outline
        emphasized && LocalThemeTone.current != ThemeTone.LIGHT -> line.raw
        else -> line.ink
    }
    val trainOuter = if (muted) colors.outline else line.raw
    val trainInner = if (muted) colors.outline else line.ink

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height + labelBand)
            .clearAndSetSemantics { }
            .drawBehind {
                if (n == 0) return@drawBehind
                val pad = spec.pad.toPx()
                val w = size.width - 2 * pad
                val y = height.toPx() / 2f
                fun x(f: Float) = pad + f * w
                val cutoff = x(drawn.value * span) + 0.5f
                // Not aired: thin dotted rail across the whole width.
                drawLine(
                    colors.outline.copy(alpha = 0.75f * drawn.value),
                    Offset(pad, y),
                    Offset(pad + w, y),
                    strokeWidth = spec.future.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
                if (lastA > 0) {
                    drawLine(line.track, Offset(pad, y), Offset(minOf(x(fx(lastA)), cutoff), y), strokeWidth = spec.track.toPx(), cap = StrokeCap.Round)
                }
                if (lastW > 0) {
                    val width = spec.run.toPx() + if (emphasized) 1.dp.toPx() else 0f
                    drawLine(runColor, Offset(pad, y), Offset(minOf(x(fx(lastW)), cutoff), y), strokeWidth = width, cap = StrokeCap.Round)
                }
                for (i in shown) {
                    val cx = x(fx(i))
                    if (cx > cutoff) continue
                    drawStop(stops[i], Offset(cx, y), spec, line.ink, runColor, halo, colors.outline, warn)
                }
                if (current in 0 until n) {
                    drawTrain(Offset(x(train.value), y), spec.train.toPx(), trainOuter, trainInner, halo, if (strip) 2.5.dp.toPx() else 2.dp.toPx())
                }
                if (labels) {
                    val top = height.toPx() + labelGap.toPx()
                    for (i in shown) {
                        if (!allLabels && i !in keySet) continue
                        val text = labelPrefix + (i + firstNumber).toString().padStart(2, '0')
                        val layout = measurer.measure(text, labelStyle)
                        val lx = (x(fx(i)) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                        drawText(layout, color = if (i == current) colors.onSurface else colors.onSurfaceVariant, topLeft = Offset(lx, top))
                    }
                }
            },
    )
}

private fun DrawScope.drawStop(state: StopState, c: Offset, spec: RouteSpec, ink: Color, run: Color, halo: Color, outline: Color, warn: Color) {
    val stroke = spec.stroke.toPx()
    when (state) {
        StopState.WATCHED -> drawCircle(run, radius = spec.stopSmall.toPx() + stroke / 2f, center = c)
        StopState.AIRED -> {
            val r = spec.stop.toPx()
            drawCircle(TransitInk.porcelain, radius = r + stroke / 2f, center = c)
            drawCircle(ink, radius = r, center = c, style = Stroke(stroke))
        }
        StopState.UNAIRED -> {
            val r = spec.stopSmall.toPx()
            drawCircle(halo, radius = r, center = c)
            drawCircle(outline, radius = r, center = c, style = Stroke(1.5.dp.toPx()))
        }
        StopState.DELAYED -> {
            val r = spec.stop.toPx() + 0.5.dp.toPx()
            drawCircle(halo, radius = r, center = c)
            drawCircle(
                warn,
                radius = r,
                center = c,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
            )
        }
    }
}

/** The "you are here" ring: halo, line ring, porcelain gap and an ink core (artifact `.tr-train i`). */
fun DrawScope.drawTrain(c: Offset, radius: Float, outer: Color, inner: Color, halo: Color, haloWidth: Float) {
    drawCircle(halo, radius = radius + haloWidth, center = c)
    drawCircle(outer, radius = radius, center = c)
    drawCircle(TransitInk.porcelain, radius = radius * 0.6f, center = c)
    drawCircle(inner, radius = radius * 0.3f, center = c)
}

// ---- "Esta noche" track ----

/** True inside the TONIGHT region of Tránsito: the cards there draw their stop on the shared track. */
val TransitTonightTrack: ProvidableModifierLocal<Boolean> = modifierLocalOf { false }

/** Height reserved above the cards of the TONIGHT region for the track and the stops. */
val TransitTrackBand: Dp = 26.dp

/**
 * The stop of one card on the TONIGHT track: a porcelain station in the card's line color, drawn above the card's top-left corner
 * only when the card sits inside that region (no-op elsewhere, so the same card works in any list).
 */
@Composable
fun Modifier.transitTrackStop(lineInk: Color): Modifier {
    var onTrack by remember { mutableStateOf(false) }
    val halo = MaterialTheme.colorScheme.background
    return this
        .modifierLocalConsumer { onTrack = TransitTonightTrack.current }
        .drawBehind {
            if (!onTrack) return@drawBehind
            val c = Offset(22.dp.toPx(), -13.dp.toPx())
            val r = 8.dp.toPx()
            drawCircle(halo, radius = r + 2.dp.toPx(), center = c)
            drawCircle(TransitInk.porcelain, radius = r, center = c)
            val ring = 3.5.dp.toPx()
            drawCircle(lineInk, radius = r - ring / 2f, center = c, style = Stroke(ring))
        }
}
