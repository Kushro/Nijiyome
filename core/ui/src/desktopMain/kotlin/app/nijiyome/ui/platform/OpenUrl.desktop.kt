// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.net.URI

/** `java.awt.Desktop.browse`; nothing happens when the system has no browser to open it. */
@Composable
internal actual fun rememberPlatformUrlOpener(): (url: String) -> Unit = remember {
    { url ->
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(URI(percentEncodeForUri(url)))
        }
    }
}

// java.net.URI refuses characters a browser accepts in a link (spaces, brackets, non-ASCII, a stray "%"). Everything outside
// the URI character set is percent-encoded as UTF-8; a well-formed %XX is left alone.
private const val URI_SAFE = "-._~:/?#[]@!$&'()*+,;="
private const val HEX = "0123456789abcdefABCDEF"

internal fun percentEncodeForUri(url: String): String {
    val afterScheme = url.indexOf("://") + 3
    // Brackets are only legal around an IPv6 host; "#" starts the fragment once.
    val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), startIndex = afterScheme).let { if (it < 0) url.length else it }
    val fragmentStart = url.indexOf('#')
    val out = StringBuilder(url.length + 16)
    var index = 0
    while (index < url.length) {
        val codePoint = url.codePointAt(index)
        val c = url[index]
        val keep = when {
            codePoint >= 128 -> false
            c == '%' -> index + 2 < url.length && url[index + 1] in HEX && url[index + 2] in HEX
            c == '[' || c == ']' -> index < authorityEnd
            c == '#' -> index == fragmentStart
            else -> c.isLetterOrDigit() || c in URI_SAFE
        }
        if (keep) {
            out.append(c)
        } else {
            String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).forEach { b -> out.append('%').append("%02X".format(b.toInt() and 0xFF)) }
        }
        index += Character.charCount(codePoint)
    }
    return out.toString()
}
