// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import java.io.File

/**
 * Where the desktop app keeps its files. `NIJIYOME_DATA_DIR` overrides everything (development, tests: never a real user
 * directory). Otherwise `%APPDATA%\Nijiyome` on Windows and `$XDG_CONFIG_HOME/Nijiyome` (default `~/.config/Nijiyome`) on Linux.
 */
class AppPaths(val dataDir: File) {
    val cacheDir: File get() = File(dataDir, "cache")
    val databaseFile: File get() = File(dataDir, "nijiyome.db")

    /** Creates the directories. Returns null when the data directory is usable, or what is wrong with it. */
    fun prepare(): String? {
        for (dir in listOf(dataDir, cacheDir)) {
            if (!dir.isDirectory && !dir.mkdirs()) return "cannot create ${dir.path}"
        }
        if (!dataDir.canWrite()) return "cannot write to ${dataDir.path}"
        return null
    }

    companion object {
        fun resolve(env: (String) -> String? = System::getenv, osName: String = System.getProperty("os.name"), home: String = System.getProperty("user.home")): AppPaths {
            env("NIJIYOME_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { return AppPaths(File(it)) }
            val base = if (osName.startsWith("Windows", ignoreCase = true)) {
                env("APPDATA")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, "AppData/Roaming")
            } else {
                env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
            }
            return AppPaths(File(base, "Nijiyome"))
        }

        /** "Windows" or "Linux": the platform label of the User-Agent. */
        fun platformLabel(osName: String = System.getProperty("os.name")): String =
            if (osName.startsWith("Windows", ignoreCase = true)) "Windows" else "Linux"
    }
}
