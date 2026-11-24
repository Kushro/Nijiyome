// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.manga.MangaRepositoryImpl
import app.nijiyome.model.ContinueReadingItem
import app.nijiyome.model.ListProgress
import app.nijiyome.model.ReadEntry
import app.nijiyome.model.ReadPosition
import app.nijiyome.model.ReadingProgress

/** A saved manga page as a [ReadPosition] for [ListProgress.mergeContinueReading]; null when the media id has no AniList / MAL id. */
internal fun ReadingProgress.toPosition(): ReadPosition? {
    val ref = MangaRepositoryImpl.refOf(mediaId) ?: return null
    return ReadPosition(mediaId, ref, title, poster, chapterNumber, completed, updatedAtMs)
}

/** "Seguir leyendo" of manga: the saved pages merged with the readlist entries being read (AniList-first list progress). */
internal fun mangaContinueReading(progress: List<ReadingProgress>, list: List<ReadEntry>, limit: Int): List<ContinueReadingItem> =
    ListProgress.mergeContinueReading(progress.mapNotNull { it.toPosition() }, list, limit)
