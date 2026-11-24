// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model

import kotlinx.coroutines.flow.StateFlow

// Settings › Red of the desktop app (Tatakai Next's server › network rows: outbound proxy, FlareSolverr, loopback media proxy).
// Android registers none of this.

enum class NetProxyKind { HTTP, SOCKS5 }

/** What Settings › Red shows. The password itself never leaves the secret store; [hasPassword] says whether one is kept. */
data class NetworkSettingsState(
    val proxyEnabled: Boolean = false,
    val proxyKind: NetProxyKind = NetProxyKind.SOCKS5,
    val proxyHost: String = "",
    val proxyPort: Int = 1080,
    val proxyUsername: String = "",
    val hasPassword: Boolean = false,
    val flareSolverrUrl: String = "",
    /** The player opens streams that need cookies / credentials / Origin through the loopback media proxy (Tatakai Next default on). */
    val localMediaProxy: Boolean = true,
)

/** The result of a "Probar". */
sealed interface NetTestResult {
    data class Ok(val latencyMs: Long, val detail: String? = null) : NetTestResult
    data class Failed(val reason: String) : NetTestResult
}

/** Settings › Red (desktop). */
interface NetworkSettingsController {
    val state: StateFlow<NetworkSettingsState>

    /** Saves the proxy; [password] null keeps the stored one, empty removes it. Takes effect for the next connection. */
    suspend fun saveProxy(enabled: Boolean, kind: NetProxyKind, host: String, port: Int, username: String, password: String?): Boolean

    /** One HTTPS request through the given proxy (not necessarily the saved one); [password] null = the stored one. */
    suspend fun testProxy(kind: NetProxyKind, host: String, port: Int, username: String, password: String?): NetTestResult

    /** Empty clears it. False when [url] is not an http(s) address. */
    suspend fun saveFlareSolverr(url: String): Boolean

    suspend fun testFlareSolverr(url: String): NetTestResult

    fun setLocalMediaProxy(enabled: Boolean)
}

/** A stream as the desktop player should open it. */
class RoutedStream(val url: String, val headers: Map<String, String>, val subtitleUrls: Map<String, String> = emptyMap())

/** Thrown when a stream must go through the loopback media proxy (an outbound proxy is set) and the proxy cannot start. */
class LocalRouteUnavailable(message: String) : Exception(message)

/**
 * Tatakai Next `server.cors-proxy-local` (desktop): mpv gets a stream through the app's loopback media proxy when its headers would
 * leak to every CDN (Cookie, Authorization, Origin) or when an outbound proxy is set (mpv cannot use a SOCKS proxy).
 */
interface LocalMediaRoute {
    /** Starts the loopback proxy in the background when it may be needed soon (the player opened). */
    fun warm() = Unit

    /**
     * [url] + [headers] (and the http subtitle URLs of the same load) as mpv should open them; null = unchanged. May block while the
     * loopback proxy starts. Throws [LocalRouteUnavailable] when the stream must not be played directly and cannot be proxied.
     */
    fun route(url: String, headers: Map<String, String>, subtitles: List<String> = emptyList()): RoutedStream?
}
