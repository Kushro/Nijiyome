// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.tanabata

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.tanabata.TanabataMaterials
import app.nijiyome.designsystem.direction.tanabata.drawBambooRod
import app.nijiyome.designsystem.direction.tanabata.drawInkDown
import app.nijiyome.designsystem.direction.tanabata.drawThread
import app.nijiyome.designsystem.direction.tanabata.tanzakuPaper
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.model.CardSize
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData

// Sparkles redesign, lane D1. The four tanzaku styles (artifact `registerCard` in dir-tanabata.js). Each one draws ROW, WISH, HERO and
// GRID its own way; COMPACT and CONTINUE are shared (TanzakuParts.kt). Under Tanabata the cards in rows hang from the rod the decor draws
// at the top of the row; in a grid they lie flat and show only their hole.

/** Row / wish width owned by the style; hero, grid and compact fill what the screen gives. */
@Composable
private fun Modifier.tzSized(context: CardContext, width: Dp): Modifier = when (context) {
    CardContext.ROW, CardContext.WISH -> this.width(width.dense())
    else -> this.fillMaxWidth()
}

/** The hero's own piece of rod, 70 % of its width, just above the thread (artifact `.tz-stub`). */
@Composable
private fun Modifier.tzHeroStub(hero: Boolean): Modifier {
    if (!hero) return this
    val bamboo = TanabataMaterials.bamboo(LocalThemeTone.current)
    return this.drawBehind {
        drawBambooRod(bamboo, top = 0f, thickness = 7.dp.toPx(), start = size.width * 0.15f, end = size.width * 0.85f)
    }
}

private fun wishShown(context: CardContext, show: CardShow, texts: TzTexts): Boolean =
    context == CardContext.WISH || context == CardContext.HERO || (show.next && texts.wish != null)

// ---------------------------------------------------------------- tanzaku

