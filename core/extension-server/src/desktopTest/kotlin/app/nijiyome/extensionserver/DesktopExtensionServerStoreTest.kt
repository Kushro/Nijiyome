// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensionserver

import app.nijiyome.model.SecretStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopExtensionServerStoreTest {
    private class MemorySecrets : SecretStore {
        val map = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override suspend fun clear() = map.clear()
    }

    private val dir: File = Files.createTempDirectory("ext-store").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val conn = ExtensionServerConnection("c1", "srv-1", "http://192.168.1.20:8099", "Sala", "secret-token-value")

    @Test
    fun theTokenIsSealedWithAKeyThatLivesInTheSecretStore() {
        val secrets = MemorySecrets()
        val file = File(dir, "extension-servers.bin")
        ExtensionServerStore(file, SecretStoreKeyCipher(secrets)).write(listOf(conn))
        val raw = file.readBytes().decodeToString()
        assertFalse("secret-token-value" in raw, "the token is not on disk in clear")
        assertFalse("192.168.1.20" in raw, "neither is the server")
        assertNotNull(secrets.map[SecretStoreKeyCipher.SECRET_KEY])
        // another run (a new cipher over the same secret store) opens it
        assertEquals(listOf(conn), ExtensionServerStore(file, SecretStoreKeyCipher(secrets)).read())
    }

    @Test
    fun withoutItsKeyTheFileReadsAsNoServersAndNoKeyIsMintedByReading() {
        val secrets = MemorySecrets()
        val file = File(dir, "extension-servers.bin")
        ExtensionServerStore(file, SecretStoreKeyCipher(secrets)).write(listOf(conn))
        secrets.map.clear()
        assertEquals(emptyList(), ExtensionServerStore(file, SecretStoreKeyCipher(secrets)).read())
        assertNull(secrets.map[SecretStoreKeyCipher.SECRET_KEY])
        // a different key never opens it either
        val other = MemorySecrets()
        ExtensionServerStore(File(dir, "other.bin"), SecretStoreKeyCipher(other)).write(emptyList())
        assertEquals(emptyList(), ExtensionServerStore(file, SecretStoreKeyCipher(other)).read())
    }

    @Test
    fun theDeviceLabelNamesThisPc() {
        assertEquals("Nijiyome (sala-pc)", desktopDeviceLabel("sala-pc"))
        assertEquals("Nijiyome (PC)", desktopDeviceLabel("  "))
        assertEquals("Nijiyome (PC)", desktopDeviceLabel(null))
        assertTrue(desktopDeviceLabel("x".repeat(300)).length <= 100)
    }
}
