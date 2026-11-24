// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import java.io.File

/** The user's pictures folder: `XDG_PICTURES_DIR` (environment, then `user-dirs.dirs`) on Linux, `Pictures` in the home folder otherwise. */
object DesktopPictures {
    fun dir(env: (String) -> String? = System::getenv, home: File = File(System.getProperty("user.home"))): File {
        env("XDG_PICTURES_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val config = env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
        val fromUserDirs = runCatching { File(config, "user-dirs.dirs").readLines() }.getOrNull()
            ?.firstNotNullOfOrNull { USER_DIRS_LINE.find(it.trim())?.groupValues?.get(1) }
            ?.replace("\$HOME", home.path)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it != home }
        return fromUserDirs ?: File(home, "Pictures")
    }

    /** What the snackbar shows: the path, with the home folder as `~`. */
    fun shown(file: File, home: String = System.getProperty("user.home")): String =
        if (file.path.startsWith(home + File.separator)) "~" + file.path.removePrefix(home) else file.path

    private val USER_DIRS_LINE = Regex("^XDG_PICTURES_DIR=\"(.*)\"$")
}
