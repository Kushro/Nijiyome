// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.Container
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assume.assumeTrue

/**
 * A real HLS stream end to end: ffmpeg makes a 6 s clip (video only, no audio track) cut into 2 s MPEG-TS segments behind a master
 * playlist, a local HTTP server serves it (requiring the extension's Referer), the download fails half-way, resumes from the
 * segments it has, and ffprobe reads the result. Skipped where ffmpeg / ffprobe are not installed.
 */
class HlsFfmpegIntegrationTest {
    private val dir: File = Files.createTempDirectory("nijiyome-hls-it").toFile()
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.close()
        dir.deleteRecursively() // temp dir created above
    }

    private fun has(tool: String) = runCatching { ProcessBuilder(tool, "-version").redirectErrorStream(true).start().apply { inputStream.readBytes() }.waitFor() == 0 }
        .getOrDefault(false)

    private fun run(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "timed out: ${cmd.joinToString(" ")}")
        assertEquals(0, p.exitValue(), "${cmd.first()} failed:\n$out")
        return out
    }

    @Test
    fun downloadsResumesAndPlays(): Unit = runBlocking {
        assumeTrue("ffmpeg and ffprobe are needed", has("ffmpeg") && has("ffprobe"))
        val hls = File(dir, "src/720").apply { mkdirs() }
        run(
            "ffmpeg", "-nostdin", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=duration=6:size=320x240:rate=24",
            "-an", "-c:v", "libx264", "-preset", "ultrafast", "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
            "-f", "hls", "-hls_time", "2", "-hls_playlist_type", "vod", "-hls_segment_filename", File(hls, "seg%03d.ts").path, File(hls, "index.m3u8").path,
        )
        File(dir, "src/master.m3u8").writeText("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=320x240\n720/index.m3u8\n")
        val segments = hls.listFiles { f -> f.name.endsWith(".ts") }!!.size
        assertTrue(segments >= 3, "ffmpeg cut $segments segments")

        val failLast = AtomicBoolean(true)
        val fetched = java.util.concurrent.ConcurrentLinkedQueue<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                fetched += path
                if (request.headers["Referer"] != "https://site.example/") return MockResponse.Builder().code(403).build()
                if (failLast.get() && path.endsWith("seg%03d.ts".format(segments - 1))) return MockResponse.Builder().code(404).build()
                val file = File(dir, "src" + path)
                if (!file.isFile) return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(Buffer().write(file.readBytes())).build()
            }
        }
        server.start()
        val engine = DesktopDownloadEngine(OkHttpClient())
        val headers = mapOf("Referer" to "https://site.example/")
        val media = engine.resolveHls(server.url("/master.m3u8").toString(), headers, app.nijiyome.model.DownloadProfile.BALANCED_1080)
        assertEquals(server.url("/720/index.m3u8").toString(), media)
        val base = File(dir, "out/Clip/Clip - 01")
        val job = DesktopDownloadEngine.Job(media, headers, Container.HLS, base)

        // First run: the last segment fails; the others stay as the resume checkpoint.
        assertFailsWith<DesktopDownloadEngine.HttpException> { engine.download(job) {} }
        val parts = DownloadNames.partsDir(base)
        assertEquals(segments - 1, parts.listFiles { f -> f.name.endsWith(".seg") }!!.size)

        // Second run: only the missing segment is fetched.
        failLast.set(false)
        fetched.clear()
        val out = engine.download(job) {}
        assertEquals(listOf("/720/index.m3u8", "/720/" + "seg%03d.ts".format(segments - 1)), fetched.toList())
        assertFalse(parts.exists())
        assertEquals("Clip - 01.ts", out.name)

        val probe = run(
            "ffprobe", "-v", "error", "-show_entries", "format=duration:stream=codec_type,codec_name", "-of", "default=noprint_wrappers=1", out.path,
        )
        assertTrue("codec_name=h264" in probe, probe)
        assertFalse("codec_type=audio" in probe, probe)
        val duration = Regex("duration=([0-9.]+)").find(probe)!!.groupValues[1].toDouble()
        assertTrue(duration in 5.5..6.5, "duration $duration")
        // Every frame decodes (no corrupt segment boundary).
        val frames = run("ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0", "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", out.path)
        assertEquals(144, frames.trim().toInt())
    }
}
