// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaRef
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.toCardData
import app.nijiyome.ui.look.cardWidth
import app.nijiyome.ui.look.rowSpacing
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.COVER_ASPECT_RATIO
import app.nijiyome.ui.media.progressFraction
import app.nijiyome.ui.patterns.label
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane S2: what every Home structure shares to draw a title, a row and its states. Cards are always TitleCard (the
// style the user picked); this file only fills TitleCardData from Home's data.

/** A [Media] card with the user's list status, progress and the "new episode" flag on top of `Media.toCardData()`. */
@Composable
internal fun HomeDiscoverState.cardFor(
    media: Media,
    subtitle: String? = null,
    nextLabel: String? = null,
    aired: Int? = null,
    isNew: Boolean = false,
): TitleCardData {
    val base = media.toCardData()
    val entry = entryFor(media.ref)
    val total = base.episodesTotal ?: entry?.totalEpisodes
    val slot = entry?.status?.slot()
    return base.copy(
        subtitle = subtitle ?: base.subtitle,
        listStatus = slot,
        listStatusLabel = slot?.label(),
        episodesSeen = entry?.progress,
        episodesTotal = total,
        progress = if (entry != null && total != null && total > 0) (entry.progress.toFloat() / total).coerceIn(0f, 1f) else null,
        nextLabel = nextLabel,
        airedEpisodes = aired,
        isNew = isNew || (aired != null && entry != null && entry.progress < aired),
    )
}

/** The card of a history entry ("Continuar"): episode, minutes left and the playback progress of that episode. */
@Composable
internal fun HomeDiscoverState.cardFor(entry: HistoryEntry): TitleCardData {
    val ref = MediaRef(anilistId = entry.anilistId, malId = entry.malId)
    val list = entryFor(ref)
    val slot = list?.status?.slot()
    val left = entry.durationSec?.let { d -> ((d - entry.progressSec).coerceAtLeast(0) + 59) / 60 }
    val next = if (left != null && !entry.completed) {
        stringResource(Res.string.rd_s2_ep_left, entry.episode, left)
    } else {
        stringResource(Res.string.rd_s2_ep_short, entry.episode)
    }
    return TitleCardData(
        key = "anime:" + (entry.anilistId ?: entry.malId ?: entry.animeId),
        title = entry.title,
        coverUrl = entry.poster,
        subtitle = stringResource(Res.string.home_continue_episode, entry.episode),
        progress = entry.progressFraction()?.takeIf { !entry.completed },
        listStatus = slot,
        listStatusLabel = slot?.label(),
        nextLabel = next,
        episodesSeen = list?.progress,
        episodesTotal = list?.totalEpisodes,
    )
}

/**
 * Section of a Home structure: the direction's [SectionHeader] with "See all" (or [trailing]) and the row below. [onHide] shows the
 * hide button of the edit mode instead of the trailing actions.
 */
@Composable
internal fun HomeSection(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    seeAllLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hasTrailing = onHide != null || trailing != null || onSeeAll != null
        if (hasTrailing) {
            SectionHeader(title = title, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (onHide != null) {
                        HideRowButton(title, onHide)
                    } else {
                        trailing?.invoke()
                        if (onSeeAll != null) {
                            TextButton(onClick = onSeeAll, shape = NijiyomeControls.shape) { Text(seeAllLabel ?: stringResource(Res.string.home_see_all)) }
                        }
                    }
                }
            }
        } else {
            SectionHeader(title = title, modifier = Modifier.padding(horizontal = 16.dp))
        }
        content()
    }
}

/** A horizontal row of cards: the direction decorates the row itself (its rod hangs at y = 0), padding only on the sides. */
@Composable
internal fun HomeCardRow(modifier: Modifier = Modifier, region: DecorRegion = DecorRegion.ROW, content: LazyListScope.() -> Unit) {
    LazyRow(
        modifier = modifier.fillMaxWidth().decor(region),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(rowSpacing()),
        content = content,
    )
}

/** The body of a row that loads on its own: placeholders (after a short delay), compact error with retry, text when empty. */
@Composable
internal fun <T> RowBody(
    state: LoadState<List<T>>,
    onRetry: () -> Unit,
    emptyText: String? = null,
    content: @Composable (List<T>) -> Unit,
) {
    when (state) {
        LoadState.Loading -> RowPlaceholder()
        is LoadState.Err -> app.nijiyome.ui.state.AppErrorState(
            cause = state.cause,
            onRetry = onRetry,
            compact = true,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        is LoadState.Ok -> if (state.value.isEmpty()) {
            if (emptyText != null) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            content(state.value)
        }
    }
}

/** Loading row: card-sized placeholders of the "carga" moment, shown only when the answer is slow. */
@Composable
internal fun RowPlaceholder(count: Int = PLACEHOLDER_COUNT) {
    val width = cardWidth()
    val height = width / COVER_ASPECT_RATIO
    if (!rememberAfterDelay()) {
        Box(Modifier.fillMaxWidth().height(height))
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(rowSpacing()),
    ) {
        repeat(count) { LoadingPlaceholder(Modifier.width(width).height(height)) }
    }
}

/**
 * A card with a caption under it that is exactly as wide as the card (the card style owns its width), as in the artifact's
 * `.nj-a-slot`: "Ep. 9 salió a las 21:00" under the poster.
 */
@Composable
internal fun CardWithCaption(modifier: Modifier = Modifier, card: @Composable () -> Unit, caption: @Composable () -> Unit) {
    Layout(contents = listOf(card, caption), modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val cards = measurables[0].map { it.measure(loose) }
        val width = cards.maxOfOrNull { it.width } ?: 0
        val captions = measurables[1].map { it.measure(Constraints(minWidth = 0, maxWidth = width.coerceAtLeast(0), minHeight = 0)) }
        val cardHeight = cards.maxOfOrNull { it.height } ?: 0
        layout(width, cardHeight + captions.sumOf { it.height }) {
            cards.forEach { it.placeRelative(0, 0) }
            var y = cardHeight
            captions.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

/** Seconds since the epoch, refreshed every minute (airing states, "now" markers, countdowns of the agenda). */
@Composable
internal fun rememberNowSec(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
            now = System.currentTimeMillis() / 1000
        }
    }
    return now
}

private const val PLACEHOLDER_COUNT = 6
private const val MINUTE_MS = 60_000L
