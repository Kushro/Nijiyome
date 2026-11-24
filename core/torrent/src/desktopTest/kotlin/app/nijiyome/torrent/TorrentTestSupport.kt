// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent

import app.nijiyome.model.torrent.TorrentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The selected files of [id] once they equal [expected] (or what they are after [timeoutMs]). libtorrent applies
 * `prioritize_files` on its network thread, so right after an add the priorities read back may still be the old ones; on a loaded
 * machine that showed up as "expected [2] but was []". Waits on the condition, never a fixed time.
 */
internal suspend fun TorrentRepository.awaitSelected(id: String, expected: Set<Int>, timeoutMs: Long = 15_000): Set<Int> {
    var seen: Set<Int> = emptySet()
    withTimeoutOrNull(timeoutMs) {
        while (true) {
            seen = files(id).filter { it.selected }.map { it.index }.toSet()
            if (seen == expected) break
            delay(50)
        }
    }
    return seen
}
