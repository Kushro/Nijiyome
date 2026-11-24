// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxUiScaleTest {
    @Test
    fun `xft dpi is read from xrdb output`() {
        assertEquals(120.0, xftDpi("Xcursor.size:\t24\nXft.dpi:\t120\nXft.hinting:\t1\n"))
        assertEquals(96.0, xftDpi("Xft.dpi: 96"))
        assertNull(xftDpi("Xcursor.theme:\tbreeze\n"))
        assertNull(xftDpi("Xft.dpi:\tabc"))
    }

    @Test
    fun `the factor is the dpi over what awt already scales`() {
        assertEquals(1.25f, scaleFactor(null, 120.0, 1.0))
        assertEquals(1.5f, scaleFactor(null, 144.0, 1.0))
        assertEquals(1f, scaleFactor(null, 96.0, 1.0))
        assertEquals(1f, scaleFactor(null, 98.0, 1.0)) // under 5 %: nothing to fix
        assertEquals(1f, scaleFactor(null, null, 1.0))
        // GDK_SCALE=2 with Xft.dpi=192: AWT already doubles, nothing left.
        assertEquals(1f, scaleFactor(null, 192.0, 2.0))
        assertEquals(1.25f, scaleFactor(null, 240.0, 2.0))
        assertEquals(4f, scaleFactor(null, 960.0, 1.0))
    }

    @Test
    fun `the environment overrides it`() {
        assertEquals(1f, scaleFactor("1", 120.0, 1.0))
        assertEquals(1.75f, scaleFactor(" 1.75 ", null, 1.0))
        assertEquals(1.25f, scaleFactor("nope", 120.0, 1.0))
        assertEquals(4f, scaleFactor("9", null, 1.0))
    }
}
