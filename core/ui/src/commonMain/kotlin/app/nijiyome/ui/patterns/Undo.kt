// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AppSnackbar
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.motion.SnackbarTransition
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_undo_action
import app.nijiyome.ui.resources.rd_i1_undo_close
import app.nijiyome.ui.resources.rd_i1_undo_dismiss
import app.nijiyome.ui.resources.rd_i1_undo_done
import app.nijiyome.ui.resources.rd_i1_undo_each
import app.nijiyome.ui.resources.rd_i1_undo_older
import app.nijiyome.ui.resources.rd_i1_undo_recent
import app.nijiyome.ui.resources.rd_i1_undo_recent_title
import app.nijiyome.ui.resources.rd_i1_undo_undone
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane I1. The undo of docs/redesign/artefacto/lab.js (`pushUndo`): every reversible action leaves "Done · Undo" in
// a queue that does not overwrite the previous one; the snackbar times out (paused while hovered or focused), then collapses into
// "Undo recent (n)", which lists each action to undo it on its own. The rule of the redesign: confirm or undo, uniformly.

/** One reversible action. [undone] flips when it is undone. */
@Stable
class UndoEntry internal constructor(
    val id: Long,
    val message: String,
    internal val undo: (() -> Unit)?,
    private val onCommit: (() -> Unit)?,
) {
    var undone: Boolean by mutableStateOf(false)
        internal set

    private var committed = false

    val undoable: Boolean get() = undo != null && !undone && !committed

    internal fun commit() {
        if (committed || undone) return
        committed = true
        onCommit?.invoke()
    }
}

/**
 * The undo queue of a screen (or of the whole shell). [show] applies nothing itself: do the action, then call
 * `show(message, undo = { revert })`. For deferred work (delete files only once the undo is gone) pass [onCommit]: it runs when the
 * entry leaves the queue (history overflow or the screen leaving).
 *
 * ```kotlin
 * val undo = rememberUndoController()
 * vm.markWatched(eps); undo.show(markedMsg, undo = { vm.markUnwatched(eps) })
 * Box { Content(); UndoSnackbar(undo, Modifier.align(Alignment.BottomCenter)) }
 * ```
 */
@Stable
class UndoController(val timeoutMillis: Long = 8_000, private val historySize: Int = 20) {
    /** Reversible entries, newest first (undone ones stay, marked). */
    val history: SnapshotStateList<UndoEntry> = mutableStateListOf()

    /** The entry on the snackbar right now. */
    var current: UndoEntry? by mutableStateOf(null)
        private set

    /** "Undone: …" shown briefly after an undo from the snackbar. */
    var confirmation: String? by mutableStateOf(null)
        private set

    private var nextId = 0L

    /** Entries that can still be undone. */
    val pendingCount: Int get() = history.count { it.undoable }

    /** Shows [message]; with [undo] it offers "Undo" and joins the history. Returns the entry (to undo it from code). */
    fun show(message: String, undo: (() -> Unit)? = null, onCommit: (() -> Unit)? = null): UndoEntry {
        val entry = UndoEntry(++nextId, message, undo, onCommit)
        if (undo != null) {
            history.add(0, entry)
            while (history.size > historySize) history.removeAt(history.lastIndex).commit()
        } else {
            entry.commit()
        }
        confirmation = null
        current = entry
        return entry
    }

    /** Undoes [entry] if it still can be. */
    fun undo(entry: UndoEntry) {
        if (!entry.undoable) return
        entry.undone = true
        entry.undo?.invoke()
        if (current == entry) {
            current = null
            confirmation = entry.message
        }
    }

    /** Hides the snackbar; the entry stays in the history. */
    fun dismiss() {
        current = null
        confirmation = null
    }

    internal fun clearConfirmation() {
        confirmation = null
    }

    /** Runs every pending [onCommit] (the screen is leaving). */
    fun commitAll() {
        history.forEach { it.commit() }
    }
}

/** An [UndoController] for this screen; pending deferred work is committed when the screen leaves the composition. */
@Composable
fun rememberUndoController(timeoutMillis: Long = 8_000): UndoController {
    val controller = remember(timeoutMillis) { UndoController(timeoutMillis) }
    DisposableEffect(controller) { onDispose { controller.commitAll() } }
    return controller
}

/**
 * The snackbar of an [UndoController], with the "snack" motion moment ([SnackbarTransition]), a life line that runs out (not under
 * CALM or OFF), "Older (n)" when more actions wait, and the collapsed "Undo recent (n)" chip after it times out.
 */
