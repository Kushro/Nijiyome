// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.AutoDownloadRule
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.LocalLibraryItem
import app.nijiyome.model.Media
import app.nijiyome.model.StreamSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

internal val DownloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * One web download as persisted by :core:download: the public [item] plus what is needed to resume, re-export or retry it after a
 * restart ([stream] with the extension headers, the HLS media playlist actually downloaded, the [media] for a retry that re-resolves).
 */
@Serializable
internal data class StoredDownload(
    val item: DownloadItem,
    val stream: StreamSource,
    /** URL handed to the Media3 `DownloadManager` (the chosen HLS media playlist, or the MP4 url). */
    val requestUrl: String,
    val media: Media? = null,
    /** 1.1 (F7): a debrid (TorBox) file: its link is renewed through `DebridRepository.refreshLink` whenever the download (re)starts. */
    val debrid: app.nijiyome.model.torrent.DebridFileRef? = null,
)

@Serializable
internal data class DownloadTarget(val id: String, val base: String)

/** Private JSON stores under `filesDir/downloads/` (atomic write: `.tmp` + rename). Missing or corrupt = empty. */
internal class DownloadStore(private val dir: File) {
    private fun <T> read(name: String, serializer: KSerializer<List<T>>): List<T> {
        val f = File(dir, name)
        return try {
            if (f.isFile) DownloadJson.decodeFromString(serializer, f.readText()) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun <T> write(name: String, serializer: KSerializer<List<T>>, value: List<T>) {
        dir.mkdirs()
        val f = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(DownloadJson.encodeToString(serializer, value))
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    fun downloads(): List<StoredDownload> = read(DOWNLOADS, ListSerializer(StoredDownload.serializer()))
    fun saveDownloads(v: List<StoredDownload>) = write(DOWNLOADS, ListSerializer(StoredDownload.serializer()), v)
    fun rules(): List<AutoDownloadRule> = read(RULES, ListSerializer(AutoDownloadRule.serializer()))
    fun saveRules(v: List<AutoDownloadRule>) = write(RULES, ListSerializer(AutoDownloadRule.serializer()), v)
    fun library(): List<LocalLibraryItem> = read(LIBRARY, ListSerializer(LocalLibraryItem.serializer()))
    fun saveLibrary(v: List<LocalLibraryItem>) = write(LIBRARY, ListSerializer(LocalLibraryItem.serializer()), v)

    /** Cleared history entries (ids hidden from Downloads › Historial; the files of completed ones stay). */
    fun hiddenHistory(): List<String> = read(HIDDEN, ListSerializer(String.serializer()))
    fun saveHiddenHistory(v: List<String>) = write(HIDDEN, ListSerializer(String.serializer()), v)

    // Desktop only (Android never reads nor writes these files).

    /** Where each desktop download writes (id -> `<folder>/<Title>/<Title> - 01` without extension), fixed when it was queued. */
    fun targets(): Map<String, String> = read(TARGETS, ListSerializer(DownloadTarget.serializer())).associate { it.id to it.base }
    fun saveTargets(v: Map<String, String>) = write(TARGETS, ListSerializer(DownloadTarget.serializer()), v.map { DownloadTarget(it.key, it.value) })

    /** Folders the desktop local library scanned and keeps watching. */
    fun libraryFolders(): List<String> = read(FOLDERS, ListSerializer(String.serializer()))
    fun saveLibraryFolders(v: List<String>) = write(FOLDERS, ListSerializer(String.serializer()), v)

    /** Videos removed from the desktop local library ("Quitar entrada"): rescans and the folder watcher skip them. */
    fun ignoredLibraryFiles(): List<String> = read(IGNORED, ListSerializer(String.serializer()))
    fun saveIgnoredLibraryFiles(v: List<String>) = write(IGNORED, ListSerializer(String.serializer()), v)

    companion object {
        const val IGNORED = "library-ignored.json"
        const val TARGETS = "desktop-targets.json"
        const val FOLDERS = "library-folders.json"
        const val DOWNLOADS = "downloads.json"
        const val RULES = "rules.json"
        const val LIBRARY = "library.json"
        const val HIDDEN = "history-hidden.json"
    }
}
