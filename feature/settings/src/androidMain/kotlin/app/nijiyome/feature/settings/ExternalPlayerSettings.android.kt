// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.runtime.Composable
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.settings_engine_external_hint
import app.nijiyome.feature.settings.resources.settings_engine_mpv_hint
import app.nijiyome.model.PlayerEngine
import app.nijiyome.feature.settings.resources.settings_external_app
import app.nijiyome.feature.settings.resources.settings_external_app_hint
import app.nijiyome.feature.settings.resources.settings_external_chooser
import app.nijiyome.feature.settings.resources.settings_external_missing
import app.nijiyome.feature.settings.resources.settings_external_mpv
import app.nijiyome.feature.settings.resources.settings_external_none_installed
import app.nijiyome.feature.settings.resources.settings_external_vlc
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Hito 4, paso D: the External engine's rows of Android, moved unchanged from SettingsSections.kt (PlayerSection) so desktop can show
// its own (mpv in its own window).

private const val PKG_MPV = "is.xyz.mpv"
private const val PKG_VLC = "org.videolan.vlc"

@Composable
internal actual fun ExternalPlayerSettingsRows(state: SettingsUiState, vm: SettingsViewModel) {
    val s = state.settings
    // BUG-046: say which external players are missing instead of listing them as if they were there.
    val installed = rememberInstalledPackages(setOf(PKG_MPV, PKG_VLC), key = s.playerEngine)
    val missing = stringResource(Res.string.settings_external_missing)
    fun label(pkg: String, name: String) = if (pkg in installed) name else "$name · $missing"
    val hint = if (installed.isEmpty()) {
        stringResource(Res.string.settings_external_app_hint) + " " + stringResource(Res.string.settings_external_none_installed)
    } else {
        stringResource(Res.string.settings_external_app_hint)
    }
    SettingRow(stringResource(Res.string.settings_external_app), hint, stack = true) {
        Segmented(
            options = listOf<SegmentedOption<String?>>(
                SegmentedOption(null, stringResource(Res.string.settings_external_chooser)),
                SegmentedOption(PKG_MPV, label(PKG_MPV, stringResource(Res.string.settings_external_mpv))),
                SegmentedOption(PKG_VLC, label(PKG_VLC, stringResource(Res.string.settings_external_vlc))),
            ),
            selected = s.externalPlayerPackage,
            onSelect = vm::setExternalPlayerPackage,
            small = true,
            fillWidth = true,
        )
    }
}

internal actual val externalEngineHintRes: StringResource = Res.string.settings_engine_external_hint
internal actual val mpvEngineHintRes: StringResource = Res.string.settings_engine_mpv_hint
internal actual val offeredEngines: List<PlayerEngine> = listOf(PlayerEngine.MEDIA3, PlayerEngine.MPV, PlayerEngine.EXTERNAL)
internal actual fun platformHint(res: StringResource): StringResource = res
@Composable
internal actual fun EmbedSourcesRows(rows: @Composable () -> Unit) = rows()

internal actual val mpvTlsSettingShown: Boolean = true
