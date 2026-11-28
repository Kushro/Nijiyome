// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.tanabata

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_skin_algodon
import app.nijiyome.designsystem.resources.rd_skin_crepe
import app.nijiyome.designsystem.resources.rd_skin_hanafuda_kanji
import app.nijiyome.designsystem.resources.rd_skin_klee_one_semibold
import app.nijiyome.designsystem.resources.rd_skin_kozo
import app.nijiyome.designsystem.resources.rd_skin_kraft
import app.nijiyome.designsystem.resources.rd_skin_shippori_mincho_bold
import app.nijiyome.designsystem.resources.rd_skin_verjurado
import app.nijiyome.designsystem.resources.rd_skin_yusei_magic
import app.nijiyome.designsystem.resources.rd_tanabata_yuji_syuku
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.imageResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// v1.3.3 paper skins of the tanzaku cards (docs/redesign/pieles, chosen by the owner on 2026-11-27). The materials live here so the
// row decor (what the cards hang from) and the cards of :core:ui read the same skin. The ids match `TanzakuSkinIds` in :core:model.

/** What a row of cards hangs from. */
enum class SkinHanger { BAMBOO, BRANCH, LINE, MAT, BAMBOO_CHAIN }

/** The outline of the paper. */
enum class SkinEdge { STRAIGHT, DECKLE, RUFFLE }

/** The grayscale shading tiles of docs/redesign/herramientas/papeles.py (`--app`). */
enum class SkinPaperTexture { NONE, KOZO, CREPE, KRAFT, COTTON, LAID }

/**
 * A paper skin. [textureStrength] is how far the tile's light and dark parts move the paper toward white and toward a deep shade of
 * itself; under the text only [calm] of it stays (the owner found a blue crepe card harder to read than a yellow one: on mid tones the
 * dark ridges land near the ink). [widthScale] and [air] make the strips bigger and give them room in a row.
 */
