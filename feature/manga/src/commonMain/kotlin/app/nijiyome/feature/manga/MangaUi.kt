// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.model.LoadState
import app.nijiyome.model.MatureContentMode
import app.nijiyome.model.Media
import app.nijiyome.model.MediaSort
import app.nijiyome.model.TitleLanguage
import app.nijiyome.ui.media.COVER_ASPECT_RATIO
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.media.mediaSubtitle
import app.nijiyome.ui.state.userMessage
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// Small pieces shared by the manga screens (home rows, mature blur / warning, AniList labels).

internal val CARD_WIDTH = 120.dp

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Poster card; [blurred] hides the cover until tapped once (MatureContentMode.BLUR). */
@Composable
internal fun MangaCard(
    media: Media,
    titleLanguage: TitleLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blurred: Boolean = false,
    subtitle: String? = mediaSubtitle(media),
) {
    var revealed by remember(media.ref) { mutableStateOf(false) }
    val hide = blurred && !revealed
    Column(modifier.width(CARD_WIDTH).clickable { if (hide) revealed = true else onClick() }) {
        Box {
            MediaCover(url = media.cover, modifier = Modifier.fillMaxWidth().then(if (hide) Modifier.blur(24.dp) else Modifier))
            if (hide) {
                Text(
                    stringResource(Res.string.manga_mature_tap),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
        }
        Text(media.displayTitle(titleLanguage), style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** One horizontal row of a [LoadState] (skeleton after 150 ms, compact error with retry, hidden when empty). */
@Composable
internal fun MediaRowSection(
    title: String,
    state: LoadState<List<Media>>,
    titleLanguage: TitleLanguage,
    onRetry: () -> Unit,
    onClick: (Media) -> Unit,
    blurred: (Media) -> Boolean = { false },
) {
    when (state) {
        LoadState.Loading -> {
            SectionHeader(title)
            if (rememberAfterDelay()) SkeletonRow()
        }
        is LoadState.Err -> {
            SectionHeader(title)
            ErrorState(
                message = state.cause.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = onRetry,
                compact = true,
            )
        }
        is LoadState.Ok -> if (state.value.isNotEmpty()) {
            SectionHeader(title)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.value, key = { it.ref.anilistId ?: it.title.hashCode() }) { media ->
                    MangaCard(media, titleLanguage, onClick = { onClick(media) }, blurred = blurred(media))
                }
            }
        }
    }
}

@Composable
internal fun SkeletonRow() {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            SkeletonBox(Modifier.width(CARD_WIDTH).height(CARD_WIDTH / COVER_ASPECT_RATIO))
        }
    }
}

/** WARN mode: one confirmation per title before opening it (classic mature dialog). */
@Composable
internal fun MatureWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.manga_mature_title)) },
        text = { Text(stringResource(Res.string.manga_mature_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.manga_mature_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

internal fun MatureContentMode.blurs(): Boolean = this == MatureContentMode.BLUR

@Composable
internal fun anilistStatusLabel(status: String?): String? = when (status?.uppercase()) {
    "RELEASING" -> stringResource(Res.string.manga_status_releasing)
    "FINISHED" -> stringResource(Res.string.manga_status_finished)
    "HIATUS" -> stringResource(Res.string.manga_status_hiatus)
    "CANCELLED" -> stringResource(Res.string.manga_status_cancelled)
    "NOT_YET_RELEASED" -> stringResource(Res.string.manga_status_upcoming)
    null -> null
    else -> status
}

@Composable
internal fun originLabel(origin: String?): String? = when (origin?.uppercase()) {
    "JP" -> stringResource(Res.string.manga_origin_jp)
    "KR" -> stringResource(Res.string.manga_origin_kr)
    "CN" -> stringResource(Res.string.manga_origin_cn)
    "TW" -> stringResource(Res.string.manga_origin_tw)
    null -> null
    else -> origin
}

@Composable
internal fun sortLabel(sort: MediaSort): String = stringResource(
    when (sort) {
        MediaSort.POPULARITY -> Res.string.manga_sort_popularity
        MediaSort.TRENDING -> Res.string.manga_sort_trending
        MediaSort.SCORE -> Res.string.manga_sort_score
        MediaSort.START_DATE -> Res.string.manga_sort_newest
        MediaSort.TITLE -> Res.string.manga_sort_title
        MediaSort.FAVOURITES -> Res.string.manga_sort_favourites
        MediaSort.EPISODES -> Res.string.manga_sort_chapters
        MediaSort.RELEVANCE -> Res.string.manga_sort_relevance
    },
)

@Composable
internal fun formatLabel(format: String?): String? = when (format?.uppercase()) {
    "MANGA" -> stringResource(Res.string.manga_format_manga)
    "ONE_SHOT" -> stringResource(Res.string.manga_format_oneshot)
    "NOVEL" -> stringResource(Res.string.manga_format_novel)
    null -> null
    else -> format.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
internal fun ErrorBlock(cause: app.nijiyome.model.AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    ErrorState(
        message = cause.userMessage(),
        retryLabel = stringResource(UiR.string.ui_retry),
        onRetry = onRetry,
        modifier = modifier,
        title = stringResource(UiR.string.ui_error_title),
    )
}
