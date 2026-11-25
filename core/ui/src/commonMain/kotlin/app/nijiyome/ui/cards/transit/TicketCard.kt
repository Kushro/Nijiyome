// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.direction.transit.StationCode
import app.nijiyome.designsystem.direction.transit.transitDisplayFamily
import app.nijiyome.designsystem.direction.transit.transitTrackStop
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle
import app.nijiyome.ui.look.CardSizing
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.MediaStatusKind
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_transit_card_ticket
import app.nijiyome.ui.resources.rd_transit_no_date
import app.nijiyome.ui.resources.rd_transit_play
import app.nijiyome.ui.resources.rd_transit_seg_chapter
import app.nijiyome.ui.resources.rd_transit_seg_episode
import app.nijiyome.ui.resources.rd_transit_seg_next
import app.nijiyome.ui.resources.rd_transit_seg_premiere
import app.nijiyome.ui.resources.rd_transit_seg_read
import app.nijiyome.ui.resources.rd_transit_seg_seen
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane D3. Card "Billete" (dir-transit.js renderBillete): a ticket with half-moon notches and a perforated cut between
// the stub (poster, or the code in dense lists) and the body, which reads in segments: the episode and what is left. Made for
// "continue watching": readable at a glance from the couch.

/**
 * A rounded ticket with two half-moon notches on the cut. [vertical] = the cut runs across (poster above), placed at [cutOf] of the
 * size; otherwise it runs down (stub on the left).
 */
private class TicketShape(
    private val radius: Dp,
    private val notch: Dp,
    private val vertical: Boolean,
    private val cutOf: (Size, Density) -> Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val n = with(density) { notch.toPx() }
        val cut = cutOf(size, density)
        val body = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(r))) }
        val holes = Path().apply {
            if (vertical) {
                addOval(Rect(Offset(0f, cut), n))
                addOval(Rect(Offset(size.width, cut), n))
            } else {
                addOval(Rect(Offset(cut, 0f), n))
                addOval(Rect(Offset(cut, size.height), n))
            }
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, holes))
    }
}

/** One segment of the ticket body: a small caption over a bold value. */
@Immutable
private data class Segment(val caption: String, val value: String)

