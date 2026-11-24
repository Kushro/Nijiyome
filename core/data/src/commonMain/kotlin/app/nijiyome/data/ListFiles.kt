// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.ListEntry
import app.nijiyome.model.ListStatus
import app.nijiyome.model.MediaRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

// v1.1 (F6, checklist lists.import-export): the list files of other apps, as Tatakai Next's desktop read them (tk-core
// lists/import.rs): a MyAnimeList XML export, an AniList list dump (`MediaListCollection`), Tatakai Next's own list file
// (`"format": "tatakai-lists"`), and Nijiyome's JSON ([parseExport]). Export adds CSV with Tatakai Next's columns.
// Pure text in / entries out. Only desktop offers these today (ListsRepository.importFile / exportCsv); Android keeps its JSON.

enum class ListFileFormat { NIJIYOME, TATAKAI, MAL_XML, ANILIST }

/** What a list file held: the usable entries and how many could not be used (no id, unknown status). */
data class ListFile(val format: ListFileFormat, val entries: List<ListEntry>, val skipped: Int = 0)

/**
 * Detects the format of [text] and parses it. Entries without a time of their own (MAL exports, old dumps) get [nowMs], so they
 * replace what the local list had, as in Tatakai Next. Throws [IllegalArgumentException] when the text is none of the formats.
 */
fun parseListFile(text: String, nowMs: Long): ListFile {
    val t = text.removePrefix("﻿").trimStart()
    if (t.startsWith("<")) {
        require(t.contains("<myanimelist")) { "not a MyAnimeList XML export" }
        return parseMalXml(t, nowMs)
    }
    val element = runCatching { listJson.parseToJsonElement(t) }.getOrElse { throw IllegalArgumentException("not a list file", it) }
    if (element is JsonObject && element.str("format") == TATAKAI_FORMAT) return parseTatakai(element, nowMs)
    aniListEntries(element)?.let { return parseAniListDump(it, nowMs) }
    return ListFile(ListFileFormat.NIJIYOME, parseExport(t))
}

/** `format` of Tatakai Next's list file. */
const val TATAKAI_FORMAT = "tatakai-lists"

// ---------------------------------------------------------------- Tatakai Next

private fun parseTatakai(file: JsonObject, nowMs: Long): ListFile {
    var skipped = 0
    val favorites = file.arr("favorites")?.objectList()?.mapNotNull { it.str("media_id") }.orEmpty().toSet()
    val entries = file.arr("entries")?.objectList().orEmpty().mapNotNull { e ->
        val status = statusOf(e.str("status"))
        val ref = MediaRef(anilistId = e.int("anilist_id")?.takeIf { it > 0 }, malId = e.int("mal_id")?.takeIf { it > 0 })
            .let { r -> if (r.anilistId == null && r.malId == null) refOfMediaId(e.str("media_id")) else r }
        if (status == null || keyOf(ref) == null) {
            skipped++
            return@mapNotNull null
        }
        val mediaId = e.str("media_id")?.takeIf { it.isNotBlank() } ?: keyOf(ref)
        ListEntry(
            ref = ref,
            title = e.str("title").orEmpty().ifBlank { "#${keyOf(ref)}" },
            poster = e.str("poster")?.takeIf { it.isNotBlank() },
            status = status,
            progress = e.int("progress")?.coerceAtLeast(0) ?: 0,
            totalEpisodes = e.int("episodes")?.takeIf { it > 0 },
            score = e.int("score")?.takeIf { it > 0 }?.coerceAtMost(100),
            favorite = mediaId in favorites,
            updatedAtMs = e.long("updated_at")?.takeIf { it > 0 }?.let(::secondsOrMillis) ?: nowMs,
            notes = e.str("notes")?.takeIf { it.isNotBlank() },
            startedAt = e.str("started_at")?.takeIf { it.isNotBlank() },
            completedAt = e.str("finished_at")?.takeIf { it.isNotBlank() },
        )
    }
    return ListFile(ListFileFormat.TATAKAI, entries, skipped)
}

