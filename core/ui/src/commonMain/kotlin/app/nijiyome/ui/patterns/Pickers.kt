// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.ui.patterns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.ui.platform.rememberFolderPicker
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_pk_change_folder
import app.nijiyome.ui.resources.rd_i1_pk_default
import app.nijiyome.ui.resources.rd_i1_pk_folder_none
import app.nijiyome.ui.resources.rd_i1_pk_hour_minus
import app.nijiyome.ui.resources.rd_i1_pk_hour_plus
import app.nijiyome.ui.resources.rd_i1_pk_internal_storage
import app.nijiyome.ui.resources.rd_i1_pk_minus
import app.nijiyome.ui.resources.rd_i1_pk_minute_minus
import app.nijiyome.ui.resources.rd_i1_pk_minute_plus
import app.nijiyome.ui.resources.rd_i1_pk_plus
import app.nijiyome.ui.resources.rd_i1_pk_port_privileged
import app.nijiyome.ui.resources.rd_i1_pk_range
import app.nijiyome.ui.resources.rd_i1_pk_range_empty
import app.nijiyome.ui.resources.rd_i1_pk_range_from
import app.nijiyome.ui.resources.rd_i1_pk_range_invalid
import app.nijiyome.ui.resources.rd_i1_pk_range_overnight
import app.nijiyome.ui.resources.rd_i1_pk_range_to
import app.nijiyome.ui.resources.rd_i1_pk_test
import app.nijiyome.ui.resources.rd_i1_pk_testing
import app.nijiyome.ui.resources.rd_i1_pk_unit_gb
import app.nijiyome.ui.resources.rd_i1_pk_unit_hour
import app.nijiyome.ui.resources.rd_i1_pk_unit_kibs
import app.nijiyome.ui.resources.rd_i1_pk_unit_mb
import app.nijiyome.ui.resources.rd_i1_pk_unit_mibs
import app.nijiyome.ui.resources.rd_i1_pk_unit_min
import app.nijiyome.ui.resources.rd_i1_pk_unit_sec
import app.nijiyome.ui.resources.rd_i1_pk_use
import app.nijiyome.ui.resources.rd_i1_pk_use_default
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane I1. Pattern `pk` of docs/redesign/artefacto/lab.js: every value has a picker made for it instead of a raw text
// field: the port with a stepper and "Test", the folder with the system picker and its path as breadcrumbs, a time of day and a time
// range with steppers, and an amount with its unit. Each validates and says why when a value cannot be used.

/**
 * A number field that never stands alone: minus and plus buttons, Up / Down (±[step]) and Page Up / Page Down (±[bigStep]), digits
 * only, and the allowed range as the error when the text is not a valid value. [onValueChange] only receives valid values.
 *
 * ```kotlin
 * NumberStepperField(value = cacheMb, onValueChange = vm::setCache, min = 64, max = 4096, label = cacheLabel, step = 64)
 * ```
 */
@Composable
fun NumberStepperField(
    value: Long,
    onValueChange: (Long) -> Unit,
    min: Long,
    max: Long,
    label: String,
    modifier: Modifier = Modifier,
    step: Long = 1,
    bigStep: Long = step * 10,
    suffix: String? = null,
    errorText: String? = null,
    width: Dp = 220.dp,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toLongOrNull()
    val invalid = parsed == null || parsed < min || parsed > max
    fun set(v: Long) {
        val c = v.coerceIn(min, max)
        text = c.toString()
        if (c != value) onValueChange(c)
    }
    val rangeMessage = stringResource(Res.string.rd_i1_pk_range, min, max)
    val shownError = if (invalid) rangeMessage else errorText
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.filter { it.isDigit() }.take(max.toString().length)
            text = digits
            digits.toLongOrNull()?.takeIf { it in min..max }?.let { if (it != value) onValueChange(it) }
        },
        label = { Text(label) },
        singleLine = true,
        isError = shownError != null,
        supportingText = shownError?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        leadingIcon = {
            IconButton(onClick = { set((parsed ?: value) - step) }, enabled = (parsed ?: value) > min) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(Res.string.rd_i1_pk_minus))
            }
        },
        trailingIcon = {
            IconButton(onClick = { set((parsed ?: value) + step) }, enabled = (parsed ?: value) < max) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(Res.string.rd_i1_pk_plus))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        // The direction's control radius, like the buttons that sit beside a stepper (Test, Use default).
        shape = NijiyomeControls.shape,
        modifier = modifier.width(width).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val base = parsed ?: value
            when (e.key) {
                Key.DirectionUp -> set(base + step)
                Key.DirectionDown -> set(base - step)
                Key.PageUp -> set(base + bigStep)
                Key.PageDown -> set(base - bigStep)
                else -> return@onPreviewKeyEvent false
            }
            true
        },
    )
}

