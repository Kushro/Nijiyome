// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.designsystem.motion.MotionExtra
import app.nijiyome.designsystem.motion.motionExtraOn

// [MotionExtra.SCROLLBARS]: a thin vertical bar drawn over the end edge of the container (desktop only). It is a modifier, not a
// sibling in a Box, so wrapping a list keeps its layout, semantics and test tags. Put it outside the scrollable (before
// `verticalScroll`, or in a lazy list's modifier) so it draws over the viewport, not over the scrolled content.

/** Overlay scrollbar for a `verticalScroll` [state]. */
@Composable
fun Modifier.overlayScrollbar(state: ScrollState): Modifier =
    if (motionExtraOn(MotionExtra.SCROLLBARS)) overlayScrollbarPlatform(state) else this

/** Overlay scrollbar for a vertical lazy list. */
@Composable
fun Modifier.overlayScrollbar(state: LazyListState): Modifier =
    if (motionExtraOn(MotionExtra.SCROLLBARS)) overlayScrollbarPlatform(state) else this

/** Overlay scrollbar for a vertical lazy grid. */
@Composable
fun Modifier.overlayScrollbar(state: LazyGridState): Modifier =
    if (motionExtraOn(MotionExtra.SCROLLBARS)) overlayScrollbarPlatform(state) else this

@Composable
internal expect fun Modifier.overlayScrollbarPlatform(state: ScrollState): Modifier

@Composable
internal expect fun Modifier.overlayScrollbarPlatform(state: LazyListState): Modifier

@Composable
internal expect fun Modifier.overlayScrollbarPlatform(state: LazyGridState): Modifier
