// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.network.AddressRules
import app.nijiyome.network.ProxyEndpoint
import app.nijiyome.network.ProxyKind
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The only way out of the hidden browser (1.1 review #1, #10): Chromium is started with `--proxy-server=socks5://127.0.0.1:<port>` and
 * `--proxy-bypass-list=<-loopback>`, so EVERY TCP connection it makes (pages, iframes, `fetch`, images, WebSockets, workers, even to
 * 127.0.0.1) comes here as a SOCKS5 CONNECT, with the host name unresolved. Each destination is checked against the same address rules
 * as the server's SafeHttp ([AddressRules]: loopback, private, link-local, CGNAT, ULA, IPv4-mapped…, and local names such as
 * `localhost` / `*.local`):
 *
 * * without an outbound proxy the name is resolved HERE, every answer must be public, and the socket is opened to one of those
 *   validated addresses (a second DNS answer is never used: no rebinding);
 * * with the outbound proxy of Settings › Red the name is never resolved on this PC (no DNS query outside the proxy, as Outbound);
 *   literal addresses and local names are still refused, and the connection is made through that proxy: SOCKS5 (with its user /
 *   password, RFC 1929) or HTTP CONNECT (with Basic credentials, sent once: a wrong password fails the connection instead of looping).
 *
 * Refused destinations get SOCKS reply 0x02 ("not allowed by ruleset"), which Chromium reports as a network error.
 *
 * The listening socket is bound to 127.0.0.1 only and lives as long as the browser. Another local process could also use it, but it
 * only ever reaches public addresses, and the outbound proxy credentials never leave this class.
 */
