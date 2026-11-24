// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.player.mpv.DesktopVideoDecoding
import kotlin.test.Test
import kotlin.test.assertEquals

class DecoderStatusTest {
    @Test
    fun `the gpu name comes out of the driver string`() {
        assertEquals(
            "AMD Radeon RX 9070 XT",
            gpuName("Mesa Gallium driver 26.3.1-arch3.5 for AMD Radeon RX 9070 XT (radeonsi, gfx1201, ACO, DRM 3.64, 7.3.5-2-cachyos)"),
        )
        assertEquals("Intel(R) Gen Graphics - 24.1.0", gpuName("Intel iHD driver for Intel(R) Gen Graphics - 24.1.0 ()"))
        assertEquals("Splitted-Desktop Systems VDPAU backend", gpuName("Splitted-Desktop Systems VDPAU backend"))
        assertEquals("VAAPI", gpuName(null))
    }

    @Test
    fun `codecs are listed in a fixed order`() {
        val v = DesktopVideoDecoding.Vaapi("x", h264 = true, hevc = true, hevcMain10 = false, av1 = true, vp9 = false)
        assertEquals(listOf("H.264", "HEVC", "AV1"), vaapiCodecs(v))
        assertEquals(emptyList(), vaapiCodecs(v.copy(h264 = false, hevc = false, av1 = false)))
    }
}
