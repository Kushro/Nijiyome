// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.manga.ReaderKeymap
import app.nijiyome.model.ReaderDirection
import app.nijiyome.model.ReaderMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderKeysTest {
    @Test
    fun pagedRtlMirrorsThePageKeysOnlyWhenAsked() {
        val rtl = { a: String, follow: Boolean -> directedAction(a, ReaderMode.PAGED, ReaderDirection.RTL, follow) }
        assertEquals(ReaderKeymap.PREV_PAGE, rtl(ReaderKeymap.NEXT_PAGE, true))
        assertEquals(ReaderKeymap.NEXT_PAGE, rtl(ReaderKeymap.PREV_PAGE, true))
        assertEquals(ReaderKeymap.SCROLL_DOWN, rtl(ReaderKeymap.SCROLL_DOWN, true))
        // Android: as before
        assertEquals(ReaderKeymap.NEXT_PAGE, rtl(ReaderKeymap.NEXT_PAGE, false))
    }

    @Test
    fun ltrAndScrollModesKeepTheKeys() {
        assertEquals(ReaderKeymap.NEXT_PAGE, directedAction(ReaderKeymap.NEXT_PAGE, ReaderMode.PAGED, ReaderDirection.LTR, true))
        ReaderMode.entries.filter { it != ReaderMode.PAGED }.forEach { mode ->
            assertEquals(ReaderKeymap.NEXT_PAGE, directedAction(ReaderKeymap.NEXT_PAGE, mode, ReaderDirection.RTL, true), "$mode")
        }
    }

    @Test
    fun escapeClosesTheSheetThenFullscreenThenLeaves() {
        assertEquals(ReaderEscape.CLOSE_SHEET, readerEscape(sheetOpen = true, fullscreen = true))
        assertEquals(ReaderEscape.LEAVE_FULLSCREEN, readerEscape(sheetOpen = false, fullscreen = true))
        assertEquals(ReaderEscape.EXIT, readerEscape(sheetOpen = false, fullscreen = false))
    }
}
