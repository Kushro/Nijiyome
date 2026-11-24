// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The external mpv's socket / lsfg-vk folder: private (0700) and never a fixed name in a shared folder (review of hito 4). */
class PrivateRuntimeDirTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-private").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    private fun mode(f: File) = PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath()))

    @Test
    fun withoutXdgRuntimeDirAFreshOwnerOnlyFolderIsMade() {
        val shared = File(tmp, "shared-tmp").apply { mkdirs() }
        val a = PrivateRuntimeDir.resolve(emptyMap(), shared)
        val b = PrivateRuntimeDir.resolve(mapOf("XDG_RUNTIME_DIR" to File(tmp, "missing").path), shared)
        assertEquals(shared, a.parentFile)
        assertTrue(a.name.startsWith("nijiyome-") && a.name.length > "nijiyome-".length + 8, a.name)
        assertNotEquals(a, b, "a new random name each time, nothing another user could create first")
        assertEquals("rwx------", mode(a))
    }

    @Test
    fun theRuntimeDirGetsAnOwnerOnlySubfolder() {
        val runtime = File(tmp, "run-user").apply { mkdirs() }
        val d = PrivateRuntimeDir.resolve(mapOf("XDG_RUNTIME_DIR" to runtime.path), File(tmp, "unused"))
        assertEquals(File(runtime, "nijiyome"), d)
        assertEquals("rwx------", mode(d))
        // An existing folder with loose permissions is tightened.
        Files.setPosixFilePermissions(d.toPath(), PosixFilePermissions.fromString("rwxrwxrwx"))
        assertEquals("rwx------", mode(PrivateRuntimeDir.resolve(mapOf("XDG_RUNTIME_DIR" to runtime.path), File(tmp, "unused"))))
    }

    @Test
    fun aLinkPlantedInsteadOfTheFolderIsNotFollowed() {
        val runtime = File(tmp, "run-link").apply { mkdirs() }
        val elsewhere = File(tmp, "elsewhere").apply { mkdirs() }
        Files.createSymbolicLink(File(runtime, "nijiyome").toPath(), elsewhere.toPath())
        val shared = File(tmp, "shared2").apply { mkdirs() }
        val d = PrivateRuntimeDir.resolve(mapOf("XDG_RUNTIME_DIR" to runtime.path), shared)
        assertEquals(shared, d.parentFile, "falls back to a fresh private folder")
        assertEquals("rwx------", mode(d))
    }

    @Test
    fun tokensAreRandom() {
        val t = (1..50).map { PrivateRuntimeDir.token() }.toSet()
        assertEquals(50, t.size)
        assertTrue(t.all { Regex("[0-9a-f]{16}").matches(it) })
    }
}
