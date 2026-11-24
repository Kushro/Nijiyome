// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.model.AppSettings
import app.nijiyome.model.PlayerEngine
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** mpv-anime-build (pinned download, extraction, sanitized config, scripts), the desktop player settings and the factory's choice. */
class AnimeBuildAndPrefsTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-anime-build").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, String>): File {
        val f = File(tmp, "release-${entries.size}-${System.nanoTime()}.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, body) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return f
    }

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private val conf = """
        [Platform-Linux-Base]
        profile-cond=platform ~= "windows"
        vo=gpu-next
        gpu-api=vulkan,auto
        fbo-format=rgba16hf
        hwdec=nvdec,vulkan,auto

        [SVP-Unix-Patch]
        input-ipc-server=/tmp/mpvsocket
        [default]
        --osd-level=1
        save-position-on-quit=yes
        fullscreen=yes
        no-border
        osc=no
        fs
        d3d11-flip=yes
        sub-font="Hey Comic"
        glsl-shaders-append="~~/shaders/KrigBilateral.glsl"
        # vo=gpu
    """.trimIndent() + "\n"

    @Test
    fun sanitizeCommentsOutOnlyWhatTheAppOwns() {
        val out = AnimeBuild.sanitizeMpvConf(conf)
        for (kept in listOf("[Platform-Linux-Base]", "profile-cond=platform ~= \"windows\"", "fbo-format=rgba16hf", "--osd-level=1", "sub-font=\"Hey Comic\"", "# vo=gpu")) {
            assertTrue(out.lines().any { it == kept }, "$kept kept")
        }
        for (dropped in listOf("vo=gpu-next", "gpu-api=vulkan,auto", "hwdec=nvdec,vulkan,auto", "input-ipc-server=/tmp/mpvsocket", "fullscreen=yes", "no-border", "osc=no", "fs", "d3d11-flip=yes")) {
            assertFalse(out.lines().any { it == dropped }, "$dropped dropped")
            assertTrue(out.lines().any { it == AnimeBuild.DISABLED_PREFIX + dropped }, "$dropped kept as a comment")
        }
        assertEquals(conf.lines().size, out.lines().size)
        assertEquals("osd-level", AnimeBuild.optionName("--osd-level=1"))
        assertEquals("border", AnimeBuild.optionName("no-border"))
        assertEquals("temporal-dither", AnimeBuild.optionName("temporal-dither=yes   # c"))
        assertNull(AnimeBuild.optionName("  # x=1"))
        assertNull(AnimeBuild.optionName("[default]"))
    }

    @Test
    fun scriptsFollowMpvsAutoloadRulesMinusWhatCannotWorkHere() {
        val dir = File(tmp, "cfg")
        val scripts = File(dir, "scripts").apply { mkdirs() }
        File(scripts, "uosc").mkdirs()
        File(scripts, "uosc/main.lua").writeText("")
        File(scripts, "mpv-scripts.disable").mkdirs()
        File(scripts, "empty-dir").mkdirs()
        listOf("b.lua", "a.js", "notes.txt", "mpvSockets.lua", "thumbfast.lua", "autosave.lua").forEach { File(scripts, it).writeText("") }
        assertEquals(listOf("a.js", "b.lua"), AnimeBuild.selectScripts(dir, uosc = false).map { it.name })
        assertEquals(listOf("a.js", "b.lua", "thumbfast.lua", "uosc"), AnimeBuild.selectScripts(dir, uosc = true).map { it.name })
        assertTrue(AnimeBuild.selectScripts(File(tmp, "missing"), uosc = true).isEmpty())
    }

    @Test
    fun unsafeArchivePathsAreRejected() {
        assertEquals("scripts/uosc/main.lua", AnimeBuild.safeRelative("scripts/uosc/main.lua"))
        assertEquals("mpv.conf", AnimeBuild.safeRelative("./mpv.conf"))
        for (bad in listOf("../evil", "a/../../evil", "/etc/passwd", "a\\..\\b", "", ".", "C:/x")) assertNull(AnimeBuild.safeRelative(bad), bad)
        val z = zip("ok.txt" to "x", "../evil.txt" to "x")
        assertFailsWith<IOException> { AnimeBuild.prepare(z, File(tmp, "out"), sha(z)) }
        assertFalse(File(tmp, "evil.txt").exists())
        assertTrue(AnimeBuild.isHelperBinary("scripts/uosc/bin/ziggy-linux"))
        assertFalse(AnimeBuild.isHelperBinary("scripts/uosc/bin/ziggy-windows.exe"))
        assertFalse(AnimeBuild.isHelperBinary("scripts/autoload.lua"))
    }

    @Test
    fun prepareVerifiesExtractsSanitizesSeedsAndIsIdempotent() {
        val z = zip(
            "mpv.conf" to "[default]\nvo=gpu-next\nsub-font=\"Hey Comic\"\n",
            "scripts/uosc/main.lua" to "-- uosc",
            "scripts/uosc/bin/ziggy-linux" to "#!/bin/sh",
            "script-opts/uosc.conf" to "autohide=yes\n",
        )
        val dir = File(tmp, "data/mpv-anime-build/${AnimeBuild.VERSION}")
        assertFailsWith<IOException> { AnimeBuild.prepare(z, dir, "0".repeat(64)) }
        assertFalse(File(dir, AnimeBuild.MARKER).exists())

        val digest = sha(z)
        AnimeBuild.prepare(z, dir, digest)
        assertTrue(File(dir, "scripts/uosc/main.lua").isFile)
        assertTrue(File(dir, "scripts/uosc/bin/ziggy-linux").canExecute())
        // Executable bits exist only on POSIX file systems (on Windows every file reports canExecute()).
        if (dir.toPath().fileSystem.supportedFileAttributeViews().contains("posix")) {
            assertFalse(File(dir, "scripts/uosc/main.lua").canExecute())
        }
        assertEquals(AnimeBuild.markerBody(digest), File(dir, AnimeBuild.MARKER).readText())
        val mpvConf = File(dir, "mpv.conf").readText()
        assertTrue("${AnimeBuild.DISABLED_PREFIX}vo=gpu-next" in mpvConf && "sub-font=\"Hey Comic\"" in mpvConf)
        assertTrue("\nvo=gpu-next\n" in File(dir, AnimeBuild.UPSTREAM_CONF).readText())
        // Nijiyome's starting profile choices: Anime4K A+A HQ up to 4K, detection on.
        assertEquals(AnimeBuild.ANIME4K_DEFAULTS, File(dir, "script-opts/anime4k.conf").readText())
        assertTrue("anime_mode=on" in File(dir, "script-opts/anime-mode.conf").readText())

        // Already current: the build's own saved choices survive.
        File(dir, "script-opts/uosc.conf").writeText("autohide=no\n")
        File(dir, "script-opts/anime-mode.conf").writeText("anime_mode=auto\n")
        AnimeBuild.prepare(z, dir, digest)
        assertEquals("autohide=no\n", File(dir, "script-opts/uosc.conf").readText())
        // A stale marker (older sanitizer) re-extracts; the user's profile choices are still kept.
        File(dir, AnimeBuild.MARKER).writeText("old")
        AnimeBuild.prepare(z, dir, digest)
        assertEquals("autohide=yes\n", File(dir, "script-opts/uosc.conf").readText())
        assertEquals("anime_mode=auto\n", File(dir, "script-opts/anime-mode.conf").readText())
    }

    @Test
    fun theDownloadIsKeptOnlyWithThePinnedHashAndStateFollowsIt() {
        val body = zip("mpv.conf" to "[default]\n")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/r.zip") { ex ->
                val b = body.readBytes()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            start()
        }
        try {
            val url = "http://127.0.0.1:${server.address.port}/r.zip"
            val cache = File(tmp, "cache")
            val data = File(tmp, "data")
            assertEquals(AnimeBuild.State.NOT_DOWNLOADED, AnimeBuild.state(data, cache))
            assertFailsWith<IOException> { AnimeBuild.download(cache, url = url, sha256 = "f".repeat(64)) }
            assertFalse(AnimeBuild.zipFile(cache).exists(), "nothing unverified is kept")
            assertFalse(File(AnimeBuild.zipFile(cache).parentFile, "${AnimeBuild.ZIP_NAME}.part").exists())
            var last = 0L
            val got = AnimeBuild.download(cache, url = url, sha256 = sha(body)) { done, _ -> last = done }
            assertEquals(body.length(), got.length())
            assertEquals(body.length(), last)
            assertEquals(AnimeBuild.State.DOWNLOADED, AnimeBuild.state(data, cache))
            // The real pin: the state only says READY for an extraction of the pinned release.
            File(AnimeBuild.configDir(data), AnimeBuild.MARKER).apply { parentFile.mkdirs() }.writeText(AnimeBuild.markerBody())
            assertEquals(AnimeBuild.State.READY, AnimeBuild.state(data, cache))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aCancelledDownloadLeavesNothingAndTwoDownloadsNeverShareThePartFile() {
        val body = zip("mpv.conf" to "[default]\n", "big.bin" to "x".repeat(300_000))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = java.util.concurrent.Executors.newFixedThreadPool(4)
            createContext("/r.zip") { ex ->
                val b = body.readBytes()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { out ->
                    // in pieces, so both downloads are in flight at once
                    b.toList().chunked(16 * 1024).forEach { out.write(it.toByteArray()); out.flush(); Thread.sleep(5) }
                }
            }
            start()
        }
        try {
            val url = "http://127.0.0.1:${server.address.port}/r.zip"
            val cache = File(tmp, "cache-cancel")
            var reads = 0
            val e = assertFailsWith<IOException> {
                AnimeBuild.download(cache, url = url, sha256 = sha(body), isCancelled = { reads > 0 }) { _, _ -> reads++ }
            }
            assertEquals(AnimeBuild.CANCELLED, e.message)
            assertFalse(AnimeBuild.zipFile(cache).exists())
            assertFalse(File(AnimeBuild.zipFile(cache).parentFile, "${AnimeBuild.ZIP_NAME}.part").exists(), "no .part left")

            // Two at once (Settings opened twice): one waits for the other, both end with the one verified zip.
            val cache2 = File(tmp, "cache-twice")
            val results = java.util.concurrent.ConcurrentLinkedQueue<Result<File>>()
            val threads = (1..2).map { Thread { results += runCatching { AnimeBuild.download(cache2, url = url, sha256 = sha(body)) } } }
            threads.forEach(Thread::start)
            threads.forEach { it.join(30_000) }
            assertEquals(2, results.size)
            assertTrue(results.all { it.isSuccess }, results.toString())
            assertEquals(sha(body), sha(AnimeBuild.zipFile(cache2)))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun desktopPlayerPrefsRoundTripAndAreSanitized() {
        val p = DesktopPlayerPrefs(
            mpvPath = "/opt/mpv", enhanced = true, enhancedUosc = false, frameGen = true, frameGenMultiplier = 3, frameGenPerformance = true,
            frameGenFlowScale = 0.5f, lsfgDll = "/x/lsfg-vk.dll",
        )
        assertEquals(p, DesktopPlayerPrefs.fromJson(p.toJson()))
        assertEquals(DesktopPlayerPrefs(), DesktopPlayerPrefs.fromJson("garbage"))
        val odd = DesktopPlayerPrefs.fromJson("""{"frame_gen_multiplier":7,"frame_gen_flow_scale":9,"mpv_path":"  "}""")
        assertEquals(0, odd.frameGenMultiplier)
        assertEquals(1f, odd.frameGenFlowScale)
        assertNull(odd.mpvPath)
        val file = File(tmp, "data/desktop-player.json")
        DesktopPlayerPrefsStore(file).update { it.copy(enhanced = true, frameGenMultiplier = 2) }
        val again = DesktopPlayerPrefsStore(file).prefs.value
        assertTrue(again.enhanced)
        assertEquals(2, again.frameGenMultiplier)
    }

    @Test
    fun theKoinModuleResolvesWithoutTheRestOfTheApp() {
        val app = org.koin.dsl.koinApplication { modules(desktopExternalMpvModule(File(tmp, "data"), File(tmp, "cache"))) }
        try {
            val factory = app.koin.get<app.nijiyome.player.ExternalSessionFactory>()
            assertTrue(factory is ExternalMpvSessionFactory)
            val control = app.koin.get<ExternalMpvSettingsControl>()
            control.store.update { it.copy(enhanced = true) }
            assertTrue(File(tmp, "data/desktop-player.json").readText().contains("\"enhanced\":true"))
            assertEquals(AnimeBuild.State.NOT_DOWNLOADED, control.animeBuildState())
        } finally {
            app.close()
        }
    }

    @Test
    fun theFactoryOpensTheExternalWindowForExternalOrForFrameGeneration() {
        val bin = File(tmp, "bin").apply { mkdirs() }
        File(bin, "mpv").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        var prefs = DesktopPlayerPrefs()
        var fg = LsfgVk.Availability(File("layer.json"), File("lsfg-vk.dll"), null)
        fun factory(path: String) = ExternalMpvSessionFactory(
            prefs = { prefs },
            settings = { AppSettings() },
            dataDir = File(tmp, "data"),
            cacheDir = File(tmp, "cache"),
            env = mapOf("PATH" to path),
            frameGenAvailability = { fg },
            refreshHz = { 60f },
            mpvLookup = { custom -> findMpv(custom, mapOf("PATH" to path), windows = false, wellKnown = emptyList()) },
        )
        val f = factory(bin.path)
        assertTrue(f.wants(PlayerEngine.EXTERNAL))
        assertFalse(f.wants(PlayerEngine.MPV), "frame generation is off")
        prefs = prefs.copy(frameGen = true)
        assertTrue(f.wants(PlayerEngine.MPV), "frame generation runs only in the external window")
        fg = fg.copy(missing = LsfgVk.Missing.LAYER)
        assertFalse(f.wants(PlayerEngine.MPV), "unless lsfg-vk is missing")
        assertNull(f.unavailableReason())
        assertTrue(factory("/nonexistent").unavailableReason()!!.contains("not installed"))
        prefs = prefs.copy(mpvPath = "/nope/mpv")
        assertEquals("mpv not found at /nope/mpv", f.unavailableReason())
    }
}