/** Tanzaku: a tall washi strip hanging by a knotted thread; the paper is the status, ink runs down the text with progress. */
@Composable
internal fun StripCardBody(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
) {
    when (context) {
        CardContext.COMPACT -> return TzCompactRow(data, show, onClick, onLongClick, modifier)
        CardContext.CONTINUE -> return TzContinue(data, size, show, TzContinueVariant.PAPER, onClick, onLongClick, modifier)
        else -> Unit
    }
    val interaction = remember { MutableInteractionSource() }
    val state = rememberTzState(interaction)
    val texts = tzTexts(data, show)
    val hero = context == CardContext.HERO
    val small = size == CardSize.SMALL && !hero
    val hangs = hero || (context != CardContext.GRID && tzHangs())
    val threadH = if (hangs) TZ_THREAD else 0.dp
    val base = data.paper()
    val paper = if (state.pressed) TanabataMaterials.pressed(base) else base
    val ink = data.ink(show)
    val wish = if (wishShown(context, show, texts) && !small) texts.wish else null
    val pad = if (small) 5.dp else 6.dp
    Column(
        modifier
            .tzSized(context, stripWidth(size))
            .tzClickable(interaction, onClick, onLongClick)
            .tzHeroStub(hero)
            .then(if (hangs) Modifier.tzRestTilt(data.key).tzThread(threadH) else Modifier)
            .padding(top = threadH),
    ) {
        Column(Modifier.tzFocusRing(state).tanzakuPaper(paper).tzHole()) {
            Box(Modifier.padding(start = pad, end = pad, top = if (small) 14.dp else 16.dp)) {
                TzPoster(data.coverUrl, tzPaperShape)
                if (show.score && data.score != null) TzScore(data.score, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (small) 56.dp else 64.dp)
                    .then(if (ink > 0f) Modifier.drawWithContent { drawInkDown(base, ink); drawContent() } else Modifier)
                    .padding(start = pad + 3.dp, end = pad + 3.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    texts.title,
                    style = if (hero) TzType.display() else TzType.title(),
                    color = TanabataMaterials.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (texts.statusLine.isNotEmpty()) {
                    Text(texts.statusLine, style = TzType.sub(), color = TanabataMaterials.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (wish != null) {
                    val big = hero || context == CardContext.WISH
                    // Two lines: "Episodio 8 · hace 1 día" lost its unit of time at one line on a 150 dp strip.
                    Text(wish, style = TzType.sub(if (big) 14.sp else 12.5.sp), color = TanabataMaterials.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- tanzaku-vertical

private fun verticalWidth(size: CardSize): Dp = when (size) {
    CardSize.SMALL -> 60.dp
    CardSize.MEDIUM -> 74.dp
    CardSize.LARGE -> 90.dp
    CardSize.XL -> 104.dp
}

private fun verticalLength(size: CardSize, hero: Boolean): Dp = when {
    hero -> 220.dp
    size == CardSize.SMALL -> 118.dp
    size == CardSize.MEDIUM -> 150.dp
    size == CardSize.LARGE -> 182.dp
    else -> 210.dp
}

/** Tanzaku vertical: a narrow strip with the title written top to bottom, for wish rows (twice as many fit per row). */
@Composable
internal fun VerticalCardBody(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
) {
    when (context) {
        CardContext.COMPACT -> return TzCompactRow(data, show, onClick, onLongClick, modifier)
        CardContext.CONTINUE -> return TzContinue(data, size, show, TzContinueVariant.VERTICAL, onClick, onLongClick, modifier)
        else -> Unit
    }
    val interaction = remember { MutableInteractionSource() }
    val state = rememberTzState(interaction)
    val texts = tzTexts(data, show)
    val hero = context == CardContext.HERO
    val hangs = hero || (context != CardContext.GRID && tzHangs())
    val threadH = if (hangs) TZ_THREAD else 0.dp
    val base = data.paper()
    val paper = if (state.pressed) TanabataMaterials.pressed(base) else base
    // Wish rows always show how far along the wish is.
    val ink = data.ink(if (context == CardContext.WISH) show.copy(progress = true) else show)
    val second = if (wishShown(context, show, texts)) texts.wish ?: texts.statusLine else texts.statusLine
    val isWish = second != texts.statusLine
    val description = listOfNotNull(texts.title, texts.statusLine.ifEmpty { null }, if (isWish) second else null).joinToString(". ")
    val titleSize = when {
        hero || size == CardSize.LARGE || size == CardSize.XL -> 16.sp
        size == CardSize.SMALL -> 12.5.sp
        else -> 14.sp
    }
    Column(
        modifier
            .tzSized(context, verticalWidth(size))
            .tzClickable(interaction, onClick, onLongClick, description)
            .tzHeroStub(hero)
            .then(if (hangs) Modifier.tzRestTilt(data.key).tzThread(threadH) else Modifier)
            .padding(top = threadH),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .tzFocusRing(state)
                .tanzakuPaper(paper, ink = ink)
                .tzHole()
                .padding(start = 5.dp, end = 5.dp, top = 15.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TzPoster(data.coverUrl, tzPaperShape, if (context == CardContext.GRID) Modifier.fillMaxWidth(0.72f) else Modifier)
            val length = verticalLength(size, hero)
            // vertical-rl: the first column (the title) is the rightmost one.
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (second.isNotEmpty()) {
                    VerticalText(
                        second,
                        TzType.sub(weight = if (isWish) FontWeight.Medium else FontWeight.SemiBold),
                        TanabataMaterials.ink,
                        length,
                    )
                }
                VerticalText(
                    texts.title,
                    TzType.title(titleSize).copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
                    TanabataMaterials.ink,
                    length,
                )
            }
            if (isWish && texts.statusName != null) {
                Text(
                    texts.statusName,
                    style = TzType.sub(10.sp, FontWeight.ExtraBold),
                    color = TanabataMaterials.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- tanzaku-sello

/** Tanzaku sello: a clean poster; a short strip with the status and the episode hangs from its top edge. */
@Composable
internal fun SealCardBody(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
) {
    when (context) {
        CardContext.COMPACT -> return TzCompactRow(data, show, onClick, onLongClick, modifier)
        CardContext.CONTINUE -> return TzContinue(data, size, show, TzContinueVariant.PANEL, onClick, onLongClick, modifier)
        else -> Unit
    }
    val interaction = remember { MutableInteractionSource() }
    val state = rememberTzState(interaction)
    val texts = tzTexts(data, show)
    val hero = context == CardContext.HERO
    val small = size == CardSize.SMALL && !hero
    val base = data.paper()
    val ink = data.ink(show)
    val wishy = context == CardContext.WISH || hero
    val sub = when {
        show.next && texts.wish != null -> texts.wish
        wishy -> texts.wish ?: texts.facts
        else -> texts.facts
    }
    // The strip prints the next episode when the screen wants it, else the episodes seen (or the count).
    val stripEp = if (texts.wish != null && (show.next || context == CardContext.WISH)) texts.wish.substringBefore(" · ") else texts.eps
    val movie = data.episodesTotal == 1 && stripEp == texts.eps
    val posterShape = MaterialTheme.shapes.medium
    val perf = LocalMotion.current.level == MotionLevel.PERF
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .tzSized(context, stripWidth(size))
            // The strip's status is written sideways (hidden from accessibility), so the card says it.
            .tzClickable(interaction, onClick, onLongClick, description = texts.statusName),
    ) {
        val w = if (constraints.hasBoundedWidth) maxWidth else stripWidth(size)
        val stripW = (w * 0.3f).coerceIn(34.dp, 54.dp)
        val stripEnd = w * 0.12f
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                Modifier
                    .padding(bottom = 5.dp)
                    .tzFocusRing(state, corner = 10.dp)
                    .then(if (!perf) Modifier.shadow(if (state.hovered) 4.dp else 1.dp, posterShape) else Modifier),
            ) {
                TzPoster(data.coverUrl, posterShape)
                if (show.score && data.score != null) TzScore(data.score, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
            Text(
                texts.title,
                style = if (hero) TzType.display() else TzType.title(),
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotEmpty()) {
                Text(sub, style = TzType.sub(), color = if (wishy) cs.onSurface else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The strip: thread from the top of the card, paper from 8 dp, over the poster's upper edge.
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = stripEnd)
                .width(stripW)
                .tzThread(paperTop = 8.dp)
                .padding(top = 8.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (hero) 118.dp else if (small) 70.dp else 84.dp)
                    .tanzakuPaper(if (state.pressed) TanabataMaterials.pressed(base) else base, ink = ink, fibreTile = 120.dp)
                    .tzHole()
                    .padding(start = 2.dp, end = 2.dp, top = 15.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                texts.statusName?.let {
                    VerticalText(it, TzType.sub(if (small) 10.sp else 11.sp, FontWeight.ExtraBold), TanabataMaterials.ink, 96.dp, fixed = false)
                }
                when {
                    movie -> Icon(Icons.Outlined.Movie, contentDescription = null, tint = TanabataMaterials.ink, modifier = Modifier.size(15.dp))
                    stripEp != null -> Text(
                        stripEp,
                        style = TzType.sub(if (small) 9.5.sp else 10.5.sp, FontWeight.ExtraBold),
                        color = TanabataMaterials.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- tanzaku-hilo

/** The paper bow (musubi) the poster hangs from: two wings in the status paper, shaded quarters, a knot. */
private fun DrawScope.drawMusubi(paper: Color, center: Offset, shadow: Boolean) {
    val w = 30.dp.toPx()
    val h = 14.dp.toPx()
    val left = center.x - w / 2f
    val top = center.y - h / 2f
    val bow = Path().apply {
        moveTo(left, top)
        lineTo(center.x, top + h * 0.4f)
        lineTo(left + w, top)
        lineTo(left + w, top + h)
        lineTo(center.x, top + h * 0.6f)
        lineTo(left, top + h)
        close()
    }
    if (shadow) translate(top = 1.dp.toPx()) { drawPath(bow, Color.Black.copy(alpha = 0.35f)) }
    drawPath(bow, paper)
    val shade = lerp(paper, TanabataMaterials.ink, 0.15f)
    clipPath(bow) {
        drawRect(shade, Offset(left, center.y), Size(w / 2f, h / 2f))
        drawRect(shade, Offset(center.x, top), Size(w / 2f, h / 2f))
    }
    drawCircle(lerp(paper, TanabataMaterials.ink, 0.3f), 3.dp.toPx(), center)
}

/** Tanzaku hilo: the lightest; the poster hangs from the thread by a paper bow in the status color, progress is a line of ink. */
@Composable
internal fun ThreadCardBody(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
) {
    when (context) {
        CardContext.COMPACT -> return TzCompactRow(data, show, onClick, onLongClick, modifier)
        CardContext.CONTINUE -> return TzContinue(data, size, show, TzContinueVariant.PANEL, onClick, onLongClick, modifier)
        else -> Unit
    }
    val interaction = remember { MutableInteractionSource() }
    val state = rememberTzState(interaction)
    val texts = tzTexts(data, show)
    val hero = context == CardContext.HERO
    val hangs = hero || (context != CardContext.GRID && tzHangs())
    val hangPad = if (hangs) TZ_THREAD + 8.dp else 12.dp
    val base = data.paper()
    val paper = if (state.pressed) TanabataMaterials.pressed(base) else base
    val ink = data.ink(show)
    val tone = LocalThemeTone.current
    val thread = TanabataMaterials.thread(tone)
    val perf = LocalMotion.current.level == MotionLevel.PERF
    val cs = MaterialTheme.colorScheme
    val posterShape = MaterialTheme.shapes.small
    val wishy = wishShown(context, show, texts)
    val line1 = when {
        show.next && texts.wish != null -> texts.wish
        wishy -> texts.wish ?: texts.statusLine
        else -> texts.statusLine
    }
    val line2 = if (wishy && line1 != texts.statusLine) texts.statusLine else null
    Column(
        modifier
            .tzSized(context, stripWidth(size))
            .tzClickable(interaction, onClick, onLongClick)
            .tzHeroStub(hero)
            .drawWithContent {
                drawContent()
                // Thread from the rod into the bow, and the bow straddling the poster's top edge.
                val cx = this.size.width / 2f
                val bowY = hangPad.toPx() - 3.dp.toPx()
                if (hangs) drawThread(thread, cx, top = 2.dp.toPx(), bottom = bowY, knot = false)
                drawMusubi(paper, Offset(cx, bowY), shadow = !perf)
            },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier
                .padding(top = hangPad)
                .tzFocusRing(state, corner = 6.dp)
                .then(if (!perf) Modifier.shadow(if (state.hovered) 8.dp else 4.dp, posterShape) else Modifier),
        ) {
            TzPoster(data.coverUrl, posterShape)
            if (show.score && data.score != null) TzScore(data.score, Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        if (ink > 0f) {
            val fill = cs.primary
            val track = cs.outline.copy(alpha = 0.35f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, top = 1.dp)
                    .height(3.dp)
                    .drawBehind {
                        val r = CornerRadius(2.dp.toPx())
                        drawRoundRect(track, cornerRadius = r)
                        drawRoundRect(fill, size = Size(this.size.width * ink, this.size.height), cornerRadius = r)
                    },
            )
        }
        Text(
            texts.title,
            style = if (hero) TzType.display() else TzType.title(),
            color = cs.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
        if (line1.isNotEmpty()) {
            Text(line1, style = TzType.sub(), color = if (wishy) cs.onSurface else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (line2 != null && line2.isNotEmpty()) {
            Text(line2, style = TzType.sub(), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
