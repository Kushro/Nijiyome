// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.tanabata

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.tanabata.LocalTanzakuSkin
import app.nijiyome.designsystem.direction.tanabata.SkinPaint
import app.nijiyome.designsystem.direction.tanabata.TanabataMaterials
import app.nijiyome.designsystem.direction.tanabata.drawHole
import app.nijiyome.designsystem.direction.tanabata.drawSkinCord
import app.nijiyome.designsystem.direction.tanabata.skinTexture
import app.nijiyome.designsystem.direction.tanabata.skinWishFamily
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.model.CardSize
import app.nijiyome.model.Density
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.LocalCardIndex
import app.nijiyome.ui.cards.LocalCardPlayAction
import app.nijiyome.ui.cards.LocalCardSourceKey
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.look.CardSizing
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.effectiveCardId

// v1.3.5 "Esta noche" (docs/redesign/movimiento, the owner kept all five layouts): what the big tanzaku of Novedades carries inside its
// paper in that row (a tag and a round action on the poster, a kind block under the title, a wide button on the featured card), the
// "+N, ver todo" paper and the agenda sheet that hangs beside the featured card. SkinnedStripCard draws the card; every text comes
// from the screen, already localised.

/** What an "Esta noche" card is: to resume, out already, airing later tonight, or the "+N" paper that opens the full list. */
enum class TonightCardKind { RESUME, AIRED, UPCOMING, MORE }

/** The round action on the poster (or the featured card's wide button): play, or the reminder bell of a title still to air. */
@Immutable
class TonightCardAction(
    /** Read by screen readers ("Ver Ao no Hako, episodio 9", "Avisarme cuando salga ..."). */
    val label: String,
    val onClick: () -> Unit,
    /** A reminder bell (a toggle) instead of play. */
    val bell: Boolean = false,
    /** The bell's reminder is on. */
    val on: Boolean = false,
)

/** What a tanzaku of "Esta noche" adds inside its paper. Every line is reserved, so cards of one row keep one height. */
@Immutable
class TonightCardInfo(
    val kind: TonightCardKind,
    /** Line 1 of the kind block, bold, with the kind's icon: "Continuar", "Salió 21:30", "Sale 23:30". */
    val headline: String,
    /** Line 2: "Ep. 6 · quedan 11 min", "Ep. 9 · te faltan 2". */
    val detail: String,
    /** Tag on the poster's top start: "Nuevo", or the airing time (drawn with a bell) for [TonightCardKind.UPCOMING]. */
    val tag: String? = null,
    val action: TonightCardAction? = null,
    /** The featured card's wide button inside the paper ("Ver Ep. 9"); it runs [action] and replaces the round one. */
    val cta: String? = null,
    /** A second tag on the poster's top end ("Para ahora"). */
    val nowTag: String? = null,
    /** A tag on the cord above the paper (the night timeline's airing time, the pile's count); the cord grows to hold it. */
    val cordTag: String? = null,
    /** Playback progress of the episode to resume, drawn as the skin's ink stroke; the band is reserved on every card. */
    val progress: Float? = null,
    /** Later tonight: duller paper and poster. */
    val dim: Boolean = false,
    /** List statuses of up to two papers stacked behind this one (the pile of the night layout). */
    val pile: List<StatusSlot?> = emptyList(),
    /** [TonightCardKind.MORE]: the big count ("+5") and the line under it ("más esta noche"), in place of the poster. */
    val more: String? = null,
    val moreLine: String? = null,
    /** Width of the strip before the skin's own scale; null = the width of the card size. */
    val width: Dp? = null,
)

/** Whether "Esta noche" draws its items as the skinned tanzaku: only when the user's card style is the tanzaku of Novedades. */
@Composable
fun tanzakuTonightCards(): Boolean = LocalLook.current.effectiveCardId() == TitleCardIds.TANZAKU

/**
 * The big skinned tanzaku of Novedades with the "Esta noche" content inside the paper. It hangs from the row's rod under Tanabata
 * like any row card; [size] sets the title size (and the width unless [TonightCardInfo.width] gives one).
 */
@Composable
fun TanzakuTonightCard(
    data: TitleCardData,
    info: TonightCardInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: CardSize = LocalLook.current.cardSize,
    index: Int = -1,
) {
    // The play lives on the round action, not on the card's own play disc; the cover still opens Details with the shared transition.
    CompositionLocalProvider(LocalCardIndex provides index, LocalCardSourceKey provides data.key, LocalCardPlayAction provides null) {
        SkinnedStripCard(data, CardContext.ROW, size, TONIGHT_SHOW, onClick, modifier, onLongClick, info)
    }
}

