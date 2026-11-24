// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import org.jetbrains.skia.FontMgr
import app.nijiyome.ui.platform.ViewerEscape
import org.koin.compose.koinInject
import java.io.File
import java.net.URI
import java.util.Locale

@Composable
internal actual fun currentUiLocale(): Locale = Locale.getDefault()

internal actual fun fontFromFile(path: String): Font = androidx.compose.ui.text.platform.Font(File(path))

/** Where imported reader fonts are copied on desktop (`<dataDir>/fonts/novel`). */
class NovelFontsDir(val dir: File)

@Composable
internal actual fun rememberFontImporter(): (picked: String) -> String? {
    val fonts = koinInject<NovelFontsDir>()
    return { picked -> importFontFile(pickedFile(picked), fonts.dir) }
}

/** A path from the desktop file chooser, or a `file://` URI. */
private fun pickedFile(picked: String): File = if (picked.startsWith("file:")) File(URI(picked)) else File(picked)

/**
 * Copies a TTF / OTF to [dir] (checklist novel.reader.custom-font); null when it is not a font, is larger than [MAX_FONT_BYTES]
 * (checked before reading, and never more than that is read) or cannot be copied.
 */
internal fun importFontFile(source: File, dir: File): String? = runCatching {
    if (source.length() > MAX_FONT_BYTES) return null
    val bytes = source.inputStream().use { it.readNBytes(MAX_FONT_BYTES + 1) }
    if (bytes.size > MAX_FONT_BYTES || !FontFiles.isFont(bytes)) return null
    dir.mkdirs()
    val target = File(dir, FontFiles.safeName(source.name, bytes))
    target.writeBytes(bytes)
    target.absolutePath
}.getOrNull()

/** Larger than any reading font, CJK collections included (Noto Sans CJK is about 20 MB per file). */
internal const val MAX_FONT_BYTES = 64 * 1024 * 1024

// Skia's font manager is the system's (fontconfig on Linux, DirectWrite on Windows): a family it matches is installed.
@OptIn(ExperimentalTextApi::class)
internal actual fun installedFontFamily(name: String): FontFamily? = runCatching {
    val set = FontMgr.default.matchFamily(name)
    try {
        if (set.count() > 0) FontFamily(SystemFont(name)) else null
    } finally {
        set.close()
    }
}.getOrNull()

internal actual fun installedFontFamilies(): List<String> = runCatching {
    val mgr = FontMgr.default
    (0 until mgr.familiesCount).map(mgr::getFamilyName).filter { it.isNotBlank() && !it.startsWith(".") }.distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}.getOrDefault(emptyList())

internal actual val installedFontsOffered: Boolean = true

@Composable
internal actual fun Modifier.readerEscapeKey(refocusKey: Any?, refocus: Boolean, onEscape: () -> Unit): Modifier {
    val focus = remember { FocusRequester() }
    val current = rememberUpdatedState(onEscape)
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
    LaunchedEffect(focus, refocusKey) { if (refocus) runCatching { focus.requestFocus() } }
    // An Esc that reaches the window because the focus was lost anyway runs the same Esc.
    ViewerEscape(focus) { current.value() }
    return focusRequester(focus).focusable().onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
            current.value()
            true
        } else {
            false
        }
    }
}
