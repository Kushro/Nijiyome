// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.nijiyome.ui.format.formatDuration
import kotlin.math.roundToInt

// Hito 6 (pointer, Tatakai Next `player_controls.rs` hover / `.tk-pc__preview`): the time under the mouse in a bubble over the seek bar.

/** Where the pointer is over the seek bar ([fraction] 0..1, null = not over it) and the bar's size in pixels. */
@Stable
internal class SeekHoverState {
    var fraction by mutableStateOf<Float?>(null)
    var width by mutableIntStateOf(0)
    var height by mutableIntStateOf(0)
}

@Composable
internal fun rememberSeekHover(): SeekHoverState = remember { SeekHoverState() }

/** Follows the pointer over the bar into [state] when [enabled] (a touch screen never hovers; Android does not enable it). */
internal fun Modifier.seekHover(state: SeekHoverState, enabled: Boolean): Modifier {
    if (!enabled) return this
    return this
        .onSizeChanged {
            state.width = it.width
            state.height = it.height
        }
        .pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    state.fraction = when (event.type) {
                        PointerEventType.Exit -> null
                        PointerEventType.Enter, PointerEventType.Move ->
                            event.changes.firstOrNull()?.position?.x?.let { hoverFraction(it, size.width.toFloat()) }
                        else -> state.fraction
                    }
                }
            }
        }
}

/** The bubble, laid out right under the bar it belongs to and drawn above it (it takes no room). */
@Composable
internal fun SeekHoverBubble(
    state: SeekHoverState,
    durationMs: Long,
    /** Sparkles S5: what is at the hovered time (the chapter: "Opening"), shown after the time; null = the time only. */
    labelAt: ((timeMs: Long) -> String?)? = null,
) {
    val f = state.fraction ?: return
    val width = state.width
    if (width <= 0 || durationMs <= 0) return
    val timeMs = hoverTimeMs(f, durationMs)
    val text = listOfNotNull(formatDuration(timeMs / 1000), labelAt?.invoke(timeMs)).joinToString(" · ")
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(0, 0) {
                val x = (f * width - p.width / 2f).roundToInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                p.place(x, -state.height - p.height - BUBBLE_GAP_DP.dp.roundToPx())
            }
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** The bar fraction under [x] of a bar [width] wide, [inset] kept free at each end. */
internal fun hoverFraction(x: Float, width: Float, inset: Float = 0f): Float {
    val usable = width - 2 * inset
    if (usable <= 0f) return 0f
    return ((x - inset) / usable).coerceIn(0f, 1f)
}

/** The time at [fraction] of [durationMs]. */
internal fun hoverTimeMs(fraction: Float, durationMs: Long): Long = (fraction.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0)).toLong()

private const val BUBBLE_GAP_DP = 4
