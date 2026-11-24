// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.util.Locale

@Composable
internal actual fun currentUiLocale(): Locale = LocalConfiguration.current.locales[0]

internal actual fun fontFromFile(path: String): Font = Font(File(path))

@Composable
internal actual fun rememberFontImporter(): (picked: String) -> String? {
    val context = LocalContext.current
    return remember(context) { { picked -> importFont(context, Uri.parse(picked)) } }
}

/** Copies a picked TTF / OTF to `filesDir/fonts/novel/` (checklist novel.reader.custom-font); null when it is not a font. */
private fun importFont(context: Context, uri: Uri): String? {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "font.ttf"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (!FontFiles.isFont(bytes)) return null
    val dir = File(context.filesDir, FontFiles.DIR).apply { mkdirs() }
    val target = File(dir, FontFiles.safeName(name, bytes))
    target.writeBytes(bytes)
    return target.absolutePath
}

// Android ships neither Georgia, Lora nor OpenDyslexic and the reader never looked fonts up by name: the presets keep their fallbacks.
internal actual fun installedFontFamily(name: String): FontFamily? = null

internal actual fun installedFontFamilies(): List<String> = emptyList()

internal actual val installedFontsOffered: Boolean = false

@Composable
internal actual fun Modifier.readerEscapeKey(refocusKey: Any?, refocus: Boolean, onEscape: () -> Unit): Modifier = this
