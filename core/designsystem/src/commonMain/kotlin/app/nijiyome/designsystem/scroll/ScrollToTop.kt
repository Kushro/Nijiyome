// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.LocalScrollToTopRequests
import app.nijiyome.designsystem.motion.MotionEasing
import app.nijiyome.designsystem.motion.MotionExtra
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.motion.motionExtraOn
import kotlinx.coroutines.flow.collectLatest

// [MotionExtra.TAP_TO_TOP]: the shell emits on [LocalScrollToTopRequests] when the current destination is tapped again; the
// screen's primary vertical container answers. Full motion: a spring (lazy lists: their own animateScrollToItem); calm and
// performance: a 250 ms tween; off: a jump.

private const val CALM_MS = 250

/** Lazy containers past this many items jump closer first, so a tween never has to compose hundreds of rows on the way. */
private const val NEAR_ITEMS = 12

/** Runs [toTop] with the motion level each time the shell asks, while [enabled] and the extra is on. */
@Composable
fun ScrollToTopEffect(enabled: Boolean = true, toTop: suspend (MotionLevel) -> Unit) {
    if (!enabled || !motionExtraOn(MotionExtra.TAP_TO_TOP)) return
    val requests = LocalScrollToTopRequests.current
    val level = LocalMotion.current.level
    val latest by rememberUpdatedState(toTop)
    LaunchedEffect(requests, level) { requests.collectLatest { latest(level) } }
}

/** [ScrollState] to the top for [level]. */
suspend fun ScrollState.scrollToTop(level: MotionLevel) {
    when (level) {
        MotionLevel.OFF -> scrollTo(0)
        MotionLevel.FULL -> animateScrollTo(0, spring(dampingRatio = 1f, stiffness = 250f))
        else -> animateScrollTo(0, tween(CALM_MS, easing = MotionEasing.ExitExponential))
    }
}

/** [LazyListState] to the first item for [level]. */
suspend fun LazyListState.scrollToTop(level: MotionLevel) {
    when (level) {
        MotionLevel.OFF -> scrollToItem(0)
        MotionLevel.FULL -> animateScrollToItem(0)
        else -> {
            if (firstVisibleItemIndex > NEAR_ITEMS) scrollToItem(NEAR_ITEMS)
            val items = layoutInfo.visibleItemsInfo
            // Distance estimate from the rows on screen; the final scrollToItem fixes whatever the estimate missed.
            val perItem = if (items.size > 1) (items.last().offset - items.first().offset).toFloat() / (items.size - 1) else 0f
            val distance = firstVisibleItemIndex * perItem + firstVisibleItemScrollOffset
            if (distance > 0f) animateScrollBy(-distance, tween(CALM_MS, easing = MotionEasing.ExitExponential))
            scrollToItem(0)
        }
    }
}

/** [LazyGridState] to the first item for [level]. */
suspend fun LazyGridState.scrollToTop(level: MotionLevel) {
    when (level) {
        MotionLevel.OFF -> scrollToItem(0)
        MotionLevel.FULL -> animateScrollToItem(0)
        else -> {
            if (firstVisibleItemIndex > NEAR_ITEMS) scrollToItem(NEAR_ITEMS)
            val items = layoutInfo.visibleItemsInfo
            val span = if (items.size > 1) items.last().index - items.first().index else 0
            val perItem = if (span > 0) (items.last().offset.y - items.first().offset.y).toFloat() / span else 0f
            val distance = firstVisibleItemIndex * perItem + firstVisibleItemScrollOffset
            if (distance > 0f) animateScrollBy(-distance, tween(CALM_MS, easing = MotionEasing.ExitExponential))
            scrollToItem(0)
        }
    }
}
