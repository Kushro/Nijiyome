// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.tanabata

import androidx.compose.runtime.Composable
import app.nijiyome.designsystem.direction.CanonDirection
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack

// Sparkles redesign. Owner: lane D1. STUB written by the integrator so the registry compiles: replace the whole body
// (palettes, type, shapes, decor) with the "tanabata" direction of docs/redesign/artefacto/dir-tanabata.{css,js}. Keep the object name and id.
object TanabataDirection : DirectionSpec {
    override val id: String = "tanabata"
    override val track: DirectionTrack = DirectionTrack.FREE
    override val palettes: List<DirectionPalette> get() = CanonDirection.palettes

    @Composable
    override fun name(): String = "Tanabata"

    @Composable
    override fun tagline(): String = ""
}
