// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.Container
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assume.assumeFalse

/**
 * Hito 6 review fixes of the desktop download engine: bodies held in memory have a hard cap (an announced or streamed size over it
 * fails without allocating it), the HLS segments survive a cancelled or failed remux (and a resume does not fetch them again), and
 * the speed limiter keeps its window during a continuous download and restarts it when the limit changes.
 */
class DesktopDownloadHardeningTest {
    private val dir: File = Files.createTempDirectory("nijiyome-dl-hardening").toFile()
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.close()
        dir.deleteRecursively() // temp dir created above
    }

    private fun serve(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
        }
        server.start()
    }

    private fun bytes(data: ByteArray) = MockResponse.Builder().code(200).body(Buffer().write(data)).build()

    // ------------------------------------------------------------------ body cap

    @Test
    fun anAnnouncedBodyOverTheCapFailsAtOnceWithoutRetrying(): Unit = runBlocking {
        serve { bytes(ByteArray(5_000)) }
        val engine = DesktopDownloadEngine(OkHttpClient(), maxBodyBytes = 1_000)
        assertFailsWith<DesktopDownloadEngine.TooLarge> { engine.resolveHls(server.url("/p.m3u8").toString(), emptyMap(), app.nijiyome.model.DownloadProfile.BEST) }
        assertEquals(1, server.requestCount, "not retried")
    }

    @Test
    fun aStreamedBodyOverTheCapFailsWhileReading(): Unit = runBlocking {
        serve { MockResponse.Builder().code(200).chunkedBody(Buffer().write(ByteArray(5_000)), 512).build() }
        val engine = DesktopDownloadEngine(OkHttpClient(), maxBodyBytes = 1_000)
        assertFailsWith<DesktopDownloadEngine.TooLarge> { engine.resolveHls(server.url("/p.m3u8").toString(), emptyMap(), app.nijiyome.model.DownloadProfile.BEST) }
        assertEquals(1, server.requestCount, "not retried")
    }

    @Test
    fun aHugeContentLengthIsNotAllocated(): Unit = runBlocking {
        // A broken or hostile server announcing 2 GiB - 1 for a playlist: rejected by the default cap, before any allocation.
        serve { MockResponse.Builder().code(200).body("#EXTM3U\n").setHeader("Content-Length", Int.MAX_VALUE.toLong()).build() }
        val engine = DesktopDownloadEngine(OkHttpClient())
        assertFailsWith<DesktopDownloadEngine.TooLarge> { engine.resolveHls(server.url("/p.m3u8").toString(), emptyMap(), app.nijiyome.model.DownloadProfile.BEST) }
    }

    // ------------------------------------------------------------------ remux

    private val segments = List(3) { Random(it).nextBytes(3_000 + it) }
    private val fetched = ConcurrentLinkedQueue<String>()

    private fun serveHls() = serve { r ->
        val path = r.url.encodedPath
        fetched += path
        if (path == "/index.m3u8") {
            bytes(
                buildString {
                    append("#EXTM3U\n#EXT-X-TARGETDURATION:2\n")
                    segments.indices.forEach { append("#EXTINF:2,\ns$it.ts\n") }
                    append("#EXT-X-ENDLIST\n")
                }.toByteArray(),
            )
        } else {
            bytes(segments[path.removePrefix("/s").removeSuffix(".ts").toInt()])
        }
    }

    /** A stand-in ffmpeg: `$in` is the input after `-i`, `$out` the last argument, then [body]. */
    private fun fakeFfmpeg(name: String, body: String): String {
        val script = File(dir, "bin/$name").apply { parentFile.mkdirs() }
        script.writeText(
            "#!/bin/sh\nin=\"\"; prev=\"\"; for a in \"$@\"; do [ \"\$prev\" = \"-i\" ] && in=\"\$a\"; prev=\"\$a\"; out=\"\$a\"; done\n$body\n",
        )
        script.setExecutable(true)
        return script.absolutePath
    }

    private val base get() = File(dir, "out/Show/Show - 01")
    private fun job() = DesktopDownloadEngine.Job(server.url("/index.m3u8").toString(), emptyMap(), Container.HLS, base)

    @Test
    fun aCancelledRemuxLeavesNoMp4AndResumesWithoutDownloadingAgain(): Unit = runBlocking {
        assumeFalse("POSIX shell scripts stand in for ffmpeg", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        serveHls()
        val started = File(dir, "remux-started")
        val hanging = fakeFfmpeg("ffmpeg-hang", "printf partial > \"\$out\"\n: > '${started.path}'\nexec sleep 60")
        val download = launch(Dispatchers.Default) { DesktopDownloadEngine(OkHttpClient(), ffmpeg = { hanging }).download(job()) {} }
        withTimeout(20_000) { while (!started.exists()) delay(20) }
        download.cancelAndJoin()

        val mp4 = DownloadNames.withExtension(base, "mp4")
        assertFalse(mp4.exists(), "the half-written .mp4 is deleted")
        val parts = DownloadNames.partsDir(base)
        assertEquals(segments.indices.toList(), segments.indices.filter { DownloadNames.segmentFile(parts, it).isFile }, "the segments are kept")

        // Resuming remuxes again from the kept segments: only the playlist is fetched.
        fetched.clear()
        val copying = fakeFfmpeg("ffmpeg-ok", "cp \"\$in\" \"\$out\"")
        val out = DesktopDownloadEngine(OkHttpClient(), ffmpeg = { copying }).download(job()) {}
        assertEquals(listOf("/index.m3u8"), fetched.toList())
        assertEquals(mp4, out)
        assertContentEquals(segments.reduce { a, b -> a + b }, out.readBytes())
        assertFalse(parts.exists(), "the segments go once the .mp4 is final")
        assertFalse(DownloadNames.withExtension(base, "ts").exists())
        assertFalse(DownloadNames.partFile(DownloadNames.withExtension(base, "ts")).exists())
    }

    @Test
    fun aFailedRemuxKeepsThePlayableTsAndNoMp4(): Unit = runBlocking {
        assumeFalse("POSIX shell scripts stand in for ffmpeg", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        serveHls()
        val failing = fakeFfmpeg("ffmpeg-fail", "printf partial > \"\$out\"\nexit 1")
        val out = DesktopDownloadEngine(OkHttpClient(), ffmpeg = { failing }).download(job()) {}
        assertEquals(DownloadNames.withExtension(base, "ts"), out)
        assertContentEquals(segments.reduce { a, b -> a + b }, out.readBytes())
        assertFalse(DownloadNames.withExtension(base, "mp4").exists(), "the failed .mp4 is deleted")
        assertFalse(DownloadNames.partsDir(base).exists())
    }

    // ------------------------------------------------------------------ speed limit

    @Test
    fun aContinuousDownloadKeepsItsDebtPastTwoSeconds() {
        var now = 0L
        val limiter = SpeedLimiter({ 100 }, { now })
        assertEquals(4_000, limiter.reserve(409_600)) // one worker reads 400 KiB at 100 KiB/s: 4 s owed
        now = 2_500_000_000L // another worker reads while the first still waits: the window is not idle
        assertEquals(2_000, limiter.reserve(51_200))
        now = 9_000_000_000L // idle for longer than the reset gap after the last reservation: no debt, no banked burst
        assertEquals(500, limiter.reserve(51_200))
    }

    @Test
    fun loweringTheLimitMidDownloadDoesNotOweTheOldBytesAtTheNewRate() {
        var now = 0L
        var kib = 10_240
        val limiter = SpeedLimiter({ kib }, { now })
        assertEquals(1_000, limiter.reserve(10L * 1024 * 1024)) // 10 MiB at 10 MiB/s
        now = 1_000_000_000L
        kib = 512
        assertEquals(125, limiter.reserve(64L * 1024)) // 64 KiB at 512 KiB/s, not ~19 s for the 10 MiB already read
    }
}
