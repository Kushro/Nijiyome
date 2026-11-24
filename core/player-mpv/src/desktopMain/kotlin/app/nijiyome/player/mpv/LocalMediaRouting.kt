// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.model.LocalMediaRoute
import app.nijiyome.model.SubtitleTrack
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.mpv.quality.mpvLogInfo

/**
 * [source] as the desktop mpv engines open it (Tatakai Next `local_stream`): through the app's loopback media proxy when [route] says
 * so (its headers would leak to every CDN, or an outbound proxy is set), with no header fields left for mpv; otherwise unchanged.
 * Link (mTLS client), torrent and local sources are never rewritten. Throws [app.nijiyome.model.LocalRouteUnavailable] when the
 * stream must not be played directly and the proxy cannot start.
 */
internal fun routedSource(source: PlayableSource, route: LocalMediaRoute?): PlayableSource {
    if (route == null || source.httpClient != null) return source
    if (source.torrent || source.localFile) {
        // the video stays local (loopback torrent stream, a file on disk); its http subtitles still go through the proxy when one is
        // set (an outbound proxy): never fetched outside it
        val subs = routedSubtitleTracks(source.subtitles, route)
        if (subs == source.subtitles) return source
        return PlayableSource(
            url = source.url,
            headers = source.headers,
            container = source.container,
            label = source.label,
            quality = source.quality,
            subtitles = subs,
            httpClient = null,
            torrent = source.torrent,
            selectSubtitle = source.selectSubtitle,
            localFile = source.localFile,
        )
    }
    val r = route.route(source.url, source.headers, source.subtitles.map { it.url }) ?: return source
    mpvLogInfo("stream opened through the loopback media proxy")
    return PlayableSource(
        url = r.url,
        headers = r.headers,
        container = source.container,
        label = source.label,
        quality = source.quality,
        subtitles = source.subtitles.map { s -> r.subtitleUrls[s.url]?.let { s.copy(url = it) } ?: s },
        httpClient = null,
        torrent = false,
        selectSubtitle = source.selectSubtitle,
        localFile = false,
    )
}

/**
 * Subtitle URLs added to a file already playing ("Buscar subtítulos", the quality menu): through the loopback proxy when [route] says
 * so (an outbound proxy is set). A track that must not be fetched directly and cannot be proxied is dropped.
 */
internal fun routedSubtitleTracks(tracks: List<SubtitleTrack>, route: LocalMediaRoute?): List<SubtitleTrack> {
    if (route == null) return tracks
    // only http(s) URLs are asked about (a local subtitle file is left alone)
    return tracks.mapNotNull { t ->
        if (!t.url.startsWith("http://", true) && !t.url.startsWith("https://", true)) return@mapNotNull t
        runCatching { route.route(t.url, emptyMap())?.let { t.copy(url = it.url) } ?: t }.getOrNull()
    }
}
