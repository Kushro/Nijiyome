// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.transit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.direction.transit.StopState
import app.nijiyome.designsystem.direction.transit.TransitInk
import app.nijiyome.designsystem.direction.transit.TransitLine
import app.nijiyome.designsystem.direction.transit.TransitPalettes
import app.nijiyome.designsystem.direction.transit.TransitRoute
import app.nijiyome.designsystem.direction.transit.rememberTransitLine
import app.nijiyome.model.CardSize
import app.nijiyome.model.Density
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardPlayTarget
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.look.CardSizing
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.pressScale
import app.nijiyome.ui.media.MediaStatusKind
import app.nijiyome.ui.platform.windowWidthDp
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_transit_completed
import app.nijiyome.ui.resources.rd_transit_prefix_chapter
import app.nijiyome.ui.resources.rd_transit_prefix_episode
import app.nijiyome.ui.resources.rd_transit_sr_read
import app.nijiyome.ui.resources.rd_transit_sr_read_open
import app.nijiyome.ui.resources.rd_transit_sr_seen
import app.nijiyome.ui.resources.rd_transit_sr_seen_aired
import app.nijiyome.ui.resources.rd_transit_sr_seen_open
import app.nijiyome.ui.resources.rd_transit_sr_unaired
import org.jetbrains.compose.resources.stringResource

// Shared pieces of the three Tránsito cards (dir-transit.js `norm`, `subOf`, `open`): the line model of a title, the card frame
// (one clickable, focusable, 48 dp node that merges the title and reads the line as its state), widths, badges and the play disc.

/** Poster ratio of the Transit cards (artifact `aspect-ratio: 2 / 3`). */
internal const val TRANSIT_POSTER_RATIO = 2f / 3f

/**
 * Where a title stands on its line. [total] is null when the provider does not know the episode count (the line then ends open);
 * [aired] is null when unknown (treated as everything aired). [current] is the episode the train stops at (1-based).
 */
@Immutable
internal data class LineModel(
    val reading: Boolean,
    val total: Int?,
    val done: Int,
    val aired: Int?,
    val current: Int,
    val finished: Boolean,
    val pending: Boolean,
) {
    /** Stations to draw: one per episode, or up to the current one with an open end when the total is unknown. */
    val stops: List<StopState>
        get() {
            val n = total ?: maxOf(current, 1)
            val airedN = aired ?: n
            return List(n) { i ->
                when {
                    i < done -> StopState.WATCHED
                    i < airedN -> StopState.AIRED
                    else -> StopState.UNAIRED
                }
            }
        }

    /** Index of the train in [stops]. */
    val trainIndex: Int get() = (current - 1).coerceAtLeast(0)
}

private val FIRST_INT = Regex("\\d+")

/** The line of [this] card (artifact `norm`). Reading = manga or novel keys. */
internal fun TitleCardData.lineModel(): LineModel {
    val reading = key.startsWith("manga") || key.startsWith("novel")
    val total = episodesTotal?.takeIf { it > 0 }
    val done = (episodesSeen ?: 0).coerceAtLeast(0).let { if (total != null) it.coerceAtMost(total) else it }
    val aired: Int? = when {
        reading -> total
        airing == MediaStatusKind.UPCOMING -> 0
        airing == MediaStatusKind.AIRING -> nextLabel?.let { FIRST_INT.find(it)?.value?.toIntOrNull() }?.let { (it - 1).coerceAtLeast(0) }
            ?.let { if (total != null) it.coerceAtMost(total) else it }
        else -> total
    }
    val finished = total != null && done >= total
    val current = if (total != null) (done + 1).coerceAtMost(total) else done + 1
    val pending = !finished && aired != null && current > aired
    return LineModel(reading, total, done, aired, current, finished, pending)
}

/** "E" or "C" before the number. */
@Composable
internal fun LineModel.prefix(): String =
    stringResource(if (reading) Res.string.rd_transit_prefix_chapter else Res.string.rd_transit_prefix_episode)

