// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Desktop: one file per chapter in the folder `novel-chapters/` next to [file] (Android keeps them in the SQLite file itself). */
internal actual fun novelTextStore(file: File): NovelTextStore = FileNovelTextStore(File(file.parentFile, "novel-chapters"))

/**
 * Downloaded chapter texts as files: `<sha-256 of the id>.json` holds the `NovelChapterText` JSON. A write goes to a temporary file
 * first and is moved in place, so a chapter is either whole or absent. The media id is not needed to read a chapter back.
 */
internal class FileNovelTextStore(private val dir: File) : NovelTextStore {
    override suspend fun put(id: String, mediaId: String, body: String, createdAtMs: Long): Long = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val bytes = body.toByteArray(Charsets.UTF_8)
        writeReplacing(fileOf(id), bytes)
        bytes.size.toLong()
    }

    override suspend fun get(id: String): String? = withContext(Dispatchers.IO) {
        fileOf(id).takeIf { it.isFile }?.readText(Charsets.UTF_8)
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { fileOf(id).delete() }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
    }

    private fun fileOf(id: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }
}

/**
 * Writes [bytes] to [target] through `<target>.tmp` and a rename, so [target] is either the old or the new content. Where the file
 * system cannot rename atomically (some FUSE, network or exFAT mounts) a plain replace is used, as `ExtState.writePrivate` does; the
 * temporary file never stays behind on a failure.
 */
internal fun writeReplacing(target: File, bytes: ByteArray, move: (Path, Path, Array<CopyOption>) -> Unit = { a, b, o -> Files.move(a, b, *o) }) {
    val tmp = File(target.parentFile, "${target.name}.tmp")
    try {
        tmp.writeBytes(bytes)
        try {
            move(tmp.toPath(), target.toPath(), arrayOf(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE))
        } catch (_: AtomicMoveNotSupportedException) {
            move(tmp.toPath(), target.toPath(), arrayOf(StandardCopyOption.REPLACE_EXISTING))
        }
    } finally {
        tmp.delete()
    }
}
