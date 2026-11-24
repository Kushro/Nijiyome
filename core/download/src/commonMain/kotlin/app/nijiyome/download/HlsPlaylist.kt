// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import java.net.URI

/**
 * HLS playlists for the desktop downloader (RFC 8216; port of Tatakai Next's `tk-core/download/hls.rs`): a multivariant playlist
 * gives its variants (picked with [DownloadLogic.pickVariant], the same rule as Android), a media playlist its segments with absolute
 * URLs, byte ranges, the `#EXT-X-MAP` init segment and the AES-128 key of each segment. `SAMPLE-AES` and DRM key formats are refused.
 * Pure (tested).
 */
object HlsPlaylist {
    data class Variant(val url: String, val height: Int?, val bandwidth: Int)

    data class Key(val url: String, val iv: String?)

    data class Segment(
        val url: String,
        /** Byte range (length, offset); null = whole resource. */
        val range: Pair<Long, Long>?,
        /** Media sequence number (the IV when the key has none). */
        val sequence: Long,
        val key: Key?,
        /** Init segment of this segment (fMP4), as its own [Segment] without key. */
        val init: Segment?,
        val durationSec: Double,
    )

    sealed interface Parsed {
        data class Master(val variants: List<Variant>) : Parsed
        data class Media(val segments: List<Segment>) : Parsed
    }

    class PlaylistException(message: String) : Exception(message)

    /** Whether a body looks like a playlist (`#EXTM3U` after an optional BOM / whitespace). */
    fun isPlaylist(body: String): Boolean = body.removePrefix("﻿").trimStart().startsWith("#EXTM3U")

    fun resolve(base: String, uri: String): String = URI(base).resolve(URI(uri.trim().replace(" ", "%20"))).toString()

    fun parse(base: String, body: String): Parsed {
        if (!isPlaylist(body)) throw PlaylistException("not a playlist")
        val lines = body.removePrefix("﻿").lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) Parsed.Master(parseMaster(base, lines)) else Parsed.Media(parseMedia(base, lines))
    }

    private fun parseMaster(base: String, lines: List<String>): List<Variant> {
        val out = ArrayList<Variant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = attributes(line.substringAfter(':'))
                val uri = lines.getOrNull(i + 1)?.takeIf { !it.startsWith("#") }
                if (uri != null) {
                    val height = attrs["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull()
                    val bandwidth = (attrs["AVERAGE-BANDWIDTH"] ?: attrs["BANDWIDTH"])?.toIntOrNull() ?: 0
                    val peak = attrs["BANDWIDTH"]?.toIntOrNull() ?: bandwidth
                    out += Variant(resolve(base, uri), height, maxOf(peak, bandwidth))
                    i++
                }
            }
            i++
        }
        if (out.isEmpty()) throw PlaylistException("hls: no variants")
        return out
    }

    private fun parseMedia(base: String, lines: List<String>): List<Segment> {
        val out = ArrayList<Segment>()
        var sequence = 0L
        var key: Key? = null
        var init: Segment? = null
        var duration = 0.0
        var range: Pair<Long, Long>? = null
        var lastRangeEnd = 0L
        var lastRangeUrl: String? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                line.startsWith("#EXT-X-KEY:") -> {
                    val a = attributes(line.substringAfter(':'))
                    key = when (a["METHOD"]?.uppercase()) {
                        null, "NONE" -> null
                        "AES-128" -> {
                            val format = a["KEYFORMAT"]
                            if (format != null && format != "identity") throw PlaylistException("encrypted")
                            Key(resolve(base, a["URI"] ?: throw PlaylistException("hls: key without uri")), a["IV"])
                        }
                        else -> throw PlaylistException("encrypted")
                    }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val a = attributes(line.substringAfter(':'))
                    val url = resolve(base, a["URI"] ?: throw PlaylistException("hls: map without uri"))
                    val r = a["BYTERANGE"]?.let { parseRange(it, null) }
                    init = Segment(url, r, -1, null, null, 0.0)
                }
                line.startsWith("#EXTINF:") -> duration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                line.startsWith("#EXT-X-BYTERANGE:") -> range = parseRange(line.substringAfter(':'), lastRangeEnd)
                line.startsWith("#") -> Unit
                else -> {
                    val url = resolve(base, line)
                    // An offset-less byte range continues the previous one only on the same resource.
                    val r = range?.let { (len, off) -> if (off < 0) len to (if (url == lastRangeUrl) lastRangeEnd else 0L) else len to off }
                    out += Segment(url, r, sequence, key, init, duration)
                    if (r != null) {
                        lastRangeEnd = r.second + r.first
                        lastRangeUrl = url
                    }
                    sequence++
                    range = null
                    duration = 0.0
                }
            }
        }
        if (out.isEmpty()) throw PlaylistException("hls: empty playlist")
        return out
    }

    /** `<n>[@<o>]`; a missing offset is -1 (continue the previous range), or [defaultOffset] when given. */
    private fun parseRange(text: String, defaultOffset: Long?): Pair<Long, Long> {
        val len = text.substringBefore('@').trim().toLongOrNull() ?: throw PlaylistException("hls: bad byte range")
        val off = if ('@' in text) text.substringAfter('@').trim().toLongOrNull() ?: 0L else (if (defaultOffset == null) 0L else -1L)
        return len to off
    }

    /** `A=1,B="x,y",C=0x10` -> map (quotes removed). */
    fun attributes(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < text.length) {
            val eq = text.indexOf('=', i)
            if (eq < 0) break
            val name = text.substring(i, eq).trim().trimStart(',').trim()
            var j = eq + 1
            val value: String
            if (j < text.length && text[j] == '"') {
                val end = text.indexOf('"', j + 1).let { if (it < 0) text.length else it }
                value = text.substring(j + 1, end)
                j = end + 1
            } else {
                val end = text.indexOf(',', j).let { if (it < 0) text.length else it }
                value = text.substring(j, end).trim()
                j = end
            }
            out[name.uppercase()] = value
            i = j + 1
        }
        return out
    }

    /** Range request header of a byte range (length, offset). */
    fun rangeHeader(range: Pair<Long, Long>): String = "bytes=${range.second}-${range.second + range.first - 1}"
}
