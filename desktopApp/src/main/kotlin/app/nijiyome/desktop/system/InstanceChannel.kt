// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * How a second launch reaches the running app (Tatakai Next's `single_instance.rs`, over a local socket instead of polled files):
 * the first launch holds the instance lock and listens on a Unix domain socket (Linux, and Windows 10 1803+ through the same JDK
 * API); a later launch that cannot take the lock sends its arguments there and exits. The running app then shows its window and
 * opens what the arguments ask ([LaunchArgs]).
 *
 * Where: `<data>/instance.sock`, in the user's own data folder. When that path is too long for a socket (~108 bytes), the socket
 * goes into a new private folder of the temporary directory (random name, created 0700 before the socket exists) and its path is
 * written to `<data>/instance.path`, which the second launch reads: nobody else can guess, pre-create or reach it.
 *
 * A message is the line `NIJIYOME/1`, one URL-encoded argument per line, then an empty line; the answer is `OK`. Every connection
 * has [READ_TIMEOUT_MS] to send at most [MAX_MESSAGE] bytes in lines of at most [MAX_LINE] characters, and at most [MAX_CLIENTS]
 * are served at once (more are closed at once): a local client that never ends its message cannot hold threads or memory.
 */
object InstanceChannel {
    private const val MAGIC = "NIJIYOME/1"
    private const val MAX_ARGS = 32
    internal const val MAX_LINE = 16 * 1024
    internal const val MAX_MESSAGE = 64 * 1024
    internal const val READ_TIMEOUT_MS = 5_000L
    private const val MAX_CLIENTS = 4
    private const val MAX_SOCKET_PATH = 100
    internal const val POINTER_FILE = "instance.path"
    private const val SOCKET_NAME = "instance.sock"

    private fun short(path: Path): Boolean = path.toString().toByteArray().size <= MAX_SOCKET_PATH

    /** Where a second launch finds the running app: the socket in the data folder, or the path the running app wrote. */
    fun clientPath(dataDir: File): Path? {
        val inData = File(dataDir, SOCKET_NAME).absoluteFile.toPath()
        if (short(inData)) return inData
        val pointed = runCatching { File(dataDir, POINTER_FILE).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Path.of(pointed) }.getOrNull()?.takeIf { it.isAbsolute && it.fileName?.toString() == SOCKET_NAME && short(it) }
    }

    /** The message for [args] (pure, unit-tested). Empty arguments mean nothing and are not sent. */
    fun encode(args: List<String>): String = buildString {
        append(MAGIC).append('\n')
        args.filter { it.isNotEmpty() }.take(MAX_ARGS).forEach { append(URLEncoder.encode(it, "UTF-8")).append('\n') }
        append('\n')
    }

    /** The arguments of a message read line by line; null when it is not one of ours or a line is too long. */
    fun decode(lines: Sequence<String>): List<String>? {
        val it = lines.iterator()
        if (!it.hasNext() || it.next() != MAGIC) return null
        val out = mutableListOf<String>()
        while (it.hasNext()) {
            val line = it.next()
            if (line.isEmpty()) break
            if (line.length > MAX_LINE) return null
            if (out.size < MAX_ARGS) runCatching { URLDecoder.decode(line, "UTF-8") }.getOrNull()?.let(out::add)
        }
        return out
    }

    /** Sends [args] to the app running on [dataDir]. true when it answered. */
    fun send(dataDir: File, args: List<String>, timeoutMs: Long = 5_000): Boolean =
        clientPath(dataDir)?.let { send(it, args, timeoutMs) } ?: false

