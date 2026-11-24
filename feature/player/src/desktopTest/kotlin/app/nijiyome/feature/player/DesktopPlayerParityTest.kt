// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.model.Quality
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.SourceKind
import app.nijiyome.player.AudioOption
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.player.SubtitleOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hito 6: the desktop player's Tatakai Next behaviour (keys, speeds, tracks, source qualities, hover time, fonts). */
class DesktopPlayerParityTest {
    @Test
    fun desktopCapabilities() {
        val c = playerCapabilities
        assertTrue(c.pictureInPicture)
        assertTrue(c.subtitleFile)
        assertEquals(5_000L, c.seekStepMs)
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f), c.speeds)
        assertTrue(c.volumeControls && c.sourceQualities && c.desktopShortcuts)
        assertFalse(c.touchFirst)
    }

    @Test
    fun ctrlDigitsPickTheSixPresets() {
        assertEquals(QualityPresets.OFF, ctrlPreset("1"))
        assertEquals(QualityPresets.ANIME4K_A, ctrlPreset("2"))
        assertEquals(QualityPresets.ANIME4K_AA, ctrlPreset("NUMPAD_3"))
        assertEquals(QualityPresets.ANIME4K_B, ctrlPreset("4"))
        assertEquals(QualityPresets.ANIME4K_C, ctrlPreset("5"))
        assertEquals(QualityPresets.FSR, ctrlPreset("6"))
        assertNull(ctrlPreset("7"))
        assertNull(ctrlPreset("0"))
        assertNull(ctrlPreset("F"))
    }

    @Test
    fun speedStepsStopAtTheEnds() {
        val s = DESKTOP_SPEEDS
        assertEquals(1.25f, nextSpeed(1f, up = true, s))
        assertEquals(1.75f, nextSpeed(1.5f, up = true, s))
        assertEquals(2f, nextSpeed(2f, up = true, s))
        assertEquals(0.5f, nextSpeed(0.5f, up = false, s))
        assertEquals(1.5f, nextSpeed(1.75f, up = false, s))
        // a speed between steps goes to the next one
        assertEquals(1.25f, nextSpeed(1.1f, up = true, s))
        assertEquals("1.75", speedLabel(1.75f))
        assertEquals("2", speedLabel(2f))
    }

    @Test
    fun subtitleKeyGoesThroughTheTracksThenOff() {
        fun t(i: Int, sel: Boolean) = SubtitleOption(groupIndex = i, label = "T$i", language = null, selected = sel)
        val tracks = listOf(t(1, false), t(2, false))
        assertEquals(1, nextSubtitle(tracks, subtitlesOn = false)?.groupIndex)
        assertEquals(2, nextSubtitle(listOf(t(1, true), t(2, false)), subtitlesOn = true)?.groupIndex)
        assertNull(nextSubtitle(listOf(t(1, false), t(2, true)), subtitlesOn = true))
        assertNull(nextSubtitle(emptyList(), subtitlesOn = false))
        assertTrue(isDesktopAction(PlayerKeymap.SUBS_TOGGLE))
        assertTrue(isDesktopAction(PlayerKeymap.LOOP))
        assertFalse(isDesktopAction(PlayerKeymap.MUTE))
    }

    @Test
    fun audioKeyWrapsAround() {
        fun a(i: Int, sel: Boolean) = AudioOption(i, "A$i", null, sel)
        assertEquals(2, nextAudio(listOf(a(1, true), a(2, false)))?.id)
        assertEquals(1, nextAudio(listOf(a(1, false), a(2, true)))?.id)
        assertNull(nextAudio(listOf(a(1, true))))
    }

    @Test
    fun qualityMenuListsTheSourcesQualities() {
        fun src(label: String, q: Quality, recommended: Boolean = false, enabled: Boolean = true) =
            RankedSource(SourceCandidate("ext", "Ext", SourceKind.WEB, label, quality = q), score = 1f, extEnabled = enabled, recommended = recommended)
        val sources = listOf(
            src("a", Quality.P720, recommended = true),
            src("b", Quality.P1080),
            src("c", Quality.P1080),
            src("d", Quality.UNKNOWN),
            src("e", Quality.P2160, enabled = false),
        )
        assertEquals(listOf(Quality.P1080, Quality.P720), sourceQualities(sources))
        assertEquals("b", sourceForQuality(sources, Quality.P1080, failed = emptySet())?.candidate?.label)
        // a failed one is skipped while another of that quality is left
        assertEquals("c", sourceForQuality(sources, Quality.P1080, failed = setOf(sources[1].key))?.candidate?.label)
        assertEquals("b", sourceForQuality(sources, Quality.P1080, failed = setOf(sources[1].key, sources[2].key))?.candidate?.label)
        assertNull(sourceForQuality(sources, Quality.P480, failed = emptySet()))
        assertEquals("a", autoSource(sources)?.candidate?.label)
        assertEquals(emptyList(), sourceQualities(listOf(src("x", Quality.UNKNOWN))))
    }

    @Test
    fun hoverTimeFollowsThePointer() {
        assertEquals(0.5f, hoverFraction(50f, 100f))
        assertEquals(0f, hoverFraction(-10f, 100f))
        assertEquals(1f, hoverFraction(130f, 100f))
        assertEquals(0f, hoverFraction(10f, 0f))
        assertEquals(60_000L, hoverTimeMs(0.25f, 240_000L))
    }

    @Test
    fun installedFontsDropJavaLogicalFontsAndFilter() {
        val cleaned = InstalledFonts.cleaned(listOf("Serif", "noto Sans", "Dialog", "Arial", "Arial", " DejaVu Sans Mono "))
        assertEquals(listOf("Arial", "DejaVu Sans Mono", "noto Sans"), cleaned)
        assertEquals(listOf("DejaVu Sans Mono"), filterFamilies(cleaned, "sans mono"))
        assertEquals(cleaned, filterFamilies(cleaned, "  "))
        assertEquals(emptyList(), filterFamilies(cleaned, "zzz"))
    }
}
