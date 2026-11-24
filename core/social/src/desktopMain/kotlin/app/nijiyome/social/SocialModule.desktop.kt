// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File

/**
 * Desktop: the shared social bindings (profile, activity, notifications list, reviews) with the given cache directory and the
 * sentences in the app's language, plus [DesktopSocialPoller] (eager): the unread count every 15 min and on window focus, and the
 * system notification when it grows. Its scope ends when Koin closes.
 */
fun desktopSocialModule(cacheDir: File): Module = module {
    includes(socialModule(cacheDir = { cacheDir }, texts = { DesktopSocialTexts() }))
    single(createdAtStart = true) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        DesktopSocialPoller(
            social = get(),
            wanted = DesktopSocialPoller.wanted(get(), get()),
            notifier = getOrNull(),
            lastNotifiedFile = File(File(cacheDir, SocialRepositoryImpl.CACHE_DIR), "last_notified.txt"),
            scope = scope,
        ).also { it.start() }
    } onClose { it?.close() }
}
