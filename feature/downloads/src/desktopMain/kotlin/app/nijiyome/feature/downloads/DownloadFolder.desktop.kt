// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.nijiyome.model.DefaultDownloadFolder
import org.koin.compose.getKoin

/** Desktop pickers return plain folder paths: there is no access grant to keep. */
@Composable
internal actual fun rememberKeepFolderAccess(): (folder: String) -> Unit = { }

/** The chosen folder's path, or the desktop default (`<Downloads>/Nijiyome/Episodes`) when none was chosen. */
@Composable
internal actual fun rememberDownloadFolderLabel(treeUri: String?): String {
    val koin = getKoin()
    return remember(treeUri, koin) { treeUri ?: koin.getOrNull<DefaultDownloadFolder>()?.path.orEmpty() }
}

internal actual val desktopDownloadsUi: Boolean = true
