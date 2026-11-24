// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.TorrentInfo
import app.nijiyome.model.WebInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Hito 5: without a torrent engine the player goes straight to the web sources; with one (Android, desktop with libtorrent) nothing changes. */
class PlayableSourcesTest {
    private fun row(label: String, kind: SourceKind) = RankedSource(
        candidate = SourceCandidate(
            extId = "io.github.toko", extName = "Toko", kind = kind, label = label, quality = Quality.P1080, reliability = 0.8f,
            torrent = if (kind == SourceKind.TORRENT) TorrentInfo("c".repeat(40)) else null,
            web = if (kind == SourceKind.WEB) WebInfo("https://cdn.example/$label.m3u8") else null,
        ),
        score = 0f,
    )

    private val ranked = listOf(row("SubsPlease", SourceKind.TORRENT), row("anizone", SourceKind.WEB), row("Judas", SourceKind.TORRENT), row("hd-1", SourceKind.WEB))

    @Test
    fun withAnEngineEveryCandidateStaysInItsOrder() {
        assertEquals(ranked, playableSources(ranked, skipTorrents = false))
    }

    @Test
    fun withoutAnEngineTorrentsAreLeftOut() {
        assertEquals(listOf("anizone", "hd-1"), playableSources(ranked, skipTorrents = true).map { it.candidate.label })
        assertEquals(emptyList(), playableSources(ranked.filter { it.candidate.kind == SourceKind.TORRENT }, skipTorrents = true))
    }
}
