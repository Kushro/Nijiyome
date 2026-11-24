// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.discover

import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.RequestPriority
import app.nijiyome.model.RequestPriorityElement
import app.nijiyome.model.SystemNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** Hito 6 (D1): the desktop airing reminders (the marker of DiscoverModule.kt). */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopAiringRemindersTest {
    private class Posted : SystemNotifier {
        val notices = mutableListOf<Pair<String, String>>()
        override fun notify(title: String, body: String) {
            notices += title to body
        }
    }

    private val minute = 60_000L

    @Test
    fun remindersArePostedEveryHalfHourWhileOnAndStopWhenOff() = runTest {
        val enabled = MutableStateFlow(true)
        val posted = Posted()
        val priorities = mutableListOf<RequestPriority?>()
        var runs = 0
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        DesktopAiringReminders(
            due = {
                runs++
                priorities += currentCoroutineContext()[RequestPriorityElement]?.priority
                if (runs == 1) listOf(AiringEpisode(media(1, title = "Frieren"), 12, 0)) else emptyList()
            },
            enabled = enabled,
            notifier = posted,
            scope = scope,
            texts = { AiringNoticeTexts.forLocale(Locale.forLanguageTag("es")) },
        ).start()
        runCurrent()
        assertEquals(listOf("Frieren · episodio 12" to "Acaba de emitirse. Abre Nijiyome para verlo."), posted.notices)
        advanceTimeBy(30 * minute + 1)
        assertEquals(2, runs)
        enabled.value = false
        advanceTimeBy(120 * minute)
        assertEquals(2, runs, "off: no more runs")
        enabled.value = true
        runCurrent()
        assertEquals(3, runs, "on again: a run at once")
        assertEquals(List<RequestPriority?>(3) { RequestPriority.BACKGROUND }, priorities)
        scope.cancel()
    }

    @Test
    fun aFailedRunIsRetriedSoonerAndAtMostEightArePosted() = runTest {
        val posted = Posted()
        var runs = 0
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        DesktopAiringReminders(
            due = {
                runs++
                if (runs == 1) error("offline") else List(12) { AiringEpisode(media(it, title = "S$it"), 1, 0) }
            },
            enabled = MutableStateFlow(true),
            notifier = posted,
            scope = scope,
            texts = { AiringNoticeTexts.ENGLISH },
        ).start()
        runCurrent()
        assertEquals(0, posted.notices.size)
        advanceTimeBy(5 * minute + 1)
        assertEquals(2, runs, "retried after 5 min")
        assertEquals(8, posted.notices.size)
        assertEquals("S0 · episode 1", posted.notices.first().first)
        scope.cancel()
    }
}
