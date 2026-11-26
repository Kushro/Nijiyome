// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.chrome.skipSegmentRes
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.rd_s5_aspect_16_9
import app.nijiyome.feature.player.resources.rd_s5_aspect_4_3
import app.nijiyome.feature.player.resources.rd_s5_aspect_fill
import app.nijiyome.feature.player.resources.rd_s5_aspect_fit
import app.nijiyome.feature.player.resources.rd_s5_aspect_stretch
import app.nijiyome.feature.player.resources.rd_s5_seg_ending
import app.nijiyome.feature.player.resources.rd_s5_seg_opening
import app.nijiyome.feature.player.resources.rd_s5_seg_recap
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
    fun skipSegmentLabelsAreResources() {
        assertEquals(Res.string.rd_s5_seg_opening, skipSegmentRes(SkipKind.INTRO))
        assertEquals(Res.string.rd_s5_seg_ending, skipSegmentRes(SkipKind.OUTRO))
        assertEquals(Res.string.rd_s5_seg_recap, skipSegmentRes(SkipKind.RECAP))
    }

    @Test
    fun aspectLabelsAreResources() {
        assertEquals(Res.string.rd_s5_aspect_fit, aspectLabelRes(AspectMode.FIT))
        assertEquals(Res.string.rd_s5_aspect_fill, aspectLabelRes(AspectMode.FILL))
        assertEquals(Res.string.rd_s5_aspect_stretch, aspectLabelRes(AspectMode.STRETCH))
        assertEquals(Res.string.rd_s5_aspect_16_9, aspectLabelRes(AspectMode.RATIO_16_9))
        assertEquals(Res.string.rd_s5_aspect_4_3, aspectLabelRes(AspectMode.RATIO_4_3))
    }
}
