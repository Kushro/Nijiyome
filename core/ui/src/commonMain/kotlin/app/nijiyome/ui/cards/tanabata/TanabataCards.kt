// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.tanabata

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.MaterialCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle

// Sparkles redesign. Owner: lane D1. STUBS written by the integrator so the registry compiles: each object draws the Material
// card until the lane replaces its body with the style of docs/redesign/artefacto/dir-tanabata.{css,js} (registerCard). Keep the
// object names and ids; split into more files inside this folder as needed.

object TanzakuCard : TitleCardStyle {
    override val id: String = TitleCardIds.TANZAKU
    override val directionId: String = "tanabata"

    @Composable
    override fun name(): String = "Tanzaku"

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

object TanzakuSealCard : TitleCardStyle {
    override val id: String = TitleCardIds.TANZAKU_SEAL
    override val directionId: String = "tanabata"

    @Composable
    override fun name(): String = "Tanzaku sello"

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

object TanzakuVerticalCard : TitleCardStyle {
    override val id: String = TitleCardIds.TANZAKU_VERTICAL
    override val directionId: String = "tanabata"

    @Composable
    override fun name(): String = "Tanzaku vertical"

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

object TanzakuThreadCard : TitleCardStyle {
    override val id: String = TitleCardIds.TANZAKU_THREAD
    override val directionId: String = "tanabata"

    @Composable
    override fun name(): String = "Tanzaku hilo"

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
