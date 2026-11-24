// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/** Android has no file manager to open an app folder in: the screens do not offer "Abrir carpeta". */
@Composable
actual fun rememberOpenFolder(): ((path: String) -> Unit)? = null
