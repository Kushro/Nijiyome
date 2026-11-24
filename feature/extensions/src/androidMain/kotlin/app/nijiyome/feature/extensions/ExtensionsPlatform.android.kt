package app.nijiyome.feature.extensions

import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberDocumentDisplayName(): (uri: String) -> String? {
    val context = LocalContext.current
    return remember(context) {
        { uri ->
            runCatching {
                context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        }
    }
}

@Composable
internal actual fun platformRelativeTimeSpan(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

internal actual val kaiDropAndPaths: Boolean = false

@Composable
internal actual fun hiddenBrowserAvailable(): Boolean = true
