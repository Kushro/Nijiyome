// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.model.CardSize
import app.nijiyome.model.CardStyle
import app.nijiyome.model.Density
import app.nijiyome.model.TitleCardIds
import app.nijiyome.ui.look.CardSizing
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.cardWidth
import app.nijiyome.ui.look.pressScale
import app.nijiyome.ui.media.MaterialPosterCard
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.dotColor
import app.nijiyome.upscale.UpscaleImageKind

// Sparkles redesign. Owner: integrator. The reference implementation of TitleCardStyle: the three previous card styles of
// Diseño y estilo (classic, detailed, poster only). ROW, GRID and WISH keep the poster card unchanged; the contexts where a
// poster at full width made no sense draw their own Material form: COMPACT a list row, HERO a 16:9 banner, and CONTINUE (classic
// and detailed) the wide resume card of the artifact (poster, title, episode, progress, play).
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
        when {
            context == CardContext.COMPACT -> MaterialCompactRow(data, show, onClick, onLongClick, modifier.fillMaxWidth())
            context == CardContext.HERO -> MaterialBanner(data, show, onClick, onLongClick, modifier.fillMaxWidth())
            // CAN-02: every Material card style (including legacy MINIMAL) uses the wide resume card in CONTINUE.
            context == CardContext.CONTINUE -> MaterialContinue(data, size, show, onClick, onLongClick, modifier)
            else -> {
                val sized = when (context) {
                    CardContext.ROW, CardContext.CONTINUE, CardContext.WISH -> modifier.width(cardWidth())
                    else -> modifier.fillMaxWidth()
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
        }
    }

    companion object {
        val Classic = MaterialCard(TitleCardIds.MATERIAL_CLASSIC, CardStyle.CLASSIC)
        val Detailed = MaterialCard(TitleCardIds.MATERIAL_DETAILED, CardStyle.DETAILED)
        val Minimal = MaterialCard(TitleCardIds.MATERIAL_MINIMAL, CardStyle.MINIMAL)
    }
}

/** Width of the wide resume card per size (artifact 300 px at the medium size); compact density narrows it like the posters. */
@Composable
private fun continueCardWidth(size: CardSize): Dp {
    val base = when (size) {
        CardSize.SMALL -> 260.dp
        CardSize.MEDIUM -> 300.dp
        CardSize.LARGE -> 340.dp
        CardSize.XL -> 380.dp
    }
    return if (LocalLook.current.density == Density.COMPACT) base * CardSizing.COMPACT_FACTOR else base
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.materialCardClick(onClick: () -> Unit, onLongClick: (() -> Unit)?, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressScale(interaction)
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** The wide resume card: poster, title, where to resume, the progress bar and the play button (artifact `.nj-continue`). */
@Composable
private fun MaterialContinue(
    data: TitleCardData,
    size: CardSize,
    show: CardShow,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val sub = (if (show.next) data.nextLabel else null) ?: data.subtitle
    val progress = data.progress?.takeIf { show.progress }
    Row(
        modifier
            .width(continueCardWidth(size))
            .materialCardClick(onClick, onLongClick, shape)
            .background(cs.surfaceContainerHigh)
            .heightIn(min = 96.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaCover(data.coverUrl, Modifier.width(56.dp), shape = MaterialTheme.shapes.small)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(data.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null) {
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        CardPlayTarget(data.title) {
            Box(Modifier.size(40.dp).background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** A dense list row: small poster, title and one line (artifact `.nj-card[data-context="compact"]`). */
@Composable
private fun MaterialCompactRow(data: TitleCardData, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val sub = (if (show.next) data.nextLabel else null) ?: data.subtitle
    Row(
        modifier
            .materialCardClick(onClick, onLongClick, MaterialTheme.shapes.small)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaCover(data.coverUrl, Modifier.width(44.dp), shape = MaterialTheme.shapes.extraSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(data.title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val dot = if (show.status) data.airing?.dotColor() else null
        if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
    }
}

/** A 16:9 banner (the title's banner, else its cover) with the title over a bottom scrim, and the play button when there is one. */
@Composable
private fun MaterialBanner(data: TitleCardData, show: CardShow, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val shape = MaterialTheme.shapes.large
    val sub = (if (show.next) data.nextLabel else null) ?: data.subtitle
    Box(modifier.materialCardClick(onClick, onLongClick, shape), contentAlignment = Alignment.BottomStart) {
        MediaCover(
            data.bannerUrl ?: data.coverUrl,
            Modifier.fillMaxWidth(),
            shape = shape,
            aspectRatio = 16f / 9f,
            kind = if (data.bannerUrl != null) UpscaleImageKind.BANNER else UpscaleImageKind.POSTER,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(data.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (LocalCardPlayAction.current != null) {
                val cs = MaterialTheme.colorScheme
                CardPlayTarget(data.title) {
                    Box(Modifier.size(48.dp).background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = cs.onPrimary)
                    }
                }
            }
        }
    }
}
