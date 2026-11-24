// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationCall
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytes
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.BindException
import java.net.InetSocketAddress

/** A body larger than the route's cap. */
class BodyTooLarge : Exception("body too large")

/**
 * One Ktor + Netty server: every request goes to [handler] (no routing plugin; the handlers dispatch on method and path
 * themselves, so the error answers of each contract are exact). Bound to exactly the given addresses, never a wildcard unless the
 * caller passes one.
 */
class KtorServer private constructor(private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>, val addresses: List<InetSocketAddress>) {
    val port: Int get() = addresses.first().port

    fun stop(graceMs: Long = 200, timeoutMs: Long = 2_000) {
        server.stop(graceMs, timeoutMs)
    }

    companion object {
        /**
         * Starts and waits until every connector is bound. [port] 0 = ephemeral (the same one for every address after the first).
         * [halfClose]: keep a connection open after the client shut down its write side (FFmpeg's HLS muxer does after a PUT body).
         * [pipeline] runs for each accepted connection after Ktor's HTTP handlers are in place (the Link server adds TLS first).
         * @throws BindException when an address cannot be bound (port in use, address not on this PC).
         */
        fun start(
            hosts: List<String>,
            port: Int,
            halfClose: Boolean = false,
            writeTimeoutSecs: Int = 60,
            threads: Int = 4,
            pipeline: ChannelPipeline.() -> Unit = {},
            handler: suspend (ApplicationCall) -> Unit,
        ): KtorServer {
            require(hosts.isNotEmpty())
            var bindPort = port
            var firstServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
            if (port == 0) {
                // resolve the ephemeral port with the first address, then bind the others to the same one
                val probe = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName(hosts.first()))
                bindPort = probe.localPort
                probe.close()
            }
            val server = embeddedServer(
                Netty,
                configure = {
                    for (h in hosts) {
                        connector {
                            host = h
                            this.port = bindPort
                        }
                    }
                    connectionGroupSize = 1
                    workerGroupSize = threads
                    callGroupSize = threads
                    responseWriteTimeoutSeconds = writeTimeoutSecs
                    shutdownGracePeriod = 200
                    shutdownTimeout = 2_000
                    configureBootstrap = {
                        if (halfClose) childOption(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    }
                    channelPipelineConfig = pipeline
                },
            ) {
                intercept(ApplicationCallPipeline.Call) {
                    handler(context)
                    finish()
                }
            }
            firstServer = server
            try {
                server.start(wait = false)
            } catch (e: Exception) {
                runCatching { firstServer.stop(0, 0) }
                throw (e as? BindException) ?: BindException(e.message ?: "bind failed").also { it.initCause(e) }
            }
            val bound = runBlocking { server.engine.resolvedConnectors() }.map { InetSocketAddress(it.host, it.port) }
            return KtorServer(server, bound)
        }
    }
}

/** The Netty channel of [call] (per-connection data of the Link server lives there). */
fun ApplicationCall.nettyChannel(): Channel? = (this as? NettyApplicationCall)?.context?.channel()

/** Reads the whole request body within [timeoutMs], at most [limit] bytes ([BodyTooLarge] above). */
suspend fun ApplicationCall.readBody(limit: Int, timeoutMs: Long): ByteArray = withTimeout(timeoutMs) {
    readLimited(receiveChannel(), limit)
}

suspend fun readLimited(ch: ByteReadChannel, limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (true) {
        val n = ch.readAvailable(buf, 0, buf.size)
        if (n < 0) break
        if (n == 0) {
            if (ch.isClosedForRead) break
            ch.awaitContent()
            continue
        }
        if (out.size() + n > limit) throw BodyTooLarge()
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

suspend fun ApplicationCall.respondStatus(status: HttpStatusCode) {
    respondBytes(ByteArray(0), ContentType.Application.OctetStream, status)
}
