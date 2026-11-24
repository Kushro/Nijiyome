// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches the local-library folders (port of Tatakai Next's `tk-core/download/watch.rs`, over the JDK's WatchService): every folder
 * below a watched root (up to [LibraryScan.MAX_DEPTH], skipping what [LibraryScan.skipDir] skips) is registered, new folders as they
 * appear. [onVideo] gets each video file that was created, renamed into place or written to (copies in progress: the caller waits
 * for the size to settle). Runs on its own daemon thread; [close] stops it.
 */
internal class LibraryWatcher(private val onVideo: (File) -> Unit) : AutoCloseable {
    private val service: WatchService = FileSystems.getDefault().newWatchService()
    private val keys = ConcurrentHashMap<WatchKey, Pair<Path, Int>>()
    private val roots = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var closed = false

    private val thread = Thread({ loop() }, "nijiyome-library-watch").apply {
        isDaemon = true
        start()
    }

    /** Folders being watched (the roots). */
    val watched: Set<String> get() = roots.toSet()

    /** Starts watching [root] and its subfolders; a missing folder is skipped (the scan still covers it later). */
    fun watch(root: File): Boolean {
        if (!root.isDirectory) return false
        if (!roots.add(root.absolutePath)) return true
        register(root.toPath(), 0)
        return true
    }

    /** Stops watching [root] and every folder below it. */
    fun unwatch(root: File) {
        val path = root.absolutePath
        if (!roots.remove(path)) return
        val prefix = root.toPath().toAbsolutePath()
        keys.entries.filter { (_, v) -> v.first.toAbsolutePath().startsWith(prefix) && roots.none { r -> v.first.toAbsolutePath().startsWith(File(r).toPath().toAbsolutePath()) } }
            .forEach { (key, _) ->
                runCatching { key.cancel() }
                keys.remove(key)
            }
    }

    private fun register(dir: Path, depth: Int) {
        if (closed) return
        runCatching {
            val key = dir.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
            )
            keys[key] = dir to depth
        }
        if (depth >= LibraryScan.MAX_DEPTH) return
        dir.toFile().listFiles()?.filter { it.isDirectory && !LibraryScan.skipDir(it.name) }?.forEach { register(it.toPath(), depth + 1) }
    }

    private fun loop() {
        while (!closed) {
            val key = try {
                service.take()
            } catch (_: ClosedWatchServiceException) {
                return
            } catch (_: InterruptedException) {
                return
            }
            val (dir, depth) = keys[key] ?: (null to 0)
            for (event in key.pollEvents()) {
                val name = event.context() as? Path ?: continue
                val path = dir?.resolve(name) ?: continue
                val file = path.toFile()
                if (file.isDirectory) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && depth < LibraryScan.MAX_DEPTH && !LibraryScan.skipDir(file.name)) {
                        register(path, depth + 1)
                        // Files copied in together with their folder came before the folder was watched.
                        LibraryScan.walkVideos(file).forEach { runCatching { onVideo(it) } }
                    }
                } else if (LibraryScan.isLibraryVideo(file.name)) {
                    runCatching { onVideo(file) }
                }
            }
            if (!key.reset()) keys.remove(key)
        }
    }

    override fun close() {
        closed = true
        runCatching { service.close() }
        thread.interrupt()
    }
}
