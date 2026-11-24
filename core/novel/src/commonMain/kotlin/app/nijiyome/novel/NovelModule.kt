// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.ChapterDownloadSource
import app.nijiyome.model.ReadListPuller
import app.nijiyome.model.NovelRepository
import app.nijiyome.model.SettingsRepository
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File

/**
 * Koin bindings of novels shared by the platforms (docs/redesign/16-parity-wave-b-contract-android.md §3.8): [filesDir] is the app's
 * private data directory (Android: `Context.filesDir`; desktop: the app's data directory). Requires dataModule (MetadataRepository,
 * ListsRepository, SettingsRepository), extensionsModule (ExtensionRepository), networkModule (OkHttpClient). The [app.nijiyome.model.Narrator]
 * is bound by each platform (Android: `novelModule`; desktop: `desktopNovelModule`).
 */
fun novelModule(filesDir: Scope.() -> File): Module = module {
    single {
        val settings = get<SettingsRepository>()
        NovelRepositoryImpl(
            metadata = get(),
            extensions = get(),
            lists = getOrNull(),
            client = get(),
            storeDir = File(filesDir(), NovelRepositoryImpl.DIR_NAME),
            settings = { settings.settings.value },
        )
    } binds arrayOf(AppDataWiper::class, ChapterDownloadSource::class, ReadListPuller::class)
    single<NovelRepository> { get<NovelRepositoryImpl>() }
}
