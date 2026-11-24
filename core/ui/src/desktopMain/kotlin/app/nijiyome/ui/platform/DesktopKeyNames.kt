// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.ui.input.key.Key

/**
 * The name Android's `KeyEvent.keyCodeToString` gives a Compose [Key], without `KEYCODE_` (`SPACE`, `DPAD_LEFT`, `F`, `0`, `SLASH`), or
 * null for keys without one here. The player and the manga reader keymaps store these names, so a keymap saved on one platform means
 * the same keys on the other.
 */
fun androidKeyName(key: Key): String? = DESKTOP_KEY_NAMES[key]

private val DESKTOP_KEY_NAMES: Map<Key, String> = buildMap {
    val letters = listOf(
        Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M, Key.N, Key.O, Key.P, Key.Q, Key.R,
        Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
    )
    letters.forEachIndexed { i, k -> put(k, ('A' + i).toString()) }
    listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        .forEachIndexed { i, k -> put(k, i.toString()) }
    listOf(
        Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
    ).forEachIndexed { i, k -> put(k, "NUMPAD_$i") }
    listOf(Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12)
        .forEachIndexed { i, k -> put(k, "F${i + 1}") }
    put(Key.Spacebar, "SPACE")
    put(Key.DirectionLeft, "DPAD_LEFT")
    put(Key.DirectionRight, "DPAD_RIGHT")
    put(Key.DirectionUp, "DPAD_UP")
    put(Key.DirectionDown, "DPAD_DOWN")
    put(Key.Enter, "ENTER")
    put(Key.NumPadEnter, "NUMPAD_ENTER")
    put(Key.Escape, "ESCAPE")
    put(Key.Tab, "TAB")
    put(Key.Backspace, "DEL")
    put(Key.Delete, "FORWARD_DEL")
    put(Key.Insert, "INSERT")
    put(Key.MoveHome, "MOVE_HOME")
    put(Key.MoveEnd, "MOVE_END")
    put(Key.PageUp, "PAGE_UP")
    put(Key.PageDown, "PAGE_DOWN")
    put(Key.Slash, "SLASH")
    put(Key.Backslash, "BACKSLASH")
    put(Key.Period, "PERIOD")
    put(Key.Comma, "COMMA")
    put(Key.Minus, "MINUS")
    put(Key.Equals, "EQUALS")
    put(Key.Plus, "PLUS")
    put(Key.Semicolon, "SEMICOLON")
    put(Key.Apostrophe, "APOSTROPHE")
    put(Key.Grave, "GRAVE")
    put(Key.LeftBracket, "LEFT_BRACKET")
    put(Key.RightBracket, "RIGHT_BRACKET")
    put(Key.NumPadAdd, "NUMPAD_ADD")
    put(Key.NumPadSubtract, "NUMPAD_SUBTRACT")
    put(Key.NumPadMultiply, "NUMPAD_MULTIPLY")
    put(Key.NumPadDivide, "NUMPAD_DIVIDE")
    put(Key.NumPadDot, "NUMPAD_DOT")
    put(Key.MediaPlayPause, "MEDIA_PLAY_PAUSE")
    put(Key.MediaNext, "MEDIA_NEXT")
    put(Key.MediaPrevious, "MEDIA_PREVIOUS")
    put(Key.MediaStop, "MEDIA_STOP")
}
