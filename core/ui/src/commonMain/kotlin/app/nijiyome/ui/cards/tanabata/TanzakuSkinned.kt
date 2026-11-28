// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.tanabata

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.direction.tanabata.DeckleShape
import app.nijiyome.designsystem.direction.tanabata.LocalTanzakuSkin
import app.nijiyome.designsystem.direction.tanabata.RuffleShape
import app.nijiyome.designsystem.direction.tanabata.SkinEdge
import app.nijiyome.designsystem.direction.tanabata.SkinPaint
import app.nijiyome.designsystem.direction.tanabata.TanabataMaterials
import app.nijiyome.designsystem.direction.tanabata.TanzakuSkin
import app.nijiyome.designsystem.direction.tanabata.drawBambooRod
import app.nijiyome.designsystem.direction.tanabata.drawEyelet
import app.nijiyome.designsystem.direction.tanabata.drawHole
import app.nijiyome.designsystem.direction.tanabata.drawSkinCord
import app.nijiyome.designsystem.direction.tanabata.hanafudaKanjiFamily
import app.nijiyome.designsystem.direction.tanabata.skinTexture
import app.nijiyome.designsystem.direction.tanabata.skinTitleFamily
import app.nijiyome.designsystem.direction.tanabata.skinWishFamily
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.model.CardSize
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.LocalCardIndex
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.media.MediaCover
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

// v1.3.3 paper skins (docs/redesign/pieles): the "tanzaku" strip in rows, wish rows, the hero and the grid, as each skin draws it.
// The paper, cord, hanger and texture come from :core:designsystem (TanzakuSkin.kt); this file lays out the poster, the texts and the
// details only one skin has (the crepe photo, the chiyogami label, the kraft tape and stamp, the hanafuda rim and ribbon, the andon
// frame, the origami flap, the festival fringe). Compacta and Continuar keep the shared parts, which wear the skin's paper.

/** Extra cord length of the Pastel strips by position, so a row hangs at different heights from the branch. */
private val PASTEL_DROP = intArrayOf(10, 0, 22, 6, 30, 14)

/** The one cord length of Pastel en rama when the user turns the stagger off (the mean of [PASTEL_DROP]). */
private const val PASTEL_EVEN = 14

/** Kanji of the Hanafuda ribbon per status (decorative; the status is also written in the text). */
private fun StatusSlot?.ribbonKanji(): String = when (this) {
    StatusSlot.WATCHING -> "視聴中"
    StatusSlot.PLANNING -> "予定"
    StatusSlot.COMPLETED -> "完走"
    StatusSlot.PAUSED -> "休止"
    StatusSlot.DROPPED -> "中断"
    StatusSlot.REWATCHING -> "再視聴"
    null -> "予定"
}

private fun StatusSlot?.ribbonColor(): Color = when (this) {
    StatusSlot.WATCHING, StatusSlot.COMPLETED, null -> SkinPaint.ribbonRed
    StatusSlot.PLANNING, StatusSlot.REWATCHING -> SkinPaint.ribbonBlue
    StatusSlot.PAUSED, StatusSlot.DROPPED -> SkinPaint.ribbonViolet
}

/** Title size of the skinned strip by card size. */
private fun titleSize(size: CardSize, hero: Boolean): TextUnit = when {
    hero -> 19.sp
    size == CardSize.SMALL -> 13.sp
    size == CardSize.MEDIUM -> 15.sp
    size == CardSize.LARGE -> 16.5.sp
    else -> 18.sp
}

