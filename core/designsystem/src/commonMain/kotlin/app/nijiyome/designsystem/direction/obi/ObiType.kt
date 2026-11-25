// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.designsystem.direction.obi

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_obi_dela_gothic_one
import app.nijiyome.designsystem.resources.rd_obi_mplus1
import org.jetbrains.compose.resources.Font

// Type of the Obi direction (dir-obi.css): Dela Gothic One for the bands and display lines, M PLUS 1 for the tool. Both are
// latin subsets (pyftsubset) of the google/fonts builds, SIL OFL 1.1; Japanese titles fall back to the system face.

/** The two faces of the Obi direction. Public so the Obi card styles of `:core:ui` letter their bands with them. */
object ObiFonts {
    /** Dela Gothic One: one weight (400). Never below 12 sp: it clogs. */
    @Composable
    fun band(): FontFamily {
        val font = Font(Res.font.rd_obi_dela_gothic_one, weight = FontWeight.W400)
        return remember(font) { FontFamily(font) }
    }

    /** M PLUS 1, variable `wght`, pinned per weight. */
    @Composable
    fun ui(): FontFamily {
        val fonts = UI_WEIGHTS.map { w ->
            Font(Res.font.rd_obi_mplus1, weight = w, style = FontStyle.Normal, variationSettings = FontVariation.Settings(w, FontStyle.Normal))
        }
        return remember(fonts) { FontFamily(fonts) }
    }

    private val UI_WEIGHTS = listOf(FontWeight.W400, FontWeight.W500, FontWeight.W600, FontWeight.W700, FontWeight.W800)

    /**
     * 長体: the horizontal compression of real obi lettering (artifact `.obi-cond`, 86 %). Apply with `style.merge(condensed())`
     * or `TextStyle(textGeometricTransform = ...)`.
     */
    fun condensed(scaleX: Float = 0.86f): TextGeometricTransform = TextGeometricTransform(scaleX = scaleX)
}

/** The app scale with Obi faces: display, headline and the large title in Dela Gothic One; the rest in bold M PLUS 1. */
@Composable
internal fun obiTypography(base: Typography): Typography {
    val band = ObiFonts.band()
    val ui = ObiFonts.ui()
    return remember(base, band, ui) {
        fun TextStyle.dela() = copy(fontFamily = band, fontWeight = FontWeight.W400, letterSpacing = 0.sp)
        fun TextStyle.mplus(weight: FontWeight? = null) = copy(fontFamily = ui, fontWeight = weight ?: fontWeight)
        base.copy(
            displayLarge = base.displayLarge.dela(),
            displayMedium = base.displayMedium.dela(),
            displaySmall = base.displaySmall.dela(),
            headlineLarge = base.headlineLarge.dela(),
            headlineMedium = base.headlineMedium.dela(),
            headlineSmall = base.headlineSmall.dela(),
            titleLarge = base.titleLarge.dela(),
            titleMedium = base.titleMedium.mplus(FontWeight.W800),
            titleSmall = base.titleSmall.mplus(FontWeight.W700),
            bodyLarge = base.bodyLarge.mplus(),
            bodyMedium = base.bodyMedium.mplus(),
            bodySmall = base.bodySmall.mplus(),
            labelLarge = base.labelLarge.mplus(FontWeight.W800),
            labelMedium = base.labelMedium.mplus(FontWeight.W700),
            labelSmall = base.labelSmall.mplus(FontWeight.W700),
            displayLargeEmphasized = base.displayLargeEmphasized.dela(),
            displayMediumEmphasized = base.displayMediumEmphasized.dela(),
            displaySmallEmphasized = base.displaySmallEmphasized.dela(),
            headlineLargeEmphasized = base.headlineLargeEmphasized.dela(),
            headlineMediumEmphasized = base.headlineMediumEmphasized.dela(),
            headlineSmallEmphasized = base.headlineSmallEmphasized.dela(),
            titleLargeEmphasized = base.titleLargeEmphasized.dela(),
            titleMediumEmphasized = base.titleMediumEmphasized.mplus(FontWeight.W800),
            titleSmallEmphasized = base.titleSmallEmphasized.mplus(FontWeight.W800),
            bodyLargeEmphasized = base.bodyLargeEmphasized.mplus(FontWeight.W700),
            bodyMediumEmphasized = base.bodyMediumEmphasized.mplus(FontWeight.W700),
            bodySmallEmphasized = base.bodySmallEmphasized.mplus(FontWeight.W700),
            labelLargeEmphasized = base.labelLargeEmphasized.mplus(FontWeight.W800),
            labelMediumEmphasized = base.labelMediumEmphasized.mplus(FontWeight.W800),
            labelSmallEmphasized = base.labelSmallEmphasized.mplus(FontWeight.W800),
        )
    }
}
