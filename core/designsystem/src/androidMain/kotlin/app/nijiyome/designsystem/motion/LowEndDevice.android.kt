// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import android.app.ActivityManager
import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/** At or below this the panel counts as 60 Hz (some report 59.94 or 60.0001). */
private const val SIXTY_HZ = 60.5f

@Composable
actual fun rememberLowEndDevice(): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) { lowRam(context) || sixtyHzPanel(view) }
}

private fun lowRam(context: Context): Boolean =
    runCatching { (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true }.getOrDefault(false)

/**
 * The fastest mode the display supports, not the current rate: a 120 Hz phone in battery saver or idle adaptive refresh
 * reports 60 Hz at the moment and is not a slow device.
 */
private fun sixtyHzPanel(view: View): Boolean = runCatching {
    val display = view.display ?: return@runCatching false
    val fastest = display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
    fastest <= SIXTY_HZ
}.getOrDefault(false)
