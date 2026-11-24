// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.extensions.ExternalCandidateSource
import app.nijiyome.extensionserver.DeepLink
import app.nijiyome.extensionserver.ExtensionServerException
import app.nijiyome.extensionserver.ExtensionServerRepository
import app.nijiyome.extensionserver.SecretStoreKeyCipher
import app.nijiyome.extensionserver.desktopExtensionServerModule
import app.nijiyome.model.ExtPasswordResult
import app.nijiyome.model.FirewallResult
import app.nijiyome.model.LinkServerPorts
import app.nijiyome.model.LinkServerState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.SecretStore
import app.nijiyome.server.ext.DEFAULT_EXT_PORT
import app.nijiyome.server.ext.FakeCatalog
import app.nijiyome.server.link.Firewall
import app.nijiyome.server.link.LinkConfig
import app.nijiyome.server.link.LsfgSetup
import app.nijiyome.server.link.TestHost
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop app as a CLIENT of another PC's extension server (Tatakai Next: Extensiones › Servidores): its real Koin bindings
 * ([desktopExtensionServerModule], tokens sealed with a key in the secret store) against the real [DesktopServers] extension server
 * on 127.0.0.1, plus the server settings of Settings › Nijiyome Link (ports, viewer password, pairing window, log, firewall, LSFG).
 * Nothing leaves loopback.
 */
class DesktopExtensionClientTest {
    private val cleanup = mutableListOf<() -> Unit>()

    @AfterTest
    fun tearDown() = cleanup.reversed().forEach { runCatching { it() } }

    private class MemorySecrets : SecretStore {
        val map = ConcurrentHashMap<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override suspend fun clear() = map.clear()
    }

    private fun tmp(prefix: String): File = Files.createTempDirectory(prefix).toFile().also { d -> cleanup += { d.deleteRecursively() } }

    /** "The other PC": the desktop servers with one extension whose `stream` route answers an HLS source. */
    private fun otherPc(
        settings: ServerSettings = ServerSettings(port = 0, extPort = 0),
        firewallButton: Boolean = false,
        firewall: (List<Int>) -> Firewall.Outcome = { Firewall.Outcome.FAILED },
        lsfgDll: () -> String? = { null },
    ): DesktopServers {
        val catalog = FakeCatalog()
        val stream = buildJsonObject {
            put("sources", buildJsonArray {
                add(buildJsonObject {
                    put("url", "https://cdn.example/ep1/index.m3u8")
                    put("type", "hls")
                    put("server", "Vidstream")
                })
            })
        }
        catalog.add(
            """{"id":"toko","name":"Toko","version":"2.1.0","type":"onlinestream","description":"d","permissions":["network:domain:cdn.example"]}""",
            "module.exports = {};",
            mapOf("stream" to stream),
        )
        val dir = tmp("other-pc")
        ServerSettings.save(File(dir, "link/server-settings.json"), settings)
        val s = DesktopServers(
            dir, { TestHost() }, catalog, bindIps = listOf("127.0.0.1"),
            lsfgDll = lsfgDll, firewallButton = firewallButton, firewall = firewall,
        )
        cleanup += { s.close() }
        s.start()
        return s
    }

    /** "This PC": the desktop graph's extension server client over [dataDir] and [secrets]. */
    private fun thisPc(dataDir: File, secrets: SecretStore): KoinApplication {
        val app = koinApplication {
            modules(
                module {
                    single { OkHttpClient() }
                    single<SecretStore> { secrets }
                },
                desktopExtensionServerModule(dataDir),
            )
        }
        cleanup += { app.close() }
        return app
    }

    private fun DesktopServers.await(what: String, f: (LinkServerState) -> Boolean): LinkServerState {
        repeat(300) {
            val st = state.value
            if (f(st)) return st
            Thread.sleep(50)
        }
        error("timed out waiting for $what: ${state.value}")
    }

    private val frieren = Media(
        ref = MediaRef(anilistId = 154587), kind = MediaKind.ANIME, title = "Frieren", titles = emptyMap(), cover = null, banner = null,
        color = null, synopsis = null, genres = emptyList(), episodes = 28, status = null, season = null, year = 2023, score = null,
        sourceProvider = "anilist",
    )

