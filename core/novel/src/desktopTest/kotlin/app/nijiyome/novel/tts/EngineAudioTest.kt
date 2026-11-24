// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.NovelChapterText
import app.nijiyome.model.TtsEngine
import app.nijiyome.model.TtsSettings
import app.nijiyome.model.TtsState
import app.nijiyome.novel.CloudTts
import app.nijiyome.novel.LocalVoiceInstaller
import app.nijiyome.novel.tts.kitten.Kitten
import app.nijiyome.player.mpv.DesktopMpvSessionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 1.1 evidence: every engine this machine has writes real audio to a file, checked without listening (duration, not silent), and
 * libmpv plays the clips to `ao=pcm` (a WAV file) or `ao=null`, never to the speakers.
 * - espeak-ng: `NIJIYOME_TEST_ESPEAK` = an espeak-ng executable (skipped without it);
 * - KittenTTS: `NIJIYOME_TEST_KITTEN_DIR` = a KittenTTS install (`runtime/`, `models/nano-int8/`) plus `NIJIYOME_TEST_ESPEAK`;
 * - cloud: a local OpenAI-compatible server returning an mp3 made by ffmpeg (skipped without ffmpeg).
 * `NIJIYOME_TEST_AUDIO_OUT` keeps the clips (else a temp folder).
 */
class EngineAudioTest {
    private val out: File = System.getenv("NIJIYOME_TEST_AUDIO_OUT")?.let { File(it).apply { mkdirs() } }
        ?: Files.createTempDirectory("nijiyome-tts-audio").toFile()
    private val espeak: File? = System.getenv("NIJIYOME_TEST_ESPEAK")?.let(::File)?.takeIf { it.canExecute() }
    /** A clip path with no file yet (the engines reuse an existing clip as their cache). */
    private fun fresh(name: String): File = File(out, name).also { it.delete() }

    private val sentence = "This is how the narrator sounds with the current settings."

    private fun assertSpeech(file: File, minMs: Long, what: String): Wav.Info {
        val info = assertNotNull(Wav.info(file), "$what: not a 16-bit WAV")
        println("$what: ${file.name} ${info.sampleRate} Hz ${info.channels} ch ${info.durationMs} ms rms=${"%.4f".format(info.rms)}")
        assertTrue(info.durationMs >= minMs, "$what: ${info.durationMs} ms")
        assertTrue(info.rms > 0.01, "$what: silent (rms ${info.rms})")
        return info
    }

    @Test
    fun espeakNgWritesSpeechToAFile(): Unit = runBlocking {
        val exe = espeak
        assumeTrue("NIJIYOME_TEST_ESPEAK not set", exe != null)
        val voice = EspeakVoice(exe!!)
        assertTrue(voice.status().available)
        assertTrue(voice.voices(TtsSettings()).isNotEmpty())
        val normal = voice.synthesize(sentence, TtsSettings(voice = "en-us"), fresh("espeak-ng.wav"))
        val a = assertSpeech(normal, 1_500, "espeak-ng")
        val high = voice.synthesize(sentence, TtsSettings(voice = "en-us", pitch = 1.8f), fresh("espeak-ng-pitch.wav"))
        assertSpeech(high, 1_500, "espeak-ng pitch 1.8")
        assertTrue(a.sampleRate > 0)
        val es = voice.synthesize("Así suena el narrador con los ajustes actuales.", TtsSettings(voice = "es"), fresh("espeak-ng-es.wav"))
        assertSpeech(es, 1_000, "espeak-ng es")
    }

    @Test
    fun kittenTtsWritesSpeechToAFile(): Unit = runBlocking {
        val dir = System.getenv("NIJIYOME_TEST_KITTEN_DIR")?.let(::File)
        assumeTrue("NIJIYOME_TEST_KITTEN_DIR / NIJIYOME_TEST_ESPEAK not set", dir != null && espeak != null)
        val kitten = Kitten(dir!!, OkHttpClient(), phonemizer = { espeak })
        assertTrue((kitten as LocalVoiceInstaller).installed("nano-int8"), "the test install is incomplete")
        val started = System.nanoTime()
        val clip = kitten.synthesize(sentence, TtsSettings(engine = TtsEngine.KITTEN, voice = "Luna"), fresh("kitten-luna.wav"))
        val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        val info = assertSpeech(clip, 1_500, "KittenTTS Luna")
        assertEquals(24_000, info.sampleRate)
        println("KittenTTS: ${info.durationMs} ms of audio in $ms ms (first run loads the model); status: ${kitten.status()}")
        val again = System.nanoTime()
        assertSpeech(kitten.synthesize("A second voice, Hugo, reads this line.", TtsSettings(voice = "Hugo"), fresh("kitten-hugo.wav")), 1_000, "KittenTTS Hugo")
        println("KittenTTS second clip in ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - again)} ms")
        assertTrue(kitten.status().available)
        kitten.close()
    }

    private fun ffmpegMp3(): File? {
        val ffmpeg = Executables.find("ffmpeg") ?: return null
        val mp3 = File(out, "source-2s.mp3")
        val p = ProcessBuilder(ffmpeg.absolutePath, "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-ac", "1", "-b:a", "64k", mp3.absolutePath)
            .redirectErrorStream(true).start()
        p.inputStream.readBytes()
        return mp3.takeIf { p.waitFor() == 0 && it.length() > 0 }
    }

