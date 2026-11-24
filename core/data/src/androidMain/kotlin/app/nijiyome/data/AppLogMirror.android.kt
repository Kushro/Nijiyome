// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.LogLevel

internal actual fun mirrorLogLine(level: LogLevel, line: String) {
    when (level) {
        LogLevel.INFO -> android.util.Log.i("Nijiyome", line)
        LogLevel.WARN -> android.util.Log.w("Nijiyome", line)
        LogLevel.ERROR -> android.util.Log.e("Nijiyome", line)
    }
}
