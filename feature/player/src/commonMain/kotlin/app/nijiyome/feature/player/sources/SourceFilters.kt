// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sources

import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.ReleaseSource
import app.nijiyome.model.SourceKind
import app.nijiyome.model.VideoCodec
import app.nijiyome.player.CodecCaps
import app.nijiyome.player.isDeviceCompatible

// android wave (docs/redesign/11 §3.6, 12 §3.11): filter state of the source picker, ported 1:1 from desktop
// apps/desktop-native/crates/tk-ui/src/components/sources.rs (SourceFilters, FilterOptions, tag_chips) plus the Android-only
// "Compatible with this device" filter. Filtering NEVER reorders: the order is the ranking. Pure, unit-tested.

/** Reliability floor of the filter, on the reliability tone thresholds. */
enum class RelFloor(val min: Float) {
    /** `>= 0.7` (medium or high). */
    MID(0.7f),

    /** `>= 0.9`. */
    HIGH(0.9f),
}

/** Resolutions the picker can filter by, best first. */
val FILTER_RESOLUTIONS: List<Quality> = listOf(Quality.P2160, Quality.P1440, Quality.P1080, Quality.P720, Quality.P480)

/** Audio/dub mode filters in the source picker (v1.2.1). */
enum class AudioFilter { SUB, DUB, DUAL_AUDIO, MULTI_DUB }

private fun candidateDubCodes(s: RankedSource): List<String> =
    s.candidate.dubs.map(::langCode).filter { it.isNotEmpty() }.distinct()

private fun candidateSubCodes(s: RankedSource): List<String> =
    s.candidate.subs.map(::langCode).filter { it.isNotEmpty() }.distinct()

private fun isMultiDubCandidate(s: RankedSource): Boolean =
    candidateDubCodes(s).size >= 3 ||
        s.candidate.note.orEmpty().contains("multi-dub", ignoreCase = true) ||
        s.candidate.note.orEmpty().contains("multidub", ignoreCase = true)

private fun isDualAudioCandidate(s: RankedSource): Boolean =
    s.candidate.tags.dualAudio || candidateDubCodes(s).size >= 2

private fun isDubCandidate(s: RankedSource): Boolean {
    val dubs = candidateDubCodes(s)
    return isDualAudioCandidate(s) ||
        dubs.any { !it.equals("JA", ignoreCase = true) && !it.equals("JPN", ignoreCase = true) } ||
        s.candidate.note.orEmpty().contains("dub", ignoreCase = true)
}

private fun isSubCandidate(s: RankedSource): Boolean {
    val subs = candidateSubCodes(s)
    val dubs = candidateDubCodes(s)
    return s.candidate.tags.multiSub ||
        subs.isNotEmpty() ||
        dubs.any { it.equals("JA", ignoreCase = true) || it.equals("JPN", ignoreCase = true) } ||
        !isDubCandidate(s)
}

private fun langMatches(candidateCodes: List<String>, selected: Set<String>): Boolean {
    if (selected.isEmpty()) return true
    return selected.any { want ->
        val w = want.uppercase()
        candidateCodes.any { have ->
            have.equals(w, ignoreCase = true) ||
                have.startsWith("$w-", ignoreCase = true) ||
                w.startsWith("$have-", ignoreCase = true)
        }
    }
}