@Composable
internal fun SkinnedStripCard(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
    /** "Esta noche" (TanzakuTonight.kt): tag, action and kind block inside the paper; null in every other context. */
    tonight: TonightCardInfo? = null,
) {
    val skin = LocalTanzakuSkin.current
    val tone = LocalThemeTone.current
    val spatial = LocalMotion.current.spatial
    val interaction = remember { MutableInteractionSource() }
    val state = rememberTzState(interaction)
    val texts = tzTexts(data, show)
    val hero = context == CardContext.HERO
    val small = size == CardSize.SMALL && !hero
    val onRod = hero || (context != CardContext.GRID && tzHangs())
    val hangs = onRod && skin.hangsByThread
    val pinned = onRod && skin == TanzakuSkin.KRAFT
    // Kept small: seeds and per-position tables multiply it, and a raw hash code overflows to a negative index.
    val index = (LocalCardIndex.current.takeIf { it >= 0 } ?: (data.key.hashCode() and 0x7fffffff)) % 997
    // A tag on the cord (the night timeline's time, the pile's count) needs a longer cord, or room above a pinned or dealt card.
    val tagDrop = if (tonight?.cordTag != null) TONIGHT_TAG_DROP else 0.dp
    val cordH = when {
        !hangs -> 0.dp
        skin == TanzakuSkin.PASTEL && LocalLook.current.design.tanzakuStagger -> TZ_THREAD + 8.dp + PASTEL_DROP[index % PASTEL_DROP.size].dp
        skin == TanzakuSkin.PASTEL -> TZ_THREAD + 8.dp + PASTEL_EVEN.dp
        else -> TZ_THREAD + 4.dp
    } + (if (hangs) tagDrop else 0.dp)
    val topGap = when {
        hangs -> cordH
        // The clothespin reaches 18 dp above the paper, so a pinned card's tag needs room above it.
        pinned -> 12.dp + if (tonight?.cordTag != null) tagDrop + 12.dp else 0.dp
        context == CardContext.GRID -> 0.dp
        else -> 6.dp + tagDrop
    }
    val status = TanabataMaterials.paper(data.listStatus, tone)
    val pressed = state.pressed
    val paper = when (skin) {
        TanzakuSkin.KRAFT -> SkinPaint.kraft
        TanzakuSkin.HANAFUDA -> SkinPaint.hanafudaStock
        else -> SkinPaint.paper(skin, status)
    }.let { if (pressed) TanabataMaterials.pressed(it) else it }
        // Later tonight (the night timeline): the paper pales toward cream, the ink stays.
        .let { if (tonight?.dim == true) lerp(it, TZ_CREAM, 0.45f) else it }
    val deep = SkinPaint.deep(status)
    val inkColor = if (skin == TanzakuSkin.KRAFT) SkinPaint.kraftInk else TanabataMaterials.ink
    val progress = data.ink(show)
    // v1.3.5: the wish line is reserved wherever this context can show one (empty when the title has none), so cards of a row match.
    val wishSlot = (context == CardContext.WISH || hero || show.next) && !small
    val wish = if (wishSlot) texts.wish.orEmpty() else null
    val width = (tonight?.width ?: stripWidth(size)) * skin.widthScale
    val air = if (context == CardContext.ROW || context == CardContext.WISH) skin.air else 0.dp
    val thread = TanabataMaterials.thread(tone)
    val bamboo = TanabataMaterials.bamboo(tone)
    val shape: Shape = when (skin.edge) {
        SkinEdge.DECKLE -> remember(index) { DeckleShape(index * 31 + 7) }
        SkinEdge.RUFFLE -> remember(index) { RuffleShape(index * 17 + 3) }
        SkinEdge.STRAIGHT -> when (skin) {
            TanzakuSkin.HANAFUDA -> RoundedCornerShape(width * 0.04f)
            TanzakuSkin.BOKASHI -> RoundedCornerShape(3.dp)
            else -> RectangleShape
        }
    }
    val sized = when (context) {
        CardContext.ROW, CardContext.WISH -> Modifier.padding(horizontal = air).width(width.dense())
        else -> Modifier.fillMaxWidth()
    }
    val dealt = skin == TanzakuSkin.HANAFUDA && onRod
    // Only dealt cards lift, so the rest skip the animation (dealt only flips when the skin or the hanging setting changes). The lift
    // travels, so below the full motion level it snaps (it ran even with animations off).
    val lift = if (dealt) {
        animateFloatAsState(if (state.hovered || state.focused) 1f else 0f, if (spatial) tween(260) else snap(), label = "deal-lift")
    } else {
        null
    }
    val ghosts = tonight?.pile?.take(2)?.map { s ->
        when (skin) {
            TanzakuSkin.KRAFT -> SkinPaint.kraft
            TanzakuSkin.HANAFUDA -> SkinPaint.hanafudaStock
            else -> SkinPaint.paper(skin, TanabataMaterials.paper(s, tone))
        }
    }.orEmpty()
    val cordTag = tonight?.cordTag?.let { rememberCordTag(it) }
    Column(
        modifier
            .then(sized)
            .tzClickable(interaction, onClick, onLongClick)
            .then(
                if (dealt) {
                    Modifier.graphicsLayer {
                        val up = lift?.value ?: 0f
                        val rest = HANAFUDA_DEAL[index % HANAFUDA_DEAL.size]
                        rotationZ = rest * (1f - up)
                        translationY = -10.dp.toPx() * up
                    }
                } else {
                    // Every card records into a layer of its own (dealt ones already have one), so scrolling a row moves the
                    // recordings instead of redrawing each paper. No clip: cords, pins and shadows still draw past the bounds.
                    Modifier.graphicsLayer().then(if (hangs) Modifier.tzRestTilt(data.key) else Modifier)
                },
            )
            .drawBehind {
                if (hero) drawBambooRod(bamboo, top = 0f, thickness = 7.dp.toPx(), start = this.size.width * 0.15f, end = this.size.width * 0.85f)
                // The origami cord goes behind the fold.
                if (hangs && skin == TanzakuSkin.FOLDED) drawSkinCord(skin, thread, this.size.width / 2f, 2.dp.toPx(), (cordH + 10.dp).toPx())
            }
            .drawWithContent {
                drawContent()
                val cx = this.size.width / 2f
                if (hangs && skin != TanzakuSkin.FOLDED) {
                    val end = when (skin) {
                        TanzakuSkin.BOKASHI -> cordH + 14.dp
                        TanzakuSkin.LANTERN -> cordH + 4.dp
                        TanzakuSkin.PASTEL -> cordH + 13.dp
                        else -> cordH + TZ_HOLE_Y + 1.dp
                    }
                    drawSkinCord(skin, thread, cx, 2.dp.toPx(), end.toPx())
                }
                if (pinned) drawClothespin(Offset(cx, topGap.toPx()))
                if (cordTag != null) {
                    val tagY = when {
                        hangs -> cordH * 0.55f
                        pinned -> 14.dp
                        else -> topGap / 2
                    }
                    drawCordTag(cordTag, cx, tagY.toPx(), inkColor)
                }
            }
            .padding(top = topGap + if (dealt) 6.dp else 0.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (ghosts.isNotEmpty()) Modifier.drawBehind { drawPileGhosts(ghosts) } else Modifier)
                .tzFocusRing(state, corner = if (skin == TanzakuSkin.HANAFUDA) width * 0.04f else 2.dp)
                .skinSurface(skin, paper, status, deep, shape, tone, state.hovered, data.listStatus?.ordinal ?: 6),
        ) {
            SkinContent(skin, data, texts, show, context, size, hero, small, width, paper, status, deep, inkColor, progress, wish, tonight)
            SkinOverlays(skin, data, width, paper, status, deep, tone, show, stamp = tonight == null)
        }
        if (skin == TanzakuSkin.FESTIVAL) FestivalFringe(status, deep, width, index)
        if (skin == TanzakuSkin.CHIYOGAMI) Tassel(deep)
    }
}

