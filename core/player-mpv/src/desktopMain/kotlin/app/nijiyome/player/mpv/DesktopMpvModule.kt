// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.model.AppSettings
import app.nijiyome.model.LocalEnhancementGate
import app.nijiyome.model.SettingsRepository
import app.nijiyome.player.MPV_SESSION_FACTORY
import app.nijiyome.player.PlayerSessionFactory
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.RemoteProcessingState
import app.nijiyome.player.mpv.external.DesktopPlayerPrefsStore
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/**
 * Hito 4: Koin bindings of the desktop mpv engine, the counterpart of Android's `mpvPlayerModule` (load it next to
 * `desktopPlayerModule()`, which has the shared player bindings). The libmpv [PlayerSessionFactory] is bound with the
 * [MPV_SESSION_FACTORY] qualifier; each session starts from the quality defaults of Settings › Reproductor, read when it is created.
 * [cacheDir] holds what mpv reads from disk (see [DesktopMpvSessionFactory]); [fontsDir] is mpv's `sub-fonts-dir`, the folder of the
 * imported subtitle fonts (the same one `desktopPlayerModule` gets).
 */
fun desktopMpvPlayerModule(cacheDir: File, fontsDir: File = File(File(cacheDir, "mpv"), "fonts")): Module = module {
    // v1.1 (F2): the system libva and the VAAPI probe at startup, off the UI thread (the source picker's codec checks read it).
    DesktopVideoDecoding.warmUp()
    // The Link modes' "PC processes this stream" flag, shared by the player and every libmpv session (same as Android).
    single { RemoteProcessingState() }
    single<LocalEnhancementGate> { get<RemoteProcessingState>() }
    // The volume both mpv engines open at, kept in `desktop-player.json` when the external mpv module (its store) is loaded too.
    single { PlayerVolumeMemory(getOrNull<DesktopPlayerPrefsStore>()) }
    single<PlayerSessionFactory>(named(MPV_SESSION_FACTORY)) {
        val settings = getOrNull<SettingsRepository>()
        DesktopMpvSessionFactory(
            cacheDir = cacheDir,
            quality = {
                val s = settings?.settings?.value ?: AppSettings()
                QualityRequest.from(s.playerPreset, s.playerQuality)
            },
            remoteProcessing = get<RemoteProcessingState>(),
            fontsDir = fontsDir,
            volumeMemory = get(),
            mediaRoute = getOrNull(),
            desktopPrefsStore = getOrNull<DesktopPlayerPrefsStore>(),
        )
    }
}