/** Empty sets = no constraint (a set is "any of"); a `true` flag = "must have". */
data class SourceFilters(
    val kind: SourceKind? = null,
    val sources: Set<ReleaseSource> = emptySet(),
    val resolutions: Set<Quality> = emptySet(),
    val codecs: Set<VideoCodec> = emptySet(),
    val tenBit: Boolean = false,
    val dualAudio: Boolean = false,
    val multiSub: Boolean = false,
    val batch: Boolean = false,
    val seadexBest: Boolean = false,
    val minReliability: RelFloor? = null,
    /** Android only: hide what the device cannot decode (HEVC / AV1 10-bit). */
    val deviceCompatible: Boolean = false,
    /** v1.2.1: show only sources confirmed cached in a linked debrid service. */
    val debridOnly: Boolean = false,
    /** v1.2.1: audio/dub mode filters (Sub, Dub, Dual Audio, Multi-Dub). */
    val audioModes: Set<AudioFilter> = emptySet(),
    /** v1.2.1: filter by one or more audio track languages (normalized codes like `JA`, `EN`, `ES-419`). */
    val audioLangs: Set<String> = emptySet(),
    /** v1.2.1: filter by one or more subtitle languages (normalized codes like `ES`, `EN`, `PT-BR`). */
    val subLangs: Set<String> = emptySet(),
) {
    fun matches(s: RankedSource, caps: CodecCaps = CodecCaps.ALL): Boolean {
        val c = s.candidate
        val tags = c.tags
        val dubCodes = candidateDubCodes(s)
        val subCodes = candidateSubCodes(s)
        val modeOk = audioModes.isEmpty() || audioModes.any { mode ->
            when (mode) {
                AudioFilter.SUB -> isSubCandidate(s)
                AudioFilter.DUB -> isDubCandidate(s)
                AudioFilter.DUAL_AUDIO -> isDualAudioCandidate(s)
                AudioFilter.MULTI_DUB -> isMultiDubCandidate(s)
            }
        }
        return (kind == null || c.kind == kind) &&
            (sources.isEmpty() || tags.source in sources) &&
            (resolutions.isEmpty() || c.quality in resolutions) &&
            (codecs.isEmpty() || tags.codec in codecs) &&
            (!tenBit || tags.bitDepth == 10) &&
            (!dualAudio || isDualAudioCandidate(s)) &&
            (!multiSub || tags.multiSub || subCodes.size >= 2) &&
            (!batch || tags.batch) &&
            (!seadexBest || tags.seadexBest) &&
            (minReliability == null || c.reliability >= minReliability.min) &&
            (!deviceCompatible || isDeviceCompatible(tags, caps)) &&
            (!debridOnly || c.debrid?.cached == true) &&
            modeOk &&
            langMatches(dubCodes, audioLangs) &&
            langMatches(subCodes, subLangs)
    }

    /** The sources that pass, in the given (ranked) order. */
    fun apply(list: List<RankedSource>, caps: CodecCaps = CodecCaps.ALL): List<RankedSource> = list.filter { matches(it, caps) }

    /** Active chip filters (the kind segment is not counted: it is always visible). */
    fun active(): Int =
        sources.size + resolutions.size + codecs.size + audioModes.size + audioLangs.size + subLangs.size +
            listOf(tenBit, dualAudio, multiSub, batch, seadexBest, deviceCompatible, debridOnly).count { it } +
            (if (minReliability != null) 1 else 0)

    /** Count of active filters housed in the expandable "Languages & video" section. */
    fun activeAdvanced(): Int =
        audioLangs.size + subLangs.size + sources.size + codecs.size +
            listOf(tenBit, batch, seadexBest, deviceCompatible).count { it }

    fun toggleFloor(floor: RelFloor): SourceFilters = copy(minReliability = if (minReliability == floor) null else floor)

    /** Everything but the kind segment back to default. */
    fun cleared(): SourceFilters = SourceFilters(kind = kind)

    /** Encoding for `rememberSaveable` (a flat list of strings). */
    fun encode(): List<String> = buildList {
        add("kind=${kind?.name ?: ""}")
        sources.forEach { add("src=${it.name}") }
        resolutions.forEach { add("res=${it.name}") }
        codecs.forEach { add("codec=${it.name}") }
        if (tenBit) add("tenBit")
        if (dualAudio) add("dualAudio")
        if (multiSub) add("multiSub")
        if (batch) add("batch")
        if (seadexBest) add("seadexBest")
        if (deviceCompatible) add("device")
        if (debridOnly) add("debrid")
        audioModes.forEach { add("amode=${it.name}") }
        audioLangs.forEach { add("alang=$it") }
        subLangs.forEach { add("slang=$it") }
        minReliability?.let { add("rel=${it.name}") }
    }

    companion object {
        fun decode(items: List<String>): SourceFilters {
            var f = SourceFilters()
            for (item in items) {
                val (k, v) = item.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                f = when (k) {
                    "kind" -> f.copy(kind = SourceKind.entries.firstOrNull { it.name == v })
                    "src" -> ReleaseSource.entries.firstOrNull { it.name == v }?.let { f.copy(sources = f.sources + it) } ?: f
                    "res" -> Quality.entries.firstOrNull { it.name == v }?.let { f.copy(resolutions = f.resolutions + it) } ?: f
                    "codec" -> VideoCodec.entries.firstOrNull { it.name == v }?.let { f.copy(codecs = f.codecs + it) } ?: f
                    "tenBit" -> f.copy(tenBit = true)
                    "dualAudio" -> f.copy(dualAudio = true)
                    "multiSub" -> f.copy(multiSub = true)
                    "batch" -> f.copy(batch = true)
                    "seadexBest" -> f.copy(seadexBest = true)
                    "device" -> f.copy(deviceCompatible = true)
                    "debrid" -> f.copy(debridOnly = true)
                    "amode" -> AudioFilter.entries.firstOrNull { it.name == v }?.let { f.copy(audioModes = f.audioModes + it) } ?: f
                    "alang" -> if (v.isNotBlank()) f.copy(audioLangs = f.audioLangs + v) else f
                    "slang" -> if (v.isNotBlank()) f.copy(subLangs = f.subLangs + v) else f
                    "rel" -> f.copy(minReliability = RelFloor.entries.firstOrNull { it.name == v })
                    else -> f
                }
            }
            return f
        }
    }
}

