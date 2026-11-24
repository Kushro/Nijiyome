// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalDropTest {
    private fun files(vararg f: File) = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = f.toList()
    }

    @Test
    fun droppedFilesArePaths() {
        val drop = externalDrop(files(File("/tmp/a.kai"), File("/tmp/b c.png")))!!
        assertEquals(listOf(File("/tmp/a.kai").absolutePath, File("/tmp/b c.png").absolutePath), drop.files)
        assertNull(drop.text)
    }

    @Test
    fun droppedTextAndUriLists() {
        val link = externalDrop(StringSelection("  https://example.org/x.png \n"))!!
        assertEquals(emptyList(), link.files)
        assertEquals("https://example.org/x.png", link.text)
        val uris = externalDrop(StringSelection("file:///tmp/a%20b.kai\r\nfile:///tmp/c.kai"))!!
        assertEquals(listOf("/tmp/a b.kai", "/tmp/c.kai"), uris.files)
        assertNull(externalDrop(StringSelection("   ")))
    }

    @Test
    fun pastedPathsAreCleaned() {
        assertEquals("C:\\Users\\x\\a.kai", cleanPastedPath("\"C:\\Users\\x\\a.kai\""))
        assertEquals("/home/x/a b.kai", cleanPastedPath("  '/home/x/a b.kai'  "))
        assertEquals("/tmp/a b.kai", cleanPastedPath("file:///tmp/a%20b.kai"))
        assertNull(cleanPastedPath(" \"\" "))
    }
}
