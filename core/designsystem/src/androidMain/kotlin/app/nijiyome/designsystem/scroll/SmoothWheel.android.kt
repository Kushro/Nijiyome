// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier

internal actual val desktopPlatform: Boolean = false

/** Touch already flings; a mouse on Android keeps the platform wheel. */
internal actual fun Modifier.smoothWheelPlatform(
    state: ScrollableState,
    orientation: Orientation,
    reverse: Boolean,
    spring: Boolean,
): Modifier = this
