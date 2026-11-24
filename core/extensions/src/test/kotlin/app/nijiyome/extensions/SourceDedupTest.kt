// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import app.nijiyome.model.Quality
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.model.TorrentInfo
import app.nijiyome.model.WebInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** BUG-086: OG5 listed 94 sources, each local Toko / Latino source again as "… · sala · …" from the PC. */
class SourceDedupTest {
    private fun web(ext: String, name: String, label: String, url: String, q: Quality = Quality.P1080, dubs: List<String> = emptyList()) =
        SourceCandidate(ext, name, SourceKind.WEB, label, quality = q, dubs = dubs, web = WebInfo(url))

    private fun torrent(ext: String, name: String, hash: String) =
        SourceCandidate(ext, name, SourceKind.TORRENT, "SubsPlease", torrent = TorrentInfo(hash))

    @Test
    fun pcCopiesOfLocalSourcesAreDropped() {
        val local = listOf(
            web("nijiyome.extension.toko", "Toko", "anizone", "https://cdn.example/a.m3u8"),
            torrent("nijiyome.extension.toko", "Toko", "57e72d46a0c1ba4c4b3dbd0e9af87d45cd0a3fcb"),
        )
        val remote = listOf(
            web("server:pc/toko", "Toko · sala", "anizone", "http://192.168.1.43:8099/api/server/v1/media/x"),
            torrent("server:pc/toko", "Toko · sala", "57E72D46A0C1BA4C4B3DBD0E9AF87D45CD0A3FCB"),
            web("server:pc/toko", "Toko · sala", "anizone", "http://192.168.1.43:8099/api/server/v1/media/y", q = Quality.P720),
            web("server:pc/toko", "Toko · sala", "anizone", "http://192.168.1.43:8099/api/server/v1/media/z", dubs = listOf("es")),
            web("server:pc/hdb", "HentaiDB · sala", "x", "http://192.168.1.43:8099/api/server/v1/media/w"),
        )
        val out = SourceDedup.withoutRemoteDuplicates(local, remote)
        assertEquals(local + remote.drop(2), out, "only exact duplicates go; other qualities, dubs and PC-only sources stay")
    }

    @Test
    fun withoutLocalSourcesThePcListIsKept() {
        val remote = listOf(web("server:pc/toko", "Toko · sala", "anizone", "http://pc/x"))
        assertEquals(remote, SourceDedup.withoutRemoteDuplicates(emptyList(), remote))
    }
}
