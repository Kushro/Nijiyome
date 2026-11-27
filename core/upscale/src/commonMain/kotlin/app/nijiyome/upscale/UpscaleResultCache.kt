// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What is stored next to each upscaled image: where it came from and how it was made. */
@Serializable
data class UpscaleCacheEntry(
    val url: String,
    val kind: UpscaleImageKind,
    /** [UpscaleKey.sourceHash] of the source bytes the result was made from. */
    val sourceHash: String,
    /** The model factor used (2, 3 or 4). */
    val scale: Int,
    val width: Int,
    val height: Int,
)

/**
 * The disk cache of upscaled results (plan §4.5): `<key>.img` holds the encoded image, `<key>.json` its [UpscaleCacheEntry].
 * Least recently used entries are evicted past [maxBytes]. Thread safe; every call except [load] and the ones that write is cheap
 * and never touches the disk, so the UI may call [peek] on its thread.
 *
 * The in-memory index is built by [load], which the queue runs on its worker; until then [peek] answers null.
 * Access times live in memory and are written to the image files' modification time by [flushAccessTimes], so the order survives
 * a restart without a write per hit.
 */
class UpscaleResultCache(
    private val files: UpscaleFileStore,
    maxBytes: Long,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private class Indexed(val entry: UpscaleCacheEntry, val bytes: Long, var lastAccess: Long)

    private val lock = Any()
    private val index = HashMap<String, Indexed>()
    private val touched = HashSet<String>()
    private var loaded = false
    private var cap = maxBytes

    private val usedBytes = MutableStateFlow(0L)
    private val clearCount = MutableStateFlow(0)

    /** Bytes used by the stored results. */
    val bytes: StateFlow<Long> = usedBytes.asStateFlow()

    /** Bumped by every [clear], so holders of finished paths know they are gone. */
    val clears: StateFlow<Int> = clearCount.asStateFlow()

    val maxBytes: Long get() = synchronized(lock) { cap }

    val isLoaded: Boolean get() = synchronized(lock) { loaded }

    /** Builds the index from the folder (blocking, idempotent). Orphan files (an image without metadata or the reverse) are deleted. */
    fun load() {
        synchronized(lock) {
            if (loaded) return
            val names = files.list().toSet()
            for (name in names) {
                when {
                    name.endsWith(META) -> {
                        val key = name.removeSuffix(META)
                        val image = key + IMAGE
                        val entry = files.read(name)?.let { decodeEntry(it) }
                        if (entry == null || image !in names) {
                            files.delete(name)
                            files.delete(image)
                        } else {
                            index[key] = Indexed(entry, files.size(image) + files.size(name), files.lastModified(image))
                        }
                    }
                    name.endsWith(IMAGE) -> if (name.removeSuffix(IMAGE) + META !in names) files.delete(name)
                    else -> files.delete(name)
                }
            }
            loaded = true
            evictLocked(keep = null)
            publishLocked()
        }
    }

    /** The stored result of [key] as a path, or null; counts as an access. Never reads the disk. */
    fun peek(key: String): String? {
        synchronized(lock) {
            val hit = index[key] ?: return null
            hit.lastAccess = clock()
            touched += key
            return files.path(key + IMAGE)
        }
    }

    fun entry(key: String): UpscaleCacheEntry? = synchronized(lock) { index[key]?.entry }

    /**
     * The stored result of [key] when it was made from a source with [sourceHash]; an entry made from other bytes is deleted (the
     * source changed) and null is returned.
     */
    fun lookup(key: String, sourceHash: String): String? {
        synchronized(lock) {
            val hit = index[key] ?: return null
            if (hit.entry.sourceHash != sourceHash) {
                removeLocked(key)
                publishLocked()
                return null
            }
            hit.lastAccess = clock()
            touched += key
            return files.path(key + IMAGE)
        }
    }

    /** Stores a result and evicts the least recently used entries past the cap. Returns the image path. */
    fun put(key: String, entry: UpscaleCacheEntry, image: ByteArray): String = synchronized(lock) {
        val meta = encodeEntry(entry)
        files.write(key + IMAGE, image)
        files.write(key + META, meta)
        index[key] = Indexed(entry, image.size.toLong() + meta.size, clock())
        touched -= key
        evictLocked(keep = key)
        publishLocked()
        files.path(key + IMAGE)
    }

    fun remove(key: String) {
        synchronized(lock) {
            removeLocked(key)
            publishLocked()
        }
    }

    /** Every key stored for [url] (all kinds and sizes), for [UpscaleQueue.sourceRefreshed]. */
    fun keysFor(url: String): List<String> = synchronized(lock) { index.filterValues { it.entry.url == url }.keys.toList() }

    /** Deletes every result, including files the index never saw. */
    fun clear() {
        synchronized(lock) {
            for (name in files.list()) files.delete(name)
            index.clear()
            touched.clear()
            publishLocked()
            clearCount.value++
        }
    }

    fun setMaxBytes(bytes: Long) {
        synchronized(lock) {
            cap = bytes
            if (loaded) evictLocked(keep = null)
            publishLocked()
        }
    }

    /** Writes the in-memory access times to the files (blocking; the queue calls it from its worker). */
    fun flushAccessTimes() {
        val pending = synchronized(lock) {
            val out = touched.mapNotNull { key -> index[key]?.let { key to it.lastAccess } }
            touched.clear()
            out
        }
        for ((key, millis) in pending) files.touch(key + IMAGE, millis)
    }

    private fun evictLocked(keep: String?) {
        var total = index.values.sumOf { it.bytes }
        while (total > cap) {
            val victim = index.entries.filter { it.key != keep }.minByOrNull { it.value.lastAccess } ?: break
            total -= victim.value.bytes
            removeLocked(victim.key)
        }
    }

    private fun removeLocked(key: String) {
        index.remove(key)
        touched -= key
        files.delete(key + IMAGE)
        files.delete(key + META)
    }

    private fun publishLocked() {
        usedBytes.value = index.values.sumOf { it.bytes }
    }

    private fun encodeEntry(entry: UpscaleCacheEntry): ByteArray =
        entryJson.encodeToString(UpscaleCacheEntry.serializer(), entry).encodeToByteArray()

    private fun decodeEntry(bytes: ByteArray): UpscaleCacheEntry? =
        runCatching { entryJson.decodeFromString(UpscaleCacheEntry.serializer(), bytes.decodeToString()) }.getOrNull()

    companion object {
        /** Extension of the image files; the image loader sniffs the format, so it does not name one. */
        const val IMAGE = ".img"
        const val META = ".json"

        fun megabytes(mb: Int): Long = mb.toLong() * 1024 * 1024

        private val entryJson = Json { ignoreUnknownKeys = true }
    }
}
