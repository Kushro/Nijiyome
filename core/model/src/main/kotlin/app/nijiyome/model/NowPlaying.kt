// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the player shows right now, for the desktop's system integrations (Discord Rich Presence). */
data class NowPlayingInfo(
    val title: String,
    /** The localised "Episode N" line of the player (null for a loose file). */
    val subtitle: String?,
    /** An `https://` poster, or null. */
    val artworkUrl: String?,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val paused: Boolean = false,
)

/**
 * The desktop player publishes here (its `publishPlaybackMetadata` and content effects) and the desktop app reads it; Android
 * never writes it (its media session has its own path). Null when no player screen is open.
 */
object NowPlaying {
    private val _current = MutableStateFlow<NowPlayingInfo?>(null)
    val current: StateFlow<NowPlayingInfo?> = _current.asStateFlow()

    fun update(transform: (NowPlayingInfo?) -> NowPlayingInfo?) {
        while (true) {
            val before = _current.value
            if (_current.compareAndSet(before, transform(before))) return
        }
    }

    fun clear() {
        _current.value = null
    }
}
