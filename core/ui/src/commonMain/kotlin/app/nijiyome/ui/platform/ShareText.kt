// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/** Returns a function that shares a plain text (Android: the system share sheet titled `chooserTitle`); nothing happens when it cannot. */
@Composable
expect fun rememberShareText(): (text: String, chooserTitle: String) -> Unit

/**
 * True where [rememberShareText] opens a system share sheet (Android). Desktop has none (the text only goes to the clipboard), so a
 * screen that already copied the text does not offer "Enviar" there.
 */
expect val systemShareAvailable: Boolean
