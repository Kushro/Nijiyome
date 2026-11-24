// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Desktop has no system back; Esc plays its part. Every [PlatformBackHandler] in the composition registers here and [dispatch] (the
 * app's Esc) runs the newest enabled one, as Android's back dispatcher does. Main thread only.
 */
class DesktopBackDispatcher {
    private class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = ArrayList<Entry>()

    /** Runs the newest enabled handler; false when there is none. */
    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }

    /** Registers a handler, newest last; [Registration.remove] takes it out. */
    fun register(enabled: Boolean, onBack: () -> Unit): Registration {
        val entry = Entry(enabled, onBack)
        entries += entry
        return object : Registration {
            override fun update(enabled: Boolean, onBack: () -> Unit) {
                entry.enabled = enabled
                entry.onBack = onBack
            }

            override fun remove() {
                entries.remove(entry)
            }
        }
    }

    interface Registration {
        fun update(enabled: Boolean, onBack: () -> Unit)
        fun remove()
    }
}

/** Null outside the desktop app (tests, previews): then [PlatformBackHandler] does nothing. */
val LocalDesktopBackDispatcher: ProvidableCompositionLocal<DesktopBackDispatcher?> = staticCompositionLocalOf { null }

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalDesktopBackDispatcher.current ?: return
    val registration = remember(dispatcher) { dispatcher.register(enabled, onBack) }
    SideEffect { registration.update(enabled, onBack) }
    DisposableEffect(registration) { onDispose { registration.remove() } }
}