    private fun freePort(): Int = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    @Test
    fun thisPcPairsWithAnotherPcsExtensionServerListsAndUsesItsExtension() = runBlocking {
        val pc = otherPc()
        pc.setExtensionServerEnabled(true)
        pc.await("ext running") { it.extRunning }
        pc.openExtensionPairing()
        val pairing = pc.await("code") { it.extPairing != null }.extPairing!!

        val dataDir = tmp("this-pc")
        val secrets = MemorySecrets()
        val koin = thisPc(dataDir, secrets).koin
        val repo = koin.get<ExtensionServerRepository>()
        // the link the other PC shows (and copies) is what this PC pastes or opens
        val conn = repo.connectWithLink(DeepLink.parse(pairing.link)!!)
        assertTrue(conn.url.startsWith("http://127.0.0.1:"), conn.url)
        assertEquals(listOf(conn.id), repo.servers.value.map { it.id })
        // the other PC lists this one under its own name
        val linked = pc.await("linked device") { it.extDevices.isNotEmpty() }.extDevices.single()
        assertTrue(linked.label.startsWith("Nijiyome ("), linked.label)

        // its extensions, and its sources merged into the source list through ExternalCandidateSource
        assertEquals("toko", repo.extensions(conn.id).single().id)
        val external = koin.getAll<ExternalCandidateSource>()
        assertEquals(1, external.size)
        val candidates = external.single().candidates(frieren, 1)
        assertTrue(candidates.isNotEmpty(), "the extension's stream comes back as a candidate")
        // the stream goes through the other PC's media proxy (its headers stay in that PC's ticket)
        val web = candidates.mapNotNull { it.web?.url }
        assertTrue(web.isNotEmpty() && web.all { it.startsWith("${conn.url}/api/server/v1/media/") }, "$web")

        // the token is sealed at rest, and a later run opens it with the key from the secret store
        val stored = File(dataDir, "extension-servers.bin")
        assertTrue(stored.isFile)
        assertFalse(conn.token in stored.readBytes().decodeToString())
        assertNotNull(secrets.map[SecretStoreKeyCipher.SECRET_KEY])
        val again = thisPc(dataDir, secrets).koin.get<ExtensionServerRepository>()
        repeat(100) { if (!again.loaded.value) Thread.sleep(20) }
        assertEquals(conn.token, again.servers.value.single().token)

        // the other PC's log shows the pairing (no token anywhere in it), newest first
        val logs = pc.await("log") { st -> st.extLogs.any { it.event == "pairing" } }.extLogs
        assertTrue(logs.zipWithNext().all { (a, b) -> a.id > b.id }, "newest first")
        assertFalse(logs.any { e -> listOfNotNull(e.route, e.name, e.error).any { conn.token in it } })

        // revoked on the other PC: this one sees it
        pc.revokeExtensionDevice(linked.id)
        pc.await("revoked") { it.extDevices.isEmpty() }
        val e = runCatching { repo.extensions(conn.id) }.exceptionOrNull() as ExtensionServerException
        assertTrue(e.unauthorized, e.code)
    }

    @Test
    fun theViewerPasswordLetsAnotherPcSignInWithoutACode() = runBlocking {
        val pc = otherPc()
        pc.setExtensionServerEnabled(true)
        val url = pc.await("ext running") { it.extRunning && it.extUrls.isNotEmpty() }.extUrls.first()
        val repo = thisPc(tmp("this-pc"), MemorySecrets()).koin.get<ExtensionServerRepository>()

        // without a password, the other PC only takes paired devices
        val noPassword = runCatching { repo.connectWithPassword(url, "whatever-long-enough") }.exceptionOrNull() as ExtensionServerException
        assertEquals(ExtensionServerException.PAIRING_REQUIRED, noPassword.code)

        pc.setExtensionPassword("short")
        assertEquals(ExtPasswordResult.INVALID, pc.state.value.extPasswordResult)
        pc.openExtensionPairing()
        pc.await("code") { it.extPairing != null }
        pc.setExtensionPassword("correct horse battery")
        val set = pc.await("password set") { it.extPasswordSet && it.extPasswordResult == ExtPasswordResult.SAVED }
        assertNull(set.extPairing, "the open code went with the old password")

        val wrong = runCatching { repo.connectWithPassword(url, "not the password") }.exceptionOrNull() as ExtensionServerException
        assertEquals(ExtensionServerException.INVALID_PASSWORD, wrong.code)
        val conn = repo.connectWithPassword(url, "correct horse battery")
        assertEquals(conn.id, repo.servers.value.single().id)

        pc.setExtensionPassword(null)
        val cleared = pc.await("password cleared") { !it.extPasswordSet && it.extPasswordResult == ExtPasswordResult.CLEARED }
        assertFalse(cleared.extPasswordSet)
        val after = runCatching { repo.connectWithPassword(url, "correct horse battery") }.exceptionOrNull() as ExtensionServerException
        assertEquals(ExtensionServerException.PAIRING_REQUIRED, after.code)
    }

    @Test
    fun closingOrReplacingThePairingWindowKillsItsCode() = runBlocking {
        val pc = otherPc()
        pc.setExtensionServerEnabled(true)
        pc.await("ext running") { it.extRunning }
        val repo = thisPc(tmp("this-pc"), MemorySecrets()).koin.get<ExtensionServerRepository>()

        pc.openExtensionPairing()
        val first = pc.await("code") { it.extPairing != null }.extPairing!!
        pc.openExtensionPairing()
        val second = pc.await("new code") { it.extPairing != null && it.extPairing!!.code != first.code }.extPairing!!
        val replaced = runCatching { repo.connectWithLink(DeepLink.parse(first.link)!!) }.exceptionOrNull() as ExtensionServerException
        assertEquals(ExtensionServerException.PAIRING_EXPIRED, replaced.code)

        pc.closeExtensionPairing()
        assertNull(pc.await("closed") { it.extPairing == null }.extPairing)
        val closed = runCatching { repo.connectWithLink(DeepLink.parse(second.link)!!) }.exceptionOrNull() as ExtensionServerException
        assertEquals(ExtensionServerException.PAIRING_EXPIRED, closed.code)
        assertTrue(repo.servers.value.isEmpty())
    }

