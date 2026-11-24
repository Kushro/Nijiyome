package app.nijiyome.feature.extensions

import androidx.compose.runtime.Composable

/**
 * Display name of a document chosen with `rememberFilePicker` (Android: a SAF content URI), or null when the platform does not say.
 * Used to reject a file that is not a `.kai` before installing it.
 */
@Composable
internal expect fun rememberDocumentDisplayName(): (uri: String) -> String?

/** The platform's relative time of [epochMs] against now ("Hace 3 horas", "Ayer"), as on the extension card's footer. */
@Composable
internal expect fun platformRelativeTimeSpan(epochMs: Long): String

/**
 * Desktop (Tatakai Next's install panel): a `.kai` can be dropped on the install panel or its path pasted in the URL dialog. Android
 * keeps its document picker and URL dialog as they were.
 */
internal expect val kaiDropAndPaths: Boolean

/**
 * False where bundles get no hidden browser ([app.nijiyome.extensions.BrowserRuntime]): an extension that asks for one shows that it
 * is limited here. True on Android. Desktop (1.1, F4): while a Chrome / Edge / Chromium is found (it can change in Settings).
 */
@Composable
internal expect fun hiddenBrowserAvailable(): Boolean

/** A pasted path of a `.kai` file (quotes and `file://` removed), or null when [text] is not one. */
internal fun pastedKaiPath(text: String): String? =
    app.nijiyome.ui.platform.cleanPastedPath(text)?.takeIf { !isHttpUrl(it) && it.endsWith(".kai", ignoreCase = true) }
