// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import app.nijiyome.ui.platform.JsonPrefsFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSearchHistoryStoreTest {
    private val file = Files.createTempDirectory("search").toFile().apply { deleteOnExit() }.resolve("search.json")

    @Test
    fun historySurvivesARestart() {
        assertEquals(emptyList(), FileSearchHistoryStore(JsonPrefsFile(file)).load())
        val entries = listOf("frieren", "one piece", "\"quoted\" ñ").let { e -> e.fold(emptyList<String>()) { acc, q -> acc.withSearch(q) } }
        FileSearchHistoryStore(JsonPrefsFile(file)).save(entries)
        assertEquals(listOf("\"quoted\" ñ", "one piece", "frieren"), FileSearchHistoryStore(JsonPrefsFile(file)).load())
        // "Borrar búsquedas recientes"
        FileSearchHistoryStore(JsonPrefsFile(file)).save(emptyList())
        assertEquals(emptyList(), FileSearchHistoryStore(JsonPrefsFile(file)).load())
    }
}