/** Resting angles of dealt Hanafuda cards by position. */
private val HANAFUDA_DEAL = floatArrayOf(-3f, 2f, -1.5f, 3.2f, -2.4f, 1.6f)

// ---------------------------------------------------------------- surface

@Composable
private fun Modifier.skinSurface(
    skin: TanzakuSkin,
    paper: Color,
    status: Color,
    deep: Color,
    shape: Shape,
    tone: ThemeTone,
    hovered: Boolean,
    pattern: Int,
): Modifier {
    val perf = LocalMotion.current.level == MotionLevel.PERF
    val shadowColor = TanabataMaterials.paperShadow(tone)
    val lifted = when {
        perf -> this.border(1.dp, TanabataMaterials.ink.copy(alpha = 0.25f), shape)
        skin == TanzakuSkin.LANTERN && tone != ThemeTone.LIGHT -> {
            val glow = status.copy(alpha = 0.75f)
            this.shadow(22.dp, shape, clip = false, ambientColor = glow, spotColor = glow)
        }
        else -> this.shadow(if (hovered) 10.dp else 7.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
    }
    // Only Festival has foil; the other skins skip the animation.
    val foil = if (skin == TanzakuSkin.FESTIVAL) animateFloatAsState(if (hovered) 1f else 0f, tween(1100), label = "foil") else null
    val rimmed = if (skin == TanzakuSkin.HANAFUDA) {
        // The thick card: its dark edge shows under the rim.
        lifted.drawBehind {
            drawRoundRect(SkinPaint.hanafudaEdge, Offset(0f, 2.5.dp.toPx()), size, CornerRadius(size.width * 0.04f))
        }
    } else {
        lifted
    }
    return rimmed
        .clip(shape)
        .drawWithCache {
            // Built once per size and colors: the gradients and the chiyogami print were rebuilt on every draw.
            val glow = when (skin) {
                TanzakuSkin.BOKASHI -> Brush.radialGradient(
                    0f to SkinPaint.light(status, 0.7f),
                    0.58f to paper,
                    1f to lerp(deep, paper, 0.38f),
                    center = Offset(size.width / 2f, size.height * 0.42f),
                    radius = size.height * 0.62f,
                )
                TanzakuSkin.LANTERN -> Brush.radialGradient(
                    0f to SkinPaint.light(status, 0.75f),
                    0.55f to lerp(paper, Color(0xFFFFF6D8), 0.2f),
                    1f to lerp(paper, deep, 0.15f),
                    center = Offset(size.width / 2f, size.height * 0.38f),
                    radius = size.height * 0.6f,
                )
                else -> null
            }
            val width = size.width
            val height = size.height
            fun festival(shift: Float): Brush = Brush.linearGradient(
                0f to deep,
                0.22f to status,
                0.34f to SkinPaint.light(status, 0.65f),
                0.46f to status,
                0.62f to lerp(deep, status, 0.35f),
                0.8f to status,
                0.92f to SkinPaint.light(status, 0.6f),
                start = Offset(-shift, -shift),
                end = Offset(width * 1.6f - shift, height - shift),
            )
            // The foil at rest; while it moves the gradient follows it frame by frame.
            val festivalRest = if (skin == TanzakuSkin.FESTIVAL) festival(0f) else null
            val chiyoGround = lerp(status, Color(0xFFFFF6E6), 0.45f)
            val chiyoInk = lerp(status, deep, 0.35f)
            val print = if (skin == TanzakuSkin.CHIYOGAMI) chiyogamiPath(pattern, size) else null
            onDrawBehind {
                when (skin) {
                    TanzakuSkin.BOKASHI, TanzakuSkin.LANTERN -> if (glow != null) drawRect(glow)
                    TanzakuSkin.FESTIVAL -> {
                        val f = foil?.value ?: 0f
                        drawRect(if (f == 0f && festivalRest != null) festivalRest else festival(size.width * 0.9f * f))
                    }
                    TanzakuSkin.CHIYOGAMI -> {
                        drawRect(chiyoGround)
                        drawChiyogami(chiyoInk, pattern, print)
                    }
                    else -> drawRect(paper)
                }
            }
        }
        .skinTexture(skin, paper, inverted = skin == TanzakuSkin.LANTERN)
        .drawBehind {
            if (skin == TanzakuSkin.FOLDED) drawCreases()
            if (skin == TanzakuSkin.HANAFUDA) {
                val rim = size.width * 0.035f
                drawRoundRect(SkinPaint.hanafudaRim, cornerRadius = CornerRadius(size.width * 0.04f), style = Stroke(rim * 2f))
            }
        }
        .drawWithContent {
            drawContent()
            if (skin == TanzakuSkin.BOKASHI) {
                val inset = 6.dp.toPx()
                drawRoundRect(
                    deep.copy(alpha = 0.7f),
                    Offset(inset, inset),
                    Size(size.width - 2 * inset, size.height - 2 * inset),
                    CornerRadius(2.dp.toPx()),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            if (skin == TanzakuSkin.LANTERN) {
                val bar = 7.dp.toPx()
                drawRect(SkinPaint.wood, Offset.Zero, Size(size.width, bar))
                drawRect(SkinPaint.wood, Offset(0f, size.height - bar), Size(size.width, bar))
            }
        }
}

/** Two folds across an origami strip: a lit crest and its shadow, under the text. */
private fun DrawScope.drawCreases() {
    for (at in floatArrayOf(0.5f, 0.75f)) {
        val y = size.height * at
        drawRect(Color.White.copy(alpha = 0.32f), Offset(0f, y - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
        drawRect(Color.Black.copy(alpha = 0.1f), Offset(0f, y), Size(size.width, 1.5.dp.toPx()))
    }
}

/**
 * The chiyogami prints, one per status (by its ordinal), so color is not the only cue: arrow feathers (yagasuri), interlocking circles (shippō), scales (uroko), checks (ichimatsu), dots (mame
 * shibori) and standing waves (tatewaku); kinari papers get small dots.
 */
private fun DrawScope.drawChiyogami(ink: Color, pattern: Int, print: Path?) {
    val u = 1.dp.toPx()
    when (pattern) {
        0, 2 -> if (print != null) drawPath(print, ink)
        5 -> if (print != null) drawPath(print, ink, style = Stroke(2.4f * u))
        1 -> {
            val st = Stroke(2.2f * u)
            tiles(size, 28f * u, 28f * u) { x, y ->
                val r = 14f * u
                drawCircle(ink, r, Offset(x, y), style = st)
                drawCircle(ink, r, Offset(x + 14f * u, y + 14f * u), style = st)
            }
        }
        3 -> tiles(size, 24f * u, 24f * u) { x, y ->
            drawRect(ink, Offset(x, y), Size(12f * u, 12f * u))
            drawRect(ink, Offset(x + 12f * u, y + 12f * u), Size(12f * u, 12f * u))
        }
        else -> tiles(size, 18f * u, 18f * u) { x, y ->
            drawCircle(ink, 2.6f * u, Offset(x + 4.5f * u, y + 4.5f * u))
            drawCircle(ink, 2.6f * u, Offset(x + 13.5f * u, y + 13.5f * u))
        }
    }
}

/**
 * The prints drawn as paths (arrow feathers, scales, standing waves) laid out over [area] as one path, built once per size by the
 * surface's draw cache; null for the prints made of plain circles and squares.
 */
private fun Density.chiyogamiPath(pattern: Int, area: Size): Path? {
    val u = 1.dp.toPx()
    return when (pattern) {
        0 -> Path().apply {
            tiles(area, 20f * u, 24f * u) { x, y ->
                val w = 10f * u
                moveTo(x, y); lineTo(x + w, y + 6f * u); lineTo(x + w, y + 18f * u); lineTo(x, y + 12f * u); close()
                moveTo(x + w, y + 6f * u); lineTo(x + 2 * w, y); lineTo(x + 2 * w, y + 12f * u); lineTo(x + w, y + 18f * u); close()
            }
        }
        2 -> Path().apply {
            tiles(area, 22f * u, 20f * u) { x, y -> moveTo(x, y + 20f * u); lineTo(x + 11f * u, y); lineTo(x + 22f * u, y + 20f * u); close() }
        }
        5 -> Path().apply {
            tiles(area, 20f * u, 40f * u) { x, y ->
                for (dx in WAVE_COLUMNS) {
                    moveTo(x + dx * u, y)
                    cubicTo(x + (dx + 10f) * u, y + 10f * u, x + (dx + 10f) * u, y + 30f * u, x + dx * u, y + 40f * u)
                }
            }
        }
        else -> null
    }
}

/** Where the two standing waves (tatewaku) of a chiyogami cell start, in dp. */
private val WAVE_COLUMNS = floatArrayOf(5f, 15f)

private inline fun tiles(area: Size, w: Float, h: Float, cell: (Float, Float) -> Unit) {
    var y = -h
    while (y < area.height + h) {
        var x = -w
        while (x < area.width + w) {
            cell(x, y)
            x += w
        }
        y += h
    }
}

// ---------------------------------------------------------------- content

@Composable
private fun SkinContent(
    skin: TanzakuSkin,
    data: TitleCardData,
    texts: TzTexts,
    show: CardShow,
    context: CardContext,
    size: CardSize,
    hero: Boolean,
    small: Boolean,
    width: Dp,
    paper: Color,
    status: Color,
    deep: Color,
    ink: Color,
    progress: Float,
    wish: String?,
    tonight: TonightCardInfo?,
) {
    val side = width * when (skin) {
        TanzakuSkin.PASTEL, TanzakuSkin.BOKASHI -> 0.11f
        TanzakuSkin.HANAFUDA -> 0.035f
        else -> 0.08f
    }
    val top = when (skin) {
        TanzakuSkin.PASTEL -> 30.dp
        TanzakuSkin.BOKASHI -> 44.dp
        TanzakuSkin.FOLDED -> width * 0.3f
        TanzakuSkin.LANTERN -> 7.dp + width * 0.09f
        TanzakuSkin.KRAFT -> 20.dp
        TanzakuSkin.HANAFUDA -> width * 0.035f
        TanzakuSkin.CHIYOGAMI -> 24.dp
        else -> 24.dp
    }
    val bottom = when (skin) {
        TanzakuSkin.KOZO -> width * 0.13f
        TanzakuSkin.CREPE -> width * 0.15f
        TanzakuSkin.LANTERN -> 7.dp + width * 0.1f
        TanzakuSkin.HANAFUDA -> width * 0.035f
        else -> width * 0.1f
    }
    val centered = skin == TanzakuSkin.BOKASHI
    val texture: @Composable ColumnScope.() -> Unit = {
        if (tonight != null) {
            TonightTexts(tonight, texts.title, skinTitleStyle(skin, size, hero), ink, paper, width, centered)
        } else {
            SkinTexts(skin, data, texts, show, context, size, hero, small, ink, progress, wish, centered)
        }
    }
    val poster: @Composable () -> Unit = {
        if (tonight != null) {
            val ratio = if (skin == TanzakuSkin.HANAFUDA) 3f / 4f else TZ_POSTER_RATIO
            TonightPoster(tonight, width, ratio, ink, paper) { Poster(skin, data, show, width, status, deep) }
        } else {
            Poster(skin, data, show, width, status, deep)
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(start = side, end = side, top = top, bottom = bottom),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (skin == TanzakuSkin.PASTEL) 14.dp else 11.dp),
    ) {
        when (skin) {
            TanzakuSkin.CHIYOGAMI -> Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RectangleShape)
                    .background(SkinPaint.label)
                    .padding(width * 0.05f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                poster()
                texture()
            }
            TanzakuSkin.HANAFUDA -> {
                poster()
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = width * 0.03f, vertical = width * 0.04f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) { texture() }
            }
            else -> {
                poster()
                texture()
            }
        }
    }
}

@Composable
private fun ColumnScope.SkinTexts(
    skin: TanzakuSkin,
    data: TitleCardData,
    texts: TzTexts,
    show: CardShow,
    context: CardContext,
    size: CardSize,
    hero: Boolean,
    small: Boolean,
    ink: Color,
    progress: Float,
    wish: String?,
    centered: Boolean,
) {
    val titleStyle = skinTitleStyle(skin, size, hero)
    val align = if (centered) TextAlign.Center else TextAlign.Start
    // v1.3.5: every line is reserved (title 2, status 1, wish 1 or 2, progress one fixed band) so a 1-line title or a missing wish no
    // longer makes a card shorter than its neighbours.
    Text(texts.title, style = titleStyle, color = ink, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = align)
    val dots = show.progress && skin.usesDots() && data.episodesTotal != null && data.episodesTotal in 2..60
    if (show.progress && !dots && skin != TanzakuSkin.KRAFT) {
        Box(Modifier.fillMaxWidth().height(4.dp), contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
            if (progress > 0f) InkStroke(progress, ink)
        }
    }
    Text(
        texts.statusLine,
        style = TzType.sub(if (small) 12.sp else 12.5.sp, FontWeight.SemiBold),
        color = if (skin == TanzakuSkin.PASTEL) lerp(ink, Color.Transparent, 0.12f) else ink,
        minLines = 1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
    if (wish != null) {
        val wishStyle = TextStyle(fontFamily = skinWishFamily(), fontWeight = FontWeight.SemiBold, fontSize = if (hero) 15.sp else 13.5.sp, lineHeight = if (hero) 20.sp else 18.sp)
        val lines = if (hero || context == CardContext.WISH) 2 else 1
        Text(wish, style = wishStyle, color = ink, minLines = lines, maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = align)
    }
    if (show.progress && skin.usesDots()) {
        Box(Modifier.fillMaxWidth().height(9.dp)) {
            if (dots) EpisodeDots(data.episodesSeen ?: 0, data.episodesTotal ?: 0, ink, fichas = skin == TanzakuSkin.HANAFUDA)
        }
    }
}

/** The title style of the skin at this card size (the tonight card writes its title the same way). */
@Composable
private fun skinTitleStyle(skin: TanzakuSkin, size: CardSize, hero: Boolean): TextStyle {
    val family = skinTitleFamily(skin)
    val tSize = titleSize(size, hero)
    val base = TzType.title(tSize)
    return when {
        skin == TanzakuSkin.BOKASHI && family != null ->
            base.copy(fontFamily = family, fontWeight = FontWeight.Normal, letterSpacing = (-0.6).sp, lineHeight = tSize * 1.22f)
        skin == TanzakuSkin.CREPE || skin == TanzakuSkin.FESTIVAL ->
            base.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = tSize * 1.08f, lineHeight = tSize * 1.25f)
        family != null -> base.copy(fontFamily = family, fontWeight = FontWeight.Bold, lineHeight = tSize * 1.25f)
        skin == TanzakuSkin.PASTEL -> base.copy(fontWeight = FontWeight.Medium, lineHeight = tSize * 1.3f)
        else -> base.copy(fontWeight = FontWeight.Bold)
    }
}

private fun TanzakuSkin.usesDots(): Boolean =
    this == TanzakuSkin.CREPE || this == TanzakuSkin.PASTEL || this == TanzakuSkin.FOLDED || this == TanzakuSkin.FESTIVAL ||
        this == TanzakuSkin.CHIYOGAMI || this == TanzakuSkin.HANAFUDA

/** Progress as a tapered stroke of ink (a brush line, not a bar). */
@Composable
internal fun InkStroke(progress: Float, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth(progress.coerceIn(0.08f, 1f)).height(4.dp).clearAndSetSemantics { }) {
        drawRoundRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.1f to ink.copy(alpha = 0.62f), 0.78f to ink.copy(alpha = 0.62f), 1f to Color.Transparent),
            cornerRadius = CornerRadius(size.height),
        )
    }
}

