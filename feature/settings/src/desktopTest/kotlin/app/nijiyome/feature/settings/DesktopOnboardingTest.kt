// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** v1.1 (F6, sistema-ajustes-7): the desktop wizard has Tatakai Next's download folder and system steps. */
class DesktopOnboardingTest {
    @Test
    fun desktopAddsDownloadsAfterPlaybackAndSystemBeforeTheEnd() {
        assertEquals(setOf(OnboardingStep.DOWNLOADS, OnboardingStep.SYSTEM), platformOnboardingSteps)
        val steps = onboardingSteps(
            notificationsNeedPermission = notificationsNeedPermission,
            listsAvailable = true,
            linkAvailable = true,
            platformSteps = platformOnboardingSteps,
        )
        assertEquals(
            listOf(
                OnboardingStep.WELCOME, OnboardingStep.APPEARANCE, OnboardingStep.PLAYBACK, OnboardingStep.DOWNLOADS,
                OnboardingStep.LISTS, OnboardingStep.LINK, OnboardingStep.SYSTEM, OnboardingStep.DONE,
            ),
            steps,
        )
    }

    @Test
    fun withoutPlatformStepsTheSharedOrderIsTheSame() {
        val shared = onboardingSteps(notificationsNeedPermission = true)
        assertTrue(shared.none { it in PLATFORM_ONBOARDING_STEPS })
        assertEquals(
            listOf(
                OnboardingStep.WELCOME, OnboardingStep.APPEARANCE, OnboardingStep.PLAYBACK, OnboardingStep.LISTS,
                OnboardingStep.LINK, OnboardingStep.PERMISSIONS, OnboardingStep.DONE,
            ),
            shared,
        )
    }

    @Test
    fun listFilesAreOfferedOnDesktop() {
        assertTrue(listFileFormatsOffered)
    }
}
