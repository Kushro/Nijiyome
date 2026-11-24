// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.desktop.TorrentLinkEntry
import app.nijiyome.extensionserver.DeepLink
import app.nijiyome.extensionserver.PendingServerLink
import app.nijiyome.torrent.PendingMagnetLink
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaunchAndInstanceTest {
    private val dir: File = Files.createTempDirectory("instance-test").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun linksAndFilesOfACommandLine() {
        // A file URI turns into the host's own path (`\t\show.torrent` on Windows): both spellings are files here.
        val files = listOf("/v/ep 1.mkv", "/t/show.torrent").flatMap { listOf(it, File(it).path) }.toSet()
        val isFile = { p: String -> p in files }
        assertEquals(LaunchTarget.Torrent("magnet:?xt=urn:btih:abc"), LaunchArgs.targetOf("magnet:?xt=urn:btih:abc", isFile))
        assertEquals(LaunchTarget.Torrent("MAGNET:?xt=urn:btih:abc"), LaunchArgs.targetOf("MAGNET:?xt=urn:btih:abc", isFile))
        assertEquals(LaunchTarget.Torrent("/t/show.torrent"), LaunchArgs.targetOf("/t/show.torrent", isFile))
        assertEquals(LaunchTarget.Torrent(File("/t/show.torrent").path), LaunchArgs.targetOf("file:///t/show.torrent", isFile))
        assertEquals(LaunchTarget.VideoFile(File("/v/ep 1.mkv").path), LaunchArgs.targetOf("file:///v/ep%201.mkv", isFile))
        assertEquals(
            LaunchTarget.OAuth("nijiyome://oauth/anilist#access_token=T"),
            LaunchArgs.targetOf("nijiyome://oauth/anilist#access_token=T", isFile),
        )
        assertEquals(LaunchTarget.Profile("Some User"), LaunchArgs.targetOf("nijiyome://user/Some%20User", isFile))
        assertEquals(LaunchTarget.Profile(null), LaunchArgs.targetOf("nijiyome://user/", isFile))
        // Another PC's extension server link: recognised as such (the servers screen validates it and asks before pairing).
        assertEquals(
            LaunchTarget.ExtensionServer("nijiyome://extensionserver/connect?server=x&code=y"),
            LaunchArgs.targetOf("nijiyome://extensionserver/connect?server=x&code=y", isFile),
        )
        assertEquals(
            LaunchTarget.ExtensionServer("NIJIYOME://ExtensionServer/connect?server=x&code=y"),
            LaunchArgs.targetOf("NIJIYOME://ExtensionServer/connect?server=x&code=y", isFile),
        )
        assertFalse("code=y" in LaunchTarget.ExtensionServer("nijiyome://extensionserver/connect?server=x&code=y").toString())
        // Anything that is not ours.
        assertNull(LaunchArgs.targetOf("nijiyome://oauth/other?code=1", isFile))
        assertNull(LaunchArgs.targetOf("https://example.com", isFile))
        assertNull(LaunchArgs.targetOf("/missing.mkv", isFile))
        assertNull(LaunchArgs.targetOf("magnet:" + "a".repeat(9_000), isFile))

        val parsed = LaunchArgs.parse(listOf("--minimized", "--ignored", "magnet:?xt=urn:btih:abc", "/v/ep 1.mkv"), isFile)
        assertTrue(parsed.minimized)
        assertEquals(LaunchTarget.Torrent("magnet:?xt=urn:btih:abc"), parsed.target)
        assertEquals(LaunchArgs(minimized = false, target = null), LaunchArgs.parse(emptyList(), isFile))
    }

    @Test
    fun anExtensionServerLinkForwardedByASecondLaunchWaitsForTheServersScreen() {
        val pending = PendingServerLink()
        val router = LaunchRouter(TorrentLinkEntry(PendingMagnetLink()), oauth = null, serverLink = pending)
        val received = LinkedBlockingQueue<List<String>>()
        val server = InstanceChannel.listen(dir) { received.put(it) }
        assertNotNull(server)
        try {
            val link = "nijiyome://extensionserver/connect?server=http%3A%2F%2F192.168.1.20%3A8099&code=123456"
            assertTrue(InstanceChannel.send(dir, listOf(link)))
            router.open(LaunchArgs.parse(received.poll(5, TimeUnit.SECONDS)!!).target)
            assertTrue(router.serverLinks.value, "the shell opens the servers screen")
            // the screen gets the link as it came; it is parsed there and paired only after the user confirms
            assertEquals(link, pending.consume())
            val parsed = DeepLink.parse(link)!!
            assertEquals("http://192.168.1.20:8099", parsed.server)
        } finally {
            server!!.close()
        }
        // without an extension server client nothing is kept and nothing opens
        val bare = LaunchRouter(TorrentLinkEntry(PendingMagnetLink()), oauth = null)
        bare.open(LaunchTarget.ExtensionServer("nijiyome://extensionserver/connect?server=x&code=y"))
        assertFalse(bare.serverLinks.value)
    }

    @Test
    fun messagesRoundTrip() {
        val args = listOf("magnet:?xt=urn:btih:abc&dn=A B", "nijiyome://user/x\ny", "")
        val decoded = InstanceChannel.decode(InstanceChannel.encode(args).lineSequence())
        // Empty arguments mean nothing and are not sent.
        assertEquals(args.dropLast(1), decoded)
        assertNull(InstanceChannel.decode(sequenceOf("HELLO", "x")))
    }

    @Test
    fun aSecondLaunchHandsItsArgumentsToTheFirst() {
        val received = LinkedBlockingQueue<List<String>>()
        val server = InstanceChannel.listen(dir) { received.put(it) }
        assertNotNull(server)
        val socket = InstanceChannel.clientPath(dir)!!
        assertEquals(File(dir, "instance.sock").toPath(), socket)
        try {
            val perms = runCatching { Files.getPosixFilePermissions(socket) }.getOrNull()
            if (perms != null) assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms)
            assertTrue(InstanceChannel.send(dir, listOf("magnet:?xt=urn:btih:abc")))
            assertEquals(listOf("magnet:?xt=urn:btih:abc"), received.poll(5, TimeUnit.SECONDS))
            assertTrue(InstanceChannel.send(dir, emptyList()), "a plain second launch only shows the window")
            assertEquals(emptyList(), received.poll(5, TimeUnit.SECONDS))
        } finally {
            server!!.close()
        }
        assertFalse(Files.exists(socket), "the socket file goes away with the app")
        assertFalse(InstanceChannel.send(dir, listOf("x")), "nobody listening")
    }

    @Test
    fun aStaleSocketFileIsReplaced() {
        Files.writeString(File(dir, "instance.sock").toPath(), "left by a crash")
        val received = LinkedBlockingQueue<List<String>>()
        val server = InstanceChannel.listen(dir) { received.put(it) }
        assertNotNull(server)
        try {
            assertTrue(InstanceChannel.send(dir, listOf("a")))
            assertEquals(listOf("a"), received.poll(5, TimeUnit.SECONDS))
        } finally {
            server!!.close()
        }
    }

    /** Hallazgo 16 de la revisión: a long data folder puts the socket in a private folder with an unguessable name. */
    @Test
    fun aLongDataDirectoryUsesAPrivateUnguessableFolder() {
        val deep = File(dir, "x".repeat(120)).apply { mkdirs() }
        // Its own folder straight in the temporary directory, as the app uses it: nested in [dir] the socket path
        // (`<tmp>/nijiyome-<random>/instance.sock`) can pass the ~100 bytes a socket allows on a runner with a long TEMP.
        val tmp = Files.createTempDirectory("t").toFile().apply { deleteOnExit() }
        assumeTrue(
            "the temporary folder is short enough for a socket path: $tmp",
            File(tmp, "nijiyome-12345678901234567890/instance.sock").absolutePath.toByteArray().size <= 100,
        )
        val received = LinkedBlockingQueue<List<String>>()
        val first = InstanceChannel.listen(deep, tmpDir = tmp) { received.put(it) }!!
        val path = InstanceChannel.clientPath(deep)!!
        first.close()
        val second = InstanceChannel.listen(deep, tmpDir = tmp) { received.put(it) }!!
        try {
            val again = InstanceChannel.clientPath(deep)!!
            assertTrue(again.parent != path.parent, "a new random folder per run: $path, $again")
            assertEquals(tmp.toPath(), again.parent.parent)
            assertTrue(again.parent.fileName.toString().startsWith("nijiyome-") && again.parent.fileName.toString().length > "nijiyome-".length + 8)
            val perms = runCatching { Files.getPosixFilePermissions(again.parent) }.getOrNull()
            if (perms != null) assertEquals(PosixFilePermissions.fromString("rwx------"), perms)
            assertTrue(InstanceChannel.send(deep, listOf("nijiyome://user/x")))
            assertEquals(listOf("nijiyome://user/x"), received.poll(5, TimeUnit.SECONDS))
        } finally {
            second.close()
        }
        assertFalse(File(deep, InstanceChannel.POINTER_FILE).exists())
        assertTrue(tmp.listFiles().isNullOrEmpty(), "the private folder goes away with the app")
        assertFalse(InstanceChannel.send(deep, listOf("x")))
        tmp.deleteRecursively()
    }

    /** Hallazgo 16: a client that never ends its message, or sends a giant line, holds nothing and blocks nobody. */
    @Test
    fun slowAndOversizedClientsAreCutOff() {
        val received = LinkedBlockingQueue<List<String>>()
        val server = InstanceChannel.listen(dir) { received.put(it) }!!
        val socket = InstanceChannel.clientPath(dir)!!
        try {
            val stalls = (1..6).map {
                SocketChannel.open(StandardProtocolFamily.UNIX).apply {
                    connect(UnixDomainSocketAddress.of(socket))
                    write(ByteBuffer.wrap("NIJIYOME/1\nmagnet".toByteArray()))
                }
            }
            // More stalled clients than handler slots: a real launch still gets through once they time out.
            val started = System.currentTimeMillis()
            var sent = false
            while (!sent && System.currentTimeMillis() - started < InstanceChannel.READ_TIMEOUT_MS * 3) {
                sent = InstanceChannel.send(dir, listOf("a"))
                if (!sent) Thread.sleep(200)
            }
            assertTrue(sent)
            assertEquals(listOf("a"), received.poll(5, TimeUnit.SECONDS))
            // The stalled ones were closed by the server.
            stalls.forEach { ch ->
                ch.configureBlocking(true)
                val n = runCatching { ch.read(ByteBuffer.allocate(8)) }.getOrDefault(-1)
                assertTrue(n == -1 || n == 0, "a stalled client was closed")
                ch.close()
            }
            // A line over the limit is refused as a whole.
            val huge = "magnet:?" + "a".repeat(InstanceChannel.MAX_LINE + 10)
            assertFalse(InstanceChannel.send(dir, listOf(huge)))
            assertNull(received.poll(1, TimeUnit.SECONDS))
        } finally {
            server.close()
        }
    }

    /** Hallazgo 8 de la revisión: from an AppImage, the restart runs `$APPIMAGE`, never the mount of the run that is leaving. */
    @Test
    fun aRestartFromAnAppImageRunsTheAppImage() {
        val appImage = File(dir, "Nijiyome.AppImage").apply { writeText("x") }
        val launcher = File(dir, "bin/Nijiyome").apply { parentFile.mkdirs(); writeText("x") }
        val mount = "/tmp/.mount_abc/usr/lib/nijiyome/bin/Nijiyome\u0000".toByteArray()
        assertEquals(listOf(appImage.path), AppRestarter.ownCommand(env = { if (it == "APPIMAGE") appImage.path else null }, appPath = launcher.path) { mount })
        assertEquals(listOf(launcher.path), AppRestarter.ownCommand(env = { null }, appPath = launcher.path) { mount })
        assertEquals(listOf("/tmp/.mount_abc/usr/lib/nijiyome/bin/Nijiyome"), AppRestarter.ownCommand(env = { null }, appPath = null) { mount })
        // An APPIMAGE that does not exist (a stale variable) is not trusted.
        assertEquals(listOf(launcher.path), AppRestarter.ownCommand(env = { "/nope/x.AppImage" }, appPath = launcher.path) { mount })
    }

    @Test
    fun aRestartKeepsTheExecutableAndDropsWhatThisRunWasStartedWith() {
        val appImage = File(dir, "Nijiyome.AppImage").apply { writeText("x") }
        assertEquals(listOf(appImage.path), AppRestarter.restartCommand(listOf(appImage.path)))
        val video = File(dir, "ep.mkv").apply { writeText("x") }
        assertEquals(
            listOf("/usr/bin/java", "-cp", "a.jar", "app.nijiyome.desktop.MainKt"),
            AppRestarter.restartCommand(listOf("/usr/bin/java", "-cp", "a.jar", "app.nijiyome.desktop.MainKt", video.path, "--minimized", "magnet:?xt=1")),
        )
        assertNull(AppRestarter.restartCommand(emptyList()))
        val env = mutableMapOf(
            "_JPACKAGE_LAUNCHER" to "1", "NIJIYOME_JSIG" to "1", "APPDIR" to "/tmp/.mount_x", "APPIMAGE" to appImage.path,
            "LD_PRELOAD" to "/tmp/.mount_x/usr/lib/nijiyome/lib/runtime/lib/libjsig.so /opt/other.so", "HOME" to "/h",
        )
        AppRestarter.freshEnvironment(env)
        assertEquals(mapOf("LD_PRELOAD" to "/opt/other.so", "HOME" to "/h"), env)
    }

}
