// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSubtitleToolsTest {
    private val root: File = Files.createTempDirectory("subtools").toFile().apply { deleteOnExit() }
    private val tools = DesktopSubtitleTools(File(root, "cache"), File(root, "fonts"), OkHttpClient())

    @Test
    fun importCopiesAndParsesSrtAndAss() = runBlocking {
        val srt = File(root, "ep 1 (es).srt").apply { writeText("1\n00:00:01,000 --> 00:00:02,500\nHola\n\n2\n00:00:03,000 --> 00:00:04,000\nMundo\n") }
        val (copy, cues) = tools.import(srt)
        assertEquals(File(File(root, "cache"), "subs"), copy.parentFile)
        assertEquals("ep 1 _es_.srt", copy.name)
        assertEquals(listOf("Hola", "Mundo"), cues.map { it.text })
        assertEquals(1_000L, cues.first().startMs)

        val ass = File(root, "show.ass").apply {
            writeText(
                "[Script Info]\nScriptType: v4.00+\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                    "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Línea ASS\n",
            )
        }
        val (assCopy, assCues) = tools.import(ass)
        assertTrue(assCopy.isFile)
        assertEquals("Línea ASS", assCues.single().text)
        // the copy is what fetch() of its file URL returns, without reading it again
        assertEquals(assCues, tools.fetch(assCopy.toURI().toString(), emptyMap()))
    }

    @Test
    fun emptyOrMissingFilesFail() = runBlocking<Unit> {
        assertFailsWith<IOException> { tools.import(File(root, "missing.srt")) }
        val empty = File(root, "empty.srt").apply { writeText("not a subtitle") }
        assertFailsWith<IOException> { tools.import(empty) }
    }

    // Hito 6 review: a source's subtitle URL cannot make the app read a local file it chose, nor fill the memory.

    private val srtText = "1\n00:00:01,000 --> 00:00:02,000\nHola\n"

    @Test
    fun onlyPickedFilesAreReadLocally() = runBlocking<Unit> {
        val outside = File(root, "secret.srt").apply { writeText(srtText) }
        assertFailsWith<IOException> { tools.fetch(outside.toURI().toString(), emptyMap()) }
        assertFailsWith<IOException> { tools.fetch(outside.absolutePath, emptyMap()) }
        // a path that only looks like it is in the picked folder
        val sneaky = File(tools.filesDir, "../../secret.srt").path
        assertFailsWith<IOException> { tools.fetch(sneaky, emptyMap()) }
        assertFailsWith<IOException> { tools.fetch("file://" + sneaky, emptyMap()) }
        for (url in listOf("ftp://example.invalid/a.srt", "jar:file:/x.jar!/a.srt", "content://x/a.srt", "data:text/plain,1", "a.srt")) {
            assertFailsWith<IOException>(url) { tools.fetch(url, emptyMap()) }
        }
        // the copy of a file the user picked is read (a new instance: not from the parsed cache)
        val (copy, _) = tools.import(File(root, "picked.srt").apply { writeText(srtText) })
        val fresh = DesktopSubtitleTools(File(root, "cache"), File(root, "fonts"), OkHttpClient())
        assertEquals(listOf("Hola"), fresh.fetch(copy.toURI().toString(), emptyMap()).map { it.text })
        assertEquals(listOf("Hola"), DesktopSubtitleTools(File(root, "cache"), File(root, "fonts"), OkHttpClient()).fetch(copy.absolutePath, emptyMap()).map { it.text })
    }

    @Test
    fun remoteTracksAreCappedWhileReadingAndNeedASubtitleType() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.start()
            val big = "a".repeat((DesktopSubtitleTools.MAX_SUBTITLE_BYTES + 1).toInt())
            // no Content-Length (chunked): cut while reading
            server.enqueue(MockResponse.Builder().chunkedBody(big, 64 * 1024).build())
            assertFailsWith<IOException> { tools.fetch(server.url("/chunked.srt").toString(), emptyMap()) }
            // a declared length over the cap is refused before reading
            server.enqueue(MockResponse.Builder().body(big).build())
            assertFailsWith<IOException> { tools.fetch(server.url("/big.srt").toString(), emptyMap()) }
            // a video / image is not a subtitle
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(srtText).build())
            assertFailsWith<IOException> { tools.fetch(server.url("/video.srt").toString(), emptyMap()) }
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/vtt; charset=utf-8").body(srtText).build())
            assertEquals(listOf("Hola"), tools.fetch(server.url("/ok.srt").toString(), emptyMap()).map { it.text })
        }
    }

    @Test
    fun readCappedStopsAtTheCap() {
        val endless = object : InputStream() {
            override fun read(): Int = 'a'.code
        }
        assertFailsWith<IOException> { DesktopSubtitleTools.readCapped(endless, 100_000) }
        assertContentEquals(byteArrayOf(1, 2, 3), DesktopSubtitleTools.readCapped(byteArrayOf(1, 2, 3).inputStream(), 3))
    }

    @Test
    fun subtitleContentTypes() {
        for (ok in listOf(null, "", "text/plain", "text/vtt; charset=utf-8", "text/x-ssa", "application/x-subrip", "application/octet-stream", "binary/octet-stream", "application/ttml+xml")) {
            assertTrue(DesktopSubtitleTools.isSubtitleContentType(ok), "$ok")
        }
        for (bad in listOf("video/mp4", "audio/aac", "image/png", "font/ttf", "application/vnd.apple.mpegurl", "application/zip", "application/pdf", "application/json")) {
            assertFalse(DesktopSubtitleTools.isSubtitleContentType(bad), bad)
        }
    }

    @Test
    fun embeddedTracksAreLeftToMpv() {
        assertNull(tools.mediaSource("/tmp/video.mkv", emptyMap(), null))
    }

    @Test
    fun fontsAreImportedListedAndDeletedWithoutTheBundledOnes() {
        val fonts = tools.fonts
        fonts.dir.mkdirs()
        File(fonts.dir, "nijiyome_outfit_medium.ttf").writeBytes(byteArrayOf(1, 2, 3))
        val src = File(root, "My Font!.otf").apply { writeBytes(ByteArray(64) { 7 }) }
        assertEquals("My Font_.otf", fonts.import(src))
        assertNull(fonts.import(File(root, "notes.txt").apply { writeText("x") }))
        assertEquals("user_nijiyome_x.ttf", fonts.import(File(root, "nijiyome_x.ttf").apply { writeBytes(ByteArray(8) { 1 }) }))
        assertEquals(listOf("My Font_.otf", "user_nijiyome_x.ttf"), fonts.list().map { it.name })
        assertNull(fonts.file("family:Noto Sans"))
        assertTrue(fonts.delete("My Font_.otf"))
        assertEquals(listOf("user_nijiyome_x.ttf"), fonts.list().map { it.name })
    }
}
