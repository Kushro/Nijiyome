// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.AppSettings
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.FeedKind
import app.nijiyome.model.FeedPage
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.InstalledExtension
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.Episode
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.StreamSource
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerSessionFactory
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.model.Container
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Milestone 3: PlayerViewModel built the desktop way (no Media3 factory, no subtitle tools, no external player, no Link client):
 * whatever the engine setting asks for, the session comes from the libmpv factory instead of failing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelWithoutMedia3Test {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val args = PlayerArgs(media = MediaRef(anilistId = 1), title = "Title", episode = 1, localUri = "/videos/episode-1.mkv")

    private fun desktopViewModel(
        engine: PlayerEngine?,
        mpv: FakeMpvFactory,
        args: PlayerArgs = this.args,
        torrents: TorrentRepository? = null,
    ) = PlayerViewModel(
        args = args,
        metadata = NoMetadata,
        extensions = NoExtensions,
        history = NoHistory,
        link = NoLinkRepository,
        remote = NoRemotePlayback,
        media3 = null,
        flush = null,
        settingsRepository = engine?.let { FakeSettings(AppSettings(playerEngine = it)) },
        torrents = torrents,
        mpvFactory = mpv,
        subtitleTools = null,
        secrets = null,
        linkDecodeLimits = { null },
        externalPlayer = null,
    )

    @Test
    fun theDefaultEngineOpensALibmpvSession() {
        val mpv = FakeMpvFactory()
        val vm = desktopViewModel(engine = null, mpv = mpv)
        assertEquals(1, mpv.created.size)
        assertEquals(listOf("/videos/episode-1.mkv"), mpv.created.single().loaded)
        assertEquals(PlayerEngine.MPV, vm.state.value.engine)
        assertSame(mpv.created.single(), vm.state.value.session)
        assertEquals(PlayerPhase.BUFFERING, vm.state.value.phase)
    }

    @Test
    fun media3AndExternalSettingsPlayOnLibmpv() {
        for (engine in listOf(PlayerEngine.MEDIA3, PlayerEngine.EXTERNAL)) {
            val mpv = FakeMpvFactory()
            val vm = desktopViewModel(engine = engine, mpv = mpv)
            assertEquals(1, mpv.created.size, "$engine")
            assertEquals(PlayerEngine.MPV, vm.state.value.engine, "$engine")
            assertEquals(PlayerPhase.BUFFERING, vm.state.value.phase, "$engine")
        }
    }

    @Test
    fun aLooseFileOffersNoOtherEpisode() {
        val loose = PlayerArgs(media = MediaRef(), title = "clip", episode = 1, localUri = "/videos/clip.mkv", looseFile = true)
        val vm = desktopViewModel(engine = null, mpv = FakeMpvFactory(), args = loose)
        assertFalse(vm.state.value.hasNextEpisode)
        assertFalse(vm.state.value.canNavigateEpisodes)
        // a download of a known title still has its next episode
        val download = desktopViewModel(engine = null, mpv = FakeMpvFactory())
        assertTrue(download.state.value.hasNextEpisode)
        assertTrue(download.state.value.canNavigateEpisodes)
        // an unpaired download (Android opens it with an empty MediaRef) behaves as before: only the desktop flag makes a loose file
        val unpaired = desktopViewModel(
            engine = null,
            mpv = FakeMpvFactory(),
            args = PlayerArgs(media = MediaRef(), title = "Show", episode = 3, localUri = "/videos/show-3.mkv"),
        )
        assertTrue(unpaired.state.value.hasNextEpisode)
        assertTrue(unpaired.state.value.hasPrevEpisode)
        assertTrue(unpaired.state.value.canNavigateEpisodes)
    }

    @Test
    fun aTorrentAddedWithoutASeriesPlaysAsALooseFile() {
        // Archivos opens a hand-added torrent with an empty MediaRef and the torrent's name (playArgsFor): there is no metadata.
        val torrents = FakeTorrents()
        val mpv = FakeMpvFactory()
        val vm = desktopViewModel(
            engine = null,
            mpv = mpv,
            args = PlayerArgs(media = MediaRef(), title = "Some.Release.1080p", episode = 1, torrentId = "abc123", fileIndex = 0),
            torrents = torrents,
        )
        assertEquals(null, vm.state.value.error)
        assertEquals(PlayerPhase.BUFFERING, vm.state.value.phase)
        assertEquals(listOf("http://127.0.0.1:1/abc123/0/video.mkv"), mpv.created.single().loaded)
        val (source, media, fileIndex) = torrents.played.single()
        assertEquals("abc123", source)
        assertEquals("Some.Release.1080p", media.title)
        assertEquals(0, fileIndex)
        // no episodes around it
        assertTrue(vm.state.value.looseFile)
        assertFalse(vm.state.value.hasNextEpisode)
        assertFalse(vm.state.value.hasPrevEpisode)
        assertFalse(vm.state.value.canNavigateEpisodes)
        assertEquals(null, vm.episodeForFiles)
    }

    @Test
    fun aTorrentOfAKnownTitleStillNeedsItsMetadata() {
        // Unchanged: a torrent behind an AniList title plays through its Media; without it the player reports the failure.
        val torrents = FakeTorrents()
        val vm = desktopViewModel(
            engine = null,
            mpv = FakeMpvFactory(),
            args = PlayerArgs(media = MediaRef(anilistId = 7), title = "Show", episode = 2, torrentId = "abc123"),
            torrents = torrents,
        )
        assertEquals(PlayerPhase.FAILED, vm.state.value.phase)
        assertTrue(torrents.played.isEmpty())
        assertFalse(vm.state.value.looseFile)
    }

    @Test
    fun androidKeepsTheEngineItAskedFor() {
        for (engine in PlayerEngine.entries) {
            assertEquals(engine, availableEngine(engine, media3 = true, mpv = true, external = true))
        }
        assertEquals(PlayerEngine.MEDIA3, availableEngine(PlayerEngine.MEDIA3, media3 = true, mpv = false, external = true))
        assertEquals(PlayerEngine.MPV, availableEngine(PlayerEngine.EXTERNAL, media3 = false, mpv = true, external = false))
        assertEquals(PlayerEngine.MEDIA3, availableEngine(PlayerEngine.EXTERNAL, media3 = true, mpv = true, external = false))
    }

    private class FakeMpvFactory : PlayerSessionFactory {
        val created = mutableListOf<FakeSession>()

        override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean): PlayerSession =
            FakeSession().also { created += it }
    }

    private class FakeSession : PlayerSession {
        val loaded = mutableListOf<String>()
        private val _state = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = _state

        override fun load(source: PlayableSource, startMs: Long) {
            loaded += source.url
            _state.update { it.copy(loadId = it.loadId + 1) }
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun togglePlay() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun seekBy(deltaMs: Long) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun selectVideoHeight(height: Int?) = Unit
        override fun selectSubtitle(groupIndex: Int?) = Unit
        override fun selectAudio(id: Int) = Unit
        override fun setPreferredLanguages(subtitle: String?, audio: String?) = Unit
        override fun setSubtitleStyle(style: SubtitleStyle) = Unit
        override fun currentPositionMs(): Long = 0
        override fun release() = Unit
    }

    /** Only [play] answers (with a loopback stream URL); the rest is what a torrent with no stats looks like. */
    private class FakeTorrents : TorrentRepository {
        val played = mutableListOf<Triple<String, Media, Int?>>()
        override val events = MutableSharedFlow<TorrentEvent>()
        override val library = MutableStateFlow(emptyList<TorrentLibraryItem>())
        override val status = MutableStateFlow(TorrentServiceStatus())

        override suspend fun play(source: String, media: Media, episode: Int, fileIndex: Int?): Result<TorrentPlayback> {
            played += Triple(source, media, fileIndex)
            val index = fileIndex ?: 0
            val stream = StreamSource(
                url = "http://127.0.0.1:1/$source/$index/video.mkv", headers = emptyMap(), container = Container.PROGRESSIVE,
                quality = null, provider = "torrent", extensionId = "torrent",
            )
            return Result.success(
                TorrentPlayback(
                    args = PlayerArgs(media = media.ref, title = media.title, episode = episode, torrentId = source, fileIndex = index),
                    stream = stream, torrentId = source, fileIndex = index, fileName = "video.mkv", batch = false,
                ),
            )
        }

        override suspend fun playbackEnded(torrentId: TorrentId, completed: Boolean) = Unit
        override suspend fun addDownload(source: String, media: Media, episode: Int?): Result<TorrentId> = Result.failure(UnsupportedOperationException())
        override fun stats(id: TorrentId): Flow<TorrentStats> = emptyFlow()
        override suspend fun files(id: TorrentId): List<TorrentFile> = emptyList()
        override fun peers(id: TorrentId): Flow<List<TorrentPeer>> = emptyFlow()
        override suspend fun trackers(id: TorrentId): List<TorrentTracker> = emptyList()
        override suspend fun remove(id: TorrentId, deleteFiles: Boolean) = Unit
        override suspend fun pause(id: TorrentId) = Unit
        override suspend fun resume(id: TorrentId) = Unit
        override suspend fun setPersist(id: TorrentId, persist: Boolean) = Unit
        override suspend fun setSeeding(id: TorrentId, seed: Boolean) = Unit
        override suspend fun downloadFile(id: TorrentId, fileIndex: Int) = Unit
        override suspend fun seekMarks(id: TorrentId, fileIndex: Int): List<ClosedFloatingPointRange<Double>> = emptyList()
        override suspend fun exportToDownloads(id: TorrentId, fileIndex: Int): Result<String> = Result.failure(UnsupportedOperationException())
        override suspend fun storageUsage(): TorrentStorage = throw UnsupportedOperationException()
    }

    private class FakeSettings(initial: AppSettings) : SettingsRepository {
        override val settings = MutableStateFlow(initial)
        override suspend fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
    }

    private object NoMetadata : MetadataRepository {
        override suspend fun feed(kind: MediaKind, feed: FeedKind, page: Int): FeedPage = throw UnsupportedOperationException()
        override suspend fun search(kind: MediaKind, query: String, page: Int): FeedPage = throw UnsupportedOperationException()
        override suspend fun media(kind: MediaKind, ref: MediaRef): Media? = null
        override suspend fun episodes(ref: MediaRef): List<Episode> = emptyList()
    }

    private object NoExtensions : ExtensionRepository {
        override val installed = MutableStateFlow(emptyList<InstalledExtension>())
        override suspend fun installFromUri(uri: String): Result<InstalledExtension> = Result.failure(UnsupportedOperationException())
        override suspend fun setEnabled(id: String, enabled: Boolean) = Unit
        override suspend fun remove(id: String) = Unit
        override suspend fun resolveStreams(media: Media, episode: Int): List<StreamSource> = emptyList()
    }

    private object NoHistory : HistoryRepository {
        override fun continueWatching(limit: Int): Flow<List<HistoryEntry>> = emptyFlow()
        override suspend fun record(entry: HistoryEntry) = Unit
        override fun progressFor(anilistId: Int): Flow<Map<Int, HistoryEntry>> = emptyFlow()
    }
}
