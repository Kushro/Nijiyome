// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.feature.player.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.AdaptiveSheetScrollColumn
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Sparkles redesign (lane S6): the building blocks every player sheet uses. The shared pickers of lane I1 (core/ui patterns) did not
// exist when this lane started, so the small ones the sheets need (choice chips, option tiles, stepper, slider with units, state note)
// live here.

// ---- container ---------------------------------------------------------------------------------------------------------------------

/**
 * The common container of the player's sheets: a side panel on wide windows (the video stays visible beside it) and a bottom sheet
 * on phones, both through [AdaptiveSheet]. Title, an optional back arrow (a step inside the sheet) and Close.
 */
@Composable
internal fun PlayerSheetFrame(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismiss, modifier = Modifier.decor(DecorRegion.SHEET)) {
        SheetHeader(title, onDismiss, onBack)
        AdaptiveSheetScrollColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
    }
}

@Composable
internal fun SheetHeader(title: String, onClose: (() -> Unit)?, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.rd_s6_back)) }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onClose != null) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(Res.string.rd_s6_close)) }
        }
    }
}

/** One concern of a sheet: the direction's section header, then its controls. Sections are separated by [SheetDivider]. */
@Composable
internal fun SheetSection(
    title: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) SectionHeader(title, Modifier.semantics { heading() }, trailing)
        content()
    }
}

@Composable
internal fun SheetDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Small caption above a group of controls ("Modo", "Nivel"). */
@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 4.dp))
}

/** A secondary explanation under a control. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

// ---- choices -----------------------------------------------------------------------------------------------------------------------

/** One option of [ChoiceChips]. [badge] = a key hint ("Ctrl+2"), [reason] = why it is disabled (read by screen readers too). */
internal data class Choice<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
    val badge: String? = null,
    val reason: String? = null,
)

/** A single choice among a handful of short values (tracks, speeds, timer, modes), wrapping onto more lines when needed. */
@Composable
internal fun <T> ChoiceChips(choices: List<Choice<T>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        choices.forEach { c ->
            val isSelected = c.value == selected
            FilterChip(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(c.value) },
                enabled = c.enabled,
                label = { Text(c.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = c.badge?.let { { KeyBadge(it) } },
                modifier = Modifier.semantics {
                    role = Role.RadioButton
                    if (c.reason != null) contentDescription = "${c.label}. ${c.reason}"
                },
            )
        }
    }
}

/** A keyboard hint beside a choice. */
@Composable
internal fun KeyBadge(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** A choice with a title and a sentence saying what it does (enhancement families), laid out two per row. */
internal data class OptionTile<T>(
    val value: T,
    val title: String,
    val description: String,
    val enabled: Boolean = true,
    val badge: String? = null,
)

@Composable
internal fun <T> OptionTiles(tiles: List<OptionTile<T>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> OptionTileView(tile, tile.value == selected, onSelect, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun <T> OptionTileView(tile: OptionTile<T>, selected: Boolean, onSelect: (T) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = { if (!selected) onSelect(tile.value) },
        enabled = tile.enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = modifier.heightIn(min = 72.dp).semantics { role = Role.RadioButton },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tile.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tile.enabled) Color.Unspecified else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                tile.badge?.let { KeyBadge(it) }
            }
            Text(
                tile.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            )
        }
    }
}

// ---- values --------------------------------------------------------------------------------------------------------------------------

/** A setting with a switch; the whole row toggles it (one touch target, one semantics node). */
@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
    hintIsWarning: Boolean = false,
    icon: ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = if (hintIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * An integer slider snapped to [step] with its value and unit beside the label ([valueText]). [onChange] runs on every step (mpv and
 * the subtitle preview take it live); [onDone] when the finger lifts.
 */
@Composable
internal fun ValueSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    valueText: String,
    onChange: (Int) -> Unit,
    onDone: () -> Unit = {},
    enabled: Boolean = true,
) {
    var drag by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = drag,
            onValueChange = { v ->
                drag = v
                val snapped = ((v / step).roundToInt() * step).coerceIn(range.first, range.last)
                if (snapped != value) onChange(snapped)
            },
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText
            },
        )
    }
}

/** "− value + (reset)": a value moved in fixed steps (subtitle delay). */
@Composable
internal fun Stepper(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusDescription: String,
    plusDescription: String,
    onReset: (() -> Unit)? = null,
    resetDescription: String? = null,
    resetEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, minusDescription) }
        Text(valueText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { stateDescription = valueText })
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, plusDescription) }
        if (onReset != null) {
            IconButton(onClick = onReset, enabled = resetEnabled) { Icon(Icons.Rounded.RestartAlt, resetDescription) }
        }
    }
}

// ---- states --------------------------------------------------------------------------------------------------------------------------

internal enum class NoteTone { INFO, WARN, ERROR }

/** A real state the sheet must say (not available here, PC offline, missing key), with its way out when there is one. */
@Composable
internal fun StateNote(text: String, tone: NoteTone = NoteTone.INFO, action: String? = null, onAction: (() -> Unit)? = null) {
    val signal = LocalSignalColors.current
    val (icon, tint) = when (tone) {
        NoteTone.INFO -> Icons.Rounded.Info to signal.info
        NoteTone.WARN -> Icons.Rounded.WarningAmber to signal.warn
        NoteTone.ERROR -> Icons.Rounded.ErrorOutline to signal.err
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = if (action != null) 2.dp else 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 8.dp))
            }
            if (action != null && onAction != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAction) { Text(action) }
                }
            }
        }
    }
}

/** A row of the "Más" action list. */
@Composable
internal fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit, supporting: String? = null, enabled: Boolean = true, badge: String? = null) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            badge?.let { KeyBadge(it) }
        }
    }
}
