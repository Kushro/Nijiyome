// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/**
 * "Abrir carpeta": shows a folder (or the folder holding a file) in the system file manager. Null where there is none to open
 * (Android: the screens hide the action). The path is a plain path or a `file:` URI.
 */
@Composable
expect fun rememberOpenFolder(): ((path: String) -> Unit)?
