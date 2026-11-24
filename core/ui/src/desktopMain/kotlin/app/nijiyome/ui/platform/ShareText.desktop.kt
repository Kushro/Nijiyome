// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.ui_copied_to_clipboard
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.jetbrains.compose.resources.stringResource

/**
 * A desktop has no system share sheet: the text goes to the clipboard and the app's snackbar says so ("Copiado al portapapeles").
 * `chooserTitle` is not shown. Nothing is said when the clipboard cannot be reached (a headless session).
 */
@Composable
actual fun rememberShareText(): (text: String, chooserTitle: String) -> Unit {
    val snackbar = LocalAppSnackbar.current
    val copied = stringResource(Res.string.ui_copied_to_clipboard)
    return remember(snackbar, copied) {
        { text, _ -> if (copyToSystemClipboard(text)) snackbar.show(copied) }
    }
}

/** Puts [text] on the system clipboard; false when there is none (headless). */
internal fun copyToSystemClipboard(text: String): Boolean = runCatching {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}.isSuccess

actual val systemShareAvailable: Boolean = false