    internal fun send(path: Path, args: List<String>, timeoutMs: Long = 5_000): Boolean = try {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { ch ->
            ch.connect(UnixDomainSocketAddress.of(path))
            ch.write(ByteBuffer.wrap(encode(args).toByteArray(Charsets.UTF_8)))
            ch.shutdownOutput()
            String(readWithin(ch, 64, timeoutMs), Charsets.UTF_8).startsWith("OK")
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Reads from [ch] until the peer ends, [limit] bytes or (with [untilBlankLine]) the message terminator, closing the channel
     * when [timeoutMs] passes first (a blocking Unix channel has no read timeout of its own). Returns what arrived.
     */
    private fun readWithin(ch: SocketChannel, limit: Int, timeoutMs: Long, untilBlankLine: Boolean = false): ByteArray {
        val timer = WATCHDOG.schedule({ runCatching { ch.close() } }, timeoutMs, TimeUnit.MILLISECONDS)
        val out = ByteArrayOutputStream()
        try {
            val buf = ByteBuffer.allocate(4096)
            while (out.size() < limit) {
                buf.clear()
                buf.limit(minOf(buf.capacity(), limit - out.size()))
                val n = ch.read(buf)
                if (n < 0) break
                out.write(buf.array(), 0, n)
                if (untilBlankLine && out.size() >= 2) {
                    val b = out.toByteArray()
                    if (b[b.size - 1] == '\n'.code.toByte() && b[b.size - 2] == '\n'.code.toByte()) break
                }
            }
        } catch (_: IOException) {
            // closed by the timer, or by the peer
        } finally {
            timer.cancel(false)
        }
        return out.toByteArray()
    }

    /** Where this run listens (see the class comment); also writes or clears the pointer file. */
    internal fun listenPath(dataDir: File, tmpDir: File): Path? {
        val inData = File(dataDir, SOCKET_NAME).absoluteFile.toPath()
        val pointer = File(dataDir, POINTER_FILE)
        if (short(inData)) {
            pointer.delete()
            return inData
        }
        val dir = try {
            Files.createTempDirectory(tmpDir.toPath(), "nijiyome-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        } catch (_: UnsupportedOperationException) {
            Files.createTempDirectory(tmpDir.toPath(), "nijiyome-")
        } catch (e: IOException) {
            System.err.println("instance: no private folder for the socket: ${e.message}")
            return null
        }
        val socket = dir.resolve(SOCKET_NAME)
        if (!short(socket)) {
            System.err.println("instance: the temporary folder's path is too long for a socket")
            return null
        }
        pointer.writeText(socket.toString())
        return socket
    }

    /**
     * Listens for later launches of the app on [dataDir] (replacing a stale socket file left by a crash: the caller holds the instance
     * lock) and calls [onArgs] on a background thread for every message. Null when the socket cannot be opened; the app then runs
     * without forwarding.
     */
    fun listen(dataDir: File, tmpDir: File = File(System.getProperty("java.io.tmpdir")), onArgs: (List<String>) -> Unit): Closeable? {
        val path = listenPath(dataDir, tmpDir) ?: return null
        val server = try {
            Files.deleteIfExists(path)
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path)) }
        } catch (e: IOException) {
            System.err.println("instance: cannot listen on $path: ${e.message}")
            return null
        } catch (e: UnsupportedOperationException) {
            System.err.println("instance: no local sockets here: ${e.message}")
            return null
        }
        // Owner only before the first connection is accepted (accepting starts on the thread below).
        runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
        val slots = Semaphore(MAX_CLIENTS)
        val thread = Thread({
            while (server.isOpen) {
                val client = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                if (!slots.tryAcquire()) {
                    runCatching { client.close() }
                    continue
                }
                val handler = Thread({
                    try {
                        client.use { ch ->
                            val text = String(readWithin(ch, MAX_MESSAGE, READ_TIMEOUT_MS, untilBlankLine = true), Charsets.UTF_8)
                            // Only a whole message counts: one cut off by the time or size limit is refused.
                            val args = if (text.endsWith("\n\n")) decode(text.lineSequence()) else null
                            runCatching { ch.write(ByteBuffer.wrap((if (args != null) "OK\n" else "NO\n").toByteArray())) }
                            if (args != null) onArgs(args)
                        }
                    } catch (_: Exception) {
                        // a client that went away
                    } finally {
                        slots.release()
                    }
                }, "instance-client")
                handler.isDaemon = true
                handler.start()
            }
        }, "instance-channel")
        thread.isDaemon = true
        thread.start()
        return Closeable {
            runCatching { server.close() }
            runCatching { Files.deleteIfExists(path) }
            if (!path.startsWith(dataDir.absoluteFile.toPath())) {
                runCatching { Files.deleteIfExists(path.parent) }
                runCatching { File(dataDir, POINTER_FILE).delete() }
            }
        }
    }

    private val WATCHDOG = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "instance-timeouts").apply { isDaemon = true } }
}
