// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.mpris

import app.nijiyome.model.MediaRef
import app.nijiyome.model.NowPlayingInfo
import app.nijiyome.model.PlayerArgs
import app.nijiyome.player.PlaybackState
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MPRIS against a private D-Bus daemon of the test (never the user's session bus), driven by `dbus-send` as an independent client and
 * by a dbus-java client for the PropertiesChanged signal. Skipped where `dbus-daemon` / `dbus-send` are not installed.
 */
class MprisTest {
    private var daemon: Process? = null
    private val dir: File = Files.createTempDirectory("mpris-bus").toFile()

    @AfterTest
    fun stop() {
        daemon?.destroy()
        daemon?.waitFor(5, TimeUnit.SECONDS)
        dir.deleteRecursively()
    }

    private fun startBus(): String? {
        if (!File("/usr/bin/dbus-daemon").canExecute() || !File("/usr/bin/dbus-send").canExecute()) return null
        val socket = File(dir, "bus")
        val conf = File(dir, "bus.conf")
        conf.writeText(
            """
            <!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"
              "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">
            <busconfig>
              <type>session</type>
              <listen>unix:path=${socket.absolutePath}</listen>
              <auth>EXTERNAL</auth>
              <policy context="default">
                <allow send_destination="*" eavesdrop="true"/>
                <allow eavesdrop="true"/>
                <allow own="*"/>
              </policy>
            </busconfig>
            """.trimIndent(),
        )
        val p = ProcessBuilder("/usr/bin/dbus-daemon", "--config-file=${conf.absolutePath}", "--nofork", "--print-address")
            .redirectErrorStream(true).start()
        daemon = p
        return p.inputStream.bufferedReader().readLine()?.trim()
    }

    private fun send(address: String, dest: String, method: String, vararg args: String): String {
        val cmd = listOf("/usr/bin/dbus-send", "--bus=$address", "--print-reply", "--dest=$dest", MPRIS_PATH, method) + args
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(10, TimeUnit.SECONDS)
        return out
    }

    private class FakePort(@Volatile var snap: MprisSnapshot) : MprisPort {
        val calls = LinkedBlockingQueue<String>()
        override fun snapshot() = snap
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun playPause() { calls += "playPause" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun seekTo(positionUs: Long) { calls += "seek $positionUs" }
    }

    private val playing = MprisSnapshot(
        MprisSnapshot.Status.PLAYING, "Frieren", "Episodio 3", "https://img.test/p.jpg", lengthUs = 1_440_000_000,
        positionUs = 60_000_000, canGoNext = true, canGoPrevious = true, trackKey = "154587_3",
    )

    @Test
    fun `a player on a private session bus answers MPRIS calls and signals changes`() {
        val address = startBus() ?: return
        val service = MprisService(connect = { DBusConnectionBuilder.forAddress(address).withShared(false).build() }, log = {})
        val port = FakePort(playing)
        val owner = Any()
        service.attach(owner, port)
        try {
            assertEquals(MprisService.BUS_NAME, service.busName)
            val dest = MprisService.BUS_NAME

            val status = send(address, dest, "org.freedesktop.DBus.Properties.Get", "string:$PLAYER_IFACE", "string:PlaybackStatus")
            assertTrue("\"Playing\"" in status, status)
            val meta = send(address, dest, "org.freedesktop.DBus.Properties.Get", "string:$PLAYER_IFACE", "string:Metadata")
            assertTrue("\"Frieren\"" in meta && "\"Episodio 3\"" in meta && "/app/nijiyome/track/154587_3" in meta, meta)
            assertTrue("1440000000" in meta && "https://img.test/p.jpg" in meta, meta)
            val identity = send(address, dest, "org.freedesktop.DBus.Properties.Get", "string:$ROOT_IFACE", "string:Identity")
            assertTrue("\"Nijiyome\"" in identity, identity)

            send(address, dest, "$PLAYER_IFACE.PlayPause")
            send(address, dest, "$PLAYER_IFACE.Next")
            send(address, dest, "$PLAYER_IFACE.Previous")
            send(address, dest, "$PLAYER_IFACE.Pause")
            send(address, dest, "$PLAYER_IFACE.Seek", "int64:5000000")
            send(address, dest, "$PLAYER_IFACE.SetPosition", "objpath:/app/nijiyome/track/154587_3", "int64:120000000")
            send(address, dest, "$PLAYER_IFACE.SetPosition", "objpath:/app/nijiyome/track/other", "int64:1") // another track: ignored
            val calls = generateSequence { port.calls.poll(2, TimeUnit.SECONDS) }.take(6).toList()
            assertEquals(listOf("playPause", "next", "previous", "pause", "seek 65000000", "seek 120000000"), calls)
            assertEquals(null, port.calls.poll(300, TimeUnit.MILLISECONDS))

            // PropertiesChanged with only what changed (PlaybackStatus), seen by a client of the same bus.
            val client = DBusConnectionBuilder.forAddress(address).withShared(false).build()
            client.use { c ->
                val got = LinkedBlockingQueue<Properties.PropertiesChanged>()
                c.addSigHandler(Properties.PropertiesChanged::class.java) { got += it }
                port.snap = playing.copy(status = MprisSnapshot.Status.PAUSED)
                service.changed()
                val signal = assertNotNull(got.poll(5, TimeUnit.SECONDS))
                assertEquals(PLAYER_IFACE, signal.interfaceName)
                assertEquals(setOf("PlaybackStatus"), signal.propertiesChanged.keys)
                assertEquals("Paused", signal.propertiesChanged["PlaybackStatus"]?.value)
            }

            // A second copy of the app on the same bus gets its own instance name.
            val second = MprisService(connect = { DBusConnectionBuilder.forAddress(address).withShared(false).build() }, log = {})
            second.attach(Any(), FakePort(playing))
            assertEquals("${MprisService.BUS_NAME}.instance${ProcessHandle.current().pid()}", second.busName)
            second.close()

            // Leaving the player (review #5): the name leaves the bus, the screen is not held, and commands go nowhere.
            service.detach(owner)
            assertFalse(hasOwner(address, dest))
            assertFalse(service.attached)
            assertEquals(null, service.busName)
            send(address, dest, "$PLAYER_IFACE.PlayPause")
            assertEquals(null, port.calls.poll(300, TimeUnit.MILLISECONDS))
        } finally {
            service.close()
        }
    }

    private fun hasOwner(address: String, name: String): Boolean {
        val out = ProcessBuilder(
            "/usr/bin/dbus-send", "--bus=$address", "--print-reply", "--dest=org.freedesktop.DBus", "/org/freedesktop/DBus",
            "org.freedesktop.DBus.NameHasOwner", "string:$name",
        ).redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().readText().also { p.waitFor(10, TimeUnit.SECONDS) } }
        return "boolean true" in out
    }

    @Test
    fun `leaving the player detaches even with the screen scope gone, and quitting leaves the bus`() {
        val address = startBus() ?: return
        val service = MprisService(connect = { DBusConnectionBuilder.forAddress(address).withShared(false).build() }, log = {})
        val owner = Any()
        // The screen's effect: attach, then onDispose while its coroutine scope is already cancelled (as when Compose disposes it).
        val screenScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job()).apply { cancel() }
        assertFalse(screenScope.isActive)
        service.attachAsync(owner, FakePort(playing))
        assertTrue(service.flush())
        assertTrue(hasOwner(address, MprisService.BUS_NAME))
        assertTrue(service.attached)
        service.detachAsync(owner)
        assertTrue(service.flush())
        assertFalse(service.attached, "the closed screen's port must not be held")
        assertFalse(hasOwner(address, MprisService.BUS_NAME), "the old episode must leave the media widget")

        // The next episode's screen: back on the bus; a stale detach of the previous screen changes nothing.
        val next = Any()
        service.attachAsync(next, FakePort(playing.copy(trackKey = "154587_4")))
        service.detachAsync(owner)
        assertTrue(service.flush())
        assertTrue(service.attached)
        assertTrue(hasOwner(address, MprisService.BUS_NAME))

        // Quitting the app: the name is released and later work is ignored.
        service.shutdown()
        assertFalse(hasOwner(address, MprisService.BUS_NAME))
        service.attachAsync(Any(), FakePort(playing))
        assertFalse(service.flush(300))
        assertFalse(hasOwner(address, MprisService.BUS_NAME))
    }

    @Test
    fun `without a bus the service stays off`() {
        val service = MprisService(connect = { error("no bus") }, log = {})
        val port = FakePort(playing)
        service.attach(Any(), port)
        service.changed()
        assertEquals(null, service.busName)
    }

    @Test
    fun `metadata carries the episode and stops empty`() {
        val m = mprisMetadata(playing)
        assertEquals(DBusPath("/app/nijiyome/track/154587_3"), m["mpris:trackid"]?.value)
        assertEquals("Frieren", m["xesam:title"]?.value)
        assertEquals(listOf("Episodio 3"), m["xesam:artist"]?.value)
        assertEquals("as", m["xesam:artist"]?.sig)
        assertEquals(1_440_000_000L, m["mpris:length"]?.value)
        val stopped = mprisMetadata(MprisSnapshot.STOPPED)
        assertEquals(setOf("mpris:trackid"), stopped.keys)
        assertEquals(DBusPath(NO_TRACK), stopped["mpris:trackid"]?.value)
        assertFalse("xesam:artist" in mprisMetadata(playing.copy(subtitle = null)))
    }

    @Test
    fun `only changed properties are signalled and never the position`() {
        assertEquals(emptyMap(), changedProperties(playing, playing.copy(positionUs = 999)))
        val next = changedProperties(playing, playing.copy(canGoNext = false))
        assertEquals(setOf("CanGoNext"), next.keys)
        assertEquals(Variant(false), next["CanGoNext"])
        assertFalse("Position" in changedProperties(null, playing))
        val stop = changedProperties(playing, MprisSnapshot.STOPPED)
        assertTrue(listOf("PlaybackStatus", "Metadata", "CanPlay", "CanSeek").all { it in stop }, stop.keys.toString())
    }

    @Test
    fun `track paths are valid object paths`() {
        assertEquals("/app/nijiyome/track/154587_3", trackPath("154587_3"))
        assertEquals("/app/nijiyome/track/a_b_c", trackPath("a-b.c"))
        assertEquals("/app/nijiyome/track/none", trackPath(""))
    }

    @Test
    fun `a position jump is a seek, progress is not`() {
        assertFalse(isJump(10_000, 0, 11_000, 1_000, playing = true))
        assertTrue(isJump(10_000, 0, 30_000, 1_000, playing = true))
        assertTrue(isJump(10_000, 0, 5_000, 1_000, playing = true))
        assertFalse(isJump(10_000, 0, 10_000, 5_000, playing = false))
        assertTrue(isJump(10_000, 0, 20_000, 5_000, playing = false))
    }

    @Test
    fun `the screen state becomes the snapshot`() {
        val args = PlayerArgs(media = MediaRef(anilistId = 154587), episode = 3, title = "Frieren")
        val ps = PlaybackState(isPlaying = true, positionMs = 1_000, durationMs = 1_440_000)
        val now = NowPlayingInfo("Frieren: Beyond Journey's End", "Episode 3", "https://img.test/p.jpg")
        val s = snapshotOf(ps, now, args, canNext = true, canPrevious = false)
        assertEquals(MprisSnapshot.Status.PLAYING, s.status)
        assertEquals("Frieren: Beyond Journey's End", s.title)
        assertEquals(1_440_000_000L, s.lengthUs)
        assertEquals(1_000_000L, s.positionUs)
        assertEquals("154587_3", s.trackKey)
        assertFalse(s.canGoPrevious)
        assertEquals("Frieren", snapshotOf(ps.copy(isPlaying = false), null, args, false, false).title)
        assertEquals(MprisSnapshot.Status.PAUSED, snapshotOf(ps.copy(isPlaying = false), null, args, false, false).status)
    }
}
