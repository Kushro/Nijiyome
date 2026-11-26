// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.designsystem.direction.tanabata

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DirectionDecor
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_tanabata_name
import app.nijiyome.designsystem.resources.rd_tanabata_tagline
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign. Owner: lane D1. The "tanabata" direction of docs/redesign/artefacto/dir-tanabata.{css,js}: indigo night floor,
// washi paper in tanzaku colors as the list statuses, lantern amber only for focus and "now", bamboo hairlines as rails.

/** Tanabata, a wish night: what you want to watch hangs like tanzaku from a bamboo rod; the rest is a clean night-time tool. */
object TanabataDirection : DirectionSpec {
    override val id: String = "tanabata"
    override val track: DirectionTrack = DirectionTrack.FREE
    override val palettes: List<DirectionPalette> = TanabataPalettes.all

    @Composable
    override fun name(): String = stringResource(Res.string.rd_tanabata_name)

    @Composable
    override fun tagline(): String = stringResource(Res.string.rd_tanabata_tagline)

    @Composable
    override fun typography(base: Typography): Typography {
        val rounded = tanabataRounded()
        return remember(base, rounded) { base.tanabata(rounded) }
    }

    @Composable
    override fun accentFont(): FontFamily? = tanabataBrush()

    /**
     * Artifact radii: s 6, m 10, l 14, xl 22 (paper is nearly square, controls softly rounded). extraSmall is 8 rather than the
     * artifact's 3: Material text fields, menus and snackbars read it, and the artifact rounds its fields at 10 (`.nj-field`), so 3
     * drew sharp-cornered fields. Paper and badges use their own 2-3 dp shapes, not this slot.
     */
    override val shapes: Shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(22.dp),
        largeIncreased = RoundedCornerShape(18.dp),
        extraLargeIncreased = RoundedCornerShape(26.dp),
        extraExtraLarge = RoundedCornerShape(36.dp),
    )

    override val decor: DirectionDecor get() = TanabataDecor
}
