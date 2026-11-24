// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** 16-bit PCM WAV files: writing KittenTTS output and checking a clip (duration, loudness) without playing it. */
internal object Wav {
    /** Mono 16-bit PCM WAV of [samples] (-1..1). */
    fun bytes(samples: FloatArray, sampleRate: Int): ByteArray {
        val dataLen = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVEfmt ".toByteArray())
        buf.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataLen)
        for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        return buf.array()
    }

    /** What a clip holds: [durationMs] and the RMS of its samples (0 = silence, 1 = full scale). */
    data class Info(val sampleRate: Int, val channels: Int, val durationMs: Long, val rms: Double)

    /** The interleaved 16-bit samples of a WAV. */
    class Pcm(val sampleRate: Int, val channels: Int, val samples: ShortArray)

    /** Reads a 16-bit PCM WAV (any channel count; chunks other than `fmt ` / `data` are skipped); null if it is not one. */
    fun info(file: File): Info? = info(file.readBytes())

    fun info(bytes: ByteArray): Info? {
        val pcm = pcm(bytes) ?: return null
        var sum = 0.0
        for (v in pcm.samples) {
            val d = v / 32768.0
            sum += d * d
        }
        val frames = pcm.samples.size / pcm.channels
        return Info(pcm.sampleRate, pcm.channels, frames * 1000L / pcm.sampleRate, if (pcm.samples.isEmpty()) 0.0 else sqrt(sum / pcm.samples.size))
    }

    fun pcm(bytes: ByteArray): Pcm? {
        if (bytes.size < 12 || String(bytes, 0, 4, Charsets.ISO_8859_1) != "RIFF" || String(bytes, 8, 4, Charsets.ISO_8859_1) != "WAVE") return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var rate = 0
        var channels = 0
        var bits = 0
        var format = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
            var len = b.getInt(pos + 4).toLong() and 0xffffffffL
            val start = pos + 8
            if (id == "fmt " && start + 16 <= bytes.size) {
                format = b.getShort(start).toInt() and 0xffff
                channels = b.getShort(start + 2).toInt()
                rate = b.getInt(start + 4)
                bits = b.getShort(start + 14).toInt()
            } else if (id == "data") {
                // 1 = PCM, 0xFFFE = WAVE_FORMAT_EXTENSIBLE (SAPI and mpv may write it for 16-bit PCM too).
                if (rate <= 0 || channels <= 0 || bits != 16 || (format != 1 && format != 0xfffe)) return null
                // Streams written by a still-running writer (mpv's ao=pcm) may declare 0 or 0xffffffff: take what is there.
                if (len == 0L || start + len > bytes.size) len = (bytes.size - start).toLong()
                val frames = (len / (2 * channels)).toInt()
                val samples = ShortArray(frames * channels)
                for (i in samples.indices) samples[i] = b.getShort(start + i * 2)
                return Pcm(rate, channels, samples)
            }
            pos = start + len.toInt() + (len.toInt() and 1)
        }
        return null
    }
}
