// SPDX-License-Identifier: AGPL-3.0-or-later
// After Tatakai Next tk-server/src/upstream.rs (outbound proxy, AGPL-3.0, same author).
package app.nijiyome.network

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/** The kind of outbound proxy (Settings › Red). */
enum class ProxyKind { HTTP, SOCKS5 }

/** One outbound proxy: where it is and its optional credentials (the credentials live in the secret store). */
data class ProxyEndpoint(
    val kind: ProxyKind,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
) {
    /** Host names are resolved by the proxy itself (SOCKS5 with remote DNS, HTTP CONNECT): no DNS query leaves this PC directly. */
    fun javaProxy(): Proxy = Proxy(
        if (kind == ProxyKind.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP,
        InetSocketAddress.createUnresolved(host, port),
    )

    val hasCredentials: Boolean get() = !username.isNullOrEmpty()

    /** `socks5://host:port` / `http://host:port` (no credentials): what logs and FlareSolverr see. */
    fun uri(): String = "${if (kind == ProxyKind.SOCKS5) "socks5" else "http"}://${bracket(host)}:$port"

    override fun toString(): String = "ProxyEndpoint(${uri()}, credentials=${if (hasCredentials) "yes" else "no"})"

    companion object {
        fun valid(host: String, port: Int): Boolean =
            port in 1..65535 && host.isNotBlank() && host.length <= 253 && host.none { it.isWhitespace() || it in "/?#@" }

        private fun bracket(h: String) = if (':' in h && !h.startsWith('[')) "[$h]" else h
    }
}

/**
 * The desktop app's outbound route (Settings › Red), process-wide: the outbound proxy every connection to the internet takes and the
 * user's FlareSolverr. Android never sets it ([install] is desktop only), so there everything stays as it was.
 *
 * No leaks: while a proxy is set, [OutboundProxySelector] answers only that proxy (never "direct" as a fallback), so a proxy that
 * fails makes the request fail. Only this PC and the private network (Nijiyome Link phones, the user's own FlareSolverr, the loopback
 * media proxy) are reached directly.
 */
object Outbound {
    private val _proxy = kotlinx.coroutines.flow.MutableStateFlow<ProxyEndpoint?>(null)

    /** [proxy] as a flow (the torrent engine re-applies its SOCKS5 setting when it changes). */
    val proxyFlow: kotlinx.coroutines.flow.StateFlow<ProxyEndpoint?> get() = _proxy

    var proxy: ProxyEndpoint?
        get() = _proxy.value
        set(value) {
            _proxy.value = value
        }

    /** FlareSolverr endpoint (`http://127.0.0.1:8191`), null = none. */
    @Volatile
    var flareSolverr: String? = null

    /** Extra endpoints whose credentials the authenticator may hand out (the "Probar" of a proxy that is not saved yet). */
    private val probes = java.util.concurrent.CopyOnWriteArrayList<ProxyEndpoint>()

    @Volatile
    private var installed = false

    /**
     * Makes [OutboundProxySelector] the JVM's default proxy selector (every OkHttp client built afterwards, Coil's included, asks it
     * per connection) and installs the SOCKS5 credentials authenticator. Call once, before any client is built. Desktop only.
     */
    @Synchronized
    fun install() {
        if (installed) return
        OutboundProxySelector.fallback = ProxySelector.getDefault()
        ProxySelector.setDefault(OutboundProxySelector)
        Authenticator.setDefault(OutboundSocksAuthenticator)
        installed = true
    }

    internal fun credentialsFor(host: String?, port: Int): ProxyEndpoint? {
        val all = listOfNotNull(proxy) + probes
        return all.firstOrNull { it.hasCredentials && it.port == port && it.host.equals(host, ignoreCase = true) }
    }

    /** Runs [block] with [endpoint]'s credentials available to the SOCKS authenticator (the test of an unsaved proxy). */
    fun <T> withProbe(endpoint: ProxyEndpoint, block: () -> T): T {
        probes += endpoint
        try {
            return block()
        } finally {
            probes -= endpoint
        }
    }

    /**
     * Whether [host] is reached without the proxy: loopback, `localhost`, `*.local` and literal private / link-local addresses (this
     * PC and the LAN). Names are never resolved here (that would be a DNS query outside the proxy).
     */
    fun isLocal(host: String?): Boolean {
        val h = host?.trim()?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return false
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")) return true
        val ip = literal(h) ?: return false
        return ip.isLoopbackAddress || ip.isSiteLocalAddress || ip.isLinkLocalAddress || ip.isAnyLocalAddress ||
            (ip is java.net.Inet6Address && (ip.address[0].toInt() and 0xFE) == 0xFC) // fc00::/7
    }

    private val V4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    private fun literal(h: String): InetAddress? = when {
        ':' in h -> runCatching { InetAddress.getByName("[$h]") }.getOrNull()
        V4.matches(h) -> runCatching { InetAddress.getByName(h) }.getOrNull()
        else -> null
    }

    /** The proxy a connection to [host] takes now; null = direct. */
    fun proxyFor(host: String?): Proxy? {
        val p = proxy ?: return null
        return if (isLocal(host)) null else p.javaProxy()
    }

    /** Proxy-Authorization for an HTTP proxy that asks (407), also preemptively for CONNECT. Install with [outbound]. */
    val okHttpAuthenticator = okhttp3.Authenticator { route: Route?, response: Response ->
        val p = route?.proxy?.takeIf { it.type() == Proxy.Type.HTTP } ?: return@Authenticator null
        val addr = p.address() as? InetSocketAddress ?: return@Authenticator null
        val e = credentialsFor(addr.hostString, addr.port) ?: return@Authenticator null
        // already sent and refused: give up (OkHttp would otherwise retry up to its follow-up limit)
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(e.username.orEmpty(), e.password.orEmpty())).build()
    }
}

