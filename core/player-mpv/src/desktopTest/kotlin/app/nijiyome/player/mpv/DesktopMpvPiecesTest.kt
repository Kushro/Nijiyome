// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.model.DecoderMode
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.mpv.quality.MpvQualityController
import app.nijiyome.player.mpv.quality.ShaderCatalog
import app.nijiyome.player.mpv.quality.ShaderNames
import app.nijiyome.player.mpv.quality.ShaderSpec
import app.nijiyome.player.mpv.quality.ShaderTransform
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Hito 4: the pure parts of the desktop engine (headers, sub-add arguments, desktop option values, the bundled assets and the shader
// installer against a temporary folder). No libmpv here: see DesktopMpvEngineIntegrationTest.
class DesktopMpvPiecesTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-mpv-test").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    @Test
    fun headersUseMediampKeysAndEscapeCommas() {
        val h = mediampHeaders(linkedMapOf("referer" to "https://a.test/ep/1", "user-agent" to "UA/1", "X-Token" to "a,b", "Origin" to "o"))
        assertEquals(listOf("User-Agent", "Referer", "X-Token", "Origin"), h.keys.toList())
        assertEquals("UA/1", h["User-Agent"])
        assertEquals("https://a.test/ep/1", h["Referer"])
        assertEquals("a\\,b", h["X-Token"])
        val none = mediampHeaders(emptyMap())
        assertEquals(mapOf("User-Agent" to MpvEngineDefaults.DEFAULT_USER_AGENT), none, "a previous load's agent never sticks")
    }

    @Test
    fun subAddArgumentsMatchAndroid() {
        assertContentEquals(arrayOf("sub-add", "u", "auto"), subAddCommand(MpvSubtitle("u")))
        assertContentEquals(arrayOf("sub-add", "u", "select", "Español"), subAddCommand(MpvSubtitle("u", "Español", select = true)))
        assertContentEquals(arrayOf("sub-add", "u", "auto", "", "es-419"), subAddCommand(MpvSubtitle("u", lang = "es-419")))
    }

    @Test
    fun desktopDecodersAndShaderLists() {
        assertEquals("nvdec,vaapi-copy,auto-safe", desktopHwdec(DecoderMode.AUTO.hwdec, windows = false))
        assertEquals("auto-safe", desktopHwdec(DecoderMode.AUTO.hwdec, windows = true))
        assertEquals("auto-copy", desktopHwdec(DecoderMode.HW.hwdec, windows = false))
        assertEquals("auto", desktopHwdec(DecoderMode.HW_PLUS.hwdec, windows = false))
        assertEquals("no", desktopHwdec(DecoderMode.SW.hwdec, windows = true))
        assertEquals("/a/b.glsl:/c.glsl", desktopQualityValue("glsl-shaders", "/a/b.glsl:/c.glsl", windows = false))
        assertEquals("C:\\a\\b.glsl;D:\\c.glsl", windowsShaderList("C:\\a\\b.glsl:D:\\c.glsl"))
        assertEquals("", windowsShaderList(""))
        assertEquals("yes", desktopQualityValue("deband", "yes", windows = true))
    }

    @Test
    fun bundledAssetsAreOnTheClasspath() {
        val shaders = DesktopMpvAssets.list(ShaderCatalog.ASSET_DIR)
        assertTrue(ShaderCatalog.CLAMP in shaders && ShaderCatalog.CAS in shaders, "shaders: $shaders")
        // The 20 shared with Android plus v1.1's desktop-only ones (VL networks, NVScaler).
        assertEquals(20 + ShaderCatalog.DESKTOP_FILES.size, shaders.size)
        assertTrue(shaders.containsAll(ShaderCatalog.DESKTOP_FILES), "shaders: $shaders")
        val fonts = DesktopMpvAssets.list(DesktopMpvAssets.FONTS_DIR)
        assertEquals(4, fonts.size)
        assertTrue(fonts.all { it.startsWith("nijiyome_") && it.endsWith(".ttf") })
        val dir = File(tmp, "fonts")
        assertEquals(4, DesktopMpvAssets.installSubtitleFonts(dir))
        val outfit = File(dir, "nijiyome_outfit_bold.ttf")
        assertContentEquals(DesktopMpvAssets.read("subfonts/nijiyome_outfit_bold.ttf"), outfit.readBytes())
        val stamp = outfit.lastModified()
        Thread.sleep(20)
        DesktopMpvAssets.installSubtitleFonts(dir)
        assertEquals(stamp, outfit.lastModified(), "an identical font is not rewritten")
    }

    @Test
    fun installerWritesTheChainWithAndroidsRelativePaths() {
        val dir = File(tmp, "shaders")
        val specs = listOf(
            ShaderSpec(ShaderCatalog.CLAMP),
            ShaderSpec("Anime4K_Restore_CNN_S.glsl"),
            ShaderSpec("Anime4K_Restore_CNN_S.glsl"),
            ShaderSpec(ShaderCatalog.CAS, ShaderTransform.CAS, casSharpening = 0.5),
        )
        val paths = assertNotNull(DesktopShaderInstaller(dir).resolve(specs, fp16 = true))
        val expected = ShaderNames.relativePaths(specs, fp16 = true).map { File(dir, it).absolutePath }
        assertEquals(expected, paths)
        assertTrue(paths.all { File(it).length() > 0 })
        assertTrue(paths[1].contains(ShaderNames.FP16_DIR) && paths[2].endsWith("__1.glsl"), "$paths")
        val cas = File(paths[3]).readText()
        assertTrue("#define SHARPENING 0.50" in cas, "CAS variant keeps the asked sharpening")
        val raw = DesktopMpvAssets.read("shaders/Anime4K_Restore_CNN_S.glsl")!!
        assertFalse(File(paths[1]).readBytes().contentEquals(raw), "FP16 copy is rewritten")
        // Full precision: the shipped file as it is. A stale copy (a previous build's) is replaced by a new installer (a new process).
        val clamp = assertNotNull(DesktopShaderInstaller(dir).resolve(specs.take(1), fp16 = false)).single()
        assertContentEquals(DesktopMpvAssets.read("shaders/${ShaderCatalog.CLAMP}"), File(clamp).readBytes())
        File(clamp).writeText("stale")
        DesktopShaderInstaller(dir).resolve(specs.take(1), fp16 = false)
        assertContentEquals(DesktopMpvAssets.read("shaders/${ShaderCatalog.CLAMP}"), File(clamp).readBytes())
        assertEquals(emptyList(), DesktopShaderInstaller(dir).resolve(emptyList(), fp16 = false))
    }

    @Test
    fun aMissingShaderLeavesTheChainOff() {
        val installer = DesktopShaderInstaller(File(tmp, "shaders")) { null }
        assertEquals(null, installer.resolve(listOf(ShaderSpec(ShaderCatalog.CLAMP)), fp16 = false))
    }

    @Test
    fun qualityControllerWritesDesktopValues() {
        val props = HashMap<String, String>()
        val port = DesktopQualityPort(write = { k, v -> props[k] = v }, readProperty = { props[it] }, windows = false)
        val c = MpvQualityController(port, DesktopShaderInstaller(File(tmp, "shaders")), QualityRequest(preset = "anime4k-a"))
        assertEquals(LINUX_AUTO_HWDEC, props["hwdec"])
        assertEquals("", props["glsl-shaders"], "the chain waits for the video size")
        c.onVideoSize(1280, 720)
        c.onViewport(1920, 1080)
        val chain = props["glsl-shaders"]!!.split(":")
        assertEquals(c.state.value.activeShaders.size, chain.size)
        assertTrue(chain.all { File(it).isFile && it.startsWith(File(tmp, "shaders").absolutePath) }, "$chain")
        c.setDecoder(DecoderMode.SW)
        assertEquals("no", props["hwdec"])
    }
}
