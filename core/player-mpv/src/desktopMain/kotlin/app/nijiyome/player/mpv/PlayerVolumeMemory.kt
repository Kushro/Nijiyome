// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.PlayerVolume
import app.nijiyome.player.mpv.external.DesktopPlayerPrefsStore

/**
 * Hito 6 (checklist player.volume: "se recuerdan"): the last volume the user set in either mpv engine. The next player opens at it,
 * also after restarting the app when there is a [store] (`desktop-player.json`). Without one it lasts for this run only.
 */
class PlayerVolumeMemory(private val store: DesktopPlayerPrefsStore? = null) {
    @Volatile
    private var current: PlayerVolume? = store?.prefs?.value?.volume?.let { PlayerVolume(it.coerceIn(0, MAX_PERCENT), store.prefs.value.muted) }

    /** The volume to open the next player at; null = mpv's own default. */
    fun last(): PlayerVolume? = current

    fun remember(volume: PlayerVolume) {
        val v = volume.copy(percent = volume.percent.coerceIn(0, MAX_PERCENT))
        if (v == current) return
        current = v
        store?.update { it.copy(volume = v.percent, muted = v.muted) }
    }

    private companion object {
        const val MAX_PERCENT = 100
    }
}
