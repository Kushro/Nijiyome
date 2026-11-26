// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.designsystem.direction.obi

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DirectionDecor
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_obi_name
import app.nijiyome.designsystem.resources.rd_obi_tagline
import org.jetbrains.compose.resources.stringResource

/**
 * Obi (lane D2, docs/redesign/artefacto/dir-obi.{css,js}): every series gets its obi, the paper band with a hook line that wraps
 * the foot of a light novel or Blu-ray cover. The band is the status language (series color while you follow it, green when
 * completed, faded when paused, neutral when dropped); everything else is a clean tool in ink and shop paper, with small paper
 * radii, Dela Gothic One on the bands and headings and bold M PLUS 1 for the rest. Lists can stand as spines on a shelf.
 */
object ObiDirection : DirectionSpec {
    override val id: String = "obi"
    override val track: DirectionTrack = DirectionTrack.FREE
    override val palettes: List<DirectionPalette> = ObiPalettes.all

    @Composable
    override fun name(): String = stringResource(Res.string.rd_obi_name)

    @Composable
    override fun tagline(): String = stringResource(Res.string.rd_obi_tagline)

    @Composable
    override fun typography(base: Typography): Typography = obiTypography(base)

    /** `--nj-font-accent`: Dela Gothic One. */
    @Composable
    override fun accentFont(): FontFamily = ObiFonts.band()

    /** Paper radii (`--nj-r-xs` 2 … `--nj-r-xl` 16). */
    override val shapes: Shapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(16.dp),
        largeIncreased = RoundedCornerShape(12.dp),
        extraLargeIncreased = RoundedCornerShape(20.dp),
        extraExtraLarge = RoundedCornerShape(24.dp),
    )

    override val decor: DirectionDecor = ObiDecor
}
