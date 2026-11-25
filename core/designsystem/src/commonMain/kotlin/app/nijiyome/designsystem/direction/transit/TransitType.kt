// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.transit

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_transit_atkinson_400
import app.nijiyome.designsystem.resources.rd_transit_atkinson_600
import app.nijiyome.designsystem.resources.rd_transit_atkinson_700
import app.nijiyome.designsystem.resources.rd_transit_overpass_700
import app.nijiyome.designsystem.resources.rd_transit_overpass_800
import app.nijiyome.designsystem.resources.rd_transit_overpass_900
import org.jetbrains.compose.resources.Font

// Type of Tránsito nocturno: Atkinson Hyperlegible Next for the interface (made to be read from afar: big monitor, TV from the
// couch) and Overpass for display, labels and figures (it descends from the Highway Gothic of road signs). Figures are tabular.
// Both are static Latin subsets of the OFL variable fonts of github.com/google/fonts; Japanese titles fall back to the system font.

/** Overpass 700 / 800 / 900: titles, labels, station codes. */
@Composable
fun transitDisplayFamily(): FontFamily = FontFamily(
    Font(Res.font.rd_transit_overpass_700, FontWeight.Bold),
    Font(Res.font.rd_transit_overpass_800, FontWeight.ExtraBold),
    Font(Res.font.rd_transit_overpass_900, FontWeight.Black),
)

/** Atkinson Hyperlegible Next 400 / 600 / 700: body text. */
@Composable
fun transitUiFamily(): FontFamily = FontFamily(
    Font(Res.font.rd_transit_atkinson_400, FontWeight.Normal),
    Font(Res.font.rd_transit_atkinson_600, FontWeight.SemiBold),
    Font(Res.font.rd_transit_atkinson_700, FontWeight.Bold),
)

private const val TABULAR = "tnum"

private fun TextStyle.transit(family: FontFamily, weight: FontWeight, tracking: TextUnit = letterSpacing): TextStyle =
    copy(fontFamily = family, fontWeight = weight, fontFeatureSettings = TABULAR, letterSpacing = tracking)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun transitTypography(base: Typography): Typography {
    val display = transitDisplayFamily()
    val ui = transitUiFamily()
    return Typography(
        displayLarge = base.displayLarge.transit(display, FontWeight.Black, (-0.016).em),
        displayMedium = base.displayMedium.transit(display, FontWeight.Black, (-0.016).em),
        displaySmall = base.displaySmall.transit(display, FontWeight.ExtraBold, (-0.012).em),
        headlineLarge = base.headlineLarge.transit(display, FontWeight.ExtraBold, (-0.012).em),
        headlineMedium = base.headlineMedium.transit(display, FontWeight.ExtraBold, (-0.008).em),
        headlineSmall = base.headlineSmall.transit(display, FontWeight.ExtraBold, (-0.004).em),
        titleLarge = base.titleLarge.transit(display, FontWeight.ExtraBold, (-0.004).em),
        titleMedium = base.titleMedium.transit(display, FontWeight.Bold),
        titleSmall = base.titleSmall.transit(display, FontWeight.Bold),
        bodyLarge = base.bodyLarge.transit(ui, FontWeight.Normal),
        bodyMedium = base.bodyMedium.transit(ui, FontWeight.Normal),
        bodySmall = base.bodySmall.transit(ui, FontWeight.Normal),
        labelLarge = base.labelLarge.transit(display, FontWeight.Bold, 0.005.em),
        labelMedium = base.labelMedium.transit(display, FontWeight.Bold, 0.01.em),
        labelSmall = base.labelSmall.transit(ui, FontWeight.SemiBold),
        displayLargeEmphasized = base.displayLargeEmphasized.transit(display, FontWeight.Black, (-0.016).em),
        displayMediumEmphasized = base.displayMediumEmphasized.transit(display, FontWeight.Black, (-0.016).em),
        displaySmallEmphasized = base.displaySmallEmphasized.transit(display, FontWeight.Black, (-0.012).em),
        headlineLargeEmphasized = base.headlineLargeEmphasized.transit(display, FontWeight.Black, (-0.012).em),
        headlineMediumEmphasized = base.headlineMediumEmphasized.transit(display, FontWeight.Black, (-0.008).em),
        headlineSmallEmphasized = base.headlineSmallEmphasized.transit(display, FontWeight.Black, (-0.004).em),
        titleLargeEmphasized = base.titleLargeEmphasized.transit(display, FontWeight.Black, (-0.004).em),
        titleMediumEmphasized = base.titleMediumEmphasized.transit(display, FontWeight.ExtraBold),
        titleSmallEmphasized = base.titleSmallEmphasized.transit(display, FontWeight.ExtraBold),
        bodyLargeEmphasized = base.bodyLargeEmphasized.transit(ui, FontWeight.Bold),
        bodyMediumEmphasized = base.bodyMediumEmphasized.transit(ui, FontWeight.Bold),
        bodySmallEmphasized = base.bodySmallEmphasized.transit(ui, FontWeight.Bold),
        labelLargeEmphasized = base.labelLargeEmphasized.transit(display, FontWeight.ExtraBold, 0.005.em),
        labelMediumEmphasized = base.labelMediumEmphasized.transit(display, FontWeight.ExtraBold, 0.01.em),
        labelSmallEmphasized = base.labelSmallEmphasized.transit(ui, FontWeight.Bold),
    )
}

/** Artifact radii: 4 / 6 / 10 / 14 / 22 (signage plates, not pills), so buttons and cards read as enamel signs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val TransitShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
    largeIncreased = RoundedCornerShape(18.dp),
    extraLargeIncreased = RoundedCornerShape(26.dp),
    extraExtraLarge = RoundedCornerShape(36.dp),
)
