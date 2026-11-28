package app.nijiyome.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import app.nijiyome.designsystem.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/** Placeholder block with the final shape of the content (02 §5). Honours [LocalReduceMotion]. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    // The pulse is read in the layer, not here: reading it in composition recomposed every skeleton each frame.
    val pulse = if (LocalReduceMotion.current) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    }
    Box(
        modifier
            // Same layer as Modifier.alpha (it clips too).
            .graphicsLayer {
                alpha = pulse?.value ?: 0.65f
                clip = true
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** `true` once [delayMillis] elapsed; used to show skeletons only after 150 ms (02 §5). */
@Composable
fun rememberAfterDelay(delayMillis: Long = 150): Boolean {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        ready = true
    }
    return ready
}
