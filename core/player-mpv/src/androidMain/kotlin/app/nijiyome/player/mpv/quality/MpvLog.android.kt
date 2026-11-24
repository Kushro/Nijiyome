// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.quality

internal actual fun mpvLogInfo(message: String) {
    android.util.Log.i("mpv", message)
}
