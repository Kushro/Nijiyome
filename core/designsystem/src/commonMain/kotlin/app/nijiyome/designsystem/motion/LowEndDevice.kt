// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.runtime.Composable

/**
 * true when this device should run in performance motion while the mode is automatic (`PerfMode.AUTO`). Android: a low-RAM
 * device (`ActivityManager.isLowRamDevice`). Desktop: Skia renders in software (`SOFTWARE_FAST` / `SOFTWARE_COMPAT`, forced with
 * `skiko.renderApi` or fallen back to at runtime). A 60 Hz panel does not count: see [rememberLowRefreshRate].
 *
 * The shell passes it to `rememberMotionChoices(prefs, lowEndDevice = rememberLowEndDevice())`. It never changes what the app
 * can do, only how much moves.
 */
@Composable
expect fun rememberLowEndDevice(): Boolean

/**
 * true when the fastest mode the display supports is 60 Hz or less (Android). It turns nothing on by itself; Settings may use it
 * to suggest performance mode. Desktop: always false.
 */
@Composable
expect fun rememberLowRefreshRate(): Boolean
