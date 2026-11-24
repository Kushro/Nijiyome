// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.link

import app.nijiyome.model.DiscoveredPc
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder
import java.util.Locale

// Parity wave B (docs/redesign/16-parity-wave-b-contract-android.md §3.11 / §7.4, worker r; checklist link.discovery): the PC announces
// `_nijiyome-link._tcp` over mDNS (desktop tk-link/src/discovery.rs, CANONICAL: TXT `v=1`, `name`, `id` = instance_id, `fp` = sha256 hex
// of the pinned certificate). The phone lists what it finds; "Emparejar" asks for the one-time code the PC shows, shows the
// fingerprint first (TOFU) and runs the normal pairing with a `nijiyome-link:v1` payload built from the announcement.

/** One PC found on the network: the public [pc] plus the TXT fingerprint used as the pin. */
data class FoundPc(val pc: DiscoveredPc, val fingerprint: String)

/** Source of announced PCs (NsdManager on device, fakes in tests). */
fun interface PcDiscovery {
    fun pcs(): Flow<List<FoundPc>>
}

/** Pure parsing / payload rules of the discovery (JVM-tested). */
object DiscoveryWire {
    const val SERVICE_TYPE = "_nijiyome-link._tcp"
    const val TXT_VERSION = "v"
    const val TXT_NAME = "name"
    const val TXT_INSTANCE_ID = "id"
    const val TXT_FINGERPRINT = "fp"
    private val HEX64 = Regex("[0-9a-fA-F]{64}")
    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    /** A resolved announcement, or null when it is not a v1 Nijiyome Link with an IPv4 host and a valid fingerprint. */
    fun parse(serviceName: String, host: String?, port: Int, txt: Map<String, String?>): FoundPc? {
        if (txt[TXT_VERSION] != "1") return null
        val fp = txt[TXT_FINGERPRINT]?.takeIf { HEX64.matches(it) }?.lowercase(Locale.ROOT) ?: return null
        val h = host?.takeIf { IPV4.matches(it) } ?: return null
        if (port !in 1..65535) return null
        val name = txt[TXT_NAME]?.takeIf { it.isNotBlank() } ?: serviceName
        return FoundPc(DiscoveredPc(name = name.take(64), host = h, port = port, instanceId = txt[TXT_INSTANCE_ID]?.takeIf { it.isNotBlank() }), fp)
    }

    /** The pairing payload of a discovered PC + the code the user typed (normalised); null when the code is not 10 Crockford symbols. */
    fun payload(found: FoundPc, code: String, nowEpochSeconds: Long): String? {
        val normalized = PairingPayload.normalizeCode(code) ?: return null
        val n = URLEncoder.encode(found.pc.name, "UTF-8").replace("+", "%20")
        return "${PairingPayload.PREFIX}h=${found.pc.host}&p=${found.pc.port}&ca=${found.fingerprint}&c=$normalized&x=${nowEpochSeconds + PAYLOAD_TTL_SECS}&n=$n"
    }

    /** Display form of the fingerprint (`7A3F 91C2 …`). */
    fun displayFingerprint(fp: String): String = fp.uppercase(Locale.ROOT).chunked(4).joinToString(" ")

    private const val PAYLOAD_TTL_SECS = 300L
}
