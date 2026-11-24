// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.player.mpv.quality.mpvLogInfo
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// Hito 4, paso D: one running external mpv and its two IPC connections (see MpvIpc.kt). Ported from Tatakai Next
// (tk-player/src/external.rs `ExternalPlayer`): spawn, wait for the endpoint, a reader thread until mpv closes the connection, then
// make sure the process is gone. Nijiyome adds the command connection (request / reply with `request_id`) and a JVM shutdown hook,
// so an mpv window never outlives the app.

internal class ExternalMpvProcess private constructor(
    private val process: Process,
    val endpoint: String,
    private val events: IpcConnection,
    private val commands: IpcConnection,
    private val windows: Boolean,
) {
    private val stopping = AtomicBoolean(false)
    private val closedOnce = AtomicBoolean(false)
    private val listening = AtomicBoolean(false)
    private val freed = AtomicBoolean(false)
    private val nextId = AtomicLong(1)

    /** Writes commands and reads their replies, in order, off the caller's thread. */
    private val commandThread: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mpv-external-cmd").apply { isDaemon = true }
    }

    /** True until the process exited (and its IPC closed). */
    val alive: Boolean get() = process.isAlive && !closedOnce.get()

    /** Exit code once it exited, else null. */
    fun exitCode(): Int? = if (process.isAlive) null else runCatching { process.exitValue() }.getOrNull()

    /** Sends [args] and forgets the reply (still read, so the connection stays in step). */
    fun send(args: List<Any?>) {
        if (!alive) return
        runCatching { commandThread.execute { runCatching { exchange(args) } } }
    }

    /** Sends [args] and waits up to [timeoutMs] for mpv's reply; null when there was none (closed, timeout). */
    fun request(args: List<Any?>, timeoutMs: Long = REQUEST_TIMEOUT_MS): IpcMessage.Reply? {
        if (!alive) return null
        val f = CompletableFuture<IpcMessage.Reply?>()
        runCatching { commandThread.execute { f.complete(runCatching { exchange(args) }.getOrNull()) } }.onFailure { return null }
        return runCatching { f.get(timeoutMs, TimeUnit.MILLISECONDS) }.getOrNull()
    }

    /** One command and its reply on the command connection (command thread only). */
    private fun exchange(args: List<Any?>): IpcMessage.Reply? {
        val id = nextId.getAndIncrement()
        commands.writeLine(ipcCommand(args, id))
        while (true) {
            val line = commands.readLine() ?: return null
            val msg = parseIpcLine(line)
            if (msg is IpcMessage.Reply && msg.requestId == id) return msg
        }
    }

    /**
     * Asks mpv to quit, then kills it if it is still there after [QUIT_GRACE_MS]. Idempotent, any thread, never blocks the caller: all
     * of it runs on its own thread. With [listen] running, the reader thread frees everything and reports the close as usual; without
     * it (a process that was never followed), this frees the connections, the command thread and the socket itself.
     */
    fun stop() {
        if (!stopping.compareAndSet(false, true)) return
        Thread({
            val sent = process.isAlive && runCatching {
                // A one-shot connection: the command thread may be waiting on a reply that never comes.
                openIpc(endpoint, windows).use { it.writeLine(ipcCommand(listOf("quit"))) }
            }.isSuccess
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(if (sent) QUIT_GRACE_MS else 0)
            while (process.isAlive && System.nanoTime() < deadline) Thread.sleep(POLL_MS)
            if (process.isAlive) {
                mpvLogInfo("external mpv did not quit in time; killing it")
                process.destroyForcibly()
                process.waitFor(LINGER_MS, TimeUnit.MILLISECONDS)
            }
            if (!listening.get()) free()
        }, "mpv-external-stop").apply { isDaemon = true }.start()
    }

    /**
     * Starts reading the event connection on its own thread: [onMessage] for every line (the initial property values included: they
     * wait in the connection until now), then [onClosed] once when mpv is gone. Call once, right after [start]; not after [stop].
     */
    fun listen(onMessage: (IpcMessage) -> Unit, onClosed: (exitCode: Int?) -> Unit) {
        if (stopping.get() || !listening.compareAndSet(false, true)) return
        Thread({ readLoop(onMessage, onClosed) }, "mpv-external-ipc").apply { isDaemon = true }.start()
    }

    /** Closes both connections, stops the command thread, removes the socket and forgets the process. Once; the process must be gone. */
    private fun free() {
        if (!freed.compareAndSet(false, true)) return
        events.close()
        commands.close()
        commandThread.shutdownNow()
        cleanupIpc(endpoint, windows)
        LiveProcesses.remove(process)
    }

    /** Reads the event connection until it closes, then makes sure the process ended and calls [onClosed] once. */
    private fun readLoop(onMessage: (IpcMessage) -> Unit, onClosed: (exitCode: Int?) -> Unit) {
        try {
            while (true) {
                val line = events.readLine() ?: break
                val msg = parseIpcLine(line) ?: continue
                runCatching { onMessage(msg) }.onFailure { System.err.println("W/mpv: external IPC handler failed: $it") }
            }
        } catch (_: IOException) {
            // mpv closed the connection (quit, crash, window closed)
        }
        // The connection closes when mpv quits; give it a moment to exit, then make sure it does.
        if (!process.waitFor(LINGER_MS, TimeUnit.MILLISECONDS)) {
            mpvLogInfo("external mpv closed its IPC but kept running; killing it")
            process.destroyForcibly()
            process.waitFor(LINGER_MS, TimeUnit.MILLISECONDS)
        }
        free()
        if (closedOnce.compareAndSet(false, true)) onClosed(exitCode())
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val QUIT_GRACE_MS = 3_000L
        private const val LINGER_MS = 5_000L
        private const val POLL_MS = 50L
        internal const val REQUEST_TIMEOUT_MS = 5_000L

        /**
         * Starts [mpv] with [args] / [env] (changes to the inherited environment; null = remove), waits for its IPC endpoint and
         * subscribes to [OBSERVED_PROPERTIES]. Blocking (usually a few hundred ms): never on the main thread. Throws [IOException]
         * when mpv cannot start, exits first, or never opens the endpoint; the process is gone then.
         */
        fun start(
            mpv: File,
            args: List<String>,
            env: Map<String, String?>,
            endpoint: String,
            windows: Boolean = isWindowsHost(),
        ): ExternalMpvProcess {
            cleanupIpc(endpoint, windows)
            val pb = ProcessBuilder(listOf(mpv.absolutePath) + args).redirectErrorStream(true)
            pb.redirectInput(ProcessBuilder.Redirect.from(File(if (windows) "NUL" else "/dev/null")))
            val penv = pb.environment()
            env.forEach { (k, v) -> if (v == null) penv.remove(k) else penv[k] = v }
            // The app's own working directory (the repository in development, the install folder once packaged) means nothing to mpv.
            System.getProperty("user.home")?.let(::File)?.takeIf { it.isDirectory }?.let(pb::directory)
            val process = try {
                pb.start()
            } catch (e: IOException) {
                throw IOException("cannot start ${mpv.path}: ${e.message}", e)
            }
            LiveProcesses.add(process)
            val tail = OutputTail(process)
            val events = try {
                connect(process, endpoint, windows, tail)
            } catch (e: IOException) {
                process.destroyForcibly()
                LiveProcesses.remove(process)
                cleanupIpc(endpoint, windows)
                throw e
            }
            val commands = try {
                openIpc(endpoint, windows).also { c ->
                    // Replies only on this one: events go to the event connection.
                    c.writeLine(ipcCommand(listOf("disable_event", "all"), 0))
                    while (true) {
                        val m = parseIpcLine(c.readLine() ?: throw IOException("mpv closed its IPC"))
                        if (m is IpcMessage.Reply && m.requestId == 0L) break
                    }
                }
            } catch (e: IOException) {
                events.close()
                process.destroyForcibly()
                LiveProcesses.remove(process)
                cleanupIpc(endpoint, windows)
                throw IOException("mpv IPC: ${e.message}", e)
            }
            return ExternalMpvProcess(process, endpoint, events, commands, windows)
        }

        /** Waits for mpv to open its endpoint, then subscribes to the observed properties (written before anything reads). */
        private fun connect(process: Process, endpoint: String, windows: Boolean, tail: OutputTail): IpcConnection {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CONNECT_TIMEOUT_MS)
            var last: String? = null
            while (true) {
                if (!process.isAlive) {
                    process.waitFor(200, TimeUnit.MILLISECONDS)
                    val detail = tail.text().takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    throw IOException("mpv exited before opening its IPC endpoint (exit ${runCatching { process.exitValue() }.getOrNull()})$detail")
                }
                try {
                    val c = openIpc(endpoint, windows)
                    observeCommands().forEach(c::writeLine)
                    return c
                } catch (e: Exception) {
                    last = e.message
                }
                if (System.nanoTime() > deadline) throw IOException("mpv IPC endpoint never appeared: $last")
                Thread.sleep(POLL_MS)
            }
        }
    }
}

/** The last lines mpv printed (stdout and stderr), for the error of a start that failed. Drains the pipe so mpv never blocks. */
private class OutputTail(process: Process) {
    private val lines = ArrayDeque<String>()

    init {
        Thread({
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(lines) {
                        lines.addLast(line)
                        while (lines.size > MAX_LINES) lines.removeFirst()
                    }
                }
            }
        }, "mpv-external-out").apply { isDaemon = true }.start()
    }

    fun text(): String = synchronized(lines) { lines.joinToString(" | ") }

    private companion object {
        const val MAX_LINES = 8
    }
}

/** Every external mpv this JVM started and that still runs: killed on exit, so a window never outlives the app. */
internal object LiveProcesses {
    private val live = ConcurrentHashMap.newKeySet<Process>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ live.forEach { runCatching { it.destroyForcibly() } } }, "mpv-external-exit"))
    }

    fun add(p: Process) {
        live += p
    }

    fun remove(p: Process) {
        live -= p
    }

    internal fun count(): Int = live.count { it.isAlive }
}
