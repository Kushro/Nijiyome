// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import app.nijiyome.model.SubtitleStyle

/**
 * 0.4.0's private subtitle extras as stored in "nijiyome_player" (`sub_text_color`, `sub_outline_color`, `sub_outline_size`,
 * `sub_position`, `sub_font_file`). Pure: built from `SharedPreferences.getAll()` so the migration is unit-tested on the JVM.
 */
data class LegacySubtitleExtras(
    val textColor: Int? = null,
    val outlineColor: Int? = null,
    val outlineSize: Float? = null,
    val positionPct: Int? = null,
    val fontFile: String? = null,
) {
    fun applyTo(style: SubtitleStyle): SubtitleStyle = style.withLegacyExtras(textColor, outlineColor, outlineSize, positionPct, fontFile)

    companion object {
        const val K_TEXT_COLOR = "sub_text_color"
        const val K_OUTLINE_COLOR = "sub_outline_color"
        const val K_OUTLINE_SIZE = "sub_outline_size"
        const val K_POSITION = "sub_position"
        const val K_FONT = "sub_font_file"
        val KEYS: List<String> = listOf(K_TEXT_COLOR, K_OUTLINE_COLOR, K_OUTLINE_SIZE, K_POSITION, K_FONT)

        /** The stored extras, or null when the user never changed them (0.4.0 wrote every key on the first change). */
        fun from(values: Map<String, *>): LegacySubtitleExtras? {
            if (KEYS.none { it in values }) return null
            return LegacySubtitleExtras(
                textColor = values[K_TEXT_COLOR] as? Int,
                outlineColor = values[K_OUTLINE_COLOR] as? Int,
                outlineSize = (values[K_OUTLINE_SIZE] as? Float)?.takeIf { it.isFinite() },
                positionPct = values[K_POSITION] as? Int,
                fontFile = (values[K_FONT] as? String)?.takeIf { it.isNotBlank() },
            )
        }
    }
}
