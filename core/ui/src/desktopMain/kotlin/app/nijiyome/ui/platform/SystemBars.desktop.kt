// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

// A desktop window has no system bars to hide; the readers' fullscreen is the window's ([ReaderWindowFullscreen]).
@Composable
actual fun SystemBarsHidden(hidden: Boolean) = Unit

/** The window goes fullscreen while [fullscreen]; it leaves fullscreen when false and on leaving the reader. */
@Composable
actual fun ReaderWindowFullscreen(fullscreen: Boolean) {
    val window = LocalAppWindow.current ?: return
    LaunchedEffect(window, fullscreen) { window.fullscreen = fullscreen }
    DisposableEffect(window) { onDispose { window.fullscreen = false } }
}
