// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

// A desktop window has no brightness of its own (the monitor's belongs to the user): the readers dim their pages instead.
@Composable
actual fun ScreenBrightness(brightness: Float) = Unit

actual val windowBrightnessAvailable: Boolean = false
