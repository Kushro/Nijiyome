// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/playlist.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import okhttp3.HttpUrl

/**
 * HLS playlist rewriting for the SSRF fetcher. Every URI of a playlist (variants, `#EXT-X-MEDIA`, segments, `#EXT-X-KEY`,
 * `#EXT-X-MAP`) is resolved against the playlist URL and handed to `map`, which returns the loopback URI FFmpeg requests instead.
 * Refused: `data:`, `file:` and any scheme other than http(s), playlists above 2 MiB, nesting deeper than master -> media, and
 * anything `map` refuses (a key on a host the policy does not allow).
 */
object Playlist {
    const val MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    enum class UriKind { PLAYLIST, SEGMENT, KEY, MAP }

    class RewriteException(val reason: Reason) : Exception(reason.name) {
        enum class Reason { TOO_LARGE, NOT_UTF8, NOT_PLAYLIST, FORBIDDEN_SCHEME, TOO_DEEP, BAD_URI, DENIED }
    }

    class Rewritten(val text: String, val isMaster: Boolean, val durationSecs: Double)

    private val TAG_KINDS = listOf(
        "#EXT-X-KEY:" to UriKind.KEY,
        "#EXT-X-SESSION-KEY:" to UriKind.KEY,
        "#EXT-X-MAP:" to UriKind.MAP,
        "#EXT-X-MEDIA:" to UriKind.PLAYLIST,
        "#EXT-X-I-FRAME-STREAM-INF:" to UriKind.PLAYLIST,
        "#EXT-X-PRELOAD-HINT:" to UriKind.SEGMENT,
        "#EXT-X-RENDITION-REPORT:" to UriKind.PLAYLIST,
    )

    private fun fail(r: RewriteException.Reason): Nothing = throw RewriteException(r)

    fun resolve(base: HttpUrl, uri: String): HttpUrl {
        if (uri.isEmpty() || uri.any { it == '\r' || it == '\n' || it == '\u0000' }) fail(RewriteException.Reason.BAD_URI)
        SCHEME.find(uri)?.value?.dropLast(1)?.lowercase()?.let { scheme ->
            if (scheme != "http" && scheme != "https") fail(RewriteException.Reason.FORBIDDEN_SCHEME)
        }
        return base.resolve(uri) ?: fail(RewriteException.Reason.BAD_URI)
    }

    fun rewrite(body: ByteArray, base: HttpUrl, depth: Int, map: (HttpUrl, UriKind) -> String): Rewritten {
        if (body.size > MAX_PLAYLIST_BYTES) fail(RewriteException.Reason.TOO_LARGE)
        val decoder = Charsets.UTF_8.newDecoder()
        val decoded = try {
            decoder.decode(java.nio.ByteBuffer.wrap(body)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            fail(RewriteException.Reason.NOT_UTF8)
        }
        val text = decoded.removePrefix("﻿")
        if (!text.trimStart().startsWith("#EXTM3U")) fail(RewriteException.Reason.NOT_PLAYLIST)
        val out = StringBuilder(text.length + text.length / 2)
        var isMaster = false
        var expectVariant = false
        var duration = 0.0
        for (raw in text.split('\n')) {
            val trimmed = raw.trimEnd('\r').trim()
            if (trimmed.isEmpty()) {
                out.append('\n')
                continue
            }
            if (trimmed.startsWith('#')) {
                if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                    isMaster = true
                    expectVariant = true
                }
                trimmed.removePrefix("#EXTINF:").takeIf { it != trimmed }?.let { d ->
                    duration += d.substringBefore(',').trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
                }
                val kind = TAG_KINDS.firstOrNull { trimmed.startsWith(it.first) }?.second
                out.append(if (kind != null && "URI=\"" in trimmed) rewriteAttr(trimmed, base, kind, depth, map) else trimmed)
                out.append('\n')
                continue
            }
            val kind = if (expectVariant) UriKind.PLAYLIST else UriKind.SEGMENT
            expectVariant = false
            if (kind == UriKind.PLAYLIST && depth >= 1) fail(RewriteException.Reason.TOO_DEEP)
            out.append(map(resolve(base, trimmed), kind)).append('\n')
        }
        if (isMaster && depth >= 1) fail(RewriteException.Reason.TOO_DEEP)
        // keep the trailing-newline shape of the input
        while (out.endsWith("\n\n") && !text.endsWith("\n\n")) out.setLength(out.length - 1)
        return Rewritten(out.toString(), isMaster, duration)
    }

    private fun rewriteAttr(tag: String, base: HttpUrl, kind: UriKind, depth: Int, map: (HttpUrl, UriKind) -> String): String {
        val key = "URI=\""
        val out = StringBuilder(tag.length + 64)
        var rest = tag
        while (true) {
            val pos = rest.indexOf(key)
            if (pos < 0) break
            // an attribute name boundary (",URI=" or ":URI="), not e.g. "XURI="
            val boundaryOk = pos == 0 || rest[pos - 1] == ',' || rest[pos - 1] == ':'
            val start = pos + key.length
            val endRel = rest.indexOf('"', start)
            if (endRel < 0) fail(RewriteException.Reason.BAD_URI)
            out.append(rest, 0, start)
            if (boundaryOk) {
                if (kind == UriKind.PLAYLIST && depth >= 1) fail(RewriteException.Reason.TOO_DEEP)
                out.append(map(resolve(base, rest.substring(start, endRel)), kind))
            } else {
                out.append(rest, start, endRel)
            }
            out.append('"')
            rest = rest.substring(endRel + 1)
        }
        out.append(rest)
        return out.toString()
    }
}
