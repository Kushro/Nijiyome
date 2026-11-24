// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * v1.1 review #7: damaged or hostile Matroska / MP4 files never take the reader (or the player) down: no OutOfMemoryError, no
 * StackOverflowError, no endless loop. Each run has a hard deadline on its own thread (a CPU loop would not see a coroutine timeout).
 */
class ContainerSubtitleReaderFuzzTest {
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "fuzz").apply { isDaemon = true } }

    @AfterTest
    fun stop() {
        pool.shutdownNow()
    }

    /** All of [bytes], in memory. */
    private class Bytes(private val bytes: ByteArray) : MediaByteSource {
        override val length: Long get() = bytes.size.toLong()
        override val remote = false
        override fun openAt(position: Long): RawReader = object : RawReader() {
            private var pos = position
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val n = readAt(pos, buffer, offset, length)
                if (n > 0) pos += n
                return n
            }
            override fun close() {}
        }
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val n = minOf(length.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, n)
            return n
        }
        override fun close() {}
    }

    /** Runs the reader over [bytes] on its own thread; fails when it throws anything or is not done within [seconds]. */
    private fun read(bytes: ByteArray, label: String, seconds: Long = 10): EmbeddedSubtitlesState {
        val reader = ContainerSubtitleReader(Bytes(bytes), { 0L }, ContainerSubtitleReader.Policy(Long.MAX_VALUE, null, false, waitMs = 1))
        val f = pool.submit<EmbeddedSubtitlesState> { runBlocking { reader.run() }; reader.state.value }
        return try {
            f.get(seconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            f.cancel(true)
            fail("$label: still reading after $seconds s")
        } catch (e: ExecutionException) {
            fail("$label: ${e.cause}")
        }
    }

    private fun fixture(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream("embedded-subs/$name")!!.use { it.readBytes() }

    private fun be32(v: Long) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun box(type: String, vararg payload: ByteArray): ByteArray {
        val body = payload.fold(ByteArray(0)) { a, b -> a + b }
        return be32(8L + body.size) + type.toByteArray(Charsets.ISO_8859_1) + body
    }

    private fun hex(s: String): ByteArray = s.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `a matroska timestamp of a huge size does not allocate it`() {
        val codec = "S_TEXT/UTF8".toByteArray()
        val entry = hex("D7 81 02 83 81 11 86") + byteArrayOf((0x80 or codec.size).toByte()) + codec
        val tracks = hex("16 54 AE 6B") + byteArrayOf((0x80 or (entry.size + 2)).toByte()) + hex("AE") + byteArrayOf((0x80 or entry.size).toByte()) + entry
        val file = hex("1A 45 DF A3 80") + hex("18 53 80 67 01 FF FF FF FF FF FF FF") + tracks +
            hex("1F 43 B6 75 01 FF FF FF FF FF FF FF") +
            hex("E7 01 00 00 00 7F FF FF F0") + ByteArray(64) // Timestamp claiming ~2 GiB
        val st = read(file, "huge timestamp")
        assertTrue(st.finished, st.toString())
        assertTrue(st.failure != null, st.toString())
        // A huge BlockDuration inside a BlockGroup too.
        val group = hex("A0 90 A1 85 82 00 00 00 41 9B 01 00 00 7F FF FF FF FF")
        val file2 = file.copyOf(file.size - 64 - 9) + hex("E7 81 00") + group + ByteArray(32)
        assertTrue(read(file2, "huge block duration").finished)
    }

    @Test
    fun `deeply nested mp4 boxes are refused without a stack overflow`() {
        // trak > mdia > mdia > ... 100 000 levels (800 kB).
        val depth = 100_000
        val out = ByteArrayOutputStream()
        // Sizes from the outside in: level k (0 = outermost mdia) wraps everything inside it.
        for (k in 0 until depth) {
            out.write(be32(8L * (depth - k)))
            out.write("mdia".toByteArray())
        }
        val nested = out.toByteArray()
        val trak = be32(8L + nested.size) + "trak".toByteArray() + nested
        val moov = be32(8L + trak.size) + "moov".toByteArray() + trak
        val file = box("ftyp", "isom".toByteArray(), ByteArray(4)) + moov
        val st = read(file, "nested boxes")
        assertTrue(st.finished, st.toString())
        assertEquals("boxes nested too deep", st.failure)
    }

    @Test
    fun `sample tables with absurd counts stay bounded`() {
        // stsz: fixed size 10, 2^31 samples; stts: one entry of 2^32-1 samples; stco: 2^31 chunks declared in a box holding two.
        val stsz = be32(0) + be32(10) + be32(0x7FFF_FFFF)
        val stts = be32(0) + be32(1) + be32(0xFFFF_FFFFL) + be32(1000)
        val stco = be32(0) + be32(0x7FFF_FFFF) + be32(100) + be32(200)
        fun table(stsc: ByteArray): List<Mp4TableSample> {
            val b = stsz + stts + stco + stsc
            var at = 0
            fun range(len: Int) = (at to at + len).also { at += len }
            val r = listOf(range(stsz.size), range(stts.size), range(stco.size), range(stsc.size))
            val f = pool.submit<List<Mp4TableSample>> { mp4SampleTable(b, 1000, r[1], r[3], r[0], r[2], false, 40_000) }
            return try {
                f.get(5, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                fail("the sample table loops")
            }
        }
        fun entries(vararg e: Triple<Long, Long, Long>) =
            be32(0) + be32(e.size.toLong()) + e.fold(ByteArray(0)) { a, t -> a + be32(t.first) + be32(t.second) + be32(t.third) }

        // 2^32-1 samples per chunk: capped at the maximum, all in the first chunk.
        val huge = table(entries(Triple(1, 0xFFFF_FFFFL, 1)))
        assertEquals(40_000, huge.size)
        assertEquals(100L, huge[0].offset)
        assertEquals(110L, huge[1].offset)
        assertEquals(1_000L, huge[1].startMs)

        // 2 000 entries whose first chunks go back and forth, one sample per chunk: each chunk is visited once.
        val flip = table(entries(*Array(2_000) { e -> Triple(if (e % 2 == 0) 1L else 2L, 1L, 1L) }))
        assertEquals(listOf(100L, 200L), flip.map { it.offset })

        // An stsc entry count larger than its box, and a first chunk past the end: nothing is invented.
        assertEquals(emptyList(), table(be32(0) + be32(0x7FFF_FFFF)))
        assertEquals(emptyList(), table(entries(Triple(0x7FFF_FFFF, 5, 1))))
    }

    @Test
    fun `random damage to real files never crashes or hangs`() {
        val rnd = Random(20261123)
        for (name in listOf("ass-srt.mkv", "tx3g.mp4", "webvtt.webm")) {
            val original = fixture(name)
            repeat(400) { round ->
                val b = original.copyOf()
                val mutated = when (round % 4) {
                    0 -> b.also { repeat(1 + rnd.nextInt(8)) { b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte() } }
                    1 -> b.copyOf(rnd.nextInt(b.size))
                    2 -> b.also { val at = rnd.nextInt(b.size - 8); repeat(8) { k -> b[at + k] = 0xFF.toByte() } }
                    else -> b.also { // a size field turned huge: 0x01 followed by large bytes, or an MP4 size of 0x7FFFFFFF
                        val at = rnd.nextInt(b.size - 8)
                        if (name.endsWith(".mp4")) be32(0x7FFF_FFFF).copyInto(b, at) else hex("01 00 00 7F FF FF FF FF").copyInto(b, at)
                    }
                }
                read(mutated, "$name round $round", seconds = 10)
            }
        }
    }
}
