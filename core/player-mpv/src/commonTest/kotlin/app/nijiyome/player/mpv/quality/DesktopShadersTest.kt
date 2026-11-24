// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.quality

import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import app.nijiyome.player.QualityReason
import app.nijiyome.player.QualityRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.1 (F2): the desktop-only shaders (Anime4K VL on HQ, NVScaler) only exist with the desktop signal; without it nothing changes. */
class DesktopShadersTest {
    private class Port : QualityMpvPort {
        val props = HashMap<String, String>()
        override fun setProperty(name: String, value: String) {
            props[name] = value
        }
        override fun getProperty(name: String): String? = props[name]
    }

    private val resolver = ShaderResolver { specs, fp16 -> ShaderNames.relativePaths(specs, fp16).map { "/s/$it" } }

    private fun controller(port: Port, r: QualityRequest, desktop: Boolean) =
        MpvQualityController(port, resolver, r, fp16 = false, now = { 0L }, desktopShaders = desktop).apply {
            onVideoSize(1280, 720)
            onViewport(2560, 1440)
        }

    @Test
    fun hqUsesVlOnlyOnDesktop() {
        assertEquals("L" to "M", ShaderCatalog.networks(QualityTier.HQ))
        assertEquals("VL" to "M", ShaderCatalog.networks(QualityTier.HQ, desktopShaders = true))
        assertEquals("M" to "S", ShaderCatalog.networks(QualityTier.BALANCED, desktopShaders = true))
        val phone = ShaderCatalog.chain(QualityPresets.ANIME4K_A, QualityTier.HQ)
        val pc = ShaderCatalog.chain(QualityPresets.ANIME4K_A, QualityTier.HQ, desktopShaders = true)
        assertTrue("Anime4K_Restore_CNN_L.glsl" in phone && phone.none { "_VL" in it })
        assertTrue("Anime4K_Restore_CNN_VL.glsl" in pc && "Anime4K_Upscale_CNN_x2_VL.glsl" in pc)
    }

    @Test
    fun nvscalerIsADesktopPreset() {
        assertFalse(QualityPresets.NVSCALER in QualityPresets.ALL)
        assertEquals(QualityPresets.NVSCALER, QualityPresets.sanitize("nvscaler"))
        assertEquals(listOf(ShaderCatalog.NVSCALER), ShaderCatalog.chain(QualityPresets.NVSCALER, QualityTier.BALANCED))
        assertEquals(QualityReason.RENDERER_UNSUPPORTED, ShaderCatalog.unsupported(QualityPresets.NVSCALER))
        assertNull(ShaderCatalog.unsupported(QualityPresets.NVSCALER, desktopShaders = true))
        assertTrue(ShaderCatalog.isUpscaler(ShaderCatalog.NVSCALER))
    }

    @Test
    fun desktopFilesAreTheVlNetworksAndNvscaler() {
        assertEquals(
            setOf(
                "Anime4K_Restore_CNN_VL.glsl", "Anime4K_Restore_CNN_Soft_VL.glsl", "Anime4K_Upscale_CNN_x2_VL.glsl",
                "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl", "NVScaler.glsl",
            ),
            ShaderCatalog.DESKTOP_FILES,
        )
        assertTrue(ShaderCatalog.ALL_FILES.none { it in ShaderCatalog.DESKTOP_FILES }, "Android's bundled list is unchanged")
    }

    @Test
    fun theControllerOffersAndAppliesThemOnlyWithTheSignal() {
        val phone = controller(Port(), QualityRequest(preset = QualityPresets.ANIME4K_A, tier = QualityTier.HQ), desktop = false)
        assertEquals(QualityPresets.ALL, phone.state.value.presets.map { it.id })
        assertTrue(phone.state.value.activeShaders.none { "_VL" in it })

        val pcPort = Port()
        val pc = controller(pcPort, QualityRequest(preset = QualityPresets.ANIME4K_A, tier = QualityTier.HQ), desktop = true)
        val ids = pc.state.value.presets.map { it.id }
        assertEquals(QualityPresets.ALL.size + 1, ids.size)
        assertEquals(ids.indexOf(QualityPresets.ARTCNN) - 1, ids.indexOf(QualityPresets.NVSCALER))
        assertTrue(pc.state.value.presets.single { it.id == QualityPresets.NVSCALER }.available)
        assertTrue(pcPort.props["glsl-shaders"]!!.contains("Anime4K_Restore_CNN_VL.glsl"))

        val nvPort = Port()
        val nv = controller(nvPort, QualityRequest(preset = QualityPresets.NVSCALER), desktop = true)
        assertEquals("/s/NVScaler.glsl", nvPort.props["glsl-shaders"])
        assertEquals(QualityPresets.NVSCALER, nv.state.value.effective.preset)

        // Without the desktop files (Android, or a desktop build without them) NVScaler is listed nowhere and falls back to off.
        val off = controller(Port(), QualityRequest(preset = QualityPresets.NVSCALER), desktop = false)
        assertEquals(QualityPresets.OFF, off.state.value.effective.preset)
    }
}
