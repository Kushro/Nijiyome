// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-core/src/net.rs (AGPL-3.0, same author; docs/origen/THIRD_PARTY_NOTICES-escritorio.md).
package app.nijiyome.server.net

import app.nijiyome.network.FlareSolverrInterceptor
import app.nijiyome.network.Outbound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * SSRF-safe outbound HTTP shared by the Link `url` source fetcher and the extension server's media proxy. Per hop: scheme / port
 * allow-list, DNS resolved here, every resolved address validated (loopback, private, CGNAT, link-local, ULA, multicast,
 * unspecified, documentation, IPv4-mapped / NAT64 / 6to4 embeds), and the connection pinned to the validated addresses (an OkHttp
 * [Dns] that only knows them), so a second DNS answer (rebinding) is never used. Redirects are followed by hand (max
 * [NetPolicy.maxRedirects]), each hop re-validated; `Cookie` / `Authorization` are dropped on a change of origin.
 */
sealed class NetError(message: String) : Exception(message) {
    class Scheme : NetError("scheme not allowed")
    class Port : NetError("port not allowed")
    class Host : NetError("host not allowed")
    class BlockedIp(val ip: InetAddress) : NetError("address not allowed")
    class Dns : NetError("dns resolution failed")
    class TooManyRedirects : NetError("too many redirects")
    class Header(reason: String) : NetError("invalid header: $reason")
    class Timeout : NetError("request timed out")
    class TooLarge : NetError("response too large")
    class Http(reason: String) : NetError("http error: $reason")

    /** The policy refuses the request (as opposed to an upstream that is down). */
    val isDenial: Boolean get() = this is Scheme || this is Port || this is Host || this is BlockedIp || this is Header || this is TooManyRedirects
}

data class NetPolicy(
    val allowHttp: Boolean,
    /** null = any port except 0. */
    val allowedPorts: Set<Int>?,
    val maxRedirects: Int,
    val timeoutMs: Long,
    /** Lower-case header names that may be forwarded; null = any (CR/LF is always refused). */
    val headerAllowlist: Set<String>?,
    val maxHeaderBytes: Int,
    /** Test-only: loopback addresses are reachable (never set in production code). */
    val allowLoopback: Boolean = false,
) {
    companion object {
        /** The Link `url` source (contract x-ffmpeg.ssrfFetcher). */
        fun linkSource(allowHttp: Boolean) = NetPolicy(
            allowHttp = allowHttp,
            allowedPorts = setOf(80, 443, 8080, 8443),
            maxRedirects = 5,
            timeoutMs = 20_000,
            headerAllowlist = setOf("referer", "origin", "user-agent", "accept", "accept-language", "cookie", "range"),
            maxHeaderBytes = 8 * 1024,
        )

        /** The extension server's media proxy and `.kai` downloads: any port, http + https, 30 s. */
        fun extensionFetch() = NetPolicy(
            allowHttp = true,
            allowedPorts = null,
            maxRedirects = 5,
            timeoutMs = 30_000,
            headerAllowlist = null,
            maxHeaderBytes = 32 * 1024,
        )
    }
}

/** The address rules live in :core:network (1.1: the desktop hidden browser's gateway applies the very same ones). */
object AddressRules {
    fun isBlockedV4(o: ByteArray): Boolean = app.nijiyome.network.AddressRules.isBlockedV4(o)

    fun isBlockedV6(o: ByteArray): Boolean = app.nijiyome.network.AddressRules.isBlockedV6(o)

    fun isBlocked(ip: InetAddress): Boolean = app.nijiyome.network.AddressRules.isBlocked(ip)

    /** `network:domain:<host>` permissions: exact host or a subdomain; `*` matches any host. */
    fun hostMatchesPermissions(host: String, permissions: List<String>): Boolean {
        val h = host.trimEnd('.').lowercase(Locale.ROOT)
        return permissions.any { p ->
            if (!p.startsWith("network:domain:")) return@any false
            val d = p.removePrefix("network:domain:").trim().trimEnd('.').lowercase(Locale.ROOT)
            d == "*" || h == d || h.endsWith(".$d")
        }
    }
}

