// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import android.content.Intent
import app.nijiyome.model.torrent.TorrentSection

/** The link carried by the launcher intent of a torrent notification, if any. */
fun TorrentLink.Companion.from(intent: Intent?): TorrentLink? {
    val sectionId = intent?.getStringExtra(TorrentLink.EXTRA_SECTION) ?: return null
    val section = TorrentSection.entries.firstOrNull { it.id == sectionId } ?: return null
    val torrentId = intent.getStringExtra(TorrentLink.EXTRA_TORRENT_ID)
    // A per-torrent section without an id has nothing to show: fall back to the client's library.
    return if (section.perTorrent && torrentId == null) TorrentLink(TorrentSection.LIBRARY, null) else TorrentLink(section, torrentId)
}