class BrowserGateway(
    private val upstream: ProxyEndpoint?,
    /** Whether a resolved [InetAddress] (with its port) may be reached. Tests loosen it to reach a local test page. */
    private val allowed: (InetAddress, Int) -> Boolean = { ip, _ -> !AddressRules.isBlocked(ip) },
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    /** Refuse `localhost`, `*.local`, single-label names… before any lookup. Tests turn it off to reach `localhost`. */
    private val refuseLocalNames: Boolean = true,
    private val log: (String) -> Unit = {},
) : Closeable {
    private val server = ServerSocket(0, 64, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val pool: ExecutorService = Executors.newCachedThreadPool { r -> Thread(r, "browser-gateway").apply { isDaemon = true } }
    private val open = ConcurrentHashMap.newKeySet<Socket>()
    private val closed = AtomicBoolean(false)

    val port: Int get() = server.localPort

    /**
     * The outbound proxy refused its user / password once: further connections fail at once instead of asking it again (Chromium retries
     * a failed connection several times). A new proxy setting restarts the browser with a new gateway.
     */
    @Volatile var upstreamRejectedCredentials: Boolean = false
        private set

    private val authLock = Any()
    private val authVerified = AtomicBoolean(false)

    /** Destinations refused so far (host:port), for the logs and the tests. */
    val refused: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        pool.execute {
            while (!closed.get()) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                open += client
                pool.execute { serve(client) }
            }
        }
    }

    /** The Chromium switches that send all its traffic here (and nothing around it). */
    fun chromiumArgs(): List<String> = listOf(
        "--proxy-server=socks5://127.0.0.1:$port",
        // Chromium skips the proxy for loopback by default: not here, so 127.0.0.1 / localhost are checked too
        "--proxy-bypass-list=<-loopback>",
        // no WebRTC UDP around the proxy
        "--force-webrtc-ip-handling-policy=disable_non_proxied_udp",
    )

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        open.forEach { runCatching { it.close() } }
        pool.shutdownNow()
    }

    private fun serve(client: Socket) {
        var remote: Socket? = null
        try {
            serveInner(client) { remote = it }
        } catch (_: IOException) {
        } catch (e: RuntimeException) {
            log("connection error: ${e.javaClass.simpleName}")
        } finally {
            if (remote == null) closeQuietly(client)
        }
    }

    /** The SOCKS5 exchange, then the relay; [opened] gets the destination socket once it exists (the caller closes on errors). */
    private fun serveInner(client: Socket, opened: (Socket) -> Unit) {
        client.soTimeout = HANDSHAKE_TIMEOUT_MS
        val input = DataInputStream(client.getInputStream())
        val out = client.getOutputStream()
        // greeting: VER, NMETHODS, METHODS -> "no authentication"
        if (input.readUnsignedByte() != 5) return
        val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        if (0 !in methods.map { it.toInt() }) {
            out.write(byteArrayOf(5, 0xFF.toByte()))
            return
        }
        out.write(byteArrayOf(5, 0))
        // request: VER, CMD, RSV, ATYP, ADDR, PORT
        if (input.readUnsignedByte() != 5) return
        val cmd = input.readUnsignedByte()
        input.readUnsignedByte()
        val target = readTarget(input)
        if (cmd != 1) {
            reply(out, REP_COMMAND_NOT_SUPPORTED)
            return
        }
        val remote = try {
            connect(target)
        } catch (e: Refused) {
            refused += "${target.host}:${target.port}"
            log("refused ${target.host}:${target.port} (${e.message})")
            reply(out, REP_NOT_ALLOWED)
            return
        } catch (e: IOException) {
            reply(out, REP_HOST_UNREACHABLE)
            return
        }
        open += remote
        opened(remote)
        reply(out, REP_OK)
        client.soTimeout = 0
        // both directions' streams are taken here, before either side can close the sockets
        val fromRemote = remote.getInputStream()
        val toClient = client.getOutputStream()
        val fromClient = client.getInputStream()
        val toRemote = remote.getOutputStream()
        pool.execute { pump(fromRemote, toClient, client, remote) }
        pump(fromClient, toRemote, client, remote)
    }

    private class Target(val host: String, val literal: InetAddress?, val port: Int)

    private class Refused(message: String) : IOException(message)

    private fun readTarget(input: DataInputStream): Target {
        val (host, literal) = when (input.readUnsignedByte()) {
            1 -> ByteArray(4).also { input.readFully(it) }.let { val a = InetAddress.getByAddress(it); a.hostAddress to a }
            3 -> {
                val name = String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) }, Charsets.US_ASCII)
                name to literalOf(name)
            }
            4 -> ByteArray(16).also { input.readFully(it) }.let { val a = InetAddress.getByAddress(it); a.hostAddress to a }
            else -> throw IOException("bad address type")
        }
        return Target(host, literal, input.readUnsignedShort())
    }

    /** The destination, after the checks; throws [Refused] for a local / private one. */
    private fun connect(t: Target): Socket {
        if (t.port !in 1..65535) throw Refused("bad port")
        if (refuseLocalNames && t.literal == null && isLocalName(t.host)) throw Refused("local name")
        t.literal?.let { if (!allowed(it, t.port)) throw Refused("local address") }
        val up = upstream
        if (up != null) {
            if (upstreamRejectedCredentials) throw IOException("the outbound proxy refused its password")
            fun openUpstream(): Socket = when (up.kind) {
                ProxyKind.SOCKS5 -> viaSocks5(up, t)
                else -> viaHttpConnect(up, t)
            }
            if (up.hasCredentials && !authVerified.get()) {
                synchronized(authLock) {
                    if (upstreamRejectedCredentials) throw IOException("the outbound proxy refused its password")
                    val sock = openUpstream()
                    authVerified.set(true)
                    return sock
                }
            }
            return openUpstream()
        }
        val addresses = t.literal?.let { listOf(it) } ?: try {
            resolve(t.host)
        } catch (e: IOException) {
            throw IOException("dns: ${e.message}")
        }
        if (addresses.isEmpty()) throw IOException("dns: no address")
        // every answer must be allowed: one private answer is enough to refuse (as the extensions' own fetch)
        addresses.firstOrNull { !allowed(it, t.port) }?.let { throw Refused("resolves to a local address") }
        var last: IOException? = null
        for (a in addresses) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(a, t.port), CONNECT_TIMEOUT_MS)
                return s
            } catch (e: IOException) {
                closeQuietly(s)
                last = e
            }
        }
        throw last ?: IOException("connect failed")
    }

    private fun viaSocks5(up: ProxyEndpoint, t: Target): Socket {
        val s = Socket()
        try {
            s.connect(InetSocketAddress(up.host, up.port), CONNECT_TIMEOUT_MS)
            s.soTimeout = HANDSHAKE_TIMEOUT_MS
            val out = s.getOutputStream()
            val input = DataInputStream(s.getInputStream())
            val withAuth = up.hasCredentials
            out.write(if (withAuth) byteArrayOf(5, 1, 2) else byteArrayOf(5, 1, 0))
            if (input.readUnsignedByte() != 5) throw IOException("upstream is not SOCKS5")
            when (input.readUnsignedByte()) {
                0 -> Unit
                2 -> {
                    val u = up.username.orEmpty().toByteArray()
                    val p = up.password.orEmpty().toByteArray()
                    out.write(byteArrayOf(1, u.size.toByte()) + u + byteArrayOf(p.size.toByte()) + p)
                    input.readUnsignedByte()
                    if (input.readUnsignedByte() != 0) {
                        credentialsRejected()
                        throw IOException("upstream SOCKS5 refused the credentials")
                    }
                }
                else -> throw IOException("upstream SOCKS5 wants an unsupported method")
            }
            // the name goes to the proxy unresolved (remote DNS)
            val name = t.host.toByteArray(Charsets.US_ASCII)
            val addr = when {
                t.literal is Inet4Address -> byteArrayOf(1) + t.literal.address
                t.literal != null -> byteArrayOf(4) + t.literal.address
                else -> byteArrayOf(3, name.size.toByte()) + name
            }
            out.write(byteArrayOf(5, 1, 0) + addr + byteArrayOf((t.port shr 8).toByte(), t.port.toByte()))
            if (input.readUnsignedByte() != 5) throw IOException("upstream is not SOCKS5")
            val rep = input.readUnsignedByte()
            input.readUnsignedByte()
            when (input.readUnsignedByte()) {
                1 -> input.skipNBytes(4)
                3 -> input.skipNBytes(input.readUnsignedByte().toLong())
                4 -> input.skipNBytes(16)
            }
            input.skipNBytes(2)
            if (rep != 0) throw IOException("upstream SOCKS5 refused the connection ($rep)")
            s.soTimeout = 0
            return s
        } catch (e: IOException) {
            closeQuietly(s)
            throw e
        }
    }

    private fun viaHttpConnect(up: ProxyEndpoint, t: Target): Socket {
        val s = Socket()
        try {
            s.connect(InetSocketAddress(up.host, up.port), CONNECT_TIMEOUT_MS)
            s.soTimeout = HANDSHAKE_TIMEOUT_MS
            val authority = if (t.host.contains(':')) "[${t.host}]:${t.port}" else "${t.host}:${t.port}"
            val req = StringBuilder("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n")
            if (up.hasCredentials) {
                val token = Base64.getEncoder().encodeToString("${up.username}:${up.password.orEmpty()}".toByteArray())
                req.append("Proxy-Authorization: Basic $token\r\n")
            }
            req.append("\r\n")
            s.getOutputStream().write(req.toString().toByteArray(Charsets.ISO_8859_1))
            val status = readHeaderBlock(s.getInputStream()).lineSequence().firstOrNull().orEmpty()
            val code = status.split(' ').getOrNull(1)?.toIntOrNull()
            // one try only: 407 (wrong or missing password) fails the connection instead of asking again
            if (code == 407) credentialsRejected()
            if (code != 200) throw IOException("upstream proxy answered ${code ?: "?"} to CONNECT")
            s.soTimeout = 0
            return s
        } catch (e: IOException) {
            closeQuietly(s)
            throw e
        }
    }

    private fun credentialsRejected() {
        if (!upstreamRejectedCredentials) log("the outbound proxy refused its user / password: the hidden browser stays offline until the proxy setting changes")
        upstreamRejectedCredentials = true
    }

    private fun readHeaderBlock(input: InputStream): String {
        val buf = StringBuilder()
        while (buf.length < MAX_HEADER_BYTES) {
            val c = input.read()
            if (c < 0) throw EOFException("upstream proxy closed")
            buf.append(c.toChar())
            if (buf.endsWith("\r\n\r\n")) return buf.toString()
        }
        throw IOException("upstream proxy header too long")
    }

    private fun pump(from: InputStream, to: OutputStream, a: Socket, b: Socket) {
        try {
            from.transferTo(to)
        } catch (_: IOException) {
        } finally {
            closeQuietly(a)
            closeQuietly(b)
        }
    }

    private fun reply(out: OutputStream, rep: Int) {
        out.write(byteArrayOf(5, rep.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        out.flush()
    }

    private fun closeQuietly(s: Socket) {
        open -= s
        runCatching { s.close() }
    }

    companion object {
        private const val REP_OK = 0
        private const val REP_NOT_ALLOWED = 2
        private const val REP_HOST_UNREACHABLE = 4
        private const val REP_COMMAND_NOT_SUPPORTED = 7
        private const val HANDSHAKE_TIMEOUT_MS = 15_000
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val MAX_HEADER_BYTES = 16 * 1024

        /** Names that are this PC or the LAN by definition (never resolved, never reached). */
        fun isLocalName(host: String): Boolean {
            val h = host.lowercase(Locale.ROOT).trimEnd('.')
            return h.isEmpty() || h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") ||
                h.endsWith(".internal") || h.endsWith(".lan") || h.endsWith(".home.arpa") || !h.contains('.') && literalOf(h) == null
        }

        /** An IPv4 / IPv6 literal (never a DNS query); null for a name. */
        fun literalOf(host: String): InetAddress? {
            val h = host.removePrefix("[").removeSuffix("]")
            val v4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
            return when {
                ':' in h -> runCatching { InetAddress.getByName("[$h]") }.getOrNull()
                v4.matches(h) -> runCatching { InetAddress.getByName(h) }.getOrNull()
                else -> null
            }
        }
    }
}
