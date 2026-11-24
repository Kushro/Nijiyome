// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/**
 * Desktop: the open viewer (player, manga or novel reader) handles Esc on its own focused root. If that root lost the keyboard focus
 * (a focused bar button went away with the bars), an Esc reaches the window instead; the window then calls [escape], which runs the
 * viewer's own Esc and gives it the focus back. Android provides none.
 */
class ViewerKeyRelay {
    private class Target(val onEscape: () -> Unit, val focus: FocusRequester)

    private val targets = ArrayList<Target>()

    /** Runs the newest viewer's Esc and focuses it again; false when no viewer is open. */
    fun escape(): Boolean {
        val target = targets.lastOrNull() ?: return false
        runCatching { target.focus.requestFocus() }
        target.onEscape()
        return true
    }

    internal fun add(onEscape: () -> Unit, focus: FocusRequester): () -> Unit {
        val target = Target(onEscape, focus)
        targets += target
        return { targets.remove(target) }
    }
}

/** Null outside the desktop window (Android, tests): [ViewerEscape] does nothing. */
val LocalViewerKeyRelay: ProvidableCompositionLocal<ViewerKeyRelay?> = staticCompositionLocalOf { null }

/** Registers this viewer's Esc ([onEscape]) and its focused root ([focus]) with the window's [ViewerKeyRelay]. */
@Composable
fun ViewerEscape(focus: FocusRequester, onEscape: () -> Unit) {
    val relay = LocalViewerKeyRelay.current ?: return
    val current = rememberUpdatedState(onEscape)
    DisposableEffect(relay, focus) {
        val remove = relay.add({ current.value() }, focus)
        onDispose { remove() }
    }
}
