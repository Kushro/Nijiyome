// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents

import app.nijiyome.feature.torrents.vm.AddTorrentViewModel
import app.nijiyome.feature.torrents.vm.DebridViewModel
import app.nijiyome.feature.torrents.vm.TorrentDetailViewModel
import app.nijiyome.feature.torrents.vm.TorrentHistoryViewModel
import app.nijiyome.feature.torrents.vm.TorrentDownloadsViewModel
import app.nijiyome.feature.torrents.vm.TorrentLibraryViewModel
import app.nijiyome.feature.torrents.vm.TorrentSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the torrent client (name FIXED: :app lists it). TorrentDetailViewModel takes the torrent id as parameter. */
val torrentsFeatureModule: Module = module {
    viewModel { TorrentLibraryViewModel(repository = get()) }
    viewModel { params -> TorrentDetailViewModel(torrentId = params.get<String>(), repository = get()) }
    viewModel { TorrentDownloadsViewModel(repository = get(), settingsRepository = get()) }
    viewModel { TorrentSettingsViewModel(settingsRepository = get(), repository = get()) }
    // parity wave A (15 §3.9, worker q)
    viewModel { AddTorrentViewModel(repository = get(), metadata = getOrNull(), pending = getOrNull()) }
    viewModel { TorrentHistoryViewModel(repository = get()) }
    viewModel { DebridViewModel(debrid = getOrNull(), settings = getOrNull()) }
}
