// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The flat folder behind [UpscaleResultCache], so the cache logic is tested without a disk. Calls are blocking. */
interface UpscaleFileStore {
    fun list(): List<String>
    fun read(name: String): ByteArray?

    /** Replaces [name] atomically: a reader never sees half a file. */
    fun write(name: String, bytes: ByteArray)
    fun delete(name: String)
    fun size(name: String): Long
    fun lastModified(name: String): Long
    fun touch(name: String, millis: Long)

    /** The path handed to the image loader. */
    fun path(name: String): String
}

/** [UpscaleFileStore] over a real folder (`cacheDir/upscaled/`, plan §4.5). */
class DirectoryUpscaleFileStore(private val dir: File) : UpscaleFileStore {
    override fun list(): List<String> = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(TMP) }?.map { it.name }.orEmpty()

    override fun read(name: String): ByteArray? = File(dir, name).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    override fun write(name: String, bytes: ByteArray) {
        dir.mkdirs()
        val target = File(dir, name)
        val tmp = File(dir, name + TMP)
        tmp.writeBytes(bytes)
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun delete(name: String) {
        File(dir, name).delete()
    }

    override fun size(name: String): Long = File(dir, name).length()

    override fun lastModified(name: String): Long = File(dir, name).lastModified()

    override fun touch(name: String, millis: Long) {
        File(dir, name).setLastModified(millis)
    }

    override fun path(name: String): String = File(dir, name).absolutePath

    private companion object {
        const val TMP = ".tmp"
    }
}
