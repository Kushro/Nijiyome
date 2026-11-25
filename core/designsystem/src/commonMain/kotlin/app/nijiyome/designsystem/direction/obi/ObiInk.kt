// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.obi

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Background and text of one obi band. */
@Immutable
data class ObiBand(val bg: Color, val fg: Color)

/**
 * Color arithmetic of the Obi direction (artifact `dir-obi.js`): sRGB mixing, WCAG contrast and the band ink rule. Public so the
 * Obi card styles in `:core:ui` paint their bands with the same rule as the direction's decoration.
 */
object ObiInk {
    /** Ink and paper of the bands. */
    val INK = Color(0xFF121212)
    val PAPER = Color(0xFFFFFFFF)

    /** Neutral of a title without a cover color, and the gray a paused band fades towards. */
    val NEUTRAL = Color(0xFF8C8C8C)

    /** Novelty yellow (`--obi-new` fallback) for when an Obi card is drawn under another direction. */
    val NEW = Color(0xFFFFD60A)
    val ON_NEW = Color(0xFF121212)

    /** Minimum contrast of band text; the artifact pushes every band to 5:1 so none falls under 4.5:1. */
    private const val BAND_CONTRAST = 5f

    /** WCAG contrast ratio of [a] and [b] (1..21). */
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** Mixes [a] into [b] by [t] in gamma sRGB, like CSS `color-mix(in srgb, ...)`; alpha is taken from [a]. */
    fun mix(a: Color, b: Color, t: Float): Color {
        val k = t.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * k,
            green = a.green + (b.green - a.green) * k,
            blue = a.blue + (b.blue - a.blue) * k,
            alpha = a.alpha,
        )
    }

    /**
     * Band colors for a series color: ink or paper text, whichever reads better, and if the pair is under 5:1 the background is
     * pushed away from the text until it passes (artifact `inkFor`).
     */
    fun bandFor(base: Color): ObiBand {
        var bg = base.copy(alpha = 1f)
        val useInk = contrast(bg, INK) >= contrast(bg, PAPER)
        val fg = if (useInk) INK else PAPER
        var k = 0
        while (k < 14 && contrast(bg, fg) < BAND_CONTRAST) {
            bg = if (useInk) mix(bg, Color.White, 0.08f) else Color(bg.red * 0.92f, bg.green * 0.92f, bg.blue * 0.92f)
            k++
        }
        return ObiBand(bg, fg)
    }

    /** Moves [fg] towards [toward] in small steps until it reaches [min] contrast on [bg] (or [toward] itself). */
    fun ensureContrast(fg: Color, bg: Color, min: Float, toward: Color): Color {
        var c = fg
        var k = 0
        while (k < 20 && contrast(c, bg) < min) {
            c = mix(c, toward, 0.12f)
            k++
        }
        return c
    }
}
