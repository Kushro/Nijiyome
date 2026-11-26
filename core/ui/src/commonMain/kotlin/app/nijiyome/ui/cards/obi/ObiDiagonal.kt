// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.obi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.obi.ObiFonts
import app.nijiyome.designsystem.direction.obi.obiNoveltyBand
import app.nijiyome.model.CardSize
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardPlayTarget
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.MediaStatusKind
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_contract_new_ep
import app.nijiyome.ui.resources.rd_contract_stamp_new
import app.nijiyome.ui.resources.rd_obi_premiere
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Obi · diagonal (registerCard 'obi-diagonal'): a clean poster; only a novelty (a premiere or a new episode) gets the band, crossing the top corner
// at 45 degrees in the novelty color, so it reads from across the room. The rest of the cards stay plain with a status chip.

private class RibbonMetrics(val inset: Dp, val thickness: Dp, val fontSize: TextUnit)

private fun ribbonMetrics(size: CardSize, context: CardContext): RibbonMetrics = when (context) {
    CardContext.HERO -> RibbonMetrics(56.dp, 34.dp, 17.sp)
    CardContext.CONTINUE -> RibbonMetrics(30.dp, 22.dp, 12.sp)
    else -> size.pick(
        s = RibbonMetrics(30.dp, 20.dp, 12.sp),
        m = RibbonMetrics(44.dp, 24.dp, 12.sp),
        l = RibbonMetrics(54.dp, 28.dp, 14.5.sp),
        xl = RibbonMetrics(60.dp, 30.dp, 15.5.sp),
    )
}

/**
 * The novelty of a title (artifact `novelty()`), or null: a premiere (not aired yet, or nothing aired so far), or a new episode the
 * user has not seen ([TitleCardData.isNew]: "Nuevo ep. 9" when the aired count is known, just "Nuevo" on small cards).
 */
@Composable
private fun obiNovelty(data: TitleCardData, context: CardContext, size: CardSize): String? {
    val short = size == CardSize.SMALL && context != CardContext.HERO
    val aired = data.airedEpisodes
    return when {
        data.airing == MediaStatusKind.UPCOMING || aired == 0 -> stringResource(Res.string.rd_obi_premiere)
        data.isNew && aired != null && !short -> stringResource(Res.string.rd_contract_new_ep, aired)
        data.isNew -> stringResource(Res.string.rd_contract_stamp_new)
        else -> null
    }
}

