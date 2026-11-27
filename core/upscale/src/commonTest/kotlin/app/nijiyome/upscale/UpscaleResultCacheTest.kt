// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpscaleResultCacheTest {
    private var now = 1_000L
    private val files = InMemoryFileStore()

    private fun cache(max: Long = Long.MAX_VALUE) = UpscaleResultCache(files, max, clock = { now })

    private fun entry(url: String, hash: String = "h1") = UpscaleCacheEntry(url, UpscaleImageKind.POSTER, hash, 2, 100, 100)

    @Test
    fun nothingIsAnsweredBeforeLoad() {
        val first = cache()
        first.load()
        first.put("k1", entry("u1"), ByteArray(100))
        val second = cache()
        assertNull(second.peek("k1"))
        second.load()
        assertNotNull(second.peek("k1"))
    }

    @Test
    fun aChangedSourceHashInvalidatesTheEntry() {
        val c = cache()
        c.load()
        c.put("k1", entry("u1", hash = "old"), ByteArray(100))
        assertNotNull(c.lookup("k1", "old"))
        assertNull(c.lookup("k1", "new"))
        assertNull(c.peek("k1"))
        assertTrue(files.files.isEmpty())
        assertEquals(0L, c.bytes.value)
    }

    @Test
    fun theLeastRecentlyUsedEntryIsEvictedPastTheCap() {
        val c = cache()
        c.load()
        c.put("k1", entry("u1"), ByteArray(1000))
        now += 10
        c.put("k2", entry("u2"), ByteArray(1000))
        val one = c.bytes.value / 2
        now += 10
        assertNotNull(c.peek("k1")) // k2 is now the least recently used
        c.setMaxBytes(one * 2 + one / 2)
        now += 10
        c.put("k3", entry("u3"), ByteArray(1000))

        assertNull(c.peek("k2"))
        assertNotNull(c.peek("k1"))
        assertNotNull(c.peek("k3"))
        assertFalse(files.files.keys.any { it.startsWith("k2") })
        assertEquals(one * 2, c.bytes.value)
    }

    @Test
    fun accessTimesSurviveARestart() {
        val c = cache()
        c.load()
        c.put("k1", entry("u1"), ByteArray(1000))
        now += 10
        c.put("k2", entry("u2"), ByteArray(1000))
        now += 10
        c.peek("k1")
        c.flushAccessTimes()
        // Files written by put carry no time in the fake store; flush stamps k1 only, so give k2 its put time.
        files.touch("k2" + UpscaleResultCache.IMAGE, 1_010)

        val one = c.bytes.value / 2
        val reopened = cache(max = one)
        reopened.load()
        assertNotNull(reopened.peek("k1"))
        assertNull(reopened.peek("k2"))
    }

    @Test
    fun orphansAreDeletedOnLoadAndClearEmptiesEverything() {
        files.write("lonely" + UpscaleResultCache.IMAGE, ByteArray(10))
        files.write("broken" + UpscaleResultCache.META, "{".encodeToByteArray())
        files.write("stray.tmp", ByteArray(1))
        val c = cache()
        c.load()
        assertTrue(files.files.isEmpty())

        c.put("k1", entry("u1"), ByteArray(10))
        c.put("k2", entry("u1"), ByteArray(10))
        assertEquals(setOf("k1", "k2"), c.keysFor("u1").toSet())
        val before = c.clears.value
        c.clear()
        assertTrue(files.files.isEmpty())
        assertEquals(before + 1, c.clears.value)
        assertNull(c.peek("k1"))
    }
}
