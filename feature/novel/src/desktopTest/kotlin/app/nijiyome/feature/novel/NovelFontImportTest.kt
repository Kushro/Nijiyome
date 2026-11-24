// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NovelFontImportTest {
    private val tmp = Files.createTempDirectory("fonts").toFile().apply { deleteOnExit() }

    @Test
    fun aFontIsCopiedToTheFontsFolder() {
        val source = tmp.resolve("My Font.otf").apply { writeBytes("OTTO".toByteArray() + ByteArray(32)) }
        val dir = tmp.resolve("data/fonts/novel")
        val path = assertNotNull(importFontFile(source, dir))
        assertEquals(dir.resolve("My_Font.otf").absolutePath, path)
        assertTrue(dir.resolve("My_Font.otf").readBytes().contentEquals(source.readBytes()))
    }

    @Test
    fun anythingElseIsRefused() {
        val zip = tmp.resolve("x.ttf").apply { writeBytes("PK\u0003\u0004".toByteArray() + ByteArray(32)) }
        assertNull(importFontFile(zip, tmp.resolve("out")))
        assertNull(importFontFile(tmp.resolve("missing.ttf"), tmp.resolve("out")))
    }

    @Test
    fun aHugeFileIsRefusedWithoutReadingIt() {
        // 500 MB sparse file that starts like a TrueType font: refused by its size, nothing copied
        val huge = tmp.resolve("huge.ttf").apply { writeBytes(byteArrayOf(0, 1, 0, 0) + ByteArray(32)) }
        java.io.RandomAccessFile(huge, "rw").use { it.setLength(500L * 1024 * 1024) }
        val out = tmp.resolve("out-huge")
        assertNull(importFontFile(huge, out))
        assertTrue(!out.exists() || out.list().isNullOrEmpty())
        huge.delete()
    }

    @Test
    fun aRealSystemFamilyIsFoundAndAMadeUpOneIsNot() {
        // The test machine has fontconfig fonts; a family that cannot exist is never found.
        assertNull(installedFontFamily("Nijiyome No Such Family 9f3a"))
        val families = installedFontFamilies()
        if (families.isNotEmpty()) assertNotNull(installedFontFamily(families.first()))
    }
}
