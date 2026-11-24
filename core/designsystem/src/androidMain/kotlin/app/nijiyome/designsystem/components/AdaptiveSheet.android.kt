package app.nijiyome.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal actual fun sideSheetDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

// BUG-079: over an immersive screen (the player in landscape) the side sheet keeps the status bar hidden too.
@Composable
internal actual fun rememberHostIsImmersive(): Boolean {
    val hostView = LocalView.current
    return remember(hostView) {
        ViewCompat.getRootWindowInsets(hostView)?.isVisible(WindowInsetsCompat.Type.statusBars()) == false
    }
}

@Composable
internal actual fun KeepDialogImmersive() {
    val dialogView = LocalView.current
    LaunchedEffect(dialogView) {
        (dialogView.parent as? DialogWindowProvider)?.window?.let { w ->
            WindowCompat.getInsetsController(w, dialogView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
internal actual fun sheetHostWidthDp(): Int = LocalConfiguration.current.screenWidthDp