/**
 * One dot per episode, seen ones filled; Hanafuda draws them as red playing chips. As many as fit the line (at most 24): longer series
 * are scaled, so 26 episodes on a narrow card no longer run off the paper (v1.3.5). One canvas instead of a composable per dot.
 */
@Composable
private fun EpisodeDots(seen: Int, total: Int, ink: Color, fichas: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(if (fichas) 7.dp else 6.dp).clearAndSetSemantics { }) {
        val d = size.height
        val gap = 3.dp.toPx()
        val fit = ((size.width + gap) / (d + gap)).toInt().coerceAtLeast(1)
        val n = minOf(total, 24, fit)
        val on = ((seen.toFloat() / total) * n).roundToInt().coerceIn(0, n)
        val r = d / 2f
        repeat(n) { i ->
            val c = Offset(r + i * (d + gap), r)
            when {
                fichas && i < on -> drawCircle(Color(0xFFC8321F), r, c)
                fichas -> {
                    drawCircle(Color(0xFFD9CFBA), r, c)
                    drawCircle(SkinPaint.hanafudaRim, r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                }
                i < on -> drawCircle(ink.copy(alpha = 0.85f), r, c)
                else -> drawCircle(ink.copy(alpha = 0.5f), r - 0.65.dp.toPx(), c, style = Stroke(1.3.dp.toPx()))
            }
        }
    }
}