/**
 * The [TonightCardInfo.width] at which a whole tonight card (the skin's scale, its air on both sides and the compact density
 * included) is [outer] wide: how a grid fits a set number of cards on a phone.
 */
@Composable
fun tanzakuTonightWidth(outer: Dp): Dp {
    val skin = LocalTanzakuSkin.current
    val dense = if (LocalLook.current.density == Density.COMPACT) CardSizing.COMPACT_FACTOR else 1f
    return (outer - skin.air * 2) / (skin.widthScale * dense)
}

/** The tonight texts replace the status, wish and dots lines, so the card shows none of them. */
private val TONIGHT_SHOW = CardShow(progress = false, status = false, next = false)

/** Cream of the tags and of a bell at rest (artifact `--cream`). */
internal val TZ_CREAM = Color(0xFFFBF6EA)

/** Extra cord a [TonightCardInfo.cordTag] needs above the paper. */
internal val TONIGHT_TAG_DROP = 22.dp

private val FIT_MIN = 9.sp

private fun TonightCardKind.icon(): ImageVector = when (this) {
    TonightCardKind.RESUME -> Icons.Outlined.History
    TonightCardKind.AIRED -> Icons.Outlined.Schedule
    TonightCardKind.UPCOMING -> Icons.Outlined.NotificationsNone
    TonightCardKind.MORE -> Icons.AutoMirrored.Filled.ArrowForward
}

private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * One line that never cuts: it shrinks (down to 9 sp) until it fits the paper. The line height stays that of [max], so a shrunk line
 * keeps the card as tall as its neighbours.
 */
@Composable
private fun FitLine(text: String, style: TextStyle, color: Color, max: TextUnit, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = align,
        maxLines = 1,
        minLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = FIT_MIN, maxFontSize = max),
    )
}

// ---------------------------------------------------------------- poster

/** The poster of a tonight card with its tags and round action; [TonightCardKind.MORE] draws the dashed "+N" box instead. */
@Composable
internal fun TonightPoster(info: TonightCardInfo, width: Dp, ratio: Float, ink: Color, paper: Color, poster: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        when {
            info.kind == TonightCardKind.MORE -> MoreBox(info, ratio, ink)
            info.dim -> Box(Modifier.graphicsLayer { alpha = 0.78f }) { poster() }
            else -> poster()
        }
        info.tag?.let { TonightTag(it, bell = info.kind == TonightCardKind.UPCOMING, width, Modifier.align(Alignment.TopStart).padding(7.dp)) }
        info.nowTag?.let { TonightTag(it, bell = false, width, Modifier.align(Alignment.TopEnd).padding(7.dp), inverse = true) }
        val action = info.action
        if (action != null && info.cta == null && info.kind != TonightCardKind.MORE) {
            TanzakuTonightAction(action, Modifier.align(Alignment.BottomEnd), disc = (width * 0.15f).coerceIn(30.dp, 40.dp), ink = ink, paper = paper)
        }
    }
}

/** A small cream tag over the poster (artifact `.tz-tag`); [inverse] = ink with cream text ("Para ahora"). */
@Composable
private fun TonightTag(text: String, bell: Boolean, width: Dp, modifier: Modifier, inverse: Boolean = false) {
    val perf = LocalMotion.current.level == MotionLevel.PERF
    val bg = if (inverse) TanabataMaterials.ink else TZ_CREAM
    val fg = if (inverse) TZ_CREAM else TanabataMaterials.ink
    val fs = (width.value * 0.056f).coerceIn(10.5f, 13f).sp
    val shape = RoundedCornerShape(2.dp)
    Row(
        modifier
            .then(if (perf) Modifier else Modifier.shadow(2.dp, shape))
            .background(bg, shape)
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (bell) Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = fg, modifier = Modifier.size((fs.value * 1.1f).dp))
        Text(text, style = TzType.sub(fs, FontWeight.Bold).tabular().copy(lineHeight = fs), color = fg, maxLines = 1, softWrap = false)
    }
}

