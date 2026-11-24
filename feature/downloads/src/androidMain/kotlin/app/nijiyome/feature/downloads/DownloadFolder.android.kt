// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile

@Composable
internal actual fun rememberKeepFolderAccess(): (folder: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { folder ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    Uri.parse(folder),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }
}

@Composable
internal actual fun rememberDownloadFolderLabel(treeUri: String?): String {
    val context = LocalContext.current
    return remember(treeUri) {
        treeUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() ?: it }
            ?: (context.getExternalFilesDir("downloads")?.absolutePath ?: "")
    }
}

internal actual val desktopDownloadsUi: Boolean = false
