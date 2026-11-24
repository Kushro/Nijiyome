// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/** Keeps the screen on while this is in the composition. Android: `View.keepScreenOn`. */
@Composable
expect fun KeepScreenOn()
