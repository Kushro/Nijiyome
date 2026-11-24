// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.labels

import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenreLabelTest {
    @Test
    fun aniListGenresMapToTheDesktopKeys() {
        // BUG-072: Detalles showed "Action, Adventure, Comedy" on the phone; the desktop says "Acción · Aventura · Comedia".
        assertEquals(Res.string.genre_action, genreLabelRes("Action"))
        assertEquals(Res.string.genre_sci_fi, genreLabelRes("Sci-Fi"))
        assertEquals(Res.string.genre_slice_of_life, genreLabelRes("Slice of Life"))
        assertEquals(Res.string.genre_mahou_shoujo, genreLabelRes("Mahou Shoujo"))
        assertNull(genreLabelRes("Isekai"))
    }
}