enum class TanzakuSkin(
    val id: String,
    val hanger: SkinHanger,
    val edge: SkinEdge,
    val texture: SkinPaperTexture,
    val textureStrength: Float,
    val textureTile: Dp,
    val calm: Float,
    val widthScale: Float,
    val air: Dp,
) {
    PASTEL("pastel", SkinHanger.BRANCH, SkinEdge.STRAIGHT, SkinPaperTexture.LAID, 0.34f, 190.dp, 0.45f, 1.1f, 10.dp),
    KOZO("kozo", SkinHanger.BAMBOO, SkinEdge.DECKLE, SkinPaperTexture.KOZO, 0.5f, 260.dp, 0.35f, 1.08f, 8.dp),
    CREPE("crepe", SkinHanger.BAMBOO, SkinEdge.RUFFLE, SkinPaperTexture.CREPE, 0.62f, 210.dp, 0.3f, 1.08f, 8.dp),
    BOKASHI("bokashi", SkinHanger.BAMBOO, SkinEdge.STRAIGHT, SkinPaperTexture.KOZO, 0.34f, 240.dp, 0.4f, 1.08f, 8.dp),
    CHIYOGAMI("chiyogami", SkinHanger.BAMBOO, SkinEdge.STRAIGHT, SkinPaperTexture.KOZO, 0.26f, 240.dp, 1f, 1.08f, 8.dp),
    FOLDED("plegado", SkinHanger.BAMBOO, SkinEdge.STRAIGHT, SkinPaperTexture.COTTON, 0.36f, 190.dp, 0.4f, 1.08f, 8.dp),
    KRAFT("kraft", SkinHanger.LINE, SkinEdge.STRAIGHT, SkinPaperTexture.KRAFT, 0.3f, 300.dp, 0.4f, 1.08f, 8.dp),
    HANAFUDA("hanafuda", SkinHanger.MAT, SkinEdge.STRAIGHT, SkinPaperTexture.COTTON, 0.55f, 170.dp, 1f, 1.04f, 6.dp),
    LANTERN("linterna", SkinHanger.BAMBOO, SkinEdge.STRAIGHT, SkinPaperTexture.KOZO, 0.5f, 240.dp, 0.4f, 1.08f, 10.dp),
    FESTIVAL("festival", SkinHanger.BAMBOO_CHAIN, SkinEdge.STRAIGHT, SkinPaperTexture.NONE, 0f, 0.dp, 1f, 1.08f, 8.dp),
    ;

    /** Strips hang from a cord (Hanafuda cards lie on a mat, Kraft cards are pinned to a line). */
    val hangsByThread: Boolean get() = this != HANAFUDA && this != KRAFT

    companion object {
        val DEFAULT: TanzakuSkin = PASTEL

        fun of(id: String?): TanzakuSkin = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** The skin the tanzaku cards wear here (the app provides the user's choice; the Appearance picker provides each option's own). */
val LocalTanzakuSkin = staticCompositionLocalOf { TanzakuSkin.DEFAULT }

// ---------------------------------------------------------------- colors

/** Colors of a skin, derived from the status paper of [TanabataMaterials.paper]. Ink on all of them stays above 6:1. */
object SkinPaint {
    private val cream = Color(0xFFFBF6EA)
    private val kinari = Color(0xFFEFE6D2)

    /** Kraft board of the Kraft skin, whatever the status. */
    val kraft: Color = Color(0xFFC9A274)

    /** Ink on kraft. */
    val kraftInk: Color = Color(0xFF2A1C12)

    /** Hanafuda stock, rim and its darker edge. */
    val hanafudaStock: Color = Color(0xFFF3ECDA)
    val hanafudaRim: Color = Color(0xFF8E3626)
    val hanafudaEdge: Color = Color(0xFF5B1F15)

    /** Chiyogami label. */
    val label: Color = Color(0xFFF4ECDA)

    /** Andon wood. */
    val wood: Color = Color(0xFF2C1F16)

    /**
     * The paper of [skin] for a status paper [base]. Mid tones (blue, violet, vermilion) are lifted a little in every textured skin, so
     * the shading keeps its distance from the ink.
     */
    fun paper(skin: TanzakuSkin, base: Color): Color = when (skin) {
        TanzakuSkin.PASTEL -> lerp(base, cream, 0.62f)
        TanzakuSkin.KOZO -> lerp(base, kinari, 0.4f)
        TanzakuSkin.CREPE -> lerp(base, Color.White, 0.12f)
        else -> lerp(base, Color.White, 0.08f)
    }

    /** A deeper dye of [base]: same hue, more saturated, darker (artifact `--deep`). */
    fun deep(base: Color): Color {
        val (h, s, v) = hsv(base)
        return Color.hsv(h, min(1f, s * 1.3f + 0.08f), v * 0.7f)
    }

    /** The light side of a sheet (bokashi center, lantern glow, foil highlight). */
    fun light(base: Color, amount: Float = 0.6f): Color = lerp(base, Color(0xFFFFFAF0), amount)

    /** The three hanafuda ribbons: red (aka-tan), blue (ao-tan), violet. */
    val ribbonRed: Color = Color(0xFFC8321F)
    val ribbonBlue: Color = Color(0xFF2E5BB8)
    val ribbonViolet: Color = Color(0xFF6B3FA0)

    /** Colors of the paper chain of the Festival skin. */
    val chain: List<Color> = listOf(
        Color(0xFFE5484D), Color(0xFFF5C542), Color(0xFF4C9EE8), Color(0xFF5CC98A), Color(0xFFC084FC), Color(0xFFF28C38),
    )

    private fun hsv(c: Color): Triple<Float, Float, Float> {
        val r = c.red
        val g = c.green
        val b = c.blue
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return Triple(h, if (mx == 0f) 0f else d / mx, mx)
    }
}

// ---------------------------------------------------------------- texture

/** The tile of [texture], or null when there is none or the motion level forbids textures (PERF, OFF). */
@Composable
fun rememberSkinTexture(texture: SkinPaperTexture): ImageBitmap? {
    if (texture == SkinPaperTexture.NONE || !LocalMotion.current.rich) return null
    return imageResource(
        when (texture) {
            SkinPaperTexture.KOZO -> Res.drawable.rd_skin_kozo
            SkinPaperTexture.CREPE -> Res.drawable.rd_skin_crepe
            SkinPaperTexture.KRAFT -> Res.drawable.rd_skin_kraft
            SkinPaperTexture.COTTON -> Res.drawable.rd_skin_algodon
            SkinPaperTexture.LAID, SkinPaperTexture.NONE -> Res.drawable.rd_skin_verjurado
        },
    )
}

/**
 * One half of the shading: the tile's luminance above (or below) mid-gray becomes the alpha of [tint], times [strength]. Together the
 * two halves work like an overlay blend that does not need a blend mode (offsets in 0..255, as in [washiFilter]).
 */
private fun shadeFilter(tint: Color, strength: Float, lighter: Boolean): ColorFilter {
    val k = strength.coerceIn(0f, 1f)
    val a = if (lighter) 2f * k else -2f * k
    val o = if (lighter) -255f * k else 255f * k
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, tint.red * 255f,
                0f, 0f, 0f, 0f, tint.green * 255f,
                0f, 0f, 0f, 0f, tint.blue * 255f,
                a, 0f, 0f, 0f, o,
            ),
        ),
    )
}

