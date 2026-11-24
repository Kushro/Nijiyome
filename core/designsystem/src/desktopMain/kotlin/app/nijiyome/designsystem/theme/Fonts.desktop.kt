package app.nijiyome.designsystem.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

internal actual fun bundledFontFamily(font: BundledFont, weights: List<FontWeight>): FontFamily =
    FontFamily(
        weights.map { w ->
            Font(
                resource = "font/${font.fileName}.ttf",
                weight = w,
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(w, FontStyle.Normal),
            )
        },
    )
