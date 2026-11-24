// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import java.io.ByteArrayOutputStream

/**
 * A minimal FLAC writer: 16-bit PCM in VERBATIM subframes (no compression, no MD5). The FFmpeg inside MediaMP's libmpv runtime has
 * no WAV demuxer but does read FLAC, so the narrator hands mpv its WAV clips (espeak-ng, SAPI, KittenTTS) as FLAC. Pure and tested
 * against ffprobe-free checks (round trip through [decodeVerbatim]) and real libmpv playback.
 */
internal object Flac {
    private const val BLOCK = 4096

    /** FLAC of [samples] (interleaved, [channels] 1..8) at [sampleRate]. */
    fun encode(samples: ShortArray, channels: Int, sampleRate: Int): ByteArray {
        require(channels in 1..8 && sampleRate in 1..655_350 && samples.size % channels == 0)
        val frames = samples.size / channels
        val out = ByteArrayOutputStream(samples.size * 2 + 1024)
        out.write("fLaC".toByteArray())
        // STREAMINFO, the last (only) metadata block: 34 bytes.
        val info = BitWriter()
        info.bits(1, 1).bits(0, 7).bits(34, 24)
        info.bits(BLOCK, 16).bits(BLOCK, 16).bits(0, 24).bits(0, 24)
        info.bits(sampleRate.toLong(), 20).bits((channels - 1).toLong(), 3).bits(15, 5).bits(frames.toLong(), 36)
        repeat(16) { info.bits(0, 8) }
        out.write(info.bytes())
        var start = 0
        var number = 0L
        while (start < frames) {
            val n = minOf(BLOCK, frames - start)
            out.write(frame(samples, channels, start, n, number++))
            start += n
        }
        return out.toByteArray()
    }

    private fun frame(samples: ShortArray, channels: Int, start: Int, n: Int, number: Long): ByteArray {
        val w = BitWriter()
        // Sync, fixed block size; block size: 16-bit (n - 1) at the end of the header; sample rate: from STREAMINFO;
        // channels: independent; 16 bits per sample.
        w.bits(0b11111111111110, 14).bits(0, 1).bits(0, 1)
        w.bits(0b0111, 4).bits(0b0000, 4)
        w.bits((channels - 1).toLong(), 4).bits(0b100, 3).bits(0, 1)
        for (b in utf8Number(number)) w.bits(b.toLong() and 0xff, 8)
        w.bits((n - 1).toLong(), 16)
        w.bits(crc8(w.bytes()).toLong(), 8)
        for (c in 0 until channels) {
            w.bits(0b00000010, 8) // VERBATIM, no wasted bits
            for (i in 0 until n) w.bits(samples[(start + i) * channels + c].toLong() and 0xffff, 16)
        }
        val body = w.bytes()
        val crc = crc16(body)
        return body + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    /** FLAC's "UTF-8" coding of a frame number (up to 36 bits). */
    internal fun utf8Number(v: Long): ByteArray {
        if (v < 0x80) return byteArrayOf(v.toByte())
        val bytes = when {
            v < 0x800 -> 2
            v < 0x10000 -> 3
            v < 0x200000 -> 4
            v < 0x4000000 -> 5
            v < 0x80000000L -> 6
            else -> 7
        }
        val out = ByteArray(bytes)
        var x = v
        for (i in bytes - 1 downTo 1) {
            out[i] = (0x80 or (x and 0x3f).toInt()).toByte()
            x = x shr 6
        }
        val lead = if (bytes == 7) 0xfe else ((0xff00 shr bytes) and 0xff)
        out[0] = (lead or x.toInt()).toByte()
        return out
    }

    internal fun crc8(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor (b.toInt() and 0xff)
            repeat(8) { crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xff else (crc shl 1) and 0xff }
        }
        return crc
    }

    internal fun crc16(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xff) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x8005) and 0xffff else (crc shl 1) and 0xffff }
        }
        return crc
    }

    /** The FLAC of a 16-bit PCM WAV; null when [wav] is not one. */
    fun fromWav(wav: ByteArray): ByteArray? {
        val pcm = Wav.pcm(wav) ?: return null
        return encode(pcm.samples, pcm.channels, pcm.sampleRate)
    }

    /** Decodes what [encode] writes (tests): (sample rate, channels, samples), checking every CRC. */
    internal fun decodeVerbatim(flac: ByteArray): Triple<Int, Int, ShortArray> {
        require(String(flac, 0, 4, Charsets.ISO_8859_1) == "fLaC")
        val si = 8
        val rate = ((flac[si + 10].toInt() and 0xff) shl 12) or ((flac[si + 11].toInt() and 0xff) shl 4) or ((flac[si + 12].toInt() and 0xff) shr 4)
        val channels = ((flac[si + 12].toInt() shr 1) and 0x07) + 1
        val out = ArrayList<Short>()
        var pos = 8 + 34
        while (pos < flac.size) {
            require(flac[pos] == 0xff.toByte() && flac[pos + 1] == 0xf8.toByte()) { "lost sync at $pos" }
            var p = pos + 4
            val first = flac[p].toInt() and 0xff
            p += when {
                first < 0x80 -> 1
                first >= 0xfe -> 7
                else -> Integer.numberOfLeadingZeros(first.inv() and 0xff) - 24
            }
            val n = (((flac[p].toInt() and 0xff) shl 8) or (flac[p + 1].toInt() and 0xff)) + 1
            p += 2
            require(crc8(flac.copyOfRange(pos, p)) == (flac[p].toInt() and 0xff)) { "header CRC" }
            p++
            val chans = Array(channels) { ShortArray(n) }
            for (c in 0 until channels) {
                require(flac[p] == 0x02.toByte()) { "not verbatim" }
                p++
                for (i in 0 until n) {
                    chans[c][i] = (((flac[p].toInt() and 0xff) shl 8) or (flac[p + 1].toInt() and 0xff)).toShort()
                    p += 2
                }
            }
            val crc = ((flac[p].toInt() and 0xff) shl 8) or (flac[p + 1].toInt() and 0xff)
            require(crc16(flac.copyOfRange(pos, p)) == crc) { "frame CRC" }
            p += 2
            for (i in 0 until n) for (c in 0 until channels) out += chans[c][i]
            pos = p
        }
        return Triple(rate, channels, out.toShortArray())
    }

    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var acc = 0L
        private var count = 0

        fun bits(value: Long, n: Int): BitWriter {
            for (i in n - 1 downTo 0) {
                acc = (acc shl 1) or ((value shr i) and 1)
                count++
                if (count == 8) {
                    out.write(acc.toInt())
                    acc = 0
                    count = 0
                }
            }
            return this
        }

        fun bits(value: Int, n: Int): BitWriter = bits(value.toLong(), n)

        fun bytes(): ByteArray {
            check(count == 0) { "not byte aligned" }
            return out.toByteArray()
        }
    }
}