@Composable
internal fun ObiDiagonalCardContent(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
) {
    val phrase = obiPhrase(data, context, show)
    val extra = obiExtra(data)
    val novelty = obiNovelty(data, context, size)
    val spoken = obiSpoken(data, phrase, extra, novelty)
    when (context) {
        CardContext.COMPACT -> ObiCompactLayout(data, phrase.long.ifBlank { null }, spoken, onClick, onLongClick, modifier) { wide ->
            val slot = data.listStatus
            when {
                novelty != null -> SlantBand(novelty, if (wide) Modifier else Modifier.fillMaxWidth())
                show.status && slot != null -> ObiStatusChip(slot, obiStatusLabel(data).orEmpty())
                else -> Text(
                    phrase.short,
                    style = obiUiStyle(12.sp, FontWeight.W400),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CardContext.CONTINUE -> DiagonalContinue(data, size, phrase, novelty, spoken, onClick, onLongClick, modifier)
        else -> DiagonalPoster(data, context, size, show, phrase, novelty, spoken, onClick, onLongClick, modifier)
    }
}

@Composable
private fun DiagonalPoster(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    phrase: ObiPhrase,
    novelty: String?,
    spoken: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val hero = context == CardContext.HERO
    val sized = when (context) {
        CardContext.ROW, CardContext.WISH -> modifier.width(obiPosterWidth(size))
        else -> modifier.fillMaxWidth()
    }
    val shape = RoundedCornerShape(4.dp)
    val progress = if (show.progress && data.episodesTotal != null && (data.episodesSeen ?: 0) > 0) data.obiProgress() else null
    val slot = data.listStatus
    val rib = ribbonMetrics(size, context)
    ObiFrame(spoken, onClick, onLongClick, shape, sized) { _ ->
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().clip(shape)) {
                MediaCover(url = data.coverUrl, modifier = Modifier.fillMaxWidth(), shape = RectangleShape)
                if (show.status && slot != null) {
                    ObiStatusChip(
                        slot,
                        obiStatusLabel(data).orEmpty(),
                        Modifier.align(Alignment.BottomStart).padding(start = 6.dp, end = 6.dp, bottom = if (progress != null) 18.dp else 8.dp),
                    )
                }
                if (progress != null) {
                    ObiBandProgress(
                        progress,
                        MaterialTheme.colorScheme.primary,
                        Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        track = Color.Black.copy(alpha = 0.45f),
                    )
                }
                if (novelty != null) {
                    ObiRibbon(novelty, rib, Modifier.matchParentSize())
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                data.title,
                style = if (hero) {
                    TextStyle(fontFamily = ObiFonts.band(), fontWeight = FontWeight.W400, fontSize = 20.sp, lineHeight = 23.sp)
                } else {
                    obiUiStyle(13.sp)
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                show.next && data.nextLabel != null -> phrase.long
                context == CardContext.WISH || hero -> phrase.long
                else -> obiFacts(data).orEmpty()
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = obiUiStyle(11.5.sp, FontWeight.W400),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Continuar: poster, title, line and progress, a round play button in the accent, and the corner band when there is news. */
@Composable
private fun DiagonalContinue(
    data: TitleCardData,
    size: CardSize,
    phrase: ObiPhrase,
    novelty: String?,
    spoken: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    ObiFrame(spoken, onClick, onLongClick, shape, modifier.width(obiContinueWidth(size)), playTitle = data.title) { raised ->
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (raised) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Box(Modifier.matchParentSize()) {
                MediaCover(
                    url = data.coverUrl,
                    modifier = Modifier.width(72.dp).fillMaxHeight(),
                    shape = RectangleShape,
                    aspectRatio = 72f / 108f,
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 108.dp).padding(start = 84.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f).padding(end = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        data.title,
                        style = obiUiStyle(14.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (phrase.long.isNotBlank()) {
                        Text(
                            phrase.long,
                            style = obiUiStyle(12.sp, FontWeight.W400),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ObiBandProgress(
                        data.obiProgress() ?: 0f,
                        MaterialTheme.colorScheme.primary,
                        Modifier.padding(top = 4.dp),
                        track = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                CardPlayTarget(data.title) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            if (novelty != null) ObiRibbon(novelty, ribbonMetrics(size, CardContext.CONTINUE), Modifier.matchParentSize())
        }
    }
}

/**
 * The corner band: a strip [RibbonMetrics.thickness] thick whose center line crosses the top-right corner [RibbonMetrics.inset] in
 * from both edges, turned 45 degrees (artifact `.obi-ribbon`). Fill [modifier] with the area it crosses (`matchParentSize()`); the
 * parent clips it.
 */
@Composable
private fun ObiRibbon(text: String, metrics: RibbonMetrics, modifier: Modifier) {
    val colors = obiNoveltyBand()
    Layout(
        content = {
            Box(Modifier.background(colors.bg), contentAlignment = Alignment.Center) {
                Text(
                    text,
                    modifier = Modifier.widthIn(max = metrics.inset * 2.83f - metrics.thickness),
                    style = TextStyle(
                        fontFamily = ObiFonts.band(),
                        fontWeight = FontWeight.W400,
                        fontSize = metrics.fontSize,
                        lineHeight = metrics.fontSize,
                        textGeometricTransform = ObiFonts.condensed(0.88f),
                    ),
                    color = colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val inset = metrics.inset.toPx()
        val thick = metrics.thickness.roundToPx()
        val length = (inset * 2.83f + thick * 2).roundToInt()
        val strip = measurables.first().measure(Constraints.fixed(length, thick))
        layout(w, h) {
            strip.placeWithLayer(x = (w - inset - length / 2f).roundToInt(), y = (inset - thick / 2f).roundToInt()) { rotationZ = 45f }
        }
    }
}

/** The novelty band of Compacta: a parallelogram in the novelty color (artifact `.obi-slant`). */
@Composable
private fun SlantBand(text: String, modifier: Modifier) {
    val colors = obiNoveltyBand()
    Box(
        modifier
            .heightIn(min = 26.dp)
            .clip(SlantShape)
            .background(colors.bg)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        ObiCondText(text, colors.fg, 12.5.sp, scaleX = 0.92f, lines = 2)
    }
}

private object SlantShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val k = with(density) { 9.dp.toPx() }
        return Outline.Generic(
            Path().apply {
                moveTo(k, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - k, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}

/** A list status chip in Obi's ink (artifact `.nj-status` under Obi): square dot of the status color and a bold label. */
@Composable
internal fun ObiStatusChip(slot: StatusSlot, label: String, modifier: Modifier = Modifier) {
    val color = LocalStatusColors.current.of(slot)
    Row(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f))
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Text(label, style = obiUiStyle(11.sp, FontWeight.W800), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
