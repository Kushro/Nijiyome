// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenFolderDesktopTest {
    @Test
    fun aFileOpensItsFolderAndAMissingPathItsNearestExistingParent() {
        val root = Files.createTempDirectory("open-folder").toFile()
        try {
            val sub = File(root, "Show").apply { mkdirs() }
            val video = File(sub, "Show - 01.mkv").apply { writeText("x") }
            assertEquals(sub.absoluteFile, folderToOpen(video.path))
            assertEquals(sub.absoluteFile, folderToOpen(sub.path))
            assertEquals(sub.absoluteFile, folderToOpen(video.toURI().toString()))
            assertEquals(sub.absoluteFile, folderToOpen(File(sub, "gone/Show - 02.mkv").path))
            assertNull(folderToOpen(""))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun eachSystemHasItsFileManagerCommand() {
        val dir = File("/data/Anime")
        assertEquals(listOf("xdg-open", dir.absolutePath), openFolderCommand("Linux", dir))
        assertEquals(listOf("explorer.exe", dir.absolutePath), openFolderCommand("Windows 11", dir))
        assertEquals(listOf("open", dir.absolutePath), openFolderCommand("Mac OS X", dir))
        assertNull(openFolderCommand("SunOS", dir))
    }
}
