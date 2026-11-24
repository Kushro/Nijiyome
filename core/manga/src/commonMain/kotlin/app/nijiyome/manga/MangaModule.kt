// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.manga

import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.ChapterDownloadSource
import app.nijiyome.model.ReadListPuller
import app.nijiyome.model.MangaRepository
import app.nijiyome.model.SettingsRepository
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File

/**
 * Koin bindings of manga (docs/redesign/16-parity-wave-b-contract-android.md §3.8) for a platform: [filesDir] is the app's private
 * data directory (Android: `Context.filesDir`; desktop: the app's data directory). Requires dataModule
 * (MetadataRepository, ListsRepository, SettingsRepository), extensionsModule (ExtensionRepository). The repository is also the
 * [ChapterDownloadSource] of manga (Downloads page of r, `getAll<ChapterDownloadSource>()`), an [AppDataWiper] and the [MangaCatalog] of
 * the reader screens.
 */
fun mangaModule(filesDir: Scope.() -> File): Module = module {
    single {
        val settings = get<SettingsRepository>()
        MangaRepositoryImpl(
            metadata = get(),
            extensions = get(),
            lists = getOrNull(),
            client = get(),
            storeDir = File(filesDir(), MangaRepositoryImpl.DIR_NAME),
            settings = { settings.settings.value },
        )
    } binds arrayOf(AppDataWiper::class, ChapterDownloadSource::class, ReadListPuller::class)
    single<MangaRepository> { get<MangaRepositoryImpl>() }
    // Extras of the manga / novel screens (AniList facts, MangaUpdates, prefs, reliability), private to the readers.
    single<MangaCatalog> { get<MangaRepositoryImpl>() }
}
