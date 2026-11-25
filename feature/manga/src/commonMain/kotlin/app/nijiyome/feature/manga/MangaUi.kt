// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.model.MatureContentMode
import app.nijiyome.model.MediaSort
import app.nijiyome.ui.state.userMessage
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// Labels and dialogs shared by the manga screens (AniList status / origin / sort / format words, mature warning, error block).

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
