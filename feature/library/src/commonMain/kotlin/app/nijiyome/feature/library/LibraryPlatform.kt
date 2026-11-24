package app.nijiyome.feature.library

import androidx.compose.runtime.Composable

/** "Hace 5 min" / "5 minutes ago" for [epochMs] in the app locale. Android: `DateUtils`; desktop: core:ui's relative time. */
@Composable
internal expect fun platformRelativeTimeSpan(epochMs: Long): String
