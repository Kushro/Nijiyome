// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import app.nijiyome.feature.details.resources.Res
import app.nijiyome.feature.details.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.model.Episode
import app.nijiyome.model.HistoryEntry
import app.nijiyome.ui.format.relativeAirDay
import app.nijiyome.ui.look.pressScale
import app.nijiyome.ui.media.progressFraction
import coil3.compose.AsyncImage
import java.util.Locale
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// 2026-11 UI refresh: shared pieces of the Details › Episodios rows (desktop episodes.rs): the rating label, the meta line and the
// thumbnail. Without a thumbnail the series art shows dimmed with the number, never an empty box.

/** "8.03" -> "8.0" (one decimal, the locale's separator). */
internal fun ratingLabel(rating: Double?, locale: Locale = Locale.getDefault()): String? =
    rating?.takeIf { it > 0 }?.let { String.format(locale, "%.1f", it) }

/** Meta line of the list / card views: relative date · length (· rating when there is no thumbnail badge for it). */
@Composable
internal fun episodeMeta(episode: Episode, withRating: Boolean): String? {
    val parts = buildList {
        relativeAirDay(episode.airDate)?.let { add(it.replaceFirstChar { c -> c.titlecase() }) }
        episode.runtimeMin?.let { add(stringResource(UiR.string.ui_minutes_short, it)) }
        if (withRating) ratingLabel(episode.rating)?.let { add("★ $it") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Thumbnail, or the series art dimmed with the episode number on top. [numbered] = false when the row already shows the number (its
 * mark and "N. Title"): no third copy over the art.
 */
@Composable
internal fun EpisodeThumb(episode: Episode, fallbackArt: String?, modifier: Modifier = Modifier, numbered: Boolean = true) {
    Box(modifier.aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val image = episode.image?.takeIf { it.isNotBlank() }
        if (image != null) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            if (!fallbackArt.isNullOrBlank()) {
                AsyncImage(
                    model = fallbackArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.35f),
                )
            }
            if (numbered) {
                Text(
                    episode.number.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
