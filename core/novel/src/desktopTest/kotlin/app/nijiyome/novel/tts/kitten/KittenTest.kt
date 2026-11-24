// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts.kitten

import app.nijiyome.model.TtsSettings
import app.nijiyome.novel.LocalVoiceModel
import app.nijiyome.novel.NarratorProblem
import app.nijiyome.novel.TtsException
import app.nijiyome.novel.TtsFailure
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 1.1: KittenTTS's text front end, model files and pinned installer (the cases of Tatakai Next's tests, plus the downloads). */
class KittenTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-kitten").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    @Test
    fun symbolTableMatchesKittenTts() {
        val m = KittenText.symbolIds
        assertEquals(0L, m['$'])
        assertEquals(1L, m[';'])
        assertEquals(10L, m['…'])
        assertEquals(11L, m['"'])
        assertEquals(16L, m[' '])
        assertEquals(17L, m['A'])
        assertEquals(43L, m['a'])
        assertEquals(69L, m['ɑ'])
        assertContentEquals(longArrayOf(0, 43, 10, 0), KittenText.inputIds("a"))
        val ipa = "həlˈoʊ"
        assertContentEquals(longArrayOf(0) + ipa.map { m.getValue(it) }.toLongArray() + longArrayOf(10, 0), KittenText.inputIds(ipa))
    }

    @Test
    fun tokenizesLikeTheSdk() {
        assertEquals("həlˈoʊ , wˈɜːld !", KittenText.tokenizePhonemes("həlˈoʊ, wˈɜːld!"))
        assertEquals("", KittenText.tokenizePhonemes("  "))
    }

    @Test
    fun numbersBecomeWordsAndUrlsGo() {
        assertEquals("zero", KittenText.numberWords(0))
        assertEquals("twenty-one", KittenText.numberWords(21))
        assertEquals("one thousand two hundred thirty-four", KittenText.numberWords(1_234))
        assertEquals("two million five", KittenText.numberWords(2_000_005))
        assertEquals("Chapter twelve. See now", KittenText.preprocess("Chapter 12. See https://x.y now"))
        assertEquals("pi is three point one four, ok", KittenText.preprocess("pi is 3.14, ok"))
        assertEquals("the 2nd minus five", KittenText.preprocess("the 2nd -5"))
    }

    @Test
    fun chunksSentencesAndLongText() {
        assertEquals(listOf("Hello there.", "Mr. Smith left!", "Done,"), KittenText.chunkText("Hello there. Mr. Smith left! Done", 400))
        assertEquals(listOf("3.5 apples."), KittenText.chunkText("3.5 apples.", 400))
        val chunks = KittenText.chunkText("word ".repeat(30), 40)
        assertTrue(chunks.size > 2)
        assertTrue(chunks.all { it.length <= 40 })
        assertTrue(KittenText.chunkText("   ", 400).isEmpty())
    }

    @Test
    fun phrasesKeepTheirPunctuation() {
        assertEquals(
            listOf(KittenText.Piece.Phrase("Hello"), KittenText.Piece.Literal(", "), KittenText.Piece.Phrase("world"), KittenText.Piece.Literal(". "), KittenText.Piece.Phrase("Bye")),
            KittenText.splitPhrases("Hello, world. Bye"),
        )
        assertEquals(listOf(KittenText.Piece.Phrase("End"), KittenText.Piece.Literal("! ")), KittenText.splitPhrases("End!"))
        assertEquals("həlˈoʊ wˈɜːld", KittenText.cleanIpa(" həlˈoʊ\n wˈɜːld \n"))
    }

    private fun npy(rows: Int, cols: Int): ByteArray {
        val header = StringBuilder("{'descr': '<f4', 'fortran_order': False, 'shape': ($rows, $cols), }")
        while ((10 + header.length + 1) % 64 != 0) header.append(' ')
        header.append('\n')
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x93.toByte()) + "NUMPY".toByteArray() + byteArrayOf(1, 0))
        out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(header.length.toShort()).array())
        out.write(header.toString().toByteArray())
        val data = ByteBuffer.allocate(rows * cols * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until rows * cols) data.putFloat(i.toFloat())
        out.write(data.array())
        return out.toByteArray()
    }

    @Test
    fun readsVoiceMatricesConfigAndResolvesVoices() {
        val m = KittenModel.npy(npy(3, 2))
        assertEquals(3 to 2, m.rows to m.cols)
        assertContentEquals(floatArrayOf(0f, 1f), m.styleFor(0))
        assertContentEquals(floatArrayOf(4f, 5f), m.styleFor(99))
        assertFailsWith<IllegalArgumentException> { KittenModel.npy("nope".toByteArray()) }
        val zip = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z ->
                z.putNextEntry(ZipEntry("expr-voice-2-f.npy"))
                z.write(npy(2, 4))
                z.closeEntry()
            }
        }.toByteArray()
        val voices = KittenModel.npz(zip)
        assertEquals(4, voices.getValue("expr-voice-2-f").cols)
        val aliases = mapOf("Bella" to "expr-voice-2-f")
        assertEquals("expr-voice-2-f", KittenModel.resolveVoice(aliases, voices, "Bella"))
        assertEquals("expr-voice-2-f", KittenModel.resolveVoice(aliases, voices, "Nobody"))

        // The real nano-int8 config.json (pinned, see KittenRelease).
        val c = KittenModel.config(
            """{"name":"Kitten TTS Nano","version":"0.8","type":"ONNX2","voices":"voices.npz","model_file":"kitten_tts_nano_v0_8.onnx",
               "speed_priors":{"expr-voice-3-f":0.8,"expr-voice-4-m":0.9},"voice_aliases":{"Luna":"expr-voice-3-f"}}""",
        )
        assertEquals("kitten_tts_nano_v0_8.onnx", c.modelFile)
        assertEquals(0.9f, c.speedPriors["expr-voice-4-m"])
        assertEquals("expr-voice-3-f", c.aliases["Luna"])
        assertEquals(24_000, c.sampleRate)
        assertFailsWith<IllegalStateException> { KittenModel.config("""{"model_file":"../x.onnx"}""") }
        assertFailsWith<IllegalStateException> { KittenModel.config("""{"type":"KITTEN2","model_file":"a.onnx"}""") }
    }

    @Test
    fun pinsTheReleaseFilesAndKnowsItsPlatforms() {
        val pins = KittenRelease.PINS
        assertTrue(pins.runtime.url.startsWith("https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.29.0/"))
        val files = pins.models.getValue("nano-int8")
        assertEquals(listOf("config.json", "kitten_tts_nano_v0_8.onnx", "voices.npz"), files.map { it.name })
        assertTrue(files.all { it.url.startsWith("https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8/resolve/84781d74e29ee25217551556398b42f80593a813/") })
        assertTrue((files + pins.runtime).all { it.sha256.matches(Regex("[0-9a-f]{64}")) && it.size > 0 })
        assertTrue(KittenRelease.platformSupported("Linux", "amd64"))
        assertTrue(KittenRelease.platformSupported("Windows 11", "amd64"))
        assertFalse(KittenRelease.platformSupported("Windows 11", "x86"))
        assertFalse(KittenRelease.platformSupported("FreeBSD", "amd64"))
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun installsEveryPinnedFileWithProgressRefusesABadOneAndRemoves(): Unit = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val jar = ByteArray(200_000) { (it % 251).toByte() }
            val config = """{"model_file":"m.onnx","voices":"voices.npz"}""".toByteArray()
            val model = ByteArray(3000) { 7 }
            val bad = ByteArray(10) { 1 }
            val bodies = mapOf("/ort.jar" to jar, "/config.json" to config, "/m.onnx" to model, "/voices.npz" to bad)
            server.dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                    val body = bodies[request.url.encodedPath] ?: return MockResponse.Builder().code(404).build()
                    return MockResponse.Builder().body(Buffer().write(body)).build()
                }
            }
            fun pin(name: String, path: String, bytes: ByteArray, sha: String = sha(bytes)) = PinnedFile(name, server.url(path).toString(), sha, bytes.size.toLong())
            val good = KittenPins(
                runtime = pin("onnxruntime.jar", "/ort.jar", jar),
                runtimeVersion = "test",
                models = mapOf("t" to listOf(pin("config.json", "/config.json", config), pin("m.onnx", "/m.onnx", model), pin("voices.npz", "/voices.npz", bad, sha = "0".repeat(64)))),
                modelInfo = listOf(LocalVoiceModel("t", "Test", 1)),
                defaultModel = "t",
            )
            val dir = File(tmp, "kitten")
            val kitten = Kitten(dir, OkHttpClient(), phonemizer = { null }, model = { "t" }, pins = good, platformSupported = { true })
            assertEquals(NarratorProblem.NOT_INSTALLED, kitten.status().problem)
            val r = kitten.install("t")
            assertTrue(r.isFailure)
            assertTrue(r.exceptionOrNull()!!.message!!.contains("checksum"), r.exceptionOrNull()!!.message)
            assertFalse(kitten.installed("t"))
            assertTrue(File(dir, "runtime/onnxruntime.jar").isFile, "the files that matched stay")
            assertFalse(File(dir, "models/t/voices.npz").exists())
            assertFalse(File(dir, "models/t/voices.npz.part").exists())
            assertEquals(null, kitten.progress.value)

            // With the right hash the install completes; espeak-ng is then what is missing.
            val fixed = good.copy(models = mapOf("t" to good.models.getValue("t").dropLast(1) + pin("voices.npz", "/voices.npz", bad)))
            val k2 = Kitten(dir, OkHttpClient(), phonemizer = { null }, model = { "t" }, pins = fixed, platformSupported = { true })
            assertTrue(k2.install("t").isSuccess)
            assertTrue(k2.installed("t"))
            assertEquals(NarratorProblem.NO_PHONEMIZER, k2.status().problem)
            val e = assertFailsWith<TtsException> { k2.synthesize("Hello.", TtsSettings(), File(tmp, "x.wav")) }
            assertEquals(TtsFailure.PHONEMIZER_MISSING, e.reason)
            assertTrue(k2.remove().isSuccess)
            assertFalse(dir.exists())
            assertEquals(NarratorProblem.UNSUPPORTED_PLATFORM, Kitten(dir, OkHttpClient(), pins = fixed, platformSupported = { false }).status().problem)
        } finally {
            server.close()
        }
    }

    @Test
    fun aFailedDownloadLeavesNoPartialFile(): Unit = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val jar = ByteArray(5000) { 3 }
            server.dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/big.jar" -> MockResponse.Builder().body(Buffer().write(jar)).build()
                    else -> MockResponse.Builder().code(503).build()
                }
            }
            val dir = File(tmp, "kitten")
            fun pins(path: String, size: Long) = KittenPins(
                runtime = PinnedFile("onnxruntime.jar", server.url(path).toString(), sha(jar), size),
                runtimeVersion = "test",
                models = mapOf("t" to emptyList()),
                modelInfo = listOf(LocalVoiceModel("t", "Test", 1)),
                defaultModel = "t",
            )
            // Larger than pinned: stops while streaming.
            val tooBig = Kitten(dir, OkHttpClient(), phonemizer = { null }, pins = pins("/big.jar", 1000), platformSupported = { true })
            assertTrue(tooBig.install("t").exceptionOrNull()!!.message!!.contains("larger"))
            assertFalse(File(dir, "runtime/onnxruntime.jar.part").exists())
            assertFalse(File(dir, "runtime/onnxruntime.jar").exists())
            // Server error.
            val down = Kitten(dir, OkHttpClient(), phonemizer = { null }, pins = pins("/missing.jar", 5000), platformSupported = { true })
            assertTrue(down.install("t").isFailure)
            assertTrue(File(dir, "runtime").listFiles().orEmpty().isEmpty())
            assertEquals(null, down.progress.value)
            // Unloading / closing with nothing loaded is harmless, and twice too.
            down.unload()
            down.close()
            down.close()
        } finally {
            server.close()
        }
    }

    @Test
    fun aRemovalLeftForTheNextStartFinishesThen() {
        val dir = File(tmp, "tts/kitten")
        File(dir, "runtime").mkdirs()
        File(dir, "runtime/onnxruntime.dll").writeText("locked on Windows")
        val marker = File(tmp, "tts/kitten.remove")
        marker.writeText("")
        Kitten(dir, OkHttpClient(), phonemizer = { null }, platformSupported = { true })
        assertFalse(dir.exists())
        assertFalse(marker.exists())
        // Without the marker an install is left alone.
        File(dir, "runtime").mkdirs()
        Kitten(dir, OkHttpClient(), phonemizer = { null }, platformSupported = { true })
        assertTrue(File(dir, "runtime").isDirectory)
    }
}
