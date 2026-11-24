// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.min

// 0.4.0 (sub-translation worker): the full cue list of an EMBEDDED subtitle track (MKV / WebM / MP4), so pre-translation and
// look-ahead work for the common case (torrent MKVs with ASS tracks on libmpv, Media3 MKV / MP4) and not only for sidecar files.
//
// Media3's own extractors read the container (MatroskaExtractor / FragmentedMp4Extractor / Mp4Extractor with
// FLAG_EMIT_RAW_SUBTITLE_DATA, so text samples arrive as the container stores them, not transcoded to Media3 cues); an ExtractorOutput
// keeps only text tracks and discards every audio / video sample. Samples become [TimedCue]s via [SubtitleSamples].
//
// Sources ([MediaByteSource]): a local file / content URI, the torrent's loopback HTTP stream limited to the downloaded byte ranges
// (never asks the torrent for a piece it does not have: reading stops at a missing range, waits, and resyncs at the next Matroska
// Cluster), or a remote HTTP URL (Range requests, starts near the playhead and stays at most a few minutes ahead of it, with a byte
// budget, so it never downloads the whole file next to the player).
//
// This file is the engine-neutral part (byte sources, track matching, sample decoding); the Media3 extractor driver is
// [EmbeddedSubtitleExtractor] (androidMain).

/**
 * Media3 `MimeTypes` of the subtitle formats [SubtitleSamples] knows, as plain strings so the decoding is shared with desktop
 * (the extractor's `Format.sampleMimeType` carries these exact values; `EmbeddedSubtitlesTest` checks them against Media3).
 */
internal object SubtitleMimes {
    const val SSA = "text/x-ssa"
    const val SUBRIP = "application/x-subrip"
    const val VTT = "text/vtt"
    const val TX3G = "application/x-quicktime-tx3g"
    const val MP4VTT = "application/x-mp4-vtt"
    const val PGS = "application/pgs"
    const val VOBSUB = "application/vobsub"
    const val DVBSUBS = "application/dvbsubs"
}

/** The next byte is not downloaded yet (torrent): the driver waits and resyncs. */
class DataNotAvailableException(val position: Long) : IOException("byte $position not available yet")

/** Sequential reader of a [MediaByteSource] from one position. */
abstract class RawReader : Closeable {
    /** Like `InputStream.read`; -1 at the end. */
    abstract fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/** Random-access bytes of a media file with what is readable right now. */
interface MediaByteSource : Closeable {
    /** Total size; -1 until [prepare] (or when unknown). */
    val length: Long

    /** Reading costs network data (pacing + budget apply). */
    val remote: Boolean

    /** Resolves [length] (an HTTP probe); may throw [IOException]. */
    suspend fun prepare() {}

    /** Byte ranges readable now without waiting; null = all of it. */
    fun available(): List<LongRange>? = null

    fun openAt(position: Long): RawReader

