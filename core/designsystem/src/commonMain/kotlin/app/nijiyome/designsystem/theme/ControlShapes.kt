// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack
import app.nijiyome.designsystem.direction.obi.ObiDirection

/**
 * Shape of the common controls of the active direction: buttons and segmented controls (artifact `.nj-btn`, `.nj-seg`). Provided
 * by [NijiyomeTheme].
 *
 * Material 3 buttons and segmented buttons take a full pill from their own component tokens (`CornerFull`), not from
 * `MaterialTheme.shapes`, so a direction's [DirectionSpec.shapes] never reaches them. A control that should follow the direction
 * passes this shape explicitly: `Button(onClick, shape = NijiyomeControls.shape)`. Text fields and chips already follow
 * `MaterialTheme.shapes` (extraSmall and small).
 */
val LocalControlShape: ProvidableCompositionLocal<CornerBasedShape> = staticCompositionLocalOf { CircleShape }

object NijiyomeControls {
    /** Buttons and segmented controls: a pill under Canon, 10 dp under Tanabata / Tránsito / Sumi, a near-square 4 dp under Obi. */
    val shape: CornerBasedShape
        @Composable @ReadOnlyComposable
        get() = LocalControlShape.current
}

/** The artifact's control radius per direction: `--nj-r-pill` for Canon, `--nj-r-s` for Obi's printed labels, `--nj-r-m` otherwise. */
internal fun controlShapeOf(direction: DirectionSpec): CornerBasedShape = when {
    direction.track == DirectionTrack.CANON -> CircleShape
    direction === ObiDirection -> direction.shapes.small
    else -> direction.shapes.medium
}
