// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 1.1 F4 moved the nudge and the expression wrapper out of Android's `WebViewEmbedSniffer` / `WebViewBrowserRuntime` into
 * [BrowserScripts]. These pin the exact text Android ran before the move, so Android keeps running the very same JavaScript.
 */
class BrowserScriptsTest {
    @Test
    fun nudgeIsTheTextAndroidRanBefore() {
        // SHA-256 and length of WebViewEmbedSniffer.NUDGE_JS as it was in 1.0 / F3 (computed from that source before the move).
        assertEquals(531, BrowserScripts.NUDGE_JS.length)
        assertEquals("1a7a3483d70696cab50da22a8675ac935ac26f71f4fcde2537eafc9b8a33c2df", sha256(BrowserScripts.NUDGE_JS))
    }

    @Test
    fun wrapperIsTheOneAndroidUsedBefore() {
        assertEquals(
            "(function(){try{var v=(\ndocument.title\n);return JSON.stringify({ok:true,v:v===undefined?null:v});}" +
                "catch(e){return JSON.stringify({ok:false,e:String(e&&e.message||e)});}})()",
            BrowserScripts.wrapExpression(" document.title ; "),
        )
        assertEquals(BrowserScripts.wrapExpression("1+1"), BrowserScripts.wrapExpression("1+1;;  "))
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
