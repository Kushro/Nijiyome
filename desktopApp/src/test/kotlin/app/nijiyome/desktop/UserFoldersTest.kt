// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserFoldersTest {
    @Test
    fun withTheDataOverrideEverythingStaysUnderIt() {
        val paths = AppPaths(File("/tmp/nijiyome-data"))
        val f = UserFolders.resolve(paths, env = mapOf("NIJIYOME_DATA_DIR" to "/tmp/nijiyome-data")::get, osName = "Linux", home = "/h")
        assertEquals(File("/tmp/nijiyome-data/Downloads/Nijiyome/Episodes"), f.webDownloads)
        assertEquals(File("/tmp/nijiyome-data/Downloads/Nijiyome"), f.torrentDownloads)
        assertEquals(File("/tmp/nijiyome-data/Downloads"), f.export)
    }

    @Test
    fun xdgDownloadFolder() {
        val home = Files.createTempDirectory("nijiyome-home").toFile()
        try {
            val cfg = File(home, ".config").apply { mkdirs() }
            File(cfg, "user-dirs.dirs").writeText("# comment\nXDG_DESKTOP_DIR=\"\$HOME/Escritorio\"\nXDG_DOWNLOAD_DIR=\"\$HOME/Descargas\"\n")
            val f = UserFolders.resolve(AppPaths(File(home, "data")), env = { null }, osName = "Linux", home = home.path)
            assertEquals(File(home, "Descargas/Nijiyome/Episodes"), f.webDownloads)
            assertEquals(File(home, "Descargas/Nijiyome"), f.torrentDownloads)
            // the environment wins over the file
            assertEquals(File("/x/dl"), UserFolders.systemDownloads(mapOf("XDG_DOWNLOAD_DIR" to "/x/dl")::get, "Linux", home.path))
            // `$HOME` alone means there is no Downloads folder
            File(cfg, "user-dirs.dirs").writeText("XDG_DOWNLOAD_DIR=\"\$HOME/\"\n")
            assertNull(UserFolders.xdgUserDir(File(cfg, "user-dirs.dirs"), "XDG_DOWNLOAD_DIR", home.path))
        } finally {
            home.deleteRecursively() // temp dir created above
        }
    }

    @Test
    fun withoutADownloadsFolderBothGoUnderTheData() {
        val home = Files.createTempDirectory("nijiyome-home").toFile()
        try {
            val data = File(home, "data")
            val f = UserFolders.resolve(AppPaths(data), env = { null }, osName = "Linux", home = home.path)
            assertNull(f.downloads)
            assertEquals(File(data, "Downloads/Nijiyome/Episodes"), f.webDownloads)
            assertEquals(File(data, "Downloads/Nijiyome"), f.torrentDownloads)
            assertEquals(File(data, "Downloads"), f.export)
            // Hallazgo 20 de la revisión: never inside the download engine's own folder (<data>/downloads, wiped by the reset) or
            // the torrent engine's state (<data>/torrents).
            // Compared as the simulated system compares names: Linux by case, Windows ignoring it (whatever the host is).
            val ownStores = listOf("downloads", "torrents")
            fun top(folder: File) = data.toPath().relativize(folder.toPath()).first().toString()
            for (folder in listOf(f.webDownloads, f.torrentDownloads, f.export)) {
                assertTrue(top(folder) !in ownStores, "$folder")
            }
            assertEquals(setOf("Downloads"), f.inDataDir(AppPaths(data)))
            val w = UserFolders.resolve(AppPaths(data), env = mapOf("USERPROFILE" to home.path)::get, osName = "Windows 11", home = home.path)
            assertNull(w.downloads)
            for (folder in listOf(w.webDownloads, w.torrentDownloads, w.export)) {
                assertTrue(ownStores.none { it.equals(top(folder), ignoreCase = true) }, "$folder")
            }
            assertEquals(setOf("Downloads (Nijiyome)"), w.inDataDir(AppPaths(data)))
            File(home, "Downloads").mkdirs()
            assertEquals(File(home, "Downloads/Nijiyome/Episodes"), UserFolders.resolve(AppPaths(data), mapOf("USERPROFILE" to home.path)::get, "Windows 11", home.path).webDownloads)
        } finally {
            home.deleteRecursively() // temp dir created above
        }
    }
}

class SignalChainingTest {
    private val jdk = System.getProperty("java.home")

    @Test
    fun linuxRelaunchesOnceWithTheSameCommand() {
        val cmd = SignalChaining.relaunchCommand({ null }, "Linux", jdk, "/opt/nijiyome/bin/Nijiyome", listOf("magnet:?xt=urn:btih:x"))
        if (!File(jdk, "lib/libjsig.so").isFile) return // a JDK without it: nothing to relaunch with
        assertEquals(listOf("/opt/nijiyome/bin/Nijiyome", "magnet:?xt=urn:btih:x"), cmd)
        assertNull(SignalChaining.relaunchCommand(mapOf("NIJIYOME_JSIG" to "1")::get, "Linux", jdk, "/x", emptyList()))
        assertNull(SignalChaining.relaunchCommand(mapOf("LD_PRELOAD" to "$jdk/lib/libjsig.so")::get, "Linux", jdk, "/x", emptyList()))
        assertNull(SignalChaining.relaunchCommand({ null }, "Windows 11", jdk, "C:/x.exe", emptyList()))
        assertNull(SignalChaining.relaunchCommand({ null }, "Linux", "/no/jdk", "/x", emptyList()))
    }
}
