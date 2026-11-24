// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberFilePicker(mimeTypes: List<String>, multiple: Boolean, onResult: (List<String>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember(mimeTypes, multiple) { { SwingChooser.chooseFiles(mimeTypes, multiple) { callback.value(it) } } }
}

@Composable
actual fun rememberContentPicker(mimeType: String, onResult: (String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember(mimeType) { { SwingChooser.chooseFiles(listOf(mimeType), multiple = false) { callback.value(it.firstOrNull()) } } }
}
