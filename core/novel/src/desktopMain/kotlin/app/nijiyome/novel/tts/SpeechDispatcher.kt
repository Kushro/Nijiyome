// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.TtsSettings
import app.nijiyome.model.TtsVoice
import app.nijiyome.novel.NarratorEngineStatus
import app.nijiyome.novel.NarratorProblem
import app.nijiyome.novel.TtsException
import app.nijiyome.novel.TtsFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One SSIP reply: its code and the text of every line (continuation lines first, the final line last). */
internal data class SsipReply(val code: Int, val lines: List<String>) {
    val ok: Boolean get() = code in 200..299
}

/**
 * Speech Dispatcher's SSIP (text protocol over its Unix socket; `spd-say` speaks it too): replies are `NNN-text` continuation lines
 * ended by a `NNN text` line; 7xx replies are events (with `SET self NOTIFICATION all on`): 701 BEGIN, 702 END, 703 CANCELED,
 * 704 PAUSED, 705 RESUMED, each with the message id and the client id on two continuation lines.
 */
internal object Ssip {
    /** Reads one reply from [reader]; null at the end of the stream. */
    fun read(reader: BufferedReader): SsipReply? {
        val lines = ArrayList<String>()
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.length < 3) continue
            val code = line.substring(0, 3).toIntOrNull() ?: continue
            val text = if (line.length > 4) line.substring(4) else ""
            lines += text
            if (line.length == 3 || line[3] == ' ') return SsipReply(code, lines)
        }
    }

    /** A value for a one-line SSIP command: no CR / LF or other control characters (they would end or forge a command). */
    fun argument(value: String): String = value.filterNot { it.isISOControl() }.trim()

    /** The data block of `SPEAK`: CRLF lines, a leading dot doubled, ended by a line with a single dot. */
    fun speakData(text: String): String = buildString {
        for (line in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            append(if (line.startsWith(".")) ".$line" else line).append("\r\n")
        }
        append(".\r\n")
    }

    /** `LIST SYNTHESIS_VOICES` lines (`name\tlanguage\tvariant`) → voices; the app language first is the caller's job. */
    fun voices(reply: SsipReply): List<TtsVoice> = reply.lines.dropLast(1).mapNotNull { line ->
        val cols = line.split('\t').map { it.trim() }
        val name = cols.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val lang = cols.getOrNull(1)?.takeIf { it.isNotEmpty() && it != "none" }
        TtsVoice(name, if (lang != null) "${displayLanguage(lang)} · $name" else name, lang)
    }

    private fun displayLanguage(tag: String): String = runCatching { Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault()) }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: tag

    /** The socket of the session's speech-dispatcher: `SPEECHD_ADDRESS=unix_socket:<path>`, else the XDG runtime one. */
    fun socketPath(env: Map<String, String> = System.getenv()): File? {
        env["SPEECHD_ADDRESS"]?.trim()?.let { addr ->
            if (addr.startsWith("unix_socket:")) return File(addr.removePrefix("unix_socket:"))
            if (addr.startsWith("inet_socket")) return null
        }
        val runtime = env["XDG_RUNTIME_DIR"]?.takeIf { it.isNotBlank() } ?: return null
        return File(runtime, "speech-dispatcher/speechd.sock")
    }
}

