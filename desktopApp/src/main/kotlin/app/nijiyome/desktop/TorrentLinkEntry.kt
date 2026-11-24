// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import app.nijiyome.model.torrent.TorrentSection
import app.nijiyome.nav.TorrentLink
import app.nijiyome.torrent.PendingMagnetLink
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where a `magnet:` link or a `.torrent` file handed to the desktop app goes in (the same path as Android's MainActivity): it waits in
 * [PendingMagnetLink] and the shell opens the torrent library, whose "Añadir torrent" sheet comes up pre-filled.
 * Today only `nijiyome <magnet | file.torrent>` calls it; the system handler (`magnet:` scheme, a second instance passing its
 * argument to the first) is the system-integration work and calls [open] too.
 */
class TorrentLinkEntry(private val pending: PendingMagnetLink, val links: MutableStateFlow<TorrentLink?> = MutableStateFlow(null)) {
    /** Offers [raw] when it is a magnet link or an existing `.torrent` file; false (nothing happens) otherwise. */
    fun open(raw: String): Boolean {
        val link = linkOf(raw) ?: return false
        pending.offer(link)
        links.value = TorrentLink(TorrentSection.LIBRARY, null)
        return true
    }

    companion object {
        /** The link to offer: a magnet as is, a `.torrent` file as its `file:` URI (what the engine reads); null when [raw] is neither. */
        fun linkOf(raw: String): String? {
            val text = raw.trim()
            if (text.startsWith(PendingMagnetLink.MAGNET_SCHEME + ":", ignoreCase = true)) return text
            val file = File(text)
            return file.takeIf { it.name.endsWith(".torrent", ignoreCase = true) && it.isFile }?.absoluteFile?.toPath()?.toUri()?.toString()
        }
    }
}
