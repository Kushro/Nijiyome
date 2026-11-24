package app.nijiyome.feature.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import app.nijiyome.extensions.browser.DesktopHiddenBrowser
import org.koin.mp.KoinPlatformTools
import app.nijiyome.ui.format.relativeTime

/** On desktop a picked document is a file path: its name is the last path segment. */
@Composable
internal actual fun rememberDocumentDisplayName(): (uri: String) -> String? =
    { uri -> runCatching { java.io.File(uri).name }.getOrNull()?.takeIf { it.isNotEmpty() } }

/** Desktop has no `DateUtils`: the shared relative time of core:ui ("hace 3 h", "ayer"). */
@Composable
internal actual fun platformRelativeTimeSpan(epochMs: Long): String = relativeTime(epochMs)

internal actual val kaiDropAndPaths: Boolean = true

/** 1.1 F4: the user's Chrome / Edge / Chromium (DesktopHiddenBrowser); without one the extension card says it is limited, as before. */
@Composable
internal actual fun hiddenBrowserAvailable(): Boolean {
    val browser = remember { KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<DesktopHiddenBrowser>() } ?: return false
    return browser.state.collectAsState().value.available
}
