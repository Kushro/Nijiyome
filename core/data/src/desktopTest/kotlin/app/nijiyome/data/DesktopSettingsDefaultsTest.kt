// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.nijiyome.model.AppSettings
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hito 6 (player-13): a new desktop install starts like Tatakai Next; what is stored wins; Android's defaults are unchanged. */
class DesktopSettingsDefaultsTest {
    @Test
    fun newDesktopInstallHasUpscalingAndDebandOff() {
        val s = SettingsCodec.read(emptyPreferences(), DESKTOP_SETTINGS_DEFAULTS)
        assertEquals(QualityPresets.OFF, s.playerPreset)
        assertFalse(s.playerQuality.deband.enabled)
        assertEquals(QualityTier.BALANCED, s.playerQuality.tier)
    }

    @Test
    fun androidDefaultsAreTheModelsOwn() {
        val s = SettingsCodec.read(emptyPreferences())
        assertEquals(AppSettings().playerPreset, s.playerPreset)
        assertTrue(s.playerQuality.deband.enabled)
    }

    @Test
    fun aStoredChoiceIsKept() {
        val p = mutablePreferencesOf()
        SettingsCodec.write(p, AppSettings(playerPreset = QualityPresets.ANIME4K_AA))
        val s = SettingsCodec.read(p, DESKTOP_SETTINGS_DEFAULTS)
        assertEquals(QualityPresets.ANIME4K_AA, s.playerPreset)
        assertTrue(s.playerQuality.deband.enabled)
    }
}
