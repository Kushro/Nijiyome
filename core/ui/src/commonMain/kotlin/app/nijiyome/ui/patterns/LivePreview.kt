// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.patterns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_sub_reset
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane I1. Pattern `sub` of docs/redesign/artefacto/lab.js: the control and its effect side by side. Each change
// moves the preview at once with the same computation the real feature uses; Reset leaves an undo. Wide windows: controls on the
// start side (scrolling) and the preview beside them; phones: the preview on top (fixed) and the controls below (scrolling).

/**
 * Two-pane settings scaffold with a live preview. The preview stays visible while the controls scroll. [caption] sits under the
 * preview ("Same units as mpv: pixels at 720 lines, scaled to the frame").
 *
 * ```kotlin
 * LivePreviewScaffold(preview = { SubtitlePreview(style) }, caption = unitsNote) {
 *     SliderRow(size, …); SliderRow(outline, …); ResetStyleButton(enabled = style != Default, onReset = { reset() })
 * }
 * ```
 *
 * [embedded] = false (default) is the full-screen form: it fills the available size and scrolls the controls itself. Pass true
 * inside a parent that already scrolls (a sheet, a scrolling column, a lazy list item): then it takes only the height of its
 * content and has no scroll of its own, so it never nests a scroll in the same direction nor asks for an unbounded height.
 *
 * ```kotlin
 * Column(Modifier.verticalScroll(rememberScrollState())) {
 *     LivePreviewScaffold(preview = { ReaderPagePreview(prefs) }, embedded = true) { … }
 * }
 * ```
 */
@Composable
fun LivePreviewScaffold(
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    stacked: Boolean = isCompactWindow(),
    controlsWidth: Dp = 360.dp,
    embedded: Boolean = false,
    controls: @Composable ColumnScope.() -> Unit,
) {
    val captionView: @Composable () -> Unit = {
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (embedded) {
        if (stacked) {
            Column(modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { preview() }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    controls()
                    captionView()
                }
            }
        } else {
            Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                Column(
                    Modifier.width(controlsWidth).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = controls,
                )
                Column(Modifier.weight(1f).padding(vertical = 8.dp), horizontalAlignment = Alignment.Start) {
                    preview()
                    captionView()
                }
            }
        }
    } else if (stacked) {
        Column(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { preview() }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                controls()
                captionView()
            }
        }
    } else {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.width(controlsWidth).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = controls,
            )
            Column(Modifier.weight(1f).padding(vertical = 8.dp), horizontalAlignment = Alignment.Start) {
                preview()
                captionView()
            }
        }
    }
}

/**
 * "Reset style" for a live-preview screen; disabled while nothing differs from the default. Pair it with an undo:
 *
 * ```kotlin
 * ResetStyleButton(enabled = style != Default) { val prev = style; vm.set(Default); undo.show(resetDone, undo = { vm.set(prev) }) }
 * ```
 */
@Composable
fun ResetStyleButton(enabled: Boolean, modifier: Modifier = Modifier, label: String = stringResource(Res.string.rd_i1_sub_reset), onReset: () -> Unit) {
    TextButton(onClick = onReset, enabled = enabled, modifier = modifier) {
        Icon(Icons.Outlined.Restore, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
