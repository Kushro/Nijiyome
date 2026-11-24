// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.link.LinkCrypto
import app.nijiyome.link.LinkResult
import app.nijiyome.link.PairingClient
import app.nijiyome.link.PairingPayload
import app.nijiyome.link.PayloadResult
import app.nijiyome.model.LinkDeviceHistoryMode
import app.nijiyome.model.LinkServerState
import app.nijiyome.server.ext.FakeCatalog
import app.nijiyome.server.link.Announcement
import app.nijiyome.server.link.Announcer
import app.nijiyome.server.link.Discovery
import app.nijiyome.server.link.TestHost
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DesktopServers], the service behind Settings › Nijiyome Link on desktop: off by default, started and stopped by the setting,
 * announced over mDNS while it runs (an in-memory announcer: nothing goes on the network), pairing with QR, devices and the
 * extension server. Everything listens on 127.0.0.1 with ephemeral ports.
 */
class DesktopServersTest {
    private val cleanup = mutableListOf<() -> Unit>()

    @AfterTest
    fun tearDown() = cleanup.reversed().forEach { runCatching { it() } }

    private class Announced(val addresses: List<InetAddress>, val name: String, val id: String, val port: Int, val fp: String) {
        @Volatile var closed = false
    }

    private class MemAnnouncer : Announcer {
        val all: MutableList<Announced> = Collections.synchronizedList(mutableListOf())
        override fun announce(addresses: List<InetAddress>, serverName: String, instanceId: String, port: Int, fingerprintHex: String): Announcement {
            val a = Announced(addresses, serverName, instanceId, port, fingerprintHex).also(all::add)
            return Announcement { a.closed = true }
        }
        fun live() = all.filter { !it.closed }
    }

    private fun servers(settings: ServerSettings?, announcer: Announcer = MemAnnouncer()): Pair<DesktopServers, File> {
        val dir = Files.createTempDirectory("desktop-servers").toFile().also { d -> cleanup += { d.deleteRecursively() } }
        if (settings != null) ServerSettings.save(File(dir, "link/server-settings.json"), settings)
        val s = DesktopServers(dir, { TestHost() }, FakeCatalog(), announcer = announcer, bindIps = listOf("127.0.0.1"))
        cleanup += { s.close() }
        s.start()
        return s to dir
    }

    private fun DesktopServers.await(what: String, f: (LinkServerState) -> Boolean): LinkServerState {
        repeat(200) {
            val st = state.value
            if (f(st)) return st
            Thread.sleep(50)
        }
        error("timed out waiting for $what: ${state.value}")
    }

    @Test
    fun offByDefaultNothingListensNorIsAnnounced() {
        val mdns = MemAnnouncer()
        val (s, dir) = servers(null, mdns)
        Thread.sleep(500)
        val st = s.state.value
        assertFalse(st.enabled)
        assertFalse(st.running)
        assertFalse(st.extRunning)
        assertTrue(mdns.all.isEmpty())
        assertTrue(st.defaultServerName.startsWith("Nijiyome ("))
        assertFalse(File(dir, "link/ca.der").exists(), "no identity is created while it is off")
    }

    @Test
    fun theSettingStartsAnnouncesAndStopsTheServer() {
        val mdns = MemAnnouncer()
        val (s, dir) = servers(ServerSettings(port = 0, extPort = 0), mdns)
        s.setEnabled(true)
        val st = s.await("running") { it.running }
        assertTrue(st.port > 0)
        assertNotNull(st.fingerprint)
        assertTrue(ServerSettings.load(File(dir, "link/server-settings.json")).linkEnabled, "the switch is persisted")
        val a = mdns.live().single()
        assertEquals(st.port, a.port)
        assertEquals(listOf(InetAddress.getByName("127.0.0.1")), a.addresses)
        assertTrue(a.name.startsWith("Nijiyome ("))
        assertEquals(64, a.fp.length)
        assertEquals("1", Discovery.txtRecords(a.name, a.id, a.fp).toMap()["v"])

        s.setAnnounce(false)
        waitUntil { mdns.live().isEmpty() }
        s.setAnnounce(true)
        waitUntil { mdns.live().size == 1 }

        s.setServerName("Sala")
        waitUntil { mdns.live().singleOrNull()?.name == "Sala" }
        assertEquals("Sala", s.await("renamed") { it.serverName == "Sala" && it.running }.serverName)

        s.setEnabled(false)
        s.await("stopped") { !it.running }
        assertTrue(mdns.live().isEmpty(), "the announcement ends with the server")
        assertFalse(ServerSettings.load(File(dir, "link/server-settings.json")).linkEnabled)
    }

    @Test
    fun anEnabledServerStartsWithTheAppAndPairsAndRevokesAPhone() {
        val (s, _) = servers(ServerSettings(linkEnabled = true, port = 0, announce = false, extPort = 0))
        val st = s.await("running at start") { it.running }
        s.openPairing()
        val pairing = s.await("pairing window") { it.pairing != null }.pairing!!
        assertEquals(10, pairing.code.length)
        val qr = assertNotNull(pairing.qr)
        assertTrue(qr.size >= 21 && qr.cells.size == qr.size * qr.size)
        assertTrue(pairing.uri.startsWith(PairingPayload.PREFIX) && "p=${st.port}" in pairing.uri)

        val payload = (PairingPayload.parse(pairing.uri) as PayloadResult.Valid).payload
        val grant = runBlocking { PairingClient().pair(payload, "Pixel 8", LinkCrypto.generateKeyPair()) }
        val deviceId = (grant as LinkResult.Ok).value.deviceId
        val dev = s.await("device listed") { it.devices.isNotEmpty() && it.pairing == null }.devices.single()
        assertEquals(deviceId, dev.id)
        assertEquals("Pixel 8", dev.name)
        assertEquals(LinkDeviceHistoryMode.FULL, dev.historyMode)

        s.setHistoryMode(deviceId, LinkDeviceHistoryMode.PAUSED)
        s.await("mode") { it.devices.singleOrNull()?.historyMode == LinkDeviceHistoryMode.PAUSED }
        s.rename(deviceId, "Teléfono")
        s.await("renamed") { it.devices.singleOrNull()?.name == "Teléfono" }
        s.revoke(deviceId)
        s.await("revoked") { it.devices.isEmpty() }

        s.openPairing()
        s.await("window again") { it.pairing != null }
        s.closePairing()
        assertNull(s.await("window closed") { it.pairing == null }.pairing)
    }

    @Test
    fun theExtensionServerHasItsOwnSwitchAndCode() {
        val (s, _) = servers(ServerSettings(port = 0, extPort = 0))
        s.setExtensionServerEnabled(true)
        val st = s.await("ext running") { it.extRunning }
        assertFalse(st.running, "the Link server stays off")
        assertTrue(st.extUrls.all { it.startsWith("http://127.0.0.1:") }, "${st.extUrls}")
        s.openExtensionPairing()
        val p = s.await("ext code") { it.extPairing != null }.extPairing!!
        assertEquals(6, p.code.length)
        assertTrue(p.link.startsWith("nijiyome://extensionserver/connect?"), p.link)
        assertNotNull(p.qr)
        s.setExtensionServerEnabled(false)
        s.await("ext stopped") { !it.extRunning }
    }

    @Test
    fun displayFingerprintGroupsTheFirstSixteenDigits() {
        assertEquals("7A3F 91C2 0E5D B8A4", DesktopServers.displayFingerprint("7a3f91c20e5db8a4ffff"))
    }

    private fun waitUntil(f: () -> Boolean) {
        repeat(200) {
            if (f()) return
            Thread.sleep(50)
        }
        error("timed out")
    }
}
