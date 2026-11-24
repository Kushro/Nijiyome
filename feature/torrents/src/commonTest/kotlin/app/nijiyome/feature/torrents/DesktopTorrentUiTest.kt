// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents

import app.nijiyome.feature.torrents.ui.droppedTorrent
import app.nijiyome.feature.torrents.vm.AddTorrentViewModel
import app.nijiyome.feature.torrents.vm.LibrarySort
import app.nijiyome.feature.torrents.vm.sortLibrary
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.torrent.EpisodeAlignment
import app.nijiyome.model.torrent.StorageLocation
import app.nijiyome.model.torrent.TorrentEvent
import app.nijiyome.model.torrent.TorrentFile
import app.nijiyome.model.torrent.TorrentId
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentPeer
import app.nijiyome.model.torrent.TorrentPlayback
import app.nijiyome.model.torrent.TorrentPreview
import app.nijiyome.model.torrent.TorrentPreviewFile
import app.nijiyome.model.torrent.TorrentPreviewProgress
import app.nijiyome.model.torrent.TORRENT_PREVIEW_TIMEOUT_DETAIL
import kotlinx.coroutines.CompletableDeferred
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentServiceStatus
import app.nijiyome.model.torrent.TorrentState
import app.nijiyome.model.torrent.TorrentStats
import app.nijiyome.model.torrent.TorrentStorage
import app.nijiyome.model.torrent.TorrentTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

