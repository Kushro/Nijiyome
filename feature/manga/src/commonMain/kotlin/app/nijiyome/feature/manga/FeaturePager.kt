// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.theme.LocalReduceMotion
import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.rd_s11_feature_go_to
import app.nijiyome.feature.manga.resources.rd_s11_feature_page
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * MAN-01: the featured titles on a wide window, one at a time at the full width of the panel (the multi-browse carousel showed 1.7
 * of them with the second crushed into a strip). Swipe or the dots move between them; autoplay every 6 s unless reduce motion is on
 * or a drag is in progress, as the carousel did.
 */
@Composable
internal fun <T> FeaturePager(items: List<T>, key: (T) -> Any, modifier: Modifier = Modifier, page: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    val state = rememberPagerState(pageCount = { items.size })
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val dragging = state.isScrollInProgress
    val current = state.currentPage
    LaunchedEffect(reduceMotion, items.size, current, dragging) {
        if (reduceMotion || dragging || items.size < 2) return@LaunchedEffect
        delay(AUTOPLAY_MS)
        state.animateScrollToPage((state.currentPage + 1) % items.size)
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = state,
            // bounded like the carousel slide it replaces (a HERO card must not take an unbounded height)
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            key = { i -> items.getOrNull(i)?.let(key) ?: i },
            verticalAlignment = Alignment.Top,
        ) { i ->
            items.getOrNull(i)?.let { page(it) }
        }
        if (items.size > 1) {
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                val pageLabel = stringResource(Res.string.rd_s11_feature_page, current + 1, items.size)
                items.indices.forEach { i ->
                    val selected = i == current
                    val goTo = stringResource(Res.string.rd_s11_feature_go_to, i + 1)
                    val width by animateDpAsState(if (selected) 20.dp else 8.dp, label = "featureDot")
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { scope.launch { if (reduceMotion) state.scrollToPage(i) else state.animateScrollToPage(i) } }
                            .semantics { contentDescription = if (selected) pageLabel else goTo },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.width(width).height(8.dp).clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
        }
    }
}

private const val AUTOPLAY_MS = 6_000L
