// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Module-private persistence (12 §4 c: "persistencia privada del módulo"): one SharedPreferences file, entries joined by newlines. */
class SharedPrefsSearchHistoryStore(context: Context) : SearchHistoryStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): List<String> =
        prefs.getString(KEY, null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(SEARCH_HISTORY_SIZE)
            .orEmpty()

    override fun save(entries: List<String>) {
        prefs.edit().putString(KEY, entries.take(SEARCH_HISTORY_SIZE).joinToString("\n")).apply()
    }

    private companion object {
        const val FILE = "nijiyome_search_history"
        const val KEY = "entries"
    }
}

/** ContentResolver-backed reader (SAF picker results and ACTION_SEND grants). */
class ContentImageReader(context: Context) : ImageBytesReader {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): PickedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)
            val mime = resolver.getType(parsed) ?: "image/jpeg"
            val bytes = resolver.openInputStream(parsed)?.use { it.readBytes() } ?: return@runCatching null
            PickedImage(bytes, mime)
        }.getOrNull()
    }
}

internal actual val imageDropAndLinks: Boolean = false