/** Resolves a host name (tests inject fixed answers; production uses the system resolver). */
fun interface Resolver {
    suspend fun resolve(host: String, port: Int): List<InetAddress>
}

object SystemResolver : Resolver {
    override suspend fun resolve(host: String, port: Int): List<InetAddress> =
        withContext(Dispatchers.IO) { InetAddress.getAllByName(host).toList() }
}

/** Names that only mean this machine or the local network (refused through a proxy, where they are never resolved here). */
fun localName(host: String): Boolean {
    val h = host.trimEnd('.').lowercase(Locale.ROOT)
    return h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".home.arpa") ||
        h.endsWith(".lan") || '.' !in h && ipLiteral(h) == null
}

/** `(scheme, host, port)` for the cross-origin credential strip. */
fun originOf(u: HttpUrl): Triple<String, String, Int> = Triple(u.scheme, u.host.lowercase(Locale.ROOT), u.port)

/** A URL as the request gave it: an http(s) [HttpUrl], or why it is not one. */
sealed interface Target {
    data class Http(val url: HttpUrl) : Target
    /** Absolute, but another scheme (`ftp:`, `file:`, `data:`...): refused by the policy. */
    data object OtherScheme : Target
    /** Not an absolute URL at all. */
    data object Malformed : Target

    companion object {
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

        fun parse(text: String): Target {
            if (text.isEmpty() || text.any { it == '\r' || it == '\n' || it == '\u0000' }) return Malformed
            val scheme = SCHEME.find(text)?.value?.dropLast(1)?.lowercase(Locale.ROOT) ?: return Malformed
            if (scheme != "http" && scheme != "https") {
                return if (runCatching { URI(text.replace(" ", "%20")) }.isSuccess) OtherScheme else Malformed
            }
            if (text.any { it == ' ' }) return Malformed
            return text.toHttpUrlOrNull()?.let { Http(it) } ?: Malformed
        }
    }
}

/** Validates request headers against [policy] (allow-list, size, CR/LF, NUL): lower-case name / value pairs ready to send. */
fun sanitizeHeaders(policy: NetPolicy, headers: Map<String, String>): List<Pair<String, String>> {
    var total = 0
    val out = ArrayList<Pair<String, String>>(headers.size)
    for ((k, v) in headers.toSortedMap()) {
        if (k.isEmpty() || k.any { it == '\r' || it == '\n' || it == '\u0000' || it == ':' }) throw NetError.Header("illegal header name")
        if (v.any { it == '\r' || it == '\n' || it == '\u0000' }) throw NetError.Header("CR/LF in header value")
        total += k.toByteArray().size + v.toByteArray().size + 4
        if (total > policy.maxHeaderBytes) throw NetError.Header("headers too large")
        val lower = k.lowercase(Locale.ROOT)
        val allow = policy.headerAllowlist
        if (allow != null) {
            if (lower !in allow) throw NetError.Header("header not allowed")
        } else if (lower in setOf("host", "content-length", "transfer-encoding", "connection")) {
            continue
        }
        out += lower to v
    }
    return out
}

/**
 * One exact local endpoint the caller trusts (BUG-023): a literal IP + port + path prefix, e.g. this PC's own extension server
 * media proxy `http://192.168.1.43:8099/api/server/v1/media/`. A matching URL skips the scheme, port, host-filter and
 * private-address checks; nothing else of that address is opened.
 */
data class TrustedOrigin(val address: InetSocketAddress, val pathPrefix: String) {
    fun matches(u: HttpUrl): Boolean {
        if (u.username.isNotEmpty() || u.password.isNotEmpty()) return false
        val ip = ipLiteral(u.host) ?: return false
        return ip == address.address && u.port == address.port &&
            pathPrefix.startsWith('/') && pathPrefix.endsWith('/') &&
            u.encodedPath.startsWith(pathPrefix) &&
            u.encodedPath.split('/').none { it == ".." || it.equals("%2e%2e", ignoreCase = true) }
    }
}

private val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")

