// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Hito 6 (D1, sistema-ajustes-1): what reads as online from the kernel's route tables. */
class DesktopConnectivityTest {
    private val header = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n"

    @Test
    fun aDefaultRouteOutsideLoopbackIsOnline() {
        val wifi = header + "wlan0\t00000000\t0102A8C0\t0003\t0\t0\t600\t00000000\t0\t0\t0\n" +
            "wlan0\t0002A8C0\t00000000\t0001\t0\t0\t600\t00FFFFFF\t0\t0\t0\n"
        assertEquals(true, DesktopConnectivity.hasDefaultRoute(wifi, ""))
    }

    @Test
    fun onlyABridgeOrNothingIsOffline() {
        // docker0 is up with its subnet, but there is no default route: the cable is out.
        val bridge = header + "docker0\t000011AC\t00000000\t0001\t0\t0\t0\t0000FFFF\t0\t0\t0\n"
        val v6 = "00000000000000000000000000000000 00 00000000000000000000000000000000 00 00000000000000000000000000000000 ffffffff 00000001 00000000 00200200       lo\n" +
            "fe800000000000000000000000000000 40 00000000000000000000000000000000 00 00000000000000000000000000000000 00000100 00000001 00000000 00000001  docker0\n"
        assertEquals(false, DesktopConnectivity.hasDefaultRoute(bridge, v6))
        assertEquals(false, DesktopConnectivity.hasDefaultRoute(header, null), "an empty table (a network namespace without network)")
    }

    @Test
    fun anIpv6OnlyDefaultRouteIsOnline() {
        val v6 = "00000000000000000000000000000000 00 00000000000000000000000000000000 00 fe800000000000000000000000000001 00000400 00000003 00000000 00450003   enp3s0\n"
        assertEquals(true, DesktopConnectivity.hasDefaultRoute(header, v6))
    }

    @Test
    fun unreadableTablesAreUnknown() {
        assertNull(DesktopConnectivity.hasDefaultRoute(null, null))
    }
}
