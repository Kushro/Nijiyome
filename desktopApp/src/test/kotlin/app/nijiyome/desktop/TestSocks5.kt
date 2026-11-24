// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A minimal SOCKS5 server on loopback for the tests (RFC 1928 CONNECT, optional RFC 1929 user / password). Every requested target
 * (`host:port` as the client sent it: a domain stays a domain) is recorded and connected to through [route].
 */
class TestSocks5(
    private val user: String? = null,
    private val pass: String? = null,
    private val route: (host: String, port: Int) -> InetSocketAddress,
) : AutoCloseable {
    private val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    val targets = CopyOnWriteArrayList<String>()
    val logins = CopyOnWriteArrayList<String>()

    init {
        thread(isDaemon = true, name = "test-socks5") {
            while (!server.isClosed) {
                val c = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }
    }

    private fun handle(c: Socket) {
        val inp = DataInputStream(c.getInputStream())
        val out = c.getOutputStream()
        require(inp.readUnsignedByte() == 5)
        val methods = ByteArray(inp.readUnsignedByte()).also { inp.readFully(it) }.map { it.toInt() }
        if (user != null) {
            if (2 !in methods) {
                out.write(byteArrayOf(5, 0xFF.toByte()))
                return
            }
            out.write(byteArrayOf(5, 2))
            require(inp.readUnsignedByte() == 1)
            val u = String(ByteArray(inp.readUnsignedByte()).also { inp.readFully(it) })
            val p = String(ByteArray(inp.readUnsignedByte()).also { inp.readFully(it) })
            logins += "$u:$p"
            val ok = u == user && p == pass
            out.write(byteArrayOf(1, if (ok) 0 else 1))
            if (!ok) return
        } else {
            out.write(byteArrayOf(5, 0))
        }
        require(inp.readUnsignedByte() == 5)
        require(inp.readUnsignedByte() == 1) // CONNECT
        inp.readUnsignedByte()
        val host = when (inp.readUnsignedByte()) {
            1 -> ByteArray(4).also { inp.readFully(it) }.joinToString(".") { (it.toInt() and 0xff).toString() }
            3 -> String(ByteArray(inp.readUnsignedByte()).also { inp.readFully(it) })
            else -> error("address type")
        }
        val port = inp.readUnsignedShort()
        targets += "$host:$port"
        val upstream = Socket()
        upstream.connect(route(host, port), 5_000)
        out.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        out.flush()
        val t = thread(isDaemon = true) { pipe(upstream.getInputStream(), out) }
        pipe(inp, upstream.getOutputStream())
        t.join(5_000)
        upstream.close()
    }

    private fun pipe(i: InputStream, o: OutputStream) {
        runCatching {
            val buf = ByteArray(8192)
            while (true) {
                val n = i.read(buf)
                if (n < 0) break
                o.write(buf, 0, n)
                o.flush()
            }
        }
    }

    override fun close() = server.close()
}
