// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_m1_loading
import org.jetbrains.compose.resources.stringResource
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

// Moment "carga" (lab.js): finding sources can take seconds; the indicator should say what is happening, not only that something
// moves. Loops run in the draw phase only, and stop after 5 s ("nada se mueve en bucle más de 5 s sin que el usuario lo pida"):
// the placeholder then stays still.

private const val LOOP_CAP_MS = 5_000L
private const val SHIMMER_PASS_MS = 1_200L
private const val INK_BEAT_MS = 900L

/** One clock for every shimmer on screen, so neighbouring placeholders sweep together. */
private object ShimmerClock {
    var epochMs = -1L
}

@Composable
internal fun LoadingPlaceholderImpl(modifier: Modifier, shape: Shape, label: String?, progress: Float?) {
    val motion = LocalMotion.current
    val level = motion.level
    val variant = motion.variant(Moments.LOADING, "shimmer")
    val colors = MaterialTheme.colorScheme
    val base = colors.surfaceContainerHigh

    if (variant == "count" && (label != null || progress != null)) {
        CountPlaceholder(modifier, shape, base, label, progress, level)
        return
    }

    // -1 = still. Shimmer: position of the pass (0..1). Ink: phase of the beat (0..1).
    val phase = remember { mutableFloatStateOf(-1f) }
    val looping = level == MotionLevel.FULL && (variant == "shimmer" || variant == "ink")
    LaunchedEffect(looping, variant) {
        if (!looping) {
            phase.floatValue = -1f
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        if (ShimmerClock.epochMs < 0) ShimmerClock.epochMs = start
        while (true) {
            val now = withFrameMillis { it }
            if (now - start > LOOP_CAP_MS) break
            phase.floatValue = if (variant == "ink") {
                ((now - start) % INK_BEAT_MS) / INK_BEAT_MS.toFloat()
            } else {
                (((now - ShimmerClock.epochMs) % SHIMMER_PASS_MS + SHIMMER_PASS_MS) % SHIMMER_PASS_MS) / SHIMMER_PASS_MS.toFloat()
            }
        }
        phase.floatValue = -1f
    }

    val highlight = colors.onSurface.copy(alpha = 0.10f)
    val ink = colors.primary
    val inkRadius = with(LocalDensity.current) { 60.dp.toPx() }
    // Ink in calm / performance motion: a still block that says it is loading, when there is room for the words.
    val words = if (variant == "ink" && !looping) stringResource(Res.string.rd_m1_loading) else null
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant)

    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val layout = words?.let { measurer.measure(it, textStyle, maxLines = 1) }
                val fits = layout != null && layout.size.width <= size.width - 16f && layout.size.height <= size.height
                onDrawBehind {
                    drawRect(base)
                    val p = phase.floatValue
                    if (p >= 0f && variant == "shimmer") {
                        // A band 60 % as wide as the block, travelling from one width left of it to one width right.
                        val centre = size.width * (p * 2f - 0.5f)
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to highlight,
                                1f to Color.Transparent,
                                startX = centre - size.width * 0.3f,
                                endX = centre + size.width * 0.3f,
                            ),
                        )
                    } else if (p >= 0f) {
                        val q = MotionEasing.Emphasized.transform(p)
                        val r = min(inkRadius, size.minDimension / 2f) * q
                        drawCircle(ink, radius = r, center = center, alpha = 0.55f * (1f - q))
                    }
                    if (fits && layout != null) {
                        drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f))
                    }
                }
            },
    )
}

/** "Contador": how many sources answered, so a stuck one shows. The bar moves 200 ms per answer in full motion. */
@Composable
private fun CountPlaceholder(modifier: Modifier, shape: Shape, base: Color, label: String?, progress: Float?, level: MotionLevel) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(shape).background(base), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, textAlign = TextAlign.Center)
            }
            if (progress != null) {
                val target = progress.coerceIn(0f, 1f)
                val shown = remember { Animatable(target) }
                LaunchedEffect(target) {
                    if (level == MotionLevel.FULL) {
                        shown.animateTo(target, tween(MotionDurations.STATE, easing = MotionEasing.ExitExponential))
                    } else {
                        shown.snapTo(target)
                    }
                }
                val fill = colors.primary
                Box(
                    Modifier
                        .widthIn(max = 200.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .progressSemantics(target)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.surfaceContainerHighest)
                        .drawBehind { drawRect(fill, size = Size(size.width * shown.value, size.height)) },
                )
            }
        }
    }
}

/**
 * Put on the content that replaces a [LoadingPlaceholder], so the arrival matches the "carga" variant: shimmer and count fade
 * in for 150 ms; ink opens in a circle from the centre (500 ms, full motion only). Performance and off motion cut directly.
 * Plays once, when the content first composes.
 */
@Composable
fun Modifier.loadedReveal(): Modifier {
    val motion = LocalMotion.current
    val level = motion.level
    if (level == MotionLevel.OFF || level == MotionLevel.PERF) return this
    val circle = level == MotionLevel.FULL && motion.variant(Moments.LOADING, "shimmer") == "ink"
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            if (circle) tween(MotionDurations.AUTHOR, easing = MotionEasing.Emphasized) else tween(MotionDurations.CALM_FADE, easing = LinearEasing),
        )
    }
    return this.graphicsLayer {
        val p = progress.value
        if (!circle) {
            alpha = p
        } else if (p < 1f) {
            clip = true
            // circle(75 %) of the artifact: 75 % of the half diagonal.
            shape = CircleWindow(0.75f * hypot(size.width, size.height) / sqrt(2f) * p)
        }
    }
}

private class CircleWindow(private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        return Outline.Rounded(RoundRect(cx - radius, cy - radius, cx + radius, cy + radius, radius, radius))
    }
}
