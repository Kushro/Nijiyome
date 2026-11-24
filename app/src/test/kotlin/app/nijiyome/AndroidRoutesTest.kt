// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome

import app.nijiyome.model.SettingsSection
import app.nijiyome.nav.Route
import app.nijiyome.nav.RouteAvailability
import app.nijiyome.nav.topLevelRoutes
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidRoutesTest {
    /**
     * The shared shell hides a destination when a service its screen needs is not registered (desktop, during the migration).
     * The Android app registers all of them: nothing may disappear from its bar or rail, or answer "not available".
     */
    @Test
    fun theAndroidAppOffersEveryDestination() {
        // Declarations only: nothing is created, so no Android context is needed.
        val app = koinApplication(createEagerInstances = false) { modules(nijiyomeModules) }
        try {
            val available = RouteAvailability(app.koin)
            assertEquals(topLevelRoutes(wide = true), topLevelRoutes(wide = true).filter(available::offers))
            assertEquals(topLevelRoutes(wide = false), topLevelRoutes(wide = false).filter(available::offers))
            val reachedFromOtherScreens = listOf(
                Route.Link, Route.Linked, Route.ExtensionServers, Route.Notifications, Route.ActivityThread(1), Route.Profile("someone"),
                Route.Downloads(), Route.You,
            ) + SettingsSection.entries.map { Route.SettingsSection(it) }
            for (route in reachedFromOtherScreens) assertTrue(available.offers(route), "$route is not offered on Android")
        } finally {
            app.close()
        }
    }
}
