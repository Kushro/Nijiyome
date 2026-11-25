package app.nijiyome.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

enum class MediaKind { ANIME, MANGA }

enum class FeedKind { TRENDING, POPULAR, TOP_RATED, AIRING, UPCOMING, SEASONAL, RECENTLY_COMPLETED }

@Serializable
data class MediaRef(val anilistId: Int? = null, val malId: Int? = null, val kitsuId: Int? = null)

@Serializable
data class Media(
    val ref: MediaRef,
    val kind: MediaKind,
    val title: String,
    val titles: Map<String, String>,
    val cover: String?,
    val banner: String?,
    val color: String?,
    val synopsis: String?,
    val genres: List<String>,
    val episodes: Int?,
    val status: String?,
    val season: String?,
    val year: Int?,
    val score: Int?,
    val sourceProvider: String,
    /**
     * BUG-143: titles known from another provider than the one that answered (e.g. Kitsu's spelling when AniList answered before,
     * or AniList's titles kept when a fallback answered). Only filled by `MetadataRepositoryImpl` when a fallback provider answers;
     * a normal answer has none, so [searchTitles] keeps its order. Never encoded when empty: the JSON of a normal Media is unchanged.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val altTitles: List<String> = emptyList(),
    /**
     * 2026-11 UI refresh: AniList `format` (`TV`, `MOVIE`, `OVA`, `ONA`, `SPECIAL`, `TV_SHORT`, `MUSIC`), shown by the detailed cards and
     * the Home spotlight. Not part of the shared metadata contract (the fixture test leaves it out); never encoded when null.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val format: String? = null,
)

/**
 * The titles sent to the extensions / the PC when looking for sources: [Media.title], then [Media.titles] in their order, then
 * [Media.altTitles]; blanks and duplicates dropped. Without alt titles this is exactly the list the app always sent (BUG-143).
 */
fun Media.searchTitles(): List<String> =
    (listOf(title) + titles.values + altTitles).filter { it.isNotBlank() }.distinct()

private val TITLE_SPLIT_REGEX = Regex("[:：~～]|\\s+-\\s+")
private val MULTI_SPACE_REGEX = Regex("\\s+")

/**
 * v1.2.1: search titles sent to torrent/stream extensions (`streamParams`, Stremio, Extension Server).
 * Keeps the original full titles (so release token matchers keep every show word) AND adds <=58-char
 * word-boundary prefixes and hyphen-safe variants right inside the first 8 entries when a light-novel
 * title exceeds 60 chars or contains multiple hyphens (fixing 0-result Nyaa/AnimeTosho/nekoBT searches).
 */
fun Media.extensionSearchTitles(): List<String> {
    val raw = searchTitles()
    if (raw.isEmpty()) return raw
    val needsSynthesis = raw.any { it.length > 60 || it.count { ch -> ch == '-' } >= 2 }
    if (!needsSynthesis) return raw

    fun shorten(s: String, maxLen: Int = 58): String? {
        val cleaned = s.replace(MULTI_SPACE_REGEX, " ").trim()
        if (cleaned.length <= maxLen) return cleaned.takeIf { it.length >= 3 }
        val head = TITLE_SPLIT_REGEX.split(cleaned, limit = 2)[0]
            .trim()
            .trimEnd('.', ',', ';', '!', '?', '-', '~', ' ')
        if (head.length in 4..maxLen) return head
        val source = if (head.length >= 12) head else cleaned
        val cut = source.take(maxLen)
        val lastSpace = cut.lastIndexOf(' ')
        val trimmed = (if (lastSpace >= 12) cut.substring(0, lastSpace) else cut)
            .trimEnd('.', ',', ';', '!', '?', '-', '~', ' ')
        return trimmed.takeIf { it.length >= 4 }
    }

    fun dehyphen(s: String): String? {
        if (s.count { it == '-' } < 2) return null
        val cleaned = s.replace('-', ' ').replace(MULTI_SPACE_REGEX, " ").trim()
        return shorten(cleaned)
    }

    val seen = LinkedHashSet<String>()
    val out = ArrayList<String>()
    fun add(candidate: String?) {
        val t = candidate?.replace(MULTI_SPACE_REGEX, " ")?.trim() ?: return
        if (t.isEmpty()) return
        if (seen.add(t.lowercase())) out += t
    }

    val primary = listOfNotNull(
        title.takeIf { it.isNotBlank() },
        titles["english"]?.takeIf { it.isNotBlank() },
        titles["romaji"]?.takeIf { it.isNotBlank() },
    ).distinct()

    for (t in primary) {
        add(t)
        if (t.length > 60) add(shorten(t))
        dehyphen(t)?.let(::add)
    }
    // Put short synonyms (<= 60 chars) before long/native titles so they land inside an extension's first 8 slots.
    val shortAlts = altTitles.filter { it.isNotBlank() && it.trim().length <= 60 }
    shortAlts.forEach { t ->
        add(t)
        dehyphen(t)?.let(::add)
    }
    for (t in raw) {
        add(t)
        if (t.length > 60) add(shorten(t))
    }
    return out
}

/** [extra] titles not already in [searchTitles] (case-insensitive) appended to [Media.altTitles]. */
fun Media.withAltTitles(extra: List<String>): Media {
    val seen = searchTitles().mapTo(HashSet()) { it.trim().lowercase() }
    val add = extra.filter { it.isNotBlank() && seen.add(it.trim().lowercase()) }
    return if (add.isEmpty()) this else copy(altTitles = altTitles + add)
}

@Serializable
data class Episode(
    val number: Int,
    val title: String?,
    val image: String?,
    val airDate: String?,
    val isFiller: Boolean = false,
    // --- 2026-11 UI refresh (desktop `EpisodeVm::{summary, runtime_min, rating}`): ani.zip / Kitsu extras, outside the shared
    // metadata contract (the fixture test leaves them out); never encoded when null so cached lists and payloads stay as they were.
    /** Episode synopsis (ani.zip `overview` / `summary`, Kitsu `synopsis`). */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val summary: String? = null,
    /** Length in minutes (ani.zip `runtime` / `length`, Kitsu `length`). */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val runtimeMin: Int? = null,
    /** ani.zip rating 0..10 (number or numeric text), two decimals. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val rating: Double? = null,
)

/**
 * 0.3.2: ani.zip `images` (TVDB artwork, `coverType` Fanart / Banner / Poster / Clearlogo). [fanart] is the 1920x1080 background the
 * Home spotlight and the Details header prefer over AniList's 1900x400 `bannerImage`; [clearlogo] is the transparent title logo shown
 * instead of the text title (the title stays the accessible name).
 */
@Serializable
data class MediaArtwork(
    val fanart: String? = null,
    val banner: String? = null,
    val poster: String? = null,
    val clearlogo: String? = null,
) {
    val isEmpty: Boolean get() = fanart == null && banner == null && poster == null && clearlogo == null
}

data class FeedPage(val items: List<Media>, val page: Int, val hasNext: Boolean)
