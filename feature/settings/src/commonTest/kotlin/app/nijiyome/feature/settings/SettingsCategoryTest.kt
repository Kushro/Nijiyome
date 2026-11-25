// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.model.SettingsSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Sparkles redesign (lane S10): the 14 categories, the old deep links and the stable ids of the search index. */
class SettingsCategoryTest {
    @Test
    fun `there are fourteen categories in the artifact order`() {
        assertEquals(14, SettingsCategory.entries.size)
        assertEquals("apariencia", SettingsCategory.entries.first().id)
        assertEquals("acerca", SettingsCategory.entries.last().id)
    }

    @Test
    fun `every old section still opens a category`() {
        SettingsSection.entries.forEach { assertNotNull(it.toCategory(), "$it") }
        assertEquals(SettingsCategory.SYSTEM, SettingsSection.NETWORK.toCategory())
        assertEquals(SettingsCategory.TRANSFERS, SettingsSection.TORRENTS.toCategory())
        assertEquals(SettingsCategory.TRANSFERS, SettingsSection.DOWNLOADS.toCategory())
        assertEquals(SettingsCategory.READERS, SettingsSection.READER.toCategory())
    }

    @Test
    fun `accounts need the lists service and system exists on desktop only`() {
        val phone = settingsCategories(hasLists = false, systemOffered = false)
        assertFalse(SettingsCategory.ACCOUNTS in phone)
        assertFalse(SettingsCategory.SYSTEM in phone)
        assertEquals(14, settingsCategories(hasLists = true, systemOffered = true).size)
    }

    @Test
    fun `catalog ids are unique and keep the ids the command palette already passes`() {
        val ids = SettingsCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val legacy = listOf(
            "palette", "theme_mode", "amoled", "contrast", "dynamic_color", "language", "look_presets", "motion", "rail_style",
            "player_chrome_style", "player_engine", "player_preset", "player_sources", "player_audio_sub_lang", "player_sub_style",
            "player_playback", "player_shortcuts", "player_embed", "player_framegen", "metadata_providers", "metadata_title_lang",
            "metadata_adult", "metadata_notifications", "metadata_search_history", "accounts_anilist_mal", "link_pairing", "link_cache",
            "network_proxy", "network_flaresolverr", "torrents_engine", "torrents_debrid", "downloads_web", "reader_manga",
            "reader_novel_tts", "data_storage", "data_desktop_system", "data_reset", "about_logs", "about_updates",
        )
        legacy.forEach { assertTrue(it in ids, it) }
    }

    @Test
    fun `network settings are found under red or proxy in their new homes`() {
        assertEquals(SettingsCategory.SYSTEM, SettingsCatalog.categoryOf("network_proxy"))
        assertEquals(SettingsCategory.SYSTEM, SettingsCatalog.categoryOf("network_media_proxy"))
        assertEquals(SettingsCategory.SOURCES, SettingsCatalog.categoryOf("network_flaresolverr"))
        val net = SettingsCatalog.entries.filter { it.id.startsWith("network_") }
        assertTrue(net.all { "red" in it.keywords })
        assertTrue(net.filter { it.id != "network_flaresolverr" }.all { e -> e.keywords.any { "proxy" in it } })
    }

    @Test
    fun `search ranks the title prefix first and ignores accents`() {
        val items = listOf(
            SettingCatalogItem("a", SettingsSection.APPEARANCE, "Paleta", "", listOf("color")),
            SettingCatalogItem("b", SettingsSection.PLAYER, "Subtítulos preferidos", "", emptyList()),
        )
        val all = SettingsCategory.entries.toSet()
        assertEquals(listOf("b"), SettingsCatalog.search("subtitulos", items, all).map { it.id })
        assertEquals(listOf("a"), SettingsCatalog.search("color", items, all).map { it.id })
        assertTrue(SettingsCatalog.search("color", items, setOf(SettingsCategory.ABOUT)).isEmpty())
    }

    @Test
    fun `hosts and urls are validated`() {
        assertTrue(proxyHostValid("127.0.0.1"))
        assertFalse(proxyHostValid("user@host"))
        assertFalse(proxyHostValid("host/path"))
        assertTrue(httpUrlValid("http://127.0.0.1:8191"))
        assertFalse(httpUrlValid("127.0.0.1:8191"))
        assertFalse(httpUrlValid("http://"))
    }
}
