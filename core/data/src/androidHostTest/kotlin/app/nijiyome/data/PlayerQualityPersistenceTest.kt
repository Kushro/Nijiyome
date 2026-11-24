// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.nijiyome.model.AppSettings
import app.nijiyome.model.DebandConfig
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.PlayerQualityPrefs
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import app.nijiyome.model.SharpenLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.4.0 (worker A): engine default, desktop preset ids and the quality-engine defaults. */
class PlayerQualityPersistenceTest {
    @Test
    fun newInstallDefaultsToMpv() {
        assertEquals(PlayerEngine.MPV, SettingsCodec.read(emptyPreferences()).playerEngine)
    }

    @Test
    fun storedMedia3IsKeptOnceWrittenBy040() {
        // <= 0.3.x settings (no player_quality_json) move to libmpv once (EngineMigrationTest); a MEDIA3 written by 0.4.0 stays.
        val p = mutablePreferencesOf()
        SettingsCodec.write(p, AppSettings(playerEngine = PlayerEngine.MEDIA3))
        assertEquals(PlayerEngine.MEDIA3, SettingsCodec.read(p).playerEngine)
    }

    @Test
    fun everyDesktopPresetIdRoundTrips() {
        QualityPresets.ALL.forEach { id ->
            val p = mutablePreferencesOf()
            SettingsCodec.write(p, AppSettings(playerPreset = id))
            assertEquals(id, SettingsCodec.read(p).playerPreset)
        }
    }

    @Test
    fun qualityPrefsRoundTripSanitized() {
        val prefs = PlayerQualityPrefs(
            tier = QualityTier.HQ,
            deband = DebandConfig(enabled = false, iterations = 99, threshold = 50, range = 0, grain = 10),
            autoAdjust = false,
            decoder = DecoderMode.SW,
            smoothing = true,
            sharpen = SharpenLevel.HIGH,
        )
        val p = mutablePreferencesOf()
        SettingsCodec.write(p, AppSettings(playerQuality = prefs))
        val read = SettingsCodec.read(p).playerQuality
        assertEquals(prefs.copy(deband = prefs.deband.copy(iterations = 16, range = 1)), read)
    }

    @Test
    fun brokenQualityJsonReadsDefaults() {
        val p = mutablePreferencesOf(stringPreferencesKey("player_quality_json") to "{\"tier\":\"ultra\"")
        assertEquals(PlayerQualityPrefs(), SettingsCodec.read(p).playerQuality)
    }
}
