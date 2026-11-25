// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Moment "inicio" (lab.js): Home opens many times a day, so the entrance is short or absent, plays only the first time in a
// session and never on going back.

/**
 * Which Home sections still get their entrance. The window opens with the first section composed in this process; a section
 * plays once (going back to Home finds its index spent) and only inside the window (sections scrolled into view later, or a
 * Home opened again an hour later, just appear).
 */
internal object HomeEntranceSession {
    private val WINDOW = 4_000.milliseconds
    private var opened: TimeMark? = null
    private val played = HashSet<Int>()

    fun claim(index: Int): Boolean {
        val mark = opened ?: TimeSource.Monotonic.markNow().also { opened = it }
        if (mark.elapsedNow() > WINDOW) return false
        return played.add(index)
    }
}

private const val CASCADE_MS = 240
private const val CASCADE_STEP_MS = 30
private const val CASCADE_CAP_MS = 300
private const val ROWS_MS = MotionDurations.LAYOUT
private const val ROWS_STEP_MS = 80

@Composable
internal fun Modifier.homeEntranceMotion(index: Int): Modifier {
    val motion = LocalMotion.current
    val level = motion.level
    val variant = motion.variant(Moments.HOME_ENTRANCE, "cascade")
    // Decided once: a recomposition or a level change never replays or cuts it.
    val plays = remember { level != MotionLevel.OFF && HomeEntranceSession.claim(index) }
    if (!plays) return this

    // What this section does at this level; null = it just appears.
    val kind: EntranceKind? = when (level) {
        MotionLevel.FULL -> when (variant) {
            "rows" -> EntranceKind.Reveal
            "none" -> EntranceKind.Fade(MotionDurations.CALM_FADE)
            else -> EntranceKind.Rise
        }
        // "Un solo fundido de 150 ms de toda la pantalla": every section at once, no delay.
        MotionLevel.CALM -> EntranceKind.Fade(MotionDurations.CALM_FADE)
        MotionLevel.PERF -> if (variant == "rows") null else EntranceKind.Fade(MotionDurations.INSTANT)
        MotionLevel.OFF -> null
    }
    if (kind == null) return this

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        when (kind) {
            EntranceKind.Rise -> {
                delay(minOf(index * CASCADE_STEP_MS, CASCADE_CAP_MS).toLong())
                progress.animateTo(1f, tween(CASCADE_MS, easing = MotionEasing.ExitExponential))
            }
            EntranceKind.Reveal -> {
                delay(minOf(index * ROWS_STEP_MS, CASCADE_CAP_MS).toLong())
                progress.animateTo(1f, tween(ROWS_MS, easing = MotionEasing.Emphasized))
            }
            is EntranceKind.Fade -> progress.animateTo(1f, tween(kind.durationMs, easing = LinearEasing))
        }
    }
    return when (kind) {
        EntranceKind.Rise -> {
            val rise = with(LocalDensity.current) { 8.dp.toPx() }
            this.graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * rise
            }
        }
        // Reveals left to right, following reading order.
        EntranceKind.Reveal -> this.drawWithContent {
            val p = progress.value
            if (p >= 1f) {
                drawContent()
            } else {
                clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
            }
        }
        is EntranceKind.Fade -> this.graphicsLayer { alpha = progress.value }
    }
}

private sealed interface EntranceKind {
    data object Rise : EntranceKind
    data object Reveal : EntranceKind
    data class Fade(val durationMs: Int) : EntranceKind
}
