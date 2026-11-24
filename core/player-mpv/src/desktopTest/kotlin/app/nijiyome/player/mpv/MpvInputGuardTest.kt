// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What an extension's source may hand the desktop mpv engines (review of hito 4). */
class MpvInputGuardTest {
    @Test
    fun headersWithLineBreaksOrNulOrBadNamesAreDropped() {
        val h = linkedMapOf(
            "Referer" to "https://site.example/",
            "User-Agent" to "UA/1 (x)",
            "X-Ok" to "a,b;\tc",
            "X-Inject" to "a\r\nX-Evil: 1",
            "X-Lf" to "a\nb",
            "X-Nul" to "a\u0000b",
            "Bad Name" to "x",
            "Bad:Name" to "x",
            "" to "x",
            "X-Del" to "a\u007fb",
        )
        assertEquals(listOf("Referer", "User-Agent", "X-Ok"), MpvInputGuard.headers(h).keys.toList())
        assertTrue(MpvInputGuard.isHeaderName("X-Custom_1.!#$%&'*+^`|~"))
        assertFalse(MpvInputGuard.isHeaderName("Ñame"))
    }

    @Test
    fun onlyHttpAndHttpsFromAnExtension() {
        for (ok in listOf("https://cdn.example/v.m3u8", "http://127.0.0.1:41234/tok/1/ep.mkv", "HTTPS://CDN.EXAMPLE/A.MP4")) {
            assertEquals(ok, MpvInputGuard.mediaUrl(ok, allowLocal = false), ok)
        }
        val refused = listOf(
            "edl://%20%https://a", "ytdl://abc", "av://lavfi:sine", "lavfi://sine", "memory://data", "file:///etc/passwd", "/etc/passwd",
            "C:\\Windows\\win.ini", "smb://host/share", "rtmp://host/live", "dvd://", "ffmpeg://x", "https:", "https:/x", "", "-—help",
            "--script=/tmp/x.lua", "~~/scripts/x.lua", "~/x.mkv", "relative/x.mkv", "https://a.example/\r\nb", "https://a.example/\u0000",
        )
        for (bad in refused) assertNull(MpvInputGuard.mediaUrl(bad, allowLocal = false), bad)
    }

    @Test
    fun localFilesOnlyForTheAppsOwnPlayback() {
        assertEquals("/home/user/Videos/ep 1.mkv", MpvInputGuard.mediaUrl("/home/user/Videos/ep 1.mkv", allowLocal = true))
        assertEquals("C:\\Users\\user\\ep.mkv", MpvInputGuard.mediaUrl("C:\\Users\\user\\ep.mkv", allowLocal = true))
        assertEquals("file:///home/user/ep.mkv", MpvInputGuard.mediaUrl("file:///home/user/ep.mkv", allowLocal = true))
        // Still no other protocol, no relative path, no mpv shortcuts.
        for (bad in listOf("edl://x", "lavfi://sine", "relative.mkv", "~/x.mkv", "-x", "file:/x")) {
            assertNull(MpvInputGuard.mediaUrl(bad, allowLocal = true), bad)
        }
    }

    @Test
    fun theEmbeddedEngineDropsUnsafeHeadersToo() {
        val h = mediampHeaders(mapOf("Referer" to "r", "X-Inject" to "a\r\nb", "X-Ok" to "a,b"))
        assertEquals(listOf("User-Agent", "Referer", "X-Ok"), h.keys.toList())
        assertEquals("a\\,b", h["X-Ok"])
    }
}
