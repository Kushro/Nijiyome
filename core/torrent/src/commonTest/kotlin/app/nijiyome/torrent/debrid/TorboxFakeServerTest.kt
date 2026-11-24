// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent.debrid

import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.AppLog
import app.nijiyome.model.LogSink
import app.nijiyome.model.Quality
import app.nijiyome.model.SecretKeys
import app.nijiyome.model.SecretStore
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.TorrentInfo
import app.nijiyome.model.torrent.DebridProblem
import app.nijiyome.model.torrent.DebridService
import app.nijiyome.model.torrent.DebridSettings
import app.nijiyome.model.torrent.EpisodeAlignment
import app.nijiyome.model.torrent.debridProblemOf
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 1.1 (F7): [DebridRepositoryImpl] + [TorboxClient] against a local fake TorBox (mockwebserver3, no internet). The fake follows the
 * public API (api.torbox.app/openapi.json, SDK models): envelope `{success, error, detail, data}`, Bearer auth, `token` in the query
 * of `requestdl`. Shapes that need a real account to confirm are listed in the F7 report.
 */
class TorboxFakeServerTest {
    private val server = MockWebServer()
    private val key = "tb-secret-key-1234567890" // gitleaks:allow (made-up value for a local fake server)
    private var now = 1_000_000L
    private val slept = Collections.synchronizedList(mutableListOf<Long>())
    private val logs = Collections.synchronizedList(mutableListOf<String>())
    private var settings = DebridSettings()

