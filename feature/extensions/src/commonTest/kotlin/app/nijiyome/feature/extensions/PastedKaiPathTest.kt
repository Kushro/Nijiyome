// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tatakai Next `clean_path`: quotes of Windows' "Copy as path", spaces and `file://` go; only `.kai` files are paths to install. */
class PastedKaiPathTest {
    @Test
    fun pastedPaths() {
        assertEquals("C:\\Users\\me\\Downloads\\toko.kai", pastedKaiPath("\"C:\\Users\\me\\Downloads\\toko.kai\""))
        assertEquals("/home/me/toko 2.kai", pastedKaiPath("  /home/me/toko 2.kai "))
        // A file URI becomes the host's own path (`\home\me\a.KAI` on Windows).
        assertEquals(java.io.File("/home/me/a.KAI").path, pastedKaiPath("file:///home/me/a.KAI"))
    }

    @Test
    fun linksAndOtherFilesAreNotPaths() {
        assertNull(pastedKaiPath("https://example.org/toko.kai"))
        assertNull(pastedKaiPath("/home/me/notes.txt"))
        assertNull(pastedKaiPath("  "))
    }
}
