// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.LinkSource
import app.nijiyome.model.MediaRef
import app.nijiyome.model.PcPlayerState
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.TorrentInfo
import app.nijiyome.model.WebInfo
import app.nijiyome.player.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop player's side of Nijiyome Link: what `GET /v1/player` reports and what "Enviar al teléfono" can send. */
class DesktopLinkRemoteTest {
    private val args = PlayerArgs(media = MediaRef(anilistId = 21), title = "One Piece", episode = 3)

    private fun web(url: String, embed: Boolean = false) = RankedSource(
        SourceCandidate(extId = "ext", extName = "Ext", kind = SourceKind.WEB, label = "Alpha", container = "HLS", web = WebInfo(url, mapOf("Referer" to "https://site/"), embed = embed)),
        score = 1f,
    )

    private fun torrent(magnet: String?) = RankedSource(
        SourceCandidate(extId = "ext", extName = "Ext", kind = SourceKind.TORRENT, label = "Group", torrent = TorrentInfo(infoHash = "0123456789abcdef0123456789abcdef01234567", magnet = magnet)),
        score = 1f,
    )

    @Test
    fun theStateIsWhatPlaysWithTheHistoryId() {
        assertEquals(
            PcPlayerState(playing = true, title = "One Piece", episode = 3, positionSecs = 125, durationSecs = 1440, mediaId = "21"),
            playerState(args, PlaybackState(isPlaying = true, positionMs = 125_900, durationMs = 1_440_000)),
        )
        val mal = playerState(args.copy(media = MediaRef(malId = 5114), title = " "), PlaybackState(isPlaying = false, durationMs = 0))
        assertEquals("mal:5114", mal.mediaId)
        assertNull(mal.title)
        assertNull(mal.durationSecs)
    }

    @Test
    fun theTorrentOrAPublicStreamCanBeSentNeverALocalOne() {
        assertEquals(
            LinkSource.Url("https://cdn.example/ep3.m3u8", mapOf("Referer" to "https://site/")),
            sendableSource(PlayerUiState(current = web("https://cdn.example/ep3.m3u8")), args),
        )
        assertEquals(LinkSource.Torrent("magnet:?xt=urn:btih:abc", "21", 3), sendableSource(PlayerUiState(current = torrent("magnet:?xt=urn:btih:abc")), args))
        assertEquals(
            LinkSource.Torrent("0123456789abcdef0123456789abcdef01234567", "21", 3),
            sendableSource(PlayerUiState(current = torrent(null)), args),
        )
        assertEquals(LinkSource.Torrent("feedbeef", "21", 3), sendableSource(PlayerUiState(), args.copy(torrentId = "feedbeef")))
        assertNull(sendableSource(PlayerUiState(current = web("http://127.0.0.1:8099/api/server/v1/media/t/a.m3u8")), args))
        assertNull(sendableSource(PlayerUiState(current = web("https://site/embed/1", embed = true)), args))
        assertNull(sendableSource(PlayerUiState(current = web("https://cdn.example/ep3.m3u8")), args.copy(localUri = "/videos/ep3.mkv")))
        assertNull(sendableSource(PlayerUiState(), args))
    }

    @Test
    fun onlyHttpToAnotherHostIsPublic() {
        assertTrue(publicHttp("https://cdn.example/a.m3u8"))
        assertTrue(publicHttp("http://93.184.216.34/a.mp4"))
        for (u in listOf("http://localhost:1/a", "http://a.localhost/a", "http://127.0.0.1/a", "http://[::1]/a", "file:///etc/passwd", "ftp://h/a", "not a url")) {
            assertFalse(publicHttp(u), u)
        }
    }
}
