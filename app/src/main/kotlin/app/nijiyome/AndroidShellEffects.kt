// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.nijiyome.model.AppSettings
import app.nijiyome.model.SettingsRepository
import org.koin.compose.koinInject

/**
 * What the shared shell (`NijiyomeApp`) cannot do itself because it needs the Activity: the system bar icon style and the
 * per-app language. Passed to it as `platformEffects`; composed first, outside the theme, in this order.
 */
@Composable
fun AndroidShellEffects(settings: AppSettings, dark: Boolean) {
    SystemBarsFollowTheme(dark)
    PerAppLanguage(settings, koinInject<SettingsRepository>())
}

/** Status / navigation bar icons follow the in-app theme mode, not only the system one. */
@Composable
private fun SystemBarsFollowTheme(dark: Boolean) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    DisposableEffect(activity, dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
        )
        onDispose { }
    }
}

/**
 * Settings › Idioma -> LocaleManager (33+) / recreate with [AppLocale.wrap] (< 33). On the first composition a language chosen in the
 * system per-app language screen (33+) wins and is copied into the settings, so the two never fight.
 */
@Composable
private fun PerAppLanguage(settings: AppSettings, repo: SettingsRepository) {
    val activity = LocalContext.current.findActivity() ?: return
    // Not an effect key: flipping it must not cancel the running effect (it may be inside repo.setLanguage).
    var adopted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.language) {
        if (!adopted) {
            adopted = true
            val system = AppLocale.systemChoice(activity)
            if (system != null && system != settings.language) {
                repo.setLanguage(system)
                return@LaunchedEffect
            }
        }
        AppLocale.apply(activity, settings.language)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
