// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import app.nijiyome.feature.downloads.transfers.HistoryFilter
import app.nijiyome.feature.downloads.transfers.TransferFilter
import app.nijiyome.feature.downloads.transfers.TransferKind
import app.nijiyome.feature.downloads.transfers.TransferPhase
import app.nijiyome.feature.downloads.transfers.TransferSort
import app.nijiyome.feature.downloads.transfers.TransfersLogic
import app.nijiyome.model.Container
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadState
import app.nijiyome.model.MediaRef
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentSessionEntry
import app.nijiyome.model.torrent.TorrentState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Sparkles lane S8: the single queue of Transferencias over web, debrid and torrent downloads. */
class TransfersLogicTest {
    private fun web(id: Int, state: DownloadState, extId: String = "ext") = DownloadItem(
        id = "$id?ep=1", ref = MediaRef(anilistId = id), title = "Show $id", episode = 1, extId = extId, sourceLabel = "S",
        container = Container.HLS, state = state, createdAtMs = id.toLong(),
    )

    private fun torrent(id: String, state: TorrentState, progress: Double) =
        TorrentLibraryItem(id = id, name = id, state = state, progress = progress, sizeBytes = 100, addedUnix = 1)

    @Test
    fun queueMixesKindsByStateAndLeavesCompleteOnesOut() {
        val q = TransfersLogic.queue(
            web = listOf(web(1, DownloadState.FAILED), web(2, DownloadState.DOWNLOADING, extId = "torrent"), web(3, DownloadState.COMPLETED)),
            torrents = listOf(torrent("a", TorrentState.PAUSED, 0.5), torrent("b", TorrentState.SEEDING, 1.0), torrent("c", TorrentState.DOWNLOADING, 0.1)),
            chapters = emptyList(),
        )
        assertEquals(listOf("w:2?ep=1", "t:c", "t:a", "w:1?ep=1"), q.map { it.key })
        assertEquals(TransferKind.DEBRID, q.first().kind)
        assertEquals(listOf("t:b"), TransfersLogic.storedTorrents(listOf(torrent("b", TorrentState.SEEDING, 1.0))).map { it.key })
    }

    @Test
    fun filtersAndSortFollowThePhase() {
        val items = listOf(
            TransfersLogic.ofTorrent(torrent("a", TorrentState.SEEDING, 1.0)),
            TransfersLogic.ofTorrent(torrent("b", TorrentState.ERROR, 0.3)),
            TransfersLogic.ofWeb(web(1, DownloadState.QUEUED)),
        )
        assertEquals(listOf("t:a"), items.filter { TransfersLogic.matches(it, TransferFilter.SEEDING) }.map { it.key })
        assertEquals(listOf("t:b"), items.filter { TransfersLogic.matches(it, TransferFilter.ERROR) }.map { it.key })
        assertEquals(listOf("w:1?ep=1"), items.filter { TransfersLogic.matches(it, TransferFilter.PAUSED) }.map { it.key })
        assertEquals(TransferPhase.FAILED, items[1].phase)
        assertEquals(listOf("t:a", "t:b", "w:1?ep=1"), TransfersLogic.sort(items, TransferSort.PROGRESS, descending = true).map { it.key })
    }

    @Test
    fun historyMergesBothKindsNewestFirstAndFilters() {
        val w = web(1, DownloadState.COMPLETED).copy(completedAtMs = 5_000)
        val s = TorrentSessionEntry(id = "h", name = "Torrent", addedUnix = 10)
        assertEquals(listOf(true, false), TransfersLogic.history(listOf(w), listOf(s), HistoryFilter.ALL).map { it.torrent })
        assertEquals(1, TransfersLogic.history(listOf(w), listOf(s), HistoryFilter.WEB).size)
        assertEquals(1, TransfersLogic.history(listOf(w), listOf(s), HistoryFilter.TORRENTS).size)
    }
}
