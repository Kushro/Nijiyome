// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.transit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.MaterialCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle

// Sparkles redesign. Owner: lane D3. STUBS written by the integrator so the registry compiles: each object draws the Material
// card until the lane replaces its body with the style of docs/redesign/artefacto/dir-transit.{css,js} (registerCard). Keep the
// object names and ids; split into more files inside this folder as needed.

object StationCard : TitleCardStyle {
    override val id: String = TitleCardIds.TRANSIT_STATION
    override val directionId: String = "transit"

    @Composable
    override fun name(): String = "Estación"

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

object TicketCard : TitleCardStyle {
    override val id: String = TitleCardIds.TRANSIT_TICKET
    override val directionId: String = "transit"

    @Composable
    override fun name(): String = "Billete"

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

object LineCard : TitleCardStyle {
    override val id: String = TitleCardIds.TRANSIT_LINE
    override val directionId: String = "transit"

    @Composable
    override fun name(): String = "Línea"

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
