// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

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
import app.nijiyome.model.StreamSource
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
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

/** [DesktopDownloadRepository]: queue, pause / resume across a restart, folder, local library and its watched folders. */
class DesktopDownloadRepositoryTest {
    private val root: File = Files.createTempDirectory("nijiyome-dl-repo").toFile()
    private val store = File(root, "store")
    private val folder = File(root, "Downloads/Nijiyome/Episodes")
    private val server = MockWebServer()
    private var settings = DownloadSettings(maxConcurrent = 1)
    private val opened = ArrayList<DesktopDownloadRepository>()

    /** Opened once the test has paused (or at tear-down); until then a [HeldBody] stops after its first bytes. */
    private val release = CountDownLatch(1)

    /** Sends the first [held] bytes of [bytes], waits for [release], then sends the rest (or fails if the client went away). */
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

    @AfterTest
    fun tearDown() {
        release.countDown()
        opened.forEach { it.close() }
        server.close()
        root.deleteRecursively() // temp dir created above
    }

    /** An implementation of [T] for the calls the repository makes in these tests (history: empty; anything else fails). */
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
        settings = { settings },
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
    ).also { opened += it }

    private val media = Media(
        ref = MediaRef(anilistId = 154587), kind = MediaKind.ANIME, title = "Sousou no Frieren", titles = emptyMap(), cover = null, banner = null,
        color = null, synopsis = null, genres = emptyList(), episodes = 28, status = null, season = null, year = 2023, score = null, sourceProvider = "test",
    )

    private fun stream(path: String) = StreamSource(
        url = server.url(path).toString(), headers = mapOf("Referer" to "https://site.example/"), container = Container.MP4, quality = "1080p",
        provider = "Alpha", extensionId = "ext",
    )

    private suspend fun DesktopDownloadRepository.waitFor(id: String, state: DownloadState) = withTimeout(20_000) {
        items.first { list -> list.any { it.id == id && it.state == state } }.first { it.id == id }
    }

    @Test
    fun queuePauseRestartAndResume(): Unit = runBlocking {
        val data = Random(5).nextBytes(400_000)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val from = request.headers["Range"]?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
                val rest = data.copyOfRange(from, data.size)
                return MockResponse.Builder().code(if (from > 0) 206 else 200)
                    .apply { if (from > 0) addHeader("Content-Range", "bytes $from-${data.size - 1}/${data.size}") }
                    // Only the first bytes of the file go out before the test pauses (on any request, retries included), so it cannot finish first.
                    .body(HeldBody(rest, held = (HELD_UNTIL_PAUSE - from).coerceIn(0, rest.size))).build()
            }
        }
        server.start()
        val first = open()
        val item = first.enqueue(DownloadRequest(media, 1, stream("/ep1.mp4"), "Alpha")).getOrThrow()
        assertEquals(DownloadLogic.itemId(MediaRef(anilistId = 154587), 1), item.id)
        val part = File(folder, "Sousou no Frieren/Sousou no Frieren - 01.mp4.part")
        // The server stops at HELD_UNTIL_PAUSE until released: the download is half-way once the part file holds those bytes.
        withTimeout(20_000) { while (part.length() < HELD_UNTIL_PAUSE) delay(20) }
        assertEquals(DownloadState.DOWNLOADING, first.items.value.single().state)
        first.pause(item.id)
        assertEquals(DownloadState.PAUSED, first.items.value.single().state)
        release.countDown()
        assertTrue(part.length() in 1 until data.size, "a part file was left: ${part.length()}")
        first.close()
        opened.clear()

        // A new run (the app restarted) keeps it paused; resuming asks for the rest only.
        val second = open()
        withTimeout(10_000) { second.items.first { it.isNotEmpty() } }
        assertEquals(DownloadState.PAUSED, second.items.value.single().state)
        second.resume(item.id)
        val done = second.waitFor(item.id, DownloadState.COMPLETED)
        val file = File(folder, "Sousou no Frieren/Sousou no Frieren - 01.mp4")
        assertEquals(file.absolutePath, done.localUri)
        assertContentEquals(data, file.readBytes())
        assertEquals(data.size.toLong(), done.totalBytes)
        assertEquals(file.absolutePath, second.localUriFor(MediaRef(anilistId = 154587), 1))
        assertNull(second.localUriFor(MediaRef(anilistId = 154587), 2))
        assertTrue(second.downloadedEpisodes(MediaRef(anilistId = 154587)).first().containsKey(1))
        val ranges = generateSequence { server.takeRequest(1, TimeUnit.SECONDS) }.mapNotNull { it.headers["Range"] }.toList()
        assertTrue(ranges.any { it.startsWith("bytes=") && it != "bytes=0-" }, "resumed with a range: $ranges")
        // Removing deletes the file.
        second.remove(item.id)
        // The list and the file are updated off the caller's thread: wait for both instead of reading right away.
        val until = System.currentTimeMillis() + 15_000
        while ((second.items.value.isNotEmpty() || file.exists()) && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue(second.items.value.isEmpty())
        assertTrue(!file.exists())
    }

    @Test
    fun aChosenFolderIsUsedAndTheFailureIsShown(): Unit = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse.Builder().code(404).build()
        }
        server.start()
        val chosen = File(root, "Elsewhere")
        settings = settings.copy(directoryTreeUri = chosen.absolutePath)
        val repo = open()
        val item = repo.enqueue(DownloadRequest(media, 2, stream("/missing.mp4"), "Alpha")).getOrThrow()
        val failed = repo.waitFor(item.id, DownloadState.FAILED)
        assertEquals("http 404", failed.error)
        assertEquals(chosen, repo.currentFolder())
        assertNotNull(repo.history().first().singleOrNull { it.id == item.id })
    }

    @Test
    fun theLibraryScansAndWatchesItsFolders(): Unit = runBlocking {
        val anime = File(root, "Anime/Show").apply { mkdirs() }
        File(anime, "[Group] Show - 01 [1080p].mkv").writeBytes(ByteArray(10))
        File(anime, "notes.txt").writeText("x")
        val repo = open()
        val found = repo.scanFolder(File(root, "Anime").absolutePath).getOrThrow()
        assertEquals(listOf("[Group] Show - 01 [1080p].mkv"), found.map { it.fileName })
        assertEquals(1, found.single().episode)
        // Scanning again adds nothing.
        assertTrue(repo.scanFolder(File(root, "Anime").absolutePath).getOrThrow().isEmpty())
        // A file that appears later joins on its own once it stops growing (the folder is watched), also in a new subfolder.
        File(anime, "[Group] Show - 02 [1080p].mkv.part").writeBytes(ByteArray(10))
        File(anime, "[Group] Show - 02 [1080p].mkv.part").renameTo(File(anime, "[Group] Show - 02 [1080p].mkv"))
        val season2 = File(root, "Anime/Show S2").apply { mkdirs() }
        Thread.sleep(300)
        File(season2, "Show S2 - 05.mp4").writeBytes(ByteArray(20))
        withTimeout(15_000) { repo.library.first { it.size == 3 } }
        assertEquals(setOf(1, 2, 5), repo.library.value.mapNotNull { it.episode }.toSet())
        // Importing a single file by path.
        val loose = File(root, "loose - 07.mkv").apply { writeBytes(ByteArray(5)) }
        assertEquals(1, repo.importFiles(listOf(loose.absolutePath)).getOrThrow().size)
        // The library survives a restart, and the folder is watched again.
        repo.close()
        opened.clear()
        val again = open()
        withTimeout(10_000) { again.library.first { it.size == 4 } }
        File(anime, "Show - 03.mkv").writeBytes(ByteArray(10))
        withTimeout(15_000) { again.library.first { it.size == 5 } }
    }

    private companion object {
        /** File offset the server sends up to before the test pauses. */
        const val HELD_UNTIL_PAUSE = 100_000
    }
}
