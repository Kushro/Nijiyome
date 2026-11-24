// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hito 6: the desktop's extra keymap actions, and that Android (no `extras`) sees exactly the 13 actions it had. */
class DesktopKeymapExtrasTest {
    @Test
    fun androidKeepsItsThirteenActions() {
        assertEquals(PlayerKeymap.ACTIONS, PlayerKeymap.actions())
        assertEquals(13, PlayerKeymap.resolve(emptyMap()).size)
        assertNull(PlayerKeymap.actionFor("PERIOD", emptyMap()))
        assertNull(PlayerKeymap.actionFor("L", emptyMap()))
        // a desktop entry in the stored map means nothing on Android and is dropped by its compact()
        assertEquals(emptyMap(), PlayerKeymap.compact(mapOf(PlayerKeymap.LOOP to "O")))
        assertEquals(mapOf(PlayerKeymap.MUTE to "Q"), PlayerKeymap.bind(emptyMap(), PlayerKeymap.MUTE, "q"))
        assertEquals(emptyMap(), PlayerKeymap.bind(emptyMap(), PlayerKeymap.LOOP, "O"))
    }

    @Test
    fun desktopResolvesTatakaiNextKeys() {
        val map = PlayerKeymap.resolve(emptyMap(), extras = true)
        assertEquals(20, map.size)
        assertEquals(PlayerKeymap.SPEED_UP, PlayerKeymap.actionFor("PERIOD", emptyMap(), extras = true))
        assertEquals(PlayerKeymap.SPEED_DOWN, PlayerKeymap.actionFor("COMMA", emptyMap(), extras = true))
        assertEquals(PlayerKeymap.AUDIO_CYCLE, PlayerKeymap.actionFor("V", emptyMap(), extras = true))
        assertEquals(PlayerKeymap.SUB_DELAY_EARLIER, PlayerKeymap.actionFor("Z", emptyMap(), extras = true))
        assertEquals(PlayerKeymap.SUB_DELAY_LATER, PlayerKeymap.actionFor("X", emptyMap(), extras = true))
        assertEquals(PlayerKeymap.SKIP_SEGMENT, PlayerKeymap.actionFor("ENTER", emptyMap(), extras = true))
        assertEquals(PlayerKeymap.LOOP, PlayerKeymap.actionFor("L", emptyMap(), extras = true))
        // the shared defaults do not clash with the extras
        assertEquals(emptyMap(), PlayerKeymap.conflicts(emptyMap(), extras = true))
    }

    @Test
    fun desktopRebindsAndResetsTheExtras() {
        val custom = PlayerKeymap.bind(emptyMap(), PlayerKeymap.LOOP, "o", extras = true)
        assertEquals(mapOf(PlayerKeymap.LOOP to "O"), custom)
        assertEquals(PlayerKeymap.LOOP, PlayerKeymap.actionFor("O", custom, extras = true))
        // binding back to the default drops the entry
        assertEquals(emptyMap(), PlayerKeymap.bind(custom, PlayerKeymap.LOOP, "L", extras = true))
        assertEquals(listOf(PlayerKeymap.AUDIO_CYCLE), PlayerKeymap.conflictsWith(emptyMap(), PlayerKeymap.LOOP, "V", extras = true))
        assertTrue(PlayerKeymap.conflicts(mapOf(PlayerKeymap.LOOP to "V"), extras = true).containsKey("V"))
    }
}
