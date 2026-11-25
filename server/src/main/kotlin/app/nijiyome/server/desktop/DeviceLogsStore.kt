// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.model.DeviceLogSource
import app.nijiyome.model.RemoteLogsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop store for app logs shipped from paired mobile devices over Nijiyome Link (`POST /v1/logs`).
 * Keeps one rotating file per device under `<dataDir>/logs/devices/<deviceId>.log` (1 MiB cap + `.1.log`)
 * and a tiny `index.json` mapping `deviceId -> name, lastUpdatedMs`.
 */
class DeviceLogsStore(
    private val dir: File,
    private val maxBytes: Long = MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) : RemoteLogsRepository {
    private val lock = Any()

    @Serializable
    private data class DeviceMeta(val name: String, val lastUpdatedMs: Long)

    private val indexFile: File get() = File(dir, "index.json")

    /** Appends [lines] received from [deviceId] ([deviceName]). Deduplicates lines already in the tail. */
    fun append(deviceId: String, deviceName: String, lines: List<String>, appVersion: String? = null, platform: String? = null): Int {
        if (lines.isEmpty()) return 0
        val safeId = sanitizeId(deviceId)
        val cleanName = deviceName.trim().ifBlank { safeId }.take(64)
        val scrubbed = lines.asSequence()
            .map { scrub(it).replace('\r', ' ').replace('\n', ' ').trim() }
            .filter { it.isNotEmpty() }
            .map { it.take(MAX_LINE_CHARS) }
            .toList()
        if (scrubbed.isEmpty()) return 0

        synchronized(lock) {
            dir.mkdirs()
            val file = File(dir, "$safeId.log")
            val rotated = File(dir, "$safeId.1.log")
            val existingTail = if (file.isFile) runCatching { file.readLines(Charsets.UTF_8).takeLast(200).toHashSet() }.getOrDefault(emptySet()) else emptySet()
            val fresh = scrubbed.filter { it !in existingTail }
            if (fresh.isEmpty()) {
                updateIndex(safeId, cleanName)
                return 0
            }
            val headerNote = if (!file.isFile && (!appVersion.isNullOrBlank() || !platform.isNullOrBlank())) {
                val info = listOfNotNull(platform?.takeIf { it.isNotBlank() }, appVersion?.takeIf { it.isNotBlank() }?.let { "v$it" }).joinToString(" ")
                "# device=$cleanName ($info)\n"
            } else ""
            val payload = headerNote + fresh.joinToString("\n") + "\n"
            if (file.isFile && file.length() + payload.length > maxBytes) {
                rotated.delete()
                file.renameTo(rotated)
            }
            file.appendText(payload, Charsets.UTF_8)
            updateIndex(safeId, cleanName)
            return fresh.size
        }
    }

    override suspend fun listDevices(): List<DeviceLogSource> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val meta = readIndex()
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") && !f.name.endsWith(".1.log") }.orEmpty()
            files.map { f ->
                val id = f.name.removeSuffix(".log")
                val m = meta[id]
                DeviceLogSource(
                    id = id,
                    name = m?.name ?: id,
                    lastUpdatedMs = m?.lastUpdatedMs ?: f.lastModified().takeIf { it > 0L },
                )
            }.sortedByDescending { it.lastUpdatedMs ?: 0L }
        }
    }

    override suspend fun recentLines(deviceId: String?, max: Int): List<String> = withContext(Dispatchers.IO) {
        val cap = max.coerceAtLeast(0)
        if (cap == 0) return@withContext emptyList()
        synchronized(lock) {
            if (deviceId != null) {
                readDeviceLines(sanitizeId(deviceId)).takeLast(cap)
            } else {
                val meta = readIndex()
                val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") && !f.name.endsWith(".1.log") }.orEmpty()
                val tagged = files.flatMap { f ->
                    val id = f.name.removeSuffix(".log")
                    val label = meta[id]?.name ?: id
                    readDeviceLines(id).takeLast(cap).map { line ->
                        // Prefix device tag after timestamp if line starts with ISO-8601, so chronological sort works cleanly.
                        val tsEnd = line.indexOf(' ')
                        val sortKey = if (tsEnd in 18..32) line.substring(0, tsEnd) else ""
                        sortKey to "[$label] $line"
                    }
                }
                tagged.sortedBy { it.first }.takeLast(cap).map { it.second }
            }
        }
    }

    override fun logFile(deviceId: String): File? {
        val f = File(dir, "${sanitizeId(deviceId)}.log")
        return f.takeIf { it.isFile && it.length() > 0 }
    }

    override fun logsDir(): File? = dir.takeIf { it.isDirectory } ?: dir.parentFile?.takeIf { it.isDirectory }

    override suspend fun clear(deviceId: String?) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (deviceId != null) {
                val safeId = sanitizeId(deviceId)
                File(dir, "$safeId.log").delete()
                File(dir, "$safeId.1.log").delete()
                val meta = readIndex().toMutableMap()
                meta.remove(safeId)
                writeIndex(meta)
            } else {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
        Unit
    }

    private fun readDeviceLines(safeId: String): List<String> {
        val rotated = File(dir, "$safeId.1.log")
        val current = File(dir, "$safeId.log")
        return buildList {
            if (rotated.isFile) addAll(runCatching { rotated.readLines(Charsets.UTF_8) }.getOrDefault(emptyList()))
            if (current.isFile) addAll(runCatching { current.readLines(Charsets.UTF_8) }.getOrDefault(emptyList()))
        }.filter { it.isNotBlank() && !it.startsWith("#") }
    }

    private fun readIndex(): Map<String, DeviceMeta> = runCatching {
        if (!indexFile.isFile) return emptyMap()
        JSON.decodeFromString(MapSerializer(String.serializer(), DeviceMeta.serializer()), indexFile.readText(Charsets.UTF_8))
    }.getOrDefault(emptyMap())

    private fun updateIndex(safeId: String, name: String) {
        val map = readIndex().toMutableMap()
        map[safeId] = DeviceMeta(name = name, lastUpdatedMs = clock())
        writeIndex(map)
    }

    private fun writeIndex(map: Map<String, DeviceMeta>) {
        runCatching {
            dir.mkdirs()
            val tmp = File(indexFile.path + ".tmp")
            tmp.writeText(JSON.encodeToString(MapSerializer(String.serializer(), DeviceMeta.serializer()), map), Charsets.UTF_8)
            if (!tmp.renameTo(indexFile)) {
                indexFile.delete()
                tmp.renameTo(indexFile)
            }
        }
    }

    companion object {
        const val MAX_BYTES = 1024L * 1024
        const val MAX_LINE_CHARS = 4096
        private val JSON = Json { ignoreUnknownKeys = true; prettyPrint = true }
        private val SAFE = Regex("[^A-Za-z0-9._-]")
        private val QUERY = Regex("""(https?://[^\s?#]+)\?[^\s]*""")
        private val BEARER = Regex("""(?i)(bearer\s+)[A-Za-z0-9._\-~+/=]+""")
        private val SECRET_PARAM = Regex("""(?i)\b(access_token|refresh_token|token|apikey|api_key|code|code_verifier|client_secret)=([^\s&]+)""")

        fun sanitizeId(id: String): String = id.replace(SAFE, "_").take(64).ifBlank { "device" }

        fun scrub(text: String): String = text
            .replace(QUERY) { it.groupValues[1] + "?…" }
            .replace(BEARER) { it.groupValues[1] + "…" }
            .replace(SECRET_PARAM) { it.groupValues[1] + "=…" }
    }
}
