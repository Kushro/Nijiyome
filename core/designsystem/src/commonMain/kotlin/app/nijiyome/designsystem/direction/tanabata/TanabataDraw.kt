// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.tanabata

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_tanabata_washi
import org.jetbrains.compose.resources.imageResource
import kotlin.math.max
import kotlin.math.roundToInt

// Sparkles redesign, lane D1. The materials of Tanabata as drawing helpers, shared by the decor and by the tanzaku cards of :core:ui.

/** Bamboo nodes along rods and rails, every 176 dp with a 3 dp joint (artifact `--tz-rod`, `--tz-rail`). */
val TanabataNodeEvery: Dp = 176.dp

/**
 * The washi fibre mask (docs/redesign/herramientas/washi.py): a grayscale tile whose luminance is fibre coverage. [image] is null
 * when the motion level forbids textures (`LocalMotion.current.rich == false`), and every draw becomes a no-op.
 */
@Immutable
class WashiTexture internal constructor(internal val image: ImageBitmap?)

/** The washi tile, or an empty texture when textures are off. */
@Composable
fun rememberWashi(): WashiTexture =
    if (LocalMotion.current.rich) WashiTexture(imageResource(Res.drawable.rd_tanabata_washi)) else WashiTexture(null)

/** Turns the gray tile into [tint] with the tile's luminance as alpha (ColorMatrix offsets are in 0..255). */
fun washiFilter(tint: Color): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, tint.red * 255f,
            0f, 0f, 0f, 0f, tint.green * 255f,
            0f, 0f, 0f, 0f, tint.blue * 255f,
            1f, 0f, 0f, 0f, 0f,
        ),
    ),
)

/** Tiles the washi fibres over [topLeft]/[size] (default: the whole draw area), tinted by [filter] (see [washiFilter]). */
fun DrawScope.drawWashi(
    washi: WashiTexture,
    filter: ColorFilter,
    alpha: Float,
    tileSize: Float,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
) {
    val image = washi.image ?: return
    if (size.width <= 0f || size.height <= 0f) return
    val tile = max(16, tileSize.roundToInt())
    val right = topLeft.x + size.width
    val bottom = topLeft.y + size.height
    clipRect(topLeft.x, topLeft.y, right, bottom) {
        var y = topLeft.y.toInt()
        while (y < bottom) {
            var x = topLeft.x.toInt()
            while (x < right) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(x, y),
                    dstSize = IntSize(tile, tile),
                    alpha = alpha,
                    colorFilter = filter,
                    filterQuality = FilterQuality.Low,
                )
                x += tile
            }
            y += tile
        }
    }
}

/** Washi fibres of [tint] behind the content of this node. Nothing under the PERF / OFF motion levels. */
@Composable
fun Modifier.washi(tint: Color, alpha: Float = 0.35f, tile: Dp = 160.dp): Modifier {
    val washi = rememberWashi()
    if (washi.image == null) return this
    return this.drawWithCache {
        val filter = washiFilter(tint)
        val tilePx = tile.toPx()
        onDrawBehind { drawWashi(washi, filter, alpha, tilePx) }
    }
}

/** Draws progress as ink running down a paper from the top: solid ink, then a soft edge (artifact `.tz-ink.has-ink`). */
fun DrawScope.drawInkDown(paper: Color, progress: Float, topLeft: Offset = Offset.Zero, size: Size = this.size) {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return
    val inked = TanabataMaterials.inked(paper)
    val edge = TanabataMaterials.inkEdge(paper)
    val soft = 5.dp.toPx()
    val tail = 2.dp.toPx()
    val h = size.height * p
    if (p >= 1f) {
        drawRect(inked, topLeft, size)
        return
    }
    val solid = (h - soft).coerceAtLeast(0f)
    if (solid > 0f) drawRect(inked, topLeft, Size(size.width, solid))
    val start = topLeft.y + solid
    val end = (topLeft.y + h + tail).coerceAtMost(topLeft.y + size.height)
    if (end > start) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to inked,
                (4f / 7f) to edge,
                1f to Color.Transparent,
                startY = start,
                endY = topLeft.y + h + tail,
            ),
            topLeft = Offset(topLeft.x, start),
            size = Size(size.width, end - start),
        )
    }
}

