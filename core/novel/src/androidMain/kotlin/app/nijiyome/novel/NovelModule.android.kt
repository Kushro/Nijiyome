// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import app.nijiyome.model.Narrator
import app.nijiyome.model.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android: the novel store lives in the app's private files directory and the [Narrator] is [NarratorImpl] (system TextToSpeech and
 * cloud engines played with Media3). Requires `androidContext()`, dataModule (also SecretStore), extensionsModule, networkModule.
 */
val novelModule: Module = module {
    includes(novelModule { androidContext().filesDir })
    single<Narrator> {
        val settings = get<SettingsRepository>()
        NarratorImpl(
            context = androidContext(),
            client = get(),
            secrets = get(),
            settings = { settings.settings.value.tts },
        )
    }
}