/** The address of an IP-literal host (no DNS), or null for a name. */
fun ipLiteral(host: String): InetAddress? = when {
    host.contains(':') -> runCatching { InetAddress.getByName("[${host.removePrefix("[").removeSuffix("]")}]") }.getOrNull()
    IPV4_LITERAL.matches(host) -> runCatching { InetAddress.getByName(host) }.getOrNull()
    else -> null
}

class SafeRequest(
    val method: String,
    val url: HttpUrl,
    val headers: List<Pair<String, String>>,
    val body: RequestBody? = null,
) {
    companion object {
        fun get(url: HttpUrl, headers: List<Pair<String, String>>) = SafeRequest("GET", url, headers)
    }
}

/** The answer of the last hop (the caller closes [response]). */
class SafeResponse(val response: Response, val finalUrl: HttpUrl)

class SafeHttp(
    val policy: NetPolicy,
    private val resolver: Resolver = SystemResolver,
    private val hostFilter: ((String) -> Boolean)? = null,
    private val trusted: List<TrustedOrigin> = emptyList(),
    private val base: OkHttpClient = sharedBase,
    /** The outbound proxy of a request to a host (Settings › Red); null = direct. Tests replace it. */
    private val outboundProxy: (String) -> Proxy? = Outbound::proxyFor,
) {
    fun withHostFilter(filter: (String) -> Boolean) = SafeHttp(policy, resolver, filter, trusted, base, outboundProxy)

    fun withTrustedOrigins(origins: List<TrustedOrigin>) = SafeHttp(policy, resolver, hostFilter, origins, base, outboundProxy)

    fun isTrusted(u: HttpUrl): Boolean = trusted.any { it.matches(u) }

    /** Scheme / port / credentials / host-filter checks that need no DNS. */
    fun checkUrl(u: HttpUrl) {
        if (isTrusted(u)) return
        when (u.scheme) {
            "https" -> Unit
            "http" -> if (!policy.allowHttp) throw NetError.Scheme()
            else -> throw NetError.Scheme()
        }
        if (u.username.isNotEmpty() || u.password.isNotEmpty()) throw NetError.Host()
        if (u.port == 0 || policy.allowedPorts?.contains(u.port) == false) throw NetError.Port()
        if (u.host.isEmpty()) throw NetError.Host()
        if (hostFilter?.invoke(u.host) == false) throw NetError.Host()
    }

    fun ipAllowed(ip: InetAddress): Boolean = (policy.allowLoopback && ip.isLoopbackAddress) || !AddressRules.isBlocked(ip)

    /**
     * The checks of a request the outbound proxy will resolve and connect: scheme / port / credentials / host filter, an IP literal
     * validated like a resolved address, and names that only mean this machine or its LAN refused. Nothing is resolved here.
     */
    fun checkViaProxy(u: HttpUrl) {
        checkUrl(u)
        ipLiteral(u.host)?.let { ip -> if (!ipAllowed(ip)) throw NetError.BlockedIp(ip) }
        if (!policy.allowLoopback && localName(u.host)) throw NetError.Host()
    }

    /** Resolve and validate: every returned address is safe to connect to. A mixed answer (one bad address) is refused whole. */
    suspend fun resolveValidated(u: HttpUrl): List<InetSocketAddress> {
        trusted.firstOrNull { it.matches(u) }?.let { return listOf(it.address) }
        checkUrl(u)
        val ips = ipLiteral(u.host)?.let(::listOf) ?: try {
            resolver.resolve(u.host, u.port)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            throw NetError.Dns()
        }
        if (ips.isEmpty()) throw NetError.Dns()
        ips.firstOrNull { !ipAllowed(it) }?.let { throw NetError.BlockedIp(it) }
        return ips.map { InetSocketAddress(it, u.port) }
    }

    /** One request with every redirect re-validated; the policy timeout bounds the whole exchange. */
    suspend fun send(req: SafeRequest): SafeResponse = sendInner(req, stream = false)

    /**
     * Like [send] for a body read as a long stream at the reader's own pace (the Link fetcher: a whole episode that FFmpeg stops
     * reading while its output is held back): the timeout bounds the connection, the response head and each read, never the body.
     */
    suspend fun sendStream(req: SafeRequest): SafeResponse = sendInner(req, stream = true)

    private suspend fun sendInner(req: SafeRequest, stream: Boolean): SafeResponse {
        val origin0 = originOf(req.url)
        var url = req.url
        var method = req.method
        var body = req.body
        var hop = 0
        while (true) {
            val host = url.host
            // Settings › Red: the outbound proxy (Tatakai Next's Upstream). Never a direct fallback: a proxy that fails fails the
            // request. Trusted LAN origins stay direct. Through a proxy the name is NOT resolved here (no DNS query outside the
            // proxy): the URL is checked against the policy without DNS ([checkViaProxy]) and the proxy resolves and connects.
            val viaProxy = if (isTrusted(url)) null else outboundProxy(host)
            val pinned = if (viaProxy != null) {
                checkViaProxy(url)
                emptyList()
            } else {
                resolveValidated(url).map { it.address }
            }
            val proxyHost = (viaProxy?.address() as? InetSocketAddress)?.hostString
            val client = base.newBuilder()
                .proxy(viaProxy ?: Proxy.NO_PROXY)
                .apply { if (viaProxy != null) proxyAuthenticator(Outbound.okHttpAuthenticator) }
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .dns(
                    Dns { name ->
                        when {
                            viaProxy == null && name == host -> pinned
                            // the HTTP proxy's own address (a SOCKS proxy is never resolved here)
                            proxyHost != null && name == proxyHost -> Dns.SYSTEM.lookup(name)
                            else -> throw UnknownHostException("not validated")
                        }
                    },
                )
                .connectTimeout(policy.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(policy.timeoutMs, TimeUnit.MILLISECONDS)
                .apply { if (!stream) callTimeout(policy.timeoutMs, TimeUnit.MILLISECONDS) }
                .build()
            val cross = originOf(url) != origin0
            val builder = Request.Builder().url(url)
            for ((k, v) in req.headers) {
                if (cross && (k.equals("cookie", true) || k.equals("authorization", true))) continue
                try {
                    builder.addHeader(k, v)
                } catch (_: IllegalArgumentException) {
                    throw NetError.Header("illegal header")
                }
            }
            builder.method(method, body)
            val resp = try {
                client.newCall(builder.build()).await()
            } catch (e: InterruptedIOException) {
                throw NetError.Timeout()
            } catch (e: IOException) {
                throw NetError.Http(e.javaClass.simpleName)
            }
            val code = resp.code
            if (code in 300..399 && code != 304) {
                val loc = resp.header("Location") ?: return SafeResponse(resp, url)
                resp.close()
                if (hop == policy.maxRedirects) throw NetError.TooManyRedirects()
                hop++
                val next = url.resolve(loc) ?: throw NetError.Host()
                if (code in 301..303 && method != "GET" && method != "HEAD") {
                    method = "GET"
                    body = null
                }
                url = next
                continue
            }
            return SafeResponse(resp, url)
        }
    }

    companion object {
        /**
         * One dispatcher and connection pool for every policy client (each hop builds a pinned client on top of it). A Cloudflare
         * challenge is solved through the user's FlareSolverr when one is set (Settings › Red), as Tatakai Next's Upstream does.
         */
        val sharedBase: OkHttpClient by lazy { OkHttpClient.Builder().addInterceptor(FlareSolverrInterceptor()).build() }
    }
}

/** Reads a whole body with a hard cap ([NetError.TooLarge] above it). */
fun readCapped(resp: Response, cap: Int): ByteArray = resp.use {
    val body = it.body
    if (body.contentLength() > cap) throw NetError.TooLarge()
    val source = body.source()
    try {
        if (source.request(cap.toLong() + 1)) throw NetError.TooLarge()
    } catch (e: InterruptedIOException) {
        throw NetError.Timeout()
    } catch (e: IOException) {
        throw NetError.Http(e.javaClass.simpleName)
    }
    source.buffer.readByteArray()
}

/** Runs the call without blocking the caller; cancelling the coroutine cancels the call. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
