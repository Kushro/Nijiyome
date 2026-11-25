// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.runtime.Composable

/**
 * true when this device should run in performance motion while the mode is automatic (`PerfMode.AUTO`). Android: a low-RAM
 * device (`ActivityManager.isLowRamDevice`) or a panel whose fastest mode is 60 Hz or less. Desktop: Skia renders in software
 * (`SOFTWARE_FAST` / `SOFTWARE_COMPAT`, forced with `skiko.renderApi` or fallen back to at runtime).
 *
 * The shell passes it to `rememberMotionChoices(prefs, lowEndDevice = rememberLowEndDevice())`. It never changes what the app
 * can do, only how much moves.
 */
@Composable
expect fun rememberLowEndDevice(): Boolean
