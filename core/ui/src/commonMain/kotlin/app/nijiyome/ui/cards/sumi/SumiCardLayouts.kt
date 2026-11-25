// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.sumi.SumiInkLine
import app.nijiyome.model.CardSize
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardPlayTarget
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.look.CardSizing

// The layouts of the Sumi styles per context. Poster cards (row, grid, wish) and "continue" are each style's own; the wide
// row card (LARGE / XL in a row), the compact list row and the hero share one layout with the style's art.

private fun sealSize(size: CardSize): Dp = when (size) {
    CardSize.SMALL -> 20.dp
    CardSize.MEDIUM -> 26.dp
    CardSize.LARGE, CardSize.XL -> 34.dp
}

/** Width rule of a poster card: rows and wishes own their width, a grid cell fills what it is given. */
@Composable
private fun posterWidth(context: CardContext, size: CardSize): Modifier =
    if (context == CardContext.GRID) Modifier.fillMaxWidth() else Modifier.width(sumiPosterWidth(size))

/** The art of a style at [width] (or filling) in the shared layouts. */
@Composable
private fun StyleArt(style: SumiStyle, data: TitleCardData, show: CardShow, context: CardContext, modifier: Modifier, small: Boolean) {
    val status = data.listStatus?.takeIf { show.status }
    when (style) {
        SumiStyle.PAPER -> PaperMat(modifier, if (small) 3.dp else 4.dp) {
            SumiPoster(data, Modifier.fillMaxWidth(), RoundedCornerShape(2.dp), hairline = false)
        }
        SumiStyle.HANKO -> Box(modifier) {
            SumiPoster(data, Modifier.fillMaxWidth(), if (small) SumiRadiusXs else SumiRadiusS, hairline = !small)
            if (status != null && !small) CornerSeal(status, 20.dp, modifier = Modifier.align(Alignment.TopEnd), inset = 4.dp)
        }
        SumiStyle.ENSO -> Box(modifier) {
            SumiPoster(data, Modifier.fillMaxWidth(), if (small) SumiRadiusXs else SumiRadiusM, hairline = false)
            val p = data.sumiProgress()
            if (!small && p != null && data.sumiLive(context) && show.progress) {
                RingBadge(p, Modifier.align(Alignment.BottomStart), size = 36.dp, traceKey = data.key)
            }
        }
    }
}

// ---- Hanko ----

@Composable
internal fun HankoPoster(data: TitleCardData, context: CardContext, size: CardSize, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val status = data.listStatus?.takeIf { show.status }
    val p = data.sumiProgress()?.takeIf { show.progress }
    Column(
        modifier.then(posterWidth(context, size)).sumiCardClick(SumiRadiusS, onClick, onLongClick, status = status?.let { data.listStatusLabel }),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            SumiPoster(data, Modifier.fillMaxWidth(), SumiRadiusS)
            if (status != null) {
                CornerSeal(status, sealSize(size), brush = size == CardSize.LARGE || size == CardSize.XL, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        if (p != null) SumiInkLine(p)
        CardTitle(data.title, size, Modifier.padding(horizontal = 2.dp))
        CardSub(data.sumiSub(context, show), context, Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp))
    }
}

/** Hanko "continue": a surface-2 row with the poster and its small seal, the episode, the ink line and the play disc. */
@Composable
internal fun HankoContinue(data: TitleCardData, size: CardSize, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val status = data.listStatus?.takeIf { show.status }
    val p = data.sumiProgress()?.takeIf { show.progress }
    val poster = when (size) {
        CardSize.SMALL, CardSize.MEDIUM -> 64.dp
        CardSize.LARGE -> 78.dp
        CardSize.XL -> 86.dp
    }
    Row(
        modifier
            .width(sumiContinueWidth(size, 300.dp))
            .sumiCardClick(SumiRadiusM, onClick, onLongClick, status = status?.let { data.listStatusLabel })
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SumiRadiusM)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(poster)) {
            SumiPoster(data, Modifier.fillMaxWidth(), SumiRadiusS)
            if (status != null) CornerSeal(status, 20.dp, modifier = Modifier.align(Alignment.TopEnd), inset = 4.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(data.title, CardSize.MEDIUM)
            CardSub(data.sumiContinueSub(), CardContext.CONTINUE)
            if (p != null) SumiInkLine(p, Modifier.padding(top = 4.dp))
        }
        CardPlayTarget(data.title) { PlayDisc(44.dp) }
    }
}

