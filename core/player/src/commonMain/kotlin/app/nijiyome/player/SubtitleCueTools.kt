// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import java.util.TreeMap

// Pure helpers of the player's subtitle tools (`SubtitleTools`, androidMain: Context, SAF and the cache folders).

/** 0.4.0 (sub-translation): torrent piece fractions -> byte ranges that are fully downloaded (rounded inwards). Pure. */
object TorrentByteRanges {
    fun of(fractions: List<ClosedFloatingPointRange<Double>>, length: Long): List<LongRange> {
        if (length <= 0) return emptyList()
        return fractions.mapNotNull { r ->
            val start = kotlin.math.ceil(r.start.coerceIn(0.0, 1.0) * length).toLong()
            val endExclusive = if (r.endInclusive >= 1.0) length else kotlin.math.floor(r.endInclusive.coerceIn(0.0, 1.0) * length).toLong()
            if (endExclusive > start) start until endExclusive else null
        }.sortedBy { it.first }
    }
}

/**
 * Embedded (in-stream) Media3 cues shifted by a positive subtitle delay: the screen records every change of the visible lines at the
 * position it happened and, with a delay `d`, shows what was visible at `position - d`. A negative delay needs future cues, which only
 * parsed external tracks have ([SubtitleParser.activeAt]). Records older than [MAX_GAP_MS] before the looked-up position are ignored, so
 * a seek never shows a stale line. Pure, no Android.
 */
class CueTimeline {
    private val changes = TreeMap<Long, List<String>>()

    fun record(positionMs: Long, lines: List<String>) {
        changes[positionMs.coerceAtLeast(0)] = lines
        while (changes.size > MAX_ENTRIES) changes.pollFirstEntry()
    }

    fun clear() = changes.clear()

    fun linesAt(positionMs: Long): List<String> {
        if (positionMs < 0) return emptyList()
        val e = changes.floorEntry(positionMs) ?: return emptyList()
        return if (positionMs - e.key > MAX_GAP_MS) emptyList() else e.value
    }

    companion object {
        const val MAX_GAP_MS = 10_000L
        const val MAX_ENTRIES = 2_000
    }
}
