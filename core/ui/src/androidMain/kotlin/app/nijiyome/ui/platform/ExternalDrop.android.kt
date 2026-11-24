// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Android screens keep their system pickers; nothing is dropped on them from outside.
@Composable
actual fun Modifier.externalDropTarget(hovering: (Boolean) -> Unit, onDrop: (ExternalDrop) -> Boolean): Modifier = this