/** The "+N" paper's box in place of the poster (artifact `.more-box`): a dashed frame, the count in brush ink, a quiet line. */
@Composable
private fun MoreBox(info: TonightCardInfo, ratio: Float, ink: Color) {
    val dash = ink.copy(alpha = 0.35f)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .drawBehind {
                val u = 1.dp.toPx()
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(2f * u),
                    style = Stroke(1.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * u, 4f * u))),
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(info.more.orEmpty(), style = TextStyle(fontFamily = skinWishFamily(), fontSize = 40.sp, lineHeight = 44.sp).tabular(), color = ink, maxLines = 1)
        info.moreLine?.let { Text(it, style = TzType.sub(13.sp), color = ink.copy(alpha = 0.8f), maxLines = 1, textAlign = TextAlign.Center) }
    }
}

/**
 * The round play (or bell) of a tonight card: a [disc] drawn in ink (or cream for a bell at rest) inside a real 48 dp target, read
 * as [TonightCardAction.label]. A bell is a toggle.
 */
@Composable
fun TanzakuTonightAction(
    action: TonightCardAction,
    modifier: Modifier = Modifier,
    disc: Dp = 34.dp,
    ink: Color = TanabataMaterials.ink,
    paper: Color = TZ_CREAM,
) {
    val perf = LocalMotion.current.level == MotionLevel.PERF
    val filled = !action.bell || action.on
    val target = Modifier.size(TONIGHT_TARGET).clip(CircleShape)
    val hit = if (action.bell) {
        target.toggleable(value = action.on, role = Role.Checkbox, onValueChange = { action.onClick() })
    } else {
        target.clickable(role = Role.Button, onClick = action.onClick)
    }
    Box(modifier.then(hit).semantics { contentDescription = action.label }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(disc)
                .then(if (perf) Modifier else Modifier.shadow(3.dp, CircleShape))
                .background(if (filled) ink else TZ_CREAM, CircleShape)
                .then(if (filled) Modifier.border(2.dp, TZ_CREAM.copy(alpha = 0.92f), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when {
                !action.bell -> Icons.Filled.PlayArrow
                action.on -> Icons.Outlined.NotificationsActive
                else -> Icons.Outlined.NotificationAdd
            }
            Icon(icon, contentDescription = null, tint = if (filled) paper else ink, modifier = Modifier.size(disc * 0.5f))
        }
    }
}

private val TONIGHT_TARGET = 48.dp

// ---------------------------------------------------------------- texts

/**
 * The texts under the poster of a tonight card: the reserved progress band, the title (2 lines), the kind block (line 1 bold with the
 * kind's icon, line 2 the detail) and, on the featured card, the wide button.
 */
@Composable
internal fun TonightTexts(info: TonightCardInfo, title: String, titleStyle: TextStyle, ink: Color, paper: Color, width: Dp, centered: Boolean) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    val line1 = (width.value * 0.064f).coerceIn(11.5f, 15f).sp
    val line2 = (width.value * 0.058f).coerceIn(11f, 14f).sp
    // Line 2 a step quieter than line 1, still well above 4.5:1 on every paper.
    val soft = lerp(ink, paper, 0.16f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        // Reserved on every card (filled only to resume), so a row of mixed kinds keeps one height.
        Box(Modifier.fillMaxWidth().height(4.dp)) { info.progress?.takeIf { it > 0f }?.let { InkStroke(it, ink) } }
        Text(title, style = titleStyle, color = ink, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = align)
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(ink.copy(alpha = 0.16f), size = Size(size.width, 1.dp.toPx())) }
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(info.kind.icon(), contentDescription = null, tint = ink, modifier = Modifier.size((line1.value * 1.15f).dp))
                FitLine(info.headline, TzType.sub(line1, FontWeight.Bold).tabular(), ink, line1, Modifier.weight(1f, fill = false), align)
            }
            FitLine(info.detail, TzType.sub(line2, FontWeight.Medium).tabular(), soft, line2, align = align)
        }
        val action = info.action
        if (info.cta != null && action != null) TonightCta(info.cta, action, ink, paper)
    }
}

/** The featured card's wide button inside the paper (artifact `.cta`): ink pill, play glyph, "Ver Ep. 9"; 48 dp tall. */
@Composable
private fun TonightCta(text: String, action: TonightCardAction, ink: Color, paper: Color) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TONIGHT_TARGET)
            .clip(shape)
            .background(ink, shape)
            .clickable(role = Role.Button, onClick = action.onClick)
            .semantics { contentDescription = action.label }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = paper, modifier = Modifier.size(18.dp))
        FitLine(text, TzType.sub(15.sp, FontWeight.Bold).tabular(), paper, 15.sp, Modifier.weight(1f, fill = false))
    }
}

// ---------------------------------------------------------------- pile and cord tag

