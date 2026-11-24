// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.media

import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.model.Media

// 2026-11 UI refresh: facts shared by the detailed cards and the Home spotlight (status dot, format, "2024 · 12 ep").

/** Status of a provider status string, for the dot / chip colors (desktop: blue airing, orange finished, violet upcoming). */
enum class MediaStatusKind { AIRING, FINISHED, UPCOMING, CANCELLED, HIATUS }

fun mediaStatusKind(status: String?): MediaStatusKind? = when (status?.trim()?.uppercase()) {
    "RELEASING" -> MediaStatusKind.AIRING
    "FINISHED" -> MediaStatusKind.FINISHED
    "NOT_YET_RELEASED" -> MediaStatusKind.UPCOMING
    "CANCELLED" -> MediaStatusKind.CANCELLED
    "HIATUS" -> MediaStatusKind.HIATUS
    else -> null
}

/** Dot color: fixed hues readable on light and dark surfaces (same meaning as the desktop dots). */
fun MediaStatusKind.dotColor(): Color = when (this) {
    MediaStatusKind.AIRING -> Color(0xFF4C9BFF)
    MediaStatusKind.FINISHED -> Color(0xFFFF9B42)
    MediaStatusKind.UPCOMING -> Color(0xFFB089FF)
    MediaStatusKind.CANCELLED -> Color(0xFFE5484D)
    MediaStatusKind.HIATUS -> Color(0xFF9A9A9A)
}

@Composable
fun MediaStatusKind.label(): String = stringResource(
    when (this) {
        MediaStatusKind.AIRING -> Res.string.ui_status_airing
        MediaStatusKind.FINISHED -> Res.string.ui_status_finished
        MediaStatusKind.UPCOMING -> Res.string.ui_status_upcoming
        MediaStatusKind.CANCELLED -> Res.string.ui_status_cancelled
        MediaStatusKind.HIATUS -> Res.string.ui_status_hiatus
    },
)

/** Localised AniList format (`TV`, `MOVIE`, `TV_SHORT`...); the raw value for an unknown one, null when missing. */
@Composable
fun mediaFormatLabel(format: String?): String? {
    val f = format?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val res = when (f) {
        "TV" -> Res.string.ui_format_tv
        "TV_SHORT" -> Res.string.ui_format_tv_short
        "MOVIE" -> Res.string.ui_format_movie
        "SPECIAL" -> Res.string.ui_format_special
        "OVA" -> Res.string.ui_format_ova
        "ONA" -> Res.string.ui_format_ona
        "MUSIC" -> Res.string.ui_format_music
        "MANGA" -> Res.string.ui_format_manga
        "NOVEL" -> Res.string.ui_format_novel
        "ONE_SHOT" -> Res.string.ui_format_one_shot
        else -> return f.replace('_', ' ')
    }
    return stringResource(res)
}

/** Classic card line: "2024 · 12 ep" (year and/or episode count); null when there is neither. */
@Composable
fun classicFactsLine(media: Media): String? {
    val parts = buildList {
        media.year?.let { add(it.toString()) }
        media.episodes?.takeIf { it > 0 }?.let { add(pluralStringResource(Res.plurals.ui_episodes_short, it, it)) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Detailed card line: "2024 · TV"; null when there is neither. */
@Composable
fun detailedFactsLine(media: Media): String? {
    val parts = listOfNotNull(media.year?.toString(), mediaFormatLabel(media.format))
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
