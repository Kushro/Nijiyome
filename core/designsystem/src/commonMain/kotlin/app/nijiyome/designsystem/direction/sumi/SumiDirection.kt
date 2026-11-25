// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.sumi

import androidx.compose.runtime.Composable
import app.nijiyome.designsystem.direction.CanonDirection
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack

// Sparkles redesign. Owner: lane D4. STUB written by the integrator so the registry compiles: replace the whole body
// (palettes, type, shapes, decor) with the "sumi" direction of docs/redesign/artefacto/dir-sumi.{css,js}. Keep the object name and id.
object SumiDirection : DirectionSpec {
    override val id: String = "sumi"
    override val track: DirectionTrack = DirectionTrack.IDENTITY
    override val palettes: List<DirectionPalette> get() = CanonDirection.palettes

    @Composable
    override fun name(): String = "Sumi"

    @Composable
    override fun tagline(): String = ""
}
