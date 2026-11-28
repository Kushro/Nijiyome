// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// v1.3.5 scroll containers: the Compose originals with the same parameters, plus the scrolling extras of the motion settings
// ([smoothWheel], [overlayScrollbar], [ScrollToTopEffect], [RowPagingArrows]). Each extra checks its own switch and the motion
// level, so swapping a container in changes nothing when the extras are off. [scrollToTopOnReselect]: only the screen's primary
// vertical container should answer the shell's "tap the current destination again" (pass false for side and nested columns).

/** [LazyColumn] with the wheel, the overlay scrollbar and tap-to-top. */
@Composable
fun NjLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    scrollToTopOnReselect: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    ScrollToTopEffect(scrollToTopOnReselect) { state.scrollToTop(it) }
    LazyColumn(
        // Outside the list's own scrollable: the wheel is seen first and the bar draws over the viewport.
        modifier = modifier.smoothWheel(state, Orientation.Vertical, reverseLayout, userScrollEnabled).overlayScrollbar(state),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = overscrollEffect,
        content = content,
    )
}

/**
 * [LazyRow] with Shift + wheel, and with [pagingArrows] the row arrows and edge fades of [RowPagingArrows] (that adds a box
 * around the row, which takes [modifier]).
 */
@Composable
fun NjLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    pagingArrows: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val row: @Composable (Modifier) -> Unit = { rowModifier ->
        LazyRow(
            modifier = rowModifier.smoothWheel(state, Orientation.Horizontal, reverseLayout, userScrollEnabled),
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            overscrollEffect = overscrollEffect,
            content = content,
        )
    }
    if (pagingArrows) RowPagingArrows(state, modifier) { fade -> row(fade) } else row(modifier)
}

/** [LazyVerticalGrid] with the wheel, the overlay scrollbar and tap-to-top. */
@Composable
fun NjLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    scrollToTopOnReselect: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    ScrollToTopEffect(scrollToTopOnReselect) { state.scrollToTop(it) }
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.smoothWheel(state, Orientation.Vertical, reverseLayout, userScrollEnabled).overlayScrollbar(state),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = overscrollEffect,
        content = content,
    )
}

/**
 * `verticalScroll` with the wheel, the overlay scrollbar and tap-to-top: a drop-in for `.verticalScroll(state)` at the same place
 * in the chain (whatever comes after it, such as padding, is still inside the scrolled content).
 */
@Composable
fun Modifier.njVerticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
    scrollToTopOnReselect: Boolean = true,
): Modifier {
    ScrollToTopEffect(scrollToTopOnReselect) { state.scrollToTop(it) }
    return this
        .smoothWheel(state, Orientation.Vertical, reverseScrolling, enabled)
        .overlayScrollbar(state)
        .verticalScroll(state, enabled, flingBehavior, reverseScrolling)
}
