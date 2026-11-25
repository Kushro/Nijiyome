// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.obi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.obi.ObiBand
import app.nijiyome.designsystem.direction.obi.ObiEaseOut
import app.nijiyome.designsystem.direction.obi.ObiFonts
import app.nijiyome.designsystem.direction.obi.obiSweepBackground
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.model.CardSize
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardPlayTarget
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_obi_eps
import app.nijiyome.ui.resources.rd_obi_resume
import org.jetbrains.compose.resources.stringResource

// Obi · faja (registerCard 'obi'): the poster wears a band across its foot in the series color, with the status tab, the line of
// the moment and, raised on hover or focus, a second line with watched count and score. Continuar crosses the whole card with the
// band; Compacta puts the band as a tag beside or under the title.

private class BandMetrics(val line: Dp, val extra: Dp, val fontSize: TextUnit, val cond: Float, val tabSize: TextUnit, val pad: Dp)

private fun bandMetrics(size: CardSize): BandMetrics = size.pick(
    s = BandMetrics(26.dp, 20.dp, 12.sp, 0.92f, 10.sp, 6.dp),
    m = BandMetrics(30.dp, 22.dp, 13.5.sp, 0.86f, 10.5.sp, 8.dp),
    l = BandMetrics(36.dp, 24.dp, 16.sp, 0.86f, 11.sp, 8.dp),
    xl = BandMetrics(40.dp, 26.dp, 17.sp, 0.86f, 11.5.sp, 10.dp),
)

/** Watch progress of the card, 0..1: the screen's, else seen / total. */
internal fun TitleCardData.obiProgress(): Float? =
    progress ?: episodesSeen?.let { seen -> episodesTotal?.takeIf { it > 0 }?.let { seen.toFloat() / it } }

