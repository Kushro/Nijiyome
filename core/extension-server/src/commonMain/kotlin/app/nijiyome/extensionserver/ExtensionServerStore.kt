// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensionserver

import app.nijiyome.link.KeyCipher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * The linked servers of this phone, tokens included, sealed at rest with [cipher] (AES-256-GCM under an AndroidKeyStore key in
 * production, see `ExtensionServerKey` in androidMain) in one file under `noBackupFilesDir`. A missing, damaged or foreign file reads as "no servers".
 * Writes go through a temporary file + rename so a crash never leaves half a file.
 */
class ExtensionServerStore(
    private val file: File,
    private val cipher: KeyCipher,
    private val json: Json = ExtensionServerClient.DefaultJson,
) {
    @Serializable
    private data class Stored(val version: Int = VERSION, val servers: List<ExtensionServerConnection> = emptyList())

    @Synchronized
    fun read(): List<ExtensionServerConnection> {
        if (!file.isFile) return emptyList()
        return try {
            val plain = cipher.open(file.readBytes()) ?: return emptyList()
            json.decodeFromString(Stored.serializer(), plain.decodeToString()).servers
                .filter { HostGuard.isAllowed(it.url) && it.token.isNotEmpty() && it.id.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun write(servers: List<ExtensionServerConnection>) {
        file.parentFile?.mkdirs()
        val sealed = cipher.seal(json.encodeToString(Stored.serializer(), Stored(servers = servers)).toByteArray())
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(sealed)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot store extension servers")
        }
    }

    private companion object {
        const val VERSION = 1
    }
}
