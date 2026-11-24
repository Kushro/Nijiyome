// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenUrlDesktopTest {
    @Test
    fun linksABrowserAcceptsBecomeValidUris() {
        assertEquals("https://example.org/a%20b/%5Bc%5D", percentEncodeForUri("https://example.org/a b/[c]"))
        assertEquals("https://example.org/a%20b?x=%7B1%7D", percentEncodeForUri("https://example.org/a%20b?x={1}"))
        assertEquals("https://anilist.co/user/x", percentEncodeForUri("https://anilist.co/user/x"))
        assertEquals("https://example.org/50%25_off", percentEncodeForUri("https://example.org/50%_off"))
        assertEquals("https://example.org?q=%5B1%5D", percentEncodeForUri("https://example.org?q=[1]"))
        assertEquals("https://example.org/a#b%23c", percentEncodeForUri("https://example.org/a#b#c"))
        assertEquals("https://example.org/%F0%9F%98%80", percentEncodeForUri("https://example.org/\uD83D\uDE00"))
        // every link the common check lets through must parse
        for (url in listOf("https://example.org/a b/[c]", "https://münchen.de/ä", "https://例え.jp/パス?q=値", "https://my_host.example/x", "https://example.org/50%_off", "https://example.org?q=[1]", "https://example.org/100%", "https://example.org/a#b#c")) {
            URI(percentEncodeForUri(webUrlOrNull(url)!!))
        }
    }
}
