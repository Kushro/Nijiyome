// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import java.io.File

/**
 * Where downloads go by default (as Tatakai Next did, with the new name): web downloads in `<Downloads>/Nijiyome/Episodes`, torrents
 * in `<Downloads>/Nijiyome`. `<Downloads>` is `XDG_DOWNLOAD_DIR` (environment, then `user-dirs.dirs`) or `~/Downloads` on Linux and
 * `%USERPROFILE%\Downloads` on Windows; without one, the same layout goes under `<data>/Downloads` (`<data>\Downloads (Nijiyome)` on Windows,
 * see [dataDownloadsName]). With `NIJIYOME_DATA_DIR` set (development, tests) `<Downloads>` is that folder of the data directory, never a
 * real user folder. Settings can point either one somewhere else.
 */
class UserFolders(
    /** The user's Downloads folder, or null when there is none. */
    val downloads: File?,
    /** Default folder of the web downloads. */
    val webDownloads: File,
    /** Default folder libtorrent writes to. */
    val torrentDownloads: File,
    /** "Guardar en Descargas" target of a torrent file. */
    val export: File,
) {
    /** Top-level folders of the data directory that hold downloads (no Downloads folder on this system): "Restablecer la app" keeps them. */
    fun inDataDir(paths: AppPaths): Set<String> {
        val data = paths.dataDir.absoluteFile.normalize()
        return listOf(webDownloads, torrentDownloads).mapNotNull { dir ->
            val rel = runCatching { data.toPath().relativize(dir.absoluteFile.normalize().toPath()) }.getOrNull() ?: return@mapNotNull null
            rel.firstOrNull()?.toString()?.takeIf { it.isNotEmpty() && it != ".." && !rel.isAbsolute }
        }.toSet()
    }

    companion object {
        fun resolve(
            paths: AppPaths,
            env: (String) -> String? = System::getenv,
            osName: String = System.getProperty("os.name"),
            home: String = System.getProperty("user.home"),
        ): UserFolders {
            val downloads = if (!env("NIJIYOME_DATA_DIR").isNullOrBlank()) {
                File(paths.dataDir, dataDownloadsName(osName))
            } else {
                systemDownloads(env, osName, home)
            }
            return if (downloads != null) {
                val root = File(downloads, "Nijiyome")
                UserFolders(downloads, File(root, "Episodes"), root, downloads)
            } else {
                // Same layout under <data>/Downloads: never the download engine's own folder (<data>/downloads, which its reset
                // wipe deletes whole) nor the torrent engine's state (<data>/torrents).
                val root = File(paths.dataDir, dataDownloadsName(osName))
                UserFolders(null, File(root, "Nijiyome/Episodes"), File(root, "Nijiyome"), root)
            }
        }

        /**
         * The downloads folder inside the data directory. Windows file names ignore case, so `Downloads` there would be the download
         * engine's own `downloads` (its reset wipe deletes it whole, with the user's episodes in it); Linux keeps `Downloads`.
         */
        internal fun dataDownloadsName(osName: String): String =
            if (osName.startsWith("Windows", ignoreCase = true)) "Downloads (Nijiyome)" else "Downloads"

        /** The Downloads folder of the system, when it exists. */
        internal fun systemDownloads(env: (String) -> String?, osName: String, home: String): File? {
            if (osName.startsWith("Windows", ignoreCase = true)) {
                val profile = env("USERPROFILE")?.takeIf { it.isNotBlank() } ?: home
                return File(profile, "Downloads").takeIf { it.isDirectory }
            }
            env("XDG_DOWNLOAD_DIR")?.takeIf { it.isNotBlank() }?.let { return File(expandHome(it, home)) }
            val config = env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
            xdgUserDir(File(config, "user-dirs.dirs"), "XDG_DOWNLOAD_DIR", home)?.let { return it }
            return File(home, "Downloads").takeIf { it.isDirectory }
        }

        /** `XDG_DOWNLOAD_DIR="$HOME/Descargas"` from `user-dirs.dirs`; `$HOME` alone means "no such folder" there. */
        internal fun xdgUserDir(file: File, key: String, home: String): File? {
            val line = runCatching { file.readLines() }.getOrNull()?.map { it.trim() }?.firstOrNull { it.startsWith("$key=") } ?: return null
            val value = line.substringAfter('=').trim().removeSurrounding("\"")
            val dir = File(expandHome(value, home))
            return dir.takeIf { it.absoluteFile.normalize() != File(home).absoluteFile.normalize() }
        }

        private fun expandHome(value: String, home: String): String = when {
            value.startsWith("\$HOME") -> home + value.removePrefix("\$HOME")
            value.startsWith("~") -> home + value.removePrefix("~")
            else -> value
        }
    }
}
