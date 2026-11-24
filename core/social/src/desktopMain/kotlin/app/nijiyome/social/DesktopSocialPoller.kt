// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.social

import app.nijiyome.model.ListProvider
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SocialRepository
import app.nijiyome.model.SystemNotifier
import app.nijiyome.model.inBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Desktop counterpart of Android's `SocialNotificationsWorker` + `SocialScheduler` (checklist social.notifications.badge), and
 * Tatakai Next's rail badge poll: while `AppSettings.socialNotifications` is on and an AniList account is linked, refreshes
 * `SocialRepository.unreadCount` every [intervalMs] (15 min) and when the window comes back to the front ([refreshNow], at most once
 * a minute); when the count grew since the last notice it posts ONE system notification. The last notified count is kept in
 * `<cache>/social/last_notified.txt`, as on Android. Background traffic for the AniList gateway.
 */
class DesktopSocialPoller(
    private val social: SocialRepository,
    private val wanted: Flow<Boolean>,
    private val notifier: SystemNotifier?,
    private val lastNotifiedFile: File,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 15L * 60 * 1000,
    private val focusGapMs: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var lastPollMs = Long.MIN_VALUE / 2

    fun start() {
        scope.launch {
            wanted.distinctUntilChanged().collectLatest { on ->
                if (!on) return@collectLatest
                while (true) {
                    poll()
                    // The next tick, or earlier when the window comes back (refreshNow).
                    withTimeoutOrNull(intervalMs) { wake.receive() }
                }
            }
        }
    }

    /** The window came back to the front: poll now unless it polled less than a minute ago. */
    fun refreshNow() {
        if (clock() - lastPollMs >= focusGapMs) wake.trySend(Unit)
    }

    /** Stops polling (Koin closing with the app). */
    fun close() = scope.cancel()

    internal suspend fun poll() {
        lastPollMs = clock()
        try {
            inBackground { social.refreshUnread() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val count = social.unreadCount.value
        val last = readLast()
        if (shouldNotify(count, last)) notifier?.notify(SocialNoticeTexts.TITLE, SocialNoticeTexts.unread(count))
        if (count != last) writeLast(count)
    }

    private fun readLast(): Int = runCatching { lastNotifiedFile.readText().trim().toInt() }.getOrDefault(0)

    private fun writeLast(count: Int) {
        runCatching {
            lastNotifiedFile.parentFile?.mkdirs()
            lastNotifiedFile.writeText(count.toString())
        }
    }

    companion object {
        /** Android's rule: a notice only when the unread count grew since the last one shown (0 resets it). */
        fun shouldNotify(count: Int, lastNotified: Int): Boolean = count > 0 && count > lastNotified

        /** True while polling must run: the setting is on and an AniList account is linked (Android's `SocialScheduler.wanted`). */
        fun wanted(settings: SettingsRepository, lists: ListsRepository): Flow<Boolean> =
            combine(
                settings.settings.map { it.socialNotifications },
                lists.accounts.map { list -> list.any { it.provider == ListProvider.ANILIST } },
            ) { enabled, linked -> enabled && linked }.distinctUntilChanged()
    }
}
