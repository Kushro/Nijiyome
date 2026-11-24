// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSecretStoreTest {
    private val dir: File = Files.createTempDirectory("secrets-test").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    /**
     * The `secret-tool` stand-ins are bash one-liners, and [SecretServiceVault] is the Linux vault only (Windows uses
     * [DpapiVault]): those tests run on Linux, where `bash` is the real one.
     */
    private fun assumeLinux() =
        assumeTrue("SecretServiceVault is Linux only", System.getProperty("os.name").orEmpty().startsWith("Linux"))

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
        val before = File(dir, "secrets.bin").readBytes()
        vault.key = null
        val store = DesktopSecretStore(dir, listOf(vault), clock = { FIXED_TIME })
        assertNull(store.get("x"), "the old file cannot be opened without its key")
        // 1.1.2: the unreadable file is set aside, not read as empty and overwritten
        val aside = File(dir, "secrets.bin.unreadable-20261124-101500")
        assertTrue(aside.isFile && before.contentEquals(aside.readBytes()), "the old file is kept as it was")
        assertEquals(aside.name, store.unreadableBackup)
        store.put("y", "2")
        assertEquals("2", DesktopSecretStore(dir, listOf(vault)).get("y"))
        assertTrue(before.contentEquals(aside.readBytes()))
    }

    /** 1.1.2: a key that is there but does not open secrets.bin (another folder replaced it): file kept aside, notice, no reuse. */
    @Test
    fun aKeyThatDoesNotOpenTheFileSetsItAsideAndWarns() = runBlocking {
        val vault = FakeVault()
        DesktopSecretStore(dir, listOf(vault)).put("list.anilist.token", "T")
        val before = File(dir, "secrets.bin").readBytes()
        vault.key = ByteArray(32) { 9 } // what another data folder wrote over it
        val logs = captureLogs()
        try {
            val store = DesktopSecretStore(dir, listOf(vault), clock = { FIXED_TIME })
            assertNull(store.get("list.anilist.token"))
            assertFalse(File(dir, "secrets.bin").exists(), "not treated as an empty file in place")
            val aside = File(dir, "secrets.bin.unreadable-20261124-101500")
            assertTrue(before.contentEquals(aside.readBytes()), "nothing overwritten")
            assertEquals(aside.name, store.unreadableBackup)
            assertTrue(logs.any { it.startsWith("WARN secrets") && "unreadable" in it }, "logged: $logs")
            assertTrue(ByteArray(32) { 9 }.contentEquals(vault.key), "the keyring item is not replaced")
            store.put("a", "1")
            // The notice survives a restart until dismissed.
            val again = DesktopSecretStore(dir, listOf(vault))
            assertEquals("1", again.get("a"))
            assertEquals(aside.name, again.unreadableBackup)
            again.dismissUnreadable()
            assertNull(DesktopSecretStore(dir, listOf(vault)).unreadableBackup)
            assertTrue(aside.isFile, "dismissing keeps the set-aside file")
        } finally {
            restoreLogs()
        }
    }

    /** A keyring that cannot be asked in a folder without secrets.backend never makes the existing file be set aside. */
    @Test
    fun aLockedKeyringDoesNotSetTheFileAside() = runBlocking {
        val vault = FakeVault()
        DesktopSecretStore(dir, listOf(vault)).put("a", "1")
        File(dir, DesktopSecretStore.BACKEND_FILE).delete()
        vault.available = false
        val store = DesktopSecretStore(dir, listOf(vault, FileVault(File(dir, "secrets.key"))))
        assertNull(store.get("a"))
        assertTrue(File(dir, "secrets.bin").isFile)
        assertFalse(File(dir, "secrets.key").exists())
        assertNull(store.unreadableBackup)
        vault.available = true
        assertEquals("1", DesktopSecretStore(dir, listOf(vault)).get("a"), "its own key is found again without secrets.backend")
    }

    // ---- 1.1.2: one keyring item per data folder, through a secret-tool stand-in -------------------------------------------

    /**
     * A `secret-tool` stand-in over a directory: items are files, `lookup` and `clear` match every item that has the given
     * attributes (Secret Service semantics), `store` replaces the item with exactly those attributes.
     */
    private val keyring: File by lazy { Files.createTempDirectory("fake-keyring").toFile() }
    private val standIn: File by lazy {
        File(keyring.parentFile, keyring.name + ".sh").apply {
            writeText(
                """
                db="${'$'}FAKE_KEYRING"; cmd=${'$'}1; shift
                [ "${'$'}cmd" = store ] && shift
                attrs=(); while [ ${'$'}# -gt 1 ]; do attrs+=("${'$'}1=${'$'}2"); shift 2; done
                matches() { for a in "${'$'}{attrs[@]}"; do grep -qxF "${'$'}a" "${'$'}1.attrs" || return 1; done; return 0; }
                case ${'$'}cmd in
                  store) secret=${'$'}(cat); id=${'$'}(printf '%s
' "${'$'}{attrs[@]}" | sort | sha256sum | cut -c1-16)
                    printf '%s
' "${'$'}{attrs[@]}" | sort > "${'$'}db/${'$'}id.attrs"; printf '%s' "${'$'}secret" > "${'$'}db/${'$'}id" ;;
                  lookup) for f in "${'$'}db"/*.attrs; do [ -e "${'$'}f" ] || continue; b=${'$'}{f%.attrs}
                    if matches "${'$'}b"; then cat "${'$'}b"; echo; exit 0; fi; done; exit 1 ;;
                  clear) for f in "${'$'}db"/*.attrs; do [ -e "${'$'}f" ] || continue; b=${'$'}{f%.attrs}
                    matches "${'$'}b" && rm -f "${'$'}b" "${'$'}f"; done; exit 0 ;;
                esac
                """.trimIndent(),
            )
        }
    }

    private fun serviceVault(dataDir: File) = SecretServiceVault(
        command = listOf("bash", standIn.path),
        env = mapOf("FAKE_KEYRING" to keyring.path),
        timeoutSeconds = 10,
        folderId = { DataFolderId.of(dataDir) },
    )

    private fun items(): Int = keyring.listFiles().orEmpty().count { it.name.endsWith(".attrs") }

    private fun legacyStore(key: ByteArray) {
        val p = ProcessBuilder(listOf("bash", standIn.path, "store", "--label=x") + SecretServiceVault.LEGACY_ATTRIBUTES)
        p.environment()["FAKE_KEYRING"] = keyring.path
        val proc = p.start()
        proc.outputStream.use { it.write(java.util.Base64.getEncoder().encode(key)) }
        assertEquals(0, proc.waitFor())
    }

    private fun legacyLoad(): ByteArray? = serviceVault(dir).loadLegacy()

    /** A secrets.bin sealed with [key], as 1.1.1 left it with the shared keyring item. */
    private fun sealWith(dataDir: File, key: ByteArray, values: Map<String, String>) = runBlocking {
        val spec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val s = SealedFileSecretStore(dataDir, app.nijiyome.link.AesGcmKeyCipher { spec })
        values.forEach { (k, v) -> s.put(k, v) }
    }

    private fun newDir(): File = Files.createTempDirectory("secrets-test-b").toFile().also { extraDirs += it }
    private val extraDirs = mutableListOf<File>()

    @AfterTest
    fun tearDownKeyring() {
        extraDirs.forEach { it.deleteRecursively() }
        keyring.deleteRecursively()
        standIn.delete()
    }

    /** The incident: a second data folder (a test run with its own NIJIYOME_DATA_DIR) on the same keyring. */
    @Test
    fun twoDataFoldersOnTheSameKeyringDoNotOverwriteEachOther() = runBlocking {
        assumeLinux()
        val other = newDir()
        DesktopSecretStore(dir, listOf(serviceVault(dir))).put("list.anilist.token", "MINE")
        DesktopSecretStore(other, listOf(serviceVault(other))).put("list.anilist.token", "TEST")
        assertEquals(2, items(), "one item per folder")
        assertTrue(DataFolderId.of(dir) != DataFolderId.of(other))
        assertEquals("MINE", DesktopSecretStore(dir, listOf(serviceVault(dir))).get("list.anilist.token"))
        assertEquals("TEST", DesktopSecretStore(other, listOf(serviceVault(other))).get("list.anilist.token"))
        // Clearing one (Restablecer la app) leaves the other's key.
        DesktopSecretStore(other, listOf(serviceVault(other))).clear()
        assertEquals(1, items())
        assertEquals("MINE", DesktopSecretStore(dir, listOf(serviceVault(dir))).get("list.anilist.token"))
        assertNull(legacyLoad(), "nothing is written under the shared pre-1.1.2 attributes")
    }

    /** A moved folder keeps its key: the id is a file of the folder, not its path. */
    @Test
    fun aMovedFolderKeepsItsKey() = runBlocking {
        assumeLinux()
        DesktopSecretStore(dir, listOf(serviceVault(dir))).put("a", "1")
        val moved = File(newDir(), "moved")
        assertTrue(dir.renameTo(moved))
        assertEquals("1", DesktopSecretStore(moved, listOf(serviceVault(moved))).get("a"))
    }

    /** Updating from 1.1.1: the shared item that opens this folder's file becomes this folder's own item. */
    @Test
    fun theSharedItemOfAnOlderVersionIsMovedToTheFolder() = runBlocking {
        assumeLinux()
        val key = ByteArray(32) { (it + 1).toByte() }
        legacyStore(key)
        sealWith(dir, key, mapOf("list.anilist.token" to "OLD"))
        File(dir, DesktopSecretStore.BACKEND_FILE).writeText(SecretServiceVault.ID)
        val store = DesktopSecretStore(dir, listOf(serviceVault(dir), FileVault(File(dir, "secrets.key"))))
        assertEquals("OLD", store.get("list.anilist.token"))
        assertTrue(key.contentEquals(serviceVault(dir).load()), "copied to this folder's item")
        assertTrue(key.contentEquals(legacyLoad()), "the shared item stays for other folders and for 1.1.1")
        // A copy of the folder made before the update (no folder id yet) still opens with the shared item.
        val copy = newDir()
        File(dir, "secrets.bin").copyTo(File(copy, "secrets.bin"))
        File(copy, DesktopSecretStore.BACKEND_FILE).writeText(SecretServiceVault.ID)
        assertEquals("OLD", DesktopSecretStore(copy, listOf(serviceVault(copy))).get("list.anilist.token"))
        assertNull(store.unreadableBackup)
        assertEquals("OLD", DesktopSecretStore(dir, listOf(serviceVault(dir))).get("list.anilist.token"))
    }

    /** The same without secrets.backend (a folder whose record was lost). */
    @Test
    fun theSharedItemIsMovedEvenWithoutABackendRecord() = runBlocking {
        assumeLinux()
        val key = ByteArray(32) { (it + 3).toByte() }
        legacyStore(key)
        sealWith(dir, key, mapOf("t" to "V"))
        assertEquals("V", DesktopSecretStore(dir, listOf(serviceVault(dir), FileVault(File(dir, "secrets.key")))).get("t"))
        assertEquals(SecretServiceVault.ID, File(dir, DesktopSecretStore.BACKEND_FILE).readText())
        assertFalse(File(dir, "secrets.key").exists())
    }

    /** The user's case after the incident: the shared item holds another folder's key, which does not open this file. */
    @Test
    fun aSharedItemThatDoesNotOpenTheFileIsNotUsed() = runBlocking {
        assumeLinux()
        val mine = ByteArray(32) { 5 }
        val theirs = ByteArray(32) { 6 }
        legacyStore(theirs)
        sealWith(dir, mine, mapOf("list.anilist.token" to "OLD"))
        val before = File(dir, "secrets.bin").readBytes()
        File(dir, DesktopSecretStore.BACKEND_FILE).writeText(SecretServiceVault.ID)
        val store = DesktopSecretStore(dir, listOf(serviceVault(dir)), clock = { FIXED_TIME })
        assertNull(store.get("list.anilist.token"))
        val aside = File(dir, "secrets.bin.unreadable-20261124-101500")
        assertTrue(before.contentEquals(aside.readBytes()))
        assertEquals(aside.name, store.unreadableBackup)
        assertTrue(theirs.contentEquals(legacyLoad()), "the shared item is left alone")
        val own = serviceVault(dir).load()
        assertNotNull(own)
        assertFalse(own.contentEquals(theirs), "this folder got a key of its own")
        store.put("list.anilist.token", "NEW")
        assertEquals("NEW", DesktopSecretStore(dir, listOf(serviceVault(dir))).get("list.anilist.token"))
    }

    /** A new folder never takes the shared item when it has no secrets.bin of its own (it would take another folder's key). */
    @Test
    fun aNewFolderDoesNotTakeTheSharedItem() = runBlocking {
        assumeLinux()
        val theirs = ByteArray(32) { 7 }
        legacyStore(theirs)
        DesktopSecretStore(dir, listOf(serviceVault(dir))).put("a", "1")
        assertTrue(theirs.contentEquals(legacyLoad()))
        assertFalse(theirs.contentEquals(serviceVault(dir).load()))
    }

    private val logLines = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var previousSink: app.nijiyome.model.LogSink? = null

    private fun captureLogs(): List<String> {
        previousSink = app.nijiyome.model.AppLog.sink
        app.nijiyome.model.AppLog.sink = app.nijiyome.model.LogSink { level, tag, message, _ -> logLines += "$level $tag $message" }
        return logLines
    }

    private fun restoreLogs() {
        previousSink?.let { app.nijiyome.model.AppLog.sink = it }
    }

    /** Hallazgo 3 de la revisión: a `secret-tool` that never answers (locked keyring, stuck bus) must not hang the caller. */
    @Test
    fun aSecretToolThatNeverAnswersTimesOut() {
        assumeLinux()
        val stuck = SecretServiceVault(command = listOf("bash", "-c", "sleep 30", "stand-in"), timeoutSeconds = 1, folderId = { "f" })
        val started = System.currentTimeMillis()
        assertFailsWith<KeyringUnavailable> { stuck.load() }
        val elapsed = System.currentTimeMillis() - started
        assertTrue(elapsed < 10_000, "gave up after $elapsed ms")
        // A grandchild that keeps the output open after the tool itself ended must not block either.
        val lingering = SecretServiceVault(command = listOf("bash", "-c", "(sleep 30 &) ; exit 1", "stand-in"), timeoutSeconds = 1, folderId = { "f" })
        val t0 = System.currentTimeMillis()
        assertNull(lingering.load())
        assertTrue(System.currentTimeMillis() - t0 < 10_000)
    }

    /**
     * 1.1.2: the app runs with libjsig preloaded; inherited by secret-tool it printed a JVM warning on stderr, so a missing item
     * (exit 1) read as a locked keyring, and a folder whose own item did not exist yet (every folder updating from 1.1.1) could
     * never open its secrets.
     */
    @Test
    fun aJvmWarningOnStderrIsNotAKeyringFailureAndLibjsigIsNotInherited() {
        assumeLinux()
        val warning = "OpenJDK VM warning: the use of signal() and sigset() for signal chaining was deprecated in version 16.0"
        val missing = SecretServiceVault(
            command = listOf("bash", "-c", "echo '$warning' >&2; exit 1", "stand-in"), timeoutSeconds = 5, folderId = { "f" },
        )
        assertNull(missing.load())
        val env = SecretServiceVault(
            command = listOf("bash", "-c", "[ -z \"${'$'}{LD_PRELOAD+x}\" ] && exit 1; echo \"${'$'}LD_PRELOAD\" >&2; exit 1", "stand-in"),
            env = mapOf("LD_PRELOAD" to "/opt/jdk/lib/libjsig.so /usr/lib/other.so"),
            timeoutSeconds = 5,
            folderId = { "f" },
        )
        // The stand-in reports what LD_PRELOAD it got as an error: only the other library is left.
        val e = assertFailsWith<KeyringUnavailable> { env.load() }
        // (ld.so also complains that the made-up library does not exist; the last line is what the stand-in printed)
        assertEquals("/usr/lib/other.so", e.message.orEmpty().lines().last())
        val only = mutableMapOf("LD_PRELOAD" to "/opt/jdk/lib/libjsig.so")
        SecretServiceVault.withoutJsig(only)
        assertFalse("LD_PRELOAD" in only)
    }

    @Test
    fun aSecretToolThatAnswersIsRead() {
        assumeLinux()
        val key = ByteArray(32) { (it * 7).toByte() }
        val b64 = java.util.Base64.getEncoder().encodeToString(key)
        val ok = SecretServiceVault(command = listOf("bash", "-c", "printf '%s\\n' '$b64'", "stand-in"), timeoutSeconds = 5, folderId = { "f" })
        assertTrue(key.contentEquals(ok.load()))
        val failing = SecretServiceVault(command = listOf("bash", "-c", "echo 'Cannot autolaunch D-Bus' >&2; exit 1", "stand-in"), timeoutSeconds = 5, folderId = { "f" })
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
        println("secret service test on the private bus ${bus.substringBefore(',')}")
        val vault = SecretServiceVault(folderId = { DataFolderId.of(dir) })
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
            assertEquals("T", DesktopSecretStore(dir, listOf(SecretServiceVault(folderId = { DataFolderId.of(dir) }))).get("list.anilist.token"))

            // 1.1.2 on the real daemon: a second folder does not touch the first one's item, and the 1.1.1 shared item migrates.
            val other = newDir()
            val otherVault = SecretServiceVault(folderId = { DataFolderId.of(other) })
            DesktopSecretStore(other, listOf(otherVault)).put("list.anilist.token", "OTHER")
            assertEquals("T", DesktopSecretStore(dir, listOf(vault)).get("list.anilist.token"))
            DesktopSecretStore(other, listOf(otherVault)).clear()
            assertEquals("T", DesktopSecretStore(dir, listOf(vault)).get("list.anilist.token"), "a clear of one folder keeps the other")

            val legacyDir = newDir()
            val legacyKey = ByteArray(32) { 42 }
            val p = ProcessBuilder(listOf("secret-tool", "store", "--label=legacy") + SecretServiceVault.LEGACY_ATTRIBUTES).start()
            p.outputStream.use { it.write(java.util.Base64.getEncoder().encode(legacyKey)) }
            assertEquals(0, p.waitFor())
            sealWith(legacyDir, legacyKey, mapOf("list.anilist.token" to "LEGACY"))
            File(legacyDir, DesktopSecretStore.BACKEND_FILE).writeText(SecretServiceVault.ID)
            val legacyVault = SecretServiceVault(folderId = { DataFolderId.of(legacyDir) })
            assertTrue(legacyKey.contentEquals(legacyVault.loadLegacy()), "a lookup of the old pair only finds the old item")
            assertEquals("LEGACY", DesktopSecretStore(legacyDir, listOf(legacyVault)).get("list.anilist.token"))
            assertTrue(legacyKey.contentEquals(legacyVault.loadLegacy()), "the shared item is kept")
            assertTrue(legacyKey.contentEquals(legacyVault.load()))
            // What 1.1.1 does on "Restablecer la app": a clear of the old pair, which must not reach any folder's own item.
            assertEquals(0, ProcessBuilder(listOf("secret-tool", "clear") + SecretServiceVault.LEGACY_ATTRIBUTES).start().waitFor())
            assertNull(legacyVault.loadLegacy())
            assertTrue(legacyKey.contentEquals(legacyVault.load()))
            assertEquals("T", DesktopSecretStore(dir, listOf(vault)).get("list.anilist.token"), "clearing the old pair kept the folders' items")
            legacyVault.delete()

            store.clear()
            assertNull(vault.load())
        }
    }

    private companion object {
        val FIXED_TIME: java.time.LocalDateTime = java.time.LocalDateTime.of(2026, 11, 24, 10, 15, 0)
    }
}
