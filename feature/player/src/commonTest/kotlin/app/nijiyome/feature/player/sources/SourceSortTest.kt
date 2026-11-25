// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sources

import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourcePrefs
import app.nijiyome.model.TorrentInfo
import app.nijiyome.model.WebInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceSortTest {
    private fun src(label: String, kind: SourceKind, quality: Quality, seeders: Int? = null, size: Long? = null, picked: Boolean = false, rel: Float = 0.8f) =
        RankedSource(
            candidate = SourceCandidate(
                extId = "ext",
                extName = "Ext",
                kind = kind,
                label = label,
                quality = quality,
                reliability = rel,
                web = if (kind == SourceKind.WEB) WebInfo("https://cdn.example/$label") else null,
                torrent = if (kind == SourceKind.TORRENT) TorrentInfo("b".repeat(40), sizeBytes = size, seeders = seeders) else null,
            ),
            score = 0f,
            picked = picked,
        )

    private val list = listOf(
        src("a", SourceKind.TORRENT, Quality.P720, seeders = 10, size = 300),
        src("b", SourceKind.WEB, Quality.P1080),
        src("c", SourceKind.TORRENT, Quality.P1080, seeders = 50, size = 200, picked = true),
    )

    @Test
    fun recommendedKeepsRankingWithThePickFirst() {
        assertEquals(listOf("c", "a", "b"), sortSources(list, SourceSort.RECOMMENDED).map { it.candidate.label })
    }

    @Test
    fun qualitySeedersAndSizeSortStably() {
        assertEquals(listOf("b", "c", "a"), sortSources(list, SourceSort.QUALITY).map { it.candidate.label })
        assertEquals(listOf("c", "a", "b"), sortSources(list, SourceSort.SEEDERS).map { it.candidate.label })
        assertEquals(listOf("c", "a", "b"), sortSources(list, SourceSort.SIZE).map { it.candidate.label })
    }

    @Test
    fun groupsByKindInRankingOrder() {
        assertEquals(listOf(SourceKind.TORRENT, SourceKind.WEB), groupByKind(list).map { it.first })
    }

    @Test
    fun bestValuesAndRuleMisses() {
        val best = bestValues(list)
        assertEquals(Quality.P1080.rank().toDouble(), best[CompareColumn.RESOLUTION])
        assertEquals(200.0, best[CompareColumn.SIZE])
        assertEquals(listOf(RuleMiss.BELOW_QUALITY), ruleMisses(list[0], SourcePrefs(minQuality = Quality.P1080, subLang = "")))
    }
}
