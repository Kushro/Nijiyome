// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.AppLog
import app.nijiyome.model.ListProvider
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * The OAuth redirect of the desktop app: the browser comes back to `http://127.0.0.1:47410/callback` (registered on the desktop
 * AniList and MAL clients), where [LoopbackOAuthServer] waits only while a login is open. What it receives is handed to the lists
 * repository as the same `nijiyome://oauth/<provider>?…` redirect Android gets from its intent filter, so the state check, the
 * PKCE exchange and the token storage are the shared code of `ListsRepositoryImpl`.
 */
object LoopbackOAuth {
    const val HOST = "127.0.0.1"
    const val PORT = 47410
    const val CALLBACK_PATH = "/callback"

    /** Ten minutes like the pending login of `PendingOAuth` would allow: then the listener closes and the login ends as timed out. */
    const val TIMEOUT_MS = 10L * 60 * 1000

    fun redirectUri(port: Int = PORT): String = "http://$HOST:$port$CALLBACK_PATH"

    /** The provider an authorize URL of `beginLink` belongs to (by its host), or null when it is not one of ours. */
    fun providerOf(authorizeUrl: String): ListProvider? {
        val host = runCatching { URI(authorizeUrl).host?.lowercase() }.getOrNull() ?: return null
        return when {
            host == "anilist.co" || host.endsWith(".anilist.co") -> ListProvider.ANILIST
            host == "myanimelist.net" || host.endsWith(".myanimelist.net") -> ListProvider.MAL
            else -> null
        }
    }

    /** The `state` parameter of an authorize URL. */
    fun stateOf(authorizeUrl: String): String? = OAuthRedirect.params(authorizeUrl.substringBefore('#'))["state"]

    /** What one request to the listener means (pure, unit-tested). */
    sealed interface Outcome {
        /** Not the callback (favicon, another path): 404, keep waiting. */
        data object Ignore : Outcome

        /** `GET /callback` with no query: the AniList token is in the fragment; answer the page that forwards it. */
        data object Forward : Outcome

        /** The redirect for the lists repository (`nijiyome://oauth/<provider>?…`); [ok] picks the page shown in the browser. */
        data class Done(val redirect: String, val ok: Boolean) : Outcome

        /**
         * A callback that is not this login's (no `state`, or another one): the browser gets the failure page and the listener keeps
         * waiting for the real one. Nothing reaches the repository, so another tab can neither end the user's login nor slip its
         * own token in (login CSRF).
         */
        data object Refused : Outcome
    }

    /**
     * Classifies a request target (`/callback?code=…&state=…`). Only a callback that carries this login's [expectedState] counts:
     * a code or a token with it is the login ([Outcome.Done] ok), an `error` with it ends the login as cancelled or denied
     * ([Outcome.Done] not ok). Anything else on `/callback` is [Outcome.Refused]. Stricter than Android's redirect check (which
     * accepts an AniList token without `state`): on desktop any local page can reach the loopback port.
     */
    fun classify(target: String, provider: ListProvider, expectedState: String): Outcome {
        val path = target.substringBefore('?').substringBefore('#')
        if (path != CALLBACK_PATH) return Outcome.Ignore
        val query = target.substringAfter('?', "").substringBefore('#')
        val params = OAuthRedirect.params("?$query")
        if (params.isEmpty()) return Outcome.Forward
        if (!constantTimeEquals(params["state"], expectedState)) return Outcome.Refused
        val redirect = OAuthRedirect.redirectUri(provider) + "?" + query
        if (params["error"] != null) return Outcome.Done(redirect, ok = false)
        val token = params["access_token"]?.takeIf { it.isNotEmpty() }
        val code = params["code"]?.takeIf { it.isNotEmpty() }
        return if (token != null || code != null) Outcome.Done(redirect, ok = true) else Outcome.Refused
    }

    private fun constantTimeEquals(a: String?, b: String): Boolean =
        a != null && java.security.MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    internal const val DONE_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Nijiyome</title></head>" +
        "<body style=\"font-family:sans-serif;margin:3rem\"><h1>Nijiyome</h1><p>Account connected. You can close this tab and return to " +
        "Nijiyome.</p><p>Cuenta conectada. Ya puedes cerrar esta pesta&ntilde;a y volver a Nijiyome.</p></body></html>"

