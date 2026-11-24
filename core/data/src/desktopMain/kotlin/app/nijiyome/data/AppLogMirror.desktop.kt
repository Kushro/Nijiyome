// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.LogLevel

internal actual fun mirrorLogLine(level: LogLevel, line: String) {
    System.err.println(line)
}