@Composable
fun UndoSnackbar(controller: UndoController, modifier: Modifier = Modifier) {
    val entry = controller.current
    val confirmation = controller.confirmation
    var hovered by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var remaining by remember(entry?.id) { mutableFloatStateOf(1f) }
    val paused = hovered || focused || historyOpen
    LaunchedEffect(entry?.id, paused) {
        if (entry == null || paused) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (remaining > 0f) {
            val now = withFrameMillis { it }
            remaining -= (now - last) / controller.timeoutMillis.toFloat()
            last = now
        }
        if (controller.current == entry) controller.dismiss()
    }
    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            delay(3_000)
            controller.clearConfirmation()
        }
    }
    // Keep the last content while the exit animation plays (plain fields: written during composition, never observed).
    val last = remember { LastShown() }
    if (entry != null) {
        last.entry = entry
        last.confirmation = null
    } else if (confirmation != null) {
        last.confirmation = confirmation
        last.entry = null
    }
    val shownEntry = last.entry
    val shownConfirmation = last.confirmation
    val level = LocalMotion.current.level
    val showLife = level == MotionLevel.FULL || level == MotionLevel.PERF
    val older = controller.pendingCount - (if (entry?.undoable == true) 1 else 0)
    Box(modifier.padding(16.dp)) {
        SnackbarTransition(visible = entry != null || confirmation != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .onFocusChanged { focused = it.hasFocus }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Enter) hovered = true
                                if (event.type == PointerEventType.Exit) hovered = false
                            }
                        }
                    },
            ) {
                Column {
                    Row(
                        Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val text = shownEntry?.message ?: stringResource(Res.string.rd_i1_undo_undone, shownConfirmation.orEmpty())
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f, fill = false).padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val e = shownEntry
                        val inverseButton = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)
                        if (e != null && e.undoable) {
                            TextButton(onClick = { controller.undo(e) }, colors = inverseButton) { Text(stringResource(Res.string.rd_i1_undo_action)) }
                        }
                        if (e != null && older > 0) {
                            Box {
                                TextButton(onClick = { historyOpen = true }, colors = inverseButton) {
                                    Text(stringResource(Res.string.rd_i1_undo_older, older))
                                }
                                UndoHistoryMenu(controller, expanded = historyOpen, onDismiss = { historyOpen = false })
                            }
                        }
                        IconButton(onClick = { controller.dismiss() }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_i1_undo_dismiss), tint = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                    if (showLife && shownEntry?.undoable == true) {
                        Box(
                            Modifier
                                .fillMaxWidth(remaining.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.inversePrimary),
                        )
                    }
                }
            }
        }
        if (entry == null && confirmation == null && controller.pendingCount > 0) {
            Box {
                AssistChip(
                    onClick = { historyOpen = true },
                    label = { Text(stringResource(Res.string.rd_i1_undo_recent, controller.pendingCount)) },
                    leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                UndoHistoryMenu(controller, expanded = historyOpen, onDismiss = { historyOpen = false })
            }
        }
    }
}

private class LastShown {
    var entry: UndoEntry? = null
    var confirmation: String? = null
}

/** The list of recent reversible actions, each with its own Undo. */
@Composable
private fun UndoHistoryMenu(controller: UndoController, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(min = 260.dp, max = 420.dp)) {
            Text(stringResource(Res.string.rd_i1_undo_recent_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(Res.string.rd_i1_undo_each), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        controller.history.forEach { e ->
            Row(
                Modifier.widthIn(min = 260.dp, max = 420.dp).heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(e.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.undoable) {
                    TextButton(onClick = { controller.undo(e) }) { Text(stringResource(Res.string.rd_i1_undo_action)) }
                } else {
                    Text(stringResource(Res.string.rd_i1_undo_done), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(Res.string.rd_i1_undo_close)) }
    }
}

/**
 * "Done, undo" through the app's single snackbar host (`LocalAppSnackbar`), for screens that do not keep their own queue.
 *
 * ```kotlin
 * LocalAppSnackbar.current.showUndo(removedMsg, undoLabel) { vm.restore(item) }
 * ```
 */
fun AppSnackbar.showUndo(message: String, undoLabel: String, onUndo: () -> Unit) = show(message, undoLabel, onUndo)

/**
 * The same as [showUndo] with the translated "Undo" label already resolved.
 *
 * ```kotlin
 * val showUndo = rememberShowUndo(); showUndo(removedMsg) { vm.restore(item) }
 * ```
 */
@Composable
fun rememberShowUndo(): (message: String, onUndo: () -> Unit) -> Unit {
    val snackbar = LocalAppSnackbar.current
    val label = stringResource(Res.string.rd_i1_undo_action)
    return remember(snackbar, label) { { message, onUndo -> snackbar.showUndo(message, label, onUndo) } }
}
