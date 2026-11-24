// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.discover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal val DiscoverJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Tiny atomic JSON file (write to `.tmp`, then replace) for the private stores of :core:discover. Corrupt / missing = [default].
 * Reads and writes of one store never interleave (a reader never sees the moment between "old file removed" and "new file in
 * place"), and the replace is a single move: `File.renameTo` fails on Windows when the target exists.
 */
internal class JsonFileStore<T>(private val file: File, private val serializer: KSerializer<T>, private val default: () -> T) {
    private val gate = Any()

    fun readBlocking(): T = synchronized(gate) {
        try {
            if (file.isFile) DiscoverJson.decodeFromString(serializer, file.readText()) else default()
        } catch (_: Exception) {
            default()
        }
    }

    suspend fun read(): T = withContext(Dispatchers.IO) { readBlocking() }

    suspend fun write(value: T): Unit = withContext(Dispatchers.IO) {
        val text = DiscoverJson.encodeToString(serializer, value)
        synchronized(gate) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(text)
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        Unit
    }
}
