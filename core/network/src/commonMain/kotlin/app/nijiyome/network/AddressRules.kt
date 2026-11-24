// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Addresses no request of Nijiyome's own fetchers may reach (moved unchanged from the server's SafeHttp in 1.1 so the desktop hidden
 * browser applies the same rules): loopback, RFC 1918, link-local / cloud metadata, CGNAT, multicast, broadcast, unspecified,
 * documentation and reserved ranges; for IPv6 also ULA, link-local, site-local and IPv4 embedded as mapped / NAT64 / 6to4.
 */
object AddressRules {
    fun isBlockedV4(o: ByteArray): Boolean {
        val a = o[0].toInt() and 0xff
        val b = o[1].toInt() and 0xff
        val c = o[2].toInt() and 0xff
        val d = o[3].toInt() and 0xff
        return a == 127 || // loopback
            a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || // RFC 1918
            (a == 169 && b == 254) || // link-local, cloud metadata
            a in 224..239 || // multicast
            (a == 255 && b == 255 && c == 255 && d == 255) || // broadcast
            a == 0 || // 0.0.0.0/8 (unspecified included)
            (a == 192 && b == 0 && c == 2) || (a == 198 && b == 51 && c == 100) || (a == 203 && b == 0 && c == 113) || // documentation
            (a == 100 && (b and 0xC0) == 64) || // 100.64.0.0/10 CGNAT (also Alibaba metadata 100.100.100.200)
            (a == 192 && b == 0 && c == 0) || // IETF protocol assignments
            (a == 198 && (b and 0xFE) == 18) || // 198.18.0.0/15 benchmarking
            a >= 240 // reserved
    }

    fun isBlockedV6(o: ByteArray): Boolean {
        val s = IntArray(8) { ((o[it * 2].toInt() and 0xff) shl 8) or (o[it * 2 + 1].toInt() and 0xff) }
        val v4 = { hi: Int, lo: Int -> byteArrayOf((hi shr 8).toByte(), hi.toByte(), (lo shr 8).toByte(), lo.toByte()) }
        // IPv4-mapped ::ffff:a.b.c.d
        if (s[0] == 0 && s[1] == 0 && s[2] == 0 && s[3] == 0 && s[4] == 0 && s[5] == 0xffff) return isBlockedV4(v4(s[6], s[7]))
        // 64:ff9b::/96 (NAT64)
        if (s[0] == 0x0064 && s[1] == 0xff9b && s[2] == 0 && s[3] == 0 && s[4] == 0 && s[5] == 0) return isBlockedV4(v4(s[6], s[7]))
        // 6to4 2002::/16
        if (s[0] == 0x2002) return isBlockedV4(v4(s[1], s[2]))
        val loopback = s.take(7).all { it == 0 } && s[7] == 1
        val unspecified = s.all { it == 0 }
        return loopback || unspecified ||
            (s[0] and 0xFF00) == 0xFF00 || // multicast
            (s[0] and 0xFE00) == 0xFC00 || // ULA fc00::/7
            (s[0] and 0xFFC0) == 0xFE80 || // link-local
            (s[0] and 0xFFC0) == 0xFEC0 || // site-local (deprecated)
            (s[0] == 0x2001 && s[1] == 0x0db8) // documentation
    }

    fun isBlocked(ip: InetAddress): Boolean = when (ip) {
        is Inet4Address -> isBlockedV4(ip.address)
        is Inet6Address -> isBlockedV6(ip.address)
        else -> true
    }
}
