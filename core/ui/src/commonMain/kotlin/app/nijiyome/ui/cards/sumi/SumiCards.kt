// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.sumi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_sumi_card_enso
import app.nijiyome.ui.resources.rd_sumi_card_hanko
import app.nijiyome.ui.resources.rd_sumi_card_paper
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane D4: the three card styles of Sumi y hanko (docs/redesign/artefacto/dir-sumi.js `registerCard`). Each
// draws the six contexts and the four sizes; any of them works under any direction (the seal reads LocalStatusColors).

/** True for the contexts where LARGE / XL become the app's wide row card. */
private fun wide(context: CardContext, size: CardSize): Boolean =
    (context == CardContext.ROW || context == CardContext.WISH) && size.isWide

/** Hanko: a clean poster with the status seal stamped in its corner (見 watching, 予 planned, 完 completed, 休 paused, 止 dropped, 再 rewatching). */
object HankoCard : TitleCardStyle {
    override val id: String = TitleCardIds.HANKO
    override val directionId: String = "sumi"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_sumi_card_hanko)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = when {
        context == CardContext.HERO -> SumiHero(SumiStyle.HANKO, data, size, show, onClick, onLongClick, modifier)
        context == CardContext.CONTINUE -> HankoContinue(data, size, show, onClick, onLongClick, modifier)
        context == CardContext.COMPACT -> SumiCompact(SumiStyle.HANKO, data, show, onClick, onLongClick, modifier)
        wide(context, size) -> SumiWide(SumiStyle.HANKO, data, context, size, show, onClick, onLongClick, modifier)
        else -> HankoPoster(data, context, size, show, onClick, onLongClick, modifier)
    }
}

/** Ensō: for continuing, the ensō of the mark circles the play button and its stroke is the real progress. */
object EnsoCard : TitleCardStyle {
    override val id: String = TitleCardIds.ENSO
    override val directionId: String = "sumi"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_sumi_card_enso)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = when {
        context == CardContext.HERO -> SumiHero(SumiStyle.ENSO, data, size, show, onClick, onLongClick, modifier)
        context == CardContext.CONTINUE -> EnsoContinue(data, size, show, onClick, onLongClick, modifier)
        context == CardContext.COMPACT -> SumiCompact(SumiStyle.ENSO, data, show, onClick, onLongClick, modifier)
        wide(context, size) -> SumiWide(SumiStyle.ENSO, data, context, size, show, onClick, onLongClick, modifier)
        else -> EnsoPoster(data, context, size, show, onClick, onLongClick, modifier)
    }
}

/** Sumi papel: the poster in a paper mat, the title in ink below and the round seal with the status name. */
object SumiPaperCard : TitleCardStyle {
    override val id: String = TitleCardIds.SUMI_PAPER
    override val directionId: String = "sumi"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_sumi_card_paper)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = when {
        context == CardContext.HERO -> SumiHero(SumiStyle.PAPER, data, size, show, onClick, onLongClick, modifier)
        context == CardContext.CONTINUE -> PaperContinue(data, size, show, onClick, onLongClick, modifier)
        context == CardContext.COMPACT -> SumiCompact(SumiStyle.PAPER, data, show, onClick, onLongClick, modifier)
        wide(context, size) -> SumiWide(SumiStyle.PAPER, data, context, size, show, onClick, onLongClick, modifier)
        else -> PaperPoster(data, context, size, show, onClick, onLongClick, modifier)
    }
}
