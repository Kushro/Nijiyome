// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import app.nijiyome.model.ContinueReadingItem
import app.nijiyome.model.ListProgress
import app.nijiyome.model.NovelProgress
import app.nijiyome.model.ReadEntry
import app.nijiyome.model.ReadPosition
import app.nijiyome.novel.NovelRepositoryImpl

/** A saved novel paragraph as a [ReadPosition]; null when the media id has no AniList / MAL id. */
internal fun NovelProgress.toPosition(): ReadPosition? {
    val ref = NovelRepositoryImpl.refOf(mediaId) ?: return null
    return ReadPosition(mediaId, ref, title, poster, chapterNumber, offset >= NovelRepositoryImpl.COMPLETED_OFFSET, updatedAtMs)
}

/** "Seguir leyendo" of novels: the saved positions merged with the readlist entries being read (ListProgress.mergeContinueReading). */
internal fun novelContinueReading(progress: List<NovelProgress>, list: List<ReadEntry>, limit: Int): List<ContinueReadingItem> =
    ListProgress.mergeContinueReading(progress.mapNotNull { it.toPosition() }, list, limit)
