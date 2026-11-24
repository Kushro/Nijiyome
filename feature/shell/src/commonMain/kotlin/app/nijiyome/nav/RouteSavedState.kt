// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclassesOfSealed

/**
 * How the back stack is saved and restored. Outside Android there is no reflection to find the serializer of a key, so every
 * [Route] is registered here; the same configuration is used on Android, so both platforms save the stack the same way.
 * [Route] is sealed: a new route is picked up without touching this file (RouteSavedStateTest checks it).
 */
@OptIn(ExperimentalSerializationApi::class)
internal val RouteSavedState: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclassesOfSealed<Route>() }
    }
}
