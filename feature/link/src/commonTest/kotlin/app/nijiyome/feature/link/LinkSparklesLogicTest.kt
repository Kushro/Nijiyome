// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link

import app.nijiyome.link.LinkApiError
import app.nijiyome.link.LinkError
import app.nijiyome.model.LinkProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkSparklesLogicTest {
    private fun p(id: String) = LinkProfile(id = id, label = id, upscale = false, frameGeneration = false, outputWidth = 1920, outputHeight = 1080, measuredSustain = "true")

    private val profiles = listOf(p("p-none"), p("p-anime4k-1440"), p("p-lsfg-1080-x2"), p("p-anime4k-1080-fg2x"), p("p-lsfg-1080-auto120"), p("p-future"))

    @Test
    fun profilesBecomeTwoAxes() {
        assertEquals(listOf(null, 1080, 1440), ProfileGrid.upscaleOptions(profiles))
        assertEquals(listOf(FrameAxis.None, FrameAxis.Fixed(2), FrameAxis.Auto(120)), ProfileGrid.frameOptions(profiles))
        assertEquals("p-anime4k-1080-fg2x", ProfileGrid.find(profiles, 1080, FrameAxis.Fixed(2))?.id)
        assertNull(ProfileGrid.axesOf(p("p-future")))
    }

    @Test
    fun pressingAnUpscaleKeepsTheFramesWhenThatPairPlays() {
        val all: (LinkProfile) -> Boolean = { true }
        assertEquals("p-anime4k-1080-fg2x", ProfileGrid.pickUpscale(profiles, 1080, FrameAxis.Fixed(2), all)?.id)
        // 1440 has no ×2: the first playable one with 1440 is taken
        assertEquals("p-anime4k-1440", ProfileGrid.pickUpscale(profiles, 1440, FrameAxis.Fixed(2), all)?.id)
        assertNull(ProfileGrid.pickUpscale(profiles, 1440, null) { it.id != "p-anime4k-1440" })
    }

    @Test
    fun pairingFailuresAreNamed() {
        assertEquals(PairProblem.EXPIRED, LinkLogic.pairProblem(LinkError.Api(LinkApiError.PairingClosed)))
        assertEquals(PairProblem.WRONG_CODE, LinkLogic.pairProblem(LinkError.Api(LinkApiError.PairingCodeInvalid)))
        assertEquals(PairProblem.TOO_MANY, LinkLogic.pairProblem(LinkError.Api(LinkApiError.PairingAttemptsExceeded)))
        assertEquals(PairProblem.UNREACHABLE, LinkLogic.pairProblem(LinkError.Transport.Refused))
        assertNull(LinkLogic.pairProblem(null))
    }

    @Test
    fun latencyAndServerFormats() {
        assertEquals(LatencyTone.GOOD, LinkLogic.latencyTone(14))
        assertEquals(LatencyTone.SLOW, LinkLogic.latencyTone(300))
        assertNull(LinkLogic.latencyTone(null))
        assertEquals("K7QM 2XRP 4D", LinkServerFormat.groupCode("K7QM2XRP4D"))
        assertEquals(0L, LinkServerFormat.secondsLeft(1_000L, 5_000L))
        assertEquals(58L, LinkServerFormat.secondsLeft(60_000L, 1_500L))
    }
}
