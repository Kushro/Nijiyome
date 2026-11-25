// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.Container
import app.nijiyome.model.DownloadRequest
import app.nijiyome.model.DownloadSettings
import app.nijiyome.model.DownloadState
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.StreamSource
import app.nijiyome.model.torrent.DebridAccount
import app.nijiyome.model.torrent.DebridFileRef
import app.nijiyome.model.torrent.DebridRepository
import app.nijiyome.model.torrent.DebridService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.BufferedSink
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 1.1 (F7): a TorBox download goes through the web download engine. Debrid links expire: every (re)start asks the service for a
 * fresh link, and the engine goes on from the part file with a Range request on the new link (the old one is dead by then). A
 * retry never asks the extensions (the source is the debrid file, not a server).
 */
class DesktopDebridDownloadTest {
    private val root: File = Files.createTempDirectory("nijiyome-dl-debrid").toFile()
    private val store = File(root, "store")
    private val folder = File(root, "Episodes")
    private val server = MockWebServer()
    private val opened = ArrayList<DesktopDownloadRepository>()
    private val release = CountDownLatch(1)
    private val data = Random(11).nextBytes(300_000)
    private val ranges = Collections.synchronizedList(mutableListOf<String>())

    /** The "CDN": only the newest link works (older ones answer 403, as an expired debrid link would). */
    private val serial = AtomicInteger(0)

    private inner class HeldBody(private val bytes: ByteArray, private val held: Int) : MockResponseBody {
        override val contentLength: Long get() = bytes.size.toLong()
        override fun writeTo(sink: BufferedSink) {
            sink.write(bytes, 0, held)
            sink.flush()
            if (held == bytes.size) return
            release.await(60, TimeUnit.SECONDS)
            sink.write(bytes, held, bytes.size - held)
            sink.flush()
        }
    }

    private inner class FakeDebrid(var fail: Throwable? = null) : DebridRepository {
        val refreshed = AtomicInteger()
        override val accounts = MutableStateFlow(listOf(DebridAccount(DebridService.TORBOX, "neo", null, 0)))
        override suspend fun link(service: DebridService, apiKey: String) = error("unused")
        override suspend fun unlink(service: DebridService) = Unit
        override suspend fun cached(infoHashes: List<String>) = emptyMap<String, Set<DebridService>>()
        override suspend fun files(service: DebridService, infoHash: String, magnet: String?) = error("unused")
        override suspend fun markCached(candidates: List<SourceCandidate>) = candidates
        override suspend fun refreshLink(file: DebridFileRef): Result<String> {
            refreshed.incrementAndGet()
            fail?.let { return Result.failure(it) }
            return Result.success(server.url("/dl/${serial.incrementAndGet()}/${file.fileId}").toString())
        }
    }

