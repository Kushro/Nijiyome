// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/**
 * Width available to the app in dp. Android: `LocalConfiguration.current.screenWidthDp` (without the system bars, as the
 * screens always measured it). Desktop: the window.
 */
@Composable
expect fun windowWidthDp(): Float

/** Height available to the app in dp (same sources as [windowWidthDp]). */
@Composable
expect fun windowHeightDp(): Float