/** What the line says to a screen reader (artifact `srLine`). */
@Composable
internal fun LineModel.spoken(): String = when {
    reading && total != null -> stringResource(Res.string.rd_transit_sr_read, done, total)
    reading -> stringResource(Res.string.rd_transit_sr_read_open, done)
    total == null -> stringResource(Res.string.rd_transit_sr_seen_open, done)
    aired == 0 -> stringResource(Res.string.rd_transit_sr_unaired, total)
    aired != null && aired < total -> stringResource(Res.string.rd_transit_sr_seen_aired, done, total, aired)
    else -> stringResource(Res.string.rd_transit_sr_seen, done, total)
}

/** The line under the title (artifact `subOf`), from what the screen gave. */
@Composable
internal fun subLine(data: TitleCardData, model: LineModel, context: CardContext, show: CardShow): String? {
    val base = when (context) {
        CardContext.CONTINUE, CardContext.WISH -> data.nextLabel ?: data.subtitle
        else -> when {
            show.next && data.nextLabel != null -> data.nextLabel
            model.finished && !model.reading && model.total != null && data.subtitle == null ->
                stringResource(Res.string.rd_transit_completed, model.total)
            else -> data.subtitle
        }
    }
    val score = if (show.score) data.score else null
    return listOfNotNull(base, score).joinToString(" · ").ifBlank { null }
}

/** Card floor (artifact `--nj-surface-2`): the Material role that carries it in each tone. */
@Composable
internal fun cardSurface(): Color =
    if (LocalThemeTone.current == ThemeTone.LIGHT) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh

/** Panel floor (artifact `--nj-surface`): white in light, the first container in dark. */
@Composable
internal fun panelSurface(): Color =
    if (LocalThemeTone.current == ThemeTone.LIGHT) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer

