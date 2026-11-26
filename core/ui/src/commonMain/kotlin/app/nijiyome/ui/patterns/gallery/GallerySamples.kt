// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.patterns.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.media.MediaStatusKind
import app.nijiyome.ui.media.SampleCover
import app.nijiyome.ui.patterns.label
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_gal_sample_next
import app.nijiyome.ui.resources.rd_i1_gal_sample_synopsis
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane I1. Bundled sample titles of the design gallery (the artifact's own sample set). No artwork ships with the app:
// each title gets a generated poster in its own color (SampleCover), so every card style shows its treatment of an image. Proper names
// stay as they are; every sentence comes from resources.

private data class Sample(
    val key: String,
    val title: String,
    val year: Int,
    val format: String,
    val status: StatusSlot,
    val seen: Int,
    val total: Int,
    val next: Int?,
    val score: String,
    val accent: Long,
    val airing: MediaStatusKind,
    val genres: List<String>,
)

private val SAMPLES = listOf(
    Sample("anime:laidoff", "Laid-Off Mage", 2026, "TV", StatusSlot.WATCHING, 1, 12, 2, "7.4", 0xFF5B6CFF, MediaStatusKind.AIRING, listOf("Fantasy", "Comedy")),
    Sample("anime:tempal", "Tempal", 2026, "TV", StatusSlot.WATCHING, 3, 12, 4, "7.1", 0xFFE0565B, MediaStatusKind.AIRING, listOf("Action", "Comedy")),
    Sample("anime:aonohako", "Ao no Hako 2", 2025, "TV", StatusSlot.PLANNING, 0, 13, 9, "8.3", 0xFF3E9BD6, MediaStatusKind.AIRING, listOf("Romance", "Sports")),
    Sample("anime:koori", "Koori no Jouheki", 2025, "TV", StatusSlot.PAUSED, 7, 12, null, "7.9", 0xFF7FB7C9, MediaStatusKind.FINISHED, listOf("Romance", "Drama")),
    Sample("anime:psyren", "PSYREN", 2026, "TV", StatusSlot.COMPLETED, 24, 24, null, "7.6", 0xFFB04BD8, MediaStatusKind.FINISHED, listOf("Action", "Mystery")),
    Sample("anime:chitose", "Chitose-kun 2", 2026, "TV", StatusSlot.DROPPED, 6, 11, null, "6.8", 0xFFF0A35A, MediaStatusKind.UPCOMING, listOf("Romance", "Slice of Life")),
)

/**
 * The six sample cards of the gallery, one per list status, with progress, score, airing state and next episode filled in.
 *
 * ```kotlin
 * DesignGalleryScreen(onBack = nav::pop, cards = gallerySampleCards())
 * ```
 */
@Composable
fun gallerySampleCards(): List<TitleCardData> {
    val synopsis = stringResource(Res.string.rd_i1_gal_sample_synopsis)
    return SAMPLES.map { s ->
        TitleCardData(
            key = s.key,
            title = s.title,
            coverUrl = SampleCover.url(Color(s.accent), s.title),
            subtitle = "${s.year} · ${s.format}",
            progress = if (s.total > 0) s.seen / s.total.toFloat() else null,
            listStatus = s.status,
            listStatusLabel = s.status.label(),
            airing = s.airing,
            nextLabel = s.next?.let { stringResource(Res.string.rd_i1_gal_sample_next, it) },
            score = s.score,
            episodesSeen = s.seen,
            episodesTotal = s.total,
            bannerUrl = SampleCover.url(Color(s.accent), s.title),
            accent = Color(s.accent),
            format = s.format,
            year = s.year,
            genres = s.genres,
            synopsis = synopsis,
        )
    }
}
