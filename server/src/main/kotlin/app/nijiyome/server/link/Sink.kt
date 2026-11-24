// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/sink.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import app.nijiyome.server.http.BodyTooLarge
import app.nijiyome.server.http.KtorServer
import app.nijiyome.server.http.readLimited
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytes

/**
 * Loopback PUT receiver of FFmpeg's HLS muxer: `PUT /sink/<session sink token>/<run generation>/<name>`. Bound to 127.0.0.1 only;
 * the token is a random capability. FFmpeg half-closes (shuts its write side) right after the body and only then reads the status,
 * so the connection is kept open after the client's end of stream (Netty `ALLOW_HALF_CLOSURE`).
 */
class Sink private constructor(private val manager: Manager) {
    private lateinit var server: KtorServer

    /** Where it listens (loopback only; checked by the security tests). */
    internal val boundAddresses: List<java.net.InetSocketAddress> get() = server.addresses

    val address: String get() = "127.0.0.1:${server.port}"

    fun stop() = server.stop(0, 500)

    private suspend fun handle(call: ApplicationCall) {
        val m = call.request.httpMethod
        if (m != HttpMethod.Put && m != HttpMethod.Post) return call.reply(HttpStatusCode.MethodNotAllowed)
        val seg = call.request.path().trimStart('/').split('/')
        if (seg.size != 4 || seg[0] != "sink") return call.reply(HttpStatusCode.NotFound)
        val gen = seg[2].toIntOrNull() ?: return call.reply(HttpStatusCode.NotFound)
        val session = manager.bySinkToken(seg[1]) ?: return call.reply(HttpStatusCode.NotFound)
        val body = try {
            readLimited(call.receiveChannel(), MAX_OBJECT_BYTES)
        } catch (_: BodyTooLarge) {
            return call.reply(HttpStatusCode.PayloadTooLarge)
        } catch (_: Exception) {
            return call.reply(HttpStatusCode.BadRequest)
        }
        when (manager.sinkPut(session, gen, seg[3], body)) {
            SinkResult.OK -> call.reply(HttpStatusCode.OK)
            SinkResult.BAD_NAME -> call.reply(HttpStatusCode.BadRequest)
            SinkResult.STALE -> call.reply(HttpStatusCode.Conflict)
        }
    }

    private suspend fun ApplicationCall.reply(s: HttpStatusCode) = respondBytes(ByteArray(0), ContentType.Application.OctetStream, s)

    companion object {
        /** A single fMP4 segment (or init) larger than this is refused. */
        const val MAX_OBJECT_BYTES = 256 * 1024 * 1024

        fun start(manager: Manager): Sink {
            val s = Sink(manager)
            s.server = KtorServer.start(listOf("127.0.0.1"), 0, halfClose = true, writeTimeoutSecs = 120, threads = 4) { s.handle(it) }
            manager.sinkAddress = s.address
            return s
        }
    }
}
