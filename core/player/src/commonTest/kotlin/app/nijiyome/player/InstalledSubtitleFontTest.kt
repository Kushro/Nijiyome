// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstalledSubtitleFontTest {
    @Test
    fun refRoundTripsAndFileNamesAreNotFamilies() {
        assertEquals("family:Noto Sans", InstalledSubtitleFont.ref(" Noto Sans "))
        assertEquals("Noto Sans", InstalledSubtitleFont.family("family:Noto Sans"))
        assertNull(InstalledSubtitleFont.family("MyFont.ttf"))
        assertNull(InstalledSubtitleFont.family("family:  "))
        assertNull(InstalledSubtitleFont.family(null))
    }
}
