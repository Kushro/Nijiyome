// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.Container
import app.nijiyome.model.DownloadItem
import app.nijiyome.model.DownloadState
import app.nijiyome.model.MediaRef
import app.nijiyome.model.StreamSource
import app.nijiyome.model.torrent.DebridFileRef
import app.nijiyome.model.torrent.DebridService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 1.1 (F7): the debrid reference of a download record (Android and desktop share the store) and the Media3 cache key. */
class DebridDownloadStoreTest {
    private val item = DownloadItem(
        id = "7?ep=1", ref = MediaRef(anilistId = 7), title = "Seven", episode = 1, extId = "torrent", sourceLabel = "TorBox",
        container = Container.MP4, state = DownloadState.PAUSED, createdAtMs = 1,
    )
    private val stream = StreamSource("https://cdn.example/dl/1", emptyMap(), Container.PROGRESSIVE, null, "TorBox", "torrent")
    private val ref = DebridFileRef(DebridService.TORBOX, "ABC".padEnd(40, 'd'), "4", "Seven - 01.mkv", "magnet:?xt=urn:btih:x")

    @Test
    fun recordsKeepTheDebridFileAndOldRecordsStillLoad() {
        val dir = Files.createTempDirectory("nijiyome-dl-store").toFile()
        try {
            val store = DownloadStore(dir)
            store.saveDownloads(listOf(StoredDownload(item, stream, stream.url, debrid = ref)))
            assertEquals(ref, store.downloads().single().debrid)
            // a record written before 1.1 (no "debrid" key) loads with no reference
            val old = DownloadJson.encodeToString(StoredDownload.serializer(), StoredDownload(item, stream, stream.url))
                .replace(",\"debrid\":null", "")
            assertNull(DownloadJson.decodeFromString(StoredDownload.serializer(), old).debrid)
        } finally {
            dir.deleteRecursively() // temp dir created above
        }
    }

    @Test
    fun cacheKeyIsStableAcrossRenewedLinks() {
        assertEquals("debrid:torbox:${"abc".padEnd(40, 'd')}:4", debridCacheKey(ref))
        assertEquals(debridCacheKey(ref), debridCacheKey(ref.copy(fileName = "other name")))
    }

    @Test
    fun refreshErrorsKeepOnlyTheCode() {
        assertEquals("debrid:torbox:ACTIVE_LIMIT", debridErrorCode(AppException(AppError.Unknown("debrid:torbox:ACTIVE_LIMIT"))))
        assertEquals("debrid:offline", debridErrorCode(AppException(AppError.Offline)))
        assertEquals("debrid:refresh_failed", debridErrorCode(IllegalStateException("https://secret.example/?token=x")))
    }

    // ---- 1.1 review ----

    private class Refresher(val answer: Result<String>) : app.nijiyome.model.torrent.DebridRepository {
        override val accounts = kotlinx.coroutines.flow.MutableStateFlow(emptyList<app.nijiyome.model.torrent.DebridAccount>())
        override suspend fun link(service: DebridService, apiKey: String) = error("unused")
        override suspend fun unlink(service: DebridService) = Unit
        override suspend fun cached(infoHashes: List<String>) = emptyMap<String, Set<DebridService>>()
        override suspend fun files(service: DebridService, infoHash: String, magnet: String?) = error("unused")
        override suspend fun refreshLink(file: DebridFileRef) = answer
    }

    @Test
    fun renewalReportsItsOwnOutcome() = kotlinx.coroutines.runBlocking {
        assertEquals(DebridRenewal.Ok("https://cdn.example/2"), renewDebridLink(ref, Refresher(Result.success("https://cdn.example/2"))))
        assertEquals(
            DebridRenewal.Failed("debrid:torbox:ACTIVE_LIMIT"),
            renewDebridLink(ref, Refresher(Result.failure(AppException(AppError.Unknown("debrid:torbox:ACTIVE_LIMIT"))))),
        )
        assertEquals(DebridRenewal.Failed("debrid:not_linked:torbox"), renewDebridLink(ref, null))
    }

    @Test
    fun storedErrorsKeepDebridCodesBareAndNoUrlsForDebridDownloads() {
        assertEquals("debrid:torbox:ACTIVE_LIMIT", storedDownloadError("Unknown(msg=debrid:torbox:ACTIVE_LIMIT)", debrid = true, fallback = "failed"))
        assertEquals("http 403 <url>", storedDownloadError("http 403 https://cdn.torbox.example/dl/1?sig=abc", debrid = true, fallback = "failed"))
        // other downloads: as before
        assertEquals("http 403 https://x.example/a", storedDownloadError("http 403 https://x.example/a", debrid = false, fallback = "failed"))
        assertEquals("failed", storedDownloadError(null, debrid = true, fallback = "failed"))
    }

    @Test
    fun onlyHttpsRequestsToDebridHostsAvoidDowngrades() {
        val hosts = setOf("cdn.torbox.example")
        assertEquals(true, httpsOnlyRequest(okhttp3.HttpUrl.Builder().scheme("https").host("cdn.torbox.example").build(), hosts))
        assertEquals(false, httpsOnlyRequest(okhttp3.HttpUrl.Builder().scheme("https").host("other.example").build(), hosts))
        assertEquals(false, httpsOnlyRequest(okhttp3.HttpUrl.Builder().scheme("http").host("cdn.torbox.example").build(), hosts))
    }
}
