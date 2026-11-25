// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.StatusMark
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
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_transit_card_line
import app.nijiyome.ui.resources.rd_transit_count
import app.nijiyome.ui.resources.rd_transit_not_aired
import app.nijiyome.ui.resources.rd_transit_play
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane D3. Card "Línea" (dir-transit.js renderLinea): one row per series with its line and stations; the densest of
// the three, for lists, search and the library.

object LineCard : TitleCardStyle {
    override val id: String = TitleCardIds.TRANSIT_LINE
    override val directionId: String = "transit"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_transit_card_line)

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
            context == CardContext.COMPACT || context == CardContext.CONTINUE || context.horizontalAt(size) ->
                Strip(data, context, size, show, onClick, modifier, onLongClick)
            context == CardContext.HERO -> Hero(data, show, onClick, modifier, onLongClick)
            else -> Tile(data, context, size, show, onClick, modifier, onLongClick)
        }
    }

    /** ROW, WISH and GRID: thumbnail beside the title, the line, and a foot with the code, the count and the status. */
    @Composable
    private fun Tile(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val model = data.lineModel()
        val wish = context == CardContext.WISH
        val small = size == CardSize.SMALL
        val floor = if (wish) panelSurface() else cardSurface()
        val raised = raisedSurface()
        val edge = MaterialTheme.colorScheme.outlineVariant
        val status = data.shownStatus(show)
        val sized = if (context == CardContext.GRID) modifier.fillMaxWidth() else modifier.width(ownedWidth(context, size, 136.dp, 160.dp, 360.dp))
        val restLine = cardLine(data, floor)
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = sized,
            shape = MaterialTheme.shapes.medium,
            spoken = model.spoken(),
            background = { if (it.lit && !wish) raised else floor },
            border = { if (it.lit) restLine.ink else edge },
            dashedBorder = wish,
        ) { state ->
            val here = if (state.lit && !wish) raised else floor
            val line = cardLine(data, here)
            Column(
                Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Thumb(data, if (small) 30.dp else 40.dp)
                    CardText(data.title, subLine(data, model, context, show), Modifier.weight(1f))
                }
                CardRoute(data, model, line, show, state.lit, height = 12.dp, halo = here)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StationCode(model.current, model.prefix(), line.ink, size = 24.dp, pending = model.pending, halo = here)
                    Text(
                        footText(model),
                        style = TextStyle(
                            fontFamily = transitDisplayFamily(),
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (status != null && !small) StatusMark(status.first, status.second)
                }
            }
        }
    }

    /** "5 de 12", "Sin emitir" (artifact `.tr-foot-txt`). */
    @Composable
    private fun footText(model: LineModel): String = when {
        !model.reading && model.aired == 0 -> stringResource(Res.string.rd_transit_not_aired)
        model.total != null -> stringResource(Res.string.rd_transit_count, model.done, model.total)
        else -> model.done.toString()
    }

    /**
     * COMPACT, CONTINUE and wide ROW / WISH: code, title, the line and the status or play disc in one row. Below 420 dp the line drops
     * to a second row under the title (phone, side column).
     */
    @Composable
    private fun Strip(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val model = data.lineModel()
        val resume = context == CardContext.CONTINUE
        val floor = cardSurface()
        val raised = raisedSurface()
        val edge = MaterialTheme.colorScheme.outlineVariant
        val status = data.shownStatus(show)
        val restLine = cardLine(data, floor)
        val sized = when (context) {
            CardContext.COMPACT -> modifier.fillMaxWidth()
            else -> modifier
                .then(if (resume) Modifier.transitTrackStop(restLine.ink) else Modifier)
                .width(ownedWidth(context, size, 136.dp, 160.dp, 360.dp))
        }
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = sized,
            shape = MaterialTheme.shapes.medium,
            spoken = model.spoken(),
            onClickLabel = if (resume) stringResource(Res.string.rd_transit_play) else null,
            background = { if (it.lit) raised else floor },
            border = { if (it.lit) restLine.ink else edge },
        ) { state ->
            val here = if (state.lit) raised else floor
            val line = cardLine(data, here)
            val sub = subLine(data, model, context, show)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val narrow = maxWidth < 420.dp
                val trailing: @Composable () -> Unit = {
                    if (resume) {
                        PlayDisc()
                    } else if (status != null) {
                        StatusMark(status.first, status.second)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 14.dp),
                ) {
                    StationCode(model.current, model.prefix(), line.ink, size = 30.dp, pending = model.pending, halo = here)
                    if (narrow) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardText(data.title, sub, titleLines = 1)
                            CardRoute(data, model, line, show, state.lit, height = 14.dp, halo = here)
                        }
                    } else {
                        CardText(data.title, sub, Modifier.weight(1f), titleLines = 1)
                        CardRoute(data, model, line, show, state.lit, Modifier.weight(1.1f).widthIn(min = 120.dp), height = 14.dp, halo = here)
                    }
                    trailing()
                }
            }
        }
    }

    /** Header card: thumbnail, large code, title and status, and the long line with its key station numbers. */
    @Composable
    private fun Hero(
        data: TitleCardData,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val model = data.lineModel()
        val floor = cardSurface()
        val raised = raisedSurface()
        val edge = MaterialTheme.colorScheme.outlineVariant
        val status = data.shownStatus(show)
        val restLine = cardLine(data, floor)
        TransitFrame(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.widthIn(max = 680.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            spoken = model.spoken(),
            background = { if (it.lit) raised else floor },
            border = { if (it.lit) restLine.ink else edge },
        ) { state ->
            val here = if (state.lit) raised else floor
            val line = cardLine(data, here)
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 22.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Thumb(data, 64.dp)
                    StationCode(model.current, model.prefix(), line.ink, size = 48.dp, pending = model.pending, halo = here)
                    CardText(data.title, subLine(data, model, CardContext.HERO, show), Modifier.weight(1f), hero = true)
                    if (status != null) StatusMark(status.first, status.second)
                }
                CardRoute(data, model, line, show, state.lit, height = 22.dp, labels = true, halo = here)
            }
        }
    }

    @Composable
    private fun Thumb(data: TitleCardData, width: Dp) {
        MediaCover(
            url = data.coverUrl,
            modifier = Modifier.width(width).cardToDetailsSource(data.key),
            shape = MaterialTheme.shapes.extraSmall,
            aspectRatio = TRANSIT_POSTER_RATIO,
        )
    }
}
