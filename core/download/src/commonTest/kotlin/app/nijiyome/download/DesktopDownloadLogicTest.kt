// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The shared pieces of the desktop downloader: names and layout, HLS playlists and the library walk. */
class DesktopDownloadLogicTest {
    @Test
    fun namesAreSafeEverywhere() {
        assertEquals("Re_Zero - Starting Life", DownloadNames.sanitize("Re:Zero - Starting   Life"))
        assertEquals("a_b_c_d", DownloadNames.sanitize("a/b\\c?d"))
        assertEquals("untitled", DownloadNames.sanitize(" .. "))
        assertEquals("CON_", DownloadNames.sanitize("CON"))
        assertEquals("COM1_", DownloadNames.sanitize("COM1"))
        assertEquals("Trailing", DownloadNames.sanitize("Trailing. "))
        assertEquals(120, DownloadNames.sanitize("x".repeat(300)).length)
        assertEquals("Frieren - 01", DownloadNames.episodeBaseName("Frieren", 1))
        assertEquals("Frieren - 112", DownloadNames.episodeBaseName("Frieren", 112))
    }

    @Test
    fun layoutUnderTheFolder() {
        val base = DownloadNames.episodeBase(File("/dl"), "Sousou no Frieren", 3)
        assertEquals(File("/dl/Sousou no Frieren/Sousou no Frieren - 03"), base)
        assertEquals(File("/dl/Sousou no Frieren/Sousou no Frieren - 03.ts"), DownloadNames.withExtension(base, "ts"))
        assertEquals(File("/dl/Sousou no Frieren/Sousou no Frieren - 03.ts.part"), DownloadNames.partFile(DownloadNames.withExtension(base, "ts")))
        val parts = DownloadNames.partsDir(base)
        assertEquals(File("/dl/Sousou no Frieren/Sousou no Frieren - 03.parts"), parts)
        assertEquals(File(parts, "00007.seg"), DownloadNames.segmentFile(parts, 7))
        assertEquals(File(parts, "init.seg"), DownloadNames.initFile(parts))
    }

    @Test
    fun masterPlaylistVariantsAndThePick() {
        val body = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            360/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=2000000,RESOLUTION=1280x720
            https://cdn.example/720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            1080/index.m3u8
        """.trimIndent()
        val master = assertIs<HlsPlaylist.Parsed.Master>(HlsPlaylist.parse("https://host.example/show/master.m3u8?t=1", body))
        assertEquals(listOf(360, 720, 1080), master.variants.map { it.height })
        assertEquals("https://host.example/show/360/index.m3u8", master.variants[0].url)
        assertEquals("https://cdn.example/720/index.m3u8", master.variants[1].url)
        val variants = master.variants.mapIndexed { i, v -> DownloadLogic.Variant(i, v.height, v.bandwidth) }
        assertEquals(1, DownloadLogic.pickVariant(variants, app.nijiyome.model.DownloadProfile.SAVER_720)?.index)
        assertEquals(2, DownloadLogic.pickVariant(variants, app.nijiyome.model.DownloadProfile.BEST)?.index)
    }

    @Test
    fun mediaPlaylistWithKeysMapAndRanges() {
        val body = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="700@0"
            #EXT-X-KEY:METHOD=AES-128,URI="../key.bin",IV=0x000102030405060708090a0b0c0d0e0f
            #EXTINF:4.0,
            seg0.m4s
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:1000@0
            all.m4s
            #EXTINF:2.5,
            #EXT-X-BYTERANGE:500
            all.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val media = assertIs<HlsPlaylist.Parsed.Media>(HlsPlaylist.parse("https://h.example/a/b/index.m3u8", body))
        val s = media.segments
        assertEquals(3, s.size)
        assertEquals(listOf(10L, 11L, 12L), s.map { it.sequence })
        assertEquals("https://h.example/a/key.bin", s[0].key?.url)
        assertEquals("0x000102030405060708090a0b0c0d0e0f", s[0].key?.iv)
        assertNull(s[1].key)
        assertEquals("https://h.example/a/b/init.mp4", s[0].init?.url)
        assertEquals(700L to 0L, s[0].init?.range)
        assertEquals(1000L to 0L, s[1].range)
        // no offset: right after the previous range of the same resource
        assertEquals(500L to 1000L, s[2].range)
        assertEquals("bytes=1000-1499", HlsPlaylist.rangeHeader(s[2].range!!))
        assertEquals(2.5, s[2].durationSec)
    }

    @Test
    fun refusedPlaylists() {
        assertFailsWith<HlsPlaylist.PlaylistException> { HlsPlaylist.parse("https://h/x.m3u8", "<html>") }
        assertFailsWith<HlsPlaylist.PlaylistException> {
            HlsPlaylist.parse("https://h/x.m3u8", "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"k\"\n#EXTINF:1,\na.ts\n")
        }
        assertFailsWith<HlsPlaylist.PlaylistException> {
            HlsPlaylist.parse("https://h/x.m3u8", "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"skd://k\",KEYFORMAT=\"com.apple.streamingkeydelivery\"\n#EXTINF:1,\na.ts\n")
        }
        assertFailsWith<HlsPlaylist.PlaylistException> { HlsPlaylist.parse("https://h/x.m3u8", "#EXTM3U\n#EXT-X-ENDLIST\n") }
        assertTrue(HlsPlaylist.isPlaylist("﻿  #EXTM3U\n"))
    }

    @Test
    fun libraryVideosAndTheWalk() {
        assertTrue(LibraryScan.isLibraryVideo("[Group] Show - 01 [1080p].mkv"))
        assertFalse(LibraryScan.isLibraryVideo("Show - 01.mkv.part"))
        assertFalse(LibraryScan.isLibraryVideo(".hidden.mp4"))
        assertFalse(LibraryScan.isLibraryVideo("sample.mkv"))
        assertFalse(LibraryScan.isLibraryVideo("notes.txt"))
        val root = Files.createTempDirectory("nijiyome-scan").toFile()
        try {
            File(root, "Show/Season 1").mkdirs()
            File(root, "Show/Season 1/Show - 02.mkv").writeText("x")
            File(root, "Show/Show - 01.mp4").writeText("x")
            File(root, "Show/Show - 03.ts.parts").mkdirs()
            File(root, "Show/Show - 03.ts.parts/00000.ts").writeText("x")
            File(root, ".trash").mkdirs()
            File(root, ".trash/old.mkv").writeText("x")
            File(root, "readme.txt").writeText("x")
            assertEquals(listOf("Show - 02.mkv", "Show - 01.mp4"), LibraryScan.walkVideos(root).map { it.name })
        } finally {
            root.deleteRecursively() // temp dir created above
        }
    }
}