/** The outbound route on an OkHttp client: the proxy selector (also the JVM default once installed) and HTTP proxy credentials. */
fun OkHttpClient.Builder.outbound(): OkHttpClient.Builder = proxySelector(OutboundProxySelector).proxyAuthenticator(Outbound.okHttpAuthenticator)

/**
 * The JVM-wide selector of [Outbound]: with a proxy set, every non-local URI gets that proxy only; without one, the selector that was
 * the default before (the system's, as the app always used) answers.
 */
object OutboundProxySelector : ProxySelector() {
    @Volatile
    internal var fallback: ProxySelector? = null

    override fun select(uri: URI?): List<Proxy> {
        val p = Outbound.proxy ?: return fallback?.takeIf { it !== this }?.select(uri) ?: listOf(Proxy.NO_PROXY)
        val host = uri?.host ?: uri?.authority?.substringAfterLast('@')?.substringBeforeLast(':')
        if (Outbound.isLocal(host)) return listOf(Proxy.NO_PROXY)
        return listOf(p.javaProxy())
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        if (Outbound.proxy == null) fallback?.takeIf { it !== this }?.connectFailed(uri, sa, ioe)
    }
}

/** SOCKS5 user / password: the JDK's SOCKS client asks the default authenticator; it gets only the configured proxy's credentials. */
private object OutboundSocksAuthenticator : Authenticator() {
    override fun getPasswordAuthentication(): PasswordAuthentication? {
        val e = Outbound.credentialsFor(requestingHost, requestingPort) ?: return null
        if (e.kind != ProxyKind.SOCKS5 && requestorType != RequestorType.PROXY) return null
        return PasswordAuthentication(e.username.orEmpty(), e.password.orEmpty().toCharArray())
    }
}

/** What a "Probar" found. */
sealed interface ProbeResult {
    data class Ok(val latencyMs: Long, val detail: String? = null) : ProbeResult
    data class Failed(val reason: String) : ProbeResult
}

/**
 * "Probar" of an outbound proxy: one HTTPS request to [target] through [endpoint] only (any HTTP answer proves the tunnel works;
 * AniList is a host the app already talks to, as in Tatakai Next).
 */
object ProxyProbe {
    const val DEFAULT_TARGET = "https://graphql.anilist.co/"

    fun test(endpoint: ProxyEndpoint, target: String = DEFAULT_TARGET, timeoutMs: Long = 10_000): ProbeResult = Outbound.withProbe(endpoint) {
        val client = OkHttpClient.Builder()
            .proxy(endpoint.javaProxy())
            .proxyAuthenticator(Outbound.okHttpAuthenticator)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val started = System.nanoTime()
        try {
            client.newCall(Request.Builder().url(target).head().build()).execute().use { r ->
                if (r.code == 407) return@withProbe ProbeResult.Failed("proxy authentication required")
            }
            ProbeResult.Ok((System.nanoTime() - started) / 1_000_000)
        } catch (e: IOException) {
            ProbeResult.Failed(e.message?.take(200) ?: e.javaClass.simpleName)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
