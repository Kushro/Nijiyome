// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FileNovelTextStoreTest {
    private val dir = Files.createTempDirectory("novel-store").toFile().apply { deleteOnExit() }

    @Test
    fun storesReadsReplacesAndDeletes() = runBlocking {
        val store = FileNovelTextStore(dir.resolve("novel-chapters"))
        val id = "ext/source/chapter 1?é"
        assertNull(store.get(id))
        assertEquals("{\"t\":\"ñ\"}".toByteArray().size.toLong(), store.put(id, "m1", "{\"t\":\"ñ\"}", 1L))
        assertEquals("{\"t\":\"ñ\"}", store.get(id))
        store.put(id, "m1", "{}", 2L)
        assertEquals("{}", store.get(id))
        store.put("other", "m1", "x", 3L)
        store.delete(id)
        assertNull(store.get(id))
        assertEquals("x", store.get("other"))
        // survives a new instance (the app restarting)
        assertEquals("x", FileNovelTextStore(dir.resolve("novel-chapters")).get("other"))
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        val folder = dir.resolve("c")
        val store = FileNovelTextStore(folder)
        store.put("a", "m", "1", 0L)
        store.clear()
        assertNull(store.get("a"))
        assertFalse(folder.exists())
        // usable again after a reset
        store.put("a", "m", "2", 0L)
        assertEquals("2", store.get("a"))
    }

    @Test
    fun theStoreFileNamesTheFolder() {
        val store = novelTextStore(dir.resolve(NovelTextStore.FILE_NAME))
        assertEquals(FileNovelTextStore::class, store::class)
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