// ---- Ensō ----

@Composable
internal fun EnsoPoster(data: TitleCardData, context: CardContext, size: CardSize, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val status = data.listStatus?.takeIf { show.status }
    val p = data.sumiProgress()
    val live = p != null && data.sumiLive(context)
    val badge = live && (show.progress || context != CardContext.GRID)
    Column(
        modifier.then(posterWidth(context, size)).sumiCardClick(SumiRadiusM, onClick, onLongClick, status = status?.let { data.listStatusLabel }),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            SumiPoster(data, Modifier.fillMaxWidth(), SumiRadiusM, hairline = false)
            if (status != null) CornerSeal(status, 20.dp, round = true, modifier = Modifier.align(Alignment.TopEnd))
            if (badge && p != null) {
                RingBadge(p, Modifier.align(Alignment.BottomStart), size = if (size == CardSize.SMALL) 34.dp else 40.dp, traceKey = data.key)
            }
        }
        CardTitle(data.title, size, Modifier.padding(horizontal = 2.dp))
        val sub = if (live && context != CardContext.WISH) data.sumiContinueSub() else data.sumiSub(context, show)
        CardSub(sub, context, Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp))
    }
}

/** Ensō "continue": a 16:9 frame of the title, and below it the episode with the ensō traced around play. */
@Composable
internal fun EnsoContinue(data: TitleCardData, size: CardSize, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val p = data.sumiProgress()?.takeIf { show.progress }
    Column(
        modifier
            .width(sumiContinueWidth(size, 268.dp))
            .sumiCardClick(SumiRadiusM, onClick, onLongClick, status = data.listStatus?.takeIf { show.status }?.let { data.listStatusLabel })
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SumiPoster(data, Modifier.fillMaxWidth(), SumiRadiusM, hairline = false, aspectRatio = 16f / 9f, url = data.bannerUrl ?: data.coverUrl)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CardTitle(data.title, CardSize.MEDIUM, maxLines = 1)
                CardSub(data.sumiContinueSub(), CardContext.CONTINUE)
            }
            CardPlayTarget(data.title) {
                if (p != null) RingPlay(p, 58.dp, traceKey = "continue:" + data.key, traceIndex = 0) else PlayDisc(42.dp)
            }
        }
    }
}

// ---- Sumi papel ----

@Composable
internal fun PaperPoster(data: TitleCardData, context: CardContext, size: CardSize, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val status = data.listStatus?.takeIf { show.status }
    val p = data.sumiProgress()?.takeIf { show.progress }
    val pad = when (size) {
        CardSize.SMALL -> 4.dp
        CardSize.MEDIUM -> 6.dp
        CardSize.LARGE, CardSize.XL -> 9.dp
    }
    Column(
        modifier.then(posterWidth(context, size)).sumiCardClick(SumiRadiusS, onClick, onLongClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PaperMat(Modifier.fillMaxWidth(), pad) { SumiPoster(data, Modifier.fillMaxWidth(), RoundedCornerShape(2.dp), hairline = false) }
        CardTitle(data.title, size, Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp), paper = true)
        CardSub(data.sumiSub(context, show), context, Modifier.padding(horizontal = 2.dp))
        if (p != null) SumiInkLine(p, Modifier.padding(horizontal = 2.dp))
        val score = data.score?.takeIf { size != CardSize.SMALL }
        if (status != null || score != null) {
            Row(
                Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (status != null) SealTag(status, data.listStatusLabel, Modifier.weight(1f, fill = false))
                if (score != null) SumiScore(score)
            }
        }
    }
}

