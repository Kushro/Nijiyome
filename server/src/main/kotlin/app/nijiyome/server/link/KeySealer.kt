// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.link

import app.nijiyome.model.SecretStore
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * How the private keys of the Link identity (CA and server) are stored at rest, like `protect.rs` in tk-link.
 * [open] reads what [seal] wrote and also a key written before sealing existed (plain PKCS#8), which the caller then
 * rewrites sealed.
 */
interface KeySealer {
    fun seal(plain: ByteArray): ByteArray

    fun open(stored: ByteArray): ByteArray

    /** True when [stored] was written by [seal] (false = a plain legacy key to rewrite). */
    fun isSealed(stored: ByteArray): Boolean

    /** No protection beyond the file permissions (0600); tests and a missing secret store. */
    object Plain : KeySealer {
        override fun seal(plain: ByteArray): ByteArray = plain

        override fun open(stored: ByteArray): ByteArray = stored

        override fun isSealed(stored: ByteArray): Boolean = true
    }
}

/**
 * AES-256-GCM with a random key kept in the app's [SecretStore] (the system keychain on desktop, or its 0600 fallback
 * file when there is none). File layout: `NJLK1` magic, 12-byte IV, ciphertext + tag. A sealed file whose key is gone
 * (the keychain was wiped or is locked) cannot be opened: the server reports the identity error instead of silently
 * minting a new CA, which would break every pairing.
 */
class SecretStoreSealer(private val secrets: SecretStore) : KeySealer {
    private val random = SecureRandom()

    override fun seal(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key(create = true), GCMParameterSpec(TAG_BITS, iv)) }
        return MAGIC + iv + cipher.doFinal(plain)
    }

    override fun open(stored: ByteArray): ByteArray {
        if (!isSealed(stored)) return stored
        val iv = stored.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val body = stored.copyOfRange(MAGIC.size + IV_BYTES, stored.size)
        val key = key(create = false) ?: throw IdentityException("the key that seals the Link identity is not in the secret store")
        return Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }.doFinal(body)
    }

    override fun isSealed(stored: ByteArray): Boolean =
        stored.size > MAGIC.size + IV_BYTES && stored.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun key(create: Boolean): SecretKeySpec? = runBlocking {
        val existing = secrets.get(SECRET_KEY)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }?.takeIf { it.size == KEY_BYTES }
        val raw = existing ?: if (create) {
            ByteArray(KEY_BYTES).also(random::nextBytes).also { secrets.put(SECRET_KEY, Base64.getEncoder().encodeToString(it)) }
        } else {
            null
        }
        raw?.let { SecretKeySpec(it, "AES") }
    }

    companion object {
        const val SECRET_KEY = "link.identity.sealing_key"
        private val MAGIC = "NJLK1".toByteArray(Charsets.US_ASCII)
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BYTES = 32
    }
}
