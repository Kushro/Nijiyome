// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import androidx.compose.ui.input.key.KeyEvent
import app.nijiyome.manga.ReaderKeymap
import app.nijiyome.model.ReaderDirection
import app.nijiyome.model.ReaderMode

/** The [app.nijiyome.manga.ReaderKeymap] name of the key in [e] ("SPACE", "DPAD_RIGHT"...), or null when it has none. */
internal expect fun readerKeyName(e: KeyEvent): String?

/**
 * Tatakai Next's desktop reader keys: Esc closes the open sheet, then leaves fullscreen, then the reader; and in paged RTL the page
 * keys follow the reading direction. True on desktop; Android keeps its original keys (Back leaves, no mirroring).
 */
internal expect val desktopReaderKeys: Boolean

/**
 * Tatakai Next `resolve_key`: paged and right to left, the key of "next page" goes back and the key of "previous page" goes forward,
 * as the tap zones do. Only with [followDirection] (desktop).
 */
internal fun directedAction(action: String, mode: ReaderMode, direction: ReaderDirection, followDirection: Boolean): String {
    if (!followDirection || mode != ReaderMode.PAGED || direction != ReaderDirection.RTL) return action
    return when (action) {
        ReaderKeymap.NEXT_PAGE -> ReaderKeymap.PREV_PAGE
        ReaderKeymap.PREV_PAGE -> ReaderKeymap.NEXT_PAGE
        else -> action
    }
}

/** What Esc does in a reader on desktop, in Tatakai Next's order. */
internal enum class ReaderEscape { CLOSE_SHEET, LEAVE_FULLSCREEN, EXIT }

internal fun readerEscape(sheetOpen: Boolean, fullscreen: Boolean): ReaderEscape = when {
    sheetOpen -> ReaderEscape.CLOSE_SHEET
    fullscreen -> ReaderEscape.LEAVE_FULLSCREEN
    else -> ReaderEscape.EXIT
}
