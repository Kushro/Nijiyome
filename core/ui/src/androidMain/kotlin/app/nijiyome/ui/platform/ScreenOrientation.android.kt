// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun isLandscapeOrientation(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
