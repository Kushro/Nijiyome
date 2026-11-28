// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.horizontalFadingEdge
import app.nijiyome.designsystem.components.sheetHostWidthDp
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionEasing
import app.nijiyome.designsystem.motion.MotionExtra
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.motion.motionExtraOn
import app.nijiyome.designsystem.motion.motionFadeIn
import app.nijiyome.designsystem.motion.motionFadeOut
import app.nijiyome.designsystem.motion.tweenFor
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_scroll_back
import app.nijiyome.designsystem.resources.rd_scroll_forward
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** A page is most of the viewport, so the last card of one page stays in view as the first of the next. */
private const val PAGE_FRACTION = 0.8f

/** Android windows this wide (tablets) show the arrows without hover. */
private const val WIDE_WINDOW_DP = 840

/**
 * [MotionExtra.ROW_ARROWS]: round arrow buttons at both ends of a horizontal card row, each hidden at its own end, paging by
 * 80 % of the viewport with an emphasized 400 ms scroll (calm: 150 ms, off: a jump). Desktop: while the pointer is over the row;
 * Android: on wide windows. The edges that still hide cards fade ([horizontalFadingEdge]; skipped in performance motion).
 * [content] gets the modifier for the row itself (the fade); the box takes [modifier]. With the extra off this is a plain box.
 */
@Composable
fun RowPagingArrows(
    state: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable (rowModifier: Modifier) -> Unit,
) {
    val on = motionExtraOn(MotionExtra.ROW_ARROWS)
    val level = LocalMotion.current.level
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wide = !desktopPlatform && sheetHostWidthDp() >= WIDE_WINDOW_DP
    // propagateMinConstraints: the row inside measures exactly as it would without the box.
    Box(modifier.hoverable(interaction, enabled = on), propagateMinConstraints = true) {
        content(if (on && level != MotionLevel.PERF) Modifier.horizontalFadingEdge(state) else Modifier)
        if (on) {
            val show = hovered || wide
            val canBack by remember(state) { derivedStateOf { state.canScrollBackward } }
            val canForward by remember(state) { derivedStateOf { state.canScrollForward } }
            val scope = rememberCoroutineScope()
            val page: (Int) -> Unit = { direction ->
                scope.launch {
                    val distance = state.layoutInfo.viewportSize.width * PAGE_FRACTION * direction
                    state.animateScrollBy(distance, level.tweenFor(400, MotionEasing.Emphasized))
                }
            }
            Box(Modifier.matchParentSize()) {
                AnimatedVisibility(
                    visible = show && canBack,
                    enter = motionFadeIn(),
                    exit = motionFadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
                ) { ArrowButton(left = true, onClick = { page(-1) }) }
                AnimatedVisibility(
                    visible = show && canForward,
                    enter = motionFadeIn(),
                    exit = motionFadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                ) { ArrowButton(left = false, onClick = { page(1) }) }
            }
        }
    }
}

@Composable
private fun ArrowButton(left: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (left) Icons.AutoMirrored.Outlined.KeyboardArrowLeft else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(if (left) Res.string.rd_scroll_back else Res.string.rd_scroll_forward),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
