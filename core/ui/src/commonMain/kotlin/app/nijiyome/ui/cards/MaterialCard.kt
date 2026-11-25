// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.model.CardSize
import app.nijiyome.model.CardStyle
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.look.cardWidth
import app.nijiyome.ui.media.MaterialPosterCard
import app.nijiyome.ui.media.dotColor

// Sparkles redesign. Owner: integrator. The reference implementation of TitleCardStyle: the three previous card styles of
// Diseño y estilo (classic, detailed, poster only), unchanged. Copy its shape for a new style; a direction's style draws every
// context its own way (this one reuses the poster card for all six, which is the minimum that is correct).
class MaterialCard private constructor(override val id: String, private val style: CardStyle) : TitleCardStyle {
    override val directionId: String = "canon"

    @Composable
    override fun name(): String = when (style) {
        CardStyle.CLASSIC -> "Material"
        CardStyle.DETAILED -> "Material detailed"
        CardStyle.MINIMAL -> "Material poster"
    }

    @Composable
    override fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    ) {
        val sized = when (context) {
            CardContext.ROW, CardContext.CONTINUE, CardContext.WISH -> modifier.width(cardWidth())
            CardContext.GRID, CardContext.COMPACT, CardContext.HERO -> modifier.fillMaxWidth()
        }
        MaterialPosterCard(
            title = data.title,
            coverUrl = data.coverUrl,
            onClick = onClick,
            modifier = sized,
            subtitle = (if (show.next) data.nextLabel else null) ?: data.subtitle,
            progress = if (show.progress) data.progress else null,
            style = style,
            statusDot = if (show.status) data.airing?.dotColor() else null,
        )
    }

    companion object {
        val Classic = MaterialCard(TitleCardIds.MATERIAL_CLASSIC, CardStyle.CLASSIC)
        val Detailed = MaterialCard(TitleCardIds.MATERIAL_DETAILED, CardStyle.DETAILED)
        val Minimal = MaterialCard(TitleCardIds.MATERIAL_MINIMAL, CardStyle.MINIMAL)
    }
}
