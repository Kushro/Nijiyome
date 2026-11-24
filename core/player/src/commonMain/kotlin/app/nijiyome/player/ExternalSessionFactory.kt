// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import app.nijiyome.model.PlayerEngine
import kotlinx.coroutines.CoroutineScope

// Hito 4, paso D: the EXTERNAL engine as a [PlayerSession]. Android's external engine is an Intent to another app (ExternalPlayer.kt,
// no session) and binds none of this; desktop binds an implementation that runs mpv in its own window and drives it over mpv's JSON
// IPC (core/player-mpv desktopMain, ExternalMpvSessionFactory).

/**
 * Creates sessions of an external player that the app controls (desktop: mpv in its own window). The player screen shows an
 * informative card instead of the video while one of them plays.
 */
interface ExternalSessionFactory : PlayerSessionFactory {
    /**
     * Whether a player opened with the engine setting [engine] plays in the external window. Desktop: the EXTERNAL setting, and also
     * the libmpv one while frame generation is on (lsfg-vk only works on mpv's own Vulkan window). Read once, when the player opens.
     */
    fun wants(engine: PlayerEngine): Boolean

    /**
     * Why the external player cannot start right now (desktop: mpv is not installed or the configured path is wrong), as a short
     * English diagnostic; null when it can. A non-null value makes the player fall back to its built-in engine with a notice.
     */
    fun unavailableReason(): String?

    /** [create] with the title the external window shows ([title]: the series and the episode, or the file name). */
    fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean, title: String): PlayerSession =
        create(scope, listener, remote)
}
