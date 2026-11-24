// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/pairing.rs (AGPL-3.0, same author), adapted from Enigmata-Temple pairing.rs.
package app.nijiyome.server.link

import app.nijiyome.link.LinkCsr
import app.nijiyome.link.PairingPayload as PhonePayload
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/** What the PC shows (text and QR). Holds the one-time code: [toString] never prints it. */
class PairingPayload(
    val host: String,
    val port: Int,
    val caFingerprint: String,
    val code: String,
    val expiresAt: Instant,
    val serverName: String,
) {
    /** `nijiyome-link:v1?h=<ipv4>&p=<port>&ca=<sha256 hex>&c=<code>&x=<unix exp>&n=<name url-encoded>` (the phone's parser). */
    fun uri(): String = "${PhonePayload.PREFIX}h=$host&p=$port&ca=$caFingerprint&c=$code&x=${expiresAt.epochSecond}&n=${pctEncode(serverName)}"

    override fun toString(): String = "PairingPayload(host=$host, port=$port)"

    companion object {
        /** RFC 3986 percent-encoding (everything but unreserved characters), so a space is `%20`, never `+`. */
        fun pctEncode(s: String): String = buildString {
            for (b in s.toByteArray()) {
                val c = b.toInt() and 0xff
                if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code) {
                    append(c.toChar())
                } else {
                    append('%').append("%02X".format(c))
                }
            }
        }
    }
}

sealed interface WindowStatus {
    data class Open(val expiresAt: Instant, val attemptsLeft: Int) : WindowStatus
    data object Closed : WindowStatus
    data object Exhausted : WindowStatus
}

class PairingException(val error: ErrorCode) : Exception(error.code)

/** What `POST /v1/pairing` grants. Holds a certificate, not secrets; [toString] keeps it short. */
class IssuedDevice(val deviceId: String, val clientCertPem: String, val caCertPem: String) {
    override fun toString(): String = "IssuedDevice(device=$deviceId)"
}

/**
 * The pairing window: 5 minutes, single use, at most 5 failed attempts, a 10-character Crockford code. Validation order: window
 * -> CSR -> code (a bad CSR never consumes an attempt). The window closes BEFORE the certificate is issued, so there is never a
 * second redeem. The code is never logged.
 */
class PairingWindow(
    private val identity: Identity,
    private val devices: DeviceRegistry,
    private val host: String,
    private val port: Int,
    private val serverName: String,
) {
    private sealed interface State {
        data object Closed : State
        class Open(val code: String, val expiresAt: Instant, var failures: Int, val maxAttempts: Int) : State
        data object Exhausted : State
    }

    private var state: State = State.Closed

    /** Opens a fresh window (replacing any previous one) with a new code. */
    @Synchronized
    fun open(ttl: Duration = DEFAULT_TTL, maxAttempts: Int = DEFAULT_MAX_ATTEMPTS, now: Instant = Instant.now()): PairingPayload {
        val code = generateCode()
        val expiresAt = Instant.ofEpochSecond(now.epochSecond + ttl.seconds)
        state = State.Open(code, expiresAt, 0, maxAttempts.coerceAtLeast(1))
        return PairingPayload(host, port, identity.caFingerprint, code, expiresAt, serverName)
    }

    @Synchronized
    fun close() {
        if (state is State.Open) state = State.Closed
    }

    @Synchronized
    fun status(now: Instant = Instant.now()): WindowStatus = when (val s = state) {
        is State.Open -> if (now < s.expiresAt) WindowStatus.Open(s.expiresAt, s.maxAttempts - s.failures) else WindowStatus.Closed
        State.Exhausted -> WindowStatus.Exhausted
        State.Closed -> WindowStatus.Closed
    }

    fun redeem(code: String, csrPem: String, deviceName: String, now: Instant = Instant.now()): IssuedDevice {
        synchronized(this) {
            when (val s = state) {
                is State.Open -> if (now >= s.expiresAt) {
                    state = State.Closed
                    throw PairingException(ErrorCode.PAIRING_CLOSED)
                }
                State.Exhausted -> throw PairingException(ErrorCode.PAIRING_ATTEMPTS_EXCEEDED)
                State.Closed -> throw PairingException(ErrorCode.PAIRING_CLOSED)
            }
            if (Identity.csrPublicKey(csrPem) == null) throw PairingException(ErrorCode.CSR_INVALID)
            val s = state as State.Open
            val given = PhonePayload.normalizeCode(code)
            if (given == null || !constantTimeEquals(s.code, given)) {
                s.failures++
                if (s.failures >= s.maxAttempts) {
                    state = State.Exhausted
                    throw PairingException(ErrorCode.PAIRING_ATTEMPTS_EXCEEDED)
                }
                throw PairingException(ErrorCode.PAIRING_CODE_INVALID)
            }
            state = State.Closed // single use
        }
        val cert = try {
            identity.issueClientFromCsr(csrPem, now)
        } catch (_: Exception) {
            throw PairingException(ErrorCode.INTERNAL)
        }
        try {
            devices.add(
                Device(
                    id = cert.deviceId,
                    name = LinkCsr.sanitizeDeviceName(deviceName),
                    fingerprint = cert.fingerprint,
                    createdAt = now.epochSecond,
                    notAfter = cert.notAfter.epochSecond,
                    // `POST /v1/pairing` only accepts `platform: "android"`; the User-Agent refines it later
                    platform = "android",
                    lastSeenAt = now.epochSecond,
                ),
            )
        } catch (_: Exception) {
            throw PairingException(ErrorCode.INTERNAL)
        }
        return IssuedDevice(cert.deviceId, cert.pem, identity.caPem)
    }

    companion object {
        const val CODE_LENGTH = 10
        val DEFAULT_TTL: Duration = Duration.ofMinutes(5)
        const val DEFAULT_MAX_ATTEMPTS = 5
        private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private val random = SecureRandom()

        fun generateCode(): String = buildString { repeat(CODE_LENGTH) { append(CROCKFORD[random.nextInt(32)]) } }

        private fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var acc = 0
            for (i in a.indices) acc = acc or (a[i].code xor b[i].code)
            return acc == 0
        }
    }
}