    /** The fake's state. */
    private inner class Fake {
        val cached = ConcurrentHashMap.newKeySet<String>()
        /** torrent id -> (hash, files) on the account. */
        val account = ConcurrentHashMap<String, Pair<String, List<Triple<String, String, Long>>>>()
        val files = ConcurrentHashMap<String, List<Triple<String, String, Long>>>()
        val nextId = AtomicInteger(100)
        val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
        var createError: String? = null
        var tooMany = ConcurrentHashMap<String, AtomicInteger>()
        var httpLinks = false
        var checkDelayMs = 0L
        var linkSerial = AtomicInteger()
        /** 1.1 review: a 302 answer for every requestdl / checkcached ([redirectTo] = its Location). */
        var redirectRequestdl: String? = null
        var redirectCheck: String? = null
        var requestdlError: String? = null
        var linkOverride: String? = null
        var hugeMylist = false
        /** Files of a torrent added again get new ids (same names). */
        var renumberOnCreate = false

        fun count(path: String) = requests.count { it.url.encodedPath == "/v1/api/$path" }

        fun dispatch(r: RecordedRequest): MockResponse {
            requests += r
            val path = r.url.encodedPath.removePrefix("/v1/api/")
            tooMany[path]?.let { left ->
                if (left.getAndDecrement() > 0) return MockResponse.Builder().code(429).addHeader("Retry-After", "1").body("").build()
            }
            if (path == "leak") return MockResponse.Builder().code(200).body("leaked").build()
            if (path == "torrents/requestdl") {
                redirectRequestdl?.let { return MockResponse.Builder().code(302).addHeader("Location", it).build() }
                requestdlError?.let { return err(400, it) }
                linkOverride?.let { return ok("\"$it\"") }
                // the token in the query, never a Bearer header on this call
                if (r.url.queryParameter("token") != key) return err(403, "BAD_TOKEN")
                val id = r.url.queryParameter("torrent_id")
                val t = account[id] ?: return err(400, "ITEM_NOT_FOUND")
                val file = r.url.queryParameter("file_id")
                if (t.second.none { it.first == file }) return err(400, "ITEM_NOT_FOUND")
                val scheme = if (httpLinks) "http" else "https"
                return ok("\"$scheme://cdn.example/dl/$id/$file?sig=${linkSerial.incrementAndGet()}\"")
            }
            if (r.headers["Authorization"] != "Bearer $key") return err(403, "BAD_TOKEN")
            return when (path) {
                "user/me" -> ok("""{"email":"neo@example.org","plan":2,"premium_expires_at":"2026-12-31T00:00:00Z"}""")
                "torrents/checkcached" -> {
                    redirectCheck?.let { return MockResponse.Builder().code(302).addHeader("Location", it).build() }
                    if (checkDelayMs > 0) Thread.sleep(checkDelayMs)
                    val hashes = r.url.queryParameter("hash").orEmpty().split(',')
                    ok("{" + hashes.filter { it in cached }.joinToString(",") { "\"$it\":{\"name\":\"x\",\"size\":1,\"hash\":\"$it\"}" } + "}")
                }
                "torrents/createtorrent" -> {
                    createError?.let { return err(400, it) }
                    val body = r.body?.utf8().orEmpty()
                    val hash = Regex("btih:([0-9a-f]{40})").find(body)?.groupValues?.get(1) ?: return err(400, "BOZO_TORRENT")
                    val onlyCached = Regex("name=\"add_only_if_cached\"\\s+(true|false)").find(body)?.groupValues?.get(1) == "true"
                    account.entries.firstOrNull { it.value.first == hash }?.let { return ok("""{"torrent_id":${it.key},"hash":"$hash"}""") }
                    if (onlyCached && hash !in cached) return err(400, "ITEM_NOT_FOUND")
                    val id = nextId.getAndIncrement().toString()
                    val list = files[hash] ?: listOf(Triple("0", "Show/Show - 01.mkv", 1_000L))
                    account[id] = hash to (if (renumberOnCreate) list.map { (fid, n, sz) -> Triple((fid.toInt() + 50).toString(), n, sz) } else list)
                    ok("""{"torrent_id":$id,"hash":"$hash","auth_id":"a"}""")
                }
                "torrents/mylist" -> {
                    val id = r.url.queryParameter("id")
                    if (id != null) {
                        if (hugeMylist) return MockResponse.Builder().code(200).body("{\"success\":true,\"data\":\"" + "x".repeat(9 * 1024 * 1024) + "\"}").build()
                        val t = account[id] ?: return err(400, "ITEM_NOT_FOUND")
                        ok(item(id, t))
                    } else {
                        ok("[" + account.entries.joinToString(",") { item(it.key, it.value) } + "]")
                    }
                }
                "torrents/controltorrent" -> {
                    val id = Regex("\"torrent_id\":\"?(\\d+)").find(r.body?.utf8().orEmpty())?.groupValues?.get(1)
                    account.remove(id)
                    ok("null")
                }
                else -> err(404, "ENDPOINT_NOT_FOUND")
            }
        }

        private fun item(id: String, t: Pair<String, List<Triple<String, String, Long>>>) =
            """{"id":$id,"hash":"${t.first}","name":"Show","download_present":true,"download_state":"cached","files":[""" +
                t.second.joinToString(",") { (fid, name, size) -> """{"id":$fid,"name":"$name","short_name":"${name.substringAfterLast('/')}","size":$size}""" } +
                "]}"

        private fun ok(data: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
            .body("""{"success":true,"error":null,"detail":"ok","data":$data}""").build()

        private fun err(code: Int, error: String) = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json")
            .body("""{"success":false,"error":"$error","detail":"$error","data":null}""").build()
    }

    private val fake = Fake()

    private class MemorySecrets : SecretStore {
        val map = ConcurrentHashMap<String, String>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override suspend fun clear() = map.clear()
    }

    private val secrets = MemorySecrets()

    @BeforeTest
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = fake.dispatch(request)
        }
        server.start()
        AppLog.sink = LogSink { _, tag, message, t -> logs += "$tag $message ${t?.toString().orEmpty()} ${t?.cause?.toString().orEmpty()}" }
    }

    @AfterTest
    fun tearDown() {
        AppLog.sink = LogSink { _, _, _, _ -> }
        server.close()
    }

    private fun repo(budgetMs: Long = 5_000, ttlMs: Long = 600_000) = DebridRepositoryImpl(
        client = OkHttpClient(),
        secrets = secrets,
        clock = { now },
        scope = null,
        pollDelayMs = 1,
        debridSettings = { settings },
        torboxBaseUrl = server.url("/v1/api").toString(),
        rateLimitSleep = { ms -> slept += ms },
        cacheTtlMs = ttlMs,
        markBudgetMs = budgetMs,
    )

    private suspend fun linked(): DebridRepositoryImpl = repo().also { it.link(DebridService.TORBOX, key).getOrThrow() }

    private fun hash(c: Char) = c.toString().repeat(40)

    private fun torrent(h: String, seeders: Int = 50) = SourceCandidate(
        extId = "nyaa", extName = "Nyaa", label = "t-$h", kind = SourceKind.TORRENT, quality = Quality.P1080, reliability = 0.6f,
        torrent = TorrentInfo(magnet = "magnet:?xt=urn:btih:$h", infoHash = h, seeders = seeders, leechers = 1, sizeBytes = 1_000),
    )

    private fun problem(r: Result<*>): DebridProblem = debridProblemOf((r.exceptionOrNull() as? AppException)?.error)

    // ------------------------------------------------------------------------------------------------ account

    @Test
    fun linkReadsTheEnvelopeAndStoresTheKey() = runBlocking {
        val r = repo()
        val account = r.link(DebridService.TORBOX, key).getOrThrow()
        assertEquals("neo@example.org", account.username)
        assertEquals(key, secrets.map[SecretKeys.debridApiKey("torbox")])
        assertEquals("Bearer $key", fake.requests.single().headers["Authorization"])
        assertEquals("false", fake.requests.single().url.queryParameter("settings"))
        // a wrong key: BAD_TOKEN envelope (and 403) = invalid key, nothing stored
        val bad = repo().link(DebridService.TORBOX, "wrong-key")
        assertEquals(DebridProblem.INVALID_KEY, problem(bad))
        assertEquals(AppError.Unknown("debrid:invalid_key"), (bad.exceptionOrNull() as AppException).error)
        // "Probar la clave" on the linked account
        assertEquals("neo@example.org", r.recheck(DebridService.TORBOX).getOrThrow().username)
    }

    // ------------------------------------------------------------------------------------------------ availability

    @Test
    fun cacheLookupIsBatchedRememberedAndBounded() = runBlocking {
        val r = linked()
        val hashes = (0 until 120).map { i -> "%040x".format(i + 1) }
        fake.cached += hashes.filterIndexed { i, _ -> i % 3 == 0 }
        val first = r.cached(hashes)
        // at most 100 new hashes per call, in batches of 50
        assertEquals(2, fake.count("torrents/checkcached"))
        val req = fake.requests.first { it.url.encodedPath.endsWith("checkcached") }
        assertEquals("object", req.url.queryParameter("format"))
        assertEquals("false", req.url.queryParameter("list_files"))
        assertEquals(50, req.url.queryParameter("hash")!!.split(',').size)
        assertEquals(hashes.take(100).filterIndexed { i, _ -> i % 3 == 0 }.toSet(), first.keys)
        // the next call only asks the 20 left; answers within the TTL never go to the server again
        val second = r.cached(hashes)
        assertEquals(3, fake.count("torrents/checkcached"))
        assertEquals(hashes.filterIndexed { i, _ -> i % 3 == 0 }.toSet(), second.keys)
        r.cached(hashes)
        assertEquals(3, fake.count("torrents/checkcached"))
        // past the TTL they are asked again
        now += 11 * 60_000L
        r.cached(hashes.take(10))
        assertEquals(4, fake.count("torrents/checkcached"))
    }

    @Test
    fun markCachedNeverHoldsTheListPastItsBudget() = runBlocking {
        val r = repo(budgetMs = 300).also { it.link(DebridService.TORBOX, key).getOrThrow() }
        fake.cached += hash('a')
        fake.checkDelayMs = 2_000
        val list = listOf(torrent(hash('a')))
        val t0 = System.nanoTime()
        val out = r.markCached(list)
        val tookMs = (System.nanoTime() - t0) / 1_000_000
        assertEquals(list, out)
        assertTrue(tookMs < 1_500, "took $tookMs ms")
        // fast answer: marked
        fake.checkDelayMs = 0
        val marked = repo().also { it.link(DebridService.TORBOX, key).getOrThrow() }.markCached(list)
        assertEquals(true, marked.single().debrid?.cached)
    }

    @Test
    fun disabledDebridTouchesNothing() = runBlocking {
        val r = linked()
        val before = fake.requests.size
        settings = DebridSettings(enabled = false)
        fake.cached += hash('a')
        assertEquals(emptyMap(), r.cached(listOf(hash('a'))))
        assertEquals(listOf(torrent(hash('a'))), r.markCached(listOf(torrent(hash('a')))))
        assertNull(r.instantStream(hash('a'), null, 1))
        assertEquals(before, fake.requests.size)
    }

    // ------------------------------------------------------------------------------------------------ resolve

    @Test
    fun cachedTorrentResolvesToAnHttpsLinkWithoutReadingTheWholeAccount() = runBlocking {
        val r = linked()
        val h = hash('c')
        fake.cached += h
        val resolved = r.resolveFile(DebridService.TORBOX, h, "magnet:?xt=urn:btih:$h", null, 1, listOf("Show")).getOrThrow()
        assertTrue(resolved.stream.url.startsWith("https://cdn.example/dl/"))
        assertEquals("TorBox", resolved.stream.provider)
        assertEquals("Show - 01.mkv", resolved.file.fileName)
        // createtorrent with add_only_if_cached=true; mylist only by id; never the full list
        val create = fake.requests.single { it.url.encodedPath.endsWith("createtorrent") }
        assertTrue(create.body!!.utf8().contains("add_only_if_cached"))
        assertTrue(Regex("name=\"add_only_if_cached\"\\s+true").containsMatchIn(create.body!!.utf8()))
        assertTrue(fake.requests.filter { it.url.encodedPath.endsWith("mylist") }.all { it.url.queryParameter("id") != null })
        // requestdl: token in the query, no Bearer header, redirect=false
        val dl = fake.requests.single { it.url.encodedPath.endsWith("requestdl") }
        assertNull(dl.headers["Authorization"])
        assertEquals("false", dl.url.queryParameter("redirect"))
        // a second play reuses the id this run learnt: no new createtorrent
        r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList()).getOrThrow()
        assertEquals(1, fake.count("torrents/createtorrent"))
    }

    @Test
    fun uncachedTorrentIsNotCachedAndLeavesNothingOnTheAccount() = runBlocking {
        val r = linked()
        val h = hash('d')
        val result = r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList())
        assertEquals(DebridProblem.NOT_CACHED, problem(result))
        assertTrue(fake.account.isEmpty())
        // the engine's own instant path does not try the same hash again right after
        assertTrue(r.failedRecently(h))
        val before = fake.requests.size
        assertNull(r.instantStream(h, null, 1))
        assertEquals(before, fake.requests.size)
    }

    @Test
    fun envelopeErrorsReachTheUserAsTheirReason() = runBlocking {
        val r = linked()
        val h = hash('e')
        fake.cached += h
        for ((code, expected) in listOf(
            "ACTIVE_LIMIT" to DebridProblem.ACTIVE_LIMIT,
            "DOWNLOAD_TOO_LARGE" to DebridProblem.TOO_LARGE,
            "PLAN_RESTRICTED_FEATURE" to DebridProblem.PLAN,
            "MONTHLY_LIMIT" to DebridProblem.MONTHLY_LIMIT,
            "COOLDOWN_LIMIT" to DebridProblem.RATE_LIMITED,
            "BAD_TOKEN" to DebridProblem.INVALID_KEY,
        )) {
            fake.createError = code
            val result = r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList())
            assertEquals(expected, problem(result), code)
        }
    }

    @Test
    fun rateLimitWaitsOnReadsButNeverRetriesCreate() = runBlocking {
        val r = linked()
        val h = hash('f')
        fake.cached += h
        // checkcached: two 429 then OK -> waited (Retry-After: 1 s) and answered
        fake.tooMany["torrents/checkcached"] = AtomicInteger(2)
        assertEquals(setOf(h), r.cached(listOf(h)).keys)
        assertEquals(listOf(1_000L, 1_000L), slept.toList())
        // createtorrent: one 429 -> rate limited, not sent again
        fake.tooMany["torrents/createtorrent"] = AtomicInteger(1)
        val result = r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList())
        assertEquals(DebridProblem.RATE_LIMITED, problem(result))
        assertEquals(1, fake.count("torrents/createtorrent"))
        // more than MAX_RATE_RETRIES 429 on a read -> rate limited
        fake.tooMany["torrents/checkcached"] = AtomicInteger(10)
        now += 60 * 60_000L
        assertEquals(emptyMap(), r.cached(listOf(h)))
    }

    @Test
    fun plainHttpLinksAreRefused() = runBlocking {
        val r = linked()
        val h = hash('9')
        fake.cached += h
        fake.httpLinks = true
        val result = r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList())
        assertEquals(DebridProblem.BAD_LINK, problem(result))
    }

    @Test
    fun linkIsRenewedEvenAfterTheTorrentLeftTheAccount() = runBlocking {
        val r = linked()
        val h = hash('7')
        fake.cached += h
        val first = r.resolveFile(DebridService.TORBOX, h, "magnet:?xt=urn:btih:$h", null, 1, emptyList()).getOrThrow()
        val again = r.refreshLink(first.file).getOrThrow()
        assertTrue(again.startsWith("https://") && again != first.stream.url)
        // removed from the account (cleanup on the web): the refresh adds it again (cached only) and asks once more
        fake.account.clear()
        val renewed = r.refreshLink(first.file).getOrThrow()
        assertTrue(renewed.startsWith("https://cdn.example/dl/"))
        assertEquals(1, fake.account.size)
        // a new run (nothing known) also works from the stored reference
        val fresh = linked()
        assertTrue(fresh.refreshLink(first.file).getOrThrow().startsWith("https://"))
    }

    // ------------------------------------------------------------------------------------------------ episode in packs

    private val pack = listOf(
        Triple("0", "[Grp] Show S2/Extras/[Grp] Show - NCOP 05 [1080p].mkv", 900_000_000L),
        Triple("1", "[Grp] Show S2/[Grp] Show S2 - 04 [1080p].mkv", 400_000_000L),
        Triple("2", "[Grp] Show S2/[Grp] Show S2 - 05 [1080p].mkv", 400_000_000L),
        Triple("3", "[Grp] Show S2/[Grp] Show S2 - 06 [1080p].mkv", 400_000_000L),
    )

    @Test
    fun packEpisodeUsesPickEpisodeFileNotTheBiggestMatch() = runBlocking {
        val r = linked()
        val h = hash('5')
        fake.cached += h
        fake.files[h] = pack
        // the classic regex alone would take the (bigger) NCOP "05"
        assertEquals("0", selectBestVideo(pack.map { RemoteFile(it.first, it.second.substringAfterLast('/'), it.third, it.second) }, 5)?.id)
        val resolved = r.resolveFile(DebridService.TORBOX, h, null, null, 5, listOf("Show Season 2", "Show")).getOrThrow()
        assertEquals("2", resolved.file.fileId)
        assertEquals("[Grp] Show S2 - 05 [1080p].mkv", resolved.file.fileName)
    }

    @Test
    fun manualAlignmentWinsOverTheNames() = runBlocking {
        val r = linked()
        val h = hash('6')
        fake.cached += h
        fake.files[h] = pack
        // the user aligned this pack: file "04" is episode 1, "05" episode 2, ...
        r.alignmentOf = { id -> if (id == h) EpisodeAlignment(firstEpisode = 1, offset = -3) else null }
        val resolved = r.resolveFile(DebridService.TORBOX, h, null, null, 2, listOf("Show")).getOrThrow()
        assertEquals("2", resolved.file.fileId)
    }

    @Test
    fun regexIsOnlyTheLastResort() {
        // no episode numbers the release parser understands -> the regex / largest video decides
        val files = listOf(RemoteFile("a", "clip.mkv", 10), RemoteFile("b", "movie.mkv", 20), RemoteFile("c", "notes.txt", 99))
        assertEquals("b", DebridRepositoryImpl.chooseDebridFile(files, null, null, emptyList(), null)?.id)
        assertEquals("a", DebridRepositoryImpl.chooseDebridFile(files, 0, 3, emptyList(), null)?.id)
    }

    // ------------------------------------------------------------------------------------------------ privacy

    @Test
    fun theKeyNeverReachesLogsOrErrors() = runBlocking {
        val r = linked()
        val h = hash('4')
        fake.cached += h
        val errors = mutableListOf<Throwable?>()
        errors += r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList()).exceptionOrNull()
        fake.httpLinks = true
        errors += r.resolveFile(DebridService.TORBOX, hash('3').also { fake.cached += it }, null, null, 1, emptyList()).exceptionOrNull()
        fake.createError = "ACTIVE_LIMIT"
        errors += r.resolveFile(DebridService.TORBOX, hash('2').also { fake.cached += it }, null, null, 1, emptyList()).exceptionOrNull()
        server.close() // offline: OkHttp exceptions name the URL, the repository must not pass them on
        errors += r.resolveFile(DebridService.TORBOX, hash('1').also { fake.cached += it }, null, null, 1, emptyList()).exceptionOrNull()
        r.cached(listOf(hash('8')))
        val texts = logs + errors.filterNotNull().flatMap { e -> generateSequence(e) { it.cause }.map { it.toString() }.toList() }
        assertTrue(errors.filterNotNull().size >= 3)
        assertFalse(texts.any { key in it }, texts.joinToString("\n"))
        assertFalse(texts.any { "token=" in it }, texts.joinToString("\n"))
        assertNotNull(errors.last())
        assertEquals(AppError.Offline, (errors.last() as AppException).error)
    }

    // ------------------------------------------------------------------------------------------------ 1.1 review

    @Test
    fun redirectsAreNeverFollowedSoTheKeyCannotLeak() = runBlocking {
        val r = linked()
        val h = hash('a')
        fake.cached += h
        // requestdl (key in its query) answers 302 to plain http: not followed, the target never sees a request
        fake.redirectRequestdl = server.url("/v1/api/leak").toString().replace("https://", "http://")
        val result = r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList())
        assertTrue(result.isFailure)
        assertEquals(0, fake.count("leak"))
        // any other endpoint: same (a 302 is an error, not a hop)
        fake.redirectCheck = server.url("/v1/api/leak").toString()
        now += 60 * 60_000L
        assertEquals(emptyMap(), r.cached(listOf(hash('b'))))
        assertEquals(0, fake.count("leak"))
    }

    @Test
    fun linksMustBeWellFormedHttpsWithoutUserInfo() = runBlocking {
        assertTrue(isSafeHttpsLink("https://cdn.example/dl/1?sig=2"))
        for (bad in listOf("http://cdn.example/x", "https://user:pass@cdn.example/x", "https://", "https:/cdn.example/x", "javascript:alert(1)", "HTTPS//x")) {
            assertFalse(isSafeHttpsLink(bad), bad)
        }
        val r = linked()
        val h = hash('c')
        fake.cached += h
        fake.linkOverride = "https://u:p@cdn.example/dl/1"
        assertEquals(DebridProblem.BAD_LINK, problem(r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList())))
    }

    @Test
    fun aHugeAnswerIsRefusedInsteadOfFillingTheHeap() = runBlocking {
        val r = linked()
        val h = hash('d')
        fake.cached += h
        fake.hugeMylist = true
        val result = r.resolveFile(DebridService.TORBOX, h, null, null, 1, emptyList())
        assertTrue(result.isFailure)
        assertEquals(AppError.Offline, (result.exceptionOrNull() as AppException).error)
    }

    @Test
    fun onlyTheInfoHashLeavesTheDevice() = runBlocking {
        val r = linked()
        val h = hash('e')
        fake.cached += h
        val magnet = "magnet:?xt=urn:btih:$h&dn=My.Show.S01E01&tr=https%3A%2F%2Ftracker.private%2Fannounce%3Fpasskey%3DSECRET&ws=https%3A%2F%2Fseed.example%2F"
        val resolved = r.resolveFile(DebridService.TORBOX, h, magnet, null, 1, emptyList()).getOrThrow()
        val body = fake.requests.single { it.url.encodedPath.endsWith("createtorrent") }.body!!.utf8()
        assertTrue(body.contains("magnet:?xt=urn:btih:$h"))
        for (leak in listOf("dn=", "tr=", "passkey", "SECRET", "ws=")) assertFalse(body.contains(leak), leak)
        assertNull(resolved.file.magnet)
    }

    @Test
    fun anEpisodeMissingFromThePackIsNoFileNotTheBiggestVideo() = runBlocking {
        val r = linked()
        val h = hash('f')
        fake.cached += h
        fake.files[h] = pack
        val result = r.resolveFile(DebridService.TORBOX, h, null, null, 13, listOf("Show Season 2", "Show"))
        assertEquals(DebridProblem.NO_FILE, problem(result))
        assertNull(DebridRepositoryImpl.chooseDebridFile(pack.map { RemoteFile(it.first, it.second.substringAfterLast('/'), it.third, it.second) }, null, 13, emptyList(), null))
    }

    @Test
    fun staleAnswersPastTheBatchLimitAreNotTrusted() = runBlocking {
        val r = linked()
        val hashes = (0 until 120).map { i -> "%040x".format(i + 1000) }
        fake.cached += hashes
        r.cached(hashes)
        r.cached(hashes)
        assertEquals(120, r.cached(hashes).size)
        // past the TTL: only the 100 asked again count; the 20 left out are unknown until asked
        now += 11 * 60_000L
        fake.cached.clear()
        fake.cached += hashes
        assertEquals(100, r.cached(hashes).size)
    }

    @Test
    fun refreshOnlyReAddsWhenTheTorrentLeftAndMatchesTheFileByName() = runBlocking {
        val r = linked()
        val h = hash('9')
        fake.cached += h
        fake.files[h] = pack
        val first = r.resolveFile(DebridService.TORBOX, h, null, null, 5, listOf("Show Season 2")).getOrThrow()
        // a plan / limit refusal: the reason, and no new createtorrent
        fake.requestdlError = "ACTIVE_LIMIT"
        assertEquals(DebridProblem.ACTIVE_LIMIT, problem(r.refreshLink(first.file)))
        assertEquals(1, fake.count("torrents/createtorrent"))
        // gone from the account and added again with other file ids: found by name
        fake.requestdlError = null
        fake.account.clear()
        fake.renumberOnCreate = true
        val renewed = r.refreshLink(first.file).getOrThrow()
        assertTrue(renewed.contains("/52?"), renewed)
    }
}
