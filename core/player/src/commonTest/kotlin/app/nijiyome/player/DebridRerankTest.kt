// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourcePrefs
import app.nijiyome.model.TorrentInfo
import app.nijiyome.model.WebInfo
import app.nijiyome.model.torrent.DebridRepository
import app.nijiyome.model.torrent.DebridService
import app.nijiyome.model.torrent.DebridStatus
import app.nijiyome.release.rank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 1.1 (F7): the debrid bonus used to be dead code (the list was ranked before the cache lookup). [markDebrid] + [rerankDebrid] put a
 * cached torrent where `score` says, move `recommended` and keep `picked`.
 */
class DebridRerankTest {
    private val prefs = SourcePrefs()

    private fun torrent(label: String, seeders: Int, hash: Char) = SourceCandidate(
        extId = "nyaa", extName = "Nyaa", kind = SourceKind.TORRENT, label = label, quality = Quality.P1080,
        torrent = TorrentInfo(infoHash = hash.toString().repeat(40), seeders = seeders),
    )

    private fun web(label: String) = SourceCandidate(
        extId = "web", extName = "Web", kind = SourceKind.WEB, label = label, quality = Quality.P1080, web = WebInfo(url = "https://x/$label.m3u8"),
    )

    /** What the extension repository hands out: ranked by `score` before any debrid lookup, first = recommended. */
    private fun ranked(vararg c: SourceCandidate): List<RankedSource> =
        rank(c.toList(), prefs) { true }.mapIndexed { i, (cand, score) -> RankedSource(cand, score, recommended = i == 0) }

    private class FakeDebrid(private val cached: Set<String>) : DebridRepository {
        override val accounts = MutableStateFlow(emptyList<app.nijiyome.model.torrent.DebridAccount>())
        override suspend fun link(service: DebridService, apiKey: String) = error("unused")
        override suspend fun unlink(service: DebridService) = Unit
        override suspend fun cached(infoHashes: List<String>) = infoHashes.filter { it in cached }.associateWith { setOf(DebridService.TORBOX) }
        override suspend fun markCached(candidates: List<SourceCandidate>) = candidates.map { c ->
            val h = c.torrent?.infoHash ?: return@map c
            c.copy(debrid = DebridStatus(DebridService.TORBOX, cached = h in cached))
        }
        override suspend fun files(service: DebridService, infoHash: String, magnet: String?) = error("unused")
    }

    @Test
    fun aCachedTorrentWithFewerSeedersMovesUpAndBecomesRecommended() = runBlocking {
        val popular = torrent("Popular", seeders = 300, hash = 'a')
        val cached = torrent("Cached", seeders = 25, hash = 'b')
        val list = ranked(popular, cached)
        assertEquals(listOf("Popular", "Cached"), list.map { it.candidate.label })
        val marked = markDebrid(list, FakeDebrid(setOf("b".repeat(40))))
        // the badge alone never changed the order before 1.1
        assertEquals(listOf("Popular", "Cached"), marked.map { it.candidate.label })
        val out = rerankDebrid(marked, prefs, prefer = true)
        assertEquals(listOf("Cached", "Popular"), out.map { it.candidate.label })
        assertEquals(listOf(true, false), out.map { it.recommended })
        assertTrue(out[0].score > list.first { it.candidate.label == "Cached" }.score)
    }

    @Test
    fun preferOffOnlyShowsTheBadge() = runBlocking {
        val list = ranked(torrent("Popular", 300, 'a'), torrent("Cached", 25, 'b'))
        val marked = markDebrid(list, FakeDebrid(setOf("b".repeat(40))))
        assertSame(marked, rerankDebrid(marked, prefs, prefer = false))
        assertEquals(true, marked[1].candidate.debrid?.cached)
    }

    @Test
    fun aCachedTorrentCountsAsWebForPreferWebAndThePickStays() = runBlocking {
        val webPrefs = prefs.copy(prefer = SourceKind.WEB)
        val list = rank(listOf(web("Alpha"), torrent("Cached", 300, 'b'), torrent("Other", 300, 'c')), webPrefs) { true }
            .mapIndexed { i, (c, s) -> RankedSource(c, s, recommended = i == 0, picked = c.label == "Other") }
        val marked = markDebrid(list, FakeDebrid(setOf("b".repeat(40))))
        val out = rerankDebrid(marked, webPrefs, prefer = true)
        // +20 (counts as web) +10 (bonus) without the seeder terms: above the plain torrent, next to the web source
        assertTrue(out.indexOfFirst { it.candidate.label == "Cached" } < out.indexOfFirst { it.candidate.label == "Other" })
        assertEquals(listOf("Other"), out.filter { it.picked }.map { it.candidate.label })
        assertEquals(1, out.count { it.recommended })
        assertTrue(out.first().recommended)
    }

    @Test
    fun knownStatusesArePutBackOnNewerLists() {
        val list = ranked(torrent("Popular", 300, 'a'), torrent("Cached", 25, 'b'))
        val known = mapOf(list[1].key to DebridStatus(DebridService.TORBOX, cached = true))
        val again = reapplyDebrid(list, known)
        assertEquals(true, again[1].candidate.debrid?.cached)
        assertEquals(listOf("Cached", "Popular"), rerankDebrid(again, prefs, prefer = true).map { it.candidate.label })
    }
}
