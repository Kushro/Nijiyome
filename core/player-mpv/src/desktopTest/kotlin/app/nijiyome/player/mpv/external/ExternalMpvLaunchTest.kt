// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The external mpv's command line and environment (Tatakai Next's tk-player external.rs tests, adapted). */
class ExternalMpvLaunchTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-xmpv-args").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    private fun launch(
        config: ExternalMpvConfig = ExternalMpvConfig.User,
        url: String = "https://cdn.example/v.m3u8",
        headers: Map<String, String> = emptyMap(),
        start: Double = 0.0,
        title: String = "",
        frameGen: LsfgLaunch? = null,
        fullscreen: Boolean = false,
        loopback: Boolean = false,
        options: Map<String, String> = emptyMap(),
        lavf: String = "",
        extra: List<String> = emptyList(),
    ) = ExternalMpvLaunch(
        endpoint = "/run/user/1000/x.sock", url = url, headers = headers, startSecs = start, title = title, config = config,
        frameGen = frameGen, fullscreen = fullscreen, lavfOptions = lavf, loopback = loopback, options = options, extra = extra,
    )

    @Test
    fun theUsersOwnConfigIsNeverDisabledAndHeadersAreRouted() {
        val a = externalMpvArgs(
            launch(
                headers = linkedMapOf("Referer" to "https://site.example/", "Cookie" to "a=b, c=d", "user-agent" to "UA/1"),
                start = 83.5,
                title = "Show \$x · E3",
            ),
        )
        assertTrue("--force-window=immediate" in a)
        assertTrue("--input-ipc-server=/run/user/1000/x.sock" in a)
        assertTrue("--title=Show \$\$x · E3" in a && "--force-media-title=Show \$\$x · E3" in a)
        assertTrue("--start=83.500" in a)
        assertTrue("--referrer=https://site.example/" in a)
        assertTrue("--user-agent=UA/1" in a)
        assertTrue("--http-header-fields-append=Cookie: a=b, c=d" in a)
        assertFalse(a.any { it.startsWith("--no-config") || it.startsWith("--config") || it.startsWith("--hwdec") || it.startsWith("--vo") })
        assertFalse(a.any { it.startsWith("--network-timeout") || it.startsWith("--fs") })
        assertEquals(listOf("--", "https://cdn.example/v.m3u8"), a.takeLast(2))

        // Nothing to say: no title, no start, the embedded engine's user agent.
        val b = externalMpvArgs(launch())
        assertFalse(b.any { it.startsWith("--start") || it.startsWith("--title") || it.startsWith("--referrer") })
        assertTrue("--user-agent=$EXTERNAL_DEFAULT_USER_AGENT" in b)
        // Torrent loopback streams wait longer for pieces.
        assertTrue("--network-timeout=90" in externalMpvArgs(launch(loopback = true)))
    }

    @Test
    fun theAppsConfigStartsWithNoConfigAndCarriesItsOptions() {
        val a = externalMpvArgs(
            launch(
                config = ExternalMpvConfig.App,
                fullscreen = true,
                options = linkedMapOf("glsl-shaders" to "/s/a.glsl:/s/b.glsl", "hwdec" to "auto-copy", "sub-font" to "Outfit"),
                lavf = "protocol_whitelist=[file,http]",
                extra = listOf("--ao=null"),
            ),
        )
        assertEquals("--no-config", a.first())
        assertTrue("--cache=yes" in a && "--demuxer-max-bytes=150MiB" in a && "--network-timeout=90" in a)
        assertTrue("--autofit-larger=90%x90%" in a)
        assertTrue("--glsl-shaders=/s/a.glsl:/s/b.glsl" in a && "--sub-font=Outfit" in a)
        // The quality engine's decoder wins over the default (later options win on mpv's command line).
        assertTrue(a.indexOf("--hwdec=auto-copy") > a.indexOf("--hwdec=auto-safe"))
        assertTrue("--demuxer-lavf-o-append=protocol_whitelist=[file,http]" in a)
        assertTrue("--fs" in a)
        assertEquals(listOf("--ao=null", "--", "https://cdn.example/v.m3u8"), a.takeLast(3))
        // mpv opens paused, after the session's own options (a `pause` among them included), until the session follows it.
        assertTrue(a.indexOf("--pause=yes") > a.indexOf("--sub-font=Outfit"))
        assertFalse("--pause=yes" in externalMpvArgs(launch().copy(startPaused = false)))
    }

    @Test
    fun animeBuildLoadsItsConfigAndFrameGenerationForcesVulkanLast() {
        val dir = File(tmp, "build").apply { mkdirs() }
        val scripts = File(dir, "scripts").apply { mkdirs() }
        File(scripts, "uosc").mkdirs()
        File(scripts, "uosc/main.lua").writeText("")
        File(scripts, "anime_profile_controller.lua").writeText("")
        File(scripts, "mpvSockets.lua").writeText("")
        val fg = LsfgLaunch(File(tmp, "lsfg-vk.toml"), 2)
        val a = externalMpvArgs(launch(config = AnimeBuild.config(dir, uosc = true), frameGen = fg))
        assertEquals("--config-dir=${dir.absolutePath}", a.first())
        assertTrue("--config=yes" in a && "--load-scripts=no" in a && "--osc=no" in a && "--resume-playback=no" in a)
        assertTrue("--scripts-append=${File(scripts, "uosc").absolutePath}" in a)
        assertTrue("--scripts-append=${File(scripts, "anime_profile_controller.lua").absolutePath}" in a)
        assertFalse(a.any { "mpvSockets" in it }, "the build's IPC script is left out")
        assertFalse(a.any { it == "--no-config" || it.startsWith("--glsl-shaders") }, "the build brings its own shaders")
        val vo = a.lastIndexOf("--vo=gpu-next")
        assertTrue(vo > a.indexOf("--osc=no") && vo > a.indexOf("--hwdec=auto-safe"))
        assertTrue(a.indexOf("--gpu-api=vulkan") > a.indexOf("--load-scripts=no"))
        assertTrue("--video-sync=audio" in a && "--interpolation=no" in a)

        // Without uosc: no uosc / thumbfast, mpv's own OSC.
        val b = externalMpvArgs(launch(config = AnimeBuild.config(dir, uosc = false)))
        assertTrue("--osc=yes" in b)
        assertFalse(b.any { it.endsWith("uosc") })
        assertFalse(b.any { it == "--gpu-api=vulkan" }, "no Vulkan forcing without frame generation")
    }

    @Test
    fun theEnvironmentPointsLsfgAtItsOwnConfigOrKeepsItOut() {
        val fg = LsfgLaunch(File("/run/user/1000/nijiyome/lsfg-vk.toml"), 2)
        val withFg = externalMpvEnv(launch(frameGen = fg), emptyMap())
        assertEquals("/run/user/1000/nijiyome/lsfg-vk.toml", withFg[LsfgVk.ENV_CONFIG])
        assertTrue(withFg.containsKey(LsfgVk.ENV_DISABLE) && withFg[LsfgVk.ENV_DISABLE] == null)
        assertEquals("1", externalMpvEnv(launch(config = ExternalMpvConfig.App), emptyMap())[LsfgVk.ENV_DISABLE])
        // The user's own mpv keeps the user's own lsfg-vk profiles.
        assertTrue(externalMpvEnv(launch(), emptyMap()).isEmpty())
    }

    @Test
    fun anAppImageDoesNotLeakItsLibrariesIntoMpv() {
        val env = mapOf(
            "APPIMAGE" to "/home/u/Nijiyome.AppImage", "APPDIR" to "/tmp/.mount_x", "GTK_PATH" to "/tmp/.mount_x/usr/lib/gtk",
            "LD_LIBRARY_PATH" to "/tmp/.mount_x/usr/lib:/opt/lib", "XDG_DATA_DIRS" to "/tmp/.mount_x/usr/share:/usr/share",
        )
        val c = appImageScrub(env)
        assertTrue(c.containsKey("GTK_PATH") && c["GTK_PATH"] == null)
        assertEquals("/opt/lib", c["LD_LIBRARY_PATH"])
        assertEquals("/usr/share", c["XDG_DATA_DIRS"])
        assertTrue(appImageScrub(mapOf("APPDIR" to "/x")).isEmpty(), "APPDIR alone is not an AppImage")
    }

    @Test
    fun headerPlanSplitsAgentReferrerAndFields() {
        val p = headerPlan(linkedMapOf("referer" to "r", "User-Agent" to "u", "Origin" to "o"))
        assertEquals("u", p.userAgent)
        assertEquals("r", p.referrer)
        assertEquals(listOf("Origin: o"), p.fields)
        assertNull(headerPlan(emptyMap()).userAgent)
    }

    @Test
    fun mpvIsFoundOnPathOrAtTheCustomPathOnly() {
        val bin = File(tmp, "bin").apply { mkdirs() }
        val mpv = File(bin, "mpv").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        assertEquals(mpv, findMpv(null, mapOf("PATH" to "/nonexistent${File.pathSeparator}${bin.path}"), windows = false))
        // A wrong custom path is "not found", never another mpv.
        assertNull(findMpv(File(tmp, "nope/mpv").path, mapOf("PATH" to bin.path), windows = false))
        assertEquals(mpv, findMpv(" ${mpv.path} ", emptyMap(), windows = false))
        // Blank = automatic.
        assertEquals(mpv, findMpv("  ", mapOf("PATH" to bin.path), windows = false))
        // Windows names.
        assertEquals("mpv.exe", mpvExeName(windows = true))
        assertTrue(wellKnownMpv(true, mapOf("ProgramFiles" to "C:\\Program Files")).any { it.path.endsWith("mpv.exe") })
    }

    @Test
    fun endpointsAreUniqueUnguessableAndLiveInThePrivateDir() {
        val a = newIpcEndpoint(windows = false, dir = tmp)
        assertTrue(a.startsWith(tmp.path) && a.endsWith(".sock"))
        assertTrue(a != newIpcEndpoint(windows = false, dir = tmp))
        // pid, sequence and a 16-hex-digit random token
        assertTrue(Regex("nijiyome-mpv-\\d+-\\d+-[0-9a-f]{16}\\.sock$").containsMatchIn(a), a)
        assertTrue(newIpcEndpoint(windows = true).startsWith("\\\\.\\pipe\\nijiyome-mpv-"))
        // The default folder is this user's private one.
        assertTrue(newIpcEndpoint(windows = false).startsWith(PrivateRuntimeDir.default.path))
    }

    @Test
    fun ytdlAndUnsafePlaylistsStayOffAndTitlesHaveNoControlCharacters() {
        val a = externalMpvArgs(launch(title = "Show\nE1\u0000"))
        assertTrue("--ytdl=no" in a && "--load-unsafe-playlists=no" in a)
        // after the user's options and the session's, so neither can turn them back on
        assertTrue(a.indexOf("--ytdl=no") > a.indexOf("--pause=yes"))
        assertTrue("--title=Show E1 " in a, a.toString())
    }

    @Test
    fun theQualityEnginesValuesAreTranslatedForAWindowOfItsOwn() {
        assertEquals("auto-safe", externalQualityValue("hwdec", app.nijiyome.model.DecoderMode.AUTO.hwdec, windows = false))
        assertEquals("no", externalQualityValue("hwdec", app.nijiyome.model.DecoderMode.SW.hwdec, windows = false))
        assertEquals("C:\\a.glsl;C:\\b.glsl", externalQualityValue("glsl-shaders", "C:\\a.glsl:C:\\b.glsl", windows = true))
        assertEquals("yes", externalQualityValue("deband", "yes", windows = false))
    }
}
