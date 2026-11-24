// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Returns a function that opens a web link in the browser; nothing happens when no app can open it.
 *
 * The links come from remote data (an AniList profile, an activity, a review), so only `http` and `https` with a host are
 * passed on: a `file:`, `smb:`, `javascript:` or custom-scheme "link" must never reach the system handler.
 */
@Composable
fun rememberOpenUrl(): (url: String) -> Unit {
    val open = rememberPlatformUrlOpener()
    return remember(open) { { url -> webUrlOrNull(url)?.let(open) } }
}

// scheme "://" authority, then nothing or a path / query / fragment. The authority has no "@" (no credentials, no
// "https://trusted@evil" look-alikes), no slash and no whitespace. Hosts are not restricted to ASCII: IDN links are fine.
private val WEB_URL = Regex("^(https?)://([^/?#@\\\\\\s]+)([/?#].*)?$", RegexOption.IGNORE_CASE)

/**
 * The link as it is handed to the system, or null when it is not an absolute `http` / `https` URL with a host.
 * The scheme comes back in lower case: Android matches intent filters case-sensitively, so "HTTPS://x" would open nothing.
 */
fun webUrlOrNull(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.any { it.isISOControl() }) return null
    val match = WEB_URL.matchEntire(trimmed) ?: return null
    val (scheme, authority, rest) = match.destructured
    // host or host:port, the port a number. (IPv6 literals are not accepted: no link the app shows uses one.)
    val parts = authority.split(':')
    if (parts.size > 2 || (parts.size == 2 && (parts[1].isEmpty() || !parts[1].all { it in '0'..'9' }))) return null
    val host = parts[0]
    // A host needs a letter or a digit, and no kind of space (the regex only excludes ASCII whitespace).
    if (host.none(Char::isLetterOrDigit) || host.any { it.isWhitespace() || Character.isSpaceChar(it) }) return null
    return scheme.lowercase() + "://" + authority + rest
}

/** true for an absolute `http` / `https` URL with a host and without credentials. */
fun isWebUrl(url: String): Boolean = webUrlOrNull(url) != null

/** Hands a URL already checked by [isWebUrl] to the system. */
@Composable
internal expect fun rememberPlatformUrlOpener(): (url: String) -> Unit
