// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `Nijiyome_<title>_E<ep>_<yyyyMMdd-HHmmss>.png`, file-system safe (the name of a player screenshot on every platform). */
fun screenshotFileName(title: String, episode: Int?, now: LocalDateTime = LocalDateTime.now()): String {
    val clean = title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(48).ifEmpty { "video" }
    val ep = episode?.let { "_E$it" }.orEmpty()
    return "Nijiyome_${clean}${ep}_${now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))}.png"
}
