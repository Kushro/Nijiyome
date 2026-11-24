@file:OptIn(ExperimentalTextApi::class)

package app.nijiyome.designsystem.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.nijiyome.designsystem.R

// compose-ui `Font(resId, weight, style, loadingStrategy, variationSettings)` is `@ExperimentalTextApi`; minSdk 26 supports
// variation axes.
internal actual fun bundledFontFamily(font: BundledFont, weights: List<FontWeight>): FontFamily {
    val resId = when (font) {
        BundledFont.SpaceGrotesk -> R.font.space_grotesk
        BundledFont.Outfit -> R.font.outfit
        BundledFont.JetBrainsMono -> R.font.jetbrains_mono
    }
    return FontFamily(
        weights.map { w ->
            Font(
                resId = resId,
                weight = w,
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(w, FontStyle.Normal),
            )
        },
    )
}
