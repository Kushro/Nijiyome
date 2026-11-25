// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.extensions

import app.nijiyome.model.ExtensionRepo
import app.nijiyome.model.ExtensionRepoEntry
import app.nijiyome.model.torrent.DebridAccount
import app.nijiyome.model.torrent.DebridService
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcesLogicTest {
    private val official = ExtensionRepo(
        url = "https://a.example/index.json",
        name = "Oficial",
        entries = listOf(
            ExtensionRepoEntry("toko", "Toko", "4.7.0", "Streams y torrents", "https://a.example/toko.kai", installed = true, updateAvailable = true),
            ExtensionRepoEntry("hikari", "Hikari", "2.1.0", "Solo torrents", "https://a.example/hikari.kai"),
        ),
        fetchedAtMs = 0L,
    )
    private val community = ExtensionRepo(
        url = "https://b.example/index.json",
        name = "Comunidad",
        entries = listOf(ExtensionRepoEntry("fumi", "Fumi", "0.9.1", "Novelas", "https://b.example/fumi.kai", installed = true)),
        fetchedAtMs = 0L,
    )

    @Test
    fun storeFiltersBySearchStatusAndRepository() {
        val repos = listOf(official, community)
        assertEquals(listOf("toko", "hikari", "fumi"), storeItems(repos, "", StoreFilter.ALL, null).map { it.entry.id })
        assertEquals(listOf("toko"), storeItems(repos, "", StoreFilter.UPDATES, null).map { it.entry.id })
        assertEquals(listOf("toko", "fumi"), storeItems(repos, "", StoreFilter.INSTALLED, null).map { it.entry.id })
        assertEquals(listOf("hikari"), storeItems(repos, "", StoreFilter.NEW, null).map { it.entry.id })
        assertEquals(listOf("fumi"), storeItems(repos, "", StoreFilter.ALL, community.url).map { it.entry.id })
        assertEquals(listOf("toko", "hikari"), storeItems(repos, "TORRENT", StoreFilter.ALL, null).map { it.entry.id })
    }

    @Test
    fun debridListsLinkedAccountsFirstThenTheRecommendedService() {
        val linked = listOf(DebridAccount(DebridService.REAL_DEBRID, "u", null, 0L))
        assertEquals(
            listOf(DebridService.REAL_DEBRID, DebridService.TORBOX, DebridService.ALLDEBRID, DebridService.PREMIUMIZE),
            debridOrder(linked),
        )
        assertEquals(DebridService.TORBOX, debridOrder(emptyList()).first())
    }
}