    @Test
    fun thePortsAreChosenValidatedAndApplied() {
        val pc = otherPc(ServerSettings(linkEnabled = true, port = 0, announce = false, extPort = 0))
        pc.await("link running") { it.running }

        val link = freePort()
        pc.setPort(link)
        val moved = pc.await("link on $link") { it.running && it.port == link }
        assertTrue(moved.addresses.single().endsWith(":$link"), "${moved.addresses}")

        // refused: privileged, out of range, the other server's port
        pc.setExtensionServerEnabled(true)
        pc.await("ext running") { it.extRunning }
        val ext = freePort()
        pc.setExtensionPort(ext)
        val extMoved = pc.await("ext on $ext") { it.extRunning && it.extPort == ext && it.extUrls.isNotEmpty() }
        assertTrue(extMoved.extUrls.first().endsWith(":$ext"), "${extMoved.extUrls}")
        for (bad in listOf(80, 70_000, ext)) pc.setPort(bad)
        pc.setExtensionPort(link)
        Thread.sleep(300)
        assertEquals(link, pc.state.value.port)
        assertEquals(ext, pc.state.value.extPort)

        // a taken port is reported, and the suggested one starts it again
        val taken = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        cleanup += { taken.close() }
        pc.setPort(taken.localPort)
        val busy = pc.await("port in use") { it.error == "port_in_use" && it.port == taken.localPort }
        assertFalse(busy.running)
        var alt = LinkServerPorts.alternative(busy.port, LinkServerPorts.LINK_DEFAULT)
        if (alt == ext) alt = LinkServerPorts.alternative(alt, LinkServerPorts.LINK_DEFAULT)
        pc.setPort(alt)
        pc.await("running on the alternative") { it.running && it.port == alt && it.error == null }
    }

    @Test
    fun theFirewallRuleIsOnlyAddedOnRequestAndOnlyWhereItExists() {
        val asked = mutableListOf<List<Int>>()
        val pc = otherPc(
            ServerSettings(port = 47_321, extPort = 8_099),
            firewallButton = true,
            firewall = { ports -> asked += ports; Firewall.Outcome.CANCELLED },
        )
        Thread.sleep(300)
        assertTrue(pc.state.value.firewallButton)
        assertNull(pc.state.value.firewall)
        assertTrue(asked.isEmpty(), "never at start")
        pc.allowPrivateNetwork()
        pc.await("cancelled") { it.firewall == FirewallResult.CANCELLED }
        assertEquals(listOf(listOf(47_321, 8_099)), asked)

        val linux = otherPc(firewallButton = false, firewall = { error("not here") })
        linux.allowPrivateNetwork()
        Thread.sleep(300)
        assertFalse(linux.state.value.firewallButton)
        assertNull(linux.state.value.firewall)
    }

    @Test
    fun linksLsfgLibraryIsThePlayersSetting() {
        var dll: String? = "D:\\Games\\Lossless Scaling\\Lossless.dll"
        val pc = otherPc(lsfgDll = { dll })
        assertEquals(LsfgSetup.Detect("D:\\Games\\Lossless Scaling\\Lossless.dll"), pc.linkConfig().lsfg)
        dll = "  "
        assertEquals(LsfgSetup.Detect(null), pc.linkConfig().lsfg)
        // a server-settings.json that still has the old field reads fine (ignored)
        val dir = tmp("old-settings")
        File(dir, "server-settings.json").writeText("""{"linkEnabled":false,"lsfgDll":"C:\\old.dll","port":47321}""")
        assertEquals(47_321, ServerSettings.load(File(dir, "server-settings.json")).port)
    }

    @Test
    fun theUisDefaultPortsAreTheServers() {
        assertEquals(LinkConfig.DEFAULT_PORT, LinkServerPorts.LINK_DEFAULT)
        assertEquals(DEFAULT_EXT_PORT, LinkServerPorts.EXT_DEFAULT)
        assertEquals(47_331, LinkServerPorts.alternative(47_321, LinkServerPorts.LINK_DEFAULT))
        assertEquals(LinkServerPorts.LINK_DEFAULT, LinkServerPorts.alternative(65_530, LinkServerPorts.LINK_DEFAULT))
        assertEquals(LinkServerPorts.LINK_DEFAULT, LinkServerPorts.alternative(80, LinkServerPorts.LINK_DEFAULT))
        assertTrue(LinkServerPorts.passwordOk("0123456789"))
        assertFalse(LinkServerPorts.passwordOk("012345678"))
        assertTrue(LinkServerPorts.passwordOk("🙂".repeat(10)), "counted in characters, as the server does")
    }
}
