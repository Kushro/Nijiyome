// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.launch

/**
 * v1.1, desktop: the dialogs and modal sheets open right now, so the window's app-wide keys (1..9, Ctrl+K, Ctrl+B, Esc) stay off
 * while one is up, and the field of a dialog takes the focus when it opens ([focusOnOpen]). Only the desktop window provides one
 * ([LocalModalTracker]); on Android there is none, so nothing is tracked and no field is focused (no keyboard popping up).
 */
class ModalTracker {
    var openCount: Int by mutableIntStateOf(0)
        private set

    val isOpen: Boolean get() = openCount > 0

    fun opened() {
        openCount++
    }

    fun closed() {
        if (openCount > 0) openCount--
    }
}

val LocalModalTracker = staticCompositionLocalOf<ModalTracker?> { null }

/** Counts the caller as an open modal (a dialog, a sheet) for as long as it is in the composition; nothing without a tracker. */
@Composable
fun TrackModal() {
    val tracker = LocalModalTracker.current ?: return
    DisposableEffect(tracker) {
        tracker.opened()
        onDispose { tracker.closed() }
    }
}

/**
 * Desktop: the field (or other focusable) this modifies takes the focus as soon as its dialog or sheet opens, so typing goes there
 * at once. Does nothing without a [LocalModalTracker] (Android keeps its behaviour: no field is focused, no keyboard pops up).
 */
fun Modifier.focusOnOpen(): Modifier = this then FocusOnOpenElement

private data object FocusOnOpenElement : ModifierNodeElement<FocusOnOpenNode>() {
    override fun create(): FocusOnOpenNode = FocusOnOpenNode()

    override fun update(node: FocusOnOpenNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "focusOnOpen"
    }
}

private class FocusOnOpenNode : Modifier.Node(), FocusRequesterModifierNode, CompositionLocalConsumerModifierNode {
    override fun onAttach() {
        if (currentValueOf(LocalModalTracker) == null) return
        // After the first frame: the dialog moves the focus to itself when it opens, and the field must win.
        coroutineScope.launch {
            withFrameNanos { }
            requestFocus()
        }
    }
}
