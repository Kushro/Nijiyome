// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.sumi

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.sumi.EnsoRing
import app.nijiyome.designsystem.direction.sumi.HankoSeal
import app.nijiyome.designsystem.direction.sumi.SumiBrushStroke
import app.nijiyome.designsystem.direction.sumi.SumiInkLine
import app.nijiyome.model.CardSize
import app.nijiyome.model.Density
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardPlayTarget
import app.nijiyome.ui.cards.LocalCardIndex
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.look.CardSizing
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.pressScale
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.platform.windowWidthDp
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_sumi_card_eps
import app.nijiyome.ui.resources.rd_sumi_card_percent
import app.nijiyome.ui.resources.rd_sumi_card_seen_of
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Pieces shared by the three Sumi card styles (artifact dir-sumi.js `renderHanko`, `renderEnso`, `renderPapel`): widths,
// the click / focus / press wrapper, the seal tag, the play disc, the facts line and the hero layout.

/** Radii of the artifact: `--nj-r-xs` 3, `-s` 6, `-m` 10. */
internal val SumiRadiusXs = RoundedCornerShape(3.dp)
internal val SumiRadiusS = RoundedCornerShape(6.dp)
internal val SumiRadiusM = RoundedCornerShape(10.dp)

/** Poster width of a poster card in a row: small / medium as Diseño y estilo, large and XL grow (artifact s 112, m 150, l 196). */
@Composable
internal fun sumiPosterWidth(size: CardSize): Dp {
    val base = when (size) {
        CardSize.SMALL -> CardSizing.SMALL_DP
        CardSize.MEDIUM -> CardSizing.MEDIUM_DP
        CardSize.LARGE -> 150f
        CardSize.XL -> 180f
    }
    return (if (LocalLook.current.density == Density.COMPACT) base * CardSizing.COMPACT_FACTOR else base).dp
}

/** Width of a wide row card (poster beside its facts) for LARGE / XL, the app's own rule. */
@Composable
internal fun sumiWideWidth(size: CardSize): Dp = CardSizing.wideCardDp(size, windowWidthDp()).dp

/** Width of a "continue" card ([base] for medium, from the artifact), never wider than the window minus its padding. */
@Composable
internal fun sumiContinueWidth(size: CardSize, base: Dp): Dp {
    val w = when (size) {
        CardSize.SMALL -> base - 40.dp
        CardSize.MEDIUM -> base
        CardSize.LARGE -> base + 60.dp
        CardSize.XL -> base + 100.dp
    }
    val max = (windowWidthDp() - 2 * CardSizing.ROW_PADDING_DP).dp
    return if (w > max) max else w
}

/**
 * Press, focus and click of a card: the press scale of Diseño y estilo, a 2 dp accent ring when focused from the keyboard,
 * one clickable node (it merges the texts below, so the title is read once) and the long press when given.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.sumiCardClick(shape: Shape, onClick: () -> Unit, onLongClick: (() -> Unit)?, status: String? = null): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = MaterialTheme.colorScheme.primary
    return this
        .pressScale(interaction)
        .then(if (focused) Modifier.border(2.dp, ring, shape) else Modifier)
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        .then(if (status != null) Modifier.semantics { stateDescription = status } else Modifier)
}

/** What the card says under the title (artifact `sub`). */
internal fun TitleCardData.sumiSub(context: CardContext, show: CardShow): String? = when {
    context == CardContext.CONTINUE -> nextLabel ?: subtitle
    (show.next || context == CardContext.WISH) && nextLabel != null -> nextLabel
    else -> subtitle ?: listOfNotNull(format, year?.toString()).joinToString(" · ").ifBlank { null }
}

/** The "continue" line: the episode to play, else the facts. */
internal fun TitleCardData.sumiContinueSub(): String? = nextLabel ?: subtitle

/** The progress in words: "3 of 12 episodes" when the counts are known, else "60% watched". */
@Composable
internal fun TitleCardData.sumiProgressLabel(): String? {
    val seen = episodesSeen
    val total = episodesTotal
    if (seen != null && total != null && total > 0) return stringResource(Res.string.rd_sumi_card_seen_of, seen, total)
    val p = progress ?: return null
    return stringResource(Res.string.rd_sumi_card_percent, "${(p.coerceIn(0f, 1f) * 100).roundToInt()}%")
}

