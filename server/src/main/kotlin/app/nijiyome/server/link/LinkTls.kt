// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/server.rs (TLS + connection loop; AGPL-3.0, same author). rustls replaced by the JDK's
// TLS through Netty's SslHandler.
package app.nijiyome.server.link

import app.nijiyome.link.LinkCrypto
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelPipeline
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.netty.handler.ssl.SslProvider
import io.netty.util.AttributeKey
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.X509TrustManager

/** Resolved at the TLS handshake from the presented client certificate. */
data class ConnInfo(val hasCert: Boolean, val deviceId: String?)

val CONN_INFO: AttributeKey<ConnInfo> = AttributeKey.valueOf("nijiyome.link.conn")

/**
 * Accepts a client certificate only when it was issued and signed by this PC's CA, is valid now and carries EKU clientAuth. No
 * certificate at all is accepted too (only `POST /v1/pairing` serves without one; every other route checks [ConnInfo]). An expired
 * certificate fails the handshake with the `certificate_expired` alert the phone maps to "pair again".
 */
class ClientCaTrustManager(private val ca: X509Certificate, private val now: () -> Date = { Date() }) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no client certificate")
        if (leaf.issuerX500Principal != ca.subjectX500Principal) throw CertificateException("client certificate not issued by this PC")
        try {
            leaf.verify(ca.publicKey)
        } catch (_: Exception) {
            throw CertificateException("client certificate not signed by this PC")
        }
        try {
            leaf.checkValidity(now())
        } catch (e: CertificateExpiredException) {
            throw CertificateException("client certificate expired", CertPathValidatorException("expired", e, null, -1, CertPathValidatorException.BasicReason.EXPIRED))
        } catch (e: CertificateNotYetValidException) {
            throw CertificateException("client certificate not yet valid", CertPathValidatorException("not yet valid", e, null, -1, CertPathValidatorException.BasicReason.NOT_YET_VALID))
        }
        if (leaf.extendedKeyUsage?.contains(EKU_CLIENT_AUTH) != true) throw CertificateException("not a client certificate")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException("server certificates are not accepted here")

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(ca)

    companion object {
        const val EKU_CLIENT_AUTH = "1.3.6.1.5.5.7.3.2"
    }
}

/** TLS 1.3 + 1.2, ECDSA P-256, ALPN http/1.1, optional client certificates, no session resumption cache to speak of. */
fun linkSslContext(identity: Identity): SslContext = SslContextBuilder.forServer(identity.serverKey, *identity.serverChain())
    .sslProvider(SslProvider.JDK)
    .protocols("TLSv1.3", "TLSv1.2")
    .clientAuth(ClientAuth.OPTIONAL)
    .trustManager(ClientCaTrustManager(identity.ca))
    .applicationProtocolConfig(
        ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            ApplicationProtocolNames.HTTP_1_1,
        ),
    )
    .sessionCacheSize(1)
    .sessionTimeout(1)
    .build()

/**
 * The live TLS connections of the Link listeners: at most [maxConnections], each tagged with its [ConnInfo] once the handshake
 * ends, and closed at once when its device is revoked (hot revocation).
 */
class LinkConnections(private val devices: DeviceRegistry, private val maxConnections: Int = MAX_CONNECTIONS) {
    private val open = AtomicInteger(0)
    private val byDevice = ConcurrentHashMap<String, MutableSet<Channel>>()

    init {
        devices.onRevoked(::closeDevice)
    }

    /** The pipeline step of each accepted connection: TLS in front of Ktor's HTTP codec, then the tagging handler. */
    fun install(pipeline: ChannelPipeline, ssl: SslContext) {
        val ch = pipeline.channel()
        if (open.incrementAndGet() > maxConnections) {
            open.decrementAndGet()
            ch.close()
            return
        }
        ch.closeFuture().addListener { open.decrementAndGet() }
        val handler = SslHandler(ssl.newEngine(ch.alloc())).apply { handshakeTimeoutMillis = HANDSHAKE_TIMEOUT_MS }
        pipeline.addFirst("ssl", handler)
        pipeline.addAfter("ssl", "link-conn", Tagger())
    }

    private val spared = ConcurrentHashMap.newKeySet<Channel>()

    /** [channel] survives the next revocation of its device (it still has to carry the answer of `DELETE /v1/devices/self`). */
    fun spare(channel: Channel) {
        spared += channel
    }

    fun closeDevice(id: String) {
        byDevice.remove(id)?.forEach { ch -> if (!spared.remove(ch)) ch.close() }
    }

    fun openCount(): Int = open.get()

    private inner class Tagger : ChannelInboundHandlerAdapter() {
        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
            if (evt is SslHandshakeCompletionEvent) {
                if (!evt.isSuccess) {
                    ctx.close()
                } else {
                    val session = ctx.pipeline().get(SslHandler::class.java)?.engine()?.session
                    val leaf = runCatching { session?.peerCertificates?.firstOrNull() as? X509Certificate }.getOrNull()
                    val info = if (leaf == null) {
                        ConnInfo(hasCert = false, deviceId = null)
                    } else {
                        ConnInfo(hasCert = true, deviceId = devices.byFingerprint(LinkCrypto.fingerprint(leaf))?.id)
                    }
                    ctx.channel().attr(CONN_INFO).set(info)
                    info.deviceId?.let { id ->
                        byDevice.computeIfAbsent(id) { ConcurrentHashMap.newKeySet() }.add(ctx.channel())
                        ctx.channel().closeFuture().addListener { byDevice[id]?.remove(ctx.channel()) }
                        // revoked between the lookup and now: drop it
                        if (devices.get(id)?.revokedAt != null) ctx.close()
                    }
                }
            }
            super.userEventTriggered(ctx, evt)
        }
    }

    companion object {
        const val MAX_CONNECTIONS = 64
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
    }
}
