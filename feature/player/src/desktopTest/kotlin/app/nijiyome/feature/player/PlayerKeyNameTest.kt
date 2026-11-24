// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.ui.input.key.Key
import app.nijiyome.player.PlayerKeymap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Desktop keys get the names Android's `KeyEvent.keyCodeToString` gives the same keys, so one saved keymap means the same on both. */
class PlayerKeyNameTest {
    @Test
    fun defaultKeymapKeysHaveTheirAndroidNames() {
        val expected = mapOf(
            Key.Spacebar to "SPACE",
            Key.DirectionLeft to "DPAD_LEFT",
            Key.DirectionRight to "DPAD_RIGHT",
            Key.DirectionUp to "DPAD_UP",
            Key.DirectionDown to "DPAD_DOWN",
            Key.F to "F",
            Key.M to "M",
            Key.P to "P",
            Key.J to "J",
            Key.K to "K",
            Key.S to "S",
            Key.C to "C",
            Key.Slash to "SLASH",
        )
        expected.forEach { (key, name) -> assertEquals(name, playerKeyName(key), "$key") }
        // every default binding is reachable from a desktop key
        val names = expected.values.toSet()
        PlayerKeymap.DEFAULTS.forEach { (action, key) -> assertTrue(key in names, "$action -> $key") }
    }

    @Test
    fun lettersDigitsAndSystemKeys() {
        assertEquals("A", playerKeyName(Key.A))
        assertEquals("Z", playerKeyName(Key.Z))
        assertEquals("0", playerKeyName(Key.Zero))
        assertEquals("9", playerKeyName(Key.Nine))
        assertEquals("NUMPAD_5", playerKeyName(Key.NumPad5))
        assertEquals("F11", playerKeyName(Key.F11))
        assertEquals("ESCAPE", playerKeyName(Key.Escape))
        assertEquals("ENTER", playerKeyName(Key.Enter))
        assertEquals("TAB", playerKeyName(Key.Tab))
        assertEquals("PAGE_UP", playerKeyName(Key.PageUp))
        assertEquals("MOVE_HOME", playerKeyName(Key.MoveHome))
        assertEquals("MEDIA_PLAY_PAUSE", playerKeyName(Key.MediaPlayPause))
    }

    @Test
    fun namesResolveToTheirActions() {
        assertEquals(PlayerKeymap.PLAY_PAUSE, PlayerKeymap.actionFor(playerKeyName(Key.Spacebar)!!, emptyMap()))
        assertEquals(PlayerKeymap.HELP, PlayerKeymap.actionFor(playerKeyName(Key.Slash)!!, emptyMap()))
        assertEquals(PlayerKeymap.SEEK_FORWARD, PlayerKeymap.actionFor(playerKeyName(Key.DirectionRight)!!, emptyMap()))
        // a key the user bound on Android
        assertEquals(PlayerKeymap.SCREENSHOT, PlayerKeymap.actionFor(playerKeyName(Key.Nine)!!, mapOf(PlayerKeymap.SCREENSHOT to "9")))
    }

    @Test
    fun unnamedKeyIsNull() {
        assertNull(playerKeyName(Key.ShiftLeft))
    }
}
