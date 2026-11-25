// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sheets

import app.nijiyome.model.QualityPresets
import app.nijiyome.player.PresetOption
import app.nijiyome.player.QualityReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sparkles redesign (lane S6): the upscaling presets grouped by family in the "Mejoras" sheet. */
class EnhancementFamiliesTest {
    private val android = QualityPresets.ALL.map { id ->
        if (id == QualityPresets.ARTCNN) PresetOption(id, available = false, unavailableReason = QualityReason.RENDERER_UNSUPPORTED) else PresetOption(id, available = true)
    }

    @Test
    fun everyPresetHasOneFamily() {
        (QualityPresets.ALL + QualityPresets.DESKTOP_EXTRA).forEach { id ->
            assertEquals(1, EnhancementFamily.entries.count { id in it.presets }, id)
        }
        assertEquals(EnhancementFamily.NONE, EnhancementFamily.of("unknown"))
        assertEquals(EnhancementFamily.ANIME4K, EnhancementFamily.of(QualityPresets.ANIME4K_CA))
    }

    @Test
    fun familiesFollowWhatTheEngineLists() {
        val families = familyChoices(android)
        assertEquals(
            listOf(
                EnhancementFamily.NONE, EnhancementFamily.ANIME4K, EnhancementFamily.FSR, EnhancementFamily.FIDELITY, EnhancementFamily.CAS,
                EnhancementFamily.ARTCNN,
            ),
            families.map { it.family },
        )
        val anime4k = families.first { it.family == EnhancementFamily.ANIME4K }
        assertTrue(anime4k.hasModes)
        assertEquals(6, anime4k.presets.size)
        val artcnn = families.first { it.family == EnhancementFamily.ARTCNN }
        assertFalse(artcnn.available)
        assertEquals(QualityReason.RENDERER_UNSUPPORTED, artcnn.reason)
    }

    @Test
    fun noUpscalingIsAlwaysOffered() {
        val families = familyChoices(listOf(PresetOption(QualityPresets.FSR, available = true)))
        assertEquals(listOf(EnhancementFamily.NONE, EnhancementFamily.FSR), families.map { it.family })
    }

    @Test
    fun pickingAFamilyKeepsTheModeInUseOrTheLastOne() {
        val anime4k = familyChoices(android).first { it.family == EnhancementFamily.ANIME4K }
        assertEquals(QualityPresets.ANIME4K_BB, presetForFamily(anime4k, QualityPresets.ANIME4K_BB, last = null))
        assertEquals(QualityPresets.ANIME4K_C, presetForFamily(anime4k, QualityPresets.FSR, last = QualityPresets.ANIME4K_C))
        assertEquals(QualityPresets.ANIME4K_A, presetForFamily(anime4k, QualityPresets.OFF, last = null))
        val artcnn = familyChoices(android).first { it.family == EnhancementFamily.ARTCNN }
        assertNull(presetForFamily(artcnn, QualityPresets.OFF, last = null))
    }

    @Test
    fun desktopShortcutsMatchTheKeys() {
        assertEquals(1, ctrlShortcut(QualityPresets.OFF))
        assertEquals(3, ctrlShortcut(QualityPresets.ANIME4K_AA))
        assertEquals(6, ctrlShortcut(QualityPresets.FSR))
        assertNull(ctrlShortcut(QualityPresets.CAS))
        assertEquals("C+A", modeLabel(QualityPresets.ANIME4K_CA))
    }
}
