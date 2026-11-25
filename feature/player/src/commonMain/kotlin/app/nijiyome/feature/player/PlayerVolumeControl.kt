// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_hud_audio
import app.nijiyome.feature.player.resources.player_hud_loop_off
import app.nijiyome.feature.player.resources.player_hud_loop_on
import app.nijiyome.feature.player.resources.player_hud_no_subtitles
import app.nijiyome.feature.player.resources.player_hud_preset
import app.nijiyome.feature.player.resources.player_hud_subtitles
import app.nijiyome.feature.player.resources.player_mute
import app.nijiyome.feature.player.resources.player_speed_value
import app.nijiyome.feature.player.resources.player_sub_delay_value
import app.nijiyome.feature.player.resources.player_subtitles_off
import app.nijiyome.feature.player.resources.player_unmute
import app.nijiyome.feature.player.resources.player_volume
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerVolume
import app.nijiyome.player.SubtitleDelay
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Hito 6 (desktop, Tatakai Next `player_controls.rs` on_volume / on_toggle_mute): the engine's own volume in the controls row, and the
// HUD lines of the desktop keys. Only shown with `PlayerCapabilities.volumeControls` / `desktopShortcuts` (false on Android).

/** Mute button and a short slider (0–100 %) of the session's own volume; follows changes made with the keys too. */
@Composable
internal fun PlayerVolumeControl(
    session: PlayerSession,
    onInteract: () -> Unit,
    /** Sparkles S5: the "Volumen 80 %" text after the slider (the slider says it to TalkBack either way). */
    showLabel: Boolean = true,
) {
    var volume by remember(session) { mutableStateOf(session.volume()) }
    var dragging by remember(session) { mutableStateOf<Float?>(null) }
    // The keys (↑ ↓ M) and the other engine change it too: read it back while the controls are up.
    LaunchedEffect(session) {
        while (true) {
            if (dragging == null) session.volume()?.let { if (it != volume) volume = it }
            delay(VOLUME_POLL_MS)
        }
    }
    val v = volume ?: return
    val volumeText = stringResource(Res.string.player_volume) + " " + (if (v.muted) 0 else v.percent) + " %"
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { session.toggleMute()?.let { volume = it }; onInteract() }) {
            if (v.muted || v.percent == 0) {
                Icon(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(Res.string.player_unmute), tint = MaterialTheme.colorScheme.onBackground)
            } else {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(Res.string.player_mute), tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        Slider(
            value = dragging ?: if (v.muted) 0f else v.percent.toFloat(),
            onValueChange = { dragging = it; onInteract() },
            onValueChangeFinished = {
                dragging?.let { target -> session.applyVolume(target.roundToInt(), v)?.let { volume = it } }
                dragging = null
            },
            valueRange = 0f..100f,
            modifier = Modifier.width(VOLUME_SLIDER_DP.dp).semantics { contentDescription = volumeText },
        )
        if (showLabel) {
            Spacer(Modifier.width(4.dp))
            Text(volumeText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** Moves the session's volume to [target] (unmuting, like the keys do). */
private fun PlayerSession.applyVolume(target: Int, from: PlayerVolume): PlayerVolume? {
    val delta = target.coerceIn(0, 100) - from.percent
    return changeVolume(delta)
}

/** The HUD line of a desktop key ([Hud.Kind.Speed]...[Hud.Kind.Preset]). */
@Composable
internal fun DesktopHudContent(hud: Hud) {
    when (hud.kind) {
        Hud.Kind.Speed -> {
            Icon(Icons.Rounded.Speed, contentDescription = null)
            Text(stringResource(Res.string.player_speed_value, hud.label.orEmpty()))
        }
        // Sparkles S5: a short line that is already worded (the send-to-phone result)
        Hud.Kind.Message -> Text(hud.label.orEmpty())
        Hud.Kind.Subtitles -> {
            Icon(Icons.Rounded.Subtitles, contentDescription = null)
            Text(
                when {
                    hud.label == null && !hud.on -> if (hud.percent == NO_TRACKS) stringResource(Res.string.player_hud_no_subtitles) else stringResource(Res.string.player_hud_subtitles, stringResource(Res.string.player_subtitles_off))
                    else -> stringResource(Res.string.player_hud_subtitles, hud.label.orEmpty())
                },
            )
        }
        Hud.Kind.Audio -> {
            Icon(Icons.Rounded.Audiotrack, contentDescription = null)
            Text(stringResource(Res.string.player_hud_audio, hud.label.orEmpty()))
        }
        Hud.Kind.SubDelay -> {
            Icon(Icons.Rounded.Subtitles, contentDescription = null)
            Text(stringResource(Res.string.player_sub_delay_value, SubtitleDelay.format(hud.millis)))
        }
        Hud.Kind.Loop -> {
            Icon(Icons.Rounded.Loop, contentDescription = null)
            Text(stringResource(if (hud.on) Res.string.player_hud_loop_on else Res.string.player_hud_loop_off))
        }
        Hud.Kind.Preset -> {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Text(stringResource(Res.string.player_hud_preset, stringResource(presetLabelRes(hud.label.orEmpty()))))
        }
        else -> Unit
    }
}

/** [Hud.percent] of a [Hud.Kind.Subtitles] HUD when the video has no subtitle track at all. */
internal const val NO_TRACKS = -1

private const val VOLUME_POLL_MS = 500L
private const val VOLUME_SLIDER_DP = 120
