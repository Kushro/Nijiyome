// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.model.PlayerEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfferedEnginesTest {
    @Test
    fun everyStoredEngineShowsAsOneThePlatformOffers() {
        assertTrue(PlayerEngine.MPV in offeredEngines)
        assertTrue(PlayerEngine.EXTERNAL in offeredEngines)
        PlayerEngine.entries.forEach { assertTrue(it.shown() in offeredEngines, "$it") }
        offeredEngines.forEach { assertEquals(it, it.shown()) }
    }
}