/**
 * A bamboo rod across [start]..[end] at [top]: lit edge, body, shaded edge, with a node every [TanabataNodeEvery]
 * (artifact `--tz-rod`).
 */
fun DrawScope.drawBambooRod(bamboo: Bamboo, top: Float, thickness: Float, start: Float = 0f, end: Float = size.width) {
    if (end <= start) return
    val w = end - start
    val edge = thickness * 2f / 7f
    drawRect(bamboo.hi, Offset(start, top), Size(w, edge))
    drawRect(bamboo.body, Offset(start, top + edge), Size(w, thickness - 2f * edge))
    drawRect(bamboo.lo, Offset(start, top + thickness - edge), Size(w, edge))
    val every = TanabataNodeEvery.toPx()
    val joint = 2.dp.toPx()
    val lit = 1.dp.toPx()
    var x = start + every - joint - lit
    while (x < end) {
        drawRect(bamboo.lo, Offset(x, top), Size(joint, thickness))
        drawRect(bamboo.hi, Offset(x + joint, top), Size(lit, thickness))
        x += every
    }
}

/** A hairline rail with bamboo joints: segments of 173 dp and 3 dp gaps (artifact `--tz-rail`). [vertical] draws it top to bottom. */
fun DrawScope.drawRail(color: Color, at: Float, vertical: Boolean = false, from: Float = 0f, to: Float = if (vertical) size.height else size.width) {
    val every = TanabataNodeEvery.toPx()
    val gap = 3.dp.toPx()
    val stroke = 1.dp.toPx()
    var s = from
    while (s < to) {
        val e = (s + every - gap).coerceAtMost(to)
        if (vertical) {
            drawRect(color, Offset(at - stroke / 2f, s), Size(stroke, e - s))
        } else {
            drawRect(color, Offset(s, at - stroke / 2f), Size(e - s, stroke))
        }
        s += every
    }
}

/** A hanging thread from [top] to [bottom] at [x], with a knot over the paper's hole unless [knot] is false; [dashed] = still to come. */
fun DrawScope.drawThread(color: Color, x: Float, top: Float, bottom: Float, knot: Boolean = true, dashed: Boolean = false) {
    val w = 1.5.dp.toPx()
    drawLine(
        color = color,
        start = Offset(x, top),
        end = Offset(x, bottom),
        strokeWidth = w,
        cap = StrokeCap.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
    )
    if (knot) {
        val kw = 7.dp.toPx()
        val kh = 5.dp.toPx()
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(x - kw / 2f, bottom - kh + 1.dp.toPx()), Size(kw, kh))
        drawOval(color, Offset(x - kw / 2f, bottom - kh), Size(kw, kh))
    }
}

/** The thread hole of a paper: a disc of the floor color with a thin ink ring. */
fun DrawScope.drawHole(center: Offset, ground: Color, radius: Float = 3.dp.toPx()) {
    drawCircle(ground, radius, center)
    drawCircle(TanabataMaterials.ink.copy(alpha = 0.4f), radius - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
}

/**
 * A sheet of tanzaku paper: soft paper shadow, the paper color, ink running down with [ink] (0..1), washi fibres on top. Under PERF the
 * shadow becomes a hairline ring and the texture goes (artifact `[data-motion="perf"]`). Clips to [shape]; content draws above.
 */
@Composable
fun Modifier.tanzakuPaper(
    paper: Color,
    shape: Shape = RoundedCornerShape(2.dp),
    ink: Float = 0f,
    elevated: Boolean = true,
    fibreTile: Dp = 160.dp,
): Modifier {
    val tone = LocalThemeTone.current
    val perf = LocalMotion.current.level == MotionLevel.PERF
    val shadowColor = TanabataMaterials.paperShadow(tone)
    val lifted = when {
        !elevated -> this
        perf -> this.border(1.dp, TanabataMaterials.ink.copy(alpha = 0.25f), shape)
        else -> this.shadow(6.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
    }
    val edged = if (tone == ThemeTone.LIGHT && !perf) lifted.border(1.dp, TanabataMaterials.ink.copy(alpha = 0.10f), shape) else lifted
    return edged
        .clip(shape)
        .background(paper)
        .then(if (ink > 0f) Modifier.drawBehind { drawInkDown(paper, ink) } else Modifier)
        .washi(TanabataMaterials.fibre(paper), alpha = 0.35f, tile = fibreTile)
}
