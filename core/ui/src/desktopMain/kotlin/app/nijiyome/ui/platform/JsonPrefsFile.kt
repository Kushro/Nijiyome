// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A small JSON object on disk for a screen's private preferences on desktop (Android keeps them in SharedPreferences). A write goes
 * to a temporary file that replaces the old one, so a crash never leaves half a file; a missing or corrupt file reads as empty.
 * Synchronous and thread-safe: the values are a few hundred bytes.
 */
class JsonPrefsFile(private val file: File) {
    @Synchronized
    fun read(): JsonObject = runCatching {
        if (file.isFile) Json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject else null
    }.getOrNull() ?: JsonObject(emptyMap())

    /** Replaces [key] (null removes it) and writes the file; false when it could not be written. */
    @Synchronized
    fun put(key: String, value: JsonElement?): Boolean = runCatching {
        val next = read().toMutableMap().apply { if (value == null) remove(key) else put(key, value) }
        file.absoluteFile.parentFile?.mkdirs()
        writeReplacing(file.absoluteFile, JsonObject(next).toString().toByteArray(Charsets.UTF_8))
    }.isSuccess
}

/**
 * Writes [bytes] to [target] through `<target>.tmp` and a rename, so [target] is either the old or the new content. Where the file
 * system cannot rename atomically (some FUSE, network or exFAT mounts) a plain replace is used, as `ExtState.writePrivate` does; the
 * temporary file never stays behind on a failure. Also the manga page capture's writer (v1.1 review #15).
 */
fun writeReplacing(target: File, bytes: ByteArray, move: (Path, Path, Array<CopyOption>) -> Unit = { a, b, o -> Files.move(a, b, *o) }) {
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