/** Watch / read progress 0..1: the given one, else seen / total. */
internal fun TitleCardData.sumiProgress(): Float? {
    progress?.let { return it.coerceIn(0f, 1f) }
    val seen = episodesSeen ?: return null
    val total = episodesTotal ?: return null
    return if (total > 0 && seen > 0) (seen / total.toFloat()).coerceIn(0f, 1f) else null
}

/** Something in progress: the ensō is drawn only for what the user is watching, rewatching or paused on. */
internal fun TitleCardData.sumiLive(context: CardContext): Boolean {
    if (sumiProgress() == null) return false
    val s = listStatus ?: return context == CardContext.CONTINUE
    return s == StatusSlot.WATCHING || s == StatusSlot.REWATCHING || s == StatusSlot.PAUSED
}

/** The poster with a hairline of ink (`box-shadow: 0 0 0 1px` text at 10 %). */
@Composable
internal fun SumiPoster(data: TitleCardData, modifier: Modifier, shape: Shape, hairline: Boolean = true, aspectRatio: Float? = null, url: String? = data.coverUrl) {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val m = if (hairline) modifier.border(1.dp, line, shape) else modifier
    if (aspectRatio != null) MediaCover(url, m, shape = shape, aspectRatio = aspectRatio) else MediaCover(url, m, shape = shape)
}

/** The paper mat around a poster (`.sm-mat`): surface 2, a line border and [pad] of paper. */
@Composable
internal fun PaperMat(modifier: Modifier, pad: Dp, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(SumiRadiusS)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SumiRadiusS)
            .padding(pad),
    ) { content() }
}

/** A seal stamped in the top-right corner of a poster (`.sm-corner`), with a ring of the background around it. */
@Composable
internal fun CornerSeal(status: StatusSlot, size: Dp, round: Boolean = false, brush: Boolean = false, modifier: Modifier = Modifier, inset: Dp = 7.dp) {
    HankoSeal(
        status,
        modifier = modifier.padding(inset).clearAndSetSemantics { },
        size = size,
        round = round,
        brush = brush,
        halo = MaterialTheme.colorScheme.background,
    )
}

