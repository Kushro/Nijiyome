// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Touch screens have no scrollbars in this app: the content itself is the handle.

@Composable
internal actual fun Modifier.overlayScrollbarPlatform(state: ScrollState): Modifier = this

@Composable
internal actual fun Modifier.overlayScrollbarPlatform(state: LazyListState): Modifier = this

@Composable
internal actual fun Modifier.overlayScrollbarPlatform(state: LazyGridState): Modifier = this
