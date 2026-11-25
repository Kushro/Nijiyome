// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.AspectMode
import app.nijiyome.model.SkipKind
import kotlin.test.Test
import kotlin.test.assertEquals

class TouchPlayerUiTest {
    @Test
    fun quickAspectCyclesFitFillStretch() {
        assertEquals(AspectMode.FILL, nextQuickAspect(AspectMode.FIT))
        assertEquals(AspectMode.STRETCH, nextQuickAspect(AspectMode.FILL))
        assertEquals(AspectMode.FIT, nextQuickAspect(AspectMode.STRETCH))
        assertEquals(AspectMode.FIT, nextQuickAspect(AspectMode.RATIO_16_9))
    }

    @Test
    fun bufferedAheadUsesTorrentSpanOrEngineBuffer() {
        val engineRanges = seekBarRanges(positionMs = 20_000, durationMs = 100_000, bufferedMs = 68_000)
        assertEquals(48L, bufferedAheadSecs(20_000, 100_000, 68_000, engineRanges))

        val torrentRanges = seekBarRanges(
            positionMs = 20_000,
            durationMs = 100_000,
            bufferedMs = 95_000,
            torrentMarks = listOf(0.0..0.35, 0.7..0.9),
        )
        assertEquals(15L, bufferedAheadSecs(20_000, 100_000, 95_000, torrentRanges))
        assertEquals(0L, bufferedAheadSecs(10_000, 0, 50_000, SeekBarRanges.NONE))
    }

    @Test
    fun signedDeltaFormatsSecondsAndMinutes() {
        assertEquals("+0s", formatSignedDelta(0))
        assertEquals("+40s", formatSignedDelta(40))
        assertEquals("-15s", formatSignedDelta(-15))
        assertEquals("+1:05", formatSignedDelta(65))
        assertEquals("-2:30", formatSignedDelta(-150))
    }

    @Test
    fun skipSegmentLabelsAreLocalized() {
        assertEquals("Opening", skipSegmentLabel(SkipKind.INTRO))
        assertEquals("Ending", skipSegmentLabel(SkipKind.OUTRO))
        assertEquals("Resumen", skipSegmentLabel(SkipKind.RECAP))
    }
}
