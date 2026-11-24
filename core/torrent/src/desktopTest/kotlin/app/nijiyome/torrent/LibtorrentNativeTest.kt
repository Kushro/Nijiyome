// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.torrent.TorrentEngineUnavailable
import app.nijiyome.model.torrent.TorrentRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication

/** Unpacking and loading libtorrent4j's native library, and the app without it. */
class LibtorrentNativeTest {
    private val dir: File = Files.createTempDirectory("nijiyome-native").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively() // temp dir created above
    }

    @Test
    fun theLibraryOfEachPlatform() {
        assertEquals("lib/x86_64/libtorrent4j.so", LibtorrentNative.resourceFor("Linux", "amd64"))
        assertEquals("lib/x86_64/libtorrent4j.dll", LibtorrentNative.resourceFor("Windows 11", "amd64"))
        assertNull(LibtorrentNative.resourceFor("Linux", "aarch64"))
        assertNull(LibtorrentNative.resourceFor("Mac OS X", "x86_64"))
    }

    @Test
    fun extractionIsOncePerSizeAndNeverHalfWritten() {
        val payload = ByteArray(300_000) { (it % 251).toByte() }
        var opens = 0
        val open = { _: String -> opens++; ByteArrayInputStream(payload) }
        val first = LibtorrentNative.extract("lib/x86_64/libtorrent4j.so", dir, open)
        assertEquals(File(dir, "libtorrent4j-300000/libtorrent4j.so"), first)
        assertEquals(payload.toList(), first.readBytes().toList())
        val stamp = first.lastModified()
        val again = LibtorrentNative.extract("lib/x86_64/libtorrent4j.so", dir, open)
        assertEquals(first, again)
        assertEquals(stamp, again.lastModified(), "an existing copy of the same size is reused")
        // a truncated copy (a crash while writing it before the rename existed) is written again
        first.writeBytes(ByteArray(10))
        assertEquals(300_000, LibtorrentNative.extract("lib/x86_64/libtorrent4j.so", dir, open).length())
        assertTrue(first.parentFile.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun whatCannotLoadIsReportedNotThrown() {
        assertIs<LibtorrentNative.Result.Failed>(LibtorrentNative.loadNow(dir, "Linux", "aarch64") { null })
        val missing = assertIs<LibtorrentNative.Result.Failed>(LibtorrentNative.loadNow(dir, "Linux", "amd64") { null })
        assertTrue("missing" in missing.reason, missing.reason)
    }

    @Test
    fun theRealLibraryLoadsFromTheJar() {
        // loadNow: another test of this JVM may have loaded it already from its own (since deleted) folder
        val loaded = assertIs<LibtorrentNative.Result.Loaded>(LibtorrentNative.loadNow(dir, System.getProperty("os.name"), System.getProperty("os.arch")) {
            LibtorrentNative::class.java.classLoader.getResourceAsStream(it)
        })
        assertTrue(loaded.library.isFile && loaded.library.length() > 1_000_000, "${loaded.library}")
        assertTrue(loaded.version.startsWith("2."), loaded.version)
        assertEquals(loaded.library.absolutePath, System.getProperty("libtorrent4j.jni.path"))
    }

    @Test
    fun withoutTheLibraryTheAppHasNoTorrentEngine() {
        val folders = DesktopTorrentFolders(File(dir, "state"), File(dir, "dl"), File(dir, "export"), dir)
        val logged = ArrayList<String>()
        val app = koinApplication {
            modules(desktopTorrentModule(folders, loadNative = { LibtorrentNative.Result.Failed("UnsatisfiedLinkError: GLIBC_2.38 not found") }, log = { logged += it }))
        }
        try {
            val repo = app.koin.get<TorrentRepository>()
            assertIs<TorrentEngineUnavailable>(repo)
            assertTrue(repo.library.value.isEmpty())
            assertTrue(logged.single().contains("GLIBC_2.38"))
        } finally {
            app.close()
        }
    }
}
