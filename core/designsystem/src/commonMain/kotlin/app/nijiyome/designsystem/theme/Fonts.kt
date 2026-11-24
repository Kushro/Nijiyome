package app.nijiyome.designsystem.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.nijiyome.designsystem.generated.NijiyomeFontFamilies

/** The three bundled variable fonts. Each platform loads them its own way ([bundledFontFamily]). */
enum class BundledFont(val fileName: String) {
    SpaceGrotesk("space_grotesk"),
    Outfit("outfit"),
    JetBrainsMono("jetbrains_mono"),
}

/**
 * One variable `[wght]` file serves every weight: each platform pins the axis per [FontWeight].
 * Android reads `res/font/`; desktop reads `font/<fileName>.ttf` from the classpath.
 */
internal expect fun bundledFontFamily(font: BundledFont, weights: List<FontWeight>): FontFamily

/**
 * Font families used by the type scale (01 §3): Space Grotesk for display/headline/title, Outfit for body/label,
 * JetBrains Mono for figures and monospace.
 *
 * The files are the variable `[wght]` builds from github.com/google/fonts (SIL OFL 1.1; licenses in
 * `assets/licenses/OFL-*.txt`, rows in THIRD_PARTY_NOTICES.md §3).
 */
object NijiyomeFonts {
    private val WEIGHTS = listOf(FontWeight.W400, FontWeight.W500, FontWeight.W600, FontWeight.W700)

    val spaceGrotesk: FontFamily = bundledFontFamily(BundledFont.SpaceGrotesk, WEIGHTS)
    val outfit: FontFamily = bundledFontFamily(BundledFont.Outfit, WEIGHTS)
    val jetBrainsMono: FontFamily = bundledFontFamily(BundledFont.JetBrainsMono, WEIGHTS)

    val families: NijiyomeFontFamilies = NijiyomeFontFamilies(
        display = spaceGrotesk,
        text = outfit,
        mono = jetBrainsMono,
    )
}
