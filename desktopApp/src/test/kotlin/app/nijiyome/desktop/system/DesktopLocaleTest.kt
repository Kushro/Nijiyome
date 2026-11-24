// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.model.AppLanguage
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hito 6 (D1): Ajustes › Idioma on desktop (social-cuentas-listas-3) and the title bar chips (sistema-ajustes-1 / -3). */
class DesktopLocaleTest {
    @Test
    fun theChosenLanguageBecomesTheProcessLocaleAndSystemGoesBack() {
        val before = Locale.getDefault()
        try {
            val system = DesktopLocale.system
            assertEquals(Locale.forLanguageTag("es"), DesktopLocale.localeFor(AppLanguage.ES, system))
            assertEquals(Locale.forLanguageTag("en"), DesktopLocale.localeFor(AppLanguage.EN, system))
            assertEquals(Locale.forLanguageTag("fr-CA"), DesktopLocale.localeFor(AppLanguage.SYSTEM, Locale.forLanguageTag("fr-CA")))

            DesktopLocale.apply(AppLanguage.ES)
            assertEquals("es", Locale.getDefault().language)
            assertFalse(DesktopLocale.apply(AppLanguage.ES), "already applied")
            assertTrue(DesktopLocale.apply(AppLanguage.EN))
            assertEquals("en", Locale.getDefault().language)
            DesktopLocale.apply(AppLanguage.SYSTEM)
            assertEquals(system, Locale.getDefault())
        } finally {
            Locale.setDefault(before)
        }
    }

    /** Revisión del hito 6: the tray menu follows the chosen language, not the default locale of the moment it is built. */
    @Test
    fun theTrayMenuFollowsTheChosenLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals(TrayTexts("Mostrar Nijiyome", "Salir"), TrayTexts.forLocale(DesktopLocale.localeFor(AppLanguage.ES)))
            Locale.setDefault(Locale.forLanguageTag("es"))
            assertEquals(TrayTexts("Show Nijiyome", "Quit"), TrayTexts.forLocale(DesktopLocale.localeFor(AppLanguage.EN)))
            assertEquals(TrayTexts("Mostrar Nijiyome", "Salir"), TrayTexts.forLocale(DesktopLocale.localeFor(AppLanguage.SYSTEM, Locale.forLanguageTag("es-AR"))))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun theTitleBarShowsLinkAndOffline() {
        assertEquals("Nijiyome", windowTitle(online = true, linkPort = null, spanish = true))
        assertEquals("Nijiyome · Link :47411", windowTitle(online = true, linkPort = 47411, spanish = true))
        assertEquals("Nijiyome · Link :47411 · Sin conexión", windowTitle(online = false, linkPort = 47411, spanish = true))
        assertEquals("Nijiyome · Offline", windowTitle(online = false, linkPort = 0, spanish = false))
    }
}

/** The density handed to the screens is the same, but a new value per language (that is what makes them compose again). */
class LanguageDensityTest {
    @Test
    fun sameDensityNewValue() {
        val base = androidx.compose.ui.unit.Density(1.5f, 1.1f)
        val es = LanguageDensity(base, "es")
        assertEquals(1.5f, es.density)
        assertEquals(1.1f, es.fontScale)
        assertFalse(es == LanguageDensity(base, "es") as Any, "identity: a new language is always a change")
    }
}
