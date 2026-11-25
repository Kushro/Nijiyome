// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.designsystem.direction.sumi

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_sumi_yuji_boku
import app.nijiyome.designsystem.resources.rd_sumi_zen_kaku_bold
import app.nijiyome.designsystem.resources.rd_sumi_zen_kaku_medium
import app.nijiyome.designsystem.resources.rd_sumi_zen_kaku_regular
import org.jetbrains.compose.resources.Font

// Type and shape of Sumi y hanko. Zen Kaku Gothic New is the UI face (bold kanji stay legible at 12 sp inside small seals);
// Yuji Boku, a brush face, is the display face of page titles, hero titles and large seals. Both files are subsets (Latin,
// Latin-1, Latin Extended-A, punctuation and the seal kanji); Japanese titles fall back to the system font.

/** Zen Kaku Gothic New, three static weights. */
@Composable
internal fun sumiUiFamily(): FontFamily = FontFamily(
    Font(Res.font.rd_sumi_zen_kaku_regular, FontWeight.Normal),
    Font(Res.font.rd_sumi_zen_kaku_medium, FontWeight.Medium),
    Font(Res.font.rd_sumi_zen_kaku_bold, FontWeight.Bold),
)

/** Yuji Boku (one weight; never synthesised bolder). */
@Composable
internal fun sumiBrushFamily(): FontFamily = FontFamily(Font(Res.font.rd_sumi_yuji_boku, FontWeight.Normal))

private fun TextStyle.withUiFace(family: FontFamily, weight: FontWeight): TextStyle = copy(fontFamily = family, fontWeight = weight)

/** Brush display: weight 400 and no tracking (artifact `.nj-h1`: `font-weight: 400; letter-spacing: 0; font-synthesis: none`). */
private fun TextStyle.withBrushFace(family: FontFamily): TextStyle =
    copy(fontFamily = family, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeight = fontSize * 1.18f)

/**
 * The scale of [base] with Sumi's faces: displays and the two larger headlines in the brush, everything else in Zen Kaku
 * (titles and labels bold like `.nj-h2`, `.nj-btn` and the card titles; body regular).
 */
internal fun sumiTypography(base: Typography, ui: FontFamily, brush: FontFamily): Typography = Typography(
    displayLarge = base.displayLarge.withBrushFace(brush),
    displayMedium = base.displayMedium.withBrushFace(brush),
    displaySmall = base.displaySmall.withBrushFace(brush),
    headlineLarge = base.headlineLarge.withBrushFace(brush),
    headlineMedium = base.headlineMedium.withBrushFace(brush),
    headlineSmall = base.headlineSmall.withUiFace(ui, FontWeight.Bold),
    titleLarge = base.titleLarge.withUiFace(ui, FontWeight.Bold),
    titleMedium = base.titleMedium.withUiFace(ui, FontWeight.Bold),
    titleSmall = base.titleSmall.withUiFace(ui, FontWeight.Bold),
    bodyLarge = base.bodyLarge.withUiFace(ui, FontWeight.Normal),
    bodyMedium = base.bodyMedium.withUiFace(ui, FontWeight.Normal),
    bodySmall = base.bodySmall.withUiFace(ui, FontWeight.Normal),
    labelLarge = base.labelLarge.withUiFace(ui, FontWeight.Bold),
    labelMedium = base.labelMedium.withUiFace(ui, FontWeight.Bold),
    labelSmall = base.labelSmall.withUiFace(ui, FontWeight.Medium),
    displayLargeEmphasized = base.displayLargeEmphasized.withBrushFace(brush),
    displayMediumEmphasized = base.displayMediumEmphasized.withBrushFace(brush),
    displaySmallEmphasized = base.displaySmallEmphasized.withBrushFace(brush),
    headlineLargeEmphasized = base.headlineLargeEmphasized.withBrushFace(brush),
    headlineMediumEmphasized = base.headlineMediumEmphasized.withBrushFace(brush),
    headlineSmallEmphasized = base.headlineSmallEmphasized.withUiFace(ui, FontWeight.Bold),
    titleLargeEmphasized = base.titleLargeEmphasized.withUiFace(ui, FontWeight.Bold),
    titleMediumEmphasized = base.titleMediumEmphasized.withUiFace(ui, FontWeight.Bold),
    titleSmallEmphasized = base.titleSmallEmphasized.withUiFace(ui, FontWeight.Bold),
    bodyLargeEmphasized = base.bodyLargeEmphasized.withUiFace(ui, FontWeight.Medium),
    bodyMediumEmphasized = base.bodyMediumEmphasized.withUiFace(ui, FontWeight.Medium),
    bodySmallEmphasized = base.bodySmallEmphasized.withUiFace(ui, FontWeight.Medium),
    labelLargeEmphasized = base.labelLargeEmphasized.withUiFace(ui, FontWeight.Bold),
    labelMediumEmphasized = base.labelMediumEmphasized.withUiFace(ui, FontWeight.Bold),
    labelSmallEmphasized = base.labelSmallEmphasized.withUiFace(ui, FontWeight.Bold),
)

/** Radii of the artifact (`--nj-r-xs` 3, s 6, m 10, l 14, xl 22): tighter than Material, like cut paper. */
internal val SumiShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
    largeIncreased = RoundedCornerShape(18.dp),
    extraLargeIncreased = RoundedCornerShape(26.dp),
    extraExtraLarge = RoundedCornerShape(36.dp),
)