    private fun audioServer(mp3: File, requests: MutableList<String>) = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}"
                return MockResponse.Builder().code(200).addHeader("Content-Type", "audio/mpeg").body(Buffer().write(mp3.readBytes())).build()
            }
        }
        start()
    }

    /** An mpv factory that plays to a WAV file ([pcm]) or nowhere. */
    private fun mpv(pcm: File?): DesktopMpvSessionFactory = DesktopMpvSessionFactory(
        cacheDir = File(out.parentFile ?: out, "nijiyome-tts-mpv-cache"),
        mainDispatcher = Dispatchers.Main,
        extraOptions = if (pcm != null) mapOf("ao" to "pcm", "ao-pcm-file" to pcm.absolutePath) else mapOf("ao" to "null"),
        audioOnly = true,
    )

    @Test
    fun cloudAudioIsDownloadedAndLibmpvPlaysEveryClipToAFile(): Unit = runBlocking {
        val mp3 = ffmpegMp3()
        assumeTrue("ffmpeg missing", mp3 != null)
        val requests = CopyOnWriteArrayList<String>()
        val server = audioServer(mp3!!, requests)
        try {
            val settings = TtsSettings(engine = TtsEngine.OPENAI_COMPATIBLE, endpoint = server.url("/v1").toString(), voice = "af_heart")
            val cloud = CloudVoice(TtsEngine.OPENAI_COMPATIBLE, CloudTts(OkHttpClient())) { null }
            val clip = cloud.synthesize("Hello.", settings, fresh("cloud.mp3"))
            assertTrue(clip.length() > 1000)
            assertTrue(requests.single().startsWith("POST /v1/audio/speech {"), requests.single())

            // Every clip of this run through libmpv to ao=pcm: what the narrator would send to the speakers, written to a WAV.
            val clips = listOfNotNull(clip, File(out, "espeak-ng.wav").takeIf { it.isFile }, File(out, "kitten-luna.wav").takeIf { it.isFile })
            for (c in clips) {
                val pcm = File(out, "mpv-${c.nameWithoutExtension}.wav")
                pcm.delete()
                val player = MpvClipPlayer(mpv(pcm), Dispatchers.Main)
                try {
                    withTimeout(30_000) { player.play(c, 1f) }
                } catch (t: Throwable) {
                    if (t is AssertionError) throw t
                    assumeNoException("libmpv runtime not usable here", t)
                } finally {
                    player.release()
                }
                Thread.sleep(300) // mpv closes the PCM file when the session is released
                val info = assertSpeech(pcm, if (c == clip) 1_800 else 1_000, "libmpv ao=pcm ← ${c.name}")
                if (c == clip) assertTrue(info.durationMs in 1_800..2_400, "${info.durationMs}")
            }
            // Speed: ao=pcm writes faster than real time (the clip is done before any property changes), so time ao=null, which
            // plays at the real pace: at 2× the same 2 s clip takes about half.
            fun timed(speed: Float): Long {
                val player = MpvClipPlayer(mpv(null), Dispatchers.Main)
                val t0 = System.nanoTime()
                try {
                    runBlocking { withTimeout(30_000) { player.play(clip, speed) } }
                } finally {
                    player.release()
                }
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
            }
            val normal = timed(1f)
            val fast = timed(2f)
            println("libmpv ao=null: 2 s clip played in $normal ms at 1x, $fast ms at 2x")
            assertTrue(fast < normal * 0.75, "1x $normal ms, 2x $fast ms")
        } finally {
            server.close()
        }
    }

    @Test
    fun theNarratorReadsAChapterWithACloudVoiceThroughLibmpv(): Unit = runBlocking {
        val mp3 = ffmpegMp3()
        assumeTrue("ffmpeg missing", mp3 != null)
        val requests = CopyOnWriteArrayList<String>()
        val server = audioServer(mp3!!, requests)
        val settings = TtsSettings(engine = TtsEngine.OPENAI_COMPATIBLE, endpoint = server.url("/v1").toString())
        val catalog = object : VoiceCatalog {
            override fun system(): DesktopVoice? = null

            override fun forEngine(engine: TtsEngine): DesktopVoice? = CloudVoice(engine, CloudTts(OkHttpClient())) { null }

            override val localVoice: LocalVoiceInstaller? = null
        }
        val player = MpvClipPlayer(mpv(null), Dispatchers.Main)
        val narrator = DesktopNarrator(catalog, player, File(out, "cache-${System.nanoTime()}"), { settings })
        val seen = CopyOnWriteArrayList<Pair<TtsState, Int?>>()
        val watch = launch(Dispatchers.Default) { narrator.state.collect { seen += it to narrator.currentParagraph.value } }
        try {
            narrator.speak(NovelChapterText("c", "C", listOf("First paragraph.", "", "Second paragraph.")), 0)
            try {
                withTimeout(5_000) { narrator.state.first { it == TtsState.SPEAKING } }
            } catch (t: Throwable) {
                assumeNoException("libmpv runtime not usable here", narrator.lastError.value ?: t)
            }
            assertEquals(0, narrator.currentParagraph.value)
            // Mid-clip (the clip lasts 2 s): pause holds it in libmpv and resume goes on from there.
            Thread.sleep(400)
            narrator.pause()
            assertEquals(TtsState.PAUSED, narrator.state.value)
            Thread.sleep(500)
            assertEquals(0, narrator.currentParagraph.value)
            narrator.resume()
            assertEquals(TtsState.SPEAKING, narrator.state.value)
            withTimeout(15_000) { narrator.currentParagraph.first { it == 2 } }
            withTimeout(15_000) { narrator.state.first { it == TtsState.IDLE } }
            assertEquals(null, narrator.lastError.value)
            assertEquals(2, requests.size, "one request per paragraph (blank skipped): $requests")
        } finally {
            watch.cancel()
            narrator.release()
            server.close()
        }
    }
}