    /** One read at [position] (scanning for a resync point); -1 at the end. */
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/** A local file / content URI ([FileChannel]); [availability] limits it to what a torrent has downloaded (null = complete). */
class ChannelByteSource(
    private val channel: FileChannel,
    private val onClose: Closeable? = null,
    private val availability: (() -> List<LongRange>?)? = null,
) : MediaByteSource {
    override val length: Long get() = runCatching { channel.size() }.getOrDefault(-1L)
    override val remote = false

    override fun available(): List<LongRange>? = availability?.invoke()

    override fun openAt(position: Long): RawReader = object : RawReader() {
        private var pos = position

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val n = channel.read(ByteBuffer.wrap(buffer, offset, length), pos)
            if (n > 0) pos += n
            return n
        }

        override fun close() {}
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int = channel.read(ByteBuffer.wrap(buffer, offset, length), position)

    override fun close() {
        runCatching { channel.close() }
        runCatching { onClose?.close() }
    }
}

/** What one embedded subtitle track is (container order = [ordinal] among the container's subtitle tracks). */
data class EmbeddedTrack(
    val id: Int,
    val ordinal: Int,
    val mime: String?,
    val language: String?,
    val label: String?,
) {
    val image: Boolean get() = SubtitleSamples.isImage(mime)
    val text: Boolean get() = SubtitleSamples.isText(mime)
}

/** The player's selected track, as the engine describes it (see [SubtitleOption.embeddedOrdinal] / [SubtitleOption.codec]). */
data class EmbeddedTrackRef(val ordinal: Int?, val language: String?, val title: String?, val codec: String?)

/** Snapshot of an extraction. [cues] / [skipped] per [EmbeddedTrack.id]; [complete] = the whole file was read. */
data class EmbeddedSubtitlesState(
    val tracks: List<EmbeddedTrack> = emptyList(),
    val cues: Map<Int, List<TimedCue>> = emptyMap(),
    val skipped: Map<Int, Set<String>> = emptyMap(),
    val complete: Boolean = false,
    /** Blocked on bytes the torrent has not downloaded yet (everything available so far is read). */
    val waiting: Boolean = false,
    /** Stopped for good without reading everything (budget, unsupported container, broken file); null = fine. */
    val failure: String? = null,
    val finished: Boolean = false,
)

/** Track matching between the engine's list and the container's (pure). */
object EmbeddedTrackMatcher {
    /**
     * The container track the player selected: same [EmbeddedTrackRef.ordinal] (both engines and Media3's extractors list tracks in
     * container order) when its language agrees; else the one with the same language (+ title); else the ordinal / the only track.
     */
    fun match(tracks: List<EmbeddedTrack>, want: EmbeddedTrackRef): EmbeddedTrack? {
        if (tracks.isEmpty()) return null
        val wantLang = LanguageCodes.normalize(want.language)
        fun langOf(t: EmbeddedTrack) = LanguageCodes.normalize(t.language)
        val byOrdinal = want.ordinal?.let { o -> tracks.firstOrNull { it.ordinal == o } }
        if (byOrdinal != null && (wantLang == null || langOf(byOrdinal) == null || langOf(byOrdinal) == wantLang)) return byOrdinal
        if (wantLang != null) {
            val same = tracks.filter { langOf(it) == wantLang }
            same.firstOrNull { !want.title.isNullOrBlank() && it.label == want.title }?.let { return it }
            if (same.size == 1) return same[0]
            if (same.size > 1) return same.minBy { abs(it.ordinal - (want.ordinal ?: 0)) }
        }
        return byOrdinal ?: tracks.singleOrNull()
    }
}

/** One subtitle event decoded from a container sample. [endMs] null = until the next sample (tx3g / MP4 WebVTT). */
data class SampleCue(val startMs: Long, val endMs: Long?, val text: String, val karaoke: Boolean = false, val drawing: Boolean = false)

/** Container text samples -> [SampleCue] (pure, no Android). */
object SubtitleSamples {
    private val IMAGE = setOf(SubtitleMimes.PGS, SubtitleMimes.VOBSUB, SubtitleMimes.DVBSUBS, "image/bmp", "image/png")
    private val TEXT = setOf(SubtitleMimes.SSA, SubtitleMimes.SUBRIP, SubtitleMimes.VTT, SubtitleMimes.TX3G, SubtitleMimes.MP4VTT)

    /** mpv codec names of bitmap subtitles. */
    private val IMAGE_CODECS = setOf("hdmv_pgs_subtitle", "pgssub", "dvd_subtitle", "dvdsub", "dvb_subtitle", "dvbsub", "xsub", "pgs", "vobsub")

    fun isImage(mimeOrCodec: String?): Boolean = mimeOrCodec != null && (mimeOrCodec in IMAGE || mimeOrCodec.lowercase() in IMAGE_CODECS)

    fun isText(mime: String?): Boolean = mime != null && mime in TEXT

