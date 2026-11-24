// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/**
 * Lets the user choose files of [mimeTypes]. Call the returned function to open the picker; [onResult] gets the chosen files
 * (Android: content URIs), empty when nothing was chosen. With [multiple] = false the picker only lets one file be chosen.
 */
@Composable
expect fun rememberFilePicker(mimeTypes: List<String>, multiple: Boolean = true, onResult: (List<String>) -> Unit): () -> Unit

/**
 * Lets the user choose one piece of content of [mimeType] to read once (a picture to search with). On Android this is the
 * "get content" chooser, which also offers the gallery and other apps, not the documents picker of [rememberFilePicker].
 * [onResult] gets what was chosen (Android: a content URI), or null when nothing was.
 */
@Composable
expect fun rememberContentPicker(mimeType: String, onResult: (String?) -> Unit): () -> Unit
