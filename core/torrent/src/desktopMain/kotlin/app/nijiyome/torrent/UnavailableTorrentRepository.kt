// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.Media
import app.nijiyome.model.torrent.EpisodeAlignment
import app.nijiyome.model.torrent.TorrentEngineUnavailable
import app.nijiyome.model.torrent.TorrentEvent
import app.nijiyome.model.torrent.TorrentFile
import app.nijiyome.model.torrent.TorrentId
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentPeer
import app.nijiyome.model.torrent.TorrentPlayback
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentServiceStatus
import app.nijiyome.model.torrent.TorrentStats
import app.nijiyome.model.torrent.TorrentStorage
import app.nijiyome.model.torrent.TorrentTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop without libtorrent4j's native library ([LibtorrentNative] could not load it): empty library, idle status, every action
 * fails with `torrent:unavailable` (shown as the generic action error) and the player skips torrent candidates.
 */
internal class UnavailableTorrentRepository : TorrentRepository, TorrentEngineUnavailable {
    private val unavailable get() = AppException(AppError.Unknown("torrent:unavailable"))

    override val events: SharedFlow<TorrentEvent> = MutableSharedFlow<TorrentEvent>().asSharedFlow()
    override val library: StateFlow<List<TorrentLibraryItem>> = MutableStateFlow(emptyList<TorrentLibraryItem>()).asStateFlow()
    override val status: StateFlow<TorrentServiceStatus> = MutableStateFlow(TorrentServiceStatus()).asStateFlow()

    override suspend fun play(source: String, media: Media, episode: Int, fileIndex: Int?): Result<TorrentPlayback> = Result.failure(unavailable)
    override suspend fun playbackEnded(torrentId: TorrentId, completed: Boolean) = Unit
    override suspend fun addDownload(source: String, media: Media, episode: Int?): Result<TorrentId> = Result.failure(unavailable)
    override suspend fun addManual(source: String, media: Media?, alignment: EpisodeAlignment?, persist: Boolean): Result<TorrentId> =
        Result.failure(unavailable)
    override suspend fun setAlignment(id: TorrentId, media: Media?, alignment: EpisodeAlignment?): Result<Unit> = Result.failure(unavailable)
    override fun stats(id: TorrentId): Flow<TorrentStats> = flow { throw unavailable }
    override suspend fun files(id: TorrentId): List<TorrentFile> = emptyList()
    override fun peers(id: TorrentId): Flow<List<TorrentPeer>> = flowOf(emptyList())
    override suspend fun trackers(id: TorrentId): List<TorrentTracker> = emptyList()
    override suspend fun remove(id: TorrentId, deleteFiles: Boolean) = Unit
    override suspend fun pause(id: TorrentId) = Unit
    override suspend fun resume(id: TorrentId) = Unit
    override suspend fun setPersist(id: TorrentId, persist: Boolean) = Unit
    override suspend fun setSeeding(id: TorrentId, seed: Boolean) = Unit
    override suspend fun downloadFile(id: TorrentId, fileIndex: Int) = Unit
    override suspend fun seekMarks(id: TorrentId, fileIndex: Int): List<ClosedFloatingPointRange<Double>> = emptyList()
    override suspend fun exportToDownloads(id: TorrentId, fileIndex: Int): Result<String> = Result.failure(unavailable)
    override suspend fun storageUsage(): TorrentStorage = throw unavailable
}
