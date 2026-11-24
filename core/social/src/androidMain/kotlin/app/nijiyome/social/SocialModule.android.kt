// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.social

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android: the shared social bindings with the app's cache directory and resource texts, plus [SocialScheduler] (eager), which
 * schedules / cancels [SocialNotificationsWorker] following `AppSettings.socialNotifications` and the linked AniList account.
 * Requires `androidContext()`.
 */
val socialModule: Module = module {
    includes(socialModule(cacheDir = { androidContext().cacheDir }, texts = { AndroidSocialTexts(androidContext()) }))
    single(createdAtStart = true) { SocialScheduler(androidContext(), get(), get()) }
}
