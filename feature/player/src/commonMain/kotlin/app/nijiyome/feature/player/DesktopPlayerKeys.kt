// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.QualityPresets
import app.nijiyome.player.AudioOption
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.SubtitleDelay
import app.nijiyome.player.SubtitleOption

// Hito 6: the desktop keys of Tatakai Next's player (tk-ui `shortcuts.rs`, `screens/player/mod.rs`) that Android does not have. The
// screen only calls these with `PlayerCapabilities.desktopShortcuts` (false on Android), so Android's keys stay as they were.

/** Ctrl+1..6 (Tatakai Next `ShaderPreset::from_shortcut`): Off, Anime4K A, A+A, B, C and FSR. */
internal val CTRL_PRESETS: List<String> = listOf(
    QualityPresets.OFF,
    QualityPresets.ANIME4K_A,
    QualityPresets.ANIME4K_AA,
    QualityPresets.ANIME4K_B,
    QualityPresets.ANIME4K_C,
    QualityPresets.FSR,
)

/** The preset of Ctrl + [key] (`1`..`6`, also on the keypad), or null for any other key. */
internal fun ctrlPreset(key: String): String? {
    val digit = key.removePrefix("NUMPAD_").toIntOrNull() ?: return null
    return CTRL_PRESETS.getOrNull(digit - 1)
}

/** The next speed of [speeds] above ([up]) or below [current]; it stays at the ends (Tatakai Next `speed_step`). */
internal fun nextSpeed(current: Float, up: Boolean, speeds: List<Float>): Float {
    val eps = 1e-3f
    return if (up) speeds.firstOrNull { it > current + eps } ?: speeds.last() else speeds.lastOrNull { it < current - eps } ?: speeds.first()
}

/**
 * The subtitle track after the selected one, then "off" after the last (Tatakai Next `cycle_track` with off): its group index, or
 * null for off. [tracks] empty = nothing to cycle (the caller says "no subtitles").
 */
internal fun nextSubtitle(tracks: List<SubtitleOption>, subtitlesOn: Boolean): SubtitleOption? {
    if (tracks.isEmpty()) return null
    val i = if (subtitlesOn) tracks.indexOfFirst { it.selected } else -1
    return when (i) {
        -1 -> tracks.first()
        tracks.lastIndex -> null
        else -> tracks[i + 1]
    }
}

/** The audio track after the selected one, wrapping around; null when there is only one (or none). */
internal fun nextAudio(tracks: List<AudioOption>): AudioOption? {
    if (tracks.size < 2) return null
    val i = tracks.indexOfFirst { it.selected }
    return tracks[(i + 1).mod(tracks.size)]
}

/**
 * Runs a desktop-only keymap action ([PlayerKeymap.DESKTOP_EXTRAS], and C going through the subtitle tracks); returns the HUD to
 * show, or null when [action] is not one of them (the shared actions run as on Android).
 */
internal fun runDesktopAction(action: String, vm: PlayerViewModel, session: PlayerSession, state: PlayerUiState, speeds: List<Float>): Hud? {
    val ps = session.state.value
    return when (action) {
        PlayerKeymap.SPEED_UP, PlayerKeymap.SPEED_DOWN -> {
            val next = nextSpeed(ps.speed, action == PlayerKeymap.SPEED_UP, speeds)
            session.setSpeed(next)
            Hud(Hud.Kind.Speed, label = speedLabel(next))
        }
        PlayerKeymap.SUBS_TOGGLE -> {
            if (ps.subtitles.isEmpty() && state.customSubtitle == null) return Hud(Hud.Kind.Subtitles, percent = NO_TRACKS)
            val on = ps.subtitlesEnabled || state.customSubtitle != null
            val next = nextSubtitle(ps.subtitles, on && state.customSubtitle == null)
            vm.selectSubtitle(next?.groupIndex)
            Hud(Hud.Kind.Subtitles, label = next?.label?.ifBlank { next.language.orEmpty() }, on = next != null)
        }
        PlayerKeymap.AUDIO_CYCLE -> {
            val next = nextAudio(ps.audioTracks) ?: return Hud(Hud.Kind.Audio, label = ps.audioTracks.firstOrNull()?.label)
            session.selectAudio(next.id)
            Hud(Hud.Kind.Audio, label = next.label)
        }
        PlayerKeymap.SUB_DELAY_EARLIER, PlayerKeymap.SUB_DELAY_LATER -> {
            val direction = if (action == PlayerKeymap.SUB_DELAY_LATER) 1 else -1
            vm.stepSubtitleDelay(direction)
            Hud(Hud.Kind.SubDelay, millis = SubtitleDelay.step(ps.subtitleDelayMs, direction))
        }
        PlayerKeymap.SKIP_SEGMENT -> {
            vm.seekBySkip()
            null
        }
        PlayerKeymap.LOOP -> {
            val on = !(ps.loop || state.loop)
            vm.setLoop(on)
            Hud(Hud.Kind.Loop, on = on)
        }
        else -> null
    }
}

/** The desktop actions [runDesktopAction] handles (C included: it goes through the tracks there). */
internal fun isDesktopAction(action: String): Boolean = action in PlayerKeymap.DESKTOP_EXTRAS || action == PlayerKeymap.SUBS_TOGGLE

/** "1.75" / "2" (the speed menu's own words come from [speedLabel] + the `x` of `player_speed_value`). */
internal fun speedLabel(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
