// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.navigation3.runtime.entryProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/** routeContentKey mirrors navigation3's internal default content key; if a navigation3 update changes it, tap-to-top breaks. */
class RouteContentKeyTest {
    @Test
    fun matchesTheEntryProviderKey() {
        val provider = entryProvider<Any> {
            entry<Route.Home> { }
            entry<Route.Library> { }
        }
        listOf<Any>(Route.Home, Route.Library).forEach { route ->
            assertEquals(provider(route).contentKey, routeContentKey(route))
        }
    }
}
