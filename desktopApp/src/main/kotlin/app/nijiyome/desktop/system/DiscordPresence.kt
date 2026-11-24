// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.model.NowPlayingInfo
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.Locale
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** What Discord shows: nothing, the app open, a Details page, or an episode (Tatakai Next's `PresenceVm`). */
sealed interface PresenceState {
    data object Clear : PresenceState

    data object Browsing : PresenceState

    /** A Details page is on screen (Tatakai Next's `PresenceVm::Viewing`). */
    data class Viewing(val title: String) : PresenceState

    data class Watching(val info: NowPlayingInfo) : PresenceState
}

/** The texts of the activity (English, or Spanish on a Spanish system; Discord shows them to the user's friends as they are). */
class PresenceTexts(private val spanish: Boolean = Locale.getDefault().language == "es") {
    val browsing get() = if (spanish) "Explorando anime" else "Browsing anime"
    val browsingState get() = if (spanish) "En Nijiyome" else "In Nijiyome"
    fun viewing(title: String) = if (spanish) "Viendo la ficha de $title" else "Looking at $title"
    val viewingState get() = if (spanish) "Ficha" else "Details page"
    fun watching(title: String) = if (spanish) "Viendo $title" else "Watching $title"
    fun paused(title: String) = if (spanish) "En pausa · $title" else "Paused · $title"
    val playingSmall get() = if (spanish) "Reproduciendo" else "Playing"
    val pausedSmall get() = if (spanish) "En pausa" else "Paused"
}

/**
 * The `SET_ACTIVITY` payload for [state] at [nowMs] (pure, unit-tested); null for [PresenceState.Clear]. Texts are cut to
 * Discord's 2..128 characters. While playing, the start / end timestamps give Discord's elapsed / remaining bar.
 */
fun activityJson(state: PresenceState, nowMs: Long, texts: PresenceTexts = PresenceTexts()): String? {
    fun fit(s: String): String = s.take(128).padEnd(2, ' ')
    return when (state) {
        PresenceState.Clear -> null
        PresenceState.Browsing -> obj(
            "details" to str(fit(texts.browsing)),
            "state" to str(fit(texts.browsingState)),
            "assets" to obj("large_image" to str(LOGO_KEY), "large_text" to str("Nijiyome")),
        )
        is PresenceState.Viewing -> obj(
            "details" to str(fit(texts.viewing(state.title))),
            "state" to str(fit(texts.viewingState)),
            "assets" to obj("large_image" to str(LOGO_KEY), "large_text" to str("Nijiyome")),
        )
        is PresenceState.Watching -> {
            val i = state.info
            val details = if (i.paused) texts.paused(i.title) else texts.watching(i.title)
            val assets = mutableListOf(
                "large_image" to str(i.artworkUrl?.takeIf { it.startsWith("https://") } ?: LOGO_KEY),
                "large_text" to str(fit(i.title.ifBlank { "Nijiyome" })),
                "small_image" to str(if (i.paused) PAUSE_KEY else PLAY_KEY),
                "small_text" to str(if (i.paused) texts.pausedSmall else texts.playingSmall),
            )
            val fields = mutableListOf(
                "type" to "3", // Watching
                "details" to str(fit(details)),
                "state" to str(fit(i.subtitle?.takeIf { it.isNotBlank() } ?: "Nijiyome")),
                "assets" to obj(*assets.toTypedArray()),
            )
            if (!i.paused) {
                val start = nowMs - i.positionMs
                val stamps = mutableListOf("start" to start.toString())
                if (i.durationMs > i.positionMs) stamps += "end" to (nowMs + (i.durationMs - i.positionMs)).toString()
                fields += "timestamps" to obj(*stamps.toTypedArray())
            }
            obj(*fields.toTypedArray())
        }
    }
}

/** The command frame that sets ([activity] non-null) or clears the activity of process [pid]. */
fun setActivityCommand(pid: Long, activity: String?, nonce: String = UUID.randomUUID().toString()): String =
    obj(
        "cmd" to str("SET_ACTIVITY"),
        "args" to obj("pid" to pid.toString(), "activity" to (activity ?: "null")),
        "nonce" to str(nonce),
    )

fun handshake(clientId: String): String = obj("v" to "1", "client_id" to str(clientId))

private const val LOGO_KEY = "logo"
private const val PLAY_KEY = "play_icon"
private const val PAUSE_KEY = "pause_icon"

private fun obj(vararg fields: Pair<String, String>): String = fields.joinToString(",", "{", "}") { (k, v) -> "${str(k)}:$v" }

private fun str(s: String): String = buildString {
    append('"')
    for (c in s) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}

/** One frame of Discord's local RPC: little-endian opcode, little-endian length, UTF-8 JSON. */
object DiscordFrames {
    const val HANDSHAKE = 0
    const val FRAME = 1
    const val CLOSE = 2

    fun write(out: OutputStream, op: Int, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        val head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).array()
        out.write(head + body)
        out.flush()
    }

    /** (opcode, JSON); throws on a closed connection. Frames over 64 KiB are refused. */
    fun read(input: InputStream): Pair<Int, String> {
        val head = input.readExactly(8)
        val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val op = bb.int
        val len = bb.int
        if (len < 0 || len > 64 * 1024) throw IOException("bad frame length $len")
        return op to String(input.readExactly(len), Charsets.UTF_8)
    }

    private fun InputStream.readExactly(n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(out, off, n - off)
            if (r < 0) throw IOException("closed")
            off += r
        }
        return out
    }
}

