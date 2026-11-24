// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.Container
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer

/** [DesktopDownloadEngine] against a local HTTP server: ranged resume of files, HLS segments with AES-128, headers, resume. */
class DesktopDownloadEngineTest {
    private val dir: File = Files.createTempDirectory("nijiyome-dl-engine").toFile()
    private val server = MockWebServer()
    private val requests = ConcurrentLinkedQueue<RecordedRequest>()
    private val engine = DesktopDownloadEngine(OkHttpClient())

    @AfterTest
    fun tearDown() {
        server.close()
        dir.deleteRecursively() // temp dir created above
    }

    private fun serve(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return handler(request)
            }
        }
        server.start()
    }

    private fun bytes(data: ByteArray, code: Int = 200, headers: Map<String, String> = emptyMap()) =
        MockResponse.Builder().code(code).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.body(Buffer().write(data)).build()

    /** Answers `Range: bytes=N-` with 206 when [honourRange], the whole body otherwise. */
    private fun ranged(data: ByteArray, request: RecordedRequest, honourRange: Boolean = true): MockResponse {
        val range = request.headers["Range"]
        if (range == null || !honourRange) return bytes(data)
        val from = range.removePrefix("bytes=").substringBefore('-').toInt()
        return bytes(data.copyOfRange(from, data.size), 206, mapOf("Content-Range" to "bytes $from-${data.size - 1}/${data.size}"))
    }

    @Test
    fun aDirectFileResumesFromItsPart(): Unit = runBlocking {
        val data = Random(1).nextBytes(700_000)
        serve { ranged(data, it) }
        val base = File(dir, "Show/Show - 01")
        val part = DownloadNames.partFile(DownloadNames.withExtension(base, "mp4"))
        part.parentFile.mkdirs()
        part.writeBytes(data.copyOfRange(0, 300_000))
        var last: DesktopDownloadEngine.Progress? = null
        val out = engine.download(DesktopDownloadEngine.Job(server.url("/v/ep1.mp4").toString(), mapOf("Referer" to "https://site.example/"), Container.MP4, base)) { last = it }
        assertEquals(DownloadNames.withExtension(base, "mp4"), out)
        assertContentEquals(data, out.readBytes())
        assertFalse(part.exists())
        assertEquals("bytes=300000-", requests.single().headers["Range"])
        assertEquals("https://site.example/", requests.single().headers["Referer"])
        assertEquals(1.0, last?.fraction)
    }

    /** Hallazgo 17 de la revisión: a 416 only means "complete" when the remote size is exactly the part's. */
    @Test
    fun aPartLargerThanTheRemoteFileStartsOver(): Unit = runBlocking {
        val data = Random(11).nextBytes(100_000)
        serve { req ->
            val range = req.headers["Range"]
            val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toLong()
            if (from != null && from >= data.size) {
                MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${data.size}").build()
            } else {
                ranged(data, req)
            }
        }
        val base = File(dir, "Show/Show - 11")
        val part = DownloadNames.partFile(DownloadNames.withExtension(base, "mp4"))
        part.parentFile.mkdirs()
        part.writeBytes(ByteArray(150_000) { 3 }) // the remote file shrank, or the part is damaged
        val out = engine.download(DesktopDownloadEngine.Job(server.url("/v/ep11.mp4").toString(), emptyMap(), Container.MP4, base)) {}
        assertContentEquals(data, out.readBytes(), "the damaged part was not taken for the finished file")
    }

    @Test
    fun aPartThatIsTheWholeFileIsComplete(): Unit = runBlocking {
        val data = Random(12).nextBytes(80_000)
        serve { MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${data.size}").build() }
        val base = File(dir, "Show/Show - 12")
        val part = DownloadNames.partFile(DownloadNames.withExtension(base, "mp4"))
        part.parentFile.mkdirs()
        part.writeBytes(data)
        val out = engine.download(DesktopDownloadEngine.Job(server.url("/v/ep12.mp4").toString(), emptyMap(), Container.MP4, base)) {}
        assertContentEquals(data, out.readBytes())
        assertEquals(1, requests.size)
    }

    /** Hallazgo 17: a 206 whose range does not start where the part ends would corrupt the file: start over. */
    @Test
    fun aRangeThatDoesNotContinueThePartStartsOver(): Unit = runBlocking {
        val data = Random(13).nextBytes(120_000)
        serve { req ->
            if (req.headers["Range"] != null) {
                bytes(data, 206, mapOf("Content-Range" to "bytes 0-${data.size - 1}/${data.size}"))
            } else {
                bytes(data)
            }
        }
        val base = File(dir, "Show/Show - 13")
        val part = DownloadNames.partFile(DownloadNames.withExtension(base, "mp4"))
        part.parentFile.mkdirs()
        part.writeBytes(data.copyOfRange(0, 40_000))
        val out = engine.download(DesktopDownloadEngine.Job(server.url("/v/ep13.mp4").toString(), emptyMap(), Container.MP4, base)) {}
        assertContentEquals(data, out.readBytes())
    }

    /** Hallazgo 17: pausing a download from a server that trickles ends at once (the HTTP call is cancelled). */
    @Test
    fun cancellingAStalledTransferEndsAtOnce(): Unit = runBlocking {
        serve { MockResponse.Builder().addHeader("Content-Length", "1000000").body(Buffer().write(ByteArray(1_000_000))).throttleBody(1, 20, java.util.concurrent.TimeUnit.SECONDS).build() }
        val base = File(dir, "Show/Show - 14")
        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            engine.download(DesktopDownloadEngine.Job(server.url("/v/ep14.mp4").toString(), emptyMap(), Container.MP4, base)) {}
        }
        kotlinx.coroutines.delay(1_500)
        val started = System.currentTimeMillis()
        job.cancel()
        job.join()
        val waited = System.currentTimeMillis() - started
        assertTrue(waited < 5_000, "cancel took $waited ms")
    }

    @Test
    fun contentRangeHeaders() {
        assertEquals(300L, ContentRange.start("bytes 300-999/1000"))
        assertEquals(null, ContentRange.start("bytes */1000"))
        assertEquals(null, ContentRange.start(null))
        assertEquals(1000L, ContentRange.unsatisfiedSize("bytes */1000"))
        assertEquals(null, ContentRange.unsatisfiedSize("bytes 0-1/2"))
        assertEquals(null, ContentRange.unsatisfiedSize(null))
    }

    @Test
    fun aServerThatIgnoresTheRangeStartsOver(): Unit = runBlocking {
        val data = Random(2).nextBytes(200_000)
        serve { ranged(data, it, honourRange = false) }
        val base = File(dir, "Show/Show - 02")
        val part = DownloadNames.partFile(DownloadNames.withExtension(base, "mkv"))
        part.parentFile.mkdirs()
        part.writeBytes(ByteArray(50_000) { 7 })
        val out = engine.download(DesktopDownloadEngine.Job(server.url("/v/ep2.mkv?token=x").toString(), emptyMap(), Container.MP4, base)) {}
        assertEquals("Show - 02.mkv", out.name)
        assertContentEquals(data, out.readBytes())
    }

    @Test
    fun hlsSegmentsAreDecryptedConcatenatedAndResumed(): Unit = runBlocking {
        val key = Random(3).nextBytes(16)
        val plain = List(3) { Random(10 + it).nextBytes(5_000 + it * 100) }
        val iv1 = ByteArray(16).also { it[15] = 6 } // media sequence 5 + 1 = 6 -> IV from the sequence
        val enc1 = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv1))
            doFinal(plain[1])
        }
        val playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-MEDIA-SEQUENCE:5
            #EXTINF:2,
            s0.ts
            #EXT-X-KEY:METHOD=AES-128,URI="/keys/k1"
            #EXTINF:2,
            s1.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:2,
            s2.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        serve { r ->
            if (r.headers["Referer"] != "https://site.example/") return@serve bytes(ByteArray(0), 403)
            when (r.url.encodedPath) {
                "/hls/index.m3u8" -> bytes(playlist.toByteArray())
                "/hls/s0.ts" -> bytes(plain[0])
                "/hls/s1.ts" -> bytes(enc1)
                "/hls/s2.ts" -> bytes(plain[2])
                "/keys/k1" -> bytes(key)
                else -> bytes(ByteArray(0), 404)
            }
        }
        val base = File(dir, "Show/Show - 03")
        // A previous run already finished the first segment: it is not fetched again.
        val parts = DownloadNames.partsDir(base).apply { mkdirs() }
        DownloadNames.segmentFile(parts, 0).writeBytes(plain[0])
        val fractions = ArrayList<Double?>()
        val out = engine.download(
            DesktopDownloadEngine.Job(server.url("/hls/index.m3u8").toString(), mapOf("Referer" to "https://site.example/"), Container.HLS, base),
        ) { fractions += it.fraction }
        assertEquals("Show - 03.ts", out.name)
        assertContentEquals(plain[0] + plain[1] + plain[2], out.readBytes())
        assertFalse(parts.exists(), "the parts go once the file is written")
        assertFalse(requests.any { it.url.encodedPath == "/hls/s0.ts" })
        assertTrue(requests.all { it.headers["Referer"] == "https://site.example/" })
        assertEquals(1.0, fractions.last())
    }

    @Test
    fun aFailedSegmentKeepsWhatWasWritten(): Unit = runBlocking {
        val playlist = "#EXTM3U\n#EXTINF:2,\na.ts\n#EXTINF:2,\nb.ts\n#EXT-X-ENDLIST\n"
        serve { r ->
            when (r.url.encodedPath) {
                "/p.m3u8" -> bytes(playlist.toByteArray())
                "/a.ts" -> bytes(byteArrayOf(1, 2, 3))
                else -> bytes(ByteArray(0), 404)
            }
        }
        val base = File(dir, "S/S - 04")
        assertFailsWith<DesktopDownloadEngine.HttpException> {
            engine.download(DesktopDownloadEngine.Job(server.url("/p.m3u8").toString(), emptyMap(), Container.HLS, base)) {}
        }
        val parts = DownloadNames.partsDir(base)
        assertContentEquals(byteArrayOf(1, 2, 3), DownloadNames.segmentFile(parts, 0).readBytes())
        assertFalse(DownloadNames.segmentFile(parts, 1).exists())
        engine.discardPartials(base)
        assertFalse(parts.exists())
    }

    @Test
    fun masterPlaylistsAreResolvedByProfile(): Unit = runBlocking {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=640x360\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=9000,RESOLUTION=1920x1080\nhigh.m3u8\n"
        serve { bytes(master.toByteArray()) }
        val url = server.url("/m/master.m3u8").toString()
        assertEquals(server.url("/m/high.m3u8").toString(), engine.resolveHls(url, emptyMap(), app.nijiyome.model.DownloadProfile.BEST))
        assertEquals(server.url("/m/low.m3u8").toString(), engine.resolveHls(url, emptyMap(), app.nijiyome.model.DownloadProfile.SAVER_720))
    }
}
