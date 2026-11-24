// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// parity wave A (15 §5 p (g)): the font file filter of SubtitleFonts (androidMain; moved out of PlayerTimersTest).
class SubtitleFontsTest {
    @Test
    fun fontFileNames() {
        assertTrue(SubtitleFonts.isFontName("x.OTF"))
        assertFalse(SubtitleFonts.isFontName("x.png"))
    }
}
