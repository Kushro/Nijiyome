// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.obi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.MaterialCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle

// Sparkles redesign. Owner: lane D2. STUBS written by the integrator so the registry compiles: each object draws the Material
// card until the lane replaces its body with the style of docs/redesign/artefacto/dir-obi.{css,js} (registerCard). Keep the
// object names and ids; split into more files inside this folder as needed.

object ObiCard : TitleCardStyle {
    override val id: String = TitleCardIds.OBI
    override val directionId: String = "obi"

    @Composable
    override fun name(): String = "Obi · faja"

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = MaterialCard.Detailed.Card(data, context, size, show, onClick, modifier, onLongClick)
}

object ObiSpineCard : TitleCardStyle {
    override val id: String = TitleCardIds.OBI_SPINE
    override val directionId: String = "obi"

    @Composable
    override fun name(): String = "Obi · lomo"

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = MaterialCard.Detailed.Card(data, context, size, show, onClick, modifier, onLongClick)
}

object ObiDiagonalCard : TitleCardStyle {
    override val id: String = TitleCardIds.OBI_DIAGONAL
    override val directionId: String = "obi"

    @Composable
    override fun name(): String = "Obi · diagonal"

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = MaterialCard.Detailed.Card(data, context, size, show, onClick, modifier, onLongClick)
}
