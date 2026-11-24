// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import java.io.File

/**
 * Downloaded chapter texts (checklist novel.download.chapters): one entry per chapter with its id, media id, the `NovelChapterText`
 * JSON and its size, in the file `storeDir/novel.db`. Android: a private SQLite file opened with the platform `SQLiteDatabase`.
 */
interface NovelTextStore {
    /** Stores [body] under [id]; returns its size in bytes. */
    suspend fun put(id: String, mediaId: String, body: String, createdAtMs: Long): Long

    suspend fun get(id: String): String?

    suspend fun delete(id: String)

    /** Closes and deletes the store ("Restablecer la app"). */
    suspend fun clear()

    companion object {
        const val FILE_NAME: String = "novel.db"
    }
}

/** The platform store of downloaded chapter texts in [file]. */
internal expect fun novelTextStore(file: File): NovelTextStore
