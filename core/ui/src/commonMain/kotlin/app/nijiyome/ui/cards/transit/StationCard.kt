// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.transit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.transit.StationCode
import app.nijiyome.designsystem.direction.transit.TransitEase
import app.nijiyome.designsystem.direction.transit.transitTrackStop
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle
import app.nijiyome.ui.look.CardSizing
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_transit_card_station
import app.nijiyome.ui.resources.rd_transit_play
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane D3. Card "Estación" (dir-transit.js renderEstacion): the poster with a station plate below it, the circle with
// the episode code in the series color sitting on the seam, and the line of episodes with the train on the next one.

object StationCard : TitleCardStyle {
    override val id: String = TitleCardIds.TRANSIT_STATION
    override val directionId: String = "transit"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_transit_card_station)

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
            context == CardContext.CONTINUE || context.horizontalAt(size) -> Horizontal(data, context, size, show, onClick, modifier, onLongClick)
            context == CardContext.COMPACT -> Compact(data, show, onClick, modifier, onLongClick)
            context == CardContext.HERO -> Hero(data, size, show, onClick, modifier, onLongClick)
            else -> Vertical(data, context, size, show, onClick, modifier, onLongClick)
        }
    }

    /** ROW, WISH and GRID: poster, then the plate with the code on the seam, title, sub line and route. */
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
        val line = cardLine(data)
        val status = data.shownStatus(show)
        val small = size == CardSize.SMALL
        val floor = cardSurface()
        val plate = if (context == CardContext.WISH) panelSurface() else floor
        val sized = if (context == CardContext.GRID) modifier.fillMaxWidth() else modifier.width(ownedWidth(context, size, 116.dp, 150.dp, 330.dp))
        val edge = MaterialTheme.colorScheme.outlineVariant
        val spatial = LocalMotion.current.spatial
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = sized,
            shape = MaterialTheme.shapes.medium,
            spoken = model.spoken(),
            background = { floor },
            border = { if (it.lit) line.ink else edge },
        ) { state ->
            Column(Modifier.fillMaxWidth()) {
                val zoom by animateFloatAsState(
                    if (state.hovered && spatial) 1.03f else 1f,
                    tween(320, easing = TransitEase),
                    label = "stationPosterZoom",
                )
                Box(Modifier.fillMaxWidth().clipToBounds()) {
                    MediaCover(
                        url = data.coverUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .cardToDetailsSource(data.key)
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                            },
                        shape = RectangleShape,
                        aspectRatio = TRANSIT_POSTER_RATIO,
                    )
                    if (status != null) PosterStatusBadge(status.first, status.second, Modifier.padding(6.dp))
                }
                val codeSize = if (small) 28.dp else 34.dp
                Box(Modifier.fillMaxWidth().background(plate)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = if (small) 8.dp else 10.dp, end = if (small) 8.dp else 10.dp, top = codeSize / 2 + 5.dp, bottom = if (small) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (small) 6.dp else 8.dp),
                    ) {
                        CardText(data.title, subLine(data, model, context, show))
                        CardRoute(data, model, line, show, state.lit, height = 12.dp, halo = plate)
                    }
                    StationCode(
                        model.current,
                        model.prefix(),
                        line.ink,
                        Modifier.offset(x = if (small) 6.dp else 8.dp, y = -(codeSize / 2) - 1.dp),
                        size = codeSize,
                        pending = model.pending,
                        halo = plate,
                    )
                }
            }
        }
    }

    /** CONTINUE (with the play disc) and ROW / WISH at the wide sizes: poster beside the code, title and route. */
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
        val line = cardLine(data)
        val status = data.shownStatus(show)
        val resume = context == CardContext.CONTINUE
        val floor = cardSurface()
        val edge = MaterialTheme.colorScheme.outlineVariant
        val poster = when {
            resume && size == CardSize.SMALL -> 60.dp
            resume -> 72.dp
            else -> CardSizing.widePosterDp(size).dp
        }
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .then(if (resume) Modifier.transitTrackStop(line.ink) else Modifier)
                .width(ownedWidth(context, size, 116.dp, 150.dp, 330.dp)),
            shape = MaterialTheme.shapes.medium,
            spoken = model.spoken(),
            onClickLabel = if (resume) stringResource(Res.string.rd_transit_play) else null,
            background = { floor },
            border = { if (it.lit) line.ink else edge },
        ) { state ->
            Row(
                Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(poster)) {
                    MediaCover(
                        url = data.coverUrl,
                        modifier = Modifier.fillMaxWidth().cardToDetailsSource(data.key),
                        shape = MaterialTheme.shapes.small,
                        aspectRatio = TRANSIT_POSTER_RATIO,
                    )
                    if (status != null && !resume) PosterStatusBadge(status.first, status.second, Modifier.padding(4.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StationCode(model.current, model.prefix(), line.ink, size = 34.dp, pending = model.pending, halo = floor)
                        CardText(data.title, subLine(data, model, context, show), Modifier.weight(1f))
                    }
                    CardRoute(data, model, line, show, state.lit, height = 14.dp, halo = floor)
                }
                if (resume) PlayDisc(data.title)
            }
        }
    }

    /** Dense row: small poster, title and sub line, status, and the code at the end. */
    @Composable
    private fun Compact(
        data: TitleCardData,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val model = data.lineModel()
        val line = cardLine(data)
        val status = data.shownStatus(show)
        val floor = cardSurface()
        val edge = MaterialTheme.colorScheme.outlineVariant
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            spoken = model.spoken(),
            background = { floor },
            border = { if (it.lit) line.ink else edge },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MediaCover(
                    url = data.coverUrl,
                    modifier = Modifier.width(44.dp).cardToDetailsSource(data.key),
                    shape = MaterialTheme.shapes.extraSmall,
                    aspectRatio = TRANSIT_POSTER_RATIO,
                )
                CardText(data.title, subLine(data, model, CardContext.COMPACT, show), Modifier.weight(1f), titleLines = 1)
                if (status != null) StatusMark(status.first, status.second)
                StationCode(model.current, model.prefix(), line.ink, size = 30.dp, pending = model.pending, halo = floor)
            }
        }
    }

    /** Header card: large poster, the large code beside the title and the route with its key station numbers. */
    @Composable
    private fun Hero(
        data: TitleCardData,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val model = data.lineModel()
        val line = cardLine(data)
        val status = data.shownStatus(show)
        val floor = cardSurface()
        val edge = MaterialTheme.colorScheme.outlineVariant
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.widthIn(max = 680.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            spoken = model.spoken(),
            background = { floor },
            border = { if (it.lit) line.ink else edge },
        ) { state ->
            Row(
                Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.width(if (size == CardSize.SMALL) 104.dp else 148.dp)) {
                    MediaCover(
                        url = data.coverUrl,
                        modifier = Modifier.fillMaxWidth().cardToDetailsSource(data.key),
                        shape = MaterialTheme.shapes.small,
                        aspectRatio = TRANSIT_POSTER_RATIO,
                    )
                    if (status != null) PosterStatusBadge(status.first, status.second, Modifier.padding(6.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StationCode(model.current, model.prefix(), line.ink, size = 48.dp, pending = model.pending, halo = floor)
                        CardText(data.title, subLine(data, model, CardContext.HERO, show), Modifier.weight(1f), hero = true)
                    }
                    CardRoute(data, model, line, show, state.lit, height = 22.dp, labels = true, halo = floor)
                }
            }
        }
    }
}
