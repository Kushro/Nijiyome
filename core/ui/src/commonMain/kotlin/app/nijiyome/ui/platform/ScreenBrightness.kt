// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/**
 * Overrides the window brightness while this is in the composition: [brightness] in 0..1, or negative for the system value.
 * The previous value comes back on leaving. Android: `WindowManager.LayoutParams.screenBrightness`. Desktop: does nothing (see
 * [windowBrightnessAvailable]).
 */
@Composable
expect fun ScreenBrightness(brightness: Float)

/**
 * True where [ScreenBrightness] changes the screen (Android). Where it does not (desktop: the monitor's belongs to the user), a reader
 * dims its own pages instead, as Tatakai Next's desktop reader did with a CSS brightness filter.
 */
expect val windowBrightnessAvailable: Boolean
