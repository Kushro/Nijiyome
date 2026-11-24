// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import app.nijiyome.ui.platform.JsonPrefsFile
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.module.Module

/**
 * Desktop: Search and image search. Needs metadata, extensions and settings; TrendingFeed is optional. The recent searches are kept
 * in `<dataDir>/search.json`.
 */
fun desktopSearchModule(dataDir: File): Module = searchModule(
    history = { FileSearchHistoryStore(JsonPrefsFile(File(dataDir, "search.json"))) },
    reader = { FileImageReader(http = getOrNull()) },
)

/** Recent searches in a JSON file (Android: SharedPreferences), newest first. */
internal class FileSearchHistoryStore(private val file: JsonPrefsFile) : SearchHistoryStore {
    override fun load(): List<String> =
        (file.read()[KEY] as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.take(SEARCH_HISTORY_SIZE).orEmpty()

    override fun save(entries: List<String>) {
        file.put(KEY, JsonArray(entries.map(::JsonPrimitive)))
    }

    private companion object {
        const val KEY = "recent"
    }
}

/**
 * Reads the picked or dropped image from a local path (or a `file://` URI), or downloads a pasted http(s) link with [http]. Never
 * more than [MAX_IMAGE_BYTES] + 1 bytes are read (a bigger image reads as too large, the file is not loaded whole); only data that
 * starts like an image counts, and a download must also say it is one (`Content-Type: image/…`), so nothing else is sent to
 * trace.moe. Null when it cannot be read or is not an image.
 */
internal class FileImageReader(private val http: OkHttpClient? = null) : ImageBytesReader {
    override suspend fun read(uri: String): PickedImage? = withContext(Dispatchers.IO) {
        runCatching {
            if (imageLink(uri) != null) return@runCatching download(uri)
            val file = if (uri.startsWith("file:")) File(URI(uri)) else File(uri)
            if (!file.isFile) return@runCatching null
            val bytes = file.inputStream().use { it.readNBytes(MAX_IMAGE_BYTES + 1) }
            imageMimeType(bytes)?.let { PickedImage(bytes, it) }
        }.getOrNull()
    }

    private fun download(url: String): PickedImage? {
        val client = http ?: return null
        client.newCall(Request.Builder().url(url).header("Accept", "image/*").build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body
            val declared = body.contentType()?.let { "${it.type}/${it.subtype}" }?.lowercase()
            if (declared == null || !declared.startsWith("image/")) return null
            val bytes = body.byteStream().use { it.readNBytes(MAX_IMAGE_BYTES + 1) }
            val sniffed = imageMimeType(bytes) ?: return null
            return PickedImage(bytes, sniffed)
        }
    }
}

/** trace.moe's upload limit (the screen's `MAX_BYTES`): one byte more is enough to say "too large". */
internal const val MAX_IMAGE_BYTES = 25 * 1024 * 1024

/** The image type its first bytes show (PNG, JPEG, GIF, WebP, BMP, AVIF), or null when they are not an image's. */
internal fun imageMimeType(bytes: ByteArray): String? {
    fun at(offset: Int, text: String) =
        bytes.size >= offset + text.length && text.indices.all { bytes[offset + it] == text[it].code.toByte() }
    return when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && at(1, "PNG\r\n") -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        at(0, "GIF87a") || at(0, "GIF89a") -> "image/gif"
        at(0, "RIFF") && at(8, "WEBP") -> "image/webp"
        at(0, "BM") && bytes.size >= 14 -> "image/bmp"
        at(4, "ftypavif") || at(4, "ftypavis") -> "image/avif"
        else -> null
    }
}
