// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop embedded-subtitle reader over small files made with FFmpeg (src/desktopTest/resources/embedded-subs, 10 s of 64x36
 * video): `ass-srt.mkv` (an ASS track in Spanish with a duplicated layer and a karaoke line, and a SubRip track in English),
 * `tx3g.mp4` (MP4 timed text) and `webvtt.webm` (WebVTT in WebM).
 */
class ContainerSubtitleReaderTest {
    private fun fixture(name: String): File {
        val url = javaClass.classLoader.getResource("embedded-subs/$name") ?: error("missing fixture $name")
        val f = Files.createTempFile("sub", name).toFile().apply { deleteOnExit() }
        url.openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }

    private fun source(file: File, available: (() -> List<LongRange>?)? = null): MediaByteSource {
        val raf = RandomAccessFile(file, "r")
        return ChannelByteSource(raf.channel, raf, available)
    }

    private fun read(src: MediaByteSource, policy: ContainerSubtitleReader.Policy? = null): EmbeddedSubtitlesState = runBlocking {
        val reader = if (policy != null) ContainerSubtitleReader(src, { 0L }, policy) else ContainerSubtitleReader(src, { 0L })
        withTimeout(20_000) { reader.run() }
        reader.state.value
    }

    @Test
    fun `matroska ass and subrip tracks become cues`() {
        val st = read(source(fixture("ass-srt.mkv")))
        assertTrue(st.finished && st.complete, st.toString())
        assertNull(st.failure)
        assertEquals(2, st.tracks.size, st.tracks.toString())
        val ass = st.tracks.single { it.mime == SubtitleMimes.SSA }
        val srt = st.tracks.single { it.mime == SubtitleMimes.SUBRIP }
        assertEquals(0, ass.ordinal)
        assertEquals(1, srt.ordinal)
        assertEquals("spa", ass.language)
        assertEquals("Español", ass.label)
        assertEquals("eng", srt.language)

        val a = st.cues.getValue(ass.id)
        // The second layer of the same event is dropped, the karaoke line is set aside.
        assertEquals(listOf(1_000L to 2_500L, 3_000L to 4_200L, 7_000L to 9_000L), a.map { it.startMs to it.endMs })
        assertEquals("Hola, mundo", a[0].text)
        assertEquals("Segunda línea\ncon salto", a[1].text)
        assertEquals("Última", a[2].text)
        assertEquals(1, st.skipped[ass.id]?.size)

        val s = st.cues.getValue(srt.id)
        assertEquals(listOf(1_500L to 2_000L, 6_000L to 8_250L), s.map { it.startMs to it.endMs })
        assertEquals("Hello, world", s[0].text)
        assertTrue("Second" in s[1].text && "line" in s[1].text, s[1].text)

        // The player's track (mpv: second subtitle track, English) matches the container's.
        assertEquals(srt, EmbeddedTrackMatcher.match(st.tracks, EmbeddedTrackRef(1, "eng", null, "subrip")))
    }

    @Test
    fun `mp4 timed text uses the sample durations`() {
        val st = read(source(fixture("tx3g.mp4")))
        assertTrue(st.finished && st.complete, st.toString())
        val t = st.tracks.single()
        assertEquals(SubtitleMimes.TX3G, t.mime)
        assertEquals("eng", t.language)
        val cues = st.cues.getValue(t.id)
        assertEquals(listOf(1_500L to 2_000L, 6_000L to 8_250L), cues.map { it.startMs to it.endMs })
        assertEquals("Hello, world", cues[0].text)
        assertEquals("Second line", cues[1].text)
    }

    @Test
    fun `webvtt in webm is read`() {
        val st = read(source(fixture("webvtt.webm")))
        assertTrue(st.finished && st.complete, st.toString())
        val t = st.tracks.single()
        assertEquals(SubtitleMimes.VTT, t.mime)
        val cues = st.cues.getValue(t.id)
        assertEquals(listOf(1_500L to 2_000L, 6_000L to 8_250L), cues.map { it.startMs to it.endMs })
        assertEquals("Hello, world", cues[0].text)
    }

