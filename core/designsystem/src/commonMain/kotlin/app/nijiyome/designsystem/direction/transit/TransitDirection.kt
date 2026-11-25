// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.transit

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import app.nijiyome.designsystem.direction.DirectionDecor
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_transit_name
import app.nijiyome.designsystem.resources.rd_transit_tagline
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane D3. Tránsito nocturno (docs/redesign/artefacto/dir-transit.{css,js}): metro signage on neutral graphite.
// Every series is a line in its own color, every episode a station with a numbered plate; the airing agenda is a departure board and
// Link is the transfer between the computer line and the phone line. The app stays an app: standard rails, lists and controls.

object TransitDirection : DirectionSpec {
    override val id: String = "transit"
    override val track: DirectionTrack = DirectionTrack.FREE
    override val palettes: List<DirectionPalette> = TransitPalettes.all

    @Composable
    override fun name(): String = stringResource(Res.string.rd_transit_name)

    @Composable
    override fun tagline(): String = stringResource(Res.string.rd_transit_tagline)

    @Composable
    override fun typography(base: Typography): Typography = transitTypography(base)

    @Composable
    override fun accentFont(): FontFamily? = transitDisplayFamily()

    override val shapes: Shapes get() = TransitShapes
    override val decor: DirectionDecor get() = TransitDecor
}
