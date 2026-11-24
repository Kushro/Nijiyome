// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import androidx.compose.runtime.Composable
import app.nijiyome.player.PlayerKeymap
import org.jetbrains.compose.resources.stringResource

// Milestone 3: the words of the keyboard shortcuts and of the translation providers (moved verbatim from PlayerExtras.kt, androidMain),
// shared by the player and PlayerSettingsExtras.

/** Human label of a key name: arrows as symbols, Space localised, `SLASH` as `?`. */
@Composable
internal fun keyLabel(key: String): String = when (key) {
    "SPACE" -> stringResource(Res.string.player_key_space)
    else -> PlayerKeymap.displayName(key)
}

@Composable
internal fun actionLabel(action: String): String {
    // Hito 6: desktop seeks 5 s and its C key goes through the subtitle tracks (Tatakai Next); Android keeps its words.
    val caps = playerCapabilities
    val fiveSeconds = caps.seekStepMs == DESKTOP_SEEK_STEP_MS
    return stringResource(
        when (action) {
            PlayerKeymap.PLAY_PAUSE -> Res.string.player_key_action_play_pause
            PlayerKeymap.SEEK_BACK -> if (fiveSeconds) Res.string.player_key_action_seek_back_5 else Res.string.player_key_action_seek_back
            PlayerKeymap.SEEK_FORWARD -> if (fiveSeconds) Res.string.player_key_action_seek_forward_5 else Res.string.player_key_action_seek_forward
            PlayerKeymap.FULLSCREEN -> Res.string.player_key_action_fullscreen
            PlayerKeymap.MUTE -> Res.string.player_key_action_mute
            PlayerKeymap.PIP -> Res.string.player_key_action_pip
            PlayerKeymap.PREV_EPISODE -> Res.string.player_key_action_prev_episode
            PlayerKeymap.NEXT_EPISODE -> Res.string.player_key_action_next_episode
            PlayerKeymap.VOLUME_UP -> Res.string.player_key_action_volume_up
            PlayerKeymap.VOLUME_DOWN -> Res.string.player_key_action_volume_down
            PlayerKeymap.SCREENSHOT -> Res.string.player_key_action_screenshot
            PlayerKeymap.SUBS_TOGGLE -> if (caps.desktopShortcuts) Res.string.player_key_action_subs_cycle else Res.string.player_key_action_subs_toggle
            PlayerKeymap.SPEED_UP -> Res.string.player_key_action_speed_up
            PlayerKeymap.SPEED_DOWN -> Res.string.player_key_action_speed_down
            PlayerKeymap.AUDIO_CYCLE -> Res.string.player_key_action_audio_cycle
            PlayerKeymap.SUB_DELAY_EARLIER -> Res.string.player_key_action_sub_delay_earlier
            PlayerKeymap.SUB_DELAY_LATER -> Res.string.player_key_action_sub_delay_later
            PlayerKeymap.SKIP_SEGMENT -> Res.string.player_key_action_skip_segment
            PlayerKeymap.LOOP -> Res.string.player_key_action_loop
            else -> Res.string.player_key_action_help
        },
    )
}

@Composable
internal fun providerLabel(p: app.nijiyome.model.TranslationProvider): String = stringResource(
    when (p) {
        app.nijiyome.model.TranslationProvider.NONE -> Res.string.player_settings_provider_none
        app.nijiyome.model.TranslationProvider.GOOGLE -> Res.string.player_settings_provider_google
        app.nijiyome.model.TranslationProvider.DEEPL -> Res.string.player_settings_provider_deepl
        app.nijiyome.model.TranslationProvider.DEEPLX -> Res.string.player_settings_provider_deeplx
        app.nijiyome.model.TranslationProvider.AZURE -> Res.string.player_settings_provider_azure
        app.nijiyome.model.TranslationProvider.OPENAI -> Res.string.player_settings_provider_openai
    },
)