/** A connection to the Discord client. */
interface DiscordConnection : Closeable {
    val input: InputStream
    val output: OutputStream
}

/**
 * Where Discord listens: `discord-ipc-0..9` under `$XDG_RUNTIME_DIR` (also its Flatpak and Snap subfolders) or `$TMPDIR` / `/tmp`
 * on Linux, the named pipe `\\.\pipe\discord-ipc-N` on Windows.
 */
fun discordEndpoints(windows: Boolean, env: (String) -> String? = System::getenv): List<String> {
    if (windows) return (0..9).map { "\\\\.\\pipe\\discord-ipc-$it" }
    val bases = listOfNotNull(env("XDG_RUNTIME_DIR"), env("TMPDIR"), env("TMP"), env("TEMP"), "/tmp").distinct()
    val subdirs = listOf("", "app/com.discordapp.Discord/", "snap.discord/", ".flatpak/com.discordapp.Discord/xdg-run/")
    return bases.flatMap { base -> subdirs.flatMap { sub -> (0..9).map { "$base/${sub}discord-ipc-$it" } } }
}

/** Opens the first endpoint that answers. */
fun connectDiscord(endpoints: List<String>, windows: Boolean): DiscordConnection? {
    for (path in endpoints) {
        try {
            if (windows) {
                val pipe = RandomAccessFile(path, "rw")
                return object : DiscordConnection {
                    override val input: InputStream = Channels.newInputStream(pipe.channel)
                    override val output: OutputStream = Channels.newOutputStream(pipe.channel)
                    override fun close() = pipe.close()
                }
            }
            if (!File(path).exists()) continue
            val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
            ch.connect(UnixDomainSocketAddress.of(path))
            return object : DiscordConnection {
                override val input: InputStream = Channels.newInputStream(ch)
                override val output: OutputStream = Channels.newOutputStream(ch)
                override fun close() = ch.close()
            }
        } catch (_: Exception) {
            continue
        }
    }
    return null
}

/**
 * Discord Rich Presence (Tatakai Next's `discord.rs`): one worker thread owns the IPC connection, [update] only queues the wanted
 * state (never blocks the UI) and the worker applies the newest one. When Discord is not running it retries every [retryMs] while
 * something should be shown. Nothing is sent without a [clientId] (the Discord application of this build).
 */
