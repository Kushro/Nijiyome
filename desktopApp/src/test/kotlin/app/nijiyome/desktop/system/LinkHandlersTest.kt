// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v1.1 (F6): "Abrir enlaces magnet con Nijiyome". Only temporary folders and recorded commands: nothing of the real session changes. */
class LinkHandlersTest {
    private val dir = Files.createTempDirectory("links").toFile()
    private val exe = "/opt/Nijiyome/Nijiyome.AppImage"

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun linux(commands: MutableList<List<String>> = mutableListOf()) = LinkHandlers(
        exe,
        windows = false,
        env = mapOf("XDG_DATA_HOME" to File(dir, "data").path, "XDG_CONFIG_HOME" to File(dir, "config").path)::get,
        home = File(dir, "home").path,
        run = { commands += it; 0 to "" },
    )

    @Test
    fun linuxWritesAHiddenEntryAndTheDefaults() {
        val commands = mutableListOf<List<String>>()
        val links = linux(commands)
        assertTrue(links.supported)
        assertFalse(links.isEnabled())
        links.mimeApps.parentFile.mkdirs()
        links.mimeApps.writeText("[Default Applications]\ntext/html=firefox.desktop\n\n[Added Associations]\nx-scheme-handler/magnet=qbittorrent.desktop;\n")

        links.setEnabled(true)
        assertTrue(links.isEnabled())
        val entry = links.handlerEntry.readText()
        assertEquals(File(dir, "data/applications/app.nijiyome.links.desktop"), links.handlerEntry)
        assertTrue("Exec=\"$exe\" %u" in entry, entry)
        assertTrue("NoDisplay=true" in entry)
        assertTrue("MimeType=x-scheme-handler/magnet;application/x-bittorrent;x-scheme-handler/nijiyome;" in entry)
        assertEquals(listOf(listOf("update-desktop-database", File(dir, "data/applications").path)), commands)
        val defaults = LinkHandlers.defaultsOf(links.mimeApps.readText())
        assertEquals("app.nijiyome.links.desktop;", defaults["x-scheme-handler/magnet"])
        assertEquals("app.nijiyome.links.desktop;", defaults["application/x-bittorrent"])
        assertEquals("firefox.desktop", defaults["text/html"])
        // the other groups stay
        assertTrue("[Added Associations]\nx-scheme-handler/magnet=qbittorrent.desktop;" in links.mimeApps.readText())

        links.setEnabled(false)
        assertFalse(links.isEnabled())
        assertEquals(
            "[Default Applications]\ntext/html=firefox.desktop\n\n[Added Associations]\nx-scheme-handler/magnet=qbittorrent.desktop;\n",
            links.mimeApps.readText(),
        )
        // review #14: the entry goes too, so the desktop no longer offers it for magnet links and .torrent files
        assertFalse(links.handlerEntry.exists())
    }

    @Test
    fun turningItOffNeverTakesAnotherClientsChoice() {
        val text = "[Default Applications]\nx-scheme-handler/magnet=qbittorrent.desktop;\n"
        assertEquals(text, LinkHandlers.withDefaults(text, mapOf("x-scheme-handler/magnet" to null), owner = LinkHandlers.HANDLER_ID))
        // a file without the group gets one only to add something
        assertEquals("", LinkHandlers.withDefaults("", mapOf("x-scheme-handler/magnet" to null), owner = LinkHandlers.HANDLER_ID))
        assertEquals(
            "[Added Associations]\na=b\n\n[Default Applications]\nx-scheme-handler/magnet=app.nijiyome.links.desktop;\n",
            LinkHandlers.withDefaults("[Added Associations]\na=b\n", mapOf("x-scheme-handler/magnet" to "app.nijiyome.links.desktop;"), owner = LinkHandlers.HANDLER_ID),
        )
    }

    @Test
    fun windowsUsesTheUserClassesKey() {
        val commands = mutableListOf<List<String>>()
        val registry = mutableMapOf<String, String>()
        val links = LinkHandlers("C:\\Program Files\\Nijiyome\\Nijiyome.exe", windows = true, run = { c ->
            commands += c
            when (c[1]) {
                "query" -> registry[c[2]]?.let { 0 to "    (Default)    REG_SZ    $it" } ?: (1 to "")
                "add" -> 0.also { registry[c[2]] = c[c.indexOf("/d") + 1] } to ""
                else -> 0.also { registry.keys.removeAll { k -> k.startsWith(c[2]) } } to ""
            }
        })
        assertFalse(links.isEnabled())
        links.setEnabled(true)
        assertTrue(links.isEnabled())
        assertTrue(commands.contains(listOf("reg", "add", LinkHandlers.MAGNET_KEY, "/v", "URL Protocol", "/t", "REG_SZ", "/d", "", "/f")))
        assertTrue(commands.contains(listOf("reg", "add", LinkHandlers.MAGNET_COMMAND_KEY, "/ve", "/d", "\"C:\\Program Files\\Nijiyome\\Nijiyome.exe\" \"%1\"", "/f")))
        // review #14: .torrent files too, through the installer's class
        assertEquals(LinkHandlers.TORRENT_CLASS, registry[LinkHandlers.TORRENT_EXT_KEY])
        assertEquals("\"C:\\Program Files\\Nijiyome\\Nijiyome.exe\" \"%1\"", registry[LinkHandlers.TORRENT_COMMAND_KEY])
        links.setEnabled(false)
        assertTrue(commands.contains(listOf("reg", "delete", LinkHandlers.MAGNET_KEY, "/f")))
        assertEquals(listOf("reg", "delete", LinkHandlers.TORRENT_EXT_KEY, "/ve", "/f"), commands.last())
        assertFalse(links.isEnabled())
        assertEquals(null, registry[LinkHandlers.TORRENT_EXT_KEY])
    }

    @Test
    fun windowsLeavesAnotherClientsTorrentDefault() {
        val commands = mutableListOf<List<String>>()
        val links = LinkHandlers("C:\\N\\Nijiyome.exe", windows = true, run = { c ->
            commands += c
            if (c[1] == "query" && c[2] == LinkHandlers.TORRENT_EXT_KEY) 0 to "    (Default)    REG_SZ    qBittorrent.File.Torrent" else 1 to ""
        })
        links.setEnabled(false)
        assertTrue(commands.none { it[1] == "delete" }, commands.toString())
    }

    @Test
    fun aHungCommandDoesNotHoldTheCaller() {
        val t0 = System.nanoTime()
        assertEquals(null, LinkHandlers.runCommand(listOf("sleep", "30"), timeoutSeconds = 1))
        val t1 = System.nanoTime()
        val (code, out) = LinkHandlers.runCommand(listOf("sh", "-c", "echo done; sleep 30 & exit 0"), timeoutSeconds = 5)!!
        assertEquals(0, code)
        assertTrue("done" in out)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 6_000)
        assertTrue((System.nanoTime() - t1) / 1_000_000 < 3_000)
    }

    @Test
    fun aDevelopmentRunOffersNothing() {
        val links = LinkHandlers(null, windows = false, run = { error("no command") })
        assertFalse(links.supported)
        assertFalse(links.isEnabled())
        links.setEnabled(true)
    }
}
