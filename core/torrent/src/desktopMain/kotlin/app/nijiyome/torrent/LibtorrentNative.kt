// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Loads libtorrent4j's native library on desktop (docs/spikes/nativos.md): the platform jar (`libtorrent4j-linux` / `-windows`) carries
 * `lib/x86_64/libtorrent4j.so|dll`, but libtorrent4j only loads it from `java.library.path` or from the `libtorrent4j.jni.path` system
 * property. So the library is copied out of the jar into a cache folder (once per size; a copy is written to a temporary name and
 * renamed into place) and the property points to it before any libtorrent4j class is touched.
 *
 * Loading can fail (Linux with glibc < 2.38, an architecture with no library in the jar): the result says why, and the app keeps
 * working without torrents.
 */
object LibtorrentNative {
    /** What [load] found: the loaded library, or why there is none. */
    sealed interface Result {
        data class Loaded(val library: File, val version: String) : Result
        data class Failed(val reason: String) : Result
    }

    @Volatile private var cached: Result? = null

    /** The library resource inside the jar for this OS / architecture, or null when libtorrent4j ships none. */
    fun resourceFor(osName: String, arch: String): String? {
        val os = osName.lowercase(Locale.ROOT)
        val x64 = arch.lowercase(Locale.ROOT) in setOf("amd64", "x86_64", "x64")
        if (!x64) return null
        return when {
            os.startsWith("windows") -> "lib/x86_64/libtorrent4j.dll"
            os.startsWith("linux") -> "lib/x86_64/libtorrent4j.so"
            else -> null
        }
    }

    /**
     * Copies [resource] (read with [open]; null = missing) into [dir] unless a copy of the same size is already there; returns the file.
     * A half-written copy of a previous run never counts: the file only appears by rename once complete.
     */
    @Throws(IOException::class)
    fun extract(resource: String, dir: File, open: (String) -> InputStream?): File {
        val name = resource.substringAfterLast('/')
        val expected = open(resource)?.use { stream -> countBytes(stream) } ?: throw IOException("missing $resource")
        val target = File(File(dir, "libtorrent4j-$expected"), name)
        if (target.isFile && target.length() == expected) return target
        target.parentFile.mkdirs()
        if (!target.parentFile.isDirectory) throw IOException("cannot create ${target.parentFile}")
        val tmp = File(target.parentFile, "$name.${ProcessHandle.current().pid()}.tmp")
        try {
            val copied = (open(resource) ?: throw IOException("missing $resource")).use { input ->
                tmp.outputStream().use { out -> input.copyTo(out, BUFFER) }
            }
            if (copied != expected) throw IOException("short copy of $resource: $copied of $expected bytes")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("cannot rename $tmp")
        } finally {
            tmp.delete()
        }
        return target
    }

    /**
     * Extracts the library into [cacheDir]/native, sets `libtorrent4j.jni.path` and loads it (once per process). Never throws: a
     * failure comes back as [Result.Failed] and every later call returns the same answer.
     */
    @Synchronized
    fun load(
        cacheDir: File,
        osName: String = System.getProperty("os.name").orEmpty(),
        arch: String = System.getProperty("os.arch").orEmpty(),
        open: (String) -> InputStream? = { LibtorrentNative::class.java.classLoader.getResourceAsStream(it) },
    ): Result {
        cached?.let { return it }
        return loadNow(cacheDir, osName, arch, open).also { cached = it }
    }

    /** [load] without the per-process answer (tests). */
    internal fun loadNow(cacheDir: File, osName: String, arch: String, open: (String) -> InputStream?): Result {
        return try {
            val resource = resourceFor(osName, arch) ?: throw IOException("no libtorrent4j library for $osName / $arch")
            val library = extract(resource, File(cacheDir, "native"), open)
            System.setProperty(JNI_PATH, library.absolutePath)
            // The first touch of the SWIG class runs System.load(libtorrent4j.jni.path).
            val version = org.libtorrent4j.LibTorrent.version()
            Result.Loaded(library, version)
        } catch (e: Throwable) {
            if (e is VirtualMachineError && e !is StackOverflowError) throw e
            Result.Failed(describe(e))
        }
    }

    /** One line for the log: the linker's message carries the missing glibc version. */
    internal fun describe(e: Throwable): String {
        var cause: Throwable = e
        while (cause.cause != null && cause.cause !== cause && cause.message == null) cause = cause.cause!!
        return "${cause::class.java.simpleName}: ${cause.message ?: "unknown"}".take(400)
    }

    private fun countBytes(stream: InputStream): Long {
        val buf = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n < 0) return total
            total += n
        }
    }

    private const val JNI_PATH = "libtorrent4j.jni.path"
    private const val BUFFER = 256 * 1024
}
