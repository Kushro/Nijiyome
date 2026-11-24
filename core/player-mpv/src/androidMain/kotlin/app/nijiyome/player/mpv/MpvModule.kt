// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.model.AppSettings
import app.nijiyome.framegen.FgController
import app.nijiyome.model.SettingsRepository
import app.nijiyome.player.MPV_SESSION_FACTORY
import app.nijiyome.player.PlayerSessionFactory
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.RemoteProcessingState
import app.nijiyome.model.LocalEnhancementGate
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin bindings of the mpv engine (name FIXED: :app lists it). The libmpv [PlayerSessionFactory] is bound with the [MPV_SESSION_FACTORY]
 * qualifier; feature/player picks it when `AppSettings.playerEngine == MPV`. 0.4.0: each session starts from the quality defaults of
 * Settings › Reproductor (`AppSettings.playerPreset` + `AppSettings.playerQuality`), read when the session is created.
 */
val mpvPlayerModule: Module = module {
    // 0.4.0 integration: the Link modes' "PC processes this stream" flag, shared by the player and every libmpv session.
    single { RemoteProcessingState() }
    single<LocalEnhancementGate> { get<RemoteProcessingState>() }
    single<PlayerSessionFactory>(named(MPV_SESSION_FACTORY)) {
        val settings = getOrNull<SettingsRepository>()
        // 0.4.0: quality defaults from Settings (worker A) + the phone frame generation controller (worker B), when bound.
        MpvSessionFactory(
            androidContext(),
            {
                val s = settings?.settings?.value ?: AppSettings()
                QualityRequest.from(s.playerPreset, s.playerQuality)
            },
            getOrNull<FgController>(),
            get<RemoteProcessingState>(),
            { settings?.settings?.value?.mpvTlsVerify ?: false },
        )
    }
}
