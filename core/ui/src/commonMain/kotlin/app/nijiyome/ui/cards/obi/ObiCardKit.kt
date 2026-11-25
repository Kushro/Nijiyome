// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalFoundationApi::class)

package app.nijiyome.ui.cards.obi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.obi.ObiBand
import app.nijiyome.designsystem.direction.obi.ObiFonts
import app.nijiyome.designsystem.direction.obi.ObiInk
import app.nijiyome.model.CardSize
import app.nijiyome.model.Density
import app.nijiyome.model.ListStatus
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.labels.genreLabel
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.look.CardSizing
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.pressScale
import app.nijiyome.ui.media.MediaStatusKind
import app.nijiyome.ui.media.mediaFormatLabel
import app.nijiyome.ui.platform.windowWidthDp
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_obi_completed
import app.nijiyome.ui.resources.rd_obi_completed_eps
import app.nijiyome.ui.resources.rd_obi_continue_ep
import app.nijiyome.ui.resources.rd_obi_dropped_at
import app.nijiyome.ui.resources.rd_obi_ep_n
import app.nijiyome.ui.resources.rd_obi_eps
import app.nijiyome.ui.resources.rd_obi_not_in_list
import app.nijiyome.ui.resources.rd_obi_of
import app.nijiyome.ui.resources.rd_obi_paused_at
import app.nijiyome.ui.resources.rd_obi_premiere
import app.nijiyome.ui.resources.rd_obi_premiere_eps
import app.nijiyome.ui.resources.rd_obi_score
import app.nijiyome.ui.resources.rd_obi_watched_of
import org.jetbrains.compose.resources.stringResource

// Shared parts of the three Obi card styles (dir-obi.js): band colors by status, the band copy, sizes, the card frame (press,
// hover, focus ring, one semantics node) and the lettering helpers (condensed band text, vertical spine title).

/** The four card sizes as an index: s, m, l and an extra step for XL. */
internal val CardSize.step: Int
    get() = when (this) {
        CardSize.SMALL -> 0
        CardSize.MEDIUM -> 1
        CardSize.LARGE -> 2
        CardSize.XL -> 3
    }

internal fun <T> CardSize.pick(s: T, m: T, l: T, xl: T): T = when (step) {
    0 -> s
    1 -> m
    2 -> l
    else -> xl
}

/** Poster width of a row card: the app's small / medium widths, and taller posters for L / XL (artifact 196 px at L). */
@Composable
internal fun obiPosterWidth(size: CardSize): Dp {
    val compact = LocalLook.current.density == Density.COMPACT
    val dp = when (size) {
        CardSize.SMALL, CardSize.MEDIUM -> CardSizing.posterDp(size, compact)
        CardSize.LARGE -> CardSizing.MEDIUM_DP * 1.3f * (if (compact) CardSizing.COMPACT_FACTOR else 1f)
        CardSize.XL -> CardSizing.MEDIUM_DP * 1.63f * (if (compact) CardSizing.COMPACT_FACTOR else 1f)
    }
    return dp.dp
}

/** Width of a "Continuar" card: the app's wide-card width (artifact 280 / 320 / 380 px). */
@Composable
internal fun obiContinueWidth(size: CardSize): Dp {
    val viewport = windowWidthDp()
    val dp = when (size) {
        CardSize.SMALL -> minOf(280f, CardSizing.wideCardDp(CardSize.LARGE, viewport))
        CardSize.MEDIUM, CardSize.LARGE -> CardSizing.wideCardDp(CardSize.LARGE, viewport)
        CardSize.XL -> CardSizing.wideCardDp(CardSize.XL, viewport)
    }
    return dp.dp
}

internal fun StatusSlot.toListStatus(): ListStatus = when (this) {
    StatusSlot.WATCHING -> ListStatus.WATCHING
    StatusSlot.PLANNING -> ListStatus.PLANNING
    StatusSlot.COMPLETED -> ListStatus.COMPLETED
    StatusSlot.PAUSED -> ListStatus.PAUSED
    StatusSlot.DROPPED -> ListStatus.DROPPED
    StatusSlot.REWATCHING -> ListStatus.REPEATING
}

