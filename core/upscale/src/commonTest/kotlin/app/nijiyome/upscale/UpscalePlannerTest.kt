// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpscalePlannerTest {
    private val defaults = ImageUpscaleSettings()

    private fun decide(
        sw: Int,
        sh: Int,
        tw: Int,
        th: Int,
        settings: ImageUpscaleSettings = defaults,
        kind: UpscaleImageKind = UpscaleImageKind.POSTER,
        limits: UpscaleLimits = TEST_LIMITS,
    ) = UpscalePlanner.decide(settings, limits, kind, sw, sh, tw, th)

    private fun plan(decision: UpscaleDecision): UpscalePlan = assertIs<UpscaleDecision.Upscale>(decision).plan

    @Test
    fun aCardSmallerThanTheSourceIsSkipped() {
        // AniList extraLarge (460x650) in a 120 dp card at density 2.
        assertIs<UpscaleDecision.Skip>(decide(460, 650, 240, 339))
    }

    @Test
    fun aFactorUpTo115IsSkippedAndAboveItQualifies() {
        assertIs<UpscaleDecision.Skip>(decide(200, 200, 230, 230))
        assertEquals(2, plan(decide(200, 200, 240, 240)).scale)
    }

    @Test
    fun tinySourcesAreSkipped() {
        assertIs<UpscaleDecision.Skip>(decide(40, 300, 400, 3000))
    }

    @Test
    fun kindsTurnedOffAndTheSwitchAreRespected() {
        assertIs<UpscaleDecision.Skip>(decide(230, 325, 690, 975, kind = UpscaleImageKind.AVATAR))
        assertIs<UpscaleDecision.Skip>(decide(230, 325, 690, 975, settings = defaults.copy(enabled = false)))
        assertIs<UpscaleDecision.Skip>(decide(230, 325, 0, 0))
    }

    @Test
    fun theModelScaleFollowsTheNeededFactor() {
        assertEquals(2, plan(decide(100, 100, 200, 200, settings = defaults.copy(quality = UpscaleQuality.BEST))).scale)
        assertEquals(3, plan(decide(100, 100, 250, 250, settings = defaults.copy(quality = UpscaleQuality.BEST))).scale)
        assertEquals(4, plan(decide(100, 100, 350, 350, settings = defaults.copy(quality = UpscaleQuality.BEST))).scale)
    }

    @Test
    fun qualityCapsTheFactor() {
        val fast = plan(decide(100, 100, 400, 400, settings = defaults.copy(quality = UpscaleQuality.FAST)))
        val balanced = plan(decide(100, 100, 400, 400, settings = defaults.copy(quality = UpscaleQuality.BALANCED)))
        val best = plan(decide(100, 100, 400, 400, settings = defaults.copy(quality = UpscaleQuality.BEST)))
        assertEquals(UpscalePlan(2, 200, 200), fast)
        assertEquals(UpscalePlan(3, 300, 300), balanced)
        assertEquals(UpscalePlan(4, 400, 400), best)
    }

    @Test
    fun theResultIsScaledDownToTheTarget() {
        // Needs x2.5: the x3 output is stored at the on-screen size.
        assertEquals(UpscalePlan(3, 250, 250), plan(decide(100, 100, 250, 250, settings = defaults.copy(quality = UpscaleQuality.BEST))))
    }

    @Test
    fun theOutputCapLowersTheFactorOrSkips() {
        val capped = plan(decide(1000, 500, 4000, 2000, settings = defaults.copy(quality = UpscaleQuality.BEST)))
        assertEquals(UpscalePlan(3, 2560, 1280), capped)
        val android = TEST_LIMITS.copy(maxOutputLongSide = 1440)
        assertIs<UpscaleDecision.Skip>(decide(1300, 700, 2600, 1400, limits = android))
    }

    @Test
    fun keysBucketTheTargetAndFollowTheQuality() {
        val a = UpscaleKey.of("u", UpscaleImageKind.POSTER, defaults, 250, 300)
        val b = UpscaleKey.of("u", UpscaleImageKind.POSTER, defaults, 256, 320)
        val c = UpscaleKey.of("u", UpscaleImageKind.POSTER, defaults.copy(quality = UpscaleQuality.FAST), 256, 320)
        val d = UpscaleKey.of("u", UpscaleImageKind.BANNER, defaults, 256, 320)
        assertEquals(a, b)
        assertEquals(false, a == c)
        assertEquals(false, a == d)
        assertEquals(64, UpscaleKey.bucket(1))
        assertEquals(128, UpscaleKey.bucket(65))
    }
}