@Composable
private fun Poster(skin: TanzakuSkin, data: TitleCardData, show: CardShow, width: Dp, status: Color, deep: Color) {
    val score: @Composable BoxScope.() -> Unit = {
        if (show.score && data.score != null) TzScore(data.score, Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
    when (skin) {
        TanzakuSkin.CREPE -> Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { rotationZ = -1.6f }
                .shadow(3.dp, RectangleShape)
                .background(Color(0xFFFBF8F2))
                .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 15.dp),
        ) {
            TzPoster(data.coverUrl, RectangleShape)
            score()
        }
        TanzakuSkin.BOKASHI -> Box(
            Modifier
                .fillMaxWidth()
                .border(1.5.dp, lerp(deep, TanabataMaterials.ink, 0.3f), RoundedCornerShape(4.dp))
                .padding(1.5.dp)
                .border(2.dp, SkinPaint.light(status, 0.7f), RoundedCornerShape(3.dp))
                .padding(2.dp),
        ) {
            TzPoster(data.coverUrl, RoundedCornerShape(2.dp))
            score()
        }
        TanzakuSkin.HANAFUDA -> Box(Modifier.fillMaxWidth().clip(RectangleShape)) {
            MediaCover(url = data.coverUrl, modifier = Modifier.fillMaxWidth(), contentDescription = null, shape = RectangleShape, aspectRatio = 3f / 4f)
            // The print's screen: a fine dot pattern over the flat inks, one tiled rect instead of a circle per dot (thousands a card).
            Spacer(
                Modifier.matchParentSize().drawWithCache {
                    val step = 3.dp.toPx()
                    val radius = 0.55.dp.toPx()
                    val dot = Color.Black.copy(alpha = 0.1f)
                    val tile = halftoneTile(step, radius, dot)
                    val screen = tile?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
                    // Just the cells whose dot the loop drew (center inside the canvas), so the edges match it too.
                    fun cover(extent: Float): Float = ceil((extent - step / 2f) / step).coerceAtLeast(0f) * step
                    val area = Size(cover(size.width), cover(size.height))
                    onDrawBehind {
                        if (screen != null) drawRect(screen, size = area) else drawHalftone(step, radius, dot)
                    }
                },
            )
            HanafudaRibbon(data.listStatus, width, Modifier.align(Alignment.CenterStart))
            score()
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(SkinPaint.hanafudaRim))
        }
        TanzakuSkin.LANTERN -> Box(Modifier.fillMaxWidth().graphicsLayer { alpha = 0.92f }) {
            TzPoster(data.coverUrl, RectangleShape)
            score()
        }
        TanzakuSkin.FESTIVAL -> Box(Modifier.fillMaxWidth().border(3.dp, SkinPaint.light(status, 0.7f)).padding(3.dp)) {
            TzPoster(data.coverUrl, RectangleShape)
            score()
        }
        TanzakuSkin.PASTEL -> Box(Modifier.fillMaxWidth()) {
            TzPoster(data.coverUrl, RoundedCornerShape(2.dp))
            score()
        }
        else -> Box(Modifier.fillMaxWidth()) {
            TzPoster(data.coverUrl, tzPaperShape)
            score()
        }
    }
}

