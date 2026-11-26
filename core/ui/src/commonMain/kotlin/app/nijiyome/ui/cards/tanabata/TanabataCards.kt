// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards.tanabata

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.model.CardSize
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_tanabata_card_seal
import app.nijiyome.ui.resources.rd_tanabata_card_tanzaku
import app.nijiyome.ui.resources.rd_tanabata_card_thread
import app.nijiyome.ui.resources.rd_tanabata_card_vertical
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign. Owner: lane D1. The four card styles of the Tanabata direction (docs/redesign/artefacto/dir-tanabata.js,
// `registerCard`). They read only the theme and the Tanabata materials, so they also work mixed with any other direction.

/** Tanzaku: a tall washi strip hanging by a knotted thread; paper color = list status, ink runs down with progress. */
object TanzakuCard : TitleCardStyle {
    override val id: String = TitleCardIds.TANZAKU
    override val directionId: String = "tanabata"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_tanabata_card_tanzaku)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = StripCardBody(data, context, size, show, onClick, modifier, onLongClick)
}

/** Tanzaku sello: a clean poster with a short strip (status and episode) hanging from its top edge. */
object TanzakuSealCard : TitleCardStyle {
    override val id: String = TitleCardIds.TANZAKU_SEAL
    override val directionId: String = "tanabata"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_tanabata_card_seal)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = SealCardBody(data, context, size, show, onClick, modifier, onLongClick)
}

/** Tanzaku vertical: a narrow strip with the title written top to bottom, dense wish rows. */
object TanzakuVerticalCard : TitleCardStyle {
    override val id: String = TitleCardIds.TANZAKU_VERTICAL
    override val directionId: String = "tanabata"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_tanabata_card_vertical)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = VerticalCardBody(data, context, size, show, onClick, modifier, onLongClick)
}

/** Tanzaku hilo: the poster hangs from the thread by a paper bow in the status color; progress is a line of ink. */
object TanzakuThreadCard : TitleCardStyle {
    override val id: String = TitleCardIds.TANZAKU_THREAD
    override val directionId: String = "tanabata"

    @Composable
    override fun name(): String = stringResource(Res.string.rd_tanabata_card_thread)

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) = ThreadCardBody(data, context, size, show, onClick, modifier, onLongClick)
}
