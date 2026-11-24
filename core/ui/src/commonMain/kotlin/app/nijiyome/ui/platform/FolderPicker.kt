// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/**
 * Lets the user choose a folder. Call the returned function to open the picker; [onResult] gets the folder (Android: the SAF tree URI)
 * or null when nothing was chosen.
 */
@Composable
expect fun rememberFolderPicker(onResult: (String?) -> Unit): () -> Unit
