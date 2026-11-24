// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.nijiyome.model.AppLanguage
import java.util.Locale

/**
 * Ajustes › Idioma and the wizard's language on desktop (Android: `AppLocale`). The JVM default locale is what Compose Resources
 * (`stringResource`, `getString`), date formats and the desktop's own texts (tray, Discord, notices) read, so it is set here; the
 * system's locale at start is what "Sistema" goes back to.
 */
object DesktopLocale {
    /** The locale the system gave this process, before any choice was applied. */
    val system: Locale = Locale.getDefault()

    /** The locale for [language]: the system's for [AppLanguage.SYSTEM]. Pure. */
    fun localeFor(language: AppLanguage, system: Locale = this.system): Locale = language.tag?.let(Locale::forLanguageTag) ?: system

    /** Makes [language] the process locale; true when it changed. */
    fun apply(language: AppLanguage): Boolean {
        val target = localeFor(language)
        if (Locale.getDefault() == target) return false
        Locale.setDefault(target)
        return true
    }
}

/**
 * Applies [language] to [content] without starting the screens over. Compose Resources reads the default locale while a string is
 * composed (its environment also reads [LocalDensity]), so on a new language the subtree gets a new [LocalDensity] value: the same
 * density in a new wrapper. [LocalDensity] is a static composition local, so everything under it composes again and every string is
 * read in the new locale, while what the screens remember (navigation, the wizard's step, ViewModels) stays.
 */
@Composable
fun AppLanguageProvider(language: AppLanguage, content: @Composable () -> Unit) {
    DesktopLocale.apply(language)
    val tag = DesktopLocale.localeFor(language).toLanguageTag()
    val base = LocalDensity.current
    val density = remember(base, tag) { LanguageDensity(base, tag) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/** [base] unchanged, as a value that differs per language (identity equality), see [AppLanguageProvider]. */
internal class LanguageDensity(private val base: Density, val tag: String) : Density by base