/** Sumi papel "continue": the matted poster, the episode with its ink line and an ink-outlined play disc. */
@Composable
internal fun PaperContinue(data: TitleCardData, size: CardSize, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val p = data.sumiProgress()?.takeIf { show.progress }
    val poster = when (size) {
        CardSize.SMALL -> 56.dp
        CardSize.MEDIUM -> 70.dp
        CardSize.LARGE -> 86.dp
        CardSize.XL -> 96.dp
    }
    Row(
        modifier
            .width(sumiContinueWidth(size, 310.dp))
            .sumiCardClick(SumiRadiusS, onClick, onLongClick, status = data.listStatus?.takeIf { show.status }?.let { data.listStatusLabel }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PaperMat(Modifier.width(poster), 4.dp) { SumiPoster(data, Modifier.fillMaxWidth(), RoundedCornerShape(2.dp), hairline = false) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(data.title, CardSize.MEDIUM, paper = true)
            CardSub(data.sumiContinueSub(), CardContext.CONTINUE)
            if (p != null) SumiInkLine(p, Modifier.padding(top = 6.dp))
        }
        CardPlayTarget(data.title, Modifier.padding(end = 4.dp)) { PlayDisc(44.dp, ink = true) }
    }
}

// ---- Shared: wide row card, compact row ----

/** LARGE / XL in a row or wish list: the app's wide card (poster beside its facts), in the style's art. */
@Composable
internal fun SumiWide(style: SumiStyle, data: TitleCardData, context: CardContext, size: CardSize, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val status = data.listStatus?.takeIf { show.status }
    val p = data.sumiProgress()?.takeIf { show.progress }
    val paper = style == SumiStyle.PAPER
    Row(
        modifier
            .width(sumiWideWidth(size))
            .sumiCardClick(SumiRadiusM, onClick, onLongClick)
            .then(
                if (paper) {
                    Modifier
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp, MaterialTheme.colorScheme.outlineVariant, SumiRadiusM)
                },
            )
            .padding(if (paper) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StyleArt(style, data, show, context, Modifier.width(CardSizing.widePosterDp(size).dp), small = false)
        Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTitle(data.title, CardSize.LARGE, paper = paper)
            val sub = if (style == SumiStyle.ENSO && data.sumiLive(context) && context != CardContext.WISH) data.sumiContinueSub() else data.sumiSub(context, show)
            CardSub(sub, context, maxLines = 2)
            if (status != null) SealTag(status, data.listStatusLabel)
            data.score?.let { SumiScore(it) }
            if (p != null) SumiInkLine(p, Modifier.padding(top = 2.dp))
        }
    }
}

/** A dense list row (search, lists): small poster, title and line, and the seal tag or, for Ensō in progress, a mini ring. */
@Composable
internal fun SumiCompact(style: SumiStyle, data: TitleCardData, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val status = data.listStatus?.takeIf { show.status }
    val p = data.sumiProgress()
    val live = style == SumiStyle.ENSO && p != null && data.sumiLive(CardContext.COMPACT)
    val progressLabel = if (live) data.sumiProgressLabel() else null
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .sumiCardClick(SumiRadiusS, onClick, onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StyleArt(style, data, show, CardContext.COMPACT, Modifier.width(if (style == SumiStyle.PAPER) 48.dp else 44.dp), small = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(data.title, CardSize.MEDIUM, maxLines = 1, paper = style == SumiStyle.PAPER)
            CardSub(progressLabel ?: data.sumiSub(CardContext.COMPACT, show), CardContext.COMPACT)
        }
        when {
            live && p != null -> RingPlay(p, 40.dp, traceKey = "compact:" + data.key, disc = false)
            status != null -> SealTag(status, data.listStatusLabel)
        }
    }
}
