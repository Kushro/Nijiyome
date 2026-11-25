// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.delay
import org.jetbrains.skiko.GraphicsApi
import java.awt.Window

@Composable
actual fun rememberLowEndDevice(): Boolean {
    var software by remember { mutableStateOf(softwareRendering()) }
    LaunchedEffect(Unit) {
        // The window picks its renderer when it is first shown, and Skiko may fall back to software a moment later.
        withFrameNanos { }
        software = softwareRendering()
        delay(2_000)
        software = softwareRendering()
    }
    return software
}

/** Desktop panels are not judged by refresh rate. */
@Composable
actual fun rememberLowRefreshRate(): Boolean = false

private fun softwareRendering(): Boolean = runCatching {
    val forced = System.getProperty("skiko.renderApi") ?: System.getenv("SKIKO_RENDER_API")
    if (forced != null && forced.startsWith("SOFTWARE", ignoreCase = true)) return@runCatching true
    Window.getWindows().any { window ->
        window is ComposeWindow && window.isDisplayable &&
            (window.renderApi == GraphicsApi.SOFTWARE_FAST || window.renderApi == GraphicsApi.SOFTWARE_COMPAT)
    }
}.getOrDefault(false)