private fun DrawScope.tile(image: ImageBitmap, tilePx: Float, filter: ColorFilter) {
    val t = max(16, tilePx.roundToInt())
    clipRect {
        var y = 0
        while (y < size.height) {
            var x = 0
            while (x < size.width) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(x, y),
                    dstSize = IntSize(t, t),
                    colorFilter = filter,
                    filterQuality = FilterQuality.Low,
                )
                x += t
            }
            y += t
        }
    }
}

/**
 * Paper shading of [skin] behind the content of this node, over its background ([paper] is that background, used for the dark side).
 * From [calmFrom] (a fraction of the height, where the text starts) only [TanzakuSkin.calm] of it stays. [inverted] darkens where the
 * tile is light (the backlit fibres of the Linterna skin).
 */
@Composable
fun Modifier.skinTexture(
    skin: TanzakuSkin,
    paper: Color,
    calmFrom: Float = 0.56f,
    strength: Float = skin.textureStrength,
    inverted: Boolean = false,
): Modifier {
    val image = rememberSkinTexture(skin.texture) ?: return this
    if (strength <= 0f) return this
    return this.drawWithCache {
        val tilePx = skin.textureTile.toPx()
        val light = shadeFilter(Color.White, strength, lighter = !inverted)
        val dark = shadeFilter(lerp(paper, Color.Black, 0.62f), strength, lighter = inverted)
        val calm = skin.calm.coerceIn(0f, 1f)
        val mask = if (calm < 1f) {
            val from = calmFrom.coerceIn(0f, 0.95f)
            Brush.verticalGradient(
                0f to Color.Black,
                from to Color.Black,
                min(1f, from + 0.09f) to Color.Black.copy(alpha = calm),
                1f to Color.Black.copy(alpha = calm),
            )
        } else {
            null
        }
        onDrawBehind {
            drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
            tile(image, tilePx, light)
            tile(image, tilePx, dark)
            if (mask != null) drawRect(mask, blendMode = BlendMode.DstIn)
            drawIntoCanvas { it.restore() }
        }
    }
}

// ---------------------------------------------------------------- edges