/** A connection to a speech-dispatcher socket (one command at a time; events go to [onEvent] from the reader thread). */
internal class SsipConnection private constructor(
    private val channel: SocketChannel,
    private val onEvent: (code: Int, messageId: String?) -> Unit,
) {
    private val out: OutputStream = Channels.newOutputStream(channel)
    private val replies = LinkedBlockingQueue<SsipReply>()

    @Volatile
    var open: Boolean = true
        private set

    private val reader = Thread({
        val input = BufferedReader(InputStreamReader(Channels.newInputStream(channel), Charsets.UTF_8))
        try {
            while (true) {
                val reply = Ssip.read(input) ?: break
                if (reply.code in 700..799) onEvent(reply.code, reply.lines.firstOrNull()) else replies.put(reply)
            }
        } catch (_: IOException) {
        } finally {
            open = false
            replies.put(SsipReply(CLOSED, listOf("closed")))
        }
    }, "ssip-reader").apply { isDaemon = true; start() }

    /** Sends [line] (and the SPEAK [data] block when the server asks for it) and returns the final reply. Blocking. */
    @Synchronized
    fun command(line: String, data: String? = null): SsipReply {
        if (!open) throw IOException("speech-dispatcher connection closed")
        send(line + "\r\n")
        var reply = replies.poll(TIMEOUT_S, TimeUnit.SECONDS) ?: throw IOException("speech-dispatcher did not answer '$line'")
        if (data != null && reply.code == 230) {
            send(data)
            reply = replies.poll(TIMEOUT_S, TimeUnit.SECONDS) ?: throw IOException("speech-dispatcher did not queue the message")
        }
        if (reply.code == CLOSED) throw IOException("speech-dispatcher connection closed")
        return reply
    }

    private fun send(text: String) {
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Says goodbye without waiting for answers (cancels what this client queued, then `QUIT`) and closes the socket; the reader
     * thread ends with it. Safe to call more than once.
     */
    fun quit() {
        if (open) runCatching { synchronized(this) { send("CANCEL self\r\nQUIT\r\n") } }
        close()
    }

    fun close() {
        open = false
        runCatching { channel.close() }
    }

    companion object {
        private const val CLOSED = -1
        private const val TIMEOUT_S = 10L

        fun connect(socket: File, onEvent: (Int, String?) -> Unit): SsipConnection {
            val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
            try {
                ch.connect(UnixDomainSocketAddress.of(socket.toPath()))
            } catch (e: IOException) {
                ch.close()
                throw e
            }
            return SsipConnection(ch, onEvent)
        }
    }
}

/**
 * The Linux system voice through speech-dispatcher (1.1, desktop `novel.tts.system`): voices of its output module, rate and pitch per
 * message, pause / resume of the message being spoken (SSIP `PAUSE` / `RESUME`), the end of each paragraph from its END event.
 * Starts the user's daemon with `speech-dispatcher --spawn` when its socket is not there yet.
 */
internal class SpeechDispatcherVoice(
    private val socket: () -> File? = { Ssip.socketPath() },
    private val spawn: suspend () -> Boolean = ::spawnDaemon,
) : LiveVoice {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Read outside [mutex] by pause / resume / disconnect.
    @Volatile
    private var connection: SsipConnection? = null
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Int>>()
    private val finished = ConcurrentHashMap<String, Int>()

    @Volatile
    private var speakingId: String? = null

    private fun onEvent(code: Int, id: String?) {
        if (id == null) return
        if (code == END || code == CANCELED) {
            waiters.remove(id)?.complete(code) ?: run { finished[id] = code }
        }
    }

    private suspend fun connection(): SsipConnection = mutex.withLock {
        connection?.takeIf { it.open }?.let { return it }
        val path = socket() ?: throw TtsException(TtsFailure.ENGINE_UNAVAILABLE)
        if (!path.exists() && spawn()) {
            var waited = 0
            while (!path.exists() && waited < SPAWN_WAIT_MS) {
                delay(100)
                waited += 100
            }
        }
        withContext(Dispatchers.IO) {
            val c = try {
                SsipConnection.connect(path, ::onEvent)
            } catch (e: IOException) {
                throw TtsException(TtsFailure.ENGINE_UNAVAILABLE, cause = e)
            }
            c.command("SET self CLIENT_NAME user:nijiyome:narrator")
            c.command("SET self NOTIFICATION all on")
            connection = c
            c
        }
    }

    private suspend fun command(line: String, data: String? = null): SsipReply {
        val c = connection()
        return withContext(Dispatchers.IO) {
            try {
                c.command(line, data)
            } catch (e: IOException) {
                c.close()
                throw TtsException(TtsFailure.ENGINE_UNAVAILABLE, cause = e)
            }
        }
    }

    override suspend fun status(): NarratorEngineStatus = try {
        val module = command("GET OUTPUT_MODULE").takeIf { it.ok }?.lines?.firstOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("OK") }
        NarratorEngineStatus(available = true, backend = "speech-dispatcher" + (module?.let { " · $it" } ?: ""))
    } catch (e: TtsException) {
        NarratorEngineStatus(available = false, backend = "speech-dispatcher", problem = NarratorProblem.BROKEN, detail = e.cause?.message)
    }

    override suspend fun voices(settings: TtsSettings): List<TtsVoice> {
        val reply = runCatching { command("LIST SYNTHESIS_VOICES") }.getOrNull()?.takeIf { it.ok } ?: return emptyList()
        val all = Ssip.voices(reply)
        val app = Locale.getDefault().language
        return all.filter { it.lang?.startsWith(app) == true }.ifEmpty { all }.sortedBy { it.label }
    }

    override suspend fun speak(text: String, settings: TtsSettings, speed: Float) {
        val s = settings.sanitized()
        command("SET self RATE ${ssipOffset(speed)}")
        command("SET self PITCH ${ssipOffset(s.pitch)}")
        val voice = s.voice?.let(Ssip::argument)?.takeIf { it.isNotEmpty() }
        if (voice != null) command("SET self SYNTHESIS_VOICE $voice") else command("SET self LANGUAGE ${Ssip.argument(Locale.getDefault().language)}")
        val queued = command("SPEAK", Ssip.speakData(text))
        if (!queued.ok) throw TtsException(TtsFailure.ENGINE_UNAVAILABLE)
        val id = queued.lines.firstOrNull { it.isNotBlank() && it.all(Char::isDigit) } ?: throw TtsException(TtsFailure.ENGINE_UNAVAILABLE)
        val conn = connection
        val done = CompletableDeferred<Int>()
        waiters[id] = done
        finished.remove(id)?.let { waiters.remove(id); done.complete(it) }
        speakingId = id
        try {
            done.await()
        } finally {
            speakingId = null
            waiters.remove(id)
            // Stopped before its END: cancel it on the connection it was queued on (never reconnect just for that; a closed one
            // already cancelled it in disconnect).
            if (!done.isCompleted && conn?.open == true) withContext(NonCancellable + Dispatchers.IO) { runCatching { conn.command("CANCEL self") } }
        }
    }

    override fun pause(): Boolean {
        if (speakingId == null || connection?.open != true) return false
        scope.launch { runCatching { command("PAUSE self") } }
        return true
    }

    override fun resume(): Boolean {
        if (speakingId == null || connection?.open != true) return false
        scope.launch { runCatching { command("RESUME self") } }
        return true
    }

    /**
     * Not narrating any more: cancels this client's messages, says `QUIT` and closes the socket (the `ssip-reader` thread ends with
     * it). The next [speak] connects again.
     */
    fun disconnect() {
        val c = connection ?: return
        connection = null
        speakingId = null
        c.quit()
    }

    /** The app is closing: [disconnect] and stop this voice's coroutines. */
    override fun close() {
        disconnect()
        scope.cancel()
    }

    companion object {
        const val END = 702
        const val CANCELED = 703
        private const val SPAWN_WAIT_MS = 3_000

        /** True where speech-dispatcher can be reached or started (its socket exists, or `speech-dispatcher` / `spd-say` is installed). */
        fun present(socket: File? = Ssip.socketPath()): Boolean =
            socket?.exists() == true || Executables.find("speech-dispatcher") != null

        private suspend fun spawnDaemon(): Boolean {
            val exe = Executables.find("speech-dispatcher") ?: return false
            return runCatching { runProcess(listOf(exe.absolutePath, "--spawn"), timeoutMs = 5_000).code == 0 }.getOrDefault(false)
        }
    }
}
