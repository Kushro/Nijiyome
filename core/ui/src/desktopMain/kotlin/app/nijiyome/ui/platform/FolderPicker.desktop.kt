// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberFolderPicker(onResult: (String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember { { SwingChooser.chooseFolder { callback.value(it) } } }
}
