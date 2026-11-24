// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

/**
 * Hito 6 (D1, checklist system.storage-usage): folders a platform counts in Ajustes › Datos besides the caches and the torrents.
 * Desktop binds one (web downloads, logs, extensions, as Tatakai Next's `StorageUsageVm`); Android binds none, so its breakdown
 * stays as it was.
 */
fun interface ExtraStorage {
    /** Measured off the main thread; a folder that cannot be read is left out. */
    suspend fun measure(): List<ExtraStorageItem>
}

/**
 * One measured folder. [path] is the folder itself; [inTorrents] is set by Settings when it lies inside the torrent download folder
 * (desktop's default `<Downloads>/Nijiyome/Episodes` is under `<Downloads>/Nijiyome`), whose figure already counts it: it is then
 * listed but not added to the bar a second time.
 */
data class ExtraStorageItem(val kind: Kind, val bytes: Long, val path: String? = null, val inTorrents: Boolean = false) {
    enum class Kind { WEB_DOWNLOADS, LOGS, EXTENSIONS }
}

/** [path] is [dir] or inside it (both absolute, compared by path segments). Pure. */
internal fun isInside(path: String, dir: String): Boolean {
    val p = java.io.File(path).absoluteFile.normalize().toPath()
    val d = java.io.File(dir).absoluteFile.normalize().toPath()
    return p.startsWith(d)
}
