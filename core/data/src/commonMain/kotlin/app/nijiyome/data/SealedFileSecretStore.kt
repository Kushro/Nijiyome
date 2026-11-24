// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.link.KeyCipher
import app.nijiyome.model.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * [SecretStore] sealed with an AES-GCM [cipher]: one JSON map in `secrets.bin` under [dir] (Android: `noBackupFilesDir`
 * with an AndroidKeyStore key, `KeystoreSecretStore`; desktop: the data directory with a key kept in the system keyring,
 * `DesktopSecretStore`), the `LinkKeyVault` / `ExtensionServerStore` pattern (random IV per write, atomic rename). Holds the AniList /
 * MAL token sets, debrid API keys and translation API keys (`SecretKeys`). A file that does not authenticate (key lost after a restore
 * to another device) reads as empty. [cipher] is injectable so the JVM tests use a plain AES key.
 */
open class SealedFileSecretStore(
    private val dir: File,
    private val cipher: KeyCipher,
) : SecretStore {
    private val mutex = Mutex()
    private var cache: Map<String, String>? = null
    private val file: File get() = File(dir, FILE)

    override suspend fun get(key: String): String? = mutex.withLock { load()[key] }

    override suspend fun put(key: String, value: String?) = mutex.withLock {
        val current = load()
        val next = if (value == null) current - key else current + (key to value)
        if (next != current) save(next)
    }

    override suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            file.delete()
            File(dir, "$FILE.tmp").delete()
        }
        cache = emptyMap()
    }

    private suspend fun load(): Map<String, String> {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            try {
                if (!file.isFile) return@withContext emptyMap()
                val plain = cipher.open(file.readBytes()) ?: return@withContext emptyMap()
                json.decodeFromString(MAP, plain.decodeToString())
            } catch (_: Exception) {
                emptyMap()
            }
        }
        cache = loaded
        return loaded
    }

    private suspend fun save(map: Map<String, String>) {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val sealed = cipher.seal(json.encodeToString(MAP, map).toByteArray(Charsets.UTF_8))
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeBytes(sealed)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        cache = map
    }

    private companion object {
        const val FILE = "secrets.bin"
        val MAP = MapSerializer(String.serializer(), String.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
