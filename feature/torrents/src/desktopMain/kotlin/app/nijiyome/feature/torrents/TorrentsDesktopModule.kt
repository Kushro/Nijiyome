// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop counterpart of [torrentsFeatureModule]: the same ViewModels. The TorrentRepository comes from `desktopTorrentModule` of
 * :core:torrent (the engine, or a repository without one when libtorrent4j cannot load).
 */
fun desktopTorrentsFeatureModule(): Module = module {
    includes(torrentsFeatureModule)
}
