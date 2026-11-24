// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

/** Reads the family name (name id 1, or 16 when present) of a TrueType / OpenType font. Pure, no Android. */
object FontNames {
    fun family(bytes: ByteArray): String? = runCatching { parse(bytes) }.getOrNull()

    private fun parse(b: ByteArray): String? {
        var base = 0
        // TrueType collection: use the first font.
        if (b.size >= 16 && String(b, 0, 4, Charsets.US_ASCII) == "ttcf") base = u32(b, 12).toInt()
        val numTables = u16(b, base + 4)
        var nameOffset = -1
        for (i in 0 until numTables) {
            val rec = base + 12 + i * 16
            if (String(b, rec, 4, Charsets.US_ASCII) == "name") {
                nameOffset = u32(b, rec + 8).toInt()
                break
            }
        }
        if (nameOffset < 0) return null
        val count = u16(b, nameOffset + 2)
        val strings = nameOffset + u16(b, nameOffset + 4)
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        for (i in 0 until count) {
            val r = nameOffset + 6 + i * 12
            val platform = u16(b, r)
            val nameId = u16(b, r + 6)
            if (nameId != 1 && nameId != 16) continue
            val len = u16(b, r + 8)
            val off = strings + u16(b, r + 10)
            if (off + len > b.size) continue
            val text = when (platform) {
                0, 3 -> String(b, off, len, Charsets.UTF_16BE)
                1 -> String(b, off, len, Charsets.ISO_8859_1)
                else -> continue
            }.trim()
            if (text.isEmpty()) continue
            // Typographic family (16) beats the legacy family (1); Windows / Unicode beat Mac.
            val rank = (if (nameId == 16) 0 else 2) + (if (platform == 1) 1 else 0)
            if (rank < bestRank) {
                best = text
                bestRank = rank
            }
        }
        return best
    }

    private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
}
