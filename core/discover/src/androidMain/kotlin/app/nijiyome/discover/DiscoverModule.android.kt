// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.discover

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android: the shared bindings with the stores in the app's `filesDir`, plus [DiscoverScheduler]. Requires `androidContext()`,
 * dataModule (MetadataRepository, HistoryRepository, ListsRepository, SettingsRepository). [DiscoverScheduler] is eager: it
 * schedules / cancels [AiringReminderWorker] following `AppSettings.airingReminders`.
 */
val discoverModule: Module = module {
    includes(discoverModule { androidContext().filesDir })
    single(createdAtStart = true) { DiscoverScheduler(androidContext(), get()) }
}
