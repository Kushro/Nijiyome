// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.link

import app.nijiyome.model.SecretStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeySealerTest {
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() = dirs.forEach { it.deleteRecursively() }

    private fun tmp(): File = Files.createTempDirectory("nijiyome-sealer").toFile().also { dirs += it }

    private class MemorySecrets : SecretStore {
        val map = mutableMapOf<String, String>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override suspend fun clear() = map.clear()
    }

    /** A PKCS#8 EC key starts with an ASN.1 SEQUENCE (0x30); a sealed file starts with the magic. */
    private fun looksPlain(file: File) = file.readBytes().first() == 0x30.toByte()

    @Test
    fun keysAreSealedAtRestAndReloadWithTheSameIdentity() {
        val dir = tmp()
        val secrets = MemorySecrets()
        val a = Identity.loadOrInit(dir, emptyList(), sealer = SecretStoreSealer(secrets))
        assertNotNull(secrets.map[SecretStoreSealer.SECRET_KEY])
        assertFalse(looksPlain(File(dir, "ca.key")))
        assertFalse(looksPlain(File(dir, "server.key")))
        val b = Identity.loadOrInit(dir, emptyList(), sealer = SecretStoreSealer(secrets))
        assertEquals(a.caFingerprint, b.caFingerprint)
        assertContentEquals(a.serverKey.encoded, b.serverKey.encoded)
    }

    @Test
    fun plainKeysFromBeforeSealingAreRewrittenSealed() {
        val dir = tmp()
        val a = Identity.loadOrInit(dir, emptyList())
        assertTrue(looksPlain(File(dir, "ca.key")))
        val secrets = MemorySecrets()
        val b = Identity.loadOrInit(dir, emptyList(), sealer = SecretStoreSealer(secrets))
        assertEquals(a.caFingerprint, b.caFingerprint)
        assertFalse(looksPlain(File(dir, "ca.key")))
        assertFalse(looksPlain(File(dir, "server.key")))
        assertEquals(a.caFingerprint, Identity.loadOrInit(dir, emptyList(), sealer = SecretStoreSealer(secrets)).caFingerprint)
    }

    @Test
    fun aSealedIdentityWithoutItsKeyIsAnErrorNotANewCa() {
        val dir = tmp()
        val secrets = MemorySecrets()
        val a = Identity.loadOrInit(dir, emptyList(), sealer = SecretStoreSealer(secrets))
        val caDer = File(dir, "ca.der").readBytes()
        secrets.map.clear()
        assertFailsWith<IdentityException> { Identity.loadOrInit(dir, emptyList(), sealer = SecretStoreSealer(secrets)) }
        // nothing was replaced: with the key back, the same CA loads
        assertContentEquals(caDer, File(dir, "ca.der").readBytes())
        assertTrue(a.caFingerprint.isNotEmpty())
    }
}