/** Torn (deckle) edges: a little on top and the sides, more at the bottom. Seeded, so each card keeps its own tear. */
class DeckleShape(private val seed: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = Random(seed)
        val w = size.width
        val h = size.height
        val side = with(density) { 3.dp.toPx() }
        val top = with(density) { 2.dp.toPx() }
        val bottom = min(h * 0.032f, with(density) { 11.dp.toPx() })
        val n = 14
        val path = Path().apply {
            moveTo(0f, r.nextFloat() * top)
            for (i in 1..n) lineTo(w * i / n, r.nextFloat() * top)
            for (i in 1..n * 2) lineTo(w - r.nextFloat() * side, h * i / (n * 2))
            for (i in n downTo 0) lineTo(w * i / n, h - r.nextFloat() * bottom)
            for (i in n * 2 - 1 downTo 1) lineTo(r.nextFloat() * side, h * i / (n * 2))
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is DeckleShape && other.seed == seed
    override fun hashCode(): Int = seed
}

/** Stretched crepe: a wavy bottom edge and barely wavy sides. */
class RuffleShape(private val seed: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = Random(seed)
        val w = size.width
        val h = size.height
        val wave = min(h * 0.016f, with(density) { 6.dp.toPx() })
        val side = with(density) { 1.2.dp.toPx() }
        val n = 30
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            for (i in 1..10) lineTo(w - (sin(i * 1.3f) * 0.5f + 0.5f) * side, h * i * 0.09f)
            for (i in n downTo 0) {
                val u = i.toFloat() / n
                lineTo(w * u, h - abs(sin(u * PI.toFloat() * 9f)) * wave - r.nextFloat() * wave * 0.2f)
            }
            for (i in 10 downTo 1) lineTo((sin(i * 1.7f) * 0.5f + 0.5f) * side, h * i * 0.09f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is RuffleShape && other.seed == seed
    override fun hashCode(): Int = seed
}

// ---------------------------------------------------------------- hangers

/** Draws what a row of cards hangs from at the top of the row: [thickness] is the rod's (the branch and line scale from it). */
fun DrawScope.drawSkinHanger(skin: TanzakuSkin, bamboo: Bamboo, thread: Color, tone: ThemeTone, thickness: Float) {
    when (skin.hanger) {
        SkinHanger.BAMBOO -> drawBambooRod(bamboo, top = 0f, thickness = thickness)
        SkinHanger.BAMBOO_CHAIN -> {
            drawBambooRod(bamboo, top = 0f, thickness = thickness)
            drawPaperChain(top = thickness * 0.5f)
        }
        SkinHanger.BRANCH -> drawBranch(branch(size.width), tone)
        SkinHanger.LINE -> {
            val y = 6.dp.toPx()
            drawLine(thread, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        SkinHanger.MAT -> drawMat(tone)
    }
}

/**
 * [drawSkinHanger] behind this node, with the branch's stem and leaf placements built once per size: the row hanger redraws on
 * every scroll frame of its row, and the branch was rebuilt (paths and all) on each of them.
 */
fun Modifier.skinHanger(skin: TanzakuSkin, bamboo: Bamboo, thread: Color, tone: ThemeTone, thickness: Dp): Modifier =
    this.drawWithCache {
        val px = thickness.toPx()
        val branch = if (skin.hanger == SkinHanger.BRANCH) branch(size.width) else null
        onDrawBehind {
            if (branch != null) drawBranch(branch, tone) else drawSkinHanger(skin, bamboo, thread, tone, px)
        }
    }

/** Where the branch runs and where its leaves hang, for one width: the stem path and per leaf (x, y, angle, scale). */
private class Branch(val stem: Path, val leaves: FloatArray)

/** One leaf in its own units (scaled per leaf); never mutated, so every branch shares it. */
private val BRANCH_LEAF: Path = Path().apply {
    moveTo(0f, 0f)
    cubicTo(10f, -7f, 38f, -7f, 58f, 0f)
    cubicTo(38f, 7f, 10f, 7f, 0f, 0f)
    close()
}

/** The wandering stem and the leaf placements of a branch [width] wide (seeded, so it is the same branch every time). */
private fun Density.branch(width: Float): Branch {
    val u = 1.dp.toPx()
    fun y(x: Float): Float = 6f * u + 3.5f * u * sin(x / (230f * u)) + 1.5f * u * sin(x / (83f * u) + 1f)
    val stem = Path().apply {
        moveTo(-10f * u, y(-10f * u))
        var x = 0f
        while (x <= width + 20f * u) {
            lineTo(x, y(x))
            x += 16f * u
        }
    }
    val r = Random(91)
    var k = 0
    var lx = r.nextFloat() * 20f * u
    val leaves = ArrayList<Float>()
    while (lx < width + 30f * u) {
        val angle = (if (k % 2 == 0) -1f else 1f) * (30f + r.nextFloat() * 40f) + 90f
        val scale = (0.42f + r.nextFloat() * 0.34f) * u
        leaves += lx
        leaves += y(lx)
        leaves += angle
        leaves += scale
        lx += 30f * u + r.nextFloat() * 14f * u
        k++
    }
    return Branch(stem, leaves.toFloatArray())
}

/** A bamboo branch along the top: a wandering stem with leaves hanging from it (Pastel en rama). */
private fun DrawScope.drawBranch(branch: Branch, tone: ThemeTone) {
    val u = 1.dp.toPx()
    val stem = if (tone == ThemeTone.LIGHT) Color(0xFF6E8F52) else Color(0xFF7A9A5E)
    val leafA = if (tone == ThemeTone.LIGHT) Color(0xFF79A05E) else Color(0xFF7FA565)
    val leafB = if (tone == ThemeTone.LIGHT) Color(0xFF8DB372) else Color(0xFF94B97A)
    val leaves = branch.leaves
    for (k in 0 until leaves.size / 4) {
        val i = k * 4
        withTransform({
            translate(leaves[i], leaves[i + 1])
            rotate(leaves[i + 2], pivot = Offset.Zero)
            scale(leaves[i + 3], leaves[i + 3], pivot = Offset.Zero)
        }) {
            drawPath(BRANCH_LEAF, if (k % 3 == 0) leafB else leafA)
        }
    }
    drawPath(branch.stem, stem, style = Stroke(4f * u, cap = StrokeCap.Round))
}

/** A loop chain of colored paper rings hanging along the rod (Festival). */
private fun DrawScope.drawPaperChain(top: Float) {
    val u = 1.dp.toPx()
    val ringW = 20f * u
    val ringH = 11f * u
    val stroke = Stroke(2.8f * u)
    var x = 6f * u
    var i = 0
    while (x < size.width) {
        val c = SkinPaint.chain[i % SkinPaint.chain.size]
        if (i % 2 == 0) {
            drawOval(c, Offset(x, top), Size(ringW, ringH), style = stroke)
            x += ringW - 5f * u
        } else {
            drawOval(c, Offset(x, top), Size(6f * u, ringH), style = stroke)
            x += 3f * u
        }
        i++
    }
}

/** The play mat the Hanafuda cards are dealt on. */
private fun DrawScope.drawMat(tone: ThemeTone) {
    val u = 1.dp.toPx()
    val felt = if (tone == ThemeTone.LIGHT) Color(0x1F6B1F2A) else Color(0x336B1F2A)
    val edge = if (tone == ThemeTone.LIGHT) Color(0x266B1F2A) else Color(0x406B1F2A)
    val inset = 4f * u
    val r = androidx.compose.ui.geometry.CornerRadius(18f * u)
    drawRoundRect(felt, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), r)
    drawRoundRect(edge, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), r, style = Stroke(1.5f * u))
}

// ---------------------------------------------------------------- cords

/** The cord of [skin] from [top] to [bottom] at [x]: twisted two-color cords, plain threads, with or without a knot. */
fun DrawScope.drawSkinCord(skin: TanzakuSkin, thread: Color, x: Float, top: Float, bottom: Float, knot: Boolean = true) {
    val u = 1.dp.toPx()
    when (skin) {
        TanzakuSkin.KOZO -> twisted(Color(0xFFEFE7D3), Color(0xFFB7A988), x, top, bottom, 2.6f * u, knot)
        TanzakuSkin.CREPE -> twisted(Color(0xFFF7F1EA), Color(0xFFD8323A), x, top, bottom, 2.6f * u, knot)
        TanzakuSkin.CHIYOGAMI -> drawThread(Color(0xFFE9C45A), x, top, bottom, knot)
        TanzakuSkin.FESTIVAL -> twisted(Color(0xFFE9C45A), Color(0xFFA8771E), x, top, bottom, 2.6f * u, knot)
        TanzakuSkin.LANTERN -> drawThread(SkinPaint.wood, x, top, bottom, knot)
        TanzakuSkin.BOKASHI -> {
            drawThread(Color(0xFFF8F3EA), x, top, bottom, knot = false)
            drawBow(Offset(x, top + 3f * u))
        }
        TanzakuSkin.PASTEL -> drawLine(lerp(thread, Color(0xFFC9B48A), 0.2f), Offset(x, top), Offset(x, bottom), strokeWidth = 1.2f * u)
        TanzakuSkin.FOLDED -> drawLine(thread, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5f * u)
        TanzakuSkin.KRAFT, TanzakuSkin.HANAFUDA -> Unit
    }
}

private fun DrawScope.twisted(a: Color, b: Color, x: Float, top: Float, bottom: Float, width: Float, knot: Boolean) {
    drawLine(a, Offset(x, top), Offset(x, bottom), strokeWidth = width, cap = StrokeCap.Round)
    val step = width * 1.3f
    var y = top + step * 0.5f
    while (y < bottom - step * 0.5f) {
        drawLine(b, Offset(x - width / 2f, y + step * 0.35f), Offset(x + width / 2f, y - step * 0.35f), strokeWidth = width * 0.55f)
        y += step
    }
    if (knot) {
        val kw = width * 2.8f
        val kh = width * 2f
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(x - kw / 2f, bottom - kh + 1.dp.toPx()), Size(kw, kh))
        drawOval(b, Offset(x - kw / 2f, bottom - kh), Size(kw, kh))
    }
}

/** The two loops of the Bokashi bow around its knot at (0, 0), in dp; drawn scaled by the density, so no path per draw. */
private val BOW_LOOPS: Path = Path().apply {
    moveTo(0f, 0f)
    cubicTo(-5f, -7f, -13f, -6f, -12f, 0f)
    cubicTo(-11f, 4f, -4f, 2f, 0f, 0f)
    moveTo(0f, 0f)
    cubicTo(5f, -7f, 13f, -6f, 12f, 0f)
    cubicTo(11f, 4f, 4f, 2f, 0f, 0f)
}

/** A thread bow tied on the rod (Bokashi). */
private fun DrawScope.drawBow(at: Offset) {
    val u = 1.dp.toPx()
    val c = Color(0xFFF8F3EA)
    withTransform({
        translate(at.x, at.y)
        scale(u, u, pivot = Offset.Zero)
    }) {
        // In dp units here: the stroke is 1.8 dp once scaled.
        drawPath(BOW_LOOPS, c, style = Stroke(1.8f, cap = StrokeCap.Round))
    }
    drawLine(c, at, Offset(at.x - 5f * u, at.y + 9f * u), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    drawCircle(c, 2f * u, at)
}

/** A metal eyelet around the hole (Pastel en rama). */
fun DrawScope.drawEyelet(center: Offset, ground: Color) {
    val u = 1.dp.toPx()
    drawCircle(Color(0xFF8E8573), 6.5f * u, center)
    drawCircle(Color(0xFFC8BFAE), 5.5f * u, center)
    drawCircle(ground, 3.6f * u, center)
    drawCircle(Color.Black.copy(alpha = 0.35f), 3.6f * u, center, style = Stroke(1f * u))
}

// ---------------------------------------------------------------- type

/** Klee One SemiBold: the handwritten wish line of every skin. */
@Composable
fun skinWishFamily(): FontFamily {
    val font = Font(Res.font.rd_skin_klee_one_semibold, FontWeight.SemiBold)
    return remember(font) { FontFamily(font) }
}

/** The title face of [skin], or null to keep the direction's. */
@Composable
fun skinTitleFamily(skin: TanzakuSkin): FontFamily? = when (skin) {
    TanzakuSkin.KOZO, TanzakuSkin.HANAFUDA, TanzakuSkin.LANTERN, TanzakuSkin.FOLDED -> {
        val font = Font(Res.font.rd_skin_shippori_mincho_bold, FontWeight.Bold)
        remember(font) { FontFamily(font) }
    }
    TanzakuSkin.CREPE, TanzakuSkin.FESTIVAL -> {
        val font = Font(Res.font.rd_skin_yusei_magic, FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    TanzakuSkin.BOKASHI -> {
        val font = Font(Res.font.rd_tanabata_yuji_syuku, FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    else -> null
}

/** The eleven brush kanji of the Hanafuda ribbons. */
@Composable
fun hanafudaKanjiFamily(): FontFamily {
    val font = Font(Res.font.rd_skin_hanafuda_kanji, FontWeight.Normal)
    return remember(font) { FontFamily(font) }
}
