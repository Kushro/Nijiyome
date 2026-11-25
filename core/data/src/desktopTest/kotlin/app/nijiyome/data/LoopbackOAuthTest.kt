// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.database.NijiyomeDatabase
import app.nijiyome.database.open
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.ListProvider
import app.nijiyome.model.SecretKeys
import app.nijiyome.model.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop login end to end, with nothing leaving the machine: a fake authorization server (MockWebServer on 127.0.0.1) plays
 * MyAnimeList's authorize page (it redirects to the loopback callback), its token endpoint and `users/@me`; a "browser" (OkHttp
 * following redirects) opens the authorize URL. The shared ListsRepositoryImpl checks the state, exchanges the code with the PKCE
 * verifier and keeps the token in the secret store.
 */
class LoopbackOAuthTest {
    private lateinit var auth: MockWebServer
    private lateinit var dir: File
    private lateinit var db: NijiyomeDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var forgeState: String? = null
    private val tokenBodies = mutableListOf<String>()
    private val port = freePort()

    private class MemorySecrets : SecretStore {
        val map = mutableMapOf<String, String>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override suspend fun clear() = map.clear()
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("oauth-test").toFile()
        db = NijiyomeDatabase.open(File(dir, "test.db"))
        auth = MockWebServer()
        auth.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path.endsWith("/authorize") -> {
                        val q = request.url
                        val state = forgeState ?: q.queryParameter("state")
                        val back = "${q.queryParameter("redirect_uri")}?code=CODE-1&state=$state"
                        MockResponse.Builder().code(302).addHeader("Location", back).build()
                    }
                    path.endsWith("/token") -> {
                        tokenBodies += request.body?.utf8().orEmpty()
                        MockResponse.Builder().addHeader("Content-Type", "application/json")
                            .body("""{"access_token":"AT-1","refresh_token":"RT-1","expires_in":3600}""").build()
                    }
                    path.endsWith("/users/@me") -> MockResponse.Builder().addHeader("Content-Type", "application/json")
                        .body("""{"id":42,"name":"tester","picture":null}""").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        auth.start()
    }

    @AfterTest
    fun tearDown() {
        runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
        auth.close()
        db.close()
        dir.deleteRecursively()
    }

    private fun repo(secrets: SecretStore) = ListsRepositoryImpl(
        dao = db.lists(),
        secrets = secrets,
        anilist = AniListListClient(OkHttpClient(), auth.url("/anilist").toString()),
        mal = MalListClient(OkHttpClient(), api = auth.url("/mal").toString().trimEnd('/'), tokenUrl = auth.url("/mal/token").toString()),
        clientIds = { "client-id" },
        scope = scope,
        syncOnStart = false,
        redirectUri = { LoopbackOAuth.redirectUri(port) },
    )

    /** The authorize URL of `beginLink`, pointed at the fake server (same query: client, state, PKCE challenge, redirect). */
    private fun onFakeServer(url: String): String = auth.url("/mal/authorize").toString() + "?" + url.substringAfter('?')

    private fun browse(url: String): Boolean = runCatching {
        OkHttpClient.Builder().followRedirects(true).build().newCall(Request.Builder().url(onFakeServer(url)).build()).execute().use { it.code == 200 }
    }.getOrDefault(false)

