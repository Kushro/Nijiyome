// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** [UpscaleFileStore] in memory. */
class InMemoryFileStore : UpscaleFileStore {
    val files = LinkedHashMap<String, ByteArray>()
    val modified = HashMap<String, Long>()

    override fun list(): List<String> = files.keys.toList()
    override fun read(name: String): ByteArray? = files[name]
    override fun write(name: String, bytes: ByteArray) {
        files[name] = bytes
    }
    override fun delete(name: String) {
        files.remove(name)
        modified.remove(name)
    }
    override fun size(name: String): Long = files[name]?.size?.toLong() ?: 0L
    override fun lastModified(name: String): Long = modified[name] ?: 0L
    override fun touch(name: String, millis: Long) {
        if (name in files) modified[name] = millis
    }
    override fun path(name: String): String = "/mem/$name"
}

/** Sources are `"<w>x<h>:<content>"`; changing the content changes the hash, the size stays. */
class FakeSource : UpscaleSourceProvider {
    val bytes = HashMap<String, ByteArray>()
    val reads = ArrayList<String>()

    fun put(url: String, width: Int, height: Int, content: String = "v1") {
        bytes[url] = "${width}x$height:$content".encodeToByteArray()
    }

    override suspend fun read(url: String): ByteArray? {
        reads += url
        return bytes[url]
    }
}

object FakeCodec : UpscaleImageCodec {
    override fun decode(bytes: ByteArray): RgbaImage? {
        val size = bytes.decodeToString().substringBefore(':')
        val w = size.substringBefore('x').toIntOrNull() ?: return null
        val h = size.substringAfter('x').toIntOrNull() ?: return null
        return RgbaImage(w, h, ByteArray(w * h * 4))
    }

    override fun encode(image: RgbaImage, width: Int, height: Int): ByteArray = ByteArray(ENCODED_SIZE)

    const val ENCODED_SIZE = 1000
}

class FakeEngine(var gpu: String? = "Test GPU") : UpscaleEngine {
    val calls = ArrayList<Triple<Int, Int, Int>>()
    var releases = 0
    var fail = false

    /** Runs inside [upscale] before the result, e.g. to release a ticket while the job is on the GPU. */
    var during: (() -> Unit)? = null

    override fun probe(): String? = gpu

    override fun upscale(rgba: ByteArray, width: Int, height: Int, scale: Int, cancelled: () -> Boolean): ByteArray? {
        calls += Triple(width, height, scale)
        during?.invoke()
        if (fail || cancelled()) return null
        return ByteArray(width * scale * height * scale * 4)
    }

    override fun release() {
        releases++
    }
}

class InMemorySettingsPersistence(var stored: String? = null) : UpscaleSettingsPersistence {
    var writes = 0
    override fun read(): String? = stored
    override fun write(json: String) {
        stored = json
        writes++
    }
}

val TEST_LIMITS = UpscaleLimits(maxOutputLongSide = 2560, defaultCacheMb = 256, pauseBetweenJobsMillis = 50)

/**
 * A foreground scope on the test scheduler for the queue's and the store's own coroutines. `backgroundScope` would not do:
 * `advanceUntilIdle()` returns as soon as only background work is left, so the queue never ran in the tests.
 */
fun TestScope.testWorkScope(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

