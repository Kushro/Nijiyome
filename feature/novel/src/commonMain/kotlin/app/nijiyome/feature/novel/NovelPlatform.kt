// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.util.Locale

/** The locale of the UI. Android: `LocalConfiguration.current.locales[0]`. Desktop: `Locale.getDefault()`. */
@Composable
internal expect fun currentUiLocale(): Locale

/** A font loaded from a TTF / OTF file on disk (throws when it cannot be read). */
internal expect fun fontFromFile(path: String): Font

/**
 * Copies a picked TTF / OTF (the string given by `rememberFilePicker`) to `filesDir/fonts/novel/` (checklist novel.reader.custom-font)
 * and returns its path; null when it is not a font. Blocking: call it off the main thread.
 */
@Composable
internal expect fun rememberFontImporter(): (picked: String) -> String?

/** A font family installed on this computer, by name; null when there is none (always on Android, whose presets stay as they were). */
internal expect fun installedFontFamily(name: String): FontFamily?

/** The names of the font families installed on this computer, sorted (empty on Android). */
internal expect fun installedFontFamilies(): List<String>

/** True where the reader offers installed font families and the `mono` preset (desktop). */
internal expect val installedFontsOffered: Boolean

/**
 * Desktop (Tatakai Next): Esc in the reader calls [onEscape] (close the sheet, then leave fullscreen, then the reader); the reader
 * takes the keyboard focus for it, and takes it back whenever [refocusKey] changes while [refocus] is true (the bars hid or a sheet
 * closed, taking a focused button with them). Android: nothing (Back does that through `PlatformBackHandler`).
 */
@Composable
internal expect fun Modifier.readerEscapeKey(refocusKey: Any?, refocus: Boolean, onEscape: () -> Unit): Modifier
