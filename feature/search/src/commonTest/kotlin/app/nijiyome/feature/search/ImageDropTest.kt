// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageDropTest {
    @Test
    fun theFirstImageFileWins() {
        assertEquals("/a/b.PNG", droppedImage(listOf("/a/notes.txt", "/a/b.PNG", "/a/c.jpg"), null))
        assertNull(droppedImage(listOf("/a/notes.txt"), null))
    }

    @Test
    fun aDroppedOrPastedLinkIsSearched() {
        assertEquals("https://x.org/a.png", droppedImage(emptyList(), " https://x.org/a.png "))
        assertEquals("http://x.org/a", imageLink("http://x.org/a"))
        assertNull(imageLink("ftp://x.org/a.png"))
        assertNull(imageLink("https://"))
        assertNull(imageLink("see https://x.org/a.png"))
        assertNull(imageLink("javascript:alert(1)"))
        assertNull(droppedImage(emptyList(), "just words"))
    }
}
