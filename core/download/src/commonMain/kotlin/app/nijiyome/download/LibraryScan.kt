// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.release.isVideoPath
import java.io.File
import java.util.Locale

/**
 * Local library folders on a plain file system (desktop; port of Tatakai Next's `tk-core/download/library.rs`): which files count as
 * finished videos and the bounded walk of a folder. Blocking.
 */
object LibraryScan {
    /** Directories never descended into (engine leftovers, OS / tool folders). */
    private val SKIP_DIRS = setOf("\$recycle.bin", "system volume information", "node_modules", "@eadir", ".trash")
    const val MAX_DEPTH = 6
    const val MAX_FILES = 20_000

    fun skipDir(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.startsWith(".") || lower.endsWith(".parts") || lower in SKIP_DIRS
    }

    /** A finished video file the library can hold (not an engine `.part` / `.tmp`, a hidden file or a sample). */
    fun isLibraryVideo(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        if (lower.endsWith(".part") || lower.endsWith(".tmp") || lower.startsWith(".")) return false
        val stem = lower.substringBeforeLast('.', lower)
        if (stem.endsWith("sample") || stem.startsWith("sample")) return false
        return isVideoPath(lower)
    }

    /** Video files under [root], sorted by path ([root] itself when it is a video file). */
    fun walkVideos(root: File): List<File> {
        if (root.isFile) return if (isLibraryVideo(root.name)) listOf(root) else emptyList()
        val out = ArrayList<File>()
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (dir, depth) = stack.removeLast()
            val entries = dir.listFiles() ?: continue
            for (f in entries) {
                if (f.isDirectory) {
                    if (depth < MAX_DEPTH && !skipDir(f.name)) stack.addLast(f to depth + 1)
                } else if (f.isFile && isLibraryVideo(f.name)) {
                    out += f
                    if (out.size >= MAX_FILES) return out.sortedBy { it.path }
                }
            }
        }
        return out.sortedBy { it.path }
    }
}