/**
 * One tile of the hanafuda screen: as many [step] cells as it takes to end on a whole pixel (at most 16), each dot drawn at its exact
 * sub-pixel place, so repeating the tile lays the dots where [drawHalftone] puts them. Null when no small tile lines up (an odd
 * density): the dots are then drawn one by one.
 */
private fun CacheDrawScope.halftoneTile(step: Float, radius: Float, dot: Color): ImageBitmap? {
    val cells = (1..16).firstOrNull { n -> abs(n * step - (n * step).roundToInt()) < 0.01f } ?: return null
    val px = (cells * step).roundToInt()
    if (px <= 0) return null
    val tile = ImageBitmap(px, px)
    CanvasDrawScope().draw(this, layoutDirection, BitmapCanvas(tile), Size(px.toFloat(), px.toFloat())) {
        for (j in 0 until cells) {
            for (i in 0 until cells) drawCircle(dot, radius, Offset(step / 2f + i * step, step / 2f + j * step))
        }
    }
    return tile
}

/** The hanafuda screen dot by dot (the fallback of [halftoneTile]). */
private fun DrawScope.drawHalftone(step: Float, radius: Float, dot: Color) {
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            drawCircle(dot, radius, Offset(x, y))
            x += step
        }
        y += step
    }
}

/** The tanzaku of a hanafuda card: a ribbon across the print with its word in brush kanji (decorative). */
@Composable
private fun HanafudaRibbon(status: StatusSlot?, width: Dp, modifier: Modifier) {
    val family = hanafudaKanjiFamily()
    Box(
        modifier
            .padding(start = width * 0.09f, top = width * 0.2f)
            .graphicsLayer { rotationZ = 16f; transformOrigin = TransformOrigin(0.5f, 0.5f) }
            .width(width * 0.2f)
            .height(width * 0.72f)
            .shadow(3.dp, RectangleShape)
            .background(status.ribbonColor())
            .clearAndSetSemantics { },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.padding(top = width * 0.06f), horizontalAlignment = Alignment.CenterHorizontally) {
            status.ribbonKanji().forEach { ch ->
                Text(ch.toString(), style = TextStyle(fontFamily = family, fontSize = (width.value * 0.085f).sp, lineHeight = (width.value * 0.1f).sp), color = Color(0xFFFFF6E6))
            }
        }
    }
}

