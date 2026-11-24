// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Desktop: [DesktopConnectivity.online], what Tatakai Next's `navigator.onLine` told its title bar (a usable network, not a request
 * to some host). Read every few seconds; any failure to read it counts as online, as on Android.
 */
@Composable
actual fun rememberIsOnline(): Boolean {
    val online by DesktopConnectivity.online.collectAsState()
    return online
}

/**
 * Whether this PC has a network to reach the internet with. Linux: a default route (IPv4 or IPv6) through an interface other than
 * `lo` (`/proc/net/route`, `/proc/net/ipv6_route`), so a cable pulled or Wi-Fi gone reads offline even while a bridge such as
 * `docker0` stays up. Elsewhere, or when those files cannot be read: an interface that is up, not loopback nor virtual, with a
 * routable address. Polled on one daemon thread from the first use; nothing is sent over the network.
 */
object DesktopConnectivity {
    private const val POLL_SECONDS = 5L

    private val state = MutableStateFlow(true)

    val online: StateFlow<Boolean> by lazy {
        state.value = checkNow()
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "connectivity").apply { isDaemon = true } }
            .scheduleWithFixedDelay({ state.value = checkNow() }, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS)
        state.asStateFlow()
    }

    /** One look at the system's network state. */
    fun checkNow(): Boolean = runCatching {
        val linux = System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)
        val fromRoutes = if (linux) hasDefaultRoute(readOrNull("/proc/net/route"), readOrNull("/proc/net/ipv6_route")) else null
        fromRoutes ?: hasUsableInterface()
    }.getOrDefault(true)

    /**
     * A default route outside `lo` in the kernel's route tables ([ipv4] `/proc/net/route`, [ipv6] `/proc/net/ipv6_route`); null
     * when neither table could be read. Pure, unit tested.
     */
    fun hasDefaultRoute(ipv4: String?, ipv6: String?): Boolean? {
        if (ipv4 == null && ipv6 == null) return null
        val v4 = ipv4.orEmpty().lineSequence().drop(1).any { line ->
            val f = line.trim().split(Regex("\\s+"))
            // Iface Destination Gateway Flags …; RTF_UP = 0x1
            f.size > 3 && f[0] != "lo" && f[1] == "00000000" && (f[3].toIntOrNull(16) ?: 0) and 0x1 != 0
        }
        val v6 = ipv6.orEmpty().lineSequence().any { line ->
            val f = line.trim().split(Regex("\\s+"))
            // dest prefix src srcprefix nexthop metric refcnt use flags iface; RTF_UP = 0x1, RTF_REJECT = 0x200
            f.size >= 10 && f[9] != "lo" && f[0].all { it == '0' } && f[1] == "00" &&
                (f[8].toIntOrNull(16) ?: 0).let { flags -> flags and 0x1 != 0 && flags and 0x200 == 0 }
        }
        return v4 || v6
    }

    private fun hasUsableInterface(): Boolean {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return true
        return interfaces.any { ni ->
            ni.isUp && !ni.isLoopback && !ni.isVirtual && ni.inetAddresses.toList().any { a ->
                (a is Inet4Address || a is Inet6Address) && !a.isLoopbackAddress && !a.isLinkLocalAddress
            }
        }
    }

    private fun readOrNull(path: String): String? = runCatching { File(path).readText() }.getOrNull()
}