    /**
     * MatroskaExtractor (raw) writes SSA blocks as `Dialogue: 0:00:00:00,<duration h:mm:ss:cc>,ReadOrder,Layer,Style,Name,MarginL,
     * MarginR,MarginV,Effect,Text`, SubRip as `1\n00:00:00,000 --> <duration>\n<text>`, WebVTT as `WEBVTT\n\n00:00:00.000 --> <duration>
     * \n<text>`: times relative to the sample time. MP4 tx3g = u16 length + text; MP4 WebVTT = `vttc` boxes with a `payl` child.
     */
    fun decode(mime: String?, sampleMs: Long, data: ByteArray): SampleCue? = when (mime) {
        SubtitleMimes.SSA -> ssa(sampleMs, String(data, Charsets.UTF_8))
        SubtitleMimes.SUBRIP -> block(sampleMs, String(data, Charsets.UTF_8), "srt")
        SubtitleMimes.VTT -> block(sampleMs, String(data, Charsets.UTF_8), "vtt")
        SubtitleMimes.TX3G -> tx3g(sampleMs, data)
        SubtitleMimes.MP4VTT -> mp4Vtt(sampleMs, data)
        else -> null
    }

    private fun ssa(sampleMs: Long, s: String): SampleCue? {
        val body = s.trimStart('﻿').substringAfter("Dialogue:", missingDelimiterValue = "").trimStart()
        if (body.isEmpty()) return null
        val f = body.split(',', limit = 11)
        if (f.size < 11) return null
        val start = ssaTime(f[0]) ?: 0L
        val end = ssaTime(f[1]) ?: return null
        val t = SubtitleText.fromAss(f[10].trimEnd('\r', '\n'))
        return SampleCue(sampleMs + start, sampleMs + end, t.plain, karaoke = t.karaoke, drawing = t.drawingOnly)
    }

    /** `h:mm:ss:cc` (Media3's raw SSA) or `h:mm:ss.cc` (files). */
    internal fun ssaTime(s: String): Long? {
        val p = s.trim().split(':', '.')
        if (p.size != 4) return null
        val h = p[0].toLongOrNull() ?: return null
        val m = p[1].toLongOrNull() ?: return null
        val sec = p[2].toLongOrNull() ?: return null
        val cs = p[3].take(2).padEnd(2, '0').toLongOrNull() ?: return null
        return ((h * 60 + m) * 60 + sec) * 1000 + cs * 10
    }

    private fun block(sampleMs: Long, s: String, format: String): SampleCue? {
        val cue = SubtitleParser.parse(s, format).firstOrNull() ?: return null
        return SampleCue(sampleMs + cue.startMs, sampleMs + cue.endMs, cue.text)
    }

    private fun tx3g(sampleMs: Long, data: ByteArray): SampleCue? {
        if (data.size < 2) return SampleCue(sampleMs, null, "")
        val len = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val n = min(len, data.size - 2)
        if (n <= 0) return SampleCue(sampleMs, null, "")
        val utf16 = n >= 2 && ((data[2] == 0xFE.toByte() && data[3] == 0xFF.toByte()) || (data[2] == 0xFF.toByte() && data[3] == 0xFE.toByte()))
        val text = String(data, 2, n, if (utf16) Charsets.UTF_16 else Charsets.UTF_8)
        return SampleCue(sampleMs, null, SubtitleParser.clean(text))
    }

    private fun mp4Vtt(sampleMs: Long, data: ByteArray): SampleCue {
        val parts = ArrayList<String>()
        fun boxes(from: Int, to: Int, visit: (type: String, start: Int, end: Int) -> Unit) {
            var p = from
            while (p + 8 <= to) {
                val size = ((data[p].toInt() and 0xFF) shl 24) or ((data[p + 1].toInt() and 0xFF) shl 16) or
                    ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
                if (size < 8 || p + size > to) break
                visit(String(data, p + 4, 4, Charsets.US_ASCII), p + 8, p + size)
                p += size
            }
        }
        boxes(0, data.size) { type, s, e ->
            if (type == "vttc") boxes(s, e) { inner, s2, e2 -> if (inner == "payl") parts += String(data, s2, e2 - s2, Charsets.UTF_8) }
        }
        return SampleCue(sampleMs, null, SubtitleParser.clean(parts.joinToString("\n")))
    }
}
