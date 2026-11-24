// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun windowWidthDp(): Float = LocalConfiguration.current.screenWidthDp.toFloat()

@Composable
actual fun windowHeightDp(): Float = LocalConfiguration.current.screenHeightDp.toFloat()
