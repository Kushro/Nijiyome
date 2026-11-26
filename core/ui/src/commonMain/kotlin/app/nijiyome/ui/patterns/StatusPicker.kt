// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.patterns

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.StatusChangeTransition
import app.nijiyome.model.ListStatus
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_stp_episode
import app.nijiyome.ui.resources.rd_i1_stp_minus
import app.nijiyome.ui.resources.rd_i1_stp_not_in_list
import app.nijiyome.ui.resources.rd_i1_stp_of_total
import app.nijiyome.ui.resources.rd_i1_stp_plus
import app.nijiyome.ui.resources.rd_i1_stp_remove
import app.nijiyome.ui.resources.rd_i1_stp_status
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane I1. Pattern `stp` of docs/redesign/artefacto/lab.js: the six list statuses in view with their color, icon and
// name, as a real radio group (a tap or the arrow keys), and the progress beside them with a stepper instead of another dialog.
// Wide windows: one row of six when the names fit, else 3 x 2 or fewer columns; phones: a 3 x 2 grid of 64 dp tiles. Each tile
// draws the direction's StatusMark with the full name.

/** [ListStatus] of a [StatusSlot] (the inverse of `ListStatus.slot()`). */
fun StatusSlot.toListStatus(): ListStatus = when (this) {
    StatusSlot.WATCHING -> ListStatus.WATCHING
    StatusSlot.PLANNING -> ListStatus.PLANNING
    StatusSlot.COMPLETED -> ListStatus.COMPLETED
    StatusSlot.PAUSED -> ListStatus.PAUSED
    StatusSlot.DROPPED -> ListStatus.DROPPED
    StatusSlot.REWATCHING -> ListStatus.REPEATING
}

/** The anime wording of a status ("Watching"). Manga and novels pass their own `labelOf` (`ListStatus.readLabelRes()`). */
@Composable
fun StatusSlot.label(): String = stringResource(toListStatus().labelRes())

/** The icon of a status, always shown next to its name (color is never the only cue). */
val StatusSlot.icon: ImageVector
    get() = when (this) {
        StatusSlot.WATCHING -> Icons.Outlined.PlayCircle
        StatusSlot.PLANNING -> Icons.Outlined.Bookmark
        StatusSlot.COMPLETED -> Icons.Outlined.CheckCircle
        StatusSlot.PAUSED -> Icons.Outlined.PauseCircle
        StatusSlot.DROPPED -> Icons.Outlined.Cancel
        StatusSlot.REWATCHING -> Icons.Outlined.Replay
    }

/**
 * The list status picker. [selected] = null when the title is not in the list. [disabledReason] returns why a status cannot be
 * chosen ("Only a completed title can be rewatched"), shown under it. Arrow keys move the choice like a radio group.
 *
 * ```kotlin
 * ListStatusPicker(selected = entry?.status?.slot(), onSelect = { vm.setStatus(it.toListStatus()) },
 *     disabledReason = { if (it == StatusSlot.REWATCHING && entry?.status != ListStatus.COMPLETED) onlyCompleted else null })
 * ```
 */
@Composable
fun ListStatusPicker(
    selected: StatusSlot?,
    onSelect: (StatusSlot) -> Unit,
    modifier: Modifier = Modifier,
    labelOf: @Composable (StatusSlot) -> String = { it.label() },
    disabledReason: (StatusSlot) -> String? = { null },
    compact: Boolean = isCompactWindow(),
    statuses: List<StatusSlot> = StatusSlot.entries,
) {
    val requesters = remember(statuses) { statuses.associateWith { FocusRequester() } }
    val labels = statuses.associateWith { labelOf(it) }
    val enabled = statuses.filter { disabledReason(it) == null }
    val groupLabel = stringResource(Res.string.rd_i1_stp_status)
    val keys = Modifier.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || enabled.isEmpty()) return@onPreviewKeyEvent false
        val step = when (e.key) {
            Key.DirectionRight, Key.DirectionDown -> 1
            Key.DirectionLeft, Key.DirectionUp -> -1
            else -> return@onPreviewKeyEvent false
        }
        val from = (selected?.let { enabled.indexOf(it) } ?: -1).let { if (it < 0) (if (step > 0) -1 else 0) else it }
        val next = enabled[(from + step + enabled.size) % enabled.size]
        onSelect(next)
        requesters[next]?.requestFocus()
        true
    }
    val group = modifier.decor(DecorRegion.STATUS_PICKER).selectableGroup().semantics { contentDescription = groupLabel }.then(keys)
    // Every status always shows its full name: the tiles keep their natural width and wrap to fewer columns (6, 3, 2, 1) instead of
    // squeezing six weighted tiles into one row, which cut the names to "Pend..." or to nothing on narrow columns.
    StatusGrid(group, columns = if (compact) listOf(3, 2, 1) else listOf(statuses.size, 3, 2, 1), gap = 8.dp) {
        statuses.forEach { slot ->
            StatusTile(slot, labels.getValue(slot), slot == selected, disabledReason(slot), { onSelect(slot) }, requesters.getValue(slot), tall = compact)
        }
    }
}

/**
 * Lays the status tiles out in equal columns: the first count of [columns] whose tiles fit at their natural width (the widest
 * tile's max intrinsic width), each row as tall as its tallest tile. Cells stretch to share the available width.
 */
