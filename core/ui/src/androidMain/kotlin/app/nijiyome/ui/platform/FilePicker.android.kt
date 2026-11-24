// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberFilePicker(mimeTypes: List<String>, multiple: Boolean, onResult: (List<String>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val types = rememberUpdatedState(mimeTypes)
    // Two launchers, one used: the system picker of OpenDocument does not offer multi-selection at all.
    val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        callback.value(uris.map { it.toString() })
    }
    val one = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        callback.value(listOfNotNull(uri?.toString()))
    }
    return remember(many, one, multiple) { { (if (multiple) many else one).launch(types.value.toTypedArray()) } }
}

@Composable
actual fun rememberContentPicker(mimeType: String, onResult: (String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val type = rememberUpdatedState(mimeType)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> callback.value(uri?.toString()) }
    return remember(launcher) { { launcher.launch(type.value) } }
}