/** Raised floor on hover (artifact `--nj-surface-3`). */
@Composable
internal fun raisedSurface(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

/** The line colors of the card: the series color when the provider gives one, else the palette accent. */
@Composable
internal fun cardLine(data: TitleCardData, surface: Color = cardSurface()): TransitLine = rememberTransitLine(data.accent, surface)

/** The list status to show, when the screen wants it and gave both the status and its label. */
internal fun TitleCardData.shownStatus(show: CardShow): Pair<StatusSlot, String>? =
    if (show.status && listStatus != null && listStatusLabel != null) listStatus to listStatusLabel else null

/** The focus ring color of the Transit palettes (amber on dark, ink on light); the Grafito one under another direction. */
@Composable
internal fun focusColor(): Color = TransitPalettes.tokensOf(MaterialTheme.colorScheme, LocalThemeTone.current).focus

/** State the frame exposes to its content. */
@Immutable
internal data class FrameState(val hovered: Boolean, val focused: Boolean) {
    val lit: Boolean get() = hovered || focused
}

/**
 * The clickable frame of every Transit card: press scale, hover and focus (a 3 dp ring outside the shape), long press, a 48 dp minimum
 * and one merged semantics node whose state is the line in words. [background] / [border] may react to the frame state.
 */
@Composable
internal fun TransitFrame(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    shape: Shape,
    spoken: String,
    onClickLabel: String? = null,
    background: @Composable (FrameState) -> Color? = { null },
    border: @Composable (FrameState) -> Color? = { null },
    dashedBorder: Boolean = false,
    content: @Composable (FrameState) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val state = FrameState(hovered, focused)
    val ring = focusColor()
    val bg = background(state)
    val edge = border(state)
    Box(
        modifier
            .pressScale(interaction)
            .then(if (focused) Modifier.drawBehind { drawFocusRing(this, ring, shape) } else Modifier)
            .clip(shape)
            .then(if (bg != null) Modifier.background(bg) else Modifier)
            .then(
                when {
                    edge == null -> Modifier
                    dashedBorder -> Modifier.drawBehind { drawDashedOutline(this, edge, shape) }
                    else -> Modifier.border(1.dp, edge, shape)
                },
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { stateDescription = spoken }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        content(state)
    }
}

private fun drawFocusRing(scope: DrawScope, color: Color, shape: Shape) = with(scope) {
    val width = 3.dp.toPx()
    val grow = 2.dp.toPx() + width / 2f
    val outline = shape.createOutline(Size(size.width + 2 * grow, size.height + 2 * grow), layoutDirection, this)
    translate(-grow, -grow) {
        drawOutline(outline, color, style = Stroke(width))
    }
}

private fun drawDashedOutline(scope: DrawScope, color: Color, shape: Shape) = with(scope) {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, color, style = Stroke(1.dp.toPx(), pathEffect = dashed(5.dp.toPx(), 4.dp.toPx())))
}

/**
 * Width of a card that owns its width (ROW / WISH / CONTINUE). [small] / [medium] are the artifact widths of the vertical card
 * (`data-size` s / m) and [continueWidth] the medium CONTINUE width; compact density narrows them like `CardSizing`, the wide sizes
 * use [CardSizing.wideCardDp] (the horizontal layout) and CONTINUE never exceeds the window minus its gutters.
 */
@Composable
internal fun ownedWidth(context: CardContext, size: CardSize, small: Dp, medium: Dp, continueWidth: Dp): Dp {
    val factor = if (LocalLook.current.density == Density.COMPACT) CardSizing.COMPACT_FACTOR else 1f
    val window = windowWidthDp()
    return when (context) {
        CardContext.CONTINUE -> {
            val w = when (size) {
                CardSize.SMALL -> continueWidth - 40.dp
                CardSize.MEDIUM -> continueWidth
                CardSize.LARGE -> continueWidth + 70.dp
                CardSize.XL -> continueWidth + 130.dp
            }
            minOf(w * factor, (window - 32f).coerceAtLeast(240f).dp)
        }
        else -> when (size) {
            CardSize.SMALL -> small * factor
            CardSize.MEDIUM -> medium * factor
            CardSize.LARGE, CardSize.XL -> CardSizing.wideCardDp(size, window).dp
        }
    }
}

/** ROW / WISH at a wide card size draw the horizontal layout (Diseño y estilo: large and extra large are horizontal cards). */
internal fun CardContext.horizontalAt(size: CardSize): Boolean =
    (this == CardContext.ROW || this == CardContext.WISH) && size.isWide

/** Status of the title over a poster: a dark plate with a ringed station, readable on any image. */
@Composable
internal fun PosterStatusBadge(slot: StatusSlot, label: String, modifier: Modifier = Modifier) {
    val ring = LocalStatusColors.current.of(slot)
    Row(
        modifier
            .clip(CircleShape)
            .background(TransitInk.ink)
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier.size(11.dp).drawBehind {
                val r = size.minDimension / 2f
                drawCircle(TransitInk.porcelain, r)
                drawCircle(ring, r - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
            },
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = BADGE_TEXT,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val BADGE_TEXT = Color(0xFFEEF1FA)

/** The play disc of CONTINUE cards: the card's play button when the screen gives one ([CardPlayTarget]), otherwise decorative. */
@Composable
internal fun PlayDisc(title: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    CardPlayTarget(title, modifier) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Title text of a card. */
@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier, maxLines: Int = 2, hero: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = if (hero) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        },
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The line under the title. */
@Composable
internal fun CardSub(text: String?, modifier: Modifier = Modifier, hero: Boolean = false) {
    if (text == null) return
    Text(
        text,
        modifier = modifier,
        style = if (hero) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Title over its sub line. */
@Composable
internal fun CardText(title: String, sub: String?, modifier: Modifier = Modifier, titleLines: Int = 2, hero: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CardTitle(title, maxLines = titleLines, hero = hero)
        CardSub(sub, hero = hero)
    }
}

/** The route of a card, or nothing when the screen hides progress. Paused and dropped titles get a grey train. */
@Composable
internal fun CardRoute(
    data: TitleCardData,
    model: LineModel,
    line: TransitLine,
    show: CardShow,
    lit: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    labels: Boolean = false,
    halo: Color = cardSurface(),
) {
    if (!show.progress) return
    TransitRoute(
        stops = model.stops,
        current = model.trainIndex,
        line = line,
        modifier = modifier,
        height = height,
        muted = data.listStatus == StatusSlot.PAUSED || data.listStatus == StatusSlot.DROPPED,
        emphasized = lit,
        openEnd = model.total == null,
        labels = labels,
        labelPrefix = model.prefix(),
        halo = halo,
    )
}

internal fun dashed(on: Float, off: Float): PathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off))

/** Draws a perforation (dotted cut) from [start] to [end]. */
internal fun DrawScope.perforation(color: Color, start: Offset, end: Offset) {
    drawLine(color, start, end, strokeWidth = 1.5.dp.toPx(), pathEffect = dashed(3.5.dp.toPx(), 3.5.dp.toPx()))
}
