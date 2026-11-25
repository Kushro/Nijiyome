// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Applies a dynamic horizontal alpha fade (using [CompositingStrategy.Offscreen] + [BlendMode.DstIn])
 * only on the left and/or right edges where more scrollable content exists. Works transparently over
 * any background (Light, Dark, AMOLED, or translucent banners) without painted solid-color gradients.
 */
fun Modifier.horizontalFadingEdge(
    scrollState: ScrollState,
    edgeWidth: Dp = 28.dp,
): Modifier = composed {
    val showLeft by remember(scrollState) { derivedStateOf { scrollState.value > 2 } }
    val showRight by remember(scrollState) {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue - 2 }
    }
    fadingEdgeLayer(showLeft = showLeft, showRight = showRight, edgeWidth = edgeWidth)
}

/**
 * [LazyListState] counterpart of [horizontalFadingEdge]: fades whichever horizontal edge still has
 * unrevealed items or clipped content.
 */
fun Modifier.horizontalFadingEdge(
    lazyListState: LazyListState,
    edgeWidth: Dp = 28.dp,
): Modifier = composed {
    val showLeft by remember(lazyListState) { derivedStateOf { lazyListState.canScrollBackward } }
    val showRight by remember(lazyListState) { derivedStateOf { lazyListState.canScrollForward } }
    fadingEdgeLayer(showLeft = showLeft, showRight = showRight, edgeWidth = edgeWidth)
}

private fun Modifier.fadingEdgeLayer(
    showLeft: Boolean,
    showRight: Boolean,
    edgeWidth: Dp,
): Modifier {
    if (!showLeft && !showRight) return this
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val edgePx = edgeWidth.toPx().coerceAtMost(size.width / 3f)
            if (edgePx <= 0f) return@drawWithContent
            if (showLeft) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startX = 0f,
                        endX = edgePx,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(edgePx, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (showRight) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - edgePx,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - edgePx, 0f),
                    size = Size(edgePx, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

/**
 * Wraps a horizontal scrollable strip (`Row` with [ScrollState]) with subtle edge-chevron buttons
 * when [showChevrons] is true (e.g., Desktop / wide layouts) and more content exists off-screen.
 */
@Composable
fun HorizontalScrollStrip(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    showChevrons: Boolean = false,
    stepPx: Int = 260,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canLeft by remember(scrollState) { derivedStateOf { scrollState.value > 4 } }
    val canRight by remember(scrollState) {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue - 4 }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        content()
        if (showChevrons && canLeft) {
            ScrollChevronButton(
                left = true,
                onClick = { scope.launch { scrollState.animateScrollTo((scrollState.value - stepPx).coerceAtLeast(0)) } },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
            )
        }
        if (showChevrons && canRight) {
            ScrollChevronButton(
                left = false,
                onClick = { scope.launch { scrollState.animateScrollTo((scrollState.value + stepPx).coerceAtMost(scrollState.maxValue)) } },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun ScrollChevronButton(
    left: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(28.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (left) Icons.AutoMirrored.Outlined.KeyboardArrowLeft else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = if (left) "Desplazar a la izquierda" else "Desplazar a la derecha",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