/** Result of testing a port. [suggestion] = a free port to offer ("Use 47321"). */
@Immutable
data class PortCheck(val ok: Boolean, val message: String, val suggestion: Int? = null)

/**
 * A TCP port: stepper field (1 to 65535), "Test" when [onTest] is given (the caller binds the port and says whether it is free), the
 * default as a one-tap chip, and a warning below 1024 (administrator rights on desktop).
 *
 * ```kotlin
 * PortPicker(port = settings.linkPort, onPortChange = vm::setLinkPort, label = linkPortLabel, defaultPort = 47321,
 *     onTest = { p -> if (vm.isPortFree(p)) PortCheck(true, freeMsg(p)) else PortCheck(false, inUseMsg(p), suggestion = 47321) })
 * ```
 */
@Composable
fun PortPicker(
    port: Int,
    onPortChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    defaultPort: Int? = null,
    onTest: (suspend (Int) -> PortCheck)? = null,
    warnPrivileged: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PortCheck?>(null) }
    LaunchedEffect(port) { result = null }
    val signals = LocalSignalColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberStepperField(port.toLong(), { onPortChange(it.toInt()) }, min = 1, max = 65535, label = label)
            if (onTest != null) {
                FilledTonalButton(
                    shape = NijiyomeControls.shape,
                    onClick = {
                        busy = true
                        scope.launch {
                            result = try {
                                onTest(port)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    // As tall as the field's box (56 dp below its 8 dp label inset), not a 40 dp button beside a taller field.
                    modifier = Modifier.padding(top = 8.dp).height(56.dp),
                ) {
                    Icon(Icons.Outlined.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(if (busy) Res.string.rd_i1_pk_testing else Res.string.rd_i1_pk_test), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (defaultPort != null) {
            if (port != defaultPort) {
                AssistChip(onClick = { onPortChange(defaultPort) }, label = { Text(stringResource(Res.string.rd_i1_pk_use_default, defaultPort)) })
            } else {
                Text(stringResource(Res.string.rd_i1_pk_default), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val r = result
        when {
            r != null -> ResultLine(
                icon = if (r.ok) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                color = if (r.ok) signals.ok else signals.err,
                text = r.message,
                action = r.suggestion?.let { s -> stringResource(Res.string.rd_i1_pk_use, s) to { onPortChange(s) } },
            )
            warnPrivileged && port < 1024 -> ResultLine(Icons.Outlined.Info, signals.warn, stringResource(Res.string.rd_i1_pk_port_privileged), null)
        }
    }
}

@Composable
private fun ResultLine(icon: ImageVector, color: Color, text: String, action: Pair<String, () -> Unit>?) {
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f, fill = false))
        if (action != null) TextButton(shape = NijiyomeControls.shape, onClick = action.second) { Text(action.first) }
    }
}

/**
 * The parts of a folder path to show as breadcrumbs: a Unix path starts with "/", Windows with its drive, and an Android tree URI
 * (`content://…/tree/primary%3AAnime`) with its volume ([internalLabel] for the built-in storage). Long paths keep the first part and
 * the last two around an ellipsis.
 */
fun folderPathSegments(path: String, internalLabel: String): List<String> {
    val shown: String
    val head = mutableListOf<String>()
    if (path.startsWith("content://")) {
        val tree = path.substringAfter("/tree/", missingDelimiterValue = path.substringAfterLast('/'))
        val decoded = runCatching { java.net.URLDecoder.decode(tree, "UTF-8") }.getOrDefault(tree)
        val volume = decoded.substringBefore(':', missingDelimiterValue = "")
        if (volume.isNotEmpty()) head += if (volume == "primary") internalLabel else volume
        shown = decoded.substringAfter(':')
    } else {
        shown = path.replace('\\', '/')
        if (shown.startsWith("/")) head += "/"
    }
    val parts = head + shown.split('/').filter { it.isNotEmpty() }
    return if (parts.size > 4) listOf(parts.first(), "…") + parts.takeLast(2) else parts
}

/**
 * A folder: its path as breadcrumb chips and "Change folder…", which opens the system picker (desktop: the folder chooser; Android:
 * the documents tree picker, whose permission the caller persists). Tapping a crumb opens the picker too.
 *
 * ```kotlin
 * FolderPathPicker(path = settings.downloadDir, onPathChange = vm::setDownloadDir, label = downloadsFolderLabel)
 * ```
 */
@Composable
fun FolderPathPicker(
    path: String?,
    onPathChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val pick = rememberFolderPicker { chosen -> if (chosen != null) onPathChange(chosen) }
    val internal = stringResource(Res.string.rd_i1_pk_internal_storage)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (path.isNullOrBlank()) {
            Text(stringResource(Res.string.rd_i1_pk_folder_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val segments = folderPathSegments(path, internal)
            FlowRow(
                Modifier.semantics(mergeDescendants = true) { contentDescription = path },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                segments.forEachIndexed { i, seg ->
                    if (i > 0) Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp).align(Alignment.CenterVertically))
                    AssistChip(
                        onClick = pick,
                        label = { Text(seg, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        OutlinedButton(shape = NijiyomeControls.shape, onClick = pick) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.rd_i1_pk_change_folder), modifier = Modifier.padding(start = 8.dp))
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "21:05" for minutes after midnight. */
fun formatTimeOfDay(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
}

/**
 * A time of day (minutes after midnight) with hour and minute steppers; both wrap around. Up / Down on a focused button repeat it.
 *
 * ```kotlin
 * TimeOfDayPicker(minutes = schedule.start, onMinutesChange = vm::setStart, label = fromLabel, stepMinutes = 15)
 * ```
 */
@Composable
fun TimeOfDayPicker(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    stepMinutes: Int = 15,
) {
    fun shift(delta: Int) = onMinutesChange((((minutes + delta) % 1440) + 1440) % 1440)
    val text = formatTimeOfDay(minutes)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shift(-60) }) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(Res.string.rd_i1_pk_hour_minus)) }
            Text(text.substring(0, 2), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { shift(60) }) { Icon(Icons.Outlined.Add, contentDescription = stringResource(Res.string.rd_i1_pk_hour_plus)) }
            Text(":", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { shift(-stepMinutes) }) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(Res.string.rd_i1_pk_minute_minus)) }
            Text(text.substring(3, 5), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            IconButton(onClick = { shift(stepMinutes) }) { Icon(Icons.Outlined.Add, contentDescription = stringResource(Res.string.rd_i1_pk_minute_plus)) }
        }
    }
}

/**
 * A time range of one day (bandwidth schedule, quiet hours). With [allowOvernight] an end before the start means "until the next
 * day" and says so; otherwise it is an error. An empty range (start == end) is always an error.
 *
 * ```kotlin
 * TimeRangePicker(start = band.from, end = band.to, onChange = { s, e -> vm.setBand(s, e) }, label = limitHoursLabel)
 * ```
 */
@Composable
fun TimeRangePicker(
    start: Int,
    end: Int,
    onChange: (start: Int, end: Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    stepMinutes: Int = 15,
    allowOvernight: Boolean = true,
) {
    val signals = LocalSignalColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeOfDayPicker(start, { onChange(it, end) }, stringResource(Res.string.rd_i1_pk_range_from), stepMinutes = stepMinutes)
            TimeOfDayPicker(end, { onChange(start, it) }, stringResource(Res.string.rd_i1_pk_range_to), stepMinutes = stepMinutes)
        }
        when {
            start == end -> ResultLine(Icons.Outlined.Error, signals.err, stringResource(Res.string.rd_i1_pk_range_empty), null)
            end < start && allowOvernight -> ResultLine(Icons.Outlined.Info, signals.info, stringResource(Res.string.rd_i1_pk_range_overnight), null)
            end < start -> ResultLine(Icons.Outlined.Error, signals.err, stringResource(Res.string.rd_i1_pk_range_invalid), null)
        }
    }
}

/** A unit of a [QuantityPicker]: [factor] = how many base units it is worth (MiB/s = 1024 when the base is KiB/s). */
@Immutable
data class QuantityUnit(val id: String, val label: String, val factor: Long)

/** Speed units over a KiB/s base. */
@Composable
fun speedUnits(): List<QuantityUnit> = listOf(
    QuantityUnit("kib", stringResource(Res.string.rd_i1_pk_unit_kibs), 1),
    QuantityUnit("mib", stringResource(Res.string.rd_i1_pk_unit_mibs), 1024),
)

/** Size units over a MB base. */
@Composable
fun sizeUnits(): List<QuantityUnit> = listOf(
    QuantityUnit("mb", stringResource(Res.string.rd_i1_pk_unit_mb), 1),
    QuantityUnit("gb", stringResource(Res.string.rd_i1_pk_unit_gb), 1024),
)

/** Duration units over a minutes base. */
@Composable
fun minuteUnits(): List<QuantityUnit> = listOf(
    QuantityUnit("min", stringResource(Res.string.rd_i1_pk_unit_min), 1),
    QuantityUnit("h", stringResource(Res.string.rd_i1_pk_unit_hour), 60),
)

/** Duration units over a seconds base. */
@Composable
fun secondUnits(): List<QuantityUnit> = listOf(
    QuantityUnit("s", stringResource(Res.string.rd_i1_pk_unit_sec), 1),
    QuantityUnit("min", stringResource(Res.string.rd_i1_pk_unit_min), 60),
    QuantityUnit("h", stringResource(Res.string.rd_i1_pk_unit_hour), 3600),
)

/** The largest unit that expresses [base] exactly ("2 h" rather than "120 min"). */
fun bestUnit(base: Long, units: List<QuantityUnit>): QuantityUnit =
    units.sortedByDescending { it.factor }.firstOrNull { base != 0L && base % it.factor == 0L } ?: units.minBy { it.factor }

/** "5 MiB/s" in the best unit. */
fun formatQuantity(base: Long, units: List<QuantityUnit>): String {
    val unit = bestUnit(base, units)
    return "${base / unit.factor} ${unit.label}"
}

/**
 * An amount with its unit (speed limit, cache size, sleep timer): the number as a [NumberStepperField], the unit as a segmented choice
 * (switching keeps the number and changes its meaning) and optional [presets] as chips. Works in base units ([QuantityUnit.factor]).
 *
 * ```kotlin
 * QuantityPicker(baseValue = limitKibs, onBaseValueChange = vm::setLimit, units = speedUnits(), label = limitLabel, min = 1,
 *     presets = listOf(1024, 5 * 1024, 10 * 1024))
 * ```
 */
@Composable
fun QuantityPicker(
    baseValue: Long,
    onBaseValueChange: (Long) -> Unit,
    units: List<QuantityUnit>,
    label: String,
    modifier: Modifier = Modifier,
    min: Long = 0,
    max: Long = Long.MAX_VALUE / 4,
    presets: List<Long> = emptyList(),
    presetLabel: (Long) -> String = { formatQuantity(it, units) },
) {
    var unit by remember(units) { mutableStateOf(bestUnit(baseValue, units)) }
    // A value set from outside that the chosen unit cannot express exactly moves to the unit that can.
    if (baseValue % unit.factor != 0L) unit = bestUnit(baseValue, units)
    val amount = baseValue / unit.factor
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberStepperField(
                value = amount,
                onValueChange = { onBaseValueChange((it * unit.factor).coerceIn(min, max)) },
                min = (min + unit.factor - 1) / unit.factor,
                max = max / unit.factor,
                label = label,
                suffix = unit.label,
            )
            if (units.size > 1) {
                Segmented(
                    options = units.map { SegmentedOption(it.id, it.label) },
                    selected = unit.id,
                    onSelect = { id ->
                        val next = units.first { it.id == id }
                        unit = next
                        onBaseValueChange((amount * next.factor).coerceIn(min, max))
                    },
                    small = true,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        if (presets.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                presets.forEach { p ->
                    FilterChip(selected = p == baseValue, onClick = { onBaseValueChange(p) }, label = { Text(presetLabel(p)) })
                }
            }
        }
    }
}