    internal const val FAILED_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Nijiyome</title></head>" +
        "<body style=\"font-family:sans-serif;margin:3rem\"><h1>Nijiyome</h1><p>The account was not connected. Return to Nijiyome for " +
        "details.</p><p>No se conect&oacute; la cuenta. Vuelve a Nijiyome para ver el motivo.</p></body></html>"

    /** Forwards `#access_token=…` to the same listener as a query string (browsers never send the fragment to a server). */
    internal const val FORWARD_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Nijiyome</title></head>" +
        "<body style=\"font-family:sans-serif;margin:3rem\"><p>Nijiyome&hellip;</p><script>" +
        "var h=location.hash.replace(/^#/,'');location.replace('/callback?'+(h||'error=no_token'));</script></body></html>"
}

/**
 * One login's listener: bound to 127.0.0.1 only (never the LAN), on [port], from [start] until this login's callback arrives,
 * [stop] is called or [timeoutMs] passes; then the socket is closed. Callbacks without its `state` are answered and ignored. Requests are read with a short timeout and at most 16 KiB, answered
 * with a fixed page and `Connection: close`. [onRedirect] gets the `nijiyome://oauth/<provider>?…` redirect exactly once:
 * the callback, or `?error=nijiyome_timeout` ([ListErrors.TIMEOUT_ERROR]) when the time runs out.
 */
class LoopbackOAuthServer(
    val port: Int = LoopbackOAuth.PORT,
    private val timeoutMs: Long = LoopbackOAuth.TIMEOUT_MS,
) {
    @Volatile
    private var socket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    /** Binds the port and starts waiting. Fails (nothing started) when the port is taken. */
    fun start(provider: ListProvider, expectedState: String, onRedirect: (String) -> Unit): Result<Unit> {
        stop()
        val server = try {
            ServerSocket().apply {
                reuseAddress = false
                bind(InetSocketAddress(InetAddress.getByName(LoopbackOAuth.HOST), port), BACKLOG)
                soTimeout = ACCEPT_SLICE_MS
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "loopback port $port is not free: ${e.message}")
            return Result.failure(e)
        }
        socket = server
        val thread = Thread({ serve(server, provider, expectedState, onRedirect) }, "oauth-loopback")
        thread.isDaemon = true
        acceptThread = thread
        thread.start()
        return Result.success(Unit)
    }

    /** Closes the listener (a new login, the app closing). Nothing is delivered. */
    fun stop() {
        socket?.let { runCatching { it.close() } }
        socket = null
        val t = acceptThread
        acceptThread = null
        if (t != null && t !== Thread.currentThread()) {
            runCatching { t.join(500) }
        }
    }

    val isListening: Boolean get() = socket?.isClosed == false

    private fun serve(server: ServerSocket, provider: ListProvider, expectedState: String, onRedirect: (String) -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var delivered: String? = null
        try {
            while (delivered == null && !server.isClosed) {
                if (System.currentTimeMillis() > deadline) {
                    delivered = OAuthRedirect.redirectUri(provider) + "?error=" + ListErrors.TIMEOUT_ERROR
                    AppLog.i(TAG, "login timed out")
                    break
                }
                val client = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                client.use { delivered = handle(it, provider, expectedState) }
            }
        } catch (_: SocketException) {
            // stop(): closed while waiting
        } finally {
            runCatching { server.close() }
            if (socket === server) socket = null
        }
        delivered?.let(onRedirect)
    }

    /** Answers one request; the redirect when it was the callback. */
    private fun handle(client: Socket, provider: ListProvider, expectedState: String): String? {
        client.soTimeout = READ_TIMEOUT_MS
        val target = try {
            readTarget(client)
        } catch (_: IOException) {
            null
        } ?: run {
            respond(client, "400 Bad Request", "")
            return null
        }
        return when (val outcome = LoopbackOAuth.classify(target, provider, expectedState)) {
            LoopbackOAuth.Outcome.Ignore -> {
                respond(client, "404 Not Found", "")
                null
            }
            LoopbackOAuth.Outcome.Forward -> {
                respond(client, "200 OK", LoopbackOAuth.FORWARD_PAGE)
                null
            }
            is LoopbackOAuth.Outcome.Done -> {
                respond(client, "200 OK", if (outcome.ok) LoopbackOAuth.DONE_PAGE else LoopbackOAuth.FAILED_PAGE)
                if (!outcome.ok) AppLog.i(TAG, "login cancelled or denied")
                outcome.redirect
            }
            LoopbackOAuth.Outcome.Refused -> {
                // Not this login's callback: say so and keep waiting for the real one.
                respond(client, "200 OK", LoopbackOAuth.FAILED_PAGE)
                AppLog.w(TAG, "callback without this login's state ignored")
                null
            }
        }
    }

    /** The target of a `GET <target> HTTP/1.x` request line; null for anything else. */
    private fun readTarget(client: Socket): String? {
        val input = client.getInputStream()
        val head = StringBuilder()
        val buf = ByteArray(2048)
        while (head.length < MAX_REQUEST && !head.contains("\r\n\r\n")) {
            val n = input.read(buf)
            if (n <= 0) break
            head.append(String(buf, 0, n, StandardCharsets.ISO_8859_1))
        }
        val line = head.lineSequence().firstOrNull() ?: return null
        val parts = line.trim().split(' ')
        if (parts.size < 3 || parts[0] != "GET" || !parts[2].startsWith("HTTP/1.")) return null
        return parts[1]
    }

    private fun respond(client: Socket, status: String, body: String) {
        runCatching {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
            client.getOutputStream().apply {
                write(head.toByteArray(StandardCharsets.ISO_8859_1))
                write(bytes)
                flush()
            }
        }
    }

    private companion object {
        const val TAG = "oauth"
        const val BACKLOG = 8
        const val ACCEPT_SLICE_MS = 1_000
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_REQUEST = 16 * 1024
    }
}