/** The round seal with the status name beside it (`.sm-tag`). */
@Composable
internal fun SealTag(status: StatusSlot, label: String?, modifier: Modifier = Modifier, sealSize: Dp = 18.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HankoSeal(status, Modifier.clearAndSetSemantics { }, size = sealSize, round = true)
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The filled play disc of a card ([ink] = the outlined variant of Sumi papel); in [CardPlayTarget] it is the card's play button when given one. */
@Composable
internal fun PlayDisc(size: Dp, modifier: Modifier = Modifier, ink: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (ink) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier.background(scheme.primary)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = if (ink) scheme.primary else scheme.onPrimary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** The ensō around a play disc (`.sm-ringwrap`): [ring] across, the disc at 0.7 of it. */
@Composable
internal fun RingPlay(progress: Float, ring: Dp, modifier: Modifier = Modifier, traceKey: String? = null, traceIndex: Int? = null, disc: Boolean = true) {
    Box(modifier.size(ring), contentAlignment = Alignment.Center) {
        EnsoRing(progress, Modifier.size(ring), traceKey = traceKey, traceIndex = traceIndex)
        if (disc) {
            PlayDisc(ring * 0.7f)
        } else {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(ring * 0.45f),
            )
        }
    }
}

/** The small ensō badge on a poster (`.sm-ringwrap.is-badge`): a disc of the background with the ring and a play glyph. */
@Composable
internal fun RingBadge(progress: Float, modifier: Modifier = Modifier, size: Dp = 40.dp, traceKey: String? = null) {
    Box(modifier.padding(6.dp).size(size).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        RingPlay(progress, size, traceKey = traceKey, disc = false)
    }
}

/** The score with its star in the planning ink (`.sm-score`). */
@Composable
internal fun SumiScore(score: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = LocalStatusColors.current.planning, modifier = Modifier.size(14.dp))
        Text(score, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Title of a poster card, per size (`.nj-card-title` 700; s smaller, l larger). */
@Composable
internal fun CardTitle(text: String, size: CardSize, modifier: Modifier = Modifier, maxLines: Int = 2, paper: Boolean = false) {
    val t = MaterialTheme.typography
    val style = when (size) {
        CardSize.SMALL -> t.labelMedium
        CardSize.MEDIUM -> if (paper) t.bodyMedium else t.labelLarge
        CardSize.LARGE, CardSize.XL -> t.titleSmall
    }
    Text(
        text,
        style = style.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The line under a card title (`.nj-card-sub`; on a wish card it is the point, so it reads in the text ink). */
@Composable
internal fun CardSub(text: String?, context: CardContext, modifier: Modifier = Modifier, maxLines: Int = 1) {
    if (text.isNullOrBlank()) return
    val wish = context == CardContext.WISH
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (wish) FontWeight.Medium else FontWeight.Normal),
        color = if (wish) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Format · year · episodes, the hero's facts line. */
@Composable
internal fun TitleCardData.sumiFacts(): String? {
    val eps = episodesTotal?.let { stringResource(Res.string.rd_sumi_card_eps, it) }
    return listOfNotNull(format, year?.toString(), eps).joinToString(" · ").ifBlank { subtitle }
}

/** Which Sumi style draws a hero. */
internal enum class SumiStyle { HANKO, ENSO, PAPER }

/**
 * The hero card of the three styles (artifact `hero`): poster (with the corner seal for Hanko, in its mat for Sumi papel),
 * the title in the display face with a brush stroke of the accent under it, the facts, the status seal tag and the progress
 * (an ink line for Hanko, the ensō around play for Ensō).
 */
@Composable
internal fun SumiHero(style: SumiStyle, data: TitleCardData, size: CardSize, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val poster = when (size) {
        CardSize.SMALL -> 120.dp
        CardSize.MEDIUM -> 168.dp
        CardSize.LARGE -> 220.dp
        CardSize.XL -> 240.dp
    }
    val gap = if (size == CardSize.SMALL) 16.dp else 22.dp
    val t = MaterialTheme.typography
    val titleSize = when (size) {
        CardSize.SMALL -> 21.sp
        CardSize.MEDIUM -> 30.sp
        CardSize.LARGE, CardSize.XL -> 44.sp
    }
    val p = data.sumiProgress()
    val progressLabel = data.sumiProgressLabel()
    val status = data.listStatus
    Row(
        modifier
            .fillMaxWidth()
            .sumiCardClick(SumiRadiusM, onClick, onLongClick)
            .padding(4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Box(Modifier.width(minOf(poster, (windowWidthDp() * 0.34f).dp))) {
            if (style == SumiStyle.PAPER) {
                PaperMat(Modifier.fillMaxWidth(), 10.dp) { SumiPoster(data, Modifier.fillMaxWidth(), RoundedCornerShape(2.dp), hairline = false) }
            } else {
                SumiPoster(data, Modifier.fillMaxWidth(), SumiRadiusS)
                if (style == SumiStyle.HANKO && status != null && show.status) {
                    CornerSeal(status, 34.dp, brush = true, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
        Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(
                    data.title,
                    style = t.headlineMedium.copy(fontSize = titleSize, lineHeight = titleSize * 1.15f, fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                SumiBrushStroke(MaterialTheme.colorScheme.primary, Modifier.padding(top = 10.dp))
            }
            HeroFacts(data, show)
            if (status != null && show.status) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SealTag(status, data.listStatusLabel, sealSize = 24.dp)
                    if (style != SumiStyle.ENSO && progressLabel != null && show.progress) {
                        Text(progressLabel, style = t.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            if (p != null && show.progress) {
                when (style) {
                    SumiStyle.HANKO -> SumiInkLine(p, Modifier.widthIn(max = 280.dp))
                    SumiStyle.ENSO -> if (data.sumiLive(CardContext.HERO)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CardPlayTarget(data.title) { RingPlay(p, 84.dp, traceKey = "hero:" + data.key, traceIndex = LocalCardIndex.current.coerceAtLeast(0)) }
                            HeroCta(data.sumiContinueSub(), progressLabel)
                        }
                    }
                    SumiStyle.PAPER -> Unit
                }
            }
        }
    }
}

@Composable
private fun HeroFacts(data: TitleCardData, show: CardShow) {
    val facts = data.sumiFacts()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (facts != null) {
            Text(facts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val score = data.score
        if (score != null) SumiScore(score)
    }
}

@Composable
private fun RowScope.HeroCta(next: String?, progress: String?) {
    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (next != null) Text(next, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progress != null) Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
