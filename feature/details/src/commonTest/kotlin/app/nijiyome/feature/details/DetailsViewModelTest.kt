package app.nijiyome.feature.details

import app.cash.turbine.test
import app.nijiyome.link.LinkApiError
import app.nijiyome.link.LinkError
import app.nijiyome.link.toAppException
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.Container
import app.nijiyome.model.MediaRelation
import app.nijiyome.model.MediaDetails
import app.nijiyome.model.CharactersPage
import app.nijiyome.model.CharacterRef
import app.nijiyome.model.CharacterEdge
import app.nijiyome.model.DiscoveredPc
import app.nijiyome.model.Episode
import app.nijiyome.model.ExtensionCompat
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.FeedKind
import app.nijiyome.model.FeedPage
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.InstalledExtension
import app.nijiyome.model.LinkCapabilities
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LinkSource
import app.nijiyome.model.LinkState
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.NextAiring
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.PairedPc
import app.nijiyome.model.PhoneTarget
import app.nijiyome.model.SendToPhone
import app.nijiyome.model.SendToPhoneRequest
import app.nijiyome.model.RankedSource
import app.nijiyome.model.ReleaseTags
import app.nijiyome.model.RemoteSession
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceStatsStore
import app.nijiyome.model.StreamSource
import app.nijiyome.model.TorrentInfo
import app.nijiyome.model.WebInfo
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val ref = MediaRef(anilistId = 7)
    private val anime = Media(
        ref = ref, kind = MediaKind.ANIME, title = "Seven", titles = emptyMap(), cover = null, banner = null, color = null,
        synopsis = null, genres = listOf("Action"), episodes = 12, status = null, season = null, year = null, score = null,
        sourceProvider = "fake",
    )
    private val webUrl = "https://cdn.example/a.m3u8"
    private val stream = StreamSource(webUrl, mapOf("Referer" to "https://x"), Container.HLS, "1080p", "prov", "ext")

    private val web = RankedSource(
        candidate = SourceCandidate(
            extId = "ext", extName = "Ext", kind = SourceKind.WEB, label = "Alpha",
            web = WebInfo(url = webUrl, headers = mapOf("Referer" to "https://x")),
        ),
        score = 90f,
        recommended = true,
    )
    private val hash = "0123456789abcdef0123456789abcdef01234567"
    private fun torrent(batch: Boolean = false, picked: Boolean = false) = RankedSource(
        candidate = SourceCandidate(
            extId = "nyaa", extName = "Nyaa", kind = SourceKind.TORRENT, label = "SubsPlease",
            torrent = TorrentInfo(infoHash = hash, seeders = 300),
            tags = ReleaseTags(batch = batch),
        ),
        score = 80f,
        picked = picked,
    )

    private fun entry(ep: Int, sec: Int, completed: Boolean, at: Long) = HistoryEntry(
        animeId = "7", title = "Seven", poster = null, episodeId = "7?ep=$ep", episode = ep, progressSec = sec,
        durationSec = 1400, completed = completed, watchedAt = Instant.ofEpochSecond(at), anilistId = 7, malId = null,
    )

    private class FakeMetadata(
        val media: Media?,
        val episodes: List<Episode>,
        val related: List<MediaRelation> = emptyList(),
        val mediaDetails: MediaDetails? = null,
        val pages: Map<Int, CharactersPage> = emptyMap(),
    ) : MetadataRepository {
        val characterPages = mutableListOf<Int>()
        override suspend fun feed(kind: MediaKind, feed: FeedKind, page: Int) = FeedPage(emptyList(), page, false)
        override suspend fun relations(ref: MediaRef): List<MediaRelation> = related
        override suspend fun details(ref: MediaRef): MediaDetails? = mediaDetails
        override suspend fun characters(ref: MediaRef, page: Int): CharactersPage {
            characterPages += page
            return pages[page] ?: CharactersPage(emptyList(), page, false)
        }
        override suspend fun search(kind: MediaKind, query: String, page: Int) = FeedPage(emptyList(), page, false)
        override suspend fun media(kind: MediaKind, ref: MediaRef) = media
        override suspend fun episodes(ref: MediaRef) = episodes
    }

    private class FakeHistory(val progress: MutableStateFlow<Map<Int, HistoryEntry>>) : HistoryRepository {
        override fun continueWatching(limit: Int): Flow<List<HistoryEntry>> = emptyFlow()
        override suspend fun record(entry: HistoryEntry) {}
        override fun progressFor(anilistId: Int): Flow<Map<Int, HistoryEntry>> = progress
    }

    private class FakeExtensions(
        val candidates: (Int) -> List<RankedSource>,
        val streams: () -> List<StreamSource> = { emptyList() },
        enabled: Boolean = true,
    ) : ExtensionRepository {
        val resolved = mutableListOf<Int>()
        override val installed: StateFlow<List<InstalledExtension>> = MutableStateFlow(
            listOf(InstalledExtension("ext", "Ext", "1.0", "anime", emptyList(), ExtensionCompat.FULL, enabled)),
        )
        override suspend fun installFromUri(uri: String) = Result.failure<InstalledExtension>(UnsupportedOperationException())
        override suspend fun setEnabled(id: String, enabled: Boolean) {}
        override suspend fun remove(id: String) {}
        override suspend fun resolveStreams(media: Media, episode: Int) = streams()
        override suspend fun resolveCandidates(media: Media, episode: Int): List<RankedSource> {
            resolved += episode
            return candidates(episode)
        }
    }

    private class FakeLink(initial: LinkState, val result: () -> Result<RemoteSession>) : LinkRepository {
        override val state = MutableStateFlow(initial)
        var lastSource: LinkSource? = null
        override fun discover(): Flow<List<DiscoveredPc>> = emptyFlow()
        override suspend fun pair(payload: String, deviceName: String) = Result.failure<PairedPc>(UnsupportedOperationException())
        override suspend fun capabilities() = Result.failure<LinkCapabilities>(UnsupportedOperationException())
        override suspend fun startSession(source: LinkSource, profileId: String?): Result<RemoteSession> {
            lastSource = source
            return result()
        }
        override suspend fun stop(sessionId: String) {}
        override suspend fun forget() {}
    }

    private class FakeStats : SourceStatsStore {
        val picks = mutableListOf<Triple<String, Int, String>>()
        override suspend fun reliability(extId: String, label: String) = 0.8f
        override suspend fun recordSourceResult(extId: String, label: String, ok: Boolean) = 0.8f
        override suspend fun pickedSource(mediaId: String, episode: Int): String? = null
        override suspend fun setPickedSource(mediaId: String, episode: Int, key: String) {
            picks += Triple(mediaId, episode, key)
        }
        override suspend fun clearPickedSource(mediaId: String, episode: Int) {}
    }

    /** Desktop: this PC's Link server handing episodes to its paired phones. */
    private class FakePhoneSender(phones: List<PhoneTarget>, val answer: (SendToPhoneRequest) -> Result<Unit>) : SendToPhone {
        override val phones = MutableStateFlow(phones)
        val sent = mutableListOf<Pair<String, SendToPhoneRequest>>()
        override suspend fun send(deviceId: String, request: SendToPhoneRequest): Result<Unit> {
            sent += deviceId to request
            return answer(request)
        }
    }

    private val pc = PairedPc("d1", "PC", "192.168.1.5", 47321)
    private val session = RemoteSession("s1", 0, "https://pc/master.m3u8", null, 4, 60)

    private fun vm(
        progress: Map<Int, HistoryEntry> = emptyMap(),
        extensions: FakeExtensions = FakeExtensions({ listOf(web, torrent()) }, { listOf(stream) }),
        link: FakeLink = FakeLink(LinkState.Unpaired) { Result.success(session) },
        media: Media? = anime,
        torrents: FakeTorrents = FakeTorrents(),
        stats: FakeStats = FakeStats(),
        related: List<MediaRelation> = emptyList(),
        metadata: FakeMetadata = FakeMetadata(media, (1..12).map { Episode(it, "Ep $it", null, null) }, related),
        prefs: DetailsPrefs = InMemoryDetailsPrefs(),
        phoneSender: SendToPhone? = null,
        noLink: Boolean = false,
        downloads: app.nijiyome.model.DownloadRepository? = null,
        debrid: app.nijiyome.model.torrent.DebridRepository? = null,
        settings: app.nijiyome.model.SettingsRepository? = null,
    ) = DetailsViewModel(
        MediaKind.ANIME, ref,
        metadata,
        FakeHistory(MutableStateFlow(progress)), extensions, if (noLink) null else link,
        settings = settings, torrents = torrents, stats = stats, prefs = prefs, phoneSender = phoneSender,
        downloads = downloads, debrid = debrid,
    )

    private val phone = PhoneTarget("p1", "Pixel 8", online = true)

    @Test
    fun `desktop sends the episode to a paired phone from where it was left`() = runTest {
        val sender = FakePhoneSender(listOf(phone)) { Result.success(Unit) }
        val vm = vm(progress = mapOf(3 to entry(3, 600, completed = false, at = 100)), extensions = FakeExtensions({ listOf(web) }), phoneSender = sender, noLink = true)
        assertFalse(vm.state.value.sendAvailable, "no Link client: the phone's own send stays hidden")
        assertEquals(listOf(phone), vm.state.value.phones)
        vm.events.test {
            vm.sendToPhone("p1")
            assertEquals(DetailsEvent.SentToPhone("Pixel 8"), awaitItem())
        }
        val (device, request) = sender.sent.single()
        assertEquals("p1", device)
        assertEquals(3, request.episode)
        assertEquals(600, request.positionSecs)
        assertEquals("7", request.mediaId)
        assertEquals("Seven", request.title)
        assertEquals(webUrl, assertIs<LinkSource.Url>(request.source).url)
        assertEquals(false, vm.state.value.sending)
        // the phones follow the Link server (off: none)
        sender.phones.value = emptyList()
        assertTrue(vm.state.value.phones.isEmpty())
    }

    @Test
    fun `desktop send to a phone starts a first watch at zero and tries the next source after a refused one`() = runTest {
        val otherUrl = "https://cdn.other/b.m3u8"
        val second = web.copy(candidate = web.candidate.copy(label = "Beta", web = WebInfo(url = otherUrl)), recommended = false)
        val sender = FakePhoneSender(listOf(phone)) { r ->
            if ((r.source as LinkSource.Url).url == webUrl) Result.failure(IllegalStateException("url_source_denied")) else Result.success(Unit)
        }
        val vm = vm(extensions = FakeExtensions({ listOf(web, second) }), phoneSender = sender, noLink = true)
        vm.events.test {
            vm.sendToPhone("p1")
            assertEquals(DetailsEvent.SentToPhone("Pixel 8"), awaitItem())
        }
        assertEquals(listOf(webUrl, otherUrl), sender.sent.map { (it.second.source as LinkSource.Url).url })
        assertTrue(sender.sent.all { it.second.positionSecs == 0 && it.second.episode == 1 })

        // a phone that is gone is not retried with other sources; no source at all is said so
        val gone = FakePhoneSender(listOf(phone)) { Result.failure(IllegalStateException("device_not_authorized")) }
        val vm2 = vm(extensions = FakeExtensions({ listOf(web, second) }), phoneSender = gone, noLink = true)
        vm2.events.test {
            vm2.sendToPhone("p1")
            assertEquals(DetailsEvent.PhoneSendFailed(PhoneSendFail.NOT_PAIRED), awaitItem())
        }
        assertEquals(1, gone.sent.size)
        val none = vm(extensions = FakeExtensions({ emptyList() }, { emptyList() }), phoneSender = sender, noLink = true)
        none.events.test {
            none.sendToPhone("p1")
            assertEquals(DetailsEvent.PhoneSendFailed(PhoneSendFail.NO_SOURCE), awaitItem())
        }
        // an unknown phone does nothing; without a sender (Android) there are no phones
        val before = sender.sent.size
        vm.sendToPhone("nope")
        assertEquals(before, sender.sent.size)
        assertTrue(vm().state.value.phones.isEmpty())
        assertEquals(PhoneSendFail.LINK_OFF, PhoneSendFail.of("link_off"))
        assertEquals(PhoneSendFail.SOURCE, PhoneSendFail.of("source_unavailable"))
    }

    @Test
    fun `loads media and sorted episodes`() = runTest {
        val s = vm().state.value
        assertEquals("Seven", assertIs<LoadState.Ok<Media>>(s.media).value.title)
        assertEquals(12, assertIs<LoadState.Ok<List<Episode>>>(s.episodes).value.size)
    }

    @Test
    fun `missing media becomes a 404 error`() = runTest {
        val s = vm(media = null).state.value
        assertEquals(AppError.Http(404), assertIs<LoadState.Err>(s.media).cause)
        assertNull(s.panel)
    }

    @Test
    fun `continue target resumes unfinished or advances after completed`() {
        assertEquals(1, continueTarget(emptyMap(), 12))
        assertEquals(3, continueTarget(mapOf(3 to entry(3, 100, false, 10)), 12))
        assertEquals(4, continueTarget(mapOf(3 to entry(3, 1300, true, 10)), 12))
        assertEquals(12, continueTarget(mapOf(12 to entry(12, 1300, true, 10)), 12))
    }

    @Test
    fun watchActionRule() {
        val airing = NextAiring(episode = 6, airingAtEpochSec = 2_000)
        assertEquals(WatchAction.START, watchActionOf(false, false, 1, null, 1_000))
        assertEquals(WatchAction.CONTINUE, watchActionOf(false, true, 4, airing, 1_000))
        assertEquals(WatchAction.UP_TO_DATE, watchActionOf(false, true, 6, airing, 1_000), "ep 6 airs later")
        assertEquals(WatchAction.CONTINUE, watchActionOf(false, true, 6, airing, 3_000), "ep 6 aired already")
        assertEquals(WatchAction.REWATCH, watchActionOf(true, true, 1, null, 1_000))
    }

    @Test
    fun `the source panel resolves the continue episode once and keeps the recommended row`() = runTest {
        val ext = FakeExtensions({ listOf(web, torrent()) })
        val s = vm(progress = mapOf(3 to entry(3, 100, false, 10)), extensions = ext).state.value
        assertEquals(3, s.panel?.episode)
        assertEquals(listOf(3), ext.resolved)
        assertEquals(web, s.shownSource)
        assertNull(s.selectedKey)
        assertEquals(torrent(), s.bestTorrent)
    }

    @Test
    fun `a stored manual pick is shown but the player reads it itself`() = runTest {
        // BUG-018: the panel's remembered pick is not forced on the player (it may be stale after a change inside the player).
        val picked = torrent(picked = true)
        val vm = vm(extensions = FakeExtensions({ listOf(web, picked) }))
        assertEquals(picked.key, vm.state.value.selectedKey)
        vm.events.test {
            vm.onWatch()
            val event = assertIs<DetailsEvent.NavigateToPlayer>(awaitItem())
            assertEquals(1, event.args.episode)
            assertNull(event.args.sourceKey)
            assertFalse(event.args.sourceConfirmed)
        }
    }

    @Test
    fun `a source chosen in the panel picker is passed once, unconfirmed`() = runTest {
        val vm = vm()
        vm.events.test {
            vm.openPicker(playOnPick = false)
            vm.onPick(torrent())
            awaitItem() // SourceChosen
            vm.onWatch()
            val first = assertIs<DetailsEvent.NavigateToPlayer>(awaitItem())
            assertEquals(torrent().key, first.args.sourceKey)
            assertFalse(first.args.sourceConfirmed, "Manual still asks, with it preselected")
            vm.onWatch()
            assertNull(assertIs<DetailsEvent.NavigateToPlayer>(awaitItem()).args.sourceKey)
        }
    }

    @Test
    fun `the split button picker plays the picked source and stores the pick`() = runTest {
        val stats = FakeStats()
        val vm = vm(stats = stats)
        vm.events.test {
            vm.openPicker(playOnPick = true)
            assertEquals(PickerRequest(1, playOnPick = true), vm.state.value.picker)
            vm.onPick(torrent())
            val event = assertIs<DetailsEvent.NavigateToPlayer>(awaitItem())
            assertEquals(torrent().key, event.args.sourceKey)
            assertTrue(event.args.sourceConfirmed, "picked right now: Manual plays it without asking again")
            assertNull(vm.state.value.picker)
            assertEquals(listOf(Triple("7", 1, torrent().key)), stats.picks)
        }
    }

    @Test
    fun `the panel picker only changes the source`() = runTest {
        val vm = vm()
        vm.events.test {
            vm.openPicker(playOnPick = false)
            vm.onPick(torrent())
            assertEquals(DetailsEvent.SourceChosen("Nyaa", "SubsPlease"), awaitItem())
            assertEquals(torrent().key, vm.state.value.selectedKey)
            assertEquals(torrent(), vm.state.value.shownSource)
        }
    }

    @Test
    fun `episode click navigates with the saved position`() = runTest {
        val vm = vm(progress = mapOf(2 to entry(2, 90, false, 5)))
        vm.events.test {
            vm.onEpisodeClick(5)
            val event = assertIs<DetailsEvent.NavigateToPlayer>(awaitItem())
            assertEquals(5, event.args.episode)
            assertEquals(0L, event.args.startMs)
            vm.onEpisodeClick(2)
            val resumed = assertIs<DetailsEvent.NavigateToPlayer>(awaitItem())
            assertEquals(90_000L, resumed.args.startMs)
            assertNull(resumed.args.remoteSessionId)
        }
    }

    @Test
    fun `candidate failure is shown in the panel and retry resolves again`() = runTest {
        var fail = true
        val ext = FakeExtensions({ if (fail) throw AppException(AppError.Extension("ext", "timeout")) else listOf(web) })
        val vm = vm(extensions = ext)
        assertEquals(AppError.Extension("ext", "timeout"), assertIs<LoadState.Err>(vm.state.value.panel?.sources).cause)
        fail = false
        vm.retrySources()
        assertEquals(listOf(web), assertIs<LoadState.Ok<List<RankedSource>>>(vm.state.value.panel?.sources).value)
    }

    @Test
    fun `no enabled extension is reported`() = runTest {
        val vm = vm(extensions = FakeExtensions({ emptyList() }, enabled = false))
        assertEquals(false, vm.state.value.hasExtensions)
    }

    @Test
    fun `download adds the torrent uri of the source and reports the id`() = runTest {
        val torrents = FakeTorrents { Result.success(hash) }
        val vm = vm(torrents = torrents)
        vm.events.test {
            vm.download(torrent(), episode = 1)
            assertEquals(DetailsEvent.DownloadQueued(hash), awaitItem())
        }
        assertEquals(listOf(FakeTorrents.Call("magnet:?xt=urn:btih:$hash", "Seven", 1)), torrents.downloads)
    }

    @Test
    fun `download failure and missing torrents are reported`() = runTest {
        val vm = vm(
            extensions = FakeExtensions({ ep -> if (ep == 1) listOf(web, torrent()) else listOf(web) }),
            torrents = FakeTorrents { Result.failure(AppException(AppError.Offline)) },
        )
        vm.events.test {
            vm.downloadEpisode(1)
            assertEquals(DetailsEvent.DownloadFailed(AppError.Offline), awaitItem())
            vm.downloadEpisode(4)
            assertEquals(DetailsEvent.NoTorrentSource, awaitItem())
            vm.downloadSeason() // best torrent is not a batch
            assertEquals(DetailsEvent.NoTorrentSource, awaitItem())
        }
    }

    @Test
    fun `a batch torrent offers the whole season`() = runTest {
        val torrents = FakeTorrents()
        val vm = vm(extensions = FakeExtensions({ listOf(torrent(batch = true)) }), torrents = torrents)
        assertTrue(vm.state.value.canDownloadSeason)
        vm.downloadSeason()
        assertEquals(listOf(FakeTorrents.Call("magnet:?xt=urn:btih:$hash", "Seven", null)), torrents.downloads)
    }

    @Test
    fun `send to device starts a url session with a web stream and navigates with its id`() = runTest {
        val link = FakeLink(LinkState.Paired(pc, reachable = true)) { Result.success(session) }
        val vm = vm(link = link)
        assertTrue(vm.state.value.pcAvailable)
        vm.events.test {
            vm.sendToDevice()
            val event = assertIs<DetailsEvent.NavigateToPlayer>(awaitItem())
            assertEquals("s1", event.args.remoteSessionId)
        }
        assertEquals(webUrl, assertIs<LinkSource.Url>(link.lastSource).url)
        assertEquals(false, vm.state.value.sending)
    }

    @Test
    fun `send failures are reported`() = runTest {
        val link = FakeLink(LinkState.Paired(pc, true)) { Result.failure(AppException(AppError.Offline)) }
        val vm = vm(link = link)
        vm.events.test {
            vm.sendToDevice()
            assertEquals(DetailsEvent.SendFailed(AppError.Offline, SendFailReason.OFFLINE), awaitItem())
        }
        // BUG-009: a torrent alone is now sendable (the PC streams it); nothing at all is "no source"
        val torrentOnly = vm(link = link, extensions = FakeExtensions({ listOf(torrent()) }))
        torrentOnly.events.test {
            torrentOnly.sendToDevice()
            assertEquals(DetailsEvent.SendFailed(AppError.Offline, SendFailReason.OFFLINE), awaitItem())
        }
        assertIs<LinkSource.Torrent>(link.lastSource)
        val noWeb = vm(link = link, extensions = FakeExtensions({ emptyList() }, { emptyList() }))
        noWeb.events.test {
            noWeb.sendToDevice()
            assertEquals(DetailsEvent.NoWebSource, awaitItem())
        }
    }

    @Test
    fun `a source the PC refuses is skipped for the next web source and the reason is kept`() = runTest {
        val denied = LinkError.Api(LinkApiError.UrlSourceDenied).toAppException()
        val otherUrl = "https://cdn.other/b.m3u8"
        val second = web.copy(
            candidate = web.candidate.copy(label = "Beta", web = WebInfo(url = otherUrl, headers = emptyMap())),
            recommended = false,
        )
        val tried = mutableListOf<String>()
        lateinit var link: FakeLink
        link = FakeLink(LinkState.Paired(pc, true)) {
            tried += assertIs<LinkSource.Url>(link.lastSource).url
            if (tried.size == 1) Result.failure(denied) else Result.success(session)
        }
        val vm = vm(link = link, extensions = FakeExtensions({ listOf(web, second) }))
        vm.events.test {
            vm.sendToDevice()
            assertIs<DetailsEvent.NavigateToPlayer>(awaitItem())
        }
        assertEquals(listOf(webUrl, otherUrl), tried)

        val alwaysDenied = FakeLink(LinkState.Paired(pc, true)) { Result.failure(denied) }
        val vm2 = vm(link = alwaysDenied, extensions = FakeExtensions({ listOf(web) }))
        vm2.events.test {
            vm2.sendToDevice()
            assertEquals(SendFailReason.SOURCE_DENIED, assertIs<DetailsEvent.SendFailed>(awaitItem()).reason)
        }
        assertEquals(false, vm2.state.value.sending)
    }

    @Test
    fun `a chosen torrent is sent first for the PC to stream, then the web sources`() = runTest {
        val sent = mutableListOf<LinkSource>()
        lateinit var link: FakeLink
        link = FakeLink(LinkState.Paired(pc, true)) {
            sent += link.lastSource!!
            if (sent.size == 1) Result.failure(LinkError.Api(LinkApiError.RequestInvalid).toAppException()) else Result.success(session)
        }
        val vm = vm(link = link, extensions = FakeExtensions({ listOf(web, torrent(picked = true)) }))
        vm.events.test {
            vm.sendToDevice()
            val other = assertIs<DetailsEvent.SentOther>(awaitItem())
            assertTrue(other.chosenWasTorrent)
            assertIs<DetailsEvent.NavigateToPlayer>(awaitItem())
        }
        val first = assertIs<LinkSource.Torrent>(sent[0])
        assertEquals("magnet:?xt=urn:btih:$hash", first.uri)
        assertEquals("7", first.mediaId)
        assertEquals(webUrl, assertIs<LinkSource.Url>(sent[1]).url)
    }

    @Test
    fun `send failure reasons come from the Link error code`() {
        assertEquals(SendFailReason.SOURCE_UNAVAILABLE, sendFailReason(LinkError.Api(LinkApiError.SourceUnavailable).toAppException()))
        assertEquals(SendFailReason.PROFILE_UNAVAILABLE, sendFailReason(LinkError.Api(LinkApiError.ProfileUnavailable).toAppException()))
        assertEquals(SendFailReason.OFFLINE, sendFailReason(LinkError.Transport.Refused.toAppException()))
        assertEquals(SendFailReason.NOT_PAIRED, sendFailReason(LinkError.Transport.Tls.Revoked.toAppException()))
        assertEquals(SendFailReason.OFFLINE, sendFailReason(AppException(AppError.Offline)))
        assertEquals(SendFailReason.OTHER, sendFailReason(IllegalStateException("x")))
    }

    @Test
    fun `related tab loads the real AniList relations, story chain first`() = runTest {
        val sequel = MediaRelation("SEQUEL", anime.copy(ref = MediaRef(anilistId = 8), title = "Eight"))
        val adaptation = MediaRelation("ADAPTATION", anime.copy(ref = MediaRef(anilistId = 9), title = "Nine", kind = MediaKind.MANGA))
        val vm = vm(related = listOf(adaptation, sequel))
        assertNull(vm.state.value.relations)
        vm.loadRelated()
        assertEquals(listOf("SEQUEL", "ADAPTATION"), assertIs<LoadState.Ok<List<MediaRelation>>>(vm.state.value.relations).value.map { it.relation })
    }

    @Test
    fun `an adult title is gated until accepted and the choice is remembered`() = runTest {
        val prefs = InMemoryDetailsPrefs()
        val adultMeta = FakeMetadata(anime, emptyList(), mediaDetails = MediaDetails(ref = ref, isAdult = true))
        val first = vm(metadata = adultMeta, prefs = prefs)
        assertTrue(first.state.value.adultGate)
        first.acceptAdult()
        assertEquals(false, first.state.value.adultGate)
        val again = vm(metadata = adultMeta, prefs = prefs)
        assertEquals(false, again.state.value.adultGate)
    }

    @Test
    fun `characters page in and stop when AniList has no next page`() = runTest {
        val edge = { id: Int -> CharacterEdge(CharacterRef(id, "C$id"), role = "MAIN") }
        val meta = FakeMetadata(
            anime, emptyList(),
            pages = mapOf(1 to CharactersPage(listOf(edge(1), edge(2)), 1, true), 2 to CharactersPage(listOf(edge(2), edge(3)), 2, false)),
        )
        val vm = vm(metadata = meta)
        vm.loadCharacters()
        vm.loadCharacters()
        vm.loadCharacters()
        val c = vm.state.value.characters!!
        assertEquals(listOf(1, 2, 3), c.items.map { it.character.id })
        assertEquals(false, c.hasNext)
        assertEquals(listOf(1, 2), meta.characterPages)
    }

    @Test
    fun `episode view mode is remembered`() = runTest {
        val prefs = InMemoryDetailsPrefs()
        vm(prefs = prefs).setEpisodeView(EpisodeView.COMPACT)
        assertEquals(EpisodeView.COMPACT, vm(prefs = prefs).state.value.episodeView)
    }

    // ---- 1.1 (F7): Download on a torrent cached on TorBox ------------------------------------------------------------------------

    private class FakeDebrid(
        val cachedHashes: Set<String>,
        val fail: Throwable? = null,
        override val settings: app.nijiyome.model.torrent.DebridSettings = app.nijiyome.model.torrent.DebridSettings(),
    ) : app.nijiyome.model.torrent.DebridRepository {
        val resolved = mutableListOf<Pair<String, Int?>>()
        override val accounts = MutableStateFlow(listOf(app.nijiyome.model.torrent.DebridAccount(app.nijiyome.model.torrent.DebridService.TORBOX, "neo", null, 0)))
        override suspend fun link(service: app.nijiyome.model.torrent.DebridService, apiKey: String) = error("unused")
        override suspend fun unlink(service: app.nijiyome.model.torrent.DebridService) = Unit
        override suspend fun cached(infoHashes: List<String>) =
            infoHashes.filter { it in cachedHashes }.associateWith { setOf(app.nijiyome.model.torrent.DebridService.TORBOX) }
        override suspend fun files(service: app.nijiyome.model.torrent.DebridService, infoHash: String, magnet: String?) = error("unused")
        override suspend fun markCached(candidates: List<SourceCandidate>) = candidates.map { c ->
            val h = c.torrent?.infoHash ?: return@map c
            c.copy(debrid = app.nijiyome.model.torrent.DebridStatus(app.nijiyome.model.torrent.DebridService.TORBOX, cached = h in cachedHashes))
        }
        override suspend fun resolveFile(
            service: app.nijiyome.model.torrent.DebridService, infoHash: String, magnet: String?, fileIndex: Int?, episode: Int?, titles: List<String>,
        ): Result<app.nijiyome.model.torrent.DebridResolved> {
            resolved += infoHash to episode
            fail?.let { return Result.failure(it) }
            val s = StreamSource("https://cdn.example/dl/9", emptyMap(), Container.PROGRESSIVE, "1080p", "TorBox", "torrent")
            return Result.success(app.nijiyome.model.torrent.DebridResolved(s, app.nijiyome.model.torrent.DebridFileRef(service, infoHash, "4", "Seven - 01.mkv")))
        }
    }

    private class FakeDownloads : app.nijiyome.model.DownloadRepository {
        val queued = mutableListOf<app.nijiyome.model.DownloadRequest>()
        override val items = MutableStateFlow(emptyList<app.nijiyome.model.DownloadItem>())
        override val summary = MutableStateFlow(app.nijiyome.model.DownloadsSummary())
        override suspend fun enqueue(request: app.nijiyome.model.DownloadRequest): Result<app.nijiyome.model.DownloadItem> {
            queued += request
            return Result.success(
                app.nijiyome.model.DownloadItem(
                    id = "7?ep=${request.episode}", ref = request.media.ref, title = request.media.title, episode = request.episode,
                    extId = request.stream.extensionId, sourceLabel = request.sourceLabel, container = Container.MP4,
                    state = app.nijiyome.model.DownloadState.QUEUED, createdAtMs = 0,
                ),
            )
        }
        override suspend fun enqueueSeason(media: Media, episodes: List<Int>, preferredServer: String?, preferredAudioLang: String?) = error("unused")
        override suspend fun pause(id: String) = Unit
        override suspend fun resume(id: String) = Unit
        override suspend fun remove(id: String) = Unit
        override suspend fun retry(id: String) = Unit
        override fun history(limit: Int): Flow<List<app.nijiyome.model.DownloadItem>> = emptyFlow()
        override suspend fun clearHistory() = Unit
        override suspend fun localUriFor(ref: MediaRef, episode: Int): String? = null
        override fun downloadedEpisodes(ref: MediaRef): Flow<Map<Int, app.nijiyome.model.DownloadItem>> = emptyFlow()
        override val rules = MutableStateFlow(emptyList<app.nijiyome.model.AutoDownloadRule>())
        override suspend fun setRule(rule: app.nijiyome.model.AutoDownloadRule?) = Unit
        override suspend fun preview(ref: MediaRef): app.nijiyome.model.AutoDownloadPreview? = null
        override suspend fun checkNow(): Result<Int> = Result.success(0)
        override val library = MutableStateFlow(emptyList<app.nijiyome.model.LocalLibraryItem>())
        override suspend fun importFiles(uris: List<String>) = Result.success(emptyList<app.nijiyome.model.LocalLibraryItem>())
        override suspend fun scanFolder(treeUri: String) = Result.success(emptyList<app.nijiyome.model.LocalLibraryItem>())
        override suspend fun matchLocal(id: String, media: Media?, episode: Int?) = Unit
        override suspend fun removeLocal(id: String, deleteFile: Boolean) = Unit
        override suspend fun storageUsedBytes(): Long = 0
        override suspend fun verifyIntegrity(): List<app.nijiyome.model.DownloadItem> = emptyList()
    }

    @Test
    fun `download of a torrent cached on TorBox goes to the web downloads with the debrid file`() = runTest {
        val torrents = FakeTorrents { Result.success(hash) }
        val downloads = FakeDownloads()
        val debrid = FakeDebrid(setOf(hash))
        val vm = vm(torrents = torrents, downloads = downloads, debrid = debrid)
        vm.events.test {
            vm.download(torrent(), episode = 1)
            assertEquals(DetailsEvent.DebridDownloadQueued("TorBox"), awaitItem())
        }
        assertEquals(listOf<Pair<String, Int?>>(hash to 1), debrid.resolved)
        val request = downloads.queued.single()
        assertEquals("https://cdn.example/dl/9", request.stream.url)
        assertEquals("4", request.debrid?.fileId)
        assertEquals("TorBox", request.sourceLabel)
        assertTrue(torrents.downloads.isEmpty())
    }

    @Test
    fun `a refused debrid download says why and goes to libtorrent`() = runTest {
        val torrents = FakeTorrents { Result.success(hash) }
        val downloads = FakeDownloads()
        val debrid = FakeDebrid(setOf(hash), fail = AppException(AppError.Unknown("debrid:torbox:DOWNLOAD_TOO_LARGE")))
        val vm = vm(torrents = torrents, downloads = downloads, debrid = debrid)
        vm.events.test {
            vm.download(torrent(), episode = 1)
            assertEquals(DetailsEvent.DebridDownloadFallback("TorBox", app.nijiyome.model.torrent.DebridProblem.TOO_LARGE), awaitItem())
            assertEquals(DetailsEvent.DownloadQueued(hash), awaitItem())
        }
        assertTrue(downloads.queued.isEmpty())
        assertEquals(1, torrents.downloads.size)
    }

    @Test
    fun `not cached, setting off or a whole pack keep libtorrent`() = runTest {
        for ((debrid, episode) in listOf(
            FakeDebrid(emptySet()) to 1,
            FakeDebrid(setOf(hash), settings = app.nijiyome.model.torrent.DebridSettings(downloadCached = false)) to 1,
            FakeDebrid(setOf(hash)) to null,
        )) {
            val torrents = FakeTorrents { Result.success(hash) }
            val downloads = FakeDownloads()
            val vm = vm(torrents = torrents, downloads = downloads, debrid = debrid)
            vm.events.test {
                vm.download(torrent(), episode = episode)
                assertEquals(DetailsEvent.DownloadQueued(hash), awaitItem())
            }
            assertTrue(debrid.resolved.isEmpty())
            assertTrue(downloads.queued.isEmpty())
        }
    }

    private class FakeSettings : app.nijiyome.model.SettingsRepository {
        override val settings = MutableStateFlow(app.nijiyome.model.AppSettings())
        override suspend fun update(transform: (app.nijiyome.model.AppSettings) -> app.nijiyome.model.AppSettings) {
            settings.value = transform(settings.value)
        }
    }

    @Test
    fun `download of another episode than the panel's prefers the torrent cached on TorBox`() = runTest {
        // 1.1 review #10: the episode-row download resolves its own list; it gets the same TorBox order as the panel
        val cachedHash = "f".repeat(40)
        val cached = RankedSource(
            candidate = SourceCandidate(extId = "nyaa", extName = "Nyaa", kind = SourceKind.TORRENT, label = "Cached", torrent = TorrentInfo(infoHash = cachedHash, seeders = 25)),
            score = 75.5f,
        )
        val ext = FakeExtensions({ ep -> if (ep == 2) listOf(torrent(), cached) else listOf(web) })
        val torrents = FakeTorrents { Result.success(hash) }
        val downloads = FakeDownloads()
        val debrid = FakeDebrid(setOf(cachedHash))
        val vm = vm(extensions = ext, torrents = torrents, downloads = downloads, debrid = debrid, settings = FakeSettings())
        vm.events.test {
            vm.downloadEpisode(2)
            assertEquals(DetailsEvent.DebridDownloadQueued("TorBox"), awaitItem())
        }
        assertEquals(listOf<Pair<String, Int?>>(cachedHash to 2), debrid.resolved)
        assertTrue(torrents.downloads.isEmpty())
    }
}
