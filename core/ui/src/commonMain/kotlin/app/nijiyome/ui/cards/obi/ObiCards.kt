// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.obi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_obi_card_band
import app.nijiyome.ui.resources.rd_obi_card_diagonal
import app.nijiyome.ui.resources.rd_obi_card_spine
import org.jetbrains.compose.resources.stringResource

// The three card styles of the Obi direction (lane D2, docs/redesign/artefacto/dir-obi.{css,js} registerCard). They work under any
// direction: they read the theme's roles and LocalStatusColors, and letter their bands with the Obi faces.

/** Obi · faja: poster with a band across its foot in the series color. The Obi direction's default card. */
object ObiCard : TitleCardStyle {
    override val id: String = TitleCardIds.OBI
    override val directionId: String = "obi"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_obi_card_band)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = ObiBandCardContent(data, context, size, show, onClick, modifier, onLongClick)
}

/** Obi · lomo: a bookshelf spine with the title running down it and a big number at the foot. */
object ObiSpineCard : TitleCardStyle {
    override val id: String = TitleCardIds.OBI_SPINE
    override val directionId: String = "obi"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_obi_card_spine)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = ObiSpineCardContent(data, context, size, show, onClick, modifier, onLongClick)
}

/** Obi · diagonal: a clean poster; only a novelty gets the band, across the top corner. */
object ObiDiagonalCard : TitleCardStyle {
    override val id: String = TitleCardIds.OBI_DIAGONAL
    override val directionId: String = "obi"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_obi_card_diagonal)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = ObiDiagonalCardContent(data, context, size, show, onClick, modifier, onLongClick)
}