class DiscordPresence(
    private val clientId: String,
    private val connect: () -> DiscordConnection?,
    private val pid: Long = ProcessHandle.current().pid(),
    private val retryMs: Long = 30_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val texts: PresenceTexts = PresenceTexts(),
    private val log: (String) -> Unit = {},
    /** Longest wait for Discord's answer to one frame; then the connection is dropped (retried later). */
    private val readTimeoutMs: Long = 10_000,
) : Closeable {
    private val queue = LinkedBlockingQueue<Any>()
    private val shutdown = Any()

    /** The open connection, so [close] can drop it even while the worker waits on a read. */
    @Volatile
    private var live: DiscordConnection? = null

    @Volatile
    private var worker: Thread? = null

    @Synchronized
    fun update(state: PresenceState) {
        if (worker == null) {
            if (state == PresenceState.Clear) return
            worker = Thread(::loop, "discord-presence").apply {
                isDaemon = true
                start()
            }
        }
        queue.put(state)
    }

    /** Clears the activity when Discord answers in time, disconnects and ends the worker (app exit). */
    @Synchronized
    override fun close() {
        val w = worker ?: return
        worker = null
        queue.put(shutdown)
        // A worker stuck on a read of a client that never answers: wait a moment for the polite clear, then drop the connection.
        w.join(readTimeoutMs.coerceAtMost(CLOSE_GRACE_MS))
        live?.let { runCatching { it.close() } }
        w.interrupt()
    }

    private fun loop() {
        var conn: DiscordConnection? = null
        var wanted: PresenceState = PresenceState.Clear
        var applied = true
        try {
            while (true) {
                val next = if (applied || wanted == PresenceState.Clear) queue.take() else queue.poll(retryMs, TimeUnit.MILLISECONDS)
                if (next === shutdown) break
                if (next is PresenceState) {
                    var latest: PresenceState = next
                    var stop = false
                    while (true) {
                        val more = queue.poll() ?: break
                        if (more === shutdown) stop = true else if (more is PresenceState) latest = more
                    }
                    if (stop) break
                    if (latest == wanted && applied && latest !is PresenceState.Watching) continue
                    wanted = latest
                }
                val (c, ok) = apply(conn, wanted)
                // Nothing to show (the setting turned off): no reason to keep Discord's connection open.
                conn = if (wanted == PresenceState.Clear && c != null) { runCatching { c.close() }; null } else c
                live = conn
                applied = ok
            }
        } catch (_: InterruptedException) {
            // closing
        } finally {
            conn?.let { c ->
                runCatching { send(c, null) }
                runCatching { c.close() }
            }
            live = null
        }
    }

    private fun apply(current: DiscordConnection?, state: PresenceState): Pair<DiscordConnection?, Boolean> {
        val activity = activityJson(state, clock(), texts)
        if (activity == null) {
            if (current != null && runCatching { send(current, null) }.isFailure) {
                runCatching { current.close() }
                return null to true
            }
            return current to true
        }
        val conn = current ?: open() ?: return null to false
        return if (runCatching { send(conn, activity) }.isSuccess) {
            conn to true
        } else {
            log("discord: setting the presence failed; reconnecting later")
            runCatching { conn.close() }
            null to false
        }
    }

    /** Runs [block] on [conn], closing the connection when Discord takes longer than [readTimeoutMs] (pipes and Unix sockets have no read timeout). */
    private fun <T> withDeadline(conn: DiscordConnection, block: () -> T): T {
        val timer = DEADLINES.schedule({ runCatching { conn.close() } }, readTimeoutMs, TimeUnit.MILLISECONDS)
        try {
            return block()
        } finally {
            timer.cancel(false)
        }
    }

    private fun open(): DiscordConnection? {
        val conn = connect() ?: return null.also { log("discord is not running; presence not shown") }
        live = conn
        return try {
            DiscordFrames.write(conn.output, DiscordFrames.HANDSHAKE, handshake(clientId))
            val (op, body) = withDeadline(conn) { DiscordFrames.read(conn.input) }
            if (op != DiscordFrames.FRAME || !body.contains("\"READY\"")) throw IOException("handshake refused: $body".take(200))
            conn
        } catch (e: Exception) {
            log("discord handshake failed: ${e.message}")
            runCatching { conn.close() }
            null
        }
    }

    private fun send(conn: DiscordConnection, activity: String?) {
        DiscordFrames.write(conn.output, DiscordFrames.FRAME, setActivityCommand(pid, activity))
        val (op, body) = withDeadline(conn) { DiscordFrames.read(conn.input) }
        if (op == DiscordFrames.CLOSE || body.contains("\"evt\":\"ERROR\"")) throw IOException("discord refused the activity: $body".take(200))
    }

    private companion object {
        const val CLOSE_GRACE_MS = 2_000L
        val DEADLINES = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "discord-deadlines").apply { isDaemon = true }
        }
    }
}
