// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.feature.details.resources.Res
import app.nijiyome.feature.details.resources.rd_s4_loading_cd
import app.nijiyome.feature.details.resources.rd_s4_loading_slow
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_back
import app.nijiyome.ui.resources.ui_retry
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * The Ficha while its title loads: the header's shape in placeholders (artwork, poster, title, facts, status, the first episodes)
 * instead of a blank page with a dot, the rate-limit banner when the metadata provider is cooling down, and after [SLOW_LOAD_MS] a
 * line that says it is slow with Reintentar and Volver. The same in every structure: none of them has anything to arrange yet.
 */
@Composable
internal fun FichaLoading(state: DetailsUiState, actions: DetailsActions, modifier: Modifier = Modifier) {
    val compact = isCompactWindow()
    var attempt by remember { mutableIntStateOf(0) }
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        slow = false
        delay(SLOW_LOAD_MS)
        slow = true
    }
    val label = stringResource(Res.string.rd_s4_loading_cd)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(if (compact) 176.dp else 220.dp).semantics { contentDescription = label }) {
            LoadingPlaceholder(Modifier.fillMaxSize(), shape = RectangleShape)
        }
        Column(Modifier.padding(horizontal = FichaGutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                LoadingPlaceholder(Modifier.width(if (compact) 96.dp else 120.dp).height(if (compact) 136.dp else 170.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LoadingPlaceholder(Modifier.fillMaxWidth(0.7f).height(32.dp))
                    LoadingPlaceholder(Modifier.fillMaxWidth(0.45f).height(18.dp))
                    LoadingPlaceholder(Modifier.width(180.dp).height(28.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                repeat(if (compact) 3 else 5) { LoadingPlaceholder(Modifier.width(88.dp).height(36.dp)) }
            }
            LoadingPlaceholder(Modifier.fillMaxWidth(if (compact) 1f else 0.8f).height(48.dp))
            if (state.fallback != null) {
                app.nijiyome.ui.state.MetadataFallbackBanner(state = state.fallback, onRetryPrimary = actions.onRetryPrimary)
            }
            if (slow) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(Res.string.rd_s4_loading_slow),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                attempt++
                                actions.onRetry()
                            }, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_retry)) }
                            TextButton(onClick = actions.onBack, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_back)) }
                        }
                    }
                }
            }
            repeat(4) { LoadingPlaceholder(Modifier.fillMaxWidth().height(64.dp)) }
        }
    }
}

/** After this long without the title, the loading page says so and offers Reintentar / Volver. */
private const val SLOW_LOAD_MS = 8_000L
