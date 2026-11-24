// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import androidx.compose.ui.input.key.KeyEvent
import app.nijiyome.manga.ReaderKeymap

internal actual fun readerKeyName(e: KeyEvent): String? = ReaderKeymap.keyName(android.view.KeyEvent.keyCodeToString(e.nativeKeyEvent.keyCode))

internal actual val desktopReaderKeys: Boolean = false
