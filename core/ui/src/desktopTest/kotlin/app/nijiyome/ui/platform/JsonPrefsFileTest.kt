// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonPrefsFileTest {
    private val dir = Files.createTempDirectory("prefs").toFile().apply { deleteOnExit() }

    @Test
    fun keepsValuesAcrossInstances() {
        val file = dir.resolve("a/prefs.json")
        assertTrue(JsonPrefsFile(file).read().isEmpty())
        JsonPrefsFile(file).put("x", JsonPrimitive(1))
        JsonPrefsFile(file).put("y", JsonPrimitive("z"))
        assertEquals(JsonPrimitive(1), JsonPrefsFile(file).read()["x"])
        JsonPrefsFile(file).put("x", null)
        assertEquals(setOf("y"), JsonPrefsFile(file).read().keys)
    }

    @Test
    fun corruptFileReadsEmpty() {
        val file = dir.resolve("bad.json").apply { writeText("{nope") }
        assertTrue(JsonPrefsFile(file).read().isEmpty())
        JsonPrefsFile(file).put("k", JsonPrimitive(true))
        assertEquals(JsonPrimitive(true), JsonPrefsFile(file).read()["k"])
    }
    @Test
    fun withoutAtomicRenameItStillReplacesAndLeavesNoTmp() {
        val dir = java.nio.file.Files.createTempDirectory("replace").toFile().apply { deleteOnExit() }
        val target = java.io.File(dir, "a.json").apply { writeText("old") }
        val tried = mutableListOf<Int>()
        writeReplacing(target, "new".toByteArray()) { from, to, options ->
            tried += options.size
            // a FUSE / network / exFAT mount: the atomic rename is refused, a plain replace works
            if (java.nio.file.StandardCopyOption.ATOMIC_MOVE in options) throw java.nio.file.AtomicMoveNotSupportedException(from.toString(), to.toString(), "no")
            java.nio.file.Files.move(from, to, *options)
        }
        kotlin.test.assertEquals(listOf(2, 1), tried)
        kotlin.test.assertEquals("new", target.readText())
        kotlin.test.assertFalse(java.io.File(dir, "a.json.tmp").exists())
    }

    @Test
    fun aFailedWriteLeavesTheOldFileAndNoTmp() {
        val dir = java.nio.file.Files.createTempDirectory("replace").toFile().apply { deleteOnExit() }
        val target = java.io.File(dir, "a.json").apply { writeText("old") }
        kotlin.test.assertFailsWith<java.io.IOException> {
            writeReplacing(target, "new".toByteArray()) { _, _, _ -> throw java.nio.file.FileSystemException("disk full") }
        }
        kotlin.test.assertEquals("old", target.readText())
        kotlin.test.assertFalse(java.io.File(dir, "a.json.tmp").exists())
    }
}
