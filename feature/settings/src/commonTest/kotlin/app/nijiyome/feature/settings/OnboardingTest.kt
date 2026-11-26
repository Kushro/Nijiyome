// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.model.AppSettings
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.Quality
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** parity wave A closure (worker q, account.setup-wizard): engine / source preferences and permissions steps of the first-run wizard. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            settings.value = transform(settings.value)
        }
    }

    @Test
    fun `steps cover language, playback, lists, pc and permissions in order`() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME, OnboardingStep.APPEARANCE, OnboardingStep.PLAYBACK, OnboardingStep.LISTS,
                OnboardingStep.LINK, OnboardingStep.PERMISSIONS, OnboardingStep.DONE,
            ),
            onboardingSteps(notificationsNeedPermission = true),
        )
        val old = onboardingSteps(notificationsNeedPermission = false)
        assertFalse(OnboardingStep.PERMISSIONS in old)
        assertTrue(OnboardingStep.PLAYBACK in old)
        assertEquals(OnboardingStep.DONE, old.last())
        // a device without the lists service or the Link client skips those two steps
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.APPEARANCE, OnboardingStep.PLAYBACK, OnboardingStep.DONE),
            onboardingSteps(notificationsNeedPermission = false, listsAvailable = false, linkAvailable = false),
        )
    }

    @Test
    fun `playback step writes the engine and source preferences`() = runTest {
        val repo = FakeSettings()
        val vm = OnboardingViewModel(repo)
        vm.setPlayerEngine(PlayerEngine.MPV)
        vm.setSourceMode(SourceMode.MANUAL)
        vm.setPreferredKind(SourceKind.TORRENT)
        vm.setMinQuality(Quality.P1080)
        vm.setSubLang("en")
        vm.setDubLang("es")
        val s = repo.settings.value
        assertEquals(PlayerEngine.MPV, s.playerEngine)
        assertEquals(SourceMode.MANUAL, s.sourcePrefs.mode)
        assertEquals(SourceKind.TORRENT, s.sourcePrefs.prefer)
        assertEquals(Quality.P1080, s.sourcePrefs.minQuality)
        assertEquals("en", s.sourcePrefs.subLang)
        assertEquals("es", s.sourcePrefs.dubLang)
        assertFalse(s.onboardingDone)

        var done = false
        vm.finish { done = true }
        assertTrue(done)
        assertTrue(repo.settings.value.onboardingDone)
        // a new install chose its style here: it never sees the post-update notice
        assertTrue(repo.settings.value.look.design.introSeen)
    }

    @Test
    fun `the steps are shown as four pages`() {
        val phone = onboardingPages(onboardingSteps(notificationsNeedPermission = true))
        assertEquals(
            listOf(OnboardingPage.LOOK, OnboardingPage.LIST, OnboardingPage.SOURCES, OnboardingPage.LINK_SYSTEM),
            phone.map { it.first },
        )
        assertEquals(listOf(OnboardingStep.LINK, OnboardingStep.PERMISSIONS), phone.last().second)
        // nothing of Link y sistema on this device: three pages
        val bare = onboardingPages(onboardingSteps(notificationsNeedPermission = false, linkAvailable = false))
        assertEquals(listOf(OnboardingPage.LOOK, OnboardingPage.LIST, OnboardingPage.SOURCES), bare.map { it.first })
        val desktop = onboardingPages(onboardingSteps(false, platformSteps = PLATFORM_ONBOARDING_STEPS))
        assertEquals(listOf(OnboardingStep.DOWNLOADS), desktop.first { it.first == OnboardingPage.SOURCES }.second)
        assertTrue(OnboardingStep.SYSTEM in desktop.last().second)
    }

    @Test
    fun `the style step writes direction, tone and palette`() = runTest {
        val repo = FakeSettings()
        val vm = OnboardingViewModel(repo)
        vm.setDirection(app.nijiyome.model.DesignDirection.TRANSIT)
        vm.setTone(app.nijiyome.model.ThemeMode.DARK, oled = true)
        vm.setDirectionPalette("linea")
        val s = repo.settings.value
        assertEquals(app.nijiyome.model.DesignDirection.TRANSIT, s.look.design.direction)
        assertEquals("linea", s.look.design.palette)
        assertTrue(s.amoled)
        assertEquals(app.nijiyome.model.ThemeMode.DARK, s.themeMode)
    }
}
