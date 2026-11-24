// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensionserver

import app.nijiyome.link.AesGcmKeyCipher
import app.nijiyome.link.KeyCipher
import app.nijiyome.model.SecretStore
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import java.io.File
import java.net.InetAddress
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop: Koin bindings of the extension server client (Tatakai Next's desktop is a client too: Extensiones › Servidores). The
 * connections live in `<dataDir>/extension-servers.bin`, sealed with AES-256-GCM under a random key kept in the app's [SecretStore]
 * (the system keyring, or its 0600 fallback file), the desktop counterpart of Android's AndroidKeyStore key. Requires the shared
 * `OkHttpClient` and a [SecretStore] in the graph. There is no QR scanner on desktop: the link is pasted or opened.
 */
fun desktopExtensionServerModule(dataDir: File): Module = extensionServerModule(
    store = { ExtensionServerStore(File(dataDir, STORE_FILE), SecretStoreKeyCipher(get<SecretStore>())) },
    deviceLabel = { desktopDeviceLabel() },
)

/** `Nijiyome (<host name>)`, the name this PC announces when it pairs with another one's server. */
internal fun desktopDeviceLabel(host: String? = hostName()): String =
    "Nijiyome (${host?.trim()?.takeIf { it.isNotEmpty() } ?: "PC"})".take(100)

private fun hostName(): String? =
    System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
        ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
        ?: runCatching { File("/etc/hostname").readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()

/**
 * [KeyCipher] whose AES-256 key is a random secret in [secrets] under [SECRET_KEY], created the first time something is sealed. A
 * sealed file whose key is gone (the keyring was wiped, "Restablecer la app") does not open: it reads as "no servers" and is
 * replaced on the next write, never decrypted with another key.
 */
class SecretStoreKeyCipher(private val secrets: SecretStore) : KeyCipher {
    @Volatile private var cached: SecretKey? = null
    private val cipher = AesGcmKeyCipher { cached ?: error("no key") }

    override fun seal(plain: ByteArray): ByteArray {
        key(create = true)
        return cipher.seal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray? {
        if (key(create = false) == null) return null
        return cipher.open(sealed)
    }

    @Synchronized
    private fun key(create: Boolean): SecretKey? {
        cached?.let { return it }
        val stored = runBlocking { secrets.get(SECRET_KEY) }
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.size == KEY_BYTES }
        val raw = stored ?: if (create) {
            ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes).also { runBlocking { secrets.put(SECRET_KEY, Base64.getEncoder().encodeToString(it)) } }
        } else {
            return null
        }
        return SecretKeySpec(raw, "AES").also { cached = it }
    }

    companion object {
        const val SECRET_KEY = "extension_server.token_seal_key"
        private const val KEY_BYTES = 32
    }
}

private const val STORE_FILE = "extension-servers.bin"