    @Test
    fun theLoopbackLoginLinksTheAccountAndStoresTheToken() = runBlocking {
        val secrets = MemorySecrets()
        val lists = repo(secrets)
        val redirects = PendingOAuthRedirect()
        val launcher = DesktopOAuthLauncher(redirects, LoopbackOAuthServer(port = port, timeoutMs = 20_000), browse = ::browse)

        val url = lists.beginLink(ListProvider.MAL).getOrThrow()
        assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A$port%2Fcallback"), url)
        assertTrue(launcher.open(url))
        val redirect = withTimeout(10_000) { redirects.link.filterNotNull().first() }
        assertTrue(redirect.startsWith("nijiyome://oauth/mal?code=CODE-1&state="), redirect)

        val account = lists.completeLink(redirect).getOrThrow()
        assertEquals("tester", account.username)
        assertTrue(secrets.map[SecretKeys.listToken("mal")].orEmpty().contains("AT-1"))
        // The exchange sent the loopback redirect and the PKCE verifier.
        val form = tokenBodies.single()
        assertTrue(form.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A$port%2Fcallback"), form)
        assertTrue(form.contains("code_verifier="), form)
        // The listener is gone once the callback arrived.
        assertFalse(portInUse(port))
    }

    /**
     * Hallazgo 4 de la revisión: another page on the machine hits the loopback callback while the user signs in (a wrong state, a
     * token or code without state, an `error` without state). None of them reaches the repository or closes the listener: the
     * user's own callback still completes the login, with the user's code.
     */
    @Test
    fun forgedCallbacksNeitherEndNorHijackTheLogin() = runBlocking {
        val secrets = MemorySecrets()
        val lists = repo(secrets)
        val redirects = PendingOAuthRedirect()
        val url = lists.beginLink(ListProvider.MAL).getOrThrow()
        var opened = false
        val holdBrowser = DesktopOAuthLauncher(redirects, LoopbackOAuthServer(port = port, timeoutMs = 20_000)) { opened = true; true }
        assertTrue(holdBrowser.open(url))
        assertTrue(opened)
        val client = OkHttpClient()
        for (forged in listOf("code=EVIL&state=not-the-state", "code=EVIL", "access_token=EVIL&token_type=Bearer", "error=access_denied")) {
            client.newCall(Request.Builder().url("http://127.0.0.1:$port/callback?$forged").build()).execute().use { assertEquals(200, it.code) }
        }
        assertNull(redirects.link.value, "nothing reached the repository")
        assertTrue(portInUse(port), "the login's listener is still open")
        // The user's real callback (the fake authorization server redirects with the right state).
        assertTrue(browse(url))
        val redirect = withTimeout(10_000) { redirects.link.filterNotNull().first() }
        assertTrue(redirect.startsWith("nijiyome://oauth/mal?code=CODE-1&state="), redirect)
        assertEquals("tester", lists.completeLink(redirect).getOrThrow().username)
        assertTrue(tokenBodies.single().contains("code=CODE-1"), "only the user's code was exchanged")
        holdBrowser.close()
    }

    @Test
    fun classifyRefusesWrongStatesAndForwardsTheFragmentPage() {
        assertEquals(LoopbackOAuth.Outcome.Ignore, LoopbackOAuth.classify("/favicon.ico", ListProvider.ANILIST, "S"))
        assertEquals(LoopbackOAuth.Outcome.Forward, LoopbackOAuth.classify("/callback", ListProvider.ANILIST, "S"))
        val ok = LoopbackOAuth.classify("/callback?access_token=T&token_type=Bearer&expires_in=10&state=S", ListProvider.ANILIST, "S")
        assertEquals(LoopbackOAuth.Outcome.Done("nijiyome://oauth/anilist?access_token=T&token_type=Bearer&expires_in=10&state=S", ok = true), ok)
        assertEquals(LoopbackOAuth.Outcome.Refused, LoopbackOAuth.classify("/callback?code=C&state=X", ListProvider.MAL, "S"))
        assertEquals(LoopbackOAuth.Outcome.Refused, LoopbackOAuth.classify("/callback?code=C", ListProvider.MAL, "S"))
        assertEquals(LoopbackOAuth.Outcome.Refused, LoopbackOAuth.classify("/callback?access_token=T", ListProvider.ANILIST, "S"))
        assertEquals(LoopbackOAuth.Outcome.Refused, LoopbackOAuth.classify("/callback?error=access_denied", ListProvider.MAL, "S"))
        assertEquals(LoopbackOAuth.Outcome.Refused, LoopbackOAuth.classify("/callback?state=S", ListProvider.MAL, "S"))
        val denied = LoopbackOAuth.classify("/callback?error=access_denied&state=S", ListProvider.MAL, "S") as LoopbackOAuth.Outcome.Done
        assertFalse(denied.ok)
        assertEquals("nijiyome://oauth/mal?error=access_denied&state=S", denied.redirect)
    }

    @Test
    fun theListenerOnlyBindsLoopbackAndClosesOnTimeout() = runBlocking {
        val redirects = PendingOAuthRedirect()
        val server = LoopbackOAuthServer(port = port, timeoutMs = 1_500)
        assertTrue(server.start(ListProvider.MAL, "S") { redirects.offer(it) }.isSuccess)
        assertTrue(server.isListening)
        // Not reachable on any other address of the machine.
        val other = java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
        if (other != null) {
            val reached = runCatching { java.net.Socket().use { s -> s.connect(java.net.InetSocketAddress(other, port), 500) } }.isSuccess
            assertFalse(reached, "the listener must not answer on $other")
        }
        val timedOut = withTimeout(10_000) { redirects.link.filterNotNull().first() }
        // Hito 6 (D1): a timeout is its own error, not "cancelled".
        assertEquals("nijiyome://oauth/mal?error=nijiyome_timeout", timedOut)
        assertFalse(server.isListening)
        val error = (repo(MemorySecrets()).completeLink(timedOut).exceptionOrNull() as AppException).error
        assertEquals(AppError.Unknown("${ListErrors.DENIED}:${ListErrors.TIMEOUT_ERROR}"), error)
    }

    /** Hito 6 (review finding 4): an AniList token that comes back without the state it was sent with is refused (common code). */
    @Test
    fun anAniListTokenWithoutTheStateIsRefused() = runBlocking {
        val lists = repo(MemorySecrets())
        val url = lists.beginLink(ListProvider.ANILIST).getOrThrow()
        val state = LoopbackOAuth.stateOf(url)
        assertNotNull(state)
        for (forged in listOf("nijiyome://oauth/anilist#access_token=T&expires_in=3600", "nijiyome://oauth/anilist#access_token=T&state=other")) {
            val error = (lists.completeLink(forged).exceptionOrNull() as AppException).error
            assertEquals(AppError.Unknown(ListErrors.INVALID_STATE), error, forged)
        }
    }

    /** Hito 6 (D1): a taken loopback port and a missing browser are told apart, so the screen can say which. */
    @Test
    fun theLauncherSaysWhyItCouldNotOpen() = runBlocking {
        val url = repo(MemorySecrets()).beginLink(ListProvider.MAL).getOrThrow()
        java.net.ServerSocket(port, 8, java.net.InetAddress.getByName(LoopbackOAuth.HOST)).use {
            val busy = DesktopOAuthLauncher(PendingOAuthRedirect(), LoopbackOAuthServer(port = port)) { true }
            assertFalse(busy.open(url))
            assertEquals(DesktopOAuthLauncher.Failure.PORT_BUSY, busy.lastFailure)
            assertEquals(port, busy.port)
        }
        val noBrowserPort = freePort()
        val noBrowserServer = LoopbackOAuthServer(port = noBrowserPort)
        val noBrowser = DesktopOAuthLauncher(PendingOAuthRedirect(), noBrowserServer) { false }
        assertFalse(noBrowser.open(url))
        assertEquals(DesktopOAuthLauncher.Failure.NO_BROWSER, noBrowser.lastFailure)
        assertFalse(noBrowserServer.isListening, "the listener closed when the browser could not open")
        assertFalse(portInUse(noBrowserPort), "the listener closed when the browser could not open")
        assertFalse(noBrowser.open("https://example.org/authorize?state=x"))
        assertEquals(DesktopOAuthLauncher.Failure.NOT_OURS, noBrowser.lastFailure)
    }

    @Test
    fun providerAndStateComeFromTheAuthorizeUrl() {
        val mal = MalListClient.authorizeUrl("id", "STATE-1", Pkce.verifier(), LoopbackOAuth.redirectUri())
        assertEquals(ListProvider.MAL, LoopbackOAuth.providerOf(mal))
        assertEquals("STATE-1", LoopbackOAuth.stateOf(mal))
        val anilist = AniListListClient.authorizeUrl("id", "STATE-2")
        assertEquals(ListProvider.ANILIST, LoopbackOAuth.providerOf(anilist))
        assertEquals("STATE-2", LoopbackOAuth.stateOf(anilist))
        assertNull(LoopbackOAuth.providerOf("https://example.com/oauth?state=x"))
        assertNotNull(LoopbackOAuth.redirectUri().takeIf { it == "http://127.0.0.1:47410/callback" })
    }

    private companion object {
        fun freePort(): Int = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }

        fun portInUse(port: Int): Boolean =
            runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 300) } }.isSuccess
    }
}
