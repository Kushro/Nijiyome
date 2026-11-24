// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.social

import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SocialRepository
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/**
 * Koin bindings of the AniList social layer shared by every platform (docs/redesign/16-parity-wave-b-contract-android.md §3.8).
 * Requires dataModule (ListsRepository, SettingsRepository) and networkModule (OkHttpClient). [cacheDir] is the platform's cache
 * directory (the social cache lives in `cacheDir/social`) and [texts] builds the localised notification sentences.
 */
fun socialModule(cacheDir: Scope.() -> File, texts: Scope.() -> SocialTexts): Module = module {
    single {
        val settings = get<SettingsRepository>()
        SocialRepositoryImpl(
            client = get(),
            lists = get(),
            cacheDir = File(cacheDir(), SocialRepositoryImpl.CACHE_DIR),
            notificationsEnabled = { settings.settings.value.socialNotifications },
            texts = texts(),
        )
    } bind AppDataWiper::class
    single<SocialRepository> { get<SocialRepositoryImpl>() }
    single<SocialActivityLookup> { get<SocialRepositoryImpl>() }
}
