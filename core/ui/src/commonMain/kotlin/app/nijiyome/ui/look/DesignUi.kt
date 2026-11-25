// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.look

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.Directions
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.motion.MotionChoices
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.theme.LocalReduceMotion
import app.nijiyome.model.DesignDirection
import app.nijiyome.model.DesignPrefs
import app.nijiyome.model.ListStatus
import app.nijiyome.model.LookPrefs
import app.nijiyome.model.MotionProfile
import app.nijiyome.model.PerfMode
import app.nijiyome.model.ScreenStructures
import app.nijiyome.model.TitleCardIds

// Sparkles redesign contract (docs/redesign/CONTRATO-COMPOSE.md). Owner: integrator. FROZEN. Bridges between the stored choices
// (`LookPrefs.design`, :core:model) and the design system (:core:designsystem, which does not see the model).

/** The Sparkles choices of the user (`LocalLook.current.design`). */
val design: DesignPrefs
    @Composable get() = LocalLook.current.design

/** The structure the user picked for each screen. A screen reads its own field and switches on it at its top level only. */
val structures: ScreenStructures
    @Composable get() = LocalLook.current.design.structures

/** The direction object of the stored choice. The shell passes it to `NijiyomeTheme`. */
fun DesignPrefs.directionSpec(): DirectionSpec = Directions.byId(direction.id)

/**
 * The card style to draw: the picked one, else the direction's default. Under Canon with nothing picked, the previous
 * Diseño y estilo card style (classic / detailed / poster only) keeps working.
 */
fun LookPrefs.effectiveCardId(): String =
    if (design.card == null && design.direction == DesignDirection.CANON) TitleCardIds.ofCardStyle(cardStyle) else design.cardId()

/** [ListStatus] in the design system's words. */
fun ListStatus.slot(): StatusSlot = when (this) {
    ListStatus.WATCHING -> StatusSlot.WATCHING
    ListStatus.PLANNING -> StatusSlot.PLANNING
    ListStatus.COMPLETED -> StatusSlot.COMPLETED
    ListStatus.PAUSED -> StatusSlot.PAUSED
    ListStatus.DROPPED -> StatusSlot.DROPPED
    ListStatus.REPEATING -> StatusSlot.REWATCHING
}

/**
 * The effective motion choices for `LocalMotion`: OFF when motion is reduced (setting, system or Animaciones desactivadas, already
 * folded into [LocalReduceMotion]), PERF when performance mode is on or [lowEndDevice] and the mode is automatic, else the profile.
 * The shell provides it once; [lowEndDevice] comes from the platform (lane M1: low RAM or software rendering).
 */
@Composable
fun rememberMotionChoices(prefs: DesignPrefs, reduce: Boolean = LocalReduceMotion.current, lowEndDevice: Boolean = false): MotionChoices {
    return remember(prefs.motionProfile, prefs.perfMode, prefs.motionVariants, reduce, lowEndDevice) {
        val level = when {
            reduce -> MotionLevel.OFF
            prefs.perfMode == PerfMode.ON || (prefs.perfMode == PerfMode.AUTO && lowEndDevice) -> MotionLevel.PERF
            prefs.motionProfile == MotionProfile.PERF -> MotionLevel.PERF
            prefs.motionProfile == MotionProfile.CALM -> MotionLevel.CALM
            else -> MotionLevel.FULL
        }
        MotionChoices(level = level, variants = app.nijiyome.model.MotionMoments.VARIANTS.keys.associateWith { prefs.motionVariant(it) })
    }
}