object TicketCard : TitleCardStyle {
    override val id: String = TitleCardIds.TRANSIT_TICKET
    override val directionId: String = "transit"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_transit_card_ticket)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        when {
            context == CardContext.COMPACT -> Compact(data, show, onClick, modifier, onLongClick)
            context == CardContext.CONTINUE || context == CardContext.HERO || context.horizontalAt(size) ->
                Horizontal(data, context, size, show, onClick, modifier, onLongClick)
            else -> Vertical(data, context, size, show, onClick, modifier, onLongClick)
        }
    }

    /** Ticket floor: the card surface on dark, white with a hairline on light (artifact `.tr-ticket > *`). */
    @Composable
    private fun ticketFloor(lit: Boolean): TicketFloor {
        val light = LocalThemeTone.current == ThemeTone.LIGHT
        val base = if (light) panelSurface() else cardSurface()
        return TicketFloor(if (lit) raisedSurface() else base, if (light) MaterialTheme.colorScheme.outlineVariant else null)
    }

    /** Episode / chapter, then what is left (artifact `segs`). */
    @Composable
    private fun segments(data: TitleCardData, model: LineModel, resume: Boolean): List<Segment> {
        val first = Segment(
            stringResource(if (model.reading) Res.string.rd_transit_seg_chapter else Res.string.rd_transit_seg_episode),
            model.prefix() + model.current.toString().padStart(2, '0'),
        )
        val seenCaption = stringResource(if (model.reading) Res.string.rd_transit_seg_read else Res.string.rd_transit_seg_seen)
        val second = when {
            resume && data.nextLabel != null && !model.pending -> Segment(stringResource(Res.string.rd_transit_seg_next), data.nextLabel)
            model.pending && data.nextLabel != null -> Segment(stringResource(Res.string.rd_transit_seg_next), data.nextLabel)
            model.finished && model.total != null -> Segment(seenCaption, "${model.total}/${model.total}")
            model.aired == 0 || data.airing == MediaStatusKind.UPCOMING ->
                Segment(stringResource(Res.string.rd_transit_seg_premiere), data.nextLabel ?: stringResource(Res.string.rd_transit_no_date))
            model.total != null -> Segment(seenCaption, "${model.done}/${model.total}")
            else -> Segment(seenCaption, model.done.toString())
        }
        return listOf(first, second)
    }

    @Composable
    private fun Segments(items: List<Segment>, hero: Boolean, small: Boolean, play: Boolean, title: String = "") {
        val perf = MaterialTheme.colorScheme.outline
        val value = TextStyle(
            fontFamily = transitDisplayFamily(),
            fontWeight = FontWeight.ExtraBold,
            fontSize = when {
                hero -> MaterialTheme.typography.titleLarge.fontSize
                small -> MaterialTheme.typography.labelLarge.fontSize
                else -> MaterialTheme.typography.titleMedium.fontSize
            },
            fontFeatureSettings = "tnum",
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { i, seg ->
                if (i > 0) Perforation(perf)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        seg.caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        seg.value,
                        style = value,
                        color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (play) {
                Perforation(perf)
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { PlayDisc(title) }
            }
        }
    }

    /** A vertical dotted cut between segments. */
    @Composable
    private fun Perforation(color: Color) {
        Spacer(
            Modifier
                .width(1.5.dp)
                .fillMaxHeight()
                .clearAndSetSemantics { }
                .drawBehind { perforation(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height)) },
        )
    }

    /** ROW, WISH and GRID: poster stub on top, perforated cut with notches, title, segments and route. */
    @Composable
    private fun Vertical(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val model = data.lineModel()
        val small = size == CardSize.SMALL
        val notch = 7.dp
        val shape = TicketShape(10.dp, notch, vertical = true) { s, _ -> s.width / TRANSIT_POSTER_RATIO }
        val status = data.shownStatus(show)
        val perf = MaterialTheme.colorScheme.outline
        val sized = if (context == CardContext.GRID) modifier.fillMaxWidth() else modifier.width(ownedWidth(context, size, 124.dp, 160.dp, 344.dp))
        val rest = ticketFloor(false)
        val lit = ticketFloor(true)
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = sized,
            shape = shape,
            spoken = model.spoken(),
            background = { if (it.lit) lit.fill else rest.fill },
            border = { rest.edge },
        ) { state ->
            val line = cardLine(data, if (state.lit) lit.fill else rest.fill)
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    MediaCover(
                        url = data.coverUrl,
                        modifier = Modifier.fillMaxWidth().cardToDetailsSource(data.key),
                        shape = RectangleShape,
                        aspectRatio = TRANSIT_POSTER_RATIO,
                    )
                    if (status != null) PosterStatusBadge(status.first, status.second, Modifier.padding(6.dp))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val inset = notch.toPx() + 2.dp.toPx()
                            perforation(perf, Offset(inset, 0f), Offset(this.size.width - inset, 0f))
                        }
                        .padding(start = if (small) 8.dp else 10.dp, end = if (small) 8.dp else 10.dp, top = if (small) 10.dp else 12.dp, bottom = if (small) 8.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardTitle(data.title)
                    Segments(segments(data, model, resume = false), hero = false, small = small, play = false)
                    CardRoute(data, model, line, show, state.lit, height = 10.dp, halo = if (state.lit) lit.fill else rest.fill)
                }
            }
        }
    }

    /** CONTINUE, HERO and wide ROW / WISH: stub on the left, vertical cut, body with title, segments and route. */
    @Composable
    private fun Horizontal(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val model = data.lineModel()
        val hero = context == CardContext.HERO
        val resume = context == CardContext.CONTINUE
        val stub = when {
            hero -> if (size == CardSize.SMALL) 110.dp else 150.dp
            resume -> 78.dp
            else -> CardSizing.widePosterDp(size).dp
        }
        val notch = if (hero) 9.dp else 7.dp
        val shape = TicketShape(10.dp, notch, vertical = false) { _, d -> with(d) { stub.toPx() } }
        val perf = MaterialTheme.colorScheme.outline
        val status = data.shownStatus(show)
        val rest = ticketFloor(false)
        val lit = ticketFloor(true)
        val sized = when {
            hero -> modifier.widthIn(max = 680.dp).fillMaxWidth()
            else -> modifier
                .then(if (resume) Modifier.transitTrackStop(cardLine(data).ink) else Modifier)
                .width(ownedWidth(context, size, 124.dp, 160.dp, 344.dp))
        }
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = sized,
            shape = shape,
            spoken = model.spoken(),
            onClickLabel = if (resume) stringResource(Res.string.rd_transit_play) else null,
            background = { if (it.lit) lit.fill else rest.fill },
            border = { rest.edge },
        ) { state ->
            val floorColor = if (state.lit) lit.fill else rest.fill
            val line = cardLine(data, floorColor)
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(stub)) {
                    MediaCover(
                        url = data.coverUrl,
                        modifier = Modifier.fillMaxWidth().cardToDetailsSource(data.key),
                        shape = RectangleShape,
                        aspectRatio = TRANSIT_POSTER_RATIO,
                    )
                    if (status != null && !resume) PosterStatusBadge(status.first, status.second, Modifier.padding(4.dp))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            val inset = notch.toPx() + 2.dp.toPx()
                            perforation(perf, Offset(0f, inset), Offset(0f, this.size.height - inset))
                        }
                        .padding(
                            start = if (hero) 24.dp else 16.dp,
                            end = if (hero) 22.dp else 14.dp,
                            top = if (hero) 18.dp else 10.dp,
                            bottom = if (hero) 18.dp else 12.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (hero) 14.dp else 8.dp, Alignment.CenterVertically),
                ) {
                    if (hero) {
                        CardText(data.title, subLine(data, model, context, show), hero = true)
                    } else {
                        CardTitle(data.title, maxLines = 1)
                    }
                    Segments(segments(data, model, resume), hero = hero, small = size == CardSize.SMALL, play = resume, title = data.title)
                    CardRoute(data, model, line, show, state.lit, height = if (hero) 18.dp else 12.dp, labels = hero, halo = floorColor)
                }
            }
        }
    }

    /** Dense row: the stub holds the station code, the body the title, sub line and status. */
    @Composable
    private fun Compact(
        data: TitleCardData,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val model = data.lineModel()
        val stub = 56.dp
        val shape = TicketShape(10.dp, 7.dp, vertical = false) { _, d -> with(d) { stub.toPx() } }
        val perf = MaterialTheme.colorScheme.outline
        val status = data.shownStatus(show)
        val rest = ticketFloor(false)
        val lit = ticketFloor(true)
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            spoken = model.spoken(),
            background = { if (it.lit) lit.fill else rest.fill },
            border = { rest.edge },
        ) { state ->
            val floorColor = if (state.lit) lit.fill else rest.fill
            val line = cardLine(data, floorColor)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(stub).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    StationCode(model.current, model.prefix(), line.ink, size = 30.dp, pending = model.pending, halo = floorColor)
                }
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            val inset = 9.dp.toPx()
                            perforation(perf, Offset(0f, inset), Offset(0f, size.height - inset))
                        }
                        .padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CardText(data.title, subLine(data, model, CardContext.COMPACT, show), Modifier.weight(1f), titleLines = 1)
                    if (status != null) StatusMark(status.first, status.second)
                }
            }
        }
    }
}

/** A ticket floor and its optional hairline (light mode). */
@Immutable
private data class TicketFloor(val fill: Color, val edge: Color?)
