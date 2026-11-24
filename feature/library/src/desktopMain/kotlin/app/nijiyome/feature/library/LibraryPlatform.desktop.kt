package app.nijiyome.feature.library

import androidx.compose.runtime.Composable
import app.nijiyome.ui.format.relativeTime

/** Desktop has no `DateUtils`: the shared relative time of core:ui ("hace 3 h", "ayer"). */
@Composable
internal actual fun platformRelativeTimeSpan(epochMs: Long): String = relativeTime(epochMs)
