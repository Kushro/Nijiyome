// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.designsystem.direction.tanabata

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_tanabata_mplus_bold
import app.nijiyome.designsystem.resources.rd_tanabata_mplus_extrabold
import app.nijiyome.designsystem.resources.rd_tanabata_mplus_medium
import app.nijiyome.designsystem.resources.rd_tanabata_mplus_regular
import app.nijiyome.designsystem.resources.rd_tanabata_yuji_syuku
import org.jetbrains.compose.resources.Font

// Fonts: Latin subsets (pyftsubset) of M PLUS Rounded 1c and Yuji Syuku from github.com/google/fonts, SIL OFL 1.1. Japanese titles
// fall back to the system face.

/** M PLUS Rounded 1c: the UI and display face (artifact `--nj-font-ui`, `--nj-font-display`). */
@Composable
internal fun tanabataRounded(): FontFamily = FontFamily(
    Font(Res.font.rd_tanabata_mplus_regular, weight = FontWeight.Normal),
    Font(Res.font.rd_tanabata_mplus_medium, weight = FontWeight.Medium),
    Font(Res.font.rd_tanabata_mplus_bold, weight = FontWeight.Bold),
    Font(Res.font.rd_tanabata_mplus_extrabold, weight = FontWeight.ExtraBold),
)

/** Yuji Syuku, the brush voice of a few short wish titles from 20 sp up (artifact `--nj-font-accent`); never for data or controls. */
@Composable
internal fun tanabataBrush(): FontFamily = FontFamily(Font(Res.font.rd_tanabata_yuji_syuku, weight = FontWeight.Normal))

private fun TextStyle.with(family: FontFamily, weight: FontWeight): TextStyle = copy(fontFamily = family, fontWeight = weight)

/**
 * The type scale of Tanabata on the app's [base] sizes: rounded face everywhere; headings 700 (800 emphasized), labels 700 because
 * buttons, chips, tabs and rail items are bold in the artifact, body 400 (500 emphasized).
 */
internal fun Typography.tanabata(f: FontFamily): Typography {
    val regular = FontWeight.Normal
    val medium = FontWeight.Medium
    val bold = FontWeight.Bold
    val heavy = FontWeight.ExtraBold
    return copy(
        displayLarge = displayLarge.with(f, bold),
        displayMedium = displayMedium.with(f, bold),
        displaySmall = displaySmall.with(f, bold),
        headlineLarge = headlineLarge.with(f, bold),
        headlineMedium = headlineMedium.with(f, bold),
        headlineSmall = headlineSmall.with(f, bold),
        titleLarge = titleLarge.with(f, bold),
        titleMedium = titleMedium.with(f, bold),
        titleSmall = titleSmall.with(f, bold),
        bodyLarge = bodyLarge.with(f, regular),
        bodyMedium = bodyMedium.with(f, regular),
        bodySmall = bodySmall.with(f, regular),
        labelLarge = labelLarge.with(f, bold),
        labelMedium = labelMedium.with(f, bold),
        labelSmall = labelSmall.with(f, medium),
        displayLargeEmphasized = displayLargeEmphasized.with(f, heavy),
        displayMediumEmphasized = displayMediumEmphasized.with(f, heavy),
        displaySmallEmphasized = displaySmallEmphasized.with(f, heavy),
        headlineLargeEmphasized = headlineLargeEmphasized.with(f, heavy),
        headlineMediumEmphasized = headlineMediumEmphasized.with(f, heavy),
        headlineSmallEmphasized = headlineSmallEmphasized.with(f, heavy),
        titleLargeEmphasized = titleLargeEmphasized.with(f, heavy),
        titleMediumEmphasized = titleMediumEmphasized.with(f, heavy),
        titleSmallEmphasized = titleSmallEmphasized.with(f, heavy),
        bodyLargeEmphasized = bodyLargeEmphasized.with(f, medium),
        bodyMediumEmphasized = bodyMediumEmphasized.with(f, medium),
        bodySmallEmphasized = bodySmallEmphasized.with(f, medium),
        labelLargeEmphasized = labelLargeEmphasized.with(f, heavy),
        labelMediumEmphasized = labelMediumEmphasized.with(f, heavy),
        labelSmallEmphasized = labelSmallEmphasized.with(f, bold),
    )
}
