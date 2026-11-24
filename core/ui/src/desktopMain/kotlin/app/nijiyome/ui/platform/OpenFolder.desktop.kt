// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import java.io.File
import java.net.URI
import java.util.Locale

/** Opens the folder in the file manager (`xdg-open` on Linux, Explorer on Windows, `java.awt.Desktop` elsewhere), off the UI thread. */
@Composable
actual fun rememberOpenFolder(): ((path: String) -> Unit)? = OPEN_FOLDER

private val OPEN_FOLDER: (String) -> Unit = { path ->
    Thread({ runCatching { openInFileManager(path) } }, "open-folder").apply { isDaemon = true }.start()
}

private fun openInFileManager(path: String) {
    val folder = folderToOpen(path) ?: return
    val command = openFolderCommand(System.getProperty("os.name").orEmpty(), folder)
    if (command != null) {
        runCatching { ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start() }
            .onSuccess { return }
    }
    val desktop = java.awt.Desktop.getDesktop()
    if (java.awt.Desktop.isDesktopSupported() && desktop.isSupported(java.awt.Desktop.Action.OPEN)) desktop.open(folder)
}

/** The folder to show for [path]: itself when it is a folder, else its parent; the nearest existing one up the tree; null = none. */
internal fun folderToOpen(path: String): File? {
    if (path.isBlank()) return null
    var f: File? = runCatching { if (path.startsWith("file:")) File(URI(path)) else File(path) }.getOrNull()?.absoluteFile ?: return null
    if (f?.isFile == true) f = f.parentFile
    while (f != null && !f.isDirectory) f = f.parentFile
    return f
}

/** The command that opens [folder] on [osName]; null = use `java.awt.Desktop`. */
internal fun openFolderCommand(osName: String, folder: File): List<String>? {
    val os = osName.lowercase(Locale.ROOT)
    return when {
        os.startsWith("windows") -> listOf("explorer.exe", folder.absolutePath)
        os.startsWith("mac") -> listOf("open", folder.absolutePath)
        os.contains("linux") || os.contains("bsd") -> listOf("xdg-open", folder.absolutePath)
        else -> null
    }
}