@Composable
private fun StatusGrid(modifier: Modifier, columns: List<Int>, gap: Dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val n = measurables.size
        if (n == 0) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val gapPx = gap.roundToPx()
        val natural = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val candidates = (columns.filter { it in 1..n } + 1).distinct()
        val widest = candidates.first()
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else natural * widest + gapPx * (widest - 1)
        val cols = candidates.firstOrNull { c -> natural * c + gapPx * (c - 1) <= available } ?: 1
        val cellW = ((available - gapPx * (cols - 1)) / cols).coerceAtLeast(0)
        val rows = measurables.chunked(cols).map { row ->
            val rowH = row.maxOf { it.maxIntrinsicHeight(cellW) }
            row.map { it.measure(Constraints(minWidth = cellW, maxWidth = cellW, minHeight = rowH)) }
        }
        val heights = rows.map { r -> r.maxOf { it.height } }
        val totalH = heights.sum() + gapPx * (rows.size - 1)
        layout(maxOf(available, constraints.minWidth), maxOf(totalH, constraints.minHeight)) {
            var y = 0
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { i, p -> p.placeRelative(i * (cellW + gapPx), y) }
                y += heights[r] + gapPx
            }
        }
    }
}

@Composable
private fun StatusTile(
    slot: StatusSlot,
    label: String,
    selected: Boolean,
    reason: String?,
    onClick: () -> Unit,
    requester: FocusRequester,
    tall: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = LocalStatusColors.current.of(slot)
    Surface(
        shape = NijiyomeControls.shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = if (tall) 64.dp else 48.dp)
            .focusRequester(requester)
            .then(if (reason != null) Modifier.alpha(0.55f) else Modifier)
            .selectable(selected = selected, enabled = reason == null, role = Role.RadioButton, onClick = onClick)
            .semantics { if (reason != null) stateDescription = reason },
    ) {
        if (tall) {
            Column(
                Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Icon(slot.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                StatusMark(slot, label, selected = selected)
                if (reason != null) {
                    Text(reason, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            // Wide: the direction's mark alone (its own color, shape and name); a Material icon beside a seal or a paper strip was a
            // second mark for the same status and took the width the name needed.
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) { StatusMark(slot, label, selected = selected) }
        }
    }
}

/**
 * Episode / chapter progress as a stepper: minus, the number (animated with the "estado" moment), plus, and "of [total]".
 * [max] is how far it may go (the aired episodes).
 *
 * ```kotlin
 * ProgressStepper(value = entry.progress, onValueChange = vm::setProgress, max = aired, total = media.episodes)
 * ```
 */
@Composable
fun ProgressStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    max: Int,
    modifier: Modifier = Modifier,
    total: Int? = null,
    label: String = stringResource(Res.string.rd_i1_stp_episode),
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(Res.string.rd_i1_stp_minus))
        }
        StatusChangeTransition(value, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { v ->
            Text("$v", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { onValueChange((value + 1).coerceAtMost(max)) }, enabled = value < max) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(Res.string.rd_i1_stp_plus))
        }
        if (total != null && total > 0) {
            Text(
                stringResource(Res.string.rd_i1_stp_of_total, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The automatic adjustments of the artifact, as pure functions the view model can apply. */
object ListEntryRules {
    /** Adding progress to a planned (or absent) title makes it Watching. */
    fun statusForProgress(status: StatusSlot?, progress: Int): StatusSlot? =
        if (progress > 0 && (status == null || status == StatusSlot.PLANNING)) StatusSlot.WATCHING else status

    /** Completed fills the progress; Rewatching starts it over. */
    fun progressForStatus(status: StatusSlot, progress: Int, total: Int?): Int = when (status) {
        StatusSlot.COMPLETED -> total?.takeIf { it > 0 } ?: progress
        StatusSlot.REWATCHING -> 0
        else -> progress
    }
}

/**
 * Status, progress and removal of one title together: the current status (animated with [StatusChangeTransition]), the picker, the
 * stepper and "Remove from list". Pair each change with an undo ([UndoController]).
 *
 * ```kotlin
 * ListStatusEditor(status = entry?.status?.slot(), onStatusChange = vm::setStatus, progress = entry?.progress ?: 0,
 *     onProgressChange = vm::setProgress, aired = media.aired, total = media.episodes, onRemove = vm::remove)
 * ```
 */
@Composable
fun ListStatusEditor(
    status: StatusSlot?,
    onStatusChange: (StatusSlot) -> Unit,
    progress: Int,
    onProgressChange: (Int) -> Unit,
    aired: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    total: Int? = null,
    labelOf: @Composable (StatusSlot) -> String = { it.label() },
    disabledReason: (StatusSlot) -> String? = { null },
    compact: Boolean = isCompactWindow(),
) {
    val current = status?.let { labelOf(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusChangeTransition(status) { s ->
            if (s != null && current != null) StatusMark(s, current, selected = true)
            else Text(stringResource(Res.string.rd_i1_stp_not_in_list), style = MaterialTheme.typography.labelLarge)
        }
        ListStatusPicker(status, onStatusChange, Modifier.fillMaxWidth(), labelOf, disabledReason, compact)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProgressStepper(progress, onProgressChange, max = aired, total = total, modifier = Modifier.weight(1f))
            if (status != null) {
                TextButton(shape = NijiyomeControls.shape, onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.rd_i1_stp_remove))
                }
            }
        }
    }
}
