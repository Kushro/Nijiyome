// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import androidx.compose.ui.window.DialogProperties
import app.nijiyome.ui.format.relativeTime

@Composable
internal actual fun rememberStorageAccess(): StorageAccess = remember {
    object : StorageAccess {
        // Desktop pickers return plain paths: there is no access grant to keep.
        override fun keepFolderAccess(tree: String) = Unit

        // The whole path says more than the folder name on a desktop (several "Anime" folders are common).
        override fun folderName(tree: String): String? = tree.takeIf { it.isNotBlank() }

        override fun documentName(uri: String): String? =
            runCatching { (if (uri.startsWith("file:")) java.io.File(java.net.URI(uri)) else java.io.File(uri)).name }.getOrNull()?.takeIf { it.isNotEmpty() }

        // The engine (and the "Añadir torrent" sheet) take a .torrent as a file:// URI.
        override fun documentUri(picked: String): String = if (picked.startsWith("file:")) picked else java.io.File(picked).toPath().toUri().toString()
    }
}

@Composable
internal actual fun relativeTimeSpan(epochMs: Long): String = relativeTime(epochMs)

internal actual val asksNotificationPermission: Boolean = false

internal actual fun fullScreenDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)

internal actual val desktopTorrentUi: Boolean = true

/** Compose Desktop's drop target over AWT: a file list whose first `.torrent` goes to [onDrop] as a path. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.torrentFileDrop(onHover: (Boolean) -> Unit, onDrop: (path: String) -> Unit): Modifier {
    val hover by rememberUpdatedState(onHover)
    val drop by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = hover(true)

            override fun onExited(event: DragAndDropEvent) = hover(false)

            override fun onEnded(event: DragAndDropEvent) = hover(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover(false)
                val files = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<java.io.File>).map { it.absolutePath }
                }.getOrNull().orEmpty()
                val torrent = droppedTorrent(files) ?: return false
                drop(torrent)
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> runCatching { event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }.getOrDefault(false) },
        target = target,
    )
}
