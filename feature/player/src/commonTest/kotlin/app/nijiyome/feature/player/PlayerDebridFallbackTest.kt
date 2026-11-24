// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.AppSettings
import app.nijiyome.model.Container
import app.nijiyome.model.Episode
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
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.StreamSource
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.model.TorrentInfo
import app.nijiyome.model.torrent.DebridAccount
import app.nijiyome.model.torrent.DebridFileRef
import app.nijiyome.model.torrent.DebridProblem
import app.nijiyome.model.torrent.DebridRepository
import app.nijiyome.model.torrent.DebridResolved
import app.nijiyome.model.torrent.DebridService
import app.nijiyome.model.torrent.DebridSettings
import app.nijiyome.model.torrent.DebridStatus
import app.nijiyome.model.torrent.TorrentEvent
import app.nijiyome.model.torrent.TorrentFile
import app.nijiyome.model.torrent.TorrentId
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentPeer
import app.nijiyome.model.torrent.TorrentPlayback
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentServiceStatus
import app.nijiyome.model.torrent.TorrentSettings
import app.nijiyome.model.torrent.TorrentStats
import app.nijiyome.model.torrent.TorrentStorage
import app.nijiyome.model.torrent.TorrentTracker
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerSessionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 1.1 (F7): a torrent cached on TorBox is the recommended source once the cache is known (the bonus used to be lost), it plays over
 * HTTPS, and when TorBox refuses it the player goes to libtorrent ONCE with a visible notice that names the reason.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDebridFallbackTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val cachedHash = "b".repeat(40)
    private val popularHash = "a".repeat(40)
    private val media = Media(
        ref = MediaRef(anilistId = 7), kind = MediaKind.ANIME, title = "Show", titles = mapOf("romaji" to "Show"), cover = null, banner = null,
        color = null, synopsis = null, genres = emptyList(), episodes = 12, status = null, season = null, year = null, score = null,
        sourceProvider = "anilist",
    )

    private fun torrent(label: String, hash: String, seeders: Int) = SourceCandidate(
        extId = "nyaa", extName = "Nyaa", kind = SourceKind.TORRENT, label = label, quality = Quality.P1080,
        torrent = TorrentInfo(infoHash = hash, seeders = seeders, magnet = "magnet:?xt=urn:btih:$hash"),
    )

    /** Already ranked by the repository (no debrid known yet): the well-seeded one first. */
    private val ranked = listOf(
        RankedSource(torrent("Popular", popularHash, 300), score = 66f, recommended = true),
        RankedSource(torrent("Cached", cachedHash, 25), score = 60.5f),
    )

    private fun viewModel(
        debrid: FakeDebrid,
        torrents: FakeTorrents,
        mpv: FakeMpv,
        debridSettings: DebridSettings = DebridSettings(),
        extensions: ExtensionRepository = Sources(ranked),
    ) =
        PlayerViewModel(
            args = PlayerArgs(media = media.ref, title = media.title, episode = 3),
            metadata = OneMedia(media),
            extensions = extensions,
            history = NoHistory,
            link = NoLinkRepository,
            remote = NoRemotePlayback,
            media3 = null,
            flush = null,
            settingsRepository = FakeSettings(AppSettings(torrent = TorrentSettings(debrid = debridSettings))),
            torrents = torrents,
            mpvFactory = mpv,
            debrid = debrid,
            linkDecodeLimits = { null },
        )

    @Test
    fun cachedTorrentIsRecommendedAndPlaysOverHttps() {
        val debrid = FakeDebrid(fail = null)
        val torrents = FakeTorrents()
        val mpv = FakeMpv()
        val vm = viewModel(debrid, torrents, mpv)
        assertEquals("Cached", vm.state.value.sources.first().candidate.label)
        assertTrue(vm.state.value.sources.first().recommended)
        assertEquals(listOf(cachedHash), debrid.resolved)
        assertEquals(listOf("https://cdn.example/dl/1/2"), mpv.loaded)
        assertTrue(torrents.played.isEmpty())
        // titles go along to pick the episode of a pack
        assertEquals(listOf("Show"), debrid.titles.single())
    }

    @Test
    fun preferOffKeepsTheOrderAndOnlyMarks() {
        val debrid = FakeDebrid(fail = null)
        val vm = viewModel(debrid, FakeTorrents(), FakeMpv(), DebridSettings(preferCached = false))
        assertEquals(listOf("Popular", "Cached"), vm.state.value.sources.map { it.candidate.label })
        assertEquals(true, vm.state.value.sources[1].candidate.debrid?.cached)
    }

    @Test
    fun torboxRefusesItOnceThenP2pWithTheReason() = runBlocking {
        val debrid = FakeDebrid(fail = AppException(AppError.Unknown("debrid:torbox:ACTIVE_LIMIT")))
        val torrents = FakeTorrents()
        val mpv = FakeMpv()
        val vm = viewModel(debrid, torrents, mpv)
        val notice = withTimeout(5_000) { vm.notices.first { it is PlayerNotice.DebridFallback } } as PlayerNotice.DebridFallback
        assertEquals("TorBox", notice.service)
        assertEquals(DebridProblem.ACTIVE_LIMIT, notice.problem)
        // one TorBox attempt, one libtorrent play of the same torrent
        assertEquals(listOf(cachedHash), debrid.resolved)
        assertEquals(1, torrents.played.size)
        assertTrue(torrents.played.single().contains(cachedHash))
        assertEquals(listOf("http://127.0.0.1:1/video.mkv"), mpv.loaded)
    }

    @Test
    fun debridOffNeverAsks() {
        val debrid = FakeDebrid(fail = null)
        val torrents = FakeTorrents()
        val vm = viewModel(debrid, torrents, FakeMpv(), DebridSettings(enabled = false))
        assertEquals(0, debrid.marks)
        assertTrue(debrid.resolved.isEmpty())
        assertEquals("Popular", vm.state.value.sources.first().candidate.label)
        assertEquals(1, torrents.played.size)
    }

    @Test
    fun theDebridBonusIsAddedOnceWhenSourcesKeepArrivingAfterTheAutoStart() {
        // 1.1 review #4: the timer re-ranked the first list; later partial lists and the final pass must not add the bonus again
        val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        val web = RankedSource(
            SourceCandidate(extId = "web", extName = "Web", kind = SourceKind.WEB, label = "Alpha", quality = Quality.P1080, web = app.nijiyome.model.WebInfo("https://x/a.m3u8")),
            score = 65f,
        )
        val progressive = object : ExtensionRepository by Sources(ranked) {
            override fun candidatesFlow(media: Media, episode: Int): Flow<List<RankedSource>> = kotlinx.coroutines.flow.flow {
                emit(ranked)
                kotlinx.coroutines.delay(31_000)
                emit(ranked + web)
                kotlinx.coroutines.delay(1_000)
                emit(ranked + web)
            }
        }
        val vm = viewModel(FakeDebrid(fail = null), FakeTorrents(), FakeMpv(), extensions = progressive)
        scheduler.advanceUntilIdle()
        val cached = vm.state.value.sources.single { it.candidate.label == "Cached" }
        // raw 60.5 + the debrid difference (+10 bonus, -0.5 seeders) once = 70
        assertEquals(70f, cached.score)
        assertEquals(listOf("Cached", "Popular", "Alpha"), vm.state.value.sources.map { it.candidate.label })
    }

    // ------------------------------------------------------------------------------------------------ fakes

    private inner class FakeDebrid(private val fail: Throwable?) : DebridRepository {
        val resolved = mutableListOf<String>()
        val titles = mutableListOf<List<String>>()
        var marks = 0
        override val accounts = MutableStateFlow(listOf(DebridAccount(DebridService.TORBOX, "neo", null, 0)))
        override suspend fun link(service: DebridService, apiKey: String) = error("unused")
        override suspend fun unlink(service: DebridService) = Unit
        override suspend fun cached(infoHashes: List<String>) = infoHashes.filter { it == cachedHash }.associateWith { setOf(DebridService.TORBOX) }
        override suspend fun markCached(candidates: List<SourceCandidate>): List<SourceCandidate> {
            marks++
            return candidates.map { c -> c.copy(debrid = DebridStatus(DebridService.TORBOX, cached = c.torrent?.infoHash == cachedHash)) }
        }
        override suspend fun files(service: DebridService, infoHash: String, magnet: String?) = error("unused")
        override suspend fun resolveFile(
            service: DebridService, infoHash: String, magnet: String?, fileIndex: Int?, episode: Int?, titles: List<String>,
        ): Result<DebridResolved> {
            resolved += infoHash
            this.titles += titles
            fail?.let { return Result.failure(it) }
            val stream = StreamSource("https://cdn.example/dl/1/2", emptyMap(), Container.PROGRESSIVE, null, "TorBox", "torrent")
            return Result.success(DebridResolved(stream, DebridFileRef(service, infoHash, "2", "Show - 03.mkv")))
        }
    }

    private class FakeTorrents : TorrentRepository {
        val played = mutableListOf<String>()
        override val events = MutableSharedFlow<TorrentEvent>()
        override val library = MutableStateFlow(emptyList<TorrentLibraryItem>())
        override val status = MutableStateFlow(TorrentServiceStatus())
        override suspend fun play(source: String, media: Media, episode: Int, fileIndex: Int?): Result<TorrentPlayback> {
            played += source
            val stream = StreamSource("http://127.0.0.1:1/video.mkv", emptyMap(), Container.PROGRESSIVE, null, "torrent", "torrent")
            return Result.success(
                TorrentPlayback(PlayerArgs(media = media.ref, title = media.title, episode = episode), stream, "id", 0, "video.mkv", false),
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

    private class FakeMpv : PlayerSessionFactory {
        val loaded = mutableListOf<String>()
        override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean): PlayerSession = object : PlayerSession {
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
    }

    private class FakeSettings(initial: AppSettings) : SettingsRepository {
        override val settings = MutableStateFlow(initial)
        override suspend fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
    }

    private class OneMedia(private val m: Media) : MetadataRepository {
        override suspend fun feed(kind: MediaKind, feed: FeedKind, page: Int): FeedPage = throw UnsupportedOperationException()
        override suspend fun search(kind: MediaKind, query: String, page: Int): FeedPage = throw UnsupportedOperationException()
        override suspend fun media(kind: MediaKind, ref: MediaRef): Media? = m
        override suspend fun episodes(ref: MediaRef): List<Episode> = emptyList()
    }

    private class Sources(private val list: List<RankedSource>) : ExtensionRepository {
        override val installed = MutableStateFlow(emptyList<InstalledExtension>())
        override suspend fun installFromUri(uri: String): Result<InstalledExtension> = Result.failure(UnsupportedOperationException())
        override suspend fun setEnabled(id: String, enabled: Boolean) = Unit
        override suspend fun remove(id: String) = Unit
        override suspend fun resolveStreams(media: Media, episode: Int): List<StreamSource> = emptyList()
        override suspend fun resolveCandidates(media: Media, episode: Int): List<RankedSource> = list
    }

    private object NoHistory : HistoryRepository {
        override fun continueWatching(limit: Int): Flow<List<HistoryEntry>> = emptyFlow()
        override suspend fun record(entry: HistoryEntry) = Unit
        override fun progressFor(anilistId: Int): Flow<Map<Int, HistoryEntry>> = emptyFlow()
    }
}