fun <T> Set<T>.toggled(v: T): Set<T> = if (v in this) this - v else this + v

/** What the current list offers, so the filter bar only draws chips that can match something. */
data class FilterOptions(
    val sources: List<ReleaseSource> = emptyList(),
    val resolutions: List<Quality> = emptyList(),
    val codecs: List<VideoCodec> = emptyList(),
    val tenBit: Boolean = false,
    val dualAudio: Boolean = false,
    val multiSub: Boolean = false,
    val batch: Boolean = false,
    val seadexBest: Boolean = false,
    /** Some source would be hidden by the device filter. */
    val deviceIncompatible: Boolean = false,
    /** v1.2.1: at least one source is cached on a linked debrid service. */
    val debridCached: Boolean = false,
    /** v1.2.1: audio/dub modes present in the current list. */
    val audioModes: List<AudioFilter> = emptyList(),
    /** v1.2.1: distinct normalized audio track language codes present in the list. */
    val audioLangs: List<String> = emptyList(),
    /** v1.2.1: distinct normalized subtitle language codes present in the list. */
    val subLangs: List<String> = emptyList(),
) {
    val anyFeature: Boolean get() = tenBit || dualAudio || multiSub || batch || seadexBest || debridCached || audioModes.isNotEmpty()
    val hasAdvanced: Boolean get() =
        audioLangs.isNotEmpty() || subLangs.isNotEmpty() || sources.isNotEmpty() || codecs.isNotEmpty() ||
            tenBit || batch || seadexBest || deviceIncompatible

    companion object {
        fun of(list: List<RankedSource>, caps: CodecCaps = CodecCaps.ALL): FilterOptions {
            val tags = list.map { it.candidate.tags }
            val hasDub = list.any(::isDubCandidate)
            val hasSub = list.any(::isSubCandidate)
            val hasDual = list.any(::isDualAudioCandidate)
            val hasMultiDub = list.any(::isMultiDubCandidate)
            val modes = buildList {
                if (hasSub && (hasDub || list.any { candidateSubCodes(it).isNotEmpty() })) add(AudioFilter.SUB)
                if (hasDub) add(AudioFilter.DUB)
                if (hasDual) add(AudioFilter.DUAL_AUDIO)
                if (hasMultiDub) add(AudioFilter.MULTI_DUB)
            }
            val audioLangs = list.flatMap(::candidateDubCodes).distinct().sorted()
            val subLangs = list.flatMap(::candidateSubCodes).distinct().sorted()
            return FilterOptions(
                sources = ReleaseSource.entries.filter { x -> tags.any { it.source == x } },
                resolutions = FILTER_RESOLUTIONS.filter { q -> list.any { it.candidate.quality == q } },
                codecs = VideoCodec.entries.filter { x -> tags.any { it.codec == x } },
                tenBit = tags.any { it.bitDepth == 10 },
                dualAudio = hasDual,
                multiSub = tags.any { it.multiSub } || list.any { candidateSubCodes(it).size >= 2 },
                batch = tags.any { it.batch },
                seadexBest = tags.any { it.seadexBest },
                deviceIncompatible = tags.any { !isDeviceCompatible(it, caps) },
                debridCached = list.any { it.candidate.debrid?.cached == true },
                audioModes = modes,
                audioLangs = audioLangs,
                subLangs = subLangs,
            )
        }
    }
}

