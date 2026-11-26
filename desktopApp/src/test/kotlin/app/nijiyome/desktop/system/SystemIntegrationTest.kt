// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.feature.settings.DesktopSystemPrefs
import app.nijiyome.model.NoticeDestination
import app.nijiyome.model.NowPlayingInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemIntegrationTest {
    private val dir: File = Files.createTempDirectory("system-test").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    // ------------------------------------------------------------------------------------------------ autostart

    @Test
    fun theAutostartEntryGoesToXdgConfigHomeAndStartsMinimized() {
        val config = File(dir, "config")
        val env = mapOf("XDG_CONFIG_HOME" to config.path)
        val exe = "/opt/Nijiyome \$HOME/100% \"real\"/Nijiyome.AppImage"
        val autostart = Autostart(exe, windows = false, env = env::get, home = File(dir, "home").path, run = { error("no command on Linux") })
        assertEquals(File(config, "autostart/app.nijiyome.desktop"), autostart.desktopFile)
        assertFalse(autostart.isEnabled())
        autostart.setEnabled(true)
        val text = autostart.desktopFile.readText()
        assertTrue(text.startsWith("[Desktop Entry]\nType=Application\n"), text)
        assertTrue(text.contains("\nExec=\"/opt/Nijiyome \\\$HOME/100%% \\\"real\\\"/Nijiyome.AppImage\" --minimized\n"), text)
        assertTrue(text.contains("\nName=Nijiyome\n") && text.contains("\nIcon=app.nijiyome\n") && text.contains("\nTerminal=false\n"), text)
        assertTrue(autostart.isEnabled())
        autostart.setEnabled(false)
        assertFalse(autostart.desktopFile.exists())
        // Without XDG_CONFIG_HOME: ~/.config/autostart (here a fake home).
        val plain = Autostart(exe, windows = false, env = { null }, home = File(dir, "home").path)
        assertEquals(File(dir, "home/.config/autostart/app.nijiyome.desktop"), plain.desktopFile)
        assertFalse(Autostart(null, windows = false).supported)
    }

    @Test
    fun windowsAutostartIsTheRunKeyOfTheUser() {
        val commands = mutableListOf<List<String>>()
        val exe = "C:\\Users\\u\\AppData\\Local\\Nijiyome\\Nijiyome.exe"
        val autostart = Autostart(exe, windows = true, run = { commands += it; 0 })
        autostart.setEnabled(true)
        autostart.setEnabled(false)
        assertTrue(autostart.isEnabled())
        assertEquals(
            listOf(
                listOf("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "Nijiyome", "/t", "REG_SZ", "/d", "\"$exe\" --minimized", "/f"),
                listOf("reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "Nijiyome", "/f"),
                listOf("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "Nijiyome"),
            ),
            commands,
        )
    }

    @Test
    fun theSettingDrivesTheEntryAndTheResetRemovesIt() = kotlinx.coroutines.runBlocking {
        val env = mapOf("XDG_CONFIG_HOME" to File(dir, "config").path)
        val autostart = Autostart("/opt/n/Nijiyome", windows = false, env = env::get)
        val store = DesktopSystemPrefsStore(File(dir, "desktop-system.properties"))
        val system = DesktopSystem(store, autostart, secrets = null, discordConfigured = false, trayAvailable = true)
        assertEquals(DesktopSystemPrefs(), system.prefs.value)
        system.update { it.copy(startWithSystem = true, closeToTray = true) }
        assertTrue(autostart.isEnabled())
        assertEquals(DesktopSystemPrefs(startWithSystem = true, closeToTray = true), store.load())
        system.wipe()
        assertFalse(autostart.isEnabled())
        assertEquals(DesktopSystemPrefs(), system.prefs.value)
    }

    /** Hito 6 (D1, sistema-ajustes-4): the switch follows the real entry, which start-up no longer puts back behind the user's back. */
    @Test
    fun theAutostartSwitchFollowsTheRealEntry() {
        val env = mapOf("XDG_CONFIG_HOME" to File(dir, "config").path)
        val store = DesktopSystemPrefsStore(File(dir, "desktop-system.properties"))
        val system = DesktopSystem(store, Autostart("/opt/n/Nijiyome", windows = false, env = env::get), secrets = null, discordConfigured = false, trayAvailable = true)
        system.update { it.copy(startWithSystem = true) }
        val entry = Autostart("/opt/n/Nijiyome", windows = false, env = env::get).desktopFile
        assertTrue(entry.isFile)
        // Removed by hand in the session's startup manager: the next start (or Ajustes) shows it off, and does not recreate it.
        entry.delete()
        system.reconcileAutostart()
        assertFalse(system.prefs.value.startWithSystem)
        assertFalse(entry.isFile)
        assertFalse(store.load().startWithSystem)
        // An entry that exists while the setting says off (another copy of the app wrote it): the switch shows it on, and the entry
        // now starts this launcher (an AppImage that moved).
        Autostart("/old/place/Nijiyome", windows = false, env = env::get).setEnabled(true)
        val moved = DesktopSystem(store, Autostart("/new/place/Nijiyome", windows = false, env = env::get), secrets = null, discordConfigured = false, trayAvailable = true)
        moved.refreshAutostart()
        assertTrue(moved.prefs.value.startWithSystem)
        assertTrue(entry.readText().contains("/new/place/Nijiyome"), entry.readText())
    }

    @Test
    fun aTrayHostIsLookedUpOnTheSessionBus() {
        assertTrue(DesktopSystem.trayHostPresent(windows = true) { error("not asked on Windows") })
        assertTrue(DesktopSystem.trayHostPresent(windows = false) { "(true,)\n" })
        assertFalse(DesktopSystem.trayHostPresent(windows = false) { "(false,)\n" })
        assertTrue(DesktopSystem.trayHostPresent(windows = false) { null }, "gdbus missing: assume a tray")
    }

    // ------------------------------------------------------------------------------------------------ build info

    @Test
    fun buildInfoReadsTheResourceAndTheEnvironmentWins() {
        val p = Properties().apply {
            setProperty(BuildInfo.VERSION, "0.2.0")
            setProperty(BuildInfo.ANILIST, "123")
            setProperty(BuildInfo.MAL, "")
            setProperty(BuildInfo.DISCORD, "not-a-number")
        }
        val info = BuildInfo.of(p) { if (it == BuildInfo.MAL) "mal-env" else null }
        assertEquals(BuildInfo(version = "0.2.0", anilistClientId = "123", malClientId = "mal-env", discordAppId = null), info)
    }

    // ------------------------------------------------------------------------------------------------ notifications

    @Test
    fun notificationsGoThroughTheFreedesktopService() {
        val commands = mutableListOf<List<String>>()
        val notifier = DesktopNotifier(windows = false, run = { commands += it; if (it.first() == "gdbus") 1 else 0 })
        notifier.post("Frieren · episode 3 downloaded", "it's \\ here")
        assertEquals("gdbus", commands[0][0])
        assertTrue(commands[0].containsAll(listOf("'Nijiyome'", "'Frieren · episode 3 downloaded'", "'it\\'s \\\\ here'")), commands[0].toString())
        assertEquals(listOf("notify-send", "--app-name=Nijiyome", "--icon=app.nijiyome", "--", "Frieren · episode 3 downloaded", "it's \\ here"), commands[1])
        assertEquals("'a b'", DesktopNotifier.gvariantString("a\nb"))
        val balloons = mutableListOf<Pair<String, String>>()
        DesktopNotifier(windows = true, run = { error("no commands on Windows") }, windowsBalloon = { t, b, _ -> balloons += t to b }).post("T", "B")
        assertEquals(listOf("T" to "B"), balloons)
    }

    @Test
    fun aClickedNotificationOpensItsDestination() {
        val commands = mutableListOf<List<String>>()
        val opened = mutableListOf<NoticeDestination>()
        val notifier = DesktopNotifier(
            windows = false,
            run = { commands += it; 0 },
            call = { commands += it; "(uint32 42,)\n" },
            startMonitor = { null },
        )
        notifier.onOpen = { opened += it }
        notifier.post("Frieren", "Episode 3 ready", NoticeDestination.Torrent("abc"))
        assertTrue(commands.single().contains("['default', '']"), commands.toString())
        assertEquals(setOf(42L), notifier.pendingIds())
        notifier.onSignalLine("/org/freedesktop/Notifications: org.freedesktop.Notifications.ActionInvoked (uint32 41, 'default')")
        assertTrue(opened.isEmpty())
        notifier.onSignalLine("/org/freedesktop/Notifications: org.freedesktop.Notifications.ActionInvoked (uint32 42, 'default')")
        assertEquals(listOf<NoticeDestination>(NoticeDestination.Torrent("abc")), opened)
        assertTrue(notifier.pendingIds().isEmpty())
        // A notice closed without a click is forgotten.
        notifier.post("Frieren", "Episode 4 ready", NoticeDestination.Transfers)
        notifier.onSignalLine("/org/freedesktop/Notifications: org.freedesktop.Notifications.NotificationClosed (uint32 42, uint32 2)")
        assertTrue(notifier.pendingIds().isEmpty())
        // Windows: the balloon gets a click handler only when there is somewhere to go.
        val handlers = mutableListOf<(() -> Unit)?>()
        val win = DesktopNotifier(windows = true, windowsBalloon = { _, _, onClick -> handlers += onClick })
        win.onOpen = { opened += it }
        win.post("T", "B")
        win.post("T", "B", NoticeDestination.Calendar)
        assertEquals(null, handlers[0])
        handlers[1]?.invoke()
        assertEquals(NoticeDestination.Calendar, opened.last())
    }

    /**
     * Against a notification server on a private session bus (`.claude/run-dbus-tests.sh` starts `dbus-run-session` and a fake
     * `org.freedesktop.Notifications` that appends each call to NIJIYOME_FAKE_NOTIFY_LOG). Skipped otherwise.
     */
    @Test
    fun aNotificationReachesAFakeServerOnAPrivateBus() {
        if (System.getenv("NIJIYOME_DBUS_TESTS") != "1") return
        val log = File(System.getenv("NIJIYOME_FAKE_NOTIFY_LOG") ?: return)
        DesktopNotifier(windows = false).post("Frieren", "Episode 3 ready · it's here")
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !log.isFile) Thread.sleep(100)
        val line = log.readText()
        assertTrue(line.contains("Nijiyome|app.nijiyome|Frieren|Episode 3 ready · it's here"), line)
    }

    // ------------------------------------------------------------------------------------------------ Discord

    private val watching = NowPlayingInfo(
        title = "Frieren",
        subtitle = "Episode 3",
        artworkUrl = "https://img.example/cover.jpg",
        positionMs = 60_000,
        durationMs = 1_440_000,
        paused = false,
    )

    /** Hito 6 (D1, player-11): a Details page shows "Viendo la ficha de <título>"; the player wins over it. */
    @Test
    fun aDetailsPageIsViewing() {
        assertEquals(
            """{"details":"Viendo la ficha de Frieren","state":"Ficha","assets":{"large_image":"logo","large_text":"Nijiyome"}}""",
            activityJson(PresenceState.Viewing("Frieren"), 0, PresenceTexts(spanish = true)),
        )
        assertEquals(PresenceState.Viewing("Frieren"), presenceFor(enabled = true, now = null, viewing = "Frieren"))
        assertEquals(PresenceState.Watching(watching), presenceFor(enabled = true, now = watching, viewing = "Frieren"))
        assertEquals(PresenceState.Browsing, presenceFor(enabled = true, now = null, viewing = " "))
        assertEquals(PresenceState.Clear, presenceFor(enabled = false, now = null, viewing = "Frieren"))
    }

    @Test
    fun theActivityOfEachState() {
        val texts = PresenceTexts(spanish = false)
        assertNull(activityJson(PresenceState.Clear, 0, texts))
        assertEquals(
            """{"details":"Browsing anime","state":"In Nijiyome","assets":{"large_image":"logo","large_text":"Nijiyome"}}""",
            activityJson(PresenceState.Browsing, 0, texts),
        )
        val now = 10_000_000L
        val playing = activityJson(PresenceState.Watching(watching), now, texts)!!
        assertTrue(playing.contains(""""type":3"""), playing)
        assertTrue(playing.contains(""""details":"Watching Frieren","state":"Episode 3""""), playing)
        assertTrue(playing.contains(""""large_image":"https://img.example/cover.jpg""""), playing)
        assertTrue(playing.contains(""""timestamps":{"start":${now - 60_000},"end":${now + 1_380_000}}"""), playing)
        val paused = activityJson(PresenceState.Watching(watching.copy(paused = true, artworkUrl = "http://x")), now, PresenceTexts(spanish = true))!!
        assertTrue(paused.contains(""""details":"En pausa · Frieren""""), paused)
        assertTrue(paused.contains(""""large_image":"logo""""), "no plain-http artwork")
        assertFalse(paused.contains("timestamps"))
        val long = activityJson(PresenceState.Watching(watching.copy(title = "\"" + "x".repeat(300))), now, texts)!!
        assertTrue(long.contains("\\\""), "quotes are escaped")
        assertEquals("""{"cmd":"SET_ACTIVITY","args":{"pid":7,"activity":null},"nonce":"n"}""", setActivityCommand(7, null, "n"))
    }

    @Test
    fun framesAreLittleEndianOpcodeLengthAndJson() {
        val out = ByteArrayOutputStream()
        DiscordFrames.write(out, DiscordFrames.HANDSHAKE, handshake("123"))
        val bytes = out.toByteArray()
        assertEquals(listOf<Byte>(0, 0, 0, 0), bytes.take(4))
        assertEquals("""{"v":1,"client_id":"123"}""".length.toByte(), bytes[4])
        assertEquals(DiscordFrames.HANDSHAKE to """{"v":1,"client_id":"123"}""", DiscordFrames.read(ByteArrayInputStream(bytes)))
    }

    /** A fake Discord client on a local socket: handshake, then every SET_ACTIVITY is recorded and acknowledged. */
    @Test
    fun presenceTalksToAFakeDiscordSocket() {
        val path = File(dir, "discord-ipc-0").toPath()
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path)) }
        val received = LinkedBlockingQueue<String>()
        val fake = Thread {
            runCatching {
                server.accept().use { ch ->
                    val input = Channels.newInputStream(ch)
                    val output = Channels.newOutputStream(ch)
                    val (op, hello) = DiscordFrames.read(input)
                    received.put("op$op:$hello")
                    DiscordFrames.write(output, DiscordFrames.FRAME, """{"cmd":"DISPATCH","evt":"READY","data":{"v":1}}""")
                    while (true) {
                        val (_, cmd) = DiscordFrames.read(input)
                        received.put(cmd)
                        DiscordFrames.write(output, DiscordFrames.FRAME, """{"cmd":"SET_ACTIVITY","evt":null,"nonce":"x"}""")
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val presence = DiscordPresence(
            clientId = "4242",
            connect = { connectDiscord(discordEndpoints(windows = false) { if (it == "XDG_RUNTIME_DIR") dir.path else null }, windows = false) },
            pid = 99,
            clock = { 1_000_000L },
            texts = PresenceTexts(spanish = false),
        )
        presence.update(PresenceState.Watching(watching))
        assertEquals("op0:" + """{"v":1,"client_id":"4242"}""", received.poll(5, TimeUnit.SECONDS))
        val set = received.poll(5, TimeUnit.SECONDS)
        assertNotNull(set)
        assertTrue(set.contains(""""cmd":"SET_ACTIVITY"""") && set.contains(""""pid":99""") && set.contains("Watching Frieren"), set)
        presence.update(PresenceState.Clear)
        val clear = received.poll(5, TimeUnit.SECONDS)
        assertTrue(clear!!.contains(""""activity":null"""), clear)
        presence.close()
        server.close()
        fake.interrupt()
    }

    /** A fake Discord on a local socket that accepts and then never answers; [eof] gets the time the app closed the connection. */
    private fun silentDiscord(name: String, eof: LinkedBlockingQueue<Long>): Pair<ServerSocketChannel, String> {
        val path = File(dir, name).toPath()
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path)) }
        Thread {
            runCatching {
                server.accept().use { ch ->
                    val input = Channels.newInputStream(ch)
                    while (input.read() >= 0) Unit
                    eof.put(System.currentTimeMillis())
                }
            }
        }.apply { isDaemon = true; start() }
        return server to path.toString()
    }

    /** Hallazgo 19 de la revisión: a client that never answers is dropped after the read timeout. */
    @Test
    fun aSilentDiscordIsDroppedAfterTheReadTimeout() {
        val eof = LinkedBlockingQueue<Long>()
        val (server, path) = silentDiscord("discord-silent", eof)
        val presence = DiscordPresence("1", connect = { connectDiscord(listOf(path), windows = false) }, readTimeoutMs = 500, retryMs = 60_000)
        val started = System.currentTimeMillis()
        presence.update(PresenceState.Browsing)
        val closedAt = eof.poll(5, TimeUnit.SECONDS)
        assertNotNull(closedAt, "the connection was dropped")
        assertTrue(closedAt - started < 4_000)
        presence.close()
        server.close()
    }

    /** Hallazgo 19: turning the presence off (or quitting) closes a connection even while it waits on Discord. */
    @Test
    fun closingDropsAConnectionThatWaitsOnDiscord() {
        val eof = LinkedBlockingQueue<Long>()
        val (server, path) = silentDiscord("discord-stuck", eof)
        val presence = DiscordPresence("1", connect = { connectDiscord(listOf(path), windows = false) }, readTimeoutMs = 60_000, retryMs = 60_000)
        presence.update(PresenceState.Browsing)
        Thread.sleep(500)
        val started = System.currentTimeMillis()
        presence.close()
        assertTrue(System.currentTimeMillis() - started < 5_000, "close() does not wait for Discord")
        assertNotNull(eof.poll(5, TimeUnit.SECONDS), "the connection was closed")
        server.close()
    }

    @Test
    fun withoutDiscordNothingFailsAndNothingIsSent() {
        var attempts = 0
        val presence = DiscordPresence("1", connect = { attempts++; null }, retryMs = 60_000)
        presence.update(PresenceState.Browsing)
        Thread.sleep(300)
        presence.close()
        assertEquals(1, attempts)
        // Clear before anything was shown starts nothing.
        DiscordPresence("1", connect = { error("not called") }).apply { update(PresenceState.Clear); close() }
    }

    @Test
    fun discordEndpointsCoverFlatpakAndSnap() {
        val linux = discordEndpoints(windows = false) { if (it == "XDG_RUNTIME_DIR") "/run/user/1000" else null }
        assertTrue("/run/user/1000/discord-ipc-0" in linux)
        assertTrue("/run/user/1000/app/com.discordapp.Discord/discord-ipc-0" in linux)
        assertTrue("/run/user/1000/snap.discord/discord-ipc-3" in linux)
        assertEquals("\\\\.\\pipe\\discord-ipc-0", discordEndpoints(windows = true).first())
    }
}
