// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

private const val ICON_RESOURCE = "/icons/nijiyome.png"

/** The window and taskbar icon (packaging/icons/nijiyome-256.png), or null if the resource is missing. */
internal fun appIcon(): Painter? =
    AppPaths::class.java.getResourceAsStream(ICON_RESOURCE)?.use { stream ->
        BitmapPainter(Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
    }
