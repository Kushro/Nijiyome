// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSecretStoreTest {
    private val dir: File = Files.createTempDirectory("secrets-test").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** A keyring in memory: [available] false plays a locked keyring / no Secret Service. */
    private class FakeVault(override val id: String = SecretServiceVault.ID) : MasterKeyVault {
        var key: ByteArray? = null
        var available = true
        var stores = 0
        override fun load(): ByteArray? = if (available) key else throw KeyringUnavailable("locked")
        override fun store(key: ByteArray) {
            if (!available) throw KeyringUnavailable("locked")
            stores++
            this.key = key
        }
        override fun delete() {
            key = null
        }
    }

    @Test
    fun secretsAreSealedAndTheKeyLivesInTheKeyring() = runBlocking {
        val vault = FakeVault()
        val store = DesktopSecretStore(dir, listOf(vault, FileVault(File(dir, "secrets.key"))))
        store.put("list.mal.token", "TOKEN-123")
        assertEquals(SecretServiceVault.ID, store.backend)
        assertEquals("secret-service", File(dir, DesktopSecretStore.BACKEND_FILE).readText())
        assertFalse(File(dir, "secrets.key").exists(), "no key file while the keyring works")
        val sealed = File(dir, "secrets.bin").readBytes()
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("TOKEN-123"), "the file is encrypted")
        // A new run reads the same key from the keyring.
        val again = DesktopSecretStore(dir, listOf(vault, FileVault(File(dir, "secrets.key"))))
        assertEquals("TOKEN-123", again.get("list.mal.token"))
        assertEquals(1, vault.stores)
    }

    @Test
    fun aLockedKeyringNeitherReadsNorOverwrites() = runBlocking {
        val vault = FakeVault()
        DesktopSecretStore(dir, listOf(vault)).put("a", "1")
        val before = File(dir, "secrets.bin").readBytes()
        vault.available = false
        val locked = DesktopSecretStore(dir, listOf(vault, FileVault(File(dir, "secrets.key"))))
        assertNull(locked.get("a"))
        assertFailsWith<KeyringUnavailable> { locked.put("b", "2") }
        assertTrue(before.contentEquals(File(dir, "secrets.bin").readBytes()), "nothing rewritten while locked")
        assertFalse(File(dir, "secrets.key").exists(), "the recorded keyring is not swapped for a file behind the user's back")
        vault.available = true
        assertEquals("1", DesktopSecretStore(dir, listOf(vault)).get("a"))
    }

    @Test
    fun withoutAKeyringTheKeyGoesToAnOwnerOnlyFile() = runBlocking {
        val vault = FakeVault().apply { available = false }
        val store = DesktopSecretStore(dir, listOf(vault, FileVault(File(dir, "secrets.key"))))
        store.put("debrid.rd.api_key", "K")
        assertEquals(FileVault.ID, store.backend)
        val key = File(dir, "secrets.key")
        assertEquals(32, key.length().toInt())
        val perms = runCatching { Files.getPosixFilePermissions(key.toPath()) }.getOrNull()
        if (perms != null) assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms)
        assertEquals("K", DesktopSecretStore(dir, listOf(vault, FileVault(key))).get("debrid.rd.api_key"))
    }

    @Test
    fun clearRemovesTheFileAndTheKeyringItem() = runBlocking {
        val vault = FakeVault()
        val store = DesktopSecretStore(dir, listOf(vault))
        store.put("x", "1")
        store.clear()
        assertNull(vault.key)
        assertFalse(File(dir, "secrets.bin").exists())
        assertFalse(File(dir, DesktopSecretStore.BACKEND_FILE).exists())
        assertNull(store.get("x"))
    }

    @Test
    fun aKeyringThatLostTheKeyStartsOver() = runBlocking {
        val vault = FakeVault()
        DesktopSecretStore(dir, listOf(vault)).put("x", "1")
        vault.key = null
        val store = DesktopSecretStore(dir, listOf(vault))
        assertNull(store.get("x"), "the old file cannot be opened without its key")
        store.put("y", "2")
        assertEquals("2", DesktopSecretStore(dir, listOf(vault)).get("y"))
    }

    /** Hallazgo 3 de la revisión: a `secret-tool` that never answers (locked keyring, stuck bus) must not hang the caller. */
    @Test
    fun aSecretToolThatNeverAnswersTimesOut() {
        val stuck = SecretServiceVault(command = listOf("bash", "-c", "sleep 30", "stand-in"), timeoutSeconds = 1)
        val started = System.currentTimeMillis()
        assertFailsWith<KeyringUnavailable> { stuck.load() }
        val elapsed = System.currentTimeMillis() - started
        assertTrue(elapsed < 10_000, "gave up after $elapsed ms")
        // A grandchild that keeps the output open after the tool itself ended must not block either.
        val lingering = SecretServiceVault(command = listOf("bash", "-c", "(sleep 30 &) ; exit 1", "stand-in"), timeoutSeconds = 1)
        val t0 = System.currentTimeMillis()
        assertNull(lingering.load())
        assertTrue(System.currentTimeMillis() - t0 < 10_000)
    }

    @Test
    fun aSecretToolThatAnswersIsRead() {
        val key = ByteArray(32) { (it * 7).toByte() }
        val b64 = java.util.Base64.getEncoder().encodeToString(key)
        val ok = SecretServiceVault(command = listOf("bash", "-c", "printf '%s\\n' '$b64'", "stand-in"), timeoutSeconds = 5)
        assertTrue(key.contentEquals(ok.load()))
        val failing = SecretServiceVault(command = listOf("bash", "-c", "echo 'Cannot autolaunch D-Bus' >&2; exit 1", "stand-in"), timeoutSeconds = 5)
        assertFailsWith<KeyringUnavailable> { failing.load() }
    }

    /**
     * The Secret Service vault against a real keyring daemon on a private session bus. Runs only inside
     * `.claude/run-dbus-tests.sh` (NIJIYOME_DBUS_TESTS=1, its own `dbus-run-session` and an unlocked gnome-keyring-daemon with its
     * data under a temporary XDG_DATA_HOME); never against the user's keyring.
     */
    @Test
    fun secretServiceVaultOnAPrivateBus() {
        if (System.getenv("NIJIYOME_DBUS_TESTS") != "1") return
        val bus = System.getenv("DBUS_SESSION_BUS_ADDRESS").orEmpty()
        assertTrue(bus.isNotEmpty())
        val vault = SecretServiceVault()
        vault.delete()
        assertNull(vault.load())
        val key = ByteArray(32) { it.toByte() }
        vault.store(key)
        assertTrue(key.contentEquals(vault.load()))
        vault.delete()
        assertNull(vault.load())
        runBlocking {
            val store = DesktopSecretStore(dir, listOf(vault, FileVault(File(dir, "secrets.key"))))
            store.put("list.anilist.token", "T")
            assertEquals(SecretServiceVault.ID, store.backend)
            assertEquals("T", DesktopSecretStore(dir, listOf(SecretServiceVault())).get("list.anilist.token"))
            store.clear()
            assertNull(vault.load())
        }
    }
}
