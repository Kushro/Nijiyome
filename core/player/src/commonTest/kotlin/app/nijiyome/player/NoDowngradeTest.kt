// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 1.1 review #1: debrid links (and https files read for subtitles) never follow a redirect down to http://. */
class NoDowngradeTest {
    private val base = OkHttpClient()

    @Test
    fun httpsOnlySourcesDoNotFollowSchemeChangingRedirects() {
        assertEquals(false, noDowngradeClient(base, httpsOnly = true).followSslRedirects)
        assertEquals(true, noDowngradeClient(base, httpsOnly = true).followRedirects)
        assertSame(base, noDowngradeClient(base, httpsOnly = false))
        assertEquals(true, PlayableSource.from(app.nijiyome.model.StreamSource("https://cdn/x", emptyMap(), app.nijiyome.model.Container.PROGRESSIVE, null, "TorBox", "torrent"), httpsOnly = true).httpsOnly)
    }

    @Test
    fun theSubtitleReaderOfAnHttpsFileDoesNotDowngrade() {
        assertEquals(false, HttpByteSource(base, "https://cdn.example/a.mkv", emptyMap(), remote = true).http.followSslRedirects)
        assertEquals(true, HttpByteSource(base, "http://127.0.0.1:1/a.mkv", emptyMap(), remote = false).http.followSslRedirects)
    }
}