/**
 * Opens a login on desktop: starts the loopback listener for the provider and state of [authorizeUrl] (from `beginLink`), then
 * the system browser on it. The redirect goes to [redirects], which Settings › Listas y cuentas already collects (the path the
 * Android Custom Tab takes). false when the URL is not ours, the port is taken or there is no browser; [lastFailure] says which.
 *
 * Both providers use the one registered redirect, port [LoopbackOAuth.PORT]: Tatakai Next tried 47410..47430 for MAL, but the
 * desktop MAL client of Nijiyome registers only `http://127.0.0.1:47410/callback`, and MAL refuses a `redirect_uri` it does not have.
 */
class DesktopOAuthLauncher(
    private val redirects: PendingOAuthRedirect,
    private val server: LoopbackOAuthServer = LoopbackOAuthServer(),
    private val browse: (String) -> Boolean = ::openInBrowser,
) {
    /** Why the last [open] answered false (null after one that worked). */
    enum class Failure { NOT_OURS, PORT_BUSY, NO_BROWSER }

    @Volatile
    var lastFailure: Failure? = null
        private set

    /** The port the listener binds (for the "port in use" text). */
    val port: Int get() = server.port

    fun open(authorizeUrl: String): Boolean {
        lastFailure = null
        val provider = LoopbackOAuth.providerOf(authorizeUrl) ?: return fail(Failure.NOT_OURS)
        val state = LoopbackOAuth.stateOf(authorizeUrl) ?: return fail(Failure.NOT_OURS)
        if (server.start(provider, state) { redirects.offer(it) }.isFailure) return fail(Failure.PORT_BUSY)
        if (!browse(authorizeUrl)) {
            server.stop()
            return fail(Failure.NO_BROWSER)
        }
        return true
    }

    private fun fail(reason: Failure): Boolean {
        lastFailure = reason
        return false
    }

    fun close() = server.stop()

    companion object {
        /** The default browser: AWT's `Desktop.browse`, else `xdg-open` (Linux) / `rundll32 url.dll` (Windows). */
        fun openInBrowser(url: String): Boolean {
            val viaAwt = runCatching {
                val desktop = java.awt.Desktop.getDesktop()
                desktop.isSupported(java.awt.Desktop.Action.BROWSE) && run { desktop.browse(URI(url)); true }
            }.getOrDefault(false)
            if (viaAwt) return true
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
            val command = if (windows) listOf("rundll32", "url.dll,FileProtocolHandler", url) else listOf("xdg-open", url)
            return runCatching { ProcessBuilder(command).start(); true }.getOrDefault(false)
        }
    }
}
