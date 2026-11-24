// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenUrlTest {
    @Test
    fun webLinksAreOpened() {
        for (url in listOf(
            "https://anilist.co/user/x", "http://example.org", " https://anilist.co/activity/1 ", "https://example.org:8443/a?b=c#d",
            "https://münchen.de/x", "https://例え.jp", "https://my_host.example/x", "https://example.org/a b/[c]",
        )) {
            assertTrue(isWebUrl(url), url)
        }
    }

    @Test
    fun theSchemeIsHandedOverInLowerCase() {
        assertEquals("https://ANILIST.CO/x", webUrlOrNull("HTTPS://ANILIST.CO/x"))
        assertEquals("http://example.org", webUrlOrNull("  Http://example.org "))
    }

    @Test
    fun anythingElseIsRefused() {
        for (url in listOf(
            "file:///etc/passwd", "smb://server/share", "javascript:alert(1)", "intent://x#Intent;end", "magnet:?xt=urn:btih:abc",
            "data:text/html,x", "content://x/y", "ftp://example.org/x", "https:///no-host", "anilist.co/user/x", "", "   ", "ht tp://x",
            // credentials and look-alike authorities
            "https://user:pass@example.com", "https://anilist.co@evil.example/x",
            // back-slashes, control characters, an empty host with a port
            "https:\\\\evil.example", "https://evil.example\\@anilist.co", "https://exa\nmple.org", "https://:443/x",
            // no real host, or a port that is not a number
            "https://\u00A0/x", "https://\u3000/x", "https://../x", "https://example.org:abc/x", "https://a:b:80/x", "https://example.org:/x",
        )) {
            assertFalse(isWebUrl(url), url)
        }
    }
}