    @AfterTest
    fun tearDown() {
        release.countDown()
        opened.forEach { it.close() }
        server.close()
        root.deleteRecursively() // temp dir created above
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> stub(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        when (method.name) {
            "history", "continueWatching" -> flowOf(emptyList<Any>())
            "toString" -> "stub"
            "hashCode" -> 0
            else -> throw UnsupportedOperationException(method.name)
        }
    } as T

    private fun open(debrid: DebridRepository) = DesktopDownloadRepository(
        settings = { DownloadSettings(maxConcurrent = 1) },
        extensions = stub<ExtensionRepository>(),
        metadata = stub<MetadataRepository>(),
        history = stub<HistoryRepository>(),
        lists = null,
        storeDir = store,
        defaultFolder = folder,
        http = OkHttpClient(),
        sniffer = { null },
        watchSettleMs = 200,
        autoCheckEveryMs = 0,
        debrid = { debrid },
    ).also { opened += it }

    private val media = Media(
        ref = MediaRef(anilistId = 21), kind = MediaKind.ANIME, title = "Show", titles = emptyMap(), cover = null, banner = null,
        color = null, synopsis = null, genres = emptyList(), episodes = 12, status = null, season = null, year = null, score = null, sourceProvider = "test",
    )

    private val ref = DebridFileRef(DebridService.TORBOX, "c".repeat(40), fileId = "2", fileName = "Show - 03.mkv")

    private fun request() = DownloadRequest(
        media, 3,
        StreamSource(server.url("/dl/0/2").toString(), emptyMap(), Container.PROGRESSIVE, "1080p", "TorBox", "torrent"),
        sourceLabel = "TorBox",
        debrid = ref,
    )

    private fun cdn() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val n = request.url.pathSegments.getOrNull(1)?.toIntOrNull()
                if (n != serial.get()) return MockResponse.Builder().code(403).build()
                val range = request.headers["Range"]
                ranges += "$n:${range ?: "-"}"
                val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
                val rest = data.copyOfRange(from, data.size)
                return MockResponse.Builder().code(if (from > 0) 206 else 200)
                    .apply { if (from > 0) addHeader("Content-Range", "bytes $from-${data.size - 1}/${data.size}") }
                    .body(HeldBody(rest, held = (HELD - from).coerceIn(0, rest.size))).build()
            }
        }
        server.start()
    }

    private suspend fun DesktopDownloadRepository.waitFor(id: String, state: DownloadState) = withTimeout(20_000) {
        items.first { list -> list.any { it.id == id && it.state == state } }.first { it.id == id }
    }

    @Test
    fun resumeAsksForAFreshLinkAndGoesOnWithARange(): Unit = runBlocking {
        cdn()
        val debrid = FakeDebrid()
        val repo = open(debrid)
        val item = repo.enqueue(request()).getOrThrow()
        val part = File(folder, "Show/Show - 03.mp4.part")
        withTimeout(20_000) { while (part.length() < HELD) delay(20) }
        assertEquals(1, debrid.refreshed.get())
        repo.pause(item.id)
        release.countDown()
        // the first link expires while paused
        val resumedFrom = part.length()
        assertTrue(resumedFrom in 1 until data.size)
        repo.resume(item.id)
        val done = repo.waitFor(item.id, DownloadState.COMPLETED)
        assertEquals(2, debrid.refreshed.get())
        assertContentEquals(data, File(done.localUri!!).readBytes())
        // first link from byte 0, then the renewed link from where the part file ended
        assertEquals("1:-", ranges.first())
        assertTrue(ranges.any { it.startsWith("2:bytes=") && !it.startsWith("2:bytes=0-") }, ranges.toString())
        assertEquals("TorBox", done.sourceLabel)
        // 1.1 review #11: a finished debrid download keeps no CDN link in downloads.json
        withTimeout(10_000) {
            while (true) {
                val files = store.walkTopDown().filter { it.isFile }.toList()
                val settled = files.none { it.name.endsWith(".tmp") } &&
                    files.none { file -> runCatching { "/dl/" in file.readText() }.getOrDefault(true) }
                if (settled) break
                delay(20)
            }
        }
    }

    @Test
    fun httpsRequestsNeverFollowADowngrade() {
        val engine = DesktopDownloadEngine(OkHttpClient())
        assertEquals(false, engine.clientFor(okhttp3.HttpUrl.Builder().scheme("https").host("cdn.example").build()).followSslRedirects)
        assertEquals(true, engine.clientFor(okhttp3.HttpUrl.Builder().scheme("http").host("cdn.example").build()).followSslRedirects)
    }

    @Test
    fun aRefusedRefreshFailsWithTheServiceReasonAndRetryNeverAsksTheExtensions(): Unit = runBlocking {
        cdn()
        release.countDown()
        val debrid = FakeDebrid(fail = AppException(AppError.Unknown("debrid:torbox:ACTIVE_LIMIT")))
        val repo = open(debrid)
        val item = repo.enqueue(request()).getOrThrow()
        val failed = repo.waitFor(item.id, DownloadState.FAILED)
        // 1.1 review #7: the bare code (the Downloads page translates it), not "Unknown(msg=...)"
        assertEquals("debrid:torbox:ACTIVE_LIMIT", failed.error)
        // the stub extension repository throws on any call: retry must go through the debrid service only
        debrid.fail = null
        repo.retry(item.id)
        val done = repo.waitFor(item.id, DownloadState.COMPLETED)
        assertContentEquals(data, File(done.localUri!!).readBytes())
    }

    private companion object {
        const val HELD = 80_000
    }
}
