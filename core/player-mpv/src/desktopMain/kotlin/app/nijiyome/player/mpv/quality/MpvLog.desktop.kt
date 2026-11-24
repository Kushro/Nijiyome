// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.quality

/** Desktop has no logcat: informational lines of the mpv engine go to stderr, like the other desktop modules' logs. */
internal actual fun mpvLogInfo(message: String) {
    System.err.println("I/mpv: $message")
}
