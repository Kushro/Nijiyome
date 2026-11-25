// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.navigation3.runtime.NavKey
import app.nijiyome.model.DownloadRepository
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.SettingsSection
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RouteSavedStateTest {
    /** Every route nested in [Route] can be saved with the back stack (a missing one would fail when the stack is saved). */
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun everyRouteHasASerializerInTheBackStackConfiguration() {
        val routes = Route::class.java.declaredClasses.filter { Route::class.java.isAssignableFrom(it) }
        assertTrue(routes.size >= 30, "found only ${routes.size} routes: the nested classes are not being read")
        val module = RouteSavedState.serializersModule
        for (route in routes) {
            assertNotNull(module.getPolymorphic(NavKey::class, route.name.replace('$', '.')), "no serializer registered for ${route.name}")
        }
    }

    @Test
    fun aRouteIsOfferedOnlyWithItsServices() {
        val nothing = RouteAvailability { false }
        assertTrue(nothing.offers(Route.Home))
        assertTrue(nothing.offers(Route.Settings))
        assertTrue(nothing.offers(Route.SettingsSection(SettingsSection.ABOUT)))
        assertFalse(nothing.offers(Route.SettingsSection(SettingsSection.ACCOUNTS)))
        assertFalse(nothing.offers(Route.Linked))
        assertFalse(nothing.offers(Route.Downloads()))
        assertEquals(
            listOf(Route.Home, Route.Search, Route.Calendar, Route.Library, Route.Manga(), Route.Novels(), Route.Extensions, Route.Settings),
            topLevelRoutes(wide = true).filter(nothing::offers),
        )
        val everything = RouteAvailability { true }
        assertEquals(topLevelRoutes(wide = true), topLevelRoutes(wide = true).filter(everything::offers))
        assertEquals(topLevelRoutes(wide = false), topLevelRoutes(wide = false).filter(everything::offers))
        val onlyLink = RouteAvailability { it == LinkRepository::class }
        assertTrue(onlyLink.offers(Route.Linked))
        assertFalse(onlyLink.offers(Route.You)) // needs the lists service
        assertFalse(onlyLink.offers(Route.Downloads()) || RouteAvailability { it == DownloadRepository::class }.offers(Route.Linked))
    }

    @Test
    fun linkIsOfferedWithEitherSideOfLink() {
        // Desktop: the Link server alone opens Link (its server side).
        val server = RouteAvailability { it == LinkServerController::class }
        assertTrue(server.offers(Route.Linked))
        assertTrue(server.offers(Route.Link))
        assertFalse(server.offers(Route.You)) // the lists service is still needed
        assertTrue(RouteAvailability { it == app.nijiyome.model.ListsRepository::class }.offers(Route.You))
        val client = RouteAvailability { it == LinkRepository::class }
        assertTrue(client.offers(Route.Linked))
        assertFalse(RouteAvailability { false }.offers(Route.Link))
    }
}