/** Reliability tone of a row: >= 0.9 high (OK), >= 0.7 medium (WARN), else low (ERR). */
enum class RelTone { HIGH, MID, LOW }

fun reliabilityTone(r: Float): RelTone = when {
    r >= 0.9f -> RelTone.HIGH
    r >= 0.7f -> RelTone.MID
    else -> RelTone.LOW
}

/** One release-tag chip of a row; texts of technical codes (`BD`, `HEVC`, `1080p`) are not translated. */
sealed interface TagChip {
    data object SeadexBest : TagChip
    data class Group(val name: String) : TagChip
    data class Code(val text: String) : TagChip
    data class BitDepth(val bits: Int) : TagChip
    data object DualAudio : TagChip
    data object MultiDub : TagChip
    data object MultiSub : TagChip
    data class Batch(val from: Int?, val to: Int?) : TagChip
}

/** Desktop `tag_chips`: display order SeaDex best, group (when it differs from the label), source, quality, codec, bit depth, ... */
fun tagChips(s: RankedSource): List<TagChip> {
    val c = s.candidate
    val tags = c.tags
    val torrent = c.kind == SourceKind.TORRENT
    val out = mutableListOf<TagChip>()
    if (tags.seadexBest) out += TagChip.SeadexBest
    tags.group?.takeIf { it.isNotEmpty() && it != c.label }?.let { out += TagChip.Group(it) }
    tags.source?.let { out += TagChip.Code(it.label()) }
    if (torrent && c.quality != Quality.UNKNOWN) out += TagChip.Code(c.quality.label())
    val codec = tags.codec
    if (codec != null) {
        out += TagChip.Code(codec.label())
    } else if (torrent) {
        c.container?.takeIf { it.isNotEmpty() }?.let { out += TagChip.Code(it) }
    }
    tags.bitDepth?.let { out += TagChip.BitDepth(it) }
    if (isMultiDubCandidate(s)) out += TagChip.MultiDub
    else if (tags.dualAudio) out += TagChip.DualAudio
    if (tags.multiSub) out += TagChip.MultiSub
    if (tags.batch) {
        val e = tags.episodes
        out += if (e != null && e.last > e.first) TagChip.Batch(e.first, e.last) else TagChip.Batch(null, null)
    }
    return out
}

/** `es, en` -> `ES · EN`; empty -> null (the row then prints "none"). */
fun langsLabel(list: List<String>): String? = list.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.uppercase() }

/** Track languages shown before "+N" (BUG-065, same as the desktop's `LANGS_SHOWN`). */
const val LANGS_SHOWN = 6

private val LANGUAGE_NAMES = listOf(
    "english" to "en", "spanish" to "es", "español" to "es", "castilian" to "es", "portuguese" to "pt", "french" to "fr", "german" to "de",
    "italian" to "it", "russian" to "ru", "arabic" to "ar", "japanese" to "ja", "chinese" to "zh", "korean" to "ko", "hindi" to "hi",
    "indonesian" to "id", "thai" to "th", "vietnamese" to "vi", "turkish" to "tr", "polish" to "pl", "malay" to "ms", "dutch" to "nl",
    "swedish" to "sv", "ukrainian" to "uk", "hebrew" to "he",
)

/**
 * BUG-065 (port of the desktop's `lang_code`): a track language as a short code. Codes stay (`es-419` -> `ES-419`); track names become
 * ISO 639 with region (`ENGLISH (CR ENGLISH)` -> `EN`, `PORTUGUESE (CR PORTUGUESE(BRAZIL))` -> `PT-BR`, `CHINESE (TRADITIONAL)` ->
 * `ZH-HANT`). Unknown names stay as they are, upper-cased.
 */
