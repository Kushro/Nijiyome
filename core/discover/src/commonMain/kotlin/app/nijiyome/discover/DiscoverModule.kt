// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.discover

import app.nijiyome.model.AiringRepository
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.RecsRepository
import app.nijiyome.model.SettingsRepository
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/**
 * Koin bindings of the calendar and the recommendations (docs/redesign/16-parity-wave-b-contract-android.md §3.8), shared by
 * Android and desktop. [dataDir] is the app's private data directory; the stores live in its `discover/` and `recs/` folders.
 * Requires MetadataRepository and HistoryRepository; ListsRepository and SettingsRepository are optional (without settings the
 * reminders count as on). The reminder scheduler is not here: Android adds it in `val discoverModule` (androidMain), desktop in
 * `desktopDiscoverModule` (desktopMain).
 */
fun discoverModule(dataDir: Scope.() -> File): Module = module {
    single {
        val settings = getOrNull<SettingsRepository>()
        AiringRepositoryImpl(
            metadata = get(),
            lists = getOrNull(),
            storeDir = File(dataDir(), AiringRepositoryImpl.DIR_NAME),
            remindersEnabled = { settings?.settings?.value?.airingReminders ?: true },
        )
    } bind AppDataWiper::class
    single<AiringRepository> { get<AiringRepositoryImpl>() }
    single {
        RecsRepositoryImpl(
            metadata = get(),
            history = get(),
            lists = getOrNull(),
            storeDir = File(dataDir(), RecsRepositoryImpl.DIR_NAME),
        )
    } bind AppDataWiper::class
    single<RecsRepository> { get<RecsRepositoryImpl>() }
}
