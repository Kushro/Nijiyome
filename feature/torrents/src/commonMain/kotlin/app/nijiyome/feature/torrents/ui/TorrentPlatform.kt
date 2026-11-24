// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

// What the torrent screens need from the platform and only they use: the documents and folders chosen with the pickers of
// :core:ui (Android: SAF URIs), the relative "added" time and the full-screen warning dialog.

/** Chosen documents and folders (Android: content / tree URIs). */
internal interface StorageAccess {
    /** Keeps read/write access to a chosen folder across restarts (Android: persistable URI permission). */
    fun keepFolderAccess(tree: String)

    /** Readable name of a chosen folder, null when unknown. */
    fun folderName(tree: String): String?

    /** File name of a chosen document, null when unknown. */
    fun documentName(uri: String): String?

    /** What the engine reads a chosen document from (Android: the content URI as is; desktop: the picked path as a `file:` URI). */
    fun documentUri(picked: String): String
}

@Composable
internal expect fun rememberStorageAccess(): StorageAccess

/** "5 minutes ago", "Yesterday"... for an instant in the past (minute resolution). */
@Composable
internal expect fun relativeTimeSpan(epochMs: Long): String

/** true where notifications need a runtime permission (Android 13+): the warning then explains why it is asked. */
internal expect val asksNotificationPermission: Boolean

/** Properties of the full-screen "Entiendo / Ahora no" dialog. */
internal expect fun fullScreenDialogProperties(): DialogProperties

/**
 * true on desktop, where the torrent screens follow Tatakai Next's desktop app: texts without phone, battery or mobile data, a
 * download folder instead of the app storage, "Abrir carpeta", the file list of "Añadir torrent" and a sortable library with a list
 * view. Android: false, its screens unchanged.
 */
internal expect val desktopTorrentUi: Boolean

/**
 * Takes a `.torrent` dropped from the file manager onto the element (desktop); [onHover] says whether one is being dragged over it.
 * Android: the modifier unchanged.
 */
@Composable
internal expect fun Modifier.torrentFileDrop(onHover: (Boolean) -> Unit, onDrop: (path: String) -> Unit): Modifier

/** The `.torrent` among dropped files (the first one), null when there is none. */
internal fun droppedTorrent(paths: List<String>): String? = paths.firstOrNull { it.trim().lowercase().endsWith(".torrent") }
