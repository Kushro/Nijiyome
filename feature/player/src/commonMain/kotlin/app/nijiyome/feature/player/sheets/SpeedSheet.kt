// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sheets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.Composable
import app.nijiyome.feature.player.keyLabel
import app.nijiyome.feature.player.playerCapabilities
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.player.SleepOption
import app.nijiyome.ui.format.formatDuration
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign (lane S6): "Velocidad y temporizador" (artifact `shVelocidad`, parity PLY-11 / PLY-12): the speeds and the sleep
// timer as chips instead of six or seven full-width rows, with loop and stable volume (they were in "Más").

@Composable
internal fun SpeedSheetContent(data: PlayerSheetData) {
    val caps = playerCapabilities
    val ps = data.ps
    val state = data.state
    val vm = data.vm
    val keymap = PlayerKeymap.resolve(state.settings.playerKeymap, caps.desktopShortcuts)
    SheetSection(stringResource(Res.string.player_speed)) {
        ChoiceChips(
            choices = caps.speeds.map { Choice(it, stringResource(Res.string.player_speed_value, speedLabel(it))) },
            selected = caps.speeds.firstOrNull { kotlin.math.abs(it - ps.speed) < 0.001f },
            onSelect = data.session::setSpeed,
        )
        if (caps.touchFirst) {
            Hint(stringResource(Res.string.rd_s6_speed_hold_hint))
        } else {
            val up = keymap[PlayerKeymap.SPEED_UP]
            val down = keymap[PlayerKeymap.SPEED_DOWN]
            if (up != null && down != null) Hint(stringResource(Res.string.rd_s6_speed_keys_hint, keyLabel(up), keyLabel(down)))
        }
    }
    SheetDivider()
    SheetSection(stringResource(Res.string.player_sleep)) {
        val sleep = state.sleep
        ChoiceChips(
            choices = SleepOption.entries.map { o ->
                val label = when (o) {
                    SleepOption.OFF -> stringResource(Res.string.player_sleep_off)
                    SleepOption.END_OF_EPISODE -> stringResource(Res.string.player_sleep_end)
                    else -> stringResource(Res.string.player_sleep_minutes, o.minutes ?: 0)
                }
                Choice(o, label)
            },
            selected = sleep?.option ?: SleepOption.OFF,
            onSelect = vm::setSleepTimer,
        )
        sleep?.remainingMs?.let { Hint(stringResource(Res.string.player_sleep_remaining, formatDuration(it / 1000))) }
        if (sleep?.option == SleepOption.END_OF_EPISODE) Hint(stringResource(Res.string.rd_s6_sleep_end_hint))
    }
    SheetDivider()
    SheetSection(stringResource(Res.string.rd_s6_playback)) {
        val loopKey = keymap[PlayerKeymap.LOOP]?.takeIf { caps.desktopShortcuts }
        ToggleRow(
            title = stringResource(Res.string.player_loop),
            checked = state.loop,
            onChange = vm::setLoop,
            hint = stringResource(Res.string.player_loop_hint) + (loopKey?.let { " " + stringResource(Res.string.rd_s6_key_suffix, keyLabel(it)) } ?: ""),
            icon = Icons.Rounded.Repeat,
        )
        ToggleRow(
            title = stringResource(Res.string.player_stable_volume),
            checked = state.settings.stableVolume,
            onChange = vm::setStableVolume,
            hint = stringResource(Res.string.player_stable_volume_hint),
            icon = Icons.Rounded.GraphicEq,
        )
    }
}

/** "1", "1.25", "0.5": the speed without trailing zeros. */
internal fun speedLabel(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
