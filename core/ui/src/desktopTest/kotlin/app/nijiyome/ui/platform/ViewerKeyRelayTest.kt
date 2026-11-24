// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.ui.focus.FocusRequester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerKeyRelayTest {
    @Test
    fun anEscapeThatReachedTheWindowRunsTheNewestViewersEscape() {
        val relay = ViewerKeyRelay()
        assertFalse(relay.escape())
        val calls = mutableListOf<String>()
        // a FocusRequester not attached to anything: requesting focus must not stop the viewer's Esc
        val removeReader = relay.add({ calls += "reader" }, FocusRequester())
        val removePlayer = relay.add({ calls += "player" }, FocusRequester())
        assertTrue(relay.escape())
        removePlayer()
        assertTrue(relay.escape())
        removeReader()
        assertFalse(relay.escape())
        assertEquals(listOf("player", "reader"), calls)
    }
}
