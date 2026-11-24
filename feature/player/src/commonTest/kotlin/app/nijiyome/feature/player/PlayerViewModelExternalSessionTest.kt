// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.AppSettings
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
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.StreamSource
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.player.ExternalSessionFactory
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerSessionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Hito 4, paso D: the view model with an external player that is a session (desktop: mpv in its own window). Android binds no
 * [ExternalSessionFactory], so none of this changes it (PlayerViewModelWithoutMedia3Test covers the view model without one).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelExternalSessionTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val args = PlayerArgs(media = MediaRef(anilistId = 1), title = "Title", episode = 3, localUri = "/videos/episode-3.mkv")

    private fun vm(engine: PlayerEngine, mpv: Factory, external: ExternalFactory?) = PlayerViewModel(
        args = args,
        metadata = NoMetadata,
        extensions = NoExtensions,
        history = NoHistory,
        link = NoLinkRepository,
        remote = NoRemotePlayback,
        media3 = null,
        flush = null,
        settingsRepository = FakeSettings(AppSettings(playerEngine = engine)),
        mpvFactory = mpv,
        subtitleTools = null,
        secrets = null,
        linkDecodeLimits = { null },
        externalPlayer = null,
        externalSessions = external,
    )

    @Test
    fun theExternalSettingPlaysInTheExternalWindowWithItsCard() {
        val mpv = Factory()
        val ext = ExternalFactory()
        val vm = vm(PlayerEngine.EXTERNAL, mpv, ext)
        assertTrue(mpv.created.isEmpty())
        val s = ext.created.single()
        assertEquals(listOf("/videos/episode-3.mkv"), s.loaded)
        assertEquals("Title · E3", ext.titles.single())
        assertEquals(PlayerEngine.EXTERNAL, vm.state.value.engine)
        assertEquals(PlayerPhase.EXTERNAL, vm.state.value.phase)
        assertNotNull(vm.state.value.external)
        assertSame(s, vm.state.value.session)
        // The first frame keeps the card (no PLAYING phase with controls over an empty video).
        s.emit { it.copy(started = true, isPlaying = true, durationMs = 1_000) }
        assertEquals(PlayerPhase.EXTERNAL, vm.state.value.phase)
    }

    @Test
    fun frameGenerationPullsALibmpvUserIntoTheExternalWindow() {
        val mpv = Factory()
        val ext = ExternalFactory(wanted = setOf(PlayerEngine.EXTERNAL, PlayerEngine.MPV))
        val vm = vm(PlayerEngine.MPV, mpv, ext)
        assertEquals(1, ext.created.size)
        assertEquals(PlayerPhase.EXTERNAL, vm.state.value.phase)

        // "Ver sin generación de cuadros": this playback moves to libmpv, the external window closes.
        vm.useBuiltInPlayer()
        assertTrue(ext.created.single().released)
        assertEquals(listOf("/videos/episode-3.mkv"), mpv.created.single().loaded)
        assertEquals(PlayerEngine.MPV, vm.state.value.engine)
        assertNull(vm.state.value.external)
        assertEquals(PlayerPhase.BUFFERING, vm.state.value.phase)
    }

    @Test
    fun withoutMpvTheBuiltInEngineSaysWhy() = runTest {
        val mpv = Factory()
        val ext = ExternalFactory(missing = "mpv is not installed (not on PATH)")
        val vm = vm(PlayerEngine.EXTERNAL, mpv, ext)
        assertTrue(ext.created.isEmpty())
        assertEquals(1, mpv.created.size)
        assertEquals(PlayerEngine.MPV, vm.state.value.engine)
        assertEquals(PlayerPhase.BUFFERING, vm.state.value.phase)
        val notice = withTimeout(5_000) { vm.notices.first() }
        assertEquals(PlayerNotice.ExternalUnavailable("mpv is not installed (not on PATH)"), notice)
    }

    @Test
    fun mpvGoneWhenTheSessionIsCreatedFallsBackInsteadOfCrashing() = runTest {
        // Review: available when the view model was built, missing by the time the source is loaded (a path changed in Settings).
        val mpv = Factory()
        val ext = ExternalFactory(goneAfterChecks = 1)
        val vm = vm(PlayerEngine.EXTERNAL, mpv, ext)
        assertTrue(ext.created.isEmpty())
        assertEquals(listOf("/videos/episode-3.mkv"), mpv.created.single().loaded)
        assertEquals(PlayerEngine.MPV, vm.state.value.engine)
        assertNull(vm.state.value.external)
        assertEquals(PlayerPhase.BUFFERING, vm.state.value.phase)
        assertEquals(PlayerNotice.ExternalUnavailable("mpv not found at /nope/mpv"), withTimeout(5_000) { vm.notices.first() })
    }

    @Test
    fun anotherEngineSettingStaysOnLibmpv() {
        val mpv = Factory()
        val ext = ExternalFactory()
        val vm = vm(PlayerEngine.MPV, mpv, ext)
        assertTrue(ext.created.isEmpty())
        assertEquals(PlayerEngine.MPV, vm.state.value.engine)
        assertNull(vm.state.value.external)
    }

    private open class Factory : PlayerSessionFactory {
        val created = mutableListOf<FakeSession>()

        override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean): PlayerSession = FakeSession().also { created += it }
    }

    private class ExternalFactory(
        private val wanted: Set<PlayerEngine> = setOf(PlayerEngine.EXTERNAL),
        private val missing: String? = null,
        /** mpv disappears after this many availability checks (the view model's own check at construction is the first). */
        private val goneAfterChecks: Int? = null,
    ) : Factory(), ExternalSessionFactory {
        val titles = mutableListOf<String>()
        private var checks = 0

        override fun wants(engine: PlayerEngine): Boolean = engine in wanted

        override fun unavailableReason(): String? {
            checks++
            return missing ?: goneAfterChecks?.takeIf { checks > it }?.let { "mpv not found at /nope/mpv" }
        }

        override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean, title: String): PlayerSession {
            titles += title
            return create(scope, listener, remote)
        }
    }

    private class FakeSession : PlayerSession {
        val loaded = mutableListOf<String>()
        var released = false
        private val _state = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = _state

        fun emit(change: (PlaybackState) -> PlaybackState) = _state.update(change)

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
        override fun release() {
            released = true
        }
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