// ---------------------------------------------------------------- overlays

@Composable
private fun BoxScope.SkinOverlays(
    skin: TanzakuSkin,
    data: TitleCardData,
    width: Dp,
    paper: Color,
    status: Color,
    deep: Color,
    tone: ThemeTone,
    show: CardShow,
    /** The Kraft stamp; off on the tonight card, whose round action sits on that corner of the poster. */
    stamp: Boolean = true,
) {
    val ground = MaterialTheme.colorScheme.background
    when (skin) {
        TanzakuSkin.PASTEL -> Canvas(Modifier.matchParentSize()) { drawEyelet(Offset(size.width / 2f, 13.dp.toPx()), ground) }
        TanzakuSkin.KOZO, TanzakuSkin.CREPE, TanzakuSkin.CHIYOGAMI, TanzakuSkin.FESTIVAL ->
            Canvas(Modifier.matchParentSize()) { drawHole(Offset(size.width / 2f, TZ_HOLE_Y.toPx()), ground) }
        TanzakuSkin.BOKASHI -> Mon(data.listStatus, deep, status, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        TanzakuSkin.FOLDED -> Canvas(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(width * 0.36f)) {
            val w = size.width
            val h = size.height
            val flap = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.62f); lineTo(w / 2f, h); lineTo(0f, h * 0.62f); close() }
            val shadow = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.62f + 3.dp.toPx()); lineTo(w / 2f, h + 3.dp.toPx()); lineTo(0f, h * 0.62f + 3.dp.toPx()); close() }
            drawPath(shadow, Color.Black.copy(alpha = 0.22f))
            drawPath(flap, Brush.verticalGradient(listOf(Color(0xFFFBF6EA), Color(0xFFEFE5D1))))
            drawLine(Color.Black.copy(alpha = 0.08f), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1.dp.toPx())
        }
        TanzakuSkin.KRAFT -> {
            // Washi tape in the status color holding the poster, and the stamp with the status and the episodes.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -(width * 0.02f), y = width * 0.12f)
                    .rotate(-24f)
                    .width(width * 0.42f)
                    .height(width * 0.1f)
                    .background(status.copy(alpha = 0.8f)),
            )
            if (stamp) Hanko(data, deep, width, Modifier.align(Alignment.TopEnd).padding(top = width * 1.12f, end = width * 0.06f))
        }
        else -> Unit
    }
}

