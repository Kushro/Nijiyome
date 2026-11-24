// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File

@Composable
actual fun Modifier.externalDropTarget(hovering: (Boolean) -> Unit, onDrop: (ExternalDrop) -> Boolean): Modifier {
    val currentHover = rememberUpdatedState(hovering)
    val currentDrop = rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = currentHover.value(true)

            override fun onExited(event: DragAndDropEvent) = currentHover.value(false)

            override fun onEnded(event: DragAndDropEvent) = currentHover.value(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentHover.value(false)
                val drop = transferableOf(event)?.let(::externalDrop) ?: return false
                return currentDrop.value(drop)
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun transferableOf(event: DragAndDropEvent): Transferable? = when (val native = event.nativeEvent) {
    is DropTargetDropEvent -> native.transferable
    is DropTargetDragEvent -> native.transferable
    else -> null
}

/** The files (absolute paths) and the text of an AWT transfer; null when it has neither. */
internal fun externalDrop(t: Transferable): ExternalDrop? {
    val files = runCatching {
        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) (t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>().map { it.absolutePath } else null
    }.getOrNull().orEmpty()
    val text = runCatching {
        if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) (t.getTransferData(DataFlavor.stringFlavor) as? String)?.trim()?.takeIf { it.isNotEmpty() } else null
    }.getOrNull()
    // A file manager that only gives text/uri-list as text: file:// lines are files.
    val fromText = if (files.isEmpty() && text != null) {
        text.lines().map { it.trim() }.filter { it.startsWith("file:") }.mapNotNull(::cleanPastedPath)
    } else {
        emptyList()
    }
    if (files.isEmpty() && fromText.isEmpty() && text == null) return null
    return ExternalDrop(files = files.ifEmpty { fromText }, text = text)
}
