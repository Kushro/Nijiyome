// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.nijiyome.designsystem.direction.RegionData
import app.nijiyome.designsystem.theme.parseHexColor
import app.nijiyome.feature.details.resources.Res
import app.nijiyome.feature.details.resources.details_fact_next_value
import app.nijiyome.feature.details.resources.details_next_short
import app.nijiyome.model.Media
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.displayTitle
import org.jetbrains.compose.resources.stringResource

/**
 * What the Ficha header knows, for directions that print it on the hero (`Modifier.decor(DecorRegion.HERO, data)`): the title, the
 * list status and its label, the next episode ("Ep. 9 en 2 d 5 h" while it counts down, otherwise the episode to play), the series
 * progress and the cover color.
 */
@Composable
internal fun rememberHeroRegion(media: Media, state: DetailsUiState): RegionData {
    val nowSec = state.clockSec ?: (System.currentTimeMillis() / 1000)
    val entry = state.listEntry
    val statusLabel = entry?.status?.let { stringResource(it.labelRes()) }
    val total = state.totalEpisodes
    val watched = remember(state.progress, state.listProgress, total) { watchedCount(state.progress, state.listProgress, total) }
    val airing = state.detailsOrNull?.nextAiring
    val left = airing?.let { countdown(it.airingAtEpochSec, nowSec) }
    val next = remember(state.media, state.episodes, state.progress, state.listEntry, state.details, nowSec) { state.nextUp(nowSec) }
    val episodeLabel = when {
        airing != null && left != null -> stringResource(Res.string.details_next_short, airing.episode, whenText(left))
        next.playable && next.episode > 0 && !next.movie -> stringResource(Res.string.details_fact_next_value, next.episode)
        else -> null
    }
    val title = media.displayTitle(state.titleLanguage)
    val progress = total?.takeIf { it > 0 && watched > 0 }?.let { (watched.toFloat() / it).coerceIn(0f, 1f) }
    val accent = remember(media.color) { parseHexColor(media.color) }
    return RegionData(
        title = title,
        statusLabel = statusLabel,
        status = entry?.status?.slot(),
        episodeLabel = episodeLabel,
        progress = progress,
        accent = accent,
    )
}
