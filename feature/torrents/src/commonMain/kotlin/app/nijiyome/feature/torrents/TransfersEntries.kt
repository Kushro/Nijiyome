// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.feature.torrents.ui.AddTorrentSheet
import app.nijiyome.feature.torrents.ui.DetailContent
import app.nijiyome.feature.torrents.vm.AddTorrentViewModel
import app.nijiyome.feature.torrents.vm.TorrentDetailViewModel
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.torrent.TorrentSection
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Sparkles redesign, lane S8: the torrent pieces that Transferencias (`DownloadsScreen` of :feature:downloads) shows. The two modules
// do not see each other, so the shell hands these to `DownloadsScreen(torrentSlots = TransfersTorrentSlots(...))`. Only core types
// cross the boundary.

/**
 * The detail of one torrent (Resumen, Archivos, Pares, Rastreadores, its menu and the remove confirmation) for the detail pane of
 * Transferencias. [showBack] = phone (stacked navigation): the pane draws its own bar with the torrent's name and a back arrow.
 * [onBack] also runs after the torrent is removed.
 */
@Composable
fun TransfersTorrentDetail(
    torrentId: String,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    showBack: Boolean,
    modifier: Modifier = Modifier,
    initialSection: TorrentSection = TorrentSection.OVERVIEW,
) {
    val viewModel: TorrentDetailViewModel = koinViewModel(key = "torrent-$torrentId", parameters = { parametersOf(torrentId) })
    DetailContent(viewModel, initialSection, onBack, onPlay, modifier, showBack = showBack)
}

/**
 * "Añadir torrent" for Transferencias: magnet, info-hash, `.torrent` (picked, dropped on the sheet or handed over by the system), the
 * series link, first episode and offset, the file selection step (desktop) and "Conservar y descargar". A `magnet:` / `.torrent`
 * opened from outside the app asks to open the sheet through [onOpenChange]. [onAdded] gets the new torrent id.
 */
@Composable
fun TransfersAddTorrentHost(open: Boolean, onOpenChange: (Boolean) -> Unit, onAdded: (torrentId: String) -> Unit) {
    val vm: AddTorrentViewModel = koinViewModel()
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val currentOpenChange by rememberUpdatedState(onOpenChange)
    LaunchedEffect(incoming) {
        if (incoming) {
            currentOpenChange(true)
            vm.incomingShown()
        }
    }
    // A fresh form each time the user opens it (an incoming link already filled it and set `incoming`).
    LaunchedEffect(open) { if (open && !incoming && vm.state.value.source.isEmpty()) vm.reset() }
    if (open) {
        AddTorrentSheet(
            vm = vm,
            onDismiss = { onOpenChange(false) },
            onAdded = { id ->
                onOpenChange(false)
                onAdded(id)
            },
        )
    }
}
