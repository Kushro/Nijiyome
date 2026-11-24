// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileImageReaderTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16)

    private fun temp(name: String, bytes: ByteArray): File =
        Files.createTempDirectory("img").toFile().apply { deleteOnExit() }.resolve(name).apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun aDroppedImageIsReadByItsContentNotItsName() = runBlocking {
        val shot = temp("shot.jpg", png)
        val image = assertNotNull(FileImageReader().read(shot.absolutePath))
        assertEquals("image/png", image.mimeType)
        assertEquals(png.size, image.bytes.size)
        assertEquals(png.size, FileImageReader().read(shot.toURI().toString())!!.bytes.size)
        // a text file renamed .png is not an image: nothing is sent to trace.moe
        assertNull(FileImageReader().read(temp("notes.png", "hello world".toByteArray()).absolutePath))
    }

    @Test
    fun aHugeFileIsNeverReadWhole() = runBlocking {
        // 300 MB sparse file that starts like a PNG: only the limit + 1 byte is read, which the screen shows as "too large"
        val big = temp("big.png", png)
        RandomAccessFile(big, "rw").use { it.setLength(300L * 1024 * 1024) }
        val image = assertNotNull(FileImageReader().read(big.absolutePath))
        assertEquals(MAX_IMAGE_BYTES + 1, image.bytes.size)
    }

    @Test
    fun downloadsMustSayAndBeImages() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient()
            val reader = FileImageReader(client)
            fun respond(body: ByteArray, type: String?) {
                val response = MockResponse.Builder().body(Buffer().write(body))
                if (type != null) response.addHeader("Content-Type", type)
                server.enqueue(response.build())
            }
            val url = server.url("/a").toString()
            respond(jpeg, "image/jpeg")
            assertEquals("image/jpeg", reader.read(url)?.mimeType)
            // no Content-Type (a local service answering anything): not uploaded
            respond(jpeg, null)
            assertNull(reader.read(url))
            // says image but is not one
            respond("<html></html>".toByteArray(), "image/png")
            assertNull(reader.read(url))
            respond(png, "text/html")
            assertNull(reader.read(url))
            client.dispatcher.executorService.shutdown()
        }
        // no HTTP client (the tests never go online): a link reads as unreadable instead of throwing
        assertNull(FileImageReader(http = null).read("https://example.org/a.png"))
    }

    @Test
    fun imageTypesBySignature() {
        assertEquals("image/gif", imageMimeType("GIF89a....".toByteArray()))
        assertEquals("image/webp", imageMimeType("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertEquals("image/avif", imageMimeType("\u0000\u0000\u0000\u001cftypavif".toByteArray()))
        assertNull(imageMimeType(ByteArray(0)))
    }
}
