// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sources

import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourcePrefs
import app.nijiyome.model.VideoCodec

// Sparkles redesign, lane S4: the order of the episode sources (new: today the list only keeps the ranking) and the columns of the
// comparator. Pure, so the picker and its tests share them. Every sort is stable: equal values keep the ranking order.

/** "Ordenar por" of the Facetas structure. [RECOMMENDED] = the ranking, the user's pick first. */
enum class SourceSort { RECOMMENDED, QUALITY, SEEDERS, SIZE }

fun sortSources(list: List<RankedSource>, sort: SourceSort): List<RankedSource> = when (sort) {
    SourceSort.RECOMMENDED -> list.sortedByDescending { it.picked }
    SourceSort.QUALITY -> list.sortedWith(
        compareByDescending<RankedSource> { it.candidate.quality.rank() }.thenByDescending { it.candidate.tags.bitDepth ?: 0 },
    )
    SourceSort.SEEDERS -> list.sortedByDescending { it.candidate.torrent?.seeders ?: -1 }
    SourceSort.SIZE -> list.sortedBy { it.candidate.torrent?.sizeBytes ?: Long.MAX_VALUE }
}

/** A column of the Comparador structure. [higherIsBetter] = false for size (smaller wins). */
enum class CompareColumn(val higherIsBetter: Boolean = true, val marksBest: Boolean = false) {
    SOURCE,
    RESOLUTION(marksBest = true),
    CODEC,
    AUDIO,
    SUBS,
    SIZE(higherIsBetter = false, marksBest = true),
    SEEDERS(marksBest = true),
    CACHE,
    RELIABILITY(marksBest = true),
}

/** The comparable value of [s] in [column]; null = unknown (never the best, sorted last). */
fun compareValue(s: RankedSource, column: CompareColumn): Double? {
    val c = s.candidate
    return when (column) {
        CompareColumn.SOURCE -> if (s.recommended) 1.0 else 0.0
        CompareColumn.RESOLUTION -> c.quality.takeIf { it != Quality.UNKNOWN }?.rank()?.toDouble()
        CompareColumn.CODEC -> when (c.tags.codec) {
            VideoCodec.AV1 -> 4.0
            VideoCodec.HEVC -> 3.0
            VideoCodec.VP9 -> 2.0
            VideoCodec.H264 -> 1.0
            null -> null
        }
        CompareColumn.AUDIO -> (c.dubs.size.coerceAtLeast(if (c.tags.dualAudio) 2 else 0)).toDouble()
        CompareColumn.SUBS -> (c.subs.size.coerceAtLeast(if (c.tags.multiSub) 2 else 0)).toDouble()
        CompareColumn.SIZE -> c.torrent?.sizeBytes?.toDouble()
        CompareColumn.SEEDERS -> c.torrent?.seeders?.toDouble()
        CompareColumn.CACHE -> if (c.debrid?.cached == true) 1.0 else 0.0
        CompareColumn.RELIABILITY -> c.reliability.toDouble()
    }
}

/** [list] sorted by [column] ([descending] = best first for columns where higher is better). Unknown values go last. */
fun sortByColumn(list: List<RankedSource>, column: CompareColumn, descending: Boolean): List<RankedSource> {
    val known = list.filter { compareValue(it, column) != null }
    val unknown = list.filter { compareValue(it, column) == null }
    val sorted = known.sortedBy { compareValue(it, column)!! }
    return (if (descending) sorted.reversed() else sorted) + unknown
}

/** The best value of each column that marks one (null when fewer than two sources have a value). */
fun bestValues(list: List<RankedSource>): Map<CompareColumn, Double> = CompareColumn.entries.filter { it.marksBest }.mapNotNull { col ->
    val values = list.mapNotNull { compareValue(it, col) }
    if (values.size < 2) return@mapNotNull null
    col to (if (col.higherIsBetter) values.max() else values.min())
}.toMap()

/** Why the automatic rules would pass over [s] (empty = it meets them). Pure: the picker words each reason. */
enum class RuleMiss { EXTENSION_OFF, BELOW_QUALITY, OTHER_KIND, NO_PREFERRED_SUBS, LOW_RELIABILITY }

fun ruleMisses(s: RankedSource, prefs: SourcePrefs): List<RuleMiss> = buildList {
    val c = s.candidate
    if (!s.extEnabled) add(RuleMiss.EXTENSION_OFF)
    if (c.quality != Quality.UNKNOWN && c.quality.rank() < prefs.minQuality.rank()) add(RuleMiss.BELOW_QUALITY)
    if (prefs.prefer != null && c.kind != prefs.prefer) add(RuleMiss.OTHER_KIND)
    val want = prefs.subLang.trim()
    if (want.isNotEmpty() && c.subs.isNotEmpty() && c.subs.none { langCode(it).startsWith(langCode(want)) }) add(RuleMiss.NO_PREFERRED_SUBS)
    if (reliabilityTone(c.reliability) == RelTone.LOW) add(RuleMiss.LOW_RELIABILITY)
}

/** The source the picker explains: the user's pick, else the recommended one, else the first. */
fun chosenSource(list: List<RankedSource>): RankedSource? =
    list.firstOrNull { it.picked } ?: list.firstOrNull { it.recommended } ?: list.firstOrNull()

/** Groups of the Facetas list: web and torrent apart, the group with the best-ranked source first; ranking kept inside. */
fun groupByKind(list: List<RankedSource>): List<Pair<SourceKind, List<RankedSource>>> =
    list.groupBy { it.candidate.kind }.toList()
