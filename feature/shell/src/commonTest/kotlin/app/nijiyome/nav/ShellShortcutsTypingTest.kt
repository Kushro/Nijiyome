// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A focused text field does not consume a digit's key press: while it takes text, 1..9 are text, not rail shortcuts. */
class ShellShortcutsTypingTest {
    @Test
    fun digitsAreTextWhileAFieldTakesText() {
        val seen = mutableListOf<ShellShortcut>()
        val shortcuts = ShellShortcuts().apply { handler = { seen += it; true } }
        fun press(key: Key, ctrl: Boolean = false) = shortcuts.onKeyDown(key, ctrl, alt = false, meta = false)
        assertTrue(press(Key.Two))
        shortcuts.textInputStarted()
        assertFalse(press(Key.Three))
        // Ctrl+K and Esc still work while typing (Tatakai Next)
        assertTrue(press(Key.K, ctrl = true))
        assertTrue(press(Key.Escape))
        shortcuts.textInputEnded()
        assertTrue(press(Key.Four))
        assertEquals(listOf(ShellShortcut.NavTo(2), ShellShortcut.FocusSearch, ShellShortcut.Back, ShellShortcut.NavTo(4)), seen)
        // without a handler (no shell yet) nothing is used
        assertFalse(ShellShortcuts().onKeyDown(Key.One, ctrl = false, alt = false, meta = false))
    }

    @Test
    fun noShortcutActsWhileADialogOrSheetIsOpen() {
        // v1.1: the keys a dialog did not use still reach the window; none of them is a shortcut while it is open.
        val seen = mutableListOf<ShellShortcut>()
        var modal = true
        var backs = 0
        val shortcuts = ShellShortcuts(platformBack = { backs++; true }, modalOpen = { modal }).apply { handler = { seen += it; true } }
        fun press(key: Key, ctrl: Boolean = false) = shortcuts.onKeyDown(key, ctrl, alt = false, meta = false)
        assertFalse(press(Key.One))
        assertFalse(press(Key.NumPad9))
        assertFalse(press(Key.K, ctrl = true))
        assertFalse(press(Key.B, ctrl = true))
        assertFalse(press(Key.Escape))
        assertEquals(emptyList(), seen)
        assertEquals(0, backs)
        // closed again: everything works as before
        modal = false
        assertTrue(press(Key.One))
        assertTrue(press(Key.K, ctrl = true))
        assertEquals(listOf(ShellShortcut.NavTo(1), ShellShortcut.FocusSearch), seen)
    }

    @Test
    fun escapeInsideAViewerGoesToTheViewer() {
        var viewerEscapes = 0
        val shortcuts = ShellShortcuts(viewerEscape = { viewerEscapes++; true })
        kotlin.test.assertTrue(shortcuts.escapeViewer())
        assertEquals(1, viewerEscapes)
        kotlin.test.assertFalse(ShellShortcuts().escapeViewer())
    }
}
