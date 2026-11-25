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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.obi.ObiBand
import app.nijiyome.designsystem.direction.obi.ObiEaseOut
import app.nijiyome.designsystem.direction.obi.ObiFonts
import app.nijiyome.designsystem.direction.obi.ObiInk
import app.nijiyome.designsystem.direction.obi.obiSweepBackground
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.model.CardSize
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardPlayTarget
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_obi_eps_unit
import org.jetbrains.compose.resources.stringResource

// Obi · lomo (registerCard 'obi-lomo'): a narrow bookshelf spine in the series color, cover crop at the top, the title running down
// the spine in Dela Gothic One and a big number at the foot (seen / total, or the episode count). Rows of spines stand on the same
// line like a shelf. Continuar and Compacta lay the spine down; Destacada opens it beside its cover.

private class SpineMetrics(val width: Dp, val height: Dp, val fontSize: TextUnit)

private fun spineMetrics(size: CardSize): SpineMetrics = size.pick(
    s = SpineMetrics(44.dp, 204.dp, 13.sp),
    m = SpineMetrics(54.dp, 256.dp, 14.5.sp),
    l = SpineMetrics(66.dp, 300.dp, 18.sp),
    xl = SpineMetrics(78.dp, 340.dp, 20.sp),
)

private class HeroMetrics(val spine: Dp, val height: Dp, val fontSize: TextUnit)

private fun heroMetrics(size: CardSize): HeroMetrics = size.pick(
    s = HeroMetrics(60.dp, 270.dp, 17.sp),
    m = HeroMetrics(72.dp, 330.dp, 20.sp),
    l = HeroMetrics(84.dp, 390.dp, 24.sp),
    xl = HeroMetrics(92.dp, 430.dp, 26.sp),
)

/** The number at the foot of a spine (artifact `spineNum()`): big figure and its small unit. */
private data class SpineNumber(val big: String, val small: String)

@Composable
private fun spineNumber(data: TitleCardData): SpineNumber {
    val seen = data.episodesSeen
    val total = data.episodesTotal?.takeIf { it > 0 }
    val read = data.progress
    return when {
        seen != null && seen > 0 && data.listStatus != StatusSlot.PLANNING -> SpineNumber(seen.toString(), total?.let { "/$it" } ?: "")
        total != null -> SpineNumber(total.toString(), stringResource(Res.string.rd_obi_eps_unit))
        // Manga and novels bring a 0..1 progress instead of episodes.
        read != null -> SpineNumber((read.coerceIn(0f, 1f) * 100).toInt().toString(), "%")
        else -> SpineNumber("?", stringResource(Res.string.rd_obi_eps_unit))
    }
}

@Composable
internal fun ObiSpineCardContent(
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
        CardContext.CONTINUE, CardContext.COMPACT -> FlatSpine(data, context, size, show, phrase, spoken, onClick, onLongClick, modifier)
        CardContext.HERO -> SpineBook(data, size, phrase, spoken, onClick, onLongClick, modifier)
        else -> {
            val m = spineMetrics(size)
            val wish = context == CardContext.WISH
            // ROW and WISH own their width; in a GRID cell the spine keeps its width and stands at the foot of the cell.
            val frame = if (context == CardContext.GRID) modifier.fillMaxWidth() else modifier.width(m.width)
            ObiFrame(spoken, onClick, onLongClick, RoundedCornerShape(3.dp), frame) { raised ->
                Box(Modifier.fillMaxWidth().height(m.height + 8.dp), contentAlignment = Alignment.BottomCenter) {
                    StandingSpine(
                        data = data,
                        band = if (wish) obiPaper() else obiTone(data),
                        foot = if (wish) remember(data.accent) { ObiInk.bandFor(data.accent ?: ObiInk.NEUTRAL) } else null,
                        metrics = m,
                        progress = if (show.progress && !wish) data.obiProgress() else null,
                        raised = raised,
                        outlined = wish,
                    )
                }
            }
        }
    }
}

/** A standing spine; it slides 8 dp off the shelf when raised (spatial motion only). */
@Composable
private fun StandingSpine(
    data: TitleCardData,
    band: ObiBand,
    foot: ObiBand?,
    metrics: SpineMetrics,
    progress: Float?,
    raised: Boolean,
    outlined: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = metrics.width,
    shape: RoundedCornerShape = RoundedCornerShape(3.dp),
) {
    val spatial = LocalMotion.current.spatial
    val lift by animateFloatAsState(
        targetValue = if (raised && spatial) 1f else 0f,
        animationSpec = if (spatial) tween(durationMillis = 320, delayMillis = 40, easing = ObiEaseOut) else snap(),
        label = "obiSpineLift",
    )
    val edge = if (outlined) MaterialTheme.colorScheme.outlineVariant else band.fg.copy(alpha = 0.12f)
    Column(
        modifier
            .width(width)
            .height(metrics.height)
            .graphicsLayer { translationY = -8.dp.toPx() * lift }
            .clip(shape)
            .obiSweepBackground(band.bg)
            .border(1.dp, edge, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaCover(url = data.coverUrl, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, aspectRatio = 1f)
        ObiVerticalText(
            text = data.title,
            style = TextStyle(fontFamily = ObiFonts.band(), fontWeight = FontWeight.W400, fontSize = metrics.fontSize, lineHeight = metrics.fontSize * 1.05f),
            color = band.fg,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
        )
        val number = spineNumber(data)
        val footBand = foot ?: band
        val rule = band.fg.copy(alpha = 0.26f)
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (foot != null) Modifier.background(foot.bg) else Modifier.drawBehind { drawRect(rule, size = Size(size.width, 1.dp.toPx())) })
                .padding(top = if (foot != null) 8.dp else 6.dp, bottom = if (progress != null) 4.dp else 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                number.big,
                style = TextStyle(fontFamily = ObiFonts.band(), fontWeight = FontWeight.W400, fontSize = metrics.fontSize * 1.35f, lineHeight = metrics.fontSize * 1.35f),
                color = footBand.fg,
                maxLines = 1,
            )
            if (number.small.isNotEmpty()) {
                Text(number.small, style = obiUiStyle(11.sp).copy(lineHeight = 11.sp), color = footBand.fg, maxLines = 1)
            }
        }
        if (progress != null) {
            ObiBandProgress(progress, band.fg, Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp))
        }
    }
}

