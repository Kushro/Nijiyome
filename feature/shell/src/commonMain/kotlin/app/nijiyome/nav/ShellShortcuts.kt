// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

import app.nijiyome.model.SettingsSection

/** The app-wide keys of Tatakai Next's desktop (`tk-ui/src/shortcuts.rs`), plus Ctrl+P for Settings modal. */
sealed interface ShellShortcut {
    /** Ctrl+K: Command Palette (settings, commands, routes & catalog search). */
    data object FocusSearch : ShellShortcut

    /** Ctrl+, (Sparkles) and Ctrl+P (kept as its alias): Settings over the current screen. */
    data object OpenSettings : ShellShortcut

    /** Ctrl+B: the rail with labels / icons only. */
    data object ToggleNav : ShellShortcut

    /** Esc: back (the screen's own back first: a panel, a sheet). */
    data object Back : ShellShortcut

    /** 1..9: the visible rail destination at [position] (1 = the first), in rail order. */
    data class NavTo(val position: Int) : ShellShortcut
}

/**
 * The shortcut of a key press: Ctrl+K, Ctrl+P / Ctrl+,, Ctrl+B (no Alt / Meta), and without modifiers Esc and 1..9 (top row or keypad).
 * Anything else is null.
 */
fun shellShortcut(key: Key, ctrl: Boolean, alt: Boolean, meta: Boolean): ShellShortcut? {
    if (alt || meta) return null
    if (ctrl) {
        return when (key) {
            Key.K -> ShellShortcut.FocusSearch
            Key.P, Key.Comma -> ShellShortcut.OpenSettings
            Key.B -> ShellShortcut.ToggleNav
            else -> null
        }
    }
    if (key == Key.Escape) return ShellShortcut.Back
    val position = DIGITS.indexOf(key).takeIf { it >= 0 } ?: KEYPAD.indexOf(key).takeIf { it >= 0 } ?: return null
    return ShellShortcut.NavTo(position + 1)
}

private val DIGITS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
private val KEYPAD = listOf(
    Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
)

/**
 * The desktop window's app-wide keys. The window passes it the key presses its screens did not use ([onKeyEvent]: the player and
 * the readers keep their own keys) and tells it when a text field takes text ([textInputStarted]); the shell acts on them. [platformBack] runs
 * the newest back handler of the screens (a panel, a sheet) and says whether there was one. Android has no such window: it passes none.
 */
class ShellShortcuts(
    private val platformBack: () -> Boolean = { false },
    /** Esc of the open viewer (player, reader) when its root lost the focus; false when none is open. */
    private val viewerEscape: () -> Boolean = { false },
    /**
     * v1.1: a dialog or a modal sheet is open (desktop `ModalTracker`). Its key presses that it did not use still reach the window,
     * and none of them is a shortcut then: no rail jump, no Search, no back underneath it (the dialog closes on its own Esc).
     */
    private val modalOpen: () -> Boolean = { false },
) {
    internal var handler: ((ShellShortcut) -> Boolean)? = null

    /** Ctrl+K asked Search for the focus and it has not taken it yet. */
    internal var focusSearchPending: Boolean by mutableStateOf(false)

    /** Ctrl+B: the rail shows the other style (labelled / icons only) until pressed again. */
    internal var navToggled: Boolean by mutableStateOf(false)

    /** True when the navigation back stack has a previous screen (drives DesktopTitleBar's Back button). */
    var canGoBack: Boolean by mutableStateOf(false)
        internal set

    internal var goBackHandler: (() -> Unit)? = null
    internal var openPaletteHandler: (() -> Unit)? = null
    internal var openSettingsHandler: ((SettingsSection?) -> Unit)? = null

    fun navigateBack() {
        goBackHandler?.invoke()
    }

    fun openCommandPalette() {
        openPaletteHandler?.invoke()
    }

    fun openSettingsModal(section: SettingsSection? = null) {
        openSettingsHandler?.invoke(section)
    }

    // Text input sessions open right now (a focused text field). A field does not consume the key press of a digit (its text comes
    // separately), so 1..9 are left alone while one is open (Tatakai Next: never while typing).
    private var textInputs = 0

    /** A text field started taking text (main thread). */
    fun textInputStarted() {
        textInputs++
    }

    /** That text field stopped taking text (main thread). */
    fun textInputEnded() {
        if (textInputs > 0) textInputs--
    }

    internal val typing: Boolean get() = textInputs > 0

    fun onKeyEvent(e: KeyEvent): Boolean =
        e.type == KeyEventType.KeyDown && onKeyDown(e.key, e.isCtrlPressed, e.isAltPressed, e.isMetaPressed)

    /** A key press: true when it was a shortcut and the shell used it. */
    internal fun onKeyDown(key: Key, ctrl: Boolean, alt: Boolean, meta: Boolean): Boolean {
        val shortcut = shellShortcut(key, ctrl, alt, meta) ?: return false
        if (modalOpen()) return false
        if (shortcut is ShellShortcut.NavTo && typing) return false
        return handler?.invoke(shortcut) ?: false
    }

    internal fun back(): Boolean = platformBack()

    internal fun escapeViewer(): Boolean = viewerEscape()
}
