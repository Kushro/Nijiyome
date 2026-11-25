// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.AdaptiveSheetScrollColumn
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LocalMotion
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// Controls of the novel reader and its settings in the Sparkles design (same shapes as the manga reader's; feature modules do not
// depend on each other): opaque bars, the reader's own panel, setting groups, switches, sliders and colour swatches.

@Composable
internal fun NovelBarSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (LocalMotion.current.rich) 2.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/**
 * The reader's panel for its sheets. Wide windows: docked at the end, full height, no scrim, so the text itself shows every
 * typography change live. Phones: a bottom sheet with [pinned] (the preview) above the scrolling controls.
 */
@Composable
internal fun BoxScope.NovelPanel(
    title: String,
    compact: Boolean,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    pinned: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = stringResource(UiR.string.ui_close)) }
        }
    }
    if (compact) {
        AdaptiveSheet(onDismissRequest = onDismiss, modifier = Modifier.decor(DecorRegion.SHEET)) {
            header()
            pinned?.invoke()
            if (scroll) {
                AdaptiveSheetScrollColumn(Modifier.padding(horizontal = 16.dp), content = content)
            } else {
                Column(Modifier.heightIn(max = 520.dp).padding(horizontal = 16.dp), content = content)
            }
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 2.dp,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(400.dp).padding(8.dp).decor(DecorRegion.SHEET),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                header()
                pinned?.invoke()
                Column(
                    Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}

/** A titled group of settings (artifact `nj-c-sub`). */
@Composable
internal fun NovelGroup(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        content()
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun NovelSwitch(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A slider with its value beside the title. [onLive] follows the drag (the live preview), [onDone] saves when the drag ends.
 */
@Composable
internal fun NovelSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: @Composable (Float) -> String,
    onLive: (Float) -> Unit = {},
    onDone: (Float) -> Unit,
) {
    var drag by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label(drag), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = drag, onValueChange = { drag = it; onLive(it) }, onValueChangeFinished = { onDone(drag) }, valueRange = range, steps = steps)
    }
}

/** Colour swatches (reading colours): each shows its colour beside its name. */
@Composable
internal fun NovelSwatches(choices: List<Pair<String, String>>, colorOf: @Composable (String) -> Color, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (id, label) ->
            val on = id == selected
            val swatch = colorOf(id)
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.heightIn(min = 48.dp).selectable(selected = on, role = Role.RadioButton) { onSelect(id) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).background(swatch, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
