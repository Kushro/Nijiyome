// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareText(): (text: String, chooserTitle: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text, chooserTitle ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            runCatching {
                context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

actual val systemShareAvailable: Boolean = true
