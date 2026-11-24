// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.Container
import app.nijiyome.model.DownloadSettings
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.MetadataRepository
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assume.assumeTrue

/**
 * Hito 6 parity of the desktop downloads with Tatakai Next: HLS segments in parallel (written in playlist order), the engine-wide
 * speed limit, a "MP4" that is an m3u8 playlist switching to HLS, the `.ts` remuxed to `.mp4` with ffmpeg, and a local-library
 * entry removed by the user that a restart does not bring back.
 */
class DesktopDownloadParityTest {
    private val dir: File = Files.createTempDirectory("nijiyome-dl-parity").toFile()
    private val server = MockWebServer()
    private val opened = ArrayList<DesktopDownloadRepository>()

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        server.close()
        dir.deleteRecursively() // temp dir created above
    }

    private fun serve(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
        }
        server.start()
    }

    private fun bytes(data: ByteArray, code: Int = 200, type: String? = null) =
        MockResponse.Builder().code(code).apply { if (type != null) addHeader("Content-Type", type) }.body(Buffer().write(data)).build()

    private fun mediaPlaylist(count: Int, prefix: String = "") = buildString {
        append("#EXTM3U\n#EXT-X-TARGETDURATION:2\n")
        repeat(count) { append("#EXTINF:2,\n${prefix}s$it.ts\n") }
        append("#EXT-X-ENDLIST\n")
    }

    // ------------------------------------------------------------------ HLS queue

    @Test
    fun segmentsDownloadFourAtATimeAndAreWrittenInOrder(): Unit = runBlocking {
        val segments = List(12) { Random(it).nextBytes(2_000 + it) }
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        serve { r ->
            val path = r.url.encodedPath
            if (path == "/hls/index.m3u8") return@serve bytes(mediaPlaylist(segments.size).toByteArray())
            val i = path.removePrefix("/hls/s").removeSuffix(".ts").toInt()
            val now = inFlight.incrementAndGet()
            peak.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            // later segments answer first: the file must still be in playlist order
            Thread.sleep(40L + (segments.size - i) * 15L)
            inFlight.decrementAndGet()
            bytes(segments[i])
        }
        val fractions = ArrayList<Double?>()
        val out = DesktopDownloadEngine(OkHttpClient()).download(
            DesktopDownloadEngine.Job(server.url("/hls/index.m3u8").toString(), emptyMap(), Container.HLS, File(dir, "S/S - 01")),
        ) { fractions += it.fraction }
        assertContentEquals(segments.reduce { a, b -> a + b }, out.readBytes())
        assertEquals(DesktopDownloadEngine.SEGMENT_PARALLELISM, peak.get())
        assertEquals(1.0, fractions.last())
        assertEquals(fractions.sortedBy { it }, fractions, "progress only grows")
    }

    @Test
    fun aFailedSegmentStopsTheQueueButKeepsTheOnesInFlight(): Unit = runBlocking {
        serve { r ->
            when (r.url.encodedPath) {
                "/p.m3u8" -> bytes(mediaPlaylist(8).toByteArray())
                "/s1.ts" -> bytes(ByteArray(0), 404)
                else -> {
                    Thread.sleep(150)
                    bytes(byteArrayOf(7, 7))
                }
            }
        }
        val base = File(dir, "F/F - 01")
        assertFailsWith<DesktopDownloadEngine.HttpException> {
            DesktopDownloadEngine(OkHttpClient()).download(DesktopDownloadEngine.Job(server.url("/p.m3u8").toString(), emptyMap(), Container.HLS, base)) {}
        }
        val parts = DownloadNames.partsDir(base)
        // 0, 2 and 3 were already being fetched when 1 failed: they are kept; nothing after them was started
        assertEquals(listOf(0, 2, 3), (0 until 8).filter { DownloadNames.segmentFile(parts, it).isFile })
    }

    // ------------------------------------------------------------------ speed limit

    @Test
    fun theLimiterSpacesReadsToTheRate() {
        var now = 0L
        var kib = 100
        val limiter = SpeedLimiter({ kib }, { now })
        assertEquals(500, limiter.reserve(51_200)) // 50 KiB at 100 KiB/s
        assertEquals(1_000, limiter.reserve(51_200))
        now = 1_100_000_000L // a little over one second later the bucket is on time again
        assertEquals(0, limiter.reserve(1))
        now = 4_000_000_000L // after an idle gap the window starts over (no banked burst, no debt)
        assertEquals(500, limiter.reserve(51_200))
        kib = 0
        assertEquals(0, limiter.reserve(10_000_000))
    }

    @Test
    fun aLimitedDownloadTakesAsLongAsItsRate(): Unit = runBlocking {
        val data = Random(9).nextBytes(300 * 1024)
        serve { bytes(data) }
        val engine = DesktopDownloadEngine(OkHttpClient(), speedLimitKib = { 400 })
        val started = System.nanoTime()
        val out = engine.download(DesktopDownloadEngine.Job(server.url("/v/a.mp4").toString(), emptyMap(), Container.MP4, File(dir, "L/L - 01"))) {}
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertContentEquals(data, out.readBytes())
        assertTrue(tookMs >= 600, "300 KiB at 400 KiB/s took $tookMs ms")
    }

    // ------------------------------------------------------------------ "MP4" that is a playlist

    @Test
    fun aFileThatIsAPlaylistIsDownloadedAsHls(): Unit = runBlocking {
        val segments = List(3) { Random(20 + it).nextBytes(1_500) }
        serve { r ->
            when (r.url.encodedPath) {
                // served as text/plain: only the #EXTM3U start tells
                "/v/ep1.mp4" -> bytes(("\uFEFF" + mediaPlaylist(3, prefix = "/hls/")).toByteArray(), type = "text/plain")
                else -> bytes(segments[r.url.encodedPath.removePrefix("/hls/s").removeSuffix(".ts").toInt()])
            }
        }
        var switched = false
        val base = File(dir, "M/M - 01")
        val out = DesktopDownloadEngine(OkHttpClient()).download(
            DesktopDownloadEngine.Job(server.url("/v/ep1.mp4").toString(), emptyMap(), Container.MP4, base),
            onHls = { switched = true },
        ) {}
        assertTrue(switched)
        assertEquals("M - 01.ts", out.name)
        assertContentEquals(segments.reduce { a, b -> a + b }, out.readBytes())
        assertFalse(DownloadNames.partFile(DownloadNames.withExtension(base, "mp4")).exists(), "the playlist text is not left as a video")
    }

    @Test
    fun playlistsAreToldByTypeOrStart() {
        assertTrue(DesktopDownloadEngine.isPlaylist("application/vnd.apple.mpegurl", null))
        assertTrue(DesktopDownloadEngine.isPlaylist("audio/x-mpegURL; charset=utf-8", "x".toByteArray()))
        assertTrue(DesktopDownloadEngine.isPlaylist("text/plain", "  #EXTM3U\n".toByteArray()))
        assertFalse(DesktopDownloadEngine.isPlaylist("video/mp4", byteArrayOf(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70)))
        assertFalse(DesktopDownloadEngine.isPlaylist(null, null))
    }

    // ------------------------------------------------------------------ remux

    @Test
    fun ffmpegIsFoundOnThePath() {
        val bin = File(dir, "bin").apply { mkdirs() }
        val tool = File(bin, "fake-tool").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        assertEquals(tool.absolutePath, DesktopDownloadEngine.onPath("fake-tool", "/nonexistent${File.pathSeparator}${bin.path}"))
        assertNull(DesktopDownloadEngine.onPath("fake-tool", "/nonexistent"))
        val cmd = DesktopDownloadEngine.remuxCommand("ffmpeg", File("a.ts"), File("a.mp4"))
        assertEquals(listOf("-map", "0", "-c", "copy"), cmd.subList(cmd.indexOf("-map"), cmd.indexOf("-map") + 4))
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

    /** A real (silent: no audio track) MPEG-TS HLS stream downloaded and remuxed to a seekable MP4. Skipped without ffmpeg. */
    @Test
    fun aTsIsRemuxedToMp4WithFfmpeg(): Unit = runBlocking {
        assumeTrue("ffmpeg and ffprobe are needed", has("ffmpeg") && has("ffprobe"))
        val hls = File(dir, "src").apply { mkdirs() }
        run(
            "ffmpeg", "-nostdin", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=duration=4:size=320x240:rate=24",
            "-an", "-c:v", "libx264", "-preset", "ultrafast", "-g", "48", "-f", "hls", "-hls_time", "2", "-hls_playlist_type", "vod",
            "-hls_segment_filename", File(hls, "seg%03d.ts").path, File(hls, "index.m3u8").path,
        )
        serve { r ->
            val f = File(hls, r.url.encodedPath.removePrefix("/"))
            if (f.isFile) bytes(f.readBytes()) else bytes(ByteArray(0), 404)
        }
        val engine = DesktopDownloadEngine(OkHttpClient(), ffmpeg = { DesktopDownloadEngine.onPath("ffmpeg") })
        val base = File(dir, "out/Clip/Clip - 01")
        val out = engine.download(DesktopDownloadEngine.Job(server.url("/index.m3u8").toString(), emptyMap(), Container.HLS, base)) {}
        assertEquals("Clip - 01.mp4", out.name)
        assertFalse(DownloadNames.withExtension(base, "ts").exists(), "the .ts goes once remuxed")
        val probe = run("ffprobe", "-v", "error", "-show_entries", "format=format_name,duration:stream=codec_type", "-of", "default=noprint_wrappers=1", out.path)
        assertTrue("format_name=mov,mp4" in probe, probe)
        assertFalse("codec_type=audio" in probe, probe)
        val duration = Regex("duration=([0-9.]+)").find(probe)!!.groupValues[1].toDouble()
        assertTrue(duration in 3.5..4.5, "duration $duration")
    }

    // ------------------------------------------------------------------ local library

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> stub(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        when (method.name) {
            "history", "continueWatching" -> flowOf(emptyList<Any>())
            "toString" -> "stub"
            "hashCode" -> 0
            else -> throw UnsupportedOperationException(method.name)
        }
    } as T

    private fun open() = DesktopDownloadRepository(
        settings = { DownloadSettings() },
        extensions = stub<ExtensionRepository>(),
        metadata = stub<MetadataRepository>(),
        history = stub<HistoryRepository>(),
        lists = null,
        storeDir = File(dir, "store"),
        defaultFolder = File(dir, "Episodes"),
        http = OkHttpClient(),
        sniffer = { null },
        watchSettleMs = 200,
        autoCheckEveryMs = 0,
    ).also { opened += it }

    @Test
    fun aRemovedVideoStaysRemovedAndAFolderCanBeForgotten(): Unit = runBlocking {
        val anime = File(dir, "Anime").apply { mkdirs() }
        val one = File(anime, "Show - 01.mkv").apply { writeBytes(ByteArray(10)) }
        File(anime, "Show - 02.mkv").writeBytes(ByteArray(10))
        val repo = open()
        assertEquals(2, repo.scanFolder(anime.absolutePath).getOrThrow().size)
        assertEquals(listOf(anime.absolutePath), repo.libraryFolders.value)
        repo.removeLocal(repo.library.value.first { it.uri == one.absolutePath }.id, deleteFile = false)
        assertTrue(one.isFile, "the file stays on disk")
        repo.close()
        opened.clear()

        // The next start rescans the folder: the removed video does not come back.
        val again = open()
        withTimeout(10_000) { again.library.first { it.isNotEmpty() } }
        delay(500)
        assertEquals(listOf("Show - 02.mkv"), again.library.value.map { it.fileName })
        // Importing it by hand brings it back.
        assertEquals(1, again.importFiles(listOf(one.absolutePath)).getOrThrow().size)

        // A forgotten folder is not watched any more: a new video there does not join; the ones in the library stay.
        again.forgetLibraryFolder(anime.absolutePath)
        assertTrue(again.libraryFolders.value.isEmpty())
        File(anime, "Show - 03.mkv").writeBytes(ByteArray(10))
        delay(1_500)
        assertEquals(setOf("Show - 01.mkv", "Show - 02.mkv"), again.library.value.map { it.fileName }.toSet())
        again.close()
        opened.clear()
        val third = open()
        withTimeout(10_000) { third.library.first { it.isNotEmpty() } }
        delay(500)
        assertTrue(third.libraryFolders.value.isEmpty())
        assertEquals(2, third.library.value.size)
    }
}
