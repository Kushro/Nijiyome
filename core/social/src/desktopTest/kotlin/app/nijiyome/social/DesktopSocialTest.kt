// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.social

import app.nijiyome.model.RequestPriority
import app.nijiyome.model.RequestPriorityElement
import app.nijiyome.model.SocialRepository
import app.nijiyome.model.SystemNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.Locale
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals

/** Hito 6 (D1): the unread poll of desktop (social-cuentas-listas-2) and its localised texts (the marker of SocialModule.desktop.kt). */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSocialTest {
    private class Posted : SystemNotifier {
        val notices = mutableListOf<String>()
        override fun notify(title: String, body: String) {
            notices += "$title: $body"
        }
    }

    /** A SocialRepository whose refreshUnread takes the next value of [counts] and records its priority. */
    private fun fakeSocial(counts: ArrayDeque<Int>, priorities: MutableList<RequestPriority?>): SocialRepository {
        val unread = MutableStateFlow(0)
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SocialRepository::class.java)) { _, method, args ->
            when (method.name) {
                "refreshUnread" -> {
                    priorities += (args.last() as Continuation<*>).context[RequestPriorityElement]?.priority
                    unread.value = counts.removeFirstOrNull() ?: unread.value
                    Unit
                }
                "getUnreadCount" -> unread
                "hashCode" -> 0
                "equals" -> false
                "toString" -> "FakeSocial"
                else -> error("not used: ${method.name}")
            }
        } as SocialRepository
    }

    @Test
    fun theCountIsPolledEveryQuarterHourAndOnFocusAndANoticeOnlyWhenItGrows() = runTest {
        val dir = Files.createTempDirectory("social").toFile()
        val posted = Posted()
        val priorities = mutableListOf<RequestPriority?>()
        val wanted = MutableStateFlow(true)
        var now = 0L
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es"))
        try {
            val poller = DesktopSocialPoller(
                social = fakeSocial(ArrayDeque(listOf(2, 2, 5, 0, 1)), priorities),
                wanted = wanted,
                notifier = posted,
                lastNotifiedFile = File(dir, "social/last_notified.txt"),
                scope = scope,
                clock = { now },
            )
            poller.start()
            runCurrent()
            assertEquals(listOf("AniList: 2 notificaciones nuevas de AniList"), posted.notices)
            advanceTimeBy(15 * 60_000L + 1)
            assertEquals(1, posted.notices.size, "same count: no second notice")
            // The window comes back to the front: polls at once (it was 15 min ago).
            now = 16 * 60_000L
            poller.refreshNow()
            runCurrent()
            assertEquals("AniList: 5 notificaciones nuevas de AniList", posted.notices.last())
            // Again right away: too soon, nothing asked.
            poller.refreshNow()
            runCurrent()
            assertEquals(3, priorities.size)
            // Read elsewhere (0), then one new: a notice for 1.
            advanceTimeBy(15 * 60_000L + 1)
            advanceTimeBy(15 * 60_000L + 1)
            assertEquals("AniList: 1 notificación nueva de AniList", posted.notices.last())
            assertEquals(List<RequestPriority?>(5) { RequestPriority.BACKGROUND }, priorities)
            // Setting off (or the account unlinked): no more polls.
            wanted.value = false
            advanceTimeBy(60 * 60_000L)
            assertEquals(5, priorities.size)
            poller.close()
        } finally {
            Locale.setDefault(previous)
            dir.deleteRecursively()
        }
    }

    @Test
    fun theSentencesFollowTheAppLanguage() {
        var locale = Locale.ENGLISH
        val texts = DesktopSocialTexts { locale }
        assertEquals("Ana started following you.", texts.notification("FOLLOWING", "Ana", null, null, "x"))
        locale = Locale.forLanguageTag("es")
        assertEquals("Ana empezó a seguirte.", texts.notification("FOLLOWING", "Ana", null, null, "x"))
        assertEquals("Se emitió el episodio 3 de Frieren.", texts.notification("AIRING", null, "Frieren", 3, "x"))
        assertEquals("fallback", texts.notification("SOMETHING_NEW", "Ana", null, null, "fallback"))
    }

    /** The Spanish sentences here are the ones of the Android resources (`values-es/strings.xml` of this module). */
    @Test
    fun theSpanishTemplatesMatchTheResources() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "src/androidMain/res/values-es/strings.xml").isFile }
        val xml = File(root, "src/androidMain/res/values-es/strings.xml").readText()
        val fromResources = Regex("""<string name="social_notification_([a-z_]+)">([^<]*)</string>""").findAll(xml)
            .map { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
            .filter { it.first in SocialNotificationTypes.TEMPLATES.values }
            .toMap()
        assertEquals(fromResources, SPANISH_TEMPLATES)
        assertEquals(ENGLISH_TEMPLATES.keys, SPANISH_TEMPLATES.keys)
    }
}
