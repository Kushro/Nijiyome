// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.nijiyome.database.HistoryDao
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.PcPlayerHub
import app.nijiyome.model.PcRemoteCommand
import app.nijiyome.model.PlayerArgs
import app.nijiyome.nav.Route
import app.nijiyome.server.desktop.DesktopLinkHost
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.getKoin

/**
 * "Continuar en la PC" (`POST /v1/control` `open` from a paired phone): the player opens on that episode at the phone's position, through
 * the same [routes] the shell takes start-up links from. The title comes from the history row the phone pushed just before.
 */
@Composable
internal fun LinkRemoteOpens(routes: MutableStateFlow<Route?>) {
    val koin = getKoin()
    LaunchedEffect(koin) {
        val hub = koin.getOrNull<PcPlayerHub>() ?: return@LaunchedEffect
        val history = koin.getOrNull<HistoryDao>()
        hub.opens.collect { open ->
            val title = runCatching { history?.get(HistoryEntry.episodeIdOf(open.mediaId, open.episode))?.title }.getOrNull()
            routes.value = linkOpenRoute(open, title)
        }
    }
}

/** The player route of a remote `open`. Pure. */
internal fun linkOpenRoute(open: PcRemoteCommand.Open, title: String?): Route.Player = Route.Player(
    PlayerArgs(
        media = DesktopLinkHost.refOf(open.mediaId),
        title = title.orEmpty(),
        episode = open.episode,
        startMs = open.positionSecs * 1000L,
    ),
)
