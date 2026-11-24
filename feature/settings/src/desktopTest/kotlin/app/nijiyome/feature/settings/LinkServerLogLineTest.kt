// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.model.ExtensionServerLogEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkServerLogLineTest {
    @Test
    fun aLogLineHasOnlyThePartsTheEntryHas() {
        val full = ExtensionServerLogEntry(7, 0, "source_failed", "/resolve", "Toko", 3, 120, "timeout")
        assertEquals("hace 1 min · source_failed · /resolve · Toko · 3 · 120 ms · timeout", logLine(full, "hace 1 min"))
        assertEquals("ahora · pairing", logLine(ExtensionServerLogEntry(8, 0, "pairing"), "ahora"))
    }
}
