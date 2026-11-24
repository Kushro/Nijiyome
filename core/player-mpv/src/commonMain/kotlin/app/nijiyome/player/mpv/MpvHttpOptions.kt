// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

/**
 * The HTTP options Android's [MpvEngine] sets on every load for a source's [headers], in order: `user-agent` (the source's, else
 * [MpvEngineDefaults.DEFAULT_USER_AGENT]), `referrer` (empty = mpv sends none) and `http-header-fields` with the rest (a string
 * list: entries separated by `,`, so a comma inside a value is escaped mpv-style). Header names are matched without case.
 *
 * v1.1: checked against mpv 0.42 / FFmpeg by MpvHttpHeadersIntegrationTest: FFmpeg sends all three on the HLS master, after its
 * redirect, on the media playlist, the AES key and every segment (redirected or not), and on a progressive file after a redirect.
 */
internal fun mpvHttpOptions(headers: Map<String, String>): List<Pair<String, String>> {
    val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
    val referer = headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
    val rest = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) && !it.equals("Referer", ignoreCase = true) }
    return listOf(
        "user-agent" to (ua ?: MpvEngineDefaults.DEFAULT_USER_AGENT),
        "referrer" to (referer ?: ""),
        "http-header-fields" to rest.entries.joinToString(",") { (k, v) -> "$k: ${v.replace(",", "\\,")}" },
    )
}
