// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** v1.3.3 paper skins: the old flat paper is gone and everyone without a choice lands on Pastel en rama. */
class TanzakuSkinTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `a store written before v1 3 3 reads as Pastel en rama`() {
        val old = json.decodeFromString(DesignPrefs.serializer(), """{"card":"tanzaku","introSeen":true}""")
        assertEquals(null, old.tanzakuSkin)
        assertEquals(TanzakuSkinIds.PASTEL, old.tanzakuSkinId())
        assertEquals(TanzakuSkinIds.PASTEL, DesignPrefs().tanzakuSkinId())
    }

    @Test
    fun `an unknown or removed skin falls back to the default, a chosen one round trips`() {
        assertEquals(TanzakuSkinIds.DEFAULT, DesignPrefs(tanzakuSkin = "flat").tanzakuSkinId())
        TanzakuSkinIds.ALL.forEach { id ->
            val back = json.decodeFromString(DesignPrefs.serializer(), json.encodeToString(DesignPrefs.serializer(), DesignPrefs(tanzakuSkin = id)))
            assertEquals(id, back.tanzakuSkinId())
        }
    }

    @Test
    fun `ten skins, all distinct, and only the tanzaku cards wear them`() {
        assertEquals(10, TanzakuSkinIds.ALL.toSet().size)
        assertTrue(TitleCardIds.isTanzaku(TitleCardIds.TANZAKU_VERTICAL))
        assertTrue(!TitleCardIds.isTanzaku(TitleCardIds.OBI))
    }
}
