// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.nijiyome.extensionserver.ExtensionServerRepository
import app.nijiyome.model.DownloadRepository
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.SocialRepository
import org.koin.compose.getKoin
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import kotlin.reflect.KClass

/**
 * Which destinations this platform can open: a route is offered when every service its screen needs is registered. On Android
 * all of them are, so every route is offered; desktop still lacks some (see [needs]), and those destinations are left out of
 * the rail and answer "not available yet" when another screen asks for them, instead of failing while resolving their ViewModel.
 */
class RouteAvailability(private val has: (KClass<*>) -> Boolean) {
    private val known = mutableMapOf<KClass<*>, Boolean>()

    fun offers(route: Route): Boolean {
        val anyOf = route.needsAnyOf()
        return route.needs().all(::present) && (anyOf.isEmpty() || anyOf.any(::present))
    }

    private fun present(service: KClass<*>): Boolean = known.getOrPut(service) { has(service) }

    /** Looks at what is registered in [koin]; nothing is created by asking. */
    constructor(koin: Koin) : this(koin::declares)
}

@Composable
internal fun rememberRouteAvailability(): RouteAvailability {
    val koin = getKoin()
    return remember(koin) { RouteAvailability(koin) }
}

/**
 * The services a destination cannot open without: the ones its ViewModel asks the dependency graph for with a plain `get()`.
 * Everything else a screen uses is optional there and the screen hides what depends on it.
 */
// Desktop (by design): la página "Tú" pide el cliente de Link (YouViewModel lo toma con get()), así que no se ofrece; Link sí,
// por [needsAnyOf]. Con esos servicios registrados vuelven solas (Descargas, Perfil, notificaciones de AniList, Listas y cuentas y los
// servidores de extensiones, a los que escritorio entra desde Extensiones, ya volvieron).
internal fun Route.needs(): List<KClass<*>> = when (this) {
    is Route.Downloads -> listOf(DownloadRepository::class)
    Route.ExtensionServers -> listOf(ExtensionServerRepository::class)
    Route.You -> listOf(ListsRepository::class, LinkRepository::class)
    // Settings itself always opens; its "Listas y cuentas" section exists only with the lists service.
    is Route.SettingsSection -> if (section == SettingsSection.ACCOUNTS) listOf(ListsRepository::class) else emptyList()
    is Route.Profile, is Route.ActivityThread, Route.Notifications -> listOf(SocialRepository::class)
    else -> emptyList()
}

/**
 * Services of which one is enough: the screen picks the side it finds. Link: the client (`LinkRepository`, Android) or the
 * server (`LinkServerController`, desktop); `LinkedDeviceScreen` draws whichever is registered.
 */
internal fun Route.needsAnyOf(): List<KClass<*>> = when (this) {
    Route.Link, Route.Linked -> listOf(LinkRepository::class, LinkServerController::class)
    else -> emptyList()
}

/** true when a definition of [service] (its own type or one it is bound to) is registered, whether or not it was created yet. */
@OptIn(KoinInternalApi::class)
private fun Koin.declares(service: KClass<*>): Boolean = instanceRegistry.instances.values.any { factory ->
    factory.beanDefinition.primaryType == service || service in factory.beanDefinition.secondaryTypes
}