/** The mon of a Bokashi strip: a dark disc with the status icon. */
@Composable
private fun Mon(status: StatusSlot?, deep: Color, paper: Color, modifier: Modifier) {
    val fill = lerp(deep, TanabataMaterials.ink, 0.45f)
    val stroke = lerp(paper, Color.White, 0.6f)
    Canvas(modifier.size(26.dp).clearAndSetSemantics { }) {
        drawCircle(fill)
        val u = size.width / 24f
        val st = Stroke(2.2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val p = Path()
        when (status) {
            StatusSlot.WATCHING, null -> { p.moveTo(9f * u, 7f * u); p.lineTo(9f * u, 17f * u); p.lineTo(17f * u, 12f * u); p.close() }
            StatusSlot.PLANNING -> { p.moveTo(8f * u, 6f * u); p.lineTo(16f * u, 6f * u); p.lineTo(16f * u, 18f * u); p.lineTo(12f * u, 15f * u); p.lineTo(8f * u, 18f * u); p.close() }
            StatusSlot.COMPLETED -> { p.moveTo(7f * u, 12.5f * u); p.lineTo(10.5f * u, 16f * u); p.lineTo(17f * u, 8.5f * u) }
            StatusSlot.PAUSED -> { p.moveTo(10f * u, 7f * u); p.lineTo(10f * u, 17f * u); p.moveTo(14f * u, 7f * u); p.lineTo(14f * u, 17f * u) }
            StatusSlot.DROPPED -> { p.moveTo(8f * u, 8f * u); p.lineTo(16f * u, 16f * u); p.moveTo(16f * u, 8f * u); p.lineTo(8f * u, 16f * u) }
            StatusSlot.REWATCHING -> { p.moveTo(7f * u, 12f * u); p.cubicTo(7f * u, 6f * u, 16f * u, 5f * u, 17f * u, 11f * u); p.moveTo(17f * u, 12f * u); p.cubicTo(17f * u, 18f * u, 8f * u, 19f * u, 7f * u, 13f * u) }
        }
        drawPath(p, stroke, style = st)
    }
}

/** The kraft stamp: status and episodes, opaque so it reads over the poster's corner. */
@Composable
private fun Hanko(data: TitleCardData, deep: Color, width: Dp, modifier: Modifier) {
    val name = data.listStatusLabel ?: return
    val eps = if (data.episodesTotal != null && data.episodesTotal > 1) "${data.episodesSeen ?: 0}/${data.episodesTotal}" else null
    Box(
        modifier
            .rotate(-12f)
            .size(width * 0.36f)
            .shadow(1.dp, CircleShape)
            .background(Color(0xFFD9B88D), CircleShape)
            .border(2.5.dp, deep, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                name.substringBefore(' ').uppercase(),
                style = TzType.sub(9.sp, FontWeight.ExtraBold).copy(letterSpacing = 0.4.sp),
                color = deep,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (eps != null) Text(eps, style = TzType.sub(12.sp, FontWeight.ExtraBold), color = deep, maxLines = 1)
        }
    }
}

/** A wooden clothespin over the line and the card's top edge (Kraft). */
private fun DrawScope.drawClothespin(at: Offset) {
    val u = 1.dp.toPx()
    val w = 14f * u
    val h = 38f * u
    val left = at.x - w / 2f
    val top = at.y - 18f * u
    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(left + 1f * u, top + 2f * u), Size(w, h), CornerRadius(3f * u))
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFFA9794B), Color(0xFFD9B183), Color(0xFFB88553)), startX = left, endX = left + w),
        Offset(left, top),
        Size(w, h),
        CornerRadius(3f * u),
    )
    drawRoundRect(Color(0xFF8A8F98), Offset(left + 2f * u, top + 13f * u), Size(w - 4f * u, 4f * u), CornerRadius(2f * u))
}

/** Lengths of the Festival fringe strips, as fractions of the card's width. */
private val FRINGE_LENGTHS = floatArrayOf(0.46f, 0.36f, 0.52f, 0.4f, 0.3f, 0.48f, 0.38f, 0.55f, 0.34f, 0.44f, 0.5f, 0.37f, 0.42f)

/** The fukinagashi fringe under a Festival strip: strips that sway on their own under the full motion level. */
@Composable
private fun FestivalFringe(status: Color, deep: Color, width: Dp, index: Int) {
    val full = LocalMotion.current.level == MotionLevel.FULL
    // The swing stays a State read in each strip's layer: reading it here recomposed the whole card every frame.
    val swing = if (full) {
        val t = rememberInfiniteTransition(label = "fringe")
        t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "fringe-swing")
    } else {
        null
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = width * 0.06f).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(13) { k ->
            val len = width * FRINGE_LENGTHS[(k + index * 3) % FRINGE_LENGTHS.size]
            val sign = if ((k + index) % 2 == 0) 1f else -1f
            Box(
                Modifier
                    .graphicsLayer { transformOrigin = TransformOrigin(0.5f, 0f); rotationZ = sign * (swing?.value ?: 0f) * (3f + (k % 3)) }
                    .width(width * 0.022f)
                    .height(len)
                    .background(Brush.verticalGradient(listOf(status, deep))),
            )
        }
    }
}

/** The tassel under a Chiyogami strip: a gold ring and the deep-dyed threads. */
@Composable
private fun ColumnScope.Tassel(deep: Color) {
    Canvas(Modifier.align(Alignment.CenterHorizontally).width(14.dp).height(48.dp).clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFFE9C45A), Offset(w * 0.25f, 0f), Size(w * 0.5f, 6.dp.toPx()))
        val strands = Path().apply { moveTo(w * 0.3f, 6.dp.toPx()); lineTo(w * 0.7f, 6.dp.toPx()); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(strands, deep)
        var x = 1.5.dp.toPx()
        while (x < w) {
            drawLine(lerp(deep, Color.White, 0.4f), Offset(w / 2f + (x - w / 2f) * 0.4f, 6.dp.toPx()), Offset(x, h), strokeWidth = 0.8.dp.toPx())
            x += 3.dp.toPx()
        }
    }
}
