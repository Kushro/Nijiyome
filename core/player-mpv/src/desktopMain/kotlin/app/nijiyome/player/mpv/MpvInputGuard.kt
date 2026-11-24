// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

// Hito 4 (review): what the desktop mpv engines accept from a source before it reaches mpv's command line, its IPC or libmpv. Streams,
// subtitles and headers come from `.kai` extensions, so they are untrusted:
// - header names must be HTTP tokens and values must not hold CR, LF, NUL or other control characters (header injection);
// - stream and subtitle URLs must be http / https; a local file only when the app itself chose it (a download, a file the user
//   opened, a subtitle the user picked). mpv understands many other protocols (`edl://`, `ytdl://`, `av://`, `lavfi://`,
//   `memory://`, `file://`...) that a remote source has no business opening;
// - nothing may start with `-` (an option) or `~` (mpv's home / config-dir shortcuts).

object MpvInputGuard {
    /** The headers mpv may send: the rest is dropped (and logged by the caller if it wants). */
    fun headers(headers: Map<String, String>): Map<String, String> =
        headers.filter { (k, v) -> isHeaderName(k) && isHeaderValue(v) }

    /** RFC 9110 token: letters, digits and ``!#$%&'*+-.^_`|~``. */
    fun isHeaderName(name: String): Boolean = name.isNotEmpty() && name.all { it.isLetterOrDigit() && it.code < 128 || it in TOKEN_EXTRA }

    /** No control characters except a tab (CR / LF / NUL would split or end the header line). */
    fun isHeaderValue(value: String): Boolean = value.none { (it.code < 0x20 && it != '\t') || it.code == 0x7f }

    /**
     * [url] when mpv may open it as a stream or a subtitle, else null: `http://` / `https://`, or, with [allowLocal], an absolute
     * local path or a `file://` URL. Anything else (another protocol, a relative path, `~`, a leading `-`, control characters) is
     * refused.
     */
    fun mediaUrl(url: String, allowLocal: Boolean): String? {
        if (url.isEmpty() || url.any { it.code < 0x20 || it.code == 0x7f }) return null
        if (url.startsWith("-") || url.startsWith("~")) return null
        val scheme = SCHEME.find(url)?.groupValues?.get(1)?.lowercase()
        return when {
            scheme == "http" || scheme == "https" -> url.takeIf { url.length > scheme.length + 3 && url.regionMatches(scheme.length, "://", 0, 3) }
            !allowLocal -> null
            isAbsoluteLocalPath(url) -> url
            scheme == "file" -> url.takeIf { it.regionMatches(4, "://", 0, 3, ignoreCase = true) }
            else -> null
        }
    }

    /** `/x` (POSIX) or `C:\x` / `C:/x` (Windows). */
    fun isAbsoluteLocalPath(path: String): Boolean =
        path.startsWith("/") || (path.length > 2 && path[0].isLetter() && path[1] == ':' && (path[2] == '\\' || path[2] == '/'))

    private const val TOKEN_EXTRA = "!#$%&'*+-.^_`|~"

    /** A URL scheme; a one-letter "scheme" is a Windows drive. */
    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]+):")
}
