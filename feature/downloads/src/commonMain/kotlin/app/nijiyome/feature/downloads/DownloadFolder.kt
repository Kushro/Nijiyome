// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import androidx.compose.runtime.Composable

/** Keeps access to a folder chosen with `rememberFolderPicker` across restarts (Android: a persistable SAF grant). */
@Composable
internal expect fun rememberKeepFolderAccess(): (folder: String) -> Unit

/** What Settings › Descargas shows for the download folder: the chosen folder's name, or the app's own folder when [treeUri] is null. */
@Composable
internal expect fun rememberDownloadFolderLabel(treeUri: String?): String

/**
 * true on desktop: Settings › Descargas and Downloads follow Tatakai Next's desktop app (no Wi-Fi switch nor battery texts, a speed
 * limit, the watched library folders, "Abrir carpeta"). Android: false, its screens unchanged.
 */
internal expect val desktopDownloadsUi: Boolean
