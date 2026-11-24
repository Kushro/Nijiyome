// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile

@Composable
internal actual fun rememberStorageAccess(): StorageAccess {
    val context = LocalContext.current
    return remember(context) {
        object : StorageAccess {
            override fun keepFolderAccess(tree: String) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        Uri.parse(tree),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }

            override fun folderName(tree: String): String? =
                runCatching { DocumentFile.fromTreeUri(context, Uri.parse(tree))?.name }.getOrNull()

            override fun documentName(uri: String): String? {
                val parsed = Uri.parse(uri)
                val name = runCatching {
                    context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull()
                return name ?: parsed.lastPathSegment
            }

            // The picker's content URI is what the engine reads.
            override fun documentUri(picked: String): String = picked
        }
    }
}

@Composable
internal actual fun relativeTimeSpan(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

internal actual val asksNotificationPermission: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal actual fun fullScreenDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)

internal actual val desktopTorrentUi: Boolean = false

@Composable
internal actual fun Modifier.torrentFileDrop(onHover: (Boolean) -> Unit, onDrop: (path: String) -> Unit): Modifier = this
