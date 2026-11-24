// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.torrent.DebridRepository
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.torrent.debrid.DebridRepositoryImpl
import app.nijiyome.torrent.system.AndroidTorrentHost
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin bindings of the torrent engine (name FIXED: :app lists it): the libtorrent4j SessionManager wrapper, loopback HTTP server,
 * labels.json, TorrentService control and network / battery / storage policies, all behind [TorrentRepository]. The concrete type is
 * also bound (internal) so [TorrentService] reaches the same instance.
 * parity wave A (15 §3.9, worker q): [DebridRepository] (shared `OkHttpClient` of `networkModule` + `SecretStore` of `dataModule`) and
 * [PendingMagnetLink] (system `magnet:` / `.torrent` handler).
 */
val torrentModule: Module = module {
    single { TorrentRepositoryImpl(host = AndroidTorrentHost(androidContext()), settingsRepository = get(), debrid = getOrNull()) }
    single<TorrentRepository> { get<TorrentRepositoryImpl>() }
    // concrete type also bound so the engine (instant HTTPS playback) and the Debrid section (preference order) reach the same instance
    single {
        // 1.1 (F7): Settings › Torrents › Debrid (enabled / prefer cached / download cached)
        val settings = getOrNull<app.nijiyome.model.SettingsRepository>()
        DebridRepositoryImpl(
            client = get(),
            secrets = get(),
            debridSettings = { settings?.settings?.value?.torrent?.debrid ?: app.nijiyome.model.torrent.DebridSettings() },
        )
    }
    single<DebridRepository> { get<DebridRepositoryImpl>() }
    single { PendingMagnetLink() }
}