/** Hito 6 desktop torrent screens: the library order, the dropped `.torrent`, and "Añadir torrent" with its file list. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTorrentUiTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun t(id: String, name: String, added: Long, size: Long = 0, progress: Double = 0.0, episode: Int? = null, state: TorrentState = TorrentState.PAUSED) =
        TorrentLibraryItem(id = id, name = name, addedUnix = added, sizeBytes = size, progress = progress, episode = episode, state = state)

    private val engineOrder = listOf(
        t("c", "charlie", added = 30, size = 5, progress = 0.2, episode = 3, state = TorrentState.SEEDING),
        t("a", "Alpha", added = 20, size = 50, progress = 1.0, episode = null, state = TorrentState.ERROR),
        t("b", "bravo", added = 10, size = 20, progress = 0.6, episode = 1, state = TorrentState.DOWNLOADING),
    )

    @Test
    fun theLibrarySortsByEveryKeyBothWays() {
        fun ids(sort: LibrarySort, desc: Boolean) = sortLibrary(engineOrder, sort, desc).map { it.id }
        assertEquals(listOf("c", "a", "b"), ids(LibrarySort.ADDED, true)) // the engine's own order
        assertEquals(listOf("b", "a", "c"), ids(LibrarySort.ADDED, false))
        assertEquals(listOf("a", "b", "c"), ids(LibrarySort.NAME, false)) // case-insensitive
        assertEquals(listOf("c", "b", "a"), ids(LibrarySort.NAME, true))
        assertEquals(listOf("b", "c", "a"), ids(LibrarySort.EPISODE, false)) // no episode last, both ways
        assertEquals(listOf("c", "b", "a"), ids(LibrarySort.EPISODE, true))
        assertEquals(listOf("a", "b", "c"), ids(LibrarySort.SIZE, true))
        assertEquals(listOf("c", "b", "a"), ids(LibrarySort.PROGRESS, false))
        assertEquals(listOf("b", "c", "a"), ids(LibrarySort.STATE, false)) // downloading, seeding, ..., error
        // the series title names a labelled torrent
        val labelled = listOf(t("x", "zzz.mkv", 1).copy(series = "Aaa"), t("y", "bbb", 2))
        assertEquals(listOf("x", "y"), sortLibrary(labelled, LibrarySort.NAME, false).map { it.id })
    }

    @Test
    fun onlyATorrentFileIsTakenFromADrop() {
        assertEquals("/d/Show.TORRENT", droppedTorrent(listOf("/d/cover.jpg", "/d/Show.TORRENT", "/d/b.torrent")))
        assertNull(droppedTorrent(listOf("/d/video.mkv")))
        assertNull(droppedTorrent(emptyList()))
    }

    private class FakeTorrents : TorrentRepository {
        override val events = MutableSharedFlow<TorrentEvent>()
        override val library = MutableStateFlow<List<TorrentLibraryItem>>(emptyList())
        override val status = MutableStateFlow(TorrentServiceStatus())
        val calls = mutableListOf<String>()
        var previewResult: Result<TorrentPreview> = Result.failure(AppException(AppError.Offline))

        override suspend fun play(source: String, media: Media, episode: Int, fileIndex: Int?): Result<TorrentPlayback> =
            Result.failure(AppException(AppError.Offline))
        override suspend fun playbackEnded(torrentId: TorrentId, completed: Boolean) {}
        override suspend fun addDownload(source: String, media: Media, episode: Int?): Result<TorrentId> = Result.success("x")
        override fun stats(id: TorrentId): Flow<TorrentStats> = flowOf()
        override suspend fun files(id: TorrentId): List<TorrentFile> = emptyList()
        override fun peers(id: TorrentId): Flow<List<TorrentPeer>> = flowOf(emptyList())
        override suspend fun trackers(id: TorrentId): List<TorrentTracker> = emptyList()
        override suspend fun remove(id: TorrentId, deleteFiles: Boolean) {}
        override suspend fun pause(id: TorrentId) {}
        override suspend fun resume(id: TorrentId) {}
        override suspend fun setPersist(id: TorrentId, persist: Boolean) {}
        override suspend fun setSeeding(id: TorrentId, seed: Boolean) {}
        override suspend fun downloadFile(id: TorrentId, fileIndex: Int) {}
        override suspend fun seekMarks(id: TorrentId, fileIndex: Int): List<ClosedFloatingPointRange<Double>> = emptyList()
        override suspend fun exportToDownloads(id: TorrentId, fileIndex: Int): Result<String> = Result.success("x")
        override suspend fun storageUsage(): TorrentStorage = TorrentStorage(0, 0, StorageLocation.AppSpecific)
        override suspend fun preview(source: String): Result<TorrentPreview> {
            calls += "preview:$source"
            return previewResult
        }
        /** Reported before the preview answers; the answer waits for [hold] when set. */
        var progress: List<TorrentPreviewProgress> = emptyList()
        var hold: CompletableDeferred<Unit>? = null
        override suspend fun preview(source: String, onProgress: (TorrentPreviewProgress) -> Unit): Result<TorrentPreview> {
            progress.forEach(onProgress)
            hold?.await()
            return preview(source)
        }
        override suspend fun addManual(source: String, media: Media?, alignment: EpisodeAlignment?, persist: Boolean): Result<TorrentId> {
            calls += "add:$source"
            return Result.success("h")
        }
        override suspend fun addManualFiles(source: String, media: Media?, alignment: EpisodeAlignment?, persist: Boolean, files: Set<Int>): Result<TorrentId> {
            calls += "addFiles:$source:${files.sorted()}"
            return Result.success("h")
        }
    }

    private val hash = "0123456789abcdef0123456789abcdef01234567"
    private val pack = TorrentPreview(
        id = hash, name = "Show S01", sizeBytes = 300,
        files = listOf(TorrentPreviewFile(0, "Show S01/Show - 01.mkv", 100), TorrentPreviewFile(1, "Show S01/Show - 02.mkv", 100), TorrentPreviewFile(2, "Show S01/info.nfo", 100)),
    )

    @Test
    fun theFileListComesFirstAndOnlyTheChosenFilesAreAdded() = runTest {
        val repo = FakeTorrents().apply { previewResult = Result.success(pack) }
        val vm = AddTorrentViewModel(repo)
        vm.setSource(hash.uppercase())
        vm.loadPreview()
        assertEquals(listOf("preview:$hash"), repo.calls)
        assertIs<LoadState.Ok<TorrentPreview>>(vm.state.value.preview)
        assertEquals(setOf(0, 1, 2), vm.state.value.chosenFiles) // all at first
        vm.toggleFile(2)
        vm.add()
        assertEquals("addFiles:$hash:[0, 1]", repo.calls.last())
    }

    @Test
    fun everyFileChosenAddsTheWholeTorrentAndNoneCannotBeAdded() = runTest {
        val repo = FakeTorrents().apply { previewResult = Result.success(pack) }
        val vm = AddTorrentViewModel(repo)
        vm.setSource(hash)
        vm.loadPreview()
        vm.setChosenFiles(emptySet())
        assertFalse(vm.state.value.canAdd)
        vm.add()
        assertFalse(repo.calls.any { it.startsWith("add") })
        vm.setChosenFiles(setOf(0, 1, 2))
        vm.add()
        assertEquals("add:$hash", repo.calls.last())
    }

    @Test
    fun withoutAFileList_asOnAndroid_theWholeTorrentIsAdded() = runTest {
        val repo = FakeTorrents()
        val vm = AddTorrentViewModel(repo)
        vm.setSource(hash)
        assertNull(vm.state.value.preview)
        assertTrue(vm.state.value.canAdd)
        vm.add()
        assertEquals(listOf("add:$hash"), repo.calls)
    }

    @Test
    fun theWaitForTheMetadataIsShownUntilTheFilesArrive() = runTest {
        val repo = FakeTorrents().apply {
            previewResult = Result.success(pack)
            progress = listOf(TorrentPreviewProgress(0, 90, 0), TorrentPreviewProgress(3, 90, 2))
            hold = CompletableDeferred()
        }
        val vm = AddTorrentViewModel(repo)
        vm.setSource("magnet:?xt=urn:btih:$hash&dn=x")
        vm.loadPreview()
        assertIs<LoadState.Loading>(vm.state.value.preview)
        assertEquals(TorrentPreviewProgress(3, 90, 2), vm.state.value.previewProgress) // the latest report
        repo.hold!!.complete(Unit)
        assertIs<LoadState.Ok<TorrentPreview>>(vm.state.value.preview)
        assertNull(vm.state.value.previewProgress)
    }

    @Test
    fun aPreviewThatRunsOutOfTimeKeepsItsLastReportAndCanBeRetried() = runTest {
        val repo = FakeTorrents().apply {
            previewResult = Result.failure(AppException(AppError.Unknown(TORRENT_PREVIEW_TIMEOUT_DETAIL)))
            progress = listOf(TorrentPreviewProgress(89, 90, 0))
        }
        val vm = AddTorrentViewModel(repo)
        vm.setSource(hash)
        vm.loadPreview()
        val err = assertIs<LoadState.Err>(vm.state.value.preview)
        assertEquals(AppError.Unknown(TORRENT_PREVIEW_TIMEOUT_DETAIL), err.cause)
        assertEquals(0, vm.state.value.previewProgress?.peers) // "no peers yet" when it gave up
        repo.previewResult = Result.success(pack)
        repo.progress = emptyList()
        vm.loadPreview()
        assertIs<LoadState.Ok<TorrentPreview>>(vm.state.value.preview)
        // a report of an older source never lands on a new one
        vm.setSource("magnet:?xt=urn:btih:$hash&dn=y")
        assertNull(vm.state.value.previewProgress)
    }

    @Test
    fun aFailedPreviewCanBeRetriedAndANewSourceForgetsTheList() = runTest {
        val repo = FakeTorrents()
        val vm = AddTorrentViewModel(repo)
        vm.setSource(hash)
        vm.loadPreview()
        assertIs<LoadState.Err>(vm.state.value.preview)
        // a failed list does not block adding the whole torrent
        assertTrue(vm.state.value.canAdd)
        repo.previewResult = Result.success(pack)
        vm.loadPreview()
        assertIs<LoadState.Ok<TorrentPreview>>(vm.state.value.preview)
        vm.setSource("magnet:?xt=urn:btih:$hash&dn=x")
        assertNull(vm.state.value.preview)
        assertTrue(vm.state.value.chosenFiles.isEmpty())
    }
}