/** Continuar and Compacta: the spine lies down. Poster, title and line, the number and, in Continuar, a play block in ink. */
@Composable
private fun FlatSpine(
    data: TitleCardData,
    context: CardContext,
    size: CardSize,
    show: CardShow,
    phrase: ObiPhrase,
    spoken: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val band = obiTone(data)
    val compact = context == CardContext.COMPACT
    val frame = if (compact) modifier.fillMaxWidth() else modifier.width(obiContinueWidth(size))
    val status = obiStatusLabel(data)
    val line = if (compact && show.status && status != null &&
        data.listStatus != StatusSlot.COMPLETED && data.listStatus != StatusSlot.PAUSED && data.listStatus != StatusSlot.DROPPED
    ) {
        "$status · ${phrase.long}"
    } else {
        phrase.long
    }
    val spatial = LocalMotion.current.spatial
    ObiFrame(spoken, onClick, onLongClick, RoundedCornerShape(3.dp), frame, playTitle = data.title.takeIf { !compact }) { raised ->
        val slide by animateFloatAsState(
            targetValue = if (raised && spatial) 1f else 0f,
            animationSpec = if (spatial) tween(durationMillis = 320, easing = ObiEaseOut) else snap(),
            label = "obiFlatSpine",
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showNumber = !compact || maxWidth > 300.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = 4.dp.toPx() * slide }
                    .clip(RoundedCornerShape(3.dp))
                    .obiSweepBackground(band.bg),
            ) {
                Box(Modifier.matchParentSize()) {
                    MediaCover(
                        url = data.coverUrl,
                        modifier = Modifier.width(if (compact) 44.dp else 64.dp).fillMaxHeight(),
                        shape = RectangleShape,
                        aspectRatio = if (compact) 44f / 56f else 1f,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = if (compact) 56.dp else 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(if (compact) 44.dp else 64.dp))
                    Column(
                        Modifier.weight(1f).padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            data.title,
                            style = TextStyle(fontFamily = ObiFonts.band(), fontWeight = FontWeight.W400, fontSize = if (compact) 14.sp else 15.sp, lineHeight = if (compact) 16.sp else 17.sp),
                            color = band.fg,
                            maxLines = if (compact) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (line.isNotBlank()) {
                            Text(line, style = obiUiStyle(12.5.sp), color = band.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (showNumber) {
                        val number = spineNumber(data)
                        val rule = band.fg.copy(alpha = 0.26f)
                        Column(
                            Modifier
                                .drawBehind { drawRect(rule, size = Size(1.dp.toPx(), this.size.height)) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                number.big,
                                style = TextStyle(fontFamily = ObiFonts.band(), fontWeight = FontWeight.W400, fontSize = 20.sp, lineHeight = 20.sp),
                                color = band.fg,
                                maxLines = 1,
                            )
                            if (number.small.isNotEmpty()) {
                                Text(number.small, style = obiUiStyle(11.sp).copy(lineHeight = 11.sp), color = band.fg, maxLines = 1)
                            }
                        }
                    }
                    if (!compact) {
                        // Play block in the band's ink (artifact `.lomo-go`): the inverse of the band.
                        CardPlayTarget(data.title, shape = RectangleShape) {
                            Box(
                                Modifier.width(44.dp).heightIn(min = 64.dp).background(band.fg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = band.bg, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                val progress = data.obiProgress()
                if (progress != null && (!compact || show.progress)) {
                    ObiBandProgress(progress, band.fg, Modifier.align(Alignment.BottomStart))
                }
            }
        }
    }
}

/** Destacada: the spine and its cover side by side, like a book opened on the shelf, with the line of the moment below. */
@Composable
private fun SpineBook(
    data: TitleCardData,
    size: CardSize,
    phrase: ObiPhrase,
    spoken: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val hm = heroMetrics(size)
    ObiFrame(spoken, onClick, onLongClick, RoundedCornerShape(3.dp), modifier.fillMaxWidth()) { raised ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // The cover is 2:3 next to the spine; the book is as tall as the width allows, else the artifact height.
                val bookHeight = if (maxWidth.value.isFinite()) ((maxWidth - hm.spine) * 1.5f).coerceAtMost(hm.height * 1.4f) else hm.height
                Row(Modifier.fillMaxWidth().height(bookHeight)) {
                    StandingSpine(
                        data = data,
                        band = obiTone(data),
                        foot = null,
                        metrics = SpineMetrics(hm.spine, bookHeight, hm.fontSize),
                        progress = null,
                        raised = false,
                        outlined = false,
                        shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp),
                    )
                    MediaCover(
                        url = data.coverUrl,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp),
                        aspectRatio = 2f / 3f,
                    )
                }
            }
            if (phrase.long.isNotBlank()) {
                Text(
                    phrase.long,
                    style = obiUiStyle(12.sp, FontWeight.W400),
                    color = if (raised) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
