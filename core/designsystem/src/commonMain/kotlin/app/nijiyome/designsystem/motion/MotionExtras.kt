// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * v1.3.5: the thirteen extra animations the owner chose from docs/redesign/movimiento (all of them, each one a switch in
 * Ajustes › Apariencia › Movimiento, all on by default). An extra still obeys [LocalMotion]: it is the user's "I want this one",
 * the level decides how much of it runs (reduce-motion and OFF always win).
 */
object MotionExtra {
    /** 1 · Desktop mouse wheel: ~100 dp per notch, a spring that keeps its speed when notches chain. Touchpads untouched. */
    const val SMOOTH_WHEEL = "rueda"

    /** 2 · Desktop: thin scrollbars that show while scrolling and widen under the pointer. */
    const val SCROLLBARS = "barras"

    /** 3 · Arrow buttons on card rows (on hover), paging with an animated scroll. */
    const val ROW_ARROWS = "flechas"

    /** 4 · Images fade in when they load. */
    const val IMAGE_FADE = "imagenes"

    /** 5 · Loaded content replaces its placeholder softly (fade, short rise). */
    const val CONTENT_REVEAL = "contenido"

    /** 6 · List items glide to their new place on filter / insert / remove. */
    const val LIST_PLACEMENT = "listas"

    /** 7 · Tab content changes with a short slide and fade. */
    const val TAB_SLIDE = "pestanas"

    /** 8 · Top-level destinations change with a fade-through. */
    const val FADE_THROUGH = "secciones"

    /** 9 · Expand / collapse animates its height. */
    const val EXPAND = "expandir"

    /** 10 · The rail's selection pill slides between items. */
    const val RAIL_INDICATOR = "indicador"

    /** 11 · Material / Canon cards lift on hover. */
    const val HOVER_LIFT = "elevacion"

    /** 12 · Phone: headers hide while scrolling down and come back on scroll up. */
    const val HIDE_HEADER = "encabezados"

    /** 13 · Tapping the selected rail / bar item again scrolls its screen to the top. */
    const val TAP_TO_TOP = "subir"

    /** In the settings order (the numbers of the proposal page). */
    val ALL: List<String> = listOf(
        SMOOTH_WHEEL, SCROLLBARS, ROW_ARROWS, IMAGE_FADE, CONTENT_REVEAL, LIST_PLACEMENT, TAB_SLIDE, FADE_THROUGH, EXPAND,
        RAIL_INDICATOR, HOVER_LIFT, HIDE_HEADER, TAP_TO_TOP,
    )
}

/** The extras the user turned off ([MotionExtra] ids); provided by the shell from DesignPrefs.motionExtrasOff. */
val LocalMotionExtrasOff: ProvidableCompositionLocal<Set<String>> = staticCompositionLocalOf { emptySet() }

/** Whether the user wants the extra animation [id]; callers still scale it by [LocalMotion]. */
@Composable
fun motionExtraOn(id: String): Boolean = id !in LocalMotionExtrasOff.current

/**
 * [MotionExtra.TAP_TO_TOP] contract (v1.3.5): the shell emits here when the user taps the destination they are already on, scoped to
 * that destination's screen; the screen's primary vertical scroll container collects it and scrolls to the top (animated by the
 * motion level, instant under OFF). Never emits when the extra is off.
 */
val LocalScrollToTopRequests: ProvidableCompositionLocal<kotlinx.coroutines.flow.Flow<Unit>> =
    staticCompositionLocalOf { kotlinx.coroutines.flow.emptyFlow() }
