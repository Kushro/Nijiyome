// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tatakai Next `shortcut_in`: Ctrl+K / Ctrl+B anywhere, Esc and 1..9 without modifiers. */
class ShellShortcutsTest {
    private fun plain(key: Key) = shellShortcut(key, ctrl = false, alt = false, meta = false)
    private fun ctrl(key: Key) = shellShortcut(key, ctrl = true, alt = false, meta = false)

    @Test
    fun ctrlKAndCtrlB() {
        assertEquals(ShellShortcut.FocusSearch, ctrl(Key.K))
        assertEquals(ShellShortcut.ToggleNav, ctrl(Key.B))
        assertNull(ctrl(Key.F))
        assertNull(plain(Key.K))
        assertNull(plain(Key.B))
        assertNull(shellShortcut(Key.K, ctrl = true, alt = true, meta = false))
    }

    @Test
    fun digitsOpenTheRailDestinations() {
        assertEquals(ShellShortcut.NavTo(1), plain(Key.One))
        assertEquals(ShellShortcut.NavTo(9), plain(Key.Nine))
        assertEquals(ShellShortcut.NavTo(3), plain(Key.NumPad3))
        assertNull(plain(Key.Zero))
        // Ctrl+1..6 belongs to the player's upscaling presets
        assertNull(ctrl(Key.One))
        assertNull(shellShortcut(Key.Two, ctrl = false, alt = true, meta = false))
    }

    @Test
    fun escapeGoesBack() {
        assertEquals(ShellShortcut.Back, plain(Key.Escape))
        assertNull(ctrl(Key.Escape))
    }
}