@Composable
internal fun ObiBandCardContent(
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
    val spoken = obiSpoken(data, phrase, extra)
    when (context) {
        CardContext.COMPACT -> {
            val band = obiTone(data)
            val sub = listOfNotNull(if (show.status) obiStatusLabel(data) else null, obiFacts(data)).joinToString(" · ").ifEmpty { null }
            ObiCompactLayout(data, sub, spoken, onClick, onLongClick, modifier) { wide ->
                Box(
                    Modifier
                        .then(if (wide) Modifier else Modifier.fillMaxWidth())
                        .clip(RoundedCornerShape(2.dp))
                        .obiSweepBackground(band.bg)
                        .heightIn(min = 26.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ObiCondText(phrase.short, band.fg, 13.sp, scaleX = 0.88f, lines = if (wide) 1 else 2)
                }
            }
        }
        CardContext.CONTINUE -> BandContinue(data, size, phrase, spoken, onClick, onLongClick, modifier)
        else -> BandPoster(data, context, size, show, phrase.let { if (size == CardSize.SMALL && context != CardContext.HERO) it.short else it.long }, extra, spoken, onClick, onLongClick, modifier)
    }
}

@Composable
private fun BandPoster(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    phrase: String,
    extra: String,
    spoken: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val wish = context == CardContext.WISH
    val hero = context == CardContext.HERO
    val band = if (wish) obiPaper() else obiTone(data)
    val metrics = bandMetrics(size).let { if (hero) BandMetrics(it.line, 24.dp, it.fontSize, it.cond, it.tabSize, 10.dp) else it }
    val sized = when (context) {
        CardContext.ROW, CardContext.WISH -> modifier.width(obiPosterWidth(size))
        else -> modifier.fillMaxWidth()
    }
    val shape = RoundedCornerShape(4.dp)
    val progress = if (show.progress) data.obiProgress() else null
    ObiFrame(spoken, onClick, onLongClick, shape, sized) { raised ->
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().clip(shape)) {
                MediaCover(url = data.coverUrl, modifier = Modifier.fillMaxWidth(), shape = RectangleShape)
                BandBlock(
                    band = band,
                    metrics = metrics,
                    phrase = phrase,
                    extra = extra.ifBlank { null },
                    status = obiStatusLabel(data),
                    progress = progress,
                    heroTitle = if (hero) data.title else null,
                    swatch = if (wish) data.accent ?: MaterialTheme.colorScheme.outline else null,
                    topLine = if (wish) MaterialTheme.colorScheme.outlineVariant else null,
                    raised = raised,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!hero) {
                Text(
                    data.title,
                    style = obiUiStyle(13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            obiFacts(data)?.let {
                Text(
                    it,
                    style = obiUiStyle(11.5.sp, FontWeight.W400),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The band over the foot of the poster. At rest the second line hangs below the poster (clipped); raised, the whole band rises so
 * it shows, 3 dp above the edge (artifact hover: `translateY(-x - 3px)`, 60 ms delay). Only spatial motion lifts it.
 */
@Composable
private fun BandBlock(
    band: ObiBand,
    metrics: BandMetrics,
    phrase: String,
    extra: String?,
    status: String?,
    progress: Float?,
    heroTitle: String?,
    swatch: Color?,
    topLine: Color?,
    raised: Boolean,
    modifier: Modifier,
) {
    val spatial = LocalMotion.current.spatial
    val lift by animateFloatAsState(
        targetValue = if (raised && spatial) 1f else 0f,
        animationSpec = if (spatial) tween(durationMillis = 320, delayMillis = 60, easing = ObiEaseOut) else snap(),
        label = "obiBandLift",
    )
    val hidden = if (extra != null) metrics.extra else 0.dp
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val down = hidden.toPx()
                val up = -3.dp.toPx()
                translationY = down + (up - down) * lift
            },
    ) {
        if (status != null) {
            Text(
                status,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .obiSweepBackground(band.bg)
                    .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                style = obiUiStyle(metrics.tabSize, FontWeight.W800).copy(lineHeight = metrics.tabSize),
                color = band.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .obiSweepBackground(band.bg)
                .then(
                    if (topLine != null) {
                        Modifier.drawBehind { drawRect(topLine, size = Size(size.width, 1.dp.toPx())) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (heroTitle != null) {
                    Text(
                        heroTitle,
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
                        style = obiUiStyle(20.sp, FontWeight.W400).copy(fontFamily = ObiFonts.band(), lineHeight = 23.sp),
                        color = band.fg,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().height(metrics.line).padding(horizontal = metrics.pad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (swatch != null) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(swatch)
                                .border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
                        )
                    }
                    ObiCondText(phrase, band.fg, metrics.fontSize, Modifier.weight(1f), scaleX = metrics.cond)
                }
                if (extra != null) {
                    val rule = band.fg.copy(alpha = 0.24f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(metrics.extra)
                            .drawBehind { drawRect(rule, size = Size(size.width, 1.dp.toPx())) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(extra, style = obiUiStyle(11.5.sp), color = band.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (progress != null) ObiBandProgress(progress, band.fg, Modifier.align(Alignment.TopStart))
        }
    }
}

/** Continuar: poster on the left, title and facts beside it, and the band crossing the whole card with Reanudar. */
@Composable
private fun BandContinue(
    data: TitleCardData,
    size: CardSize,
    phrase: ObiPhrase,
    spoken: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val band = obiTone(data)
    val big = size.step >= 2
    val poster = if (big) 84.dp else 72.dp
    val minHeight = if (big) 128.dp else 112.dp
    val line = if (big) 40.dp else 36.dp
    val shape = RoundedCornerShape(6.dp)
    val facts = listOfNotNull(
        obiFacts(data),
        data.episodesTotal?.takeIf { it > 0 }?.let { stringResource(Res.string.rd_obi_eps, it) },
    ).joinToString(" · ")
    ObiFrame(spoken, onClick, onLongClick, shape, modifier.width(obiContinueWidth(size)), playTitle = data.title) { raised ->
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (raised) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Box(Modifier.matchParentSize()) {
                MediaCover(
                    url = data.coverUrl,
                    modifier = Modifier.width(poster).fillMaxHeight(),
                    shape = RectangleShape,
                    aspectRatio = poster / minHeight,
                )
            }
            Column(Modifier.fillMaxWidth().heightIn(min = minHeight), verticalArrangement = Arrangement.SpaceBetween) {
                Column(
                    Modifier.padding(start = poster + 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        data.title,
                        style = obiUiStyle(14.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (facts.isNotEmpty()) {
                        Text(
                            facts,
                            style = obiUiStyle(12.sp, FontWeight.W400),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    obiStatusLabel(data)?.let { status ->
                        Text(
                            status,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .obiSweepBackground(band.bg)
                                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                            style = obiUiStyle(10.5.sp, FontWeight.W800).copy(lineHeight = 10.5.sp),
                            color = band.fg,
                            maxLines = 1,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(line).obiSweepBackground(band.bg)) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ObiCondText(phrase.long, band.fg, if (big) 16.sp else 14.sp, Modifier.weight(1f))
                            Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(20.dp).background(band.fg.copy(alpha = 0.3f)))
                            CardPlayTarget(data.title, shape = RoundedCornerShape(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = band.fg, modifier = Modifier.size(18.dp))
                                    Text(
                                        stringResource(Res.string.rd_obi_resume),
                                        style = obiUiStyle(12.5.sp, FontWeight.W800),
                                        color = band.fg,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 2.dp),
                                    )
                                }
                            }
                        }
                        ObiBandProgress(data.obiProgress() ?: 0f, band.fg, Modifier.align(Alignment.TopStart))
                    }
                }
            }
        }
    }
}

/**
 * Compacta (shared by the band and diagonal styles): 44 dp poster, title and facts; the tag goes under the title across the width in
 * narrow places and moves to the right from 400 dp. The title never shares its line with the tag.
 */
@Composable
internal fun ObiCompactLayout(
    data: TitleCardData,
    sub: String?,
    spoken: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    tail: @Composable (wide: Boolean) -> Unit,
) {
    ObiFrame(spoken, onClick, onLongClick, RoundedCornerShape(4.dp), modifier.fillMaxWidth()) { _ ->
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(vertical = 4.dp)) {
            val wide = maxWidth >= 400.dp
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = if (wide) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MediaCover(url = data.coverUrl, modifier = Modifier.width(44.dp), shape = RoundedCornerShape(2.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        data.title,
                        style = obiUiStyle(14.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sub != null) {
                        Text(
                            sub,
                            style = obiUiStyle(12.sp, FontWeight.W400),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!wide) Box(Modifier.fillMaxWidth().padding(top = 4.dp)) { tail(false) }
                }
                if (wide) Box(Modifier.widthIn(max = 170.dp)) { tail(true) }
            }
        }
    }
}
