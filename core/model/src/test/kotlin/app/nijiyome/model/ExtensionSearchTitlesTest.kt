// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtensionSearchTitlesTest {
    @Test
    fun longAndHyphenatedLightNovelTitlesProduceShortCleanVariantsWithinTop8() {
        val en = "The Laid-Off Cheat-Granting Mage Enjoys a Second Lease on Life"
        val romaji = "Tsuihou Sareta Cheat Fuyo Majutsushi wa Kimama na Second Life wo Ouka Suru.: Ore wa Buki dake ja Naku, Arayuru Mono ni \"Kyouka Point\" wo Fuyo Dekiru shi, Ore no Ishi de Itsudemo Kouka wo Kaijo Dekiru kedo, Nokotta Hitotachi Daijoubu?"
        val native = "追放されたチート付与魔術師は気ままなセカンドライフを謳歌する。　～俺は武器だけじゃなく、あらゆるものに『強化ポイント』を付与できるし、俺の意思でいつでも効果を解除できるけど、残った人たち大丈夫？～"
        val media = Media(
            ref = MediaRef(anilistId = 207329, malId = 63431),
            kind = MediaKind.ANIME,
            title = en,
            titles = linkedMapOf("romaji" to romaji, "english" to en, "native" to native),
            cover = null,
            banner = null,
            color = null,
            synopsis = null,
            genres = emptyList(),
            episodes = 12,
            status = "RELEASING",
            season = "FALL",
            year = 2026,
            score = 76,
            sourceProvider = "anilist",
            altTitles = listOf("Chii-Fuyo", "The Laid-Off Cheat-Granting Mage Enjoys a New Life"),
        )
        val top8 = media.extensionSearchTitles().take(8)
        // Must contain <=60-char variants for both English and Romaji AND a hyphen-safe variant for AnimeTosho/nekoBT.
        assertTrue(top8.any { it.length in 10..60 && it.startsWith("The Laid-Off Cheat-Granting") }, "expected <=60 EN variant in $top8")
        assertTrue(top8.any { it.length in 10..60 && it.startsWith("The Laid Off Cheat Granting") && '-' !in it }, "expected hyphen-safe EN variant in $top8")
        assertTrue(top8.any { it.length in 10..60 && it.startsWith("Tsuihou Sareta Cheat Fuyo") }, "expected <=60 Romaji variant in $top8")
        // Full original titles must still be preserved for release token classification.
        assertTrue(en in media.extensionSearchTitles())
        assertTrue(romaji in media.extensionSearchTitles())
    }

    @Test
    fun shortNormalTitlesStayUnchanged() {
        val media = Media(
            ref = MediaRef(anilistId = 154587),
            kind = MediaKind.ANIME,
            title = "Frieren: Beyond Journey's End",
            titles = linkedMapOf("english" to "Frieren: Beyond Journey's End", "romaji" to "Sousou no Frieren"),
            cover = null,
            banner = null,
            color = null,
            synopsis = null,
            genres = emptyList(),
            episodes = 28,
            status = "FINISHED",
            season = "FALL",
            year = 2023,
            score = 91,
            sourceProvider = "anilist",
        )
        assertEquals(listOf("Frieren: Beyond Journey's End", "Sousou no Frieren"), media.extensionSearchTitles())
    }
}
