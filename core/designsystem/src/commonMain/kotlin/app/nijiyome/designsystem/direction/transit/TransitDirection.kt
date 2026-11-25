// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.transit

import androidx.compose.runtime.Composable
import app.nijiyome.designsystem.direction.CanonDirection
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack

// Sparkles redesign. Owner: lane D3. STUB written by the integrator so the registry compiles: replace the whole body
// (palettes, type, shapes, decor) with the "transit" direction of docs/redesign/artefacto/dir-transit.{css,js}. Keep the object name and id.
object TransitDirection : DirectionSpec {
    override val id: String = "transit"
    override val track: DirectionTrack = DirectionTrack.FREE
    override val palettes: List<DirectionPalette> get() = CanonDirection.palettes

    @Composable
    override fun name(): String = "Transit"

    @Composable
    override fun tagline(): String = ""
}
