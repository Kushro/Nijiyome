// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import java.io.File
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** lsfg-vk detection with temporary folders, the multiplier rule and the dedicated config. */
class LsfgVkTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-lsfg").toFile()
    private val home = File(tmp, "home").apply { mkdirs() }
    private val etc = File(tmp, "etc")

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    private val v2 = """{"file_format_version":"1.1.0","layer":{"name":"VK_LAYER_LSFGVK_frame_generation","type":"GLOBAL",
        |"library_path":"liblsfg-vk-layer.so","disable_environment":{"DISABLE_LSFGVK":"1"}}}""".trimMargin()

    /** An environment that sees only [tmp] (no system layer folders). */
    private fun env(vararg extra: Pair<String, String>) = mapOf(
        "XDG_CONFIG_DIRS" to File(tmp, "etc-xdg").path,
        "XDG_DATA_DIRS" to File(tmp, "share").path,
        "XDG_CONFIG_HOME" to File(home, ".config").path,
        "XDG_DATA_HOME" to File(home, ".local/share").path,
    ) + extra

    private fun layer(dir: File, name: String = "lsfg.json", body: String = v2) = File(dir, name).apply {
        parentFile.mkdirs()
        writeText(body)
    }

    private fun steamDir() = File(home, ".local/share/Steam/steamapps/common/Lossless Scaling").apply { mkdirs() }

    /** lsfg-vk is a Linux Vulkan layer: [LsfgVk.detect] answers NOT_LINUX elsewhere, so its paths are Linux paths. */
    private fun assumeLinux() = assumeTrue("lsfg-vk runs on Linux only", System.getProperty("os.name").orEmpty().startsWith("Linux"))

    @Test
    fun manifestsOfBothMajorVersionsAreRecognised() {
        assertTrue(LsfgVk.isLsfgManifest(v2))
        assertTrue(LsfgVk.isLsfgManifest("""{"layers":[{"name":"VK_LAYER_LS_frame_generation"}]}"""))
        assertFalse(LsfgVk.isLsfgManifest("""{"layer":{"name":"VK_LAYER_MANGOHUD_overlay_x86_64"}}"""))
        assertFalse(LsfgVk.isLsfgManifest("not json"))
    }

    @Test
    fun theLayerIsFoundWhereTheVulkanLoaderLooks() {
        assumeLinux()
        assertNull(LsfgVk.findLayer(env(), home, etc))
        val other = layer(File(tmp, "share/vulkan/implicit_layer.d"), "mangohud.json", """{"layer":{"name":"VK_LAYER_MANGOHUD_overlay"}}""")
        assertNull(LsfgVk.findLayer(env(), home, etc), "${other.name} is another layer")
        val sys = layer(File(tmp, "share/vulkan/implicit_layer.d"), "VkLayer_LSFGVK_frame_generation.json")
        assertEquals(sys, LsfgVk.findLayer(env(), home, etc))
        // The user's own folder comes first, and VK_ADD_IMPLICIT_LAYER_PATH before everything.
        val user = layer(File(home, ".config/vulkan/implicit_layer.d"))
        assertEquals(user, LsfgVk.findLayer(env(), home, etc))
        val added = layer(File(tmp, "added"))
        assertEquals(added, LsfgVk.findLayer(env("VK_ADD_IMPLICIT_LAYER_PATH" to added.parent), home, etc))
        // Defaults when the XDG variables are unset.
        val dirs = LsfgVk.layerDirs(emptyMap(), home).map { it.path }
        assertEquals(File(home, ".config/vulkan/implicit_layer.d").path, dirs.first())
        assertTrue("/usr/share/vulkan/implicit_layer.d" in dirs && "/etc/vulkan/implicit_layer.d" in dirs)
    }

    @Test
    fun theShaderPackNextToAConfiguredDllWinsThenSteamThenTheConfiguredOne() {
        assertNull(LsfgVk.findDll(null, env(), home))
        // The game's Lossless.dll in Steam's folder is the last resort.
        val steamGame = File(steamDir(), "Lossless.dll").apply { writeText("x") }
        assertEquals(steamGame, LsfgVk.findDll(null, env(), home))
        val steamPack = File(steamDir(), "lsfg-vk.dll").apply { writeText("x") }
        assertEquals(steamPack, LsfgVk.findDll(null, env(), home))
        // A configured DLL elsewhere: its sibling lsfg-vk.dll wins over Steam's, the DLL itself comes after Steam's pack.
        val mine = File(tmp, "mine").apply { mkdirs() }
        val myGame = File(mine, "Lossless.dll").apply { writeText("x") }
        assertEquals(steamPack, LsfgVk.findDll(myGame.path, env(), home))
        val myPack = File(mine, "lsfg-vk.dll").apply { writeText("x") }
        assertEquals(myPack, LsfgVk.findDll(myGame.path, env(), home))
        // The user's lsfg-vk config counts as configured.
        steamPack.delete()
        steamGame.delete()
        myPack.delete()
        File(home, ".config/lsfg-vk").mkdirs()
        File(home, ".config/lsfg-vk/conf.toml").writeText("version = 2\n[global]\ndll = \"${myGame.path}\"\n[[profile]]\ndll = \"no\"\n")
        assertEquals(myGame, LsfgVk.findDll(null, env(), home))
    }

    @Test
    fun theUsersGlobalDllIsRead() {
        assertEquals("/home/user/Lossless.dll", LsfgVk.userDll("version = 2\n[global]\nallow_fp16 = true\ndll = \"/home/user/Lossless.dll\"\n"))
        assertNull(LsfgVk.userDll("version = 2\n[[profile]]\ndll = \"x\"\n"))
        assertNull(LsfgVk.userDll("[global]\ndll_extra = 1\n"))
    }

    @Test
    fun detectSaysWhatIsMissing() {
        assertEquals(LsfgVk.Missing.NOT_LINUX, LsfgVk.detect(null, env(), home, linux = false, sysconfDir = etc).missing)
        assumeLinux()
        assertEquals(LsfgVk.Missing.LAYER, LsfgVk.detect(null, env(), home, linux = true, sysconfDir = etc).missing)
        layer(File(tmp, "share/vulkan/implicit_layer.d"))
        assertEquals(LsfgVk.Missing.DLL, LsfgVk.detect(null, env(), home, linux = true, sysconfDir = etc).missing)
        File(steamDir(), "lsfg-vk.dll").writeText("x")
        val a = LsfgVk.detect(null, env(), home, linux = true, sysconfDir = etc)
        assertTrue(a.available)
        assertEquals("lsfg-vk.dll", a.dll?.name)
    }

    @Test
    fun theMultiplierFitsTheRefreshRate() {
        // Tatakai Next BUG-036: 24 fps x4 = 96 fps cannot show on 60 Hz.
        assertEquals(2, LsfgVk.multiplierFor(4, 60.0))
        assertEquals(2, LsfgVk.multiplierFor(3, 59.94))
        assertEquals(3, LsfgVk.multiplierFor(4, 75.0))
        assertEquals(4, LsfgVk.multiplierFor(4, 120.0))
        assertEquals(4, LsfgVk.multiplierFor(4, 143.98))
        assertEquals(2, LsfgVk.multiplierFor(2, 144.0), "never above the choice")
        assertEquals(2, LsfgVk.multiplierFor(4, 30.0), "never below x2")
        assertEquals(4, LsfgVk.multiplierFor(4, null), "unknown refresh keeps the choice")
        assertEquals(4, LsfgVk.multiplierFor(4, 0.0))
        // Automatic: the highest that fits.
        assertEquals(2, LsfgVk.multiplierFor(0, 60.0))
        assertEquals(4, LsfgVk.multiplierFor(0, 144.0))
        assertEquals(4, LsfgVk.multiplierFor(9, null), "clamped to x4")
    }

    @Test
    fun theDedicatedConfigHasOneProfileForMpv() {
        assumeLinux()
        val dll = File("/games/Lossless Scaling/lsfg-vk.dll")
        val t = LsfgVk.configToml(9, 0.85f, performance = true, dll = dll)
        assertTrue(t.startsWith("version = 2"))
        assertTrue("dll = \"/games/Lossless Scaling/lsfg-vk.dll\"" in t)
        assertTrue("active_in = [ \"mpv\" ]" in t && "multiplier = 4" in t && "flow_scale = 0.85" in t && "performance_mode = true" in t)
        assertFalse("dll =" in LsfgVk.configToml(2, 0.1f, false, null))
        assertTrue("flow_scale = 0.25" in LsfgVk.configToml(2, 0.1f, false, null))
        val written = LsfgVk.writeConfig(File(tmp, "run"), 3, 0.85f, false, dll)
        assertEquals(3, written.multiplier)
        assertEquals(written.configFile.readText(), LsfgVk.configToml(3, 0.85f, false, dll))
        // A name nobody can guess, a new one per write.
        assertTrue(Regex("lsfg-vk-[0-9a-f]{16}\\.toml").matches(written.configFile.name), written.configFile.name)
        assertTrue(written.configFile != LsfgVk.writeConfig(File(tmp, "run"), 3, 0.85f, false, dll).configFile)
        // Control characters of a path cannot break the file.
        assertEquals("\"a\\nb\\r\\t\\u0001\\\\ \\\"q\\\"\"", LsfgVk.tomlString("a\nb\r\t\u0001\\ \"q\""))
    }
}
