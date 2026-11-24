// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.FontNames
import app.nijiyome.player.InstalledSubtitleFont
import java.io.File

/**
 * Hito 6: the libass family of `SubtitleStyle.fontFile` on desktop: a font installed on the system ([InstalledSubtitleFont], libass
 * finds it through fontconfig), else an imported file in [fontsDir] (its `name` table's family, or its file name without extension).
 * Null = no such font: [mpvFontFamily] falls back to the bundled one.
 */
internal fun desktopSubtitleFamily(fontFile: String?, fontsDir: File?): String? {
    InstalledSubtitleFont.family(fontFile)?.let { return it }
    val name = fontFile ?: return null
    val f = fontsDir?.let { File(it, name) }?.takeIf { it.isFile } ?: return null
    val bytes = runCatching { f.readBytes() }.getOrNull() ?: return null
    return FontNames.family(bytes) ?: f.nameWithoutExtension
}
