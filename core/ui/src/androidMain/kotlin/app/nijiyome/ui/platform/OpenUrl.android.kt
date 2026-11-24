// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** `ACTION_VIEW` with `FLAG_ACTIVITY_NEW_TASK`; nothing happens when no app can open it. */
@Composable
internal actual fun rememberPlatformUrlOpener(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
