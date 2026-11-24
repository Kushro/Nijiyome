// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun ScreenBrightness(brightness: Float) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
        onDispose {
            if (window != null && previous != null) window.attributes = window.attributes.also { it.screenBrightness = previous }
        }
    }
    LaunchedEffect(brightness, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.also { it.screenBrightness = if (brightness >= 0f) brightness.coerceIn(0.02f, 1f) else -1f }
    }
}

actual val windowBrightnessAvailable: Boolean = true