    @Test
    fun `a torrent is read as it downloads`() {
        val file = fixture("ass-srt.mkv")
        val total = file.length()
        var calls = 0
        // Every look at the ranges downloads a little more (sequential download, like a torrent streaming from the start).
        val grow = { listOf(0L until minOf(total, 600L + 400L * (calls++ / 8))) }
        val st = read(source(file, grow), ContainerSubtitleReader.Policy(Long.MAX_VALUE, null, false, waitMs = 5))
        assertTrue(st.finished, st.toString())
        assertNull(st.failure, st.toString())
        val ass = st.tracks.single { it.mime == SubtitleMimes.SSA }
        assertEquals(3, st.cues.getValue(ass.id).size)
    }

    @Test
    fun `the byte budget and unknown files stop it`() {
        val budget = read(source(fixture("ass-srt.mkv")), ContainerSubtitleReader.Policy(200, null, false, waitMs = 5))
        assertEquals("budget", budget.failure)
        val junk = Files.createTempFile("junk", ".bin").toFile().apply { writeBytes(ByteArray(4096) { (it * 7).toByte() }); deleteOnExit() }
        assertEquals("unsupported container", read(source(junk)).failure)
    }

    @Test
    fun `matroska blocks become the raw samples media3 emits`() {
        assertEquals(SubtitleMimes.SSA, mkvMime("S_TEXT/ASS"))
        assertEquals(SubtitleMimes.SUBRIP, mkvMime("S_TEXT/UTF8"))
        assertEquals(SubtitleMimes.VTT, mkvMime("D_WEBVTT/SUBTITLES"))
        assertEquals(SubtitleMimes.PGS, mkvMime("S_HDMV/PGS"))
        val ssa = mkvRawSample(SubtitleMimes.SSA, "3,0,Default,,0,0,0,,Hi".toByteArray(), 1_230)!!.toString(Charsets.UTF_8)
        assertEquals("Dialogue: 0:00:00:00,0:00:01:23,3,0,Default,,0,0,0,,Hi", ssa)
        val cue = SubtitleSamples.decode(SubtitleMimes.SSA, 10_000, ssa.toByteArray())!!
        assertEquals(10_000L to 11_230L, cue.startMs to cue.endMs)
        assertEquals("Hi", cue.text)
        val srt = SubtitleSamples.decode(SubtitleMimes.SUBRIP, 5_000, mkvRawSample(SubtitleMimes.SUBRIP, "Hello".toByteArray(), 3_661_001)!!)!!
        assertEquals(5_000L + 3_661_001L, srt.endMs)
        assertNull(mkvRawSample(SubtitleMimes.PGS, byteArrayOf(1), 1))
        val webm = mkvRawSample(SubtitleMimes.VTT, "id1\nline:90%\nHi\nthere".toByteArray(), 1_000, webmVtt = true)!!
        assertEquals("Hi\nthere", SubtitleSamples.decode(SubtitleMimes.VTT, 0, webm)!!.text)
    }

    @Test
    fun `ebml and cluster helpers`() {
        assertEquals(1, vintLength(0x81))
        assertEquals(4, vintLength(0x1A))
        assertEquals(8, vintLength(0x01))
        assertEquals(0, vintLength(0))
        // TrackNumber (D7) = 2, CodecID (86) = "S_TEXT/ASS"
        val b = byteArrayOf(0xD7.toByte(), 0x81.toByte(), 2, 0x86.toByte(), 0x8A.toByte()) + "S_TEXT/ASS".toByteArray()
        val seen = mutableListOf<Pair<Long, String>>()
        children(b, 0, b.size) { id, s, e -> seen += id to b.copyOfRange(s, e).joinToString(",") }
        assertEquals(listOf(0xD7L, 0x86L), seen.map { it.first })
        val cluster = byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75, 0x81.toByte(), 0xE7.toByte(), 0x81.toByte(), 0)
        assertTrue(isClusterAt(cluster, 0, cluster.size))
        assertFalse(isClusterAt(cluster.copyOf().also { it[5] = 0x11 }, 0, cluster.size))
    }
}
