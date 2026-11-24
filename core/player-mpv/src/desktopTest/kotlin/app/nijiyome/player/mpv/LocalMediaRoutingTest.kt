// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.model.Container
import app.nijiyome.model.LocalMediaRoute
import app.nijiyome.model.RoutedStream
import app.nijiyome.model.SubtitleTrack
import app.nijiyome.player.PlayableSource
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** link-servidor-7: what the desktop mpv engines open once the loopback media proxy has its say. */
class LocalMediaRoutingTest {
    private class FakeRoute : LocalMediaRoute {
        val asked = mutableListOf<String>()
        override fun route(url: String, headers: Map<String, String>, subtitles: List<String>): RoutedStream? {
            asked += url
            if ("Cookie" !in headers) return null
            return RoutedStream("http://127.0.0.1:5000/api/server/v1/media/T/index.m3u8", emptyMap(), subtitles.associateWith { "http://127.0.0.1:5000/api/server/v1/media/S/es.vtt" })
        }
    }

    private fun src(headers: Map<String, String>, torrent: Boolean = false, client: OkHttpClient? = null, local: Boolean = false) = PlayableSource(
        url = "https://cdn.example/v/index.m3u8",
        headers = headers,
        container = Container.HLS,
        label = "Ext - 1080p",
        quality = "1080p",
        subtitles = listOf(SubtitleTrack("https://cdn.example/s/es.vtt", "es", "Español", "vtt")),
        httpClient = client,
        torrent = torrent,
        selectSubtitle = 0,
        localFile = local,
    )

    @Test
    fun aStreamWithCredentialsIsRewrittenWithoutHeaders() {
        val r = FakeRoute()
        val out = routedSource(src(mapOf("Cookie" to "a=b", "Referer" to "https://site/")), r)
        assertTrue(out.url.startsWith("http://127.0.0.1:5000/"))
        assertTrue(out.headers.isEmpty())
        assertEquals("http://127.0.0.1:5000/api/server/v1/media/S/es.vtt", out.subtitles.single().url)
        assertEquals("Español", out.subtitles.single().label)
        assertEquals(0, out.selectSubtitle)
        assertEquals("1080p", out.quality)
    }

    /** An outbound proxy is set: every http URL goes through the loopback proxy. */
    private class AllRoute : LocalMediaRoute {
        val asked = mutableListOf<String>()
        override fun route(url: String, headers: Map<String, String>, subtitles: List<String>): RoutedStream {
            asked += url
            return RoutedStream("http://127.0.0.1:5000/api/server/v1/media/T/x", emptyMap())
        }
    }

    @Test
    fun torrentAndLocalSourcesStillProxyTheirHttpSubtitles() {
        val r = AllRoute()
        val torrent = src(emptyMap(), torrent = true)
        val out = routedSource(torrent, r)
        assertEquals(torrent.url, out.url) // the video stays on the loopback torrent stream
        assertTrue(out.torrent)
        assertEquals("http://127.0.0.1:5000/api/server/v1/media/T/x", out.subtitles.single().url)
        assertEquals(listOf("https://cdn.example/s/es.vtt"), r.asked)
        // a subtitle file on disk is never asked about
        val tracks = listOf(SubtitleTrack("/home/u/es.srt", "es", "es", "srt"))
        assertEquals(tracks, routedSubtitleTracks(tracks, r))
        assertEquals(1, r.asked.size)
    }

    @Test
    fun otherSourcesAreLeftAlone() {
        val r = FakeRoute()
        val plain = src(mapOf("Referer" to "https://site/"))
        assertSame(plain, routedSource(plain, r))
        assertSame(plain, routedSource(plain, null))
        // Link (mTLS client), torrent loopback streams and local files are never asked about
        val link = src(mapOf("Cookie" to "a=b"), client = OkHttpClient())
        val torrent = src(mapOf("Cookie" to "a=b"), torrent = true)
        val local = src(mapOf("Cookie" to "a=b"), local = true)
        assertSame(link, routedSource(link, r))
        // torrent / local: only their http subtitles are asked about (FakeRoute leaves them: no outbound proxy)
        assertSame(torrent, routedSource(torrent, r))
        assertSame(local, routedSource(local, r))
        assertEquals(listOf("https://cdn.example/v/index.m3u8", "https://cdn.example/s/es.vtt", "https://cdn.example/s/es.vtt"), r.asked)
    }
}