/** The status tab text: the screen's label, else the list label of the slot; null when the title is in no list. */
@Composable
internal fun obiStatusLabel(data: TitleCardData): String? =
    data.listStatusLabel ?: data.listStatus?.let { stringResource(it.toListStatus().labelRes()) }

/**
 * Band colors (artifact `tone()`): the series color while followed or planned, the completed status color when completed, faded
 * towards gray when paused, neutral paper when dropped. Without a cover color the status color stands in. Text is ink or paper,
 * whichever reads, and the background is pushed until the pair reaches 5:1.
 */
@Composable
internal fun obiTone(data: TitleCardData): ObiBand {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    return when (data.listStatus) {
        StatusSlot.COMPLETED -> ObiBand(status.completed, scheme.background)
        StatusSlot.DROPPED -> ObiBand(scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
        else -> {
            val base = data.accent ?: data.listStatus?.let { status.of(it) } ?: ObiInk.NEUTRAL
            val paused = data.listStatus == StatusSlot.PAUSED
            remember(base, paused) { ObiInk.bandFor(if (paused) ObiInk.mix(base, ObiInk.NEUTRAL, 0.55f) else base) }
        }
    }
}

/** Paper band of the wish list (artifact tone `paper`): the series color only as a small swatch. */
@Composable
internal fun obiPaper(): ObiBand = ObiBand(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)

/** The line of the moment on a band, long and short (artifact `phrase()`). */
internal data class ObiPhrase(val long: String, val short: String)

@Composable
internal fun obiPhrase(data: TitleCardData, context: CardContext, show: CardShow): ObiPhrase {
    val seen = data.episodesSeen
    val total = data.episodesTotal?.takeIf { it > 0 }
    val next = data.nextLabel?.takeIf { show.next || context == CardContext.CONTINUE }
    return when {
        data.listStatus == StatusSlot.COMPLETED -> ObiPhrase(
            total?.let { stringResource(Res.string.rd_obi_completed_eps, it) } ?: stringResource(Res.string.rd_obi_completed),
            total?.let { stringResource(Res.string.rd_obi_of, it, it) } ?: stringResource(Res.string.rd_obi_completed),
        )
        data.listStatus == StatusSlot.DROPPED && seen != null ->
            ObiPhrase(stringResource(Res.string.rd_obi_dropped_at, seen), stringResource(Res.string.rd_obi_ep_n, seen))
        data.listStatus == StatusSlot.PAUSED && seen != null ->
            ObiPhrase(stringResource(Res.string.rd_obi_paused_at, seen), stringResource(Res.string.rd_obi_ep_n, seen))
        next != null -> ObiPhrase(next, next)
        data.airing == MediaStatusKind.UPCOMING -> ObiPhrase(
            total?.let { stringResource(Res.string.rd_obi_premiere_eps, it) } ?: stringResource(Res.string.rd_obi_premiere),
            stringResource(Res.string.rd_obi_premiere),
        )
        context == CardContext.CONTINUE && seen != null -> ObiPhrase(
            stringResource(Res.string.rd_obi_continue_ep, seen + 1),
            stringResource(Res.string.rd_obi_ep_n, seen + 1),
        )
        data.listStatus == StatusSlot.PLANNING && total != null -> {
            val eps = stringResource(Res.string.rd_obi_eps, total)
            ObiPhrase(listOfNotNull(eps, mediaFormatLabel(data.format)).joinToString(" · "), eps)
        }
        seen != null && total != null -> stringResource(Res.string.rd_obi_of, seen, total).let { ObiPhrase(it, it) }
        else -> (data.subtitle ?: total?.let { stringResource(Res.string.rd_obi_eps, it) } ?: "").let { ObiPhrase(it, it) }
    }
}

/** Second band line (artifact `extra()`): watched count and score, or format · year · first genre. */
@Composable
internal fun obiExtra(data: TitleCardData): String {
    val seen = data.episodesSeen
    val total = data.episodesTotal?.takeIf { it > 0 }
    return if (seen != null && seen > 0 && total != null && data.listStatus != StatusSlot.PLANNING) {
        listOfNotNull(
            stringResource(Res.string.rd_obi_watched_of, seen, total),
            data.score?.let { stringResource(Res.string.rd_obi_score, it) },
        ).joinToString(" · ")
    } else {
        listOfNotNull(mediaFormatLabel(data.format), data.year?.toString(), data.genres.firstOrNull()?.let { genreLabel(it) })
            .joinToString(" · ")
            .ifEmpty { data.subtitle.orEmpty() }
    }
}

/** Facts under the title (artifact `subOf()`): format · year, else the screen's subtitle. */
@Composable
internal fun obiFacts(data: TitleCardData): String? =
    listOfNotNull(mediaFormatLabel(data.format), data.year?.toString()).joinToString(" · ").ifEmpty { null } ?: data.subtitle

/** What TalkBack reads for the whole card: title, status, the line of the moment and the facts. */
@Composable
internal fun obiSpoken(data: TitleCardData, phrase: ObiPhrase, extra: String, novelty: String? = null): String =
    listOfNotNull(
        data.title,
        novelty,
        obiStatusLabel(data) ?: stringResource(Res.string.rd_obi_not_in_list),
        phrase.long.ifBlank { null },
        extra.ifBlank { null },
    ).joinToString(". ")

/**
 * The card frame: press scale, hover, a 2 dp focus ring outside the card, click / long click and one semantics node that reads
 * [spoken]. The content's own semantics are cleared so the card is read once. [content] gets whether the card is raised (hovered or
 * focused), which lifts the band or the spine.
 */
@Composable
internal fun ObiFrame(
    spoken: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    shape: Shape,
    modifier: Modifier,
    content: @Composable (raised: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .drawWithContent {
                drawContent()
                if (focused) {
                    val o = 3.dp.toPx()
                    drawRoundRect(
                        color = ring,
                        topLeft = Offset(-o, -o),
                        size = Size(size.width + 2 * o, size.height + 2 * o),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            .pressScale(interaction)
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .hoverable(interaction)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Box(Modifier.clearAndSetSemantics { }) { content(hovered || focused) }
    }
}

/** Band lettering: Dela Gothic One compressed horizontally (長体), one line with an ellipsis unless [lines] allows more. */
@Composable
internal fun ObiCondText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    scaleX: Float = 0.86f,
    lines: Int = 1,
    textAlign: TextAlign? = null,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        maxLines = lines,
        softWrap = lines > 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        style = TextStyle(
            fontFamily = ObiFonts.band(),
            fontWeight = FontWeight.W400,
            fontSize = fontSize,
            lineHeight = fontSize * 1.15f,
            letterSpacing = 0.01.em,
            textGeometricTransform = ObiFonts.condensed(scaleX),
        ),
    )
}

/** UI lettering of the cards (M PLUS 1, bold by default), whatever the active direction. */
@Composable
internal fun obiUiStyle(fontSize: TextUnit, weight: FontWeight = FontWeight.W700): TextStyle =
    TextStyle(fontFamily = ObiFonts.ui(), fontWeight = weight, fontSize = fontSize, lineHeight = fontSize * 1.3f)

/** Square 3 dp progress strip in the band's own ink (artifact `.obi-band > .nj-progress`). */
@Composable
internal fun ObiBandProgress(progress: Float, color: Color, modifier: Modifier = Modifier, track: Color = color.copy(alpha = 0.22f)) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawRect(track)
        if (p > 0f) drawRect(color, size = Size(size.width * p, size.height))
    }
}

/**
 * A spine title (artifact `writing-mode: vertical-rl`): the text is laid out on one line along the available height and turned a
 * quarter clockwise, so it reads top to bottom, centered across the spine width. Compose has no vertical writing mode; CJK
 * glyphs turn with the line as well (the artifact keeps them upright).
 */
@Composable
internal fun ObiVerticalText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Layout(
        content = { Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
    ) { measurables, constraints ->
        val along = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val across = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val text0 = measurables.first().measure(Constraints(maxWidth = along, maxHeight = across))
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else text0.height
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else text0.width
        layout(w, h) {
            // Rotation keeps the center: place so that, once turned, the line starts at the top and sits centered across.
            text0.placeWithLayer(x = (w - text0.width) / 2, y = (text0.width - text0.height) / 2) { rotationZ = 90f }
        }
    }
}
