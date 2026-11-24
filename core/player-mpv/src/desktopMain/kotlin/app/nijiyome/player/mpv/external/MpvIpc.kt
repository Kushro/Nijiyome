// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicLong

// Hito 4, paso D: the transport of mpv's JSON IPC (`--input-ipc-server`), JDK only: a unix domain socket on Linux (JDK 16+
// UnixDomainSocketAddress), a named pipe on Windows (opened as a file, the way Tatakai Next's tk-player/src/external.rs did).
//
// A connection is used either for reading or for request / reply, never for both at once: `Channels.newInputStream` of a blocking
// socket channel takes the same lock for reads and writes, and a synchronous Windows pipe handle cannot be written while a read is
// pending. mpv serves several clients, so the session opens two (events, commands).

/** One line-oriented connection to mpv's IPC endpoint. */
internal interface IpcConnection : Closeable {
    /** The next line without its newline; null at the end of the stream. Blocks. */
    fun readLine(): String?

    /** Writes [line] and a newline, flushed. */
    fun writeLine(line: String)
}

/**
 * A fresh endpoint name, unique per process and per call and not guessable (a random token): a socket path in this user's private
 * folder on Linux ([PrivateRuntimeDir]), a pipe name on Windows.
 */
internal fun newIpcEndpoint(windows: Boolean = isWindowsHost(), dir: File? = null): String {
    val name = "nijiyome-mpv-${ProcessHandle.current().pid()}-${ENDPOINT_SEQ.incrementAndGet()}-${PrivateRuntimeDir.token()}"
    if (windows) return "\\\\.\\pipe\\$name"
    return File(dir ?: PrivateRuntimeDir.default, "$name.sock").absolutePath
}

private val ENDPOINT_SEQ = AtomicLong()

/** Opens [endpoint]; throws while mpv has not created it yet. */
internal fun openIpc(endpoint: String, windows: Boolean = isWindowsHost()): IpcConnection =
    if (windows) PipeConnection(endpoint) else UnixConnection(endpoint)

/** Removes a stale socket file (mpv deletes its own; a crash leaves it). Named pipes vanish with their server. */
internal fun cleanupIpc(endpoint: String, windows: Boolean = isWindowsHost()) {
    if (!windows) runCatching { File(endpoint).delete() }
}

internal fun isWindowsHost(): Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

private class UnixConnection(path: String) : IpcConnection {
    private val channel: SocketChannel = SocketChannel.open(UnixDomainSocketAddress.of(path))
    private val reader = BufferedReader(InputStreamReader(Channels.newInputStream(channel), Charsets.UTF_8))
    private val out: OutputStream = Channels.newOutputStream(channel)

    override fun readLine(): String? = reader.readLine()

    override fun writeLine(line: String) {
        out.write((line + "\n").toByteArray(Charsets.UTF_8))
        out.flush()
    }

    override fun close() {
        runCatching { channel.close() }
    }
}

/** The Windows side: the pipe opened as a file, read byte by byte up to each newline. */
// PENDIENTE-ESCRITORIO: mpv externo en Windows sin probar
private class PipeConnection(name: String) : IpcConnection {
    private val file = RandomAccessFile(name, "rw")

    override fun readLine(): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = file.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8)
            if (b == '\n'.code) return buf.toString(Charsets.UTF_8).trimEnd('\r')
            buf.write(b)
        }
    }

    override fun writeLine(line: String) {
        file.write((line + "\n").toByteArray(Charsets.UTF_8))
    }

    override fun close() {
        runCatching { file.close() }
    }
}
