// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.framegen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.3.3: whether embedded LSFG frame generation presents in any desktop player session right now, for code outside the player
 * (the image upscale queue pauses on it, docs/plan-upscaling.md §4.6). Fed by each session's `onActiveChanged` and cleared when the
 * session is released; it only observes, frame generation itself does not read it.
 */
object DesktopFrameGenActive {
    private val owners = HashSet<Any>()
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun set(owner: Any, active: Boolean) {
        synchronized(owners) {
            if (active) owners.add(owner) else owners.remove(owner)
            _active.value = owners.isNotEmpty()
        }
    }
}
