// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import app.nijiyome.ui.platform.androidKeyName

// Ctrl / Alt / Meta combinations are not reader keys (Ctrl+K is the app's search, not K); Shift is the same key.
internal actual fun readerKeyName(e: KeyEvent): String? =
    if (e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) null else androidKeyName(e.key)

internal actual val desktopReaderKeys: Boolean = true
