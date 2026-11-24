package app.nijiyome.feature.library

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun platformRelativeTimeSpan(epochMs: Long): String {
    LocalContext.current // recompose on configuration (locale) changes
    return DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}
