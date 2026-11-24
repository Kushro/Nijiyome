// SPDX-License-Identifier: AGPL-3.0-or-later
// Model adapted from Enigmata (app.enigmata.temple.vault.KeystoreTempleKeyCipher), same author, AGPL-3.0-or-later.
package app.nijiyome.link

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The non-exportable AES key (alias [ALIAS]) living in AndroidKeyStore; created on first use. */
object AndroidKeystoreKey {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val ALIAS = "nijiyome_link_client_key_seal"

    fun cipher(): KeyCipher = AesGcmKeyCipher(::secretKey)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
