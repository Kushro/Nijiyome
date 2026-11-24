// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent.engine

import app.nijiyome.network.ProxyEndpoint
import app.nijiyome.network.ProxyKind
import app.nijiyome.torrent.LibtorrentNative
import app.nijiyome.torrent.system.TorrentProxy
import app.nijiyome.torrent.system.torrentProxyOf
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings › Red on libtorrent (1.1): a SOCKS5 outbound proxy carries the session; an HTTP one does not (torrents go direct). */
class TorrentProxyTest {
    @Test
    fun onlyASocks5ProxyReachesLibtorrent() {
        assertNull(torrentProxyOf(null))
        assertNull(torrentProxyOf(ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", 3128)))
        assertEquals(TorrentProxy("127.0.0.1", 9050, "u", "p"), torrentProxyOf(ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", 9050, "u", "p")))
        assertFalse("secret" in TorrentProxy("h", 1, "u", "secret").toString())
    }

    @Test
    fun theHostFollowsTheOutboundProxyWithoutAScopeOfItsOwn() = kotlinx.coroutines.runBlocking {
        val dir = Files.createTempDirectory("nj-thost").toFile()
        try {
            val outbound = kotlinx.coroutines.flow.MutableStateFlow<ProxyEndpoint?>(null)
            val host = app.nijiyome.torrent.system.DesktopTorrentHost(File(dir, "s"), File(dir, "d"), File(dir, "e"), outbound = outbound)
            assertNull(host.proxy())
            val seen = java.util.concurrent.atomic.AtomicInteger()
            val job = launch(kotlinx.coroutines.Dispatchers.Default) { host.proxyChanges.collect { seen.incrementAndGet() } }
            kotlinx.coroutines.delay(200)
            outbound.value = ProxyEndpoint(ProxyKind.SOCKS5, "127.0.0.1", 9050)
            kotlinx.coroutines.withTimeout(5_000) { while (seen.get() < 1) kotlinx.coroutines.delay(20) }
            assertEquals(TorrentProxy("127.0.0.1", 9050), host.proxy())
            outbound.value = ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", 3128) // HTTP: libtorrent goes direct
            kotlinx.coroutines.withTimeout(5_000) { while (seen.get() < 2) kotlinx.coroutines.delay(20) }
            assertNull(host.proxy())
            job.cancel()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theSettingsPackCarriesTheSocks5Proxy() {
        val dir = Files.createTempDirectory("nj-tproxy").toFile()
        try {
            assertIs<LibtorrentNative.Result.Loaded>(LibtorrentNative.load(File(dir, "cache")))
            val pack = SettingsPack()
            LibtorrentSession.applyProxy(pack, TorrentProxy("127.0.0.1", 9050, "alice", "pw"))
            assertEquals(settings_pack.proxy_type_t.socks5_pw.swigValue(), pack.getInteger(settings_pack.int_types.proxy_type.swigValue()))
            assertEquals("127.0.0.1", pack.getString(settings_pack.string_types.proxy_hostname.swigValue()))
            assertEquals(9050, pack.getInteger(settings_pack.int_types.proxy_port.swigValue()))
            assertEquals("alice", pack.getString(settings_pack.string_types.proxy_username.swigValue()))
            assertTrue(pack.getBoolean(settings_pack.bool_types.proxy_hostnames.swigValue()))
            assertTrue(pack.getBoolean(settings_pack.bool_types.proxy_peer_connections.swigValue()))
            assertTrue(pack.getBoolean(settings_pack.bool_types.proxy_tracker_connections.swigValue()))
            assertFalse(pack.getBoolean(settings_pack.bool_types.enable_upnp.swigValue()))
            assertFalse(pack.getBoolean(settings_pack.bool_types.enable_lsd.swigValue()))
            val noAuth = SettingsPack().also { LibtorrentSession.applyProxy(it, TorrentProxy("h", 1080)) }
            assertEquals(settings_pack.proxy_type_t.socks5.swigValue(), noAuth.getInteger(settings_pack.int_types.proxy_type.swigValue()))
            // turning it off leaves nothing of the old proxy in the session (applied over the same pack, as applySettings does)
            LibtorrentSession.applyProxy(pack, null)
            assertEquals(settings_pack.proxy_type_t.none.swigValue(), pack.getInteger(settings_pack.int_types.proxy_type.swigValue()))
            assertEquals("", pack.getString(settings_pack.string_types.proxy_hostname.swigValue()))
            assertEquals("", pack.getString(settings_pack.string_types.proxy_username.swigValue()))
            assertEquals("", pack.getString(settings_pack.string_types.proxy_password.swigValue()))
        } finally {
            dir.deleteRecursively()
        }
    }
}
