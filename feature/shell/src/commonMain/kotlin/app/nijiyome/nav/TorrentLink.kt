// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import app.nijiyome.model.torrent.TorrentSection

/**
 * Deep link from a torrent notification (worker f, `core/torrent` TorrentNotifications): the launcher intent carries the section id
 * (`TorrentSection.id`) and an optional torrent id. The shell turns it into `Route.Torrents(section, torrentId)`.
 */
data class TorrentLink(val section: TorrentSection, val torrentId: String?) {
    companion object {
        /** Same values as `TorrentNotifications.EXTRA_SECTION` / `EXTRA_TORRENT_ID` in :core:torrent. */
        const val EXTRA_SECTION: String = "app.nijiyome.torrent.SECTION"
        const val EXTRA_TORRENT_ID: String = "app.nijiyome.torrent.TORRENT_ID"
    }
}
