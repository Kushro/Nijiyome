// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.feature.settings.ExtraStorage
import app.nijiyome.feature.settings.ExtraStorageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Ajustes › Datos on desktop (checklist system.storage-usage, Tatakai Next's `StorageUsageVm`): besides the caches and the torrents,
 * the web downloads folder in use ([webDownloads]: Ajustes › Descargas, or the default), the logs and the extensions.
 */
class DesktopExtraStorage(
    private val webDownloads: () -> File,
    private val logs: File,
    private val extensions: List<File>,
) : ExtraStorage {
    override suspend fun measure(): List<ExtraStorageItem> = withContext(Dispatchers.IO) {
        val web = webDownloads()
        listOfNotNull(
            folderBytes(web)?.let { ExtraStorageItem(ExtraStorageItem.Kind.WEB_DOWNLOADS, it, path = web.absolutePath) },
            folderBytes(logs)?.let { ExtraStorageItem(ExtraStorageItem.Kind.LOGS, it, path = logs.absolutePath) },
            extensions.mapNotNull(::folderBytes).takeIf { it.isNotEmpty() }?.sum()
                ?.let { ExtraStorageItem(ExtraStorageItem.Kind.EXTENSIONS, it) },
        )
    }

    companion object {
        /** Bytes of the regular files under [dir] (links are not followed); 0 for a folder not created yet, null when unreadable. */
        fun folderBytes(dir: File): Long? {
            if (!dir.exists()) return 0L
            var total = 0L
            return try {
                Files.walkFileTree(dir.toPath(), object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (attrs.isRegularFile) total += attrs.size()
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
                })
                total
            } catch (_: IOException) {
                null
            }
        }
    }
}

/** The web downloads folder in use: Ajustes › Descargas (a path or a `file:` URI) or [default]. */
fun webDownloadsFolder(chosen: String?, default: File): File = chosen?.takeIf { it.isNotBlank() }?.let { value ->
    if (value.startsWith("file:")) runCatching { File(java.net.URI(value)) }.getOrNull() else File(value)
} ?: default
