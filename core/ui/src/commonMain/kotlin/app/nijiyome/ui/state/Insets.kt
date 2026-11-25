package app.nijiyome.ui.state

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Provided by the navigation shell (`NijiyomeApp`): `statusBars` on compact windows (where the bottom navigation bar consumes
 * the bottom system inset) and `statusBars union navigationBars` on wide windows (where the rail does not consume bottom insets).
 */
val LocalScreenInsets = compositionLocalOf<WindowInsets?> { null }

/** Insets every screen `Scaffold` should pass to `contentWindowInsets`. */
val screenInsets: WindowInsets
    @Composable get() = LocalScreenInsets.current ?: WindowInsets.statusBars