/** Tatakai Next's `media_id`: `"21"` (AniList) or `"mal:5114"`. */
private fun refOfMediaId(id: String?): MediaRef {
    val s = id?.trim().orEmpty()
    return when {
        s.startsWith("mal:") -> MediaRef(malId = s.removePrefix("mal:").toIntOrNull()?.takeIf { it > 0 })
        else -> MediaRef(anilistId = s.toIntOrNull()?.takeIf { it > 0 })
    }
}

/** Unix seconds (Tatakai Next, AniList) to ms; a value already in ms is kept. */
private fun secondsOrMillis(v: Long): Long = if (v < 100_000_000_000L) v * 1000 else v

// ---------------------------------------------------------------- AniList dump

/** The `MediaList` entries of `{data:{MediaListCollection}}`, `{MediaListCollection}`, a bare collection, or null for other JSON. */
private fun aniListEntries(element: kotlinx.serialization.json.JsonElement): List<JsonObject>? {
    if (element is JsonArray) {
        // a bare array is a Nijiyome export unless its items look like AniList's MediaList (status in capitals, a media object)
        val items = element.objectList()
        return items.takeIf { it.isNotEmpty() && it.all { o -> o.obj("media") != null || o.str("mediaId") != null } && it.none { o -> o.obj("ref") != null } }
    }
    val o = element as? JsonObject ?: return null
    val collection = o.obj("data")?.obj("MediaListCollection") ?: o.obj("MediaListCollection") ?: o.takeIf { it.arr("lists") != null } ?: return null
    val lists = collection.arr("lists") ?: return null
    val seen = HashSet<String>()
    return lists.objectList().flatMap { it.arr("entries")?.objectList().orEmpty() }.filter { e ->
        // an entry appears once per custom list it is in
        val key = e.str("id") ?: e.obj("media")?.str("id") ?: e.str("mediaId")
        key == null || seen.add(key)
    }
}

private fun parseAniListDump(items: List<JsonObject>, nowMs: Long): ListFile {
    var skipped = 0
    val entries = items.mapNotNull { o ->
        val entry = parseAniListEntry(o)
        if (entry == null) {
            skipped++
            return@mapNotNull null
        }
        // POINT_100 dumps have scores above 10; POINT_10 / decimal ones are scaled up.
        val raw = o.str("score")?.toDoubleOrNull()?.takeIf { it > 0 }
        val score = raw?.let { if (it > 10) it.coerceAtMost(100.0) else it * 10 }?.roundToInt()
        entry.copy(score = score, updatedAtMs = entry.updatedAtMs.takeIf { it > 0 } ?: nowMs)
    }
    return ListFile(ListFileFormat.ANILIST, entries, skipped)
}

// ---------------------------------------------------------------- MyAnimeList XML

/** MAL export status (text, or the number of older exports). */
internal fun malExportStatus(s: String, rewatching: Boolean): ListStatus? = when (s.trim().lowercase()) {
    "watching", "1" -> if (rewatching) ListStatus.REPEATING else ListStatus.WATCHING
    "completed", "2" -> ListStatus.COMPLETED
    "on-hold", "on hold", "3" -> ListStatus.PAUSED
    "dropped", "4" -> ListStatus.DROPPED
    "plan to watch", "6" -> ListStatus.PLANNING
    else -> null
}

private fun parseMalXml(text: String, nowMs: Long): ListFile {
    var skipped = 0
    val entries = mutableListOf<ListEntry>()
    // An index walks the text (v1.1 review #18: copying the rest of the file per <anime> was quadratic on big exports).
    var at = 0
    while (true) {
        val open = text.indexOf("<anime>", at)
        if (open < 0) break
        val start = open + "<anime>".length
        val close = text.indexOf("</anime>", start)
        if (close < 0) break
        val block = text.substring(start, close)
        at = close + "</anime>".length
        val title = xmlTag(block, "series_title").orEmpty()
        val malId = xmlTag(block, "series_animedb_id")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val status = malExportStatus(xmlTag(block, "my_status").orEmpty(), xmlTag(block, "my_rewatching")?.trim() == "1")
        if (malId == null || status == null) {
            skipped++
            continue
        }
        fun num(tag: String) = xmlTag(block, tag)?.trim()?.toIntOrNull()
        entries += ListEntry(
            ref = MediaRef(malId = malId),
            title = title.ifBlank { "#mal:$malId" },
            poster = null,
            status = status,
            progress = num("my_watched_episodes")?.coerceAtLeast(0) ?: 0,
            totalEpisodes = num("series_episodes")?.takeIf { it > 0 },
            score = num("my_score")?.takeIf { it > 0 }?.coerceAtMost(10)?.times(10),
            updatedAtMs = nowMs,
            notes = xmlTag(block, "my_comments")?.takeIf { it.isNotBlank() },
            startedAt = malDate(xmlTag(block, "my_start_date")),
            completedAt = malDate(xmlTag(block, "my_finish_date")),
        )
    }
    return ListFile(ListFileFormat.MAL_XML, entries, skipped)
}

