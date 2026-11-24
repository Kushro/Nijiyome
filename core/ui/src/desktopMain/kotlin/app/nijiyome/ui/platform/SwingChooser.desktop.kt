// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The desktop pickers: Swing's file chooser, modal over the app window, on the event thread. Paths come back absolute (no URIs).
 * Headless (tests) answers "nothing chosen".
 */
internal object SwingChooser {
    fun chooseFiles(mimeTypes: List<String>, multiple: Boolean, onResult: (List<String>) -> Unit) = onEventThread {
        if (GraphicsEnvironment.isHeadless()) return@onEventThread onResult(emptyList())
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = multiple
            extensionsFor(mimeTypes)?.let { exts ->
                fileFilter = FileNameExtensionFilter(exts.joinToString(", ") { "*.$it" }, *exts.toTypedArray())
            }
        }
        val answer = chooser.showOpenDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow)
        val files = if (answer != JFileChooser.APPROVE_OPTION) {
            emptyList()
        } else if (multiple) {
            chooser.selectedFiles.toList()
        } else {
            listOfNotNull(chooser.selectedFile)
        }
        onResult(files.filter(File::isFile).map { it.absolutePath })
    }

    fun chooseFolder(onResult: (String?) -> Unit) = onEventThread {
        if (GraphicsEnvironment.isHeadless()) return@onEventThread onResult(null)
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        val answer = chooser.showOpenDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow)
        onResult(chooser.selectedFile?.takeIf { answer == JFileChooser.APPROVE_OPTION && it.isDirectory }?.absolutePath)
    }

    /** File extensions of the MIME types the screens ask for; null = any file. */
    fun extensionsFor(mimeTypes: List<String>): List<String>? {
        val exts = mimeTypes.flatMap { mime ->
            when {
                mime == "*/*" -> return null
                mime.startsWith("video/") -> VIDEO
                mime == "application/x-bittorrent" -> listOf("torrent")
                mime.startsWith("image/") -> listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
                mime == "application/json" -> listOf("json")
                mime == "application/xml" || mime == "text/xml" -> listOf("xml")
                mime.startsWith("font/") || mime.contains("font") -> listOf("ttf", "otf")
                mime == "text/plain" -> listOf("txt", "srt", "vtt", "ass", "ssa")
                else -> return null
            }
        }.distinct()
        return exts.ifEmpty { null }
    }

    private val VIDEO = listOf("mkv", "mp4", "m4v", "webm", "avi", "mov", "ts", "m2ts", "wmv", "flv", "ogv")

    private fun onEventThread(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
