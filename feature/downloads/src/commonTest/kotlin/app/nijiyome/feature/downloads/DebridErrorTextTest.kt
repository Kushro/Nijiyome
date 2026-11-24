// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import app.nijiyome.model.torrent.DebridProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 1.1 review #7: a failed TorBox download shows its reason, not the raw `debrid:*` code. */
class DebridErrorTextTest {
    @Test
    fun debridCodesAreRecognisedBareOrWrapped() {
        assertEquals(DebridProblem.ACTIVE_LIMIT, debridProblemIn("debrid:torbox:ACTIVE_LIMIT"))
        assertEquals(DebridProblem.ACTIVE_LIMIT, debridProblemIn("Unknown(msg=debrid:torbox:ACTIVE_LIMIT)"))
        assertEquals(DebridProblem.INVALID_KEY, debridProblemIn("debrid:invalid_key"))
        assertEquals(DebridProblem.OFFLINE, debridProblemIn("debrid:offline"))
        assertEquals(DebridProblem.SERVICE, debridProblemIn("debrid:refresh_failed"))
        assertNull(debridProblemIn("http 404"))
    }
}
