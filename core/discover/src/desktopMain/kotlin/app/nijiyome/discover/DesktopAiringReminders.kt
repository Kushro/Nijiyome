// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.discover

import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.NoticeDestination
import app.nijiyome.model.SystemNotifier
import app.nijiyome.model.inBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File
import java.util.Locale

/**
 * Desktop counterpart of Android's `AiringReminderWorker` + `DiscoverScheduler` (checklist home.calendar.reminders): while
 * `AppSettings.airingReminders` is on, every [intervalMs] (30 min, as the worker) asks [due] (`AiringRepositoryImpl.dueReminders`)
 * which followed episodes aired since the previous run and posts one system notification each (at most [maxPerRun]). There is
 * no job scheduler that outlives the app on desktop, so this runs while the app is open (the tray keeps it open when the window
 * is closed). A failed run (offline) is tried again after [retryMs]. Background traffic for the AniList gateway, as the worker.
 */
class DesktopAiringReminders(
    private val due: suspend () -> List<AiringEpisode>,
    private val enabled: Flow<Boolean>,
    private val notifier: SystemNotifier,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 30L * 60 * 1000,
    private val retryMs: Long = 5L * 60 * 1000,
    private val maxPerRun: Int = 8,
    private val texts: () -> AiringNoticeTexts = { AiringNoticeTexts.forLocale(Locale.getDefault()) },
) {
    fun start() {
        scope.launch {
            enabled.distinctUntilChanged().collectLatest { on ->
                while (on) {
                    val ok = runOnce()
                    delay(if (ok) intervalMs else retryMs)
                }
            }
        }
    }

    /** One run: true when the schedule could be read. */
    internal suspend fun runOnce(): Boolean = try {
        val episodes = inBackground { due() }
        val t = texts()
        episodes.take(maxPerRun).forEach { notifier.notify(t.title(it.media.title, it.episode), t.body, NoticeDestination.Calendar) }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}

/** The texts of a reminder: the ones of Android's `airing_notification_*` resources, without the tap (desktop notices have none). */
data class AiringNoticeTexts(private val titlePattern: String, val body: String) {
    fun title(series: String, episode: Int): String = titlePattern.format(series, episode)

    companion object {
        val ENGLISH = AiringNoticeTexts("%1\$s · episode %2\$d", "Aired now. Open Nijiyome to watch it.")
        val SPANISH = AiringNoticeTexts("%1\$s · episodio %2\$d", "Acaba de emitirse. Abre Nijiyome para verlo.")

        fun forLocale(locale: Locale): AiringNoticeTexts = if (locale.language == "es") SPANISH else ENGLISH
    }
}

/**
 * Desktop: [discoverModule] plus [DesktopAiringReminders], started with the app when a [SystemNotifier] is bound. Its scope ends
 * when Koin closes.
 */
fun desktopDiscoverModule(dataDir: File): Module = module {
    includes(discoverModule { dataDir })
    single(createdAtStart = true) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val notifier = getOrNull<SystemNotifier>()
        val settings = getOrNull<SettingsRepository>()
        val airing = get<AiringRepositoryImpl>()
        if (notifier != null && settings != null) {
            DesktopAiringReminders(
                due = airing::dueReminders,
                enabled = settings.settings.map { it.airingReminders },
                notifier = notifier,
                scope = scope,
            ).start()
        }
        DesktopDiscoverJobs(scope)
    } onClose { it?.scope?.cancel() }
}

/** The background scope of [desktopDiscoverModule] (cancelled when Koin closes). */
class DesktopDiscoverJobs(internal val scope: CoroutineScope)
