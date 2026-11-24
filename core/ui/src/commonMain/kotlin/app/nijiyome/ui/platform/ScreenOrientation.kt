// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/** true when the device is held in landscape. Android: `Configuration.orientation`. Desktop: false (a window has no device orientation). */
@Composable
expect fun isLandscapeOrientation(): Boolean
