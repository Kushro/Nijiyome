// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.nijiyome.model.AppSettings
import app.nijiyome.model.torrent.TorrentSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDefaultSettingsTest {
    @Test
    fun aFirstRunOnDesktopGetsTheDesktopTorrentValues() {
        val t = SettingsCodec.read(emptyPreferences(), DESKTOP_DEFAULT_SETTINGS).torrent
        assertTrue(t.upnp)
        assertEquals(50, t.maxPeers)
        assertFalse(t.preloadNext)
        // the shared (Android) defaults do not change
        assertEquals(TorrentSettings(), SettingsCodec.read(emptyPreferences()).torrent)
        assertEquals(AppSettings().torrent, TorrentSettings())
    }

    @Test
    fun storedTorrentSettingsWin() {
        val p = mutablePreferencesOf(stringPreferencesKey("torrent_settings_json") to """{"upnp":false,"maxPeers":12}""")
        val t = SettingsCodec.read(p, DESKTOP_DEFAULT_SETTINGS).torrent
        assertFalse(t.upnp)
        assertEquals(12, t.maxPeers)
    }
}
