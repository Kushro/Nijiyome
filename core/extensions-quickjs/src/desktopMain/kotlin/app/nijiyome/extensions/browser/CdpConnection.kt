// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A CDP protocol error (`{"error": {...}}`), or the browser went away. */
open class CdpException(message: String) : IOException(message)

/** One CDP event; [session] is the flattened session it came from (null = the browser target). */
internal data class CdpEvent(val method: String, val params: JsonObject, val session: String?)

/**
 * How CDP messages travel: text frames of a WebSocket, or `\u0000`-terminated JSON over the debugging pipe. A frame larger than
 * [MAX_FRAME_BYTES] is never held in memory (1.1 review #9): its bytes are dropped while they are read and only its head reaches
 * [CdpConnection], which fails the call it answers.
 */
internal interface CdpTransport : Closeable {
    fun start(sink: FrameSink)

    fun send(text: String)
}

internal interface FrameSink {
    fun frame(text: String)

    /** A frame over the size cap was skipped; [head] is its beginning (enough to find its `"id"`). */
    fun oversized(head: String)

    fun closed(error: Throwable?)
}

/** Largest CDP frame kept (an `evaluate` result is capped at 2 MB of text, which JSON escaping can grow a few times). */
internal const val MAX_FRAME_BYTES: Int = 16 * 1024 * 1024

/** Collects the bytes of one frame up to [max]; past it only the first [HEAD_BYTES] are kept. */
internal class FrameAssembler(private val max: Int = MAX_FRAME_BYTES) {
    private var buf = ByteArrayOutputStream(64 * 1024)
    private val head = ByteArrayOutputStream(HEAD_BYTES)
    private var size = 0L

    /** Bytes held now (tests: never more than [max]). */
    val held: Int get() = buf.size()

    fun add(bytes: ByteArray, off: Int, len: Int) {
        if (head.size() < HEAD_BYTES) head.write(bytes, off, minOf(len, HEAD_BYTES - head.size()))
        size += len
        if (size <= max) {
            buf.write(bytes, off, len)
        } else if (buf.size() > 0) {
            buf = ByteArrayOutputStream(64 * 1024) // drop what was gathered: this frame is skipped
        }
    }

    /** Ends the frame: delivers it, or reports it as oversized. */
    fun finish(sink: FrameSink) {
        if (size <= max) sink.frame(buf.toString(Charsets.UTF_8)) else sink.oversized(head.toString(Charsets.UTF_8))
        if (buf.size() > 256 * 1024) buf = ByteArrayOutputStream(64 * 1024) else buf.reset()
        head.reset()
        size = 0
    }

    companion object {
        const val HEAD_BYTES = 256
    }
}

/** Reads `\u0000`-terminated frames from [input] into [sink] until it ends. */
internal fun readNulFrames(input: InputStream, sink: FrameSink, max: Int = MAX_FRAME_BYTES) {
    val assembler = FrameAssembler(max)
    val chunk = ByteArray(64 * 1024)
    var error: Throwable? = null
    try {
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            var start = 0
            for (i in 0 until n) {
                if (chunk[i].toInt() == 0) {
                    assembler.add(chunk, start, i - start)
                    assembler.finish(sink)
                    start = i + 1
                }
            }
            if (start < n) assembler.add(chunk, start, n - start)
        }
    } catch (e: IOException) {
        error = e
    }
    sink.closed(error)
}

/**
 * `--remote-debugging-pipe`: the browser reads commands from its fd 3 and writes replies to fd 4 (Linux: two FIFOs only this user can
 * open, inside the private profile folder; Windows: two anonymous pipes, see [WindowsPipes]). No TCP port exists at all.
 */
internal class PipeTransport(private val toBrowser: OutputStream, private val fromBrowser: InputStream) : CdpTransport {
    private val closed = AtomicBoolean(false)

    override fun start(sink: FrameSink) {
        val reader = Thread({ readNulFrames(fromBrowser, sink) }, "cdp-pipe-reader")
        reader.isDaemon = true
        reader.start()
    }

    @Synchronized
    override fun send(text: String) {
        if (closed.get()) throw CdpException("the browser is closed")
        toBrowser.write(text.toByteArray(Charsets.UTF_8))
        toBrowser.write(0)
        toBrowser.flush()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { toBrowser.close() }
        runCatching { fromBrowser.close() }
    }
}

/**
 * `--remote-debugging-port=0`: the browser's WebSocket on 127.0.0.1, never through a proxy. The JDK client hands text over in parts,
 * so the size cap applies while the frame arrives. Windows fallback only (see [ChromiumHost]).
 */
internal class WebSocketTransport(private val url: String) : CdpTransport {
    private val client = java.net.http.HttpClient.newBuilder().proxy(java.net.http.HttpClient.Builder.NO_PROXY).build()

    @Volatile private var socket: java.net.http.WebSocket? = null
    private val sendLock = Any()

