// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.TtsEngine
import app.nijiyome.novel.CloudTts
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.1: the pure parts of the desktop file voices (espeak-ng, SAPI), the catalog's choice of system voice and the WAV checks. */
class FileVoicesTest {
    @Test
    fun espeakPitchAndVoiceListing() {
        assertEquals(50, EspeakVoice.espeakPitch(1f))
        assertEquals(25, EspeakVoice.espeakPitch(0.5f))
        assertEquals(99, EspeakVoice.espeakPitch(2f))
        val v = EspeakVoice.parseVoices(
            "Pty Language       Age/Gender VoiceName          File                 Other Languages\n" +
                " 5  es              --/M      Spanish_(Spain)    roa/es\n" +
                " 5  en-us           --/F      English_(America)  gmw/en-US            (en 3)\n",
        )
        assertEquals(listOf("es", "en-us"), v.map { it.id })
        assertEquals("English (America) (en-us)", v[1].label)
    }

    @Test
    fun sapiSsmlAndVoices() {
        assertEquals(
            "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"es-AR\"><prosody pitch=\"+20%\">A &amp; &lt;b&gt; &quot;c&quot;</prosody></speak>",
            SapiVoice.ssml("A & <b> \"c\"", 20, "es-AR"),
        )
        assertTrue(SapiVoice.ssml("x", -50, "en").contains("pitch=\"-50%\""))
        assertEquals(50, SapiVoice.sapiPitchPercent(2f))
        assertEquals(-50, SapiVoice.sapiPitchPercent(0.5f))
        val v = SapiVoice.parseVoices("Microsoft Sabina Desktop|es-MX|Female\n\nX||NotSet\n")
        assertEquals(listOf("Microsoft Sabina Desktop", "X"), v.map { it.id })
        assertEquals("es-MX", v[0].lang)
        assertNull(v[1].lang)
    }

    @Test
    fun theCatalogPicksSpeechDispatcherThenEspeakThenNothingAndSapiOnWindows() {
        val cloud = CloudTts(OkHttpClient())
        val espeak = File("/usr/bin/espeak-ng")
        fun catalog(windows: Boolean, spd: Boolean, found: Set<String>) =
            DesktopVoiceCatalog(cloud, { null }, kitten = null, windows = windows, speechDispatcher = { spd }, find = { n -> if (n in found) File("/x/$n") else null })
        assertTrue(catalog(false, true, setOf("espeak-ng")).system() is SpeechDispatcherVoice)
        assertTrue(catalog(false, false, setOf("espeak-ng")).system() is EspeakVoice)
        assertNull(catalog(false, false, emptySet()).system())
        assertTrue(catalog(true, true, setOf("powershell")).system() is SapiVoice)
        assertNull(catalog(true, false, emptySet()).system())
        assertTrue(catalog(false, false, emptySet()).forEngine(TtsEngine.OPENAI) is CloudVoice)
        assertNull(catalog(false, false, emptySet()).forEngine(TtsEngine.KITTEN))
        assertTrue(espeak.path.isNotEmpty())
    }

    @Test
    fun wavInfoMeasuresDurationAndLoudness() {
        val rate = 24_000
        val tone = FloatArray(rate / 2) { (0.5 * sin(2 * PI * 440 * it / rate)).toFloat() }
        val info = Wav.info(Wav.bytes(tone, rate))!!
        assertEquals(500, info.durationMs)
        assertEquals(1, info.channels)
        assertTrue(info.rms in 0.3..0.4, "${info.rms}")
        assertEquals(0.0, Wav.info(Wav.bytes(FloatArray(rate), rate))!!.rms)
        assertNull(Wav.info("not a wav".toByteArray()))
        val f = Files.createTempFile("nijiyome", ".wav").toFile()
        f.writeBytes(Wav.bytes(tone, rate))
        assertEquals(500, Wav.info(f)!!.durationMs)
        f.delete()
    }

    @Test
    fun flacOfAWavRoundTripsWithItsCrcs() {
        assertTrue(Flac.utf8Number(0x7f).contentEquals(byteArrayOf(0x7f)))
        assertTrue(Flac.utf8Number(0x80).contentEquals(byteArrayOf(0xc2.toByte(), 0x80.toByte())))
        assertTrue(Flac.utf8Number(0x800).contentEquals(byteArrayOf(0xe0.toByte(), 0xa0.toByte(), 0x80.toByte())))
        // CRC-8 / CRC-16 check values of FLAC's polynomials for "123456789".
        assertEquals(0xf4, Flac.crc8("123456789".toByteArray()))
        assertEquals(0xfee8, Flac.crc16("123456789".toByteArray()))
        val rate = 22_050
        val tone = FloatArray(rate * 3 / 2) { (0.4 * sin(2 * PI * 300 * it / rate)).toFloat() }
        val wav = Wav.bytes(tone, rate)
        val flac = Flac.fromWav(wav)!!
        val (r, ch, samples) = Flac.decodeVerbatim(flac)
        assertEquals(rate, r)
        assertEquals(1, ch)
        assertTrue(samples.contentEquals(Wav.pcm(wav)!!.samples))
        assertNull(Flac.fromWav("nope".toByteArray()))
        // Stereo, a partial last block.
        val stereo = ShortArray(2 * 5000) { (it * 7).toShort() }
        val (_, ch2, s2) = Flac.decodeVerbatim(Flac.encode(stereo, 2, 44_100))
        assertEquals(2, ch2)
        assertTrue(s2.contentEquals(stereo))
    }

    @Test
    fun ssmlDropsWhatXmlCannotHold() {
        assertEquals("ab\tc\nd😀", SapiVoice.xmlText("a\u0000b\u0007\tc\nd\u000B\uFFFE😀\uD800"))
        val ssml = SapiVoice.ssml("Uno\u0001 & dos\u001F", 0, "es")
        assertTrue(ssml.contains(">Uno &amp; dos</prosody>"), ssml)
    }
}
