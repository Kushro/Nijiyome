// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.sumi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.MaterialCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle

// Sparkles redesign. Owner: lane D4. STUBS written by the integrator so the registry compiles: each object draws the Material
// card until the lane replaces its body with the style of docs/redesign/artefacto/dir-sumi.{css,js} (registerCard). Keep the
// object names and ids; split into more files inside this folder as needed.

object HankoCard : TitleCardStyle {
    override val id: String = TitleCardIds.HANKO
    override val directionId: String = "sumi"

    @Composable
    override fun name(): String = "Hanko"

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

object EnsoCard : TitleCardStyle {
    override val id: String = TitleCardIds.ENSO
    override val directionId: String = "sumi"

    @Composable
    override fun name(): String = "Ensō"

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

object SumiPaperCard : TitleCardStyle {
    override val id: String = TitleCardIds.SUMI_PAPER
    override val directionId: String = "sumi"

    @Composable
    override fun name(): String = "Sumi papel"

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
