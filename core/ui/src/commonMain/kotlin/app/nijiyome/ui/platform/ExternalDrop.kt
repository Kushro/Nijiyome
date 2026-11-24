// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What was dropped on a screen from outside the app: local file paths, and the dropped text (a link from a browser). */
data class ExternalDrop(val files: List<String>, val text: String?)

/**
 * Accepts files and text dragged from outside the app (Tatakai Next's drop zones). Desktop: the system drag and drop, [hovering] is
 * true while something is dragged over; [onDrop] returns whether it was used. Android: does nothing (the screens keep their pickers).
 */
@Composable
expect fun Modifier.externalDropTarget(hovering: (Boolean) -> Unit = {}, onDrop: (ExternalDrop) -> Boolean): Modifier

/**
 * A path pasted from a file manager: Windows' "Copy as path" adds quotes, others a `file://` URI or spaces around it
 * (Tatakai Next `clean_path`). Null when nothing is left.
 */
fun cleanPastedPath(text: String): String? {
    var t = text.trim()
    while (t.length >= 2 && ((t.first() == '"' && t.last() == '"') || (t.first() == '\'' && t.last() == '\''))) t = t.substring(1, t.length - 1).trim()
    if (t.startsWith("file://")) t = runCatching { java.io.File(java.net.URI(t)).path }.getOrDefault(t.removePrefix("file://"))
    return t.ifEmpty { null }
}