fun langCode(raw: String): String {
    val s = raw.trim()
    val parts = s.split('-', '_')
    val head = parts.first()
    val isCode = head.length in 2..3 && head.all { it.isLetter() && it.code < 128 } &&
        parts.drop(1).all { p -> p.length in 2..4 && p.all { it.isLetterOrDigit() && it.code < 128 } }
    if (isCode) return s.replace('_', '-').uppercase()
    val lower = s.lowercase()
    val name = lower.substringBefore('(').trim()
    val code = LANGUAGE_NAMES.firstOrNull { (n, _) -> name == n || name.startsWith("$n ") }?.second ?: return s.uppercase()
    val region = when {
        "brazil" in lower -> "BR"
        "latin" in lower || "419" in lower -> "419"
        code == "es" && ("spain" in lower || "castilian" in lower || "europe" in lower) -> "ES"
        code == "pt" && "portugal" in lower -> "PT"
        "traditional" in lower -> "HANT"
        "simplified" in lower -> "HANS"
        else -> null
    }
    return if (region != null) "${code.uppercase()}-$region" else code.uppercase()
}

/**
 * BUG-065: `ENGLISH (CR ENGLISH) · PORTUGUESE (CR PORTUGUESE(BRAZIL)) · …` -> `ES-419 · EN · PT-BR · … +7`: short codes without repeats,
 * the preferred language [first] (and its regional variants) first, more than [LANGS_SHOWN] end in "+N". null when [list] is empty.
 */
fun langsShort(list: List<String>, first: String?): String? {
    val codes = list.map(::langCode).filter { it.isNotEmpty() }.distinct().toMutableList()
    if (codes.isEmpty()) return null
    val f = first?.trim()?.uppercase().orEmpty()
    if (f.isNotEmpty()) codes.sortBy { !(it == f || it.startsWith("$f-")) }
    if (codes.size > LANGS_SHOWN) {
        val rest = codes.size - (LANGS_SHOWN - 1)
        return codes.take(LANGS_SHOWN - 1).joinToString(" · ") + " +$rest"
    }
    return codes.joinToString(" · ")
}

/**
 * BUG-017 / BUG-020 (plan §11.2): the ranking as this device sees it — sources without a hardware decoder (MediaCodecList) go after the
 * ones it decodes in hardware. Used by the player and by the Details panel so both recommend the same row.
 */
fun rankForThisDevice(sources: List<RankedSource>): List<RankedSource> =
    app.nijiyome.player.rankForDevice(sources, app.nijiyome.feature.player.deviceCodecCaps())

/**
 * 1.1 (F7): [sources] with the debrid statuses of the linked services ([app.nijiyome.player.markDebrid], bounded in time) and, when
 * "Preferir TorBox cuando esté en caché" is on ([debridSettings]), the cached rows re-scored and the device order applied again. The
 * player and the Details panel use it so both recommend the same row. Debrid off or no account: [sources] unchanged.
 */
suspend fun withDebridOrder(
    sources: List<RankedSource>,
    debrid: app.nijiyome.model.torrent.DebridRepository?,
    prefs: app.nijiyome.model.SourcePrefs,
    debridSettings: app.nijiyome.model.torrent.DebridSettings,
): List<RankedSource> {
    if (debrid == null || !debridSettings.enabled) return sources
    return debridOrder(app.nijiyome.player.markDebrid(sources, debrid), prefs, debridSettings)
}

/** 1.1 (F7): the re-ranking half of [withDebridOrder], for lists whose statuses are already known. */
fun debridOrder(
    sources: List<RankedSource>,
    prefs: app.nijiyome.model.SourcePrefs,
    debridSettings: app.nijiyome.model.torrent.DebridSettings,
): List<RankedSource> {
    val reranked = app.nijiyome.player.rerankDebrid(sources, prefs, prefer = debridSettings.enabled && debridSettings.preferCached)
    return if (reranked === sources) sources else rankForThisDevice(reranked)
}