/** Text of `<tag>…</tag>` in [block], CDATA unwrapped, entities decoded. */
internal fun xmlTag(block: String, tag: String): String? {
    val open = "<$tag>"
    val start = block.indexOf(open).takeIf { it >= 0 }?.plus(open.length) ?: return null
    val end = block.indexOf("</$tag>", start).takeIf { it >= 0 } ?: return null
    val raw = block.substring(start, end).trim()
    return if (raw.startsWith("<![CDATA[") && raw.endsWith("]]>")) raw.substring(9, raw.length - 3) else xmlUnescape(raw)
}

internal fun xmlUnescape(s: String): String = XML_ENTITY.replace(s) { m ->
    val e = m.groupValues[1]
    when {
        e == "amp" -> "&"
        e == "lt" -> "<"
        e == "gt" -> ">"
        e == "quot" -> "\""
        e == "apos" -> "'"
        e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let(::codePoint) ?: m.value
        e.startsWith("#") -> e.substring(1).toIntOrNull()?.let(::codePoint) ?: m.value
        else -> m.value
    }
}

private fun codePoint(cp: Int): String? = runCatching { String(Character.toChars(cp)) }.getOrNull()

private val XML_ENTITY = Regex("&(#?[A-Za-z0-9]{1,8});")

/** MAL dates are `YYYY-MM-DD`, with `0000-00-00` for "not set". */
private fun malDate(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("0000") }

// ---------------------------------------------------------------- CSV export

/** Tatakai Next's CSV columns (`media_id` is the local key: `"21"` or `"mal:5114"`); `repeats` is always 0 (Nijiyome has none). */
const val LIST_CSV_HEADER = "media_id,anilist_id,mal_id,title,status,progress,episodes,score,started_at,finished_at,repeats,notes,updated_at"

/** CSV of [entries] (header + one row each, CRLF, RFC 4180 quoting). `updated_at` in Unix seconds, as Tatakai Next wrote it. */
fun exportEntriesCsv(entries: List<ListEntry>): String = buildString {
    append(LIST_CSV_HEADER).append("\r\n")
    for (e in entries) {
        val cols = listOf(
            csvField(keyOf(e.ref).orEmpty()),
            e.ref.anilistId?.toString().orEmpty(),
            e.ref.malId?.toString().orEmpty(),
            csvField(e.title),
            e.status.id,
            e.progress.toString(),
            e.totalEpisodes?.toString().orEmpty(),
            e.score?.toString().orEmpty(),
            csvField(e.startedAt.orEmpty()),
            csvField(e.completedAt.orEmpty()),
            "0",
            csvField(e.notes.orEmpty()),
            (e.updatedAtMs / 1000).toString(),
        )
        append(cols.joinToString(",")).append("\r\n")
    }
}

/**
 * A text cell: a value a spreadsheet would run as a formula (starting with `=`, `+`, `-`, `@`, a tab or a carriage return) gets a
 * leading `'` (v1.1 review #18: an imported title or note like `=HYPERLINK(…)` must not become a live formula), then RFC 4180 quoting.
 */
internal fun csvField(s: String): String {
    val safe = if (s.isNotEmpty() && s[0] in FORMULA_STARTS) "'$s" else s
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}

private const val FORMULA_STARTS = "=+-@\t\r"