/** Up to two papers stacked behind a card (artifact `.pile .ghost`: -5° and +4°, a few dp off), drawn behind its node. */
internal fun DrawScope.drawPileGhosts(papers: List<Color>) {
    val u = 1.dp.toPx()
    papers.take(2).forEachIndexed { i, paper ->
        val deg = if (i == 0) -5f else 4f
        val dx = if (i == 0) -8f else 9f
        val dy = if (i == 0) 6f else 4f
        rotate(deg, pivot = center) {
            translate(dx * u, dy * u) {
                drawRect(Color.Black.copy(alpha = 0.22f), Offset(0f, 2f * u), size)
                drawRect(paper)
            }
        }
    }
}

/** The measured text of a cord tag, kept while the text stays the same. */
@Composable
internal fun rememberCordTag(text: String): TextLayoutResult {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val style = TzType.sub(12.5.sp, FontWeight.Bold).tabular()
    return remember(text, style, measurer) { measurer.measure(text, style, maxLines = 1, softWrap = false) }
}

/** A cream time tag on the cord (artifact `.ttag`), centered on ([cx], [cy]). */
internal fun DrawScope.drawCordTag(layout: TextLayoutResult, cx: Float, cy: Float, ink: Color) {
    val padH = 8.dp.toPx()
    val padV = 4.dp.toPx()
    val w = layout.size.width + 2 * padH
    val h = layout.size.height + 2 * padV
    val topLeft = Offset(cx - w / 2f, cy - h / 2f)
    val r = CornerRadius(3.dp.toPx())
    drawRoundRect(Color.Black.copy(alpha = 0.25f), topLeft + Offset(0f, 1.dp.toPx()), Size(w, h), r)
    drawRoundRect(TZ_CREAM, topLeft, Size(w, h), r)
    drawText(layout, color = ink, topLeft = topLeft + Offset(padH, padV))
}

// ---------------------------------------------------------------- the agenda sheet

/** Colors of the agenda sheet's content: its paper (or surface), text, quieter text and rules. */
@Immutable
class TonightSheetColors(val paper: Color, val ink: Color, val muted: Color, val rule: Color)

/** Cords of the sheet, as fractions of its width (artifact `.hang--sheet`: 9 % from each side). */
private val SHEET_HOLES = floatArrayOf(0.09f, 0.91f)

/** Cord above the sheet's paper when it hangs from the rod. */
private val SHEET_CORD = 30.dp

/**
 * The agenda sheet of the featured layout (artifact `.sheet`): a wide paper of the user's skin, quiet blue, hung by two cords from the
 * row's rod under Tanabata (elsewhere it lies flat and shows its two holes). The texture stays at its calm level over the whole sheet,
 * since it is all text. [content] gets the colors to write with.
 */
@Composable
fun TanzakuTonightSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.(TonightSheetColors) -> Unit) {
    val skin = LocalTanzakuSkin.current
    val tone = LocalThemeTone.current
    val hangs = tzHangs()
    val perf = LocalMotion.current.level == MotionLevel.PERF
    val paper = lerp(SkinPaint.paper(skin, TanabataMaterials.paper(StatusSlot.PLANNING, tone)), TZ_CREAM, 0.55f)
    val ink = TanabataMaterials.ink
    val colors = remember(paper) { TonightSheetColors(paper, ink, lerp(ink, paper, 0.25f), ink.copy(alpha = 0.2f)) }
    val thread = TanabataMaterials.thread(tone)
    val ground = MaterialTheme.colorScheme.background
    val shadow = TanabataMaterials.paperShadow(tone)
    val shape = RoundedCornerShape(2.dp)
    val cord = if (hangs) SHEET_CORD else 0.dp
    Column(
        modifier
            .drawWithContent {
                drawContent()
                if (hangs) {
                    for (f in SHEET_HOLES) drawSkinCord(skin, thread, size.width * f, 2.dp.toPx(), (cord + 13.dp).toPx())
                }
            }
            .padding(top = cord)
            .then(
                if (perf) {
                    Modifier.border(1.dp, ink.copy(alpha = 0.25f), shape)
                } else {
                    Modifier.shadow(7.dp, shape, clip = false, ambientColor = shadow, spotColor = shadow)
                },
            )
            .clip(shape)
            .background(paper)
            .skinTexture(skin, paper, calmFrom = 0f)
            .drawWithContent {
                drawContent()
                for (f in SHEET_HOLES) drawHole(Offset(size.width * f, 12.dp.toPx()), ground)
            }
            .padding(top = 18.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) { content(colors) }
    }
}
