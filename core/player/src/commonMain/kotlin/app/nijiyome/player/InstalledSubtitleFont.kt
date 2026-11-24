// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

/**
 * Hito 6: a font installed on the system, chosen for the subtitles (desktop). It is stored in `SubtitleStyle.fontFile` as
 * `family:<name>`: an imported file name never has a `:` (its name is sanitised), so the two never mix, and a platform that does not
 * know the prefix (Android) finds no such file and falls back to the bundled font, as with a deleted import.
 */
object InstalledSubtitleFont {
    const val PREFIX = "family:"

    /** The `fontFile` value of the installed family [name]. */
    fun ref(name: String): String = PREFIX + name.trim()

    /** The installed family named by [fontFile], or null when it is an imported file name (or nothing). */
    fun family(fontFile: String?): String? = fontFile?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.trim()?.takeIf { it.isNotEmpty() }
}