    override fun start(sink: FrameSink) {
        val done = AtomicBoolean(false)
        val assembler = FrameAssembler()
        socket = client.newWebSocketBuilder().buildAsync(
            java.net.URI.create(url),
            object : java.net.http.WebSocket.Listener {
                override fun onText(webSocket: java.net.http.WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage<*>? {
                    val bytes = data.toString().toByteArray(Charsets.UTF_8)
                    assembler.add(bytes, 0, bytes.size)
                    if (last) assembler.finish(sink)
                    webSocket.request(1)
                    return null
                }

                override fun onClose(webSocket: java.net.http.WebSocket, statusCode: Int, reason: String): java.util.concurrent.CompletionStage<*>? {
                    if (done.compareAndSet(false, true)) sink.closed(null)
                    return null
                }

                override fun onError(webSocket: java.net.http.WebSocket, error: Throwable) {
                    if (done.compareAndSet(false, true)) sink.closed(error)
                }
            },
        ).get(10, TimeUnit.SECONDS)
    }

    override fun send(text: String) {
        val ws = socket ?: throw CdpException("the browser is closed")
        synchronized(sendLock) {
            try {
                ws.sendText(text, true).get(15, TimeUnit.SECONDS)
            } catch (e: Exception) {
                throw CdpException("the browser is closed")
            }
        }
    }

    override fun close() {
        socket?.abort()
    }
}

/** The reply to a call was larger than [MAX_FRAME_BYTES] and was dropped. */
class CdpTooLarge(message: String) : CdpException(message)

/** Request / reply bookkeeping and event fan-out over one [CdpTransport]. Thread-safe; listeners run on the transport's thread. */
internal class CdpConnection(private val transport: CdpTransport) : Closeable {
    private val seq = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val listeners = CopyOnWriteArrayList<(CdpEvent) -> Unit>()
    private val closedFlag = AtomicBoolean(false)
    private val onGone = CopyOnWriteArrayList<() -> Unit>()

    val closed: Boolean get() = closedFlag.get()

    fun start() {
        transport.start(object : FrameSink {
            override fun frame(text: String) = receive(text)

            override fun oversized(head: String) = tooLarge(head)

            override fun closed(error: Throwable?) = gone()
        })
    }

    /** Runs [l] for every event until the returned handle is called. */
    fun listen(l: (CdpEvent) -> Unit): () -> Unit {
        listeners += l
        return { listeners -= l }
    }

    /** Runs [l] once when the browser connection ends (crash, close), until the returned handle is called. */
    fun onClosed(l: () -> Unit): () -> Unit {
        onGone += l
        if (closed) l()
        return { onGone -= l }
    }

    suspend fun call(method: String, params: JsonObject = EMPTY, session: String? = null, timeoutMs: Long = DEFAULT_TIMEOUT_MS): JsonObject {
        if (closed) throw CdpException("the browser is closed")
        val id = seq.getAndIncrement()
        val reply = CompletableDeferred<JsonObject>()
        pending[id] = reply
        try {
            transport.send(frame(id, method, params, session))
            // OrNull: only this call's own deadline becomes an error here; a caller's timeout still cancels as such
            val msg = withTimeoutOrNull(timeoutMs) { reply.await() } ?: throw CdpException("$method timed out")
            (msg["error"] as? JsonObject)?.let { err ->
                throw CdpException("$method: " + ((err["message"] as? JsonPrimitive)?.contentOrNull ?: err.toString()))
            }
            return msg["result"] as? JsonObject ?: EMPTY
        } finally {
            pending.remove(id)
        }
    }

    /** Sends without waiting for the reply (answers to paused requests from a listener). Errors are ignored. */
    fun fire(method: String, params: JsonObject = EMPTY, session: String? = null) {
        if (closed) return
        runCatching { transport.send(frame(seq.getAndIncrement(), method, params, session)) }
    }

    override fun close() {
        runCatching { transport.close() }
        gone()
    }

    private fun frame(id: Int, method: String, params: JsonObject, session: String?): String = buildJsonObject {
        put("id", id)
        put("method", method)
        put("params", params)
        if (session != null) put("sessionId", session)
    }.toString()

    private fun receive(text: String) {
        val msg = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val id = (msg["id"] as? JsonPrimitive)?.intOrNull
        if (id != null) {
            pending[id]?.complete(msg)
            return
        }
        val method = (msg["method"] as? JsonPrimitive)?.contentOrNull ?: return
        val event = CdpEvent(method, msg["params"] as? JsonObject ?: EMPTY, (msg["sessionId"] as? JsonPrimitive)?.contentOrNull)
        for (l in listeners) runCatching { l(event) }
    }

    /** An oversized frame: the call it answers (its `"id"` is near the start) fails; events that large are just dropped. */
    private fun tooLarge(head: String) {
        val id = ID.find(head)?.groupValues?.get(1)?.toIntOrNull() ?: return
        pending[id]?.completeExceptionally(CdpTooLarge("the browser's answer is larger than ${MAX_FRAME_BYTES / (1024 * 1024)} MB"))
    }

    private fun gone() {
        if (!closedFlag.compareAndSet(false, true)) return
        val error = CdpException("the browser is closed")
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        onGone.forEach { runCatching { it() } }
    }

    companion object {
        val EMPTY = JsonObject(emptyMap())
        private val ID = Regex("^\\{\\s*\"id\"\\s*:\\s*(\\d+)")
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
